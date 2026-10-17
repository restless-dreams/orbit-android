package com.hyuchia.orbit.data

import com.hyuchia.orbit.FileWords
import com.hyuchia.orbit.map.GoalNumbers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

class MapTextTest {
    private val words = FileWords(Locale.US)

    @Test
    fun headingsAndListsMakeTheOutline() {
        val outline = MapText.outline(
            """
            # 🏖️ Weekend in Lisbon

            ## 🍽️ Food
            - Pastéis de nata
            - Seafood <!-- orbit status: todo -->
            ## Places
            ### Alfama
            """.trimIndent(),
            words,
        )
        assertEquals("🏖️ Weekend in Lisbon", outline.title)
        assertEquals(2, outline.branches)
        assertEquals(5, outline.ideas)
    }

    @Test
    fun aListUnderTheRootHeadingIsItsOrbit() {
        val outline = MapText.outline("# Root\n- One\n  - Deeper\n- Two\n", words)
        assertEquals(2, outline.branches)
        assertEquals(3, outline.ideas)
    }

    @Test
    fun theFrontmatterTitleAndStyleWin() {
        val outline = MapText.outline("---\ntitle: \"Trip plan\"\ntheme: night\n---\n# Trip\n## Food\n", words)
        assertEquals("Trip plan", outline.title)
        assertEquals("night", outline.style)
        assertEquals(1, outline.branches)
    }

    @Test
    fun anAutomaticThemeIsNoStyleOfItsOwn() {
        assertNull(MapText.outline("---\ntheme: auto\n---\n# Trip\n", words).style)
        assertNull(MapText.outline("# Trip\n", words).style)
    }

    @Test
    fun aMapThatIsOnlyAList() {
        val outline = MapText.outline("- [x] Groceries\n  - Milk\n  - Bread\n    - Rye\n", words)
        assertEquals("Groceries", outline.title)
        assertEquals(2, outline.branches)
        assertEquals(3, outline.ideas)
    }

    @Test
    fun notesQuotesAndCodeAreNotIdeas() {
        val outline = MapText.outline("# Root\nA note.\n> - quoted\n```\n- code\n```\n## Branch\n", words)
        assertEquals(1, outline.ideas)
    }

    @Test
    fun anEmptyFileStillHasATitle() {
        assertEquals("Untitled map", MapText.outline("", words).title)
    }

    @Test
    fun anUntitledMapIsNamedInTheAppsLanguage() {
        val spanish = FileWords(Locale.forLanguageTag("es-MX"))
        assertEquals("Mapa sin título", MapText.outline("", spanish).title)
        assertEquals("# Mapa sin título\n", MapText.newMap("", spanish))
        assertEquals("Mapa sin título.md", MapText.fileName("  ...  ", emptyList(), spanish))
    }

    @Test
    fun toDosCountCheckboxesAndStatusesOnceEach() {
        val outline = MapText.outline(
            """
            # Launch
            ## Research <!-- orbit status: done -->
            - [x] Interviews
            - [/] Survey
            - [ ] Report
            ## Build
            <!-- orbit
            status: blocked
            -->
            ## Ideas <!-- orbit status: "maybe later" -->
            - [X] Shipped one
            """.trimIndent(),
            words,
        )
        // Research counts through its three to-dos, as its ring does; "maybe later" is a label, not a to-do.
        assertEquals(5, outline.progress.total)
        assertEquals(2, outline.progress.finished)
    }

    @Test
    fun theHomeCardCountsAMapAsItsOutlineDoes() {
        val outline = MapText.outline(
            """
            # 🎯 Goals

            - 🛒 Groceries <!-- orbit type: progress -->
              - Apples <!-- orbit type: goal, target: 5, current: 2, unit: apples -->
                Green ones, if they have them.
              - Eggs <!-- orbit type: goal, target: 12, current: 12 -->
            - 🗾 Japan trip <!-- orbit type: goal, target: 3000, current: 1250, unit: USD -->
            - 💳 Credit card <!-- orbit type: goal, start: 4800, current: 3100, target: 0, unit: USD -->
            - ✈️ Trip planning
              - [x] Book the flights
              - [ ] Renew the passport
              - Hotel nights <!-- orbit type: goal, target: 4, current: 2 -->
            """.trimIndent(),
            words,
        )
        // The outline's header for this map says "2 of 7 done · 52%".
        assertEquals("2 of 7 done · 52%", GoalNumbers.progress(outline.progress, words, Locale.US))
        assertFalse(outline.progress.allGoals)
    }

