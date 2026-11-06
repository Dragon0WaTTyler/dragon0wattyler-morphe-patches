package app.morphe.patches.readest

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import org.w3c.dom.Element
import javax.xml.parsers.DocumentBuilderFactory

class ReadestAirActionsPatchTest {
    @Test
    fun `Samsung actions are unique and bind the requested gestures to logical page keys`() {
        val actions = parse(RemoteActionsResources.XML).getElementsByTagName("action")
        assertEquals(2, actions.length)

        val parsed = (0 until actions.length).map { actions.item(it) as Element }
        assertEquals(listOf("readest_page_next", "readest_page_previous"), parsed.map { it.getAttribute("id") })
        assertEquals(
            listOf("@string/readest_s_pen_next_page", "@string/readest_s_pen_previous_page"),
            parsed.map { it.getAttribute("label") },
        )
        assertEquals(listOf("1", "2"), parsed.map { it.getAttribute("priority") })
        assertEquals(listOf("PAGE_DOWN", "PAGE_UP"), parsed.map { it.getAttribute("trigger_key") })
        assertEquals(listOf("click|swipe_left", "double_click|swipe_right"), parsed.map { action ->
            val preferences = action.getElementsByTagName("preference")
            (0 until preferences.length)
                .map { preferences.item(it) as Element }
                .single { it.getAttribute("name") == "gesture" }
                .getAttribute("value")
        })
        assertTrue(parsed.all { it.getAttribute("repeatable") == "false" })
        assertEquals(2, parsed.map { it.getAttribute("priority") }.toSet().size)
        parsed.forEach { action ->
            val preferences = action.getElementsByTagName("preference")
            assertEquals("false", (0 until preferences.length)
                .map { preferences.item(it) as Element }
                .single { it.getAttribute("name") == "button_only" }
                .getAttribute("value"))
            assertEquals("false", (0 until preferences.length)
                .map { preferences.item(it) as Element }
                .single { it.getAttribute("name") == "motion_only" }
                .getAttribute("value"))
        }
    }

    @Test
    fun `manifest registration targets only Readest MainActivity`() {
        val manifest = parse(
            """<manifest xmlns:android="$ANDROID_NAMESPACE" package="com.bilingify.readest">
                <application><activity android:name="$MAIN_ACTIVITY" /></application>
            </manifest>""",
        )

        RemoteActionsResources.registerInManifest(manifest)

        val activity = manifest.getElementsByTagName("activity").item(0) as Element
        val metadata = activity.getElementsByTagName("meta-data").item(0) as Element
        assertEquals(REMOTE_ACTION, metadata.getAttributeNS(ANDROID_NAMESPACE, "name"))
        assertEquals("@xml/readest_s_pen_air_actions", metadata.getAttributeNS(ANDROID_NAMESPACE, "resource"))
        val action = activity.getElementsByTagName("action").item(0) as Element
        assertEquals(REMOTE_ACTION, action.getAttributeNS(ANDROID_NAMESPACE, "name"))
    }

    @Test
    fun `manifest registration fails closed if the activity is absent or already registered`() {
        val missingActivity = parse("""<manifest xmlns:android="$ANDROID_NAMESPACE"><application /></manifest>""")
        val missingActivityFailure = runCatching {
            RemoteActionsResources.registerInManifest(missingActivity)
        }.exceptionOrNull()
        assertNotNull(missingActivityFailure)
        assertTrue(missingActivityFailure.message.orEmpty().contains("MainActivity"))

        val alreadyRegistered = parse(
            """<manifest xmlns:android="$ANDROID_NAMESPACE"><application>
                <activity android:name="$MAIN_ACTIVITY">
                    <meta-data android:name="$REMOTE_ACTION" android:resource="@xml/other" />
                </activity>
            </application></manifest>""",
        )
        assertNotNull(runCatching { RemoteActionsResources.registerInManifest(alreadyRegistered) }.exceptionOrNull())
    }

    private fun parse(xml: String) = DocumentBuilderFactory.newInstance().apply {
        isNamespaceAware = true
    }.newDocumentBuilder().parse(xml.byteInputStream())
}
