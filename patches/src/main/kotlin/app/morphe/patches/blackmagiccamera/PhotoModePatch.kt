package app.morphe.patches.blackmagiccamera

import app.morphe.patcher.extensions.InstructionExtensions.addInstruction
import app.morphe.patcher.patch.bytecodePatch

private const val EXTENSION_CLASS = "Lapp/morphe/extension/blackmagiccamera/PhotoCaptureRuntime;"

@Suppress("unused")
val blackmagicCameraPhotoModePatch = bytecodePatch(
    name = "Blackmagic Camera — Enable Photo Mode",
    description = "Adds a photo/video switch and saves LUT-rendered camera frames to Gallery at preview-output resolution.",
) {
    compatibleWith(COMPATIBILITY_BLACKMAGIC_CAMERA)
    dependsOn(blackmagicCameraCaptureRuntimePatch)
    extendWith("extensions/extension.mpe")

    execute {
        val insertionIndex = MainActivityOnCreateFingerprint.instructionMatches.single().index + 1
        MainActivityOnCreateFingerprint.method.addInstruction(
            insertionIndex,
            "invoke-static/range {p0 .. p0}, $EXTENSION_CLASS->enablePhotoMode(Landroid/app/Activity;)V",
        )
    }
}
