package com.hyuchia.orbit.data

import java.io.File
import java.io.FileDescriptor
import java.io.FileOutputStream
import java.io.PrintStream
import java.nio.file.FileAlreadyExistsException
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

/** Platform exceptions whose messages quote what they read, such as a map's JSON or a typed pattern: kept without them. */
private val QUOTING = listOf("kotlinx.serialization.", "java.util.regex.")

/** Links and addresses of any kind: content://, https://, file://. */
private val ADDRESS = Regex("""\b[A-Za-z][A-Za-z0-9+.-]*://\S+""")
/** Paths to files: /data/user/0/…/maps/trip/map.md. */
private val PATH = Regex("""(?:/[^\s/:'"“”]+){2,}/?""")
/** An apostrophe inside a word, as in "can't", "it's" or "Ana's": not a quote, unlike any other single quote mark. */
private const val APOSTROPHE = """(?<=[\p{L}\p{N}])'(?i:t|s|d|m|ll|re|ve)(?![\p{L}\p{N}])"""

/** A single quote mark that isn't such an apostrophe, wherever it is: right after a letter too, as in x'Ana's diary'. */
private const val SINGLE_QUOTE = """(?!$APOSTROPHE)'"""

/**
 * Quoted text, a title or a line of a map, in any of the quotes messages
 * use: double, curly, low („…“), angled, corner, backticks or single. Each
 * runs from its opening mark to the last closing one after it, across
 * lines, as what's quoted can have quotes and line breaks of its own
 * ('Ana's notes'), or to the end when none closes it, as a message cut
 * short. When in doubt it takes more, never less: two quotes and the words
 * between them go as one.
 */
private val QUOTED = Regex(
    listOf(
        """"[\s\S]*"""",
        """“[\s\S]*”""",
        """„[\s\S]*[“”]""",
        """«[\s\S]*»""",
        """‹[\s\S]*›""",
        """‘[\s\S]*’""",
        """「[\s\S]*」""",
        """『[\s\S]*』""",
        """`[\s\S]*`""",
        """$SINGLE_QUOTE[\s\S]*$SINGLE_QUOTE""",
        """(?:["“„«‹‘「『`]|$SINGLE_QUOTE)[\s\S]*""",
    ).joinToString("|"),
)

