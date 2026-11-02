/* SPDX-License-Identifier: GPL-3.0-only
 * Independent Subscription Groups additions, 2026-11-01. See LICENSE and NOTICE.
 */
package app.morphe.extension.youtube.groups;

import static org.junit.Assert.*;

import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

public class GroupBackupTest {
    private static final String A = "UCJ3AxeCHGPZkMi3kRfCuiHw";
    private static final String B = "UCvtT19MZW8dq5Wwfu6B0oxw";

    private GroupBackup.Snapshot fixture(String file) throws IOException {
        try (InputStream input = getClass().getResourceAsStream("/groups/" + file)) {
            assertNotNull(input);
            return GroupBackup.parse(input);
        }
    }

    @Test public void importsIndexedBackupAndIgnoresMetadata() throws Exception {
        GroupBackup.Snapshot snapshot = fixture("pockettube-indexed.json");
        assertEquals(3, snapshot.groups.size());
        assertEquals(3, snapshot.memberships);
        assertEquals("Science & Tech", snapshot.groups.get(0).name);
        assertEquals(List.of(A, B), snapshot.groups.get(0).channelIds);
    }

    @Test public void importsLegacyBackup() throws Exception {
        assertEquals(GroupBackup.encode(fixture("pockettube-indexed.json")),
                GroupBackup.encode(fixture("pockettube-legacy.json")));
    }

    @Test public void preservesMultipleGroupMembership() throws Exception {
        GroupBackup.Snapshot snapshot = fixture("pockettube-indexed.json");
        assertTrue(snapshot.groups.get(0).channelIds.contains(A));
        assertTrue(snapshot.groups.get(1).channelIds.contains(A));
        assertEquals("تعلم 🎓", snapshot.groups.get(1).name);
    }

    @Test public void preservesEmptyGroups() throws Exception {
        assertTrue(fixture("pockettube-indexed.json").groups.get(2).channelIds.isEmpty());
        assertEquals(0, GroupBackup.parse("{}").groups.size());
        assertEquals(0, GroupBackup.parse("{\"ysc_collection\":{}}").groups.size());
    }

    @Test public void keepsWhitespaceAndCaseSensitiveNamesDistinct() throws Exception {
        GroupBackup.Snapshot snapshot = GroupBackup.parse("{\" A \":[],\"A\":[],\"a\":[]}");
        assertEquals(List.of(" A ", "A", "a"), snapshot.groups.stream().map(g -> g.name).toList());
    }

    @Test public void deduplicatesOnlyWithinEachGroup() throws Exception {
        GroupBackup.Snapshot snapshot = GroupBackup.parse("{\"A\":[\"" + A + "\",\"" + A
                + "\"],\"B\":[\"" + A + "\"]}");
        assertEquals(1, snapshot.groups.get(0).channelIds.size());
        assertEquals(2, snapshot.memberships);
    }

    @Test public void snapshotsAreImmutable() throws Exception {
        GroupBackup.Snapshot snapshot = fixture("pockettube-indexed.json");
        assertThrows(UnsupportedOperationException.class, () -> snapshot.groups.clear());
        assertThrows(UnsupportedOperationException.class, () -> snapshot.groups.get(0).channelIds.clear());
    }

    @Test public void acceptsBomAndEscapedNames() throws Exception {
        GroupBackup.Snapshot snapshot = GroupBackup.parse("\uFEFF{\"\\u5b66\\u4e60 \\\"A\\\" \\\\ \\n\":[]}");
        assertEquals("学习 \"A\" \\ \n", snapshot.groups.get(0).name);
        assertEquals(0, snapshot.memberships);
    }

    @Test public void rejectsMalformedAndTruncatedFiles() {
        for (String text : List.of("", " ", "{", "{\"A\":[", "{\"A\":[]", "{\"A\":[\"" + A,
                "{\"A\":[],}", "{\"A\":[\"" + A + "\",]}", "{A:[]}", "{'A':[]}",
                "{\"A\":[]} trailing", "{\"A\":[]}{}", "{\"A\":[]}//comment", "{\"A\\q\":[]}")) {
            assertThrows(text, GroupBackup.InvalidBackup.class, () -> GroupBackup.parse(text));
        }
    }

    @Test public void rejectsInvalidUtf8() {
        assertThrows(GroupBackup.InvalidBackup.class,
                () -> GroupBackup.parse(new ByteArrayInputStream(new byte[]{(byte) 0xc3, 0x28})));
    }

    @Test public void rejectsUnpairedSurrogatesAndRawControls() {
        for (String text : List.of("{\"\\ud800\":[]}", "{\"\\udc00\":[]}", "{\"A\nB\":[]}")) {
            assertThrows(GroupBackup.InvalidBackup.class, () -> GroupBackup.parse(text));
        }
    }

