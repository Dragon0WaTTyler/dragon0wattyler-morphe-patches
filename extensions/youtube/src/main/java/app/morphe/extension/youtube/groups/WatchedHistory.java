/* SPDX-License-Identifier: GPL-3.0-only
 * Independent Subscription Groups groups.6, modified 2026-11-02. See LICENSE/NOTICE.
 */
package app.morphe.extension.youtube.groups;

import org.json.JSONArray;
import org.json.JSONObject;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.nio.file.AtomicMoveNotSupportedException;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/** App-local completed-watch IDs only. Never reads or writes YouTube account history. */
public final class WatchedHistory {
    private final File file;
    public WatchedHistory(File directory) { file = new File(directory, "subscription-groups-watched-v6.json"); }
    public static boolean valid(String id) { return id != null && id.matches("[A-Za-z0-9_-]{11}"); }
    public Set<String> load() throws IOException {
        if (!file.isFile()) return Set.of();
        if (file.length() > 8 * 1024 * 1024) throw new IOException("Local watched history is too large");
        try {
            JSONObject json = new JSONObject(new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8));
            if (json.getInt("version") != 1) throw new IOException("Unsupported local watched history");
            JSONArray array = json.getJSONArray("watched"); Set<String> ids = new LinkedHashSet<>();
            for (int i = 0; i < array.length(); i++) { Object value = array.get(i); if (!(value instanceof String)) throw new IOException("Invalid watched ID type"); String id = (String) value; if (!valid(id)) throw new IOException("Invalid watched ID"); ids.add(id); }
            return Collections.unmodifiableSet(ids);
        } catch (IOException failure) { throw failure; }
        catch (Exception failure) { throw new IOException("Invalid local watched history", failure); }
    }
    public void save(Set<String> ids) throws IOException {
        try {
            for (String id : ids) if (!valid(id)) throw new IOException("Invalid watched ID");
            byte[] data = new JSONObject().put("version", 1).put("watched", new JSONArray(ids)).toString().getBytes(StandardCharsets.UTF_8);
            if (data.length > 8 * 1024 * 1024) throw new IOException("Local watched history is too large");
            file.getParentFile().mkdirs(); File tmp = new File(file.getPath() + ".tmp"); Files.write(tmp.toPath(), data);
            try { Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
            catch (AtomicMoveNotSupportedException unsupported) { Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING); }
        } catch (IOException failure) { throw failure; }
        catch (Exception failure) { throw new IOException("Could not persist local watched history", failure); }
    }
}
