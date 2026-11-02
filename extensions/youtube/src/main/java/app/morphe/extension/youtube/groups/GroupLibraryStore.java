/* SPDX-License-Identifier: GPL-3.0-only
 * Independent Subscription Groups V2, 2026-11-01. See LICENSE and NOTICE.
 */
package app.morphe.extension.youtube.groups;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/** One atomic file for membership, identities, appearance and selection preferences. V1 is never removed. */
public final class GroupLibraryStore {
    private final File directory, file;
    public GroupLibraryStore(File directory) { this.directory = directory; file = new File(directory, "subscription-groups-v2.json"); }
    public synchronized GroupLibrary load() throws IOException {
        if (file.exists()) {
            try (InputStream in = new FileInputStream(file)) {
                GroupLibraryCodec.Import data = GroupLibraryCodec.parse(in);
                if (!data.fullBackup) throw new IOException("Missing V2 storage metadata");
                return data.library;
            }
        }
        GroupLibrary migrated = GroupLibrary.fromV1(new GroupStore(directory).load());
        save(migrated); return migrated;
    }
    public synchronized void save(GroupLibrary library) throws IOException {
        byte[] bytes = GroupLibraryCodec.encode(library).getBytes(StandardCharsets.UTF_8);
        if (bytes.length > GroupBackup.MAX_BYTES) throw new IOException("Group backup exceeds 4 MiB");
        GroupStore.atomicWrite(file, bytes);
    }
}
