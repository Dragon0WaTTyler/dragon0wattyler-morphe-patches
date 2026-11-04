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

internal object MainScreenContentFingerprint : Fingerprint(
    definingClass = "Lcom/blackmagicdesign/android/blackmagiccam/ui/MainActivity\$loadMainScreen\$1;",
    name = "invokeSuspend",
    parameters = listOf("Ljava/lang/Object;"),
    returnType = "Ljava/lang/Object;",
    filters = listOf(
        methodCall("Lcv0;->a(Lbv0;Landroidx/compose/runtime/internal/a;)V"),
    ),
)

internal object CoreRecordingStateFingerprint : Fingerprint(
    definingClass = "Lcom/blackmagicdesign/android/camera/model/s;",
    name = "v",
    parameters = listOf("Lcom/blackmagicdesign/android/utils/entity/CoreRecordingState;"),
    returnType = "V",
)

/** VideoSessionManager.f(long) builds/attaches a camera session using its owned preview field `e`. */
internal object VideoSessionManagerBuildSessionFingerprint : Fingerprint(
    definingClass = "Lcom/blackmagicdesign/android/camera/domain/j;",
    name = "f",
    parameters = listOf("J"),
    returnType = "V",
)
