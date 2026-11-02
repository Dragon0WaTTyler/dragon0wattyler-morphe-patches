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
}
