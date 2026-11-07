package com.hyuchia.orbit.map

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.hyuchia.orbit.page.MapNode
import com.hyuchia.orbit.page.MapOptions
import com.hyuchia.orbit.page.MapState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SketchingTest {
    private fun node(id: String, depth: Int, drawn: String?) = MapNode(
        id = id,
        text = id,
        parentId = if (depth == 0) null else "root",
        depth = depth,
        index = 0,
        childCount = 0,
        drawnColor = drawn,
    )

    private fun ui(vararg nodes: MapNode) = MapUi(
        map = MapState(title = "Map", nodes = nodes.toList(), options = MapOptions(), path = listOf("root"), mode = "focus"),
    )

    @Test
    fun `the pens are the map's own colors, as its center and branches are drawn, then black and white`() {
        val colors = Sketching.penColors(
            ui(
                node("root", 0, "#3f51b5"),
                node("food", 1, "#e91e63"),
                node("trips", 1, "#e91e63"),
                node("deep", 2, "#00ff00"),
                node("unread", 1, "var(--_orbit-color-3)"),
            ),
        )

        assertEquals(listOf(0xFF3F51B5.toInt(), 0xFFE91E63.toInt(), 0xFF1C1B1F.toInt(), 0xFFFFFFFF.toInt()), colors)
    }

    @Test
    fun `the pen starts dark on light paper and light on dark`() {
        val colors = listOf(0xFF3F51B5.toInt(), 0xFF1C1B1F.toInt(), 0xFFFFFFFF.toInt())
        assertEquals(0xFF1C1B1F.toInt(), Sketching.inkFor(0xFFFFF59D.toInt(), colors))
        assertEquals(0xFFFFFFFF.toInt(), Sketching.inkFor(0xFF1A237E.toInt(), colors))
    }

    @Test
    fun `the paper is the planet's color as the map draws it, opaque`() {
        assertEquals(0xFFE91E63.toInt(), Sketching.paperOf(node("food", 1, "#e91e6380")))
        assertEquals(0xFFAABBCC.toInt(), Sketching.paperOf(node("food", 1, "#abc")))
        // A color the page couldn't resolve gives a plain light paper.
        assertEquals(0xFFECE6F0.toInt(), Sketching.paperOf(node("food", 1, "var(--_orbit-color-1)")))
    }

    @Test
    fun `the map's dimming color is read as the page resolves it`() {
        assertEquals(Color(15 / 255f, 23 / 255f, 42 / 255f, 0.45f).toArgb(), Sketching.scrimColor("rgba(15, 23, 42, 0.45)"))
        assertEquals(Color(0f, 0f, 0f, 0.4f).toArgb(), Sketching.scrimColor("rgb(0 0 0 / 40%)"))
        assertEquals(Color(1f, 1f, 1f, 1f).toArgb(), Sketching.scrimColor("rgb(255, 255, 255)"))
        assertNull(Sketching.scrimColor("red"))
        assertNull(Sketching.scrimColor(null))
        assertNull(Sketching.scrimColor("rgba(1, 2)"))
    }
}
