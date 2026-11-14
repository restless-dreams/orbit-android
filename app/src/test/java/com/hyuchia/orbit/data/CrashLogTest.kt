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

    @Test
    fun `quoted text goes whole, with apostrophes and line breaks of its own, or with no quote to close it`() {
        assertEquals("Couldn't read \"…\"", CrashLog.maskMessage("Couldn't read 'Alice's diary: the lawyer'", emptyList()))
        assertEquals("Couldn't read \"…\"", CrashLog.maskMessage("Couldn't read 'first line\nsecond line'", emptyList()))
        assertEquals("Couldn't read \"…\" at 3", CrashLog.maskMessage("Couldn't read 'James' notes' at 3", emptyList()))
        assertEquals("Couldn't read \"…\"", CrashLog.maskMessage("Couldn't read \"She said \"call\" twice\"", emptyList()))
        assertEquals("Couldn't read \"…\"", CrashLog.maskMessage("Couldn't read ‘Ana’s list’", emptyList()))
        // A message cut short, its quote never closed: what follows it goes.
        assertEquals("Couldn't read \"…\"", CrashLog.maskMessage("Couldn't read 'Call the lawyer", emptyList()))
        assertEquals("Couldn't read \"…\"", CrashLog.maskMessage("Couldn't read “Call the\nlawyer", emptyList()))
        // Every other kind of quote, and a single quote right after a letter, which only an apostrophe in a word would be.
        assertEquals("Couldn't read x\"…\"", CrashLog.maskMessage("Couldn't read x'Ana's diary'", emptyList()))
        assertEquals("Couldn't read 3\"…\" at 4", CrashLog.maskMessage("Couldn't read 3'Ana' at 4", emptyList()))
        assertEquals("Couldn't read \"…\" at 4", CrashLog.maskMessage("Couldn't read ‹Ana's list› at 4", emptyList()))
        assertEquals("Couldn't read \"…\" at 4", CrashLog.maskMessage("Couldn't read `Ana's list` at 4", emptyList()))
        assertEquals("Couldn't read \"…\" at 4", CrashLog.maskMessage("Couldn't read 「アナの日記」 at 4", emptyList()))
        assertEquals("Couldn't read \"…\" at 4", CrashLog.maskMessage("Couldn't read 『アナの日記』 at 4", emptyList()))
        assertEquals("Couldn't read \"…\" at 4", CrashLog.maskMessage("Couldn't read „Annas Tagebuch“ at 4", emptyList()))
        assertEquals("Couldn't read \"…\" at 4", CrashLog.maskMessage("Couldn't read „Dziennik Anny” at 4", emptyList()))
        assertEquals("Couldn't read \"…\"", CrashLog.maskMessage("Couldn't read `Ana's list", emptyList()))
        // Apostrophes inside words, and quotes that close, leave the rest as it was.
        assertEquals("Can't read \"…\" or \"…\": it isn't there", CrashLog.maskMessage("Can't read 'a' or \"b\": it isn't there", emptyList()))
    }

    @Test
    fun `quoted text with apostrophes and line breaks reaches neither the report nor Android`() {
        val before = Thread.getDefaultUncaughtExceptionHandler()
        val passed = mutableListOf<Throwable>()
        Thread.setDefaultUncaughtExceptionHandler { _, error -> passed += error }

        try {
            log.install()
            val crashes = Thread.getDefaultUncaughtExceptionHandler()!!
            val cause = IOException("Couldn't read 'the codicil\nand its secret'")
            crashes.uncaughtException(Thread.currentThread(), IllegalStateException("Couldn't open 'Alice's diary: the will'", cause))

            val report = log.reports().single().text()
            val printed = StringWriter().also { passed.single().printStackTrace(PrintWriter(it)) }.toString()

            for (said in listOf(report, printed)) {
                assertTrue(said.contains("java.lang.IllegalStateException: Couldn't open \"…\"\n"))
                assertTrue(said.contains("Caused by: java.io.IOException: Couldn't read \"…\"\n"))
                assertFalse(said.contains("diary"))
                assertFalse(said.contains("codicil"))
                assertFalse(said.contains("secret"))
            }
        } finally {
            Thread.setDefaultUncaughtExceptionHandler(before)
        }
    }

    @Test
    fun `a report that won't delete stays offered, and its card with it`() {
        log.record("main", IllegalStateException("broken"))
        val report = log.unseen()!!

        // Storage that fails, or a file that won't go.
        val undeletable = object : File(report.file.path) {
            override fun delete(): Boolean = false
        }

        assertFalse(log.delete(CrashReport(report.time, undeletable)))
        assertTrue(report.file.exists())
        assertEquals(report.time, log.unseen()?.time)

        assertTrue(log.delete(report))
        assertNull(log.unseen())
        assertFalse(report.file.exists())
    }

    @Test
    fun `crashes in the same millisecond keep a report each, the latest last`() {
        val first = log.record("main", IllegalStateException("first"))
        val second = log.record("worker", IllegalStateException("second"))

        assertEquals(listOf(second, first), log.reports().map { it.file })
        assertTrue(first.readText().contains("IllegalStateException: first\n"))
        assertTrue(second.readText().contains("IllegalStateException: second\n"))
        // Both say when they were, by the clock.
        assertTrue(second.readText().contains("Time: 2025-10-09T08:53:20Z\n"))
        assertEquals(second, log.unseen()?.file)
    }

    @Test
    fun `a crash after the clock went back is offered, and kept over the earlier ones`() {
        repeat(KEPT_REPORTS) {
            now += 1_000
            log.record("main", IllegalStateException("before"))
        }

        log.markSeen(log.unseen()!!)
        // Set back a day, by hand or by the network.
        now -= 86_400_000L
        val after = log.record("main", IllegalStateException("after the clock went back"))

        assertEquals(after, log.unseen()?.file)
        assertEquals(after, log.reports().first().file)
        assertEquals(KEPT_REPORTS, log.reports().size)
        assertTrue(after.readText().contains("Time: 2025-10-08T08:53:25Z\n"))

        // Deleted, it isn't offered, and the next crash comes after it all the same.
        log.delete(log.unseen()!!)
        now -= 1_000
        val next = log.record("main", IllegalStateException("again"))

        assertEquals(next, log.unseen()?.file)
    }

    @Test
    fun `reports earlier versions kept read as they did, and new ones come after them`() {
        folder.mkdirs()
        // As every version before this one named them: by the time of the crash.
        val earlier = File(folder, "crash-1760000005000.txt").apply { writeText("Orbit crash report\nOrbit 0.5.0 (50), release build\n") }
        File(folder, "seen").writeText("1760000005000")

        assertEquals(listOf(1_760_000_005_000L), log.reports().map { it.time })
        assertNull(log.unseen())

        val next = log.record("main", IllegalStateException("broken"))

        assertEquals(listOf(next, earlier), log.reports().map { it.file })
        assertEquals("crash-1760000005001.txt", next.name)
        assertEquals(next, log.unseen()?.file)
    }
}

/** How many reports are kept. */
private const val KEPT_REPORTS = 5
