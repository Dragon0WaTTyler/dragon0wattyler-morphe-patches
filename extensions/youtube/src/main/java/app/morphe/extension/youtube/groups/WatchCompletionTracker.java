/* SPDX-License-Identifier: GPL-3.0-only
 * Independent Subscription Groups groups.6, modified 2026-11-02. See LICENSE/NOTICE.
 */
package app.morphe.extension.youtube.groups;

import java.util.ArrayList;
import java.util.List;

/** Conservative completion: >=90% unique observed content and substantial real playing time. */
public final class WatchCompletionTracker {
    private String id = "";
    private long position = -1, at = -1, duration, activeMillis;
    private boolean baselinePlaying, recorded;
    private final List<long[]> intervals = new ArrayList<>();
    public synchronized void reset() { id = ""; position = at = -1; duration = activeMillis = 0; intervals.clear(); baselinePlaying = recorded = false; }
    public synchronized void suppress(String video) { if (id.equals(video)) recorded = true; }
    public synchronized String sample(String video, long length, long next, long now, float speed, boolean eligible) {
        if (!WatchedHistory.valid(video) || length <= 0 || next < 0 || next > length + 1000 || speed <= 0 || !Float.isFinite(speed)) { baselinePlaying = false; return null; }
        if (!id.equals(video) || Math.abs(duration - length) > 1000) { reset(); id = video; duration = length; }
        long elapsed = now - at, delta = next - position;
        if (eligible && baselinePlaying && elapsed > 0 && elapsed <= 5000 && delta > 0
                && delta <= elapsed * Math.min(speed, 8f) * 1.3 + 500) {
            add(Math.max(0, position), Math.min(next, length)); activeMillis += elapsed;
        }
        position = next; at = now; baselinePlaying = eligible;
        long watched = 0; for (long[] span : intervals) watched += span[1] - span[0];
        if (!recorded && eligible && watched >= Math.ceil(length * .90) && next >= Math.ceil(length * .90)
                && activeMillis >= 30000) { recorded = true; return video; }
        return null;
    }
    private void add(long start, long end) {
        for (int i = 0; i < intervals.size();) {
            long[] span = intervals.get(i);
            if (span[1] < start) { i++; continue; }
            if (span[0] > end) break;
            start = Math.min(start, span[0]); end = Math.max(end, span[1]); intervals.remove(i);
        }
        int at = 0; while (at < intervals.size() && intervals.get(at)[0] < start) at++;
        if (intervals.size() < 4096) intervals.add(at, new long[]{start, end});
    }
}
