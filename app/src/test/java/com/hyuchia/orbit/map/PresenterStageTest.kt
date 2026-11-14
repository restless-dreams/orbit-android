package com.hyuchia.orbit.map

import com.hyuchia.orbit.page.MapNode
import com.hyuchia.orbit.page.MapState
import com.hyuchia.orbit.page.Media
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The presenter's stage while the audience watches elsewhere: an idea with
 * only a sound gets its play button there, as one with a picture or a video
 * gets theirs, and a map with only sounds keeps the room the buttons need.
 */
class PresenterStageTest {
    private val fado = MapNode("fado", "Fado", parentId = "lisbon", depth = 1, audio = Media("media/sound-fado.mp3"))
    private val tram = MapNode("tram", "Tram", parentId = "lisbon", depth = 1, image = Media("media/tram.jpg"))
    private val ferry = MapNode("ferry", "Ferry", parentId = "lisbon", depth = 1, video = Media("media/ferry.mp4"))
    private val sintra = MapNode("sintra", "Sintra", parentId = "lisbon", depth = 1)
    private val lisbon = MapNode("lisbon", "Lisbon")

    @Test
    fun `an idea with only a sound has its button on the stage, as pictures and videos have theirs`() {
        assertTrue(hasStageButtons(fado))
        assertTrue(hasStageButtons(tram))
        assertTrue(hasStageButtons(ferry))
        assertFalse(hasStageButtons(sintra))
    }

    @Test
    fun `a map with only sounds keeps room for the stage's buttons under its preview`() {
        assertTrue(MapUi(map = MapState("Lisbon", listOf(lisbon, fado, sintra))).stageBarRoom)
        assertTrue(MapUi(map = MapState("Lisbon", listOf(lisbon, tram))).stageBarRoom)
        assertFalse(MapUi(map = MapState("Lisbon", listOf(lisbon, sintra))).stageBarRoom)
    }
}
