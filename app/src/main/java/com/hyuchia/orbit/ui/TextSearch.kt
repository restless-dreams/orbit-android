package com.hyuchia.orbit.ui

/**
 * The text with its case folded letter by letter, to search it whatever the
 * case. Every letter stays one letter, so a match in the folded text is at
 * the same place in the text: `lowercase()` makes İ two letters, which
 * shifts every match after it. Folding through upper case also finds İ and
 * ı by i, and ς by σ.
 */
fun foldCase(text: String): String {
    val folded = StringBuilder(text.length)
    var index = 0

    while (index < text.length) {
        val letter = text.codePointAt(index)
        val fold = Character.toLowerCase(Character.toUpperCase(letter))
        folded.appendCodePoint(if (Character.charCount(fold) == Character.charCount(letter)) fold else letter)
        index += Character.charCount(letter)
    }

    return folded.toString()
}

/** The words of a search, case folded, to look for in [foldCase]d text. */
fun searchWords(query: String): List<String> = foldCase(query).split(Regex("""\s+""")).filter { it.isNotEmpty() }
