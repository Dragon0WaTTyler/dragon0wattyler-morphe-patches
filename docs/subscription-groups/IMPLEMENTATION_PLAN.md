> Historical pre-groups.4 document. Current behavior/gates: README.md, FIXES_AND_COVERAGE.md and ANDROID_CHECKLIST.md.

# Subscription Groups: investigation and implementation plan

Written 2026-11-01, before feature implementation. This is an independent, modified
Morphe patch bundle, not an official Morphe or PocketTube release.

## Source baseline and findings

- `MorpheApp/morphe-patches`: `0bb704a9a4edb5324f6e607f3fc5a531713c607c`,
  main, version 2.0.0. Latest published release checked: v2.0.0 (2026-10-30).
- Template: `a826ea8066fad67d5000b23ab89a4634c2a4f621`.
- PocketTube public support/translations repo: `52c83abcca68fd918eccb5a8461a037177c69106`.
- The installed codebase-memory skill was read; its graph tools are unavailable in
  this session. Findings below use direct source reads and scoped `rg` searches.

### Morphe architecture

Patches use Kotlin `bytecodePatch`/`resourcePatch`, explicit dependencies and
fingerprints over DEX methods. The shared extension patch injects the Java runtime
from `extensions/youtube`, `shared-youtube`, and `shared`. The settings patch already
depends on this machinery, adds strings using `addResourcesPatch`, and serializes
custom preferences through `PreferenceScreen` and `NonInteractivePreference`.
`Settings.java` registers `BooleanSetting` instances with the existing preference,
search and import/export system. R8 preserves `app.morphe.**` extension classes.

`SettingsPatch.kt` repurposes the existing GoogleApiActivity, removes its conflicting
methods and calls its superclass; this preserves Android Fragment activity-result
delivery. Adding an Activity to the manifest is explicitly discouraged there for root
installs. `NavigationBarHookPatch.kt` supports native tab/browse hooks, but depends on
changing app internals. The MVP needs none of those new hooks: a custom Preference
opens a native DialogFragment inside the existing settings Activity. Existing
`OpenChannelOfLiveAvatarPatch.java` uses ACTION_VIEW channel links restricted to the
current runtime package; reuse that public navigation pattern, including clones.

### Backup and synchronization evidence

PocketTube's README explicitly says the product is not fully open source. Its public
README and website provide no versioned JSON schema or developer synchronization
API contract. Repository translations document file backup and Drive sync UI. The
maintainer describes export/import and `ysc_settings` in
https://github.com/NabokD/pockettube/issues/184#issuecomment-4749301306 . This is not
a synchronization API.

Public interoperability evidence:

- https://github.com/yuliskov/MediaServiceCore/blob/master/youtubeapi/src/main/java/com/liskovsoft/youtubeapi/channelgroups/importing/pockettube/PocketTubeService.kt
- https://github.com/yuliskov/MediaServiceCore/blob/master/youtubeapi/src/test/resources/channelgroups/pockettube.json

The implementation and fixture establish `ysc_collection` as an object whose keys
identify groups and whose corresponding top-level arrays contain channel IDs. Other
top-level `ysc_*` metadata is outside this MVP. A legacy group-name -> channel-ID-array
shape is also described in https://github.com/TeamNewPipe/NewPipe/issues/6575 .
These are observed/publicly documented interoperability shapes, not an official
versioned PocketTube schema. Implement them independently; do not copy extension code
or redistribute the large public fixture, which includes unrelated user metadata.
Use new synthetic test fixtures. Unknown shapes fail with an explanatory error.

No documented legitimate public PocketTube synchronization API was found in the
bounded investigation of the public repo, website and issue search. This does not
prove none exists privately. There will be no OAuth, private Drive access, guessed
endpoints, remote requests, or synchronization claims in this MVP.

### Compatibility and build requirements

`youtube/shared/Constants.kt` requires an APK and declares:

| YouTube | Android minimum | Status |
| --- | --- | --- |
| 20.26.46 | 8 / API 26 | supported target |
| 20.51.39, 21.23.492 | 9 / API 28 | supported targets |
| 21.28.208, 21.30.216 | 10 / API 29 | supported targets |
| 21.42.241, 21.43.523 | 10 / API 29 | experimental targets |

Reuse this compatibility declaration; it is metadata, not proof of device testing.
The source pins patcher 2.0.0, patches plugin 2.1.0-dev.1, patches library
2.1.0-dev.2, AGP 9.1.0, Gradle 9.8.2. The template currently uses plugin/patcher
2.1.0 and is therefore not a drop-in substitute for this complete upstream bundle.
CI uses JDK 21, Java target/toolchain 17; shared runtime compileSdk is 36, minSdk 26.
GitHub Packages needs working package credentials as documented by the template.
An initial unmodified `:patches:buildAndroid` attempt failed to resolve the pinned
plugin, with and without the available GitHub authentication. Investigate build
resolution further without silently changing dependency versions.

## Smallest vertical slice

1. Add an optional `PocketTube Groups` patch depending on existing settings and
   shared extension infrastructure, using the shared YouTube compatibility list.
2. Add a Morphe BooleanSetting (default disabled), a switch and a custom entry
   under General > PocketTube Groups. The entry is disabled when the feature is off.
3. Implement an isolated immutable group model and backup parser. Preserve exact
   Unicode/case/space-sensitive group names and channel IDs; deduplicate only within
   each group; keep membership in multiple groups. Validate every indexed group.
4. Add a native DialogFragment containing a ListView, an Import button, back/group
   navigation and empty/error states. Use ACTION_OPEN_DOCUMENT with CATEGORY_OPENABLE
   and content streams, no filesystem/storage permission or persistent URI grant.
5. Read and parse bounded UTF-8 input off the main thread; preview and explicitly
   confirm replacement of the local collection. A canceled or corrupt import never
   replaces saved data. Store a normalized versioned snapshot atomically in app-private
   storage. Keep it separate from Morphe settings export and YouTube subscriptions.
6. Open a channel using a validated channel ID, ACTION_VIEW and `setPackage` with
   the current package. Use no playback, feed-filter or existing navigation-tab hooks.
7. Add unit tests for both observed formats, exact names, empty groups, duplicate and
   multiple membership, persistence round trips, invalid shapes/IDs, truncated input,
   malformed UTF-8, limits, and failed import preserving existing storage.
8. Run focused tests, documented CI tasks (`:patches:test`, extension unit tests),
   string checks and `:patches:buildAndroid`. Bundle build must follow JVM tasks because
   buildAndroid adds DEX to the JAR in place. Record exact results and artifact hashes.
9. Retain LICENSE/NOTICE and original copyright notices. Mark bundle metadata and
   README as an independent derivative, with modification date and source instructions.

## Device acceptance gate

On a supported stock APK, apply this patch with the usual Morphe selection; verify
settings/search, enable/disable without restart, picker cancel/error/success, Unicode
groups, overlapping channels, restart persistence, rotation while picking/importing,
same-app channel navigation and normal video playback, miniplayer, SponsorBlock,
stream spoofing and the existing navigation layout. Repeat on a package-renamed build
and root install. No APK or connected device is initially available; report this
gate as unverified unless that changes. Live filtered feeds and automatic sync remain
separate future milestones.
