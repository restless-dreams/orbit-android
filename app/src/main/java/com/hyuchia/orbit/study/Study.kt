package com.hyuchia.orbit.study

import com.hyuchia.orbit.page.MapNode
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.longOrNull

/**
 * Days a card waits before it comes back once it's been known 1, 2, 3…
 * times in a row: the boxes of a Leitner system, from the first to the last.
 */
val STUDY_INTERVALS = listOf(1, 3, 7, 16, 35, 80, 180)

/** New cards a map brings into its sessions each day, after the ones that are due, unless the learner chose otherwise ([NEW_CARDS_CHOICES]). */
const val NEW_CARDS_A_DAY = 20

/** How many new cards a day the learner can choose from, in Settings and where a session runs out of them. */
val NEW_CARDS_CHOICES = listOf(5, 10, 20, 30, 50)

/** How many other cards a card answered with Again waits for before it comes back, later in the session. */
const val AGAIN_AFTER = 3

/**
 * How a card of a map's study is going, as an answer left it: this device's
 * answers are kept in the map's `meta.json` (`MapMetadata.study`), and the
 * other devices' come through the folder the maps are kept in
 * (`MapMetadata.studyElsewhere`). Days are counted since 1970-01-01 in the
 * phone's time zone (`LocalDate.toEpochDay`).
 */
@Serializable(with = StudyCardSerializer::class)
data class StudyCard(
    /** How many times in a row it was known when it was due: 0 after Again, at most one for each of [STUDY_INTERVALS]. */
    val box: Int = 0,

    /** The day it's due. */
    val due: Long = 0,

    /** The day it was first studied, for how many new cards a day brings. */
    val first: Long = 0,

    /** When it was answered, in milliseconds since 1970: of a card's answers on every device, the latest decides how it's going. 0 when unknown. */
    val at: Long = 0,

    /**
     * What a newer version keeps about the card that this one doesn't know,
     * as it was written: it stays while the card does, and an answer here
     * starts the card afresh without it.
     */
    val extras: Map<String, JsonElement> = emptyMap(),
)

/**
 * Reads and writes a [StudyCard] as a JSON object, leaving out what's 0, and
 * keeping in [StudyCard.extras] the fields this version doesn't know or
 * can't read.
 */
internal object StudyCardSerializer : KSerializer<StudyCard> {
    private val numbers = listOf("box", "due", "first", "at")

    override val descriptor: SerialDescriptor = SerialDescriptor("com.hyuchia.orbit.study.StudyCard", JsonObject.serializer().descriptor)

    override fun deserialize(decoder: Decoder): StudyCard {
        val json = decoder as? JsonDecoder ?: throw SerializationException("A study card is read from JSON")
        val fields = json.decodeJsonElement() as? JsonObject ?: throw SerializationException("A study card is a JSON object")
        val read = numbers.mapNotNull { name -> (fields[name] as? JsonPrimitive)?.takeIf { !it.isString }?.longOrNull?.let { name to it } }.toMap()

        return StudyCard(
            box = read["box"]?.toInt() ?: 0,
            due = read["due"] ?: 0,
            first = read["first"] ?: 0,
            at = read["at"] ?: 0,
            extras = fields.filterKeys { it !in read },
        )
    }

    override fun serialize(encoder: Encoder, value: StudyCard) {
        val json = encoder as? JsonEncoder ?: throw SerializationException("A study card is written as JSON")
        val written = listOf("box" to value.box.toLong(), "due" to value.due, "first" to value.first, "at" to value.at)
            .filter { (_, number) -> number != 0L }
            .associate { (name, number) -> name to JsonPrimitive(number) }

        json.encodeJsonElement(JsonObject(value.extras.filterKeys { it !in written } + written))
    }
}

/**
 * The card after an answer on `today`, given `at` that moment; null is a
 * card never studied. Known when it's due, it moves up a box and waits that
 * box's days. Known before it's due, studying anyway, it keeps its day, and
 * stays just as it was: knowing a card early says little about how long it
 * will stay known. Again brings it back today, to start over from the first
 * box.
 */
fun StudyCard?.answered(knew: Boolean, today: Long, at: Long = 0): StudyCard {
    val card = this ?: StudyCard(due = today, first = today)

    return when {
        !knew -> StudyCard(box = 0, due = today, first = card.first, at = at)
        card.due > today -> card
        else -> {
            val box = (card.box + 1).coerceAtMost(STUDY_INTERVALS.size)
            StudyCard(box = box, due = today + STUDY_INTERVALS[box - 1], first = card.first, at = at)
        }
    }
}

/** Answers in the order they decide how a card is going: the later last, and of two at the same moment, the card further along. */
private val answerOrder = compareBy<StudyCard>({ it.at }, { it.due }, { it.box }, { it.first })

/** Of two answers to a card, the one that decides how it's going (see [answerOrder]); the first, when they're as late and as far along. */
fun latestOf(one: StudyCard, other: StudyCard): StudyCard = if (answerOrder.compare(one, other) >= 0) one else other

/**
 * How each card is going, from this device's answers (`mine`) and the other
 * devices' (`elsewhere`, by device): each card as its latest answer on any of
 * them left it.
 */
