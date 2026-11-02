/* SPDX-License-Identifier: GPL-3.0-only
 * Independent Subscription Groups groups.5, 2026-11-02. See LICENSE and NOTICE.
 */
package app.morphe.extension.youtube.groups;

import android.app.Activity;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.widget.BaseAdapter;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import java.lang.reflect.Field;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.TimeUnit;
import static org.junit.Assert.*;

/** Production Android card/filter view and both real dispatchers, using deterministic local transports. */
@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE, sdk = {29, 35})
public class GroupFeedIntegrationTest {
    private ActivityController<Activity> controller;
    private Activity activity;
    private GroupFeedView feed;
    private ListView list;
    private final Workers uploadsWorkers = new Workers(), metadataWorkers = new Workers();
    private final Map<String, Integer> calls = new HashMap<>();
    private int metadataCalls;
    private final Set<String> watchedIds = new HashSet<>();
    private final List<Runnable> watchedObservers = new ArrayList<>();
    private final WatchedState localWatched = new WatchedState() {
        public boolean ready() { return true; }
        public boolean contains(String id) { return watchedIds.contains(id); }
        public void mark(String id, boolean value) { if (value) watchedIds.add(id); else watchedIds.remove(id); for (Runnable r : new ArrayList<>(watchedObservers)) r.run(); }
        public void clear() { watchedIds.clear(); for (Runnable r : new ArrayList<>(watchedObservers)) r.run(); }
        public void observe(Runnable r) { watchedObservers.add(r); }
        public void remove(Runnable r) { watchedObservers.remove(r); }
    };
    private final Map<String, FeedSource.Window> cache = new HashMap<>();
    private static final class Workers extends AbstractExecutorService {
        final ArrayDeque<Runnable> queue = new ArrayDeque<>(); boolean stopped;
        public void execute(Runnable task) { queue.add(task); }
        public void shutdown() { stopped = true; }
        public List<Runnable> shutdownNow() { stopped = true; queue.clear(); return List.of(); }
        public boolean isShutdown() { return stopped; }
        public boolean isTerminated() { return stopped; }
        public boolean awaitTermination(long timeout, TimeUnit unit) { return stopped; }
        void finish() { int guard = 0; while (!queue.isEmpty()) { assertTrue(++guard < 5000); queue.remove().run(); } }
    }
    private static String channel(int n) { return "UC" + String.format(Locale.ROOT, "%022d", n); }
    private static String video(int n) { return String.format(Locale.ROOT, "%011d", n); }
    @Before public void setup() throws Exception {
        controller = Robolectric.buildActivity(Activity.class).setup(); activity = controller.get();
        android.content.pm.PackageInfo info = activity.getPackageManager().getPackageInfo(activity.getPackageName(), 0);
        info.versionName = "21.30.216"; Shadows.shadowOf(activity.getPackageManager()).installPackage(info);
        app.morphe.extension.shared.Utils.setContext(activity);
        FeedSource uploads = new FeedSource() {
            public Window cached(String id) { return cache.get(id); }
            public Window memory(String id) { return cache.get(id); }
            public List<GroupVideo> recent(String id, boolean refresh) {
                calls.merge(id, 1, Integer::sum); int n = Integer.parseInt(id.substring(2));
                GroupVideo item = new GroupVideo(video(n), id, "Video " + n, "Channel " + n, "", System.currentTimeMillis() - (n == 1 ? 3L * 86400000 : 1000), -1);
                List<GroupVideo> result = List.of(item); cache.put(id, new Window(result, System.currentTimeMillis())); return result;
            }
        };
        java.io.File folder = new java.io.File(activity.getCacheDir(), "feed-integration-" + UUID.randomUUID());
        GroupMetadataSource metadata = new GroupMetadataSource(folder, (id, tab) -> {
            metadataCalls++; int n = Integer.parseInt(id.substring(2)); boolean shorts = n % 3 == 0;
            Map<String, VideoMetadata> videos = new HashMap<>();
            if (tab.equals("shorts") && shorts) videos.put(video(n), new VideoMetadata(-1, VideoMetadata.Type.SHORT, VideoMetadata.Live.NONE, false));
            if (tab.equals("videos") && !shorts) videos.put(video(n), new VideoMetadata(272, VideoMetadata.Type.VIDEO, VideoMetadata.Live.NONE, false));
            return new ChannelPageMetadata(id, "", videos, Map.of("videos", "v", "shorts", "s"), System.currentTimeMillis());
        });
        feed = new GroupFeedView(activity, null, uploads, metadata, uploadsWorkers, metadataWorkers, localWatched);
        list = (ListView) field(feed, "list"); activity.setContentView(feed); layout();
    }
    @After public void cleanup() { feed.pause(); controller.pause().stop().destroy(); }
    private void layout() { feed.measure(View.MeasureSpec.makeMeasureSpec(500, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(900, View.MeasureSpec.EXACTLY)); feed.layout(0, 0, 500, 900); }
    private static Object field(Object value, String name) throws Exception { Field field = value.getClass().getDeclaredField(name); field.setAccessible(true); return field.get(value); }
    private GroupLibrary.Entry group(int first, int count) {
        List<String> channels = new ArrayList<>(); for (int i = first; i < first + count; i++) channels.add(channel(i));
        return new GroupLibrary.Entry(UUID.randomUUID().toString(), "Books", "", false, channels);
    }
    private void settle() {
        uploadsWorkers.finish(); metadataWorkers.finish(); Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(100)); layout();
    }
    @Test public void selectingOnceRendersAll155AndChangingActualChipsDoesNotReloadUploads() {
        feed.bind(group(0, 155), new GroupPreferences(), true);
        // First available upload is rendered before the remainder finishes.
        uploadsWorkers.queue.remove().run(); Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(100));
        assertTrue(list.getAdapter().getCount() > 0); assertFalse(uploadsWorkers.queue.isEmpty());
        View firstCard = list.getAdapter().getView(0, null, list); list.performItemClick(firstCard, 0, 0);
        assertEquals(video(0), Shadows.shadowOf(activity).getNextStartedActivity().getData().getQueryParameter("v"));
        settle(); assertEquals(155, calls.size()); assertEquals(155, list.getAdapter().getCount()); assertEquals(155, metadataCalls);
        GroupFilterBar bar = new GroupFilterBar(activity, feed::setContentFilter);
        LinearLayout row = (LinearLayout) bar.getChildAt(0); row.getChildAt(GroupContentFilter.VIDEOS.ordinal()).performClick();
        assertEquals(103, list.getAdapter().getCount()); assertEquals(GroupContentFilter.VIDEOS, feed.contentFilter());
        row = (LinearLayout) bar.getChildAt(0); assertTrue(row.getChildAt(GroupContentFilter.VIDEOS.ordinal()).isSelected());
        row.getChildAt(GroupContentFilter.SHORTS.ordinal()).performClick(); settle(); assertEquals(52, list.getAdapter().getCount());
        int afterShorts = metadataCalls;
        bar.select(GroupContentFilter.ALL); settle(); assertEquals(155, list.getAdapter().getCount()); assertEquals(afterShorts, metadataCalls);
        assertEquals(155, calls.values().stream().mapToInt(Integer::intValue).sum());
        Set<String> ids = new HashSet<>(); long previous = Long.MAX_VALUE;
        for (int i = 0; i < list.getAdapter().getCount(); i++) { GroupVideo video = (GroupVideo) list.getAdapter().getItem(i); assertTrue(ids.add(video.id)); assertTrue(video.published <= previous); previous = video.published; }
    }
    @Test public void todayAndRapidGroupFilterSwitchesKeepOnlyTheCurrentGroup() {
        feed.bind(group(0, 155), new GroupPreferences(), true); feed.setContentFilter(GroupContentFilter.SHORTS);
        feed.bind(group(200, 3), new GroupPreferences().with("default_content", "shorts"), true); settle();
        assertEquals(1, list.getAdapter().getCount());
        for (int i = 0; i < list.getAdapter().getCount(); i++) assertEquals(channel(201), ((GroupVideo) list.getAdapter().getItem(i)).channelId);
        feed.setContentFilter(GroupContentFilter.TODAY); settle(); assertEquals(3, list.getAdapter().getCount());
        feed.bind(group(0, 3), new GroupPreferences().with("default_content", "today"), true); settle(); assertEquals(2, list.getAdapter().getCount());
    }
    @Test public void productionCardDurationOverlayIsBottomRightAndCannotConsumeClicks() throws Exception {
        feed.bind(group(1, 1), new GroupPreferences(), true); settle();
        BaseAdapter adapter = (BaseAdapter) list.getAdapter(); View card = adapter.getView(0, null, list);
        Object holder = card.getTag(); TextView badge = (TextView) field(holder, "duration"); ImageView avatar = (ImageView) field(holder, "avatarImage");
        assertEquals("4:32", badge.getText().toString()); assertFalse(badge.isClickable()); assertFalse(badge.isFocusable());
        assertEquals(Gravity.BOTTOM | Gravity.RIGHT, ((FrameLayout.LayoutParams) badge.getLayoutParams()).gravity); assertTrue(avatar.getClipToOutline());
        card.measure(View.MeasureSpec.makeMeasureSpec(500, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(1000, View.MeasureSpec.AT_MOST)); card.layout(0, 0, card.getMeasuredWidth(), card.getMeasuredHeight());
        ImageView image = (ImageView) field(holder, "image"); assertEquals(Math.round(500 * 9f / 16f), image.getMeasuredHeight());
        list.performItemClick(card, 0, 0); android.content.Intent intent = Shadows.shadowOf(activity).getNextStartedActivity(); assertEquals(video(1), intent.getData().getQueryParameter("v"));
    }
    @Test public void recycledProductionCardClearsUnknownDurationAndDisplaysOnlyExplicitLiveState() throws Exception {
        feed.bind(group(1, 1), new GroupPreferences(), true); settle();
        BaseAdapter adapter = (BaseAdapter) list.getAdapter(); View card = adapter.getView(0, null, list); Object holder = card.getTag();
        Field bound = adapter.getClass().getDeclaredField("bound"); bound.setAccessible(true);
        GroupVideo original = (GroupVideo) adapter.getItem(0); TextView badge = (TextView) field(holder, "duration");
        bound.set(adapter, List.of(original.withMetadata("", VideoMetadata.UNKNOWN))); adapter.getView(0, card, list);
        assertEquals(View.GONE, badge.getVisibility()); assertEquals("", badge.getText().toString()); assertEquals("C", ((TextView) field(holder, "avatar")).getText().toString());
        bound.set(adapter, List.of(original.withMetadata("", new VideoMetadata(-1, VideoMetadata.Type.STREAM, VideoMetadata.Live.LIVE, false)))); adapter.getView(0, card, list);
        assertEquals(View.VISIBLE, badge.getVisibility()); assertEquals("LIVE", badge.getText().toString());
        bound.set(adapter, List.of(original.withMetadata("", new VideoMetadata(-1, VideoMetadata.Type.STREAM, VideoMetadata.Live.UPCOMING, false)))); adapter.getView(0, card, list);
        assertEquals("UPCOMING", badge.getText().toString());
    }
    @Test public void productionOverflowLongPressPreservesLocalWatchedActionsWithoutRssReload() throws Exception {
        feed.bind(group(1, 1), new GroupPreferences(), true); settle();
        BaseAdapter adapter = (BaseAdapter) list.getAdapter(); View card = adapter.getView(0, null, list);
        String id = ((GroupVideo) adapter.getItem(0)).id; Object holder = card.getTag(); View more = (View) field(holder, "more");
        activity.setContentView(card); assertTrue(more.isLongClickable()); assertTrue(more.performLongClick());
        android.widget.PopupMenu menu = org.robolectric.shadows.ShadowPopupMenu.getLatestPopupMenu(); assertNotNull(menu);
        menu.getMenu().performIdentifierAction(VideoActionPlan.Action.MARK_WATCHED.ordinal() + 1, 0);
        assertTrue(watchedIds.contains(id)); assertEquals(1, calls.values().stream().mapToInt(Integer::intValue).sum());
    }
    @Test public void hideWatchedReprojectsCachedAll155AndRestoresWithoutRssReload() {
        GroupLibrary.Entry group = group(0, 155); GroupPreferences prefs = new GroupPreferences();
        for (int i = 0; i < 155; i++) watchedIds.add(video(i));
        feed.bind(group, prefs.with("hide_watched", "true"), true); settle();
        assertEquals(155, calls.size()); assertEquals(0, list.getAdapter().getCount());
        feed.bind(group, prefs, true); settle(); assertEquals(155, list.getAdapter().getCount());
        assertEquals(155, calls.values().stream().mapToInt(Integer::intValue).sum());
        feed.bind(group, prefs.with("hide_watched", "true"), true); localWatched.mark(video(2), false); settle();
        assertEquals(1, list.getAdapter().getCount()); assertEquals(video(2), ((GroupVideo) list.getAdapter().getItem(0)).id);
        localWatched.clear(); settle(); assertEquals(155, list.getAdapter().getCount());
        assertEquals(155, calls.values().stream().mapToInt(Integer::intValue).sum());
    }
    @Test public void groupsRememberIndependentFiltersWhileStockAllRestorationDoesNotMutateThem() {
        GroupLibrary.Entry tech = group(0, 6), music = group(100, 6); GroupPreferences prefs = new GroupPreferences();
        feed.bind(tech, prefs, true); settle(); feed.setContentFilter(GroupContentFilter.VIDEOS); settle();
        feed.bind(music, prefs, true); settle(); assertEquals(GroupContentFilter.ALL, feed.contentFilter());
        feed.setContentFilter(GroupContentFilter.SHORTS); settle(); feed.pause();
        feed.bind(tech, prefs, true); settle(); assertEquals(GroupContentFilter.VIDEOS, feed.contentFilter());
        feed.bind(music, prefs, true); settle(); assertEquals(GroupContentFilter.SHORTS, feed.contentFilter());
        assertEquals(12, calls.size()); assertTrue(calls.values().stream().allMatch(value -> value == 1));
    }
    @Test public void persistedFilterRestoresBeforeInitialMetadataDispatchAndDoesNotRefetchOnTabReturn() {
        GroupLibrary.Entry group = group(0, 6);
        GroupPreferences prefs = new GroupPreferences().with(GroupPreferences.CONTENT_PREFIX + group.id, "shorts");
        feed.bind(group, prefs, true); assertEquals(GroupContentFilter.SHORTS, feed.contentFilter()); settle(); assertEquals(2, list.getAdapter().getCount());
        int count = metadataCalls; feed.pause(); feed.bind(group, prefs, true); settle();
        assertEquals(GroupContentFilter.SHORTS, feed.contentFilter()); assertEquals(count, metadataCalls); assertEquals(6, calls.size());
    }

}
