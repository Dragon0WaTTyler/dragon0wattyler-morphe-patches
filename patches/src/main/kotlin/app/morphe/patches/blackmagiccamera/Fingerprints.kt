package app.morphe.patches.blackmagiccamera

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.methodCall

internal object MainActivityOnCreateFingerprint : Fingerprint(
    definingClass = "Lcom/blackmagicdesign/android/blackmagiccam/ui/MainActivity;",
    name = "onCreate",
    parameters = listOf("Landroid/os/Bundle;"),
    returnType = "V",
    filters = listOf(
        methodCall("Lxn2;->onCreate(Landroid/os/Bundle;)V"),
    ),
)

internal object MainActivityOnStartFingerprint : Fingerprint(
    definingClass = "Lcom/blackmagicdesign/android/blackmagiccam/ui/MainActivity;",
    name = "onStart",
    parameters = emptyList(),
    returnType = "V",
    filters = listOf(
        methodCall("Lmj;->onStart()V"),
    ),
)

internal object MainActivityOnDestroyFingerprint : Fingerprint(
    definingClass = "Lcom/blackmagicdesign/android/blackmagiccam/ui/MainActivity;",
    name = "onDestroy",
    parameters = emptyList(),
    returnType = "V",
)

internal object VideoSessionRecorderStateFingerprint : Fingerprint(
    definingClass = "Lcom/blackmagicdesign/android/camera/domain/h;",
    name = "x",
    parameters = listOf("Lcom/blackmagicdesign/android/recorder/BaseRecorder\$State;"),
    returnType = "V",
)

internal object PipelineSurfaceManagerOutputsFingerprint : Fingerprint(
    definingClass = "Lcom/blackmagicdesign/android/camera/domain/b;",
    name = "b",
    parameters = listOf(
        "Loa6;",
        "Landroid/util/Size;",
        "Landroid/media/ImageReader;",
    ),
    returnType = "Lg85;",
)
