# Spotify with Spicetify

The Spotify section in Morphe Manager contains eight experimental patches for
`com.spotify.music` **9.1.80.2221** (version code **145767611**, **ARM64**).
The stock release has a universal APK with `arm64-v8a`, `armeabi-v7a`, `x86`
and `x86_64` libraries. Its ARM64 split bundle contains a base APK, an ABI
split and configuration splits for density and languages. The stock manifest
declares minimum SDK 24 and target SDK 37. Compatibility metadata is scoped to
ARM64. [Stock package variants](https://apkpure.net/spotify-app/com.spotify.music/download/9.1.80.2221)

All eight patches applied to the signed original 9.1.80.2221 build in offline
tests, including a combined pass on the ARM64 split base with Morphe Patcher
2.2.0-dev.7. A newer signed build, **9.1.94.1929** (version code **147600575**),
failed the settings ABI check and is outside the compatibility table. Spotify
**9.2.0.26** was listed publicly by November 4, 2026. Its exact build metadata
and patch compatibility remain unverified. [Newer public listing](https://www.techspot.com/downloads/6773-spotify-for-android.html)

| Patch | What it adds |
| --- | --- |
| Clean sharing links | Removes sharing identifiers and known marketing parameters while preserving timestamps and context. Selected by default. |
| Theme colors | Adds built-in and custom themes, color imports and community themes. Requires Android 11 or later. Some screens and hardcoded colors retain the stock palette. |
| Pin shortcuts on Home | Pins playlists, albums and Liked Songs in the chosen order. The optional pins-only mode hides Spotify's shortcuts. |
| Spicetify extensions | Adds Trash Bin, Hide podcasts, Play a random song and Shuffle+ in the Marketplace. Each extension has its own switch. |
| Hide Premium tab | Hides the navigation tab after restart. |
| Hide Home and Browse ads | Hides supported image and video brand-ad sections. |
| Hide player ad cards | Hides supported brand-ad cards and embedded ad pages in Now Playing. |
| Local files from a server | Streams an HTTPS WebDAV folder or Jellyfin library into Local Files and Your Library. Requires Android 8 or later, byte-range support and Spotify's **Local audio files** setting. Use a normal install for the added manifest components. |

## Installation

1. Add the repository's [development source](https://morphe.software/add-source?github=Dragon0WaTTyler/dragon0wattyler-morphe-patches/tree/dev) in Morphe Manager or load a locally built `.mpp` bundle. Select a bundle whose patch list includes Spotify.
2. Enable **Experimental app versions** for the source. Enable **Expert mode** to select optional patches.
3. Select **Spotify** and provide your own original **9.1.80.2221** APK or supported split archive. Keep the complete split set for an installation built from splits.
4. Apply and install the selected patches. Android requires a matching signing certificate for updates. Replacing stock Spotify with a Manager-signed build may require uninstalling stock first, which removes local app data and downloads. Keep the same signing key for future updates.
5. Open **Settings and privacy**, then **Spicetify**, just above **Log out**. Only installed features appear. Restart when Spotify's Spicetify settings prompt for changes that take effect at startup.

## Runtime limitations

Spotify may open while Home or Search fails to load. Upstream reports that
Spotify's catalog can reject re-signed clients through Play Integrity. The
issue provides a maintainer report of that behavior, and the cause on a
particular phone still requires device diagnostics. APK patching and DEX checks
cover compatibility before launch. Stock, sharing-only and all-patches APKs
installed and reached the signed-out screen on an Android 15 x86_64 emulator.
Android 17, sign-in, Home, Search, Library, playback and feature behavior still
require testing on the target phone. [Upstream runtime issue](https://github.com/spicetify/morphe-patches/issues/5)

The settings ABI failure is a separate patch-time issue. Spicetify v1.2.0 hashed
the byte layout emitted by dexlib2. A newer dexlib2 emitted a different layout
for equivalent classes. The imported fix compares canonical class structure
and code and keeps the version-specific checks before injecting hooks. [Upstream ABI issue](https://github.com/spicetify/morphe-patches/issues/17)

## Local verification

Build with Java 21, the Android SDK and this repository's Gradle wrapper:

```sh
./gradlew :patches:test :extensions:spotify:testDebugUnitTest :patches:generatePatchesList :patches:buildAndroid
./gradlew :patches:validateSpotify -PspotifyApk=/absolute/path/to/stock.apk
```

`validateSpotify` accepts a stock single APK or the extracted base APK for an
offline check. It applies all eight patches by default. Pass `-PspotifyPatch`
with a single patch name to isolate that patch, or `-PspotifyOutput` with a new
absolute path to retain the unsigned output. A patched base from a split set
is a verification artifact and needs the matching splits for installation.

## Changelog

- **2026-11-04** Imported all eight Spicetify Android patches and the post-v1.2.0 settings ABI fix. Bundled the Spotify extension and its native bridge separately from the existing apps. Made theme background replacement atomic when an image already exists.

## Source and license

The Spotify code is derived from [Spicetify Android at 6741682](https://github.com/spicetify/morphe-patches/tree/6741682b28f5c120e7e995537b49705a5d8789ec).
The [GPL-3.0 license](../LICENSE), [upstream NOTICE](../docs/licenses/spicetify-NOTICE)
and [third-party attribution](../docs/licenses/spicetify-third-party-notices.md)
are included with the patch source and bundle. This repository publishes the
patches and their corresponding source. Spotify's app comes from its publisher.
