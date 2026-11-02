> Historical pre-groups.4 document. Current behavior/gates: README.md, FIXES_AND_COVERAGE.md and ANDROID_CHECKLIST.md.

# PocketTube Groups V2 implementation plan

Baseline: the existing V1 source and patch bundle in `outputs/`, not a new app.
All V2 work is isolated in `outputs/PocketTube-Groups-V2/`; V1 stays intact.
Target first: YouTube 21.30.216, including the runtime-renamed
`app.morphe.android.youtube` installation. APK/device verification is pending;
the user explicitly requested continuing without waiting for an APK.

## Investigation findings

- The required codebase-memory skill was applied in V1. Its graph tools remain
  unavailable (tool inventory checked); structural findings use direct source reads.
- `navigation/NavigationBarHookPatch.kt` already identifies browse fragments by a
  descriptive diagnostic string and resolves a native endpoint-to-browse-ID method
  structurally. It exposes browse-ID, search and navigation listeners. Reuse that
  resolution to add optional view/page callbacks; do not hardcode obfuscated names.
- `NavigationBar` has listeners for tab transitions. `FEsubscriptions` is the
  native browse ID; channel pages use their UC ID. Cached browse pages are covered
  by the native endpoint setter hook. Search has a separate fragment/hook.
- The existing RecyclerView tree hook is for flyout menus, not a subscription
  header. There is no verified adapter insertion point between the native carousel
  and filter chips. Both can be server-rendered Litho. V2 will insert a distinctly
  labeled native group row above the existing browse root (or below, by preference),
  preserving the original root/carousel/filters as one unmodified child. It will
  not reparent individual Litho controls or replace the bottom navigation.
- `AiSListFilter` explicitly documents that UC IDs in arbitrary component buffers
  can belong to unrelated URLs. Raw-ID buffer filtering is therefore unsuitable
  as reliable membership attribution. It would also only filter fetched items.
- The documented YouTube Data API supports channels' upload playlists and paginated
  `playlistItems.list`, but requires developer credentials/quota. There is no
  documented native arbitrary-group subscriptions parameter in the inspected hooks.
  References: https://developers.google.com/youtube/v3/docs/ and
  https://developers.google.com/youtube/v3/docs/playlistItems/list .
- The real public YouTube Atom endpoint
  `https://www.youtube.com/feeds/videos.xml?channel_id=UCXuqSBlHAE6Xw-yeJA0Tunw`
  returned HTTP 200 with real upload entries during investigation. This is an
  observed YouTube-owned feed, not a PocketTube API or a full-history API contract.
  It exposes a bounded recent window and no historical page token. It cannot
  reliably classify Shorts/live/podcasts, duration or watched status.

## Planned vertical implementation

1. Keep the working V1 backup parser and V1 store codec/tests. Add an immutable V2
   library with stable group IDs, exact names, ordered membership sets, icons,
   pinned state and typed preference values. Migrate the V1 file once to a new
   atomic V2 file; keep V1 untouched for rollback. Reject unsupported/corrupt V2
   state rather than silently resetting it.
2. Add optional shared browse-view/endpoint hook helpers. Bind a native selector
   only to the exact Subscriptions browse page; a small channel assignment action
   may bind to a valid channel browse ID. Match fragment identity, use weak view
   references, guard attachment/state, and prevent duplicate wrappers.
3. Implement All/default/remembered group state with stable IDs. Default is All for
   migrated users. On tab entry: remembered valid -> configured valid -> All;
   remember OFF always uses configured valid -> All. Deletion/import invalidates
   missing IDs immediately. Publish immutable state changes on the main thread.
4. Render horizontal chips or a dropdown with icons/counts, selected styling,
   configurable spacing/order/placement/empty/All visibility and Manage. Palette
   comes from Morphe ThemeUtils so OLED black is respected. All/disabled integration
   restores the original native feed; no synthetic tab replaces Subscriptions.
5. Provide a clearly labeled **Recent group uploads** panel in that same browse
   screen, above the original native feed. Fetch channels progressively in bounded
   batches with limited concurrency, cache responses locally, deduplicate video IDs,
   render thumbnail/title/channel/publication and launch the existing native player.
   Provide refresh, load-more-channel batches, loading/empty/error/retry states.
   Native carousel and native filters stay in the original feed below the panel.
   A visible explanation distinguishes the group snapshot from that original feed.
   Sorting/date controls apply only to known RSS fields and loaded results. No
   unsupported Shorts/live/watched/duration switches will be shown. This is an
   explicit finite-window fallback, not complete group history or complete feed
   pagination. Keep a FeedSource interface for a credentialed future provider.
6. Replace the V1 text-list AlertDialog with a full-window native list-based group
   management surface in the host Activity: toolbar/back, search, group icons/counts,
   create/rename/delete (confirmation), pin/icon/reorder, searchable channel rows,
   add/remove/copy/move/multi-group assignment. Use small dialogs only for individual
   text edits and confirmations. No separate Activity/application.
7. Expand the Morphe settings screen into selection, appearance, feed and data
   sections. A custom Preference uses the shared typed library preferences so
   dynamic default-group choices and the remember toggle are transactionally
   persisted and immediately reflected in active views. Preserve the V1 master
   BooleanSetting key and existing settings entry identity.
8. Keep SAF import; add transactional replace/merge with preview and duplicate
   counts, full local export/restore retaining IDs/icons/preferences and a
   PocketTube-compatible membership representation. Failures/cancel preserve data.
9. Add tests for all requested core state transitions, CRUD/order/membership,
   migration/preferences, merge/replace, corruption and a 15-group/1,253-membership
   synthetic workload; feed parsing/deduplication/batching/cancellation and hook
   instruction construction. Re-run V1 and upstream tests, check strings, build
   `:patches:buildAndroid` last, and audit patch discovery/DEX contents.
10. Package V2 sources/bundle, compatibility report, exact installation/rollback
    steps, runtime checklist, known limitations and changelog. Mark absent APK/device
    checks as unverified; do not equate compile/test success with Android correctness.

## Gates

Hook refinement after reading upstream `video/series/HistoryPatch.kt`: reuse its
final native one-View-to-View browse wrapper anchor. Capture the actual native
receiver before that call, because R8 may reuse p0 or overwrite the receiver with
the returned View. Notify page changes through a small no-argument bridge before
the existing route reader can reuse the setter's p0 as a scratch register. Synthetic
DEX tests cover high registers, receiver reuse, static decoys and fail-closed matching.
The V2 lossless backup is an explicit envelope rather than adding reserved keys
beside group names; this preserves even groups named `ysc_collection`. A separate
membership-only export remains compatible with the V1/PocketTube plain group map.

No claim of complete group feeds, automatic PocketTube sync, live native-header
placement verification, or preserved on-device playback without relevant evidence.
Automatic sync remains out of scope for V2; no private Drive storage or proprietary
extension code is accessed. Unsupported classifications are deferred with precise
data limitations. If the view hook cannot be verified without the APK, ship its
source/bundle with that gate explicitly pending rather than claim a runtime pass.
