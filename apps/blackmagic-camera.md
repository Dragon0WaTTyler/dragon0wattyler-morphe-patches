# Blackmagic Camera patches

> **Status:** Experimental development candidate. Check the [repository releases](https://github.com/Dragon0WaTTyler/dragon0wattyler-morphe-patches/releases) for the current prerelease and its verified bundle.

## App compatibility

- **App:** Blackmagic Camera for Android
- **Package:** `com.blackmagicdesign.android.blackmagiccam`
- **Version:** `3.5.1.0044`
- **Version code:** `26101004` (ARM64 and ARMv7 APKM splits)
- **Source:** This app remains a section of the existing [multi-app Morphe source](https://morphe.software/add-source?github=Dragon0WaTTyler/dragon0wattyler-morphe-patches).

## Independent patches

- **Blackmagic Camera — Enable Photo Mode** adds a VIDEO/PHOTO selector and still shutter.
- **Blackmagic Camera — Take Photos While Recording** adds a separate shutter while recording. The two patches can be selected independently or together.

Both use a rendered camera-pipeline frame and save JPEGs to Gallery. See the [implementation, verification, and limitations](../docs/blackmagic-camera-photo-patches.md).

## Runtime limitations

Capture uses a downscaled 12-fps compositor output with a long edge of about 1280 pixels, rather than native-resolution sensor stills. LUT output follows the app's renderer, but saved-image matching against the preview—including custom LUTs—has not been confirmed on a physical device. Continuous video/audio recording, repeated captures, and high-resolution, HDR, and high-frame-rate workflows also require device testing.

This is an experimental candidate, not a claim of device-verified behavior. Install the development prerelease only for testing.
