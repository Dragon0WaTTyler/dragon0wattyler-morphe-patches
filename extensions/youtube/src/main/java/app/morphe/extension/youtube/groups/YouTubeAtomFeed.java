/* SPDX-License-Identifier: GPL-3.0-only
 * Independent Subscription Groups V2, 2026-11-01. See LICENSE and NOTICE.
 */
package app.morphe.extension.youtube.groups;

import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.XMLReader;
import org.xml.sax.helpers.DefaultHandler;
import java.io.IOException;
import java.io.StringReader;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import javax.xml.parsers.SAXParserFactory;

/** Parses actual public YouTube Atom fields, without guessing duration/type/watch-state metadata. */
public final class YouTubeAtomFeed {
    public static final int MAX_BYTES = 512 * 1024;
    public static List<GroupVideo> parse(String xml, String channelId) throws IOException {
        if (!GroupBackup.isChannelId(channelId) || xml == null || xml.getBytes(StandardCharsets.UTF_8).length > MAX_BYTES
                || xml.contains("<!DOCTYPE") || xml.contains("<!ENTITY")) throw new IOException("Invalid channel feed");
        try {
            SAXParserFactory factory = SAXParserFactory.newInstance(); factory.setNamespaceAware(true);
            XMLReader reader = factory.newSAXParser().getXMLReader();
            // DTDs are rejected above. Entity resolution also fails closed on Android SAX variants.
            reader.setEntityResolver((publicId, systemId) -> { throw new org.xml.sax.SAXException("External entities forbidden"); });
            Parser parser = new Parser(channelId); reader.setContentHandler(parser);
            reader.parse(new InputSource(new StringReader(xml)));
            if (!parser.feed || !parser.ownerSeen) throw new IOException("Missing channel feed identity");
            if (parser.entries > 0 && parser.videos.isEmpty()) throw new IOException("No valid upload entries");
            return Collections.unmodifiableList(new ArrayList<>(parser.videos.values()));
        } catch (IOException failure) { throw failure; }
        catch (Exception failure) { throw new IOException("Could not parse channel feed", failure); }
    }
    public static String safeThumbnail(String url) {
        try {
            URI uri = new URI(url); String host = uri.getHost();
            if (host != null && (host.equals("i.ytimg.com") || host.endsWith(".ytimg.com"))
                    && (uri.getScheme().equals("https") || uri.getScheme().equals("http"))
                    && uri.getUserInfo() == null && (uri.getPort() == -1 || uri.getPort() == 443)) {
                return url.startsWith("http:") ? "https:" + url.substring(5) : url;
            }
        } catch (Exception ignored) { }
        return "";
    }
    private static final class Parser extends DefaultHandler {
        final String expected; final LinkedHashMap<String, GroupVideo> videos = new LinkedHashMap<>();
        final StringBuilder text = new StringBuilder();
        boolean feed, ownerSeen, entry, author;
        String id = "", title = "", name = "", published = "", thumbnail = "", rootName = "";
        long views = -1;
        int entries;
        Parser(String channel) { expected = channel; }
        @Override public void startElement(String uri, String local, String q, Attributes attributes) {
            text.setLength(0);
            if (local.equals("feed") && uri.equals("http://www.w3.org/2005/Atom")) feed = true;
            if (local.equals("entry")) { entries++; entry = true; id = title = name = published = thumbnail = ""; views = -1; }
            if (local.equals("author")) author = true;
            if (entry && local.equals("thumbnail") && uri.equals("http://search.yahoo.com/mrss/")) thumbnail = safeThumbnail(attributes.getValue("url"));
            if (entry && local.equals("statistics") && uri.equals("http://search.yahoo.com/mrss/")) {
                try { views = Long.parseLong(attributes.getValue("views")); } catch (Exception ignored) { views = -1; }
            }
        }
        @Override public void characters(char[] chars, int start, int length) { if (text.length() < MAX_BYTES) text.append(chars, start, length); }
        @Override public void endElement(String uri, String local, String q) throws org.xml.sax.SAXException {
            String value = text.toString().trim();
            if (local.equals("channelId") && uri.equals("http://www.youtube.com/xml/schemas/2015")) {
                if (!value.equals(expected) && !value.equals(expected.substring(2))) throw new org.xml.sax.SAXException("Channel identity mismatch");
                ownerSeen = true;
            }
            if (entry && local.equals("videoId") && uri.equals("http://www.youtube.com/xml/schemas/2015")) id = value;
            if (local.equals("name") && author) { if (entry) name = value; else rootName = value; }
            if (entry && local.equals("title") && uri.equals("http://www.w3.org/2005/Atom")) title = value;
            if (entry && local.equals("published")) published = value;
            if (local.equals("author")) author = false;
            if (local.equals("entry")) {
                try {
                    if (title.isEmpty() || videos.size() >= 100) throw new IllegalArgumentException();
                    GroupVideo video = new GroupVideo(id, expected, title, name.isEmpty() ? rootName : name,
                            thumbnail, OffsetDateTime.parse(published).toInstant().toEpochMilli(), Math.max(-1, views));
                    videos.putIfAbsent(video.id, video);
                } catch (RuntimeException ignored) { /* A malformed entry does not invent a playable video. */ }
                entry = false;
            }
        }
    }
    private YouTubeAtomFeed() {}
}