fun mergedProgress(mine: Map<String, StudyCard>?, elsewhere: Map<String, Map<String, StudyCard>>?): Map<String, StudyCard> {
    if (elsewhere.isNullOrEmpty()) {
        return mine.orEmpty()
    }

    val merged = LinkedHashMap(mine.orEmpty())

    for (cards in elsewhere.values) {
        for ((idea, card) in cards) {
            merged[idea] = merged[idea]?.let { latestOf(it, card) } ?: card
        }
    }

    return merged
}

/** How many days from `today` the card comes back if it's known now. */
fun StudyCard?.daysIfKnown(today: Long): Long = answered(knew = true, today).due - today

/**
 * The cards under the idea `start`, at any depth, in outline order: the ideas
 * with ideas or a note under them. `start` itself isn't one. `nodes` are the
 * map's, each followed by the ones under it, as the page reports them.
 */
fun cardsUnder(nodes: List<MapNode>, start: String): List<MapNode> {
    val index = nodes.indexOfFirst { it.id == start }

    if (index < 0) {
        return emptyList()
    }

    val depth = nodes[index].depth
    return nodes.drop(index + 1).takeWhile { it.depth > depth }.filter { it.childCount > 0 || !it.note.isNullOrBlank() }
}

/**
 * The cards a session studies, in order: the ones due by `today`, the most
 * overdue first, then new ones in outline order, as many as the day has
 * room for ([newRoom]) of `newADay`, and `moreNew` more. `anyway` studies
 * them all, due or not, in outline order.
 */
fun sessionOrder(
    cards: List<MapNode>,
    progress: Map<String, StudyCard>,
    today: Long,
    anyway: Boolean = false,
    moreNew: Int = 0,
    newADay: Int = NEW_CARDS_A_DAY,
): List<String> {
    if (anyway) {
        return cards.map { it.id }
    }

    val due = cards.filter { card -> progress[card.id]?.let { it.due <= today } == true }.sortedBy { progress.getValue(it.id).due }
    val fresh = cards.filter { it.id !in progress }.take(newRoom(progress, today, newADay) + moreNew)
    return (due + fresh).map { it.id }
}

/** How many new cards `today` still has room for, of `newADay`: the cards first studied today count, on any device. */
fun newRoom(progress: Map<String, StudyCard>, today: Long, newADay: Int): Int = (newADay - progress.values.count { it.first == today }).coerceAtLeast(0)

/** How many of `cards` have never been studied. */
fun newCards(cards: List<MapNode>, progress: Map<String, StudyCard>): Int = cards.count { it.id !in progress }

/**
 * The next day after `today` any of `cards` comes back; new cards left for
 * another day come tomorrow. Null when there are no cards.
 */
fun nextStudy(cards: List<MapNode>, progress: Map<String, StudyCard>, today: Long): Long? =
    cards.minOfOrNull { card -> progress[card.id]?.due?.coerceAtLeast(today + 1) ?: (today + 1) }

/** How many of the map's cards are due by `today`: what its card on Home says. */
fun dueCount(progress: Map<String, StudyCard>?, today: Long): Int = progress?.values?.count { it.due <= today } ?: 0

/** Why a session shows its summary rather than a card. */
enum class StudyEnding {
    /** Every card of the session is known. */
    Finished,

    /** None of the cards is due, and the day's new cards are studied. */
    CaughtUp,

    /** The idea has no cards under it. */
    NoCards,
}

/**
 * A study session: the cards still to go, the one on screen first, and how
 * the session has gone. It ends when every card has been known once; a card
 * answered with Again comes back [AGAIN_AFTER] cards later.
 */
data class Study(
    /** The idea studying began at: the cards are the ideas under it. */
    val start: String,

    val queue: List<String>,

    /** How many cards the session has. */
    val total: Int,

    /** The cards known so far. */
    val known: Int = 0,

    /** The cards that needed Again. */
    val missed: Set<String> = emptySet(),

    /** The card on screen shows what's under it. */
    val revealed: Boolean = false,

    /** Studying anyway: every card, due or not. */
    val anyway: Boolean = false,

    /** Why the summary shows, once studying is over or can't begin. */
    val ending: StudyEnding? = null,

    /** The answer before, to take back: the session as it was before it, the card and how that card was going. */
    val last: StudyAnswer? = null,

    /** How many days the card on screen waits if it's known now. */
    val ifKnown: Long = 1,

    /** In how many days cards come back next, for the summary. */
    val nextIn: Long? = null,

    /** New cards left for another day, which "Keep going" brings in now. */
    val newLeft: Int = 0,
) {
    val current: String? get() = queue.firstOrNull()

    /** The card on screen's place among the session's cards, from 1. */
    val position: Int get() = (known + 1).coerceAtMost(total)

    /** The cards known on the first try. */
    val knewFirst: Int get() = total - missed.size

    /** The session after the card on screen is answered: known, or Again, which brings it back later. */
    fun answered(knew: Boolean): Study {
        val card = current ?: return this
        val rest = queue.drop(1)
        val queue = if (knew) rest else rest.toMutableList().apply { add(minOf(AGAIN_AFTER, size), card) }
        return copy(
            queue = queue,
            known = if (knew) known + 1 else known,
            missed = if (knew) missed else missed + card,
            revealed = false,
            ending = if (queue.isEmpty()) StudyEnding.Finished else null,
        )
    }
}

/**
 * An answer, to take back: the session before it, its card, how that card
 * was going before (null: never studied), and this device's own answer to
 * it before (null: none), which taking it back restores.
 */
data class StudyAnswer(val before: Study, val card: String, val progress: StudyCard?, val mine: StudyCard? = progress)
