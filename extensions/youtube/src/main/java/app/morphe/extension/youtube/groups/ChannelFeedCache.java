/* SPDX-License-Identifier: GPL-3.0-only
 * Independent Subscription Groups V7, 2026-11-02. See LICENSE and NOTICE.
 */
package app.morphe.extension.youtube.groups;

import org.json.JSONArray;
import org.json.JSONObject;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;

/** Versioned per-channel history cache. Bounded by 500 retained videos/channel, 2048 channels and 64 MiB. */
final class ChannelFeedCache {
    static final int SCHEMA_VERSION = 1, MAX_VIDEOS_PER_CHANNEL = 500;
    private static final long MAX_CACHE_BYTES = 64L * 1024 * 1024;
    private static final int MAX_CHANNELS = 2048, MAX_HISTORY_FILE_BYTES = 4 * 1024 * 1024;
    private static final Comparator<GroupVideo> NEWEST = Comparator
            .comparingLong((GroupVideo video) -> video.published).reversed().thenComparing(video -> video.id);
    private final File directory, legacyDirectory;
    private final LinkedHashMap<String, FeedSource.Window> memory = new LinkedHashMap<>(64, 0.75f, true);
    private int writes;
    private final Object memoryLock = new Object(), diskLock = new Object();

    ChannelFeedCache(File directory) { this(directory, null); }
    ChannelFeedCache(File directory, File legacyDirectory) { this.directory = directory; this.legacyDirectory = legacyDirectory; }

    FeedSource.Window memory(String channel) { synchronized (memoryLock) { return memory.get(channel); } }

