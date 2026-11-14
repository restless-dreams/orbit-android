package com.hyuchia.orbit.study

import com.hyuchia.orbit.data.MetadataFile
import com.hyuchia.orbit.page.MapNode
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class StudyTest {
    private val today = 20_000L

    /** Photosynthesis, as the page reports it: each idea followed by the ones under it. */
    private val nodes = listOf(
        MapNode("photosynthesis", "Photosynthesis", depth = 0, childCount = 3),
        MapNode("light", "Light reactions", parentId = "photosynthesis", depth = 1, childCount = 2, note = "In the thylakoids."),
        MapNode("photosystem-ii", "Photosystem II", parentId = "light", depth = 2),
        MapNode("photosystem-i", "Photosystem I", parentId = "light", depth = 2, childCount = 1),
        MapNode("p700", "P700", parentId = "photosystem-i", depth = 3),
        MapNode("calvin", "Calvin cycle", parentId = "photosynthesis", depth = 1, note = "In the stroma."),
        MapNode("inputs", "Inputs", parentId = "photosynthesis", depth = 1, childCount = 1, note = "   "),
        MapNode("water", "Water", parentId = "inputs", depth = 2),
    )

    @Test
    fun `a card known when it's due moves up a box and waits that box's days`() {
        val new = null.answered(knew = true, today)
        assertEquals(StudyCard(box = 1, due = today + 1, first = today), new)
        val second = new.answered(knew = true, today + 1)
        assertEquals(StudyCard(box = 2, due = today + 1 + 3, first = today), second)
        var card = second

        STUDY_INTERVALS.drop(2).forEachIndexed { index, days ->
            val day = card.due
            card = card.answered(knew = true, day)
            assertEquals(index + 3, card.box)
            assertEquals(day + days, card.due)
        }

        // The last box keeps its interval.
        val last = card.answered(knew = true, card.due)
        assertEquals(STUDY_INTERVALS.size, last.box)
        assertEquals(card.due + STUDY_INTERVALS.last(), last.due)
    }

    @Test
    fun `Again brings a card back today, to start over from the first box`() {
        val known = StudyCard(box = 4, due = today, first = today - 30)
        assertEquals(StudyCard(box = 0, due = today, first = today - 30), known.answered(knew = false, today))
        assertEquals(StudyCard(box = 0, due = today, first = today), null.answered(knew = false, today))
        // Known once it has come back, it waits a day.
        assertEquals(StudyCard(box = 1, due = today + 1, first = today - 30), known.answered(knew = false, today).answered(knew = true, today))
    }

    @Test
    fun `a card known before it's due keeps its day, and a missed one still starts over`() {
        val waiting = StudyCard(box = 3, due = today + 5, first = today - 10)
        assertSame(waiting, waiting.answered(knew = true, today))
        assertEquals(5L, waiting.daysIfKnown(today))
        assertEquals(StudyCard(box = 0, due = today, first = today - 10), waiting.answered(knew = false, today))
    }

    @Test
    fun `how long a card waits if it's known now`() {
        assertEquals(1L, null.daysIfKnown(today))
        assertEquals(7L, StudyCard(box = 2, due = today).daysIfKnown(today))
        assertEquals(180L, StudyCard(box = STUDY_INTERVALS.size, due = today).daysIfKnown(today))
    }

    @Test
    fun `the cards are the ideas under where studying begins with ideas or a note under them, in outline order`() {
        assertEquals(listOf("light", "photosystem-i", "calvin", "inputs"), cardsUnder(nodes, "photosynthesis").map { it.id })
        assertEquals(listOf("photosystem-i"), cardsUnder(nodes, "light").map { it.id })
        assertEquals(emptyList<MapNode>(), cardsUnder(nodes, "calvin"))
        assertEquals(emptyList<MapNode>(), cardsUnder(nodes, "gone"))
    }

    @Test
    fun `a session takes the due cards, the most overdue first, then the day's new ones in outline order`() {
        val cards = cardsUnder(nodes, "photosynthesis")
        val progress = mapOf(
            "calvin" to StudyCard(box = 2, due = today - 1, first = today - 10),
            "inputs" to StudyCard(box = 1, due = today - 4, first = today - 10),
            "photosystem-i" to StudyCard(box = 3, due = today + 2, first = today - 10),
        )
        assertEquals(listOf("inputs", "calvin", "light"), sessionOrder(cards, progress, today))
        assertEquals(listOf("light", "photosystem-i", "calvin", "inputs"), sessionOrder(cards, progress, today, anyway = true))
    }

    @Test
    fun `new cards are held to the day's limit, and keeping going brings in more`() {
        val many = listOf(MapNode("top", "Top", depth = 0)) + (1..30).map { MapNode("idea-$it", "Idea $it", parentId = "top", depth = 1, note = "Note $it") }
        val cards = cardsUnder(many, "top")
        assertEquals(NEW_CARDS_A_DAY, sessionOrder(cards, emptyMap(), today).size)
        val studiedToday = cards.take(15).associate { it.id to StudyCard(box = 1, due = today + 1, first = today) }
        assertEquals(cards.drop(15).take(NEW_CARDS_A_DAY - 15).map { it.id }, sessionOrder(cards, studiedToday, today))
        assertEquals(15, sessionOrder(cards, studiedToday, today, moreNew = NEW_CARDS_A_DAY).size)
        assertEquals(15, newCards(cards, studiedToday))
    }

    @Test
    fun `the learner's new cards a day decide how many a session brings, counting the ones first studied today on any device`() {
        val many = listOf(MapNode("top", "Top", depth = 0)) + (1..30).map { MapNode("idea-$it", "Idea $it", parentId = "top", depth = 1, note = "Note $it") }
        val cards = cardsUnder(many, "top")
        assertEquals(5, sessionOrder(cards, emptyMap(), today, newADay = 5).size)
        assertEquals(30, sessionOrder(cards, emptyMap(), today, newADay = 50).size)
        // Three studied here today and two on the tablet: the day has room for five more of ten.
        val here = cards.take(3).associate { it.id to StudyCard(box = 1, due = today + 1, first = today, at = 1) }
        val tablet = mapOf("tablet" to cards.drop(3).take(2).associate { it.id to StudyCard(box = 1, due = today + 1, first = today, at = 2) })
        val progress = mergedProgress(here, tablet)
        assertEquals(5, newRoom(progress, today, newADay = 10))
        assertEquals(cards.drop(5).take(5).map { it.id }, sessionOrder(cards, progress, today, newADay = 10))
        assertEquals(0, newRoom(progress, today, newADay = 5))
        assertEquals(cards.drop(5).take(5).map { it.id }, sessionOrder(cards, progress, today, moreNew = 5, newADay = 5))
    }

    @Test
    fun `of every device's answers to a card, the latest decides how it goes, and the card further along when they're as late`() {
        val mine = mapOf(
            "ser" to StudyCard(box = 3, due = today + 7, first = today - 20, at = 1_000),
            "estar" to StudyCard(box = 1, due = today + 1, first = today, at = 5_000),
        )
        val elsewhere = mapOf(
            "tablet" to mapOf(
                "ser" to StudyCard(box = 0, due = today, first = today - 20, at = 2_000),
                "estar" to StudyCard(box = 2, due = today + 3, first = today - 5, at = 4_000),
                "tener" to StudyCard(box = 1, due = today + 1, first = today, at = 3_000),
            ),
            // Answers from before answers had times, here and there: the card further along goes on.
            "old phone" to mapOf("ir" to StudyCard(box = 2, due = today + 3, first = today - 9)),
        )
        val merged = mergedProgress(mine + ("ir" to StudyCard(box = 1, due = today + 1, first = today - 9)), elsewhere)
        assertEquals(StudyCard(box = 0, due = today, first = today - 20, at = 2_000), merged["ser"])
        assertEquals(mine["estar"], merged["estar"])
        assertEquals(elsewhere.getValue("tablet")["tener"], merged["tener"])
        assertEquals(StudyCard(box = 2, due = today + 3, first = today - 9), merged["ir"])
        assertSame(mine, mergedProgress(mine, null))
        assertEquals(elsewhere.getValue("tablet"), mergedProgress(null, mapOf("tablet" to elsewhere.getValue("tablet"))))
    }

    @Test
    fun `an answer keeps its moment, and starts the card afresh without what a newer version kept about it`() {
        val kept = StudyCard(box = 2, due = today, first = today - 9, at = 1_000, extras = mapOf("ease" to JsonPrimitive(2.5)))
        assertEquals(StudyCard(box = 3, due = today + 7, first = today - 9, at = 9_000), kept.answered(knew = true, today, at = 9_000))
        assertEquals(StudyCard(box = 0, due = today, first = today - 9, at = 9_000), kept.answered(knew = false, today, at = 9_000))
        // Known early, it stays just as it was, moment and all.
        assertSame(kept, kept.answered(knew = true, today - 1, at = 9_000))
    }

    @Test
    fun `a card's file keeps what a newer version wrote, leaves out what's 0, and keeps a number it can't read as it was`() {
        val json = Json { ignoreUnknownKeys = true }
        val read = json.decodeFromString(StudyCard.serializer(), """{"box":2,"due":20000,"first":19990,"at":1800000000000,"ease":2.5,"box2":{"a":1}}""")
        assertEquals(StudyCard(box = 2, due = 20_000, first = 19_990, at = 1_800_000_000_000), read.copy(extras = emptyMap()))
        assertEquals(setOf("ease", "box2"), read.extras.keys)
        assertEquals("""{"ease":2.5,"box2":{"a":1},"box":2,"due":20000,"first":19990,"at":1800000000000}""", json.encodeToString(StudyCard.serializer(), read))
        assertEquals("""{"due":20000}""", json.encodeToString(StudyCard.serializer(), StudyCard(due = 20_000)))
        // A field this version reads, written in another shape, stays as it was until the card is answered.
        val odd = json.decodeFromString(StudyCard.serializer(), """{"box":"two","due":20000}""")
        assertEquals(0, odd.box)
        assertEquals("""{"box":"two","due":20000}""", json.encodeToString(StudyCard.serializer(), odd))
        // In meta.json, the map's other fields and its study go together.
        val metadata = MetadataFile.decode("""{"pinned":true,"study":{"ser":{"box":1,"due":20001,"first":20000,"at":5,"ease":2.5}},"studyElsewhere":{"tablet":{"estar":{"due":20002}}}}""")
        assertEquals(setOf("ser", "estar"), metadata.studyProgress.keys)
        assertEquals(
            """{"pinned":true,"study":{"ser":{"ease":2.5,"box":1,"due":20001,"first":20000,"at":5}},"studyElsewhere":{"tablet":{"estar":{"due":20002}}}}""",
            MetadataFile.encode(metadata),
        )
    }

    @Test
    fun `a box out of the boxes' range is kept as it was written, and an answer goes on from the nearest box`() {
        val json = Json { ignoreUnknownKeys = true }

        for ((written, nearest) in listOf(-1 to 0, Int.MIN_VALUE to 0, 8 to STUDY_INTERVALS.size, Int.MAX_VALUE to STUDY_INTERVALS.size)) {
            val card = json.decodeFromString(StudyCard.serializer(), """{"box":$written,"due":$today,"first":${today - 9}}""")
            assertEquals(written, card.box)
            assertEquals("""{"box":$written,"due":$today,"first":${today - 9}}""", json.encodeToString(StudyCard.serializer(), card))

            // Known, it moves on from the nearest box; Again starts it over; known early, it stays just as it was.
            val box = (nearest + 1).coerceAtMost(STUDY_INTERVALS.size)
            assertEquals(StudyCard(box = box, due = today + STUDY_INTERVALS[box - 1], first = today - 9, at = 5), card.answered(knew = true, today, at = 5))
            assertEquals(STUDY_INTERVALS[box - 1].toLong(), card.daysIfKnown(today))
            assertEquals(StudyCard(box = 0, due = today, first = today - 9, at = 5), card.answered(knew = false, today, at = 5))
            val early = card.copy(due = today + 2)
            assertSame(early, early.answered(knew = true, today))
        }

        // A box past what a whole number holds can't be read: it stays as it was written, beside the card's other fields.
        val huge = json.decodeFromString(StudyCard.serializer(), """{"box":4294967297,"due":$today}""")
        assertEquals(0, huge.box)
        assertEquals("""{"box":4294967297,"due":$today}""", json.encodeToString(StudyCard.serializer(), huge))
        assertEquals(StudyCard(box = 1, due = today + 1, at = 5), huge.answered(knew = true, today, at = 5))
    }

    @Test
    fun `the map's cards are its ideas with ideas or a note under them, but its top one`() {
        assertEquals(setOf("light", "photosystem-i", "calvin", "inputs"), mapCards(nodes))
        assertEquals(emptySet<String>(), mapCards(emptyList()))
        val progress = mapOf("light" to StudyCard(due = today), "photosystem-ii" to StudyCard(due = today), "gone" to StudyCard(due = today))
        assertEquals(setOf("photosystem-ii", "gone"), notCards(progress, mapCards(nodes)))
    }

    @Test
    fun `a session without cards the map no longer has drops them from what's to go and its count, and from what Undo brings back`() {
        val before = Study(start = "top", queue = listOf("a", "b", "c"), total = 4, known = 1, revealed = true)
        val session = Study(start = "top", queue = listOf("b", "c", "a"), total = 4, known = 1, missed = setOf("a"), last = StudyAnswer(before, "a", null))

        // B, on screen, went: C comes, hidden, and A stays, still missed.
        val left = session.copy(revealed = true).without(setOf("b"))
        assertEquals(listOf("c", "a"), left.queue)
        assertEquals(3, left.total)
        assertFalse(left.revealed)
        assertEquals(setOf("a"), left.missed)
        assertEquals(listOf("a", "c"), left.last?.before?.queue)

        // A went: it's no longer missed or to take back, and the card on screen stays as it was.
        val withoutA = session.copy(revealed = true).without(setOf("a"))
        assertEquals(listOf("b", "c"), withoutA.queue)
        assertEquals(emptySet<String>(), withoutA.missed)
        assertNull(withoutA.last)
        assertTrue(withoutA.revealed)
        assertSame(session, session.without(setOf("elsewhere")))
    }

    @Test
    fun `an answer is kept as a change to its card, against this device's answers as they are when it's written`() {
        val ser = StudyCard(box = 1, due = today + 1, first = today, at = 5)
        val estar = StudyCard(box = 2, due = today + 3, first = today - 9, at = 4)
        val tablet = mapOf("tablet" to mapOf("estar" to estar))
        assertEquals(mapOf("estar" to estar, "ser" to ser), StudyChange.Answer("ser", ser, before = null).applyTo(mapOf("estar" to estar), emptyMap()))

        // A later answer there instead (another device's, folded in as the folder was left) stays; this device's own
        // answer before it is answered over, whatever the phone's clock did meanwhile.
        val tabletLater = StudyCard(box = 4, due = today + 16, first = today - 9, at = 9)
        assertEquals(mapOf("ser" to tabletLater), StudyChange.Answer("ser", ser, before = null).applyTo(mapOf("ser" to tabletLater), emptyMap()))
        assertEquals(mapOf("ser" to tabletLater), StudyChange.Answer("ser", ser, before = estar).applyTo(mapOf("ser" to tabletLater), emptyMap()))
        assertEquals(mapOf("ser" to ser), StudyChange.Answer("ser", ser, before = tabletLater).applyTo(mapOf("ser" to tabletLater), emptyMap()))
        assertEquals(mapOf("estar" to estar), StudyChange.Forget(setOf("ser", "gone")).applyTo(mapOf("estar" to estar, "ser" to ser), emptyMap()))

        // Taken back, this device's own answer before comes back beside the tablet's, or none.
        val again = StudyCard(box = 0, due = today, first = today - 9, at = 9)
        assertEquals(emptyMap<String, StudyCard>(), StudyChange.Undo("estar", again, own = null, progress = estar).applyTo(mapOf("estar" to again), tablet))
        assertEquals(mapOf("estar" to ser), StudyChange.Undo("estar", again, own = ser, progress = estar).applyTo(mapOf("estar" to again), tablet))
        // Once the tablet's answers were folded into this device's, how the card was going comes back.
        assertEquals(mapOf("estar" to estar), StudyChange.Undo("estar", again, own = null, progress = estar).applyTo(mapOf("estar" to again), emptyMap()))
        // A later answer the folder brought in stays.
        val later = again.copy(at = 10)
        assertEquals(mapOf("estar" to later), StudyChange.Undo("estar", again, own = null, progress = estar).applyTo(mapOf("estar" to later), emptyMap()))
    }

    @Test
    fun `cards come back next on the soonest day after today, new ones tomorrow`() {
        val cards = cardsUnder(nodes, "photosynthesis")
        val all = cards.associate { it.id to StudyCard(box = 2, due = today + 3, first = today - 5) }
        assertEquals(today + 3, nextStudy(cards, all, today))
        assertEquals(today + 1, nextStudy(cards, all - "calvin", today))
        assertNull(nextStudy(emptyList(), emptyMap(), today))
    }

    @Test
    fun `Home counts the cards due`() {
        val progress = mapOf("a" to StudyCard(due = today), "b" to StudyCard(due = today - 3), "c" to StudyCard(due = today + 1))
        assertEquals(2, dueCount(progress, today))
        assertEquals(0, dueCount(null, today))
    }

    @Test
    fun `a session ends once every card is known, and Again brings a card back a few cards later`() {
        val start = Study(start = "top", queue = listOf("a", "b", "c", "d", "e"), total = 5, revealed = true)
        val missed = start.answered(knew = false)
        assertEquals(listOf("b", "c", "d", "a", "e"), missed.queue)
        assertEquals(setOf("a"), missed.missed)
        assertEquals(1, missed.position)
        assertEquals(false, missed.revealed)
        var session = missed

        while (session.ending == null) {
            session = session.copy(revealed = true).answered(knew = true)
        }

        assertEquals(StudyEnding.Finished, session.ending)
        assertEquals(5, session.known)
        assertEquals(4, session.knewFirst)
        // The last card, missed, comes straight back.
        assertEquals(listOf("z"), Study(start = "top", queue = listOf("z"), total = 1).answered(knew = false).queue)
    }
}
