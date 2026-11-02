/* SPDX-License-Identifier: GPL-3.0-only
 * Independent Subscription Groups groups.5, 2026-11-01. See LICENSE and NOTICE.
 */
package app.morphe.extension.youtube.groups;

import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.Looper;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.Spanned;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import app.morphe.extension.shared.patches.LithoRelayoutPatch.LithoViewInterface;
import app.morphe.extension.shared.patches.components.ContextInterface;
import app.morphe.extension.youtube.settings.Settings;
import app.morphe.extension.youtube.shared.NavigationBar;
import com.facebook.litho.TextContent;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Map;
import java.util.WeakHashMap;

/** Exact component path -> mounted Litho root -> owning Subscriptions chrome. No label guessing. */
public final class GroupsNativeChrome implements ViewTreeObserver.OnPreDrawListener, View.OnAttachStateChangeListener {
    private static final Handler main = new Handler(Looper.getMainLooper());
    private static final Map<View, Boolean> filterRoots = new WeakHashMap<>(), carouselRoots = new WeakHashMap<>();
    private static final Map<GroupsNativeChrome, Boolean> activeBindings = new WeakHashMap<>();
    private static final Map<View, DetachedCarousel> detachedCarousels = new WeakHashMap<>();
    private final WeakReference<View> root, content, nativeContent;
    private final Map<View, Integer> changed = new WeakHashMap<>();
    private boolean active, group;
    private ViewTreeObserver observer;
    public static final class FilterMarker { }
    private static final class DetachedCarousel {
        final WeakReference<ViewGroup> parent;
        final int index, visibility;
        final ViewGroup.LayoutParams params;
        DetachedCarousel(ViewGroup parent, View view) {
            this.parent = new WeakReference<>(parent); index = parent.indexOfChild(view);
            visibility = view.getVisibility(); params = view.getLayoutParams();
        }
    }
    GroupsNativeChrome(View nativeRoot, View content) {
        this(nativeRoot, content, null);
    }
    GroupsNativeChrome(View nativeRoot, View content, View nativeContent) {
        root = new WeakReference<>(nativeRoot); this.content = new WeakReference<>(content); this.nativeContent = new WeakReference<>(nativeContent);
        nativeRoot.addOnAttachStateChangeListener(this); if (nativeRoot.isAttachedToWindow()) onViewAttachedToWindow(nativeRoot);
    }
    public static CharSequence onLithoTextLoaded(ContextInterface context, CharSequence original) {
        if (original == null || original.length() == 0 || context == null) return original;
        if (context.patch_getPathBuilder().indexOf("subscriptions_chip_bar") < 0) {
            if (original instanceof Spanned && ((Spanned) original).getSpans(0, original.length(), FilterMarker.class).length > 0) {
                Spannable copy = new SpannableString(original);
                for (FilterMarker marker : ((Spanned) original).getSpans(0, original.length(), FilterMarker.class)) copy.removeSpan(marker);
                return copy;
            }
            return original;
        }
        if (original instanceof Spanned && ((Spanned) original).getSpans(0, original.length(), FilterMarker.class).length > 0) return original;
        Spannable copy = new SpannableString(original); copy.setSpan(new FilterMarker(), 0, original.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE); return copy;
    }
    public static void onLithoTextMounted(Drawable drawable, CharSequence text) {
        if (!(text instanceof Spanned) || ((Spanned) text).getSpans(0, text.length(), FilterMarker.class).length == 0) return;
        // The existing mount callback runs before the drawable receives its callback host.
        main.post(() -> {
            if (drawable instanceof TextContent && !((TextContent) drawable).getTextItems().contains(text)) return;
            if (!(drawable.getCallback() instanceof View)) return;
            View view = (View) drawable.getCallback();
            for (int i = 0; i < 12 && view != null; i++) {
                if (view instanceof LithoViewInterface) { filterRoots.put(view, true); return; }
                view = view.getParent() instanceof View ? (View) view.getParent() : null;
            }
        });
    }
    public static void onCarouselCreated(View view) {
        if (view != null) { carouselRoots.put(view, true); syncCarousels(); }
    }
    public static int carouselWidth(int original) {
        return Settings.POCKETTUBE_GROUPS.get() && NavigationBar.NavigationButton.getSelectedNavigationButton() == NavigationBar.NavigationButton.SUBSCRIPTIONS ? 0 : original;
    }
    void setMode(boolean active, boolean group) {
        this.active = active; this.group = group; activeBindings.put(this, active); syncCarousels(); apply();
    }
    private void apply() {
        View page = root.get(), inner = content.get();
        if (!active || page == null || inner == null) { restore(); return; }
        for (View value : new ArrayList<>(changed.keySet())) {
            if (!GroupChromeBinding.owns(page, value) || (!group && filterRoots.containsKey(value))) restore(value);
        }
        if (group) for (View value : new ArrayList<>(filterRoots.keySet())) hideIfOwned(page, inner, value);
    }
    private void hideIfOwned(View page, View inner, View view) {
        if (view == page || !GroupChromeBinding.owns(page, view) || GroupChromeBinding.owns(view, inner)) return;
        View nativeFeed = nativeContent.get();
        if (GroupChromeBinding.owns(inner, view) && (nativeFeed == null || !GroupChromeBinding.owns(nativeFeed, view))) return;
        // Only collapse a proven row; never a toolbar, page root or the custom content.
        if (!changed.containsKey(view)) changed.put(view, view.getVisibility());
        view.setVisibility(View.GONE);
    }
    private void restore(View view) { Integer prior = changed.remove(view); if (prior != null && view.getVisibility() == View.GONE) view.setVisibility(prior); }
    private void restore() { for (View value : new ArrayList<>(changed.keySet())) restore(value); }
    private static void syncCarousels() {
        for (View carousel : new ArrayList<>(carouselRoots.keySet())) {
            boolean ownedByActivePage = false;
            for (GroupsNativeChrome binding : new ArrayList<>(activeBindings.keySet())) {
                if (!Boolean.TRUE.equals(activeBindings.get(binding))) continue;
                View page = binding.root.get(), inner = binding.content.get();
                DetachedCarousel detached = detachedCarousels.get(carousel);
                View parent = detached == null ? null : detached.parent.get();
                boolean belongs = page != null && (parent == null ? GroupChromeBinding.owns(page, carousel) : GroupChromeBinding.owns(page, parent));
                if (page != null && inner != null && carousel != page && belongs && !GroupChromeBinding.owns(carousel, inner)) {
                    ownedByActivePage = true; break;
                }
            }
            if (ownedByActivePage) detachCarousel(carousel); else restoreCarousel(carousel);
        }
    }
    private static void detachCarousel(View carousel) {
        if (detachedCarousels.containsKey(carousel) || !(carousel.getParent() instanceof ViewGroup)) return;
        ViewGroup parent = (ViewGroup) carousel.getParent(); detachedCarousels.put(carousel, new DetachedCarousel(parent, carousel));
        parent.removeView(carousel);
    }
    private static void restoreCarousel(View carousel) {
        DetachedCarousel detached = detachedCarousels.remove(carousel); if (detached == null) return;
        ViewGroup parent = detached.parent.get();
        if (parent != null && carousel.getParent() == null) {
            parent.addView(carousel, Math.max(0, Math.min(detached.index, parent.getChildCount())), detached.params);
            carousel.setVisibility(detached.visibility);
        }
    }
    void release() {
        activeBindings.remove(this); syncCarousels(); restore(); View view = root.get(); if (view != null) view.removeOnAttachStateChangeListener(this);
        if (observer != null && observer.isAlive()) observer.removeOnPreDrawListener(this); observer = null;
    }
    public boolean onPreDraw() { apply(); return true; }
    public void onViewAttachedToWindow(View view) { if (observer != null && observer.isAlive()) observer.removeOnPreDrawListener(this); observer = view.getViewTreeObserver(); observer.addOnPreDrawListener(this); activeBindings.put(this, active); syncCarousels(); apply(); }
    public void onViewDetachedFromWindow(View view) { activeBindings.put(this, false); syncCarousels(); restore(); if (observer != null && observer.isAlive()) observer.removeOnPreDrawListener(this); observer = null; }
}
