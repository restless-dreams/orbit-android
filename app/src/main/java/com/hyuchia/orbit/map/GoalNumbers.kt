package com.hyuchia.orbit.map

import android.icu.number.Notation
import android.icu.number.NumberFormatter
import android.os.Build
import androidx.annotation.RequiresApi
import com.hyuchia.orbit.R
import com.hyuchia.orbit.Words
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.DecimalFormatSymbols
import java.text.NumberFormat
import java.util.Currency
import java.util.Locale
import kotlin.math.roundToInt

private val NOT_A_DIGIT = Regex("[^0-9]")

/** What a number field holds: nothing, a number, or why it isn't one. */
sealed interface NumberReading {
    data object Empty : NumberReading
    data class Number(val value: BigDecimal) : NumberReading

    /** Why it isn't a number. */
    sealed interface Problem : NumberReading {
        /** The problem in words, to show under the field. */
        fun message(words: Words): String
    }

    /** Not a number at all: `example` is one, written the language's way. */
    data class NotANumber(val example: String) : Problem {
        override fun message(words: Words): String = words.string(R.string.goal_write_a_number, example)
    }

    /** A number too big for a goal to keep. */
    data object TooBig : Problem {
        override fun message(words: Words): String = words.string(R.string.goal_number_too_big)
    }
}

/** Shortens a large number the way the phone's language does (1.3K, 12K, 1.2M), as money in its currency; null where it can't. */
fun interface Shortener {
    fun shorten(value: BigDecimal, currency: Currency?, locale: Locale): String?
}

/** Numbers as people type and read them in their language: 1,250.5 in English, 1.250,5 in German. */
object GoalNumbers {
    /**
     * Reads a number as typed: with the language's decimal point and its
     * thousands grouped or not. With no decimal point of the language, a
     * lone `.` or `,` that doesn't group three digits is taken for one, as
     * keyboards that only offer the other one type it: 12.5 on a German phone.
     */
    fun read(text: String, locale: Locale = Locale.getDefault()): NumberReading {
        val symbols = DecimalFormatSymbols.getInstance(locale)
        // Spaces and apostrophes group digits in some languages, and mean nothing else.
        var rest = text.filterNot { it.isWhitespace() || Character.isSpaceChar(it) || it == '\'' || it == '’' }

        if (rest.isEmpty()) {
            return NumberReading.Empty
        }

        val negative = rest.first() == '-' || rest.first() == '−'

        if (negative || rest.first() == '+') {
            rest = rest.drop(1)
        }

        // Digits in any script count as their value.
        val digits = rest.map { char -> Character.digit(char, 10).takeIf { it >= 0 }?.let { '0' + it } ?: char }.joinToString("")
        val plain = plainNumber(digits, symbols.decimalSeparator, symbols.groupingSeparator)
            ?: return NumberReading.NotANumber(field(BigDecimal("12.5"), locale))
        val value = BigDecimal(plain).let { if (negative) it.negate() else it }

        if (value.abs() >= BigDecimal("1e15")) {
            return NumberReading.TooBig
        }

        return NumberReading.Number(tidy(value))
    }

    /** A number's digits with `.` as its decimal point, or null when the text isn't a number. */
    private fun plainNumber(text: String, decimal: Char, grouping: Char): String? {
        val marks = setOf(decimal, grouping, '.', ',')

        if (text.none { it in '0'..'9' } || text.any { it !in '0'..'9' && it !in marks }) {
            return null
        }

        val point = text.lastIndexOf(decimal)

        if (point >= 0) {
            val fraction = text.substring(point + 1)

            if (fraction.any { it !in '0'..'9' }) {
                return null
            }

            val whole = ungrouped(text.substring(0, point)) ?: return null
            return "${whole.ifEmpty { "0" }}.${fraction.ifEmpty { "0" }}"
        }

        val mark = text.indexOfFirst { it !in '0'..'9' }

        if (mark >= 0 && text.count { it !in '0'..'9' } == 1 && text.length - mark - 1 != 3) {
            return "${text.substring(0, mark).ifEmpty { "0" }}.${text.substring(mark + 1).ifEmpty { "0" }}"
        }

        return ungrouped(text)
    }

