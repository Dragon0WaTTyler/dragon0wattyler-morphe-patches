import org.jetbrains.kotlin.gradle.dsl.JvmTarget

group = "local.subscriptiongroups"

patches {
    about {
        name = "Dragon0WaTTyler Morphe Patches"
        description = "Independent, growing collection of Morphe patches for Android apps."
        source = "https://github.com/Dragon0WaTTyler/dragon0wattyler-morphe-patches"
        author = "Dragon0WaTTyler"
        contact = "GitHub Issues"
        website = "https://github.com/Dragon0WaTTyler/dragon0wattyler-morphe-patches"
        license = "GNU General Public License v3.0"
    }
}
// Separate configuration so gson is available at runtime for the
// generatePatchesList task but never bundled into the APK.
val patchListGeneratorClasspath = configurations.create("patchListGeneratorClasspath")
val spotifyBridgeAssembler = configurations.create("spotifyBridgeAssembler")
val jamApk = providers.gradleProperty("jamApk")
val jamOutput = providers.gradleProperty("jamOutput")

// Resolve local composite build projects (smali, patcher) to jars for compilation,
// same as the runtime classpath. Otherwise, both the classes directory and the jar
// are passed to D8 by buildAndroid, and it fails with "Classpath type already present".
configurations.compileClasspath {
    attributes {
        attribute(LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE, objects.named(LibraryElements.JAR))
    }
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    // Required due to smali, or build fails. Can be removed once smali is bumped.
    implementation(libs.guava)

    implementation(libs.morphe.patches.library)
    compileOnly(libs.gson)

    patchListGeneratorClasspath(libs.gson)
    spotifyBridgeAssembler("com.github.MorpheApp.smali:smali:${libs.versions.smali.get()}")

    // Android API stubs defined here.
    compileOnly(project(":patches:stub"))

    testImplementation(kotlin("test"))
    testImplementation("org.junit.jupiter:junit-jupiter-api:${libs.versions.junit.jupiter.get()}")
    testImplementation(libs.kotlinx.coroutines.core)
    testImplementation(libs.protobuf.javalite)
    testRuntimeOnly(libs.junit.jupiter.engine)
}

val spotifyBridgeSource = layout.projectDirectory.dir("src/main/smali/spotify")
val spotifyBridgeOutput = layout.buildDirectory.dir("generated/spotify-bridge")
val assembleSpotifyBridge = tasks.register<JavaExec>("assembleSpotifyBridge") {
    description = "Assembles Spicetify's native Spotify bridge"
    classpath = spotifyBridgeAssembler
    mainClass.set("com.android.tools.smali.smali.Main")
    inputs.dir(spotifyBridgeSource)
    outputs.dir(spotifyBridgeOutput)
    val dex = spotifyBridgeOutput.map { it.file("extensions/spotify-settings.dex") }
    doFirst {
        val output = dex.get().asFile
        output.parentFile.mkdirs()
        args("assemble", spotifyBridgeSource.asFile.absolutePath, "--api", "24", "--output", output.absolutePath)
    }
}
sourceSets.main { resources.srcDir(assembleSpotifyBridge) }

// Optional native integrations stay out of the common settings DEX. In particular,
// the server filter subclasses a native model whose final flag only that patch changes.
for ((scope, artifact) in listOf("extensions" to "spotify-extensions.dex", "server" to "spotify-server.dex")) {
    val source = layout.projectDirectory.dir("src/main/smali/spotify-$scope")
    val output = layout.buildDirectory.dir("generated/spotify-$scope-bridge")
    val assembler = tasks.register<JavaExec>("assembleSpotify${scope.replaceFirstChar { it.uppercase() }}Bridge") {
        description = "Assembles the optional Spotify $scope native bridge"
        classpath = spotifyBridgeAssembler
        mainClass.set("com.android.tools.smali.smali.Main")
        inputs.dir(source)
        outputs.dir(output)
        doFirst {
            val dex = output.get().file("extensions/$artifact").asFile
            dex.parentFile.mkdirs()
            args("assemble", source.asFile.absolutePath, "--api", "24", "--output", dex.absolutePath)
        }
    }
    sourceSets.main { resources.srcDir(assembler) }
}

