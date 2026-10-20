package com.hyuchia.orbit.map

import com.hyuchia.orbit.page.MapNode
import com.hyuchia.orbit.page.MapState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class EditorFieldTest {
    @Test
    fun aChangeFromOutsideShowsInTheField() {
        // An undo took the idea's new text back.
        assertEquals("Porto", followedText(value = "Porto", text = "New York", committed = "New York"))
        assertEquals("", followedText(value = "", text = "A note", committed = "A note"))
    }

    @Test
    fun theFieldsOwnSaveTidiedIsNoChange() {
        // "New " was saved as "New": the space stays in the field, for the next word.
        assertNull(followedText(value = "New", text = "New ", committed = "New "))
        assertNull(followedText(value = "Two lines", text = "Two\nlines", committed = "Two\nlines"))
        assertNull(followedText(value = "", text = "  ", committed = "  "))
    }

    @Test
    fun typingWaitingToBeSavedStays() {
        assertNull(followedText(value = "Porto", text = "New Yo", committed = "New "))
    }

    @Test
    fun aPanelOnAnIdeaTheMapNoLongerHasCloses() {
        val map = MapState("Cities", listOf(MapNode("cities", "Cities"), MapNode("porto", "Porto", parentId = "cities")))

        // An undo took back the text that named the idea "new-york".
        assertNull(MapUi(map = map, panel = Panel.Editor("new-york", opened = 3)).withoutPanelOnGoneIdea().panel)
        assertNull(MapUi(map = map, panel = Panel.Details("new-york")).withoutPanelOnGoneIdea().panel)
        assertEquals(Panel.Editor("porto", opened = 3), MapUi(map = map, panel = Panel.Editor("porto", opened = 3)).withoutPanelOnGoneIdea().panel)
        assertEquals(Panel.Outline, MapUi(map = map, panel = Panel.Outline).withoutPanelOnGoneIdea().panel)
    }
}
