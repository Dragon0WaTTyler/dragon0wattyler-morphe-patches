/* SPDX-License-Identifier: GPL-3.0-only
 * Independent Subscription Groups V2, 2026-11-01. See LICENSE and NOTICE.
 */
package app.morphe.extension.youtube.groups;
import java.io.IOException;
import java.util.List;

/** Provider boundary. Current RSS implementation is a finite recent window, with no history cursor. */
public interface FeedSource {
    List<GroupVideo> recent(String channelId, boolean refresh) throws IOException;
    /** Called on a worker. Stale data may be displayed while a fresh request runs. */
    default Window cached(String channel) throws IOException { return null; }
    /** In-memory lookup only: no disk/network or locks held across I/O. */
    default Window memory(String channel) { return null; }
    /** Shared server cooldown; the queue does not spend attempts while waiting. */
    default long delayMillis(long now) { return 0; }
    final class Window {
        public static final long TTL = 15 * 60 * 1000L;
        public final List<GroupVideo> videos;
        public final long fetchedAt;
        public Window(List<GroupVideo> videos, long fetchedAt) { this.videos = java.util.Collections.unmodifiableList(new java.util.ArrayList<>(videos)); this.fetchedAt = fetchedAt; }
        public boolean fresh(long now) { return fetchedAt <= now && now - fetchedAt < TTL; }
    }
    final class Failure extends IOException {
        public final boolean retryable;
        public final long retryAfterMillis;
        public Failure(String message, boolean retryable, long retryAfterMillis) {
            super(message); this.retryable = retryable; this.retryAfterMillis = Math.max(0, retryAfterMillis);
        }
    }
}
