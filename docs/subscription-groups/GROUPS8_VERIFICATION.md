# PocketTube Groups V8 verification

## Build and checks

- Candidate: `patches-2.0.0-groups.8.mpp` (15,604,617 bytes; SHA-256 `3BF37F96084365C6AF345655844C250494BEB00D83155B9C3FEDA6AC4C54F9D6`).
- `:extensions:youtube:test`: 419 tests, 0 failures, 0 skipped.
- `:patches:checkStringResources`: 159,174 strings verified, no issues.
- `:patches:buildAndroid`: successful. The `.mpp` is a ZIP-format bundle.
- Toolchain: Gradle 9.8.2, JDK 17, Android compile SDK 36, locally available pinned Morphe build components.

## Runtime boundary

`adb devices` returned no connected devices. The YouTube 21.30.216 APK and Samsung S23 Ultra runtime were not available. This verifies compilation, source unit tests, resources, and bundle creation; it does not verify the selector's visual appearance, the toolbar gap, native popup behavior, or YouTube actions on-device. Follow [ANDROID_CHECKLIST_V8.md](ANDROID_CHECKLIST_V8.md) for focused device checks. Native-menu constraints are detailed in [NATIVE_MENU_INVESTIGATION.md](NATIVE_MENU_INVESTIGATION.md) and [GROUPS8_LIMITATIONS.md](GROUPS8_LIMITATIONS.md).
