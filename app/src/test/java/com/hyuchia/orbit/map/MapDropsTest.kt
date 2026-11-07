package com.hyuchia.orbit.map

import com.hyuchia.orbit.FileWords
import com.hyuchia.orbit.data.AddedMotion
import com.hyuchia.orbit.data.AddedPicture
import com.hyuchia.orbit.data.DropKind
import com.hyuchia.orbit.data.DroppedItem
import com.hyuchia.orbit.data.ImportResult
import com.hyuchia.orbit.data.MapBundle
import com.hyuchia.orbit.page.MapNode
import com.hyuchia.orbit.study.Study
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

class MapDropsTest {
    private val picture = DroppedItem.Picture("content://photos/1", "Tram.jpg")
    private val video = DroppedItem.Video("content://videos/2", "Walk.mp4")
    private val sound = DroppedItem.Sound("content://sounds/5", "Fado.m4a")
    private val text = DroppedItem.Text("Milk\nEggs")
    private val map = DroppedItem.MapFile("content://files/3", "Trip.opml")

    private val trip = listOf(
        MapNode("trip", "Trip", childCount = 2),
        MapNode("food", "Food", parentId = "trip", index = 0, childCount = 2, icon = "🍽"),
        MapNode("bakeries", "Bakeries", parentId = "food", index = 0, childCount = 1),
        MapNode("nata", "Pastéis de nata", parentId = "bakeries", index = 0),
        MapNode("sardines", "Sardines", parentId = "food", index = 1),
        MapNode("museums", "Museums", parentId = "trip", index = 1),
    )

    @Test
    fun `map files open as maps, whatever came with them, and the hint says so before they're let go`() {
        assertEquals(DropPlan.OpenMaps(listOf(map)), planDrop(listOf(text, map, picture)))
        assertEquals(DropPlan.OpenMaps(listOf(map)), planDrop(listOf(text, map)))
        // A drag of maps with text, or with pictures, videos or sounds, says it opens them as maps: what letting go does.
        assertEquals(DropOffer.Maps, dropOffer(setOf(DropKind.Maps, DropKind.Ideas)))
        assertEquals(DropOffer.Maps, dropOffer(setOf(DropKind.Ideas, DropKind.Maps, DropKind.Pictures)))
        assertEquals(DropOffer.Maps, dropOffer(setOf(DropKind.Sounds, DropKind.Videos, DropKind.Maps)))
    }

    @Test
    fun `one picture or video becomes the idea's, and several become ideas of their own`() {
        assertEquals(DropPlan.Media(picture), planDrop(listOf(picture)))
        assertEquals(DropPlan.Media(video), planDrop(listOf(video)))
        assertEquals(DropPlan.NewIdeas(listOf(picture, video)), planDrop(listOf(picture, video)))
    }

    @Test
    fun `one sound becomes the idea's, and several, with pictures and videos too, become ideas of their own`() {
        assertEquals(DropPlan.Media(sound), planDrop(listOf(sound)))
        assertEquals(DropPlan.NewIdeas(listOf(sound, picture)), planDrop(listOf(sound, picture)))
        assertEquals(DropPlan.OpenMaps(listOf(map)), planDrop(listOf(sound, map)))
        assertEquals(DropOffer.Sounds, dropOffer(setOf(DropKind.Sounds)))
        assertEquals(DropOffer.Pictures, dropOffer(setOf(DropKind.Sounds, DropKind.Pictures)))
    }

    @Test
    fun `a sound joins its idea as one added from the phone does, with its caption when it has one`() {
        assertEquals("""{"src":"media/sound-mf2k1x.mp4","caption":"Fado"}""", soundAttribute("media/sound-mf2k1x.mp4", "Fado").toString())
        assertEquals("""{"src":"media/sound-mf2k1x.mp3"}""", soundAttribute("media/sound-mf2k1x.mp3", null).toString())
    }

    @Test
    fun `a new caption, description or motion goes to the page alone, for the rest of the file's fields to stay as they are`() {
        assertEquals("""{"caption":"Mist before seven"}""", mediaChange("caption", "Mist before seven").toString())
        assertEquals("""{"alt":null}""", mediaChange("alt", null).toString())
        assertEquals("""{"motion":{"src":"media/lake-motion.mp4","still":0.8}}""", motionChange(AddedMotion("media/lake-motion.mp4", 0.8)).toString())
    }

    @Test
    fun `an iPhone's still and movie joined are one live photo, the idea's alone, and an idea of its own among others`() {
        val live = DroppedItem.LivePhoto(DroppedItem.Picture("content://photos/7", "IMG_0911.HEIC"), DroppedItem.Video("content://videos/8", "IMG_0911.MOV"))
        assertEquals(DropPlan.Media(live), planDrop(listOf(live)))
        assertEquals(DropPlan.NewIdeas(listOf(live, picture)), planDrop(listOf(live, picture)))
    }

