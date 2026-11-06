# Spotify with Spicetify

The current development bundle targets **Spotify 9.1.94.1929**, package
`com.spotify.music`, version code **147600575**, **arm64-v8a**. Six patches are
validated offline. Two remain visible as **unavailable** while their
native integration is being ported. All targets remain experimental pending
Android device testing. This version replaces the earlier 9.1.80.2221 target.

## Package and compatibility

The original ARM64 split package contains a base with 15 DEX files and 18
configuration APKs: the ARM64 ABI split, xhdpi resources and language splits.
Its manifest declares minimum SDK 24 and target SDK 37. The inspected base's
SHA-256 is
`0a06a8ba872fb489ce0af4d0c8ac0f7b5ab53bdcb5655918e5c1cd0741bf096b`.
All 19 APKs passed Android SDK `apksigner verify`, with Spotify certificate
SHA-256
`6505b181933344f93893d586e399b94616183f04349cb572a9e81a3335e28ffd`.
[Original package listing](https://apkpure.net/spotify-app/com.spotify.music/download/9.1.94.1929)

Use an original APK or supported archive from your own legitimate installation.
Keep all matching splits for installation. A universal APK encountered during
investigation had a matching distributor checksum but failed APK v2 integrity
verification; its checksum alone did not establish publisher authenticity. The
port uses the separately verified original split base. APKs are private test
fixtures and are excluded from the repository and releases.

## Features

| Patch | Status on 9.1.94.1929 | Function |
| --- | --- | --- |
| Clean sharing links | Ported; selected by default | Removes sharing identifiers and known marketing parameters while preserving timestamps and context. |
| Theme colors | Ported; optional | Built-in and custom themes, image imports and community themes. Requires Android 11 or later. Some screens retain native colors. |
| Pin shortcuts on Home | Ported; optional | Pins playlists, albums and Liked Songs in order, with a pins-only option. Uses the new native eight-tile grid. |
| Hide Premium tab | Ported; optional | Hides the navigation tab after restart. Does not change the subscription. |
| Hide Home and Browse ads | Ported; optional | Hides supported image and video brand-ad sections. |
| Hide player ad cards | Ported; optional | Hides supported image-brand cards and embedded ad pages in Now Playing. |
| Spicetify extensions | **Unavailable** | Trash Bin, Random, Shuffle+ and Hide podcasts retain their source/settings, but native menus, Home chips and Library filters require a further port. |
| Local files from a server | **Unavailable** | WebDAV/Jellyfin source/settings are preserved; native Library rows and playback integration require a further port after the move to Kotlin Flow. |

Unavailable patches cannot be selected in Manager. Forcing them through a patcher
API fails explicitly before dependencies, resources or legacy hooks run. Their
old native bridge classes are excluded from the merged DEX. Existing preferences
are retained, but unsupported settings do not appear in a newly patched build.
The internal Cosmos/Esperanto bridge is independently validated for the Home pins
picker; it does not enable the unavailable extension collection.

## Installation

1. Add the repository's [development source](https://morphe.software/add-source?github=Dragon0WaTTyler/dragon0wattyler-morphe-patches/tree/dev) in Morphe Manager, or load the current `.mpp` bundle.
2. Enable **Experimental app versions**. Enable **Expert mode** to select optional patches.
3. Select **Spotify 9.1.94.1929 ARM64** and supply an original APK or supported split archive. Keep its complete matching split set.
4. Apply the desired available patches and install. Updates need the same signing certificate. Replacing a stock installation with a Manager-signed build may require uninstalling stock, which removes app data and downloads.
5. Open **Settings and privacy**, then **Spicetify**. Installed features keep their existing settings. Restart when prompted for changes that apply at startup.

## Compatibility changes

The settings factory now freezes a fixed-size list. The new hook copies that list
before adding the native Spicetify row. Startup initialization follows the inherited Application lifecycle. Its renderer, DI provider, Kotlin function
interface, navigation signatures and analytics discriminator are matched to the
actual 9.1.94 bytecode and pinned by canonical ABI snapshots.

Home uses new section/tile models and an eight-item cap. Navigation and both ad
mappers have new classes and branch boundaries. Theme layout and color palettes
are now separate: color hooks follow the actual dark palette selector and
CompositionLocal default; raw color hooks follow the initialized singleton.
The primitive green path also changed. Resource and Compose color roles were
compared against the verified original package. Sharing hooks retain structural
matching. All 70 checked Esperanto protobuf field numbers remain unchanged.

## Runtime limitations

The upstream [runtime issue #5](https://github.com/spicetify/morphe-patches/issues/5)
includes a maintainer report that Spotify's catalog requires Play Integrity tied
to the signing certificate and rejects modified clients. This is evidence of a
backend limitation reported upstream, not a confirmed diagnosis for every device.
No attestation bypass, authentication change or speculative network fix is added.
A previously working client can lose access later; Web working with the same
account does not establish native-client authorization.

The [ABI issue #17](https://github.com/spicetify/morphe-patches/issues/17) is
separate: v1.2.0 compared serialized dexlib2 layouts, which changed across
patcher versions. The imported canonical class/code digest fixes that false
mismatch while keeping strict target-specific checks before injection.

No physical Android device was available for this port; offline validation was
requested. Android 17 / API 37 installation, startup, sign-in, Home, Search,
Library, playback, settings navigation and feature behavior remain **untested**.
The old 9.1.80 emulator login smoke test does not validate this new target.
Successful compilation or APK transformation does not prove a working Spotify
session or solve the upstream backend restriction.

## Local verification

Use Java 21, the Android SDK and the repository's Gradle wrapper:

```sh
./gradlew :patches:test :extensions:spotify:testDebugUnitTest :patches:generatePatchesList :patches:buildAndroid
./gradlew :patches:validateSpotify -PspotifyApk=/absolute/path/to/original-base.apk
```

`validateSpotify` audits the assembled bridge's native method and field linkage,
checks eight distinct registrations, and applies the **six available patches**
together by default. Use `-PspotifyPatch="Clean sharing links"` for one patch, or
`-PspotifyOutput=/absolute/path/to/new-output.apk` to retain unsigned output.
Forcing either unavailable patch is an intentional error. An extracted split base
is suitable for offline transformation checks and needs matching splits to install.

## Validation record (2026-11-06)

- Java 21 build, patch registration and Android bundle DEX parsing passed.
- Spotify extension: **597 tests passed**, no failures or skips.
- Patch module: **98 passed, 5 skipped** because optional private fixtures were absent; no failures.
- Six available patches applied individually and together to the verified original ARM64 base with **Morphe Patcher 2.2.0-dev.7**, including full DEX rebuilding. A combined run also passed with the repository's pinned **2.0.0** API/toolchain.
- The bridge audit resolved **32 native method/field references across five assembled classes**, including access flags and inherited Application startup.
- Ten refusal tests passed: changed startup, sharing builder/response, Home, navigation, both ad schemas, Esperanto protocol, and forced selection of each unavailable patch. Failed runs produced no APK.
- All **198 existing non-Spotify patch metadata entries** matched the previous release; total named application patch entries remained **206**.
- The combined output manifest matched the original after excluding XML source line numbers; package/version and SDK values were preserved.
- Physical device, Android 17 runtime, authenticated catalog and playback checks remain untested.

## Changelog

- **2026-11-06** Target 9.1.94.1929 ARM64 using publisher-signed splits. Port settings, sharing, themes, Home pins, navigation and ad hooks. Quarantine extensions and server files explicitly; preserve their source and preferences. Add native bridge linkage validation. Device functionality remains unverified.
- **2026-11-04** Import all eight Spicetify Android patches and the post-v1.2.0 canonical settings ABI fix for 9.1.80.2221. Isolate Spotify from the existing apps. Make theme background replacement atomic.

## Source and license

Derived from [Spicetify Android at 6741682](https://github.com/spicetify/morphe-patches/tree/6741682b28f5c120e7e995537b49705a5d8789ec).
The [GPL-3.0 license](../LICENSE), [upstream NOTICE](../docs/licenses/spicetify-NOTICE)
and [third-party attribution](../docs/licenses/spicetify-third-party-notices.md)
are included with source and bundle. Releases retain corresponding source.
Spotify's app is obtained separately from its publisher.
