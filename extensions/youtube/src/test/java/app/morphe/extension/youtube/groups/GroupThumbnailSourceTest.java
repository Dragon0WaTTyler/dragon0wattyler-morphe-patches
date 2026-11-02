/* SPDX-License-Identifier: GPL-3.0-only
 * Independent Subscription Groups groups.6, modified 2026-11-02. See LICENSE/NOTICE.
 */
package app.morphe.extension.youtube.groups;

import android.os.Looper;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE, sdk = {29, 35})
public class GroupThumbnailSourceTest {
    @Rule public TemporaryFolder temporary = new TemporaryFolder();
    private static final String ID = "00000000401", CHANNEL = "UC0000000000000000000001";
    private static final List<ThumbnailVariant> HD = List.of(new ThumbnailVariant("https://i.ytimg.com/vi/" + ID + "/reported-1920.jpg", 1920, 1080));
    private static final class Jobs extends AbstractExecutorService implements RecentFeedSession.Scheduler {
        final ArrayDeque<Runnable> jobs = new ArrayDeque<>(); final List<Long> delays = new ArrayList<>(); boolean stopped;
        public void execute(Runnable job) { jobs.add(job); }
        public void shutdown() { stopped = true; }
        public List<Runnable> shutdownNow() { stopped = true; jobs.clear(); return List.of(); }
        public boolean isShutdown() { return stopped; }
        public boolean isTerminated() { return stopped; }
        public boolean awaitTermination(long timeout, TimeUnit unit) { return stopped; }
        public long now() { return System.currentTimeMillis(); }
        public RecentFeedSession.Ticket after(long delay, Runnable action) { delays.add(delay); AtomicBoolean cancelled = new AtomicBoolean(); jobs.add(() -> { if (!cancelled.get()) action.run(); }); return () -> cancelled.set(true); }
        void finish() { int guard = 0; while (!jobs.isEmpty()) { assertTrue(++guard < 100); jobs.remove().run(); } Shadows.shadowOf(Looper.getMainLooper()).idle(); }
    }
    @Test public void duplicateVisibleCardsShareOneRequestAndReuseMemoryAndDisk() throws Exception {
        java.io.File folder = temporary.newFolder(); Jobs jobs = new Jobs(); AtomicInteger count = new AtomicInteger(); List<List<ThumbnailVariant>> delivered = new ArrayList<>();
        ThumbnailMetadataSource source = new ThumbnailMetadataSource(folder, (id, channel) -> { count.incrementAndGet(); return HD; }, jobs, jobs);
        source.request(ID, CHANNEL, () -> true, delivered::add); source.request(ID, CHANNEL, () -> true, delivered::add); jobs.finish();
        assertEquals(1, count.get()); assertEquals(2, delivered.size()); assertEquals(1920, delivered.get(0).get(0).width);
        source.request(ID, CHANNEL, () -> true, delivered::add); assertEquals(3, delivered.size()); assertTrue(jobs.jobs.isEmpty());
        ThumbnailMetadataSource restarted = new ThumbnailMetadataSource(folder, (id, channel) -> { throw new AssertionError("Valid persistent metadata must avoid network"); }, jobs, jobs);
        restarted.request(ID, CHANNEL, () -> true, delivered::add); jobs.finish(); assertEquals(4, delivered.size()); assertEquals(1, count.get());
    }
    @Test public void recycledOrDetachedCardsDoNotTriggerQueuedNetworkOrReceiveResults() throws Exception {
        Jobs jobs = new Jobs(); AtomicInteger count = new AtomicInteger(); AtomicBoolean active = new AtomicBoolean(true); List<List<ThumbnailVariant>> delivered = new ArrayList<>();
        ThumbnailMetadataSource source = new ThumbnailMetadataSource(temporary.newFolder(), (id, channel) -> { count.incrementAndGet(); return HD; }, jobs, jobs);
        source.request(ID, CHANNEL, active::get, delivered::add); active.set(false); jobs.finish(); assertEquals(0, count.get()); assertTrue(delivered.isEmpty());
        source.request(ID, CHANNEL, () -> false, delivered::add); assertTrue(jobs.jobs.isEmpty());
        active.set(true); source.request(ID, CHANNEL, active::get, delivered::add); jobs.finish(); assertEquals(1, count.get()); assertEquals(1, delivered.size());
    }
    @Test public void transientFailuresRetryWithBoundedExponentialBackoffThenNegativeCache() throws Exception {
        Jobs jobs = new Jobs(); AtomicInteger count = new AtomicInteger(); List<List<ThumbnailVariant>> delivered = new ArrayList<>();
        ThumbnailMetadataSource source = new ThumbnailMetadataSource(temporary.newFolder(), (id, channel) -> { count.incrementAndGet(); throw new FeedSource.Failure("503", true, 0); }, jobs, jobs);
        source.request(ID, CHANNEL, () -> true, delivered::add); jobs.finish();
        assertEquals(3, count.get()); assertEquals(List.of(1000L, 2000L), jobs.delays); assertEquals(1, delivered.size()); assertTrue(delivered.get(0).isEmpty());
        source.request(ID, CHANNEL, () -> true, delivered::add); assertTrue(jobs.jobs.isEmpty()); assertEquals(3, count.get());
    }
    @Test public void unavailableMetadataDoesNotInventAssetsOrRetryPermanentFailures() throws Exception {
        Jobs jobs = new Jobs(); AtomicInteger count = new AtomicInteger(); List<List<ThumbnailVariant>> delivered = new ArrayList<>();
        ThumbnailMetadataSource source = new ThumbnailMetadataSource(temporary.newFolder(), (id, channel) -> { count.incrementAndGet(); throw new FeedSource.Failure("No public player metadata", false, 0); }, jobs, jobs);
        source.request(ID, CHANNEL, () -> true, delivered::add); jobs.finish(); assertEquals(1, count.get()); assertTrue(jobs.delays.isEmpty()); assertTrue(delivered.get(0).isEmpty());
    }
}
