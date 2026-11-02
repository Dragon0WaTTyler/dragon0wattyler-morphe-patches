/* SPDX-License-Identifier: GPL-3.0-only
 * Independent Subscription Groups groups.5, 2026-11-01. See LICENSE and NOTICE.
 */
package app.morphe.extension.youtube.groups;

import android.content.Context;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.util.function.Consumer;
import static app.morphe.extension.shared.StringRef.str;

/** Same compact chip primitives as the group selector, projecting cached uploads only. */
final class GroupFilterBar extends HorizontalScrollView {
    private final Consumer<GroupContentFilter> listener;
    private GroupContentFilter selected = GroupContentFilter.ALL;
    GroupFilterBar(Context context, Consumer<GroupContentFilter> listener) {
        super(context); this.listener = listener; setHorizontalScrollBarEnabled(false); render();
    }
    void restore(GroupContentFilter value) { if (selected != value) { selected = value; render(); } }
    void select(GroupContentFilter value) { selected = value; render(); listener.accept(value); }
    private void render() {
        int old = getScrollX(); removeAllViews();
        LinearLayout row = new LinearLayout(getContext()); row.setPaddingRelative(GroupUi.dp(getContext(), 12), 0, GroupUi.dp(getContext(), 8), GroupUi.dp(getContext(), 4));
        for (GroupContentFilter filter : GroupContentFilter.values()) {
            TextView chip = GroupUi.chip(getContext(), str("morphe_pockettube_g5_filter_" + filter.name().toLowerCase(java.util.Locale.ROOT)), selected == filter, () -> select(filter));
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-2, GroupUi.dp(getContext(), 48)); params.setMarginEnd(GroupUi.dp(getContext(), 8)); row.addView(chip, params);
        }
        addView(row); scrollTo(old, 0);
    }
}
