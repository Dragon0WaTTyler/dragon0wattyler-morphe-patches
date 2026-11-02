/* SPDX-License-Identifier: GPL-3.0-only
 * Independent Subscription Groups V2, 2026-11-01. See LICENSE and NOTICE.
 */
package app.morphe.extension.youtube.groups;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Looper;
import android.util.LruCache;
import android.widget.ImageView;
import java.lang.ref.WeakReference;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

final class GroupThumbnails {
    private static final ExecutorService worker = Executors.newFixedThreadPool(2);
    private static final Handler main = new Handler(Looper.getMainLooper());
    private static final LruCache<String, Bitmap> cache = new LruCache<String, Bitmap>(8 * 1024 * 1024) {
        @Override protected int sizeOf(String key, Bitmap bitmap) { return bitmap.getByteCount(); }
    };
    static void bind(ImageView image, String address) {
        image.setTag(address); image.setImageDrawable(null); image.setBackgroundColor(GroupUi.control());
        String safe = YouTubeAtomFeed.safeThumbnail(address); if (safe.isEmpty()) return;
        Bitmap cached = cache.get(safe); if (cached != null) { image.setImageBitmap(cached); return; }
        WeakReference<ImageView> reference = new WeakReference<>(image);
        worker.execute(() -> {
            try {
                ImageView current = reference.get();
                if (current == null || !address.equals(current.getTag())) return;
                byte[] data = RecentFeedSource.get(safe, 512 * 1024);
                BitmapFactory.Options options = new BitmapFactory.Options(); options.inJustDecodeBounds = true;
                BitmapFactory.decodeByteArray(data, 0, data.length, options);
                if (options.outWidth < 1 || options.outHeight < 1 || options.outWidth > 4096 || options.outHeight > 4096) return;
                options.inJustDecodeBounds = false; options.inSampleSize = 1;
                while ((long) options.outWidth * options.outHeight * 4 / options.inSampleSize / options.inSampleSize > 1024 * 1024) options.inSampleSize *= 2;
                Bitmap bitmap = BitmapFactory.decodeByteArray(data, 0, data.length, options); if (bitmap == null) return;
                cache.put(safe, bitmap);
                main.post(() -> { ImageView target = reference.get(); if (target != null && address.equals(target.getTag())) target.setImageBitmap(bitmap); });
            } catch (Exception ignored) { /* Thumbnail failure leaves a neutral placeholder; text/playback remain available. */ }
        });
    }
    private GroupThumbnails() {}
}
