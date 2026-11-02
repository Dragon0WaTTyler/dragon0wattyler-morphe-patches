/* SPDX-License-Identifier: GPL-3.0-only
 * Independent Subscription Groups groups.4, 2026-11-01. See LICENSE and NOTICE.
 */
package app.morphe.extension.youtube.groups;

import org.junit.Test;
import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.TimeUnit;
import static org.junit.Assert.*;

/** Executes the real automatic dispatcher with deterministic workers/time, without a manual page trigger. */
public class GroupCoverageTest {
    private static String channel(int id) { return "UC" + String.format(Locale.ROOT, "%022d", id); }
    private static GroupVideo video(String channel, int id, long timestamp) {
        return new GroupVideo(String.format(Locale.ROOT, "%011d", id), channel, "Video " + id, "Channel", "", timestamp, id);
    }
    private static List<String> channels(int count) {
        List<String> result = new ArrayList<>(); for (int i = 0; i < count; i++) result.add(channel(i)); return result;
    }
    private static final class Workers extends AbstractExecutorService {
        final ArrayDeque<Runnable> tasks = new ArrayDeque<>(); boolean shutdown;
        int largestQueue;
        public void execute(Runnable task) { if (shutdown) throw new java.util.concurrent.RejectedExecutionException(); tasks.addLast(task); largestQueue = Math.max(largestQueue, tasks.size()); }
        public void shutdown() { shutdown = true; }
        public List<Runnable> shutdownNow() { shutdown = true; List<Runnable> result = new ArrayList<>(tasks); tasks.clear(); return result; }
        public boolean isShutdown() { return shutdown; } public boolean isTerminated() { return shutdown && tasks.isEmpty(); }
        public boolean awaitTermination(long time, TimeUnit unit) { return isTerminated(); }
        void next() { tasks.removeFirst().run(); }
    }
    private static final class Clock implements RecentFeedSession.Scheduler {
        long time = 1800000000000L, serial;
        final PriorityQueue<Alarm> alarms = new PriorityQueue<>(Comparator.comparingLong((Alarm a) -> a.due).thenComparingLong(a -> a.order));
        final class Alarm { long due, order; Runnable task; boolean canceled; }
        public long now() { return time; }
        public RecentFeedSession.Ticket after(long delay, Runnable task) {
            Alarm alarm = new Alarm(); alarm.due = time + delay; alarm.order = serial++; alarm.task = task; alarms.add(alarm); return () -> alarm.canceled = true;
        }
        boolean next() {
            while (!alarms.isEmpty()) { Alarm alarm = alarms.remove(); if (alarm.canceled) continue; time = Math.max(time, alarm.due); alarm.task.run(); return true; }
            return false;
        }
        void advance(long millis) { time += millis; }
    }
    interface Response { List<GroupVideo> fetch(String id, int attempt) throws IOException; }
    private static final class Source implements FeedSource {
        final Clock clock; final Map<String, Window> cache = new HashMap<>(); final Map<String, Integer> calls = new LinkedHashMap<>();
        long cooldown;
        boolean memoryEnabled;
        Response response = (id, attempt) -> List.of(video(id, Integer.parseInt(id.substring(2)), 100));
        Source(Clock clock) { this.clock = clock; }
        public Window cached(String channel) { return cache.get(channel); }
        public Window memory(String channel) { return memoryEnabled ? cache.get(channel) : null; }
        public long delayMillis(long now) { return Math.max(0, cooldown - now); }
        public List<GroupVideo> recent(String id, boolean refresh) throws IOException {
            int attempt = calls.merge(id, 1, Integer::sum); List<GroupVideo> result = response.fetch(id, attempt);
            cache.put(id, new Window(result, clock.now())); return result;
        }
        int requests() { return calls.values().stream().mapToInt(Integer::intValue).sum(); }
    }
    private static final class Fixture {
        final Workers workers = new Workers(); final Clock clock = new Clock(); final Source source = new Source(clock);
        RecentFeedSession.State last; boolean progressive;
        final RecentFeedSession session = new RecentFeedSession(source, workers, Runnable::run, state -> {
            last = state; if (!state.videos.isEmpty() && state.pendingChannels > 0) progressive = true;
        }, clock);
        void finish() {
            for (int i = 0; i < 50000; i++) {
                if (!workers.tasks.isEmpty()) workers.next();
                else if (!clock.next()) { assertNotNull(last); assertTrue("Coverage must be terminal", last.terminal); return; }
            }
            fail("Automatic queue failed to settle");
        }
    }
    @Test public void all155UniqueChannelsAutomaticallyReachTerminalCoverage() {
        Fixture f = new Fixture(); List<String> ids = channels(155); ids.addAll(channels(20));
        f.session.start(ids, false); f.finish();
        assertEquals(155, f.source.requests()); assertEquals(155, f.source.calls.size());
        assertTrue(f.source.calls.values().stream().allMatch(count -> count == 1));
        assertEquals(155, f.last.totalChannels); assertEquals(155, f.last.loadedChannels);
        assertEquals(155, f.last.inspectedChannelIds.size()); assertTrue(f.last.pendingChannelIds.isEmpty());
        assertEquals(155, f.last.channelAttempts.size()); assertTrue(f.last.channelFailures.isEmpty());
        assertEquals(0, f.last.pendingChannels); assertEquals(0, f.last.failedChannels);
        assertEquals(0, f.last.uninspectedChannels); assertFalse(f.last.completeHistory); assertTrue(f.progressive);
        assertTrue(f.workers.largestQueue <= RecentFeedSession.MAX_CONCURRENT);
    }
    @Test public void aLaterChannelIntroducesNewestPublicationAndIsMergedAtTheTop() {
        Fixture f = new Fixture();
        f.source.response = (id, attempt) -> { int n = Integer.parseInt(id.substring(2)); return List.of(video(id, n, n == 154 ? 900000 : 100)); };
        f.session.start(channels(155), false); f.finish();
        assertEquals(channel(154), f.last.videos.get(0).channelId); assertEquals(900000, f.last.videos.get(0).published);
        assertEquals(155, f.last.videos.size());
    }
    @Test public void transientFailuresUseExponentialBackoffAutomatically() {
        Fixture f = new Fixture(); long start = f.clock.now();
        f.source.response = (id, attempt) -> { if (attempt < 3) throw new IOException("Timed out"); return List.of(video(id, 1, 50)); };
        f.session.start(channels(1), false); f.workers.next();
        assertEquals(1, f.source.requests()); assertEquals(1, f.last.retryingChannels); assertFalse(f.last.terminal);
        assertTrue(f.workers.tasks.isEmpty()); f.finish();
        assertEquals(3, f.source.requests()); assertEquals(start + 3000, f.clock.now()); assertEquals(1, f.last.loadedChannels);
    }
    @Test public void permanentFailureDoesNotAbandonTheRestOf155Channels() {
        Fixture f = new Fixture();
        f.source.response = (id, attempt) -> { if (id.equals(channel(17))) throw new FeedSource.Failure("404", false, 0); return List.of(video(id, Integer.parseInt(id.substring(2)), 40)); };
        f.session.start(channels(155), false); f.finish();
        assertEquals(155, f.source.requests()); assertEquals(Integer.valueOf(1), f.source.calls.get(channel(17)));
        assertEquals(154, f.last.loadedChannels); assertEquals(1, f.last.failedChannels); assertTrue(f.last.terminal);
        assertEquals("404", f.last.channelFailures.get(channel(17)));
    }
    @Test public void exhaustedTransientFailureIsAccountedForWithoutStoppingCoverage() {
        Fixture f = new Fixture();
        f.source.response = (id, attempt) -> { if (id.equals(channel(17))) throw new IOException("Unavailable"); return List.of(video(id, Integer.parseInt(id.substring(2)), 40)); };
        f.session.start(channels(155), false); f.finish();
        assertEquals(157, f.source.requests()); assertEquals(Integer.valueOf(3), f.source.calls.get(channel(17)));
        assertEquals(154, f.last.loadedChannels); assertEquals(1, f.last.failedChannels); assertEquals(0, f.last.pendingChannels);
    }
    @Test public void serverCooldownPreventsLaunchingRemainingRequestsEarly() {
        Fixture f = new Fixture(); long start = f.clock.now();
        f.source.response = (id, attempt) -> {
            if (id.equals(channel(0)) && attempt == 1) { f.source.cooldown = f.clock.now() + 7000; throw new FeedSource.Failure("429", true, 7000); }
            if (Integer.parseInt(id.substring(2)) >= 4) assertTrue(f.clock.now() >= start + 7000);
            return List.of(video(id, Integer.parseInt(id.substring(2)), 100));
        };
        f.session.start(channels(20), false); f.finish(); assertEquals(20, f.last.loadedChannels); assertEquals(start + 7000, f.clock.now());
    }
    @Test public void cachedOverlapIsReusedAcrossGroupsWithoutAnotherRequest() {
        Fixture f = new Fixture(); f.session.start(List.of(channel(1), channel(2)), false); f.finish();
        f.session.start(List.of(channel(2), channel(3)), false); f.finish();
        assertEquals(3, f.source.requests()); assertEquals(Integer.valueOf(1), f.source.calls.get(channel(2)));
        assertEquals(1, f.last.cachedChannels); assertEquals(2, f.last.videos.size());
    }
    @Test public void staleReturnKeepsCachedVideosAndRefreshesAutomatically() {
        Fixture f = new Fixture(); f.session.start(channels(2), false); f.finish(); f.session.pause();
        f.clock.advance(FeedSource.Window.TTL + 1); f.session.resume();
        assertEquals(2, f.last.videos.size()); assertEquals(2, f.last.staleChannels); assertTrue(f.last.loading);
        f.finish(); assertEquals(4, f.source.requests()); assertEquals(0, f.last.staleChannels);
    }
    @Test public void explicitRefreshPreservesVisibleResultsUntilNewWindowsArrive() {
        Fixture f = new Fixture(); f.session.start(channels(3), false); f.finish(); f.session.refresh();
        assertEquals(3, f.last.videos.size()); assertTrue(f.last.loading); f.finish(); assertEquals(6, f.source.requests());
    }
    @Test public void lateOlderDuplicateCannotOverwriteNewerPublication() {
        Fixture f = new Fixture();
        f.source.response = (id, attempt) -> List.of(video(id, 99, id.equals(channel(0)) ? 500 : 100));
        f.session.start(channels(2), false); f.finish();
        assertEquals(1, f.last.videos.size()); assertEquals(500, f.last.videos.get(0).published); assertEquals(channel(0), f.last.videos.get(0).channelId);
    }
    @Test public void groupSwitchCancelsQueuedJobsAndIsolatesNewResults() {
        Fixture f = new Fixture(); f.session.start(channels(155), false);
        f.session.start(List.of(channel(999)), false); f.finish();
        assertEquals(1, f.source.requests()); assertEquals(1, f.last.videos.size()); assertEquals(channel(999), f.last.videos.get(0).channelId);
    }
    @Test public void repeatedPauseResumeNeverSkipsOrRepeatsChannels() {
        Fixture f = new Fixture(); f.session.start(channels(155), false);
        for (int i = 0; i < 7; i++) f.workers.next();
        f.session.pause(); f.session.pause();
        while (!f.workers.tasks.isEmpty()) f.workers.next();
        int before = f.source.requests(); f.session.resume(); f.finish();
        assertEquals(7, before); assertEquals(155, f.source.requests()); assertEquals(155, f.last.loadedChannels);
    }
    @Test public void activeAggregateDoesNotSilentlyDropVideosAt6000() {
        Fixture f = new Fixture();
        f.source.response = (id, attempt) -> { int n = Integer.parseInt(id.substring(2)); List<GroupVideo> result = new ArrayList<>();
            for (int i = 0; i < 100; i++) result.add(video(id, n * 100 + i, n * 100L + i)); return result; };
        f.session.start(channels(70), false); f.finish(); assertEquals(7000, f.last.videos.size());
    }
    @Test public void publicationOffsetsAreNormalizedAndRetrievalTimeIsNeverUsedAsPublication() {
        Fixture f = new Fixture();
        long older = OffsetDateTime.parse("2026-11-01T13:00:00+03:00").toInstant().toEpochMilli();
        long newer = OffsetDateTime.parse("2026-11-01T07:00:00-04:00").toInstant().toEpochMilli();
        f.source.response = (id, attempt) -> List.of(video(id, Integer.parseInt(id.substring(2)), id.equals(channel(0)) ? older : newer));
        f.session.start(channels(2), false); f.finish(); assertEquals(newer, f.last.videos.get(0).published); assertTrue(newer < f.clock.now());
    }
    @Test public void deterministicTiesRemainStableAcrossRefreshAndCompletionOrder() {
        Fixture f = new Fixture(); f.session.start(channels(5), false); f.finish();
        List<String> order = new ArrayList<>(); for (GroupVideo v : f.last.videos) order.add(v.id);
        f.session.refresh(); f.finish(); List<String> after = new ArrayList<>(); for (GroupVideo v : f.last.videos) after.add(v.id);
        assertEquals(order, after);
    }
    @Test public void emptyGroupTerminatesWithoutNetworkOrRetryTimers() {
        Fixture f = new Fixture(); f.session.start(List.of(), false); f.finish(); assertEquals(0, f.source.requests()); assertTrue(f.last.videos.isEmpty());
    }
    @Test public void freshMemoryWindowsAppearImmediatelyWithoutOccupyingRequestWorkers() {
        Fixture f = new Fixture(); f.source.memoryEnabled = true; f.session.start(channels(155), false); f.finish();
        f.session.start(channels(155), false);
        assertTrue(f.last.terminal); assertEquals(155, f.last.loadedChannels); assertEquals(155, f.last.videos.size());
        assertTrue(f.workers.tasks.isEmpty()); assertEquals(155, f.source.requests()); assertEquals(0, f.last.uninspectedChannels);
    }
    @Test public void staleMemoryWindowsAreVisibleBeforeAnyWorkerCanRefreshThem() {
        Fixture f = new Fixture(); f.source.memoryEnabled = true; f.session.start(channels(3), false); f.finish();
        f.clock.advance(FeedSource.Window.TTL + 1); f.session.start(channels(3), false);
        assertEquals(3, f.last.videos.size()); assertEquals(3, f.last.staleChannels); assertTrue(f.last.loading);
        assertEquals(3, f.source.requests()); f.finish(); assertEquals(6, f.source.requests());
    }
}
