/* SPDX-License-Identifier: GPL-3.0-only
 * Independent Subscription Groups V2, 2026-11-01. See LICENSE and NOTICE.
 */
package app.morphe.extension.youtube.groups;

import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.PopupMenu;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import android.view.ViewTreeObserver;
import app.morphe.extension.youtube.shared.NavigationBar;
import app.morphe.extension.youtube.shared.PlayerType;
import app.morphe.extension.youtube.settings.Settings;
import static app.morphe.extension.shared.StringRef.str;

/** One content host below native chrome; custom and original video lists are mutually exclusive. */
final class SubscriptionsPage extends LinearLayout {
    final WeakReference<Object> owner;
    private final View original;
    private final LinearLayout header;
    private final GroupService service;
    private final GroupService.Listener observer = this::refresh;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final GroupFeedView feed;
    private final ExclusiveFeedHost content;
    private final GroupFilterBar filters;
    private GroupsNativeChrome nativeChrome;
    private GroupChromeBinding chrome;
    private boolean tabActive = true;
    private String browseId, active = "", signature = "", lastDefault = "";
    private boolean pendingEntry = true, enabled, resumed = true, presented;
    private final ViewTreeObserver.OnGlobalLayoutListener visibility = () -> {
        boolean next = presented(); if (next != presented) { presented = next; refresh(); }
    };
    private HorizontalScrollView scroll;
    private final Runnable saveScroll = this::persistScroll;
    private void persistScroll() {
        if (scroll != null && service.ready() && service.library().preferences.enabled("remember_scroll")) {
            String x = Integer.toString(scroll.getScrollX());
            if (!x.equals(service.library().preferences.get("scroll_x"))) service.preference("scroll_x", x);
        }
    }
    SubscriptionsPage(View original, Object fragment, String browseId) {
        super(original.getContext()); this.original = original; owner = new WeakReference<>(fragment); this.browseId = browseId;
        setOrientation(VERTICAL); if (original.getLayoutParams() != null) setLayoutParams(original.getLayoutParams());
        service = GroupService.get(getContext()); header = GroupUi.vertical(getContext());
        feed = new GroupFeedView(getContext(), service);
        content = new ExclusiveFeedHost(original, feed);
        filters = new GroupFilterBar(getContext(), feed::setContentFilter);
        addView(filters, new LayoutParams(-1, -2)); addView(header, new LayoutParams(-1, -2));
        addView(content, new LayoutParams(-1, 0, 1)); filters.setVisibility(GONE);
        header.setVisibility(GONE); feed.setVisibility(GONE); refresh();
    }
    boolean isSubscriptions() { return "FEsubscriptions".equals(browseId); }
    void pageChanged(String id) {
        String next = id == null ? "" : id;
        if (next.equals("FEsubscriptions")) tabActive = true;
        if (!next.equals(browseId) && next.equals("FEsubscriptions")) pendingEntry = true;
        browseId = next; signature = ""; refresh();
    }
    void enter() { tabActive = true; pendingEntry = true; refresh(); }
    void leave() { tabActive = false; pause(); content.selectGroup(false); filters.setVisibility(GONE); if (chrome != null) chrome.setActive(false); if (nativeChrome != null) nativeChrome.setMode(false, false); }
    void chromeReady(View nativeRoot) {
        if (chrome != null) chrome.release();
        if (nativeChrome != null) nativeChrome.release();
        chrome = new GroupChromeBinding(nativeRoot); nativeChrome = new GroupsNativeChrome(nativeRoot, this, original); refresh();
    }
    void activityResumed(boolean value) { resumed = value; if (value) refresh(); else pause(); }
    private boolean presented() { return resumed && isShown() && isAttachedToWindow() && !NavigationBar.isSearchBarActive() && !PlayerType.getCurrent().isMaximizedOrFullscreen(); }
    void pause() { if (feed != null) feed.pause(); main.removeCallbacks(saveScroll); saveScroll.run(); }
    @Override protected void onAttachedToWindow() { super.onAttachedToWindow(); service.observe(observer); getViewTreeObserver().addOnGlobalLayoutListener(visibility); refresh(); }
    @Override protected void onDetachedFromWindow() { service.remove(observer); if (getViewTreeObserver().isAlive()) getViewTreeObserver().removeOnGlobalLayoutListener(visibility); pause(); if (chrome != null) chrome.setActive(false); if (nativeChrome != null) nativeChrome.setMode(false, false); super.onDetachedFromWindow(); }
    @Override public void onVisibilityAggregated(boolean visible) { super.onVisibilityAggregated(visible); if (visible) refresh(); else pause(); }
    void select(String id) {
        GroupLibrary data = service.library(); if (!service.ready() || (!id.isEmpty() && data.find(id) == null)) return;
        active = id; pendingEntry = false; signature = ""; service.preference("last_group", id); refresh();
    }
    void refresh() {
        if (feed == null || service == null) return;
        boolean integration = Settings.POCKETTUBE_GROUPS.get();
        if (chrome != null) chrome.setActive(integration && isSubscriptions() && tabActive && presented());
        if (!integration) {
            if (nativeChrome != null) nativeChrome.setMode(false, false);
            enabled = false; active = ""; signature = ""; header.setVisibility(GONE); filters.setVisibility(GONE); content.selectGroup(false); feed.pause(); return;
        }
        if (!enabled) pendingEntry = true; enabled = true;
        if (!(isSubscriptions() || GroupBackup.isChannelId(browseId))) { if (nativeChrome != null) nativeChrome.setMode(false, false); header.setVisibility(GONE); filters.setVisibility(GONE); content.selectGroup(false); feed.pause(); return; }
        setBackgroundColor(GroupUi.background());
        GroupLibrary data = service.library();
        if (service.ready() && isSubscriptions()) {
            if (pendingEntry || (!lastDefault.equals(data.preferences.get("default")) && !data.preferences.enabled("remember"))) {
                active = GroupSelection.enter(data, true); pendingEntry = false;
            } else active = GroupSelection.reconcile(data, active, true);
            lastDefault = data.preferences.get("default");
        }
        String nextSignature = signature(data);
        if (!nextSignature.equals(signature)) { signature = nextSignature; renderHeader(data); }
        feed.retainGroups(data);
        GroupLibrary.Entry group = data.find(active);
        boolean showFeed = isSubscriptions() && tabActive && service.ready() && group != null && data.preferences.enabled("feed");
        boolean preparing = isSubscriptions() && tabActive && !service.ready() && service.error().isEmpty();
        content.selectGroup(showFeed || preparing); filters.setVisibility(showFeed && !Settings.HIDE_FILTER_BAR_IN_FEED.get() ? VISIBLE : GONE);
        if (nativeChrome != null) nativeChrome.setMode(integration && isSubscriptions() && tabActive && presented(), showFeed || preparing);
        if (preparing) feed.preparing();
        if (showFeed) {
            feed.bind(group, data.preferences, presented()); filters.restore(feed.contentFilter());
        } else feed.pause();
        // Placement is a sibling ordering operation; stock children and their layout remain intact.
        int desiredIndex = 1; // Content filters, then group selector, then exactly one feed.
        if (indexOfChild(header) != desiredIndex) { removeView(header); addView(header, desiredIndex, new LayoutParams(-1, -2)); }
    }
    private String signature(GroupLibrary data) {
        StringBuilder out = new StringBuilder(browseId).append('|').append(active).append('|').append(service.ready()).append('|')
                .append(service.error()).append('|').append(GroupUi.background()).append('|').append(GroupUi.foreground()).append('|');
        for (String key : List.of("style", "spacing", "icons", "counts", "show_all", "show_empty", "order", "selector", "feed")) out.append(data.preferences.get(key)).append('|');
        for (GroupLibrary.Entry group : data.ordered(false)) out.append(group.id).append(group.name).append(group.icon).append(group.pinned).append(group.channels.size()).append('|');
        return out.toString();
    }
    private void renderHeader(GroupLibrary data) {
        int priorScroll = scroll == null ? Integer.parseInt(data.preferences.get("scroll_x")) : scroll.getScrollX();
        header.removeAllViews(); scroll = null; header.setVisibility(VISIBLE); header.setBackgroundColor(GroupUi.background());
        if (GroupBackup.isChannelId(browseId)) {
            header.addView(GroupUi.secondary(getContext(), str("morphe_pockettube_v2_assign"), () -> GroupsManagerDialog.show(GroupUi.activity(getContext()), browseId)));
            return;
        }
        if (!service.ready()) {
            // No empty dashboard while the local file is loading. Recovery stays reachable on failure.
            if (service.error().isEmpty()) { header.setVisibility(GONE); return; }
            header.addView(GroupUi.secondary(getContext(), str("morphe_pockettube_ux_groups_unavailable"), () -> GroupsManagerDialog.show(GroupUi.activity(getContext()), "")));
            return;
        }
        List<String> ids = new ArrayList<>(), names = new ArrayList<>();
        if (data.preferences.enabled("show_all") || active.isEmpty()) { ids.add(""); names.add(str("morphe_pockettube_g5_all_groups")); }
        for (GroupLibrary.Entry group : data.ordered(true)) { ids.add(group.id); names.add(chip(group, data.preferences)); }
        if (!active.isEmpty() && !ids.contains(active) && data.find(active) != null) { ids.add(active); names.add(chip(data.find(active), data.preferences)); }
        LinearLayout row = new LinearLayout(getContext()); row.setGravity(Gravity.CENTER_VERTICAL);
        if (!data.preferences.enabled("selector")) {
            if (!active.isEmpty()) row.addView(GroupUi.secondary(getContext(), str("morphe_pockettube_v2_original"), () -> select("")), new LayoutParams(0, GroupUi.dp(getContext(), 48), 1));
            else { header.setVisibility(GONE); return; }
        } else if (data.preferences.get("style").equals("dropdown")) {
            Spinner spinner = new Spinner(getContext()); spinner.setAdapter(new ArrayAdapter<>(getContext(), android.R.layout.simple_spinner_dropdown_item, names));
            spinner.setSelection(Math.max(0, ids.indexOf(active))); spinner.setContentDescription(str("morphe_pockettube_v2_selector_label"));
            spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                public void onNothingSelected(AdapterView<?> parent) { }
                public void onItemSelected(AdapterView<?> parent, View view, int position, long id) { if (!ids.get(position).equals(active)) select(ids.get(position)); }
            }); row.addView(spinner, new LayoutParams(0, -2, 1));
        } else {
            scroll = new HorizontalScrollView(getContext()); scroll.setHorizontalScrollBarEnabled(false);
            LinearLayout chips = new LinearLayout(getContext()); int gap = data.preferences.get("spacing").equals("compact") ? 4 : 8;
            chips.setPaddingRelative(GroupUi.dp(getContext(), 12), 0, GroupUi.dp(getContext(), 4), 0);
            List<Integer> visible = new ArrayList<>(); for (int i = 0; i < Math.min(24, ids.size()); i++) visible.add(i);
            int selectedIndex = ids.indexOf(active); if (selectedIndex >= 24) visible.add(selectedIndex);
            for (int index : visible) {
                String id = ids.get(index); TextView button = GroupUi.chip(getContext(), names.get(index), id.equals(active), () -> select(id));
                button.setMaxWidth(Integer.MAX_VALUE);
                LayoutParams params = new LayoutParams(-2, GroupUi.dp(getContext(), 48)); params.setMarginEnd(GroupUi.dp(getContext(), gap)); chips.addView(button, params);
            }
            if (ids.size() > 24) chips.addView(GroupUi.secondary(getContext(), str("morphe_pockettube_v2_more"), () -> GroupsManagerDialog.pick(GroupUi.activity(getContext()))), new LayoutParams(-2, GroupUi.dp(getContext(), 48)));
            scroll.addView(chips); row.addView(scroll, new LayoutParams(0, -2, 1));
            HorizontalScrollView current = scroll; scroll.post(() -> { if (current == scroll) current.scrollTo(priorScroll, 0); });
            scroll.setOnTouchListener((view, event) -> { if (event.getActionMasked() == MotionEvent.ACTION_UP || event.getActionMasked() == MotionEvent.ACTION_CANCEL) { main.removeCallbacks(saveScroll); main.postDelayed(saveScroll, 300); } return false; });
        }
        View overflow = GroupUi.overflow(getContext(), str("morphe_pockettube_ux_group_options"));
        overflow.setOnClickListener(this::showOptions);
        row.addView(overflow, new LayoutParams(GroupUi.dp(getContext(), 48), GroupUi.dp(getContext(), 48)));
        header.addView(row);
    }
    private void showOptions(View anchor) {
        PopupMenu menu = new PopupMenu(getContext(), anchor);
        menu.getMenu().add(str("morphe_pockettube_ux_manage_groups"))
                .setOnMenuItemClickListener(item -> { GroupsManagerDialog.show(GroupUi.activity(getContext()), ""); return true; });
        if (feed.getVisibility() == VISIBLE) feed.addMenuItems(menu.getMenu());
        else menu.getMenu().add(str("morphe_pockettube_ux_details"))
                .setOnMenuItemClickListener(item -> { GroupOptions.aboutFeed(getContext()); return true; });
        menu.show();
    }
    static String chip(GroupLibrary.Entry group, GroupPreferences prefs) {
        return (prefs.enabled("icons") && !group.icon.isEmpty() ? group.icon + " " : "") + group.name;
    }
}