    @Test
    fun `a picture keeps a live photo's motion with it as the page holds one, with where its still was taken when known`() {
        assertEquals("""{"src":"media/picture-1.jpg"}""", pictureAttribute(AddedPicture("media/picture-1.jpg")).toString())
        assertEquals(
            """{"src":"media/picture-1.jpg","motion":{"src":"media/picture-1-motion.mp4"}}""",
            pictureAttribute(AddedPicture("media/picture-1.jpg", AddedMotion("media/picture-1-motion.mp4"))).toString(),
        )
        assertEquals(
            """{"src":"media/picture-1.jpg","motion":{"src":"media/picture-1-motion.mp4","still":0.733}}""",
            pictureAttribute(AddedPicture("media/picture-1.jpg", AddedMotion("media/picture-1-motion.mp4", 0.733333))).toString(),
        )
    }

    @Test
    fun `text that comes with a picture, such as its address from a browser, stays out`() {
        assertEquals(DropPlan.Media(picture), planDrop(listOf(picture, DroppedItem.Text("https://example.com/tram.jpg"))))
    }

    @Test
    fun `text and text files become ideas, and nothing usable is nothing`() {
        val file = DroppedItem.TextFile("content://files/4", "Groceries.txt")
        assertEquals(DropPlan.Ideas(listOf(text, file)), planDrop(listOf(text, file)))
        assertEquals(DropPlan.Nothing, planDrop(emptyList()))
    }

    @Test
    fun `a private map behind its lock takes no drops, as a map not ready, making a path, studied or at an earlier version takes none`() {
        val ready = MapUi(ready = true)

        assertTrue(takesDrops(ready, locked = false))
        assertFalse(takesDrops(ready, locked = true))
        assertFalse(takesDrops(MapUi(ready = false), locked = false))
        assertFalse(takesDrops(ready.copy(pathMaking = true), locked = false))
        assertFalse(takesDrops(ready.copy(study = Study(start = "top", queue = listOf("card"), total = 1)), locked = false))
        assertFalse(takesDrops(ready.copy(history = HistoryUi(showing = 1_760_000_000_000)), locked = false))
        // The history open on the map as it is now: drops are the map's.
        assertTrue(takesDrops(ready.copy(history = HistoryUi()), locked = false))
    }

    @Test
    fun `a map presented or studied takes no drop or paste, and no press that would lead to an edit`() {
        val ready = MapUi(ready = true)
        // A presentation's clock starts as the presentation does: here, at 0.
        val presenting = ready.copy(presentation = Presentation(listOf("top"), 0, startedAt = 0L))
        val studying = ready.copy(study = Study(start = "top", queue = listOf("card"), total = 1))

        assertFalse(takesDrops(presenting, locked = false))
        assertFalse(presenting.takesEdits)
        assertFalse(studying.takesEdits)
        assertFalse(ready.copy(pathMaking = true).takesEdits)
        assertFalse(ready.copy(readOnly = ReadOnly.Newer).takesEdits)
        assertTrue(ready.takesEdits)
    }

    @Test
    fun `the hint says what a drag offers the map`() {
        assertEquals(DropOffer.Ideas, dropOffer(setOf(DropKind.Ideas)))
        assertEquals(DropOffer.Pictures, dropOffer(setOf(DropKind.Pictures, DropKind.Ideas)))
        assertEquals(DropOffer.Videos, dropOffer(setOf(DropKind.Videos)))
        assertEquals(DropOffer.Maps, dropOffer(setOf(DropKind.Maps)))
        assertNull(dropOffer(emptySet()))
    }

    @Test
    fun `an idea can't go inside itself or anything under it`() {
        val nodes = trip.associateBy { it.id }
        assertTrue(isWithin(nodes, "nata", "food"))
        assertTrue(isWithin(nodes, "food", "food"))
        assertFalse(isWithin(nodes, "museums", "food"))
        assertFalse(isWithin(nodes, "trip", "food"))
    }

    @Test
    fun `an idea dragged out is its outline, as copying it gives`() {
        assertEquals("🍽 Food\n- Bakeries\n  - Pastéis de nata\n- Sardines", ideaOutline(trip, "food"))
        assertEquals("Museums", ideaOutline(trip, "museums"))
        assertEquals("", ideaOutline(trip, "gone"))
    }

    @Test
    fun `an outline deeper than a call for each level could go is written`() {
        val chain = listOf(MapNode("top", "Top")) + (1..3_000).map { MapNode("step-$it", "Step $it", parentId = if (it == 1) "top" else "step-${it - 1}") }
        val lines = ideaOutline(chain, "top").lines()
        assertEquals(3_001, lines.size)
        assertEquals("${"  ".repeat(2_999)}- Step 3000", lines.last())
    }

