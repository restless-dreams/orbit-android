package com.hyuchia.orbit.study

import com.hyuchia.orbit.Logs
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.io.IOException

/** How long after an answer the storage couldn't take it's tried again, while it's still unwritten: as long as a map's text waits. */
const val ANSWER_RETRY_MILLISECONDS = 15_000L

/** Why answers given in study mode aren't in the map's details yet. */
enum class AnswersUnsaved {
    /** The device's storage couldn't take them, full most likely: they're tried again until it does. */
    Storage,

    /** A newer version of Orbit keeps the maps, so this one changes none of them: not until Orbit is updated. */
    Newer,
}

/**
 * The answers given in study mode on their way to the maps' details, each
 * map's in a line of its own ([StudyWriting]), for as long as Orbit runs: a
 * map's screen that closes leaves its answers here, and one that opens it
 * again, or another beside it, counts them. Written on `scope`, the app's,
 * by `keep`; why one couldn't be is `problem`'s to say.
 */
class StudyWrites(
    private val scope: CoroutineScope,
    private val keep: suspend (id: String, change: StudyChange) -> Unit,
    private val retryAfter: Long = ANSWER_RETRY_MILLISECONDS,
    private val problem: (IOException) -> AnswersUnsaved = { AnswersUnsaved.Storage },
) {
    private val lines = HashMap<String, StudyWriting>()

    /** Map `id`'s answers on their way to its details. */
    @Synchronized
    fun of(id: String): StudyWriting = lines.getOrPut(id) { StudyWriting(scope, { change -> keep(id, change) }, retryAfter, problem) }
}

/**
 * A map's answers on their way to its details: each change to them
 * ([StudyChange]) written one after another, in the order they were made,
 * on `scope`. A change counts as written only once `keep` wrote it. One the
 * storage couldn't take, full or kept by a newer Orbit (an [IOException]),
 * stays first in line, and those after it wait behind it, so an answer taken
 * back is never written before the answer: it's tried again `retryAfter`
 * milliseconds later, as soon as another change comes, or when asked
 * ([retry]), for as long as Orbit runs, and [problem] says why meanwhile.
 * They're only in memory, as a map's unsaved text is: Android stopping
 * Orbit loses them, which [problem] said. Another failure, which trying
 * again wouldn't mend, loses that one change alone.
 */
class StudyWriting(
    scope: CoroutineScope,
    private val keep: suspend (StudyChange) -> Unit,
    private val retryAfter: Long = ANSWER_RETRY_MILLISECONDS,
    private val problemOf: (IOException) -> AnswersUnsaved = { AnswersUnsaved.Storage },
) {
    /** The changes made and not written yet, oldest first: a session counts them ([unwritten]). */
    private val waiting = ArrayDeque<StudyChange>()

    /** How many changes were made, and how many of them are written. */
    private var made = 0L

    private val writtenCount = MutableStateFlow(0L)

    /** Wakes the writing for a change just made, or to try again now. */
    private val wake = Channel<Unit>(Channel.CONFLATED)

    private val unsaved = MutableStateFlow<AnswersUnsaved?>(null)

    /** Why the answers given aren't all written, while one couldn't be; null once they are. */
    val problem: StateFlow<AnswersUnsaved?> = unsaved.asStateFlow()

    init {
        scope.launch { write() }
    }

    /** Makes `change` to the map's answers, after the ones made before it. */
    fun add(change: StudyChange) {
        synchronized(waiting) {
            waiting.addLast(change)
            made += 1
        }

        wake.trySend(Unit)
    }

    /**
     * Reads the map's answers with the changes not written yet (`read` gets
     * them, oldest first), so none counts twice or not at all: a change only
     * stops being among them once the map's details have it.
     */
    fun <T> unwritten(read: (List<StudyChange>) -> T): T = synchronized(waiting) { read(waiting.toList()) }

    /** Tries the change the storage couldn't take again now. */
    fun retry() {
        wake.trySend(Unit)
    }

    /** Waits until every change made so far is written: then the folder the maps are kept in can take them. */
    suspend fun written() {
        val given = synchronized(waiting) { made }
        writtenCount.first { it >= given }
    }

    private suspend fun write() {
        while (true) {
            val change = synchronized(waiting) { waiting.firstOrNull() }

            if (change == null) {
                wake.receive()
                continue
            }

            // Only a change made, or a retry asked for, from now on brings the next try sooner.
            wake.tryReceive()

            try {
                keep(change)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failed: IOException) {
                // Said once, not at every try: it stays first in line, and the ones after it wait.
                if (unsaved.value == null) {
                    Logs.warn("OrbitStudy", "Couldn't keep an answer: it's tried again until it is", failed)
                }

                unsaved.value = problemOf(failed)
                withTimeoutOrNull(retryAfter) { wake.receive() }
                continue
            } catch (failed: Exception) {
                // An answer that couldn't be kept for another reason is lost alone: the ones after it are still written.
                Logs.warn("OrbitStudy", "Couldn't keep an answer", failed)
            }

            val done = synchronized(waiting) {
                waiting.removeFirst()
                waiting.isEmpty()
            }

            writtenCount.update { it + 1 }

            if (done && unsaved.value != null) {
                Logs.note("OrbitStudy", "The answers that couldn't be kept are now")
                unsaved.value = null
            }
        }
    }
}
