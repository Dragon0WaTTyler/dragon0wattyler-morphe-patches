/* SPDX-License-Identifier: GPL-3.0-only
 * Independent Subscription Groups groups.4, 2026-11-01. See LICENSE and NOTICE.
 */
package app.morphe.extension.youtube.groups;

import android.app.Activity;
import android.graphics.Insets;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.view.View;
import android.view.WindowInsets;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ListView;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import app.morphe.extension.youtube.shared.NavigationBar;
import static org.junit.Assert.*;

/** Actual Android View visibility/layout under Robolectric; representative chrome, not a YouTube APK. */
@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE, sdk = {29, 35})
public class GroupPresentationTest {
    private ActivityController<Activity> controller;
    private Activity activity;
    private LinearLayout page;
    private View toolbar, carousel, filters, navigation;
    private ListView original, groups;
    private ExclusiveFeedHost host;
    @Before public void create() {
        controller = Robolectric.buildActivity(Activity.class).setup(); activity = controller.get();
        page = new LinearLayout(activity); page.setOrientation(LinearLayout.VERTICAL);
        toolbar = new View(activity); carousel = new View(activity); filters = new View(activity); navigation = new View(activity);
        page.addView(toolbar, new LinearLayout.LayoutParams(-1, 56)); page.addView(carousel, new LinearLayout.LayoutParams(-1, 80));
        page.addView(filters, new LinearLayout.LayoutParams(-1, 48));
        original = new ListView(activity); groups = new ListView(activity); host = new ExclusiveFeedHost(original, groups);
        page.addView(host, new LinearLayout.LayoutParams(-1, 0, 1)); page.addView(navigation, new LinearLayout.LayoutParams(-1, 48));
        activity.setContentView(page); layout();
    }
    @After public void destroy() { controller.pause().stop().destroy(); }
    private void layout() {
        page.measure(View.MeasureSpec.makeMeasureSpec(500, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(900, View.MeasureSpec.EXACTLY));
        page.layout(0, 0, 500, 900);
    }
    private void one(boolean group) {
        assertEquals(group ? View.GONE : View.VISIBLE, original.getVisibility());
        assertEquals(group ? View.VISIBLE : View.GONE, groups.getVisibility());
        assertEquals(1, (original.getVisibility() == View.VISIBLE ? 1 : 0) + (groups.getVisibility() == View.VISIBLE ? 1 : 0));
    }
    @Test public void selectingAGroupShowsExactlyOneFullHeightFeed() {
        host.selectGroup(true); layout(); one(true);
        assertEquals(host.getHeight(), groups.getHeight()); assertTrue(host.getHeight() > 400);
    }
    @Test public void allRestoresOriginalFeed() { host.selectGroup(true); host.selectGroup(false); layout(); one(false); }
    @Test public void rapidSwitchingNeverShowsBothFeeds() { for (int i = 0; i < 200; i++) { host.selectGroup(i % 3 != 0); one(i % 3 != 0); } }
    @Test public void emptyGroupLoadingDoesNotRevealOriginalUnderneath() { host.selectGroup(true); assertNull(groups.getAdapter()); layout(); one(true); }
    @Test public void nativeToolbarCarouselFiltersAndBottomNavigationAreUntouched() {
        host.selectGroup(true); layout();
        for (View sibling : new View[]{toolbar, carousel, filters, navigation}) assertEquals(View.VISIBLE, sibling.getVisibility());
        assertSame(page, toolbar.getParent()); assertSame(page, navigation.getParent());
    }
    @Test public void cachedPageDetachRestoresNativeAndReattachReappliesSelection() {
        host.selectGroup(true); page.removeView(host); one(false);
        page.addView(host, 3, new LinearLayout.LayoutParams(-1, 0, 1)); layout(); one(true);
    }
    @Test public void leavingTheTabRestoresNativeWithoutTouchingAnotherTab() {
        FrameLayout otherTab = new FrameLayout(activity); View otherFeed = new View(activity); otherTab.addView(otherFeed);
        host.selectGroup(true); host.selectGroup(false); one(false); assertEquals(View.VISIBLE, otherFeed.getVisibility());
    }
    @Test public void recreationUsesRestoredGroupSelectionAndAllIndependently() throws Exception {
        GroupLibraryStore store = new GroupLibraryStore(activity.getCacheDir());
        GroupLibrary library = GroupLibrary.empty().create("Books");
        library = library.preferences(library.preferences.with("default", library.groups.get(0).id)); store.save(library);
        host.selectGroup(true); Activity before = activity;
        controller.recreate(); activity = controller.get(); assertNotSame(before, activity);
        ListView nativeView = new ListView(activity), groupView = new ListView(activity);
        ExclusiveFeedHost recreated = new ExclusiveFeedHost(nativeView, groupView);
        recreated.selectGroup(!GroupSelection.enter(store.load(), true).isEmpty()); activity.setContentView(recreated);
        assertEquals(View.GONE, nativeView.getVisibility()); assertEquals(View.VISIBLE, groupView.getVisibility());
        store.save(library.preferences(library.preferences.with("default", "")));
        controller.recreate(); activity = controller.get(); nativeView = new ListView(activity); groupView = new ListView(activity);
        recreated = new ExclusiveFeedHost(nativeView, groupView);
        recreated.selectGroup(!GroupSelection.enter(store.load(), true).isEmpty()); activity.setContentView(recreated);
        assertEquals(View.VISIBLE, nativeView.getVisibility()); assertEquals(View.GONE, groupView.getVisibility());
    }
    @Test public void nativeVisibilityChangesCannotCreateASecondFeedAtLayout() {
        host.selectGroup(true); original.setVisibility(View.VISIBLE); host.requestLayout(); layout(); one(true);
    }
    @Test public void allModeRespectsNativeLoadingVisibilityAndRestoresItsOriginalState() {
        original.setVisibility(View.INVISIBLE); host.selectGroup(true); one(true);
        host.selectGroup(false); layout(); assertEquals(View.INVISIBLE, original.getVisibility()); assertEquals(View.GONE, groups.getVisibility());
        original.setVisibility(View.VISIBLE); layout(); one(false);
    }
    private final class InsetRoot extends FrameLayout {
        int safeTop = 24;
        InsetRoot() { super(activity); }
        @Override public WindowInsets getRootWindowInsets() {
            WindowInsets.Builder builder = new WindowInsets.Builder();
            if (Build.VERSION.SDK_INT >= 30) builder.setInsets(WindowInsets.Type.statusBars(), Insets.of(0, safeTop, 0, 0)).setVisible(WindowInsets.Type.statusBars(), safeTop > 0);
            else builder.setSystemWindowInsets(Insets.of(0, safeTop, 0, 0));
            return builder.build();
        }
    }
    private final class NativeToolbar extends FrameLayout implements NavigationBar.AppCompatToolbarPatchInterface {
        final InsetRoot root; int baseTop;
        NativeToolbar(InsetRoot root) { super(activity); this.root = root; }
        public Drawable patch_getNavigationIcon() { return null; }
        @Override public void getLocationOnScreen(int[] position) { position[0] = 0; position[1] = baseTop + root.getPaddingTop(); }
    }
    @Test public void measuredInsetsAreAppliedOnceAndRestoredWithoutGlobalChanges() {
        InsetRoot root = new InsetRoot(); root.setPadding(3, 7, 5, 9); NativeToolbar bar = new NativeToolbar(root);
        root.addView(bar, new FrameLayout.LayoutParams(100, 56)); activity.setContentView(root);
        root.measure(View.MeasureSpec.makeMeasureSpec(500, 1073741824), View.MeasureSpec.makeMeasureSpec(900, 1073741824)); root.layout(0, 0, 500, 900);
        GroupChromeBinding binding = new GroupChromeBinding(root); binding.setActive(true);
        assertEquals(24, root.getPaddingTop()); binding.onPreDraw(); binding.onPreDraw(); assertEquals(24, root.getPaddingTop());
        assertEquals(3, root.getPaddingLeft()); assertEquals(9, root.getPaddingBottom());
        binding.setActive(false); assertEquals(7, root.getPaddingTop()); binding.release();
    }
    @Test public void stockInsetsAndHiddenStatusBarDoNotAddAnotherStatusHeight() {
        assertEquals(0, GroupChromeBinding.missingTopInset(24, 24));
        assertEquals(0, GroupChromeBinding.missingTopInset(24, 40));
        assertEquals(0, GroupChromeBinding.missingTopInset(0, 0));
        assertEquals(17, GroupChromeBinding.missingTopInset(24, 7));
    }
    @Test public void nativeLateInsetPaddingReplacesOurCompensationRatherThanDoublingIt() {
        InsetRoot root = new InsetRoot(); root.setPadding(0, 7, 0, 0); NativeToolbar bar = new NativeToolbar(root);
        root.addView(bar, new FrameLayout.LayoutParams(100, 56)); activity.setContentView(root);
        root.measure(View.MeasureSpec.makeMeasureSpec(500, 1073741824), View.MeasureSpec.makeMeasureSpec(900, 1073741824)); root.layout(0, 0, 500, 900);
        GroupChromeBinding binding = new GroupChromeBinding(root); binding.setActive(true); assertEquals(24, root.getPaddingTop());
        // Native inset consumption can pad inside the toolbar without moving its outer bounds.
        bar.setPadding(0, 24, 0, 0); binding.onPreDraw();
        assertEquals(7, root.getPaddingTop()); binding.release();
    }
    @Test public void cachedBrowseOwnerCannotBindAnUnrelatedTabChrome() {
        FrameLayout unrelated = new FrameLayout(activity); unrelated.addView(new View(activity));
        assertTrue(GroupChromeBinding.owns(page, host)); assertFalse(GroupChromeBinding.owns(unrelated, host));
    }
}
