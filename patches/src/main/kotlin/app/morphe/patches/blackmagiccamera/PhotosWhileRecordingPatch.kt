package app.morphe.patches.blackmagiccamera

import app.morphe.patcher.extensions.InstructionExtensions.addInstruction
import app.morphe.patcher.patch.bytecodePatch

private const val EXTENSION_CLASS = "Lapp/morphe/extension/blackmagiccamera/PhotoCaptureRuntime;"

@Suppress("unused")
val blackmagicCameraPhotosWhileRecordingPatch = bytecodePatch(
    name = "Blackmagic Camera — Take Photos While Recording",
    description = "Adds a recording shutter that saves a separate LUT-rendered preview frame without changing the active recording session.",
) {
    compatibleWith(COMPATIBILITY_BLACKMAGIC_CAMERA)
    dependsOn(blackmagicCameraCaptureRuntimePatch)
    extendWith("extensions/extension.mpe")

    execute {
        val insertionIndex = MainActivityOnStartFingerprint.instructionMatches.single().index + 1
        MainActivityOnStartFingerprint.method.addInstruction(
            insertionIndex,
            "invoke-static/range {p0 .. p0}, $EXTENSION_CLASS->enablePhotosWhileRecording(Landroid/app/Activity;)V",
        )

        VideoSessionRecorderStateFingerprint.method.addInstruction(
            0,
            "invoke-static/range {p1 .. p1}, $EXTENSION_CLASS->onVideoRecorderState(Ljava/lang/Object;)V",
        )

    }
}
