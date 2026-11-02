/* SPDX-License-Identifier: GPL-3.0-only
 * Independent Subscription Groups groups.4, 2026-11-01. See LICENSE and NOTICE.
 */
package app.morphe.extension.youtube.groups;

import org.junit.Test;
import org.junit.Rule;
import org.junit.rules.TemporaryFolder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.io.File;
import java.util.List;
import static org.junit.Assert.*;

public class GroupCacheTest {
    private static final String CHANNEL = "UCXuqSBlHAE6Xw-yeJA0Tunw";
    @Rule public TemporaryFolder folder = new TemporaryFolder();
    private byte[] xml() {
        return ("<feed xmlns=\"http://www.w3.org/2005/Atom\" xmlns:yt=\"http://www.youtube.com/xml/schemas/2015\">"
                + "<yt:channelId>" + CHANNEL + "</yt:channelId><author><name>Channel</name></author><entry><yt:videoId>abcdefghijk</yt:videoId>"
                + "<title>Title</title><published>2026-11-01T10:00:00Z</published></entry></feed>").getBytes(StandardCharsets.UTF_8);
    }
    @Test public void aWindowSurvivesStoreRecreationWithoutChangingPublicationTime() throws Exception {
        byte[] xml = xml(); List<GroupVideo> videos = YouTubeAtomFeed.parse(new String(xml, StandardCharsets.UTF_8), CHANNEL);
        long fetchedAt = 1800000000000L; ChannelFeedCache first = new ChannelFeedCache(folder.getRoot()); first.put(CHANNEL, xml, videos, fetchedAt);
        FeedSource.Window memory = first.get(CHANNEL); assertSame(memory, first.get(CHANNEL));
        FeedSource.Window disk = new ChannelFeedCache(folder.getRoot()).get(CHANNEL);
        assertNotNull(disk); assertEquals(fetchedAt, disk.fetchedAt); assertEquals(videos.get(0).published, disk.videos.get(0).published);
        assertTrue(disk.fresh(fetchedAt + 100)); assertFalse(disk.fresh(fetchedAt + FeedSource.Window.TTL));
    }
    @Test public void staleWindowRemainsAvailableForImmediateDisplay() throws Exception {
        byte[] xml = xml(); ChannelFeedCache cache = new ChannelFeedCache(folder.getRoot());
        cache.put(CHANNEL, xml, YouTubeAtomFeed.parse(new String(xml, StandardCharsets.UTF_8), CHANNEL), 1000);
        FeedSource.Window hit = cache.get(CHANNEL); assertNotNull(hit); assertFalse(hit.fresh(2000000)); assertEquals(1, hit.videos.size());
    }
    @Test public void corruptAndOversizedCacheFilesFallBackWithoutPretendingSuccess() throws Exception {
        File file = new File(folder.getRoot(), CHANNEL + ".xml"); Files.write(file.toPath(), "<broken".getBytes(StandardCharsets.UTF_8));
        assertNull(new ChannelFeedCache(folder.getRoot()).get(CHANNEL));
        Files.write(file.toPath(), new byte[YouTubeAtomFeed.MAX_BYTES + 1]); assertNull(new ChannelFeedCache(folder.getRoot()).get(CHANNEL));
    }
    @Test public void futureDatedCacheDoesNotCountAsFresh() { assertFalse(new FeedSource.Window(List.of(), 10000).fresh(9000)); }
    @Test public void refreshedFiniteWindowRetainsPreviouslySeenVideosAcrossRestart() throws Exception {
        ChannelFeedCache first = new ChannelFeedCache(folder.getRoot());
        GroupVideo older = new GroupVideo("abcdefghijk", CHANNEL, "Old title", "Channel", "https://i.ytimg.com/old.jpg", 1000, 1);
        GroupVideo newer = new GroupVideo("bcdefghijkl", CHANNEL, "New title", "Channel", "https://i.ytimg.com/new.jpg", 2000, 2);
        first.put(CHANNEL, new byte[0], List.of(older), 10_000);
        first.put(CHANNEL, new byte[0], List.of(newer), 20_000);

        FeedSource.Window reopened = new ChannelFeedCache(folder.getRoot()).get(CHANNEL);
        assertNotNull(reopened);
        assertEquals(2, reopened.videos.size());
        assertEquals("bcdefghijkl", reopened.videos.get(0).id);
        assertEquals("abcdefghijk", reopened.videos.get(1).id);
        assertEquals(1000, reopened.videos.get(1).published);
        assertEquals(20_000, reopened.fetchedAt);
    }
    @Test public void duplicateRefreshUpdatesPresentationWithoutChangingPublicationTimestamp() throws Exception {
        ChannelFeedCache cache = new ChannelFeedCache(folder.getRoot());
        cache.put(CHANNEL, new byte[0], List.of(new GroupVideo("abcdefghijk", CHANNEL, "Original", "Channel", "", 1000, 1)), 10_000);
        cache.put(CHANNEL, new byte[0], List.of(new GroupVideo("abcdefghijk", CHANNEL, "Updated", "Channel", "", 9000, 5)), 20_000);
        GroupVideo retained = new ChannelFeedCache(folder.getRoot()).get(CHANNEL).videos.get(0);
        assertEquals("Updated", retained.title);
        assertEquals(1000, retained.published);
        assertEquals(5, retained.views);
    }
    @Test public void historyRetentionIsBoundedPerChannel() throws Exception {
        ChannelFeedCache cache = new ChannelFeedCache(folder.getRoot());
        java.util.ArrayList<GroupVideo> videos = new java.util.ArrayList<>();
        for (int i = 0; i < ChannelFeedCache.MAX_VIDEOS_PER_CHANNEL + 10; i++) {
            String id = String.format(java.util.Locale.ROOT, "%011d", i);
            videos.add(new GroupVideo(id, CHANNEL, "Video", "Channel", "", i + 1, -1));
        }
        cache.put(CHANNEL, new byte[0], videos, 100_000);
        assertEquals(ChannelFeedCache.MAX_VIDEOS_PER_CHANNEL, new ChannelFeedCache(folder.getRoot()).get(CHANNEL).videos.size());
    }
}
