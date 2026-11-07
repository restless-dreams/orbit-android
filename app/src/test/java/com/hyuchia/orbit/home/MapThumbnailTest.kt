package com.hyuchia.orbit.home

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.CompletableDeferred
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** A map's picture on its card follows the map while the card shows: its text, and the media it has as it arrives. */
@RunWith(AndroidJUnit4::class)
class MapThumbnailTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `a map's picture is drawn again when its key changes while its card shows`() {
        var key by mutableStateOf("text")
        val drawn = mutableListOf<String>()
        var shown: File? = null

        compose.setContent {
            val current = key
            shown = rememberThumbnail(current, cached = { null }, draw = { File("$current.webp").also { drawn += current } }).value
        }

        compose.waitForIdle()
        assertEquals(File("text.webp"), shown)

        // The picture its text points at arrived: Home's card shows the map with it.
        key = "text-and-its-picture"
        compose.waitForIdle()
        assertEquals(File("text-and-its-picture.webp"), shown)
        assertEquals(listOf("text", "text-and-its-picture"), drawn)
    }

    @Test
    fun `the picture drawn last stays until the new one is ready, and one drawn before shows at once`() {
        var key by mutableStateOf("text")
        val next = CompletableDeferred<File?>()
        var shown: File? = null

        compose.setContent {
            val current = key
            shown = rememberThumbnail(current, cached = { File("text.webp").takeIf { current == "text" } }, draw = { next.await() }).value
        }

        compose.waitForIdle()
        assertEquals(File("text.webp"), shown)

        key = "text-and-its-picture"
        compose.waitForIdle()
        // No glyph in between: the card keeps the map as it was until it's drawn as it is.
        assertEquals(File("text.webp"), shown)

        next.complete(File("text-and-its-picture.webp"))
        compose.waitForIdle()
        assertEquals(File("text-and-its-picture.webp"), shown)
    }
}