    /** Digits grouped by marks (1,250,000, or 12,34,567 as in India), as plain digits; null when a mark has no digits on one side. */
    private fun ungrouped(text: String): String? {
        if (text.isEmpty()) {
            return ""
        }

        val groups = text.split(NOT_A_DIGIT)
        return if (groups.any { it.isEmpty() }) null else groups.joinToString("")
    }

    /** A number for a text field: the language's decimal point and no grouping, so it's easy to change. */
    fun field(value: BigDecimal, locale: Locale = Locale.getDefault()): String {
        val format = NumberFormat.getNumberInstance(locale)
        format.isGroupingUsed = false
        format.minimumFractionDigits = 0
        format.maximumFractionDigits = 6
        return format.format(value)
    }

    /** A number as the phone's language writes it, as money in its currency: $1,250, 1.250,50 €, 2.5. */
    fun amount(value: BigDecimal, currency: Currency?, locale: Locale = Locale.getDefault()): String {
        val whole = value.signum() == 0 || value.stripTrailingZeros().scale() <= 0

        val format = if (currency != null) {
            NumberFormat.getCurrencyInstance(locale).also { it.currency = currency }
        } else {
            NumberFormat.getNumberInstance(locale)
        }

        val places = currency?.defaultFractionDigits?.coerceAtLeast(0) ?: 6
        format.maximumFractionDigits = if (whole) 0 else places
        format.minimumFractionDigits = if (whole || currency == null) 0 else places
        format.roundingMode = RoundingMode.HALF_EVEN
        return format.format(value)
    }

    /** How far along a goal is, as a percentage: never 100% until it's reached. */
    fun percent(goal: Goal, locale: Locale = Locale.getDefault()): String {
        val whole = if (goal.reached) 100 else (goal.share * 100).roundToInt().coerceAtMost(99)
        return NumberFormat.getPercentInstance(locale).format(whole / 100.0)
    }

    /** The currency's symbol and which side of the number it goes on in the language: `$` before, `€` after. */
    fun currencySide(currency: Currency, locale: Locale = Locale.getDefault()): Pair<String, Boolean> {
        val symbol = currency.getSymbol(locale)
        val sample = NumberFormat.getCurrencyInstance(locale).also { it.currency = currency }.format(1)
        return symbol to (sample.indexOf(symbol).let { it < 0 || it <= sample.indexOfFirst(Char::isDigit) })
    }

    /**
     * The count an amount picks its plural by: the amount when it's whole,
     * else one that reads like several, as fractions do in English and Spanish.
     */
    private fun countOf(value: BigDecimal): Int {
        val whole = value.stripTrailingZeros()
        return if (whole.scale() <= 0 && whole <= BigDecimal(Int.MAX_VALUE)) whole.toInt() else Int.MAX_VALUE
    }

    /**
     * A goal's numbers in words, for its details: "2 of 5 apples · 3 to go",
     * "$1,250 of $3,000 · 42% · $1,750 to go", "$3,100 left of $4,800 · 35% paid".
     * Without `toGo`, what's left to go isn't said.
     */
    fun summary(goal: Goal, words: Words, locale: Locale = Locale.getDefault(), toGo: Boolean = true): String {
        val target = goal.target ?: return words.string(R.string.goal_no_target_yet)
        val write = { value: BigDecimal -> amount(value, goal.currency, locale) }
        val left = goal.left ?: BigDecimal.ZERO

        return when (goal.way) {
            GoalWay.PayingOff -> if (goal.reached) {
                words.string(R.string.goal_summary_paid_off, write(goal.current), write(goal.start))
            } else {
                words.string(R.string.goal_summary_paying_off, write(goal.current), write(goal.start), percent(goal, locale))
            }
            GoalWay.SavingUp -> when {
                goal.reached -> words.string(R.string.goal_summary_reached, write(goal.current), write(target))
                toGo -> words.plural(R.plurals.goal_summary_saving_up_to_go, countOf(left), write(goal.current), write(target), percent(goal, locale), write(left))
                else -> words.string(R.string.goal_summary_saving_up, write(goal.current), write(target), percent(goal, locale))
            }
            GoalWay.Counting -> {
                val counted = goal.word?.let { word -> words.string(R.string.goal_amount_with_word, write(target), word) } ?: write(target)

                when {
                    goal.reached -> words.string(R.string.goal_summary_reached, write(goal.current), counted)
                    toGo -> words.plural(R.plurals.goal_summary_counting_to_go, countOf(left), write(goal.current), counted, write(left))
                    else -> words.string(R.string.goal_amount_of_target, write(goal.current), counted)
                }
            }
        }
    }

