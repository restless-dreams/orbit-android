package com.hyuchia.orbit.home

import com.hyuchia.orbit.FileWords
import com.hyuchia.orbit.R
import com.hyuchia.orbit.data.MapEditResult
import com.hyuchia.orbit.data.MapMetadata
import com.hyuchia.orbit.data.MapOutline
import com.hyuchia.orbit.data.MapSummary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Locale

/**
 * What Home says, and shows, when a tag renamed or taken out of every map stays on some of them, and when the page
 * couldn't rename, tag or duplicate a map.
 */
class TagRenameTest {
    private val english = FileWords(Locale.US)
    private val spanish = FileWords(Locale.forLanguageTag("es-MX"))
    private val portuguese = FileWords(Locale.forLanguageTag("pt-BR"))

    /** Why the page couldn't make a change, as the app's page says it in each language. */
    private fun tooOld(words: FileWords) = MapEditResult.PageFailed(words.string(R.string.page_webview_too_old))

    private fun stopped(words: FileWords) = MapEditResult.PageFailed(words.string(R.string.page_stopped))

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

    @Test
    fun `maps the page couldn't change keep the tag, and Home says why as the page did`() {
        assertEquals(
            "The tag stays on “Trip”. The page stopped.",
            keptTagMessage(listOf(map("Trip") to stopped(english)), english, readOnly = false),
        )
        // The maps after it weren't tried: they keep the tag for the same reason, which comes before the others.
        assertEquals(
            "The tag stays on 3 maps. This device's WebView is too old to draw maps. Update it from your app store.",
            keptTagMessage(listOf(map("Trip") to MapEditResult.NotWritten, map("Plan") to tooOld(english), map("Home") to tooOld(english)), english, readOnly = false),
        )
        assertEquals(
            "The tag stays on 1 map. The page stopped.",
            keptTagMessage(listOf(map("Journal", private = true) to stopped(english)), english, readOnly = false),
        )
        assertEquals(
            "La etiqueta se queda en 2 mapas. El WebView de este dispositivo es demasiado antiguo para dibujar mapas. Actualízalo desde tu tienda de apps.",
            keptTagMessage(listOf(map("Viaje") to tooOld(spanish), map("Casa") to tooOld(spanish)), spanish, readOnly = false),
        )
        assertEquals(
            "A etiqueta continua em “Viagem”. A página parou.",
            keptTagMessage(listOf(map("Viagem") to stopped(portuguese)), portuguese, readOnly = false),
        )
    }

    @Test
    fun `a map the page couldn't rename, tag or duplicate says what didn't happen, then why`() {
        assertEquals(
            "Orbit couldn’t rename the map. This device's WebView is too old to draw maps. Update it from your app store.",
            notMadeMessage(R.string.home_map_not_renamed, tooOld(english), english),
        )
        assertEquals("Orbit couldn’t change the map’s tags. The page stopped.", notMadeMessage(R.string.home_map_tags_not_changed, stopped(english), english))
        assertEquals("Orbit couldn’t duplicate the map. The page stopped.", notMadeMessage(R.string.home_map_not_copied, stopped(english), english))
        assertEquals(
            "Orbit no pudo duplicar el mapa. El WebView de este dispositivo es demasiado antiguo para dibujar mapas. Actualízalo desde tu tienda de apps.",
            notMadeMessage(R.string.home_map_not_copied, tooOld(spanish), spanish),
        )
        assertEquals("O Orbit não conseguiu renomear o mapa. A página parou.", notMadeMessage(R.string.home_map_not_renamed, stopped(portuguese), portuguese))
    }
}
