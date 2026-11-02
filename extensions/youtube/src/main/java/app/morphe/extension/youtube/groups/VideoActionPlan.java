/* SPDX-License-Identifier: GPL-3.0-only
 * Independent Subscription Groups groups.6, modified 2026-11-02. See LICENSE/NOTICE.
 */
package app.morphe.extension.youtube.groups;

import java.util.ArrayList;
import java.util.List;

/** Only capabilities backed by actual handlers; no speculative feedback or Premium/download actions. */
public final class VideoActionPlan {
    public enum Action { PLAY, CHANNEL, SHARE, WATCH_LATER, SAVE_PLAYLIST, QUEUE_LAST, MARK_WATCHED, MARK_UNWATCHED }
    public static List<Action> actions(boolean watchLater, boolean playlist, boolean queue, boolean localReady, boolean watched) {
        List<Action> result = new ArrayList<>(List.of(Action.PLAY, Action.CHANNEL));
        if (watchLater) result.add(Action.WATCH_LATER);
        if (playlist) result.add(Action.SAVE_PLAYLIST);
        if (queue) result.add(Action.QUEUE_LAST);
        result.add(Action.SHARE);
        if (localReady) result.add(watched ? Action.MARK_UNWATCHED : Action.MARK_WATCHED);
        return result;
    }
    private VideoActionPlan() { }
}
