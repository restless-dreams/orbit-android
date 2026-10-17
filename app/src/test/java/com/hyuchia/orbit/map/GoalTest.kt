package com.hyuchia.orbit.map

import com.hyuchia.orbit.FileWords
import com.hyuchia.orbit.page.MapNode
import com.hyuchia.orbit.page.protocolJson
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.util.Currency
import java.util.Locale

class GoalTest {
    private fun attributes(json: String): JsonObject = protocolJson.parseToJsonElement(json).jsonObject

    private fun goal(json: String): Goal = Goal.read(attributes(json))

    private fun node(type: String?, json: String?): MapNode = MapNode(id = "apples", text = "Apples", type = type, attrs = json?.let(::attributes))

    private val dollars: Currency = Currency.getInstance("USD")

    private val words = FileWords(Locale.US)

    @Test
    fun aGoalIsReadFromItsNode() {
        val apples = Goal.of(node("goal", """{"target": 5, "current": 2, "unit": "apples"}"""))!!
        assertEquals(BigDecimal(5), apples.target)
        assertEquals(BigDecimal(2), apples.current)
        assertEquals(BigDecimal.ZERO, apples.start)
        assertEquals("apples", apples.word)
        assertNull(apples.currency)
        assertEquals(GoalWay.Counting, apples.way)
        assertTrue(Goal.of(node(" Goal ", """{"target": 5}""")) != null)
        assertNull(Goal.of(node("progress", """{"target": 5}""")))
        assertNull(Goal.of(node(null, null)))
    }

    @Test
    fun aGoalWithoutAttributesHasNoTargetYet() {
        val empty = Goal.of(node("goal", null))!!
        assertNull(empty.target)
        assertEquals(BigDecimal.ZERO, empty.current)
        assertEquals(0.0, empty.share, 0.0)
        assertFalse(empty.reached)
    }

    @Test
    fun numbersAreWrittenNumbersOrTextThatIsOne() {
        val written = goal("""{"target": "12", "current": " 2.5 ", "start": "1"}""")
        assertEquals(BigDecimal(12), written.target)
        assertEquals(BigDecimal("2.5"), written.current)
        assertEquals(BigDecimal.ONE, written.start)
        val unreadable = goal("""{"target": "lots", "current": true, "start": [1]}""")
        assertNull(unreadable.target)
        assertEquals(BigDecimal.ZERO, unreadable.start)
        assertEquals(BigDecimal.ZERO, unreadable.current)
        assertNull(goal("""{"target": "1e3"}""").target)
        assertNull(goal("""{"target": "1,250"}""").target)
        assertNull(goal("""{"target": 1000000000000000}""").target)
        assertNull(goal("""{"target": null}""").target)
    }

    @Test
    fun currentStartsWhereTheGoalStarted() {
        assertEquals(BigDecimal(4800), goal("""{"start": 4800, "target": 0}""").current)
    }

    @Test
    fun theShareCountsUpFromTheStart() {
        assertEquals(0.4, goal("""{"target": 5, "current": 2}""").share, 1e-9)
        assertEquals(0.5, goal("""{"start": 10, "target": 20, "current": 15}""").share, 1e-9)
        assertEquals(1250.0 / 3000, goal("""{"target": 3000, "current": 1250, "unit": "USD"}""").share, 1e-9)
    }

    @Test
    fun countingDownIsHowMuchIsPaid() {
        val card = goal("""{"start": 4800, "current": 3100, "target": 0, "unit": "USD"}""")
        assertTrue(card.countsDown)
        assertEquals(GoalWay.PayingOff, card.way)
        assertEquals(1700.0 / 4800, card.share, 1e-9)
        assertEquals(BigDecimal(1700), card.done)
        assertEquals(BigDecimal(3100), card.left)
        assertFalse(card.reached)
        assertTrue(goal("""{"start": 4800, "current": 0, "target": 0}""").reached)
    }

    @Test
    fun aTargetWhereTheGoalStartedIsReached() {
        val goal = goal("""{"start": 3, "target": 3, "current": 1}""")
        assertTrue(goal.reached)
        assertEquals(1.0, goal.share, 0.0)
        assertTrue(goal("""{"target": 0}""").reached)
    }

