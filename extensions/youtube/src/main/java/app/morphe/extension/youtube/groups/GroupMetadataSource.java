/* SPDX-License-Identifier: GPL-3.0-only
 * Independent Subscription Groups groups.5, 2026-11-01. See LICENSE and NOTICE.
 */
package app.morphe.extension.youtube.groups;

import android.content.Context;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;

/** Independent optional enrichment: Atom coverage can progress even if web metadata is unavailable. */
public final class GroupMetadataSource {
    static final ExecutorService WORKERS = Executors.newFixedThreadPool(2);
    private static GroupMetadataSource instance;
    public static synchronized GroupMetadataSource get(Context context) {
        if (instance == null) instance = new GroupMetadataSource(new File(context.getApplicationContext().getCacheDir(), "subscription-groups-metadata-v5"));
        return instance;
    }
    interface Loader { ChannelPageMetadata load(String channel, String tab) throws IOException; }
    private final File directory;
    private final Loader loader;
    private final Map<String, ChannelPageMetadata> memory = new LinkedHashMap<>(32, .75f, true);
    private final Map<String, CompletableFuture<ChannelPageMetadata>> flights = new ConcurrentHashMap<>();
    private final AtomicLong cooldown = new AtomicLong();
    private final Object diskLock = new Object();
    GroupMetadataSource(File directory) { this(directory, (channel, tab) -> {
        String html = new String(RecentFeedSource.get("https://www.youtube.com/channel/" + channel + "/" + tab + "?hl=en",
                ChannelPageMetadata.MAX_BYTES, "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 Chrome/131.0.0.0 Safari/537.36"), StandardCharsets.UTF_8);
        ChannelPageMetadata data = ChannelPageMetadata.parse(html, channel, tab, System.currentTimeMillis());
        if (tab.equals("podcasts") || tab.equals("shows")) {
            Map<String, VideoMetadata> episodes = new LinkedHashMap<>(data.videos);
            for (String playlist : data.podcastPlaylists) {
                String page = new String(RecentFeedSource.get("https://www.youtube.com/playlist?list=" + playlist + "&hl=en", ChannelPageMetadata.MAX_BYTES,
                        "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 Chrome/131.0.0.0 Safari/537.36"), StandardCharsets.UTF_8);
                episodes.putAll(ChannelPageMetadata.podcastEpisodes(page, playlist));
            }
            data = new ChannelPageMetadata(channel, data.avatar, episodes, data.tabs, data.fetchedAt, data.podcastPlaylists);
        }
        return data;
    }); }
    GroupMetadataSource(File directory, Loader loader) { this.directory = directory; this.loader = loader; }
    private static String key(String channel, String tab) {
        if (!GroupBackup.isChannelId(channel) || !List.of("videos", "shorts", "streams", "shows", "podcasts").contains(tab)) throw new IllegalArgumentException("Invalid metadata key");
        return channel + "-" + tab;
    }
    synchronized ChannelPageMetadata memory(String channel, String tab) { return memory.get(key(channel, tab)); }
    private synchronized void remember(String key, ChannelPageMetadata data) {
        memory.put(key, data); while (memory.size() > 1024) memory.remove(memory.keySet().iterator().next());
    }
    ChannelPageMetadata cached(String channel, String tab) {
        String key = key(channel, tab); ChannelPageMetadata data = memory(channel, tab); if (data != null) return data;
        synchronized (diskLock) {
            try {
                File file = new File(directory, key + ".json"); if (!file.isFile() || file.length() > 512 * 1024) return null;
                JSONObject json = new JSONObject(new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8));
                if (json.optInt("version") != 2 || !channel.equals(json.optString("channel")) || !tab.equals(json.optString("tab"))) return null;
                Map<String, VideoMetadata> videos = new LinkedHashMap<>(); JSONArray array = json.getJSONArray("videos");
                for (int i = 0; i < array.length(); i++) {
                    JSONObject v = array.getJSONObject(i); String id = v.getString("id"); if (!id.matches("[A-Za-z0-9_-]{11}")) throw new IOException("Invalid cached ID");
                    List<ThumbnailVariant> variants = new ArrayList<>(); JSONArray images = v.optJSONArray("thumbnails");
                    if (images != null) for (int j = 0; j < images.length(); j++) {
                        JSONObject image = images.getJSONObject(j); variants.add(new ThumbnailVariant(image.getString("url"), image.getInt("width"), image.getInt("height")));
                    }
                    videos.put(id, new VideoMetadata(v.getLong("duration"), VideoMetadata.Type.valueOf(v.getString("type")), VideoMetadata.Live.valueOf(v.getString("live")), v.getBoolean("podcast"), variants));
                }
                Map<String, String> tabs = new LinkedHashMap<>(); JSONObject savedTabs = json.getJSONObject("tabs");
                for (String type : List.of("videos", "shorts", "streams", "shows", "podcasts")) if (savedTabs.has(type)) tabs.put(type, savedTabs.getString(type));
                data = new ChannelPageMetadata(channel, json.optString("avatar"), videos, tabs, json.getLong("fetched")); remember(key, data); return data;
            } catch (Exception corrupted) { return null; }
        }
    }
    ChannelPageMetadata load(String channel, String tab, boolean force) throws IOException {
        String key = key(channel, tab); ChannelPageMetadata data = cached(channel, tab);
        if (!force && data != null && data.fresh(System.currentTimeMillis())) return data;
        CompletableFuture<ChannelPageMetadata> own = new CompletableFuture<>(), prior = flights.putIfAbsent(key, own);
        if (prior != null) {
            try { return prior.get(); }
            catch (InterruptedException canceled) { Thread.currentThread().interrupt(); throw new IOException("Canceled", canceled); }
            catch (ExecutionException error) { if (error.getCause() instanceof IOException) throw (IOException) error.getCause(); throw new IOException(error.getCause()); }
        }
        try {
            long delay = Math.max(RecentFeedSource.serverDelay(System.currentTimeMillis()), Math.max(0, cooldown.get() - System.currentTimeMillis())); if (delay > 0) throw new FeedSource.Failure("Metadata cooldown", true, delay);
            // A successful Videos response identifies absence of other public tabs. No blind requests.
            ChannelPageMetadata base = cached(channel, "videos");
            String actualTab = tab.equals("podcasts") && base != null && !base.tabs.containsKey("podcasts") && base.tabs.containsKey("shows") ? "shows" : tab;
            if (!actualTab.equals("videos") && base != null && base.fresh(System.currentTimeMillis()) && !base.tabs.containsKey(actualTab)) {
                data = new ChannelPageMetadata(channel, base.avatar, Map.of(), base.tabs, System.currentTimeMillis());
            } else data = loader.load(channel, actualTab);
            if (!channel.equals(data.channel)) throw new IOException("Metadata identity mismatch");
            remember(key, data); save(key, tab, data); own.complete(data); return data;
        } catch (IOException error) {
            if (error instanceof FeedSource.Failure) {
                FeedSource.Failure failure = (FeedSource.Failure) error;
                if (failure.retryAfterMillis > 0) cooldown.accumulateAndGet(System.currentTimeMillis() + failure.retryAfterMillis, Math::max);
            }
            own.completeExceptionally(error); throw error;
        } catch (RuntimeException error) { own.completeExceptionally(error); throw error; }
        finally { flights.remove(key, own); }
    }
    private void save(String key, String tab, ChannelPageMetadata data) {
        synchronized (diskLock) {
            try {
                JSONObject json = new JSONObject(); json.put("version", 2); json.put("channel", data.channel); json.put("tab", tab);
                json.put("fetched", data.fetchedAt); json.put("avatar", data.avatar); json.put("tabs", new JSONObject(data.tabs));
                JSONArray videos = new JSONArray();
                for (Map.Entry<String, VideoMetadata> entry : data.videos.entrySet()) {
                    VideoMetadata m = entry.getValue(); JSONObject v = new JSONObject(); v.put("id", entry.getKey()); v.put("duration", m.durationSeconds);
                    v.put("type", m.type.name()); v.put("live", m.live.name()); v.put("podcast", m.podcast);
                    JSONArray images = new JSONArray(); for (ThumbnailVariant image : m.thumbnails) images.put(new JSONObject().put("url", image.url).put("width", image.width).put("height", image.height));
                    v.put("thumbnails", images); videos.put(v);
                }
                json.put("videos", videos); byte[] bytes = json.toString().getBytes(StandardCharsets.UTF_8); if (bytes.length > 512 * 1024) return;
                directory.mkdirs(); File tmp = new File(directory, key + ".tmp"), target = new File(directory, key + ".json");
                Files.write(tmp.toPath(), bytes); Files.move(tmp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
                File[] files = directory.listFiles((dir, name) -> name.endsWith(".json")); if (files == null) return;
                Arrays.sort(files, Comparator.comparingLong(File::lastModified)); long size = 0; for (File file : files) size += file.length();
                int count = files.length; for (File file : files) { if (size <= 32L * 1024 * 1024 && count <= 4096) break; long length = file.length(); if (file.delete()) { size -= length; count--; } }
            } catch (Exception ignored) { /* Cache failure never prevents enrichment or playback. */ }
        }
    }
    public FeedSource forTab(String tab) {
        return new FeedSource() {
            public Window cached(String id) { ChannelPageMetadata m = GroupMetadataSource.this.cached(id, tab); return m == null ? null : new Window(List.of(), m.fetchedAt); }
            public Window memory(String id) { ChannelPageMetadata m = GroupMetadataSource.this.memory(id, tab); return m == null ? null : new Window(List.of(), m.fetchedAt); }
            public long delayMillis(long now) { return Math.max(RecentFeedSource.serverDelay(now), Math.max(0, cooldown.get() - now)); }
            public List<GroupVideo> recent(String id, boolean refresh) throws IOException { load(id, tab, refresh); return List.of(); }
        };
    }
    /** Memory-only, so rendering never touches disk or waits for a network request. */
    public GroupVideo enrich(GroupVideo video) {
        VideoMetadata metadata = video.metadata; String avatar = video.avatar;
        VideoMetadata.Type type = metadata.type; VideoMetadata.Live live = metadata.live;
        long avatarTime = -1, durationTime = -1, liveTime = -1, thumbnailTime = -1, duration = metadata.durationSeconds;
        List<ThumbnailVariant> thumbnails = metadata.thumbnails;
        boolean podcast = metadata.podcast;
        for (String tab : List.of("videos", "shows", "podcasts", "streams", "shorts")) {
            ChannelPageMetadata data = memory(video.channelId, tab); if (data == null) continue;
            if (!data.avatar.isEmpty() && data.fetchedAt >= avatarTime) { avatar = data.avatar; avatarTime = data.fetchedAt; }
            VideoMetadata found = data.videos.get(video.id);
            if (found != null) {
                podcast |= found.podcast;
                if (!found.thumbnails.isEmpty() && data.fetchedAt >= thumbnailTime) { thumbnails = found.thumbnails; thumbnailTime = data.fetchedAt; }
                if (found.type.ordinal() > type.ordinal()) type = found.type;
                if (found.durationSeconds > 0 && data.fetchedAt >= durationTime) { duration = found.durationSeconds; durationTime = data.fetchedAt; }
                if (found.live != VideoMetadata.Live.UNKNOWN && data.fetchedAt >= liveTime) {
                    live = found.live; liveTime = data.fetchedAt;
                    if (!data.fresh(System.currentTimeMillis()) && (live == VideoMetadata.Live.LIVE || live == VideoMetadata.Live.UPCOMING)) live = VideoMetadata.Live.UNKNOWN;
                }
            }
        }
        return video.withMetadata(avatar, new VideoMetadata(duration, type, live, podcast, thumbnails));
    }
    public List<GroupVideo> enrich(List<GroupVideo> videos) {
        List<GroupVideo> result = new ArrayList<>(videos.size()); for (GroupVideo video : videos) result.add(enrich(video)); return result;
    }
}
