# Blackmagic Camera photo patches

These patches target Blackmagic Camera Android **3.5.1.0044** (`26101004`), package `com.blackmagicdesign.android.blackmagiccam`, for ARM64 and ARMv7 APKM installs.

## Patch selection

- **Blackmagic Camera — Enable Photo Mode** adds a VIDEO/PHOTO selector. Selecting PHOTO MODE reveals a still shutter; VIDEO MODE leaves Blackmagic's original recording controls available without an extra still shutter. The selected mode survives activity recreation for the lifetime of the app process.
- **Blackmagic Camera — Take Photos While Recording** adds a separate shutter while the recorder is active. It can be selected by itself or together with Photo Mode.

Both use the same runtime extension. Photo Mode arms its feature from MainActivity.onCreate; the recording patch arms its feature from MainActivity.onStart. The shared runtime attaches controls after the permission-gated MainActivity$loadMainScreen$1.invokeSuspend call to cv0.a(...) returns. In the target APK, that call installs the main Compose screen with setContentView; the v1.0.0-dev.5 onCreate/onStart overlay was added before this point and was removed when the screen was installed. This is the evidence-backed reason the tested build launched with both patches applied but showed no controls. The activity callbacks only arm their feature flags; they do not show the camera controls before the screen exists.

If one patch's lifecycle callback arrives after the main-screen hook has already installed controls, the runtime now detects the changed feature set and rebuilds the control group on the UI thread. This prevents a timing race from omitting the second, independently selected shutter. Each lifecycle injection is also checked in the generated target DEX: Photo Mode activation in `MainActivity.onCreate`, recording-photo activation in `MainActivity.onStart`, main-screen attachment immediately after `cv0.a(...)`, and cleanup in `MainActivity.onDestroy`.

Capture uses PixelCopy on the target APK’s defpackage.tt preview SurfaceView. It copies the displayed preview pixels and asynchronously writes JPEGs to Pictures/Blackmagic Camera through MediaStore. It does not add a camera output, rebuild the active session, or call the recorder’s start/stop controls. The recording shutter visibility follows the app’s CoreRecordingState (RECORDING), not BaseRecorder.State.READY, which only describes recorder readiness.

The control group attaches to `android.R.id.content` after Blackmagic installs its main screen, tracks content replacement and layout changes, and restores itself if the overlay is detached. It first searches the Compose accessibility tree for Blackmagic's localized Record/Stop Recording control and positions the added controls beside it. If that semantic node is unavailable, it tries preview-relative corners starting at the upper-left, then scans for a free rectangle that avoids any detected record-control bounds. If no safe area fits, it hides the added controls rather than covering Blackmagic's recording control. The diagnostic panel reports which placement path was found. Because Compose semantics and final drawing/touch bounds can vary by device and orientation, collision-free placement and preservation of the original record button's hit target still require a device screenshot and touch test. The PHOTO shutter appears after selecting PHOTO MODE; the separate recording shutter appears only while the verified `CoreRecordingState` is `RECORDING` (not while paused). A small status indicator remains available whenever either patch is enabled, including while idle with only the recording patch selected. It probes a 2×2 PixelCopy frame in bursts of up to six attempts, then waits two seconds before trying again while the preview remains unavailable; only one probe runs at a time, and callbacks from replaced surfaces are ignored. FRAME READY appears only after the camera surface returns a frame. Long-press the mode or shutter control (or tap the status indicator) to inspect extension-loaded, patch-enabled, main-screen-hook, UI-attached, preview-frame, control-placement, recording-state, last-copy, and last-save diagnostics without ADB.

## Limits and device verification

This is a rendered-preview capture path, not Blackmagic’s native full-resolution still-photo pipeline. The JPEG is limited to the preview view’s rendered dimensions and 12 megapixels; it can be softer or lower resolution than a native still. PixelCopy is requested at shutter time, so it does not depend on a separately sampled frame or 12-fps reader. The saved image is the preview surface itself, without camera UI controls. No additional rotation or LUT transform is applied by the extension.

The target APK's decompiled pipeline provides static support for LUT preservation: the `PREVIEW` output carries an `isLutVisible` flag into Blackmagic's `CompositeRenderer`, which passes that flag to its configured `Float16Lut` before producing the preview surface. PixelCopy reads that surface, so it should save the LUT-rendered preview without applying the LUT twice or changing the active camera session. This establishes the intended pipeline path, not device-observed pixel equivalence; custom-LUT color, rotation, HDR conversion, and display color-space matching still need comparison on the target device. Physical-device checks still needed include LUT on/off/custom-LUT comparisons, PHOTO/VIDEO switching, continuous video/audio capture with repeated stills, rotation and lens changes, background/foreground and camera teardown, storage errors, and high-resolution/HDR/high-frame-rate workflows.

Only one PixelCopy/JPEG save may be in flight; rapid taps are rejected with brief feedback. Preview, PixelCopy, allocation, and MediaStore failures are recorded in the in-app diagnostic panel and shown as a short toast. RAW and native sensor-resolution stills are not implemented. Avoid treating uninterrupted capture in every high-speed/HDR workflow as verified until device testing.

## Verification

Verified APK facts came from the APKM base.apk and ABI splits: package/version/version code and the DEX method signatures used by the patches. Morphe applied each selector independently and both together to the base APK; modified DEX files parsed, lifecycle callbacks were verified in their exact target methods, the main-screen attachment call was verified immediately after cv0.a(...), the recording-state hook was present only for the recording patch, the PixelCopy path was present, and the patched APK ZIP contained the generated patch and extension DEX entries. The local v1.0.0-dev.6 Morphe bundle and selector-specific APK outputs build successfully. This is build-time/static validation, not an install or runtime test. The corrected build has not yet been installed on a device.

The repository’s `SdkDexVerifier` also reports 838 missing vendor dependency hierarchy references in the stock APK (Netty, native-tcnative and other optional/vendor classes). These are from unchanged stock classes; the targeted validator therefore checks the changed output DEX files structurally and confirms the final APK archive entries instead of accepting those unrelated whole-app hierarchy errors.

Build the bundle with the repository’s official task:

```powershell
$env:ANDROID_HOME='C:\Users\walid\AppData\Local\Android\Sdk'
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
$initPath=Join-Path $env:TEMP 'morphe-blackmagic-local-plugin.init.gradle'
.\gradlew.bat -I $initPath '-Pgpr.user=local-source-build' '-Pgpr.key=local-source-build' :patches:buildAndroid --console=plain
```

Apply and statically validate a chosen selection against the extracted stock `base.apk`:

```powershell
$apk='C:\path\to\extracted-blackmagic-apkm\base.apk'
.\gradlew.bat -I $initPath '-Pgpr.user=local-source-build' '-Pgpr.key=local-source-build' :patches:validateBlackmagicCamera "-PbmcApk=$apk" '-PbmcMode=photo' --console=plain
.\gradlew.bat -I $initPath '-Pgpr.user=local-source-build' '-Pgpr.key=local-source-build' :patches:validateBlackmagicCamera "-PbmcApk=$apk" '-PbmcMode=recording' --console=plain
.\gradlew.bat -I $initPath '-Pgpr.user=local-source-build' '-Pgpr.key=local-source-build' :patches:validateBlackmagicCamera "-PbmcApk=$apk" '-PbmcMode=both' --console=plain
```

`patches:generatePatchesList` loads the definitions and writes the Manager patch-selection metadata. Release builds use the repository's standard versioned bundle name (`patches-<version>.mpp`), which is also the filename referenced by `patches-bundle.json` and attached to GitHub releases. The release workflow sets the bundle version from the prerelease tag.
