package com.hyuchia.orbit.data

import com.hyuchia.orbit.FileWords
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

class FolderSyncTest {
    private val words = FileWords(Locale.US)
    private val folder = "content://folder"

    /**
     * A map here; with `file`, it's kept in the folder, matching `matched`
     * when they last compared, and missed by a sync at `missingSince`.
     */
    private fun map(id: String, text: String, file: String? = null, matched: String = text, folder: String = this.folder, missingSince: Long? = null, readable: Boolean = true) =
        LocalMap(id, digest(text), file?.let { SyncRecord(folder, it, digest(matched), modified = 1, size = 1, missingSince = missingSince) }, readable)

    private val now = 1_000_000_000L

    /** A file in the folder; a null `text` wasn't read, as it's just as it was when it last matched. */
    private fun file(name: String, text: String?) = RemoteFile(FolderFile(name, modified = 2, size = 2), text)

    @Test
    fun `a map not in the folder yet gets a file of its own`() {
        assertEquals(listOf(SyncStep.Write("plan", null)), planSync(folder, listOf(map("plan", "# Plan")), emptyList(), emptyList()))
    }

    @Test
    fun `a map and a file that still match need nothing`() {
        val steps = planSync(folder, listOf(map("plan", "# Plan", file = "Plan.md")), listOf(file("Plan.md", null)), emptyList())
        assertEquals(emptyList<SyncStep>(), steps)
    }

    @Test
    fun `a map changed here is written to its file`() {
        val steps = planSync(folder, listOf(map("plan", "# Plan\n- New", file = "Plan.md", matched = "# Plan")), listOf(file("Plan.md", null)), emptyList())
        assertEquals(listOf(SyncStep.Write("plan", "Plan.md")), steps)
    }

    @Test
    fun `a file changed in the folder changes its map`() {
        val there = file("Plan.md", "# Plan\n- From the laptop")
        val steps = planSync(folder, listOf(map("plan", "# Plan", file = "Plan.md")), listOf(there), emptyList())
        assertEquals(listOf(SyncStep.Take("plan", there, expected = digest("# Plan"))), steps)
    }

    @Test
    fun `a map changed on both sides is kept twice`() {
        val there = file("Plan.md", "# Plan\n- From the laptop")
        val steps = planSync(folder, listOf(map("plan", "# Plan\n- From the phone", file = "Plan.md", matched = "# Plan")), listOf(there), emptyList())
        assertEquals(listOf(SyncStep.KeepBoth("plan", there)), steps)
    }

    @Test
    fun `the same change on both sides only catches the record up`() {
        val there = file("Plan.md", "# Plan\n- Same")
        val steps = planSync(folder, listOf(map("plan", "# Plan\n- Same", file = "Plan.md", matched = "# Plan")), listOf(there), emptyList())
        assertEquals(listOf(SyncStep.Link("plan", there)), steps)
    }

    @Test
    fun `a file gone from the folder is only noted the first time`() {
        val maps = listOf(map("plan", "# Plan", file = "Plan.md"), map("trip", "# Trip", file = "Trip.md"))
        val record = maps.first().record!!
        assertEquals(listOf(SyncStep.Missing("plan", record)), planSync(folder, maps, listOf(file("Trip.md", null)), emptyList(), now = now))
    }

    @Test
    fun `a file still gone a while later sends its unchanged map to the trash`() {
        val missed = now - MISSING_CONFIRMATION
        val maps = listOf(map("plan", "# Plan", file = "Plan.md", missingSince = missed), map("trip", "# Trip", file = "Trip.md"))
        assertEquals(listOf(SyncStep.Remove("plan")), planSync(folder, maps, listOf(file("Trip.md", null)), emptyList(), now = now))
    }

    @Test
    fun `a file gone again only moments later waits for a later sync`() {
        val maps = listOf(map("plan", "# Plan", file = "Plan.md", missingSince = now - 4_000), map("trip", "# Trip", file = "Trip.md"))
        assertEquals(emptyList<SyncStep>(), planSync(folder, maps, listOf(file("Trip.md", null)), emptyList(), now = now))
    }

    @Test
    fun `a file back after a sync missed it is found again`() {
        val maps = listOf(map("plan", "# Plan", file = "Plan.md", missingSince = now - 60_000))
        val record = maps.single().record!!
        assertEquals(listOf(SyncStep.Found("plan", record)), planSync(folder, maps, listOf(file("Plan.md", null)), emptyList(), now = now))
    }

    @Test
    fun `a file that couldn't be read is left alone, and so is its map`() {
        val unreadable = RemoteFile(FolderFile("Plan.md", 2, 2), null, unreadable = true)
        val maps = listOf(map("plan", "# Plan\n- New", file = "Plan.md", matched = "# Plan"), map("trip", "# Trip", file = "Trip.md"))
        assertEquals(emptyList<SyncStep>(), planSync(folder, maps, listOf(unreadable, file("Trip.md", null)), emptyList(), now = now))
        // Nor does a new file that couldn't be read become a map yet.
        val new = RemoteFile(FolderFile("Ideas.md", 2, 2), null, unreadable = true)
        assertEquals(emptyList<SyncStep>(), planSync(folder, listOf(map("trip", "# Trip", file = "Trip.md")), listOf(new, file("Trip.md", null)), emptyList(), now = now))
    }

