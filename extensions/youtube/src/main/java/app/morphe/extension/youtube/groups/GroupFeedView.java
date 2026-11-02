/* SPDX-License-Identifier: GPL-3.0-only
 * Independent Subscription Groups UX refinement, 2026-11-01. See LICENSE and NOTICE.
 */
package app.morphe.extension.youtube.groups;

import android.app.AlertDialog;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.text.format.DateUtils;
import android.view.Gravity;
import android.view.Menu;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.PopupMenu;
import android.widget.ProgressBar;
import android.widget.TextView;
import java.text.NumberFormat;
import java.time.ZoneId;
import java.util.List;
import static app.morphe.extension.shared.StringRef.str;

/** Borderless Android video cards. Feed scope and diagnostics live in the row's overflow menu. */
final class GroupFeedView extends LinearLayout {
    private final Handler main = new Handler(Looper.getMainLooper());
    private final GroupService service;
    private final WatchedState watched;
    private final boolean publicThumbnailMetadata;
    private final Runnable watchedChanged = this::queueRender;
    private final RecentFeedSession session;
    private final GroupMetadataSource metadata;
    private final java.util.concurrent.ExecutorService metadataWorkers;
    private RecentFeedSession metadataSession;
    private RecentFeedSession.State metadataState;
    private GroupContentFilter contentFilter = GroupContentFilter.ALL;
    private String metadataTab = "", contentPolicy = "";
    private final java.util.Map<String, GroupContentFilter> localFilters = new java.util.HashMap<>();
    private final TextView empty;
    private final ProgressBar progress;
    private final ListView list;
    private final Videos adapter = new Videos();
    private RecentFeedSession.State state;
    private GroupLibrary.Entry group;
    private GroupPreferences preferences;
    private String sort = "newest", range = "any", lastDefaults = "";
    private boolean running, needsStart = true, renderQueued;
    private final Runnable update = () -> { renderQueued = false; render(); };

    GroupFeedView(Context context, GroupService service) {
        this(context, service, RecentFeedSource.get(context), GroupMetadataSource.get(context), RecentFeedSource.WORKERS, GroupMetadataSource.WORKERS, WatchedService.get(context), true);
    }
    GroupFeedView(Context context, GroupService service, FeedSource uploads, GroupMetadataSource metadata,
                  java.util.concurrent.ExecutorService uploadWorkers, java.util.concurrent.ExecutorService metadataWorkers) {
        this(context, service, uploads, metadata, uploadWorkers, metadataWorkers,
                service == null ? WatchedState.EMPTY : WatchedService.get(context), false);
    }
    GroupFeedView(Context context, GroupService service, FeedSource uploads, GroupMetadataSource metadata,
                  java.util.concurrent.ExecutorService uploadWorkers, java.util.concurrent.ExecutorService metadataWorkers, WatchedState watched) {
        this(context, service, uploads, metadata, uploadWorkers, metadataWorkers, watched, false);
    }
    GroupFeedView(Context context, GroupService service, FeedSource uploads, GroupMetadataSource metadata,
                  java.util.concurrent.ExecutorService uploadWorkers, java.util.concurrent.ExecutorService metadataWorkers, WatchedState watched, boolean publicThumbnailMetadata) {
        super(context); this.service = service; this.watched = watched; this.publicThumbnailMetadata = publicThumbnailMetadata; setOrientation(VERTICAL); setBackgroundColor(GroupUi.background());
        this.metadataWorkers = metadataWorkers;
        progress = new ProgressBar(context, null, android.R.attr.progressBarStyleHorizontal);
        progress.setIndeterminate(true); progress.setIndeterminateTintList(ColorStateList.valueOf(GroupUi.muted()));
        progress.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        addView(progress, new LayoutParams(-1, GroupUi.dp(context, 2)));
        FrameLayout body = new FrameLayout(context); addView(body, new LayoutParams(-1, 0, 1));
        list = new ListView(context); list.setDivider(null); list.setDividerHeight(0);
        list.setClipToPadding(false); list.setVerticalScrollBarEnabled(false); list.setAdapter(adapter);
        body.addView(list, new FrameLayout.LayoutParams(-1, -1));
        empty = GroupUi.text(context, "", 14); empty.setGravity(Gravity.CENTER);
        empty.setPadding(GroupUi.dp(context, 24), GroupUi.dp(context, 16), GroupUi.dp(context, 24), GroupUi.dp(context, 16));
        empty.setAccessibilityLiveRegion(ACCESSIBILITY_LIVE_REGION_POLITE);
        body.addView(empty, new FrameLayout.LayoutParams(-1, -1)); list.setEmptyView(empty);
        list.setOnItemClickListener((parent, view, position, id) -> {
            if (position >= 0 && position < adapter.bound.size()) GroupUi.video(context, adapter.bound.get(position).id);
        });
        this.metadata = metadata;
        session = new RecentFeedSession(uploads, uploadWorkers, main::post, value -> {
            state = value; queueRender();
        });
    }

