package app.morphe.patches.blackmagiccamera

import app.morphe.patcher.extensions.InstructionExtensions.addInstruction
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.bytecodePatch

private const val EXTENSION_CLASS = "Lapp/morphe/extension/blackmagiccamera/PhotoCaptureRuntime;"

internal val blackmagicCameraCaptureRuntimePatch = bytecodePatch {
    execute {
        MainActivityOnDestroyFingerprint.method.addInstruction(
            0,
            "invoke-static/range {p0 .. p0}, $EXTENSION_CLASS->onActivityDestroyed(Landroid/app/Activity;)V",
        )

        PipelineSurfaceManagerOutputsFingerprint.method.addInstructions(
            0,
            """
                invoke-static/range {p0 .. p3}, $EXTENSION_CLASS->resolveReader(Ljava/lang/Object;Ljava/lang/Object;Landroid/util/Size;Landroid/media/ImageReader;)Landroid/media/ImageReader;
                move-result-object p3
            """.trimIndent(),
        )
    }
}
