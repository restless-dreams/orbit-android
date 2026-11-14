package com.hyuchia.orbit.map

import java.util.IdentityHashMap

/**
 * The ideas each step of undo renamed. New text gives an idea a new id, and
 * what follows the idea (an open panel, the presentation's path, its
 * rehearsal, its cards, a copy on its way to it) follows it there. Undo takes
 * the text back, and the old text gives the idea its old id again, so it
 * takes those renames back too, last first; redo makes them again. They're
 * kept by the very versions on the undo and redo stacks, which equal texts
 * couldn't tell apart.
 */
internal class UndoRenames {
    /** The renames made since each version on the undo stack, in the order they were made. */
    private val since = IdentityHashMap<String, MutableList<Pair<String, String>>>()

    /** The renames redo makes again, by the version on the redo stack it goes to. */
    private val redone = IdentityHashMap<String, List<Pair<String, String>>>()

    /** Idea `from` became `to` in a change made since `version`, the last on the undo stack; none to go back to keeps nothing. */
    fun renamed(version: String?, from: String, to: String) {
        if (version != null) {
            since.getOrPut(version) { mutableListOf() } += from to to
        }
    }

    /**
     * Undo goes back to `version` from `left`, the text it leaves, which goes
     * on the redo stack: the renames to take back, last first, each as the
     * id the idea has now and the one it has again.
     */
    fun undo(version: String, left: String): List<Pair<String, String>> {
        val made = since.remove(version).orEmpty()
        redone[left] = made
        return made.asReversed().map { (from, to) -> to to from }
    }

    /** Redo goes on to `version` from `left`, which goes back on the undo stack: the renames to make again, in order. */
    fun redo(version: String, left: String): List<Pair<String, String>> {
        val made = redone.remove(version).orEmpty()

        if (made.isNotEmpty()) {
            since[left] = made.toMutableList()
        }

        return made
    }

    /** A new change: redo has nothing to go to, and versions undo let go of rename nothing more. */
    fun changed(undo: Collection<String>) {
        redone.clear()
        since.keys.retainAll { version -> undo.any { it === version } }
    }
}