    void bind(GroupLibrary.Entry next, GroupPreferences prefs, boolean visible) {
        preferences = prefs;
        boolean changed = group == null || !group.id.equals(next.id) || !group.channels.equals(next.channels);
        if (changed) { needsStart = true; list.setSelection(0); metadataTab = ""; }
        boolean newGroup = group == null || !group.id.equals(next.id);
        group = next;
        String policy = prefs.get("remember_content") + ":" + prefs.get("default_content");
        if (newGroup || !policy.equals(contentPolicy)) {
            contentFilter = prefs.enabled("remember_content") && localFilters.containsKey(next.id)
                    ? localFilters.get(next.id) : ContentFilterSelection.enter(prefs, next.id);
            contentPolicy = policy;
        }
        String defaults = prefs.get("sort") + prefs.get("range") + prefs.get("remember_filters")
                + (prefs.enabled("remember_filters") ? prefs.get("last_sort") + prefs.get("last_range") : "");
        if (changed || !defaults.equals(lastDefaults)) {
            sort = prefs.get(prefs.enabled("remember_filters") ? "last_sort" : "sort");
            range = prefs.get(prefs.enabled("remember_filters") ? "last_range" : "range");
            if (!GroupPreferences.CHOICES.get("sort").contains(sort)) sort = prefs.get("sort");
            if (!GroupPreferences.CHOICES.get("range").contains(range)) range = prefs.get("range");
            lastDefaults = defaults;
        }
        if (!visible) { pause(); return; }
        if (needsStart) sessionStart(false);
        else if (!running) { running = true; session.resume();
            if (!metadataTab.equals(contentFilter.tab())) startMetadata(false); else if (metadataSession != null) metadataSession.resume();
        } else if (!metadataTab.equals(contentFilter.tab())) startMetadata(false);
        render();
    }
    private void sessionStart(boolean refresh) {
        if (group == null) return;
        running = true; needsStart = false;
        if (!refresh) { state = null; adapter.bound = List.of(); list.setSelection(0); }
        session.start(group.channels, refresh); startMetadata(refresh); render();
    }
    private void queueRender() { if (!renderQueued) { renderQueued = true; main.postDelayed(update, 80); } }
    void setContentFilter(GroupContentFilter value) {
        if (group != null && preferences != null && preferences.enabled("remember_content")) {
            localFilters.put(group.id, value); if (service != null) service.contentFilter(group.id, value);
        }
        if (contentFilter == value) return;
        contentFilter = value; list.setSelection(0);
        if (running && group != null && !metadataTab.equals(value.tab())) startMetadata(false);
        render();
    }
    GroupContentFilter contentFilter() { return contentFilter; }
    private void startMetadata(boolean refresh) {
        if (group == null) return;
        if (metadataSession != null) metadataSession.pause();
        metadataTab = contentFilter.tab(); metadataState = null;
        metadataSession = new RecentFeedSession(metadata.forTab(metadataTab), metadataWorkers, main::post,
                value -> { metadataState = value; queueRender(); });
        metadataSession.start(group.channels, refresh);
    }
    void pause() { GroupImages.cancelThumbnails(list); if (running) { running = false; session.pause(); } if (metadataSession != null) metadataSession.pause(); }
    void preparing() {
        progress.setVisibility(VISIBLE); empty.setText(str("morphe_pockettube_ux_loading"));
    }

