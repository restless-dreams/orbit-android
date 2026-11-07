package com.hyuchia.orbit.data

import com.hyuchia.orbit.FileWords
import com.hyuchia.orbit.page.ImportProblem
import com.hyuchia.orbit.page.ImportWarning
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

/**
 * A nested markdown list with two lines at the top, as a notes app or a
 * markdown file writes it. As written, Orbit draws it around an untitled
 * middle; read as an outline, its top line is the middle.
 */
private const val NESTED_LIST = "- Trip\n  - Flights\n    - Window seat\n  - Hotels\n- Packing\n  - Passport\n"

class ImporterTest {
    @Test
    fun orbitMarkdownIsKeptAsWritten() {
        val markdown = "# Trip\n\n## Food\n- Tacos\n"
        assertEquals(markdown, Importer.markdownFor(markdown))
        assertEquals("- Groceries\n  - Milk\n", Importer.markdownFor("- Groceries\n  - Milk"))
    }

    @Test
    fun plainLinesBecomeAListUnderTheFirst() {
        assertEquals("# Groceries\n\n- Milk\n- Eggs\n- Bread\n", Importer.markdownFor("Groceries\nMilk\n\n• Eggs\nBread\n"))
    }

    @Test
    fun aSingleLineIsAMapsTitle() {
        assertEquals("# Ideas for the weekend\n", Importer.markdownFor("  Ideas for the weekend  "))
    }

    @Test
    fun aLongLineIsKeptWholeAsTheNote() {
        val line = "Remember to ask the venue about the projector, the stage lights and whether the sound check can move to Friday morning"
        val markdown = Importer.markdownFor(line)
        assertEquals("# Remember to ask the venue about the projector, the stage lights and whether…\n\n$line\n", markdown)
    }

    @Test
    fun sharedTextWithASubjectIsNamedByIt() {
        assertEquals(
            "# A long read about maps\n\nhttps://example.com/articles/2026/10/maps-and-minds\n",
            Importer.markdownFor("https://example.com/articles/2026/10/maps-and-minds", "A long read about maps"),
        )
        assertEquals("# Groceries\n\n- Milk\n- Eggs\n", Importer.markdownFor("Milk\nEggs", "Groceries"))
    }

    @Test
    fun `text indented as an outline keeps its branches as a new map`() {
        assertTrue(Importer.isOutline("Trip\n  Flights\n    Window seat\n  Hotels"))
        assertTrue(Importer.isOutline("Trip\n\tFlights"))
        // Tabs count as four spaces among spaces, and blank lines don't count.
        assertTrue(Importer.isOutline("Trip\n\n\tFlights\n    Hotels"))
        assertTrue(Importer.isOutline("- Trip\n  - Flights"))
        assertTrue(Importer.isOutline("• Groceries\n    1. Milk\n    - [ ] Bread\n    * [x] Butter"))
    }

    @Test
    fun `a list without indents, or markdown with headings, makes the map it always has`() {
        assertFalse(Importer.isOutline("Milk\nEggs\nBread"))
        assertFalse(Importer.isOutline("- Milk\n- Eggs"))
        // Lines all indented alike are a list without indents.
        assertFalse(Importer.isOutline("  Milk\n  Eggs"))
        assertFalse(Importer.isOutline("# Trip\n  Flights"))
        assertFalse(Importer.isOutline("Notes\n## Trip\n  Flights"))
        assertFalse(Importer.isOutline("---\ntheme: night\n---\n  Trip"))
        assertFalse(Importer.isOutline("  \n"))
    }

    @Test
    fun `a nested markdown list in a markdown file is kept byte for byte`() = runTest {
        // Opened with Open a file or from a file manager, or dropped on the list: each goes through importFile.
        listOf("Trip.md" to "text/markdown", "Trip.markdown" to "", "Trip.md" to "text/plain", "Trip" to "text/x-markdown").forEach { (name, type) ->
            val plain = Importer.isPlainText(name, type)
            assertFalse("$name ($type)", plain)
            assertEquals(NESTED_LIST, Importer.newMap(NESTED_LIST, name.substringBeforeLast('.'), plain) { _, _ -> throw AssertionError("A markdown file isn't made again on a page") })
        }
    }

    @Test
    fun `a markdown file of paragraphs alone is kept as written, its frontmatter too`() = runTest {
        // A journal's note: Orbit reads it as the note of a map without ideas yet, named by its title.
        val journal = "---\ntitle: Journal\n---\nToday I walked by the river.\n\nThen I slept."
        val plain = Importer.isPlainText("Journal.md", "text/markdown")
        assertEquals("$journal\n", Importer.newMap(journal, "Journal", plain) { _, _ -> throw AssertionError("A markdown file isn't made again on a page") })
        // The same lines shared as text still become a list.
        assertEquals("# ---\n\n- title: Journal\n- ---\n- Today I walked by the river.\n- Then I slept.\n", Importer.newMap(journal, null, plain = true) { _, _ -> null })
    }

