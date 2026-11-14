package com.hyuchia.orbit.page

import android.os.Handler
import android.os.SystemClock

/**
 * A wait that counts only the time Orbit is in sight, then runs `done` on `handler`'s thread: while Orbit is
 * away, Android may freeze it and its pages, and a page frozen halfway through its start isn't late for it. A
 * plain delay runs on the clock that went on meanwhile, and would fire the moment Orbit came back.
 */
internal class SightedWait(private val handler: Handler, private val done: () -> Unit) {
    /** What's left of the wait, in milliseconds, or null when nothing's waited for. */
    private var left: Long? = null

    /** When the clock last started counting, while it counts. */
    private var since: Long? = null

    private val fire = Runnable {
        left = null
        since = null
        done()
    }

    /** Whether it's waiting, counting or not. */
    val waiting: Boolean get() = left != null

    /** Waits `millis` of Orbit's time in sight, from now if it's `inSight`, else from when it is. */
    fun start(millis: Long, inSight: Boolean) {
        cancel()
        left = millis

        if (inSight) {
            resume()
        }
    }

    /** Stops counting, as Orbit leaves sight. */
    fun pause() {
        val started = since ?: return
        handler.removeCallbacks(fire)
        left = left?.let { (it - (SystemClock.uptimeMillis() - started)).coerceAtLeast(0) }
        since = null
    }

    /** Counts again, as Orbit is back in sight. */
    fun resume() {
        val remaining = left ?: return

        if (since != null) {
            return
        }

        since = SystemClock.uptimeMillis()
        handler.postDelayed(fire, remaining)
    }

    fun cancel() {
        handler.removeCallbacks(fire)
        left = null
        since = null
    }
}
