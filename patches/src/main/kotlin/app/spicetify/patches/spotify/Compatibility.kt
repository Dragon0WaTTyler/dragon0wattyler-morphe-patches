package app.spicetify.patches.spotify

import app.morphe.patcher.patch.ApkFileType
import app.morphe.patcher.patch.AppTarget
import app.morphe.patcher.patch.Compatibility
import app.morphe.patcher.patch.SupportedAbi

val spotifyCompatibility = Compatibility(
    name = "Spotify",
    packageName = "com.spotify.music",
    apkFileType = ApkFileType.APKM,
    appIconColor = 0x1DB954,
    targets = listOf(
        AppTarget(
            version = "9.1.94.1929",
            versionCodes = mapOf(SupportedAbi.ARM64_V8A to 147600575),
            isExperimental = true,
            description = "Experimental Android customization patches; six patches validated offline; device testing pending.",
        ),
    ),
)

/** Fail closed if an API consumer overrides Manager's unavailable state. */
internal fun requireSpotify9194Port(reason: String) {
    throw app.morphe.patcher.patch.PatchException("Unsupported on Spotify 9.1.94.1929: $reason. No legacy hooks were applied.")
}
