/* SPDX-License-Identifier: GPL-3.0-only
 * Independent Subscription Groups additions, 2026-11-01. See LICENSE and NOTICE.
 */
package app.morphe.extension.youtube.groups;

import static org.junit.Assert.*;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

public class GroupStoreTest {
    @Rule public TemporaryFolder temporary = new TemporaryFolder();
    private static final String BACKUP = "{\"A\":[\"UCJ3AxeCHGPZkMi3kRfCuiHw\"],\"B\":[\"UCJ3AxeCHGPZkMi3kRfCuiHw\"]}";

    @Test public void initiallyEmpty() throws Exception {
        assertTrue(new GroupStore(temporary.getRoot()).load().groups.isEmpty());
    }

    @Test public void persistsAcrossRepositoryRecreationAndReplaces() throws Exception {
        GroupStore first = new GroupStore(temporary.getRoot());
        first.replace(GroupBackup.parse(BACKUP));
        GroupStore recreated = new GroupStore(temporary.getRoot());
        assertEquals(2, recreated.load().memberships);
        recreated.replace(GroupBackup.parse("{\"New\":[]}"));
        assertEquals(1, first.load().groups.size());
        assertEquals("New", first.load().groups.get(0).name);
        assertEquals(1, temporary.getRoot().list().length);
    }

    @Test public void corruptedImportLeavesSavedFileByteIdentical() throws Exception {
        GroupStore store = new GroupStore(temporary.getRoot());
        store.replace(GroupBackup.parse(BACKUP));
        File file = new File(temporary.getRoot(), "subscription-groups-v1.json");
        byte[] before = Files.readAllBytes(file.toPath());
        assertThrows(IOException.class, () -> store.replace(GroupBackup.parse("{\"Bad\":[")));
        assertArrayEquals(before, Files.readAllBytes(file.toPath()));
        assertEquals(2, store.load().memberships);
    }

    @Test public void corruptedStoredFileFailsWithoutDeletingEvidence() throws Exception {
        File file = new File(temporary.getRoot(), "subscription-groups-v1.json");
        Files.write(file.toPath(), "{broken".getBytes(StandardCharsets.UTF_8));
        assertThrows(IOException.class, () -> new GroupStore(temporary.getRoot()).load());
        assertEquals("{broken", new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8));
    }

    @Test public void failedAtomicReplacementCleansTemporaryAndKeepsTarget() throws Exception {
        File target = temporary.newFolder("subscription-groups-v1.json");
        File marker = new File(target, "existing");
        Files.write(marker.toPath(), "keep".getBytes(StandardCharsets.UTF_8));
        assertThrows(IOException.class,
                () -> new GroupStore(temporary.getRoot()).replace(GroupBackup.parse(BACKUP)));
        assertEquals("keep", new String(Files.readAllBytes(marker.toPath()), StandardCharsets.UTF_8));
        assertEquals(1, temporary.getRoot().list().length);
    }

    @Test public void emptyCollectionCanReplaceAfterExplicitConfirmation() throws Exception {
        GroupStore store = new GroupStore(temporary.getRoot());
        store.replace(GroupBackup.parse(BACKUP));
        store.replace(GroupBackup.empty());
        assertTrue(store.load().groups.isEmpty());
    }
}
