/* SPDX-License-Identifier: GPL-3.0-only
 * Independent Subscription Groups groups.5, 2026-11-01. See LICENSE and NOTICE.
 */
package app.morphe.extension.youtube.groups;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Looper;
import android.util.LruCache;
import android.widget.ImageView;
import android.view.View;
import java.io.File;
import java.lang.ref.WeakReference;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Shared channel-image flights and identity-bound delivery; recycled rows never receive stale images. */
final class GroupImages {
    interface Downloader { byte[] get(String url) throws java.io.IOException; }
    private static final GroupImages DEFAULT = new GroupImages(Executors.newFixedThreadPool(2), new Handler(Looper.getMainLooper()), url -> RecentFeedSource.get(
            app.morphe.extension.youtube.patches.BypassImageRegionRestrictionsPatch.overrideImageURL(url), 2 * 1024 * 1024), true);
    private final java.util.concurrent.Executor workers;
    private final Handler main;
    private final Downloader downloader;
    private final boolean upgradeMetadata;
    GroupImages(java.util.concurrent.Executor workers, Handler main, Downloader downloader) { this(workers, main, downloader, false); }
    private GroupImages(java.util.concurrent.Executor workers, Handler main, Downloader downloader, boolean upgradeMetadata) {
        this.workers = workers; this.main = main; this.downloader = downloader; this.upgradeMetadata = upgradeMetadata;
    }
    private final LruCache<String, Bitmap> memory = new LruCache<String, Bitmap>(24 * 1024 * 1024) {
        @Override protected int sizeOf(String key, Bitmap value) { return value.getByteCount(); }
    };
    private final Map<String, List<Target>> flights = new HashMap<>();
    private final Map<String, Long> failed = new HashMap<>();
    private static final Object diskLock = new Object();
    private static final class Target {
        final WeakReference<ImageView> image; final Object token; final Runnable failure;
        Target(ImageView image, Object token, Runnable failure) { this.image = new WeakReference<>(image); this.token = token; this.failure = failure; }
        void failed() { ImageView view = image.get(); if (view != null && view.getTag() == token && failure != null) failure.run(); }
        void deliver(Bitmap bitmap) { ImageView view = image.get(); if (view != null && view.getTag() == token) {
            if (token instanceof VideoToken) { VideoToken video = (VideoToken) token; if (bitmap.getWidth() < video.quality) return; video.quality = bitmap.getWidth(); }
            view.setImageBitmap(bitmap);
        } }
    }
    static void bind(ImageView image, String address, boolean avatar) { DEFAULT.bindImage(image, address, avatar); }
    private static final class VideoToken implements View.OnAttachStateChangeListener {
        final String id; volatile boolean active; int quality;
        VideoToken(String id) { this.id = id; }
        public void onViewAttachedToWindow(View view) { if (view.getTag() == this) active = true; }
        public void onViewDetachedFromWindow(View view) { active = false; }
    }
    static void cancelThumbnails(View view) {
        Object tag = view.getTag(); if (tag instanceof VideoToken) ((VideoToken) tag).active = false;
        if (view instanceof android.view.ViewGroup) { android.view.ViewGroup group = (android.view.ViewGroup) view; for (int i = 0; i < group.getChildCount(); i++) cancelThumbnails(group.getChildAt(i)); }
    }
    static void bindThumbnail(ImageView image, GroupVideo video, boolean publicMetadata) { DEFAULT.thumbnail(image, video, publicMetadata); }
    void thumbnail(ImageView image, GroupVideo video) { thumbnail(image, video, true); }
    private void thumbnail(ImageView image, GroupVideo video, boolean publicMetadata) {
        Object old = image.getTag(); VideoToken token = new VideoToken(video.id);
        if (old instanceof VideoToken) { ((VideoToken) old).active = false; image.removeOnAttachStateChangeListener((VideoToken) old);
            if (((VideoToken) old).id.equals(video.id)) token.quality = ((VideoToken) old).quality;
        }
        if (!(old instanceof VideoToken) || !((VideoToken) old).id.equals(video.id)) image.setImageDrawable(null);
        image.setTag(token); image.addOnAttachStateChangeListener(token);
        int width = image.getWidth() > 0 ? image.getWidth() : image.getResources().getDisplayMetrics().widthPixels;
        List<String> urls = ThumbnailVariant.order(video.metadata.thumbnails, Math.min(2048, Math.max(1, width)), video.thumbnail);
        for (String url : urls) { Bitmap preview = memory.get(url); if (preview != null && preview.getWidth() >= token.quality) { image.setImageBitmap(preview); token.quality = preview.getWidth(); break; } }
        WeakReference<ImageView> reference = new WeakReference<>(image);
        thumbnailCandidate(reference, token, urls, 0);
        int best = 0; for (ThumbnailVariant v : video.metadata.thumbnails) best = Math.max(best, v.width);
        if (upgradeMetadata && publicMetadata && best < Math.min(2048, Math.max(1, width))) image.post(() -> {
            ImageView target = reference.get(); if (target == null || target.getTag() != token || !target.isAttachedToWindow() || !target.isShown()) return;
            token.active = true;
            ThumbnailMetadataSource.get(target.getContext()).request(video.id, video.channelId, () -> token.active, variants -> {
                ImageView current = reference.get(); if (current == null || current.getTag() != token || !token.active) return;
                List<ThumbnailVariant> combined = new ArrayList<>(video.metadata.thumbnails); combined.addAll(variants);
                thumbnailCandidate(reference, token, ThumbnailVariant.order(combined, Math.min(2048, Math.max(1, width)), video.thumbnail), 0);
            });
        });
    }
    private void thumbnailCandidate(WeakReference<ImageView> ref, Object token, List<String> urls, int index) {
        ImageView image = ref.get(); if (image == null || image.getTag() != token || index >= urls.size()) return;
        request(image, urls.get(index), false, token, () -> thumbnailCandidate(ref, token, urls, index + 1));
    }
    void bindImage(ImageView image, String address, boolean avatar) {
        Object previous = image.getTag(); if (previous instanceof VideoToken) { ((VideoToken) previous).active = false; image.removeOnAttachStateChangeListener((VideoToken) previous); }
        Object token = new Object(); image.setTag(token); image.setImageDrawable(null);
        request(image, address, avatar, token, null);
    }
    private void request(ImageView image, String address, boolean avatar, Object token, Runnable failure) {
        String url = avatar ? ChannelPageMetadata.safeAvatar(address) : YouTubeAtomFeed.safeThumbnail(address);
        if (url.isEmpty()) { if (failure != null) main.post(failure); return; }
        Target target = new Target(image, token, failure);
        Bitmap bitmap = memory.get(url); if (bitmap != null) { target.deliver(bitmap); return; }
        synchronized (flights) {
            Long retry = failed.get(url); if (retry != null && retry > System.currentTimeMillis()) { if (failure != null) main.post(failure); return; }
            List<Target> pending = flights.get(url); if (pending != null) { pending.add(target); return; }
            pending = new ArrayList<>(); pending.add(target); flights.put(url, pending);
        }
        File directory = new File(image.getContext().getApplicationContext().getCacheDir(), "subscription-groups-images-v5");
        workers.execute(() -> {
            Bitmap result = null;
            try {
                byte[] data = cachedBytes(directory, url);
                if (data != null) result = decode(data, avatar ? 128 : 2048);
                if (result == null) {
                    data = downloader.get(url); result = decode(data, avatar ? 128 : 2048);
                    if (result != null) saveBytes(directory, url, data);
                }
                if (result != null) memory.put(url, result);
            } catch (Exception ignored) { /* Initials/neutral thumbnail remain visible. */ }
            List<Target> targets;
            synchronized (flights) {
                targets = flights.remove(url);
                if (result == null) { failed.put(url, System.currentTimeMillis() + 60_000); if (failed.size() > 512) failed.clear(); }
                else failed.remove(url);
            }
            Bitmap ready = result;
            if (targets != null) main.post(() -> { for (Target value : targets) { if (ready != null) value.deliver(ready); else value.failed(); } });
        });
    }
    static Bitmap decode(byte[] data, int size) {
        BitmapFactory.Options options = new BitmapFactory.Options(); options.inJustDecodeBounds = true;
        BitmapFactory.decodeByteArray(data, 0, data.length, options);
        if (options.outWidth < 1 || options.outHeight < 1 || options.outWidth > 8192 || options.outHeight > 8192) return null;
        options.inJustDecodeBounds = false; options.inSampleSize = ThumbnailVariant.sampleSize(options.outWidth, options.outHeight, size);
        return BitmapFactory.decodeByteArray(data, 0, data.length, options);
    }
    private static String fileName(String url) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(url.getBytes(StandardCharsets.UTF_8));
        StringBuilder out = new StringBuilder(); for (byte value : digest) out.append(String.format(java.util.Locale.ROOT, "%02x", value & 255)); return out + ".img";
    }
    private static byte[] cachedBytes(File directory, String url) throws Exception {
        synchronized (diskLock) {
            File file = new File(directory, fileName(url));
            if (!file.isFile() || file.length() > 2 * 1024 * 1024 || System.currentTimeMillis() - file.lastModified() > 7L * 86400000) return null;
            return Files.readAllBytes(file.toPath());
        }
    }
    private static void saveBytes(File directory, String url, byte[] data) {
        synchronized (diskLock) {
            try {
                directory.mkdirs(); String name = fileName(url); File file = new File(directory, name), tmp = new File(directory, name + ".tmp");
                Files.write(tmp.toPath(), data); Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING);
                File[] files = directory.listFiles((dir, child) -> child.endsWith(".img")); if (files == null) return;
                Arrays.sort(files, Comparator.comparingLong(File::lastModified)); long size = 0; for (File item : files) size += item.length();
                for (File item : files) { if (size <= 32L * 1024 * 1024) break; long length = item.length(); if (item.delete()) size -= length; }
            } catch (Exception ignored) { }
        }
    }
}