tasks {
    processResources {
        into("META-INF/licenses/spicetify") {
            from(rootProject.file("LICENSE"))
            from(rootProject.file("docs/licenses/spicetify-NOTICE"))
            from(rootProject.file("docs/licenses/spicetify-third-party-notices.md"))
        }
    }

    test {
        useJUnitPlatform()
        dependsOn(":extensions:youtube:generateReleaseProto", ":extensions:youtube:compileReleaseJavaWithJavac")
        jamApk.orNull?.let { systemProperty("jamApk", it) }
        // The Android-boundary tests load release bytecode in isolated class loaders.
        // Declare those actual classes as inputs so runtime changes invalidate cached results.
        inputs.dir(rootProject.file(
            "extensions/youtube/build/intermediates/javac/release/compileReleaseJavaWithJavac/classes"
        )).withPropertyName("queueRuntimeClasses").withNormalizer(ClasspathNormalizer::class)
        inputs.file(rootProject.file(
            "extensions/youtube/src/main/java/app/morphe/extension/youtube/patches/OpenVideosFullscreenHookPatch.java"
        )).withPropertyName("queueFullscreenBoundarySource")
        inputs.files(
            rootProject.file("extensions/youtube/src/main/java/app/morphe/extension/youtube/patches/queue/QueueSaveActions.java"),
            rootProject.file("extensions/youtube/src/main/java/app/morphe/extension/youtube/patches/queue/QueueSaveFeedback.java"),
            rootProject.file("extensions/youtube/src/main/proto/app/morphe/extension/youtube/innertube/playlist_queue.proto"),
            rootProject.file("extensions/youtube/src/main/proto/app/morphe/extension/youtube/innertube/icon.proto")
        ).withPropertyName("queueSaveBoundarySources")
    }

    register<JavaExec>("checkStringResources") {
        description = "Checks resource strings for invalid formatting"

        // Only the compiled patches and the raw string resources are needed. Using the full
        // main runtime classpath would run processResources, which builds and minifies all extensions.
        classpath = files(
            sourceSets["main"].output.classesDirs,
            configurations.runtimeClasspath,
            layout.projectDirectory.dir("src/main/resources"),
        )
        mainClass.set("app.morphe.patches.util.resource.CheckStringResourcesKt")
    }

    register<JavaExec>("generatePatchesList") {
        description = "Build patch with patch list"

        dependsOn(build)
        systemProperty("patches.version", project.version.toString())

        classpath = sourceSets["main"].runtimeClasspath + patchListGeneratorClasspath
        mainClass.set("util.PatchListGeneratorKt")
    }

    register<JavaExec>("validateJam") {
        description = "Patches a supplied YouTube Music APK for Jam validation and optional device testing"
        group = "verification"

        dependsOn("testClasses")
        classpath = sourceSets["test"].runtimeClasspath
        mainClass.set("app.morphe.patches.music.interaction.jam.JamDeviceBuildKt")

        doFirst {
            val apk = jamApk.orNull
                ?: throw GradleException("validateJam requires -PjamApk=/absolute/path/to/ytm.apk")
            setArgs(listOfNotNull(apk, jamOutput.orNull))
        }
    }

    register<JavaExec>("validatePlaylistQueue") {
        description = "Applies Native playlist queue to a stock APK and verifies it with Android SDK tools"
        group = "verification"
        dependsOn("testClasses")
        classpath = sourceSets["test"].runtimeClasspath
        mainClass.set("app.morphe.patches.youtube.interaction.nativeplaylistqueue.PlaylistQueueApkBuildKt")
        doFirst {
            val apk = providers.gradleProperty("playlistQueueApk").orNull
                ?: throw GradleException("validatePlaylistQueue requires -PplaylistQueueApk=/absolute/path/youtube.apk")
            val mode = providers.gradleProperty("playlistQueueMode").getOrElse("standalone")
            val output = providers.gradleProperty("playlistQueueOutput").orNull
            setArgs(listOfNotNull(apk, mode, output))
        }
    }

    register<JavaExec>("validateBlackmagicCamera") {
        description = "Applies selected Blackmagic Camera patches to a stock base APK and verifies the DEX output"
        group = "verification"
        dependsOn("testClasses", "buildAndroid")
        classpath = sourceSets["test"].runtimeClasspath
        mainClass.set("app.morphe.patches.blackmagiccamera.BlackmagicCameraApkBuildKt")
        doFirst {
            val apk = providers.gradleProperty("bmcApk").orNull
                ?: throw GradleException("validateBlackmagicCamera requires -PbmcApk=/absolute/path/to/base.apk")
            val mode = providers.gradleProperty("bmcMode").getOrElse("both")
            val output = providers.gradleProperty("bmcOutput").orNull
            val bundle = layout.buildDirectory.file("libs/patches-${project.version}.mpp").get().asFile
            if (!bundle.isFile) {
                throw GradleException("No canonical Morphe bundle found at ${bundle.absolutePath}")
            }
            setArgs(listOf(bundle.absolutePath, apk, mode) + listOfNotNull(output))
        }
    }
    register<JavaExec>("validateSpotify") {
        description = "Applies Spicetify from the built bundle to a supplied stock Spotify APK"
        group = "verification"
        dependsOn("testClasses", "buildAndroid")
        classpath = sourceSets["test"].runtimeClasspath
        maxHeapSize = "4g"
        mainClass.set("app.spicetify.patches.spotify.SpotifyApkBuildKt")
        doFirst {
            val apk = providers.gradleProperty("spotifyApk").orNull
                ?: throw GradleException("validateSpotify requires -PspotifyApk=/absolute/path/to/stock.apk")
            val selection = providers.gradleProperty("spotifyPatch").getOrElse("all")
            val bundle = layout.buildDirectory.file("libs/patches-${project.version}.mpp").get().asFile
            setArgs(listOf(bundle.absolutePath, apk, selection) + listOfNotNull(providers.gradleProperty("spotifyOutput").orNull))
        }
    }
    register<JavaExec>("validateReadest") {
        description = "Applies Readest S Pen Air Actions to a supplied stock Readest APK"
        group = "verification"
        dependsOn("testClasses", "buildAndroid")
        classpath = sourceSets["test"].runtimeClasspath
        mainClass.set("app.morphe.patches.readest.ReadestApkBuildKt")
        doFirst {
            val apk = providers.gradleProperty("readestApk").orNull
                ?: throw GradleException("validateReadest requires -PreadestApk=/absolute/path/to/Readest.apk")
            val output = providers.gradleProperty("readestOutput").orNull
            val bundle = layout.buildDirectory.file("libs/patches-${project.version}.mpp").get().asFile
            if (!bundle.isFile) {
                throw GradleException("No canonical Morphe bundle found at ${bundle.absolutePath}")
            }
            setArgs(listOf(bundle.absolutePath, apk) + listOfNotNull(output))
        }
    }
    // Used by gradle-semantic-release-plugin.
    publish {
        dependsOn("generatePatchesList")
    }
}
