# PocketTube Groups V7 verification

Candidate bundle: `patches-2.0.0-groups.7.mpp` (15,605,808 bytes, unsigned local build).

## Automated checks

| Suite | Passed | Failed | Skipped |
| --- | ---: | ---: | ---: |
| YouTube extension | 419 | 0 | 0 |
| YouTube Music extension | 83 | 0 | 0 |
| Patches JVM suite | 52 | 0 | 5 |
| **Total** | **554** | **0** | **5** |

The five skipped cases are the APK-dependent `JamPatchRegressionTest` checks. They require a YouTube Music APK, which was not provided. `:patches:checkStringResources` verified 159,173 strings with no issues. `:patches:buildAndroid` succeeded, and the generated `.mpp` passed ZIP integrity validation.

The build used Gradle 9.8.2, a JDK 17 runtime/toolchain, Android compile SDK 36, and locally available pinned Morphe build components. The candidate version was supplied as a Gradle property; the repository's semantic-release version metadata was left untouched.

## Runtime boundary

No YouTube 21.30.216 APK was supplied and `adb devices` returned no connected device. This is source and bundle verification only. The Samsung S23 Ultra playback, scrolling, migration from an installed V6.1 build, and RSS/cache behavior on-device remain unverified. Follow [RUNTIME_CHECKLIST.md](RUNTIME_CHECKLIST.md) before treating it as device-validated.

The group feed still uses a finite public Atom window. Local history retains up to 500 previously observed videos per channel and cannot restore uploads that were never retrieved. See [GROUPS7_HISTORY.md](GROUPS7_HISTORY.md) for the source comparison and retention details.
