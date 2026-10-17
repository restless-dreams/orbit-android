package com.hyuchia.orbit.map

import com.hyuchia.orbit.FileWords
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.util.Currency
import java.util.Locale

class GoalNumbersTest {
    private val english = Locale.US
    private val mexican = Locale.forLanguageTag("es-MX")
    private val spanish = Locale.forLanguageTag("es-ES")
    private val german = Locale.GERMANY
    private val dollars = Currency.getInstance("USD")
    private val pesos = Currency.getInstance("MXN")
    private val euros = Currency.getInstance("EUR")
    private val words = FileWords(english)

    private fun read(text: String, locale: Locale): BigDecimal? = (GoalNumbers.read(text, locale) as? NumberReading.Number)?.value

    /** Why the text isn't a number, as the app says it in English. */
    private fun problem(text: String, locale: Locale): String? = (GoalNumbers.read(text, locale) as? NumberReading.Problem)?.message(words)

    /** Spaces as the formats write them, which may be non-breaking, as plain ones. */
    private fun plain(text: String): String = text.replace(' ', ' ').replace(' ', ' ')

    @Test
    fun englishAndMexicanSpanishReadADecimalPoint() {
        for (locale in listOf(english, mexican)) {
            assertEquals(BigDecimal("1250.5"), read("1,250.5", locale))
            assertEquals(BigDecimal("1250.5"), read("1250.5", locale))
            assertEquals(BigDecimal(1250), read("1,250", locale))
            assertEquals(BigDecimal(1250000), read("1,250,000", locale))
            assertEquals(BigDecimal("0.5"), read(".5", locale))
            assertEquals(BigDecimal(-12), read("-12", locale))
            // A comma that doesn't group three digits is a decimal point typed another way.
            assertEquals(BigDecimal("12.5"), read("12,5", locale))
        }
    }

    @Test
    fun germanAndSpainsSpanishReadADecimalComma() {
        for (locale in listOf(german, spanish)) {
            assertEquals(BigDecimal("1250.5"), read("1.250,5", locale))
            assertEquals(BigDecimal("1250.5"), read("1250,5", locale))
            assertEquals(BigDecimal(1250), read("1.250", locale))
            assertEquals(BigDecimal("12.5"), read("12,50", locale))
            assertEquals(BigDecimal("1.5"), read("1.5", locale))
            assertEquals(BigDecimal(1250), read("1 250", locale))
        }
    }

    @Test
    fun whatIsntANumberIsSaidInTheLanguagesWay() {
        assertEquals("Write a number, like 12.5", problem("five", english))
        assertEquals("Write a number, like 12,5", problem("fünf", german))
        assertEquals("Write a number, like 12,5", problem("1e3", spanish))
        assertEquals("Write a number, like 12.5", problem("$12", mexican))
        assertEquals("Write a number, like 12.5", problem("1,,250", english))
        assertEquals("Write a number, like 12.5", problem(",", english))
        assertEquals("That's too big a number", problem("1000000000000000", english))
        assertEquals(NumberReading.Empty, GoalNumbers.read("  ", english))
    }

    @Test
    fun numbersAreKeptToSixDecimals() {
        assertEquals(BigDecimal("0.333333"), read("0.33333333", english))
        assertEquals(BigDecimal(5), read("5.000", english))
    }

    @Test
    fun fieldsShowNumbersPlainlyInTheLanguagesWay() {
        assertEquals("1250.5", GoalNumbers.field(BigDecimal("1250.5"), english))
        assertEquals("1250,5", GoalNumbers.field(BigDecimal("1250.5"), german))
        assertEquals("1250,5", GoalNumbers.field(BigDecimal("1250.5"), spanish))
        assertEquals("3000", GoalNumbers.field(BigDecimal(3000), mexican))

        for (locale in listOf(english, mexican, spanish, german)) {
            for (value in listOf("0.25", "1250.5", "3000", "123456.789")) {
                assertEquals(BigDecimal(value), read(GoalNumbers.field(BigDecimal(value), locale), locale))
            }
        }
    }

