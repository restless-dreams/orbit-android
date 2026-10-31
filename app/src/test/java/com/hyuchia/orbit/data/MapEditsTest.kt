package com.hyuchia.orbit.data

import com.hyuchia.orbit.FileWords
import com.hyuchia.orbit.page.EditedMap
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.util.Locale

/**
 * Home's changes to maps without opening them: how they're tried, kept and
 * refused. The edits themselves are the page's (web/map-edits.ts, held to
 * Orbit in web/map-edits.test.ts); here a light stand-in makes them, and
 * refuses a map a newer Orbit made, or one with "unreadable" in it, as the
 * page does those Orbit can only show.
 */
class MapEditsTest {
    private val files: File = Files.createTempDirectory("orbit-edits").toFile()
    private val maps = File(files, "maps")
    private var now = 1_000_000_000_000L
    private val words = FileWords(Locale.US)

    private val starters = object : StarterMaps {
        override fun samples() = emptyList<String>()

        override fun welcome(): StarterMap? = null
    }

    /** What the editor was asked, to tell how many tries an edit took. */
    private val asked = mutableListOf<String>()

    /** Something to do to the map's file as the editor makes an edit, as another change landing meanwhile would. */
    private var meanwhile: (() -> Unit)? = null

    private val editor = MapTextEditor { markdown, title, tags ->
        asked += markdown
        meanwhile?.invoke()

        when {
            MapText.formatVersion(markdown) > 1 -> EditedMap(refused = "newer")
            "unreadable" in markdown -> EditedMap(refused = "unreadable")
            else -> EditedMap(markdown = (title?.let { MapText.retitle(markdown, it, words) } ?: markdown).let { text -> tags?.let { MapTags.write(text, it) } ?: text })
        }
    }

    private suspend fun loaded(language: Locale = Locale.US): Pair<MapRepository, MapEdits> {
        val repository = MapRepository(maps, File(files, "trash"), starters, FileWords(language), clock = { now }).also { it.load() }
        return repository to MapEdits(repository, editor, FileWords(language))
    }

    @After
    fun cleanUp() {
        files.deleteRecursively()
    }

    @Test
    fun `a map's tags are kept in its file, where notes apps read them, with the version before`() = runTest {
        val (repository, edits) = loaded()
        val trip = repository.add("# Trip\n")
        val plan = repository.add("---\ntheme: night\n---\n\n# Plan\n")

        assertEquals(MapEditResult.Written, edits.setTags(trip, listOf("Work", "japan")))
        assertEquals(MapEditResult.Written, edits.setTags(plan, listOf("work")))
        assertEquals("---\ntags: [Work, japan]\n---\n\n# Trip\n", repository.read(trip))
        assertEquals("---\ntheme: night\ntags: [work]\n---\n\n# Plan\n", repository.read(plan))
        assertEquals(listOf("Work", "japan"), repository.summary(trip)!!.outline.tags)
        assertEquals(VersionReason.Tags, repository.versions(trip).single().reason)
        assertEquals(MapEditResult.Missing, edits.setTags("not-a-map", listOf("work")))
    }

    @Test
    fun `a tag is renamed or taken out in every map that has it, whatever its capitals`() = runTest {
        val (repository, edits) = loaded()
        val trip = repository.add("---\ntags: [Work, japan]\n---\n\n# Trip\n")
        val plan = repository.add("---\ntheme: night\ntags: [work]\n---\n\n# Plan\n")
        val other = repository.add("# Other\n")

        assertEquals(TagRename(changed = 2, left = 0), edits.renameTag("WORK", "Office"))
        assertEquals("---\ntags: [Office, japan]\n---\n\n# Trip\n", repository.read(trip))
        assertEquals("---\ntheme: night\ntags: [Office]\n---\n\n# Plan\n", repository.read(plan))
        assertEquals("# Other\n", repository.read(other))

        // Renamed to a tag the map has already, it's one tag.
        assertEquals(TagRename(changed = 1, left = 0), edits.renameTag("japan", "office"))
        assertEquals("---\ntags: [Office]\n---\n\n# Trip\n", repository.read(trip))

        assertEquals(TagRename(changed = 2, left = 0), edits.renameTag("office", null))
        assertEquals("# Trip\n", repository.read(trip))
        assertEquals("---\ntheme: night\n---\n\n# Plan\n", repository.read(plan))
    }

