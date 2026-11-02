/* SPDX-License-Identifier: GPL-3.0-only
 * Independent Subscription Groups groups.6.1, modified 2026-11-02. See LICENSE/NOTICE.
 */
package app.morphe.extension.youtube.groups;

import android.app.Activity;
import android.os.Looper;
import android.os.SystemClock;
import android.view.View;
import java.lang.ref.WeakReference;
import app.morphe.extension.shared.innertube.utils.AuthUtils;
import app.morphe.extension.youtube.settings.Settings;

/** Calls the original native tap handler. YouTube/Morphe own rendering, dismissal and every native action. */
public final class GroupsNativeVideoMenu {
    private static WeakReference<Activity> activity = new WeakReference<>(null);
    private static long hostGeneration;
    private static boolean establishedHost;
    private static Pending pending;
    private static WeakReference<java.util.Map<?, ?>> verifiedMap = new WeakReference<>(null);
    private static String verifiedId;
    private static final class Pending {
        final WeakReference<View> sender;
        final String id;
        final long host, account, at;
        boolean blocked;
        Pending(View sender, String id) {
            this.sender = new WeakReference<>(sender); this.id = id; host = hostGeneration;
            account = AuthUtils.getContextGeneration(); at = SystemClock.uptimeMillis();
        }
    }
    private static final NativeMenuRegistry registry = new NativeMenuRegistry(SystemClock::uptimeMillis, () ->
            Settings.POCKETTUBE_GROUPS.get() && !AuthUtils.isIncognito()
                    ? new NativeMenuRegistry.Scope(hostGeneration, AuthUtils.getContextGeneration()) : null);
    static void host(Activity host) {
        if (activity.get() == host) return;
        // Native feed handlers can be built before Groups first wraps Subscriptions. Keep those
        // same-account, weak capabilities through the initial host bind; later host changes clear them.
        boolean firstHost = !establishedHost && activity.get() == null && host != null;
        establishedHost = true;
        if (!firstHost) { registry.clear(); pending = null; verifiedId = null; verifiedMap.clear(); hostGeneration++; }
        activity = new WeakReference<>(host);
    }
    static void pause(Activity host) { if (activity.get() == host) host(null); }
    /** Native constructor injection. Does not modify or consume any native command. */
    public static void capture(NativeMenuRegistry.Handler handler) {
        // The native UI bindings used by this bridge must have been constructed on the UI thread.
        if (Looper.myLooper() == Looper.getMainLooper()) registry.capture(handler);
    }
    static boolean open(View sender, GroupVideo video) {
        return open(sender, video, registry);
    }
    static boolean open(View sender, GroupVideo video, NativeMenuRegistry capabilities) {
        Activity host = activity.get();
        if (Looper.myLooper() != Looper.getMainLooper() || host == null || host.isFinishing() || host.isDestroyed()
                || !sender.isAttachedToWindow() || !sender.isShown() || GroupUi.activity(sender.getContext()) != host) return false;
        pending = new Pending(sender, video.id);
        if (capabilities.open(video.id, sender) && pending != null && !pending.blocked) return true;
        pending = null; return false;
    }
    /** Runs only on the actual native feed-menu command entry, before shared flyout extraction. */
    public static boolean beforeNativeMenu(Object nativeCommand, java.util.Map<?, ?> map) {
        verifiedId = null; verifiedMap.clear();
        Pending selection = pending;
        if (Looper.myLooper() != Looper.getMainLooper() || selection == null || map == null
                || selection.sender.get() == null || map.get("com.google.android.libraries.youtube.rendering.elements.sender_view") != selection.sender.get()) return true;
        try {
            if (!valid(selection) || !(nativeCommand instanceof com.google.protobuf.MessageLite)) { selection.blocked = true; return false; }
            String id = NativeMenuIdentity.payloadVideo(((com.google.protobuf.MessageLite) nativeCommand).toByteArray());
            if (!selection.id.equals(id)) { selection.blocked = true; return false; }
            verifiedId = id; verifiedMap = new WeakReference<>(map);
            return true;
        } catch (RuntimeException unavailable) { selection.blocked = true; return false; }
    }
    /** Supplies Morphe's queue hooks with exact identity only for our verified command/map pair. */
    public static String identityFor(java.util.Map<?, ?> map) {
        Pending selection = pending;
        if (map != verifiedMap.get() || verifiedId == null || selection == null || !valid(selection)) return null;
        String result = verifiedId; verifiedId = null; verifiedMap.clear(); return result;
    }
    private static boolean valid(Pending selection) {
        long age = SystemClock.uptimeMillis() - selection.at;
        View sender = selection.sender.get();
        return age >= 0 && age <= 1500 && sender != null && sender.isAttachedToWindow() && sender.isShown() && selection.host == hostGeneration
                && selection.account == AuthUtils.getContextGeneration() && !AuthUtils.isIncognito() && activity.get() != null;
    }
    private GroupsNativeVideoMenu() { }
}
