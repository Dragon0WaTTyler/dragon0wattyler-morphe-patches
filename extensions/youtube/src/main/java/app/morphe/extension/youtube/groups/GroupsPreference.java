/* SPDX-License-Identifier: GPL-3.0-only
 * Independent Subscription Groups additions, 2026-11-01. See LICENSE and NOTICE.
 */
package app.morphe.extension.youtube.groups;

import android.content.Context;
import android.preference.Preference;
import android.util.AttributeSet;

import app.morphe.extension.shared.settings.preference.AbstractPreferenceFragment;
import app.morphe.extension.youtube.settings.Settings;

/** XML entry point; the existing Morphe settings Activity hosts the entire feature. */
@SuppressWarnings({"unused", "deprecation"})
public final class GroupsPreference extends Preference {
    public GroupsPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
        setPersistent(false);
    }

    @Override
    protected void onClick() {
        if (!Settings.POCKETTUBE_GROUPS.get()) return;
        AbstractPreferenceFragment settings = AbstractPreferenceFragment.instance.get();
        if (settings == null || !settings.isAdded()) return;
        GroupsManagerDialog.show(settings.getActivity(), "");
    }
}