    void addMenuItems(Menu menu) {
        menu.add(str("morphe_pockettube_ux_refresh")).setOnMenuItemClickListener(item -> { sessionStart(true); return true; });
        menu.add(str("morphe_pockettube_ux_sort")).setOnMenuItemClickListener(item -> { chooseFilter("sort"); return true; });
        menu.add(str("morphe_pockettube_ux_date_range")).setOnMenuItemClickListener(item -> { chooseFilter("range"); return true; });
        if (state != null && state.failedChannels > 0)
            menu.add(str("morphe_pockettube_ux_retry")).setEnabled(!state.loading)
                    .setOnMenuItemClickListener(item -> { session.retry(); return true; });
        menu.add(str("morphe_pockettube_ux_details")).setOnMenuItemClickListener(item -> { details(); return true; });
    }
    private void details() {
        if (group == null) return;
        String message = str("morphe_pockettube_v2_feed_explanation") + "\n\n" + str("morphe_pockettube_g5_metadata_scope");
        if (metadataState != null) message += "\n\n" + str("morphe_pockettube_g5_metadata_coverage", metadataTab, metadataState.loadedChannels, metadataState.totalChannels, metadataState.pendingChannels, metadataState.failedChannels);
        if (state != null) {
            String freshness = state.oldestWindowAt == 0 ? str("morphe_pockettube_g4_no_cache")
                    : DateUtils.getRelativeTimeSpanString(state.oldestWindowAt, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS).toString();
            message = str("morphe_pockettube_g4_coverage", state.totalChannels, state.loadedChannels, state.pendingChannels,
                    state.failedChannels, state.uninspectedChannels, state.retryingChannels, state.videos.size(), state.cachedChannels, state.staleChannels, freshness)
                    + "\n\n" + message;
            int shown = 0;
            StringBuilder failures = new StringBuilder();
            for (java.util.Map.Entry<String, String> entry : state.channelFailures.entrySet()) {
                if (shown++ >= 20) break;
                failures.append('\n').append(RecentFeedSource.get(getContext()).channelName(entry.getKey()))
                        .append(" · ").append(entry.getKey()).append(" · ").append(entry.getValue());
            }
            if (!state.channelFailures.isEmpty()) message += "\n" + failures;
            if (state.channelFailures.size() > 20) message += "\n" + str("morphe_pockettube_g4_more_failures", state.channelFailures.size() - 20);
        }
        new AlertDialog.Builder(getContext()).setTitle(group.name).setMessage(message).setPositiveButton(android.R.string.ok, null).show();
    }
    private void chooseFilter(String key) {
        List<String> choices = GroupPreferences.CHOICES.get(key); String[] labels = new String[choices.size()];
        for (int i = 0; i < labels.length; i++) labels[i] = str("morphe_pockettube_v2_choice_" + choices.get(i));
        new AlertDialog.Builder(getContext()).setTitle(str(key.equals("sort") ? "morphe_pockettube_ux_sort" : "morphe_pockettube_ux_date_range"))
                .setSingleChoiceItems(labels, choices.indexOf(key.equals("sort") ? sort : range), (dialog, position) -> {
                    String value = choices.get(position); if (key.equals("sort")) sort = value; else range = value;
                    if (preferences.enabled("remember_filters")) service.preference("last_" + key, value);
                    adapter.bound = List.of(); list.setSelection(0); render(); dialog.dismiss();
                }).setNegativeButton(android.R.string.cancel, null).show();
    }
    private void render() {
        if (group == null) return;
        setBackgroundColor(GroupUi.background()); empty.setTextColor(GroupUi.muted());
        progress.setIndeterminateTintList(ColorStateList.valueOf(GroupUi.muted()));
        progress.setVisibility(running && (state == null || state.loading || (metadataState != null && metadataState.loading)) ? VISIBLE : INVISIBLE);
        List<GroupVideo> videos = state == null ? List.of()
                : contentFilter.apply(metadata.enrich(RecentFeedSession.visible(state, sort, range, System.currentTimeMillis(), ZoneId.systemDefault())), System.currentTimeMillis(), ZoneId.systemDefault());
        if (preferences != null && preferences.enabled("hide_watched")) {
            if (!watched.ready()) videos = List.of();
            else { java.util.ArrayList<GroupVideo> visible = new java.util.ArrayList<>();
                for (GroupVideo video : videos) if (!watched.contains(video.id)) visible.add(video); videos = visible;
            }
        }
        String message = (preferences != null && preferences.enabled("hide_watched") && !watched.ready()) || state == null || state.loading || (contentFilter != GroupContentFilter.ALL && metadataState != null && metadataState.loading) ? "morphe_pockettube_ux_loading"
                : state.totalChannels == 0 ? "morphe_pockettube_ux_empty_group"
                : state.failedChannels > 0 && state.videos.isEmpty() ? "morphe_pockettube_ux_load_error" : "morphe_pockettube_ux_empty_feed";
        boolean metadataUnavailable = contentFilter != GroupContentFilter.ALL && contentFilter != GroupContentFilter.TODAY
                && metadataState != null && !metadataState.loading && metadataState.failedChannels > 0 && videos.isEmpty();
        empty.setText(str(metadataUnavailable ? "morphe_pockettube_g5_filter_unavailable" : contentFilter == GroupContentFilter.PODCASTS && videos.isEmpty() && state != null && !state.loading && metadataState != null && !metadataState.loading ? "morphe_pockettube_g5_no_podcasts" : message));
        boolean canRetry = state != null && !state.loading && state.failedChannels > 0 && state.videos.isEmpty();
        empty.setOnClickListener(metadataUnavailable ? view -> startMetadata(true) : canRetry ? view -> session.retry() : null);
        empty.setClickable(canRetry || metadataUnavailable); empty.setFocusable(canRetry || metadataUnavailable);
        int position = list.getFirstVisiblePosition(); View first = list.getChildAt(0); int top = first == null ? 0 : first.getTop();
        boolean atTop = position == 0 && top >= 0;
        String anchor = adapter.bound.size() > position && position >= 0 ? adapter.bound.get(position).id : "";
        adapter.bound = videos; adapter.notifyDataSetChanged();
        if (atTop) list.setSelection(0);
        else for (int i = 0; i < videos.size(); i++) if (videos.get(i).id.equals(anchor)) { list.setSelectionFromTop(i, top); break; }
    }
    void retainGroups(GroupLibrary library) { localFilters.keySet().removeIf(id -> library.find(id) == null); }
    @Override protected void onAttachedToWindow() { super.onAttachedToWindow(); watched.observe(watchedChanged); queueRender(); }
    @Override protected void onDetachedFromWindow() { watched.remove(watchedChanged); pause(); main.removeCallbacks(update); renderQueued = false; super.onDetachedFromWindow(); }

