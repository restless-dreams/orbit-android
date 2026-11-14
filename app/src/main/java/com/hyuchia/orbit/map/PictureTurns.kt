package com.hyuchia.orbit.map

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject

/**
 * Which change to each idea's picture is its latest. A picture copied in
 * from the phone, or let go on the idea, takes a turn as its copy starts
 * ([take]), and a live photo's movie notes the turn there is ([now]): any
 * picture set on the idea since, a later copy's, a sketch's or one taken
 * off, ends that turn ([changed]). So a copy that finishes late never puts
 * its picture over a newer one, nor gives its motion to a picture it wasn't
 * taken with. A caption or description changed meanwhile is the same
 * picture, and ends nothing. An idea's turn goes with it as new text gives it
 * a new id ([renamed]), and is never another idea's that took its old id.
 */
class PictureTurns {
    /** Each idea's latest turn, by its id now: every turn has a number of its own, whatever idea it's on. */
    private val latest = HashMap<String, Long>()

    private var turns = 0L

    /** A copy's turn on idea `nodeId`, as it was named then: it's its idea's latest while [current] says so. */
    data class Turn(val nodeId: String, val number: Long)

    /** A copy for idea `nodeId`'s picture starts: the turn of any copy under way there is over. */
    @Synchronized
    fun take(nodeId: String): Turn = Turn(nodeId, next(nodeId))

    /** The turn idea `nodeId`'s picture is at now, without taking one: for a movie to give only to the picture there is. */
    @Synchronized
    fun now(nodeId: String): Turn = Turn(nodeId, latest.getOrPut(nodeId) { ++turns })

    /**
     * Idea `nodeId`'s picture was given `image` (its `update`): another file,
     * or none, ends the turn of any copy under way there; a change to the
     * picture it has, as its caption, description or motion, ends nothing.
     */
    @Synchronized
    fun changed(nodeId: String, image: JsonElement?) {
        if (image == null || image is JsonNull || (image as? JsonObject)?.containsKey("src") != false) {
            next(nodeId)
        }
    }

    /** Whether `turn` is still its idea's latest, under whatever id the idea has now: nothing has changed its picture since it was taken. */
    @Synchronized
    fun current(turn: Turn): Boolean = turn.number in latest.values

    /** New text gave idea `from` the id `to`: its turn goes with it, and a turn left on `to`, an idea removed since, is over. */
    @Synchronized
    fun renamed(from: String, to: String) {
        val turn = latest.remove(from)

        if (turn != null) {
            latest[to] = turn
        } else {
            latest.remove(to)
        }
    }

    /** Ideas `ids` were removed from the map: their turns are over, and an idea that takes one of their ids later starts afresh. */
    @Synchronized
    fun removed(ids: Collection<String>) {
        latest.keys.removeAll(ids.toSet())
    }

    private fun next(nodeId: String): Long = (++turns).also { latest[nodeId] = it }
}
