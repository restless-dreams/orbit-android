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
    fun aMapSaysTheVersionOfTheFormatItIsWrittenFor() {
        assertEquals(1, MapText.formatVersion("# Talk\n"))
        assertEquals(2, MapText.formatVersion("---\ntitle: Talk\nschemaVersion: 2\n---\n# Talk\n"))
        assertEquals(3, MapText.formatVersion("---\nschemaVersion: \"3\"\n---\n# Talk\n"))
        assertEquals(2, MapText.formatVersion("title: Talk\nschemaVersion: 2\nnodes:\n  - text: Talk\n"))
        // In a markdown map's body, the same words are only text.
        assertEquals(1, MapText.formatVersion("# Talk\nschemaVersion: 4\n"))
        assertEquals(1, MapText.formatVersion("---\nschemaVersion: soon\n---\n# Talk\n"))
    }

    @Test
    fun `a version is read as Orbit reads it, any number above 0, one between two versions counting as the later`() {
        assertEquals(2, MapText.formatVersion("---\nschemaVersion: 2.0\n---\n# Talk\n"))
        assertEquals(2, MapText.formatVersion("---\nschemaVersion: 1.5\n---\n# Talk\n"))
        assertEquals(1, MapText.formatVersion("---\nschemaVersion: 0.5\n---\n# Talk\n"))
        assertEquals(2, MapText.formatVersion("schemaVersion: 2.0\nnodes:\n  - text: Talk\n"))
        assertEquals(1, MapText.formatVersion("---\nschemaVersion: -2\n---\n# Talk\n"))
    }

    @Test
    fun `a YAML map is named, counted and tagged as Orbit reads it`() {
        // As the release data has one, and older versions of Orbit kept it.
        val yaml = "title: Study notes\nnodes:\n  - text: Photosynthesis\n    icon: 🌿\n    children:\n      - text: Light reactions\n        status: done\n" +
            "      - text: Calvin cycle\n        note: Fixes carbon from the air.\n        status: todo\n      - Chlorophyll\n"
        val outline = MapText.outline(yaml, words)

        assertEquals("Study notes", outline.title)
        assertEquals(3, outline.branches)
        assertEquals(3, outline.ideas)
        assertEquals(listOf("Photosynthesis", "Light reactions", "Calvin cycle", "Chlorophyll"), MapText.ideas(yaml).map { it.name })
        assertEquals(listOf(null, 0, 0, 0), MapText.ideas(yaml).map { it.parent })
        assertEquals(1.0, outline.progress.done, 0.0)
        assertEquals(2, outline.progress.total)

        // Without a title, its one center names it; several at the top share one the title names, or none.
        assertEquals("Photosynthesis", MapText.outline("nodes:\n  - icon: 🌿\n    text: Photosynthesis\n", words).title)
        val several = MapText.outline("tags: [science]\nnodes:\n  - text: Photosynthesis\n  - text: Respiration\n", words)
        assertEquals("Untitled map", several.title)
        assertEquals(2, several.branches)
        assertEquals(listOf("science"), several.tags)
    }

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
    fun goalsAndProgressWrittenForANewerVersionAreIdeasLikeAnyOther() {
        val outline = MapText.outline(
            """
            # Shopping
            - Apples <!-- orbit type: goal@2, target: 5, current: 5 -->
            - Eggs <!-- orbit type: goal@1, target: 12, current: 6 -->
            - Pears <!-- orbit type: goal@99999999999, target: 4, current: 4 -->
            - Week <!-- orbit type: progress@2 -->
              - [x] Bread
            """.trimIndent(),
            words,
        )
        // A goal written for a newer goal may mean its numbers another way, however new it is: only Eggs is a goal here, and the to-do
        // under the newer Progress counts as it does under any idea.
        assertEquals("1 of 2 done · 75%", GoalNumbers.progress(outline.progress, words, Locale.US))
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
    fun `a YAML map is searched for its ideas and notes, never for the words of its structure`() {
        val yaml = """
            title: Biology
            nodes:
              - text: Cells
                status: done
                color: "#4CAF50"
                note: The **smallest** unit of [life](https://example.com/life)
                children:
                  - text: Nucleus
                    note: |
                      Holds the DNA.

                      Has a *membrane*.
                  - { text: Ribosome, note: "Makes proteins", priority: high }
                  - Mitochondrion
        """.trimIndent()
        val text = MapText.searchText(yaml)

        assertEquals(
            listOf("Cells", "The smallest unit of life", "Nucleus", "Holds the DNA.", "Has a membrane.", "Ribosome", "Makes proteins", "Mitochondrion"),
            text.lines().filter { it.isNotBlank() },
        )

        // The words its structure uses, which every YAML map would match on, and values no one writes to be read.
        for (word in listOf("nodes", "children", "text", "note", "status", "done", "color", "4CAF50", "priority", "high", "example.com")) {
            assertFalse("“$word” is searched", text.contains(word))
        }
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
    fun `a YAML map's ideas written in flow style are read as Orbit reads them`() {
        // Orbit's own example writes some children this way; a whole idea can be written in braces too.
        val yaml = """
            title: Product roadmap
            nodes:
              - text: Product roadmap
                children:
                  - text: Research
                    status: done
                    children: [User interviews, Competitor teardown]
                  - { text: Build, status: active, children: [{ text: Frontend, children: [App shell] }, Backend] }
                  - text: Launch
        """.trimIndent() + "\n"

        val ideas = MapText.ideas(yaml)
        assertEquals(listOf("Product roadmap", "Research", "User interviews", "Competitor teardown", "Build", "Frontend", "App shell", "Backend", "Launch"), ideas.map { it.name })
        assertEquals(listOf(null, 0, 1, 1, 0, 4, 5, 4, 0), ideas.map { it.parent })

        // Home counts them, and their statuses, as the map does.
        val outline = MapText.outline(yaml, words)
        assertEquals(3, outline.branches)
        assertEquals(8, outline.ideas)
        assertEquals(1.0, outline.progress.done, 0.0)

        // The list of nodes on its line, and a list going on over several lines, with comments in it.
        assertEquals(listOf("Trip", "Tokyo", "Kyoto"), MapText.ideas("title: Trip\nnodes: [{ text: Trip, children: [Tokyo, Kyoto] }]\n").map { it.name })
        val over = "nodes:\n  - text: Trip\n    children: [\n      Tokyo,  # the capital\n      # and then\n      { text: Kyoto, status: done }\n    ]\n  - Osaka\n"
        assertEquals(listOf("", "Trip", "Tokyo", "Kyoto", "Osaka"), MapText.ideas(over).map { it.name })
        assertEquals(listOf(null, 0, 1, 1, 0), MapText.ideas(over).map { it.parent })
    }

    @Test
    fun `hashtags at the end of an idea are its tags, not its name, as Orbit reads them`() {
        val markdown = "# Trip\n- Launch #web #ads\n- Offline sync #frontend #sprint-3 <!-- orbit status: done -->\n- C#\n- Fix #42\n- ship \\#notatag\n"

        assertEquals(listOf("Trip", "Launch", "Offline sync", "C#", "Fix #42", "ship #notatag"), MapText.ideas(markdown).map { it.name })
    }

    @Test
    fun `what a YAML idea's other keys hold isn't ideas`() {
        // Tags, a note's lines and a picture's fields, written as lists and blocks, however they're indented.
        val yaml = "nodes:\n  - text: Trip\n    tags:\n      - travel\n    links:\n    - https://example.com\n    note: |\n      - pack light\n" +
            "    image:\n      src: media/map.png\n      text: The route\n    children:\n      - Tokyo # the capital\n      - text: Kyoto\n        tags: [old, temples]\n"

        assertEquals(listOf("Trip", "Tokyo", "Kyoto"), MapText.ideas(yaml).map { it.name })
        assertEquals(listOf(null, 0, 0), MapText.ideas(yaml).map { it.parent })
    }

    @Test
    fun `a YAML map without ideas has none`() {
        assertFalse(MapText.hasContent("nodes: []\n"))
        assertFalse(MapText.hasContent("title: Biology\nnodes:\n"))
        assertFalse(MapText.hasContent("nodes:\n  - text: \"\"\n"))
        // Empty nodes at the top share a center Orbit names after the title, as it does a markdown map's after its frontmatter's: nothing of the map's own.
        assertFalse(MapText.hasContent("title: Biology\ntheme: night\nnodes:\n  - text: ''\n    id: biology\n    children: []\n  - ~\n  - null\n  -\n"))
        assertFalse(MapText.hasContent("nodes: [{text: \"\", children: []}, \"\", {id: biology}]\n"))
        assertFalse(MapText.hasContent("nodes: [\n  {text: \"\"},\n  \"\"\n]\n"))
        assertFalse(MapText.hasContent("# Made by a script\nnodes:\n  - text: \"\"  # none yet\n    children:\n      - \"  \"\n"))
        // Its options alone: a YAML map without any nodes, as its frontmatter can say too.
        assertFalse(MapText.hasContent("title: Biology\ntheme: night\n"))
        assertFalse(MapText.hasContent("---\nflavor: yaml\ntitle: Biology\n---\nnodes: []\n"))
    }

    @Test
    fun `a YAML map with an idea, a note or any value on one has content`() {
        assertTrue(MapText.hasContent("nodes:\n  - text: Biology\n"))
        assertTrue(MapText.hasContent("title: Biology\nnodes:\n- Biology\n"))
        assertTrue(MapText.hasContent("nodes: [Biology]\n"))
        assertTrue(MapText.hasContent("nodes: [{text: \"\", children: [Cells]}]\n"))
        assertTrue(MapText.hasContent("nodes: [\n  {text: Cells},\n]\n"))
        assertTrue(MapText.hasContent("nodes:\n  - text: \"\"\n    children: [It's done]\n"))
        assertTrue(MapText.hasContent("nodes:\n  - text: \"\"\n    note: Cells and how they live.\n"))
        assertTrue(MapText.hasContent("nodes:\n  - text: \"\"\n    children:\n      - text: Cells\n"))
        assertTrue(MapText.hasContent("nodes:\n  - text: \"\"\n    image:\n      src: media/cell.jpg\n"))
        assertTrue(MapText.hasContent("nodes:\n  - text: \"\"\n    note: |\n      # Cells\n"))
        // A value that starts with # is text, not a comment, as Orbit reads it.
        assertTrue(MapText.hasContent("nodes:\n  - text: #biology\n"))
        assertTrue(MapText.hasContent("nodes:\n  - text: 0\n"))
        assertTrue(MapText.hasContent("nodes:\n  - text: \"Cells,\n      and how they live\"\n"))
        assertTrue(MapText.hasContent("---\nflavor: yaml\n---\nnodes:\n  - text: Cells\n"))
    }

    @Test(timeout = 10_000)
    fun `a YAML map's long line is read in one pass`() {
        val spaces = " ".repeat(2_000_000)
        assertTrue(MapText.hasContent("title: Biology\nnodes:\n  - a${spaces}b\n"))
        assertFalse(MapText.hasContent("title: Biology\nnodes:\n  - text: \"\"$spaces\n"))
    }

    @Test(timeout = 5_000)
    fun `a heading, a list item or frontmatter with a long run of spaces is read in one pass`() {
        val spaces = " ".repeat(100_000)
        // A line separator after the spaces makes a line neither a heading nor a list item: that's told in one pass too.
        val markdown = "---\ntitle: Lisbon${spaces}trip\n---\n# Lisbon${spaces}trip\n## Tram$spaces##\n- Fado$spaces<!-- orbit status: done -->\n" +
            "<!-- orbit\nstatus: done$spaces\n-->\n$spaces\n-$spaces\u2028Seen\n#$spaces\u2028Seen\n"
        val outline = MapText.outline(markdown, words)

        assertEquals("Lisbon${spaces}trip", outline.title)
        assertEquals(1, outline.branches)
        assertEquals(listOf("Lisbon${spaces}trip", "Tram", "Fado"), MapText.ideas(markdown).map { it.name })
        assertEquals(1.0, outline.progress.done, 0.0)
        assertFalse(MapText.hasContent("#\n$spaces\n"))
        assertTrue(MapText.retitle(markdown, "Porto", words).startsWith("---\ntitle: Porto\n---\n# Porto\n"))
    }

    @Test(timeout = 5_000)
    fun `a YAML map's item or key with a long run of spaces is read in one pass`() {
        val spaces = " ".repeat(100_000)
        val yaml = "title: Trip$spaces\nnodes:\n  - Lisbon${spaces}trip\n    children:\n      - text: Tram$spaces\n        status: done$spaces\n      -$spaces\u2028Seen\n"
        val outline = MapText.outline(yaml, words)

        assertEquals("Trip", outline.title)
        assertEquals(listOf("Lisbon${spaces}trip", "Tram"), MapText.ideas(yaml).map { it.name })
        assertEquals(1.0, outline.progress.done, 0.0)
    }

    @Test(timeout = 5_000)
    fun `a line of thousands of brackets or comments that never close is read in one pass`() {
        // Read once from each, as the patterns did, 20,000 of these took seconds.
        val brackets = "[".repeat(20_000)
        val links = "![a](".repeat(5_000)
        val comments = "<!--".repeat(5_000)
        val orbit = "<!-- orbit".repeat(2_000)
        val markdown = "# Trip $brackets\n- $links\n- $comments\n- Fado $orbit\n$brackets\n## Map $comments x -->\n"

        assertEquals("Trip $brackets", MapText.outline(markdown, words).title)
        assertEquals(listOf("Trip $brackets", links, comments, "Fado $orbit", "Map"), MapText.ideas(markdown).map { it.name })
        assertTrue(MapText.searchText(markdown).contains(brackets))
        assertTrue(MapText.hasContent(markdown))
        assertTrue(MapText.retitle(markdown, "Porto", words).startsWith("# Porto\n"))
    }

    @Test
    fun `links and comments read in one pass come out as the patterns made them`() {
        // The patterns a line's links and comments were read with, which took seconds on lines of thousands of brackets.
        val link = Regex("""!?\[([^\]]*)]\([^)]*\)""")
        val comment = Regex("""(?<!\s)\s*<!--.*?-->""")
        val orbit = Regex("""<!--\s*orbit\b(.*?)-->""")
        val pieces = listOf("[", "]", "(", ")", "!", "a", " ", "  ", "<!--", "-->", "orbit", "orbits", "-", ">", "<", "b c", "_")
        val random = java.util.Random(7)

        repeat(20_000) {
            val text = (0 until random.nextInt(14)).joinToString("") { pieces[random.nextInt(pieces.size)] }

            assertEquals(text, link.replace(text) { it.groupValues[1] }, MapText.linkWords(text))
            assertEquals(text, comment.replace(text, ""), MapText.withoutComments(text))
            assertEquals(text, comment.find(text)?.value, MapText.commentsIn(text).firstOrNull()?.let(text::substring))
            assertEquals(text, orbit.find(text)?.groupValues?.get(1), MapText.orbitComment(text))
        }
    }

    @Test
    fun `markdown with a line that starts with nodes stays markdown, as Orbit reads it`() {
        assertTrue(MapText.hasContent("# Graphs\nnodes:\n"))
        assertTrue(MapText.hasContent("# Graphs\nnodes: []\nGraphs are made of nodes and edges.\n"))
        assertTrue(MapText.hasContent("# Graphs\nnodes:\n## Edges\nare the lines\n"))
        assertEquals("Graphs", MapText.outline("# Graphs\nnodes: the points of a graph\n## Edges\n", words).title)
        assertEquals(listOf("Graphs", "Edges"), MapText.ideas("# Graphs\nnodes: the points of a graph\n## Edges\n").map { it.name })
    }

    @Test
    fun ideasAreReadWithTheirNamesAndParents() {
        val ideas = MapText.ideas("---\ntitle: Trip\n---\n# **Lisbon** trip\n## Flights <!-- orbit status: done -->\n- [x] Book _seats_\n- copy_versions_here\n## Read [the guide](https://example.com)\n")

        assertEquals(
            listOf(
                TextIdea("Lisbon trip", null),
                TextIdea("Flights", 0),
                TextIdea("Book seats", 1),
                // Underscores inside a word are the name's own.
                TextIdea("copy_versions_here", 1),
                TextIdea("Read the guide", 0),
            ),
            ideas,
        )
    }

    @Test
    fun longTextIsCutAtAWordsEnd() {
        assertEquals("A short title", MapText.shorten("A  short\ttitle", 80))
        assertEquals("The quick brown…", MapText.shorten("The quick brown fox jumps over the lazy dog", 20))
        assertEquals("x".repeat(19) + "…", MapText.shorten("x".repeat(500), 20))
    }
}