    @Test
    fun `a map whose text couldn't be read keeps its file, untouched`() {
        val maps = listOf(map("plan", "", file = "Plan.md", matched = "# Plan", readable = false), map("trip", "# Trip", file = "Trip.md"))
        val changed = file("Plan.md", "# Plan\n- From the laptop")
        assertEquals(emptyList<SyncStep>(), planSync(folder, maps, listOf(changed, file("Trip.md", null)), emptyList(), now = now))
        assertEquals(emptyList<SyncStep>(), planSync(folder, maps, listOf(file("Trip.md", null)), emptyList(), now = now))
    }

    @Test
    fun `a deleted map's file that couldn't be read is deleted later, not forgotten`() {
        val deleted = SyncRecord(folder, "Plan.md", digest("# Plan"), 1, 1)
        val unreadable = RemoteFile(FolderFile("Plan.md", 2, 2), null, unreadable = true)
        assertEquals(emptyList<SyncStep>(), planSync(folder, emptyList(), listOf(unreadable), listOf(deleted), now = now))
    }

    @Test
    fun `a map open on a screen is written again rather than deleted`() {
        val maps = listOf(map("plan", "# Plan", file = "Plan.md"), map("trip", "# Trip", file = "Trip.md"))
        val steps = planSync(folder, maps, listOf(file("Trip.md", null)), emptyList(), onScreen = setOf("plan"))
        assertEquals(listOf(SyncStep.Write("plan", null)), steps)
    }

    @Test
    fun `a map changed here since its file was deleted is written again`() {
        val maps = listOf(map("plan", "# Plan\n- New", file = "Plan.md", matched = "# Plan"), map("trip", "# Trip", file = "Trip.md"))
        assertEquals(listOf(SyncStep.Write("plan", null)), planSync(folder, maps, listOf(file("Trip.md", null)), emptyList()))
    }

    @Test
    fun `an emptied folder gets the maps written again instead of deleting them`() {
        val maps = listOf(map("plan", "# Plan", file = "Plan.md"), map("trip", "# Trip", file = "Trip.md"))
        assertEquals(listOf(SyncStep.Write("plan", null), SyncStep.Write("trip", null)), planSync(folder, maps, emptyList(), emptyList()))
    }

    @Test
    fun `a folder with none of the maps' files is taken as another folder, not as deletions`() {
        val maps = listOf(map("plan", "# Plan", file = "Plan.md"), map("trip", "# Trip", file = "Trip.md"))
        val other = file("Notes.md", "# Notes")
        val steps = planSync(folder, maps, listOf(other), emptyList())
        assertEquals(listOf(SyncStep.Write("plan", null), SyncStep.Write("trip", null), SyncStep.Add(other)), steps)
    }

    @Test
    fun `a file renamed in the folder keeps its map`() {
        val renamed = file("Bakery plan.md", "# Plan")
        val maps = listOf(map("plan", "# Plan", file = "Plan.md"), map("trip", "# Trip", file = "Trip.md"))
        val steps = planSync(folder, maps, listOf(renamed, file("Trip.md", null)), emptyList())
        assertEquals(listOf(SyncStep.Link("plan", renamed)), steps)
    }

    @Test
    fun `a file new in the folder becomes a map`() {
        val new = file("Ideas.md", "# Ideas")
        val steps = planSync(folder, listOf(map("plan", "# Plan", file = "Plan.md")), listOf(file("Plan.md", null), new), emptyList())
        assertEquals(listOf(SyncStep.Add(new)), steps)
    }

    @Test
    fun `a map already in the folder from another device is followed, not copied`() {
        val welcome = file("Welcome to Orbit.md", "# Welcome to Orbit")
        val steps = planSync(folder, listOf(map("welcome", "# Welcome to Orbit")), listOf(welcome), emptyList())
        assertEquals(listOf(SyncStep.Link("welcome", welcome)), steps)
    }

    @Test
    fun `a map kept in another folder starts over in this one`() {
        val steps = planSync(folder, listOf(map("plan", "# Plan", file = "Plan.md", folder = "content://old")), listOf(file("Plan.md", "# Plan, another")), emptyList())
        assertEquals(listOf(SyncStep.Write("plan", null), SyncStep.Add(file("Plan.md", "# Plan, another"))), steps)
    }

    @Test
    fun `a map deleted here deletes its file, unless the file changed since`() {
        val deleted = SyncRecord(folder, "Plan.md", digest("# Plan"), 1, 1)
        assertEquals(listOf(SyncStep.DeleteFile(deleted)), planSync(folder, emptyList(), listOf(file("Plan.md", null)), listOf(deleted)))

        val changed = file("Plan.md", "# Plan\n- From the laptop")
        assertEquals(listOf(SyncStep.Forget(deleted), SyncStep.Add(changed)), planSync(folder, emptyList(), listOf(changed), listOf(deleted)))
        assertEquals(listOf(SyncStep.Forget(deleted)), planSync(folder, emptyList(), emptyList(), listOf(deleted)))
    }

    @Test
    fun `file names follow titles, without what files can't hold, and never repeat`() {
        assertEquals("Bakery relaunch.md", MapText.fileName("Bakery relaunch", emptyList(), words))
        assertEquals("Q3 plan v2.md", MapText.fileName("Q3: plan/v2", emptyList(), words))
        assertEquals("Untitled map.md", MapText.fileName("  ...  ", emptyList(), words))
        assertEquals("Plan (3).md", MapText.fileName("Plan", listOf("plan.md", "Plan (2).md"), words))
    }

    @Test
    fun `pictures are found by their place in the media folder`() {
        val markdown = "# Trip\n- Beach ![](media/picture-abc.jpg)\n- <!-- orbit image: media/map_2.png -->\n- media/../secret"
        assertEquals(setOf("picture-abc.jpg", "map_2.png"), picturesIn(markdown))
    }
}
