package com.hyuchia.orbit.study

import com.hyuchia.orbit.map.MapUi
import com.hyuchia.orbit.page.MapCommand
import com.hyuchia.orbit.page.ViewMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * A study session as it goes: it hides the ideas under every card of the
 * branch on the map's page, flies to each card and reveals what's under it
 * when asked, and keeps each answer among this device's. The map's text is
 * never touched: hiding is only how the page draws the map, and progress
 * lives in `meta.json`, and in the folder the maps are kept in. A card goes
 * as its latest answer, here or on another device, left it. The session
 * lives in `ui`; days come from `today`, as days since 1970-01-01, and
 * answers are timed by `now`, in milliseconds.
 */
class StudyControl(
    scope: CoroutineScope,
    private val ui: MutableStateFlow<MapUi>,

    /** Tells the map's page something. */
    private val send: (MapCommand) -> Unit,

    /** This device's answers to the map's cards, as its details have them now. */
    private val progress: () -> Map<String, StudyCard>,

    /** Keeps this device's answers, in the map's details. */
    private val keep: suspend (Map<String, StudyCard>) -> Unit,

    private val today: () -> Long,

    /** The other devices' answers, by device, as the folder the maps are kept in last said. */
    private val elsewhere: () -> Map<String, Map<String, StudyCard>> = { emptyMap() },

    /** How many new cards a day the learner chose. */
    private val newADay: () -> Int = { NEW_CARDS_A_DAY },

    private val now: () -> Long = System::currentTimeMillis,
) {
    /** This device's answers, with this session's. */
    private var mine: Map<String, StudyCard> = emptyMap()

    /** The other devices' answers, as the session began. */
    private var others: Map<String, Map<String, StudyCard>> = emptyMap()

    /** How the map's cards are going: each as its latest answer, here or elsewhere, left it. */
    private var cards: Map<String, StudyCard> = emptyMap()

    /** The cards whose ideas the page hides now. */
    private val hidden = LinkedHashSet<String>()

    /** This device's answers to keep, the latest only, written one at a time so an older one never lands last. */
    private val saves = Channel<Map<String, StudyCard>>(Channel.CONFLATED)

    init {
        scope.launch {
            for (latest in saves) {
                keep(latest)
            }
        }
    }

    private val study: Study? get() = ui.value.study

    /**
     * Begins studying the cards under `start`: the ones due, then new ones,
     * or `anyway`, all of them. `moreNew` brings in that many new cards
     * past the day's. With none to study, the session opens on its summary.
     */
    fun start(start: String, anyway: Boolean = false, moreNew: Int = 0) {
        val nodes = ui.value.map?.nodes ?: return
        val ideas = nodes.mapTo(HashSet()) { it.id }
        val kept = progress()
        // Ideas the map no longer has leave their progress behind, but only once the map was read whole, which only a
        // map that can be changed was for sure: one Orbit couldn't read all of, or a newer Orbit made, may show only
        // some of its ideas, or give them other ids, and the page says a map is in the trash, or kept by a newer
        // Orbit, in place of either. Its progress stays for when it's read whole; the session studies the ideas shown.
        mine = if (ui.value.readOnly == null) kept.filterKeys { it in ideas } else kept
        others = elsewhere()
        cards = mergedProgress(mine, others).filterKeys { it in ideas }

        if (mine.size != kept.size) {
            saves.trySend(mine)
        }

        val today = today()
        val branch = cardsUnder(nodes, start)
        val order = sessionOrder(branch, cards, today, anyway, moreNew, newADay())

        val ending = when {
            branch.isEmpty() -> StudyEnding.NoCards
            order.isEmpty() -> StudyEnding.CaughtUp
            else -> null
        }

        revealAll()
        val session = Study(
            start = start,
            queue = order,
            total = order.size,
            anyway = anyway,
            ending = ending,
            ifKnown = order.firstOrNull()?.let { cards[it] }.daysIfKnown(today),
            nextIn = nextStudy(branch, cards, today)?.minus(today),
            // Caught up with new cards still to study: they wait for another day, or for "Keep going".
            newLeft = if (ending == StudyEnding.CaughtUp) newCards(branch, cards) else 0,
        )
        ui.update { it.copy(study = session, panel = null, pathMaking = false) }
        send(MapCommand.Present(true))
        send(MapCommand.View(ViewMode.FOCUS))

        if (ending == null) {
            // Every card's ideas wait out of view, so revealing one never gives away another's.
            hide(branch.map { it.id })
            send(MapCommand.Focus(order.first()))
        } else {
            send(MapCommand.Focus(start))
        }
    }

    /** Studies every card under where the session began, due or not. */
    fun studyAnyway() {
        study?.let { start(it.start, anyway = true) }
    }

    /**
     * Studies new cards left for another day now: the ones today still has
     * room for, once the learner chose more a day, or else another day's.
     */
    fun keepGoing() {
        val session = study ?: return
        val perDay = newADay()
        start(session.start, moreNew = if (newRoom(cards, today(), perDay) > 0) 0 else perDay)
    }

    /** Shows the ideas under the card on screen, and its note. */
    fun reveal() {
        val session = study ?: return
        val card = session.current ?: return

        if (session.revealed || session.ending != null) {
            return
        }

        show(card)
        update { it.copy(revealed = true) }
    }

    /**
     * Answers the card on screen, once it's revealed: known, or Again, which
     * brings it back later in the session. The answer is kept at once, so a
     * session left halfway keeps what it got through.
     */
    fun answer(knew: Boolean) {
        val session = study ?: return
        val card = session.current ?: return

        if (!session.revealed || session.ending != null) {
            return
        }

        val today = today()
        val before = cards[card]
        val after = before.answered(knew, today, now())
        val last = StudyAnswer(session.copy(last = null), card, before, mine[card])
        mine = mine + (card to after)
        cards = cards + (card to after)
        saves.trySend(mine)
        val ideas = ui.value.nodes.keys
        // Cards whose ideas went from the map while it was studied are skipped.
        val answered = session.answered(knew).let { next -> next.copy(queue = next.queue.filter { it in ideas }) }

        if (answered.queue.isEmpty()) {
            val branch = cardsUnder(ui.value.map?.nodes.orEmpty(), session.start)
            revealAll()
            send(MapCommand.Focus(session.start))
            val newLeft = if (session.anyway) 0 else newCards(branch, cards)
            update { answered.copy(ending = StudyEnding.Finished, last = last, nextIn = nextStudy(branch, cards, today)?.minus(today), newLeft = newLeft) }
            return
        }

        val next = answered.queue.first()
        hide(listOf(next))
        send(MapCommand.Focus(next))
        update { answered.copy(last = last, ifKnown = cards[next].daysIfKnown(today)) }
    }

    /** Takes back the last answer: its card comes back revealed, to answer again, and its progress as it was. */
    fun undo() {
        val session = study ?: return
        val last = session.last ?: return
        mine = if (last.mine == null) mine - last.card else mine + (last.card to last.mine)
        cards = if (last.progress == null) cards - last.card else cards + (last.card to last.progress)
        saves.trySend(mine)
        hide(cardsUnder(ui.value.map?.nodes.orEmpty(), last.before.start).map { it.id }.filter { it != last.card })
        show(last.card)
        send(MapCommand.Focus(last.card))
        update { last.before.copy(revealed = true, ifKnown = last.progress.daysIfKnown(today())) }
    }

    /** Ends the session: the map shows everything again, and can change again. */
    fun stop() {
        if (study == null) {
            return
        }

        revealAll()
        send(MapCommand.Present(false))
        ui.update { it.copy(study = null) }
    }

    /** The page drew the map again (its process had stopped, say): it hides the cards again, and goes back to the one on screen. */
    fun pageLoaded() {
        val session = study ?: return
        send(MapCommand.Present(true))

        if (hidden.isNotEmpty()) {
            send(MapCommand.HideChildren(hidden.toList()))
        }

        send(MapCommand.Focus(session.current?.takeIf { session.ending == null } ?: session.start))
    }

    private fun update(change: (Study) -> Study) {
        ui.update { state -> state.study?.let { state.copy(study = change(it)) } ?: state }
    }

    private fun hide(ids: List<String>) {
        val fresh = ids.filter { hidden.add(it) }

        if (fresh.isNotEmpty()) {
            send(MapCommand.HideChildren(fresh))
        }
    }

    private fun show(card: String) {
        hidden.remove(card)
        send(MapCommand.RevealChildren(listOf(card)))
    }

    private fun revealAll() {
        if (hidden.isNotEmpty()) {
            hidden.clear()
            send(MapCommand.RevealChildren(null))
        }
    }
}
