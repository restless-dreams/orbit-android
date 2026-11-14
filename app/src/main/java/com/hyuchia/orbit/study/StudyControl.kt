package com.hyuchia.orbit.study

import com.hyuchia.orbit.map.MapUi
import com.hyuchia.orbit.page.MapCommand
import com.hyuchia.orbit.page.MapNode
import com.hyuchia.orbit.page.ViewMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
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
 *
 * Each answer is kept as a change to that one card ([StudyChange]), written
 * one after another in the order they were given, against the answers as
 * they are then: never a copy of them all, which could bring back an older
 * one. The changes go through the map's `answers`, which outlive the map's
 * screen, so the last answer is kept even as the map closes ([close]), and
 * one the storage couldn't take is tried again until it does, as the map's
 * `ui` says ([MapUi.answersUnsaved]).
 */
class StudyControl(
    scope: CoroutineScope,
    private val ui: MutableStateFlow<MapUi>,

    /** Tells the map's page something. */
    private val send: (MapCommand) -> Unit,

    /** This device's answers to the map's cards, as its details have them now. */
    private val progress: () -> Map<String, StudyCard>,

    /** The map's answers on their way to its details, which make each change to them as they are when it's made. */
    private val answers: StudyWriting,

    private val today: () -> Long,

    /** The other devices' answers, by device, as the folder the maps are kept in last said. */
    private val elsewhere: () -> Map<String, Map<String, StudyCard>> = { emptyMap() },

    /** How many new cards a day the learner chose. */
    private val newADay: () -> Int = { NEW_CARDS_A_DAY },

    private val now: () -> Long = System::currentTimeMillis,

    /** Hears that answers the storage couldn't take are written after all, outside a session, which says so itself. */
    private val saved: () -> Unit = {},
) {
    /** This device's answers, with this session's. */
    private var mine: Map<String, StudyCard> = emptyMap()

    /** The other devices' answers, as the session began. */
    private var others: Map<String, Map<String, StudyCard>> = emptyMap()

    /** How the map's cards are going: each as its latest answer, here or elsewhere, left it. */
    private var cards: Map<String, StudyCard> = emptyMap()

    /** The cards whose ideas the page hides now. */
    private val hidden = LinkedHashSet<String>()

    /** Whether the map's screen went: its answers go on being written, and it makes no more. */
    @Volatile
    private var closed = false

    init {
        // Answers the storage couldn't take show on the map until they're written, the session's or one before it.
        scope.launch {
            var before: AnswersUnsaved? = null

            answers.problem.collect { problem ->
                ui.update { it.copy(answersUnsaved = problem) }

                if (before != null && problem == null && study == null) {
                    saved()
                }

                before = problem
            }
        }

        // The map changed while it's studied, as it can only elsewhere (a session takes no edits): the cards it no longer
        // has leave the session at once.
        scope.launch {
            ui.map { it.map?.nodes }.distinctUntilChanged().collect { nodes ->
                if (nodes != null) {
                    follow(nodes)
                }
            }
        }
    }

    private val study: Study? get() = ui.value.study

    /**
     * Begins studying the cards under `start`: the ones due, then new ones,
     * or `anyway`, all of them. `moreNew` brings in that many new cards
     * past the day's. With none to study, the session opens on its summary.
     * Answers still being written count, so a session begun again (Keep
     * going, Study anyway) knows every answer the one before got.
     */
    fun start(start: String, anyway: Boolean = false, moreNew: Int = 0) {
        val nodes = ui.value.map?.nodes ?: return
        val ideas = nodes.mapTo(HashSet()) { it.id }

        // Read together with the changes still to be written, so none counts twice or not at all: a change leaves
        // them only once the details have it.
        val (kept, elsewhere) = answers.unwritten { unwritten ->
            val elsewhere = elsewhere()
            unwritten.fold(progress()) { answers, change -> change.applyTo(answers, elsewhere) } to elsewhere
        }

        // Ideas the map no longer has leave their progress behind, but only once the map was read whole, which only a
        // map that can be changed was for sure: one Orbit couldn't read all of, or a newer Orbit made, may show only
        // some of its ideas, or give them other ids, and the page says a map is in the trash, or kept by a newer
        // Orbit, in place of either. Its progress stays for when it's read whole; the session studies the ideas shown.
        mine = if (ui.value.readOnly == null) kept.filterKeys { it in ideas } else kept
        others = elsewhere
        cards = mergedProgress(mine, others).filterKeys { it in ideas }

        if (mine.size != kept.size) {
            change(StudyChange.Forget(kept.keys - mine.keys))
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

    /** Shows the ideas under the card on screen, and its note. A card the map no longer has leaves the session instead. */
    fun reveal() {
        val session = study ?: return
        val card = session.current ?: return

        if (session.revealed || session.ending != null) {
            return
        }

        if (!stillACard(card)) {
            ui.value.map?.nodes?.let(::follow)
            return
        }

        show(card)
        update { it.copy(revealed = true) }
    }

    /**
     * Answers the card on screen, once it's revealed: known, or Again, which
     * brings it back later in the session. The answer is kept at once, so a
     * session left halfway keeps what it got through. A card the map no
     * longer has, as it changed elsewhere, takes no answer: the session goes
     * on without it.
     */
    fun answer(knew: Boolean) {
        val session = study ?: return
        val card = session.current ?: return

        if (!session.revealed || session.ending != null) {
            return
        }

        if (!stillACard(card)) {
            ui.value.map?.nodes?.let(::follow)
            return
        }

        val today = today()
        val before = cards[card]
        val after = before.answered(knew, today, now())
        val own = mine[card]
        val last = StudyAnswer(session.copy(last = null), card, before, own)
        mine = mine + (card to after)
        cards = cards + (card to after)
        change(StudyChange.Answer(card, after, before = own))
        val stillCards = mapCards(ui.value.map?.nodes.orEmpty())
        // Cards that went from the map while it was studied are skipped.
        val answered = session.answered(knew).let { next -> next.without(next.queue.filterTo(HashSet()) { it !in stillCards }) }.copy(last = last)

        if (answered.queue.isEmpty()) {
            finish(answered)
            return
        }

        val next = answered.queue.first()
        hide(listOf(next))
        send(MapCommand.Focus(next))
        update { answered.copy(ifKnown = cards[next].daysIfKnown(today)) }
    }

    /**
     * Takes back the last answer: its card comes back revealed, to answer
     * again, and its progress as it was, or as a later answer from another
     * device left it, if one came in meanwhile: that one stays, and the
     * card goes on from it.
     */
    fun undo() {
        val session = study ?: return
        val last = session.last ?: return
        val answer = mine[last.card]

        if (answer != null) {
            change(StudyChange.Undo(last.card, answer, own = last.mine, progress = last.progress))
        }

        val (own, progress) = kept(last.card)
        mine = if (own == null) mine - last.card else mine + (last.card to own)
        cards = if (progress == null) cards - last.card else cards + (last.card to progress)
        hide(cardsUnder(ui.value.map?.nodes.orEmpty(), last.before.start).map { it.id }.filter { it != last.card })
        show(last.card)
        send(MapCommand.Focus(last.card))
        update { last.before.copy(revealed = true, ifKnown = progress.daysIfKnown(today())) }
    }

    /**
     * This device's own answer to `card`, and how the card is going, every
     * device's answers together, as the map's details will have them once
     * the changes still to be written are.
     */
    private fun kept(card: String): Pair<StudyCard?, StudyCard?> = answers.unwritten { unwritten ->
        val elsewhere = elsewhere()
        val answers = unwritten.fold(progress()) { answers, change -> change.applyTo(answers, elsewhere) }
        answers[card] to mergedProgress(answers.filterKeys { it == card }, elsewhere)[card]
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

    /** Waits until every answer given so far is written: then the folder the maps are kept in can take them. */
    suspend fun written() {
        answers.written()
    }

    /** Tries the answers the storage couldn't take again now. */
    fun tryAgain() {
        answers.retry()
    }

    /**
     * The map's screen is going: the answers still to be written are, in
     * the order they were given, by the map's answers, which outlive it, and
     * nothing more is taken.
     */
    fun close() {
        closed = true
    }

    private fun change(change: StudyChange) {
        if (!closed) {
            answers.add(change)
        }
    }

    /** Whether the map still has `card` as a card: ideas or a note under it. */
    private fun stillACard(card: String): Boolean = ui.value.nodes[card]?.let { it.childCount > 0 || !it.note.isNullOrBlank() } == true

    /**
     * The map is `nodes` now, as it changed elsewhere while it's studied: the
     * cards it no longer has leave the session, and the next card comes when
     * the one on screen went. With none left, the session ends on its summary.
     */
    private fun follow(nodes: List<MapNode>) {
        val session = study ?: return
        val stillCards = mapCards(nodes)
        val gone = (session.queue + listOfNotNull(session.last?.card)).filterTo(HashSet()) { it !in stillCards }

        if (gone.isEmpty()) {
            return
        }

        val ideas = nodes.mapTo(HashSet()) { it.id }
        hidden.retainAll(ideas)
        val left = session.without(gone)

        when {
            session.ending != null -> update { left }
            left.queue.isEmpty() -> finish(left)
            left.current != session.current -> {
                val next = left.queue.first()
                hide(listOf(next))
                send(MapCommand.Focus(next))
                update { left.copy(ifKnown = cards[next].daysIfKnown(today())) }
            }
            else -> update { left }
        }
    }

    /** The session's last card is behind it: the whole branch shows again, and the summary says how it went. */
    private fun finish(session: Study) {
        val today = today()
        val branch = cardsUnder(ui.value.map?.nodes.orEmpty(), session.start)
        revealAll()
        send(MapCommand.Focus(session.start))
        val newLeft = if (session.anyway) 0 else newCards(branch, cards)
        update { session.copy(ending = StudyEnding.Finished, revealed = false, nextIn = nextStudy(branch, cards, today)?.minus(today), newLeft = newLeft) }
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
