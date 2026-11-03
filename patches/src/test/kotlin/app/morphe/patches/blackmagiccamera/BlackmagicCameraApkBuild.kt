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

        fun callsFrom(owner: String, name: String, parameters: List<String>, target: String): Boolean {
            val method = patchedMethods.singleOrNull {
                it.definingClass == owner && it.name == name
                        && it.parameterTypes.map { type -> type.toString() } == parameters
            } ?: return false
            return method.implementation?.instructions?.any { instruction ->
                val reference = (instruction as? ReferenceInstruction)?.reference as? MethodReference
                reference?.definingClass == "Lapp/morphe/extension/blackmagiccamera/PhotoCaptureRuntime;"
                        && reference.name == target
            } == true
        }

        val runtime = "Lapp/morphe/extension/blackmagiccamera/PhotoCaptureRuntime;"
        val activityClass = "Lcom/blackmagicdesign/android/blackmagiccam/ui/MainActivity;"
        val runtimeMethods = patchedMethods
            .filter { it.definingClass == runtime }
            .map { it.name }
            .toSet()
        val requiredRuntimeMethods = buildSet {
            add("onMainScreenLoaded")
            add("onActivityDestroyed")
            if (mode != "recording") add("enablePhotoMode")
            if (mode != "photo") {
                add("enablePhotosWhileRecording")
                add("onCoreRecordingState")
            }
        }
        check(runtimeMethods.containsAll(requiredRuntimeMethods)) {
            "Built extension is missing called bridge methods"
        }
        val loaderClass = "Lcom/blackmagicdesign/android/blackmagiccam/ui/MainActivity\$loadMainScreen\$1;"
        val screenLoader = patchedMethods.single {
            it.definingClass == loaderClass && it.name == "invokeSuspend"
        }
        val screenInstructions = requireNotNull(screenLoader.implementation) {
            "Blackmagic main-screen loader has no DEX implementation"
        }.instructions.toList()
        fun instructionIndex(owner: String, name: String): Int = screenInstructions.indexOfFirst { instruction ->
            val reference = (instruction as? ReferenceInstruction)?.reference as? MethodReference
            reference?.definingClass == owner && reference.name == name
        }
        val setMainContentIndex = instructionIndex("Lcv0;", "a")
        val attachUiIndex = instructionIndex(runtime, "onMainScreenLoaded")
        check(setMainContentIndex >= 0 && attachUiIndex == setMainContentIndex + 1) {
            "Controls are not attached immediately after Blackmagic creates the main screen"
        }
        check(calls(runtime, "resolveReader").not()) {
            "Photo capture must not add an extra camera output to the active session"
        }
        check(calls("Landroid/view/PixelCopy;", "request")) {
            "Photo capture does not copy the displayed preview surface"
        }
        check(calls(runtime, "enablePhotoMode") == (mode != "recording")) {
            "Photo Mode hook does not match selected patches"
        }
        check(calls(runtime, "enablePhotosWhileRecording") == (mode != "photo")) {
            "Recording shutter hook does not match selected patches"
        }
        check(calls(runtime, "onMainScreenLoaded")) {
            "Shared runtime main-screen attachment hook is absent"
        }
        check(callsFrom(activityClass, "onCreate", listOf("Landroid/os/Bundle;"), "enablePhotoMode")
                == (mode != "recording")) {
            "Photo Mode activation is not attached to MainActivity.onCreate"
        }
        check(callsFrom(activityClass, "onStart", emptyList(), "enablePhotosWhileRecording")
                == (mode != "photo")) {
            "Recording-photo activation is not attached to MainActivity.onStart"
        }
        check(callsFrom(activityClass, "onDestroy", emptyList(), "onActivityDestroyed")) {
            "Shared runtime cleanup is not attached to MainActivity.onDestroy"
        }
        check(calls(runtime, "onCoreRecordingState") == (mode != "photo")) {
            "Core recording-state hook does not match selected patches"
        }
        val runtimeSource = File(
            "../extensions/extension/src/main/java/app/morphe/extension/blackmagiccamera/PhotoCaptureRuntime.java"
        ).readText()
        check("photoModeSelected = !photoModeSelected;" in runtimeSource
                && "shutter.setVisibility(photoModeSelected ? View.VISIBLE : View.GONE);" in runtimeSource) {
            "Photo Mode selection is not wired to showing and hiding the still shutter"
        }
        check("overlay.addOnAttachStateChangeListener" in runtimeSource
                && "MAIN_HANDLER.post(() ->" in runtimeSource
                && "root.getViewTreeObserver().addOnGlobalLayoutListener(layoutListener);" in runtimeSource
                && "refreshUiSafely(Ui.this);" in runtimeSource
                && "previousContent != root" in runtimeSource
                && "controls.getParent() != overlay" in runtimeSource
                && "listenerRoot != root" in runtimeSource) {
            "Camera controls do not handle content detachment and layout changes"
        }
        check("findAvailableBounds(" in runtimeSource
                && "record == null || !Rect.intersects(candidate, record)" in runtimeSource
                && "if (controlBounds == null)" in runtimeSource
                && "No collision-free area; added controls hidden" in runtimeSource) {
            "Camera controls must not cover Blackmagic's recording control when placement space is constrained"
        }
        check("lastCameraActivity.get()" in runtimeSource
                && "installUiSafely(ui, activity);" in runtimeSource
                && "refreshUiSafely(ui)" in runtimeSource
                && "refreshFeatureUi(activity);" in runtimeSource
                && "ensureControls(activity);" in runtimeSource
                && "featureSetChanged" in runtimeSource) {
            "Camera Activity fallback or safe UI lifecycle reporting is missing"
        }
        check("MAIN_HANDLER.post(() -> installUiSafely(Ui.this, current));" in runtimeSource
                && "refreshUiSafely(Ui.this);" in runtimeSource) {
            "Layout-change UI reinstall and refresh must use the safe diagnostics boundary"
        }
        check("retryFrameProbe(surface, generation);" in runtimeSource
                && "generation != probeGeneration || surface != probedSurface" in runtimeSource
                && "probeAttempts == 0 || probeAttempts >= 6 ? 2000L : 500L" in runtimeSource) {
            "Preview diagnostics do not retry at a bounded rate or reject stale surface callbacks"
        }
        check("controls.setVisibility(photoModeEnabled || recordingCaptureEnabled ? View.VISIBLE : View.GONE);" in runtimeSource
                && "recordingShutter.setVisibility(recordingCaptureEnabled && recording ? View.VISIBLE : View.GONE);" in runtimeSource) {
            "Status diagnostics must remain accessible while the recording shutter is gated by active recording"
        }
        check("PixelCopy.request(preview, bitmap" in runtimeSource
                && "onCoreRecordingState(Object state)" in runtimeSource
                && "\"RECORDING\".equalsIgnoreCase(name)" in runtimeSource) {
            "Photo frame capture or active-recording state tracking is missing"
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
