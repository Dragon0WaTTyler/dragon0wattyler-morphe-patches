/* SPDX-License-Identifier: GPL-3.0-only
 * Independent Subscription Groups V2, 2026-11-01. See LICENSE and NOTICE.
 */
package app.morphe.extension.youtube.groups;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Typed, immutable preferences persisted in the same atomic transaction as group data. */
public final class GroupPreferences {
    public static final Map<String, String> DEFAULTS;
    public static final Map<String, List<String>> CHOICES;
    public static final List<String> TOGGLES = List.of("remember", "selector", "feed", "icons", "counts",
            "show_empty", "show_all", "remember_scroll", "remember_filters", "hide_watched", "remember_content");
    static {
        Map<String, String> defaults = new LinkedHashMap<>();
        defaults.put("default", ""); defaults.put("last_group", "unselected");
        defaults.put("remember", "false"); defaults.put("selector", "true"); defaults.put("feed", "true");
        defaults.put("icons", "true"); defaults.put("counts", "false"); defaults.put("show_empty", "true");
        defaults.put("show_all", "true"); defaults.put("remember_scroll", "true"); defaults.put("scroll_x", "0");
        defaults.put("remember_filters", "false");
        defaults.put("hide_watched", "false"); defaults.put("remember_content", "true");
        Map<String, List<String>> choices = new LinkedHashMap<>();
        choices.put("order", List.of("manual", "name", "count"));
        choices.put("style", List.of("chips", "dropdown"));
        choices.put("spacing", List.of("comfortable", "compact"));
        choices.put("placement", List.of("above", "below"));
        choices.put("sort", List.of("newest", "oldest", "views"));
        choices.put("range", List.of("any", "today", "week", "month"));
        choices.put("default_content", List.of("all", "today", "videos", "shorts", "live", "podcasts"));
        choices.put("last_sort", choices.get("sort")); choices.put("last_range", choices.get("range"));
        choices.forEach((key, values) -> defaults.put(key, values.get(0)));
        defaults.put("last_sort", "unselected"); defaults.put("last_range", "unselected");
        DEFAULTS = Collections.unmodifiableMap(defaults);
        CHOICES = Collections.unmodifiableMap(choices);
    }
    public final Map<String, String> values;
    public GroupPreferences() { values = DEFAULTS; }
    private GroupPreferences(Map<String, String> values) { this.values = Collections.unmodifiableMap(values); }
    public String get(String key) { return values.get(key); }
    public boolean enabled(String key) { return "true".equals(get(key)); }
    static final String CONTENT_PREFIX = "content_group_";
    public static boolean contentKey(String key) { return key != null && key.startsWith(CONTENT_PREFIX) && GroupLibrary.validId(key.substring(CONTENT_PREFIX.length())); }
    public GroupPreferences retainGroups(java.util.Set<String> ids) {
        Map<String, String> next = new LinkedHashMap<>(values);
        next.keySet().removeIf(key -> contentKey(key) && !ids.contains(key.substring(CONTENT_PREFIX.length())));
        return next.equals(values) ? this : new GroupPreferences(next);
    }
    public GroupPreferences with(String key, String value) {
        if ((!DEFAULTS.containsKey(key) && !contentKey(key)) || value == null) throw new IllegalArgumentException("Unknown preference");
        if (key.equals("default_content") || contentKey(key)) value = ContentFilterSelection.safe(value);
        if (TOGGLES.contains(key) && !List.of("true", "false").contains(value)) throw new IllegalArgumentException("Invalid toggle");
        if (CHOICES.containsKey(key) && !CHOICES.get(key).contains(value)
                && !(key.startsWith("last_") && value.equals("unselected"))) throw new IllegalArgumentException("Invalid choice");
        if ((key.equals("default") || key.equals("last_group")) && !value.isEmpty() && !GroupLibrary.validId(value)) {
            throw new IllegalArgumentException("Invalid group reference");
        }
        if (key.equals("scroll_x")) {
            int x = Integer.parseInt(value);
            if (x < 0 || x > 1000000) throw new IllegalArgumentException("Invalid scroll position");
        }
        Map<String, String> next = new LinkedHashMap<>(values); next.put(key, value);
        return new GroupPreferences(next);
    }
}