    @Test
    fun amountsAreWrittenAsMoneyInTheirCurrency() {
        assertEquals("$1,250", GoalNumbers.amount(BigDecimal(1250), dollars, english))
        assertEquals("$1,250.50", GoalNumbers.amount(BigDecimal("1250.5"), dollars, english))
        assertEquals("$1,250", GoalNumbers.amount(BigDecimal(1250), pesos, mexican))
        assertEquals("12.345 €", plain(GoalNumbers.amount(BigDecimal(12345), euros, german)))
        assertEquals("12.345,50 €", plain(GoalNumbers.amount(BigDecimal("12345.5"), euros, spanish)))
        assertEquals("¥1,250", GoalNumbers.amount(BigDecimal("1250.4"), Currency.getInstance("JPY"), Locale.JAPAN).replace('￥', '¥'))
    }

    @Test
    fun otherNumbersAreWrittenWithoutCurrency() {
        assertEquals("1,250.5", GoalNumbers.amount(BigDecimal("1250.5"), null, english))
        assertEquals("1.250,5", GoalNumbers.amount(BigDecimal("1250.5"), null, german))
        assertEquals("2", GoalNumbers.amount(BigDecimal(2), null, spanish))
        assertEquals("0,25", GoalNumbers.amount(BigDecimal("0.25"), null, spanish))
    }

    @Test
    fun theCurrencySymbolGoesWhereTheLanguagePutsIt() {
        assertEquals("$" to true, GoalNumbers.currencySide(dollars, english))
        assertEquals("€" to false, GoalNumbers.currencySide(euros, german))
        assertEquals("$" to true, GoalNumbers.currencySide(pesos, mexican))
    }

    @Test
    fun aGoalsNumbersInWords() {
        val apples = Goal(target = BigDecimal(5), current = BigDecimal(2), start = BigDecimal.ZERO, unit = "apples")
        assertEquals("2 of 5 apples · 3 to go", GoalNumbers.summary(apples, words, english))
        val trip = Goal(target = BigDecimal(3000), current = BigDecimal(1250), start = BigDecimal.ZERO, unit = "USD")
        assertEquals("$1,250 of $3,000 · 42% · $1,750 to go", GoalNumbers.summary(trip, words, english))
        val card = Goal(target = BigDecimal.ZERO, current = BigDecimal(3100), start = BigDecimal(4800), unit = "USD")
        assertEquals("$3,100 left of $4,800 · 35% paid", GoalNumbers.summary(card, words, english))
        assertEquals("12 of 12 · Reached", GoalNumbers.summary(Goal(BigDecimal(12), BigDecimal(12), BigDecimal.ZERO), words, english))
        assertEquals("$0 left of $4,800 · Paid off", GoalNumbers.summary(card.copy(current = BigDecimal.ZERO), words, english))
        assertEquals("No target yet", GoalNumbers.summary(Goal(null, BigDecimal.ZERO, BigDecimal.ZERO), words, english))
        assertEquals("1.250 € of 3.000 € · 42 % · 1.750 € to go", plain(GoalNumbers.summary(trip.copy(unit = "EUR"), words, german)))
        val pesos = GoalNumbers.summary(trip.copy(unit = "MXN"), words, mexican)
        assertTrue(pesos.startsWith("$1,250 of $3,000 · 42") && pesos.endsWith("% · $1,750 to go"))
        // Without what's to go, as the amount dialog says what a goal comes to.
        assertEquals("$1,250 of $3,000 · 42%", GoalNumbers.summary(trip, words, english, toGo = false))
        assertEquals("2 of 5 apples", GoalNumbers.summary(apples, words, english, toGo = false))
    }

