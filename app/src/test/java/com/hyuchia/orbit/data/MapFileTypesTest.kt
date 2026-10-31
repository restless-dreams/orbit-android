package com.hyuchia.orbit.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/** The manifest's filters for opening and sharing files take the types maps come as, as `MapFileTypes` lists them. */
class MapFileTypesTest {
    /** The main activity's intent filters, from the manifest in the module's folder, where Gradle runs its tests. */
    private val filters: List<Element> = run {
        val manifest = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(File("src/main/AndroidManifest.xml")).documentElement
        val activity = manifest.elements("activity").single { it.getAttribute("android:name") == ".MainActivity" }
        activity.elements("intent-filter")
    }

    private fun Element.elements(tag: String): List<Element> = getElementsByTagName(tag).let { nodes -> (0 until nodes.length).map { nodes.item(it) as Element } }

    private fun Element.values(tag: String, attribute: String): Set<String> = elements(tag).map { it.getAttribute(attribute) }.filter { it.isNotEmpty() }.toSet()

    private fun Element.action(): String = values("action", "android:name").single()

    private fun Element.types(): Set<String> = values("data", "android:mimeType")

    @Test
    fun `files open with Orbit for every type a map comes as`() {
        val typed = filters.single { it.action() == "android.intent.action.VIEW" && MapFileTypes.ANY_FILE !in it.types() }
        assertEquals(MapFileTypes.MAPS, typed.types())
    }

    @Test
    fun `files shared to Orbit become maps for every type a map comes as, and text`() {
        val shared = filters.single { it.action() == "android.intent.action.SEND" }
        assertEquals(MapFileTypes.MAPS + "text/plain", shared.types())
    }

    @Test
    fun `files that say nothing of what they are open with Orbit only when they're named as OPML or XMind`() {
        // Android gives these two no type of their own, so file managers open them as application/octet-stream. It
        // knows FreeMind's .mm as application/x-freemind, and other files have a type, or are no map anyone would open.
        val untyped = filters.single { it.action() == "android.intent.action.VIEW" && MapFileTypes.ANY_FILE in it.types() }
        assertEquals(setOf(MapFileTypes.ANY_FILE), untyped.types())
        assertEquals(setOf(".opml", ".xmind"), untyped.values("data", "android:pathSuffix"))
        assertEquals(OtherFormat.Opml.extensions + OtherFormat.XMind.extensions, listOf("opml", "xmind"))
    }

    @Test
    fun `the file picker offers every type a map comes as, OPML that says it's XML among them, and Restore every zip`() {
        assertTrue(MapFileTypes.FILE_PICKER.containsAll(MapFileTypes.MAPS + MapFileTypes.XML))
        assertEquals(MapFileTypes.ZIP + MapFileTypes.ANY_FILE, MapFileTypes.ZIPS_PICKER.toSet())
    }

    @Test
    fun `every type and extension says the same format wherever it's read`() {
        OtherFormat.entries.forEach { format ->
            format.types.forEach { assertEquals(format, OtherApps.formatByName("", it)) }
            format.extensions.forEach { assertEquals(format, OtherApps.formatByName("map.$it", "")) }
        }

        assertEquals(setOf("md", "markdown", "yaml", "yml", "zip", "opml", "mm", "xmind"), MapFileTypes.EXTENSIONS)
        assertEquals(DroppedItem.MapFile("content://maps/1", "Plan.yml"), droppedFile("content://maps/1", "application/octet-stream", "Plan.yml"))
        assertEquals(DroppedItem.MapFile("content://maps/2", null), droppedFile("content://maps/2", "application/x-zip", null))
        assertEquals(DropKind.Maps, dropKindOf("application/x-opml"))
    }
}
