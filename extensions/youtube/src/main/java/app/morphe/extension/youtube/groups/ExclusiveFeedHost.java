/* SPDX-License-Identifier: GPL-3.0-only
 * Independent Subscription Groups groups.4, 2026-11-01. See LICENSE and NOTICE.
 */
package app.morphe.extension.youtube.groups;

import android.view.View;
import android.widget.FrameLayout;

/** Only these two page-local content roots are swapped. Native chrome stays outside. */
final class ExclusiveFeedHost extends FrameLayout {
    private final View original, groups;
    private boolean groupSelected;
    private int nativeVisibility;
    ExclusiveFeedHost(View original, View groups) {
        super(original.getContext()); this.original = original; this.groups = groups;
        nativeVisibility = original.getVisibility();
        groups.setVisibility(GONE);
        addView(original, new LayoutParams(-1, -1)); addView(groups, new LayoutParams(-1, -1));
    }
    void selectGroup(boolean selected) {
        if (selected && !groupSelected) nativeVisibility = original.getVisibility();
        if (!selected && groupSelected) { groups.setVisibility(GONE); original.setVisibility(nativeVisibility); }
        groupSelected = selected; apply();
    }
    boolean groupSelected() { return groupSelected; }
    private void apply() {
        // Hide first, including during loading: there is never a frame with two feeds.
        if (groupSelected) {
            if (original.getVisibility() != GONE) nativeVisibility = original.getVisibility();
            original.setVisibility(GONE); groups.setVisibility(VISIBLE);
        }
        else groups.setVisibility(GONE); // Native mode owns its own loading/visibility lifecycle.
    }
    @Override protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        apply(); super.onLayout(changed, left, top, right, bottom);
    }
    @Override protected void onAttachedToWindow() { super.onAttachedToWindow(); apply(); }
    @Override protected void onDetachedFromWindow() {
        // A cached native page must not retain our hidden content on another route.
        groups.setVisibility(GONE); if (groupSelected) original.setVisibility(nativeVisibility); super.onDetachedFromWindow();
    }
}
