package com.hyuchia.orbit.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** When a live photo's motion plays in the viewer, and when it ends. */
class LivePhotoPlayTest {
    @Test
    fun `it plays once without sound as its page comes up fitted, and never zoomed in`() {
        val live = LivePhotoPlay()

        live.arrive(current = true, moving = true, shown = true, fitted = true)
        assertEquals(false, live.play?.sound)

        // Back on a page left zoomed in: it stays still.
        live.arrive(current = true, moving = true, shown = true, fitted = false)
        assertNull(live.play)

        // Its mark, or held, plays nothing zoomed in either.
        live.press(fitted = false)
        live.hold(fitted = false)
        assertNull(live.play)
        assertFalse(live.held)
    }

    @Test
    fun `zooming in on one playing ends it there, held or not`() {
        val live = LivePhotoPlay()

        live.press(fitted = true)
        assertEquals(true, live.play?.sound)
        live.stop()
        assertNull(live.play)

        live.hold(fitted = true)
        assertTrue(live.held)
        live.stop()
        assertNull(live.play)
        assertFalse(live.held)
    }

    @Test
    fun `held, it keeps playing through its end until the finger lifts, or Orbit leaves the screen`() {
        val live = LivePhotoPlay()

        live.hold(fitted = true)
        live.ended()
        assertTrue(live.play?.held == true)

        live.lift()
        assertNull(live.play)

        // Held as the screen turns off: it ends, and nothing of it is kept for when Orbit is back.
        live.hold(fitted = true)
        live.stop()
        assertNull(live.play)
        assertFalse(live.held)
    }
}
