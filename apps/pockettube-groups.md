# PocketTube-style Subscription Groups (YouTube)

> **Status:** Development candidate. Published in the current combined Morphe patch source; a separate PocketTube repository and its own release stream have **not** been created yet.

## What it does

Adds optional subscription groups and group management to YouTube's Subscriptions experience. The implementation includes locally managed groups, recent-upload views, and local watched state. This is an independent patch inspired by a PocketTube-style workflow, **not** an official PocketTube extension or synchronization service.

- **App:** YouTube (`com.google.android.youtube`)
- **Patch:** PocketTube Groups
- **Target for this custom patch:** YouTube `21.30.216`; validate against the exact APK and device before relying on it.
- **Source branch:** [`dev`](https://github.com/Dragon0WaTTyler/dragon0wattyler-morphe-patches/tree/dev)
- **Install via existing source:** [Add to Morphe Manager](https://morphe.software/add-source?github=Dragon0WaTTyler/dragon0wattyler-morphe-patches) and enable **Pre-release**.

## PocketTube candidate releases

These are PocketTube-specific candidate assets **inside the existing shared repository's releases**, not separate GitHub Releases yet.

| Candidate | Published in | Download | Notes |
| --- | --- | --- | --- |
| Groups V8.1 | [v1.0.0-dev.4](https://github.com/Dragon0WaTTyler/dragon0wattyler-morphe-patches/releases/tag/v1.0.0-dev.4) | [`patches-2.0.0-groups.8.1.mpp`](https://github.com/Dragon0WaTTyler/dragon0wattyler-morphe-patches/releases/download/v1.0.0-dev.4/patches-2.0.0-groups.8.1.mpp) | Collapsed carousel-gap regression and native-menu handler capture improvements |
| Groups V8 | [v1.0.0-dev.3](https://github.com/Dragon0WaTTyler/dragon0wattyler-morphe-patches/releases/tag/v1.0.0-dev.3) | [`patches-2.0.0-groups.8.mpp`](https://github.com/Dragon0WaTTyler/dragon0wattyler-morphe-patches/releases/download/v1.0.0-dev.3/patches-2.0.0-groups.8.mpp) | Previous candidate |
| Groups V7 | [v1.0.0-dev.2](https://github.com/Dragon0WaTTyler/dragon0wattyler-morphe-patches/releases/tag/v1.0.0-dev.2) | [`patches-2.0.0-groups.7.mpp`](https://github.com/Dragon0WaTTyler/dragon0wattyler-morphe-patches/releases/download/v1.0.0-dev.2/patches-2.0.0-groups.7.mpp) | Earlier candidate |

The existing source also publishes an automatically generated `patches-<version>.mpp` bundle. Check which artifact you select; these are not independently maintained update channels yet.

## Change history, testing and limitations

- [V8.1 changelog](../docs/subscription-groups/GROUPS8_1_CHANGELOG.md)
- [V8.1 verification](../docs/subscription-groups/GROUPS8_1_VERIFICATION.md)
- [V8.1 known limitations](../docs/subscription-groups/GROUPS8_1_LIMITATIONS.md)
- [Samsung device checklist](../docs/subscription-groups/ANDROID_CHECKLIST_V8_1.md)
- [Technical details](../docs/subscription-groups/TECHNICAL_NOTES.md)

**Important:** V8.1 passed documented build and test checks, but a real-device test of the reported YouTube layout/menu issues was still pending at the time of verification. Native YouTube's three-dot menu can only be reused in supported runtime situations; a custom fallback remains for unmatched entries. Full PocketTube live sync is not implemented.

## Future independent release source

Once a dedicated `dragon0wattyler-pockettube` repository is created, the patch source and release automation will be migrated and verified there. **Do not replace the working existing Morphe source until that new source has a tested .mpp and its own valid metadata.** See [repository split plan](../docs/REPOSITORY_SPLIT.md).
