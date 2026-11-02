> Historical pre-groups.4 document. Current behavior/gates: README.md, FIXES_AND_COVERAGE.md and ANDROID_CHECKLIST.md.

# V2 UX refinement plan — before implementation

Baseline: working V2 `2.0.0-groups.2`. Candidate: `2.0.0-groups.3`, isolated in
`outputs/PocketTube-Groups-V2-UX/`. Keep V1 and V2 artifacts unchanged.

## Findings

`GroupUi.button` is a generic 48dp-minimum, bold, filled control reused for chips,
Manage, toolbar actions and feed footers. `SubscriptionsPage` adds a separate label
before the group row. `GroupFeedView` adds another heading/toolbar, technical status
text, compact utility rows and two full-width footer buttons. These accumulate
dashboard chrome and make the custom results look detached from the native feed.

The V2 hook wraps the result of the native toolbar wrapper, explaining screenshot
1: all group chrome appears before YouTube's toolbar. Move the same structurally
resolved hook into that wrapper's content argument. Native code then builds the
toolbar/insets around the groups plus browse content, without moving native toolbar
views at runtime. Keep actual receiver/high-register safety and test call ordering.
Litho identifiers exist for native
subscriptions channel/chip bars, but the inspected runtime offers no verified native
view insertion point between them. This iteration will retain the working hook and
unmodified native browse root, instead of inventing parent/child assumptions. The
exact carousel/filter-row placement and native Litho card reuse need APK evidence;
no APK is supplied. Two screenshots were subsequently supplied: screenshot 1 is the
current result, screenshot 2 establishes compact chips and full-width cards. The user's report
establishes V2 functionality on their device, not a visual acceptance test here.

## Implementation

1. Add a feature-scoped chip renderer: 32dp visual height, 8dp corner radius,
   single-line 14sp medium text, modest horizontal padding, theme-aware neutral and
   inverse selected colors, ripple and selected accessibility semantics. Keep 48dp
   touch rows. Do not shrink management controls globally.
2. Remove the everyday section heading and Manage chip. A transparent overflow icon
   at the trailing edge opens management, refresh, sort/date, load/retry and Details.
   In All mode show only the subtle group row; native controls/feed remain intact.
   Channel assignment becomes a compact secondary text action.
3. Remove the feed toolbar, technical status line and large footer buttons. Keep
   channel counts, failures, finite-window/native-filter explanations in Details.
   Show only consumer loading/empty/error states when required. Add a quiet progress
   indicator during active work and load subsequent channel batches at the end of
   user scrolling; retain explicit Load more and Retry in overflow.
4. Restyle results as borderless native Android video cards: 16:9 thumbnails,
   readable title/metadata spacing, no boxed dashboard styling. Native same-package
   playback remains unchanged. These are Android cards styled after YouTube, not
   claimed to be YouTube's internal Litho renderer.
5. Preserve the current browse root, native carousel/filter behavior, selection,
   default/remember settings, import/merge/export/migration/management and storage
   identities. Only correct the existing hook ordering relative to the native
   toolbar wrapper. No backend redesign, new permissions or new features.
6. Run the existing suites, targeted feed interaction regressions and resource
   checks; build `:patches:buildAndroid` last. Audit patch discovery, runtime DEX,
   original V1/V2 hashes and UI-only scope. Package updated source/bundle, UI
   changelog, honest compatibility notes and a focused visual acceptance checklist.

## Verification boundary

Compilation and core tests do not prove visual parity, touch behavior or Android
runtime layout. Both the original feed and group list retain independent scrolling
in the existing safe sibling composition. Precise between-carousel insertion or a
single native feed adapter cannot be certified without target APK/layout evidence.
Do not hide that limitation or present an HTML mockup as an Android screenshot.
