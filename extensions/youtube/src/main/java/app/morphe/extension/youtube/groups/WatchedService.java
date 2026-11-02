/* SPDX-License-Identifier: GPL-3.0-only
 * Independent Subscription Groups groups.6, modified 2026-11-02. See LICENSE/NOTICE.
 */
package app.morphe.extension.youtube.groups;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import java.lang.ref.WeakReference;
import java.util.Set;
import java.util.LinkedHashSet;
import java.util.Collections;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.CopyOnWriteArrayList;
import app.morphe.extension.shared.Logger;

final class WatchedService implements WatchedState {
    private static WatchedService instance;
    static synchronized WatchedService get(Context context) { if (instance == null) instance = new WatchedService(context.getApplicationContext()); return instance; }
    private final WatchedHistory store;
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());
    private volatile Set<String> ids = Set.of();
    private volatile boolean ready;
    private final CopyOnWriteArrayList<WeakReference<Runnable>> observers = new CopyOnWriteArrayList<>();
    private WatchedService(Context context) {
        store = new WatchedHistory(context.getFilesDir());
        worker.execute(() -> { try { ids = store.load(); } catch (Exception failure) { Logger.printException(() -> "Could not read local group watched history", failure); } finally { ready = true; notifyChanged(); } });
    }
    public boolean ready() { return ready; }
    public boolean contains(String id) { return ids.contains(id); }
    public void observe(Runnable listener) { for (WeakReference<Runnable> ref : observers) if (ref.get() == listener) return; observers.add(new WeakReference<>(listener)); }
    public void remove(Runnable listener) { observers.removeIf(ref -> ref.get() == null || ref.get() == listener); }
    private void notifyChanged() { main.post(() -> { for (WeakReference<Runnable> ref : observers) { Runnable value = ref.get(); if (value != null) value.run(); } }); }
    public void mark(String id, boolean watched) {
        if (!WatchedHistory.valid(id)) return;
        if (!watched) GroupsWatchedPlayback.suppress(id);
        worker.execute(() -> { Set<String> next = new LinkedHashSet<>(ids); if (watched ? next.add(id) : next.remove(id)) save(next); });
    }
    public void clear() { GroupsWatchedPlayback.reset(); worker.execute(() -> save(Set.of())); }
    private void save(Set<String> next) {
        try { store.save(next); ids = Collections.unmodifiableSet(new LinkedHashSet<>(next)); notifyChanged(); }
        catch (Exception failure) { Logger.printException(() -> "Could not save local group watched history", failure); }
    }
}
