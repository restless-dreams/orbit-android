package com.hyuchia.orbit.data

import org.junit.Assert.assertEquals
import org.junit.Test

class MapTagsTest {
    @Test
    fun `tags are read from a list on one line, a tag a line, or words apart with commas`() {
        assertEquals(listOf("work", "japan trip"), MapTags.read("---\ntags: [work, japan trip]\n---\n\n# Trip\n"))
        assertEquals(listOf("work", "2027"), MapTags.read("---\ntheme: night\ntags:\n  - work\n  - \"2027\"\nviewMode: all\n---\n\n# Trip\n"))
        assertEquals(listOf("work", "trip"), MapTags.read("---\ntags: work, trip\n---\n# Trip\n"))
        assertEquals(listOf("solo"), MapTags.read("---\ntags: solo\n---\n# Trip\n"))
    }

    // Each read below is what Orbit's own YAML reading of the frontmatter gives (web/look.ts's tagsOf over parseOrbit).

    @Test
    fun `a comment after the tags is no tag, nor part of one`() {
        assertEquals(listOf("work", "japan trip"), MapTags.read("---\ntags: [work, japan trip] # the map's\n---\n# Trip\n"))
        assertEquals(listOf("work", "trip"), MapTags.read("---\ntags: work, trip # the map's\n---\n# Trip\n"))
        assertEquals(listOf("work", "a # b"), MapTags.read("---\ntags:\n  - work # first\n  - \"a # b\" # second\n---\n# Trip\n"))
        assertEquals(listOf("work", "trip"), MapTags.read("---\ntags:\n  - work\n  # the next one too\n  - trip\n---\n# Trip\n"))
        assertEquals(listOf("Biology", "plants"), MapTags.read("tags: [Biology, plants] # school\nnodes:\n  - text: Photosynthesis\n"))
        // A hash inside a word, or one a value starts with, is the tag's own.
        assertEquals(listOf("C#", "web"), MapTags.read("---\ntags: [C#, web]\n---\n# Trip\n"))
        assertEquals(listOf("C#", "web"), MapTags.read("---\ntags: C#, web\n---\n# Trip\n"))
    }

    @Test
    fun `a quoted tag keeps its commas, quotes and escapes`() {
        assertEquals(listOf("say \"hi\", then go", "work"), MapTags.read("---\ntags: [\"say \\\"hi\\\", then go\", work]\n---\n# Trip\n"))
        assertEquals(listOf("it's, here", "work"), MapTags.read("---\ntags: ['it''s, here', work]\n---\n# Trip\n"))
        assertEquals(listOf("back\\slash", "été"), MapTags.read("---\ntags: [\"back\\\\slash\", \"\\u00e9t\\u00e9\"]\n---\n# Trip\n"))
        // An apostrophe inside a word opens no quotes.
        assertEquals(listOf("it's", "work"), MapTags.read("---\ntags: [it's, work]\n---\n# Trip\n"))
        // Words in quotes, apart with commas, are tags apart.
        assertEquals(listOf("work", "trip"), MapTags.read("---\ntags: \"work, trip\"\n---\n# Trip\n"))
    }

    @Test
    fun `a list of tags may go on over several lines, and is written over whole`() {
        val text = "---\ntags: [work,\n  japan trip] # both\ntheme: night\n---\n# Trip\n"

        assertEquals(listOf("work", "japan trip"), MapTags.read(text))
        assertEquals("---\ntags: [home]\ntheme: night\n---\n# Trip\n", MapTags.write(text, listOf("home")))
    }

    @Test
    fun `a tag is the same whatever its capitals, and loses a leading hash`() {
        assertEquals(listOf("Work", "trip"), MapTags.read("---\ntags: [Work, \"#trip\", work, '  trip ']\n---\n# Trip\n"))
        assertEquals(MapTags.key("Japan  Trip"), MapTags.key("#japan trip"))
    }