    @Test
    fun pastTheTargetIsReachedAndBelowTheStartIsNotBegun() {
        val over = goal("""{"target": 5, "current": 7}""")
        assertTrue(over.reached)
        assertEquals(1.0, over.share, 0.0)
        assertEquals(BigDecimal.ZERO, over.left)
        assertEquals(0.0, goal("""{"target": 5, "current": -2}""").share, 0.0)
        assertEquals(0.0, goal("""{"start": 4800, "target": 0, "current": 5000}""").share, 0.0)
        assertFalse(goal("""{"target": 5, "current": 4.999}""").reached)
    }

    @Test
    fun theWayOfCountingComesFromTheAttributes() {
        assertEquals(GoalWay.PayingOff, goal("""{"start": 4800, "target": 0}""").way)
        assertEquals(GoalWay.PayingOff, goal("""{"start": 20, "target": 5, "unit": "km"}""").way)
        assertEquals(GoalWay.SavingUp, goal("""{"target": 3000, "unit": "EUR"}""").way)
        assertEquals(GoalWay.SavingUp, goal("""{"target": 3000, "unit": "mxn"}""").way)
        assertEquals(GoalWay.Counting, goal("""{"target": 5, "unit": "apples"}""").way)
        assertEquals(GoalWay.Counting, goal("""{"target": 5, "unit": "XYZ"}""").way)
        assertEquals(GoalWay.Counting, goal("""{"target": 5}""").way)
    }

    @Test
    fun eachWaysNumbersAreItsOwnAttributes() {
        val card = attributes("""{"start": 4800, "current": 3100, "target": 0, "unit": "USD"}""")
        assertEquals(BigDecimal(4800) to BigDecimal(3100), Goal.read(card).fields(GoalWay.PayingOff, card))
        val trip = attributes("""{"target": 3000, "unit": "USD"}""")
        assertEquals(BigDecimal(3000) to null, Goal.read(trip).fields(GoalWay.SavingUp, trip))
        assertEquals(mapOf("target" to JsonPrimitive(BigDecimal(3500))), Goal.read(trip).firstChanges(GoalWay.SavingUp, BigDecimal(3500)))
        // What was owed at the start comes with 0 as the target, unless there's one already.
        assertEquals(
            mapOf("start" to JsonPrimitive(BigDecimal(4800)), "target" to JsonPrimitive(BigDecimal.ZERO)),
            goal("""{"unit": "USD"}""").firstChanges(GoalWay.PayingOff, BigDecimal(4800)),
        )
        assertEquals(mapOf("start" to JsonPrimitive(BigDecimal(900))), Goal.read(card).firstChanges(GoalWay.PayingOff, BigDecimal(900)))
        assertEquals(mapOf("target" to JsonNull), Goal.read(trip).firstChanges(GoalWay.Counting, null))
    }

    @Test
    fun switchingWaysKeepsHowFarAlongAGoalIs() {
        val apples = goal("""{"target": 5, "current": 2, "unit": "apples"}""")
        assertEquals(mapOf("unit" to JsonPrimitive("USD")), apples.switchTo(GoalWay.SavingUp, dollars, "apples"))
        val owed = apples.switchTo(GoalWay.PayingOff, dollars, "apples")
        assertEquals(
            mapOf("unit" to JsonPrimitive("USD"), "start" to JsonPrimitive(BigDecimal(5)), "target" to JsonPrimitive(BigDecimal.ZERO), "current" to JsonPrimitive(BigDecimal(3))),
            owed,
        )
        assertEquals(apples.share, goal("""{"start": 5, "target": 0, "current": 3, "unit": "USD"}""").share, 1e-9)

        val card = goal("""{"start": 4800, "current": 3100, "target": 0, "unit": "USD"}""")
        val counted = card.switchTo(GoalWay.Counting, dollars, "payments")
        assertEquals(
            mapOf("unit" to JsonPrimitive("payments"), "start" to JsonNull, "target" to JsonPrimitive(BigDecimal(4800)), "current" to JsonPrimitive(BigDecimal(1700))),
            counted,
        )
        assertEquals(mapOf("unit" to JsonNull), goal("""{"target": 3000, "unit": "USD"}""").switchTo(GoalWay.Counting, dollars, null))
        assertEquals(emptyMap<String, Any>(), card.switchTo(GoalWay.PayingOff, dollars, null))
    }

