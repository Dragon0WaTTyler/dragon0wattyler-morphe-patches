/* SPDX-License-Identifier: GPL-3.0-only
 * Independent Subscription Groups V2, 2026-11-01. See LICENSE and NOTICE.
 */
package app.morphe.patches.youtube.misc.groups

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.util.getReference
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod.Companion.toMutable
import app.morphe.patches.youtube.misc.navigation.wireBrowseViewWrapper
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.builder.MutableMethodImplementation
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.TwoRegisterInstruction
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import com.android.tools.smali.dexlib2.immutable.ImmutableMethod
import com.android.tools.smali.dexlib2.immutable.ImmutableMethodParameter
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertTrue

class GroupBrowseHookTest {
    private val owner = "Lfixture/Browse;"
    private fun method(code: String) = ImmutableMethod(
        owner, "create", listOf(ImmutableMethodParameter("Landroid/view/View;", null, null)),
        "Landroid/view/View;", AccessFlags.PUBLIC.value, null, null, MutableMethodImplementation(30),
    ).toMutable().apply { addInstructions(0, code) }

    @Test fun `captures a high receiver before the native result overwrites p0`() {
        val method = method("""
            invoke-virtual/range { p0 .. p1 }, $owner->toolbar(Landroid/view/View;)Landroid/view/View;
            move-result-object p0
            return-object p0
        """)
        wireBrowseViewWrapper(method, owner, "groups", setOf(owner))
        val capture = method.getInstruction<TwoRegisterInstruction>(0)
        assertEquals(28, capture.registerB)
        assertTrue(capture.registerA < 16)
        assertEquals(29, method.getInstruction<TwoRegisterInstruction>(1).registerB)
        assertEquals("groups", method.getInstruction(2).getReference<MethodReference>()!!.name)
        assertEquals(29, method.getInstruction<TwoRegisterInstruction>(4).registerA)
        assertEquals(Opcode.INVOKE_VIRTUAL_RANGE, method.getInstruction(5).opcode)
        assertEquals(28, method.getInstruction<OneRegisterInstruction>(6).registerA)
        assertEquals(Opcode.RETURN_OBJECT, method.getInstruction(7).opcode)
    }

    @Test fun `uses the actual native receiver even when p0 has already been reused`() {
        val method = method("""
            move-object/from16 v20, p0
            move-object/from16 v21, p1
            const/16 p0, 0x0
            invoke-virtual/range { v20 .. v21 }, $owner->toolbar(Landroid/view/View;)Landroid/view/View;
            move-result-object v20
            return-object v20
        """)
        wireBrowseViewWrapper(method, owner, "groups", setOf(owner))
        assertEquals(20, method.getInstruction<TwoRegisterInstruction>(3).registerB)
        assertEquals("groups", method.getInstruction(5).getReference<MethodReference>()!!.name)
        assertEquals(21, method.getInstruction<TwoRegisterInstruction>(7).registerA)
    }

    @Test fun `ignores a later static helper and retains the original instructions`() {
        val method = method("""
            invoke-virtual/range { p0 .. p1 }, $owner->toolbar(Landroid/view/View;)Landroid/view/View;
            move-result-object v22
            invoke-static/range { v22 .. v22 }, Lfixture/Style;->style(Landroid/view/View;)Landroid/view/View;
            move-result-object v22
            return-object v22
        """)
        wireBrowseViewWrapper(method, owner, "groups", setOf(owner))
        val names = method.instructions.mapNotNull { it.getReference<MethodReference>()?.name }
        assertEquals(listOf("groups", "toolbar", "style"), names)
    }

    @Test fun `keeps groups inside the native toolbar even with a later Series wrapper`() {
        val method = method("""
            invoke-virtual/range { p0 .. p1 }, $owner->toolbar(Landroid/view/View;)Landroid/view/View;
            move-result-object v22
            move-object/from16 v0, p0
            move-object/from16 v1, v22
            invoke-virtual { v0, v1 }, $owner->patch_seriesTrackerHistoryView(Landroid/view/View;)Landroid/view/View;
            move-result-object v22
            return-object v22
        """)
        wireBrowseViewWrapper(method, owner, "groups", setOf(owner))
        val names = method.instructions.mapNotNull { it.getReference<MethodReference>()?.name }
        assertEquals(listOf("groups", "toolbar", "patch_seriesTrackerHistoryView"), names)
    }

    @Test fun `refuses to patch an unrecognized wrapper instead of guessing`() {
        val method = method("""
            invoke-static/range { p1 .. p1 }, Lfixture/Style;->style(Landroid/view/View;)Landroid/view/View;
            move-result-object v22
            return-object v22
        """)
        assertFails { wireBrowseViewWrapper(method, owner, "groups", setOf(owner)) }
    }
    @Test fun `chrome callback observes native result without replacing it or losing receiver`() {
        val method = method("""
            invoke-virtual/range { p0 .. p1 }, $owner->toolbar(Landroid/view/View;)Landroid/view/View;
            move-result-object v0
            return-object v0
        """)
        wireBrowseViewWrapper(method, owner, "groups", setOf(owner), "chrome")
        val capture = method.getInstruction<TwoRegisterInstruction>(0)
        assertTrue(capture.registerA != 0)
        assertEquals(listOf("groups", "toolbar", "chrome"), method.instructions.mapNotNull { it.getReference<MethodReference>()?.name })
        assertEquals(Opcode.MOVE_RESULT_OBJECT, method.getInstruction(6).opcode)
        assertEquals(0, method.getInstruction<OneRegisterInstruction>(6).registerA)
        assertEquals(0, method.getInstruction<TwoRegisterInstruction>(7).registerB)
        assertEquals(0, method.getInstruction<OneRegisterInstruction>(9).registerA)
    }
    @Test fun `chrome callback supports native wrapper overwriting high p0`() {
        val method = method("""
            invoke-virtual/range { p0 .. p1 }, $owner->toolbar(Landroid/view/View;)Landroid/view/View;
            move-result-object p0
            return-object p0
        """)
        wireBrowseViewWrapper(method, owner, "groups", setOf(owner), "chrome")
        assertEquals(28, method.getInstruction<OneRegisterInstruction>(6).registerA)
        assertEquals(28, method.getInstruction<OneRegisterInstruction>(9).registerA)
    }
}
