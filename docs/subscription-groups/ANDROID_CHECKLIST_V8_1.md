# PocketTube Groups V8.1 Android checklist

Run on the reported Samsung S23 Ultra with YouTube 21.30.216.

- Open Subscriptions with Groups enabled. Confirm the filter chips begin directly below the native toolbar, with status-bar spacing intact and no blank carousel-height region.
- Confirm the YouTube toolbar, custom content-filter chips, group selector, scrolling, and original feed remain stable; switch tabs, enter search/fullscreen, rotate, then return.
- Open a native Home video whose exact ID is also present in a group. With Groups enabled, enter Subscriptions and open that video's group-card overflow within 60 seconds. Confirm it opens YouTube's real menu. Repeat after reordering and scrolling the group feed.
- Try an RSS-only video that has no live native card. Confirm it deterministically shows the custom fallback and the documented four local actions.
- Confirm tapping the three-dot control prefers the native menu when the exact live capability exists; long-press it and verify “Mark as watched locally” / “Mark as unwatched locally” still works.
- Change account, enter incognito, wait past 60 seconds, and recycle the original card; verify stale capabilities fall back instead of dispatching a different video's command.
- Disable Groups or leave Subscriptions. Confirm the native channel carousel is restored in its original location and Home/other tabs retain their native layout.
