package app.morphe.patches.blackmagiccamera

import app.morphe.patcher.extensions.InstructionExtensions.addInstruction
import app.morphe.patcher.patch.bytecodePatch

private const val EXTENSION_CLASS = "Lapp/morphe/extension/blackmagiccamera/PhotoCaptureRuntime;"

internal val blackmagicCameraCaptureRuntimePatch = bytecodePatch {
    execute {
        VideoSessionManagerBuildSessionFingerprint.method.addInstruction(
            0,
            "invoke-static/range {p0 .. p0}, $EXTENSION_CLASS->onVideoSessionManager(Ljava/lang/Object;)V",
        )

        MainActivityOnDestroyFingerprint.method.addInstruction(
            0,
            "invoke-static/range {p0 .. p0}, $EXTENSION_CLASS->onActivityDestroyed(Landroid/app/Activity;)V",
        )

        val contentIndex = MainScreenContentFingerprint.instructionMatches.single().index + 1
        MainScreenContentFingerprint.method.addInstruction(
            contentIndex,
            "invoke-static/range {p0 .. p0}, $EXTENSION_CLASS->onMainScreenLoaded(Ljava/lang/Object;)V",
        )
    }
}
