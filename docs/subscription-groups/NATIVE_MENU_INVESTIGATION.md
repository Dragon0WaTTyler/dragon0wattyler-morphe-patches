# Native menu investigation — evidence and remaining blocker

## Existing call paths

* V6 GroupFeedView → GroupVideoActions → Android PopupMenu. The source is a PopupMenu, not a native YouTube sheet. GroupVideo stores stable IDs, public presentation metadata and publication time. It has no native renderer/command context.
* FeedVideoActionsPatch finds a bound Litho menu button and calls its original performClick. FeedVideoCardsPatch maps native card text/thumbnail metadata to IDs for overlays. That mechanism is not a general arbitrary-ID menu constructor and its text matching is unsuitable as sole authority for invoking a different card's actions.
* FlyoutPatch.FeedFlyoutBufferObjectFingerprint matches the native feed-menu command handler with the native command and Map containing sender_view, endpoint.tag, innertube.bundle and interaction_logger. Native sheet and popup paths consume the menu model. FlyoutUtils records the selected identity for existing Morphe actions.
* AskFlyoutMenuPatch demonstrates ElementsTapHandlerFingerprint/constructor, the command-holder getter, native tap invocation, MenuBottomSheetFragmentFactoryFingerprint, row binding/click/dismissal and the popup path. It modifies an existing native model rather than constructing arbitrary account context from an ID.
* QueueFlyoutActions modifies eligible existing native menu models and uses native row dispatch; NativePlaylistQueuePatch owns actual queue/controller capabilities. These do not supply a complete arbitrary feed menu. No queue or Ask feature is forced on by Groups.

## V6.1 bridge

GroupsNativeMenuPatch is an unnamed internal dependency of Groups. It uses those existing fingerprints, derives the feed-menu command's extension number from the native protobuf initializer, and resolves every required input before its own class mutations. If resolution is unsupported it logs the limitation and leaves this bridge disabled (NativeMenuConfig remains0). The existing shared flyout dependency must itself apply successfully.

The patch adds uniquely named command-byte and tap bridges to the existing Elements tap handler. Constructor callbacks weakly register genuine commands. NativeMenuIdentity accepts only the known Elements→Innertube→resolved native-menu command chain, one MenuRenderer extension66439850, and one consistent valid video ID across the schema's explicit video-targeted endpoints. Unknown, mixed, unrelated nested-menu, malformed or oversized/deep commands are rejected. No ID substitution occurs.

NativeMenuRegistry keeps only a weak original handler, command digest, timestamp and activity/account generations. On click it revalidates the whole command and invokes that original handler with the actual group overflow button as sender. The full native renderer and opaque command context stay owned by that handler. It never rebuilds a native menu, clones another video's menu or sends account requests itself.

At the actual native feed-menu entry, beforeNativeMenu verifies the selected ID again against the real native payload and exact sender. Invalid group-owned requests are stopped; unrelated Home requests pass through. FlyoutUtils receives the verified identity only for this exact command/map/sender pair, so existing Morphe queue hooks avoid stale global IDs or custom-card title heuristics. Models and opaque endpoints are not rewritten. Native rendering and actual action execution remain app-owned.

## Why this is not full arbitrary-card coverage

The public Atom/channel/watch source used for full group coverage does not return the Android native MenuRenderer, native Elements command bindings, account-specific feedback tokens, interaction logger or renderer resources. A bare ID or web JSON cannot authorize or initialize every native action. Native card reuse would need the app's renderer factory, native card model/resources and binding context; the existing hooks expose no safe general factory for this public model. No undocumented get-menu route or guessed command model has been introduced.

This candidate can reuse only commands YouTube has actually bound on the UI thread, after the Groups host is established, while the original handler remains alive and the60s/current-activity/current-account checks pass. Initial Home bindings before host setup, background-thread constructors, unbound RSS videos, recycled/expired commands, changed schemas and incognito retain fallback. Opening a group does not fetch or construct native contexts for every video. Source-only investigation cannot verify the target command initializer/serialization/anchor requirements; the original21.30.216 APK and physical runtime tests remain necessary.

No full native-menu success, signed-in mutations, casting, feedback, entitlement-gated downloads, same-screen icon/scroll/dismissal equivalence or native-card reuse is claimed. Those actions are not separately fabricated. When a captured menu is usable, its original context is retained; whether every action remains appropriate and functional with the custom anchor is a target-device gate. The long-press local action menu remains usable independently.

## V8 follow-up

V8 rechecked the same boundary against the current group-feed model and call paths. The compact selector change does not alter menu dispatch. RSS-only entries still have no native renderer or bound `ElementsTapHandler`, so the native route cannot be extended to them safely from the data currently available. Keep the existing live-command reuse plus explicit custom-menu fallback until a target APK exposes a verified renderer factory/context that is valid for these entries. See [GROUPS8_LIMITATIONS.md](GROUPS8_LIMITATIONS.md).