    @Test
    fun aGoalsNumbersInSpanishAgreeWithWhatsToGo() {
        val spanishWords = FileWords(mexican)
        val apples = Goal(target = BigDecimal(5), current = BigDecimal(2), start = BigDecimal.ZERO, unit = "manzanas")
        assertEquals("2 de 5 manzanas · faltan 3", GoalNumbers.summary(apples, spanishWords, mexican))
        assertEquals("4 de 5 manzanas · falta 1", GoalNumbers.summary(apples.copy(current = BigDecimal(4)), spanishWords, mexican))
        // A fraction to go reads as more than one.
        assertEquals("3.5 de 5 km · faltan 1.5", GoalNumbers.summary(apples.copy(current = BigDecimal("3.5"), unit = "km"), spanishWords, mexican))
        assertEquals("5 de 5 manzanas · Meta alcanzada", GoalNumbers.summary(apples.copy(current = BigDecimal(5)), spanishWords, mexican))
        val trip = Goal(target = BigDecimal(3000), current = BigDecimal(2999), start = BigDecimal.ZERO, unit = "MXN")
        val oneToGo = GoalNumbers.summary(trip, spanishWords, mexican)
        assertTrue(oneToGo, oneToGo.startsWith("$2,999 de $3,000 · 99") && oneToGo.endsWith(" · falta $1"))
        val card = Goal(target = BigDecimal.ZERO, current = BigDecimal.ZERO, start = BigDecimal(4800), unit = "MXN")
        assertEquals("$0 por pagar de $4,800 · Todo pagado", GoalNumbers.summary(card, spanishWords, mexican))
        assertEquals("Aún sin objetivo", GoalNumbers.summary(Goal(null, BigDecimal.ZERO, BigDecimal.ZERO), spanishWords, mexican))
        assertEquals("Escribe un número, como 12.5", (GoalNumbers.read("cinco", mexican) as NumberReading.Problem).message(spanishWords))
    }

    @Test
    fun aPercentageOnlyReaches100WhenTheGoalDoes() {
        val almost = Goal(target = BigDecimal(1000), current = BigDecimal(999), start = BigDecimal.ZERO)
        assertEquals("99%", GoalNumbers.percent(almost, english))
        assertEquals("100%", GoalNumbers.percent(almost.copy(current = BigDecimal(1000)), english))
        assertEquals("0%", GoalNumbers.percent(almost.copy(current = BigDecimal.ZERO), english))
    }

    @Test
    fun theOutlineShowsTheNumbersBriefly() {
        // A short form like Android's own: thousands as K, kept to two digits.
        val shortener = Shortener { value, currency, _ ->
            val short = if (value >= BigDecimal(1000)) "${value.divide(BigDecimal(1000)).setScale(1, java.math.RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()}K" else value.toPlainString()
            if (currency != null) "$$short" else short
        }

        val apples = Goal(target = BigDecimal(5), current = BigDecimal(2), start = BigDecimal.ZERO, unit = "apples")
        assertEquals("2/5", GoalNumbers.brief(apples, words, english, shortener))
        val trip = Goal(target = BigDecimal(3000), current = BigDecimal(1250), start = BigDecimal.ZERO, unit = "USD")
        assertEquals("$1.3K of $3K", GoalNumbers.brief(trip, words, english, shortener))
        val card = Goal(target = BigDecimal.ZERO, current = BigDecimal(3100), start = BigDecimal(4800), unit = "USD")
        assertEquals("$3.1K left", GoalNumbers.brief(card, words, english, shortener))
        // Where the phone can't shorten numbers, they're written in full.
        val none = Shortener { _, _, _ -> null }
        assertEquals("$1,250 of $3,000", GoalNumbers.brief(trip, words, english, none))
        assertTrue(GoalNumbers.brief(Goal(null, BigDecimal.ZERO, BigDecimal.ZERO), words, english, none).isNotEmpty())
        assertEquals("$3.1K por pagar", GoalNumbers.brief(card, FileWords(mexican), mexican, shortener))
    }
}
