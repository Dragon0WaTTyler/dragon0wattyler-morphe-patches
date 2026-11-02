/* SPDX-License-Identifier: GPL-3.0-only
 * Independent Subscription Groups groups.5, 2026-11-01. See LICENSE and NOTICE.
 */
package app.morphe.extension.youtube.groups;

import java.util.Locale;

/** Only explicit YouTube metadata. Unknown is never inferred from duration/title/thumbnail. */
public final class VideoMetadata {
    public enum Type { UNKNOWN, VIDEO, SHORT, STREAM }
    public enum Live { UNKNOWN, NONE, LIVE, UPCOMING, ENDED }
    public static final VideoMetadata UNKNOWN = new VideoMetadata(-1, Type.UNKNOWN, Live.UNKNOWN, false);
    public final long durationSeconds;
    public final Type type;
    public final Live live;
    public final boolean podcast;
    public final java.util.List<ThumbnailVariant> thumbnails;
    public VideoMetadata(long seconds, Type type, Live live, boolean podcast) {
        this(seconds, type, live, podcast, java.util.List.of());
    }
    public VideoMetadata(long seconds, Type type, Live live, boolean podcast, java.util.List<ThumbnailVariant> thumbnails) {
        durationSeconds = seconds > 0 ? seconds : -1;
        this.type = type; this.live = live; this.podcast = podcast;
        this.thumbnails = java.util.Collections.unmodifiableList(new java.util.ArrayList<>(thumbnails));
    }
    public String badge() {
        if (live == Live.LIVE) return "LIVE";
        if (live == Live.UPCOMING) return "UPCOMING";
        if (type == Type.SHORT || durationSeconds <= 0) return "";
        return formatDuration(durationSeconds);
    }
    public static String formatDuration(long seconds) {
        if (seconds <= 0) return "";
        return seconds >= 3600
                ? String.format(Locale.ROOT, "%d:%02d:%02d", seconds / 3600, seconds / 60 % 60, seconds % 60)
                : String.format(Locale.ROOT, "%d:%02d", seconds / 60, seconds % 60);
    }
    public static long parseDuration(String text) {
        if (text == null || !text.matches("[0-9]+:[0-5][0-9](:[0-5][0-9])?")) return -1;
        try {
            long seconds = 0;
            for (String part : text.split(":")) seconds = Math.addExact(Math.multiplyExact(seconds, 60), Long.parseLong(part));
            return seconds > 0 ? seconds : -1;
        } catch (RuntimeException invalid) { return -1; }
    }
}