    @Test public void rejectsDuplicateObjectKeysRatherThanLosingMembership() {
        for (String text : List.of("{\"A\":[],\"A\":[]}",
                "{\"A\":[],\"ysc_collection\":{\"A\":\"A\",\"A\":\"A\"}}")) {
            assertThrows(GroupBackup.InvalidBackup.class, () -> GroupBackup.parse(text));
        }
    }

    @Test public void rejectsUnsupportedShapesAndInvalidIds() {
        for (String text : List.of("[]", "null", "42", "\"hello\"", "{\"A\":null}",
                "{\"A\":{}}", "{\"A\":[12]}", "{\"A\":[null]}", "{\"A\":[true]}",
                "{\"A\":[\"@handle\"]}", "{\"A\":[\"https://youtube.com/channel/" + A + "\"]}",
                "{\"A\":[\"UCshort\"]}", "{\"A\":[\"../injection\"]}", "{\" \":[]}",
                "{\"groups\":[] ,\"version\":1}", "{\"ysc_collection\":[] ,\"ysc_settings\":{}}")) {
            assertThrows(text, GroupBackup.InvalidBackup.class, () -> GroupBackup.parse(text));
        }
    }

    @Test public void validatesEveryIndexedGroup() {
        for (String text : List.of("{\"ysc_collection\":{\"Missing\":\"Missing\"}}",
                "{\"ysc_collection\":{\"A\":12},\"A\":[]}",
                "{\"ysc_collection\":{\"A\":{}},\"A\":[]}",
                "{\"ysc_collection\":{\"A\":\"A\"},\"A\":[\"bad\"]}")) {
            assertThrows(GroupBackup.InvalidBackup.class, () -> GroupBackup.parse(text));
        }
    }

    @Test public void enforcesByteAndDepthLimits() {
        byte[] oversized = new byte[GroupBackup.MAX_BYTES + 1];
        assertThrows(GroupBackup.InvalidBackup.class, () -> GroupBackup.parse(new ByteArrayInputStream(oversized)));
        assertThrows(GroupBackup.InvalidBackup.class, () -> GroupBackup.parse(" ".repeat(GroupBackup.MAX_BYTES + 1)));
        assertThrows(GroupBackup.InvalidBackup.class, () -> GroupBackup.parse("{\"" + "界".repeat(GroupBackup.MAX_BYTES / 3) + "\":[]}"));
        assertThrows(GroupBackup.InvalidBackup.class, () -> GroupBackup.parse("[".repeat(40) + "]".repeat(40)));
    }

    @Test public void enforcesGroupAndMembershipLimits() {
        StringBuilder groups = new StringBuilder("{");
        for (int i = 0; i <= 2000; i++) {
            if (i > 0) groups.append(',');
            groups.append('"').append(i).append("\":[]");
        }
        groups.append('}');
        assertThrows(GroupBackup.InvalidBackup.class, () -> GroupBackup.parse(groups.toString()));
        String repeated = "{\"A\":[" + ("\"" + A + "\",").repeat(100000) + "\"" + A + "\"]}";
        assertThrows(GroupBackup.InvalidBackup.class, () -> GroupBackup.parse(repeated));
    }

    @Test public void propagatesReadFailures() {
        InputStream broken = new InputStream() {
            @Override public int read() throws IOException { throw new IOException("Unavailable provider"); }
        };
        assertThrows(IOException.class, () -> GroupBackup.parse(broken));
        assertThrows(GroupBackup.InvalidBackup.class, () -> GroupBackup.parse((InputStream) null));
    }

    @Test public void channelIdsAreStrictlyValidated() {
        assertTrue(GroupBackup.isChannelId(A));
        assertFalse(GroupBackup.isChannelId(null));
        assertFalse(GroupBackup.isChannelId(" " + A));
        assertFalse(GroupBackup.isChannelId(A + "/videos"));
    }

    @Test public void localEncodingRoundTripsUnicodeEscapesAndMembership() throws Exception {
        GroupBackup.Snapshot input = GroupBackup.parse("{\"学习 \\\"A\\\" \\\\ \\n\":[\"" + A + "\"]}");
        String encoded = GroupBackup.encode(input);
        GroupBackup.Snapshot output = GroupBackup.readStored(new ByteArrayInputStream(encoded.getBytes(StandardCharsets.UTF_8)));
        assertEquals(encoded, GroupBackup.encode(output));
    }

    @Test public void rejectsUnsupportedLocalSchemas() {
        for (String json : List.of("{}", "{\"schema\":2,\"groups\":{}}", "{\"schema\":\"1\",\"groups\":{}}",
                "{\"schema\":1,\"groups\":[]}")) {
            assertThrows(GroupBackup.InvalidBackup.class,
                    () -> GroupBackup.readStored(new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8))));
        }
    }
}
