package com.hyuchia.orbit.ui

import java.text.Normalizer
import java.util.Locale

/**
 * The emoji the icon sheet's search finds, and the words it finds them by: each emoji's name and
 * keywords in the app's language and in English, from `assets/emoji/` (written by
 * `scripts/emoji-names.ts` from Unicode's data).
 */
class EmojiIndex internal constructor(private val entries: List<EmojiEntry>, private val tones: Map<String, String>) {
    val size: Int get() = entries.size

    /**
     * The emoji whose name or keywords have a word starting with each word of `query`, ignoring
     * case and accents. Names come first: the whole name, then its whole words, then words it
     * starts; then keywords. Within each, the emoji people use most come first, as Unicode ranks
     * them, then the rest in Unicode's order. Words in English come after the app's language's.
     */
    fun search(query: String): List<EmojiMatch> {
        val typed = query.trim()

        if (typed.isEmpty()) {
            return emptyList()
        }

        val search = Search(typed)

        return entries
            .mapNotNull { entry -> entry.score(search)?.let { score -> entry to score } }
            .sortedWith(compareBy({ it.second }, { it.first.rank }, { it.first.order }))
            .map { (entry, _) -> entry.match(search, tones) }
    }

    companion object {
        /**
         * Reads the files' text: `emoji` lists every emoji with its rank and skin tones, `words` has
         * their names and keywords in the app's language and `english` in English, when the app's
         * language isn't English. `drawable` leaves out the emoji the phone can't draw.
         */
        fun read(emoji: String, words: String, english: String?, drawable: (String) -> Boolean): EmojiIndex {
            val named = readWords(words)
            val fallback = english?.let(::readWords).orEmpty()
            val entries = mutableListOf<EmojiEntry>()

            emoji.lineSequence().filter(::isData).forEach { line ->
                val parts = line.split('\t')
                val character = parts[0]
                val own = named[character]
                val inEnglish = fallback[character]

                if ((own != null || inEnglish != null) && drawable(character)) {
                    val rank = parts.getOrNull(1)?.toIntOrNull() ?: Int.MAX_VALUE
                    val variants = parts.getOrNull(2)?.split(' ')?.filter { it.isNotEmpty() && drawable(it) }.orEmpty()
                    entries.add(EmojiEntry(character, variants, rank, entries.size, own, inEnglish))
                }
            }

            val tones = SkinTones.mapNotNull { tone -> (named[tone] ?: fallback[tone])?.let { tone to it.name } }.toMap()
            return EmojiIndex(entries, tones)
        }
    }
}

/** An emoji the search found. */
class EmojiMatch internal constructor(
    /** The emoji, without a skin tone. */
    val emoji: String,

    /** Its name, in the app's language when it has one. */
    val name: String,

    /** Its skin tones that the phone can draw. */
    val variants: List<String>,

    /** The skin tone pasted into the search, when one was. */
    val pasted: String?,

    private val tones: Map<String, String>,
) {
    /** The name of one of its skin tones: its name, then the name of each tone in it, as Unicode names them. */
    fun nameOf(variant: String): String {
        // An emoji with two people can have a tone for each, and says both when they're different.
        val names = variant.codePoints().toArray()
            .map { String(Character.toChars(it)) }
            .filter { it in tones }
            .distinct()
            .map { tones.getValue(it) }

        return if (names.isEmpty()) name else "$name: ${names.joinToString(", ")}"
    }
}

/** Unicode's five skin tones, which go after an emoji (or each person in it) to change its tone. */
internal val SkinTones = listOf("🏻", "🏼", "🏽", "🏾", "🏿")

private const val VARIATION_SELECTOR = "️"

/** Words in English come after every match in the app's language. */
private const val ENGLISH_AFTER = 10

private val Marks = Regex("""\p{Mn}+""")
private val Separators = Regex("""[^\p{L}\p{N}]+""")

/** Text as the search compares it: lowercase, without accents, and its words apart by single spaces. */
internal fun normalize(text: String): String = Normalizer.normalize(text.lowercase(Locale.ROOT), Normalizer.Form.NFD)
    .replace(Marks, "")
    .replace(Separators, " ")
    .trim()

/** A line with an emoji, not the files' notice ("# From Unicode's…"), which a keycap's "#️⃣" isn't. */
private fun isData(line: String): Boolean = line.isNotBlank() && !line.startsWith("# ")

/** A language's file: a line per emoji, with its name and its keywords apart with "|", after tabs. */
private fun readWords(text: String): Map<String, EmojiWords> = text.lineSequence().filter(::isData).mapNotNull { line ->
    val parts = line.split('\t')
    val name = parts.getOrNull(1)?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
    parts[0] to EmojiWords(name, parts.getOrNull(2)?.split('|')?.filter { it.isNotBlank() }.orEmpty())
}.toMap()

/** What was typed, ready to compare: as it was, normalized, without spaces, and word by word. */
internal class Search(val typed: String) {
    val text = normalize(typed)
    val compact = text.replace(" ", "")
    val words = text.split(' ').filter { it.isNotEmpty() }

    /** The typed text as an emoji, to find the emoji itself when one is pasted. */
    val emoji = typed.replace(VARIATION_SELECTOR, "")
}

/** One emoji's name and keywords in one language, normalized for the search. */
internal class EmojiWords(val name: String, private val rawKeywords: List<String>) {
    private val normalizedName = normalize(name)
    private val compactName = normalizedName.replace(" ", "")
    private val nameWords = normalizedName.split(' ').filter { it.isNotEmpty() }
    private val keywords = rawKeywords.map(::normalize).filter { it.isNotEmpty() }
    private val keywordWords = keywords.flatMap { it.split(' ') }.distinct()

    /** How well these words match, lower being better, or null when they don't. */
    fun score(search: Search): Int? {
        if (search.text.isEmpty()) {
            // Only symbols, like "?" or "#": CLDR's keywords have some, and names like "keycap: #" have the rest.
            return when {
                rawKeywords.any { it == search.typed } -> 3
                name.contains(search.typed) -> 4
                else -> null
            }
        }

        return when {
            normalizedName == search.text -> 0
            search.words.all { word -> word in nameWords } -> 1
            compactName.startsWith(search.compact) || search.words.all { word -> nameWords.any { it.startsWith(word) } } -> 2
            keywords.any { it == search.text } || search.words.all { word -> word in nameWords || word in keywordWords } -> 3
            search.words.all { word -> nameWords.any { it.startsWith(word) } || keywordWords.any { it.startsWith(word) } } -> 4
            else -> null
        }
    }
}

/**
 * An emoji in the search: its skin tones, its place in Unicode's ranking of the emoji people use
 * most (`Int.MAX_VALUE` when it isn't ranked) and in Unicode's order, and its words in the app's
 * language and in English.
 */
internal class EmojiEntry(
    private val emoji: String,
    private val variants: List<String>,
    val rank: Int,
    val order: Int,
    private val words: EmojiWords?,
    private val english: EmojiWords?,
) {
    fun score(search: Search): Int? {
        if (pasted(search) != null || emoji.replace(VARIATION_SELECTOR, "") == search.emoji) {
            return -1
        }

        val own = words?.score(search)
        val inEnglish = english?.score(search)?.plus(ENGLISH_AFTER)
        return listOfNotNull(own, inEnglish).minOrNull()
    }

    fun match(search: Search, tones: Map<String, String>): EmojiMatch = EmojiMatch(emoji, (words ?: english)?.name.orEmpty(), variants, pasted(search), tones)

    private fun pasted(search: Search): String? = variants.firstOrNull { it.replace(VARIATION_SELECTOR, "") == search.emoji }
}
