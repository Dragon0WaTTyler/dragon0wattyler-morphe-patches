/* SPDX-License-Identifier: GPL-3.0-only
 * Independent Subscription Groups groups.4, 2026-11-01. See LICENSE and NOTICE.
 */
package app.morphe.extension.youtube.groups;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

/** Real JVM HTTP headers/status policy against a local test server; not Android network verification. */
public class GroupHttpPolicyTest {
    private ServerSocket server;
    private String root;
    @Before public void create() throws Exception {
        server = new ServerSocket(0, 8, InetAddress.getByName("127.0.0.1"));
        Thread responder = new Thread(() -> {
            while (!server.isClosed()) try (Socket socket = server.accept()) {
                BufferedReader input = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.US_ASCII));
                String request = input.readLine(); if (request == null) continue;
                String line; do { line = input.readLine(); } while (line != null && !line.isEmpty());
                String path = request.split(" ")[1]; int status = path.equals("/missing") ? 404 : path.equals("/throttle") ? 429 : path.equals("/redirect") ? 302 : 200;
                String body = status == 200 ? "window" : "";
                String headers = status == 429 ? "Retry-After: 3\r\n" : status == 302 ? "Location: /okay\r\n" : "";
                socket.getOutputStream().write(("HTTP/1.1 " + status + " Test\r\nContent-Length: " + body.length() + "\r\nConnection: close\r\n" + headers + "\r\n" + body).getBytes(StandardCharsets.US_ASCII));
                socket.getOutputStream().flush();
            } catch (IOException stopped) { return; }
        }, "group-http-fixture"); responder.setDaemon(true); responder.start();
        root = "http://127.0.0.1:" + server.getLocalPort();
    }
    @After public void close() throws Exception { server.close(); }
    @Test public void notFoundIsPermanentAndThrottleIsRetriedAfterServerDelay() {
        FeedSource.Failure missing = assertThrows(FeedSource.Failure.class, () -> RecentFeedSource.get(root + "/missing", 100));
        assertFalse(missing.retryable);
        FeedSource.Failure throttle = assertThrows(FeedSource.Failure.class, () -> RecentFeedSource.get(root + "/throttle", 100));
        assertTrue(throttle.retryable); assertEquals(3000, throttle.retryAfterMillis);
    }
    @Test public void retryAfterHttpDateSecondsAndInvalidHeadersAreHandled() {
        long now = Instant.parse("2026-11-01T10:00:00Z").toEpochMilli();
        String later = DateTimeFormatter.RFC_1123_DATE_TIME.format(Instant.ofEpochMilli(now + 60000).atZone(ZoneOffset.UTC));
        assertEquals(60000, RecentFeedSource.retryAfter(later, now)); assertEquals(3000, RecentFeedSource.retryAfter("3", now));
        assertEquals(0, RecentFeedSource.retryAfter("not a date", now)); assertEquals(0, RecentFeedSource.retryAfter("-2", now));
    }
    @Test public void redirectsAreNotFollowedAndResponseSizeIsBounded() throws Exception {
        assertThrows(FeedSource.Failure.class, () -> RecentFeedSource.get(root + "/redirect", 100));
        assertThrows(IOException.class, () -> RecentFeedSource.get(root + "/okay", 3));
        assertEquals("window", new String(RecentFeedSource.get(root + "/okay", 100), StandardCharsets.UTF_8));
    }
    @Test public void canceledRequestDoesNotBeginNetworkWork() {
        Thread.currentThread().interrupt();
        try { assertThrows(IOException.class, () -> RecentFeedSource.get(root + "/okay", 100)); }
        finally { Thread.interrupted(); }
    }
}
