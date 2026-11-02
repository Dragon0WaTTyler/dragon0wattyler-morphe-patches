/* SPDX-License-Identifier: GPL-3.0-only
 * Independent Subscription Groups groups.5, 2026-11-01. See LICENSE and NOTICE.
 */
package app.morphe.extension.youtube.groups;

import app.morphe.extension.shared.patches.components.BufferAsciiStrings;
import app.morphe.extension.shared.patches.components.ContextInterface;
import app.morphe.extension.shared.patches.components.Filter;
import app.morphe.extension.shared.patches.components.StringFilterGroup;
import app.morphe.extension.youtube.settings.Settings;

/** Dedicated Subscription component only. Removing its model also removes its reserved layout space. */
@SuppressWarnings("unused")
public final class GroupsComponentsFilter extends Filter {
    public GroupsComponentsFilter() { addPathCallbacks(new StringFilterGroup(Settings.POCKETTUBE_GROUPS, "subscriptions_channel_bar")); }
    static boolean carouselPath(CharSequence path) {
        String value = path.toString();
        for (String component : value.split("\\|")) if (component.equals("subscriptions_channel_bar") || component.startsWith("subscriptions_channel_bar.")) return true;
        return false;
    }
    @Override public boolean isFiltered(ContextInterface context, String identifier, String accessibility,
            CharSequence path, byte[] buffer, BufferAsciiStrings strings, StringFilterGroup matched, FilterContentType type, int index) {
        // This exact Subscription-only component is also prepared off-tab. Never filter generic avatars.
        return Settings.POCKETTUBE_GROUPS.get() && carouselPath(path);
    }
}
