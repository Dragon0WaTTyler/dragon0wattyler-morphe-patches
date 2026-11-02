/* SPDX-License-Identifier: GPL-3.0-only
 * Independent Subscription Groups groups.4, 2026-11-01. See LICENSE and NOTICE.
 */
package app.morphe.extension.youtube.groups;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;

/** Bounds cache residency, never the active group's aggregate or a returned channel window. */
final class ChannelFeedCache {
    private final File directory;
    private final LinkedHashMap<String, FeedSource.Window> memory = new LinkedHashMap<>(64, 0.75f, true);
    private int writes;
    private final Object memoryLock = new Object(), diskLock = new Object();
    ChannelFeedCache(File directory) { this.directory = directory; }
    FeedSource.Window memory(String channel) { synchronized (memoryLock) { return memory.get(channel); } }
    FeedSource.Window get(String channel) {
        if (!GroupBackup.isChannelId(channel)) return null;
        FeedSource.Window hit = memory(channel); if (hit != null) return hit;
        File file = new File(directory, channel + ".xml");
        if (!file.isFile() || file.length() > YouTubeAtomFeed.MAX_BYTES) return null;
        try {
            List<GroupVideo> videos = YouTubeAtomFeed.parse(new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8), channel);
            FeedSource.Window result = new FeedSource.Window(videos, file.lastModified());
            synchronized (memoryLock) {
                FeedSource.Window current = memory.get(channel); if (current != null) return current;
                remember(channel, result); return result;
            }
        } catch (IOException invalid) { return null; }
    }
    void put(String channel, byte[] xml, List<GroupVideo> videos, long fetchedAt) {
        synchronized (memoryLock) { remember(channel, new FeedSource.Window(videos, fetchedAt)); }
        synchronized (diskLock) { try {
            if (!directory.isDirectory() && !directory.mkdirs()) return;
            File file = new File(directory, channel + ".xml"); GroupStore.atomicWrite(file, xml);
            file.setLastModified(fetchedAt);
            if (++writes % 12 == 0) trim();
        } catch (IOException ignored) { /* A disk-cache failure cannot discard successful uploads. */ } }
    }
    private void remember(String channel, FeedSource.Window window) {
        memory.put(channel, window);
        while (memory.size() > 256) memory.remove(memory.keySet().iterator().next());
    }
    private void trim() {
        File[] files = directory.listFiles((dir, name) -> name.endsWith(".xml")); if (files == null) return;
        Arrays.sort(files, Comparator.comparingLong(File::lastModified).reversed()); long bytes = 0;
        for (int i = 0; i < files.length; i++) {
            bytes += files[i].length();
            if (i >= 2048 || bytes > 64L * 1024 * 1024) try { Files.deleteIfExists(files[i].toPath()); } catch (IOException ignored) { }
        }
    }
}
