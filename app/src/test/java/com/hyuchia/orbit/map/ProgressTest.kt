package com.hyuchia.orbit.map

import com.hyuchia.orbit.FileWords
import com.hyuchia.orbit.page.MapNode
import com.hyuchia.orbit.page.MapState
import com.hyuchia.orbit.page.Media
import com.hyuchia.orbit.page.protocolJson
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

class ProgressTest {
    private val words = FileWords(Locale.US)

    private fun attributes(json: String): JsonObject = protocolJson.parseToJsonElement(json).jsonObject

    private fun goal(id: String, parentId: String, json: String = "{}") = MapNode(id = id, text = id, parentId = parentId, type = "goal", attrs = attributes(json))

    private fun toDo(id: String, parentId: String, status: String = "todo") = MapNode(id = id, text = id, parentId = parentId, status = status)

    private fun idea(id: String, parentId: String?, type: String? = null) = MapNode(id = id, text = id, parentId = parentId, type = type)

    private fun MapProgress.ringed(nodes: List<MapNode>): List<String> = nodes.filter { ringByItself(it) }.map { it.id }

    /** The map `web/rings.test.ts` draws: rings by themselves, and what keeps its look. */
    private val weekend = listOf(
        idea("weekend", null),
        idea("trip", "weekend"),
        toDo("train", "trip", "done"),
        toDo("pack", "trip"),
        toDo("socks", "pack", "done"),
        toDo("shirts", "pack"),
        goal("hotel", "trip", """{"target": 4, "current": 2}"""),
        idea("chores", "weekend"),
        idea("laundry", "chores"),
        toDo("whites", "laundry", "done"),
        toDo("colors", "laundry"),
        idea("savings-plan", "weekend"),
        goal("someday", "savings-plan"),
        idea("launch", "weekend", type = "milestone"),
        toDo("post", "launch"),
        idea("garden", "weekend").copy(image = Media(src = "garden.jpg")),
        toDo("tulips", "garden"),
        idea("talk", "weekend").copy(video = Media(src = "https://www.youtube.com/watch?v=dQw4w9WgXcQ")),
        toDo("rehearse", "talk"),
        idea("ideas", "weekend"),
        idea("read", "ideas"),
    )

    private fun node(id: String, nodes: List<MapNode> = weekend) = nodes.first { it.id == id }

    @Test
    fun ideasWithAToDoOrAGoalWithATargetRightUnderThemShowARing() {
        val progress = MapProgress(weekend)
        assertEquals(listOf("trip", "pack", "laundry"), progress.ringed(weekend))
        // Chores only has to-dos further down; Savings plan has a goal without a target.
        assertFalse(progress.ringByItself(node("chores")))
        assertFalse(progress.ringByItself(node("savings-plan")))
        assertFalse(progress.ringByItself(node("weekend")))
        assertTrue(progress.followsItsIdeas(node("trip")))
        assertFalse(progress.followsItsIdeas(node("chores")))
    }

    @Test
    fun kindsAndPicturesKeepTheirLook() {
        val progress = MapProgress(weekend)
        assertFalse(progress.ringByItself(node("launch")))
        assertFalse(progress.ringByItself(node("garden")))
        assertFalse(progress.ringByItself(node("talk")))
        // Their to-dos still count, for them and for every idea above them.
        assertEquals(1, progress.under(node("launch")).total)
    }

    @Test
    fun aStatusThatIsALabelNeitherShowsARingNorCounts() {
        val nodes = listOf(
            idea("library", null),
            idea("copies", "library"),
            toDo("id", "copies", "primary key"),
            toDo("series", "copies", "foreign key"),
            idea("launch", "library"),
            toDo("beta", "launch", "closed"),
            toDo("docs", "launch", "In-Progress"),
            toDo("talk", "launch", "WIP"),
            toDo("name", "launch", "maybe later"),
        )
        val progress = MapProgress(nodes)
        assertFalse(progress.ringByItself(node("copies", nodes)))
        assertEquals(0, progress.under(node("copies", nodes)).total)
        // Closed is done; in progress and WIP are to-dos still open; "maybe later" is a label.
        assertTrue(progress.ringByItself(node("launch", nodes)))
        val launch = progress.under(node("launch", nodes))
        assertEquals(3, launch.total)
        assertEquals(1, launch.finished)
        assertTrue(node("beta", nodes).isDone)
        assertFalse(node("name", nodes).isToDo)
    }

