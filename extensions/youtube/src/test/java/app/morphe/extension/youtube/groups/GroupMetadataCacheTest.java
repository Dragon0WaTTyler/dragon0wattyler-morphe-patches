/* SPDX-License-Identifier: GPL-3.0-only
 * Independent Subscription Groups groups.5, 2026-11-01. See LICENSE and NOTICE.
 */
package app.morphe.extension.youtube.groups;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Locale;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.Assert.*;

public class GroupMetadataCacheTest {
    @Rule public TemporaryFolder temporary = new TemporaryFolder();
    private ChannelPageMetadata data(String channel, String tab) {
        return new ChannelPageMetadata(channel, GroupMetadataTest.AVATAR, Map.of(GroupMetadataTest.ID,
                new VideoMetadata(272, VideoMetadata.Type.VIDEO, VideoMetadata.Live.NONE, false, List.of(
                        new ThumbnailVariant("https://i.ytimg.com/vi/" + GroupMetadataTest.ID + "/provided.jpg", 1280, 720)))), Map.of("videos", "v", "shorts", "s"), System.currentTimeMillis());
    }
    @Test public void oneChannelRequestServesManyVideosAndOverlappingGroups() throws Exception {
        AtomicInteger calls = new AtomicInteger(); GroupMetadataSource source = new GroupMetadataSource(temporary.newFolder(), (channel, tab) -> { calls.incrementAndGet(); return data(channel, tab); });
        source.load(GroupMetadataTest.CHANNEL, "videos", false);
        for (int i = 0; i < 155; i++) { source.load(GroupMetadataTest.CHANNEL, "videos", false); assertEquals(GroupMetadataTest.AVATAR, source.enrich(GroupMetadataTest.model(1, VideoMetadata.UNKNOWN)).avatar); }
        assertEquals(1, calls.get());
    }
    @Test public void persistentCacheSurvivesNewSourceAndRetainsDurationAndAvatar() throws Exception {
        File folder = temporary.newFolder(); new GroupMetadataSource(folder, this::data).load(GroupMetadataTest.CHANNEL, "videos", false);
        GroupMetadataSource source = new GroupMetadataSource(folder, (channel, tab) -> { throw new AssertionError("Fresh disk cache must avoid network"); });
        source.load(GroupMetadataTest.CHANNEL, "videos", false); GroupVideo video = source.enrich(GroupMetadataTest.model(7, VideoMetadata.UNKNOWN));
        assertEquals(272, video.metadata.durationSeconds); assertEquals(GroupMetadataTest.AVATAR, video.avatar); assertEquals(7, video.published);
        assertEquals(1280, video.metadata.thumbnails.get(0).width); assertEquals(720, video.metadata.thumbnails.get(0).height);
        assertTrue(video.metadata.thumbnails.get(0).url.endsWith("/provided.jpg"));
    }
    @Test public void forceRefreshAndStaleCacheRevalidateOnlyMetadata() throws Exception {
        AtomicInteger calls = new AtomicInteger(); GroupMetadataSource source = new GroupMetadataSource(temporary.newFolder(), (channel, tab) -> { calls.incrementAndGet(); return data(channel, tab); });
        source.load(GroupMetadataTest.CHANNEL, "videos", false); source.load(GroupMetadataTest.CHANNEL, "videos", true); assertEquals(2, calls.get());
        assertEquals(-1, source.enrich(new GroupVideo("00000000002", GroupMetadataTest.CHANNEL, "Unknown", "C", "", 1, 1)).metadata.durationSeconds);
    }
    @Test public void corruptCacheFallsBackWithoutInventedMetadata() throws Exception {
        File folder = temporary.newFolder(); Files.write(new File(folder, GroupMetadataTest.CHANNEL + "-videos.json").toPath(), "{broken".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        AtomicInteger calls = new AtomicInteger(); GroupMetadataSource source = new GroupMetadataSource(folder, (channel, tab) -> { calls.incrementAndGet(); return data(channel, tab); });
        assertNull(source.cached(GroupMetadataTest.CHANNEL, "videos")); source.load(GroupMetadataTest.CHANNEL, "videos", false); assertEquals(1, calls.get());
    }
    @Test public void missingActualImageLeavesInitialsFallbackAvailable() throws Exception {
        GroupMetadataSource source = new GroupMetadataSource(temporary.newFolder(), (channel, tab) -> new ChannelPageMetadata(channel, "", Map.of(), Map.of(), System.currentTimeMillis()));
        source.load(GroupMetadataTest.CHANNEL, "videos", false); GroupVideo video = source.enrich(GroupMetadataTest.model(1, VideoMetadata.UNKNOWN));
        assertEquals(GroupMetadataTest.AVATAR, video.avatar); // Existing valid metadata is reused.
        GroupVideo unknown = source.enrich(new GroupVideo(GroupMetadataTest.ID, GroupMetadataTest.CHANNEL, "T", "Channel", "", 1, 1)); assertEquals("", unknown.avatar);
    }
    @Test public void concurrentSameChannelJoinsSingleFlight() throws Exception {
        AtomicInteger calls = new AtomicInteger(); CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1);
        GroupMetadataSource source = new GroupMetadataSource(temporary.newFolder(), (channel, tab) -> {
            calls.incrementAndGet(); entered.countDown(); try { if (!release.await(3, TimeUnit.SECONDS)) throw new IOException("test timeout"); } catch (InterruptedException e) { throw new IOException(e); } return data(channel, tab);
        });
        ExecutorService workers = Executors.newFixedThreadPool(2);
        try {
            Future<ChannelPageMetadata> one = workers.submit(() -> source.load(GroupMetadataTest.CHANNEL, "videos", false)); assertTrue(entered.await(3, TimeUnit.SECONDS));
            Future<ChannelPageMetadata> two = workers.submit(() -> source.load(GroupMetadataTest.CHANNEL, "videos", false)); release.countDown();
            assertSame(one.get(3, TimeUnit.SECONDS), two.get(3, TimeUnit.SECONDS)); assertEquals(1, calls.get());
        } finally { release.countDown(); workers.shutdownNow(); }
    }
    @Test public void absentTabAvoidsRedundantRequestsAndDoesNotAffectRegularMetadata() throws Exception {
        AtomicInteger calls = new AtomicInteger(); GroupMetadataSource source = new GroupMetadataSource(temporary.newFolder(), (channel, tab) -> { calls.incrementAndGet(); return data(channel, tab); });
        source.load(GroupMetadataTest.CHANNEL, "videos", false); assertTrue(source.load(GroupMetadataTest.CHANNEL, "streams", false).videos.isEmpty());
        assertEquals(1, calls.get()); assertEquals(272, source.enrich(GroupMetadataTest.model(1, VideoMetadata.UNKNOWN)).metadata.durationSeconds);
    }
    @Test public void staleMetadataRemainsAvailableWhileRefreshFailsAndLiveBadgeExpires() throws Exception {
        AtomicInteger calls = new AtomicInteger(); GroupMetadataSource source = new GroupMetadataSource(temporary.newFolder(), (channel, tab) -> {
            if (calls.incrementAndGet() > 1) throw new IOException("offline");
            return new ChannelPageMetadata(channel, GroupMetadataTest.AVATAR, Map.of(GroupMetadataTest.ID,
                    new VideoMetadata(-1, VideoMetadata.Type.STREAM, VideoMetadata.Live.LIVE, false)), Map.of(), System.currentTimeMillis() - 16 * 60_000);
        });
        source.load(GroupMetadataTest.CHANNEL, "streams", false);
        GroupVideo video = source.enrich(GroupMetadataTest.model(1, VideoMetadata.UNKNOWN)); assertEquals(GroupMetadataTest.AVATAR, video.avatar); assertEquals("", video.metadata.badge());
        try { source.load(GroupMetadataTest.CHANNEL, "streams", false); fail("Stale metadata must refresh"); } catch (IOException expected) { }
        assertEquals(2, calls.get()); assertEquals(GroupMetadataTest.AVATAR, source.enrich(GroupMetadataTest.model(1, VideoMetadata.UNKNOWN)).avatar);
    }
    private static final class Workers extends AbstractExecutorService {
        final ArrayDeque<Runnable> queue = new ArrayDeque<>(); boolean stopped; int maximum;
        public void execute(Runnable task) { queue.add(task); maximum = Math.max(maximum, queue.size()); }
        public void shutdown() { stopped = true; }
        public List<Runnable> shutdownNow() { stopped = true; return List.of(); }
        public boolean isShutdown() { return stopped; }
        public boolean isTerminated() { return stopped; }
        public boolean awaitTermination(long value, TimeUnit unit) { return stopped; }
        void finish() { int guard = 0; while (!queue.isEmpty()) { assertTrue(++guard < 2000); queue.remove().run(); } }
    }
    @Test public void actualMetadataAdapterAutomaticallyProcessesAll155UniqueChannelsAndReusesCache() throws Exception {
        AtomicInteger calls = new AtomicInteger(); GroupMetadataSource source = new GroupMetadataSource(temporary.newFolder(), (channel, tab) -> { calls.incrementAndGet(); return data(channel, tab); });
        Workers workers = new Workers(); List<String> channels = new ArrayList<>();
        for (int i = 0; i < 155; i++) channels.add("UC" + String.format(Locale.ROOT, "%022d", i)); channels.add(channels.get(0));
        RecentFeedSession.State[] last = new RecentFeedSession.State[1];
        RecentFeedSession session = new RecentFeedSession(source.forTab("videos"), workers, Runnable::run, state -> last[0] = state);
        session.start(channels, false); workers.finish(); assertTrue(last[0].terminal); assertEquals(155, last[0].loadedChannels); assertEquals(155, calls.get());
        assertTrue(workers.maximum <= RecentFeedSession.MAX_CONCURRENT); session.start(channels, false); workers.finish(); assertEquals(155, calls.get()); session.pause();
    }
    @Test public void rapidMetadataGroupAndFilterSwitchingDiscardsObsoleteCallbacks() throws Exception {
        GroupMetadataSource source = new GroupMetadataSource(temporary.newFolder(), this::data); Workers workers = new Workers();
        List<RecentFeedSession.State> old = new ArrayList<>(), current = new ArrayList<>();
        RecentFeedSession first = new RecentFeedSession(source.forTab("videos"), workers, Runnable::run, old::add);
        first.start(List.of(GroupMetadataTest.CHANNEL), false); first.pause(); int oldCount = old.size();
        RecentFeedSession second = new RecentFeedSession(source.forTab("shorts"), workers, Runnable::run, current::add);
        second.start(List.of("UC0000000000000000000002"), false); workers.finish();
        assertEquals(oldCount, old.size()); assertTrue(current.get(current.size() - 1).terminal);
        assertEquals(List.of("UC0000000000000000000002"), current.get(current.size() - 1).inspectedChannelIds); second.pause();
    }
}
