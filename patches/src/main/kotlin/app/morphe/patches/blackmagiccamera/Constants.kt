package app.morphe.patches.blackmagiccamera

import app.morphe.patcher.patch.ApkFileType
import app.morphe.patcher.patch.AppTarget
import app.morphe.patcher.patch.Compatibility
import app.morphe.patcher.patch.SupportedAbi

internal val COMPATIBILITY_BLACKMAGIC_CAMERA = Compatibility(
    name = "Blackmagic Camera",
    packageName = "com.blackmagicdesign.android.blackmagiccam",
    apkFileType = ApkFileType.APKM,
    appIconColor = 0x101010,
    targets = listOf(
        AppTarget(
            version = "3.5.1.0044",
            versionCodes = mapOf(
                SupportedAbi.ARM64_V8A to 26101004,
                SupportedAbi.ARMEABI_V7A to 26101004,
            ),
        ),
    ),
)
