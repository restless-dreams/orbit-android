package com.hyuchia.orbit.data

/** Past this many pairs of unmatched branches to compare, two versions are too far apart to look for renamed branches. */
private const val BRANCHES_COMPARED = 40_000L

/**
 * What changed from one version of a map to the next, idea by idea: the
 * ideas added, removed, renamed and moved, by name, each branch's highest
 * idea before the ones under it. `details` says the text changed with no
 * idea added, removed, renamed or moved: notes, statuses, links or styles.
 */
data class VersionChanges(
    val added: List<String> = emptyList(),
    val removed: List<String> = emptyList(),
    val renamed: List<Pair<String, String>> = emptyList(),
    val moved: List<String> = emptyList(),
    val details: Boolean = false,
) {
    /** Whether nothing changed, text and all. */
    val none: Boolean get() = added.isEmpty() && removed.isEmpty() && renamed.isEmpty() && moved.isEmpty() && !details

    companion object {
        /** What changed from the text `before` to the text `after`. */
        fun between(before: String, after: String): VersionChanges = between(MapText.ideas(before), MapText.ideas(after), textChanged = before != after)

        /**
         * What changed from the ideas `before` to the ideas `after`: matched
         * from the center down, by name under the same parent, then as a
         * rename (a branch that kept most of its ideas, or an idea in the same
         * place among its siblings); then an idea found elsewhere by its name
         * has moved, and the rest were added or removed. Linear in the ideas,
         * for maps of thousands. A center Orbit made up compares as
         * [sameCenters] has it.
         */
        fun between(before: List<TextIdea>, after: List<TextIdea>, textChanged: Boolean): VersionChanges {
            val (earlier, later) = sameCenters(before, after)
            val old = Outline(earlier)
            val new = Outline(later)
            val partner = IntArray(earlier.size) { -1 }
            val taken = BooleanArray(later.size)
            val renamed = mutableListOf<Pair<String, String>>()
            val moved = mutableListOf<String>()
            val pending = ArrayDeque<Int>()

            fun pair(from: Int, to: Int) {
                partner[from] = to
                taken[to] = true

                // A center made up without a title is named in the reader's language: no one renamed it.
                if (old.names[from] != new.names[to] && !old.untitled[from] && !new.untitled[to]) {
                    renamed += old.names[from] to new.names[to]
                }

                pending += from
            }

            /** Matches the ideas under each matched pair, down every branch. */
            fun descend() {
                while (pending.isNotEmpty()) {
                    val from = pending.removeFirst()
                    matchChildren(old, new, old.children[from], new.children[partner[from]], taken, ::pair)
                }
            }

            old.roots.zip(new.roots).forEach { (from, to) -> pair(from, to) }
            descend()

            // An idea that left its branch and turns up in another, by its name: it moved, with what's under it.
            val elsewhere = HashMap<String, ArrayDeque<Int>>()
            later.indices.filterNot { taken[it] }.forEach { elsewhere.getOrPut(new.names[it]) { ArrayDeque() }.addLast(it) }

            for (from in earlier.indices) {
                if (partner[from] >= 0) {
                    continue
                }

                val to = elsewhere[old.names[from]]?.removeFirstOrNull()?.takeIf { !taken[it] } ?: continue
                moved += old.names[from]
                pair(from, to)
                descend()
            }

            val removed = topFirst(earlier.indices.filter { partner[it] < 0 }, old) { partner[it] >= 0 }
            val added = topFirst(later.indices.filter { !taken[it] }, new) { taken[it] }
            val changed = added.isNotEmpty() || removed.isNotEmpty() || renamed.isNotEmpty() || moved.isNotEmpty()
            return VersionChanges(added.map(new.names::get), removed.map(old.names::get), renamed, moved, details = textChanged && !changed)
        }

        /**
         * `before` and `after`, with centers that compare. A YAML map with
         * several ideas at its top, or none, has a center Orbit made up, and
         * one with a single idea there has that idea for its center: when one
         * version's center is made up and the other's was written, and that
         * written one is one of the ideas at the made-up one's top (or there
         * are none), it goes under a center made up the same way. So a map that
         * gained a second idea at its top added that idea, rather than renamed
         * its center and moved what was under it ([fitsUnder] tells which).
         */
        private fun sameCenters(before: List<TextIdea>, after: List<TextIdea>): Pair<List<TextIdea>, List<TextIdea>> {
            val madeUpBefore = before.firstOrNull()?.takeIf { it.madeUp }
            val madeUpAfter = after.firstOrNull()?.takeIf { it.madeUp }

            return when {
                madeUpBefore != null && madeUpAfter == null && fitsUnder(after, before) -> before to under(madeUpBefore, after)
                madeUpAfter != null && madeUpBefore == null && fitsUnder(before, after) -> under(madeUpAfter, before) to after
                else -> before to after
            }
        }

        /**
         * Whether the center of `written` is one of the ideas at the top of
         * `madeUp`, whose center Orbit made up, rather than that made-up center
         * written down, as markdown writes it, often under the same name as one
         * of those ideas (both named by the map's title): it holds what that
         * idea holds at least as much as it holds the ideas at the top. Either
         * version without ideas fits.
         */
        private fun fitsUnder(written: List<TextIdea>, madeUp: List<TextIdea>): Boolean {
            val top = madeUp.indices.filter { madeUp[it].parent == 0 }
            val center = written.indexOfFirst { it.parent == null }

            if (center < 0 || top.isEmpty()) {
                return true
            }

            val under = written.filter { it.parent == center }.mapTo(HashSet()) { it.name }
            val asCenter = top.count { madeUp[it].name in under }
            val asIdea = top.filter { madeUp[it].name == written[center].name }.maxOfOrNull { idea -> madeUp.count { it.parent == idea && it.name in under } } ?: return false
            return asIdea >= asCenter
        }

        /** `ideas` under `center`, made up as another version's is. */
        private fun under(center: TextIdea, ideas: List<TextIdea>): List<TextIdea> = listOf(center) + ideas.map { it.copy(parent = it.parent?.plus(1) ?: 0) }

        /**
         * Pairs the ideas under two matched ideas: the same name first, in
         * order; then a branch that kept at least half its ideas' names; then
         * an idea in the same place among its siblings, as a rename leaves it.
         */
        private fun matchChildren(old: Outline, new: Outline, from: List<Int>, to: List<Int>, taken: BooleanArray, pair: (Int, Int) -> Unit) {
            val byName = HashMap<String, ArrayDeque<Int>>()
            to.filterNot { taken[it] }.forEach { byName.getOrPut(new.names[it]) { ArrayDeque() }.addLast(it) }
            val left = LinkedHashSet<Int>()

            for (child in from) {
                val match = byName[old.names[child]]?.removeFirstOrNull()

                if (match == null) {
                    left += child
                } else {
                    pair(child, match)
                }
            }

            val waiting = to.filterTo(LinkedHashSet()) { !taken[it] }

            if (left.isEmpty() || waiting.isEmpty()) {
                return
            }

            // A branch renamed keeps most of what's under it. Versions far apart (one brought back, say) skip
            // this, rather than comparing every branch with every other.
            if (left.size.toLong() * waiting.size <= BRANCHES_COMPARED) {
                val theirs = waiting.associateWith { candidate -> new.children[candidate].mapTo(HashSet()) { new.names[it] } }

                for (child in left.toList()) {
                    val names = old.children[child].mapTo(HashSet()) { old.names[it] }

                    if (names.isEmpty()) {
                        continue
                    }

                    val match = waiting.firstOrNull { candidate ->
                        val under = theirs.getValue(candidate)
                        under.isNotEmpty() && names.count { it in under } * 2 >= maxOf(names.size, under.size)
                    } ?: continue

                    left -= child
                    waiting -= match
                    pair(child, match)
                }
            }

            // An idea renamed stays where it was among its siblings. A branch in another's place, with none of its
            // ideas, took its place rather than renamed it.
            val atPlace = HashMap<Int, Int>()
            to.forEachIndexed { place, idea -> if (idea in waiting && new.children[idea].isEmpty()) atPlace[place] = idea }

            from.forEachIndexed { place, child ->
                if (child in left && old.children[child].isEmpty()) {
                    atPlace.remove(place)?.let { match -> pair(child, match) }
                }
            }
        }

        /** `ideas` with each branch's highest idea first (one whose parent stays, `stays`), then the ones under them, in order. */
        private fun topFirst(ideas: List<Int>, outline: Outline, stays: (Int) -> Boolean): List<Int> {
            val (tops, under) = ideas.partition { idea -> outline.parents[idea]?.let(stays) ?: true }
            return tops + under
        }
    }
}

/** A version's ideas as a tree: each one's name, its parent, and the ideas under it, in order. */
private class Outline(ideas: List<TextIdea>) {
    val names: List<String> = ideas.map { it.name }

    /** Whether each is a center Orbit made up for a map without a title, which it names in the reader's language. */
    val untitled: List<Boolean> = ideas.map { it.madeUp && it.name.isEmpty() }

    val parents: List<Int?> = ideas.map { it.parent }
    val children: List<MutableList<Int>> = List(ideas.size) { mutableListOf() }
    val roots = mutableListOf<Int>()

    init {
        ideas.forEachIndexed { index, idea ->
            val parent = idea.parent

            if (parent == null || parent !in ideas.indices) {
                roots += index
            } else {
                children[parent] += index
            }
        }
    }
}
