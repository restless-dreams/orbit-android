package com.hyuchia.orbit.map

import com.hyuchia.orbit.page.MapFile
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

/** What a copy of an idea's file is called for the app it opens in: never a path, nor a name a file system refuses. */
class IdeaFileViewsTest {
    private val local = File("/maps/trip/media/file-boarding-pass.pdf")

    @Test
    fun `a copy goes by the name people know, made one every file system takes`() {
        assertEquals("Boarding pass.pdf", copyName(MapFile("media/file-boarding-pass.pdf", name = "Boarding pass.pdf"), local))
        assertEquals("Hotel booking final.pdf", copyName(MapFile("media/file-a.pdf", name = "Hotel: booking/final.pdf"), local))
        assertEquals("etc passwd", copyName(MapFile("media/file-a", name = "../../etc/passwd"), local))
        assertEquals("file-boarding-pass.pdf", copyName(MapFile("media/file-boarding-pass.pdf", name = "///"), local))
        assertEquals("plan.pdf", copyName(MapFile("media/plan.pdf"), local))
        assertEquals(104, copyName(MapFile("media/a.pdf", name = "a".repeat(300) + ".pdf"), local).length)
    }
}
