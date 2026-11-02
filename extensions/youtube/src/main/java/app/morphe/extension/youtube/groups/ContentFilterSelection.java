/* SPDX-License-Identifier: GPL-3.0-only
 * Independent Subscription Groups groups.6, modified 2026-11-02. See LICENSE/NOTICE.
 */
package app.morphe.extension.youtube.groups;

import java.util.Locale;

/** One deterministic group/content policy, including old backups with no per-group content state. */
public final class ContentFilterSelection {
    public static String safe(String value) {
        try { return GroupContentFilter.valueOf(value.toUpperCase(Locale.ROOT)).name().toLowerCase(Locale.ROOT); }
        catch (Exception invalid) { return "all"; }
    }
    public static GroupContentFilter enter(GroupPreferences preferences, String group) {
        String saved = preferences.enabled("remember_content") ? preferences.get(GroupPreferences.CONTENT_PREFIX + group) : null;
        return GroupContentFilter.valueOf(safe(saved == null ? preferences.get("default_content") : saved).toUpperCase(Locale.ROOT));
    }
    private ContentFilterSelection() { }
}
