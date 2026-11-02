/* SPDX-License-Identifier: GPL-3.0-only
 * Independent Subscription Groups V2, 2026-11-01. See LICENSE and NOTICE.
 */
package app.morphe.extension.youtube.groups;

import android.content.Context;
import android.preference.Preference;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Switch;
import app.morphe.extension.youtube.settings.Settings;

/** Uses Morphe's custom Preference infrastructure; dynamic values live in the atomic group library. */
@SuppressWarnings({"unused", "deprecation"})
public final class GroupOptionPreference extends Preference {
    private final GroupService service;
    private final GroupService.Listener observer = this::refresh;
    public GroupOptionPreference(Context context, AttributeSet attrs) {
        super(context, attrs); setPersistent(false); service = GroupService.get(context); service.observe(observer);
    }
    private String option() { return getKey().substring("morphe_pockettube_option_".length()); }
    private void refresh() {
        // The framework dependency handles the master switch; do not latch our own enabled bit off.
        setEnabled(service.ready());
        if (service.ready()) setSummary(GroupOptions.summary(service.library(), option()));
        notifyChanged();
    }
    @Override protected void onAttachedToActivity() { super.onAttachedToActivity(); refresh(); }
    @Override protected void onClick() { if (Settings.POCKETTUBE_GROUPS.get()) GroupOptions.choose(getContext(), service, option()); }
    @Override protected void onBindView(View view) {
        super.onBindView(view);
        ViewGroup frame = view.findViewById(android.R.id.widget_frame);
        if (frame != null) {
            frame.removeAllViews(); boolean toggle = GroupPreferences.TOGGLES.contains(option());
            frame.setVisibility(toggle ? View.VISIBLE : View.GONE);
            if (toggle) {
                Switch widget = new Switch(getContext()); widget.setChecked(service.library().preferences.enabled(option()));
                widget.setEnabled(isEnabled()); widget.setClickable(false); widget.setFocusable(false);
                widget.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO); frame.addView(widget);
            }
        }
    }
}
