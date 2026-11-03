# Blackmagic Camera photo patches

These patches target Blackmagic Camera Android **3.5.1.0044** (`26101004`), package `com.blackmagicdesign.android.blackmagiccam`, for ARM64 and ARMv7 APKM installs.

## Patch selection

- **Blackmagic Camera — Enable Photo Mode** adds a VIDEO/PHOTO selector and a still shutter.
- **Blackmagic Camera — Take Photos While Recording** adds a separate shutter while the recorder is active. It can be selected by itself or together with Photo Mode.

Both use the same runtime extension and a separate camera-pipeline `ImageReader` output. The app’s `PipelineSurfaceManager` sends that output through the existing compositor, with its LUT-enable setting and monitoring flags disabled. The capture path copies the latest output frame and asynchronously writes JPEGs to `Pictures/Blackmagic Camera` via MediaStore. It does not call the recorder’s start/stop controls or reconfigure the session from the shutter callback.

## Limits and device verification

This is a rendered-frame capture path, not Blackmagic’s native full-resolution still-photo pipeline. The extra output is downscaled through the app’s existing size helper (with a 1280-pixel long-edge fallback) and is rate-limited by the app’s external output configuration to 12 fps. A shutter can therefore save a frame up to roughly one output interval old. Camera controls remain those of the active preview. Rotation is based on display rotation; exact crop, orientation and color equivalence require device comparison.

Static APK analysis shows the app updates its preview and recording renderers when its active LUT changes, and the injected output is configured through that rendering pipeline. This supports LUT retention, including LUT changes, but the actual saved color has **not** been compared against the preview on a device. Do not treat exact matching as verified. The physical-device checks still needed are LUT on/off/custom-LUT comparisons, PHOTO/VIDEO switching, continuous video/audio capture with repeated stills, rotation and lens changes, background/foreground and camera teardown, storage errors, and high-resolution/HDR/high-frame-rate workflows. No Android device was attached during this build.

The extension uses one active 12-fps rendered output and a single in-flight JPEG save. Storage or output-reader errors show a short toast. Captures are preview-resolution JPEGs; RAW, native sensor-resolution stills and guarantee of uninterrupted capture at every high-speed/HDR setting are not implemented.

## Verification

Verified APK facts came from the APKM `base.apk` and ABI splits: package/version/version code and the DEX method signatures used by the patches. Morphe applied each selector independently and both together to the base APK; modified DEX files parsed, expected hook references were present/absent for each selection, and the patched APK ZIP contained the generated patch and extension DEX entries. This is build-time/static validation, not an install or runtime test.

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
