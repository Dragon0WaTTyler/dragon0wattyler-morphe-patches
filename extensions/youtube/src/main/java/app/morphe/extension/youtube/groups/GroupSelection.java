/* SPDX-License-Identifier: GPL-3.0-only
 * Independent Subscription Groups V2, 2026-11-01. See LICENSE and NOTICE.
 */
package app.morphe.extension.youtube.groups;

/** Pure tab-entry/reconciliation state machine. Empty ID always denotes the original feed. */
public final class GroupSelection {
    public static String enter(GroupLibrary library, boolean integration) {
        if (!integration) return "";
        if (library.preferences.enabled("remember")) {
            String last = library.preferences.get("last_group");
            if (last.isEmpty() || library.find(last) != null) return last;
        }
        return fallback(library);
    }
    public static String fallback(GroupLibrary library) {
        String configured = library.preferences.get("default");
        return library.find(configured) != null ? configured : "";
    }
    public static String reconcile(GroupLibrary library, String active, boolean integration) {
        if (!integration) return "";
        return active.isEmpty() || library.find(active) != null ? active : fallback(library);
    }
    private GroupSelection() {}
}
