/* SPDX-License-Identifier: GPL-3.0-only
 * Independent Subscription Groups groups.6.1, modified 2026-11-02. See LICENSE/NOTICE.
 */
package app.morphe.extension.youtube.groups;

import app.morphe.extension.youtube.innertube.queue.PlaylistQueueRenderer.*;
import java.io.ByteArrayOutputStream;
import java.util.Arrays;
import org.junit.Test;
import static org.junit.Assert.*;

/** Native protobuf fixtures exercise production identity/registry code, not a real target APK. */
public class NativeMenuRegistryTest {
    static final int FIELD = 123456789; // Explicit synthetic native schema resolved by the fixture.
    static final String ID = "00000000611", OTHER = "00000000612";
    static byte[] field(int number, byte[] bytes) {
        ByteArrayOutputStream out = new ByteArrayOutputStream(); varint(out, ((long) number << 3) | 2); varint(out, bytes.length); out.write(bytes, 0, bytes.length); return out.toByteArray();
    }
    static void varint(ByteArrayOutputStream out, long value) { do { int next = (int) (value & 127); value >>>= 7; out.write(next | (value == 0 ? 0 : 128)); } while (value != 0); }
    static byte[] join(byte[] one, byte[] two) { byte[] result = Arrays.copyOf(one, one.length + two.length); System.arraycopy(two, 0, result, one.length, two.length); return result; }
    static MenuItem item(String id) { return MenuItem.newBuilder().setMenuServiceItemRenderer(MenuServiceItem.newBuilder()
            .setText(Text.newBuilder().setSimpleText("Native fixture"))
            .setServiceEndpoint(NavigationEndpoint.newBuilder().setAddToPlaylistEndpoint(AddToPlaylistEndpoint.newBuilder().setVideoId(id)))).build(); }
    static byte[] menu(MenuRenderer renderer) { return field(169495254, field(FIELD, field(1, Menu.newBuilder().setMenuRenderer(renderer).build().toByteArray()))); }
    static byte[] command(String id) { return menu(MenuRenderer.newBuilder().addItems(item(id)).build()); }
    static final class Handler implements NativeMenuRegistry.Handler {
        byte[] bytes; int opens; Object sender;
        Handler(String id) { bytes = command(id); }
        public byte[] patch_groupsMenuCommand() { return bytes.clone(); }
        public void patch_groupsOpenMenu(Object anchor) { opens++; sender = anchor; }
    }
    static final class Fixture {
        long now = 100; NativeMenuRegistry.Scope scope = new NativeMenuRegistry.Scope(1, 1);
        final NativeMenuRegistry registry = new NativeMenuRegistry(() -> now, () -> scope, bytes -> NativeMenuIdentity.video(bytes, FIELD));
    }
    @Test public void recognizesExactVideoInOriginalNativeMenuCommand() {
        assertEquals(ID, NativeMenuIdentity.video(command(ID), FIELD));
        assertNull(NativeMenuIdentity.video(command(ID), 0)); assertNull(NativeMenuIdentity.video(command(ID), FIELD + 1));
    }
    @Test public void rejectsMissingMixedAndInvalidVideoEndpoints() {
        assertNull(NativeMenuIdentity.video(menu(MenuRenderer.getDefaultInstance()), FIELD));
        assertNull(NativeMenuIdentity.video(menu(MenuRenderer.newBuilder().addItems(item(ID)).addItems(item(OTHER)).build()), FIELD));
        assertNull(NativeMenuIdentity.video(command("invalid"), FIELD));
        MenuItem targetless = MenuItem.newBuilder().setMenuServiceItemRenderer(MenuServiceItem.newBuilder().setText(Text.newBuilder().setSimpleText(ID))).build();
        assertNull(NativeMenuIdentity.video(menu(MenuRenderer.newBuilder().addItems(targetless).build()), FIELD));
    }
    @Test public void rejectsOtherActionsThatMerelyContainANestedMenu() {
        byte[] inner = field(FIELD, field(1, Menu.newBuilder().setMenuRenderer(MenuRenderer.newBuilder().addItems(item(ID))).build().toByteArray()));
        assertNull(NativeMenuIdentity.video(field(169495254, field(987654321 / 2, inner)), FIELD));
        assertNull(NativeMenuIdentity.video(field(169495254, join(inner, field(FIELD + 1, new byte[]{8, 1}))), FIELD));
        assertNull(NativeMenuIdentity.video(join(command(ID), command(OTHER)), FIELD));
    }
    @Test public void malformedOversizedAndDeepCommandsNeverBecomeCapabilities() {
        byte[] data = command(ID); assertNull(NativeMenuIdentity.video(Arrays.copyOf(data, data.length - 1), FIELD));
        assertNull(NativeMenuIdentity.video(new byte[NativeMenuIdentity.MAX_BYTES + 1], FIELD));
        assertNull(NativeMenuIdentity.video(new byte[]{(byte) 255, (byte) 255}, FIELD));
        ByteArrayOutputStream invalidKey = new ByteArrayOutputStream(); varint(invalidKey, (((1L << 32) + 169495254) << 3) | 2); varint(invalidKey, 0);
        assertNull(NativeMenuIdentity.video(invalidKey.toByteArray(), FIELD));
        byte[] deep = Menu.newBuilder().setMenuRenderer(MenuRenderer.newBuilder().addItems(item(ID))).build().toByteArray();
        for (int i = 0; i < 15; i++) deep = field(2, deep);
        assertNull(NativeMenuIdentity.video(field(169495254, field(FIELD, deep)), FIELD));
    }
    @Test public void captureLeavesOriginalNativeHomeHandlerAndBytesUntouched() {
        Fixture f = new Fixture(); Handler handler = new Handler(ID); byte[] original = handler.bytes.clone();
        f.registry.capture(handler); assertEquals(0, handler.opens); assertArrayEquals(original, handler.bytes);
        Object nativeSender = new Object(); handler.patch_groupsOpenMenu(nativeSender);
        assertEquals(1, handler.opens); assertSame(nativeSender, handler.sender); assertArrayEquals(original, handler.bytes);
    }
    @Test public void invokesOnlySelectedVideoWithExactSenderAfterFeedReordering() {
        Fixture f = new Fixture(); Handler first = new Handler(ID), second = new Handler(OTHER); f.registry.capture(first); f.registry.capture(second);
        Object anchor = new Object(); assertTrue(f.registry.open(OTHER, anchor)); assertEquals(0, first.opens); assertEquals(1, second.opens); assertSame(anchor, second.sender);
        assertTrue(f.registry.open(ID, anchor)); assertEquals(1, first.opens);
    }
    @Test public void reboundHandlersAndChangedContextBytesAreRejected() {
        Fixture f = new Fixture(); Handler handler = new Handler(ID); f.registry.capture(handler); handler.bytes = command(OTHER);
        assertFalse(f.registry.open(ID, new Object())); assertEquals(0, handler.opens);
        handler.bytes = command(ID); f.registry.capture(handler); handler.bytes = join(field(1, new byte[]{1}), handler.bytes);
        assertEquals(ID, NativeMenuIdentity.video(handler.bytes, FIELD)); assertFalse(f.registry.open(ID, new Object())); assertEquals(0, handler.opens);
    }
    @Test public void expiredPausedAndDifferentAccountCapabilitiesNeverDispatch() {
        Fixture f = new Fixture(); Handler handler = new Handler(ID); f.registry.capture(handler); f.now += 60_001;
        assertFalse(f.registry.open(ID, new Object())); f.registry.capture(handler); f.scope = new NativeMenuRegistry.Scope(1, 2);
        assertFalse(f.registry.open(ID, new Object())); f.registry.capture(handler); f.scope = new NativeMenuRegistry.Scope(2, 2);
        assertFalse(f.registry.open(ID, new Object())); f.registry.capture(handler); f.scope = null;
        assertFalse(f.registry.open(ID, new Object())); assertEquals(0, handler.opens);
    }
    @Test public void unsupportedAndThrowingHandlersHaveFunctionalFallbackSignal() {
        Fixture f = new Fixture(); Handler invalid = new Handler(ID); invalid.bytes = new byte[]{8, 0}; f.registry.capture(invalid);
        assertFalse(f.registry.open(ID, new Object())); assertFalse(f.registry.open(OTHER, new Object()));
        NativeMenuRegistry.Handler throwing = new NativeMenuRegistry.Handler() {
            public byte[] patch_groupsMenuCommand() { return command(ID); }
            public void patch_groupsOpenMenu(Object sender) { throw new IllegalStateException("native context lost"); }
        };
        f.registry.capture(throwing); assertFalse(f.registry.open(ID, new Object())); assertFalse(f.registry.open(ID, new Object()));
    }
    @Test public void repeatedRapidMenuOpensDoNotMixSelectionsOrLoseNewestBinding() {
        Fixture f = new Fixture(); Handler one = new Handler(ID), two = new Handler(OTHER); f.registry.capture(one); f.registry.capture(two);
        for (int i = 0; i < 155; i++) assertTrue(f.registry.open(i % 2 == 0 ? ID : OTHER, new Object()));
        assertEquals(78, one.opens); assertEquals(77, two.opens); f.registry.clear(); assertFalse(f.registry.open(ID, new Object()));
    }
    @Test public void genuineNativeEndpointBytesArePreservedIncludingUnknownActions() throws Exception {
        Fixture f = new Fixture(); Handler handler = new Handler(ID);
        MenuItem opaque = MenuItem.parseFrom(field(300000001, new byte[]{8, 1}));
        byte[] original = join(field(1, new byte[]{5, 6}), menu(MenuRenderer.newBuilder().addItems(item(ID)).addItems(opaque).build())); handler.bytes = original.clone();
        f.registry.capture(handler); assertTrue(f.registry.open(ID, new Object())); assertArrayEquals(original, handler.bytes);
    }
}
