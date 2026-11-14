package com.hyuchia.orbit.study

/**
 * A change to this device's answers to a map's cards, made against them as
 * the map's details have them when it's written (`MapRepository.changeStudy`),
 * never by writing back a copy of them all: an answer, an answer taken back,
 * or the progress of ideas the map no longer has dropped. So a change that
 * lands after something else changed the answers, the other devices' folded
 * in as the folder was left, say, or after a session began again, loses
 * nothing of it.
 */
sealed interface StudyChange {
    /** This device's answers once the change is made to them (`mine`), beside the other devices' (`elsewhere`, by device). */
    fun applyTo(mine: Map<String, StudyCard>, elsewhere: Map<String, Map<String, StudyCard>>): Map<String, StudyCard>

    /**
     * `idea` was answered: its card goes as `card` says, over this device's
     * own answer before it (`before`, as the session had it; null: none). A
     * later answer that's there instead, as another device's is once the
     * other devices' answers were folded into this one's (the folder was
     * left), stays: a card goes as its latest answer anywhere left it.
     */
    data class Answer(val idea: String, val card: StudyCard, val before: StudyCard?) : StudyChange {
        override fun applyTo(mine: Map<String, StudyCard>, elsewhere: Map<String, Map<String, StudyCard>>): Map<String, StudyCard> {
            val kept = mine[idea]

            if (kept != null && kept != before && kept.outranks(card)) {
                return mine
            }

            return mine + (idea to card)
        }
    }

    /**
     * The answer to `idea` that left it as `answer` is taken back: this
     * device's own answer goes back to `own` (null: none). Once the card has
     * no answers of the other devices beside this one's, as when they were
     * folded into it because the folder was left, it goes back to how the
     * card was going before, every device's answers together (`progress`), so
     * what they had studied stays. An answer that isn't `answer` anymore
     * (the folder brought a later one in) stays as it is.
     */
    data class Undo(val idea: String, val answer: StudyCard, val own: StudyCard?, val progress: StudyCard?) : StudyChange {
        override fun applyTo(mine: Map<String, StudyCard>, elsewhere: Map<String, Map<String, StudyCard>>): Map<String, StudyCard> {
            if (mine[idea] != answer) {
                return mine
            }

            val before = if (elsewhere.values.any { idea in it }) own else progress
            return if (before == null) mine - idea else mine + (idea to before)
        }
    }

    /** The ideas `ideas`, which the map no longer has, leave their progress behind. */
    data class Forget(val ideas: Set<String>) : StudyChange {
        override fun applyTo(mine: Map<String, StudyCard>, elsewhere: Map<String, Map<String, StudyCard>>): Map<String, StudyCard> = mine - ideas
    }
}
