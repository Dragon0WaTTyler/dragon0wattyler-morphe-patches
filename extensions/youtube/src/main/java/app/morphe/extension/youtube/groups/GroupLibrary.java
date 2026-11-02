/* SPDX-License-Identifier: GPL-3.0-only
 * Independent Subscription Groups V2, 2026-11-01. See LICENSE and NOTICE.
 */
package app.morphe.extension.youtube.groups;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Stable group identities survive renaming; membership changes never touch YouTube subscriptions. */
public final class GroupLibrary {
    public static final class Entry {
        public final String id, name, icon;
        public final boolean pinned;
        public final List<String> channels;
        public Entry(String id, String name, String icon, boolean pinned, Collection<String> channels) {
            if (!validId(id) || name == null || icon == null || icon.length() > 16) throw new IllegalArgumentException("Invalid group metadata");
            this.id = id; this.name = name; this.icon = icon; this.pinned = pinned;
            this.channels = Collections.unmodifiableList(new ArrayList<>(new LinkedHashSet<>(channels)));
        }
        Entry members(Collection<String> channels) { return new Entry(id, name, icon, pinned, channels); }
    }
    public final List<Entry> groups;
    public final GroupPreferences preferences;
    public final int memberships;
    public GroupLibrary(List<Entry> entries, GroupPreferences preferences) {
        Map<String, Object> map = new LinkedHashMap<>(); Set<String> ids = new HashSet<>();
        for (Entry entry : entries) {
            if (!ids.add(entry.id) || map.put(entry.name, entry.channels) != null) throw new IllegalArgumentException("Duplicate group identity or name");
        }
        try { memberships = GroupBackup.fromGroups(map).memberships; }
        catch (GroupBackup.InvalidBackup failure) { throw new IllegalArgumentException(failure.getMessage(), failure); }
        this.groups = Collections.unmodifiableList(new ArrayList<>(entries));
        this.preferences = preferences.retainGroups(ids);
    }
    public static GroupLibrary empty() { return new GroupLibrary(List.of(), new GroupPreferences()); }
    public static boolean validId(String id) { return id != null && id.matches("[A-Za-z0-9-]{1,64}"); }
    public static GroupLibrary fromV1(GroupBackup.Snapshot snapshot) {
        List<Entry> entries = new ArrayList<>();
        for (GroupBackup.Group group : snapshot.groups) entries.add(new Entry(UUID.randomUUID().toString(), group.name, "", false, group.channelIds));
        return new GroupLibrary(entries, new GroupPreferences());
    }
    public Entry find(String id) { for (Entry group : groups) if (group.id.equals(id)) return group; return null; }
    public Entry named(String name) { for (Entry group : groups) if (group.name.equals(name)) return group; return null; }
    public GroupLibrary preferences(GroupPreferences prefs) { return new GroupLibrary(groups, prefs); }
    public GroupLibrary create(String name) {
        List<Entry> next = new ArrayList<>(groups);
        next.add(new Entry(UUID.randomUUID().toString(), name, "", false, List.of()));
        return new GroupLibrary(next, preferences);
    }
    public GroupLibrary edit(String id, String name, String icon, boolean pinned) {
        Entry old = required(id); List<Entry> next = new ArrayList<>();
        for (Entry group : groups) next.add(group == old ? new Entry(id, name, icon, pinned, old.channels) : group);
        return new GroupLibrary(next, preferences);
    }
    public GroupLibrary delete(String id) {
        required(id); List<Entry> next = new ArrayList<>(groups); next.removeIf(g -> g.id.equals(id));
        return new GroupLibrary(next, preferences);
    }
    public GroupLibrary move(String id, int delta) {
        Entry entry = required(id); List<Entry> next = new ArrayList<>(groups); int at = next.indexOf(entry);
        next.remove(at); next.add(Math.max(0, Math.min(next.size(), at + delta)), entry);
        return new GroupLibrary(next, preferences);
    }
    public GroupLibrary membership(String groupId, String channelId, boolean add) {
        if (!GroupBackup.isChannelId(channelId)) throw new IllegalArgumentException("Invalid channel ID");
        Entry old = required(groupId); LinkedHashSet<String> channels = new LinkedHashSet<>(old.channels);
        if (add) channels.add(channelId); else channels.remove(channelId);
        List<Entry> next = new ArrayList<>(); for (Entry group : groups) next.add(group == old ? old.members(channels) : group);
        return new GroupLibrary(next, preferences);
    }
    public GroupLibrary assign(String channel, Set<String> selectedGroups, Set<String> editedGroups) {
        GroupLibrary next = this;
        for (Entry group : groups) if (editedGroups.contains(group.id)) next = next.membership(group.id, channel, selectedGroups.contains(group.id));
        return next;
    }
    public GroupLibrary transfer(String from, String to, String channel, boolean move) {
        if (from.equals(to)) return this;
        if (!required(from).channels.contains(channel)) throw new IllegalArgumentException("Channel no longer belongs to the source group");
        required(to);
        GroupLibrary next = membership(to, channel, true);
        return move ? next.membership(from, channel, false) : next;
    }
    public GroupLibrary importLibrary(GroupLibrary incoming, boolean merge, boolean fullBackup) {
        List<Entry> next = merge ? new ArrayList<>(groups) : new ArrayList<>();
        Set<String> ids = new HashSet<>(); if (merge) for (Entry g : next) ids.add(g.id);
        for (Entry imported : incoming.groups) {
            Entry existing = named(imported.name);
            Entry chosen;
            if (existing != null && (merge || !fullBackup)) {
                LinkedHashSet<String> channels = new LinkedHashSet<>();
                if (merge) channels.addAll(existing.channels); channels.addAll(imported.channels);
                chosen = existing.members(channels);
            } else {
                String id = imported.id;
                if (ids.contains(id)) id = UUID.randomUUID().toString();
                chosen = new Entry(id, imported.name, imported.icon, imported.pinned, imported.channels);
            }
            if (merge && existing != null) next.remove(existing);
            if (merge && existing != null) next.add(Math.min(groups.indexOf(existing), next.size()), chosen); else next.add(chosen);
            ids.add(chosen.id);
        }
        return new GroupLibrary(next, !merge && fullBackup ? incoming.preferences : preferences);
    }
    public List<Entry> ordered(boolean visibleOnly) {
        List<Entry> result = new ArrayList<>(groups);
        if (visibleOnly && !preferences.enabled("show_empty")) result.removeIf(g -> g.channels.isEmpty());
        Comparator<Entry> order = Comparator.comparing((Entry g) -> !g.pinned);
        if (preferences.get("order").equals("name")) order = order.thenComparing(g -> g.name, String.CASE_INSENSITIVE_ORDER);
        if (preferences.get("order").equals("count")) order = order.thenComparing(Comparator.comparingInt((Entry g) -> g.channels.size()).reversed());
        result.sort(order); return result;
    }
    private Entry required(String id) { Entry group = find(id); if (group == null) throw new IllegalArgumentException("Group no longer exists"); return group; }
}
