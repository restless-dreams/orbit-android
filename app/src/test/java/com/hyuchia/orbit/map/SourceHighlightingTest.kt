package com.hyuchia.orbit.map

import com.hyuchia.orbit.data.MapText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * What the source editor colors on a line: its links, comments and YAML
 * keys, read in one pass, as the patterns it read them with found them.
 */
class SourceHighlightingTest {
    @Test(timeout = 5_000)
    fun `a line of thousands of brackets, comments or spaces is read in one pass`() {
        // Read again from each `[`, `<!--` or space, as the patterns did, 20,000 of these took seconds on each change.
        val brackets = "[".repeat(100_000)
        val links = "[](".repeat(30_000)
        val comments = "<!--".repeat(25_000)
        val spaces = " ".repeat(100_000)

        assertEquals(emptyList<IntRange>(), MapText.links(brackets).toList())
        assertEquals(emptyList<IntRange>(), MapText.links(links).toList())
        assertEquals(listOf(0..10), MapText.links("[Lisbon](a)$brackets").toList())
        // A line separator in the middle stops each `<!--` before it: none is a comment.
        assertEquals(emptyList<IntRange>(), sourceComments("$comments Lisbon").map { it.range }.toList())
        assertEquals(emptyList<IntRange>(), sourceComments("$comments\rLisbon").map { it.range }.toList())
        assertEquals(listOf(0 until comments.length), sourceComments(comments).map { it.range }.toList())
        assertNull(sourceYamlKey(spaces))
        assertNull(sourceYamlKey("$spaces-$spaces"))
        assertEquals(spaces.length + 2 until spaces.length + 6, sourceYamlKey("$spaces- text: Lisbon"))
    }

    @Test(timeout = 5_000)
    fun `a comment on a line of a million characters is read whole`() {
        // Android's patterns failed on these, past about 400,000 characters, with one that read to the `-->` in one pass.
        val words = "Lisbon ".repeat(150_000)
        val comments = "<!--".repeat(250_000)

        assertEquals(listOf(0 until words.length + 8), sourceComments("<!-- $words-->").map { it.range }.toList())
        assertEquals(listOf(0 until words.length + 5), sourceComments("<!-- $words").map { it.range }.toList())
        assertEquals(listOf(comments.length + 8 until comments.length + 18), sourceComments("$comments\rLisbon <!-- a -->").map { it.range }.toList())
    }

    @Test
    fun `the patterns comments are read with repeat no group`() {
        // Android's patterns keep a step on a stack of 8 MB each time a group repeats, so `<!--(?:(?!-->).)*+` failed on lines of about 400,000 characters. The JVM's don't: this reads the patterns themselves.
        for (pattern in listOf(COMMENT, UNTIL_SEPARATOR)) {
            assertFalse(pattern.pattern, Regex("""\)[*+{]""").containsMatchIn(pattern.pattern))
        }
    }

    @Test
    fun `comments and YAML keys read in one pass are the ones the patterns found`() {
        // The patterns the source editor read them with, which took seconds on lines of thousands of `<!--` or spaces.
        val comment = Regex("""<!--.*?(-->|$)""")
        val yamlKey = Regex("""^(\s*-?\s*)([A-Za-z][\w-]*):""")
        val pieces = listOf("<!--", "-->", "-", ">", "<", "!", "a", " ", "\t", "\r", "\n", "\r\n", " ", "\u0085", "\u000b", " ", ":", "_", "1")
        val random = java.util.Random(13)

        repeat(20_000) {
            val text = (0 until random.nextInt(14)).joinToString("") { pieces[random.nextInt(pieces.size)] }

            assertEquals(text, comment.findAll(text).map { it.range to it.value }.toList(), sourceComments(text).map { it.range to it.value }.toList())
            assertEquals(text, yamlKey.find(text)?.groups?.get(2)?.range, sourceYamlKey(text))
        }
    }
}
