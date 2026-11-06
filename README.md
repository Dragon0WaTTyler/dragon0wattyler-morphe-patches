# Dragon0WaTTyler's Morphe Patches

An independent, actively maintained **multi-app Morphe patch source for Android**. This repository combines patches from a pinned Morphe base with custom work for Readest, YouTube/PocketTube Groups, Blackmagic Camera and Spotify.

This is a community project—not an official release from Morphe, Samsung, Spotify, or any supported app developer. Patches and app versions have different levels of testing; check the notes below before installing.

<a href="https://morphe.software/add-source?github=Dragon0WaTTyler/dragon0wattyler-morphe-patches"><img src=".github/assets/add-to-morphe.svg" alt="Add this repository to Morphe Manager" height="48"></a>

**Quick links:** [Development releases](https://github.com/Dragon0WaTTyler/dragon0wattyler-morphe-patches/releases) · [Current `dev` patch list](https://github.com/Dragon0WaTTyler/dragon0wattyler-morphe-patches/blob/dev/README.md#patches-list) · [Source code (`dev`)](https://github.com/Dragon0WaTTyler/dragon0wattyler-morphe-patches/tree/dev) · [Report an issue](https://github.com/Dragon0WaTTyler/dragon0wattyler-morphe-patches/issues)

## App catalog

All apps use **one shared patch source and release bundle**. The pages below explain features, compatibility, installation and known limitations; they are *not* separate app release channels.

| App | Included features | Target / status | Documentation |
| --- | --- | --- | --- |
| **YouTube / PocketTube Groups** | 110 YouTube patch entries; custom subscription groups and management | PocketTube candidate targets YouTube `21.30.216`; device UI checks pending | [PocketTube details](https://github.com/Dragon0WaTTyler/dragon0wattyler-morphe-patches/blob/dev/apps/pockettube-groups.md) |
| **YouTube Music** | 49 patch entries for playback, interface and customization | Multiple supported versions, including experimental `9.43.51` | [Compatibility and patch list](https://github.com/Dragon0WaTTyler/dragon0wattyler-morphe-patches/blob/dev/README.md#patches-list) |
| **Reddit** | 31 patch entries for layout, ads, media and usability | Multiple supported versions, including experimental `2026.44.0` | [Compatibility and patch list](https://github.com/Dragon0WaTTyler/dragon0wattyler-morphe-patches/blob/dev/README.md#patches-list) |
| **Blackmagic Camera** | Enable Photo Mode; take photos while recording | `3.5.1.0044` — experimental, device tests still needed | [Blackmagic details](https://github.com/Dragon0WaTTyler/dragon0wattyler-morphe-patches/blob/dev/apps/blackmagic-camera.md) |
| **Spotify / Spicetify** | 8 native patch ports: sharing, themes, pins, tabs, ad cards, extensions and server local files | `9.1.94.1929` ARM64 — offline validated; authenticated playback unverified | [Spotify details](https://github.com/Dragon0WaTTyler/dragon0wattyler-morphe-patches/blob/dev/apps/spotify-spicetify.md) |
| **Readest / S Pen Air Actions** | Turn pages with S Pen clicks and directional gestures | `0.12.20` ARM64 — reported working on Galaxy S23 Ultra | [Readest setup and verification](https://github.com/Dragon0WaTTyler/dragon0wattyler-morphe-patches/blob/dev/apps/readest-s-pen-air-actions.md) |

The current development metadata also includes **5 universal patches** (such as Clone app and Change installer source), plus a template/example entry. Supported versions shown above are examples or custom-patch targets, **not** a claim that every patch supports every listed version. For complete per-patch compatibility, consult the [generated development list](https://github.com/Dragon0WaTTyler/dragon0wattyler-morphe-patches/blob/dev/README.md#patches-list).

## Install or update

1. Use **Add to Morphe Manager** above, or add `Dragon0WaTTyler/dragon0wattyler-morphe-patches` as a patch source.
2. Enable **Pre-release** for this source to access development bundles. If a patch targets an experimental app version, enable the relevant experimental-version option in Morphe Manager.
3. Select the app and patch, then supply a compatible **original APK or supported split package**. Follow the app-specific instructions and version requirements.
4. Back up relevant app data before installation. An APK re-signed by Morphe may not install over the developer-signed original; uninstalling the original can erase local data.
5. To update, install the newer patch-source bundle and repatch the compatible app. Avoid mixing independent patch sources for the same app.

## Releases and update channels

- **`dev` — pre-releases:** frequent experiments and new integrations. The [Readest Air Actions patch was introduced in `v1.0.0-dev.11`](https://github.com/Dragon0WaTTyler/dragon0wattyler-morphe-patches/releases/tag/v1.0.0-dev.11).
- **`main` — stable:** stable source branch. A stable bundle has not yet been published here; use the `dev` release stream for the current custom patches.
- **One bundle for many apps:** the `.mpp` files under [Releases](https://github.com/Dragon0WaTTyler/dragon0wattyler-morphe-patches/releases) include the repository's supported patches. A historical PocketTube candidate may also have its own named asset inside a shared release; it is not an independent update channel.

## Available patches

The detailed version/patch list is generated automatically from release metadata. As there is no stable `main` release yet, the latest published catalog is on `dev`.

<!-- PATCHES_START -->

No stable `main` release has been published yet. See the [current development patch list](https://github.com/Dragon0WaTTyler/dragon0wattyler-morphe-patches/blob/dev/README.md#patches-list) and [pre-release downloads](https://github.com/Dragon0WaTTyler/dragon0wattyler-morphe-patches/releases). The release workflow will populate this section when a stable release is made.

<!-- PATCHES_END -->

## Development and verification

The [`dev` branch](https://github.com/Dragon0WaTTyler/dragon0wattyler-morphe-patches/tree/dev) contains the current implementation, tests and app-specific documentation. Development releases are built by GitHub Actions. For local builds, see [the development README](https://github.com/Dragon0WaTTyler/dragon0wattyler-morphe-patches/blob/dev/README.md#build-and-release).

**Testing matters:** a successful patch build is not the same as a working app on real hardware. Readest S Pen controls were reported working on a Galaxy S23 Ultra on **2026-11-06**; that does not establish compatibility with other devices or versions. Blackmagic Camera and Spotify retain the runtime caveats in their documentation.

## License and acknowledgements

Licensed under [GPL-3.0](LICENSE), subject to preserved upstream notices and applicable component licenses. This is a modified, independent Morphe-based project; supported Android apps and their trademarks belong to their respective owners.