    @Test
    fun `a nested markdown list shared as text is made on a page, its top line in the middle`() = runTest {
        var asked: String? = null
        // What the page makes of it (web/paste.test.ts checks this same list).
        val middle = "# Trip\n\n- Flights\n  - Window seat\n- Hotels\n- Packing\n  - Passport\n"

        val made = Importer.newMap(NESTED_LIST, null, plain = true) { text, _ ->
            asked = text
            middle
        }

        assertEquals(NESTED_LIST, asked)
        assertEquals(middle, made)
        // Where no page can make it, as on a WebView too old for maps, the list is kept as written.
        assertEquals(NESTED_LIST, Importer.newMap(NESTED_LIST, null, plain = true) { _, _ -> null })
    }

    @Test
    fun `markdown with a nodes line in a note or a fence stays markdown, and only a YAML map is read as YAML`() {
        // A note with a line that starts with "nodes:", and a fence of YAML in a note: markdown, kept as written.
        assertFalse(Importer.isYamlMap("# Graphs\n\nA graph joins its\nnodes:\nwith edges.\n"))
        assertFalse(Importer.isYamlMap("\n# Settings\n\n```yaml\nnodes:\n  - text: Example\n```\n\n"))
        assertFalse(Importer.isYamlMap("- Graphs\n\n  A note.\n\nnodes:\n- Edges\n"))
        // YAML maps, as Orbit reads them: its nodes first, its options first, a comment first, or its frontmatter saying so.
        assertTrue(Importer.isYamlMap("nodes:\n  - text: Trip\n"))
        assertTrue(Importer.isYamlMap("title: Trip\nnodes:\n  - Lisbon\n"))
        assertTrue(Importer.isYamlMap("# Packed on Friday\nnodes:\n  - Lisbon\n"))
        assertTrue(Importer.isYamlMap("---\nflavor: yaml\n---\nnodes:\n  - Lisbon\n"))
        // A file named or typed as YAML is read as YAML, whatever it holds.
        assertTrue(Importer.isYamlMap("- Lisbon\n", yamlFile = true))
    }

    @Test
    fun `text files make maps as shared text does, and markdown files are maps as written`() {
        assertTrue(Importer.isPlainText("Trip.txt", "text/plain"))
        assertTrue(Importer.isPlainText("Trip.TXT", ""))
        assertTrue(Importer.isPlainText("Trip", "text/plain; charset=utf-8"))
        assertFalse(Importer.isPlainText("Trip.MD", ""))
        assertFalse(Importer.isPlainText("Trip.txt", "text/markdown"))
        assertFalse(Importer.isPlainText("Trip.markdown", "application/octet-stream"))
        assertFalse(Importer.isPlainText("Trip.yaml", "text/plain"))
        assertFalse(Importer.isPlainText("Trip", "application/octet-stream"))
    }

    @Test
    fun `only files other apps hand over are imported`() {
        val own = "com.hyuchia.orbit.files"

        assertTrue(importable("content", "com.android.providers.downloads.documents", own))
        assertFalse(importable("file", null, own))
        assertFalse(importable("content", own, own))
        assertFalse(importable("content", null, own))
        assertFalse(importable(null, null, own))
    }

    @Test
    fun whatChangedOnTheWayInIsSaidInTheAppsWordsCountedAcrossMaps() {
        val warnings = listOf(
            ImportWarning("floating-ideas-moved", 1, "One floating topic (“Compost?”) had no place in the tree: it's now under the center."),
            ImportWarning("icons-not-kept", 2, "2 icons were left out."),
            ImportWarning("icons-not-kept", 1, "One icon was left out."),
            ImportWarning("a-kind-not-known-yet", 1, "Something Orbit learned to say later."),
        )
        assertEquals(
            listOf("1 floating topic is now an idea under the center.", "3 icons were left out: an idea shows one icon, as an emoji.", "Something Orbit learned to say later."),
            Importer.importNotes(warnings, FileWords(Locale.ENGLISH)),
        )
        assertEquals(
            listOf("1 tema flotante ahora es una idea bajo el centro.", "Se omitieron 3 íconos: cada idea muestra un solo ícono, como emoji.", "Something Orbit learned to say later."),
            Importer.importNotes(warnings, FileWords(Locale.forLanguageTag("es-MX"))),
        )
    }

    @Test
    fun everyWarningOrbitGivesHasWordsInEveryLanguage() {
        val codes = listOf(
            "floating-ideas-moved", "summaries-moved", "callouts-moved", "boundaries-in-notes", "links-in-notes", "missing-link-targets",
            "icons-not-kept", "pictures-not-kept", "attachments-not-kept", "local-links-not-kept", "encrypted-branches-not-read", "formatting-not-kept",
        )

        for (locale in listOf(Locale.ENGLISH, Locale.forLanguageTag("es-MX"))) {
            val notes = Importer.importNotes(codes.map { ImportWarning(it, 2, "untranslated") }, FileWords(locale))
            assertTrue(notes.none { it == "untranslated" })
            assertTrue(notes.all { "2" in it })
        }
    }

    @Test
    fun whyAFileMakesNoMapIsSaidInTheAppsWords() {
        val words = FileWords(Locale.forLanguageTag("es-MX"))
        assertEquals("Ese archivo de XMind tiene contraseña. Ábrelo en XMind, guárdalo sin contraseña y vuelve a importarlo.", Importer.problemText(ImportProblem("password-protected"), words))
        assertEquals("Orbit no pudo leer eso: something new", Importer.problemText(ImportProblem("something-new", "something new"), words))
    }
}
