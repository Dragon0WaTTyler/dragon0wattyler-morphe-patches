/* SPDX-License-Identifier: GPL-3.0-only
 * Independent Subscription Groups groups.6, modified 2026-11-02. See LICENSE/NOTICE.
 */
package app.morphe.extension.youtube.groups;

import android.content.Context;
import android.content.Intent;
import android.view.View;
import android.widget.PopupMenu;
import java.util.List;
import app.morphe.extension.shared.Utils;
import app.morphe.extension.shared.Logger;
import app.morphe.extension.youtube.patches.SaveToWatchLaterPatch;
import app.morphe.extension.youtube.patches.queue.NativePlaylistQueuePatch;
import static app.morphe.extension.shared.StringRef.str;
import static app.morphe.extension.youtube.groups.VideoActionPlan.Action;

/** Stable-ID fallback menu. Native account actions are exposed only while their real adapters are ready. */
final class GroupVideoActions {
    interface NativeActions { boolean watchLater(); boolean playlist(); boolean queue(String id); boolean invoke(Action action, String id); }
    private static final NativeActions NATIVE = new NativeActions() {
        public boolean watchLater() { return SaveToWatchLaterPatch.canSaveGroupVideo(); }
        public boolean playlist() { return NativePlaylistQueuePatch.canSaveGroupVideo(); }
        public boolean queue(String id) { return NativePlaylistQueuePatch.canAppendGroupVideo(id); }
        public boolean invoke(Action action, String id) {
            if (action == Action.WATCH_LATER && watchLater()) { SaveToWatchLaterPatch.saveVideo(id); return true; }
            if (action == Action.SAVE_PLAYLIST) return NativePlaylistQueuePatch.saveGroupVideo(id);
            if (action == Action.QUEUE_LAST) return NativePlaylistQueuePatch.appendGroupVideo(id);
            return false;
        }
    };
    interface NativeMenu { boolean open(View sender, GroupVideo video); }
    static void show(Context context, View anchor, GroupVideo video, WatchedState watched) {
        show(context, anchor, video, watched, GroupsNativeVideoMenu::open, () -> showLocal(context, anchor, video, watched));
    }
    static void show(Context context, View anchor, GroupVideo video, WatchedState watched, NativeMenu nativeMenu, Runnable fallback) {
        if (!nativeMenu.open(anchor, video)) fallback.run();
    }
    static void showLocal(Context context, View anchor, GroupVideo video, WatchedState watched) { menu(context, anchor, video, watched, NATIVE).show(); }
    static PopupMenu menu(Context context, View anchor, GroupVideo video, WatchedState watched, NativeActions nativeActions) {
        PopupMenu menu = new PopupMenu(context, anchor);
        List<Action> actions = VideoActionPlan.actions(nativeActions.watchLater(), nativeActions.playlist(), nativeActions.queue(video.id), watched.ready(), watched.contains(video.id));
        for (Action action : actions) {
            String key = action == Action.PLAY ? "morphe_pockettube_ux_open_video" : action == Action.CHANNEL ? "morphe_pockettube_ux_open_channel"
                    : "morphe_pockettube_g6_action_" + action.name().toLowerCase(java.util.Locale.ROOT);
            menu.getMenu().add(0, action.ordinal() + 1, action.ordinal(), str(key)).setOnMenuItemClickListener(item -> {
                try {
                    switch (action) {
                        case PLAY: GroupUi.video(context, video.id); break;
                        case CHANNEL: GroupUi.channel(context, video.channelId); break;
                        case SHARE:
                            context.startActivity(Intent.createChooser(new Intent(Intent.ACTION_SEND).setType("text/plain")
                                    .putExtra(Intent.EXTRA_TEXT, "https://www.youtube.com/watch?v=" + video.id), str("morphe_pockettube_g6_action_share"))); break;
                        case MARK_WATCHED: watched.mark(video.id, true); break;
                        case MARK_UNWATCHED: watched.mark(video.id, false); break;
                        default: if (!nativeActions.invoke(action, video.id)) Utils.showToastShort(str("morphe_pockettube_g6_action_unavailable"));
                    }
                } catch (RuntimeException failure) { Logger.printException(() -> "Group video action failed", failure); Utils.showToastShort(str("morphe_pockettube_g6_action_unavailable")); }
                return true;
            });
        }
        return menu;
    }
    private GroupVideoActions() { }
}