    @Test
    fun aGoalWithoutATargetOnlyTakesItsUnitWhenSwitching() {
        assertEquals(mapOf("unit" to JsonPrimitive("USD")), goal("{}").switchTo(GoalWay.PayingOff, dollars, null))
        assertEquals(emptyMap<String, Any>(), goal("{}").switchTo(GoalWay.Counting, dollars, null))
    }

    @Test
    fun stepsNeverGoBelowWhereTheCountStarted() {
        assertTrue(goal("""{"target": 5, "current": 2}""").canStepDown)
        assertFalse(goal("""{"target": 5, "current": 0}""").canStepDown)
        assertFalse(goal("""{"target": 5, "current": 0.5}""").canStepDown)
        assertFalse(goal("""{"target": 5}""").canStepDown)
        assertTrue(goal("""{"start": 10, "target": 20, "current": 11}""").canStepDown)
        assertEquals(BigDecimal(3), goal("""{"target": 5, "current": 2}""").plus(BigDecimal.ONE).current)
        assertEquals(BigDecimal.ONE, goal("""{"target": 5}""").plus(BigDecimal.ONE).current)
    }

    @Test
    fun amountsAddTakeOffAndPay() {
        val trip = goal("""{"target": 3000, "current": 1250, "unit": "USD"}""")
        assertEquals(BigDecimal("1300.5"), trip.plus(trip.signed(GoalChange.Add, BigDecimal("50.5"))).current)
        assertEquals(BigDecimal(1000), trip.plus(trip.signed(GoalChange.TakeOff, BigDecimal(250))).current)
        val card = goal("""{"start": 4800, "current": 3100, "target": 0, "unit": "USD"}""")
        assertEquals(BigDecimal(2900), card.plus(card.signed(GoalChange.Paid, BigDecimal(200))).current)
        assertEquals(BigDecimal("0.3"), goal("""{"target": 1, "current": 0.1}""").plus(BigDecimal("0.2")).current)
    }

    @Test
    fun amountsThatDontFitAreRefusedInWords() {
        val trip = goal("""{"target": 3000, "current": 1250, "unit": "USD"}""")
        assertEquals("Write an amount above 0", trip.refuse(GoalChange.Add, BigDecimal.ZERO, words, Locale.US))
        assertNull(trip.refuse(GoalChange.Add, BigDecimal(5000), words, Locale.US))
        assertNull(trip.refuse(GoalChange.TakeOff, BigDecimal(1250), words, Locale.US))
        assertEquals("That's more than the $1,250 saved", trip.refuse(GoalChange.TakeOff, BigDecimal(1300), words, Locale.US))
        val card = goal("""{"start": 4800, "current": 3100, "target": 0, "unit": "USD"}""")
        assertNull(card.refuse(GoalChange.Paid, BigDecimal(3100), words, Locale.US))
        assertEquals("That's more than the $3,100 owed", card.refuse(GoalChange.Paid, BigDecimal("3100.01"), words, Locale.US))
    }

    @Test
    fun numbersAreKeptPlainForTheMap() {
        assertEquals("1250.5", number(BigDecimal("1250.50")).content)
        assertEquals("3000", number(BigDecimal("3E+3")).content)
        assertEquals("0.333333", number(BigDecimal("0.3333333333")).content)
        assertEquals("123456789012.346", number(BigDecimal("123456789012.3456789")).content)
        assertFalse(number(BigDecimal.TEN).isString)
    }

    @Test
    fun ideasThatStopBeingGoalsLoseTheirNumbers() {
        assertEquals(setOf("target", "current", "start", "unit"), Goal.removed.keys)
        assertTrue(Goal.removed.values.all { it == JsonNull })
    }

    @Test
    fun anEditThatTakesAGoalToItsTargetIsTold() {
        val before = node("goal", """{"target": 5, "current": 4}""")
        val after = node("goal", """{"target": 5, "current": 5}""")
        assertTrue(reachedGoal(mapOf(before.id to before), listOf(after)))
        assertFalse(reachedGoal(mapOf(after.id to after), listOf(after)))
        assertFalse(reachedGoal(emptyMap(), listOf(after)))
        assertFalse(reachedGoal(mapOf(before.id to before), listOf(before)))
    }
}
