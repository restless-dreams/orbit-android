package com.hyuchia.orbit.map

import com.hyuchia.orbit.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * A talk's ideas, as the map takes them in. What's said before each pause
 * joins the orbit in focus as one change, one change after another, and the
 * talk's changes go back with one undo. Once the talk is over and its last
 * ideas are saved, a message says how many joined, with Undo for them: it
 * comes only then, so it never takes back a change from before the talk, and
 * its Undo takes back the talk alone, while the talk is still the last
 * change. Nothing said after the talk is over joins the map, however late a
 * speech service says it; and a map behind its lock says nothing of it.
 */
internal class SpokenIdeas(
    private val scope: CoroutineScope,

    /** Adds `ideas` to the orbit of the idea `parent` as one change, and says how many joined once its save is in: none when the page couldn't. */
    private val join: suspend (parent: String, ideas: List<String>) -> Int,

    /** What undo would bring back now: the map before its last change, or null when there's nothing to undo. */
    private val lastChange: () -> String?,

    private val undo: () -> Unit,
    private val say: (MapEvent) -> Unit,

    /** Whether the map is behind its lock, where nothing is said about it and nothing is undone. */
    private val locked: () -> Boolean,
) {
    /** One talk, as the map takes it in. */
    private class Talking {
        /** Over: nothing more said joins the map. */
        var over = false

        /** How many of its changes the page is still making. */
        var adding = 0

        /** How many ideas joined the map. */
        var added = 0

        /** Its first change is saved, which kept the map as it was before the talk, for its undo. */
        var saved = false

        /** Its message, with Undo, was said. */
        var offered = false
    }

    /** The talk going on, or the last one. */
    private var talk: Talking? = null

    /** The talk whose change the page is making now, until it answers: the save that comes meanwhile is that talk's. */
    private var adding: Talking? = null

    /** A talk's changes are made one at a time, so each save is known for the change it is. */
    private val turns = Mutex()

    /** A talk begins: what's said from now on joins the map, until it's over. */
    fun start() {
        talk = Talking()
    }

    /** Ideas just said, to join the orbit of `parent`: false when there's no talk going on to take them. */
    fun add(parent: String, ideas: List<String>): Boolean {
        val talk = talk?.takeUnless { it.over } ?: return false

        if (ideas.isEmpty()) {
            return false
        }

        talk.adding += 1

        scope.launch {
            val added = turns.withLock {
                adding = talk

                try {
                    join(parent, ideas)
                } finally {
                    adding = null
                }
            }

            talk.adding -= 1
            talk.added += added
            offer(talk)
        }

        return true
    }

    /**
     * Whether a save of this `kind` keeps the map as it was, for undo: every
     * save does but a talk's after its first, which go back with it.
     */
    fun keepsUndo(kind: String): Boolean {
        val talk = adding?.takeIf { kind == "add" } ?: return true
        adding = null
        val first = !talk.saved
        talk.saved = true
        return first
    }

    /** The talk is over: nothing said after this joins the map, and once its ideas are saved, a message offers to take them back. */
    fun over() {
        val talk = talk ?: return
        talk.over = true
        offer(talk)
    }

    /**
     * Says how many ideas the talk brought, with Undo, once it's over and the
     * last of them is saved: then or never, so a talk that ended behind the
     * lock, or brought nothing, isn't offered later, long after it's over.
     */
    private fun offer(talk: Talking) {
        if (!talk.over || talk.adding > 0 || talk.offered) {
            return
        }

        talk.offered = true

        if (talk.added == 0 || locked()) {
            return
        }

        // The map as it was before the talk: its Undo brings that back while nothing has changed since.
        val before = lastChange() ?: return

        say(
            MapEvent.Snackbar(
                ShownText.Plural(R.plurals.map_added_ideas, talk.added),
                undo = true,
                onUndo = {
                    if (lastChange() === before) {
                        undo()
                    }
                },
            ),
        )
    }
}