    @Test
    fun `a map Orbit can only show is left as it is, byte for byte, and says why`() = runTest {
        val (repository, edits) = loaded()
        val newer = repository.add("---\nschemaVersion: 2\ntags: [work]\n---\n# Plan\n")
        val unreadable = repository.add("---\ntags: [work]\n---\n# Plan <!-- unreadable -->\n")
        val trip = repository.add("---\ntags: [work]\n---\n# Trip\n")

        assertEquals(MapEditResult.Newer, edits.rename(newer, "Roadmap"))
        assertEquals(MapEditResult.Newer, edits.setTags(newer, listOf("home")))
        assertEquals(MapEditResult.Unreadable, edits.rename(unreadable, "Roadmap"))
        assertEquals(TagRename(changed = 1, left = 2), edits.renameTag("work", "office"))
        assertEquals("---\nschemaVersion: 2\ntags: [work]\n---\n# Plan\n", repository.read(newer))
        assertEquals("---\ntags: [work]\n---\n# Plan <!-- unreadable -->\n", repository.read(unreadable))
        assertEquals("---\ntags: [office]\n---\n# Trip\n", repository.read(trip))
        assertEquals(emptyList<MapVersion>(), repository.versions(newer))

        val copy = edits.duplicate(newer)
        assertNull(copy.id)
        assertEquals(MapEditResult.Newer, copy.result)
    }

    @Test
    fun `a rename keeps the map as it was in its history first, saying why`() = runTest {
        val (repository, edits) = loaded()
        val id = repository.add("# Plan\n- A\n")

        assertEquals(MapEditResult.Written, edits.rename(id, "Roadmap"))
        assertEquals("# Roadmap\n- A\n", repository.read(id))
        assertEquals(VersionReason.Renaming, repository.versions(id).single().reason)
        assertEquals("# Plan\n- A\n", repository.readVersion(id, repository.versions(id).single().time))
    }

    @Test
    fun `a map that changes while it's edited is edited again as it is then`() = runTest {
        val (repository, edits) = loaded()
        val id = repository.add("# Plan\n- A\n")
        val file = File(maps, "$id/map.md")

        meanwhile = {
            meanwhile = null
            file.writeText("# Plan\n- A\n- B\n")
        }

        assertEquals(MapEditResult.Written, edits.setTags(id, listOf("work")))
        assertEquals("---\ntags: [work]\n---\n\n# Plan\n- A\n- B\n", repository.read(id))
        assertEquals(listOf("# Plan\n- A\n", "# Plan\n- A\n- B\n"), asked)
    }

    @Test
    fun `a change the storage can't take leaves the map as it was, and says so`() = runTest {
        val (repository, edits) = loaded()
        val id = repository.add("# Plan\n")
        val folder = File(maps, id)
        // A folder nothing can be written into fails each write as a full disk does.
        folder.setWritable(false)

        try {
            assertEquals(MapEditResult.NotWritten, edits.rename(id, "Trip"))
            assertEquals(MapEditResult.NotWritten, edits.setTags(id, listOf("work")))
        } finally {
            folder.setWritable(true)
        }

        assertEquals("# Plan\n", repository.read(id))
    }

    @Test
    fun `a map whose file can't be read is refused, and stays as it is`() = runTest {
        val (repository, edits) = loaded()
        val id = repository.add("# Plan\n")
        val file = File(maps, "$id/map.md")
        file.setReadable(false)

        try {
            assertEquals(MapEditResult.Refused, edits.rename(id, "Trip"))
        } finally {
            file.setReadable(true)
        }

        assertEquals("# Plan\n", repository.read(id))
    }

    @Test
    fun `a copy is named as one in the app's language, and is private when its map is`() = runTest {
        val (repository, edits) = loaded(Locale.forLanguageTag("es-MX"))
        val id = repository.add("# Diario\n- Hoy\n")
        repository.setPrivate(id, true)

        val copy = edits.duplicate(id)

        assertEquals(MapEditResult.Written, copy.result)
        assertEquals("Diario (copia)", repository.summary(copy.id!!)!!.title)
        assertEquals("# Diario (copia)\n- Hoy\n", repository.read(copy.id!!))
        assertEquals(true, repository.summary(copy.id!!)!!.metadata.isPrivate)
    }
}
