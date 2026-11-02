/* SPDX-License-Identifier: GPL-3.0-only
 * Independent Subscription Groups V2, 2026-11-01. See LICENSE and NOTICE.
 */
package app.morphe.extension.youtube.groups;

import android.content.Context;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.ref.WeakReference;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import app.morphe.extension.shared.Utils;
import static app.morphe.extension.shared.StringRef.str;

/** Serialized disk transactions with immutable snapshots and weak main-thread subscriptions. */
public final class GroupService {
    public interface Listener { void changed(); }
    public interface Change { GroupLibrary apply(GroupLibrary previous) throws Exception; }
    private static GroupService instance;
    public static synchronized GroupService get(Context context) {
        if (instance == null) instance = new GroupService(context.getApplicationContext());
        return instance;
    }
    private final Context context;
    private final GroupLibraryStore store;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final CopyOnWriteArrayList<WeakReference<Listener>> listeners = new CopyOnWriteArrayList<>();
    private volatile GroupLibrary library = GroupLibrary.empty();
    private volatile boolean ready;
    private volatile String error = "";
    private GroupService(Context context) {
        this.context = context; store = new GroupLibraryStore(context.getFilesDir());
        reload();
    }
    public GroupLibrary library() { return library; }
    public boolean ready() { return ready; }
    public String error() { return error; }
    public void reload() {
        worker.execute(() -> {
            try { library = store.load(); ready = true; error = ""; }
            catch (Exception failure) { error = str("morphe_pockettube_load_failed"); }
            publish();
        });
    }
    public void observe(Listener listener) {
        for (WeakReference<Listener> reference : listeners) if (reference.get() == listener) return;
        listeners.add(new WeakReference<>(listener));
    }
    public void remove(Listener listener) { listeners.removeIf(reference -> reference.get() == null || reference.get() == listener); }
    private void publish() {
        main.post(() -> {
            for (WeakReference<Listener> reference : listeners) {
                Listener listener = reference.get(); if (listener != null) listener.changed(); else listeners.remove(reference);
            }
        });
    }
    public void change(Change change) { change(change, null); }
    public void change(Change change, Runnable success) {
        worker.execute(() -> {
            try {
                if (!ready) throw new IllegalStateException(str("morphe_pockettube_load_failed"));
                GroupLibrary next = change.apply(library);
                if (next != library) commit(next);
                if (success != null) main.post(success);
            } catch (Exception failure) { failure(failure); }
        });
    }
    private void commit(GroupLibrary next) throws Exception {
        store.save(next); library = next; ready = true; error = ""; publish();
    }
    public void contentFilter(String groupId, GroupContentFilter value) {
        change(previous -> previous.find(groupId) == null || !previous.preferences.enabled("remember_content") || value.name().equalsIgnoreCase(previous.preferences.get(GroupPreferences.CONTENT_PREFIX + groupId)) ? previous
                : previous.preferences(previous.preferences.with(GroupPreferences.CONTENT_PREFIX + groupId, value.name().toLowerCase(java.util.Locale.ROOT))));
    }
    public void preference(String key, String value) {
        change(previous -> value.equals(previous.preferences.get(key)) ? previous : previous.preferences(previous.preferences.with(key, value)));
    }
    public void preview(Uri uri, Consumer<GroupLibraryCodec.Import> result) {
        worker.execute(() -> {
            try (InputStream in = context.getContentResolver().openInputStream(uri)) {
                GroupLibraryCodec.Import data = GroupLibraryCodec.parse(in);
                main.post(() -> result.accept(data));
            } catch (Exception failure) { failure(failure); main.post(() -> result.accept(null)); }
        });
    }
    public void restore(GroupLibraryCodec.Import data, boolean merge, Consumer<Boolean> complete) {
        worker.execute(() -> {
            try {
                if (merge && !ready) throw new IllegalStateException(str("morphe_pockettube_load_failed"));
                commit(library.importLibrary(data.library, merge, data.fullBackup));
                main.post(() -> complete.accept(true));
            } catch (Exception failure) { failure(failure); main.post(() -> complete.accept(false)); }
        });
    }
    public void export(Uri uri, boolean membershipsOnly, Consumer<Boolean> complete) {
        worker.execute(() -> {
            try {
                if (!ready) throw new IllegalStateException(str("morphe_pockettube_load_failed"));
                byte[] data = (membershipsOnly ? GroupLibraryCodec.encodeMemberships(library) : GroupLibraryCodec.encode(library)).getBytes(StandardCharsets.UTF_8);
                try (OutputStream out = context.getContentResolver().openOutputStream(uri, "wt")) {
                    if (out == null) throw new IllegalStateException("Document unavailable");
                    out.write(data); out.flush();
                }
                main.post(() -> complete.accept(true));
            } catch (Exception failure) { failure(failure); main.post(() -> complete.accept(false)); }
        });
    }
    private void failure(Exception failure) {
        main.post(() -> Utils.showToastLong(str("morphe_pockettube_v2_error", failure.getMessage() == null ? "" : failure.getMessage())));
    }
}
