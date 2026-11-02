/* SPDX-License-Identifier: GPL-3.0-only
 * Independent Subscription Groups additions, 2026-11-01. See LICENSE and NOTICE.
 */
package app.morphe.extension.youtube.groups;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/** App-private, versioned storage. Call on a worker thread. Invalid imports cannot overwrite it. */
public final class GroupStore {
    private final File file;

    public GroupStore(File directory) { file = new File(directory, "subscription-groups-v1.json"); }

    public synchronized GroupBackup.Snapshot load() throws IOException {
        if (!file.exists()) return GroupBackup.empty();
        try (InputStream input = new FileInputStream(file)) { return GroupBackup.readStored(input); }
    }

    public synchronized void replace(GroupBackup.Snapshot snapshot) throws IOException {
        byte[] bytes = GroupBackup.encode(snapshot).getBytes(StandardCharsets.UTF_8);
        if (bytes.length > GroupBackup.MAX_BYTES) throw new IOException("Local collection exceeds size limit");
        atomicWrite(file, bytes);
    }

    static void atomicWrite(File file, byte[] bytes) throws IOException {
        Path temporary = Files.createTempFile(file.getParentFile().toPath(), "subscription-groups-", ".tmp");
        try {
            try (FileOutputStream out = new FileOutputStream(temporary.toFile())) {
                out.write(bytes);
                out.getFD().sync();
            }
            // Same-directory atomic rename. If unsupported, fail and retain the previous file.
            Files.move(temporary, file.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }
}
