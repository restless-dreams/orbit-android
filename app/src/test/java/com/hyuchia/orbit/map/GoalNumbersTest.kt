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
    fun aDecimalPointTwiceOrMarksThatGroupNothingAreNoNumber() {
        // Read as 12.3 or 123, a number typed another way would be saved as another number, without a word.
        for (locale in listOf(english, mexican)) {
            assertEquals("Write a number, like 12.5", problem("1.2.3", locale))
            assertEquals("Write a number, like 12.5", problem("1,2.5", locale))
            assertEquals("Write a number, like 12.5", problem("1,234,56", locale))
            assertEquals("Write a number, like 12.5", problem("1234,567.5", locale))
        }

        for (locale in listOf(german, spanish)) {
            assertEquals("Write a number, like 12,5", problem("1.2.3", locale))
            assertEquals("Write a number, like 12,5", problem("1,2,3", locale))
            assertEquals("Write a number, like 12,5", problem("1.2,5", locale))
            assertEquals("Write a number, like 12,5", problem("1,250,000", locale))
        }

        // Grouped as people group them, in threes or as in India, they read as ever.
        assertEquals(BigDecimal(1234567), read("12,34,567", Locale.forLanguageTag("en-IN")))
        assertEquals(BigDecimal("1234567.5"), read("1,234,567.5", english))
        assertEquals(BigDecimal(1234567), read("1.234.567", german))
    }

    @Test
    fun spacesAndApostrophesGroupDigitsAsOtherMarksDo() {
        // As the languages that group with them write numbers, whatever space or apostrophe the keyboard types.
        assertEquals(BigDecimal(1234), read("1 234", english))
        assertEquals(BigDecimal(12345678), read("12 345 678", english))
        assertEquals(BigDecimal(1234), read("1'234", english))
        assertEquals(BigDecimal("1234.5"), read("1’234.5", Locale.forLanguageTag("de-CH")))
        assertEquals(BigDecimal("1234567.5"), read("1 234  567,5", Locale.FRANCE))
        assertEquals(BigDecimal("1234567.5"), read("1\u202F234\u00A0567,5", Locale.FRANCE))
        assertEquals(BigDecimal(-1234), read(" - 1 234 ", english))
        // In twos before the last three, as India groups them with commas.
        assertEquals(BigDecimal(1234567), read("12 34 567", english))

        // Marks that group nothing make no number, rather than another one: 1 2 isn't 12, nor is 1 2.5 12.5.
        for (typed in listOf("1 2", "1'2", "1’2", "1 2.5", "12 5", "1 23", "1 2345", "1 234'567", "1,234 567", "1 2 345")) {
            assertEquals(typed, "Write a number, like 12.5", problem(typed, english))
        }

        assertEquals("Write a number, like 12,5", problem("1 2,5", german))
    }

    @Test
    fun numbersAreKeptToSixDecimals() {
        assertEquals(BigDecimal("0.333333"), read("0.33333333", english))
        assertEquals(BigDecimal(5), read("5.000", english))
    }

    @Test
    fun aNumberThatRoundsToTooBigIsTooBig() {
        // Kept to 15 digits, it would be 1e15, which no reader of the map takes: its goal would lose the number.
        assertEquals("That's too big a number", problem("999999999999999.5", english))
        assertEquals("That's too big a number", problem("-999999999999999.9", english))
        assertEquals(BigDecimal("999999999999999"), read("999999999999999.4", english))
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
    fun aGoalsNumbersInBrazilianPortugueseAgreeWithWhatsToGo() {
        val brazilian = Locale.forLanguageTag("pt-BR")
        val portugueseWords = FileWords(brazilian)
        val apples = Goal(target = BigDecimal(5), current = BigDecimal(2), start = BigDecimal.ZERO, unit = "maçãs")
        assertEquals("2 de 5 maçãs · faltam 3", GoalNumbers.summary(apples, portugueseWords, brazilian))
        assertEquals("4 de 5 maçãs · falta 1", GoalNumbers.summary(apples.copy(current = BigDecimal(4)), portugueseWords, brazilian))
        // A fraction to go reads as more than one, after Brazil's decimal comma.
        assertEquals("3,5 de 5 km · faltam 1,5", GoalNumbers.summary(apples.copy(current = BigDecimal("3.5"), unit = "km"), portugueseWords, brazilian))
        assertEquals("5 de 5 maçãs · Meta alcançada", GoalNumbers.summary(apples.copy(current = BigDecimal(5)), portugueseWords, brazilian))
        val card = Goal(target = BigDecimal.ZERO, current = BigDecimal.ZERO, start = BigDecimal(4800), unit = "BRL")
        assertEquals("R$ 0 a pagar de R$ 4.800 · Tudo pago", plain(GoalNumbers.summary(card, portugueseWords, brazilian)))
        assertEquals("Ainda sem objetivo", GoalNumbers.summary(Goal(null, BigDecimal.ZERO, BigDecimal.ZERO), portugueseWords, brazilian))
        assertEquals("Digite um número, como 12,5", (GoalNumbers.read("cinco", brazilian) as NumberReading.Problem).message(portugueseWords))
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
