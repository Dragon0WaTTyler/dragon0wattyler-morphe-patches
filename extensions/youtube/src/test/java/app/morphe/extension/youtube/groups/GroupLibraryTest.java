/* SPDX-License-Identifier: GPL-3.0-only
 * Independent Subscription Groups V2, 2026-11-01. See LICENSE and NOTICE.
 */
package app.morphe.extension.youtube.groups;

import org.junit.Test;
import org.junit.Rule;
import org.junit.rules.TemporaryFolder;
import java.io.File;
import java.nio.file.Files;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;
import static org.junit.Assert.*;

public class GroupLibraryTest {
    private static final String A = "UCJ3AxeCHGPZkMi3kRfCuiHw", B = "UCvtT19MZW8dq5Wwfu6B0oxw";
    @Rule public TemporaryFolder folder = new TemporaryFolder();
    private GroupLibrary library() { return GroupLibrary.empty().create("Books").create("Music"); }
    @Test public void defaultIsOriginalFeedAndEmptyStateHasUnsetHistory() {
        GroupLibrary data = library(); assertEquals("", GroupSelection.enter(data, true));
        assertFalse(data.preferences.enabled("remember")); assertEquals("unselected", data.preferences.get("last_group"));
    }
    @Test public void defaultAndRememberedAndDeletedFallbacks() {
        GroupLibrary data = library(); String books = data.groups.get(0).id, music = data.groups.get(1).id;
        data = data.preferences(data.preferences.with("default", books));
        assertEquals(books, GroupSelection.enter(data, true));
        data = data.preferences(data.preferences.with("last_group", music));
        assertEquals(books, GroupSelection.enter(data, true));
        data = data.preferences(data.preferences.with("remember", "true"));
        assertEquals(music, GroupSelection.enter(data, true));
        data = data.delete(music); assertEquals(books, GroupSelection.enter(data, true));
        data = data.delete(books); assertEquals("", GroupSelection.enter(data, true));
    }
    @Test public void rememberedAllIsDifferentFromNoRememberedSelection() {
        GroupLibrary data = library(); String books = data.groups.get(0).id;
        data = data.preferences(data.preferences.with("default", books).with("remember", "true"));
        assertEquals(books, GroupSelection.enter(data, true));
        data = data.preferences(data.preferences.with("last_group", ""));
        assertEquals("", GroupSelection.enter(data, true));
    }
    @Test public void integrationDisabledAlwaysRestoresAll() {
        GroupLibrary data = library(); String id = data.groups.get(0).id;
        data = data.preferences(data.preferences.with("default", id));
        assertEquals("", GroupSelection.enter(data, false)); assertEquals("", GroupSelection.reconcile(data, id, false));
    }
    @Test public void renamingKeepsIdentityDefaultAndMembership() {
        GroupLibrary data = library(); String id = data.groups.get(0).id;
        data = data.membership(id, A, true).preferences(data.preferences.with("default", id));
        data = data.edit(id, "文学 🎓", "📚", true);
        assertEquals(id, GroupSelection.enter(data, true)); assertEquals(List.of(A), data.find(id).channels);
        assertEquals("文学 🎓", data.find(id).name); assertTrue(data.find(id).pinned);
    }
    @Test public void invalidAndDuplicateNamesNeverMutateSource() {
        GroupLibrary data = library(); assertThrows(IllegalArgumentException.class, () -> data.create("Books"));
        assertThrows(IllegalArgumentException.class, () -> data.create(" ")); assertEquals(2, data.groups.size());
        assertEquals(3, data.create("books").groups.size());
    }
    @Test public void copiesMovesAndMultipleMembershipAreLocalAndDeduplicated() {
        GroupLibrary data = library(); String first = data.groups.get(0).id, second = data.groups.get(1).id;
        data = data.membership(first, A, true).membership(first, A, true);
        assertEquals(1, data.memberships);
        data = data.transfer(first, second, A, false); assertEquals(2, data.memberships);
        data = data.transfer(first, second, A, true); assertEquals(1, data.memberships);
        assertTrue(data.find(first).channels.isEmpty()); assertEquals(List.of(A), data.find(second).channels);
        data = data.assign(A, Set.of(first, second), Set.of(first, second)); assertEquals(2, data.memberships);
        data = data.assign(A, Set.of(first), Set.of(first, second)); assertEquals(1, data.memberships);
        assertTrue(data.find(second).channels.isEmpty());
    }
    @Test public void deletionDoesNotDeleteOtherGroupsMembership() {
        GroupLibrary data = library(); String first = data.groups.get(0).id, second = data.groups.get(1).id;
        data = data.membership(first, A, true).membership(second, A, true).delete(first);
        assertEquals(List.of(A), data.find(second).channels);
        assertEquals("", GroupSelection.reconcile(data, first, true));
    }
    @Test public void reorderingAndPinsAndVisibleEmptyGroups() {
        GroupLibrary data = library(); String first = data.groups.get(0).id, second = data.groups.get(1).id;
        data = data.move(second, -1); assertEquals(second, data.groups.get(0).id);
        data = data.edit(first, "Books", "", true); assertEquals(first, data.ordered(false).get(0).id);
        data = data.membership(second, A, true).preferences(data.preferences.with("show_empty", "false"));
        assertEquals(1, data.ordered(true).size()); assertEquals(second, data.ordered(true).get(0).id);
    }
    @Test public void mergeUnionsMembershipAndPreservesExistingOrderIdsAndPreferences() throws Exception {
        GroupLibrary data = library(); String books = data.groups.get(0).id, music = data.groups.get(1).id;
        data = data.membership(books, A, true).preferences(data.preferences.with("default", books));
        GroupLibrary incoming = GroupLibraryCodec.parse("{\"Books\":[\"" + B + "\"],\"New\":[]}").library;
        data = data.importLibrary(incoming, true, false);
        assertEquals(List.of(A, B), data.find(books).channels); assertEquals(music, data.groups.get(1).id);
        assertEquals(books, data.preferences.get("default")); assertEquals(3, data.groups.size());
    }
    @Test public void plainReplacementPreservesMatchingIdsButReplacesMembership() throws Exception {
        GroupLibrary data = library(); String books = data.groups.get(0).id;
        data = data.membership(books, A, true);
        data = data.importLibrary(GroupLibraryCodec.parse("{\"Books\":[\"" + B + "\"]}").library, false, false);
        assertEquals(books, data.groups.get(0).id); assertEquals(List.of(B), data.find(books).channels); assertEquals(1, data.groups.size());
    }
    @Test public void fullBackupRoundTripsEveryPreferenceOrderAndMetadata() throws Exception {
        GroupLibrary data = library(); String id = data.groups.get(0).id;
        data = data.edit(id, "ysc_collection", "📚", true).membership(id, A, true).move(id, 1);
        GroupPreferences prefs = data.preferences.with("default", id).with("remember", "true").with("last_group", id)
                .with("style", "dropdown").with("spacing", "compact").with("scroll_x", "123").with("range", "week");
        data = data.preferences(prefs);
        GroupLibraryCodec.Import decoded = GroupLibraryCodec.parse(GroupLibraryCodec.encode(data));
        assertTrue(decoded.fullBackup); assertEquals(data.preferences.values, decoded.library.preferences.values);
        assertEquals(data.groups.get(0).id, decoded.library.groups.get(0).id);
        assertEquals("📚", decoded.library.find(id).icon); assertEquals(List.of(A), decoded.library.find(id).channels);
    }
    @Test public void membershipOnlyExportStillUsesV1Parser() throws Exception {
        GroupLibrary data = library(); data = data.membership(data.groups.get(0).id, A, true);
        assertEquals(1, GroupBackup.parse(GroupLibraryCodec.encodeMemberships(data)).memberships);
    }
    @Test public void duplicateCountIsReportedForPreview() throws Exception {
        GroupLibraryCodec.Import data = GroupLibraryCodec.parse("{\"Books\":[\"" + A + "\",\"" + A + "\"]}");
        assertEquals(1, data.duplicates); assertEquals(1, data.library.memberships);
    }
    @Test public void fullReplacementRestoresAllPreferencesFromDisk() throws Exception {
        GroupLibrary saved = library(); String id = saved.groups.get(0).id;
        GroupPreferences prefs = saved.preferences.with("default", id).with("last_group", "")
                .with("order", "count").with("style", "dropdown").with("spacing", "compact")
                .with("placement", "below").with("sort", "views").with("range", "month")
                .with("last_sort", "oldest").with("last_range", "today").with("scroll_x", "789");
        for (String key : GroupPreferences.TOGGLES) prefs = prefs.with(key, Boolean.toString(!prefs.enabled(key)));
        saved = saved.preferences(prefs);
        GroupLibrary restored = GroupLibrary.empty().create("Unrelated").importLibrary(
                GroupLibraryCodec.parse(GroupLibraryCodec.encode(saved)).library, false, true);
        GroupLibraryStore store = new GroupLibraryStore(folder.getRoot()); store.save(restored);
        GroupLibrary reopened = store.load(); assertEquals(prefs.values, reopened.preferences.values);
        assertEquals(id, reopened.groups.get(0).id); assertNull(reopened.named("Unrelated"));
    }
    @Test public void assignmentDoesNotEraseMembershipAddedOutsideItsEditedScope() {
        GroupLibrary data = library(); String first = data.groups.get(0).id, second = data.groups.get(1).id;
        data = data.membership(second, A, true).create("Added while assignment was open");
        String concurrent = data.groups.get(2).id;
        data = data.membership(concurrent, A, true).assign(A, Set.of(first), Set.of(first, second));
        assertEquals(List.of(A), data.find(first).channels); assertTrue(data.find(second).channels.isEmpty());
        assertEquals(List.of(A), data.find(concurrent).channels);
    }
    @Test public void v1FileMigratesExactlyOnceWithoutBeingChanged() throws Exception {
        GroupStore legacy = new GroupStore(folder.getRoot()); legacy.replace(GroupBackup.parse("{\"Books\":[\"" + A + "\"]}"));
        File v1 = new File(folder.getRoot(), "subscription-groups-v1.json"); byte[] bytes = Files.readAllBytes(v1.toPath());
        GroupLibraryStore store = new GroupLibraryStore(folder.getRoot()); GroupLibrary migrated = store.load();
        String id = migrated.groups.get(0).id; store.save(migrated.preferences(migrated.preferences.with("default", id)));
        GroupLibrary reopened = new GroupLibraryStore(folder.getRoot()).load();
        assertEquals(id, reopened.groups.get(0).id); assertEquals(id, GroupSelection.enter(reopened, true));
        assertArrayEquals(bytes, Files.readAllBytes(v1.toPath()));
    }
    @Test public void corruptionAndUnknownSchemaCannotSilentlyResetStorage() throws Exception {
        GroupLibraryStore store = new GroupLibraryStore(folder.getRoot()); store.save(library());
        File file = new File(folder.getRoot(), "subscription-groups-v2.json"); byte[] before = Files.readAllBytes(file.toPath());
        assertThrows(Exception.class, () -> store.save(GroupLibraryCodec.parse("{broken").library));
        assertArrayEquals(before, Files.readAllBytes(file.toPath()));
        String invalid = new String(before, StandardCharsets.UTF_8).replace("\"schema\":2", "\"schema\":999");
        Files.write(file.toPath(), invalid.getBytes(StandardCharsets.UTF_8));
        assertThrows(Exception.class, store::load); assertEquals(invalid, new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8));
    }
    @Test public void preferencesRejectUnsupportedOptions() {
        GroupPreferences prefs = new GroupPreferences(); assertThrows(IllegalArgumentException.class, () -> prefs.with("sort", "duration"));
        assertThrows(IllegalArgumentException.class, () -> prefs.with("remember", "yes"));
        assertThrows(IllegalArgumentException.class, () -> prefs.with("scroll_x", "-1"));
        assertThrows(IllegalArgumentException.class, () -> prefs.with("default", "../invalid"));
    }
    @Test public void fifteenGroupsAnd1253MembershipsRoundTrip() throws Exception {
        GroupLibrary data = GroupLibrary.empty();
        for (int i = 0; i < 15; i++) data = data.create("Group " + i);
        for (int i = 0; i < 1253; i++) {
            String channel = "UC" + String.format(java.util.Locale.ROOT, "%022d", i % 800);
            data = data.membership(data.groups.get(i % 15).id, channel, true);
        }
        assertEquals(1253, data.memberships); GroupLibraryStore store = new GroupLibraryStore(folder.getRoot()); store.save(data);
        assertEquals(1253, store.load().memberships); assertEquals(15, store.load().groups.size());
    }
}
