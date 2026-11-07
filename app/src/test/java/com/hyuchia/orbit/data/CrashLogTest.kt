package com.hyuchia.orbit.data

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.io.IOException
import java.io.PrintWriter
import java.io.StringWriter
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
    fun `messages that quote what they read are left out, and single quotes are masked too`() {
        val json = kotlinx.serialization.SerializationException("Unexpected JSON token at offset 12: Expected end of the string. JSON input: {\"text\":\"Call the lawyer")
        val report = log.record("main", IllegalStateException("Couldn't read 'Call the lawyer'", json)).readText()

        assertTrue(report.contains("java.lang.IllegalStateException: Couldn't read \"…\"\n"))
        assertTrue(report.contains("Caused by: kotlinx.serialization.SerializationException\n"))
        assertFalse(report.contains("lawyer"))
        assertEquals("Can't read \"…\", nor \"…\", nor \"…\"", CrashLog.maskMessage("Can't read 'Call', nor ‘the’, nor \"lawyer\"", emptyList()))
        // Whatever follows "JSON input" goes, however it's quoted.
        assertEquals("Unexpected token at 3. ", CrashLog.maskMessage("Unexpected token at 3. JSON input: ...ext\":\"Call the lawyer...", emptyList()))
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

    @Test
    fun `names of one or two letters are masked too, where they stand as words`() {
        assertEquals("Couldn't save … at 3", CrashLog.maskMessage("Couldn't save Yo at 3", listOf("Yo")))
        assertEquals("… is gone, and … too: …'s pictures", CrashLog.maskMessage("Q is gone, and q too: Q's pictures", listOf("Q")))
        // Inside a longer word, the letters are that word's: the message stays readable.
        assertEquals("Your map is gone", CrashLog.maskMessage("Your map is gone", listOf("Yo")))
        // A blank name masks nothing.
        assertEquals("Couldn't save it", CrashLog.maskMessage("Couldn't save it", listOf("", " ")))

        val short = CrashLog(folder, about, hidden = { listOf("Al", "al") }, clock = { now })
        val report = short.record("main", IllegalStateException("Couldn't add to Al, a map")).readText()
        assertTrue(report.contains("java.lang.IllegalStateException: Couldn't add to …, a map\n"))
    }

    @Test
    fun `Android's own handler gets the crash as the report has it, never a map's path or title`() {
        val before = Thread.getDefaultUncaughtExceptionHandler()
        val passed = mutableListOf<Throwable>()
        // Android's handler, which writes what it gets to the phone's log and the system's crash reports.
        Thread.setDefaultUncaughtExceptionHandler { _, error -> passed += error }

        try {
            log.install()
            val crashes = Thread.getDefaultUncaughtExceptionHandler()!!
            val cause = IOException("Couldn't add the map trip-to-lisbon at /data/user/0/com.hyuchia.orbit/files/maps/trip-to-lisbon/map.md")
            val error = IllegalStateException("Couldn't open Trip to Lisbon", cause)
            error.addSuppressed(PageException("- Beach day with Ana"))
            crashes.uncaughtException(Thread.currentThread(), error)

            val given = passed.single()
            val printed = StringWriter().also { given.printStackTrace(PrintWriter(it)) }.toString()
            assertTrue(printed.startsWith("java.lang.IllegalStateException: Couldn't open …\n\tat com.hyuchia.orbit.data.CrashLogTest."))
            assertTrue(printed.contains("Caused by: java.io.IOException: Couldn't add the map … at …\n"))
            assertTrue(printed.contains("Suppressed: com.hyuchia.orbit.data.CrashLogTest\$PageException\n"))
            assertFalse(printed.contains("Lisbon", ignoreCase = true))
            assertFalse(printed.contains("/data/"))
            assertFalse(printed.contains("Beach"))
            assertEquals("Couldn't open …", given.message)
            // Where it happened, exactly as it was.
            assertEquals(error.stackTrace.toList(), given.stackTrace.toList())
            assertEquals(cause.stackTrace.toList(), given.cause!!.stackTrace.toList())
            // And the report is kept, as ever.
            assertEquals(1, log.reports().size)

            // A crash with nothing to mask goes on as it is, its kind the system's to see.
            now += 1_000
            val plain = IllegalArgumentException("A goal can't be below zero")
            crashes.uncaughtException(Thread.currentThread(), plain)
            assertSame(plain, passed.last())

            // Causes that loop end as they do in the report.
            now += 1_000
            val first = IllegalStateException("first Trip to Lisbon")
            val second = IllegalArgumentException("second", first)
            first.initCause(second)
            crashes.uncaughtException(Thread.currentThread(), second)
            val looped = StringWriter().also { passed.last().printStackTrace(PrintWriter(it)) }.toString()
            assertTrue(looped.contains("CIRCULAR REFERENCE"))
            assertFalse(looped.contains("Lisbon"))
        } finally {
            Thread.setDefaultUncaughtExceptionHandler(before)
        }
    }
}
