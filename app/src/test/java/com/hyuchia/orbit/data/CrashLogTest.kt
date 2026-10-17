package com.hyuchia.orbit.data

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.io.IOException
import java.nio.file.Files

class CrashLogTest {
    private val folder: File = Files.createTempDirectory("orbit-crashes").toFile()
    private var now = 1_760_000_000_000L
    private val about = "Orbit 1.0 (10), release build\nAndroid 15 (API 35) on Google Pixel 8"
    private val log = CrashLog(folder, about, hidden = { listOf("trip-to-lisbon", "Trip to Lisbon") }, clock = { now })

    /** An exception of Orbit's own, whose message could hold anything. */
    private class PageException(message: String) : Exception(message)

    @After
    fun cleanUp() {
        folder.deleteRecursively()
    }

    @Test
    fun `a report has the versions and where it happened, and nothing from the maps`() {
        val cause = IOException("Couldn't add the map trip-to-lisbon at /data/user/0/com.hyuchia.orbit/files/maps/trip-to-lisbon/map.md")
        val error = IllegalStateException("Couldn't open “Trip to Lisbon” from content://media/external/file/12", cause)
        error.addSuppressed(PageException("- Beach day with Ana"))
        val report = log.record("main", error).readText()

        assertTrue(report.startsWith("Orbit crash report\n$about\nTime: 2025-10-09T08:53:20Z\nThread: main\n\n"))
        assertTrue(report.contains("java.lang.IllegalStateException: Couldn't open \"…\" from …\n"))
        assertTrue(report.contains("\tat com.hyuchia.orbit.data.CrashLogTest."))
        assertTrue(report.contains("Caused by: java.io.IOException: Couldn't add the map … at …\n"))
        assertFalse(report.contains("Lisbon"))
        assertFalse(report.contains("lisbon"))
        assertFalse(report.contains("Beach"))
    }

    @Test
    fun `an exception of Orbit's own is kept without its message`() {
        val report = log.record("DefaultDispatcher-worker-1", PageException("# Trip\n- Beach day")).readText()
        assertTrue(report.contains("com.hyuchia.orbit.data.CrashLogTest\$PageException\n"))
        assertFalse(report.contains("Beach"))
    }

    @Test
    fun `the last five reports are kept, and the latest is offered once`() {
        assertNull(log.unseen())

        repeat(7) {
            now += 1_000
            log.record("main", IllegalStateException("broken"))
        }

        assertEquals(5, log.reports().size)
        val latest = log.unseen()!!
        assertEquals(now, latest.time)

        log.markSeen(latest)
        assertNull(log.unseen())
        now += 1_000
        log.record("main", IllegalStateException("broken again"))
        val next = log.unseen()!!
        log.delete(next)
        assertNull(log.unseen())
        assertFalse(next.file.exists())
        assertEquals(4, log.reports().size)
    }

    @Test
    fun `long stacks and cause loops end`() {
        val first = IllegalStateException("first")
        val second = IllegalArgumentException("second", first)
        first.initCause(second)
        fun deep(depth: Int): Throwable = if (depth == 0) IllegalStateException("deep") else deep(depth - 1)
        val report = log.record("main", second).readText()
        assertEquals(1, Regex("Caused by: java.lang.IllegalStateException").findAll(report).count())
        val stack = log.record("main", deep(200)).readText()
        assertTrue(stack.contains(" more\n"))
    }
}
