/* SPDX-License-Identifier: GPL-3.0-only
 * Independent Subscription Groups V2, 2026-11-01. See LICENSE and NOTICE.
 */
package app.morphe.extension.youtube.groups;

import android.content.Context;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicLong;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

/** YouTube-owned public Atom windows with a bounded private disk cache. No credentials or scraping. */
public final class RecentFeedSource implements FeedSource {
    private static final AtomicLong youtubeCooldown = new AtomicLong();
    static long serverDelay(long now) { return Math.max(0, youtubeCooldown.get() - now); }
    public static final ThreadPoolExecutor WORKERS = (ThreadPoolExecutor) Executors.newFixedThreadPool(4);
    private static RecentFeedSource instance;
    public static synchronized RecentFeedSource get(Context context) {
        if (instance == null) instance = new RecentFeedSource(context.getApplicationContext()); return instance;
    }
    private final ChannelFeedCache cache;
    private final Map<String, CompletableFuture<List<GroupVideo>>> flights = new ConcurrentHashMap<>();
    private final AtomicLong cooldownUntil = new AtomicLong();
    private final Map<String, String> channelNames = new ConcurrentHashMap<>();
    private RecentFeedSource(Context context) { this(new File(context.getCacheDir(), "subscription-groups-rss-v2")); }
    RecentFeedSource(File cacheDirectory) { cache = new ChannelFeedCache(cacheDirectory); }
    @Override public FeedSource.Window cached(String channel) {
        FeedSource.Window window = cache.get(channel); if (window != null) remember(channel, window.videos); return window;
    }
    @Override public FeedSource.Window memory(String channel) { return cache.memory(channel); }
    @Override public long delayMillis(long now) { return Math.max(serverDelay(now), Math.max(0, cooldownUntil.get() - now)); }
    @Override public List<GroupVideo> recent(String channel, boolean refresh) throws IOException {
        if (!GroupBackup.isChannelId(channel)) throw new IOException("Invalid channel ID");
        FeedSource.Window previous = cached(channel);
        if (!refresh && previous != null && previous.fresh(System.currentTimeMillis())) return previous.videos;
        CompletableFuture<List<GroupVideo>> own = new CompletableFuture<>(), existing = flights.putIfAbsent(channel, own);
        if (existing != null) {
            try { return existing.get(); }
            catch (InterruptedException canceled) { Thread.currentThread().interrupt(); throw new IOException("Canceled", canceled); }
            catch (ExecutionException failure) {
                if (failure.getCause() instanceof IOException) throw (IOException) failure.getCause();
                throw new IOException("Channel request failed", failure.getCause());
            }
        }
        try {
            long delay = delayMillis(System.currentTimeMillis());
            if (delay > 0) throw new FeedSource.Failure("Server cooldown", true, delay);
            List<GroupVideo> result = download(channel); own.complete(result); return result;
        } catch (IOException failure) { own.completeExceptionally(failure); throw failure; }
        finally { flights.remove(channel, own); }
    }
    private List<GroupVideo> download(String channel) throws IOException {
        byte[] data;
        try { data = get("https://www.youtube.com/feeds/videos.xml?channel_id=" + channel, YouTubeAtomFeed.MAX_BYTES); }
        catch (FeedSource.Failure failure) {
            if (failure.retryable && failure.retryAfterMillis > 0) cooldownUntil.accumulateAndGet(System.currentTimeMillis() + failure.retryAfterMillis, Math::max);
            throw failure;
        }
        String xml = new String(data, StandardCharsets.UTF_8); List<GroupVideo> result = YouTubeAtomFeed.parse(xml, channel);
        remember(channel, result);
        cache.put(channel, data, result, System.currentTimeMillis());
        return result;
    }
    private void remember(String channel, List<GroupVideo> result) {
        if (!result.isEmpty() && !result.get(0).channelName.isEmpty()) channelNames.put(channel, result.get(0).channelName);
    }
    public String channelName(String channel) { String name = channelNames.get(channel); return name == null ? channel : name; }
    public void cachedNames(List<String> channels, Runnable complete) {
        WORKERS.execute(() -> {
            for (String id : channels) {
                if (channelNames.containsKey(id) || !GroupBackup.isChannelId(id)) continue;
                cached(id);
            }
            complete.run();
        });
    }
    static byte[] get(String address, int limit) throws IOException {
        return get(address, limit, "SubscriptionGroups/2 (public YouTube feeds)");
    }
    static byte[] get(String address, int limit, String userAgent) throws IOException {
        if (Thread.currentThread().isInterrupted()) throw new IOException("Canceled");
        URL target = new URL(address);
        boolean youtube = target.getHost().equals("www.youtube.com");
        long waiting = youtube ? serverDelay(System.currentTimeMillis()) : 0;
        if (waiting > 0) throw new FeedSource.Failure("Server cooldown", true, waiting);
        HttpURLConnection connection = (HttpURLConnection) target.openConnection();
        connection.setConnectTimeout(10000); connection.setReadTimeout(10000); connection.setInstanceFollowRedirects(false);
        connection.setUseCaches(false);
        connection.setRequestProperty("User-Agent", userAgent);
        try {
            int status = connection.getResponseCode();
            if (status != 200) {
                boolean transientFailure = status == 408 || status == 425 || status == 429 || status >= 500;
                long retryAfter = retryAfter(connection.getHeaderField("Retry-After"), System.currentTimeMillis());
                if (status == 429 && retryAfter == 0) retryAfter = 5000;
                if (youtube && transientFailure && retryAfter > 0) youtubeCooldown.accumulateAndGet(System.currentTimeMillis() + retryAfter, Math::max);
                throw new FeedSource.Failure("Channel unavailable (HTTP " + status + ")", transientFailure, retryAfter);
            }
            if (connection.getContentLengthLong() > limit) throw new IOException("Response too large");
            try (InputStream in = connection.getInputStream()) { return bytes(in, limit); }
        } finally { connection.disconnect(); }
    }
    static long retryAfter(String value, long now) {
        if (value == null) return 0;
        try { return Math.max(0, Math.multiplyExact(Long.parseLong(value.trim()), 1000)); }
        catch (Exception notSeconds) {
            try { return Math.max(0, ZonedDateTime.parse(value, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant().toEpochMilli() - now); }
            catch (Exception invalid) { return 0; }
        }
    }
    static byte[] bytes(InputStream in, int limit) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream(); byte[] buffer = new byte[8192]; int count;
        while ((count = in.read(buffer)) != -1) {
            if (Thread.currentThread().isInterrupted()) throw new IOException("Canceled");
            if (out.size() + count > limit) throw new IOException("Response too large"); out.write(buffer, 0, count);
        }
        return out.toByteArray();
    }
    private static String utf8(InputStream input, int limit) throws IOException { return new String(bytes(input, limit), StandardCharsets.UTF_8); }
}