    @Test
    fun `a map without tags has none`() {
        assertEquals(emptyList<String>(), MapTags.read("# Trip\n\n- tags: [not, these]\n"))
        assertEquals(emptyList<String>(), MapTags.read("---\ntheme: night\n---\n# Trip\n"))
        assertEquals(emptyList<String>(), MapTags.read("---\ntags:\n---\n# Trip\n"))
    }

    @Test
    fun `tags go in a frontmatter of their own, as Orbit writes them`() {
        assertEquals("---\ntags: [work, japan trip]\n---\n\n# Trip\n\n- One\n", MapTags.write("# Trip\n\n- One\n", listOf("work", "japan trip")))
        // A tag YAML would read as a number goes in quotes, so each goes on a line: what Orbit's own writer makes.
        assertEquals(
            "---\ntags:\n  - work\n  - japan trip\n  - \"2027\"\n---\n\n# Trip\n\n- One\n",
            MapTags.write("# Trip\n\n- One\n", listOf("work", "japan trip", "2027")),
        )
    }

    @Test
    fun `tags join a frontmatter, or take the place of the ones it had`() {
        assertEquals("---\ntheme: night\ntags: [work]\n---\n\n# Trip\n", MapTags.write("---\ntheme: night\n---\n\n# Trip\n", listOf("work")))
        assertEquals("---\ntheme: night\ntags: [home, garden]\n---\n# Trip\n", MapTags.write("---\ntheme: night\ntags: [work]\n---\n# Trip\n", listOf("home", "garden")))
        assertEquals(
            "---\ntags: [trip]\nviewMode: all\n---\n# Trip\n",
            MapTags.write("---\ntags:\n  - work\n  - \"2027\"\nviewMode: all\n---\n# Trip\n", listOf("trip")),
        )
    }

    @Test
    fun `no tags takes the key out, and a frontmatter left empty with it`() {
        assertEquals("---\ntheme: night\n---\n\n# Trip\n", MapTags.write("---\ntheme: night\ntags: [work]\n---\n\n# Trip\n", emptyList()))
        assertEquals("# Trip\n", MapTags.write("---\ntags:\n  - work\n  - trip\n---\n\n# Trip\n", emptyList()))
        assertEquals("# Trip\n", MapTags.write("# Trip\n", emptyList()))
    }

    @Test
    fun `a YAML map keeps its tags among its own keys, never in frontmatter that would make it markdown`() {
        val yaml = "title: Study notes\nnodes:\n  - text: Photosynthesis\n    tags: [not, the, map's]\n"

        assertEquals(emptyList<String>(), MapTags.read(yaml))
        assertEquals("tags: [Biology]\n$yaml", MapTags.write(yaml, listOf("Biology")))
        assertEquals(listOf("Biology", "plants"), MapTags.read("tags:\n  - Biology\n  - plants\nnodes:\n  - text: Photosynthesis\n"))
        assertEquals("tags: [School]\nnodes:\n  - text: Photosynthesis\n", MapTags.write("tags: [Biology]\nnodes:\n  - text: Photosynthesis\n", listOf("School")))
        assertEquals("nodes:\n  - text: Photosynthesis\n", MapTags.write("tags: [Biology]\nnodes:\n  - text: Photosynthesis\n", emptyList()))
    }

    @Test(timeout = 5_000)
    fun `tags with a long run of spaces are read in one pass`() {
        val spaces = " ".repeat(100_000)

        assertEquals(listOf("work", "japan trip"), MapTags.read("---\ntags: work,${spaces}japan trip$spaces\n---\n# Trip\n"))
        assertEquals(listOf("work", "japan trip"), MapTags.read("---\ntags:\n  - work$spaces\n  -${spaces}japan${spaces}trip\n---\n# Trip\n"))
    }

    @Test
    fun `what's written reads back the same`() {
        val tags = listOf("work", "Japan trip", "2027", "a: b", "\"quoted\"", "-dash", "café")
        assertEquals(tags, MapTags.read(MapTags.write("# Trip\n", tags)))
        assertEquals(listOf("work"), MapTags.read(MapTags.write("# Trip\n", listOf(" work ", "#work", "WORK"))))
    }
}
