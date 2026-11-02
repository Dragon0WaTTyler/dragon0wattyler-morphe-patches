> Historical pre-groups.4 document. Current behavior/gates: README.md, FIXES_AND_COVERAGE.md and ANDROID_CHECKLIST.md.

# UX candidate visual/runtime acceptance — pending

Use the supplied screenshot 1 as the before image and screenshot 2 as the style
reference. No after screenshot or device runtime pass is claimed in this package.

- [ ] Back up data; patch stock YouTube 21.30.216 with the usual patches plus
  PocketTube Groups from `patches-2.0.0-groups.3.mpp`. Install as the same package and
  signing key, keeping app data. Do not repatch the installed APK or uninstall it.
- [ ] Open Subscriptions: YouTube logo/toolbar and system status bar stay above
  the group row; no content sits under status icons. Verify both with All and a group.
- [ ] One compact horizontal row, no Subscription groups or Recent from headings;
  32dp chip faces/rounded corners, inverse selected state, clean side spacing. No
  large filled Manage chip. Overflow is visually secondary and reachable by touch.
- [ ] Scroll long group names and 15+ groups, including Arabic/mixed labels. Check
  selected state, icons/count preference, remembered horizontal position, dropdown,
  group order/pins and More for >24 groups. No clipped chip text at normal font scale.
- [ ] Select All: original feed/carousel/native filters/bottom navigation work with
  only the subtle group row added. Disable master: all custom UI disappears, data stays.
- [ ] Select group: 16:9 thumbnail cards with title/channel/views/date below; no
  dashboard heading, status counters or large footer actions. No overlap or detached
  panel background. Record portrait and landscape screenshots on the S23 Ultra.
- [ ] Group overflow: Manage groups, Refresh, Sort by, Date range, Load more while
  channels remain, Retry when failures exist, and Details all work. Technical counts
  and fallback/native-filter explanations appear only in Details/advanced settings.
- [ ] Scroll near loaded-list end: another channel batch loads without a large
  button. Menu Load more is still usable with empty/filtered results. Empty/error
  states are short and tappable Retry works. No repeated loading while off screen.
- [ ] Open video/card menu/channel; normal native player/back/miniplayer/Shorts/live
  behavior remains correct. Native filters continue operating on the original feed.
- [ ] Change default, remember group/All, restart app, create/rename/delete/reorder,
  edit multi-membership, import/merge/replace/export. Prior data/settings remain.
- [ ] Test dark/light/OLED, high font scale, RTL, keyboard/TalkBack, split screen,
  rotation, search, switching tabs, cached pages, and native toolbar collapse/return.
- [ ] Test with Series/history and the normal full patch selection. Check logcat
  for verification/fragment/layout failures; do not equate build success with this pass.
- [ ] If needed, roll back using the preserved V2 `groups.2` bundle/APK with the same
  package/signing identity. Data schema is unchanged. V1 rollback still uses its own file.

Capture after screenshots in the same theme/font/display scale as the supplied
references. Exact between-carousel placement and one unified native feed adapter
are not included; record these separately from visual polish acceptance.
