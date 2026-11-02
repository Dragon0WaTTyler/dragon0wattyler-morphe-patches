# groups.4 bug-fix plan — written before implementation

Baseline: installed/tested groups.3 UX candidate. Work is isolated in
`outputs/PocketTube-Groups-V4/`; V1/V2/V3 remain intact. No dependency upgrade.

## Evidence and causes

- `SubscriptionsPage` adds both lists, assigning 45% height to group results and
  weight 1 to the original View. It never hides the original. This is the direct
  duplication cause. The supplied screenshot shows native toolbar/carousel/filter
  chrome outside the wrapped content argument; no live APK/hierarchy is available.
- `RecentFeedSession` uses a cursor and 12-channel batches, advancing only through
  `more()`. groups.2 exposed footer buttons; groups.3 moved that trigger to overflow
  and end scrolling. Neither automatically inspects the whole group. Later channel
  batches can therefore introduce newer uploads. Loaded-item timestamp sorting is
  already publication-based, but first-batch coverage cannot prove recency across
  all 155 channels. The old 6,000-item global trimming also hides data.
- Current cache is a 15-minute private XML cache; network concurrency is 4. Current
  failures have no automatic retry/backoff. Publication uses OffsetDateTime->epoch
  milliseconds; local time zone is only used for the Today date boundary.
- Upstream PR #4278, head e79dc0c9f959d309ddeaf8f0a71002b4a207676c, fixes guide
  response/account caching on 21.28/21.30 by the 1073830184 check. It does not alter
  toolbar geometry/insets. Investigate/backport only that narrow versioned fix as
  an internal feature dependency, retaining attribution and pinned build versions.
- Our replacement content can lose the native root's inset behavior. Observe the
  native wrapper result without replacing it; correct only measured toolbar overlap
  in that page's own chrome root. Do not alter window flags, decor padding or global
  visibility. Avoid replacing native inset listeners or adding a fixed status height.

## Implementation

1. A dedicated exclusive FrameLayout hosts native OR group content, always full
   height, never both. Selection hides native before binding/loading group content;
   All/off/other routes restore native. Keep the compact selector outside that host.
   Native chrome remains built by the existing outer toolbar wrapper. A small group-
   mode note explicitly scopes native filters to All and directs group filtering to
   overflow; do not pretend native Shorts/live filters control Atom content.
2. Replace manual channel pagination with an automatic, generation-scoped queue:
   all unique valid channel IDs, at most 4 jobs in flight, progressive snapshots,
   per-channel cached windows, up to 3 attempts with exponential backoff, Retry-After/
   shared source cooldown, cancellation/pause/resume, no abandoned remaining batch.
   Track success, pending/in-flight/retry, permanent/exhausted failures, cached/stale
   windows and freshness separately from actual historical completeness.
3. Reuse fresh memory/disk windows across groups; show stale windows while refreshing.
   Keep current results on refresh. Remove global video trimming. Aggregate by stable
   video ID and actual publication time, deterministically preferring newer metadata
   on duplicates. A late/old response must not overwrite a newer publication result.
   Full-window retrieval stays finite Atom windows, never complete upload history.
4. Add a post-wrapper side-effect callback for scoped chrome/inset observation,
   preserving the original return View. Find the toolbar through Morphe's existing
   interface, measure safe inset vs actual toolbar geometry, apply only missing page
   padding, restore on route/master/detach and avoid fighting native collapse/scroll.
5. Retain group storage/CRUD/import/settings/default/remembered/navigation identities.
   Add real Android-view host tests with Robolectric (test-only), pure automatic
   coverage/cache/retry/chronology tests, synthetic DEX callback tests, all previous
   tests and resource checks. Build :patches:buildAndroid last; audit bundle/source
   integrity and V1/V2/V3 preservation. No Android device/target APK test is claimed.
6. Deliver groups.4 bundle/source/diff, bug-root-cause report, coverage limitations,
   build logs, exact test results and focused Android runtime checklist.

## Completion evidence

155-channel test must reach terminal success/failure accounting without `more()`,
including late newest results, duplicates, backoff, permanent errors, cancellation,
overlap caches and stable UTC ordering. Widget tests must exercise actual host
visibility/restoration, rapid selection, attach/recreate and tab isolation, including
untouched fake native chrome siblings. These are JVM/shadow-framework tests, not proof
of matching the target APK or correct on-device layout/playback.
