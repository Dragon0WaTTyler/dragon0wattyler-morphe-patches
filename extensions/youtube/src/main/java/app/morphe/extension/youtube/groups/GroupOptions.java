/* SPDX-License-Identifier: GPL-3.0-only
 * Independent Subscription Groups V2, 2026-11-01. See LICENSE and NOTICE.
 */
package app.morphe.extension.youtube.groups;

import android.app.AlertDialog;
import android.content.Context;
import java.util.ArrayList;
import java.util.List;
import static app.morphe.extension.shared.StringRef.str;

final class GroupOptions {
    static final List<String> SELECTION = List.of("default", "remember", "selector", "feed");
    static final List<String> APPEARANCE = List.of("style", "spacing", "icons", "counts", "order", "show_empty", "show_all", "placement", "remember_scroll");
    static final List<String> FEED = List.of("sort", "range", "remember_filters", "hide_watched", "remember_content", "default_content", "clear_watched");
    static final List<String> ALL;
    static { List<String> keys = new ArrayList<>(SELECTION); keys.addAll(APPEARANCE); keys.addAll(FEED); ALL = java.util.Collections.unmodifiableList(keys); }
    static String title(String key) { return str("morphe_pockettube_v2_" + key); }
    static String summary(GroupLibrary library, String key) {
        if (key.equals("hide_watched")) return str(library.preferences.enabled(key) ? "morphe_pockettube_v2_on" : "morphe_pockettube_v2_off") + " · " + str("morphe_pockettube_g6_local_scope");
        if (key.equals("clear_watched")) return str("morphe_pockettube_g6_local_scope");
        if (GroupPreferences.TOGGLES.contains(key)) return str(library.preferences.enabled(key) ? "morphe_pockettube_v2_on" : "morphe_pockettube_v2_off");
        if (key.equals("default")) {
            GroupLibrary.Entry group = library.find(library.preferences.get(key));
            return group == null ? str("morphe_pockettube_v2_original") : group.name;
        }
        if (key.equals("default_content")) return str("morphe_pockettube_g5_filter_" + library.preferences.get(key));
        return str("morphe_pockettube_v2_choice_" + library.preferences.get(key));
    }
    static void choose(Context context, GroupService service, String key) {
        if (key.equals("clear_watched")) {
            new AlertDialog.Builder(context).setTitle(title(key)).setMessage(str("morphe_pockettube_g6_clear_confirm"))
                    .setPositiveButton(android.R.string.ok, (dialog, which) -> WatchedService.get(context).clear())
                    .setNegativeButton(android.R.string.cancel, null).show(); return;
        }
        if (!service.ready()) return;
        GroupLibrary data = service.library();
        if (GroupPreferences.TOGGLES.contains(key)) { service.preference(key, Boolean.toString(!data.preferences.enabled(key))); return; }
        List<String> values = new ArrayList<>(), labels = new ArrayList<>();
        if (key.equals("default")) {
            values.add(""); labels.add(str("morphe_pockettube_v2_original"));
            for (GroupLibrary.Entry group : data.ordered(false)) { values.add(group.id); labels.add(group.name); }
        } else {
            values.addAll(GroupPreferences.CHOICES.get(key));
            for (String value : values) labels.add(str((key.equals("default_content") ? "morphe_pockettube_g5_filter_" : "morphe_pockettube_v2_choice_") + value));
        }
        new AlertDialog.Builder(context).setTitle(title(key)).setSingleChoiceItems(labels.toArray(new String[0]),
                Math.max(0, values.indexOf(data.preferences.get(key))), (dialog, which) -> {
                    service.preference(key, values.get(which)); dialog.dismiss();
                }).setNegativeButton(android.R.string.cancel, null).show();
    }
    static void aboutFeed(Context context) {
        new AlertDialog.Builder(context).setTitle(str("morphe_pockettube_v2_about_feed"))
                .setMessage(str("morphe_pockettube_v2_feed_explanation"))
                .setPositiveButton(android.R.string.ok, null).show();
    }
    private GroupOptions() {}
}
