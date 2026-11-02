/* SPDX-License-Identifier: GPL-3.0-only
 * Independent Subscription Groups groups.6, modified 2026-11-02. See LICENSE/NOTICE.
 */
package app.morphe.extension.youtube.groups;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/** Visible-card-only public metadata. No player, stream request, login, prefetch or history mutation. */
final class ThumbnailMetadataSource {
    interface Loader { List<ThumbnailVariant> load(String id, String channel) throws IOException; }
    private static ThumbnailMetadataSource instance;
    static synchronized ThumbnailMetadataSource get(Context context) {
        if (instance == null) instance = new ThumbnailMetadataSource(new File(context.getApplicationContext().getCacheDir(), "subscription-groups-thumbnail-metadata-v6"),
                (id, channel) -> ChannelPageMetadata.watchThumbnails(new String(RecentFeedSource.get("https://www.youtube.com/watch?v=" + id + "&hl=en", 8 * 1024 * 1024,
                        "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 Chrome/131.0.0.0 Safari/537.36"), StandardCharsets.UTF_8), id, channel));
        return instance;
    }
    private static final class Window {
        final List<ThumbnailVariant> variants; final long at, ttl;
        Window(List<ThumbnailVariant> variants, long at) { this(variants, at, variants.isEmpty() ? 5 * 60_000 : 3600_000); }
        Window(List<ThumbnailVariant> variants, long at, long ttl) { this.variants = java.util.Collections.unmodifiableList(new ArrayList<>(variants)); this.at = at; this.ttl = ttl; }
        boolean fresh() { return at <= System.currentTimeMillis() && System.currentTimeMillis() - at < ttl; }
    }
    private static final class Listener {
        final BooleanSupplier active; final Consumer<List<ThumbnailVariant>> callback;
        Listener(BooleanSupplier active, Consumer<List<ThumbnailVariant>> callback) { this.active = active; this.callback = callback; }
    }
    private final File directory; private final Loader loader;
    private final Object diskLock = new Object();
    private final Handler main = new Handler(Looper.getMainLooper());
    private final java.util.concurrent.ExecutorService workers;
    private final RecentFeedSession.Scheduler timer;
    private final Map<String, Window> memory = new LinkedHashMap<>(32, .75f, true);
    private final Map<String, List<Listener>> flights = new LinkedHashMap<>();
    ThumbnailMetadataSource(File directory, Loader loader) { this(directory, loader, Executors.newScheduledThreadPool(2)); }
    private ThumbnailMetadataSource(File directory, Loader loader, ScheduledExecutorService workers) {
        this(directory, loader, workers, new RecentFeedSession.Scheduler() {
            public long now() { return System.currentTimeMillis(); }
            public RecentFeedSession.Ticket after(long delay, Runnable action) { java.util.concurrent.Future<?> future = workers.schedule(action, delay, TimeUnit.MILLISECONDS); return () -> future.cancel(false); }
        });
    }
    ThumbnailMetadataSource(File directory, Loader loader, java.util.concurrent.ExecutorService workers, RecentFeedSession.Scheduler timer) {
        this.directory = directory; this.loader = loader; this.workers = workers; this.timer = timer;
    }
    void request(String id, String channel, BooleanSupplier active, Consumer<List<ThumbnailVariant>> callback) {
        if ((id == null || !id.matches("[A-Za-z0-9_-]{11}")) || !GroupBackup.isChannelId(channel) || !active.getAsBoolean()) return;
        String key = id + "-" + channel;
        synchronized (this) {
            Window value = memory.get(key);
            if (value != null) { callback.accept(value.variants); if (value.fresh()) return; }
            List<Listener> pending = flights.get(key);
            if (pending != null) { pending.add(new Listener(active, callback)); return; }
            pending = new ArrayList<>(); pending.add(new Listener(active, callback)); flights.put(key, pending);
        }
        workers.execute(() -> load(key, id, channel, 0));
    }
    private synchronized boolean active(String key) {
        List<Listener> values = flights.get(key); if (values == null) return false;
        values.removeIf(value -> !value.active.getAsBoolean());
        if (values.isEmpty()) { flights.remove(key); return false; } return true;
    }
    private void load(String key, String id, String channel, int attempts) {
        if (!active(key)) return;
        Window cached = attempts == 0 ? disk(key) : null;
        if (cached != null) { remember(key, cached); publish(key, cached, cached.fresh()); if (cached.fresh()) return; }
        long delay = RecentFeedSource.serverDelay(System.currentTimeMillis());
        if (delay > 0) { timer.after(delay, () -> load(key, id, channel, attempts)); return; }
        try {
            Window result = new Window(loader.load(id, channel), System.currentTimeMillis()); remember(key, result); save(key, result); publish(key, result, true);
        } catch (Exception failure) {
            boolean retry = !(failure instanceof FeedSource.Failure) || ((FeedSource.Failure) failure).retryable;
            if (retry && attempts < 2 && active(key)) {
                long requested = failure instanceof FeedSource.Failure ? ((FeedSource.Failure) failure).retryAfterMillis : 0;
                timer.after(Math.max(1000L << attempts, requested), () -> load(key, id, channel, attempts + 1));
            } else {
                synchronized (this) { cached = memory.get(key); }
                cached = new Window(cached == null ? List.of() : cached.variants, System.currentTimeMillis(), 5 * 60_000);
                remember(key, cached); publish(key, cached, true);
            }
        }
    }
    private synchronized void remember(String key, Window value) { memory.put(key, value); while (memory.size() > 256) memory.remove(memory.keySet().iterator().next()); }
    private void publish(String key, Window value, boolean terminal) {
        List<Listener> listeners;
        synchronized (this) { List<Listener> pending = terminal ? flights.remove(key) : flights.get(key); listeners = pending == null ? List.of() : new ArrayList<>(pending); }
        main.post(() -> { for (Listener listener : listeners) if (listener.active.getAsBoolean()) listener.callback.accept(value.variants); });
    }
    private Window disk(String key) {
        try {
            File file = new File(directory, key + ".json"); if (!file.isFile() || file.length() > 64 * 1024) return null;
            JSONObject json = new JSONObject(new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8));
            if (!key.equals(json.getString("key"))) return null;
            JSONArray images = json.getJSONArray("images"); List<ThumbnailVariant> values = new ArrayList<>();
            for (int i = 0; i < images.length(); i++) { JSONObject image = images.getJSONObject(i); values.add(new ThumbnailVariant(image.getString("url"), image.getInt("width"), image.getInt("height"))); }
            return new Window(values, json.getLong("at"));
        } catch (Exception failure) { return null; }
    }
    private void save(String key, Window value) {
        synchronized (diskLock) { try {
            JSONArray images = new JSONArray(); for (ThumbnailVariant v : value.variants) images.put(new JSONObject().put("url", v.url).put("width", v.width).put("height", v.height));
            byte[] data = new JSONObject().put("key", key).put("at", value.at).put("images", images).toString().getBytes(StandardCharsets.UTF_8); if (data.length > 64 * 1024) return;
            directory.mkdirs(); File file = new File(directory, key + ".json"), tmp = new File(directory, key + ".tmp"); Files.write(tmp.toPath(), data); Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING);
            File[] files = directory.listFiles((dir, name) -> name.endsWith(".json")); if (files == null) return;
            java.util.Arrays.sort(files, java.util.Comparator.comparingLong(File::lastModified)); long total = 0; for (File f : files) total += f.length();
            int count = files.length; for (File f : files) { if (total <= 8L * 1024 * 1024 && count <= 1024) break; long bytes = f.length(); if (f.delete()) { total -= bytes; count--; } }
        } catch (Exception ignored) { } }
    }
}
