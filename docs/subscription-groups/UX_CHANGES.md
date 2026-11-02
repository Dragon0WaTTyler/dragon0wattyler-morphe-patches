> Historical pre-groups.4 document. Current behavior/gates: README.md, FIXES_AND_COVERAGE.md and ANDROID_CHECKLIST.md.

# V2 UX candidate — screenshot-driven changes

Version **2.0.0-groups.3**, independent derivative, modified 2026-11-01. This is a
new candidate in `outputs/PocketTube-Groups-V2-UX/`. Original V1 and working V2
bundles/source archives remain unchanged for rollback.

Screenshot 1 is the user's working V2 result. Screenshot 2 is the visual direction:
compact rectangular chips with rounded corners, an inverse selected state,
YouTube toolbar above content, and full-width video cards with metadata below.
The screenshots are reference evidence; they are not screenshots of this candidate.

## UI changes

| Before (screenshot 1 / V2) | New candidate |
| --- | --- |
| Custom content above YouTube logo/toolbar, heading near status bar | Hook wraps the native toolbar method's content argument; YouTube builds its toolbar/insets outside the group UI |
| Separate Subscription groups section heading | Removed from everyday browsing; row keeps an accessibility label |
| 48dp filled, bold utility buttons used as chips | 32dp visual chip face inside 48dp touch row, 8dp corners, 14sp medium text, restrained spacing, neutral/inverse colors |
| Manage looks like a group | Transparent trailing overflow icon; Manage groups is its first menu item |
| A second Recent from heading and Filters/refresh toolbar | Removed; refresh, sort and date range live in the row's overflow |
| Recent window / loaded / unavailable / native-filter diagnostics always visible | Details dialog only, including finite-window and independent-filter explanations |
| Large More channels and Retry unavailable channels footer | Removed; Load more and Retry remain in overflow; user scrolling near the end advances additional channel batches |
| Small thumbnails next to dense utility text | Borderless 16:9 full-width thumbnails, title and channel/views/date underneath, channel initial circle and secondary video overflow |
| Separate dashboard chrome around results | No heading, border, card-panel background or action footer; shared YouTube/OLED palette |

Initial loading uses a quiet 2dp progress indicator and a short Loading videos state.
Empty and all-failed states use brief consumer messages; the error state is tappable
to retry. Partially unavailable channels do not add banners over successful results.
Details remains accessible through Group options. The user can always choose All.
The chip face is compact while its touch target remains 48dp; selected state and
button semantics are exposed to accessibility services. Horizontal labels are
single-line, with a width cap and full accessible names. Relative margins/padding
support RTL; actual RTL/large-font behavior still requires device verification.

## Functionality retained

No parser, storage, migration, data model, preference keys/defaults, feed source,
cache, group selection/default/remembered rules, management or import/export logic
is changed. Existing icon/count/order/style preferences remain honored. To obtain
a names-only row like the reference, turn off Show channel counts and optionally
Show group icons; this candidate does not overwrite your current choices.

Manage groups and backups stays available in Morphe settings and from overflow.
Sorting/date/refresh/load/retry actions are retained in the menu. Native same-package
video and channel navigation is retained. All and disabled integration retain the
original browse content. Native carousel/filter chips and bottom navigation are not
dissected, removed or replaced. Channel-page assignment becomes an unfilled secondary
action. The independent patch/source/runtime identity and signing requirements remain.

## Toolbar ordering correction

V2 injected its bridge after the final native View-to-View toolbar wrapper. Its
LinearLayout therefore sat outside that wrapper and pushed the logo/toolbar below
the custom feed, as screenshot 1 shows. This candidate uses the same structural
anchor, but wraps the original content argument before the native call. It preserves
the actual receiver, high registers, native return register and original call order.
Injected `patch_` bridge calls (including Series history) are excluded as anchors.
Synthetic DEX tests cover the new ordering and late Series/static wrappers. Actual
APK matching and Android insets/layout remain pending; source tests cannot prove them.

## Remaining boundaries

This is a visual refinement of the working Android-view integration. The video cards
use native Android views styled after screenshot 2; they are not YouTube's internal
Litho card renderer. No undocumented native card factory has been invented.

The original browse root remains intact. The compact group row sits inside the
native toolbar wrapper, above that root by default; the existing placement option
can still move it below. Exact insertion between the avatar carousel and native
filter row is not implemented. In group mode, group results and original feed
retain independent scrolling in the existing sibling layout. Combining both into
one native feed adapter would require a separate verified hook, beyond this polish
iteration. The native filters apply to the original feed independently; Details
explains this instead of displaying technical text during everyday browsing.

Recent uploads are still finite public channel-feed windows, not complete history
or PocketTube synchronization. Automatic batching changes presentation of retrieval,
not its scope. No unavailable classification filters, duration labels or real avatars
are fabricated. Channel circles use initials, and thumbnails come from the same feed.

No target APK/device is available here. The user's report confirms working prior V2,
not this candidate. Toolbar placement, portrait/landscape cards, touch interactions,
accessibility, native filter coexistence, playback and final visual parity are pending
device checks. See VERIFICATION.md and VISUAL_CHECKLIST.md.