    private final class Videos extends BaseAdapter {
        List<GroupVideo> bound = List.of();
        public int getCount() { return bound.size(); }
        public Object getItem(int position) { return bound.get(position); }
        public long getItemId(int position) { return position; }
        public View getView(int position, View recycled, ViewGroup parent) {
            VideoCard card;
            if (recycled == null) { card = new VideoCard(); recycled = card.root; recycled.setTag(card); }
            else card = (VideoCard) recycled.getTag();
            GroupVideo video = bound.get(position); card.title.setText(video.title);
            String views = video.views < 0 ? "" : str("morphe_pockettube_ux_views", NumberFormat.getIntegerInstance().format(video.views)) + " · ";
            card.meta.setText(video.channelName + " · " + views
                    + DateUtils.getRelativeTimeSpanString(video.published, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS));
            card.avatar.setText(GroupUi.initials(video.channelName));
            GroupImages.bind(card.avatarImage, video.avatar, true);
            String duration = video.metadata.badge();
            card.duration.setText(duration); card.duration.setVisibility(duration.isEmpty() ? GONE : VISIBLE);
            card.title.setTextColor(GroupUi.foreground()); card.meta.setTextColor(GroupUi.muted());
            GradientDrawable circle = new GradientDrawable(); circle.setShape(GradientDrawable.OVAL); circle.setColor(GroupUi.control());
            card.avatar.setBackground(circle); card.avatar.setTextColor(GroupUi.foreground());
            card.root.setBackgroundColor(GroupUi.background()); card.labels.setBackgroundColor(GroupUi.background());
            GroupImages.bindThumbnail(card.image, video, publicThumbnailMetadata);
            card.more.setOnClickListener(anchor -> {
                GroupVideoActions.show(getContext(), anchor, video, watched);
            });
            card.more.setOnLongClickListener(anchor -> { GroupVideoActions.showLocal(getContext(), anchor, video, watched); return true; });
            return recycled;
        }
    }
    private final class VideoCard {
        final LinearLayout root = new LinearLayout(getContext());
        final ImageView image = new ImageView(getContext()) {
            @Override protected void onMeasure(int widthSpec, int heightSpec) {
                int width = MeasureSpec.getSize(widthSpec);
                super.onMeasure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
                        MeasureSpec.makeMeasureSpec(Math.max(1, Math.round(width * 9f / 16f)), MeasureSpec.EXACTLY));
            }
        };
        final TextView title = GroupUi.text(getContext(), "", 16), meta = GroupUi.text(getContext(), "", 12);
        final TextView avatar = GroupUi.text(getContext(), "", 16);
        final ImageView avatarImage = new ImageView(getContext());
        final TextView duration = GroupUi.text(getContext(), "", 12);
        final LinearLayout labels = GroupUi.vertical(getContext());
        final View more = GroupUi.overflow(getContext(), str("morphe_pockettube_g61_video_options"));
        VideoCard() {
            root.setOrientation(VERTICAL); root.setPadding(0, 0, 0, GroupUi.dp(getContext(), 20));
            image.setScaleType(ImageView.ScaleType.CENTER_CROP); image.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
            FrameLayout thumbnail = new FrameLayout(getContext());
            thumbnail.addView(image, new FrameLayout.LayoutParams(-1, -2));
            duration.setTextColor(android.graphics.Color.WHITE); duration.setTypeface(Typeface.DEFAULT_BOLD);
            duration.setIncludeFontPadding(false); duration.setGravity(Gravity.CENTER);
            duration.setPadding(GroupUi.dp(getContext(), 4), GroupUi.dp(getContext(), 2), GroupUi.dp(getContext(), 4), GroupUi.dp(getContext(), 2));
            GradientDrawable badge = new GradientDrawable(); badge.setColor(0xCC000000); badge.setCornerRadius(GroupUi.dp(getContext(), 4)); duration.setBackground(badge);
            duration.setClickable(false); duration.setFocusable(false); duration.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
            FrameLayout.LayoutParams badgeParams = new FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM | Gravity.RIGHT);
            badgeParams.setMargins(GroupUi.dp(getContext(), 8), GroupUi.dp(getContext(), 8), GroupUi.dp(getContext(), 8), GroupUi.dp(getContext(), 8));
            thumbnail.addView(duration, badgeParams); root.addView(thumbnail, new LayoutParams(-1, -2));
            LinearLayout metadata = new LinearLayout(getContext()); metadata.setGravity(Gravity.TOP);
            metadata.setPaddingRelative(GroupUi.dp(getContext(), 12), GroupUi.dp(getContext(), 10), GroupUi.dp(getContext(), 2), 0);
            avatar.setGravity(Gravity.CENTER); avatar.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
            FrameLayout avatarHost = new FrameLayout(getContext());
            avatarHost.addView(avatar, new FrameLayout.LayoutParams(-1, -1));
            avatarImage.setScaleType(ImageView.ScaleType.CENTER_CROP); avatarImage.setClipToOutline(true);
            GradientDrawable avatarOutline = new GradientDrawable(); avatarOutline.setShape(GradientDrawable.OVAL); avatarOutline.setColor(android.graphics.Color.TRANSPARENT);
            avatarImage.setBackground(avatarOutline); avatarImage.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
            avatarHost.addView(avatarImage, new FrameLayout.LayoutParams(-1, -1));
            metadata.addView(avatarHost, new LayoutParams(GroupUi.dp(getContext(), 36), GroupUi.dp(getContext(), 36)));
            labels.setPaddingRelative(GroupUi.dp(getContext(), 12), 0, 0, 0);
            title.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL)); title.setMaxLines(2); title.setEllipsize(TextUtils.TruncateAt.END);
            title.setIncludeFontPadding(false); meta.setMaxLines(2); meta.setEllipsize(TextUtils.TruncateAt.END);
            meta.setPadding(0, GroupUi.dp(getContext(), 4), 0, 0); labels.addView(title); labels.addView(meta);
            metadata.addView(labels, new LayoutParams(0, -2, 1)); more.setFocusable(false);
            metadata.addView(more, new LayoutParams(GroupUi.dp(getContext(), 48), GroupUi.dp(getContext(), 48)));
            root.addView(metadata, new LayoutParams(-1, -2));
        }
    }
}
