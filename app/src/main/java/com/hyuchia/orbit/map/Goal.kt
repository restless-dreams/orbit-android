package com.hyuchia.orbit.map

import androidx.annotation.StringRes
import com.hyuchia.orbit.R
import com.hyuchia.orbit.Words
import com.hyuchia.orbit.page.MapNode
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode
import java.util.Currency
import java.util.Locale

/** The type of goal ideas. */
const val GOAL_TYPE = "goal"

/** The attributes a goal keeps its numbers in. */
const val GOAL_TARGET = "target"
const val GOAL_CURRENT = "current"
const val GOAL_START = "start"
const val GOAL_UNIT = "unit"
val GOAL_ATTRIBUTES = listOf(GOAL_TARGET, GOAL_CURRENT, GOAL_START, GOAL_UNIT)

/** Numbers this large or larger aren't read: the map page's numbers stop holding every whole unit not far above it. */
private val LARGEST = BigDecimal("1e15")

/** A number written as text, the way YAML would read it unquoted. */
private val PLAIN_NUMBER = Regex("""[+-]?(\d+(\.\d*)?|\.\d+)""")

/** How a goal counts toward its target, which names its numbers. */
enum class GoalWay(@StringRes val label: Int) {
    /** Up to a sum of money: "Target" and "Saved so far". */
    SavingUp(R.string.goal_way_saving_up),

    /** Down from what was owed: "Owed at the start" and "Owed now", with 0 as the target. */
    PayingOff(R.string.goal_way_paying_off),

    /** Up to a number of things: "How many" and "Have so far", with a word for what's counted. */
    Counting(R.string.goal_way_counting),
}

/** A change to a goal by an amount, as the long-press menu and the details offer them: its dialog's title and button. */
enum class GoalChange(@StringRes val title: Int, @StringRes val confirm: Int) {
    Add(R.string.goal_add_an_amount, R.string.goal_add),
    TakeOff(R.string.goal_take_off_an_amount, R.string.goal_take_off),
    Paid(R.string.goal_paid_an_amount, R.string.common_done),
}

/**
 * A goal idea's numbers (`type: goal`), from its attributes: the `target` it's
 * after, where it is (`current`, which is `start` until written), where it
 * started (`start`, 0 until written) and its `unit`: a currency code for
 * money, or a word for what's counted. Paying something off starts above its
 * target.
 */
