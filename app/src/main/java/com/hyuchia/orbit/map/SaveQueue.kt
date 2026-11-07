package com.hyuchia.orbit.map

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** A text to save, and the catch-up it was made before: one made before the map changed elsewhere isn't written. */
internal class Save(val text: String, val generation: Int)

/**
 * A map's saves, written one at a time, in turn. Two kinds wait: the
 * newest text the screen has, which takes the place of an older one still
 * waiting, since it holds all that one did; and texts made on the map as it
 * was before a change from elsewhere, each kept in the map's history, which
 * nothing takes the place of. Those set aside go first. A write that's begun
 * ends, even as the screen goes, and what's still waiting then is the
 * closing screen's to keep ([takeAsides]).
 */
internal class SaveQueue(
    scope: CoroutineScope,

    /** Writes a save. */
    private val write: suspend (Save) -> Unit,

    /** Keeps a text in the map's history, without making it the map. */
    private val keepAside: suspend (String) -> Unit,
) {
    /** The newest text to save, while it waits for the write before it. */
    private var waiting: Save? = null

    /** Texts to keep in the map's history, the oldest first. */
    private val asides = ArrayDeque<String>()

    /** Says there's something to write. */
    private val wake = Channel<Unit>(Channel.CONFLATED)

    /** Writes what waits, one at a time, until the screen goes: nothing more begins then, and what's left is the closing screen's. */
    val job: Job = scope.launch {
        for (signal in wake) {
            while (isActive) {
                // Taken off the queue only as its write begins, which then ends whatever happens: none is lost or kept twice.
                val aside = asides.removeFirstOrNull()

                if (aside != null) {
                    withContext(NonCancellable) { keepAside(aside) }
                    continue
                }

                val save = waiting ?: break
                waiting = null
                withContext(NonCancellable) { write(save) }
            }
        }
    }

    /** Saves the map's text: written once the writes before it are, unless a newer text comes first. */
    fun save(save: Save) {
        waiting = save
        wake.trySend(Unit)
    }

    /** Keeps a text made on the map as it was before a change from elsewhere in its history, in turn. */
    fun setAside(text: String) {
        asides.addLast(text)
        wake.trySend(Unit)
    }

    /**
     * The screen caught up with a change made elsewhere: the save waiting
     * was made on the map as it was before, so it goes into the history,
     * and the next save doesn't take its place.
     */
    fun caughtUp() {
        val stale = waiting ?: return
        waiting = null
        setAside(stale.text)
    }

    /**
     * The texts still waiting to be set aside, once the screen's work
     * stopped and its last write ended: the closing screen keeps them. The
     * save waiting with them, if one is, holds no more than the screen's
     * text, which closing writes.
     */
    fun takeAsides(): List<String> = asides.toList().also { asides.clear() }
}
