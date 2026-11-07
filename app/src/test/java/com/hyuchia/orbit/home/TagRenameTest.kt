package com.hyuchia.orbit.home

import com.hyuchia.orbit.FileWords
import com.hyuchia.orbit.data.MapEditResult
import com.hyuchia.orbit.data.MapMetadata
import com.hyuchia.orbit.data.MapOutline
import com.hyuchia.orbit.data.MapSummary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Locale

/** What Home says, and shows, when a tag renamed or taken out of every map stays on some of them. */
class TagRenameTest {
    private val english = FileWords(Locale.US)
    private val spanish = FileWords(Locale.forLanguageTag("es-MX"))

    private fun map(title: String, private: Boolean = false) = MapSummary(title.lowercase(), MapOutline(title, branches = 0, ideas = 0, tags = listOf("work")), 0, MapMetadata(isPrivate = private), "", "")

    @Test
    fun `the maps shown follow a renamed tag, unless some kept it`() {
        assertEquals("office", shownAfterRename("Work", "work", "office", stayed = false))
        // The maps that kept the old tag stay shown, rather than every map that changed.
        assertEquals("Work", shownAfterRename("Work", "work", "office", stayed = true))
        assertNull(shownAfterRename("work", "work", null, stayed = false))
        assertEquals("work", shownAfterRename("work", "work", null, stayed = true))
        // Another tag shown stays shown, whatever happened to this one.
        assertEquals("home", shownAfterRename("home", "work", "office", stayed = false))
        assertNull(shownAfterRename(null, "work", "office", stayed = true))
    }

    @Test
    fun `nothing is said when every map took the change`() {
        assertNull(keptTagMessage(emptyList(), english, readOnly = false))
    }

    @Test
    fun `one map that kept the tag is named, with why`() {
        assertEquals(
            "The tag stays on “Trip”: Orbit couldn’t save the change. Is this device’s storage full?",
            keptTagMessage(listOf(map("Trip") to MapEditResult.NotWritten), english, readOnly = false),
        )
        assertEquals(
            "The tag stays on “Trip”: Orbit couldn’t change it. Try again.",
            keptTagMessage(listOf(map("Trip") to MapEditResult.KeptChanging), english, readOnly = false),
        )
        assertEquals(
            "The tag stays on “Trip”: Orbit couldn’t change it. Try again.",
            keptTagMessage(listOf(map("Trip") to MapEditResult.Refused), english, readOnly = false),
        )
        assertEquals(
            "The tag stays on “Trip”, which Orbit can only show: a newer version made it, or it can’t read all of it.",
            keptTagMessage(listOf(map("Trip") to MapEditResult.Newer), english, readOnly = false),
        )
        assertEquals(
            "La etiqueta se queda en “Viaje”: Orbit no pudo guardar el cambio. ¿Está lleno el almacenamiento de este dispositivo?",
            keptTagMessage(listOf(map("Viaje") to MapEditResult.NotWritten), spanish, readOnly = false),
        )
    }

    @Test
    fun `several maps are counted, and a full storage says so first`() {
        assertEquals(
            "The tag stays on 2 maps: Orbit couldn’t save the change. Is this device’s storage full?",
            keptTagMessage(listOf(map("Trip") to MapEditResult.KeptChanging, map("Plan") to MapEditResult.NotWritten), english, readOnly = false),
        )
        assertEquals(
            "The tag stays on 2 maps: Orbit couldn’t change them. Try again.",
            keptTagMessage(listOf(map("Trip") to MapEditResult.KeptChanging, map("Plan") to MapEditResult.Newer), english, readOnly = false),
        )
        assertEquals(
            "The tag stays on 2 maps that Orbit can only show: a newer version made them, or it can’t read all of them.",
            keptTagMessage(listOf(map("Trip") to MapEditResult.Newer, map("Plan") to MapEditResult.Unreadable), english, readOnly = false),
        )
        assertEquals(
            "La etiqueta se queda en 3 mapas: Orbit no pudo cambiarlos. Vuelve a intentarlo.",
            keptTagMessage(listOf(map("Viaje") to MapEditResult.KeptChanging, map("Plan") to MapEditResult.Refused, map("Casa") to MapEditResult.Refused), spanish, readOnly = false),
        )
    }

    @Test
    fun `a private map is counted, never named`() {
        assertEquals(
            "The tag stays on 1 map: Orbit couldn’t change it. Try again.",
            keptTagMessage(listOf(map("Journal", private = true) to MapEditResult.KeptChanging), english, readOnly = false),
        )
    }

    @Test
    fun `maps a newer Orbit keeps say so`() {
        assertEquals(
            "A newer version of Orbit keeps these maps: update Orbit to change them.",
            keptTagMessage(listOf(map("Trip") to MapEditResult.Refused, map("Plan") to MapEditResult.Refused), english, readOnly = true),
        )
    }
}
