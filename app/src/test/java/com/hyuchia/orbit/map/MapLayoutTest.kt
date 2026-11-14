package com.hyuchia.orbit.map

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.hyuchia.orbit.page.MapNode
import com.hyuchia.orbit.page.MapState
import com.hyuchia.orbit.page.Media
import com.hyuchia.orbit.ui.MinimumMapWidth
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
    fun `a panel is over the map as a sheet only while one is open and there's no room for it beside the map`() {
        val searching = MapUi(panel = Panel.Search)

        listOf(phone, tablet).forEach { screen ->
            assertTrue("$screen", screen.layout(searching).panelOver)
            assertTrue("$screen", screen.layout(editing).panelOver)
            // Picking a result closes the search at once: no panel, so no sheet's top counts (rememberSheetTop).
            assertFalse("$screen", screen.layout(nothingOpen).panelOver)
            assertFalse("$screen", screen.layout(MapUi(panel = Panel.Menu("porto", 10f, 20f))).panelOver)
        }

        listOf(phoneOnItsSide, tabletOnItsSide).forEach { screen ->
            assertFalse("$screen", screen.layout(searching).panelOver)
            assertFalse("$screen", screen.layout(editing).panelOver)
            // Exporting stays a sheet over the map wherever it opens.
            assertTrue("$screen", screen.layout(MapUi(panel = Panel.Export)).panelOver)
        }
    }

    @Test
    fun `the map makes room for a keyboard that's its own, or for a panel that lifts it, on phones and tablets both ways up`() {
        val menu = MapUi(panel = Panel.Menu("porto", 120f, 300f))
        val lifting = listOf(editing, MapUi(panel = Panel.Details("porto")), MapUi(panel = Panel.Outline), MapUi(panel = Panel.Style), MapUi(panel = Panel.History))

        listOf(phone, phoneOnItsSide, tablet, tabletOnItsSide).forEach { screen ->
            // Typing on the map, in a planet or the + in an orbit, with an idea's menu open at most.
            assertTrue(screen.layout(nothingOpen).roomForKeyboard)
            assertTrue(screen.layout(menu).roomForKeyboard)
            // A panel that lifts the map over it, or stands beside it, keeps the idea it's about in view above the keyboard.
            lifting.forEach { ui -> assertTrue("${ui.panel} on $screen", screen.layout(ui).roomForKeyboard) }
        }
    }

    @Test
    fun `the map makes no room for a keyboard that's another field's, on phones and tablets both ways up`() {
        val searching = MapUi(panel = Panel.Search)
        val exporting = MapUi(panel = Panel.Export)
        val source = MapUi(source = SourceState("# Cities"))
        val sketching = MapUi(sketch = SketchUi("porto", SketchPhase.Describing))

        listOf(phone, phoneOnItsSide, tablet, tabletOnItsSide).forEach { screen ->
            // The search keeps its own field above the keyboard, as a sheet over the map or beside it.
            assertFalse("search on $screen", screen.layout(searching).roomForKeyboard)
            assertFalse("export on $screen", screen.layout(exporting).roomForKeyboard)
            // The map's text has its own room for the keyboard, and a sketch's description rides over it.
            assertFalse("the map's text on $screen", screen.layout(source).roomForKeyboard)
            assertFalse("a sketch on $screen", screen.layout(sketching).roomForKeyboard)
            // A sheet or dialog with a field of its own: an idea's icon being picked, the map's tags.
            assertFalse("a field over the map on $screen", screen.layout(nothingOpen, fieldOver = true).roomForKeyboard)
            // Over a panel that lifts the map too: the icon's search, opened from the editor.
            assertFalse("a field over the editor on $screen", screen.layout(editing, fieldOver = true).roomForKeyboard)
        }
    }

    @Test
    fun `the map keeps its size until another field's keyboard has gone`() {
        // The search opens, and the keyboard comes up for its field.
        var elsewhere = keyboardElsewhere(before = false, roomForKeyboard = false, keyboardUp = false)
        assertFalse(elsewhere)
        elsewhere = keyboardElsewhere(elsewhere, roomForKeyboard = false, keyboardUp = true)
        assertTrue(elsewhere)

        // An idea found is picked, with the keyboard still up: the map has room for a keyboard again, but not for this one.
        elsewhere = keyboardElsewhere(elsewhere, roomForKeyboard = true, keyboardUp = true)
        assertTrue(elsewhere)

        // Once it has gone, the next keyboard is the map's, for typing on a planet.
        elsewhere = keyboardElsewhere(elsewhere, roomForKeyboard = true, keyboardUp = false)
        assertFalse(elsewhere)
        assertFalse(keyboardElsewhere(elsewhere, roomForKeyboard = true, keyboardUp = true))
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
    fun `beside a panel or its text, the map keeps a phone's width, on a window just wide enough for them`() {
        val window = Screen(760.dp, 500.dp)

        for (ui in listOf(editing, MapUi(panel = Panel.Outline), MapUi(source = SourceState("# Cities")))) {
            val layout = window.layout(ui)

            assertTrue(layout.panelBeside || layout.sourceBeside)
            assertTrue("The map keeps ${window.width - layout.beside} beside what's open", window.width - layout.beside >= MinimumMapWidth)
        }

        // The text takes what the map leaves: narrower than its own width, as wide as a panel at least.
        assertEquals(760.dp - 1.dp - MinimumMapWidth, window.layout(MapUi(source = SourceState("# Cities"))).sourceWidth)
        assertEquals(SidePanelWidth, Screen(741.dp, 500.dp).layout(MapUi(source = SourceState("# Cities"))).sourceWidth)
        // A window too narrow to leave the map its width beside a panel has the panel over the map instead.
        assertFalse(Screen(740.dp, 500.dp).layout(editing).panelBeside)
    }

    @Test
    fun `the toolbar keeps Style and layout only while the map beside an open panel has room for it`() {
        val outline = MapUi(panel = Panel.Outline)

        // With nothing open the window's width is the map's, and it has room; beside the outline it hasn't.
        assertTrue(Screen(760.dp, 500.dp).layout(nothingOpen).styleInToolbar)
        assertFalse(Screen(760.dp, 500.dp).layout(outline).styleInToolbar)
        // On a tablet on its side, the map beside the outline still has room.
        assertTrue(tabletOnItsSide.layout(outline).styleInToolbar)
    }

    @Test
    fun `the top bar has room for Redo and the lock on a tablet, and not on a phone or a phone's width of map beside the list`() {
        assertFalse(phone.layout(nothingOpen).roomyTopBar)
        assertFalse(phoneOnItsSide.layout(nothingOpen).roomyTopBar)
        assertTrue(tablet.layout(nothingOpen).roomyTopBar)
        assertTrue(tabletOnItsSide.layout(nothingOpen).roomyTopBar)
        // A tablet upright, the map beside the list.
        assertFalse(Screen(416.dp, 1232.dp).layout(nothingOpen).roomyTopBar)
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
        fun layout(ui: MapUi, fieldOver: Boolean = false): MapLayout = mapLayout(width, height, ui, fieldOver)

        override fun toString() = "a screen $width by $height"
    }
}
