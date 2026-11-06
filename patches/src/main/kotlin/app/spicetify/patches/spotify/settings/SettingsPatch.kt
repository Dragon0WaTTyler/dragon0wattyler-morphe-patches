package app.spicetify.patches.spotify.settings

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.extensions.InstructionExtensions.replaceInstruction
import app.morphe.patcher.patch.BytecodePatchContext
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.util.smali.ExternalLabel
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import java.util.Properties

private const val EXTENSION = "Lapp/spicetify/extension/spotify/settings/"

// Adds nothing to the manifest. A root mount install keeps the stock manifest, so the settings
// pages are dialogs over Spotify's activity (SpicetifySettingsScreen) instead of an activity.
internal val settingsPatch = bytecodePatch {
    extendWith("extensions/spotify.mpe")
    extendWith("extensions/spotify-settings.dex")

    execute {
        val snapshot = Properties().apply {
            NativeSettingsAbi::class.java.getResourceAsStream("/spotify/settings/9.1.94.1929.properties")!!.use(::load)
        }
        for (type in NativeSettingsAbi.TYPES) {
            val definition = classDefByOrNull(type)
                ?: throw PatchException("Spotify settings ABI changed: missing $type")
            if (NativeSettingsAbi.digest(definition) != snapshot.getProperty(type)) {
                throw PatchException("Spotify settings ABI changed: $type. Use the verified Spotify 9.1.94.1929 APK.")
            }
        }

        val root = mutableClassDefBy("Lp/wbs;").methods.single { it.name == "create" }
        val rootInstructions = root.implementation!!.instructions
        val insertion = rootInstructions.indices.single { index ->
            (rootInstructions[index] as? ReferenceInstruction)?.reference.toString() ==
                "Lp/r06;->i0([Ljava/lang/Object;)Ljava/util/List;" &&
                rootInstructions.getOrNull(index + 1)?.opcode == Opcode.MOVE_RESULT_OBJECT &&
                (rootInstructions.getOrNull(index + 2) as? ReferenceInstruction)?.reference.toString() ==
                "Lp/fdn1;->E(Ljava/lang/Iterable;)Lp/r8;"
        } + 2
        // The native list is fixed-size. Return a copied list before Spotify freezes it.
        root.addInstructions(insertion, """
            invoke-static {v0}, ${EXTENSION}nativebridge/SettingsBridge;->append(Ljava/util/List;)Ljava/util/List;
            move-result-object v0
        """.trimIndent())

        val analytics = mutableClassDefBy("Lp/b3l0;").methods.single {
            it.name == "<init>" && it.parameterTypes == listOf("I", "Lp/v2g1;", "B")
        }
        val instructions = analytics.implementation!!.instructions
        val marker = instructions.indexOfFirst {
            (it as? ReferenceInstruction)?.reference.toString() == "mobile-settings-element-standard-navigation"
        }
        val switch = (marker + 1 until instructions.size).first { instructions[it].opcode == Opcode.PACKED_SWITCH }
        val sink = (switch + 1 until instructions.size).first {
            (instructions[it] as? ReferenceInstruction)?.reference.toString() == "Lp/b3g1;->b:Ljava/lang/String;"
        }
        // v13's constructor discriminator and metadata strings have been consumed. Native cases
        // use v0 for their label, including ad_partners, so leave v0 intact on mismatch.
        analytics.addInstructionsWithLabels(switch, """
            const/4 v13, -0x1
            if-ne v11, v13, :native_labels
            const-string v0, "spicetify_settings"
            goto/16 :label_sink
        """.trimIndent(), ExternalLabel("native_labels", analytics.getInstruction(switch)),
            ExternalLabel("label_sink", analytics.getInstruction(sink)))

        // SpotifyApplication now inherits its lifecycle from this pinned base class.
        mutableClassDefBy("Lp/k1a1;").methods.single {
            it.name == "onCreate" && it.parameterTypes.isEmpty() && it.returnType == "V"
        }
            .addInstructions(0,
                "invoke-static/range {p0 .. p0}, ${EXTENSION}PatchSettings;->initialize(Landroid/content/Context;)V")
    }
}

internal fun BytecodePatchContext.enableSetting(name: String) {
    mutableClassDefBy("${EXTENSION}InstalledPatches;").methods.single { it.name == name }
        .replaceInstruction(0, "const/4 v0, 0x1")
}

internal val themeSettingsPatch = bytecodePatch {
    dependsOn(settingsPatch)
    execute { enableSetting("themeColors") }
}
