# PocketTube Groups V7 feed history

## Source investigation

The group feed currently retrieves channel uploads from YouTube's public Atom feed at `https://www.youtube.com/feeds/videos.xml?channel_id=…`. The parser accepts up to 100 entries from a single response; the live source normally exposes a small recent window (commonly around 15). This feed has no continuation token or historical cursor, so requesting it again cannot recover older uploads that were never observed.

The repository also contains Morphe's `ChannelUploadsRequest`, which browses the channel uploads playlist through YouTube's InnerTube `browse` route and follows continuations, bounded to 200 pages. It is used by channel search, not by the PocketTube group feed. Reusing it would add an authenticated/client-context-sensitive dependency and substantially change the current isolated public-feed request path. V7 therefore keeps the existing public Atom source and does not claim that it can enumerate an unseen channel history. This source choice avoids inventing a new endpoint or implying that the bounded InnerTube route is a guaranteed complete archive.

| Source | Historical reach | Network and compatibility | V7 decision |
| --- | --- | --- | --- |
| YouTube public Atom feed | Finite recent window; no cursor | Small unauthenticated request; existing V6 path | Keep as refresh source |
| Existing Morphe uploads-playlist browse | Continuations, bounded to 200 pages | Larger request volume; relies on existing InnerTube client and context; only used by channel search | Not coupled into groups in this candidate |
| Local per-channel history | Preserves entries observed by this installation | No extra request; bounded local storage | Use as durable fallback for older observed uploads |

## V7 behavior

- Store each channel snapshot in app-private `filesDir/subscription-groups-feed-v1` as an atomic, versioned JSON record.
- On refresh, union source uploads with locally retained uploads by stable video ID, then sort by the source publication timestamp.
- Keep the first validated publication timestamp for an existing ID while refreshing its title, channel name, thumbnail, and view count.
- Retain at most 500 uploads per channel. The cache remains capped at 2,048 channel files and 64 MiB; the in-memory LRU remains capped at 256 channels.
- Keep V6's finite Atom cache readable during migration. The first successful V7 refresh writes the new JSON format; existing group data and preferences are stored separately and are not migrated or overwritten.
- Record schema version, channel ID, attempted and successful refresh timestamps, oldest retained and newest observed publication timestamps, finite-window coverage status, and an explicitly absent continuation cursor.
- A cache read or write failure does not turn a failed network response into success or discard successful uploads already returned by the source.

## Coverage limit

The feed can retain only uploads that this installation has previously fetched. It cannot reconstruct older videos omitted before V7, deleted/private items absent from the public feed, or uploads outside the source window that were not previously observed. `coverageStatus` describes a finite recent source with locally retained history; it does not mean complete channel history. No target APK/device profiling or Samsung runtime verification was performed by this source-level change.

## V6.1 to V7 changelog

- Persist per-channel observed uploads across refreshes instead of replacing the older window with the latest Atom response.
- Preserve publication timestamps during metadata refresh and deduplicate by video ID.
- Add a versioned cache record and bounded per-channel/global retention.
- Move the group feed cache into persistent app-private storage and read the prior V6 cache as a migration fallback.
- Document the Atom feed's historical boundary and distinguish retained observations from complete history.
