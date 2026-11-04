package app.spicetify.patches.spotify

import app.morphe.patcher.Patcher
import app.morphe.patcher.PatcherConfig
import app.morphe.patcher.apk.ApkUtils.applyTo
import app.morphe.patcher.patch.PatchLoader
import com.android.tools.smali.dexlib2.Opcodes
import com.android.tools.smali.dexlib2.dexbacked.DexBackedDexFile
import kotlinx.coroutines.runBlocking
import java.io.File
import java.nio.file.Files
import java.util.zip.ZipFile

/** Offline patch application against a private APK fixture. The optional output is unsigned. */
fun main(args: Array<String>) {
    val (bundlePath, inputPath, selection) = args
    val bundle = File(bundlePath)
    val input = File(inputPath).canonicalFile
    val output = args.getOrNull(3)?.let { File(it).canonicalFile }
    require(input.isFile) { "Stock Spotify APK not found: $input" }
    require(output == null || !output.exists()) { "Output already exists: $output" }

    ZipFile(bundle).use { archive ->
        for (name in listOf("classes.dex", "extensions/spotify.mpe", "extensions/spotify-settings.dex")) {
            val entry = checkNotNull(archive.getEntry(name)) { "Missing $name in $bundle" }
            archive.getInputStream(entry).verifyDex()
        }
    }

    val spotify = PatchLoader.Jar(setOf(bundle)).filter { patch ->
        patch.compatibility?.any { it.packageName == "com.spotify.music" } == true
    }
    check(spotify.size == 8 && spotify.map { it.name }.toSet().size == 8) {
        "Expected eight distinct Spotify patches, got ${spotify.map { it.name }}"
    }
    val patches = if (selection == "all") spotify.toSet() else setOf(spotify.single { it.name == selection })
    val workspace = Files.createTempDirectory("morphe-spotify-").toFile()
    try {
        Patcher(PatcherConfig(input, workspace, validate = true)).use { patcher ->
            patcher += patches
            runBlocking {
                patcher().collect { result ->
                    result.exception?.let { throw it }
                    result.patch.name?.let { println("Applied $it") }
                }
            }
            val result = patcher.get()
            check(result.dexFiles.isNotEmpty()) { "Morphe produced no DEX output" }
            if (output == null) {
                result.dexFiles.forEach { it.stream.verifyDex() }
            } else {
                val candidate = input.copyTo(workspace.resolve("candidate.apk"))
                result.applyTo(candidate)
                ZipFile(candidate).use { apk ->
                    for (dex in result.dexFiles) apk.getInputStream(apk.getEntry(dex.name)).verifyDex()
                }
                candidate.copyTo(output)
                println("Unsigned APK: $output")
            }
        }
    } finally {
        workspace.deleteRecursively()
    }
}

private fun java.io.InputStream.verifyDex() = buffered().use { dex ->
    check(DexBackedDexFile.fromInputStream(Opcodes.getDefault(), dex).classes.isNotEmpty())
}
