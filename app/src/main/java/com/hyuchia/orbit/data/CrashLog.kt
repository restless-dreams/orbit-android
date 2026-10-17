package com.hyuchia.orbit.data

import java.io.File
import java.time.Instant
import java.util.IdentityHashMap
import kotlin.system.exitProcess

/** Reports kept: the latest crashes. */
private const val KEPT = 5
/** Stack lines kept for each exception: where it happened, and how it got there. */
private const val FRAMES = 60
/** Causes followed, one inside the next. */
private const val CAUSES = 10
private const val PREFIX = "crash-"
private const val SUFFIX = ".txt"
/** Holds when the latest report the home screen showed was written. */
private const val SEEN = "seen"

/** Packages whose exceptions say what went wrong in their own words: Orbit's own, and others', can carry what people wrote. */
private val PLATFORM = listOf("java.", "javax.", "kotlin.", "kotlinx.", "android.", "androidx.", "dalvik.", "libcore.")

/** Links and addresses of any kind: content://, https://, file://. */
private val ADDRESS = Regex("""\b[A-Za-z][A-Za-z0-9+.-]*://\S+""")
/** Paths to files: /data/user/0/…/maps/trip/map.md. */
private val PATH = Regex("""(?:/[^\s/:'"“”]+){2,}/?""")
/** Text in double or curly quotes: a title, a line of a map. */
private val QUOTED = Regex(""""[^"]*"|“[^”]*”|«[^»]*»""")

/** A crash kept on the phone: when it was, and its report. */
class CrashReport(val time: Long, val file: File) {
    fun text(): String = file.readText()
}

/**
 * Keeps a report of each crash, on the phone only, so it can be shared to
 * get it fixed without Google's crash reporting: the app's and Android's
 * versions (`about`), and where in the code it happened. Nothing from the
 * maps goes in: messages that could hold what people wrote are left out or
 * have it masked, along with every map's name and title from `hidden`.
 * The last few reports are kept; the home screen offers the latest once.
 */
class CrashLog(
    private val folder: File,
    private val about: String,
    private val hidden: () -> Collection<String> = { emptyList() },
    private val clock: () -> Long = System::currentTimeMillis,
) {
    /** Writes a report of every crash from now on, then lets the app end as it would have. */
    fun install() {
        val previous = Thread.getDefaultUncaughtExceptionHandler()

        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            try {
                record(thread.name, error)
            } catch (ignored: Throwable) {
                // Nothing more to do: the crash goes on as it would have.
            }

            if (previous != null) {
                previous.uncaughtException(thread, error)
            } else {
                exitProcess(10)
            }
        }
    }

    /** Keeps a report of `error`, which ended the thread `thread`, and lets the oldest go past the last few. */
    fun record(thread: String, error: Throwable): File {
        val time = clock()
        val masked = runCatching { hidden() }.getOrDefault(emptyList())

        val report = buildString {
            append("Orbit crash report\n")
            append(about).append('\n')
            append("Time: ").append(Instant.ofEpochMilli(time)).append('\n')
            append("Thread: ").append(thread).append("\n\n")
            describe(error, masked, this)
        }

        folder.mkdirs()
        val file = File(folder, "$PREFIX$time$SUFFIX")
        writeAtomically(file, report)
        reports().drop(KEPT).forEach { it.file.delete() }
        return file
    }

    /** The reports kept, the latest first. */
    fun reports(): List<CrashReport> =
        folder.listFiles { file -> file.name.startsWith(PREFIX) && file.name.endsWith(SUFFIX) }.orEmpty()
            .mapNotNull { file -> file.name.removePrefix(PREFIX).removeSuffix(SUFFIX).toLongOrNull()?.let { CrashReport(it, file) } }
            .sortedByDescending { it.time }

    /** The latest crash, unless the home screen showed it already. */
    fun unseen(): CrashReport? {
        val latest = reports().firstOrNull() ?: return null
        val seen = runCatching { File(folder, SEEN).readText().trim().toLong() }.getOrDefault(0L)
        return latest.takeIf { it.time > seen }
    }

    /** The home screen showed `report`: it isn't offered again. */
    fun markSeen(report: CrashReport) {
        folder.mkdirs()
        writeAtomically(File(folder, SEEN), report.time.toString())
    }

    fun delete(report: CrashReport) {
        markSeen(report)
        report.file.delete()
    }

    private fun describe(error: Throwable, masked: Collection<String>, report: StringBuilder) {
        val seen = IdentityHashMap<Throwable, Unit>()
        var current: Throwable? = error
        var depth = 0

        while (current != null && depth < CAUSES && seen.put(current, Unit) == null) {
            if (depth > 0) {
                report.append("Caused by: ")
            }

            report.append(current.javaClass.name)
            message(current, masked)?.let { report.append(": ").append(it) }
            report.append('\n')
            current.stackTrace.take(FRAMES).forEach { frame -> report.append("\tat ").append(frame).append('\n') }

            if (current.stackTrace.size > FRAMES) {
                report.append("\t… ").append(current.stackTrace.size - FRAMES).append(" more\n")
            }

            current = current.cause
            depth += 1
        }
    }

    private fun message(error: Throwable, masked: Collection<String>): String? {
        val message = error.message?.takeIf { it.isNotBlank() } ?: return null

        if (PLATFORM.none { error.javaClass.name.startsWith(it) }) {
            return null
        }

        return maskMessage(message, masked)
    }

    companion object {
        /**
         * A platform exception's message without what could be someone's:
         * links, paths, quoted text, and each of `masked` (maps' names and
         * titles) are replaced with "…".
         */
        fun maskMessage(message: String, masked: Collection<String>): String {
            var text = ADDRESS.replace(message, "…")
            text = PATH.replace(text, "…")
            text = QUOTED.replace(text, "\"…\"")

            masked.filter { it.length >= 3 }.sortedByDescending { it.length }.forEach { word ->
                text = text.replace(word, "…", ignoreCase = true)
            }

            return text.take(500)
        }
    }
}
