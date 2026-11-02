> Historical pre-groups.4 document. Current behavior/gates: README.md, FIXES_AND_COVERAGE.md and ANDROID_CHECKLIST.md.

# V2 Android runtime checklist — pending

No APK/device was available for these checks. Mark each item only after observing
the patched app, and record APK hash, Manager version, complete patch selection,
package/signing identity, device/Android version and logs. V1's user-reported S23
Ultra test is separate from this V2 checklist.

- [ ] Import V2 source in Manager; patch stock 21.30.216 with the usual patches and
  optional PocketTube Groups. Confirm native browse fingerprints and wrapper match.
- [ ] Install as a same-package/same-signature update. V1 groups migrate once; confirm
  the real 15 groups and 1,253 memberships. Existing Morphe settings remain present.
- [ ] Enable master, open Subscriptions; one labeled selector appears, no duplicate
  headers. Native carousel, content filters, bottom navigation and original feed work.
- [ ] Switch groups rapidly and scroll chips; highlight/count/icon correct. Test
  chips/dropdown, pinned/manual/name/count order, hidden empty/All, compact spacing,
  placement, selector visibility and scroll restoration. Test More with >24 groups.
- [ ] Select All: original feed uses full remaining page space, no group requests.
  Disable integration: all custom headers/actions/panels disappear; saved data remains.
- [ ] Default group OFF/ON remember matrix: default Books -> enter/re-enter; switch
  Music; remember OFF -> Books on entry; ON -> Music after restart; remember All ->
  All after restart. Delete remembered/default group -> documented fallback. Rename
  default group preserves selection. Restore/import while page is visible updates it.
- [ ] A group shows real recent uploads, thumbnails/title/channel/date. More channels
  increments windows loaded; it must not imply older-history pagination. Refresh/retry,
  missing/private channel, no-network, empty group and all-failed states are usable.
- [ ] Newest/oldest/views/date controls affect loaded items; remembered filters survive
  restart. Native Shorts/live/podcast filters affect the original feed independently.
- [ ] Open regular video, available Short and livestream from native or group rows.
  Player, fullscreen/back, miniplayer, background playback, audio and navigation work.
  Group panel does not cover native controls and pauses requests when not visible.
- [ ] Enter native channel page: Add to groups selects/deselects several local groups;
  save immediately updates counts. Actual subscription state never changes. Test the
  alternative route through Manage when the channel-page hook cannot match.
- [ ] Manage: searchable recycled lists, create/rename/icon/pin/reorder/delete
  confirmation; search/browse channels, add UC/canonical URL, remove/copy/move and
  multiassignment. Newly created concurrent groups are not overwritten by assignment.
- [ ] Import real indexed backup and synthetic plain backup. Preview counts/duplicates,
  Merge and confirmed Replace. Cancel picker/discard preview/malformed/truncated/bad
  UTF-8/oversized files/provider failure leave existing data intact. Full V2 export ->
  restore keeps all preferences/IDs/icons/order; membership export imports into V1.
- [ ] Rotate while manager/search/assignment/import preview/picker/feed loading; leave
  app, resume, enter search, navigate away/back, kill/restart process. No stale dialogs,
  lost confirmed data, duplicate headers, spurious feed requests or crashes. Unconfirmed
  import preview is intentionally discarded on process death. Test gesture/predictive
  back and hardware back in manager pages and with keyboard visible.
- [ ] Test dark/light/OLED, large font, portrait/landscape, RTL, split-screen and
  accessibility focus. Header/panel should not overlap controls or consume the page
  in a way that prevents native browsing; verify with the S23 Ultra real dataset.
- [ ] Verify SponsorBlock, stream spoofing, queue, downloads, history/series, native
  navigation, Morphe settings search/import and other selected patches still work.
- [ ] Measure scrolling, memory/network and startup on device before making any
  performance claim. Check logcat for verification exceptions and fragment warnings.
- [ ] Exercise V1 rollback and V2 return with retained app data, package and signing
  identity. Original V1 file unchanged; V2 edits require explicit export/import to V1.

If the browse hook fails during patching, retain the exact patch log and stock APK
hash/path. If patching succeeds but UI fails, record logcat and screenshots. A
successful `.mpp` build or synthetic DEX test does not close these gates.
