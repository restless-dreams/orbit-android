package com.hyuchia.orbit.map

import com.hyuchia.orbit.page.MapNode
import com.hyuchia.orbit.page.MapState

/** Statuses Orbit draws as done: a to-do with one is done. */
private val DONE_STATUS = Regex("done|complete|completed|shipped|finished|closed")

/**
 * Statuses that make an idea a to-do: the words Orbit draws as to do, in
 * progress, blocked or done. Any other status, such as "primary key" in a
 * map of a database, is a label.
 */
private val TO_DO_STATUS = Regex(
    "todo|to[ -]?do|planned|backlog|next|open|active|doing|in[ -]?progress|wip|started|ongoing|" +
        "blocked|stuck|at[ -]?risk|failed|done|complete|completed|shipped|finished|closed",
)

private val MapNode.statusWord: String get() = status?.trim()?.lowercase().orEmpty()

/** A to-do: an idea whose status is one of a to-do's (see [TO_DO_STATUS]). */
val MapNode.isToDo: Boolean get() = TO_DO_STATUS.matches(statusWord)

/** A to-do that's done. */
val MapNode.isDone: Boolean get() = DONE_STATUS.matches(statusWord)

/** An idea of the Progress kind. */
val MapNode.isProgress: Boolean get() = type?.trim().equals("progress", ignoreCase = true)

/** A goal with a target of its own, which shows its own numbers. */
private val MapNode.hasOwnTarget: Boolean get() = Goal.of(this)?.target != null

/**
 * The goals and to-dos under an idea, as its ring counts them: how many
 * there are, how much of them is done (a to-do as done or not, a goal by how
 * far along it is), how many are done or reached, and how many are goals.
 */
data class Rollup(val total: Int = 0, val done: Double = 0.0, val finished: Int = 0, val goals: Int = 0) {
    val share: Double get() = if (total == 0) 0.0 else done / total

    operator fun plus(other: Rollup): Rollup = Rollup(total + other.total, done + other.done, finished + other.finished, goals + other.goals)

    /** Whether they're all goals, which are reached rather than done once finished. */
    val allGoals: Boolean get() = total > 0 && goals == total
}

/**
 * How a map's progress rings count, as the map draws them (`web/look.ts`).
 *
 * An idea counts through the goals and to-dos under it, rather than as one
 * of them, when it's a Progress idea, a goal without a target of its own, or
 * any other idea with a to-do or a goal with a target right under it. Every
 * other goal with a target and every other to-do is counted once, by each
 * idea above it. The rule doesn't change with the map's setting: turning
 * rings off only stops ideas from showing them by themselves.
 */
class MapProgress(nodes: List<MapNode>, private val ringsOn: Boolean = true) {
    private val children: Map<String?, List<MapNode>> = nodes.groupBy { it.parentId }

    /** The goals and to-dos under each idea, by id: counted without recursion, so maps of any depth count. */
    val rollUps: Map<String, Rollup> = HashMap<String, Rollup>(nodes.size).also { result ->
        // Every idea after the one above it: counted from the last, each idea's ideas are counted before it.
        val order = ArrayList<MapNode>(nodes.size)
        val waiting = ArrayDeque<MapNode>()
        nodes.firstOrNull()?.let { waiting.addLast(it) }

        while (waiting.isNotEmpty()) {
            val node = waiting.removeLast()
            order.add(node)
            children[node.id]?.let { waiting.addAll(it) }
        }

        for (node in order.asReversed()) {
            var under = Rollup()

            for (child in children[node.id].orEmpty()) {
                under += asItem(child) + result.getValue(child.id)
            }

            result[node.id] = under
        }
    }

    /** The goals and to-dos under the idea. */
    fun under(node: MapNode): Rollup = rollUps[node.id] ?: Rollup()

    /** Whether a to-do, or a goal with a target, is right under the idea. */
    fun hasItemsUnder(node: MapNode): Boolean = children[node.id].orEmpty().any { it.isToDo || it.hasOwnTarget }

    /** Whether the idea counts through the goals and to-dos under it, rather than as one of them. */
    private fun countsThrough(node: MapNode): Boolean =
        if (node.isGoal) Goal.of(node)?.target == null else node.isProgress || hasItemsUnder(node)

    /** What an idea itself counts for in the rollups above it. */
    private fun asItem(node: MapNode): Rollup {
        if (countsThrough(node)) {
            return Rollup()
        }

        val goal = Goal.of(node)

        if (goal != null) {
            return Rollup(1, goal.share, if (goal.reached) 1 else 0, 1)
        }

        if (!node.isToDo) {
            return Rollup()
        }

        val done = node.isDone
        return Rollup(1, if (done) 1.0 else 0.0, if (done) 1 else 0, 0)
    }

    /**
     * Whether the map draws the idea with a Progress ring by itself: an idea
     * with no kind and no picture or video, with a to-do or a goal with a
     * target right under it, on a map that shows them.
     */
    fun ringByItself(node: MapNode): Boolean =
        ringsOn && node.type.isNullOrBlank() && node.image == null && node.video == null && hasItemsUnder(node)

    /**
     * Whether the idea's ring shows the progress of the goals and to-dos
     * under it: a Progress idea, a goal without a target, or an idea with a
     * ring by itself.
     */
    fun followsItsIdeas(node: MapNode): Boolean =
        node.isProgress || (node.isGoal && Goal.of(node)?.target == null) || ringByItself(node)

    companion object {
        /** A map's rings, on unless its frontmatter says `progressRings: false`. */
        fun of(map: MapState?): MapProgress = MapProgress(map?.nodes.orEmpty(), map?.options?.progressRings != false)
    }
}
