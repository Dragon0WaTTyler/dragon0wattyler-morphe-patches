/* SPDX-License-Identifier: GPL-3.0-only
 * Independent groups.4 verification helper, 2026-11-01. See LICENSE/NOTICE.
 */
package app.morphe.extension.youtube.groups;
import java.io.File;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
public class LiveCoverageCheck {
    public static void main(String[] args) throws Exception {
        ExecutorService workers = Executors.newFixedThreadPool(4);
        CountDownLatch complete = new CountDownLatch(1);
        AtomicReference<RecentFeedSession.State> result = new AtomicReference<>();
        RecentFeedSession session = new RecentFeedSession(new RecentFeedSource(new File(args[0])), workers, Runnable::run, state -> {
            result.set(state); if (state.terminal) complete.countDown();
        });
        try {
            session.start(List.of("UCXuqSBlHAE6Xw-yeJA0Tunw", "UCBJycsmduvYEL83R_U4JriQ"), false);
            if (!complete.await(90, TimeUnit.SECONDS)) throw new AssertionError("Live coverage did not settle in 90 seconds");
            RecentFeedSession.State state = result.get();
            if (state.loadedChannels != 2 || state.failedChannels != 0 || state.videos.isEmpty())
                throw new AssertionError("Live coverage failed: " + state.channelFailures);
            for (int i = 1; i < state.videos.size(); i++) if (state.videos.get(i - 1).published < state.videos.get(i).published)
                throw new AssertionError("Actual upload publications are not descending");
            if (state.videos.stream().map(v -> v.id).distinct().count() != state.videos.size()) throw new AssertionError("Duplicate video IDs");
            System.out.println("Production source + automatic queue: 2/2 public channels inspected; " + state.videos.size() + " real uploads; pending=0, failures=0; unique IDs and publication chronology verified.");
            System.out.println("Cached windows: " + state.cachedChannels + ". Finite per-channel windows only; not the user's 155-channel group, not Android runtime verification.");
        } finally { session.pause(); workers.shutdownNow(); workers.awaitTermination(12, TimeUnit.SECONDS); }
    }
}
