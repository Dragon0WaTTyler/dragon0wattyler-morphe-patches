/* SPDX-License-Identifier: GPL-3.0-only
 * Independent Subscription Groups groups.5, 2026-11-01. See LICENSE and NOTICE.
 */
package app.morphe.extension.youtube.groups;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ColorDrawable;
import android.os.Handler;
import android.os.Looper;
import android.text.Spanned;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.Shadows;
import app.morphe.extension.shared.patches.LithoRelayoutPatch.LithoViewInterface;
import app.morphe.extension.shared.patches.components.ContextInterface;
import java.io.ByteArrayOutputStream;
import java.util.ArrayDeque;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE, sdk = {29, 35})
public class GroupNativeUiTest {
    private ActivityController<Activity> controller;
    private Activity activity;
    private LinearLayout root;
    private View content, toolbar, home;
    private NativeRow filters, carousel;
    private GroupsNativeChrome binding;
    private static final class NativeRow extends FrameLayout implements LithoViewInterface {
        NativeRow(Activity a) { super(a); }
        public void patch_forceRelayout() { }
        public void patch_forceRemount() { }
    }
    private static ContextInterface context(String path) {
        return new ContextInterface() {
            public String patch_getIdentifier() { return ""; }
            public StringBuilder patch_getPathBuilder() { return new StringBuilder(path); }
            public Integer patch_getHeightConstraint() { return null; }
            public Object patch_getHorizontalCollectionSwipeProtector() { return null; }
        };
    }
    @Before public void setup() {
        org.robolectric.shadows.ShadowBitmapFactory.setAllowInvalidImageData(false);
        controller = Robolectric.buildActivity(Activity.class).setup(); activity = controller.get();
        app.morphe.extension.shared.Utils.setContext(activity);
        root = new LinearLayout(activity); root.setOrientation(LinearLayout.VERTICAL);
        toolbar = new View(activity); filters = new NativeRow(activity); carousel = new NativeRow(activity); content = new FrameLayout(activity); home = new NativeRow(activity);
        root.addView(toolbar, new LinearLayout.LayoutParams(-1, 56)); root.addView(carousel, new LinearLayout.LayoutParams(-1, 80)); root.addView(filters, new LinearLayout.LayoutParams(-1, 48));
        root.addView(content, new LinearLayout.LayoutParams(-1, 0, 1)); activity.setContentView(root);
        binding = new GroupsNativeChrome(root, content); GroupsNativeChrome.onCarouselCreated(carousel);
        mark(filters, "subscriptions_chip_bar|chip.e"); mark(home, "subscriptions_chip_bar|chip.e");
    }
    private void mark(View view, String path) {
        CharSequence text = GroupsNativeChrome.onLithoTextLoaded(context(path), "All"); ColorDrawable drawable = new ColorDrawable(); drawable.setCallback(view);
        GroupsNativeChrome.onLithoTextMounted(drawable, text); Shadows.shadowOf(Looper.getMainLooper()).idle();
    }
    @After public void cleanup() { binding.release(); controller.pause().stop().destroy(); }
    private void layout() { root.measure(View.MeasureSpec.makeMeasureSpec(500, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(900, View.MeasureSpec.EXACTLY)); root.layout(0, 0, 500, 900); }
    @Test public void customGroupHidesOnlyOwnedNativeFiltersAndCarouselWithoutGap() {
        binding.setMode(true, true); layout();
        assertEquals(View.GONE, filters.getVisibility()); assertEquals(View.GONE, carousel.getVisibility()); assertEquals(View.VISIBLE, toolbar.getVisibility());
        assertEquals(56, content.getTop()); assertEquals(View.VISIBLE, home.getVisibility());
    }
    @Test public void allGroupsRestoresOriginalNativeFiltersAndKeepsCarouselRemoved() {
        binding.setMode(true, true); binding.setMode(true, false); layout();
        assertEquals(View.VISIBLE, filters.getVisibility()); assertEquals(View.GONE, carousel.getVisibility()); assertEquals(104, content.getTop());
    }
    @Test public void leavingTabOrDisablingRestoresExactOriginalVisibility() {
        filters.setVisibility(View.INVISIBLE); binding.setMode(true, true); binding.setMode(false, false);
        assertEquals(View.INVISIBLE, filters.getVisibility()); assertEquals(View.VISIBLE, carousel.getVisibility()); assertEquals(View.VISIBLE, home.getVisibility());
    }
    @Test public void reusedNativeRootThatContainsContentIsNeverHidden() {
        GroupsNativeChrome.onCarouselCreated(root); binding.setMode(true, true); assertEquals(View.VISIBLE, root.getVisibility());
    }
    @Test public void rowInsideCustomContentIsNeverHidden() {
        NativeRow custom = new NativeRow(activity); ((FrameLayout) content).addView(custom); mark(custom, "subscriptions_chip_bar|chip.e");
        binding.setMode(true, true); assertEquals(View.VISIBLE, custom.getVisibility());
    }
    @Test public void rapidGroupFilterAllSwitchesDoNotMutateOtherTabs() {
        for (int i = 0; i < 155; i++) { boolean group = i % 3 != 0; binding.setMode(true, group); assertEquals(group ? View.GONE : View.VISIBLE, filters.getVisibility()); assertEquals(View.VISIBLE, home.getVisibility()); }
    }
    @Test public void markerUsesComponentPathAndPreservesOriginalTextAndStyling() {
        CharSequence original = "Shorts"; assertSame(original, GroupsNativeChrome.onLithoTextLoaded(context("home_chip_bar"), original));
        CharSequence marked = GroupsNativeChrome.onLithoTextLoaded(context("subscriptions_chip_bar|chip.e"), original);
        assertEquals("Shorts", marked.toString()); assertEquals(1, ((Spanned) marked).getSpans(0, marked.length(), GroupsNativeChrome.FilterMarker.class).length);
        assertSame(marked, GroupsNativeChrome.onLithoTextLoaded(context("subscriptions_chip_bar|chip.e"), marked));
        CharSequence reused = GroupsNativeChrome.onLithoTextLoaded(context("home_chip_bar|chip.e"), marked);
        assertEquals(0, ((Spanned) reused).getSpans(0, reused.length(), GroupsNativeChrome.FilterMarker.class).length);
    }
    @Test public void carouselPathDoesNotMatchCardAvatarsOrChannelPages() {
        assertTrue(GroupsComponentsFilter.carouselPath("feed|subscriptions_channel_bar.e|row"));
        assertFalse(GroupsComponentsFilter.carouselPath("video_card|avatar.e")); assertFalse(GroupsComponentsFilter.carouselPath("channel_header|avatar.e"));
        assertFalse(GroupsComponentsFilter.carouselPath("subscriptions_channel_bar_channel.e"));
    }
    @Test public void groupChipLabelsNeverIncludeCountsEvenWithLegacyPreference() {
        GroupLibrary library = GroupLibrary.empty().create("Books & Literature — a complete long group name");
        GroupLibrary.Entry group = library.groups.get(0); GroupPreferences prefs = library.preferences.with("counts", "true").with("icons", "false");
        assertEquals(group.name, SubscriptionsPage.chip(group, prefs)); assertFalse(new GroupPreferences().enabled("counts"));
    }
    private byte[] png(int color) {
        Bitmap bitmap = Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888); bitmap.eraseColor(color);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream(); bitmap.compress(Bitmap.CompressFormat.PNG, 100, bytes); return bytes.toByteArray();
    }
    @Test public void avatarSingleFlightMemoryCacheAndPersistentReuse() {
        ArrayDeque<Runnable> jobs = new ArrayDeque<>(); AtomicInteger calls = new AtomicInteger(); String url = GroupMetadataTest.AVATAR + "&test=cache";
        GroupImages images = new GroupImages(jobs::add, new Handler(Looper.getMainLooper()), ignored -> { calls.incrementAndGet(); return png(Color.RED); });
        ImageView one = new ImageView(activity), two = new ImageView(activity);
        images.bindImage(one, url, true); images.bindImage(two, url, true); assertEquals(1, jobs.size()); jobs.remove().run(); Shadows.shadowOf(Looper.getMainLooper()).idle();
        assertNotNull(one.getDrawable()); assertNotNull(two.getDrawable()); assertEquals(1, calls.get());
        images.bindImage(two, url, true); assertTrue(jobs.isEmpty());
        GroupImages recreated = new GroupImages(jobs::add, new Handler(Looper.getMainLooper()), ignored -> { throw new AssertionError("Image disk cache must be used"); });
        recreated.bindImage(one, url, true); jobs.remove().run(); Shadows.shadowOf(Looper.getMainLooper()).idle(); assertNotNull(one.getDrawable());
    }
    @Test public void recycledAvatarNeverReceivesPreviousChannelImage() {
        ArrayDeque<Runnable> jobs = new ArrayDeque<>(); GroupImages images = new GroupImages(jobs::add, new Handler(Looper.getMainLooper()), url -> png(url.contains("old") ? Color.RED : Color.BLUE));
        ImageView image = new ImageView(activity); images.bindImage(image, GroupMetadataTest.AVATAR + "&old", true); images.bindImage(image, GroupMetadataTest.AVATAR + "&new", true);
        jobs.remove().run(); Shadows.shadowOf(Looper.getMainLooper()).idle(); assertNull(image.getDrawable());
        jobs.remove().run(); Shadows.shadowOf(Looper.getMainLooper()).idle(); assertEquals(Color.BLUE, ((BitmapDrawable) image.getDrawable()).getBitmap().getPixel(0, 0));
    }
    @Test public void invalidAvatarAndDecodeFailureKeepFallbackLayerVisible() {
        ArrayDeque<Runnable> jobs = new ArrayDeque<>(); GroupImages images = new GroupImages(jobs::add, new Handler(Looper.getMainLooper()), url -> new byte[]{0, 1, 2});
        FrameLayout frame = new FrameLayout(activity); View initials = new View(activity); ImageView image = new ImageView(activity); frame.addView(initials); frame.addView(image);
        images.bindImage(image, "https://invalid.test/avatar", true); assertTrue(jobs.isEmpty()); assertNull(image.getDrawable());
        images.bindImage(image, GroupMetadataTest.AVATAR + "&bad", true); jobs.remove().run(); Shadows.shadowOf(Looper.getMainLooper()).idle();
        assertNull(image.getDrawable()); assertEquals(View.VISIBLE, initials.getVisibility());
    }
    @Test public void nativeCarouselInsideOriginalFeedCanBeRemovedWithoutHidingCustomContent() {
        FrameLayout nativeOriginal = new FrameLayout(activity); NativeRow nativeCarousel = new NativeRow(activity); nativeOriginal.addView(nativeCarousel);
        ((FrameLayout) content).addView(nativeOriginal); GroupsNativeChrome.onCarouselCreated(nativeCarousel);
        GroupsNativeChrome nested = new GroupsNativeChrome(root, content, nativeOriginal);
        try { nested.setMode(true, false); assertEquals(View.GONE, nativeCarousel.getVisibility()); assertEquals(View.VISIBLE, nativeOriginal.getVisibility()); assertEquals(View.VISIBLE, content.getVisibility()); }
        finally { nested.release(); }
    }
    @Test public void nativeVideoAndChannelNavigationRetainsSamePackageAndStableId() {
        GroupUi.video(activity, GroupMetadataTest.ID); android.content.Intent video = Shadows.shadowOf(activity).getNextStartedActivity();
        assertEquals(activity.getPackageName(), video.getPackage()); assertEquals(GroupMetadataTest.ID, video.getData().getQueryParameter("v"));
        GroupUi.channel(activity, GroupMetadataTest.CHANNEL); android.content.Intent channel = Shadows.shadowOf(activity).getNextStartedActivity();
        assertEquals(activity.getPackageName(), channel.getPackage()); assertEquals("/channel/" + GroupMetadataTest.CHANNEL, channel.getData().getPath());
    }
    private byte[] sizedPng(int width, int height, int color) {
        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888); bitmap.eraseColor(color);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream(); bitmap.compress(Bitmap.CompressFormat.PNG, 100, bytes); return bytes.toByteArray();
    }
    private GroupVideo hdVideo(String id) {
        return new GroupVideo(id, GroupMetadataTest.CHANNEL, "HD", "Channel", "https://i.ytimg.com/vi/" + id + "/preview.jpg", 1, 1, "",
                new VideoMetadata(200, VideoMetadata.Type.VIDEO, VideoMetadata.Live.NONE, false, java.util.List.of(
                        new ThumbnailVariant("https://i.ytimg.com/vi/" + id + "/provided-720.jpg", 720, 405),
                        new ThumbnailVariant("https://i.ytimg.com/vi/" + id + "/provided-1280.jpg", 1280, 720))));
    }
    @Test public void actualThumbnailLoaderKeeps1280PixelsFor1080CardAndSharesRequests() {
        ArrayDeque<Runnable> jobs = new ArrayDeque<>(); AtomicInteger calls = new AtomicInteger();
        GroupImages images = new GroupImages(jobs::add, new Handler(Looper.getMainLooper()), url -> { calls.incrementAndGet(); assertTrue(url.contains("provided-1280")); return sizedPng(1280, 720, Color.BLUE); });
        ImageView one = new ImageView(activity), two = new ImageView(activity); one.layout(0, 0, 1080, 608); two.layout(0, 0, 1080, 608);
        images.thumbnail(one, hdVideo("00000000101")); images.thumbnail(two, hdVideo("00000000101")); assertEquals(1, jobs.size());
        jobs.remove().run(); Shadows.shadowOf(Looper.getMainLooper()).idle(); assertEquals(1, calls.get());
        assertEquals(1280, ((BitmapDrawable) one.getDrawable()).getBitmap().getWidth()); assertEquals(1280, ((BitmapDrawable) two.getDrawable()).getBitmap().getWidth());
    }
    @Test public void cachedPreviewRemainsVisibleWhileRealHigherResolutionSourceLoads() {
        ArrayDeque<Runnable> jobs = new ArrayDeque<>();
        GroupImages images = new GroupImages(jobs::add, new Handler(Looper.getMainLooper()), url -> url.contains("preview") ? sizedPng(480, 360, Color.RED) : sizedPng(1280, 720, Color.BLUE));
        GroupVideo video = hdVideo("00000000102"); ImageView image = new ImageView(activity); image.layout(0, 0, 1080, 608);
        images.bindImage(image, video.thumbnail, false); jobs.remove().run(); Shadows.shadowOf(Looper.getMainLooper()).idle();
        images.thumbnail(image, video); assertEquals(480, ((BitmapDrawable) image.getDrawable()).getBitmap().getWidth());
        jobs.remove().run(); Shadows.shadowOf(Looper.getMainLooper()).idle(); assertEquals(1280, ((BitmapDrawable) image.getDrawable()).getBitmap().getWidth());
        images.thumbnail(image, video); assertTrue(jobs.isEmpty());
    }
    @Test public void failedHigherResolutionFallsBackToAvailableMetadataWithoutInventingUrls() {
        ArrayDeque<Runnable> jobs = new ArrayDeque<>(); java.util.List<String> calls = new java.util.ArrayList<>();
        GroupImages images = new GroupImages(jobs::add, new Handler(Looper.getMainLooper()), url -> { calls.add(url); if (url.contains("1280")) throw new java.io.IOException("HTTP404"); return sizedPng(720, 405, Color.BLUE); });
        ImageView image = new ImageView(activity); image.layout(0, 0, 1080, 608); images.thumbnail(image, hdVideo("00000000103"));
        jobs.remove().run(); Shadows.shadowOf(Looper.getMainLooper()).idle(); jobs.remove().run(); Shadows.shadowOf(Looper.getMainLooper()).idle();
        assertEquals(720, ((BitmapDrawable) image.getDrawable()).getBitmap().getWidth()); assertEquals(2, calls.size());
        assertTrue(calls.get(0).contains("1280")); assertTrue(calls.get(1).contains("720"));
    }
    @Test public void rebindCannotReplaceAnAlreadySharpThumbnailWithCachedLowerQuality() {
        ArrayDeque<Runnable> jobs = new ArrayDeque<>();
        GroupImages images = new GroupImages(jobs::add, new Handler(Looper.getMainLooper()), url -> url.contains("1280") ? sizedPng(1280, 720, Color.BLUE) : sizedPng(720, 405, Color.RED));
        GroupVideo sharp = hdVideo("00000000104"); ImageView image = new ImageView(activity); image.layout(0, 0, 1080, 608);
        images.thumbnail(image, sharp); jobs.remove().run(); Shadows.shadowOf(Looper.getMainLooper()).idle();
        ImageView warm = new ImageView(activity); images.bindImage(warm, sharp.metadata.thumbnails.get(0).url, false);
        jobs.remove().run(); Shadows.shadowOf(Looper.getMainLooper()).idle();
        GroupVideo lower = new GroupVideo(sharp.id, sharp.channelId, sharp.title, sharp.channelName, sharp.thumbnail, sharp.published, sharp.views,
                sharp.avatar, new VideoMetadata(200, VideoMetadata.Type.VIDEO, VideoMetadata.Live.NONE, false, java.util.List.of(sharp.metadata.thumbnails.get(0))));
        images.thumbnail(image, lower); assertTrue(jobs.isEmpty());
        assertEquals(1280, ((BitmapDrawable) image.getDrawable()).getBitmap().getWidth());
    }
    @Test public void nativeActionsReceiveImmutableVideoIdAfterFeedChangesAndUnsupportedActionsAreAbsent() {
        GroupVideo original = GroupMetadataTest.model(1, VideoMetadata.UNKNOWN); java.util.List<String> invoked = new java.util.ArrayList<>();
        GroupVideoActions.NativeActions supported = new GroupVideoActions.NativeActions() {
            public boolean watchLater() { return true; } public boolean playlist() { return true; } public boolean queue(String id) { return true; }
            public boolean invoke(VideoActionPlan.Action action, String id) { invoked.add(action.name() + ":" + id); return true; }
        };
        android.widget.PopupMenu menu = GroupVideoActions.menu(activity, toolbar, original, WatchedState.EMPTY, supported);
        // The original immutable command survives another row/video being bound or reordered.
        GroupVideo other = new GroupVideo("00000000002", original.channelId, "Other", "Channel", "", 3, 1);
        menu.getMenu().performIdentifierAction(VideoActionPlan.Action.SAVE_PLAYLIST.ordinal() + 1, 0);
        menu.getMenu().performIdentifierAction(VideoActionPlan.Action.QUEUE_LAST.ordinal() + 1, 0);
        assertEquals(java.util.List.of("SAVE_PLAYLIST:" + original.id, "QUEUE_LAST:" + original.id), invoked); assertNotEquals(original.id, other.id);
        GroupVideoActions.NativeActions unavailable = new GroupVideoActions.NativeActions() {
            public boolean watchLater() { return false; } public boolean playlist() { return false; } public boolean queue(String id) { return false; }
            public boolean invoke(VideoActionPlan.Action action, String id) { throw new AssertionError("Unavailable native action invoked"); }
        };
        menu = GroupVideoActions.menu(activity, toolbar, original, WatchedState.EMPTY, unavailable);
        assertNull(menu.getMenu().findItem(VideoActionPlan.Action.SAVE_PLAYLIST.ordinal() + 1)); assertNull(menu.getMenu().findItem(VideoActionPlan.Action.WATCH_LATER.ordinal() + 1));
        menu.getMenu().performIdentifierAction(VideoActionPlan.Action.SHARE.ordinal() + 1, 0);
        android.content.Intent chooser = Shadows.shadowOf(activity).getNextStartedActivity(); android.content.Intent share = chooser.getParcelableExtra(android.content.Intent.EXTRA_INTENT);
        assertEquals(android.content.Intent.ACTION_SEND, share.getAction()); assertEquals("https://www.youtube.com/watch?v=" + original.id, share.getStringExtra(android.content.Intent.EXTRA_TEXT));
    }

}
