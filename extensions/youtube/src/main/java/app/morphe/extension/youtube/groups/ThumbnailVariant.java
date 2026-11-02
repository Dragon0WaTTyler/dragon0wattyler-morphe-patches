/* SPDX-License-Identifier: GPL-3.0-only
 * Independent Subscription Groups groups.6, modified 2026-11-02. See LICENSE/NOTICE.
 */
package app.morphe.extension.youtube.groups;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;

/** Real metadata URLs/dimensions only. No synthesized maxres or resize parameters. */
public final class ThumbnailVariant {
    public final String url;
    public final int width, height;
    public ThumbnailVariant(String url, int width, int height) {
        this.url = YouTubeAtomFeed.safeThumbnail(url); this.width = width; this.height = height;
        if (this.url.isEmpty() || width <= 0 || height <= 0 || width > 8192 || height > 8192) throw new IllegalArgumentException("Invalid thumbnail variant");
    }
    public static int sampleSize(int width, int height, int target) {
        int sample = 1; target = Math.max(1, target);
        while (width / (sample * 2) >= target && sample < 8192) sample *= 2;
        while ((long) Math.max(1, width / sample) * Math.max(1, height / sample) * 4 > 12L * 1024 * 1024) sample *= 2;
        return sample;
    }
    public static List<String> order(List<ThumbnailVariant> variants, int targetWidth, String preview) {
        List<ThumbnailVariant> candidates = new ArrayList<>(variants);
        // Smallest sufficient source, otherwise best available. Lower real sources remain fallbacks.
        candidates.sort(Comparator.comparingInt((ThumbnailVariant v) -> v.width));
        int selected = candidates.size() - 1;
        for (int i = 0; i < candidates.size(); i++) if (candidates.get(i).width >= targetWidth) { selected = i; break; }
        LinkedHashSet<String> urls = new LinkedHashSet<>();
        if (selected >= 0) {
            urls.add(candidates.get(selected).url);
            for (int i = selected + 1; i < candidates.size(); i++) urls.add(candidates.get(i).url);
            for (int i = selected - 1; i >= 0; i--) urls.add(candidates.get(i).url);
        }
        String safe = YouTubeAtomFeed.safeThumbnail(preview); if (!safe.isEmpty()) urls.add(safe);
        return new ArrayList<>(urls);
    }
}
