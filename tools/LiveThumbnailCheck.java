/* SPDX-License-Identifier: GPL-3.0-only
 * Independent Subscription Groups groups.6, modified 2026-11-02. See LICENSE/NOTICE.
 */
package app.morphe.extension.youtube.groups;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONObject;

/** Optional public-network desktop probe. No streams, account state or Android runtime validation. */
public final class LiveThumbnailCheck {
    public static void main(String[] args) throws Exception {
        String id = "2reQut8WNGc", channel = "UCBJycsmduvYEL83R_U4JriQ";
        List<ThumbnailVariant> variants = ChannelPageMetadata.watchThumbnails(new String(RecentFeedSource.get(
                "https://www.youtube.com/watch?v=" + id + "&hl=en", 8 * 1024 * 1024,
                "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 Chrome/131.0.0.0 Safari/537.36"), StandardCharsets.UTF_8), id, channel);
        if (variants.isEmpty()) throw new AssertionError("No actual public thumbnail metadata");
        String chosen = ThumbnailVariant.order(variants, 1080, "").get(0);
        ThumbnailVariant selected = variants.stream().filter(v -> v.url.equals(chosen)).findFirst().orElseThrow();
        byte[] image = RecentFeedSource.get(chosen, 2 * 1024 * 1024);
        Files.write(Path.of(args[0]), image);
        JSONArray values = new JSONArray();
        for (ThumbnailVariant v : variants) values.put(new JSONObject().put("url", v.url).put("width", v.width).put("height", v.height));
        JSONObject report = new JSONObject().put("videoId", id).put("channelId", channel).put("actual_metadata", values)
                .put("selected_for_1080_width", new JSONObject().put("url", chosen).put("width", selected.width).put("height", selected.height))
                .put("download_bytes", image.length).put("scope", "Desktop production parser/selection/network sample; target sharpness comparison pending");
        Files.writeString(Path.of(args[1]), report.toString(2));
        System.out.println("Public production parser: " + variants.size() + " supplied variants; selected " + selected.width + "x" + selected.height + "; " + image.length + " image bytes.");
        System.out.println("Desktop source sample only; target Android rendering remains pending.");
    }
}
