package com.hyuchia.orbit.map

import java.util.Collections
import java.util.WeakHashMap

/**
 * The ideas that work under way is for, as a picture, a video, a sound or
 * files still being copied in, or a drop: each is followed as new text gives
 * it a new id ([renamed], as the map's screen hears of each), so what lands
 * late lands on the idea it was for, under the id it has then, never on
 * another idea that took its old id since. An idea removed meanwhile is
 * [gone] for good, whatever idea takes its id after it ([removed]). One the
 * work no longer holds is let go of by itself.
 */
class FollowedIdeas {
    /** An idea work under way is for: [id] is its id now, which no idea has once it's [gone]. */
    class Idea internal constructor(id: String) {
        @Volatile
        var id: String = id
            internal set

        /** Whether the idea was removed from the map: what was under way for it lands nowhere. */
        val gone: Boolean get() = id == GONE
    }

    private val ideas: MutableSet<Idea> = Collections.newSetFromMap(WeakHashMap())

    /** Follows idea `id` from now on, for as long as the work holds what this returns. */
    @Synchronized
    fun follow(id: String): Idea = Idea(id).also { ideas += it }

    /** New text gave idea `from` the id `to`: the work for it follows it there. */
    @Synchronized
    fun renamed(from: String, to: String) {
        for (idea in ideas) {
            if (idea.id == from) {
                idea.id = to
            }
        }
    }

    /** Ideas `ids` were removed from the map: the work for them is for no idea now, not even one that takes an id of theirs later. */
    @Synchronized
    fun removed(ids: Collection<String>) {
        for (idea in ideas) {
            if (idea.id in ids) {
                idea.id = GONE
            }
        }
    }

    private companion object {
        /** The id of an idea removed: none on a map has it, so nothing for it lands anywhere. */
        const val GONE = "\u0000removed"
    }
}
