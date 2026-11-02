/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-patches/pull/3114
 *
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to this code.
 */

package app.morphe.patches.youtube.video.series

import app.morphe.patcher.extensions.InstructionExtensions.addInstruction
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.patch.resourcePatch
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod.Companion.toMutable
import app.morphe.patches.shared.misc.fix.proto.fixProtoLibraryPatch
import app.morphe.patches.shared.misc.settings.preference.NonInteractivePreference
import app.morphe.patches.shared.misc.settings.preference.PreferenceScreenPreference
import app.morphe.patches.shared.misc.settings.preference.PreferenceScreenPreference.Sorting
import app.morphe.patches.shared.misc.settings.preference.SwitchPreference
import app.morphe.patches.youtube.layout.player.icons.copyPlayerButtonIcons
import app.morphe.patches.youtube.layout.playlistautoplay.navigationIntentHook
import app.morphe.patches.youtube.misc.extension.sharedExtensionPatch
import app.morphe.patches.youtube.misc.navigation.hookNavigationButtonCreated
import app.morphe.patches.youtube.misc.navigation.navigationBarHookPatch
import app.morphe.patches.youtube.misc.playercontrols.addTopControl
import app.morphe.patches.youtube.misc.playercontrols.initializeTopControl
import app.morphe.patches.youtube.misc.playercontrols.legacyPlayerControlsPatch
import app.morphe.patches.youtube.misc.playercontrols.legacyPlayerControlsResourcePatch
import app.morphe.patches.youtube.misc.playservice.is_21_02_or_greater
import app.morphe.patches.youtube.misc.playservice.versionCheckPatch
import app.morphe.patches.youtube.misc.settings.PreferenceScreen
import app.morphe.patches.youtube.misc.settings.settingsPatch
import app.morphe.patches.youtube.shared.Constants.COMPATIBILITY_YOUTUBE
import app.morphe.patches.youtube.video.information.PlayerInitFingerprint
import app.morphe.patches.youtube.video.information.onCreateHook
import app.morphe.patches.youtube.video.information.videoInformationPatch
import app.morphe.patches.youtube.video.information.videoTimeHook
import app.morphe.patches.youtube.video.videoid.VideoIdFingerprint
import app.morphe.util.ResourceGroup
import app.morphe.util.copyResources
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.builder.MutableMethodImplementation
import com.android.tools.smali.dexlib2.iface.instruction.FiveRegisterInstruction
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import com.android.tools.smali.dexlib2.immutable.ImmutableMethod
import java.util.logging.Logger

// Modified 2026-11-02: shared with independent Subscription Groups local completion tracking.
internal val playbackIdentityPatch = bytecodePatch(description = "Provides coherent native player identity and position.") {
    dependsOn(videoInformationPatch, versionCheckPatch)
    execute {
        if (!is_21_02_or_greater) return@execute
        PlayerInitFingerprint.classDef.apply {
            val idGetter = ControllerVideoIdFingerprint(
                VideoIdFingerprint.instructionMatches.first().getMethodCalled()
            ).originalMethod

            interfaces.add($$"$${SERIES_TRACKER_EXTENSION_PREFIX}PlaybackBridge$Source;")

            fun addGetter(name: String, returnType: String, target: String, wide: Boolean) {
                methods.add(
                    ImmutableMethod(
                        type,
                        name,
                        null,
                        returnType,
                        AccessFlags.PUBLIC.value,
                        null,
                        null,
                        MutableMethodImplementation(if (wide) 3 else 2),
                    ).toMutable().apply {
                        val suffix = if (wide) "wide" else "object"
                        addInstructions(
                            0,
                            """
                                $target
                                move-result-$suffix v0
                                return-$suffix v0
                            """
                        )
                    }
                )
            }

            addGetter(
                "patch_seriesTrackerVideoId",
                "Ljava/lang/String;",
                "invoke-virtual { p0 }, $idGetter",
                false
            )
            addGetter(
                "patch_seriesTrackerPosition",
                "J",
                "invoke-virtual { p0 }, $type->patch_getVideoTime()J",
                true
            )
        }

    }
}
