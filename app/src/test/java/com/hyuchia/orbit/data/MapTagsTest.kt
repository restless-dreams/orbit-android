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
    fun `what's written reads back the same`() {
        val tags = listOf("work", "Japan trip", "2027", "a: b", "\"quoted\"", "-dash", "café")
        assertEquals(tags, MapTags.read(MapTags.write("# Trip\n", tags)))
        assertEquals(listOf("work"), MapTags.read(MapTags.write("# Trip\n", listOf(" work ", "#work", "WORK"))))
    }
}
