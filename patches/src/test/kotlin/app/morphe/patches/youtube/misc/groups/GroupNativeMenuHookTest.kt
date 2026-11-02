/* SPDX-License-Identifier: GPL-3.0-only
 * Independent Subscription Groups groups.6.1, modified 2026-11-02. See LICENSE/NOTICE.
 */
package app.morphe.patches.youtube.misc.groups

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.util.getReference
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod.Companion.toMutable
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.builder.MutableMethodImplementation
import com.android.tools.smali.dexlib2.iface.instruction.RegisterRangeInstruction
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import com.android.tools.smali.dexlib2.immutable.ImmutableMethod
import com.android.tools.smali.dexlib2.immutable.ImmutableMethodParameter
import org.junit.jupiter.api.Test
import kotlin.test.*

class GroupNativeMenuHookTest {
    private val declaration = "Lfixture/Proto;->newSingularGeneratedExtension(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;ILjava/lang/Object;Ljava/lang/Class;)Ljava/lang/Object;"
    private fun initializer(extra: String = "", duplicate: Boolean = false) = ImmutableMethod(
        "Lfixture/Menu;", "<clinit>", listOf(), "V", AccessFlags.STATIC.value or AccessFlags.CONSTRUCTOR.value,
        null, null, MutableMethodImplementation(7)
    ).toMutable().apply { addInstructions(0, """
        const v4, 123456789
        $extra
        invoke-static/range { v0 .. v6 }, $declaration
        ${if (duplicate) "invoke-static/range { v0 .. v6 }, $declaration" else ""}
        return-void
    """) }
    @Test fun `resolves the actual constant passed as extension number`() {
        assertEquals(123456789, nativeMenuExtensionNumber(initializer()))
    }
    @Test fun `rejects an overwritten constant rather than guessing a schema`() {
        assertFails { nativeMenuExtensionNumber(initializer("sget v4, Lfixture/Other;->number:I")) }
    }
    @Test fun `rejects multiple native extension declarations`() {
        assertFails { nativeMenuExtensionNumber(initializer(duplicate = true)) }
    }
    @Test fun `rejects missing or invalid native extension number`() {
        val method = ImmutableMethod("Lfixture/Menu;", "<clinit>", listOf(), "V",
            AccessFlags.STATIC.value or AccessFlags.CONSTRUCTOR.value, null, null, MutableMethodImplementation(1)).toMutable()
        method.addInstructions(0, "return-void"); assertFails { nativeMenuExtensionNumber(method) }
        assertFails { nativeMenuExtensionNumber(initializer("const/4 v4, 0x0")) }
    }
    private fun constructor(code: String) = ImmutableMethod(
        "Lfixture/Tap;", "<init>", listOf(ImmutableMethodParameter("Ljava/lang/Object;", null, null)),
        "V", AccessFlags.PUBLIC.value or AccessFlags.CONSTRUCTOR.value, null, null, MutableMethodImplementation(30)
    ).toMutable().apply { addInstructions(0, code) }
    @Test fun `captures a high native receiver after initialization without invoking its action`() {
        val method = constructor("return-void"); captureGroupsMenuHandler(method)
        val first = method.instructions.first(); assertEquals(Opcode.INVOKE_STATIC_RANGE, first.opcode)
        assertEquals(28, (first as RegisterRangeInstruction).startRegister); assertEquals(1, first.registerCount)
        assertEquals("capture", first.getReference<MethodReference>()!!.name)
        assertEquals(Opcode.RETURN_VOID, method.instructions.last().opcode)
    }
    @Test fun `every normal constructor return preserves existing Ask callback and native branches`() {
        val method = constructor("""
            if-eqz p1, :other
            invoke-static/range { p0 .. p0 }, Lfixture/Ask;->created(Ljava/lang/Object;)V
            return-void
            :other
            return-void
        """)
        captureGroupsMenuHandler(method)
        val names = method.instructions.mapNotNull { it.getReference<MethodReference>()?.name }
        assertEquals(listOf("created", "capture", "capture"), names)
        assertEquals(2, method.instructions.count { it.opcode == Opcode.RETURN_VOID })
        assertEquals(Opcode.IF_EQZ, method.instructions.first().opcode)
    }
}