    /**
     * The goals and to-dos an idea follows, in words: "2 of 4 reached · 54%",
     * or "done" when there are to-dos among them. The share is rounded as the
     * map's rings round it.
     */
    fun progress(rollup: Rollup, words: Words, locale: Locale = Locale.getDefault()): String {
        val count = NumberFormat.getIntegerInstance(locale)
        val percent = NumberFormat.getPercentInstance(locale).format((rollup.share * 100).roundToInt() / 100.0)
        val phrase = if (rollup.allGoals) R.plurals.goal_progress_reached else R.plurals.goal_progress_done
        return words.plural(phrase, rollup.finished, count.format(rollup.finished), count.format(rollup.total), percent)
    }

    /**
     * A goal's numbers under its fields in the editor. Without a target, a
     * goal with ideas under it follows the goals and to-dos among them, and
     * says so: "Follows the goals and to-dos under it: 2 of 4 reached · 54%".
     */
    fun editorSummary(goal: Goal, hasIdeasUnder: Boolean, under: Rollup, words: Words, locale: Locale = Locale.getDefault()): String = when {
        goal.target != null || !hasIdeasUnder -> summary(goal, words, locale)
        under.total > 0 -> words.string(R.string.goal_follows_the_ideas_under_it, progress(under, words, locale))
        else -> words.string(R.string.goal_no_target_follows_the_ideas_under_it)
    }

    /** A goal's numbers briefly, for the outline: "2/5", "$1.3K of $3K", "$3.1K left". */
    fun brief(goal: Goal, words: Words, locale: Locale = Locale.getDefault(), shortener: Shortener = PhoneShortener): String {
        val target = goal.target ?: return words.string(R.string.goal_no_target_yet)
        val write = { value: BigDecimal -> shortener.shorten(value, goal.currency, locale) ?: amount(value, goal.currency, locale) }

        return when (goal.way) {
            GoalWay.PayingOff -> words.string(R.string.goal_brief_left, write(goal.current))
            GoalWay.SavingUp -> words.string(R.string.goal_amount_of_target, write(goal.current), write(target))
            GoalWay.Counting -> words.string(R.string.goal_brief_counting, write(goal.current), write(target))
        }
    }
}

/** Android's own short numbers, from Android 11; earlier ones write numbers in full. */
object PhoneShortener : Shortener {
    override fun shorten(value: BigDecimal, currency: Currency?, locale: Locale): String? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) shortened(value, currency, locale) else null

    @RequiresApi(Build.VERSION_CODES.R)
    private fun shortened(value: BigDecimal, currency: Currency?, locale: Locale): String? = runCatching {
        // Halves round up, as the map's rings round them: 1,250 is 1.3K in both.
        var formatter = NumberFormatter.withLocale(locale).notation(Notation.compactShort()).roundingMode(RoundingMode.HALF_UP)

        if (currency != null) {
            formatter = formatter.unit(android.icu.util.Currency.getInstance(currency.currencyCode))
        }

        formatter.format(value).toString()
    }.getOrNull()
}
