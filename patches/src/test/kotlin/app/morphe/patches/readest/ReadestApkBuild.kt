package app.morphe.patches.readest

import app.morphe.patcher.Patcher
import app.morphe.patcher.PatcherConfig
import app.morphe.patcher.apk.ApkUtils.applyTo
import app.morphe.patcher.patch.PatchLoader
import kotlinx.coroutines.runBlocking
import java.io.File
import java.nio.file.Files
import java.util.zip.ZipFile

/** Applies the built patch to a supplied stock APK to check patcher compatibility. */
fun main(arguments: Array<String>) {
    require(arguments.size in 2..3) {
        "Usage: ReadestApkBuildKt <bundle.mpp> <base.apk> [output.apk]"
    }

    val bundle = File(arguments[0]).canonicalFile
    val input = File(arguments[1]).canonicalFile
    require(bundle.isFile) { "Morphe bundle not found: $bundle" }
    require(input.isFile) { "Readest base APK not found: $input" }

    val output = File(arguments.getOrNull(2) ?: "build/validation/readest-patched.apk").canonicalFile
    output.parentFile.mkdirs()

    val patchLoader = PatchLoader.Jar(setOf(bundle))
    val patch = patchLoader.single { it.name == "Readest S Pen Air Actions" }
    val workspace = Files.createTempDirectory("readest-air-actions-").toFile()

    Patcher(PatcherConfig(input, workspace, validate = true)).use { patcher ->
        patcher += setOf(patch)
        runBlocking {
            patcher().collect { result ->
                result.exception?.let { throw it }
                println("Applied: ${result.patch.name}")
            }
        }

        val result = patcher.get()
        check(result.dexFiles.isNotEmpty()) { "Patch execution returned no DEX output" }
        check(result.resources.resourcesApk?.isFile == true) {
            "Morphe did not compile the patched manifest and RemoteActions resources"
        }
        input.copyTo(output, overwrite = true)
        result.applyTo(output)
    }

    ZipFile(output).use { apk ->
        check(apk.getEntry("AndroidManifest.xml") != null) { "Patched APK has no AndroidManifest.xml" }
        check(apk.getEntry("resources.arsc") != null) { "Patched APK has no compiled resources" }
    }
    println("Patched APK build validation succeeded: ${output.absolutePath}")
}
