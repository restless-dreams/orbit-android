package com.hyuchia.orbit.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class OtherAppsTest {
    private fun zipOf(vararg names: String): ByteArray {
        val bytes = ByteArrayOutputStream()

        ZipOutputStream(bytes).use { zip ->
            names.forEach { name ->
                zip.putNextEntry(ZipEntry(name))
                zip.write("content of $name".toByteArray())
                zip.closeEntry()
            }
        }

        return bytes.toByteArray()
    }

    private fun kindOf(bytes: ByteArray, name: String = "", type: String = "application/octet-stream"): FileKind {
        val zipNamed = name.endsWith(".zip") || type == "application/zip"
        return OtherApps.kindOf(name, type, zipNamed, ByteArrayInputStream(bytes))
    }

    @Test
    fun namesAndTypesTellTheFormat() {
        assertEquals(OtherFormat.Opml, OtherApps.formatByName("Trip.OPML", ""))
        assertEquals(OtherFormat.FreeMind, OtherApps.formatByName("launch.mm", "application/octet-stream"))
        assertEquals(OtherFormat.XMind, OtherApps.formatByName("Plan.xmind", ""))
        assertEquals(OtherFormat.Opml, OtherApps.formatByName("", "text/x-opml; charset=utf-8"))
        assertEquals(OtherFormat.FreeMind, OtherApps.formatByName("", "application/x-freemind"))
        assertEquals(OtherFormat.XMind, OtherApps.formatByName("", "application/vnd.xmind.workbook"))
        assertNull(OtherApps.formatByName("notes.md", "text/markdown"))
    }

    @Test
    fun theFirstElementTellsXml() {
        assertEquals(OtherFormat.Opml, OtherApps.formatOfText("﻿<?xml version=\"1.0\"?>\n<!-- exported -->\n<!DOCTYPE opml>\n<opml version=\"2.0\">"))
        assertEquals(OtherFormat.FreeMind, OtherApps.formatOfText("<map version=\"freeplane 1.11.5\">"))
        assertEquals(OtherFormat.XMind, OtherApps.formatOfText("<?xml version=\"1.0\" encoding=\"UTF-8\"?><xmap-content xmlns=\"urn:xmind:xmap:xmlns:content:2.0\">"))
        assertNull(OtherApps.formatOfText("# Map\n<map>"))
        assertNull(OtherApps.formatOfText("<html><body>map</body></html>"))
    }

    @Test
    fun filesWithoutANameAreToldByWhatTheyHold() {
        assertEquals(FileKind.FromOtherApp(OtherFormat.Opml), kindOf("<opml version=\"1.0\"><body/></opml>".toByteArray()))
        val utf16 = byteArrayOf(0xFF.toByte(), 0xFE.toByte()) + "<map version=\"1.0.1\">".toByteArray(Charsets.UTF_16LE)
        assertEquals(FileKind.FromOtherApp(OtherFormat.FreeMind), kindOf(utf16))
        assertEquals(FileKind.Text, kindOf("# Trip\n## Food".toByteArray()))
        assertEquals(FileKind.Text, kindOf("nodes:\n  - text: Trip".toByteArray(), "trip.yaml", "application/yaml"))
    }

    @Test
    fun aWorkbookIsAZipThatHoldsXmindsContent() {
        assertEquals(FileKind.FromOtherApp(OtherFormat.XMind), kindOf(zipOf("resources/a.png", "content.json", "manifest.json")))
        assertEquals(FileKind.FromOtherApp(OtherFormat.XMind), kindOf(zipOf("meta.xml", "content.xml", "META-INF/manifest.xml")))
        assertEquals(FileKind.Zip, kindOf(zipOf("map.md", "media/a.png", "orbit.json")))
    }

    @Test
    fun aZipCalledOneIsOrbitsWhateverItHolds() {
        assertEquals(FileKind.Zip, kindOf(zipOf("content.json"), "maps.zip"))
        assertEquals(FileKind.Zip, kindOf("not really a zip".toByteArray(), "maps.zip"))
        assertEquals(FileKind.FromOtherApp(OtherFormat.XMind), kindOf(zipOf("map.md"), "Plan.xmind"))
    }

    @Test
    fun anOriginalFromAnotherAppCountsItsIdeasAsAMapsAreCountedWithoutItsCenter() {
        // Two top outlines share a center Orbit makes up: all three are ideas.
        assertEquals(3, OtherApps.ideasIn("<opml><body><outline text=\"a\"><outline text=\"b\"/></outline><outline text=\"c\"/></body></opml>"))
        // One top outline is the center.
        assertEquals(2, OtherApps.ideasIn("<opml><body><outline text=\"Trip\"><outline text=\"a\"/><outline text=\"b\"></outline></outline></body></opml>"))
        assertEquals(1, OtherApps.ideasIn("<map version=\"1.0.1\"><node TEXT=\"a\"><node TEXT=\"b\"/></node><stylenode/></map>"))
        assertNull(OtherApps.ideasIn("# Trip\n## Food"))
    }
}
