/* SPDX-License-Identifier: GPL-3.0-only
 * Independent Subscription Groups groups.6.1, modified 2026-11-02. See LICENSE/NOTICE.
 */
package app.morphe.extension.youtube.groups;

import android.app.Activity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.PopupMenu;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import org.robolectric.android.controller.ActivityController;
import java.util.*;
import java.util.concurrent.atomic.*;
import app.morphe.extension.youtube.innertube.queue.PlaylistQueueRenderer.*;
import app.morphe.extension.youtube.patches.utils.FlyoutUtils;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE, sdk = {29, 35})
public class GroupNativeMenuUiTest {
    private ActivityController<Activity> controller; private Activity activity; private View sender;
    @Before public void setup() throws Exception {
        controller = Robolectric.buildActivity(Activity.class).setup(); activity = controller.get();
        android.content.pm.PackageInfo info = activity.getPackageManager().getPackageInfo(activity.getPackageName(), 0);
        info.versionName = "21.30.216"; Shadows.shadowOf(activity.getPackageManager()).installPackage(info);
        app.morphe.extension.shared.Utils.setContext(activity);
        FrameLayout root = new FrameLayout(activity); sender = new View(activity); root.addView(sender, new FrameLayout.LayoutParams(48, 48)); activity.setContentView(root);
        root.measure(View.MeasureSpec.makeMeasureSpec(500, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(900, View.MeasureSpec.EXACTLY)); root.layout(0, 0, 500, 900);
        GroupsNativeVideoMenu.host(activity);
    }
    @After public void cleanup() { GroupsNativeVideoMenu.pause(activity); controller.pause().stop().destroy(); }
    private GroupVideo video(String id, VideoMetadata.Type type) { return new GroupVideo(id, GroupMetadataTest.CHANNEL, "Title", "Channel", "", 1, 1, "", new VideoMetadata(200, type, VideoMetadata.Live.NONE, false)); }
    private NativeMenuRegistry registry(NativeMenuRegistry.Handler handler) {
        NativeMenuRegistry r = new NativeMenuRegistry(() -> 100, () -> new NativeMenuRegistry.Scope(1, 1), bytes -> NativeMenuIdentity.video(bytes, NativeMenuRegistryTest.FIELD)); r.capture(handler); return r;
    }
    @Test public void dispatcherPrefersNativeAndKeepsSelectedVideoTypeAndSenderAfterReordering() {
        AtomicInteger fallback = new AtomicInteger(); List<String> selected = new ArrayList<>();
        GroupVideo regular = video(NativeMenuRegistryTest.ID, VideoMetadata.Type.VIDEO), shorts = video(NativeMenuRegistryTest.OTHER, VideoMetadata.Type.SHORT);
        GroupVideo live = video("00000000613", VideoMetadata.Type.STREAM).withMetadata("", new VideoMetadata(-1, VideoMetadata.Type.STREAM, VideoMetadata.Live.LIVE, false));
        GroupVideo upcoming = video("00000000614", VideoMetadata.Type.STREAM).withMetadata("", new VideoMetadata(-1, VideoMetadata.Type.STREAM, VideoMetadata.Live.UPCOMING, false));
        List<GroupVideo> kinds = List.of(regular, shorts, live, upcoming);
        for (int i = 0; i < 155; i++) {
            GroupVideo chosen = kinds.get(i % kinds.size());
            GroupVideoActions.show(activity, sender, chosen, WatchedState.EMPTY, (anchor, item) -> { assertSame(sender, anchor); assertSame(chosen, item); selected.add(item.id); return true; }, fallback::incrementAndGet);
        }
        assertEquals(155, selected.size()); assertEquals(0, fallback.get()); assertEquals(shorts.id, selected.get(1));
    }
    @Test public void missingNativeContextUsesOnlyFunctionalFallback() {
        AtomicInteger fallback = new AtomicInteger();
        GroupVideoActions.show(activity, sender, video(NativeMenuRegistryTest.ID, VideoMetadata.Type.STREAM), WatchedState.EMPTY, (anchor, item) -> false, fallback::incrementAndGet);
        assertEquals(1, fallback.get());
        assertFalse(GroupsNativeVideoMenu.open(sender, video(NativeMenuRegistryTest.ID, VideoMetadata.Type.UNKNOWN)));
    }
    @Test public void originalNativeCommandDispatchSetsMorpheIdentityOnlyForExactMap() {
        AtomicBoolean nativeDispatch = new AtomicBoolean(); Map<String, Object> map = new HashMap<>(); map.put("com.google.android.libraries.youtube.rendering.elements.sender_view", sender);
        NativeMenuRegistry.Handler handler = new NativeMenuRegistry.Handler() {
            public byte[] patch_groupsMenuCommand() { return NativeMenuRegistryTest.command(NativeMenuRegistryTest.ID); }
            public void patch_groupsOpenMenu(Object actual) {
                assertSame(sender, actual);
                Menu nativeMenu = Menu.newBuilder().setMenuRenderer(MenuRenderer.newBuilder().addItems(NativeMenuRegistryTest.item(NativeMenuRegistryTest.ID))).build();
                assertTrue(GroupsNativeVideoMenu.beforeNativeMenu(nativeMenu, map));
                assertNull(GroupsNativeVideoMenu.identityFor(new HashMap<>(map)));
                FlyoutUtils.extractFlyoutIdFromMap(map); assertEquals(NativeMenuRegistryTest.ID, FlyoutUtils.getFlyoutVideoId()); nativeDispatch.set(true);
            }
        };
        assertTrue(GroupsNativeVideoMenu.open(sender, video(NativeMenuRegistryTest.ID, VideoMetadata.Type.VIDEO), registry(handler))); assertTrue(nativeDispatch.get());
        // A normal native Home sender keeps the original typed endpoint and execution path.
        Map<String, Object> home = new HashMap<>(); home.put("com.google.android.libraries.youtube.rendering.elements.sender_view", new View(activity));
        home.put("com.google.android.libraries.youtube.innertube.endpoint.tag", (FlyoutUtils.FlyoutMenuVideoIdInterface) () -> NativeMenuRegistryTest.OTHER);
        assertTrue(GroupsNativeVideoMenu.beforeNativeMenu(new Object(), home)); FlyoutUtils.extractFlyoutIdFromMap(home);
        assertEquals(NativeMenuRegistryTest.OTHER, FlyoutUtils.getFlyoutVideoId());
    }
    @Test public void mismatchedNativePayloadCannotShowTheWrongVideoMenu() {
        AtomicBoolean nativeDispatch = new AtomicBoolean();
        NativeMenuRegistry.Handler handler = new NativeMenuRegistry.Handler() {
            public byte[] patch_groupsMenuCommand() { return NativeMenuRegistryTest.command(NativeMenuRegistryTest.ID); }
            public void patch_groupsOpenMenu(Object actual) {
                Map<String, Object> map = Map.of("com.google.android.libraries.youtube.rendering.elements.sender_view", actual);
                Menu wrong = Menu.newBuilder().setMenuRenderer(MenuRenderer.newBuilder().addItems(NativeMenuRegistryTest.item(NativeMenuRegistryTest.OTHER))).build();
                if (GroupsNativeVideoMenu.beforeNativeMenu(wrong, map)) nativeDispatch.set(true);
            }
        };
        assertFalse(GroupsNativeVideoMenu.open(sender, video(NativeMenuRegistryTest.ID, VideoMetadata.Type.VIDEO), registry(handler))); assertFalse(nativeDispatch.get());
    }
    @Test public void detachedAndOtherActivityAnchorsNeverInvokeNativeHandler() {
        NativeMenuRegistryTest.Handler handler = new NativeMenuRegistryTest.Handler(NativeMenuRegistryTest.ID); NativeMenuRegistry r = registry(handler);
        View detached = new View(activity); assertFalse(GroupsNativeVideoMenu.open(detached, video(NativeMenuRegistryTest.ID, VideoMetadata.Type.VIDEO), r));
        GroupsNativeVideoMenu.pause(activity); assertFalse(GroupsNativeVideoMenu.open(sender, video(NativeMenuRegistryTest.ID, VideoMetadata.Type.VIDEO), r)); assertEquals(0, handler.opens);
    }
    @Test public void localWatchedActionsRemainSeparateFromASuccessfulNativeMenu() {
        Set<String> records = new HashSet<>(); WatchedState local = new WatchedState() {
            public boolean ready() { return true; } public boolean contains(String id) { return records.contains(id); }
            public void mark(String id, boolean value) { if (value) records.add(id); else records.remove(id); }
            public void clear() { records.clear(); } public void observe(Runnable listener) {} public void remove(Runnable listener) {}
        };
        GroupVideo item = video(NativeMenuRegistryTest.ID, VideoMetadata.Type.VIDEO);
        GroupVideoActions.show(activity, sender, item, local, (anchor, selected) -> true, () -> fail("Native menu must stay primary"));
        GroupVideoActions.NativeActions unsupported = new GroupVideoActions.NativeActions() {
            public boolean watchLater() { return false; } public boolean playlist() { return false; } public boolean queue(String id) { return false; }
            public boolean invoke(VideoActionPlan.Action action, String id) { throw new AssertionError(); }
        };
        PopupMenu menu = GroupVideoActions.menu(activity, sender, item, local, unsupported);
        menu.getMenu().performIdentifierAction(VideoActionPlan.Action.MARK_WATCHED.ordinal() + 1, 0); assertTrue(records.contains(item.id));
        menu = GroupVideoActions.menu(activity, sender, item, local, unsupported);
        menu.getMenu().performIdentifierAction(VideoActionPlan.Action.MARK_UNWATCHED.ordinal() + 1, 0); assertFalse(records.contains(item.id));
        assertNull(menu.getMenu().findItem(VideoActionPlan.Action.SAVE_PLAYLIST.ordinal() + 1));
    }
    @Test public void accountContextEpochAndIncognitoInvalidateCapturedNativeCapabilities() {
        var auth = app.morphe.extension.shared.innertube.utils.AuthUtils.getRequestHeader();
        String originalPage = auth.get("X-Goog-PageId"); boolean originalIncognito = app.morphe.extension.shared.innertube.utils.AuthUtils.isIncognito();
        try {
            app.morphe.extension.shared.innertube.utils.AuthUtils.setIncognitoStatus(false);
            NativeMenuRegistryTest.Handler handler = new NativeMenuRegistryTest.Handler(NativeMenuRegistryTest.ID);
            NativeMenuRegistry r = new NativeMenuRegistry(() -> 100, () -> app.morphe.extension.shared.innertube.utils.AuthUtils.isIncognito() ? null
                    : new NativeMenuRegistry.Scope(1, app.morphe.extension.shared.innertube.utils.AuthUtils.getContextGeneration()), bytes -> NativeMenuIdentity.video(bytes, NativeMenuRegistryTest.FIELD));
            r.capture(handler); long before = app.morphe.extension.shared.innertube.utils.AuthUtils.getContextGeneration();
            app.morphe.extension.shared.innertube.utils.AuthUtils.setPageId("fixture-v61-context");
            assertTrue(app.morphe.extension.shared.innertube.utils.AuthUtils.getContextGeneration() > before); assertFalse(r.open(NativeMenuRegistryTest.ID, sender));
            r.capture(handler); app.morphe.extension.shared.innertube.utils.AuthUtils.setIncognitoStatus(true);
            assertFalse(r.open(NativeMenuRegistryTest.ID, sender)); assertEquals(0, handler.opens);
        } finally {
            app.morphe.extension.shared.innertube.utils.AuthUtils.setPageId(originalPage);
            app.morphe.extension.shared.innertube.utils.AuthUtils.setIncognitoStatus(originalIncognito);
        }
    }
}
