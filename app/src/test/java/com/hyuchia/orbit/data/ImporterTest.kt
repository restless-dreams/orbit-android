package com.hyuchia.orbit.data

import com.hyuchia.orbit.FileWords
import com.hyuchia.orbit.page.ImportProblem
import com.hyuchia.orbit.page.ImportWarning
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

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
