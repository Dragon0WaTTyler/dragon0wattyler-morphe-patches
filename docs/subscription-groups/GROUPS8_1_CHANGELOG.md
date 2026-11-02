# PocketTube Groups V8.1 candidate

- Detach the fingerprinted Subscriptions channel-carousel container from its original parent while Groups chrome is active, instead of only setting it `GONE`. Restore it at the same index with its original layout parameters when the Subscriptions chrome becomes inactive or detaches.
- Add layout regression coverage proving the matched row no longer occupies vertical space: in the fixture, the feed starts immediately below the toolbar and filter row, and the carousel returns to its original place when Groups exits.
- Capture weak, validated native menu handlers for the current account even before Groups first binds its Subscriptions host. Preserve those bindings across the initial host attach; clear them on later host/activity changes, account changes, incognito, expired commands, or handler rebinding.
- Reuse remains exact-video only: the original YouTube handler is invoked and the native command payload and sender/video checks are still required. RSS-only entries without a matching live native card continue to use the custom fallback.
- Keep “Mark as watched locally” in the existing PocketTube long-press actions; the three-dot click remains free to open the native YouTube menu.

## Candidate

`patches-2.0.0-groups.8.1.mpp` (local build; unsigned).