    @Test
    fun `several map files are one result, with a line for each that didn't come in`() {
        val words = FileWords(Locale.US)
        val results = listOf(
            "Trip.md" to ImportResult.Imported("trip", "Trip"),
            "Plan.xmind" to ImportResult.ImportedMany(added = 2, alreadyHere = 1),
            "Ticket.zip" to ImportResult.Failed("There's no map in that zip."),
        )

        assertEquals(
            ImportResult.ImportedMany(added = 3, alreadyHere = 1, notes = listOf("Ticket.zip: There's no map in that zip.")),
            droppedMaps(results, words),
        )
        assertEquals(ImportResult.Imported("trip", "Trip"), droppedMaps(results.take(1), words))
        assertEquals(ImportResult.Failed("A.md: No\nB.md: No"), droppedMaps(listOf("A.md" to ImportResult.Failed("No"), "B.md" to ImportResult.Failed("No")), words))
        assertNull(droppedMaps(emptyList(), words))
    }

    private fun bundle(title: String) = MapBundle("# $title\n", title, pictures = emptyMap(), uuid = "uuid-$title", focus = null, view = null)

    @Test
    fun `files that are other versions of maps here are each asked about, before what the rest brought`() {
        val words = FileWords(Locale.US)
        val trip = ImportResult.AlreadyHere(bundle("Trip"), existing = "trip")
        val plan = ImportResult.AlreadyHere(bundle("Plan"), existing = "plan")
        val results = listOf(
            "Trip.zip" to trip,
            "Ideas.md" to ImportResult.Imported("ideas", "Ideas"),
            "Plan.zip" to plan,
            "Ticket.zip" to ImportResult.Failed("There's no map in that zip."),
        )

        // In the order they came, and not counted as here already: they may update what's here.
        assertEquals(
            ImportResult.SeveralFiles(listOf(trip, plan), ImportResult.ImportedMany(added = 1, alreadyHere = 0, notes = listOf("Ticket.zip: There's no map in that zip."))),
            droppedMaps(results, words),
        )
        // Questions alone have nothing said after them, and failures alone are a problem, as before.
        assertEquals(ImportResult.SeveralFiles(listOf(trip, plan), summary = null), droppedMaps(listOf("Trip.zip" to trip, "Plan.zip" to plan), words))
        assertEquals(
            ImportResult.SeveralFiles(listOf(plan), ImportResult.Failed("Ticket.zip: There's no map in that zip.")),
            droppedMaps(listOf("Plan.zip" to plan, "Ticket.zip" to ImportResult.Failed("There's no map in that zip.")), words),
        )
        // One file is asked about as it always was.
        assertEquals(trip, droppedMaps(listOf("Trip.zip" to trip), words))
    }

    @Test
    fun `private maps here already are counted apart, for a summary to leave out while they hide`() {
        val results = listOf(
            "All.zip" to ImportResult.ImportedMany(added = 1, alreadyHere = 3, privateAlreadyHere = 2),
            "More.zip" to ImportResult.ImportedMany(added = 0, alreadyHere = 2, privateAlreadyHere = 1),
        )

        assertEquals(ImportResult.ImportedMany(added = 1, alreadyHere = 5, privateAlreadyHere = 3), droppedMaps(results, FileWords(Locale.US)))
    }

    @Test
    fun `files that brought nothing at all say so`() {
        assertEquals(ImportResult.ImportedMany(added = 0, alreadyHere = 0), droppedMaps(List(2) { "Empty.zip" to ImportResult.ImportedMany(added = 0, alreadyHere = 0) }, FileWords(Locale.US)))
    }

    @Test
    fun `a file of another kind joins the idea with every file let go with it, and text stays out`() {
        val pdf = DroppedItem.File("content://files/9", "Ticket.pdf")
        val photo = DroppedItem.Picture("content://photos/1", "Tram.jpg")
        val notes = DroppedItem.TextFile("content://files/4", "Groceries.txt")

        assertEquals(DropPlan.Files(listOf(pdf)), planDrop(listOf(pdf)))
        assertEquals(DropPlan.Files(listOf(photo, pdf, notes)), planDrop(listOf(photo, pdf, DroppedItem.Text("https://example.com"), notes)))
        // Maps still open as maps, whatever comes with them.
        val map = DroppedItem.MapFile("content://files/5", "Trip.md")
        assertEquals(DropPlan.OpenMaps(listOf(map)), planDrop(listOf(pdf, map)))
        assertEquals(DropOffer.Files, dropOffer(setOf(DropKind.Files, DropKind.Pictures)))
        assertEquals(DropOffer.Maps, dropOffer(setOf(DropKind.Files, DropKind.Maps)))
    }
}
