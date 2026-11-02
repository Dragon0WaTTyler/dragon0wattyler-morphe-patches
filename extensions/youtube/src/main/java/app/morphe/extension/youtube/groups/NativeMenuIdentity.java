/* SPDX-License-Identifier: GPL-3.0-only
 * Independent Subscription Groups groups.6.1, modified 2026-11-02. See LICENSE/NOTICE.
 */
package app.morphe.extension.youtube.groups;

import app.morphe.extension.youtube.innertube.queue.PlaylistQueueRenderer.MenuRenderer;
import app.morphe.extension.youtube.innertube.queue.PlaylistQueueRenderer.NavigationEndpoint;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;

/** Reads identity only. The original native command is never edited or reconstructed. */
final class NativeMenuIdentity {
    static final int MAX_BYTES = 256 * 1024;
    private static final int MENU_RENDERER = 66439850;
    private int fields, visited, menus;
    private boolean rejected;
    private String video;

    static String video(byte[] command) {
        return video(command, NativeMenuConfig.commandField());
    }
    static String video(byte[] command, int commandField) {
        if (command == null || command.length == 0 || command.length > MAX_BYTES) return null;
        if (commandField < 10000 || commandField > 536870911) return null;
        // Accept only one known Elements->Innertube->native-feed-menu command chain.
        // A nested menu inside some other button's action is not a menu capability.
        command = onlyCommand(command, 169495254);
        if (command == null) return null;
        command = onlyCommand(command, commandField);
        if (command == null) return null;
        return payloadVideo(command);
    }
    static String payloadVideo(byte[] command) {
        if (command == null || command.length == 0 || command.length > MAX_BYTES) return null;
        NativeMenuIdentity scan = new NativeMenuIdentity();
        try { scan.walk(command, 0, command.length, 0); }
        catch (RuntimeException invalid) { return null; }
        return !scan.rejected && scan.menus == 1 ? scan.video : null;
    }
    private static byte[] onlyCommand(byte[] bytes, int expected) {
        int[] at = {0}; byte[] payload = null;
        try {
            while (at[0] < bytes.length) {
                long key = varint(bytes, at, bytes.length); int number = (int) (key >>> 3);
                if (key < 8 || key >>> 3 > 536870911L) return null;
                if ((key & 7) != 2 || (number != expected && number != 1)) return null;
                long size = varint(bytes, at, bytes.length);
                if (size < 0 || size > bytes.length - at[0]) return null;
                int end = at[0] + (int) size;
                if (number == expected) { if (payload != null) return null; payload = Arrays.copyOfRange(bytes, at[0], end); }
                at[0] = end;
            }
        } catch (IllegalArgumentException invalid) { return null; }
        return payload;
    }
    private void walk(byte[] bytes, int begin, int end, int depth) {
        if (depth > 10 || (visited += end - begin) > 1024 * 1024) { rejected = true; return; }
        int[] at = {begin};
        while (at[0] < end) {
            if (++fields > 4096) { rejected = true; return; }
            long key = varint(bytes, at, end);
            if (key < 8 || key >>> 3 > 536870911L) throw new IllegalArgumentException();
            int wire = (int) (key & 7), number = (int) (key >>> 3);
            if (wire == 0) { varint(bytes, at, end); continue; }
            if (wire == 1 || wire == 5) { at[0] += wire == 1 ? 8 : 4; if (at[0] > end) throw new IllegalArgumentException(); continue; }
            if (wire != 2) throw new IllegalArgumentException();
            long size = varint(bytes, at, end);
            if (size < 0 || size > end - at[0]) throw new IllegalArgumentException();
            int next = at[0] + (int) size;
            if (number == MENU_RENDERER) {
                menus++;
                String id = menu(Arrays.copyOfRange(bytes, at[0], next));
                if (id == null) rejected = true; else video = id;
            } else if (next > at[0]) {
                // Length-delimited strings are not necessarily messages. Invalid branches are ignored.
                try { walk(bytes, at[0], next, depth + 1); } catch (IllegalArgumentException ignored) { }
            }
            at[0] = next;
        }
    }
    private static long varint(byte[] bytes, int[] at, int end) {
        long value = 0;
        for (int shift = 0; shift < 64; shift += 7) {
            if (at[0] >= end) throw new IllegalArgumentException();
            int next = bytes[at[0]++] & 255;
            if (shift == 63 && (next & 254) != 0) throw new IllegalArgumentException();
            value |= (long) (next & 127) << shift;
            if ((next & 128) == 0) return value;
        }
        throw new IllegalArgumentException();
    }
    private static String menu(byte[] bytes) {
        try {
            MenuRenderer menu = MenuRenderer.parseFrom(bytes);
            if (menu.getItemsCount() == 0 || menu.getItemsCount() > 100) return null;
            Set<String> ids = new LinkedHashSet<>();
            for (var item : menu.getItemsList()) {
                if (item.hasMenuServiceItemRenderer()) ids(item.getMenuServiceItemRenderer().getServiceEndpoint(), ids);
                if (item.hasMenuNavigationItemRenderer()) ids(item.getMenuNavigationItemRenderer().getNavigationEndpoint(), ids);
                if (item.hasMenuConditionalServiceItemRenderer()) ids(item.getMenuConditionalServiceItemRenderer().getServiceEndpoint(), ids);
                if (item.hasMenuConditionalNavigationItemRenderer()) ids(item.getMenuConditionalNavigationItemRenderer().getNavigationEndpoint(), ids);
            }
            return ids.size() == 1 && WatchedHistory.valid(ids.iterator().next()) ? ids.iterator().next() : null;
        } catch (Exception invalid) { return null; }
    }
    private static void ids(NavigationEndpoint command, Set<String> result) {
        if (command.hasWatchEndpoint()) add(command.getWatchEndpoint().getVideoId(), result);
        if (command.hasAddToPlaylistEndpoint()) add(command.getAddToPlaylistEndpoint().getVideoId(), result);
        if (command.hasOfflineVideoEndpoint()) add(command.getOfflineVideoEndpoint().getVideoId(), result);
        if (command.hasGetDownloadActionCommand()) add(command.getGetDownloadActionCommand().getVideoId(), result);
        if (command.hasPlaylistEditEndpoint()) for (var action : command.getPlaylistEditEndpoint().getActionsList()) add(action.getAddedVideoId(), result);
        if (command.hasCreatePlaylistDialogEndpoint()) for (String id : command.getCreatePlaylistDialogEndpoint().getVideoIdsList()) add(id, result);
    }
    private static void add(String id, Set<String> result) {
        if (id == null || id.isEmpty()) return;
        // An invalid explicit ID invalidates the whole candidate, rather than being discarded.
        result.add(WatchedHistory.valid(id) ? id : "invalid");
    }
    private NativeMenuIdentity() { }
}
