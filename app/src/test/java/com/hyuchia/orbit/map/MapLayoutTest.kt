package com.hyuchia.orbit.map

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.hyuchia.orbit.page.MapNode
import com.hyuchia.orbit.page.MapState
import com.hyuchia.orbit.page.Media
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The map screen on a phone and a tablet, each both ways up, with what's open on it. */
class MapLayoutTest {
    private val phone = Screen(411.dp, 914.dp)
    private val phoneOnItsSide = Screen(914.dp, 411.dp)
    private val tablet = Screen(800.dp, 1280.dp)
    private val tabletOnItsSide = Screen(1280.dp, 800.dp)
    private val nothingOpen = MapUi()
    private val editing = MapUi(panel = Panel.Editor("porto"))

    @Test
    fun `a phone keeps the map's tools under it, and its panels over it`() {
        val layout = phone.layout(nothingOpen)

        assertFalse(layout.short)
        assertFalse(layout.wide)
        assertTrue(layout.crumbsOver)
        assertTrue(layout.styleInToolbar)
        assertFalse(layout.labeledPresent)
        assertEquals(0.dp, layout.beside)
    }

    @Test
    fun `a phone on its side has a rail, the breadcrumbs in the top bar, and room beside the map`() {
        val layout = phoneOnItsSide.layout(nothingOpen)

        assertTrue(layout.short)
        assertTrue(layout.wide)
        assertFalse(layout.crumbsOver)
        // Style and layout moves into the menu: the rail holds the idea's own buttons.
        assertFalse(layout.styleInToolbar)
        assertTrue(layout.labeledPresent)
    }

    @Test
    fun `a tablet on its side has room beside the map, and a tablet upright doesn't`() {
        assertTrue(tabletOnItsSide.layout(nothingOpen).wide)
        assertFalse(tabletOnItsSide.layout(nothingOpen).short)
        assertFalse(tablet.layout(nothingOpen).wide)
        assertFalse(tablet.layout(nothingOpen).short)
    }

    @Test
    fun `the editor lifts the map where there's no room beside it, and stands beside it where there is`() {
        listOf(phone, tablet).forEach { screen ->
            val layout = screen.layout(editing)

            assertTrue(layout.lifted)
            assertFalse(layout.panelBeside)
            assertEquals(0.dp, layout.beside)
        }

        listOf(phoneOnItsSide, tabletOnItsSide).forEach { screen ->
            val layout = screen.layout(editing)

            assertFalse(layout.lifted)
            assertTrue(layout.panelBeside)
            assertEquals(SidePanelWidth + 1.dp, layout.beside)
        }
    }

    @Test
    fun `search goes beside the map, or over it without lifting it`() {
        val searching = MapUi(panel = Panel.Search)

        assertTrue(tabletOnItsSide.layout(searching).panelBeside)
        assertFalse(tablet.layout(searching).panelBeside)
        assertFalse(tablet.layout(searching).lifted)
    }

    @Test
    fun `the map's text stands beside the map, wider than a panel, where there's room`() {
        val source = MapUi(source = SourceState("# Cities"))

        assertTrue(tabletOnItsSide.layout(source).sourceBeside)
        assertEquals(SidePanelWidth + SourceExtraWidth + 1.dp, tabletOnItsSide.layout(source).beside)
        assertFalse(phone.layout(source).sourceBeside)
        assertEquals(0.dp, phone.layout(source).beside)
    }

    @Test
    fun `making a path, the breadcrumbs float over the map on a short screen too`() {
        assertTrue(phoneOnItsSide.layout(MapUi(pathMaking = true)).crumbsOver)
    }

    @Test
    fun `a map with pictures and sounds needs more width for Style and layout in its toolbar`() {
        val map = MapState(
            "Cities",
            listOf(
                MapNode("cities", "Cities"),
                MapNode("porto", "Porto", parentId = "cities", image = Media("porto.jpg")),
                MapNode("lisbon", "Lisbon", parentId = "cities", audio = Media("fado.m4a")),
            ),
        )

        assertTrue(phone.layout(MapUi(map = MapState("Cities", listOf(MapNode("cities", "Cities"))))).styleInToolbar)
        assertFalse(phone.layout(MapUi(map = map)).styleInToolbar)
        assertTrue(tablet.layout(MapUi(map = map)).styleInToolbar)
    }

    private class Screen(val width: Dp, val height: Dp) {
        fun layout(ui: MapUi): MapLayout = mapLayout(width, height, ui)
    }
}
