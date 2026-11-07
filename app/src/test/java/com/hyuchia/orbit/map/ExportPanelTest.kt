package com.hyuchia.orbit.map

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PhoneAndroid
import com.hyuchia.orbit.FileWords
import com.hyuchia.orbit.data.Uncleaned
import com.hyuchia.orbit.page.PrintRequest
import com.hyuchia.orbit.page.ViewMode
import com.hyuchia.orbit.ui.uncleanedLines
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

/** What the export panel asks for and says, away from its screen. */
class ExportPanelTest {
    private val words = FileWords(Locale.US)

    @Test
    fun `as it opens, a picture shows the map as it opens, the whole map for one that opens on it`() {
        // A map opens on its center unless its own view says the whole map, as the Style panel sets it.
        assertEquals(ViewMode.FOCUS, framedView(Framing.Opening, opensIn = null))
        assertEquals(ViewMode.FOCUS, framedView(Framing.Opening, opensIn = ViewMode.FOCUS))
        assertEquals(ViewMode.OVERVIEW, framedView(Framing.Opening, opensIn = ViewMode.OVERVIEW))
        assertEquals(ViewMode.OVERVIEW, framedView(Framing.Whole, opensIn = null))

        // On paper too; this view stays the view on screen, on the idea in focus.
        assertEquals(PrintRequest(view = ViewMode.OVERVIEW), printRequest(Framing.Opening, ViewMode.FOCUS, focus = "pack", opensIn = ViewMode.OVERVIEW))
        assertEquals(PrintRequest(view = ViewMode.FOCUS), printRequest(Framing.Opening, ViewMode.OVERVIEW, focus = "pack", opensIn = null))
        assertEquals(PrintRequest(view = ViewMode.OVERVIEW), printRequest(Framing.Whole, ViewMode.FOCUS, focus = "pack", opensIn = null))
        assertEquals(PrintRequest(view = ViewMode.FOCUS, focus = "pack"), printRequest(Framing.View, ViewMode.FOCUS, focus = "pack", opensIn = ViewMode.OVERVIEW))
    }

    @Test
    fun `a sound or motion chosen to go as it is but past a page's room isn't named as going`() {
        val uncleaned = Uncleaned(
            pictures = setOf("picture-1.jpg"),
            videos = setOf("video-1.mp4"),
            sounds = setOf("sound-big.wav", "sound-small.wav"),
            motions = setOf("picture-1-motion.mp4"),
        )
        val chosen = uncleaned.names

        // The page has room for the small sound alone, beside a memo that goes clean, and for no motion: the big
        // sound and the motion stay on this device with the others past its room, and the page's row counts them there.
        val named = uncleaned.named(Format.WebPage, chosen, sounds = setOf("sound-small.wav", "memo-clean.mp4"), motions = emptySet())
        assertEquals(Uncleaned(pictures = setOf("picture-1.jpg"), sounds = setOf("sound-small.wav")), named)
        assertEquals(
            listOf("A picture and a sound go as they are, with where they were taken or recorded."),
            uncleanedLines(named, chosen, words, Icons.Outlined.PhoneAndroid).map { it.text },
        )

        // Not chosen, they stay for what can't be taken out of them, as in every way out.
        val unchosen = uncleaned.named(Format.WebPage, emptySet(), sounds = emptySet(), motions = emptySet())
        assertEquals(uncleaned.inPage, unchosen)
        assertEquals(
            listOf("A live photo and 2 sounds stay on this device: Orbit can't take out where they were taken or recorded."),
            uncleanedLines(unchosen, emptySet(), words, Icons.Outlined.PhoneAndroid).map { it.text },
        )

        // A zip has room for all of them, and a drawing or paper carries only pictures.
        assertEquals(uncleaned, uncleaned.named(Format.Zip, chosen, emptySet(), emptySet()))
        assertEquals(Uncleaned(pictures = setOf("picture-1.jpg")), uncleaned.named(Format.Pdf, chosen, emptySet(), emptySet()))
    }
}