    @Test
    fun aMapWithRingsOffShowsNoneByThemselvesAndCountsTheSame() {
        val on = MapProgress(weekend)
        val off = MapProgress(weekend, ringsOn = false)
        assertEquals(emptyList<String>(), off.ringed(weekend))
        assertFalse(off.followsItsIdeas(node("trip")))
        // Goals without a target still follow the ideas under them.
        assertTrue(off.followsItsIdeas(node("someday")))
        assertEquals(on.rollUps, off.rollUps)
    }

    @Test
    fun theSettingComesFromTheMapsOptions() {
        val off = protocolJson.decodeFromString<MapState>("""{"title": "Trip", "nodes": [], "options": {"progressRings": false}}""")
        val on = protocolJson.decodeFromString<MapState>("""{"title": "Trip", "nodes": [], "options": {"theme": "cosmos"}}""")
        assertEquals(false, off.options.progressRings)
        val trip = listOf(idea("trip", null), toDo("passport", "trip"))
        assertFalse(MapProgress.of(off.copy(nodes = trip)).ringByItself(trip[0]))
        assertTrue(MapProgress.of(on.copy(nodes = trip)).ringByItself(trip[0]))
    }

    @Test
    fun eachToDoAndGoalCountsOnce() {
        val progress = MapProgress(weekend)
        // The train booked, Socks packed, Shirts not yet, the Hotel halfway: Pack counts through its own to-dos.
        val trip = progress.under(node("trip"))
        assertEquals(4, trip.total)
        assertEquals(2.5, trip.done, 1e-9)
        assertEquals(2, trip.finished)
        assertEquals(0.5, progress.under(node("pack")).share, 1e-9)
        assertEquals("2 of 4 done · 63%", GoalNumbers.progress(trip, words, Locale.US))
    }

    @Test
    fun aProgressIdeaAboveCountsOnlyTheIdeasUnderTheseIdeasNeverThemselves() {
        val nodes = weekend.map { if (it.id == "weekend") it.copy(type = "progress") else it }
        val all = MapProgress(nodes).under(node("weekend", nodes))
        // Trip's 4, Laundry's 2, and the to-dos under Launch, Garden and Talk; never Trip, Pack or Laundry.
        assertEquals(9, all.total)
        assertEquals(3.5, all.done, 1e-9)
        assertEquals("3 of 9 done · 39%", GoalNumbers.progress(all, words, Locale.US))
        // In Spanish, what's done agrees with how many are.
        val spanish = Locale.forLanguageTag("es-MX")
        assertTrue(GoalNumbers.progress(all, FileWords(spanish), spanish).startsWith("3 de 9 hechas · 39"))
        assertTrue(GoalNumbers.progress(Rollup(total = 4, done = 1.0, finished = 1), FileWords(spanish), spanish).startsWith("1 de 4 hecha · 25"))
    }

    @Test
    fun progressIdeasCountEachGoalByHowFarAlongItIs() {
        val nodes = listOf(
            idea("plans", null),
            idea("groceries", "plans", type = "progress"),
            goal("apples", "groceries", """{"target": 5, "current": 2}"""),
            goal("eggs", "groceries", """{"target": 12, "current": 12}"""),
            idea("launch", "plans", type = "Progress"),
            toDo("design", "launch", "done"),
            toDo("build", "launch", "active"),
            idea("empty", "plans", type = "progress"),
        )
        val progress = MapProgress(nodes)
        assertEquals(0.7, progress.under(node("groceries", nodes)).share, 1e-9)
        assertEquals(0.5, progress.under(node("launch", nodes)).share, 1e-9)
        assertEquals(0, progress.under(node("empty", nodes)).total)
        assertEquals("1 of 2 reached · 70%", GoalNumbers.progress(progress.under(node("groceries", nodes)), words, Locale.US))
        assertTrue(progress.followsItsIdeas(node("empty", nodes)))
        assertFalse(progress.followsItsIdeas(node("plans", nodes)))
    }