    @Test
    fun goalsWrittenInBlocksAndCommentsOfTheirOwnCount() {
        val outline = MapText.outline(
            """
            # Shopping
            - Apples
              <!-- orbit
              type: goal
              target: 5
              current: 5
              unit: apples
              -->
              Green ones, if they have them.
            - Eggs
              <!-- orbit type: "goal", target: 12, current: 6 -->
            """.trimIndent(),
            words,
        )
        // Apples reached, Eggs halfway: both are goals, so they're reached rather than done.
        assertEquals("1 of 2 reached · 75%", GoalNumbers.progress(outline.progress, words, Locale.US))
    }

    @Test
    fun aMapWithoutToDosOrGoalsCountsNothing() {
        assertEquals(0, MapText.outline("# Trip\n- Food\n- Places\n", words).progress.total)
        // Statuses that are labels, as in a map of a database, aren't to-dos.
        val columns = MapText.outline("# Library\n## copies\n- id <!-- orbit status: \"primary key\" -->\n- series_id <!-- orbit link: \"#series\", status: \"foreign key\" -->\n", words)
        assertEquals(0, columns.progress.total)
    }

    @Test
    fun searchTextKeepsTheWordsAndDropsTheMarkup() {
        val text = MapText.searchText(
            """
            ---
            theme: night
            ---
            # **Trip** to [Lisbon](https://example.com)
            Plan the *weekend*.
            ## Food <!-- orbit color: "#FF0000" -->
            <!-- orbit
            tags: [eat]
            -->
            - [x] Pastéis de nata #sweet
            """.trimIndent(),
        )
        assertEquals(listOf("Trip to Lisbon", "Plan the weekend.", "Food", "Pastéis de nata #sweet"), text.lines().filter { it.isNotBlank() })
        assertFalse(text.contains("theme"))
        assertFalse(text.contains("orbit"))
    }

    @Test
    fun retitlingKeepsTheRootsAttributes() {
        assertEquals("# Holidays <!-- orbit icon: 🌴 -->\n## Food", MapText.retitle("# Trip <!-- orbit icon: 🌴 -->\n## Food", "Holidays", words))
        assertEquals("---\ntitle: \"Plans: 2027\"\n---\n# Plans: 2027\n", MapText.retitle("---\ntitle: Plans\n---\n# Plans\n", "Plans: 2027", words))
        assertEquals("- Groceries\n  - Milk", MapText.retitle("- Shopping\n  - Milk", "Groceries", words))
    }

    @Test
    fun titlesThatLookLikeStructureStayTitles() {
        assertEquals("# \\# not a heading\n", MapText.newMap("# not a heading", words))
        assertEquals("# \\1. First\n", MapText.newMap("1. First", words))
    }

    @Test
    fun folderNamesAreReadableAndUnique() {
        assertEquals("weekend-in-lisbon", MapText.folderName("Weekend in Lisbon!", emptySet()))
        assertEquals("ideas-2", MapText.folderName("Ideas", setOf("ideas")))
        assertEquals("map", MapText.folderName("🪐", emptySet()))
        assertEquals("investigación", MapText.folderName("Investigación", emptySet()))
    }

    @Test
    fun aNewMapIsItsRootAndItsStyle() {
        assertEquals("# Study plan\n", MapText.newMap("  Study plan ", words))
        assertEquals("# Untitled map\n", MapText.newMap("", words))
        assertEquals("---\ntheme: cosmos\n---\n\n# Stars\n", MapText.newMap("Stars", words, "cosmos"))
    }

    @Test
    fun aMapWithIdeasOrNotesHasContent() {
        assertTrue(MapText.hasContent("# Trip\n"))
        assertTrue(MapText.hasContent("Just a thought, no headings.\n"))
        assertTrue(MapText.hasContent("---\ntheme: night\n---\n\n#  <!-- orbit id: empty -->\n- Beach\n"))
    }

    @Test
    fun anEmptiedMapHasNone() {
        assertFalse(MapText.hasContent(""))
        assertFalse(MapText.hasContent("   \n\n"))
        assertFalse(MapText.hasContent("#  <!-- orbit id: empty -->\n"))
        assertFalse(MapText.hasContent("---\ntheme: night\n---\n\n# \n"))
        assertFalse(MapText.hasContent("<!--\norbit\nstatus: done\n-->\n#\n-\n"))
    }

    @Test
    fun longTextIsCutAtAWordsEnd() {
        assertEquals("A short title", MapText.shorten("A  short\ttitle", 80))
        assertEquals("The quick brown…", MapText.shorten("The quick brown fox jumps over the lazy dog", 20))
        assertEquals("x".repeat(19) + "…", MapText.shorten("x".repeat(500), 20))
    }
}
