/* SPDX-License-Identifier: GPL-3.0-only
 * Independent Subscription Groups groups.4, 2026-11-01. See LICENSE and NOTICE.
 */
package app.morphe.extension.youtube.groups;

import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/** Automatic whole-group channel coverage. Finishing coverage never means complete upload history. */
public final class RecentFeedSession {
    public static final int MAX_CONCURRENT = 4, MAX_ATTEMPTS = 3;
    public interface Ticket { void cancel(); }
    public interface Scheduler { long now(); Ticket after(long delayMillis, Runnable task); }
    private static final ScheduledExecutorService TIMER = Executors.newSingleThreadScheduledExecutor(task -> {
        Thread thread = new Thread(task, "group-coverage-retries"); thread.setDaemon(true); return thread;
    });
    private static final Scheduler REAL_TIME = new Scheduler() {
        public long now() { return System.currentTimeMillis(); }
        public Ticket after(long delay, Runnable task) { Future<?> future = TIMER.schedule(task, delay, TimeUnit.MILLISECONDS); return () -> future.cancel(false); }
    };
    private static final int WAITING = 0, RUNNING = 1, SUCCESS = 2, FAILED = 3;
    private static final Comparator<GroupVideo> NEWEST = Comparator.comparingLong((GroupVideo v) -> v.published).reversed().thenComparing(v -> v.id);
    private static final class Job {
        final String channel;
        int status = WAITING, attempts;
        long due;
        boolean cached;
        String lastError = "";
        Job(String channel) { this.channel = channel; }
    }
    public static final class State {
        public final List<GroupVideo> videos;
        public final int loadedChannels, totalChannels, failedChannels, remainingChannels;
        public final int pendingChannels, inFlightChannels, retryingChannels, uninspectedChannels, cachedChannels, staleChannels;
        public final long oldestWindowAt, newestWindowAt;
        public final boolean loading, terminal;
        public final List<String> inspectedChannelIds, pendingChannelIds;
        public final Map<String, String> channelFailures;
        public final Map<String, Integer> channelAttempts;
        public final boolean completeHistory = false;
        State(List<GroupVideo> videos, int loaded, int total, int failed, int pending, boolean loading) {
            this(videos, loaded, total, failed, pending, loading, 0, 0, pending, 0, 0, 0, 0);
        }
        State(List<GroupVideo> videos, int loaded, int total, int failed, int pending, boolean loading,
              int inFlight, int retrying, int uninspected, int cached, int stale, long oldest, long newest) {
            this(videos, loaded, total, failed, pending, loading, inFlight, retrying, uninspected, cached, stale, oldest, newest,
                    List.of(), List.of(), Map.of(), Map.of());
        }
        State(List<GroupVideo> videos, int loaded, int total, int failed, int pending, boolean loading,
              int inFlight, int retrying, int uninspected, int cached, int stale, long oldest, long newest,
              List<String> inspected, List<String> pendingIds, Map<String, String> failures, Map<String, Integer> attempts) {
            this.videos = Collections.unmodifiableList(new ArrayList<>(videos)); loadedChannels = loaded; totalChannels = total;
            failedChannels = failed; pendingChannels = remainingChannels = pending; this.loading = loading;
            inFlightChannels = inFlight; retryingChannels = retrying; uninspectedChannels = uninspected;
            cachedChannels = cached; staleChannels = stale; oldestWindowAt = oldest; newestWindowAt = newest;
            terminal = pending == 0;
            inspectedChannelIds = Collections.unmodifiableList(new ArrayList<>(inspected));
            pendingChannelIds = Collections.unmodifiableList(new ArrayList<>(pendingIds));
            channelFailures = Collections.unmodifiableMap(new LinkedHashMap<>(failures));
            channelAttempts = Collections.unmodifiableMap(new LinkedHashMap<>(attempts));
        }
    }
    private final FeedSource source;
    private final ExecutorService worker;
    private final Executor callback;
    private final Consumer<State> listener;
    private final Scheduler scheduler;
    private final LinkedHashMap<String, Job> jobs = new LinkedHashMap<>();
    private final Map<String, Future<?>> futures = new LinkedHashMap<>();
    private final Map<String, FeedSource.Window> windows = new LinkedHashMap<>();
    private Ticket wake;
    private int generation;
    private boolean active, force;

