/* SPDX-License-Identifier: GPL-3.0-only
 * Independent Subscription Groups groups.6, modified 2026-11-02. See LICENSE/NOTICE.
 */
package app.morphe.extension.youtube.groups;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import java.nio.file.Files;
import java.io.File;
import java.io.IOException;
import java.util.*;
import static org.junit.Assert.*;

public class GroupsV6CoreTest {
    private static final String ID = "00000000001", SECOND = "00000000002";
    @Rule public TemporaryFolder temporary = new TemporaryFolder();
    @Test public void defaultGroupAndDefaultContentCombineWithoutChangingGroupPolicy() {
        GroupLibrary library = GroupLibrary.empty().create("Tech"); String group = library.groups.get(0).id;
        library = library.preferences(library.preferences.with("default", group).with("default_content", "videos"));
        assertEquals(group, GroupSelection.enter(library, true)); assertEquals(GroupContentFilter.VIDEOS, ContentFilterSelection.enter(library.preferences, group));
        assertTrue(library.preferences.enabled("remember_content")); assertFalse(library.preferences.enabled("hide_watched"));
    }
    @Test public void independentPerGroupFiltersSurviveEncodingAndRestart() throws Exception {
        GroupLibrary library = GroupLibrary.empty().create("Tech").create("Music"); String tech = library.groups.get(0).id, music = library.groups.get(1).id;
        library = library.preferences(library.preferences.with("remember", "true").with("last_group", tech)
                .with(GroupPreferences.CONTENT_PREFIX + tech, "videos").with(GroupPreferences.CONTENT_PREFIX + music, "live"));
        GroupLibraryStore store = new GroupLibraryStore(temporary.newFolder()); store.save(library); GroupLibrary restored = store.load();
        assertEquals(tech, GroupSelection.enter(restored, true)); assertEquals(GroupContentFilter.VIDEOS, ContentFilterSelection.enter(restored.preferences, tech));
        assertEquals(GroupContentFilter.LIVE, ContentFilterSelection.enter(restored.preferences, music));
        restored = GroupLibraryCodec.parse(GroupLibraryCodec.encode(restored)).library;
        assertEquals(GroupContentFilter.VIDEOS, ContentFilterSelection.enter(restored.preferences, tech));
    }
    @Test public void rememberContentToggleIsIndependentOfRememberGroup() {
        GroupLibrary library = GroupLibrary.empty().create("Tech"); String id = library.groups.get(0).id;
        GroupPreferences prefs = library.preferences.with("default_content", "today").with(GroupPreferences.CONTENT_PREFIX + id, "videos");
        assertEquals(GroupContentFilter.VIDEOS, ContentFilterSelection.enter(prefs, id));
        assertEquals(GroupContentFilter.TODAY, ContentFilterSelection.enter(prefs.with("remember_content", "false"), id));
        assertFalse(prefs.enabled("remember"));
    }
    @Test public void invalidFiltersFallBackToAllAndDeletedGroupsLoseKeys() throws Exception {
        GroupLibrary library = GroupLibrary.empty().create("Tech"); String id = library.groups.get(0).id;
        library = library.preferences(library.preferences.with(GroupPreferences.CONTENT_PREFIX + id, "unsupported-filter"));
        assertEquals(GroupContentFilter.ALL, ContentFilterSelection.enter(library.preferences, id));
        assertEquals("all", library.preferences.with("default_content", "unknown").get("default_content"));
        assertNull(library.delete(id).preferences.get(GroupPreferences.CONTENT_PREFIX + id));
        assertNull(new GroupLibrary(library.groups, library.preferences.with(GroupPreferences.CONTENT_PREFIX + "orphan", "live")).preferences.get(GroupPreferences.CONTENT_PREFIX + "orphan"));
    }
    @Test public void renamingPreservesFilterAndReplaceImportPrunesDeletedGroupPreferences() {
        GroupLibrary library = GroupLibrary.empty().create("Tech"); String id = library.groups.get(0).id;
        library = library.preferences(library.preferences.with(GroupPreferences.CONTENT_PREFIX + id, "shorts"));
        assertEquals(GroupContentFilter.SHORTS, ContentFilterSelection.enter(library.edit(id, "Renamed", "", false).preferences, id));
        GroupLibrary replaced = library.importLibrary(GroupLibrary.empty().create("Other"), false, false);
        assertNull(replaced.preferences.get(GroupPreferences.CONTENT_PREFIX + id));
    }
    @Test public void legacyV5BackupGetsRememberOnAndAllDefault() throws Exception {
        GroupLibrary library = GroupLibrary.empty().create("Tech"); String json = GroupLibraryCodec.encode(library);
        org.json.JSONObject root = new org.json.JSONObject(json); org.json.JSONObject prefs = root.getJSONObject("morphe_groups_v2").getJSONObject("preferences");
        prefs.remove("remember_content"); prefs.remove("default_content"); prefs.remove("hide_watched");
        GroupLibrary restored = GroupLibraryCodec.parse(root.toString()).library;
        assertTrue(restored.preferences.enabled("remember_content")); assertFalse(restored.preferences.enabled("hide_watched"));
        assertEquals(GroupContentFilter.ALL, ContentFilterSelection.enter(restored.preferences, restored.groups.get(0).id));
    }
    @Test public void reliableCompletionRequiresNinetyPercentUniqueObservedPlayback() {
        WatchCompletionTracker tracker = new WatchCompletionTracker(); String result = null;
        for (int i = 0; i <= 89; i++) assertNull(tracker.sample(ID, 100000, i * 1000, i * 1000, 1, true));
        result = tracker.sample(ID, 100000, 90000, 90000, 1, true); assertEquals(ID, result);
        assertNull(tracker.sample(ID, 100000, 91000, 91000, 1, true));
    }
    @Test public void briefPlaybackAndSeekingNearEndAreNotWatched() {
        WatchCompletionTracker tracker = new WatchCompletionTracker(); tracker.sample(ID, 100000, 0, 0, 1, true);
        for (int i = 1; i < 5; i++) assertNull(tracker.sample(ID, 100000, i * 1000, i * 1000, 1, true));
        assertNull(tracker.sample(ID, 100000, 99000, 5000, 1, true)); assertNull(tracker.sample(ID, 100000, 100000, 6000, 1, true));
    }
    @Test public void previewExcludedPausedTimeAndLongGapsCannotComplete() {
        WatchCompletionTracker tracker = new WatchCompletionTracker();
        for (int i = 0; i <= 100; i++) assertNull(tracker.sample(ID, 100000, i * 1000, i * 1000, 1, false));
        tracker.reset(); tracker.sample(ID, 100000, 0, 0, 1, true);
        assertNull(tracker.sample(ID, 100000, 95000, 95000, 1, true));
    }
    @Test public void repeatedIntervalsDoNotDoubleCountAndDifferentVideosDoNotMix() {
        WatchCompletionTracker tracker = new WatchCompletionTracker(); long clock = 0;
        for (int lap = 0; lap < 4; lap++) for (int i = 0; i <= 40; i++) assertNull(tracker.sample(ID, 100000, i * 1000, clock++ * 1000, 1, true));
        for (int i = 50; i <= 100; i++) assertNull(tracker.sample(SECOND, 100000, i * 1000, clock++ * 1000, 1, true));
    }
    @Test public void normalDoubleSpeedCompletionAndManualUnwatchSuppression() {
        WatchCompletionTracker tracker = new WatchCompletionTracker(); String result = null;
        for (int i = 0; i <= 45; i++) { String next = tracker.sample(ID, 100000, i * 2000, i * 1000, 2, true); if (next != null) result = next; }
        assertEquals(ID, result); tracker.suppress(ID); assertNull(tracker.sample(ID, 100000, 92000, 46000, 2, true));
    }
    @Test public void localWatchedIdsPersistDeduplicateUnmarkAndClearIndependentlyOfGroups() throws Exception {
        File directory = temporary.newFolder(); WatchedHistory store = new WatchedHistory(directory);
        assertTrue(store.load().isEmpty()); store.save(new LinkedHashSet<>(List.of(ID, ID, SECOND)));
        Set<String> ids = new LinkedHashSet<>(new WatchedHistory(directory).load()); assertEquals(2, ids.size()); ids.remove(ID); store.save(ids);
        assertFalse(new WatchedHistory(directory).load().contains(ID)); assertTrue(new WatchedHistory(directory).load().contains(SECOND));
        store.save(Set.of()); assertTrue(store.load().isEmpty());
    }
    @Test public void corruptedWatchedFileIsRejectedAndInvalidWriteCannotReplaceValidHistory() throws Exception {
        File directory = temporary.newFolder(); WatchedHistory store = new WatchedHistory(directory); store.save(Set.of(ID));
        try { store.save(Set.of("bad")); fail(); } catch (IOException expected) { }
        assertEquals(Set.of(ID), store.load()); Files.write(new File(directory, "subscription-groups-watched-v6.json").toPath(), "{bad".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        try { store.load(); fail(); } catch (IOException expected) { }
    }
    private ThumbnailVariant image(int width) { return new ThumbnailVariant("https://i.ytimg.com/vi/" + ID + "/real-" + width + ".jpg", width, width * 9 / 16); }
    @Test public void selectsActualSufficientResolutionAndProvidesRealFallbacksOnly() {
        List<ThumbnailVariant> images = List.of(image(360), image(720), image(1280), image(1920));
        List<String> urls = ThumbnailVariant.order(images, 1080, "https://i.ytimg.com/vi/" + ID + "/hqdefault.jpg");
        assertEquals(image(1280).url, urls.get(0)); assertEquals(image(1920).url, urls.get(1)); assertEquals(image(720).url, urls.get(2));
        assertEquals(image(720).url, ThumbnailVariant.order(List.of(image(360), image(720)), 1440, "").get(0));
        assertTrue(ThumbnailVariant.order(List.of(), 1440, "https://invalid.test/a").isEmpty());
    }
    @Test public void samplingDoesNotShrink1280Below1080AndBoundsLargeAllocations() {
        assertEquals(1, ThumbnailVariant.sampleSize(1280, 720, 1080)); assertEquals(2, ThumbnailVariant.sampleSize(3840, 2160, 1440));
        int sample = ThumbnailVariant.sampleSize(8192, 8192, 2048); assertTrue((long) (8192 / sample) * (8192 / sample) * 4 <= 12L * 1024 * 1024);
    }
    @Test public void unsupportedNativeActionsAreOmittedWhileFallbackAndManualStateRemain() {
        List<VideoActionPlan.Action> plain = VideoActionPlan.actions(false, false, false, true, false);
        assertEquals(List.of(VideoActionPlan.Action.PLAY, VideoActionPlan.Action.CHANNEL, VideoActionPlan.Action.SHARE, VideoActionPlan.Action.MARK_WATCHED), plain);
        List<VideoActionPlan.Action> enabled = VideoActionPlan.actions(true, true, true, true, true);
        assertTrue(enabled.contains(VideoActionPlan.Action.SAVE_PLAYLIST)); assertTrue(enabled.contains(VideoActionPlan.Action.QUEUE_LAST)); assertTrue(enabled.contains(VideoActionPlan.Action.MARK_UNWATCHED));
        assertFalse(enabled.contains(VideoActionPlan.Action.MARK_WATCHED));
    }
    @Test public void realPlayerThumbnailMetadataRequiresMatchingVideoAndChannelIdentities() throws Exception {
        String channel = "UC0000000000000000000001";
        org.json.JSONObject player = new org.json.JSONObject().put("videoDetails", new org.json.JSONObject().put("videoId", ID).put("channelId", channel)
                .put("thumbnail", new org.json.JSONObject().put("thumbnails", new org.json.JSONArray().put(new org.json.JSONObject()
                        .put("url", "https://i.ytimg.com/vi/" + ID + "/actual-source.jpg").put("width", 1920).put("height", 1080)))));
        List<ThumbnailVariant> variants = ChannelPageMetadata.watchThumbnails("<script>var ytInitialPlayerResponse = " + player + ";</script>", ID, channel);
        assertEquals(1920, variants.get(0).width);
        try { ChannelPageMetadata.watchThumbnails(player.toString(), SECOND, channel); fail(); } catch (IOException expected) { }
        try { ChannelPageMetadata.watchThumbnails(player.toString(), ID, "UC0000000000000000000002"); fail(); } catch (IOException expected) { }
    }
    @Test public void veryBriefRegularVideosNeedManualMarkRatherThanAutomaticCompletion() {
        WatchCompletionTracker tracker = new WatchCompletionTracker();
        for (int i = 0; i <= 20; i++) assertNull(tracker.sample(ID, 20000, i * 1000, i * 1000, 1, true));
    }

}
