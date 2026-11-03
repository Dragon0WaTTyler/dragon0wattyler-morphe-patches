package app.morphe.patches.blackmagiccamera

import app.morphe.patcher.Patcher
import app.morphe.patcher.PatcherConfig
import app.morphe.patcher.apk.ApkUtils.applyTo
import app.morphe.patcher.dex.NoOpDexVerifier
import app.morphe.patcher.patch.Patch
import app.morphe.patcher.patch.PatchLoader
import com.android.tools.smali.dexlib2.Opcodes
import com.android.tools.smali.dexlib2.dexbacked.DexBackedDexFile
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import kotlinx.coroutines.runBlocking
import java.io.BufferedInputStream
import java.io.File
import java.nio.file.Files
import java.util.zip.ZipFile

/** Applies selected bundle patches to the verified base APK and checks the compiled output. */
fun main(arguments: Array<String>) {
    require(arguments.size in 2..4) {
        "Usage: BlackmagicCameraApkBuildKt <bundle.mpp> <base.apk> <photo|recording|both> [output.apk]"
    }
    val bundleFile = File(arguments[0]).canonicalFile
    val input = File(arguments[1]).canonicalFile
    require(bundleFile.isFile) { "Morphe bundle not found: $bundleFile" }
    require(input.isFile) { "Blackmagic Camera base APK not found: $input" }
    val mode = arguments[2]
    require(mode in setOf("photo", "recording", "both")) { "Unknown patch mode: $mode" }

    ZipFile(bundleFile).use { bundle ->
        val patchDexEntry = bundle.getEntry("classes.dex") ?: error("Bundle has no compiled classes.dex")
        val patchDexClasses = BufferedInputStream(bundle.getInputStream(patchDexEntry)).use { dex ->
            DexBackedDexFile.fromInputStream(Opcodes.getDefault(), dex).classes.map { it.type }.toSet()
        }
        check("Lapp/morphe/patches/blackmagiccamera/PhotoModePatchKt;" in patchDexClasses)
        check("Lapp/morphe/patches/blackmagiccamera/PhotosWhileRecordingPatchKt;" in patchDexClasses)

        val extensionEntry = bundle.getEntry("extensions/extension.mpe") ?: error("Bundle has no extension.mpe")
        val extensionClasses = BufferedInputStream(bundle.getInputStream(extensionEntry)).use { dex ->
            DexBackedDexFile.fromInputStream(Opcodes.getDefault(), dex).classes.map { it.type }.toSet()
        }
        check("Lapp/morphe/extension/blackmagiccamera/PhotoCaptureRuntime;" in extensionClasses)
    }

    val patchLoader = PatchLoader.Jar(setOf(bundleFile))
    val photo = patchLoader.single { it.name == "Blackmagic Camera — Enable Photo Mode" }
    val recording = patchLoader.single { it.name == "Blackmagic Camera — Take Photos While Recording" }
    val selected: Set<Patch<*>> = when (mode) {
        "photo" -> setOf(photo)
        "recording" -> setOf(recording)
        else -> setOf(photo, recording)
    }

    val workspace = Files.createTempDirectory("blackmagic-$mode-").toFile()
    Patcher(PatcherConfig(input, workspace, verifier = NoOpDexVerifier, validate = true)).use { patcher ->
        patcher += selected
        runBlocking {
            patcher().collect { result ->
                result.exception?.let { throw it }
                println("Applied: ${result.patch.name}")
            }
        }
        val result = patcher.get()
        check(result.dexFiles.isNotEmpty()) { "Patch execution returned no DEX output" }
        val expectedDexNames = result.dexFiles.map { it.name }.toSet()
        val compiledDexDirectory = workspace.resolve("patched/dex")
        val compiledDexFiles = expectedDexNames.map(compiledDexDirectory::resolve)
        check(compiledDexFiles.isNotEmpty()) { "Morphe did not write compiled DEX files" }
        val patchedMethods = compiledDexFiles.flatMap { file ->
            check(file.isFile) { "Morphe output DEX is missing: $file" }
            BufferedInputStream(file.inputStream()).use { dex ->
                DexBackedDexFile.fromInputStream(Opcodes.getDefault(), dex).classes.flatMap { it.methods.toList() }
            }
        }
        check(patchedMethods.isNotEmpty()) { "Patched APK contains no readable DEX methods" }
        fun calls(owner: String, name: String): Boolean = patchedMethods.any { method ->
            method.implementation?.instructions?.any { instruction ->
                val reference = (instruction as? ReferenceInstruction)?.reference as? MethodReference
                reference?.definingClass == owner && reference.name == name
            } == true
        }

        val runtime = "Lapp/morphe/extension/blackmagiccamera/PhotoCaptureRuntime;"
        check(calls(runtime, "resolveReader")) {
            "Compiled PipelineSurfaceManager hook is absent"
        }
        check(calls(runtime, "enablePhotoMode") == (mode != "recording")) {
            "Photo Mode hook does not match selected patches"
        }
        check(calls(runtime, "enablePhotosWhileRecording") == (mode != "photo")) {
            "Recording shutter hook does not match selected patches"
        }
        check(calls(runtime, "onVideoRecorderState") == (mode != "photo")) {
            "Recording state hook does not match selected patches"
        }
        check(patchedMethods.any { it.definingClass == runtime }) { "Runtime extension class is absent from patched APK" }
        val output = File(arguments.getOrNull(3) ?: workspace.resolve("patched.apk").path).canonicalFile
        output.parentFile?.mkdirs()
        input.copyTo(output, overwrite = true)
        result.applyTo(output)
        ZipFile(output).use { apk ->
            check(expectedDexNames.all { apk.getEntry(it) != null }) {
                "Compiled patch or extension DEX is absent from final APK"
            }
        }
        println("Patched APK written: ${output.canonicalPath}")
        println("$mode: Morphe patch application and output DEX structure verification succeeded")
    }
}
