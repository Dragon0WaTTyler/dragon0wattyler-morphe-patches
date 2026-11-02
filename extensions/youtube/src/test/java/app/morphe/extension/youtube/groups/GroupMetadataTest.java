/* SPDX-License-Identifier: GPL-3.0-only
 * Independent Subscription Groups groups.5, 2026-11-01. See LICENSE and NOTICE.
 */
package app.morphe.extension.youtube.groups;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import java.io.IOException;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import static org.junit.Assert.*;

public class GroupMetadataTest {
    static final String CHANNEL = "UC0000000000000000000001", ID = "00000000001";
    static final String AVATAR = "https://yt3.googleusercontent.com/profile=s160-c";
    static JSONObject page(String tab, JSONArray entries) throws Exception {
        JSONObject identity = new JSONObject().put("externalId", CHANNEL).put("avatar", new JSONObject().put("thumbnails", new JSONArray().put(new JSONObject().put("url", AVATAR))));
        JSONObject endpoint = new JSONObject().put("browseEndpoint", new JSONObject().put("browseId", CHANNEL).put("params", "returned-params"))
                .put("commandMetadata", new JSONObject().put("webCommandMetadata", new JSONObject().put("url", "/@example/" + tab)));
        JSONObject renderer = new JSONObject().put("selected", true).put("endpoint", endpoint).put("content", new JSONObject().put("contents", entries));
        return new JSONObject().put("metadata", new JSONObject().put("channelMetadataRenderer", identity))
                .put("contents", new JSONObject().put("twoColumnBrowseResultsRenderer", new JSONObject().put("tabs", new JSONArray().put(new JSONObject().put("tabRenderer", renderer)))));
    }
    static JSONObject video(String id, String duration) throws Exception {
        return new JSONObject().put("videoRenderer", new JSONObject().put("videoId", id).put("lengthText", new JSONObject().put("simpleText", duration)));
    }
    static ChannelPageMetadata parse(String tab, JSONObject item) throws Exception {
        return ChannelPageMetadata.parse(page(tab, new JSONArray().put(item)).toString(), CHANNEL, tab, System.currentTimeMillis());
    }
    static GroupVideo model(long published, VideoMetadata m) { return new GroupVideo(ID, CHANNEL, "Title", "Channel", "", published, 1, AVATAR, m); }
    @Test public void channelIdentityAvatarAndExactDurationResolveTogether() throws Exception {
        ChannelPageMetadata p = parse("videos", video(ID, "18:45")); assertEquals(AVATAR, p.avatar); assertEquals(1125, p.videos.get(ID).durationSeconds);
        assertEquals(VideoMetadata.Type.VIDEO, p.videos.get(ID).type); assertEquals("returned-params", p.tabs.get("videos"));
    }
    @Test public void shortDurationDoesNotMakeRegularVideoAShort() throws Exception {
        assertEquals(VideoMetadata.Type.VIDEO, parse("videos", video(ID, "0:20")).videos.get(ID).type);
    }
    @Test public void explicitShortsEndpointClassifiesWithoutDurationGuess() throws Exception {
        JSONObject shortItem = new JSONObject().put("shortsLockupViewModel", new JSONObject().put("onTap", new JSONObject().put("innertubeCommand", new JSONObject().put("reelWatchEndpoint", new JSONObject().put("videoId", ID)))));
        VideoMetadata m = parse("shorts", shortItem).videos.get(ID); assertEquals(VideoMetadata.Type.SHORT, m.type); assertEquals(-1, m.durationSeconds); assertEquals("", m.badge());
    }
    @Test public void streamTabClassifiesArchivedLiveAndRetainsDuration() throws Exception {
        VideoMetadata m = parse("streams", video(ID, "1:03:22")).videos.get(ID);
        assertEquals(VideoMetadata.Type.STREAM, m.type); assertEquals(VideoMetadata.Live.ENDED, m.live); assertEquals("1:03:22", m.badge());
    }
    @Test public void currentLiveBadgeOverridesDurationAndVideosClassification() throws Exception {
        JSONObject item = video(ID, "8:12"); item.getJSONObject("videoRenderer").put("thumbnailOverlays", new JSONArray().put(new JSONObject().put("thumbnailOverlayTimeStatusRenderer", new JSONObject().put("style", "LIVE"))));
        VideoMetadata m = parse("videos", item).videos.get(ID); assertEquals(VideoMetadata.Live.LIVE, m.live); assertEquals(-1, m.durationSeconds); assertEquals("LIVE", m.badge());
    }
    @Test public void upcomingIsNeverShownAsZeroOrCompletedDuration() throws Exception {
        JSONObject item = video(ID, "0:00"); item.getJSONObject("videoRenderer").put("upcomingEventData", new JSONObject().put("startTime", "1800000000"));
        VideoMetadata m = parse("videos", item).videos.get(ID); assertEquals("UPCOMING", m.badge()); assertEquals(-1, m.durationSeconds);
    }
    @Test public void modernLockupUsesOnlyItsOwnDurationOverlay() throws Exception {
        JSONObject value = new JSONObject().put("contentId", ID).put("contentType", "LOCKUP_CONTENT_TYPE_VIDEO")
                .put("contentImage", new JSONObject().put("thumbnailViewModel", new JSONObject().put("overlays", new JSONArray().put(new JSONObject().put("thumbnailBottomOverlayViewModel", new JSONObject().put("badges", new JSONArray().put(new JSONObject().put("thumbnailBadgeViewModel", new JSONObject().put("text", "4:32")))))))))
                .put("metadata", new JSONObject().put("unrelated", video("00000000002", "9:01")));
        ChannelPageMetadata p = parse("videos", new JSONObject().put("lockupViewModel", value)); assertEquals(272, p.videos.get(ID).durationSeconds); assertEquals(1, p.videos.size());
    }
    @Test public void podcastRequiresExplicitEpisodeMetadataAndNeverTitleGuess() throws Exception {
        JSONObject item = video(ID, "30:00"); item.getJSONObject("videoRenderer").put("title", new JSONObject().put("simpleText", "The podcast live shorts show"));
        assertFalse(parse("videos", item).videos.get(ID).podcast);
        item.getJSONObject("videoRenderer").put("isPodcast", true); assertTrue(parse("videos", item).videos.get(ID).podcast);
    }
    @Test public void podcastPlaylistIsNotMisclassifiedAsAnEpisode() throws Exception {
        JSONObject playlist = new JSONObject().put("lockupViewModel", new JSONObject().put("contentId", ID).put("contentType", "LOCKUP_CONTENT_TYPE_PODCAST"));
        assertTrue(parse("shows", playlist).videos.isEmpty());
    }
    @Test public void explicitPodcastPlaylistResolvesActualEpisodeMembership() throws Exception {
        String playlist = "PL00000000000000000001";
        JSONObject card = new JSONObject().put("lockupViewModel", new JSONObject().put("contentId", playlist).put("contentType", "LOCKUP_CONTENT_TYPE_PODCAST"));
        ChannelPageMetadata channel = parse("podcasts", card); assertEquals(List.of(playlist), channel.podcastPlaylists); assertTrue(channel.videos.isEmpty());
        JSONObject response = new JSONObject().put("microformat", new JSONObject().put("microformatDataRenderer", new JSONObject().put("urlCanonical", "https://www.youtube.com/playlist?list=" + playlist)))
                .put("contents", new JSONObject().put("items", new JSONArray().put(new JSONObject().put("playlistVideoRenderer", new JSONObject().put("videoId", ID).put("lengthText", new JSONObject().put("simpleText", "18:45"))))));
        VideoMetadata m = ChannelPageMetadata.podcastEpisodes(response.toString(), playlist).get(ID); assertTrue(m.podcast); assertEquals(1125, m.durationSeconds);
        try { ChannelPageMetadata.podcastEpisodes(response.toString(), "PL00000000000000000002"); fail("Wrong podcast playlist accepted"); } catch (IOException expected) { }
    }
    @Test public void unknownMetadataRemainsOnlyInAllAndDateFilter() {
        GroupVideo video = model(System.currentTimeMillis(), VideoMetadata.UNKNOWN);
        for (GroupContentFilter f : List.of(GroupContentFilter.VIDEOS, GroupContentFilter.SHORTS, GroupContentFilter.LIVE, GroupContentFilter.PODCASTS)) assertFalse(f.matches(video, System.currentTimeMillis(), ZoneId.of("UTC")));
        assertTrue(GroupContentFilter.ALL.matches(video, 0, ZoneId.of("UTC")));
    }
    @Test public void todayUsesLocalMidnightAcrossOffsetAndDst() {
        ZoneId zone = ZoneId.of("America/New_York"); long now = Instant.parse("2026-11-01T08:00:00Z").toEpochMilli();
        assertFalse(GroupContentFilter.TODAY.matches(model(Instant.parse("2026-11-01T03:59:59Z").toEpochMilli(), VideoMetadata.UNKNOWN), now, zone));
        assertTrue(GroupContentFilter.TODAY.matches(model(Instant.parse("2026-11-01T04:00:00Z").toEpochMilli(), VideoMetadata.UNKNOWN), now, zone));
        assertFalse(GroupContentFilter.TODAY.matches(model(now + 1, VideoMetadata.UNKNOWN), now, zone));
    }
    @Test public void typeFilteringPreservesChronologyAndVideoIdentity() {
        long now = System.currentTimeMillis(); GroupVideo video = model(now, new VideoMetadata(100, VideoMetadata.Type.VIDEO, VideoMetadata.Live.NONE, false));
        GroupVideo stream = new GroupVideo("00000000002", CHANNEL, "Live", "Channel", "", now - 10, 2, AVATAR, new VideoMetadata(100, VideoMetadata.Type.STREAM, VideoMetadata.Live.ENDED, false));
        assertEquals(List.of(video), GroupContentFilter.VIDEOS.apply(List.of(video, stream), now, ZoneId.of("UTC")));
        assertEquals(List.of(stream), GroupContentFilter.LIVE.apply(List.of(video, stream), now, ZoneId.of("UTC")));
        assertEquals(List.of(video, stream), GroupContentFilter.ALL.apply(List.of(video, stream), now, ZoneId.of("UTC")));
    }
    @Test public void durationFormattingHasNoZeroPlaceholderAndHandlesHours() {
        assertEquals("4:32", VideoMetadata.formatDuration(272)); assertEquals("18:45", VideoMetadata.formatDuration(1125));
        assertEquals("1:03:22", VideoMetadata.formatDuration(3802)); assertEquals("", VideoMetadata.formatDuration(0)); assertEquals("", VideoMetadata.UNKNOWN.badge());
        assertEquals(-1, VideoMetadata.parseDuration("LIVE")); assertEquals(-1, VideoMetadata.parseDuration("3:90")); assertEquals(-1, VideoMetadata.parseDuration("999999999999999999999:00"));
    }
    @Test public void avatarHostsRejectCredentialsHttpAndUnrelatedUrls() {
        assertEquals(AVATAR, ChannelPageMetadata.safeAvatar(AVATAR)); assertEquals("", ChannelPageMetadata.safeAvatar("https://yt3.googleusercontent.com.evil.test/a"));
        assertEquals("", ChannelPageMetadata.safeAvatar("https://user@yt3.ggpht.com/a")); assertEquals("", ChannelPageMetadata.safeAvatar("http://yt3.ggpht.com/a"));
    }
    @Test public void htmlJsonExtractionPreservesEscapedBracesAndQuotes() throws Exception {
        JSONObject root = page("videos", new JSONArray().put(video(ID, "4:32"))); root.put("description", "a } brace and \\\" quote </script>");
        ChannelPageMetadata p = ChannelPageMetadata.parse("<script>var ytInitialData = " + root + ";</script>", CHANNEL, "videos", 1); assertEquals(1, p.videos.size());
    }
    @Test public void wrongOwnerWrongTabTruncatedAndOversizedMetadataFailClosed() throws Exception {
        String json = page("videos", new JSONArray()).toString();
        for (String malformed : List.of(json.replace(CHANNEL, "UC0000000000000000000002"), json.replace("/videos", "/featured"), "var ytInitialData = {\"broken\":", " ".repeat(ChannelPageMetadata.MAX_BYTES + 1))) {
            try { ChannelPageMetadata.parse(malformed, CHANNEL, "videos", 1); fail("Invalid metadata accepted"); } catch (IOException expected) { }
        }
    }
}
