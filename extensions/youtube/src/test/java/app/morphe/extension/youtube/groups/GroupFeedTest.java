/* SPDX-License-Identifier: GPL-3.0-only
 * Independent Subscription Groups V2, 2026-11-01. See LICENSE and NOTICE.
 */
package app.morphe.extension.youtube.groups;

import org.junit.Test;
import org.junit.After;
import java.io.IOException;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;
import static org.junit.Assert.*;

public class GroupFeedTest {
    private static final String CHANNEL = "UCXuqSBlHAE6Xw-yeJA0Tunw";
    private final ExecutorService workers = Executors.newFixedThreadPool(4);
    @After public void finish() throws Exception { workers.shutdownNow(); workers.awaitTermination(3, TimeUnit.SECONDS); }
    private static String channel(int id) { return "UC" + String.format(java.util.Locale.ROOT, "%022d", id); }
    private static GroupVideo video(String channel, int id, long published, long views) {
        return new GroupVideo(String.format(java.util.Locale.ROOT, "%011d", id), channel, "Video " + id, "Channel", "", published, views);
    }
    private static String atom(String owner, String entries) {
        return "<?xml version=\"1.0\"?><feed xmlns=\"http://www.w3.org/2005/Atom\" xmlns:yt=\"http://www.youtube.com/xml/schemas/2015\" xmlns:media=\"http://search.yahoo.com/mrss/\">"
                + "<yt:channelId>" + owner + "</yt:channelId><author><name>Channel title</name></author>" + entries + "</feed>";
    }
    private static String entry(String id, String date) {
        return "<entry><yt:videoId>" + id + "</yt:videoId><title>Title &amp; more</title><author><name>Channel title</name></author>"
                + "<published>" + date + "</published><media:group><media:thumbnail url=\"https://i.ytimg.com/vi/" + id
                + "/hqdefault.jpg\"/><media:community><media:statistics views=\"1234\"/></media:community></media:group></entry>";
    }
    @Test public void parsesRealFieldsDeduplicatesAndUsesExpectedOwner() throws Exception {
        String item = entry("abcdefghijk", "2026-11-01T10:00:00+00:00");
        List<GroupVideo> videos = YouTubeAtomFeed.parse(atom(CHANNEL.substring(2), item + item), CHANNEL);
        assertEquals(1, videos.size()); GroupVideo video = videos.get(0);
        assertEquals(CHANNEL, video.channelId); assertEquals("Title & more", video.title); assertEquals("Channel title", video.channelName);
        assertEquals(1234, video.views); assertTrue(video.thumbnail.startsWith("https://i.ytimg.com/"));
        assertEquals(Instant.parse("2026-11-01T10:00:00Z").toEpochMilli(), video.published);
    }
    @Test public void missingChannelsMalformedFeedsAndExternalEntitiesFailClosed() {
        for (String xml : List.of("<feed>", atom(channel(1), ""), "<feed xmlns=\"http://www.w3.org/2005/Atom\"/>",
                atom(CHANNEL, entry("invalid", "2026-11-01T00:00:00Z")), atom(CHANNEL, entry("abcdefghijk", "bad date")),
                "<!DOCTYPE feed [<!ENTITY x SYSTEM \"file:///etc/passwd\">]>" + atom(CHANNEL, ""),
                "<!DOCTYPE feed SYSTEM \"https://example.com/evil.dtd\">" + atom(CHANNEL, ""))) {
            assertThrows(IOException.class, () -> YouTubeAtomFeed.parse(xml, CHANNEL));
        }
    }
    @Test public void emptyPublicFeedIsARealEmptyStateAndOversizedFeedsFail() throws Exception {
        assertTrue(YouTubeAtomFeed.parse(atom(CHANNEL, ""), CHANNEL).isEmpty());
        assertThrows(IOException.class, () -> YouTubeAtomFeed.parse(" ".repeat(YouTubeAtomFeed.MAX_BYTES + 1), CHANNEL));
    }
    @Test public void onlyYouTubeThumbnailHostsAreAllowed() {
        assertEquals("https://i.ytimg.com/vi/abcdefghijk/hqdefault.jpg", YouTubeAtomFeed.safeThumbnail("http://i.ytimg.com/vi/abcdefghijk/hqdefault.jpg"));
        for (String address : List.of("https://evil.example/thumbnail", "https://i.ytimg.com.evil.example/a", "file:///a", "https://user@i.ytimg.com/a", "https://i.ytimg.com:1234/a")) {
            assertEquals("", YouTubeAtomFeed.safeThumbnail(address));
        }
    }
    private static RecentFeedSession.State await(BlockingQueue<RecentFeedSession.State> queue, Predicate<RecentFeedSession.State> condition) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (System.nanoTime() < deadline) {
            RecentFeedSession.State state = queue.poll(1, TimeUnit.SECONDS);
            if (state != null && condition.test(state)) return state;
        }
        throw new AssertionError("Feed state did not reach the expected transition");
    }
    @Test public void automaticallyLoadsBeyondTheFirstBatchWithoutClaimingCompleteHistory() throws Exception {
        BlockingQueue<RecentFeedSession.State> events = new LinkedBlockingQueue<>(); List<String> channels = new ArrayList<>();
        for (int i = 0; i < 25; i++) channels.add(channel(i));
        RecentFeedSession session = new RecentFeedSession((id, force) -> List.of(video(id, Integer.parseInt(id.substring(2)), 10, 20)), workers, Runnable::run, events::add);
        session.start(channels, false);
        RecentFeedSession.State last = await(events, s -> !s.loading && s.loadedChannels == 25);
        assertEquals(0, last.remainingChannels); assertEquals(25, last.videos.size()); assertFalse(last.completeHistory);
    }
    @Test public void retriesOnlyFailedChannels() throws Exception {
        AtomicBoolean failed = new AtomicBoolean(); BlockingQueue<RecentFeedSession.State> events = new LinkedBlockingQueue<>();
        RecentFeedSession session = new RecentFeedSession((id, force) -> {
            if (id.equals(channel(1)) && !failed.getAndSet(true)) throw new IOException("Unavailable");
            return List.of(video(id, Integer.parseInt(id.substring(2)), 10, 20));
        }, workers, Runnable::run, events::add);
        session.start(List.of(channel(0), channel(1)), false);
        RecentFeedSession.State retried = await(events, s -> !s.loading && s.loadedChannels == 2);
        assertEquals(0, retried.failedChannels); assertEquals(2, retried.videos.size());
    }
    @Test public void changingGroupsRejectsLateResultsFromThePreviousGroup() throws Exception {
        CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1);
        AtomicReference<RecentFeedSession.State> latest = new AtomicReference<>(); BlockingQueue<RecentFeedSession.State> events = new LinkedBlockingQueue<>();
        RecentFeedSession session = new RecentFeedSession((id, force) -> {
            if (id.equals(channel(0))) {
                entered.countDown(); boolean waiting = true;
                while (waiting) try { release.await(); waiting = false; } catch (InterruptedException ignored) { }
            }
            return List.of(video(id, Integer.parseInt(id.substring(2)), 10, 20));
        }, workers, Runnable::run, state -> { latest.set(state); events.add(state); });
        try {
            session.start(List.of(channel(0)), false); assertTrue(entered.await(2, TimeUnit.SECONDS));
            session.start(List.of(channel(1)), false); await(events, s -> !s.loading && !s.videos.isEmpty() && s.videos.get(0).channelId.equals(channel(1)));
        } finally { release.countDown(); }
        workers.shutdown(); assertTrue(workers.awaitTermination(3, TimeUnit.SECONDS));
        assertEquals(channel(1), latest.get().videos.get(0).channelId);
    }
    @Test public void emptyGroupAndDuplicateChannelsAreHandled() throws Exception {
        BlockingQueue<RecentFeedSession.State> events = new LinkedBlockingQueue<>();
        RecentFeedSession session = new RecentFeedSession((id, force) -> List.of(video(id, 0, 10, 20), video(id, 0, 10, 20)), workers, Runnable::run, events::add);
        session.start(List.of(), false); RecentFeedSession.State empty = await(events, s -> !s.loading);
        assertEquals(0, empty.totalChannels); assertTrue(empty.videos.isEmpty());
        session.start(List.of(channel(0), channel(0)), false);
        RecentFeedSession.State loaded = await(events, s -> !s.loading && s.loadedChannels == 1);
        assertEquals(1, loaded.totalChannels); assertEquals(1, loaded.videos.size());
    }
    @Test public void repeatedPauseResumesInterruptedChannelWithoutLosingIt() throws Exception {
        CountDownLatch entered = new CountDownLatch(1);
        java.util.concurrent.atomic.AtomicInteger attempts = new java.util.concurrent.atomic.AtomicInteger();
        BlockingQueue<RecentFeedSession.State> events = new LinkedBlockingQueue<>();
        RecentFeedSession session = new RecentFeedSession((id, force) -> {
            if (attempts.incrementAndGet() == 1) {
                entered.countDown();
                try { new CountDownLatch(1).await(); }
                catch (InterruptedException cancelled) { throw new IOException("Paused", cancelled); }
            }
            return List.of(video(id, 1, 10, 20));
        }, workers, Runnable::run, events::add);
        session.start(List.of(channel(1)), false); assertTrue(entered.await(2, TimeUnit.SECONDS));
        session.pause(); session.pause(); session.resume();
        RecentFeedSession.State resumed = await(events, s -> !s.loading && s.loadedChannels == 1);
        assertEquals(1, resumed.videos.size()); assertEquals(0, resumed.failedChannels);
        assertEquals(2, attempts.get()); assertFalse(resumed.completeHistory);
    }
    @Test public void reliableSortingAndTodayFilteringOperateOnlyOnLoadedItems() {
        long now = Instant.parse("2026-11-01T20:00:00Z").toEpochMilli();
        GroupVideo yesterday = video(CHANNEL, 1, now - 86400000, 100), today = video(CHANNEL, 2, now - 3600000, 10);
        RecentFeedSession.State state = new RecentFeedSession.State(List.of(yesterday, today), 1, 100, 0, 99, false);
        assertEquals(today.id, RecentFeedSession.visible(state, "newest", "any", now, ZoneId.of("UTC")).get(0).id);
        assertEquals(yesterday.id, RecentFeedSession.visible(state, "oldest", "any", now, ZoneId.of("UTC")).get(0).id);
        assertEquals(yesterday.id, RecentFeedSession.visible(state, "views", "any", now, ZoneId.of("UTC")).get(0).id);
        assertEquals(List.of(today), RecentFeedSession.visible(state, "newest", "today", now, ZoneId.of("UTC")));
        assertFalse(state.completeHistory);
    }
}