data class Goal(
    /** Null when it isn't written, or isn't a number: the goal asks for one. */
    val target: BigDecimal?,

    val current: BigDecimal,
    val start: BigDecimal,
    val unit: String? = null,
) {
    /** The unit as a currency, when the goal counts money. */
    val currency: Currency? = currencyOf(unit)

    /** What's counted, when it isn't money. */
    val word: String? get() = unit.takeIf { currency == null }

    /** Down from where it started, as paying something off is. */
    val countsDown: Boolean get() = target != null && start > target

    /** How the goal counts, as its attributes say: down is paying off, money is saving up, anything else is counting. */
    val way: GoalWay
        get() = when {
            countsDown -> GoalWay.PayingOff
            currency != null -> GoalWay.SavingUp
            else -> GoalWay.Counting
        }

    /** It got where it was going; a target where it started is reached from the start. */
    val reached: Boolean
        get() {
            val target = target ?: return false

            return when {
                target.compareTo(start) == 0 -> true
                countsDown -> current <= target
                else -> current >= target
            }
        }

    /** How far along it is, from 0 to 1. Without a target, nothing is done. */
    val share: Double
        get() {
            val target = target ?: return 0.0

            if (reached) {
                return 1.0
            }

            return (current - start).divide(target - start, MathContext.DECIMAL64).toDouble().coerceIn(0.0, 1.0)
        }

    /** How much is done since the start: saved, paid off or counted. */
    val done: BigDecimal get() = if (countsDown) start - current else current - start

    /** What's still to go, never below 0; null without a target. */
    val left: BigDecimal? get() = target?.let { target -> (if (countsDown) current - target else target - current).max(BigDecimal.ZERO) }

    /** The two numbers its way of counting asks for, as written: what it's after and how far it's got. Null where not written. */
    fun fields(way: GoalWay, attributes: JsonObject?): Pair<BigDecimal?, BigDecimal?> {
        val written = { key: String -> readNumber(attributes?.get(key)) }
        val first = if (way == GoalWay.PayingOff) written(GOAL_START) else written(GOAL_TARGET)
        return first to written(GOAL_CURRENT)
    }

    /**
     * The attributes to set for the first number of a way of counting:
     * the target, or for paying off, what was owed at the start, with 0 as
     * the target once there's none.
     */
    fun firstChanges(way: GoalWay, value: BigDecimal?): Map<String, JsonElement> = when (way) {
        GoalWay.PayingOff -> buildMap {
            put(GOAL_START, numberOrNull(value))

            if (target == null && value != null) {
                put(GOAL_TARGET, number(BigDecimal.ZERO))
            } else if (value == null && target != null) {
                // Nothing owed at the start leaves no target either: the goal asks for one, or follows the ideas under it.
                put(GOAL_TARGET, JsonNull)
            }
        }
        GoalWay.SavingUp, GoalWay.Counting -> mapOf(GOAL_TARGET to numberOrNull(value))
    }

    /**
     * The attributes that make this goal count another way, keeping how far
     * along it is: the money for saving up and paying off (`currency`, unless
     * it counts money already), and the word for counting. Between up and
     * down the numbers turn around: $1,250 saved of $3,000 is $1,750 owed of
     * $3,000.
     */
    fun switchTo(way: GoalWay, currency: Currency, word: String?): Map<String, JsonElement> {
        val changes = mutableMapOf<String, JsonElement>()

        if (way == GoalWay.Counting) {
            if (this.currency != null) {
                changes[GOAL_UNIT] = word?.takeIf { it.isNotBlank() }?.let(::JsonPrimitive) ?: JsonNull
            }
        } else if (this.currency == null) {
            changes[GOAL_UNIT] = JsonPrimitive(currency.currencyCode)
        }

        val target = target ?: return changes
        val span = (target - start).abs()
        val down = way == GoalWay.PayingOff

        if (down != countsDown && span.signum() != 0) {
            if (down) {
                changes[GOAL_START] = number(span)
                changes[GOAL_TARGET] = number(BigDecimal.ZERO)
                changes[GOAL_CURRENT] = number(span - done)
            } else {
                changes[GOAL_START] = JsonNull
                changes[GOAL_TARGET] = number(span)
                changes[GOAL_CURRENT] = number(done)
            }
        }

        return changes
    }

    /** Whether one can be taken off a count without going below where it started. */
    val canStepDown: Boolean get() = current - BigDecimal.ONE >= minOf(start, target ?: start)

    /** The amount a change adds to `current`: taking off and paying lower it. */
    fun signed(change: GoalChange, amount: BigDecimal): BigDecimal = if (change == GoalChange.Add) amount else amount.negate()

    /** The goal after `amount` is added to where it is (or taken off, below 0). */
    fun plus(amount: BigDecimal): Goal = copy(current = tidy(current + amount))

    /** Why an amount can't be added, taken off or paid, in words; null when it can. */
    fun refuse(change: GoalChange, amount: BigDecimal, words: Words, locale: Locale = Locale.getDefault()): String? = when {
        amount.signum() <= 0 -> words.string(R.string.goal_amount_above_zero)
        change == GoalChange.TakeOff && amount > done ->
            words.string(R.string.goal_more_than_saved, GoalNumbers.amount(done.max(BigDecimal.ZERO), currency, locale))
        change == GoalChange.Paid && amount > (left ?: current) ->
            words.string(R.string.goal_more_than_owed, GoalNumbers.amount((left ?: current).max(BigDecimal.ZERO), currency, locale))
        else -> null
    }

    companion object {
        /** The goal a node is, or null when it isn't one. */
        fun of(node: MapNode): Goal? = if (node.isGoal) read(node.attrs) else null

        /** A goal from its attributes: numbers that can't be read count as not written. */
        fun read(attributes: JsonObject?): Goal {
            val start = readNumber(attributes?.get(GOAL_START)) ?: BigDecimal.ZERO
            val unit = (attributes?.get(GOAL_UNIT) as? JsonPrimitive)?.takeIf { it.isString }?.content?.trim()?.ifEmpty { null }
            return Goal(
                target = readNumber(attributes?.get(GOAL_TARGET)),
                current = readNumber(attributes?.get(GOAL_CURRENT)) ?: start,
                start = start,
                unit = unit,
            )
        }

        /** The attributes a goal keeps, all removed: for an idea that stops being one. */
        val removed: Map<String, JsonElement> get() = GOAL_ATTRIBUTES.associateWith { JsonNull }
    }
}

/** A number in an attribute: a plain number, or text that is one. Anything else is no number. */
internal fun readNumber(element: JsonElement?): BigDecimal? {
    val primitive = element as? JsonPrimitive ?: return null

    if (primitive is JsonNull) {
        return null
    }

    val text = primitive.content.trim()

    if (primitive.isString && !PLAIN_NUMBER.matches(text)) {
        return null
    }

    val value = text.toBigDecimalOrNull() ?: return null
    return value.takeIf { it.abs() < LARGEST }
}

/** The currencies the phone knows, by code. */
private val CURRENCIES: Map<String, Currency> by lazy { Currency.getAvailableCurrencies().associateBy { it.currencyCode } }

/** The unit as a currency, when it's a currency code: `USD`, `eur`. */
internal fun currencyOf(unit: String?): Currency? {
    val code = unit?.trim()?.uppercase(Locale.ROOT)?.takeIf { it.length == 3 && it.all { char -> char in 'A'..'Z' } } ?: return null
    return CURRENCIES[code]
}

/** The phone's own currency, from its region; dollars where the region has none. */
fun phoneCurrency(locale: Locale = Locale.getDefault()): Currency =
    runCatching { Currency.getInstance(locale) }.getOrNull() ?: Currency.getInstance("USD")

/**
 * A number as the map keeps it: at most 6 decimals and 15 digits, which the
 * map page holds exactly and writes plainly (`1250.5`).
 */
internal fun tidy(value: BigDecimal): BigDecimal {
    var tidy = if (value.scale() > 6) value.setScale(6, RoundingMode.HALF_EVEN) else value

    if (tidy.precision() > 15) {
        tidy = tidy.round(MathContext(15, RoundingMode.HALF_EVEN))
    }

    tidy = tidy.stripTrailingZeros()
    return if (tidy.scale() < 0) tidy.setScale(0) else tidy
}

/** A number for the map's text: a plain JSON number, written as it is. */
internal fun number(value: BigDecimal): JsonPrimitive = JsonPrimitive(tidy(value))

private fun numberOrNull(value: BigDecimal?): JsonElement = value?.let(::number) ?: JsonNull
