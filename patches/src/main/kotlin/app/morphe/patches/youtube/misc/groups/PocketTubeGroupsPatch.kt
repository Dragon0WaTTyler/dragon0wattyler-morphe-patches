/* SPDX-License-Identifier: GPL-3.0-only
 * Independent Subscription Groups additions, 2026-11-01. See LICENSE and NOTICE.
 */
package app.morphe.patches.youtube.misc.groups

import app.morphe.patches.youtube.video.series.playbackIdentityPatch
import app.morphe.patches.youtube.video.information.onCreateHook
import app.morphe.patches.youtube.video.information.videoTimeHook
import app.morphe.patches.youtube.misc.auth.authHookPatch
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.patch.Compatibility
import app.morphe.patches.shared.misc.settings.preference.NonInteractivePreference
import app.morphe.patches.shared.misc.settings.preference.PreferenceScreenPreference
import app.morphe.patches.shared.misc.settings.preference.PreferenceCategory
import app.morphe.patches.shared.misc.settings.preference.SwitchPreference
import app.morphe.patches.youtube.misc.settings.PreferenceScreen
import app.morphe.patches.youtube.misc.settings.settingsPatch
import app.morphe.patches.youtube.shared.Constants.COMPATIBILITY_YOUTUBE
import app.morphe.patches.youtube.misc.navigation.addBrowseViewHook
import app.morphe.patches.youtube.misc.navigation.addSearchResultsHook
import app.morphe.patches.youtube.misc.navigation.browseIdHookPatch
import app.morphe.patches.youtube.misc.navigation.navigationBarHookPatch
import app.morphe.patches.youtube.misc.fix.guidecache.fixGuideCachePatch

private const val RUNTIME = "Lapp/morphe/extension/youtube/groups/GroupsSubscriptions;"
private fun option(key: String) = NonInteractivePreference(
    key = "morphe_pockettube_option_$key",
    titleKey = "morphe_pockettube_v2_$key",
    summaryKey = null,
    tag = "app.morphe.extension.youtube.groups.GroupOptionPreference",
    selectable = true,
    dependency = "morphe_pockettube_groups",
)
private fun category(key: String, vararg options: String) = PreferenceCategory(
    key = "morphe_pockettube_category_$key",
    titleKey = "morphe_pockettube_v2_${key}_section",
    sorting = PreferenceScreenPreference.Sorting.UNSORTED,
    preferences = options.map(::option).toSet(),
)

@Suppress("unused")
val pocketTubeGroupsPatch = bytecodePatch(
    name = "PocketTube Groups",
    description = "Integrates subscription groups, recent upload windows and local group management " +
        "into Subscriptions. Targets YouTube 21.30.216; APK/runtime validation required. No automatic sync.",
    default = false,
) {
    dependsOn(settingsPatch, navigationBarHookPatch, browseIdHookPatch, fixGuideCachePatch, groupsNativeChromePatch, playbackIdentityPatch, authHookPatch, groupsNativeMenuPatch)
    compatibleWith(Compatibility(
        packageName = "com.google.android.youtube", name = "YouTube",
        apkFileType = COMPATIBILITY_YOUTUBE.apkFileType,
        appIconColor = COMPATIBILITY_YOUTUBE.appIconColor,
        signatures = COMPATIBILITY_YOUTUBE.signatures,
        targets = COMPATIBILITY_YOUTUBE.targets.filter { it.version == "21.30.216" },
    ))

    execute {
        onCreateHook("Lapp/morphe/extension/youtube/groups/GroupsWatchedPlayback;", "newVideoStarted")
        videoTimeHook("Lapp/morphe/extension/youtube/groups/GroupsWatchedPlayback;", "videoTimeChanged")
        addBrowseViewHook(RUNTIME)
        addSearchResultsHook("$RUNTIME->onSearchShown()V")
        PreferenceScreen.GENERAL.addPreferences(
            PreferenceScreenPreference(
                key = "morphe_pockettube_groups_screen",
                sorting = PreferenceScreenPreference.Sorting.UNSORTED,
                preferences = setOf(
                    SwitchPreference("morphe_pockettube_groups", summary = true),
                    category("selection", "default", "remember", "selector", "feed"),
                    category("appearance", "style", "spacing", "icons", "order", "show_empty", "show_all", "placement", "remember_scroll"),
                    category("feed", "sort", "range", "remember_filters", "hide_watched", "remember_content", "default_content", "clear_watched"),
                    NonInteractivePreference(
                        key = "morphe_pockettube_v2_feed_scope",
                        titleKey = "morphe_pockettube_v2_about_feed",
                        summaryKey = "morphe_pockettube_v2_feed_explanation",
                    ),
                    NonInteractivePreference(
                        key = "morphe_pockettube_open",
                        tag = "app.morphe.extension.youtube.groups.GroupsPreference",
                        selectable = true,
                        dependency = "morphe_pockettube_groups",
                    ),
                ),
            ),
        )
    }
}