    /** The map `web/goal.test.ts` counts: goals that follow the ideas under them. */
    private val plans = listOf(
        idea("plans", null, type = "progress"),
        goal("trip", "plans"),
        goal("flights", "trip", """{"target": 1, "current": 1}"""),
        goal("hotel", "trip", """{"target": 4, "current": 2}"""),
        toDo("train", "trip", "done"),
        toDo("passport", "trip"),
        idea("groceries", "plans", type = "progress"),
        goal("apples", "groceries", """{"target": 5, "current": 2}"""),
        goal("pantry", "groceries"),
        goal("rice", "pantry"),
        toDo("brown", "rice", "done"),
        toDo("basmati", "rice"),
        goal("savings", "plans", """{"target": 1000, "current": 250, "unit": "USD"}"""),
        toDo("account", "savings", "done"),
        goal("someday", "plans"),
        idea("read", "someday"),
    )

    @Test
    fun goalsWithoutATargetFollowTheGoalsAndToDosUnderThem() {
        val progress = MapProgress(plans)
        val trip = progress.under(node("trip", plans))
        assertEquals(4, trip.total)
        assertEquals(2.5, trip.done, 1e-9)
        assertEquals("2 of 4 done · 63%", GoalNumbers.progress(trip, words, Locale.US))
        assertTrue(progress.followsItsIdeas(node("trip", plans)))
        // Pantry and Rice have no target: only the two rices count under them, and Groceries counts Apples and those.
        assertEquals(2, progress.under(node("pantry", plans)).total)
        val groceries = progress.under(node("groceries", plans))
        assertEquals(3, groceries.total)
        assertEquals(1.4, groceries.done, 1e-9)
        // The whole map: the trip's 4, the groceries' 3, Savings and its account; never Trip, Pantry, Rice or Someday.
        val all = progress.under(node("plans", plans))
        assertEquals(9, all.total)
        assertEquals(2.5 + 1.4 + 0.25 + 1, all.done, 1e-9)
    }

    @Test
    fun aGoalWithATargetShowsItsOwnNumbersWhateverIsUnderIt() {
        val progress = MapProgress(plans)
        val savings = node("savings", plans)
        assertFalse(progress.followsItsIdeas(savings))
        assertFalse(progress.ringByItself(savings))
        assertEquals(1, progress.under(savings).total)
    }

    @Test
    fun theEditorSaysAGoalWithoutATargetFollowsTheIdeasUnderIt() {
        val progress = MapProgress(plans)
        val trip = Goal.of(node("trip", plans))!!
        assertEquals(
            "Follows the goals and to-dos under it: 2 of 4 done · 63%",
            GoalNumbers.editorSummary(trip, hasIdeasUnder = true, under = progress.under(node("trip", plans)), words = words, locale = Locale.US),
        )
        val someday = Goal.of(node("someday", plans))!!
        assertEquals(
            "No target yet. Without one, it follows the goals and to-dos under it.",
            GoalNumbers.editorSummary(someday, hasIdeasUnder = true, under = progress.under(node("someday", plans)), words = words, locale = Locale.US),
        )
        assertEquals("No target yet", GoalNumbers.editorSummary(someday, hasIdeasUnder = false, under = Rollup(), words = words, locale = Locale.US))
        val savings = Goal.of(node("savings", plans))!!
        assertEquals(GoalNumbers.summary(savings, words, Locale.US), GoalNumbers.editorSummary(savings, hasIdeasUnder = true, under = progress.under(node("savings", plans)), words = words, locale = Locale.US))
    }
}
