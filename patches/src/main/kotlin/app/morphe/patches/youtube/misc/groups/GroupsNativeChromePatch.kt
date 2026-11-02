/* SPDX-License-Identifier: GPL-3.0-only
 * Independent Subscription Groups groups.5, 2026-11-01. See LICENSE and NOTICE.
 */
package app.morphe.patches.youtube.misc.groups

import app.morphe.patcher.extensions.InstructionExtensions.addInstruction
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.util.getReference
import app.morphe.patches.shared.misc.litho.filter.addLithoFilter
import app.morphe.patches.shared.misc.litho.relayout.LithoTextMountFingerprint
import app.morphe.patches.shared.misc.litho.relayout.lithoRelayoutPatch
import app.morphe.patches.shared.misc.textcomponent.hookLithoSpannableString
import app.morphe.patches.shared.misc.textcomponent.hookSpannableString
import app.morphe.patches.shared.misc.textcomponent.textComponentPatch
import app.morphe.patches.youtube.ad.injectHideViewCall
import app.morphe.patches.youtube.layout.hide.general.HideSubscribedChannelsBarConstructorFingerprint
import app.morphe.patches.youtube.layout.hide.general.HideSubscribedChannelsBarLandscapeFingerprint
import app.morphe.patches.youtube.misc.litho.filter.lithoFilterPatch
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.TwoRegisterInstruction
import com.android.tools.smali.dexlib2.iface.reference.FieldReference

private const val CHROME = "Lapp/morphe/extension/youtube/groups/GroupsNativeChrome;"

/** Reuses existing native component boundaries; no obfuscated class names or visible-label matching. */
internal val groupsNativeChromePatch = bytecodePatch(description = "Scopes subscription groups filter and carousel chrome.") {
    dependsOn(lithoFilterPatch, textComponentPatch, lithoRelayoutPatch)
    execute {
        addLithoFilter("Lapp/morphe/extension/youtube/groups/GroupsComponentsFilter;")
        hookSpannableString(CHROME, overrideSpan = true)
        hookLithoSpannableString(CHROME)

        LithoTextMountFingerprint.let { fingerprint ->
            // Resolve the original text field in the CURRENT method, so another patch's mount
            // callback does not move this insertion to an unrelated instruction.
            val field = fingerprint.instructionMatches.last().instruction.getReference<FieldReference>()!!
            fingerprint.method.apply {
                val index = implementation!!.instructions.indexOfFirst {
                    it.opcode == Opcode.IPUT_OBJECT && it.getReference<FieldReference>()?.toString() == field.toString()
                }
                check(index >= 0) { "Could not locate native chip text mount" }
                val text = getInstruction<TwoRegisterInstruction>(index)
                addInstruction(index + 1, "invoke-static { v${text.registerB}, v${text.registerA} }, " +
                        "$CHROME->onLithoTextMounted(Landroid/graphics/drawable/Drawable;Ljava/lang/CharSequence;)V")
            }
        }

        HideSubscribedChannelsBarConstructorFingerprint.let {
            it.method.injectHideViewCall(it.instructionMatches[1].index, CHROME, "onCarouselCreated")
        }
        HideSubscribedChannelsBarLandscapeFingerprint.match(HideSubscribedChannelsBarConstructorFingerprint.originalClassDef).let {
            it.method.apply {
                val index = it.instructionMatches.last().index
                val register = getInstruction<OneRegisterInstruction>(index).registerA
                addInstructions(index + 1, """
                    invoke-static { v$register }, $CHROME->carouselWidth(I)I
                    move-result v$register
                """)
            }
        }
    }
}