/**
 * A crash kept on the phone: its place among them, `time`, and its report.
 * Its place is when it was, in milliseconds since 1970, unless the clock
 * says that's no later than the latest one's (two crashes in a millisecond,
 * or a clock set back): then it's just after it, so each one keeps its own
 * report and the latest is always the last one. Its report says when it
 * was, by the clock.
 */
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
    /**
     * Writes a report of every crash from now on, then lets the app end as
     * it would have. Android's own handler writes what it's given to the
     * phone's log and hands it to the system's crash reports, which can
     * leave the phone: it gets the crash as the report has it, never a map's
     * path or name ([passedOn]).
     */
    fun install() {
        val previous = Thread.getDefaultUncaughtExceptionHandler()

        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            val masked = runCatching { hidden() }.getOrDefault(emptyList())

            try {
                record(thread.name, error, masked)
            } catch (ignored: Throwable) {
                // Nothing more to do: the crash goes on as it would have.
            }

            if (previous != null) {
                // Should making its copy fail, as out of memory, what failed goes on instead: it says nothing of the maps either.
                previous.uncaughtException(thread, runCatching { passedOn(error, masked) }.getOrElse { it })
            } else {
                // Only a plain JVM gets here, such as the unit tests': Android always has a handler before this one. The
                // process would end without a word, so the error goes to its standard error first, past any test runner
                // that takes over System.err, for whoever runs it to see why.
                val standardError = PrintStream(FileOutputStream(FileDescriptor.err), true)
                standardError.println("Exception in thread \"${thread.name}\"")
                error.printStackTrace(standardError)
                exitProcess(10)
            }
        }
    }

    /** Keeps a report of `error`, which ended the thread `thread`, and lets the oldest go past the last few. */
    fun record(thread: String, error: Throwable): File = record(thread, error, runCatching { hidden() }.getOrDefault(emptyList()))

    private fun record(thread: String, error: Throwable, masked: Collection<String>): File {
        val time = clock()

        val report = buildString {
            append("Orbit crash report\n")
            append(about).append('\n')
            append("Time: ").append(Instant.ofEpochMilli(time)).append('\n')
            append("Thread: ").append(thread).append("\n\n")
            describe(error, masked, this)
        }

        // Threads that crash at once take their places one after the other.
        synchronized(this) {
            folder.mkdirs()
            // After the latest report, and after the one the home screen showed last, even when that one is gone: never
            // in an earlier one's place, where it would hide, or take its name and write over it.
            var place = maxOf(time, (reports().firstOrNull()?.time ?: Long.MIN_VALUE) + 1, seen() + 1)

            while (true) {
                val file = File(folder, "$PREFIX$place$SUFFIX")

                try {
                    writeNew(file) { output -> output.write(report.toByteArray()) }
                } catch (taken: FileAlreadyExistsException) {
                    place += 1
                    continue
                }

                reports().drop(KEPT).forEach { it.file.delete() }
                return file
            }
        }
    }

    /** The reports kept, the latest first. */
    fun reports(): List<CrashReport> =
        folder.listFiles { file -> file.name.startsWith(PREFIX) && file.name.endsWith(SUFFIX) }.orEmpty()
            .mapNotNull { file -> file.name.removePrefix(PREFIX).removeSuffix(SUFFIX).toLongOrNull()?.let { CrashReport(it, file) } }
            .sortedByDescending { it.time }

    /** The latest crash, unless the home screen showed it already. */
    fun unseen(): CrashReport? = reports().firstOrNull()?.takeIf { it.time > seen() }

    /** The place of the latest report the home screen showed, or 0 before it showed any. */
    private fun seen(): Long = runCatching { File(folder, SEEN).readText().trim().toLong() }.getOrDefault(0L)

    /** The home screen showed `report`: it isn't offered again. */
    fun markSeen(report: CrashReport) {
        folder.mkdirs()
        writeAtomically(File(folder, SEEN), report.time.toString())
    }

    /** Deletes `report`, which isn't offered again; false when it couldn't be deleted, and stays offered as it was. */
    fun delete(report: CrashReport): Boolean {
        if (!report.file.delete() && report.file.exists()) {
            return false
        }

        // It's gone: an older one the home screen never showed isn't offered in its place, where the phone can keep that.
        runCatching { markSeen(report) }
        return true
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

        if (PLATFORM.none { error.javaClass.name.startsWith(it) } || QUOTING.any { error.javaClass.name.startsWith(it) }) {
            return null
        }

        return maskMessage(message, masked)
    }

    /**
     * `error` as it goes on to Android once its report is kept: itself while
     * each exception in it says no more than the report does, and otherwise
     * a copy that prints as it does, each exception's kind, stack, causes and
     * suppressed exceptions as they were, with the messages the report has.
     */
    internal fun passedOn(error: Throwable, masked: Collection<String>): Throwable {
        val messages = IdentityHashMap<Throwable, String?>()

        fun mask(each: Throwable): String? = messages.getOrPut(each) { runCatching { message(each, masked) }.getOrNull() }

        return try {
            if (chain(error).all { each -> each.toString() == shown(each.javaClass.name, mask(each)) }) error else copy(error, ::mask, IdentityHashMap())
        } catch (failed: Throwable) {
            // Whatever goes wrong copying it, nothing of the maps goes on: the kind of crash, where it happened, and no words.
            MaskedThrowable(error.javaClass.name, null).also { it.stackTrace = error.stackTrace }
        }
    }

    /** `error` and every exception it holds: its causes, and those suppressed by it and by them. */
    private fun chain(error: Throwable): Collection<Throwable> {
        val found = IdentityHashMap<Throwable, Unit>()
        val waiting = ArrayDeque(listOf(error))

        while (waiting.isNotEmpty()) {
            val each = waiting.removeFirst()

            if (found.put(each, Unit) == null) {
                each.cause?.let(waiting::add)
                waiting.addAll(each.suppressed)
            }
        }

        return found.keys
    }

    /** A copy of `error`, as [passedOn] makes it, with each message from `message`; `copies` keeps loops of causes looping. */
    private fun copy(error: Throwable, message: (Throwable) -> String?, copies: IdentityHashMap<Throwable, Throwable>): Throwable {
        copies[error]?.let { return it }
        val copied = MaskedThrowable(error.javaClass.name, message(error))
        copies[error] = copied
        copied.stackTrace = error.stackTrace
        error.cause?.let { copied.initCause(copy(it, message, copies)) }
        error.suppressed.forEach { copied.addSuppressed(copy(it, message, copies)) }
        return copied
    }

    companion object {
        /**
         * A platform exception's message without what could be someone's:
         * links, paths, quoted text, and each of `masked` (maps' names and
         * titles) are replaced with "…": one of three letters or more
         * wherever it is, and a shorter one where it stands as a word, so a
         * map called "Al" goes without taking every "al" in the message.
         */
        fun maskMessage(message: String, masked: Collection<String>): String {
            // What a parser read follows "JSON input": whatever quotes it has, it's left out whole.
            var text = ADDRESS.replace(message.substringBefore("JSON input"), "…")
            text = PATH.replace(text, "…")
            text = QUOTED.replace(text, "\"…\"")

            masked.filter { it.isNotBlank() }.sortedByDescending { it.length }.forEach { word ->
                text = if (word.length >= 3) {
                    text.replace(word, "…", ignoreCase = true)
                } else {
                    text.replace(Regex("""(?<![\p{L}\p{N}])${Regex.escape(word)}(?![\p{L}\p{N}])""", RegexOption.IGNORE_CASE), "…")
                }
            }

            return text.take(500)
        }
    }
}

/** How an exception prints itself: its kind, then its message, if it has one. */
private fun shown(kind: String, message: String?): String = message?.let { "$kind: $it" } ?: kind

/**
 * An exception as Orbit passes it on to Android ([CrashLog.passedOn]): it
 * prints as the one it stands for, of the kind `kind`, with only `message`,
 * the words the crash report has of that one's message.
 */
private class MaskedThrowable(private val kind: String, message: String?) : Throwable(message) {
    override fun toString(): String = shown(kind, message)
}
