/* SPDX-License-Identifier: GPL-3.0-only
 * Independent Subscription Groups V2, 2026-11-01. See LICENSE and NOTICE.
 */
package app.morphe.extension.youtube.groups;

import android.app.Activity;
import android.app.Application;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import app.morphe.extension.shared.Logger;
import app.morphe.extension.youtube.shared.NavigationBar;
import app.morphe.extension.youtube.shared.PlayerType;

/** Injection boundary scoped by the native browse ID and fragment identity, not by view text. */
@SuppressWarnings("unused")
public final class GroupsSubscriptions {
    private static final List<WeakReference<SubscriptionsPage>> pages = new ArrayList<>();
    private static final Handler main = new Handler(Looper.getMainLooper());
    private static boolean initialized;
    public static View wrapBrowseView(Object fragment, String browseId, View original) {
        try {
            if (!("FEsubscriptions".equals(browseId) || GroupBackup.isChannelId(browseId))
                    || original == null || original.getParent() != null || original instanceof SubscriptionsPage
                    || GroupUi.activity(original.getContext()) == null) return original;
            initialize((Application) original.getContext().getApplicationContext());
            GroupsNativeVideoMenu.host(GroupUi.activity(original.getContext()));
            SubscriptionsPage page = new SubscriptionsPage(original, fragment, browseId);
            pages.add(new WeakReference<>(page)); return page;
        } catch (RuntimeException failure) {
            Logger.printException(() -> "Subscription groups browse wrapper failed", failure); return original;
        }
    }
    public static void onBrowsePageChanged(Object fragment, String id) {
        main.post(() -> each(page -> { if (page.owner.get() == fragment) page.pageChanged(id); }));
    }
    public static void onSearchShown() { main.post(() -> each(SubscriptionsPage::leave)); }
    public static void onChromeReady(Object fragment, View chrome) {
        if (chrome == null) return;
        // Only the content created by this onCreateView owns its returned chrome.
        try { for (int i = pages.size() - 1; i >= 0; i--) {
            SubscriptionsPage page = pages.get(i).get();
            if (page != null && page.owner.get() == fragment && GroupChromeBinding.owns(chrome, page)) { page.chromeReady(chrome); break; }
        } } catch (RuntimeException failure) { Logger.printException(() -> "Group page chrome binding failed", failure); }
    }
    static void choose(String group) { each(page -> { if (page.isShown() && page.isSubscriptions()) page.select(group); }); }
    private static void initialize(Application app) {
        if (initialized) return; initialized = true;
        PlayerType.getOnChange().addObserver(type -> main.post(() -> each(SubscriptionsPage::refresh)));
        NavigationBar.addOnNavigationButtonChangedListener(button -> main.post(() -> each(page -> {
            if (button == NavigationBar.NavigationButton.SUBSCRIPTIONS) page.enter(); else page.leave();
        })));
        app.registerActivityLifecycleCallbacks(new Application.ActivityLifecycleCallbacks() {
            public void onActivityResumed(Activity activity) { each(page -> { if (GroupUi.activity(page.getContext()) == activity) { GroupsNativeVideoMenu.host(activity); page.activityResumed(true); } }); }
            public void onActivityPaused(Activity activity) { GroupsNativeVideoMenu.pause(activity); each(page -> { if (GroupUi.activity(page.getContext()) == activity) page.activityResumed(false); }); }
            public void onActivityCreated(Activity activity, Bundle state) { }
            public void onActivityStarted(Activity activity) { }
            public void onActivityStopped(Activity activity) { }
            public void onActivitySaveInstanceState(Activity activity, Bundle state) { }
            public void onActivityDestroyed(Activity activity) { GroupsNativeVideoMenu.pause(activity); each(page -> { if (GroupUi.activity(page.getContext()) == activity) page.pause(); }); }
        });
    }
    private static void each(java.util.function.Consumer<SubscriptionsPage> action) {
        pages.removeIf(reference -> reference.get() == null);
        for (WeakReference<SubscriptionsPage> reference : new ArrayList<>(pages)) {
            SubscriptionsPage page = reference.get(); if (page != null) action.accept(page);
        }
    }
    private GroupsSubscriptions() {}
}