    public RecentFeedSession(FeedSource source, ExecutorService worker, Executor callback, Consumer<State> listener) {
        this(source, worker, callback, listener, REAL_TIME);
    }
    public RecentFeedSession(FeedSource source, ExecutorService worker, Executor callback, Consumer<State> listener, Scheduler scheduler) {
        this.source = source; this.worker = worker; this.callback = callback; this.listener = listener; this.scheduler = scheduler;
    }
    public synchronized void start(List<String> channels, boolean refresh) {
        LinkedHashSet<String> unique = new LinkedHashSet<>(channels);
        for (String id : unique) if (!GroupBackup.isChannelId(id)) throw new IllegalArgumentException("Invalid group channel");
        boolean preserve = refresh && unique.equals(jobs.keySet());
        stop(); jobs.clear(); if (!preserve) windows.clear();
        for (String channel : unique) {
            FeedSource.Window memory = source.memory(channel);
            FeedSource.Window previous = windows.get(channel);
            if (memory != null && (previous == null || previous.fetchedAt <= memory.fetchedAt)) windows.put(channel, memory);
            Job job = new Job(channel); job.cached = windows.containsKey(channel);
            if (!refresh && memory != null && memory.fresh(scheduler.now())) job.status = SUCCESS;
            jobs.put(channel, job);
        }
        force = refresh; active = true; emit(); drain();
    }
    public synchronized void refresh() { start(new ArrayList<>(jobs.keySet()), true); }
    public synchronized void cancel() { pause(); }
    public synchronized void pause() {
        if (!active) return;
        stop();
        for (Job job : jobs.values()) if (job.status == RUNNING) {
            job.status = WAITING; job.attempts = Math.max(0, job.attempts - 1);
        }
    }
    public synchronized void resume() {
        if (active) return; active = true;
        // Keep already displayed windows, but automatically revisit stale successful channels.
        long now = scheduler.now();
        for (Job job : jobs.values()) {
            FeedSource.Window window = windows.get(job.channel);
            if (job.status == SUCCESS && (window == null || !window.fresh(now))) { job.status = WAITING; job.attempts = 0; job.due = 0; job.cached = window != null; }
        }
        emit(); drain();
    }
    /** Optional explicit retry after automatic attempts are exhausted. Other pending channels continue regardless. */
    public synchronized void retry() {
        for (Job job : jobs.values()) if (job.status == FAILED) { job.status = WAITING; job.attempts = 0; job.due = 0; }
        active = true; emit(); drain();
    }
    private void stop() {
        generation++; active = false; if (wake != null) wake.cancel(); wake = null;
        for (Future<?> future : futures.values()) future.cancel(true); futures.clear();
    }
    private synchronized void drain() {
        if (!active) return;
        if (wake != null) wake.cancel(); wake = null;
        long now = scheduler.now(), cooldown = source.delayMillis(now), earliest = Long.MAX_VALUE;
        if (cooldown > 0) { schedule(cooldown); return; }
        int inFlight = 0; for (Job job : jobs.values()) if (job.status == RUNNING) inFlight++;
        for (Job job : jobs.values()) {
            if (job.status != WAITING) continue;
            if (job.due > now) { earliest = Math.min(earliest, job.due); continue; }
            if (inFlight >= MAX_CONCURRENT) continue;
            job.status = RUNNING; job.attempts++; inFlight++;
            int token = generation; boolean refresh = force;
            try { futures.put(job.channel, worker.submit(() -> request(token, job.channel, refresh))); }
            catch (RuntimeException rejected) { job.status = FAILED; inFlight--; }
        }
        emit();
        if (earliest != Long.MAX_VALUE) schedule(Math.max(1, earliest - now));
    }
    private void schedule(long delay) {
        int token = generation;
        wake = scheduler.after(delay, () -> { synchronized (this) { if (token == generation) { wake = null; drain(); } } });
    }
    private void request(int token, String channel, boolean refresh) {
        try {
            FeedSource.Window cached = null;
            try { cached = source.cached(channel); } catch (Exception ignored) { /* A broken cache must not block network coverage. */ }
            if (cached != null) {
                cached(token, channel, cached);
                if (!refresh && cached.fresh(scheduler.now())) {
                    complete(token, channel, cached.videos, null, cached.fetchedAt, true); return;
                }
            }
            if (Thread.currentThread().isInterrupted()) return;
            List<GroupVideo> videos = source.recent(channel, refresh || cached != null);
            complete(token, channel, videos, null, scheduler.now(), false);
        } catch (Exception failure) { complete(token, channel, null, failure, 0, false); }
    }
    private synchronized void cached(int token, String channel, FeedSource.Window window) {
        if (token != generation || !active) return;
        FeedSource.Window current = windows.get(channel);
        if (current == null || current.fetchedAt <= window.fetchedAt) windows.put(channel, window);
        jobs.get(channel).cached = true; emit();
    }
    private synchronized void complete(int token, String channel, List<GroupVideo> result, Exception failure, long fetchedAt, boolean cached) {
        if (token != generation || !active) return;
        Job job = jobs.get(channel); if (job == null || job.status != RUNNING) return;
        futures.remove(channel);
        if (result != null) {
            List<GroupVideo> valid = new ArrayList<>(); for (GroupVideo video : result) if (video.channelId.equals(channel)) valid.add(video);
            windows.put(channel, new FeedSource.Window(valid, fetchedAt)); job.status = SUCCESS; job.cached = cached;
        } else {
            job.lastError = failure == null || failure.getMessage() == null ? "Channel unavailable" : failure.getMessage();
            boolean retryable = !(failure instanceof FeedSource.Failure) || ((FeedSource.Failure) failure).retryable;
            if (retryable && job.attempts < MAX_ATTEMPTS) {
                long requested = failure instanceof FeedSource.Failure ? ((FeedSource.Failure) failure).retryAfterMillis : 0;
                job.status = WAITING; job.due = scheduler.now() + Math.max(1000L << (job.attempts - 1), requested);
            } else job.status = FAILED;
        }
        emit(); drain();
    }
    private void emit() {
        Map<String, GroupVideo> aggregate = new LinkedHashMap<>();
        int success = 0, failed = 0, running = 0, retrying = 0, uninspected = 0, cached = 0, stale = 0;
        long oldest = Long.MAX_VALUE, newest = 0, now = scheduler.now();
        List<String> inspected = new ArrayList<>(), pendingIds = new ArrayList<>();
        Map<String, String> failures = new LinkedHashMap<>(); Map<String, Integer> attempts = new LinkedHashMap<>();
        for (Job job : jobs.values()) {
            attempts.put(job.channel, job.attempts);
            if (job.status == SUCCESS) inspected.add(job.channel);
            else if (job.status == FAILED) failures.put(job.channel, job.lastError);
            else pendingIds.add(job.channel);
            if (job.status == SUCCESS) success++;
            if (job.status == FAILED) failed++;
            if (job.status == RUNNING) running++;
            if (job.status == WAITING && job.attempts > 0) retrying++;
            if (job.status == WAITING && job.attempts == 0) uninspected++;
            FeedSource.Window window = windows.get(job.channel); if (window == null) continue;
            if (job.cached) cached++; if (!window.fresh(now)) stale++;
            oldest = Math.min(oldest, window.fetchedAt); newest = Math.max(newest, window.fetchedAt);
            for (GroupVideo video : window.videos) {
                if (!video.channelId.equals(job.channel)) continue;
                GroupVideo previous = aggregate.get(video.id);
                if (previous == null || video.published > previous.published || (video.published == previous.published && video.channelId.compareTo(previous.channelId) < 0)) aggregate.put(video.id, video);
            }
        }
        List<GroupVideo> videos = new ArrayList<>(aggregate.values()); videos.sort(NEWEST);
        int pending = jobs.size() - success - failed;
        State state = new State(videos, success, jobs.size(), failed, pending, active && pending > 0,
                running, retrying, uninspected, cached, stale, oldest == Long.MAX_VALUE ? 0 : oldest, newest, inspected, pendingIds, failures, attempts);
        int token = generation; callback.execute(() -> { synchronized (this) { if (token == generation) listener.accept(state); } });
    }
    public static List<GroupVideo> visible(State state, String sort, String range, long now, ZoneId zone) {
        long since = Long.MIN_VALUE;
        if (range.equals("today")) since = Instant.ofEpochMilli(now).atZone(zone).toLocalDate().atStartOfDay(zone).toInstant().toEpochMilli();
        if (range.equals("week")) since = now - 7L * 86400000;
        if (range.equals("month")) since = now - 30L * 86400000;
        List<GroupVideo> list = new ArrayList<>(); for (GroupVideo video : state.videos) if (video.published >= since) list.add(video);
        Comparator<GroupVideo> order = NEWEST;
        if (sort.equals("oldest")) order = Comparator.comparingLong((GroupVideo v) -> v.published).thenComparing(v -> v.id);
        if (sort.equals("views")) order = Comparator.comparingLong((GroupVideo v) -> v.views).reversed().thenComparing(NEWEST);
        list.sort(order); return list;
    }
}
