package com.hyuchia.orbit.page

import android.os.Handler
import android.os.Looper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import java.time.Duration

/** What a page laid out for paper tells the printer once it's laid out and once its pictures are in (web/web-page.ts), and how long the printer waits for each. */
@RunWith(RobolectricTestRunner::class)
class MapPrinterTest {
    /** How long a printed page's pictures may take, as web/print.ts's `PICTURE_WAIT` has it, in ms. */
    private val pictureWait = 15_000L

    @Test
    fun `a page is ready with how many pictures it goes without`() {
        assertEquals(0, picturesMissing("ready"))
        assertEquals(2, picturesMissing("ready:2"))
    }

    @Test
    fun `nothing else is a page being ready`() {
        assertNull(picturesMissing(null))
        assertNull(picturesMissing("hello"))
        assertNull(picturesMissing(LAID_OUT))
        assertNull(picturesMissing("ready:"))
        assertNull(picturesMissing("ready:some"))
        assertNull(picturesMissing("ready:-1"))
        assertNull(picturesMissing("ready:0"))
    }

    @Test
    fun `a big map laid out late still has its pictures' whole wait`() {
        var gaveUp = false
        val patience = PrintPatience(Handler(Looper.getMainLooper())) { gaveUp = true }
        patience.start()

        // Laying a big map out takes most of the time a page has to load.
        idle(25_000)
        patience.laidOut()
        // Then a picture from the web never comes: the page waits for it, and is ready without it.
        idle(pictureWait + 1_000)
        assertFalse(gaveUp)

        // A page that never says it's ready is given up on, in time.
        idle(PrintPatience.PICTURES_TIMEOUT - pictureWait - 2_000)
        assertFalse(gaveUp)
        idle(1_000)
        assertTrue(gaveUp)
    }

    @Test
    fun `a page that's never laid out is given up on`() {
        var gaveUp = false
        val patience = PrintPatience(Handler(Looper.getMainLooper())) { gaveUp = true }
        patience.start()

        idle(PrintPatience.READY_TIMEOUT - 1)
        assertFalse(gaveUp)
        idle(1)
        assertTrue(gaveUp)
    }

    @Test
    fun `a page laid out again gets no more time`() {
        var gaveUp = false
        val patience = PrintPatience(Handler(Looper.getMainLooper())) { gaveUp = true }
        patience.start()
        patience.laidOut()

        idle(PrintPatience.PICTURES_TIMEOUT - 1_000)
        patience.laidOut()
        idle(1_000)
        assertTrue(gaveUp)
    }

    @Test
    fun `a page that's ready is never given up on`() {
        var gaveUp = false
        val patience = PrintPatience(Handler(Looper.getMainLooper())) { gaveUp = true }
        patience.start()
        patience.laidOut()
        patience.stop()

        idle(PrintPatience.READY_TIMEOUT + PrintPatience.PICTURES_TIMEOUT)
        assertFalse(gaveUp)
    }

    private fun idle(milliseconds: Long) {
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(milliseconds))
    }
}
