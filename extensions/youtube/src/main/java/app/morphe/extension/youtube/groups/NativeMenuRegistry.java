/* SPDX-License-Identifier: GPL-3.0-only
 * Independent Subscription Groups groups.6.1, modified 2026-11-02. See LICENSE/NOTICE.
 */
package app.morphe.extension.youtube.groups;

import java.lang.ref.WeakReference;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

/** Weak, ephemeral native capabilities. No command/account data is persisted. */
public final class NativeMenuRegistry {
    /** Implemented on the app's existing Elements tap handler by the unnamed bytecode patch. */
    public interface Handler {
        byte[] patch_groupsMenuCommand();
        void patch_groupsOpenMenu(Object sender);
    }
    static final class Scope {
        final long host, account;
        Scope(long host, long account) { this.host = host; this.account = account; }
        boolean same(Scope other) { return other != null && host == other.host && account == other.account; }
    }
    private static final long TTL = 60_000;
    private static final class Binding {
        final WeakReference<Handler> handler;
        final byte[] digest;
        final Scope scope;
        final long at;
        Binding(Handler handler, byte[] digest, Scope scope, long at) {
            this.handler = new WeakReference<>(handler); this.digest = digest; this.scope = scope; this.at = at;
        }
    }
    private final LongSupplier clock;
    private final Supplier<Scope> context;
    private final java.util.function.Function<byte[], String> identity;
    private final Map<String, Binding> bindings = new LinkedHashMap<>(32, .75f, true);
    NativeMenuRegistry(LongSupplier clock, Supplier<Scope> context) { this(clock, context, NativeMenuIdentity::video); }
    NativeMenuRegistry(LongSupplier clock, Supplier<Scope> context, java.util.function.Function<byte[], String> identity) {
        this.clock = clock; this.context = context; this.identity = identity;
    }
    synchronized void capture(Handler handler) {
        Scope scope = context.get(); if (scope == null || handler == null) return;
        try {
            byte[] bytes = handler.patch_groupsMenuCommand(); String id = identity.apply(bytes);
            if (!WatchedHistory.valid(id) || !scope.same(context.get())) return;
            bindings.put(id, new Binding(handler, digest(bytes), scope, clock.getAsLong()));
            while (bindings.size() > 128) bindings.remove(bindings.keySet().iterator().next());
        } catch (RuntimeException ignored) { /* A missing native capability never breaks native construction. */ }
    }
    synchronized boolean open(String id, Object sender) {
        if (!WatchedHistory.valid(id) || sender == null) return false;
        Binding binding = bindings.get(id); if (binding == null) return false;
        Handler handler = binding.handler.get(); long age = clock.getAsLong() - binding.at;
        if (handler == null || age < 0 || age > TTL || !binding.scope.same(context.get())) { bindings.remove(id); return false; }
        try {
            byte[] current = handler.patch_groupsMenuCommand();
            if (!id.equals(identity.apply(current)) || !Arrays.equals(binding.digest, digest(current)) || !binding.scope.same(context.get())) {
                bindings.remove(id); return false;
            }
            handler.patch_groupsOpenMenu(sender); return true;
        } catch (RuntimeException failure) { bindings.remove(id); return false; }
    }
    synchronized void clear() { bindings.clear(); }
    private static byte[] digest(byte[] value) {
        try { return MessageDigest.getInstance("SHA-256").digest(value); }
        catch (java.security.NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }
}
