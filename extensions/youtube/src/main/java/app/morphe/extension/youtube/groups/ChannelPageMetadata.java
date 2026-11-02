/* SPDX-License-Identifier: GPL-3.0-only
 * Independent Subscription Groups groups.5, 2026-11-01. See LICENSE and NOTICE.
 */
package app.morphe.extension.youtube.groups;

import org.json.JSONArray;
import org.json.JSONObject;
import java.io.IOException;
import java.net.URI;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.List;
import java.util.ArrayList;

/** Bounded public-page presentation metadata, never rounded publication dates or recommendations. */
public final class ChannelPageMetadata {
    public static final int MAX_BYTES = 4 * 1024 * 1024;
    public final String channel, avatar;
    public final Map<String, VideoMetadata> videos;
    public final Map<String, String> tabs;
    public final long fetchedAt;
    public final List<String> podcastPlaylists;
    public ChannelPageMetadata(String channel, String avatar, Map<String, VideoMetadata> videos,
                               Map<String, String> tabs, long fetchedAt) {
        this(channel, avatar, videos, tabs, fetchedAt, List.of());
    }
    public ChannelPageMetadata(String channel, String avatar, Map<String, VideoMetadata> videos,
                               Map<String, String> tabs, long fetchedAt, List<String> podcasts) {
        this.channel = channel; this.avatar = safeAvatar(avatar); this.fetchedAt = fetchedAt;
        this.videos = Collections.unmodifiableMap(new LinkedHashMap<>(videos));
        this.tabs = Collections.unmodifiableMap(new LinkedHashMap<>(tabs));
        podcastPlaylists = Collections.unmodifiableList(new ArrayList<>(podcasts));
    }
    public boolean fresh(long now) { return fetchedAt <= now && now - fetchedAt < 15 * 60 * 1000L; }
    public static String safeAvatar(String address) {
        try {
            URI uri = new URI(address); String host = uri.getHost();
            if ("https".equals(uri.getScheme()) && uri.getUserInfo() == null && (uri.getPort() == -1 || uri.getPort() == 443)
                    && ("yt3.googleusercontent.com".equals(host) || "yt3.ggpht.com".equals(host))) return address;
        } catch (Exception ignored) { }
        return "";
    }
    public static ChannelPageMetadata parse(String response, String channel, String tab, long now) throws IOException {
        if (!GroupBackup.isChannelId(channel) || response == null || response.length() > MAX_BYTES)
            throw new IOException("Invalid channel metadata");
        try {
            JSONObject root = new JSONObject(initialJson(response));
            JSONObject identity = object(root, "metadata", "channelMetadataRenderer");
            if (!channel.equals(identity.optString("externalId"))) throw new IOException("Metadata channel identity mismatch");
            String avatar = imageUrl(identity.optJSONObject("avatar"));
            if (avatar.isEmpty()) avatar = imageUrl(object(root, "header", "pageHeaderRenderer", "content", "pageHeaderViewModel",
                    "image", "decoratedAvatarViewModel", "avatar", "avatarViewModel", "image"));
            Map<String, String> tabs = new LinkedHashMap<>(); Map<String, VideoMetadata> videos = new LinkedHashMap<>();
            JSONObject selected = findTab(root.optJSONObject("contents"), channel, tabs, 0);
            if (selected == null) throw new IOException("No selected channel tab");
            String url = object(selected, "endpoint", "commandMetadata", "webCommandMetadata").optString("url");
            if (!url.endsWith("/" + tab)) throw new IOException("Channel tab unavailable: " + tab);
            collect(selected.optJSONObject("content"), tab, videos, 0);
            List<String> podcasts = new ArrayList<>(); findPodcasts(selected.optJSONObject("content"), podcasts, 0);
            return new ChannelPageMetadata(channel, avatar, videos, tabs, now, podcasts);
        } catch (IOException failure) { throw failure; }
        catch (Exception failure) { throw new IOException("Could not parse channel metadata", failure); }
    }
    /** Balanced JSON extraction handles braces, escaped quotes and script text inside JSON strings. */
    static String initialJson(String response) throws IOException {
        return initialJson(response, new String[]{"var ytInitialData =", "window[\"ytInitialData\"] =", "ytInitialData ="});
    }
    static String initialJson(String response, String[] markers) throws IOException {
        int start = response.trim().startsWith("{") ? response.indexOf('{') : -1;
        if (start < 0) for (String marker : markers) { int at = response.indexOf(marker); if (at >= 0) { start = response.indexOf('{', at + marker.length()); break; } }
        if (start < 0) throw new IOException("Missing initial channel metadata");
        boolean quoted = false, escaped = false; int depth = 0;
        for (int i = start; i < response.length(); i++) {
            char c = response.charAt(i);
            if (quoted) { if (escaped) escaped = false; else if (c == '\\') escaped = true; else if (c == '"') quoted = false; }
            else if (c == '"') quoted = true;
            else if (c == '{' || c == '[') { if (++depth > 128) throw new IOException("Metadata too deep"); }
            else if (c == '}' || c == ']') { if (--depth == 0) return response.substring(start, i + 1); }
        }
        throw new IOException("Truncated initial channel metadata");
    }
    private static JSONObject findTab(Object node, String channel, Map<String, String> tabs, int depth) {
        if (node == null || depth > 48) return null;
        JSONObject selected = null;
        if (node instanceof JSONObject) {
            JSONObject value = (JSONObject) node;
            JSONObject renderer = value.optJSONObject("tabRenderer");
            if (renderer != null) {
                JSONObject endpoint = object(renderer, "endpoint", "browseEndpoint");
                String url = object(renderer, "endpoint", "commandMetadata", "webCommandMetadata").optString("url");
                for (String type : new String[]{"videos", "shorts", "streams", "shows", "podcasts"})
                    if (channel.equals(endpoint.optString("browseId")) && url.endsWith("/" + type)) tabs.put(type, endpoint.optString("params"));
                return renderer.optBoolean("selected") ? renderer : null;
            }
            Iterator<String> keys = value.keys();
            while (keys.hasNext()) { JSONObject found = findTab(value.opt(keys.next()), channel, tabs, depth + 1); if (found != null) selected = found; }
        } else if (node instanceof JSONArray) {
            JSONArray array = (JSONArray) node;
            for (int i = 0; i < array.length(); i++) { JSONObject found = findTab(array.opt(i), channel, tabs, depth + 1); if (found != null) selected = found; }
        }
        return selected;
    }
    private static void collect(Object node, String tab, Map<String, VideoMetadata> results, int depth) {
        if (node == null || depth > 48) return;
        if (node instanceof JSONArray) {
            JSONArray array = (JSONArray) node; for (int i = 0; i < array.length(); i++) collect(array.opt(i), tab, results, depth + 1);
        } else if (node instanceof JSONObject) {
            JSONObject value = (JSONObject) node;
            for (String key : new String[]{"videoRenderer", "gridVideoRenderer", "videoWithContextRenderer", "playlistVideoRenderer", "reelItemRenderer", "shortsLockupViewModel", "lockupViewModel"}) {
                JSONObject renderer = value.optJSONObject(key);
                if (renderer != null) { video(renderer, key, tab, results); return; }
            }
            Iterator<String> keys = value.keys(); while (keys.hasNext()) collect(value.opt(keys.next()), tab, results, depth + 1);
        }
    }
    private static void findPodcasts(Object node, List<String> playlists, int depth) {
        if (node == null || depth > 48) return;
        if (node instanceof JSONObject) {
            JSONObject value = (JSONObject) node, lockup = value.optJSONObject("lockupViewModel");
            if (lockup != null) {
                String id = lockup.optString("contentId");
                if (lockup.optString("contentType").equals("LOCKUP_CONTENT_TYPE_PODCAST") && id.matches("[A-Za-z0-9_-]{10,128}") && !playlists.contains(id)) playlists.add(id);
                return;
            }
            Iterator<String> keys = value.keys(); while (keys.hasNext()) findPodcasts(value.opt(keys.next()), playlists, depth + 1);
        } else if (node instanceof JSONArray) { JSONArray array = (JSONArray) node; for (int i = 0; i < array.length(); i++) findPodcasts(array.opt(i), playlists, depth + 1); }
    }
    /** Membership in an explicitly labelled podcast playlist is reliable episode evidence. */
    public static Map<String, VideoMetadata> podcastEpisodes(String response, String playlist) throws IOException {
        if (response == null || response.length() > MAX_BYTES || !playlist.matches("[A-Za-z0-9_-]{10,128}")) throw new IOException("Invalid podcast response");
        try {
            JSONObject root = new JSONObject(initialJson(response));
            URI canonical = new URI(object(root, "microformat", "microformatDataRenderer").optString("urlCanonical"));
            if (!"www.youtube.com".equals(canonical.getHost()) || !"/playlist".equals(canonical.getPath())
                    || !("list=" + playlist).equals(canonical.getQuery())) throw new IOException("Podcast playlist identity mismatch");
            Map<String, VideoMetadata> videos = new LinkedHashMap<>(); collect(root.optJSONObject("contents"), "podcasts", videos, 0);
            Map<String, VideoMetadata> episodes = new LinkedHashMap<>();
            for (Map.Entry<String, VideoMetadata> entry : videos.entrySet()) {
                VideoMetadata m = entry.getValue(); episodes.put(entry.getKey(), new VideoMetadata(m.durationSeconds, m.type, m.live, true, m.thumbnails));
            }
            return episodes;
        } catch (IOException failure) { throw failure; }
        catch (Exception failure) { throw new IOException("Could not parse podcast membership", failure); }
    }
    private static void video(JSONObject value, String kind, String tab, Map<String, VideoMetadata> results) {
        String id = value.optString("videoId", value.optString("contentId"));
        boolean shorts = kind.equals("reelItemRenderer") || kind.equals("shortsLockupViewModel");
        if (shorts && id.isEmpty()) id = object(value, "onTap", "innertubeCommand", "reelWatchEndpoint").optString("videoId");
        if (!id.matches("[A-Za-z0-9_-]{11}")) return; // Playlist podcast cards are NOT episodes.
        if (kind.equals("lockupViewModel") && !"LOCKUP_CONTENT_TYPE_VIDEO".equals(value.optString("contentType"))) return;
        VideoMetadata.Type type = shorts || tab.equals("shorts") ? VideoMetadata.Type.SHORT
                : tab.equals("streams") ? VideoMetadata.Type.STREAM : tab.equals("videos") ? VideoMetadata.Type.VIDEO : VideoMetadata.Type.UNKNOWN;
        long seconds = VideoMetadata.parseDuration(text(value.optJSONObject("lengthText")));
        if (seconds < 0) seconds = VideoMetadata.parseDuration(text(value.optJSONObject("lengthTextRuns")));
        Badge badge = new Badge();
        badges(value.opt("thumbnailOverlays"), badge, 0);
        badges(value.opt("badges"), badge, 0);
        badges(object(value, "contentImage", "thumbnailViewModel").opt("overlays"), badge, 0);
        if (seconds < 0) seconds = badge.duration;
        boolean upcoming = value.has("upcomingEventData") || value.optBoolean("isUpcoming");
        VideoMetadata.Live live = upcoming ? VideoMetadata.Live.UPCOMING : badge.live ? VideoMetadata.Live.LIVE
                : type == VideoMetadata.Type.STREAM ? VideoMetadata.Live.ENDED : type == VideoMetadata.Type.VIDEO || type == VideoMetadata.Type.SHORT ? VideoMetadata.Live.NONE : VideoMetadata.Live.UNKNOWN;
        if (live == VideoMetadata.Live.LIVE || live == VideoMetadata.Live.UPCOMING) { type = VideoMetadata.Type.STREAM; seconds = -1; }
        List<ThumbnailVariant> images = new ArrayList<>();
        thumbnailSources(value.optJSONObject("thumbnail"), images);
        thumbnailSources(object(value, "contentImage", "thumbnailViewModel", "image"), images);
        if (shorts) thumbnailSources(object(value, "onTap", "innertubeCommand", "reelWatchEndpoint", "thumbnail"), images);
        results.put(id, new VideoMetadata(seconds, type, live, value.optBoolean("isPodcast") || badge.podcast, images));
    }
    private static void thumbnailSources(JSONObject image, List<ThumbnailVariant> variants) {
        if (image == null) return;
        JSONArray images = image.optJSONArray("thumbnails"); if (images == null) images = image.optJSONArray("sources");
        if (images == null) return;
        for (int i = 0; i < images.length(); i++) {
            JSONObject value = images.optJSONObject(i); if (value == null) continue;
            try { variants.add(new ThumbnailVariant(value.optString("url"), value.optInt("width"), value.optInt("height"))); }
            catch (IllegalArgumentException ignored) { }
        }
    }
    public static List<ThumbnailVariant> watchThumbnails(String response, String videoId, String channelId) throws IOException {
        if (response == null || response.length() > 8 * 1024 * 1024) throw new IOException("Invalid public watch metadata");
        try {
            JSONObject root = new JSONObject(initialJson(response, new String[]{"var ytInitialPlayerResponse =", "window[\"ytInitialPlayerResponse\"] =", "ytInitialPlayerResponse ="}));
            JSONObject video = object(root, "videoDetails");
            if (!videoId.equals(video.optString("videoId")) || !channelId.equals(video.optString("channelId")))
                throw new FeedSource.Failure("Watch metadata identity unavailable", false, 0);
            List<ThumbnailVariant> variants = new ArrayList<>(); thumbnailSources(video.optJSONObject("thumbnail"), variants); return variants;
        } catch (IOException failure) { throw failure; }
        catch (Exception failure) { throw new FeedSource.Failure("Public thumbnail metadata unavailable", false, 0); }
    }
    private static final class Badge { long duration = -1; boolean live, podcast; }
    private static void badges(Object node, Badge badge, int depth) {
        if (node == null || depth > 12) return;
        if (node instanceof JSONObject) {
            JSONObject value = (JSONObject) node;
            String style = value.optString("badgeStyle", value.optString("style"));
            if (style.equals("LIVE") || style.equals("BADGE_STYLE_TYPE_LIVE_NOW") || style.equals("THUMBNAIL_OVERLAY_BADGE_STYLE_LIVE")) badge.live = true;
            if (style.equals("BADGE_STYLE_TYPE_PODCAST") || style.equals("THUMBNAIL_OVERLAY_BADGE_STYLE_PODCAST")) badge.podcast = true;
            Object raw = value.opt("text"); String text = raw instanceof String ? (String) raw : raw instanceof JSONObject ? text((JSONObject) raw) : "";
            long duration = VideoMetadata.parseDuration(text); if (duration > 0 && badge.duration < 0) badge.duration = duration;
            Iterator<String> keys = value.keys(); while (keys.hasNext()) badges(value.opt(keys.next()), badge, depth + 1);
        } else if (node instanceof JSONArray) { JSONArray array = (JSONArray) node; for (int i = 0; i < array.length(); i++) badges(array.opt(i), badge, depth + 1); }
    }
    private static String imageUrl(JSONObject image) {
        if (image == null) return "";
        JSONArray sources = image.optJSONArray("thumbnails"); if (sources == null) sources = image.optJSONArray("sources");
        if (sources != null) for (int i = 0; i < sources.length(); i++) {
            JSONObject value = sources.optJSONObject(i); String url = value == null ? "" : safeAvatar(value.optString("url"));
            if (!url.isEmpty()) return url;
        }
        return "";
    }
    static JSONObject object(JSONObject value, String... path) {
        for (String key : path) { value = value == null ? null : value.optJSONObject(key); if (value == null) return new JSONObject(); }
        return value;
    }
    private static String text(JSONObject value) {
        if (value == null) return "";
        if (value.has("simpleText")) return value.optString("simpleText");
        JSONArray runs = value.optJSONArray("runs"); StringBuilder text = new StringBuilder();
        if (runs != null) for (int i = 0; i < runs.length(); i++) { JSONObject run = runs.optJSONObject(i); if (run != null) text.append(run.optString("text")); }
        return text.toString();
    }
}
