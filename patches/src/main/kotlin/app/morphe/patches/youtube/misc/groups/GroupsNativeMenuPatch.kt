/* SPDX-License-Identifier: GPL-3.0-only
 * Independent Subscription Groups groups.6.1, modified 2026-11-02. See LICENSE/NOTICE.
 */
package app.morphe.patches.youtube.misc.groups

import app.morphe.patcher.extensions.InstructionExtensions.addInstruction
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.util.getReference
import app.morphe.patcher.util.registersUsed
import app.morphe.patcher.util.numberOfParameterRegisters
import app.morphe.patches.shared.misc.bridge.addBridge
import app.morphe.patches.youtube.interaction.ask.ElementsTapHandlerConstructorFingerprint
import app.morphe.patches.youtube.interaction.ask.ElementsTapHandlerFingerprint
import app.morphe.patches.youtube.layout.flyout.FeedFlyoutBufferObjectFingerprint
import app.morphe.patches.youtube.layout.flyout.flyoutPatch
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.Method
import com.android.tools.smali.dexlib2.iface.instruction.NarrowLiteralInstruction
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import java.util.logging.Logger

private const val HANDLER = "Lapp/morphe/extension/youtube/groups/NativeMenuRegistry\$Handler;"
private const val RUNTIME = "Lapp/morphe/extension/youtube/groups/GroupsNativeVideoMenu;"

/** Resolve the actual native command extension, never a guessed command number. */
internal fun nativeMenuExtensionNumber(initializer: Method): Int {
    val instructions = initializer.implementation!!.instructions.toList()
    val calls = instructions.withIndex().filter { (_, instruction) ->
        instruction.getReference<MethodReference>()?.let {
            it.name == "newSingularGeneratedExtension" && it.parameterTypes.size == 7 && it.parameterTypes[4].toString() == "I"
        } == true
    }
    check(calls.size == 1) { "Native menu command must declare exactly one extension" }
    val call = calls.single()
    val register = call.value.registersUsed[4]
    val write = instructions.take(call.index).lastOrNull {
        it.opcode.setsRegister() && it is OneRegisterInstruction && it.registerA == register
    }
    check(write is NarrowLiteralInstruction && write.opcode in setOf(Opcode.CONST, Opcode.CONST_16, Opcode.CONST_HIGH16, Opcode.CONST_4)) { "Native menu extension number is not a direct constant" }
    return write.narrowLiteral.also { check(it in 10000..536870911) { "Invalid native menu extension number" } }
}

/** Constructor callbacks use the native receiver even when its register is above v15. */
internal fun captureGroupsMenuHandler(method: app.morphe.patcher.util.proxy.mutableTypes.MutableMethod) {
    val returns = method.implementation!!.instructions.withIndex().filter { it.value.opcode == Opcode.RETURN_VOID }.map { it.index }
    check(returns.isNotEmpty()) { "Missing native handler constructor return" }
    returns.reversed().forEach { method.addInstruction(it, "invoke-static/range { p0 .. p0 }, $RUNTIME->capture($HANDLER)V") }
}

internal val groupsNativeMenuPatch = bytecodePatch(description = "Reuses available native video-menu capabilities for Groups.") {
    dependsOn(flyoutPatch)
    execute {
        // Resolve every target-dependent input before mutating classes. Unsupported layouts retain fallback.
        val resolved = try {
            val menuType = FeedFlyoutBufferObjectFingerprint.originalMethod.parameterTypes.first().toString()
            val field = nativeMenuExtensionNumber(classDefBy(menuType).methods.single { it.name == "<clinit>" })
            val tap = ElementsTapHandlerFingerprint.originalMethod
            check(tap.parameterTypes.first().toString() == "Landroid/view/View;" && tap.parameterTypes.size == 3 && tap.returnType == "V")
            val getter = ElementsTapHandlerFingerprint.instructionMatches.last().getMethodCalled()
            val holder = classDefBy(tap.definingClass).fields.single { it.type == getter.definingClass }
            val constructor = ElementsTapHandlerConstructorFingerprint.method
            check(constructor.implementation!!.instructions.any { it.opcode == Opcode.RETURN_VOID })
            check(FeedFlyoutBufferObjectFingerprint.originalMethod.implementation!!.registerCount > FeedFlyoutBufferObjectFingerprint.originalMethod.numberOfParameterRegisters)
            Triple(field, Pair(tap, getter), Pair(holder, constructor))
        } catch (unsupported: Exception) {
            Logger.getLogger("PocketTube Groups").warning("Native menu capability hook unavailable; functional fallback retained: ${unsupported.message}")
            null
        } ?: return@execute
        val (number, methods, binding) = resolved
        val (tap, getter) = methods
        val (holder, constructor) = binding
        mutableClassDefBy(tap.definingClass).apply {
            interfaces.add(HANDLER)
            addBridge("patch_groupsMenuCommand", listOf(), "[B", 2, """
                iget-object v0, p0, $holder
                invoke-virtual { v0 }, $getter
                move-result-object v0
                invoke-interface { v0 }, Lcom/google/protobuf/MessageLite;->toByteArray()[B
                move-result-object v0
                return-object v0
            """)
            addBridge("patch_groupsOpenMenu", listOf("Ljava/lang/Object;"), "V", 3, """
                check-cast p1, Landroid/view/View;
                const/4 v0, 0x0
                invoke-virtual { p0, p1, v0, v0 }, ${tap.definingClass}->${tap.name}(${tap.parameterTypes.joinToString("")})V
                return-void
            """)
        }
        captureGroupsMenuHandler(constructor)
        // Both parameters remain the exact original native command and context map.
        FeedFlyoutBufferObjectFingerprint.method.addInstructionsWithLabels(0, """
            invoke-static/range { p1 .. p2 }, $RUNTIME->beforeNativeMenu(Ljava/lang/Object;Ljava/util/Map;)Z
            move-result v0
            if-nez v0, :groups_native_original
            return-void
            :groups_native_original
            nop
        """)
        mutableClassDefBy("Lapp/morphe/extension/youtube/groups/NativeMenuConfig;").methods.single { it.name == "commandField" }.apply {
            addInstructions(0, "const v0, $number\nreturn v0")
        }
    }
}
