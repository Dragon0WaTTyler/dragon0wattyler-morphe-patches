/* SPDX-License-Identifier: GPL-3.0-only
 * Independent Subscription Groups groups.6, modified 2026-11-02. See LICENSE/NOTICE.
 */
package app.morphe.extension.youtube.groups;

import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import java.lang.ref.WeakReference;
import java.util.concurrent.atomic.AtomicBoolean;
import app.morphe.extension.shared.Utils;
import app.morphe.extension.shared.Logger;
import app.morphe.extension.shared.innertube.utils.AuthUtils;
import app.morphe.extension.youtube.series.PlaybackBridge;
import app.morphe.extension.youtube.patches.VideoInformation;
import app.morphe.extension.youtube.settings.Settings;
import app.morphe.extension.youtube.shared.PlayerType;
import app.morphe.extension.youtube.shared.VideoState;

/** Same-controller observations only; player responses/prefetch and feed thumbnail visibility are never watches. */
@SuppressWarnings("unused")
public final class GroupsWatchedPlayback {
    private static final Handler main = new Handler(Looper.getMainLooper());
    private static final AtomicBoolean queued = new AtomicBoolean();
    private static final WatchCompletionTracker completion = new WatchCompletionTracker();
    private static volatile WeakReference<PlaybackBridge.Source> source = new WeakReference<>(null);
    public static void newVideoStarted(VideoInformation.PlaybackController player) {
        source = new WeakReference<>(player instanceof PlaybackBridge.Source ? (PlaybackBridge.Source) player : null);
        completion.reset();
    }
    public static void videoTimeChanged(long ignored) {
        if (!Settings.POCKETTUBE_GROUPS.get()) { completion.reset(); return; }
        if (queued.compareAndSet(false, true)) main.post(() -> { queued.set(false); sample(); });
    }
    private static void sample() {
        try {
            if (!Settings.POCKETTUBE_GROUPS.get() || AuthUtils.isIncognito()) { completion.reset(); return; }
            PlaybackBridge.Source player = source.get(); if (player == null) { completion.reset(); return; }
            String id = player.patch_seriesTrackerVideoId(); long time = player.patch_seriesTrackerPosition();
            long length = VideoInformation.getVideoLength();
            if (player != source.get() || id == null || !id.equals(player.patch_seriesTrackerVideoId()) || !id.equals(VideoInformation.getVideoId())) { completion.reset(); return; }
            String type = PlayerType.getCurrent().name();
            boolean eligible = VideoState.getCurrent() == VideoState.PLAYING && type.startsWith("WATCH_WHILE_")
                    && !type.contains("DISMISSED") && !VideoInformation.lastVideoIdIsShort() && !VideoInformation.isLiveStream();
            String watched = completion.sample(id, length, time, SystemClock.elapsedRealtime(), VideoInformation.getPlaybackSpeed(), eligible);
            if (watched != null && Utils.getContext() != null) WatchedService.get(Utils.getContext()).mark(watched, true);
        } catch (RuntimeException failure) { completion.reset(); Logger.printException(() -> "Local Groups playback observation failed", failure); }
    }
    static void suppress(String id) { completion.suppress(id); }
    static void reset() { completion.reset(); }
    private GroupsWatchedPlayback() { }
}
