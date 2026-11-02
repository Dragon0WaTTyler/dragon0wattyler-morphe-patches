/* SPDX-License-Identifier: GPL-3.0-only
 * Independent Subscription Groups groups.5, 2026-11-01. See LICENSE and NOTICE.
 */
package app.morphe.extension.youtube.groups;

import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

/** Pure local projection; changing content filters cannot restart upload retrieval. */
public enum GroupContentFilter {
    ALL, TODAY, VIDEOS, SHORTS, LIVE, PODCASTS;
    public boolean matches(GroupVideo video, long now, ZoneId zone) {
        VideoMetadata m = video.metadata;
        switch (this) {
            case TODAY: return video.published >= Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
                    .atStartOfDay(zone).toInstant().toEpochMilli() && video.published <= now;
            case VIDEOS: return m.type == VideoMetadata.Type.VIDEO && m.live != VideoMetadata.Live.LIVE
                    && m.live != VideoMetadata.Live.UPCOMING;
            case SHORTS: return m.type == VideoMetadata.Type.SHORT;
            case LIVE: return m.type == VideoMetadata.Type.STREAM || m.live == VideoMetadata.Live.LIVE || m.live == VideoMetadata.Live.UPCOMING;
            case PODCASTS: return m.podcast;
            default: return true;
        }
    }
    public List<GroupVideo> apply(List<GroupVideo> videos, long now, ZoneId zone) {
        List<GroupVideo> result = new ArrayList<>();
        for (GroupVideo video : videos) if (matches(video, now, zone)) result.add(video);
        return result;
    }
    String tab() {
        if (this == SHORTS) return "shorts";
        if (this == LIVE) return "streams";
        if (this == PODCASTS) return "podcasts";
        return "videos";
    }
}
