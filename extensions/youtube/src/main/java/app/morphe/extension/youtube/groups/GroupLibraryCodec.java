/* SPDX-License-Identifier: GPL-3.0-only
 * Independent Subscription Groups V2, 2026-11-01. See LICENSE and NOTICE.
 */
package app.morphe.extension.youtube.groups;

import org.json.JSONArray;
import org.json.JSONObject;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Strict V1 imports and a lossless V2 envelope. The envelope avoids reserved-name collisions. */
public final class GroupLibraryCodec {
    public static final class Import {
        public final GroupLibrary library;
        public final boolean fullBackup;
        public final int duplicates;
        Import(GroupLibrary library, boolean fullBackup, int duplicates) {
            this.library = library; this.fullBackup = fullBackup; this.duplicates = duplicates;
        }
    }
    public static Import parse(InputStream input) throws IOException { return parse(GroupBackup.read(input)); }
    public static Import parse(String text) throws IOException {
        try {
            GroupBackup.validateJson(text); // Same strict JSON grammar and limits as V1.
            JSONObject root = new JSONObject(text.startsWith("\uFEFF") ? text.substring(1) : text);
            if (!(root.opt("morphe_groups_v2") instanceof JSONObject)) {
                GroupBackup.Snapshot parsed = GroupBackup.parse(text);
                return new Import(GroupLibrary.fromV1(parsed), false, parsed.duplicateMemberships);
            }
            JSONObject metadata = root.getJSONObject("morphe_groups_v2");
            if (root.length() != 1 || !(metadata.get("schema") instanceof Number)
                    || !"2".equals(metadata.get("schema").toString())) throw new IllegalArgumentException("Unsupported group backup version");
            JSONArray order = metadata.getJSONArray("groups");
            List<GroupLibrary.Entry> groups = new ArrayList<>(); Set<String> names = new HashSet<>(); int duplicates = 0, memberships = 0;
            for (int i = 0; i < order.length(); i++) {
                JSONObject row = order.getJSONObject(i); String name = string(row, "name");
                if (!names.add(name) || !(row.get("pinned") instanceof Boolean)) throw new IllegalArgumentException("Invalid group metadata");
                JSONArray channels = row.getJSONArray("channels"); List<String> ids = new ArrayList<>();
                memberships += channels.length();
                if (memberships > 100000) throw new IllegalArgumentException("Too many memberships");
                for (int j = 0; j < channels.length(); j++) {
                    Object id = channels.get(j); if (!(id instanceof String)) throw new IllegalArgumentException("Invalid channel ID");
                    ids.add((String) id);
                }
                duplicates += ids.size() - new HashSet<>(ids).size();
                groups.add(new GroupLibrary.Entry(string(row, "id"), name, string(row, "icon"), row.getBoolean("pinned"), ids));
            }
            GroupPreferences prefs = new GroupPreferences(); JSONObject values = metadata.getJSONObject("preferences");
            java.util.Iterator<String> keys = values.keys();
            while (keys.hasNext()) {
                String key = keys.next();
                if (GroupPreferences.DEFAULTS.containsKey(key) || GroupPreferences.contentKey(key)) prefs = prefs.with(key, string(values, key));
            }
            return new Import(new GroupLibrary(groups, prefs), true, duplicates);
        } catch (Exception failure) { throw new GroupBackup.InvalidBackup("Invalid V2 group metadata: " + failure.getMessage()); }
    }
    private static String string(JSONObject json, String key) throws Exception {
        Object value = json.get(key); if (!(value instanceof String)) throw new IllegalArgumentException("Expected text"); return (String) value;
    }
    public static String encode(GroupLibrary library) throws IOException {
        try {
            JSONArray order = new JSONArray();
            for (GroupLibrary.Entry group : library.groups) {
                order.put(new JSONObject().put("id", group.id).put("name", group.name).put("icon", group.icon)
                        .put("pinned", group.pinned).put("channels", new JSONArray(group.channels)));
            }
            JSONObject prefs = new JSONObject(); library.preferences.values.forEach((key, value) -> { try { prefs.put(key, value); } catch (Exception impossible) { throw new IllegalStateException(impossible); } });
            String text = new JSONObject().put("morphe_groups_v2", new JSONObject()
                    .put("schema", 2).put("groups", order).put("preferences", prefs)).toString();
            GroupBackup.validateJson(text);
            return text;
        } catch (IOException failure) { throw failure; }
        catch (Exception failure) { throw new IOException("Could not encode group backup", failure); }
    }
    public static String encodeMemberships(GroupLibrary library) throws IOException {
        StringBuilder out = new StringBuilder("{"); boolean firstGroup = true;
        for (GroupLibrary.Entry group : library.groups) {
            if (!firstGroup) out.append(','); firstGroup = false;
            GroupBackup.quote(out, group.name); out.append(":["); boolean firstId = true;
            for (String id : group.channels) { if (!firstId) out.append(','); firstId = false; GroupBackup.quote(out, id); }
            out.append(']');
        }
        String text = out.append('}').toString(); GroupBackup.parse(text); return text;
    }
    private GroupLibraryCodec() {}
}
