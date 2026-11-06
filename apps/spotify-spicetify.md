# Spotify with Spicetify

The current development bundle targets **Spotify 9.1.94.1929**, package
`com.spotify.music`, version code **147600575**, **arm64-v8a**. All eight patches
are ported and validated offline, with native constructor/parcel/color probes on
an Android 15 / API 35 emulator. App startup, authenticated service access and
playback on the target Android 17 phone remain unverified. Targets stay experimental.

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
| Spicetify extensions | Ported; optional | Trash Bin, Random, Shuffle+ and Hide podcasts, with native menus, Home chips and Library filters. Each extension stays off until enabled. |
| Local files from a server | Ported; optional | WebDAV/Jellyfin streaming in Local Files and Library. Requires Android 8+, HTTPS byte-range support, Local audio files enabled, and a normal install with its provider/activity. Unavailable for root mount installs. |

The optional menu and server bridges each have their own DEX and are merged only
when their patch is selected. A sharing-only build contains neither optional
native bridge. The server filter has a separate identity from Spotify's Courses
filter, including equality, stable ID and Parcelable restoration.

These eight patches do **not** add offline music downloads or promise removal of
audio ads. Spotify documents music downloads as a Premium capability; the free
service supports podcast downloads. [Spotify offline listening](https://support.spotify.com/article/listen-offline/)

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

## Connection troubleshooting

Open **Spicetify → Connection troubleshooting**. The page reads local network
capabilities, the installed app version, whether the signer matches Spotify's
publisher, installed features, and the last captured Home failure. A narrowly
matched observer reads the Throwable Spotify already handles and its verified
HTTP status field. It does not change requests, authentication or native error
handling. Cancellation is ignored. Reports omit exception messages, tokens,
accounts, request URLs and response bodies; copying is a user action.

**Pause customizations after restart** preserves preferences and pauses feature
behavior on the next launch, including themes, filters, menus, server scans and
playback hooks. Restart before comparing Home/Search. Existing self overlays are
unregistered when paused. Turning the switch off and restarting restores saved
choices. This comparison still includes settings injection and the modified
signer, so it cannot by itself prove a backend restriction.

Network validation only says Android sees an Internet connection. An observed
HTTP rejection identifies the response, not its underlying cause. A rigorous
comparison would use stock, a rebuilt/re-signed no-op, sharing-only, and active
or paused feature builds with the same input, signer and network while recording
session differences. No such authenticated device comparison was available.

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
The native ART probe validates selected object construction paths, not this new target's startup or authenticated operation. The old 9.1.80 login smoke test is not evidence for 9.1.94.
Successful compilation or APK transformation does not prove a working Spotify
session or solve the upstream backend restriction.

## Local verification

Use Java 21, the Android SDK and the repository's Gradle wrapper:

```sh
./gradlew :patches:test :extensions:spotify:testDebugUnitTest :patches:generatePatchesList :patches:buildAndroid
./gradlew :patches:validateSpotify -PspotifyApk=/absolute/path/to/original-base.apk
```

`validateSpotify` audits the assembled bridge's native method and field linkage,
checks eight distinct registrations, and applies the **eight available patches**
together by default. Use `-PspotifyPatch="Clean sharing links"` for one patch, or
`-PspotifyOutput=/absolute/path/to/new-output.apk` to retain unsigned output.
The server-files patch is unavailable for mount installs. An extracted split base
is suitable for offline transformation checks and needs matching splits to install.

## Validation record (2026-11-06)

- Java 21 build, registration and bundle DEX parsing passed.
- Spotify extension: **602 tests passed**, no failures or skips.
- Patch module: **100 passed, 5 optional private-fixture skips**, no failures.
- All eight patches applied together to the publisher-signed original ARM64 base using the pinned **Patcher 2.0.0** API/toolchain and **2.2.0-dev.7** with full DEX rebuilding.
- Native bridge audit resolved **47 method/field references across 11 classes**; the default sharing-only APK excludes the optional native bridges.
- An isolated **API 35 x86_64 ART** process loaded the patched ARM64 base's DEX without launching Spotify. It verified actual native settings DI/renderer/model construction, fixed-size list copying and idempotence, menu/action/provider constructors, distinct server/Courses IDs and equality, Parcelable round-trip, native album/artist row merging with page metadata preserved, and all **eight** primitive color paths updated and restored.
- The original 198 non-Spotify metadata entries were unchanged; total named application patch entries remain 206. License/attribution remains bundled.
- Earlier compatibility refusal tests cover changed startup/sharing/Home/navigation/ad/protocol schemas. The two newly ported integrations have their own ABI refusal checks.
- No physical device, Android 17 runtime, sign-in, service catalog, audible playback, download or real server streaming test was available. Successful APK transformation does not prove these behaviors.

## Changelog

- **2026-11-06, follow-up** Complete native ports of extensions and server files for 9.1.94.1929. The actual Your Library endpoint remains Rx (`mcj1.m(yob0)`); the earlier Flow candidate was unrelated. Preserve both new page metadata lists. Isolate optional bridges, distinguish the server filter from Courses, fix copying of default/byte-constructed primitive color groups, and add local Home diagnostics plus a restart-only customization pause. Add the reusable [native ART probe](../tools/spotify-art-probe/README.md).

- **2026-11-06** Target 9.1.94.1929 ARM64 using publisher-signed splits. Port settings, sharing, themes, Home pins, navigation and ad hooks. Quarantine extensions and server files explicitly; preserve their source and preferences. Add native bridge linkage validation. Device functionality remains unverified.
- **2026-11-04** Import all eight Spicetify Android patches and the post-v1.2.0 canonical settings ABI fix for 9.1.80.2221. Isolate Spotify from the existing apps. Make theme background replacement atomic.

## Source and license

Derived from [Spicetify Android at 6741682](https://github.com/spicetify/morphe-patches/tree/6741682b28f5c120e7e995537b49705a5d8789ec).
The [GPL-3.0 license](../LICENSE), [upstream NOTICE](../docs/licenses/spicetify-NOTICE)
and [third-party attribution](../docs/licenses/spicetify-third-party-notices.md)
are included with source and bundle. Releases retain corresponding source.
Spotify's app is obtained separately from its publisher.
