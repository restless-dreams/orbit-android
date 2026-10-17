package com.hyuchia.orbit.page

import com.hyuchia.orbit.FileWords
import com.hyuchia.orbit.nearby.SpanishWords
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.Locale

class PageWordsTest {
    private val english = FileWords(Locale.US).pageWords()
    private val spanish = SpanishWords.pageWords()

    private fun asset(name: String): String = File("src/main/assets/map/$name").readText()

    @Test
    fun pagesSpeakTheLanguageOfTheAppsStringsWithTheRegionOfTheAppsLocale() {
        val mexico = Locale.forLanguageTag("es-MX")
        assertEquals("es-MX", pageLanguage(listOf(mexico), "es"))
        // A phone whose first language the app doesn't speak shows the app in its next one.
        assertEquals("es-MX", pageLanguage(listOf(Locale.FRANCE, mexico), "es"))
        assertEquals("en-GB", pageLanguage(listOf(Locale.UK), "en"))
        // In none of them, the app's English, and its pages' too.
        assertEquals("en", pageLanguage(listOf(Locale.FRANCE, Locale.GERMANY), "en"))
        assertEquals("es", pageLanguage(emptyList(), "es"))
    }

    @Test
    fun theEnglishWordsAreThePagesOwn() {
        assertEquals("en", english.language)
        assertEquals("of {target}", english.goalOfTarget)
        assertEquals("left", english.goalLeft)
        assertEquals("Untitled map", english.untitledMap)
        assertEquals("Orbit found no ideas in this text.", english.noIdeas)
        assertEquals("Orbit couldn't read all of this text.", english.textNotRead)
        assertEquals("Orbit couldn't read all of this map, so it didn't add to it.", english.mapNotAddedTo)
        assertEquals("A newer version of Orbit made this map. Update Orbit to add to it.", english.mapFromNewerOrbit)
        assertEquals("Waiting for the presenter", english.waiting)
        assertEquals("Looking for the presenter…", english.looking)
        assertEquals("The presenter isn't letting anyone new in.", english.closed)
        assertEquals("Tap for sound", english.tapForSound)
        assertEquals("Present (P)", english.presentKey)
        assertEquals("Stop presenting (Esc)", english.stopPresentingKey)
        assertEquals("Drawn with Orbit", english.drawnWithOrbit)
        assertEquals(
            mapOf(
                "milestone" to "Milestone", "question" to "Question", "decision" to "Decision", "risk" to "Risk",
                "source" to "Source", "person" to "Person", "progress" to "Progress", "goal" to "Goal",
            ),
            english.kinds,
        )
    }

    @Test
    fun theSpanishWordsComeFromTheAppsSpanishStrings() {
        assertEquals("de {target}", spanish.goalOfTarget)
        assertEquals("por pagar", spanish.goalLeft)
        assertEquals("Mapa sin título", spanish.untitledMap)
        assertEquals("Hito", spanish.kinds["milestone"])
        assertEquals("Meta", spanish.kinds["goal"])
        assertEquals("Presentar", spanish.present)
        // The same words the join page and the map's sharing screen use.
        assertEquals("Descargar para Orbit", spanish.downloadForOrbit)
        assertEquals("Quien presenta te quitó de la presentación.", spanish.removed)
    }

    @Test
    fun aPageHearsTheLanguageAsACommandOfItsOwn() {
        val command = protocolJson.parseToJsonElement(protocolJson.encodeToString(LanguageCommand.serializer(), LanguageCommand(spanish))).jsonObject
        assertEquals("language", command.getValue("type").jsonPrimitive.content)
        val words = command.getValue("words").jsonObject
        assertEquals("es-MX", words.getValue("language").jsonPrimitive.content)
        assertEquals("Hito", words.getValue("kinds").jsonObject.getValue("milestone").jsonPrimitive.content)
    }

    @Test
    fun theTemplatesTakeTheirLanguage() {
        listOf("web-page.html", "browser.html", "join.html").forEach { name ->
            val page = fill(asset(name), mapOf(htmlIn("es-MX")))
            assertTrue(name, page.contains("<html lang=\"es-MX\">"))
        }
    }

    @Test
    fun aWebPageSpeaksTheLanguageOfItsWordsWithNothingLeftToFill() {
        val page = WebPage.page(asset("web-page.html"), "", "Viaje", "# Viaje\n", AppTheme(dark = false, tokens = emptyMap()), emptyList(), null, spanish)
        assertTrue(page.contains("<html lang=\"es-MX\">"))
        assertTrue(page.contains("title=\"Presentar (P)\""))
        assertTrue(page.contains("aria-label=\"Dejar de presentar (Esc)\""))
        assertTrue(page.contains("Descargar para Orbit"))
        assertTrue(page.contains(">Dibujado con Orbit</a>"))
        assertTrue(page.contains("\"untitledMap\":\"Mapa sin título\""))
        assertFalse(page.contains("<!--"))
    }

    @Test
    fun anEnglishWebPageReadsAsBefore() {
        val page = WebPage.page(asset("web-page.html"), "", "Trip", "# Trip\n", AppTheme(dark = false, tokens = emptyMap()), emptyList(), null, english)
        assertTrue(page.contains("<html lang=\"en\">"))
        assertTrue(page.contains("<button class=\"page__present\" type=\"button\" title=\"Present (P)\">"))
        assertTrue(page.contains("\n  Present\n</button>"))
        assertTrue(page.contains("<nav class=\"page__controls\" aria-label=\"Presentation\">"))
        assertTrue(page.contains("aria-label=\"Previous idea\""))
        assertTrue(page.contains("aria-label=\"Next idea\""))
        assertTrue(page.contains("\n  Download for Orbit\n"))
        assertTrue(page.contains(">Drawn with Orbit</a>"))
        assertFalse(page.contains("<!--"))
    }
}