    void recordAttempt(String channel, long attemptedAt) {
        if (!GroupBackup.isChannelId(channel)) return;
        synchronized (diskLock) {
            File file = new File(directory, channel + ".json");
            try {
                if (!directory.isDirectory() && !directory.mkdirs()) return;
                if (file.isFile() && file.length() <= MAX_HISTORY_FILE_BYTES) {
                    JSONObject root = new JSONObject(new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8));
                    if (root.optInt("schemaVersion", -1) == SCHEMA_VERSION && channel.equals(root.optString("channelId"))) {
                        root.put("lastAttemptedRefreshAt", attemptedAt);
                        GroupStore.atomicWrite(file, root.toString().getBytes(StandardCharsets.UTF_8));
                        return;
                    }
                }
                FeedSource.Window previous = get(channel);
                if (previous != null) writeHistory(channel, previous.videos, attemptedAt, previous.fetchedAt);
            } catch (Exception ignored) { /* Diagnostics are best-effort and cannot block retrieval. */ }
        }
    }

    FeedSource.Window get(String channel) {
        if (!GroupBackup.isChannelId(channel)) return null;
        FeedSource.Window hit = memory(channel); if (hit != null) return hit;
        File history = new File(directory, channel + ".json");
        if (history.isFile()) {
            try {
                if (history.length() > MAX_HISTORY_FILE_BYTES) return null;
                JSONObject root = new JSONObject(new String(Files.readAllBytes(history.toPath()), StandardCharsets.UTF_8));
                if (root.optInt("schemaVersion", -1) != SCHEMA_VERSION || !channel.equals(root.optString("channelId"))) throw new IOException("Unsupported history record");
                JSONArray entries = root.getJSONArray("videos"); List<GroupVideo> videos = new ArrayList<>();
                for (int i = 0; i < entries.length() && videos.size() < MAX_VIDEOS_PER_CHANNEL; i++) {
                    JSONObject entry = entries.optJSONObject(i); if (entry == null) continue;
                    try {
                        videos.add(new GroupVideo(entry.getString("id"), channel, entry.optString("title"),
                                entry.optString("channelName"), entry.optString("thumbnail"), entry.getLong("published"),
                                entry.optLong("views", -1)));
                    } catch (RuntimeException | org.json.JSONException invalid) { /* Skip corrupt entries, keep valid history. */ }
                }
                FeedSource.Window result = new FeedSource.Window(videos, root.optLong("lastSuccessfulRefreshAt", history.lastModified()));
                synchronized (memoryLock) { FeedSource.Window current = memory.get(channel); if (current != null) return current; remember(channel, result); return result; }
            } catch (Exception invalid) { /* Try the V6 Atom cache below if one is available. */ }
        }
        // Read V6's finite Atom cache during migration, then the next refresh writes the versioned history file.
        File legacy = new File(directory, channel + ".xml");
        if (!legacy.isFile() && legacyDirectory != null) legacy = new File(legacyDirectory, channel + ".xml");
        if (!legacy.isFile() || legacy.length() > YouTubeAtomFeed.MAX_BYTES) return null;
        try {
            List<GroupVideo> videos = YouTubeAtomFeed.parse(new String(Files.readAllBytes(legacy.toPath()), StandardCharsets.UTF_8), channel);
            FeedSource.Window result = new FeedSource.Window(videos, legacy.lastModified());
            synchronized (memoryLock) { FeedSource.Window current = memory.get(channel); if (current != null) return current; remember(channel, result); return result; }
        } catch (IOException invalid) { return null; }
    }

    void put(String channel, byte[] ignoredXml, List<GroupVideo> incoming, long fetchedAt) {
        if (!GroupBackup.isChannelId(channel)) return;
        synchronized (diskLock) {
            FeedSource.Window previous = get(channel);
            LinkedHashMap<String, GroupVideo> merged = new LinkedHashMap<>();
            if (previous != null) for (GroupVideo video : previous.videos) if (video.channelId.equals(channel)) merged.put(video.id, video);
            for (GroupVideo video : incoming) if (video.channelId.equals(channel)) {
                GroupVideo existing = merged.get(video.id);
                // Keep the first validated publication timestamp; refresh presentation fields from the source.
                merged.put(video.id, existing == null ? video : new GroupVideo(video.id, channel, video.title,
                        video.channelName, video.thumbnail, existing.published, video.views, video.avatar, video.metadata));
            }
            List<GroupVideo> retained = new ArrayList<>(merged.values()); retained.sort(NEWEST);
            if (retained.size() > MAX_VIDEOS_PER_CHANNEL) retained = new ArrayList<>(retained.subList(0, MAX_VIDEOS_PER_CHANNEL));
            FeedSource.Window result = new FeedSource.Window(retained, fetchedAt);
            synchronized (memoryLock) { remember(channel, result); }
            try {
                if (!directory.isDirectory() && !directory.mkdirs()) return;
                writeHistory(channel, retained, fetchedAt, fetchedAt);
                new File(directory, channel + ".json").setLastModified(fetchedAt);
                if (++writes % 12 == 0) trim();
            } catch (Exception ignored) { /* Cache failures never discard the successful network result. */ }
        }
    }

    private void writeHistory(String channel, List<GroupVideo> videos, long attemptedAt, long successfulAt) throws Exception {
        JSONObject root = new JSONObject().put("schemaVersion", SCHEMA_VERSION).put("channelId", channel)
                .put("lastAttemptedRefreshAt", attemptedAt).put("lastSuccessfulRefreshAt", successfulAt)
                .put("newestObservedUploadAt", videos.isEmpty() ? 0 : videos.get(0).published)
                .put("oldestRetainedUploadAt", videos.isEmpty() ? 0 : videos.get(videos.size() - 1).published)
                .put("coverageStatus", "finite_recent_window_with_locally_retained_history")
                .put("paginationCursor", JSONObject.NULL);
        JSONArray entries = new JSONArray();
        for (GroupVideo video : videos) entries.put(new JSONObject().put("id", video.id).put("title", video.title)
                .put("channelName", video.channelName).put("thumbnail", video.thumbnail).put("published", video.published)
                .put("views", video.views));
        root.put("videos", entries);
        byte[] bytes = root.toString().getBytes(StandardCharsets.UTF_8);
        if (bytes.length > MAX_HISTORY_FILE_BYTES) throw new IOException("Channel history exceeds size limit");
        GroupStore.atomicWrite(new File(directory, channel + ".json"), bytes);
    }

    private void remember(String channel, FeedSource.Window window) {
        memory.put(channel, window);
        while (memory.size() > 256) memory.remove(memory.keySet().iterator().next());
    }
    private void trim() {
        File[] files = directory.listFiles((dir, name) -> name.endsWith(".json") || name.endsWith(".xml")); if (files == null) return;
        Arrays.sort(files, Comparator.comparingLong(File::lastModified).reversed()); long bytes = 0;
        for (int i = 0; i < files.length; i++) {
            bytes += files[i].length();
            if (i >= MAX_CHANNELS || bytes > MAX_CACHE_BYTES) try { Files.deleteIfExists(files[i].toPath()); } catch (IOException ignored) { }
        }
    }
}
