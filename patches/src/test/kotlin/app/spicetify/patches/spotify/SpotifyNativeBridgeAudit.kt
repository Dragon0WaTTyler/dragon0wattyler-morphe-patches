package app.spicetify.patches.spotify

import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.DexFileFactory
import com.android.tools.smali.dexlib2.Opcodes
import com.android.tools.smali.dexlib2.dexbacked.DexBackedDexFile
import com.android.tools.smali.dexlib2.iface.ClassDef
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import com.android.tools.smali.dexlib2.iface.reference.TypeReference
import java.io.File
import java.util.zip.ZipFile

/** Checks native linkage against a private original APK; does not establish ART or UI correctness. */
internal fun verifySpotifyNativeBridge(bundle: File, apk: File) {
    val dex = DexFileFactory.loadDexContainer(apk, Opcodes.forApi(37))
    val native = dex.dexEntryNames.flatMap { dex.getEntry(it)!!.dexFile.classes }.associateBy { it.type }
    val application = checkNotNull(native["Lcom/spotify/music/SpotifyApplication;"])
    check(application.superclass == "Lp/k1a1;") { "Spotify Application inheritance changed" }
    check(native[application.superclass]!!.methods.any {
        it.name == "onCreate" && it.parameterTypes.isEmpty() && it.returnType == "V" && it.implementation != null
    }) { "Spotify inherited Application lifecycle missing" }
    ZipFile(bundle).use { zip ->
        val bridge = zip.getInputStream(zip.getEntry("extensions/spotify-settings.dex")).buffered().use {
            DexBackedDexFile.fromInputStream(Opcodes.forApi(37), it).classes
        }
        fun hierarchy(type: String, seen: MutableSet<String> = mutableSetOf()): List<ClassDef> {
            if (!seen.add(type)) return emptyList()
            val definition = native[type] ?: return emptyList()
            return listOf(definition) + (definition.interfaces + listOfNotNull(definition.superclass))
                .flatMap { hierarchy(it, seen) }
        }
        fun present(type: String) {
            if (type.startsWith("Lp/")) {
                val found = checkNotNull(native[type]) { "Bridge type missing: $type" }
                check(AccessFlags.PUBLIC.isSet(found.accessFlags)) { "Bridge cannot access native type $type" }
            }
        }
        var references = 0
        for (definition in bridge) {
            definition.superclass?.let(::present)
            definition.interfaces.forEach(::present)
            for (method in definition.methods) for (instruction in method.implementation?.instructions ?: emptyList()) {
                when (val reference = (instruction as? ReferenceInstruction)?.reference) {
                    is TypeReference -> present(reference.type)
                    is MethodReference -> if (reference.definingClass.startsWith("Lp/")) {
                        present(reference.definingClass)
                        val found = hierarchy(reference.definingClass).flatMap { it.methods }.firstOrNull {
                            it.name == reference.name && it.parameterTypes == reference.parameterTypes && it.returnType == reference.returnType
                        }
                        checkNotNull(found) { "Bridge method missing: $reference" }
                        check(AccessFlags.PUBLIC.isSet(found.accessFlags) ||
                            (reference.name == "<init>" && reference.definingClass == definition.superclass &&
                                AccessFlags.PROTECTED.isSet(found.accessFlags))) { "Bridge cannot access $reference" }
                        check(AccessFlags.STATIC.isSet(found.accessFlags) == instruction.opcode.toString().startsWith("INVOKE_STATIC")) {
                            "Bridge invocation kind disagrees with $reference"
                        }
                        references++
                    }
                    is FieldReference -> if (reference.definingClass.startsWith("Lp/")) {
                        present(reference.definingClass)
                        val found = hierarchy(reference.definingClass).flatMap { it.fields }.firstOrNull {
                            it.name == reference.name && it.type == reference.type
                        }
                        checkNotNull(found) { "Bridge field missing: $reference" }
                        check(AccessFlags.PUBLIC.isSet(found.accessFlags)) { "Bridge cannot access $reference" }
                        check(AccessFlags.STATIC.isSet(found.accessFlags) == instruction.opcode.toString().startsWith("SGET")) {
                            "Bridge field access kind disagrees with $reference"
                        }
                        references++
                    }
                }
            }
        }
        check(references > 20) { "Native bridge audit did not inspect the expected references" }
        println("Native bridge linkage: ${bridge.size} classes, $references method/field references verified")
    }
}
