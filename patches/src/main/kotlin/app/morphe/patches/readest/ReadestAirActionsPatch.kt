package app.morphe.patches.readest

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.patch.resourcePatch
import app.morphe.patcher.patch.ApkFileType
import app.morphe.patcher.patch.AppTarget
import app.morphe.patcher.patch.Compatibility
import org.w3c.dom.Element

internal const val ANDROID_NAMESPACE = "http://schemas.android.com/apk/res/android"
internal const val REMOTE_ACTION = "com.samsung.android.support.REMOTE_ACTION"
internal const val MAIN_ACTIVITY = "com.bilingify.readest.MainActivity"
private const val REMOTE_ACTIONS_RESOURCE = "res/xml/readest_s_pen_air_actions.xml"
private const val AIR_ACTIONS_STRINGS_RESOURCE = "res/values/strings.xml"

private val READEST_COMPATIBILITY = Compatibility(
    name = "Readest",
    packageName = "com.bilingify.readest",
    apkFileType = ApkFileType.APK,
    appIconColor = 0x5A3EFF,
    targets = listOf(
        AppTarget(version = "0.12.20"),
    ),
)

internal object RemoteActionsResources {
    const val XML = """<?xml version="1.0" encoding="utf-8"?>
<remote-actions version="1.2">
    <action
        id="readest_page_next"
        label="@string/readest_s_pen_next_page"
        priority="1"
        trigger_key="PAGE_DOWN"
        repeatable="false">
        <preference name="gesture" value="click|swipe_left" />
        <preference name="button_only" value="false" />
        <preference name="motion_only" value="false" />
    </action>
    <action
        id="readest_page_previous"
        label="@string/readest_s_pen_previous_page"
        priority="2"
        trigger_key="PAGE_UP"
        repeatable="false">
        <preference name="gesture" value="double_click|swipe_right" />
        <preference name="button_only" value="false" />
        <preference name="motion_only" value="false" />
    </action>
</remote-actions>
"""

    fun addStrings(document: org.w3c.dom.Document) {
        val strings = listOf(
            "readest_s_pen_next_page" to "Next page",
            "readest_s_pen_previous_page" to "Previous page",
        )
        val existingNames = document.getElementsByTagName("string").let { nodes ->
            (0 until nodes.length).map { (nodes.item(it) as Element).getAttribute("name") }.toSet()
        }
        check(strings.none { (name, _) -> name in existingNames }) {
            "Readest already declares one of the S Pen action string names"
        }

        strings.forEach { (name, text) ->
            document.documentElement.appendChild(document.createElement("string").apply {
                setAttribute("name", name)
                textContent = text
            })
        }
    }

    fun registerInManifest(manifest: org.w3c.dom.Document) {
        val activities = manifest.getElementsByTagName("activity")
        val activity = (0 until activities.length)
            .map { activities.item(it) as Element }
            .singleOrNull { it.getAttributeNS(ANDROID_NAMESPACE, "name") == MAIN_ACTIVITY }
            ?: error("Readest 0.12.20 MainActivity was not found; refusing to add Samsung RemoteActions")

        check(activity.getElementsByTagName("meta-data").let { nodes ->
            (0 until nodes.length).none {
                (nodes.item(it) as Element).getAttributeNS(ANDROID_NAMESPACE, "name") == REMOTE_ACTION
            }
        }) { "Readest MainActivity already declares Samsung RemoteActions" }

        val filter = manifest.createElement("intent-filter")
        filter.appendChild(manifest.createElement("action").apply {
            setAttributeNS(ANDROID_NAMESPACE, "android:name", REMOTE_ACTION)
        })
        activity.appendChild(filter)

        activity.appendChild(manifest.createElement("meta-data").apply {
            setAttributeNS(ANDROID_NAMESPACE, "android:name", REMOTE_ACTION)
            setAttributeNS(ANDROID_NAMESPACE, "android:resource", "@xml/readest_s_pen_air_actions")
        })
    }
}

private val readestAirActionsResourcesPatch = resourcePatch(
    name = "Readest Air Actions resources",
    description = "Declares Samsung S Pen Air Actions for Readest.",
    default = false,
) {
    compatibleWith(READEST_COMPATIBILITY)

    execute {
        document("AndroidManifest.xml").use(RemoteActionsResources::registerInManifest)
        document(AIR_ACTIONS_STRINGS_RESOURCE).use(RemoteActionsResources::addStrings)
        get(REMOTE_ACTIONS_RESOURCE, copy = false).writeText(RemoteActionsResources.XML)
    }
}

private object ReadestDispatchKeyEventFingerprint : Fingerprint(
    definingClass = "Lcom/bilingify/readest/MainActivity;",
    name = "dispatchKeyEvent",
    parameters = listOf("Landroid/view/KeyEvent;"),
    returnType = "Z",
)

@Suppress("unused")
internal val readestSPenAirActionsPatch = bytecodePatch(
    name = "Readest S Pen Air Actions",
    description = "Adds S Pen single/double press and left/right gestures for page turns.",
) {
    compatibleWith(READEST_COMPATIBILITY)
    dependsOn(readestAirActionsResourcesPatch)

    execute {
        // Readest 0.12.20 already passes PAGE_UP/PAGE_DOWN through MainActivity's
        // existing dispatch path to its WebView. Resolve this concrete entrypoint
        // before keeping the manifest declaration, and reject other APK layouts.
        val dispatchKeyEvent = ReadestDispatchKeyEventFingerprint.method
        check(dispatchKeyEvent.returnType == "Z" && dispatchKeyEvent.parameterTypes == listOf("Landroid/view/KeyEvent;")) {
            "Readest key dispatch ABI changed; S Pen patch cannot be applied safely"
        }
    }
}
