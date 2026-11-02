/* SPDX-License-Identifier: GPL-3.0-only
 * Independent Subscription Groups groups.4, 2026-11-01. See LICENSE and NOTICE.
 */
package app.morphe.extension.youtube.groups;

import android.os.Build;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.WindowInsets;
import java.lang.ref.WeakReference;
import java.util.ArrayDeque;
import app.morphe.extension.youtube.shared.NavigationBar;

/** Observes only the returned page chrome. Never changes window flags/decor or native inset listeners. */
final class GroupChromeBinding implements ViewTreeObserver.OnPreDrawListener, View.OnAttachStateChangeListener {
    private final WeakReference<View> root;
    private WeakReference<View> toolbarRef = new WeakReference<>(null);
    private ViewTreeObserver observer;
    private boolean active;
    private int appliedTop;
    private String geometry = "";
    private final int[] position = new int[2];
    GroupChromeBinding(View root) {
        this.root = new WeakReference<>(root); root.addOnAttachStateChangeListener(this);
        if (root.isAttachedToWindow()) onViewAttachedToWindow(root);
    }
    void setActive(boolean value) {
        if (active == value) return; active = value; geometry = "";
        if (!active) restore(); else onPreDraw();
    }
    void release() {
        restore(); View view = root.get(); if (view != null) view.removeOnAttachStateChangeListener(this);
        if (observer != null && observer.isAlive()) observer.removeOnPreDrawListener(this); observer = null;
    }
    public void onViewAttachedToWindow(View view) {
        if (observer != null && observer.isAlive()) observer.removeOnPreDrawListener(this);
        observer = view.getViewTreeObserver(); observer.addOnPreDrawListener(this); geometry = "";
        toolbarRef.clear();
    }
    public void onViewDetachedFromWindow(View view) {
        restore(); if (observer != null && observer.isAlive()) observer.removeOnPreDrawListener(this); observer = null; geometry = "";
    }
    public boolean onPreDraw() {
        View page = root.get();
        if (!active || page == null || !page.isShown() || !page.isAttachedToWindow()) return true;
        WindowInsets insets = page.getRootWindowInsets(); if (insets == null) return true;
        int safeTop = insets.getSystemWindowInsetTop();
        if (Build.VERSION.SDK_INT >= 30) safeTop = insets.getInsets(WindowInsets.Type.statusBars() | WindowInsets.Type.displayCutout()).top;
        View toolbar = toolbarRef.get();
        if (toolbar == null || !owns(page, toolbar)) { toolbar = findToolbar(page); toolbarRef = new WeakReference<>(toolbar); }
        if (toolbar == null || toolbar.getHeight() == 0 || !toolbar.isShown()) return true;
        // Do not follow native collapsing/translated toolbars while the page scrolls.
        StringBuilder spacing = new StringBuilder().append(page.getPaddingTop() - appliedTop);
        for (View v = toolbar; v != null && v != page; v = v.getParent() instanceof View ? (View) v.getParent() : null) {
            if (v.getTranslationY() != 0 || v.getScrollY() != 0) return true;
            spacing.append(':').append(v.getPaddingTop());
            if (v.getLayoutParams() instanceof ViewGroup.MarginLayoutParams) spacing.append(':').append(((ViewGroup.MarginLayoutParams) v.getLayoutParams()).topMargin);
        }
        String next = page.getWidth() + ":" + page.getHeight() + ":" + safeTop + ":" + page.getResources().getConfiguration().orientation + ":" + spacing;
        if (next.equals(geometry)) return true;
        toolbar.getLocationOnScreen(position);
        int baseToolbarTop = position[1] + toolbar.getPaddingTop() - appliedTop;
        if (baseToolbarTop < 0) return true; // A natively collapsed bar is intentionally off screen.
        geometry = next;
        int required = missingTopInset(safeTop, baseToolbarTop);
        if (required != appliedTop) {
            page.setPadding(page.getPaddingLeft(), Math.max(0, page.getPaddingTop() - appliedTop + required), page.getPaddingRight(), page.getPaddingBottom());
            appliedTop = required; return false; // Lay out before drawing, preventing one overlapping frame.
        }
        return true;
    }
    private void restore() {
        View view = root.get(); if (view != null && appliedTop != 0)
            view.setPadding(view.getPaddingLeft(), Math.max(0, view.getPaddingTop() - appliedTop), view.getPaddingRight(), view.getPaddingBottom());
        appliedTop = 0;
    }
    static int missingTopInset(int safeTop, int toolbarTop) { return Math.max(0, safeTop - toolbarTop); }
    static boolean owns(View root, View content) {
        for (View view = content; view != null; view = view.getParent() instanceof View ? (View) view.getParent() : null)
            if (view == root) return true;
        return false;
    }
    private static View findToolbar(View root) {
        ArrayDeque<View> queue = new ArrayDeque<>(); queue.add(root); int seen = 0;
        while (!queue.isEmpty() && seen++ < 256) {
            View view = queue.removeFirst();
            if (view instanceof NavigationBar.AppCompatToolbarPatchInterface) return view;
            if (view instanceof ViewGroup) {
                ViewGroup group = (ViewGroup) view;
                for (int i = 0; i < group.getChildCount() && queue.size() < 256; i++) queue.addLast(group.getChildAt(i));
            }
        }
        return null;
    }
}
