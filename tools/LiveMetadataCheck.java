/* SPDX-License-Identifier: GPL-3.0-only
 * Independent Subscription Groups groups.5, 2026-11-01. See LICENSE and NOTICE.
 */
package app.morphe.extension.youtube.groups;

import java.io.File;
import java.util.List;

/** Optional public-network desktop smoke check; not a deterministic unit test or Android verification. */
public final class LiveMetadataCheck {
    public static void main(String[] args) throws Exception {
        GroupMetadataSource source = new GroupMetadataSource(new File(args[0]));
        RecentFeedSource uploads = new RecentFeedSource(new File(args[0], "rss"));
        String channel = "UCBJycsmduvYEL83R_U4JriQ";
        for (String tab : List.of("videos", "shorts", "streams")) {
            ChannelPageMetadata data = source.load(channel, tab, false);
            if (data.avatar.isEmpty() || data.videos.isEmpty()) throw new AssertionError("No live metadata for " + tab);
            long durations = data.videos.values().stream().filter(m -> m.durationSeconds > 0).count();
            System.out.println("Public " + tab + ": " + data.videos.size() + " explicit IDs, " + durations + " exact duration badges, trusted channel avatar present.");
        }
        List<GroupVideo> enriched = source.enrich(uploads.recent(channel, false));
        long avatars = enriched.stream().filter(v -> !v.avatar.isEmpty()).count(), duration = enriched.stream().filter(v -> v.metadata.durationSeconds > 0).count();
        long shorts = enriched.stream().filter(v -> v.metadata.type == VideoMetadata.Type.SHORT).count();
        if (avatars != enriched.size() || duration == 0) throw new AssertionError("Recent uploads did not join with actual metadata");
        System.out.println("Atom join: " + enriched.size() + " uploads, " + avatars + " avatars, " + duration + " durations, " + shorts + " explicit Shorts; publication timestamps retained.");
        String podcastChannel = "UCSHZKyawb77ixDdsGog4iWA";
        source.load(podcastChannel, "videos", false);
        ChannelPageMetadata podcast = source.load(podcastChannel, "podcasts", false);
        long episodes = podcast.videos.values().stream().filter(m -> m.podcast).count();
        if (episodes == 0) throw new AssertionError("Explicit podcast playlist membership was not resolved");
        long recentEpisodes = source.enrich(uploads.recent(podcastChannel, false)).stream().filter(v -> v.metadata.podcast).count();
        if (recentEpisodes == 0) throw new AssertionError("Public recent episodes did not match podcast membership");
        System.out.println("Podcasts: " + episodes + " explicit first-page playlist episode IDs; " + recentEpisodes + " match current Atom uploads.");
        System.out.println("Desktop public-source sample only. Not the user's 155 channels, not full history, not target Android runtime.");
    }
}
