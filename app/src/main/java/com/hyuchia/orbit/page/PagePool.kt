package com.hyuchia.orbit.page

import android.content.Context
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** How long after a map opens the pool makes the next page, once the map has settled. */
private const val REWARM_DELAY = 2_000L

/**
 * Keeps one Orbit page ready, so a map draws the moment it opens instead of
 * waiting for a WebView to start. The spare page also does the work no screen
 * shows: thumbnails, pictures to export and conversions.
 */
class PagePool(context: Context, private val mapsDirectory: File) {
    private val application = context.applicationContext
    private val handler = Handler(Looper.getMainLooper())
    private var spare: OrbitPage? = null
    private val rewarm = Runnable { warm() }

    /** Makes the spare page, if there isn't one. Call it when the app is idle. */
    fun warm() {
        if (spare?.isAlive != true) {
            spare = OrbitPage(application, mapsDirectory)
        }
    }

    /** A page for a screen to show. Give it back with [give] when the screen goes. */
    fun take(): OrbitPage {
        val page = spare?.takeIf { it.isAlive } ?: OrbitPage(application, mapsDirectory)
        spare = null
        handler.removeCallbacks(rewarm)
        handler.postDelayed(rewarm, REWARM_DELAY)
        return page
    }

    /** Takes a page back: it becomes the spare if there isn't one. */
    fun give(page: OrbitPage) {
        page.listener = null
        page.detach()

        if (!page.isAlive) {
            page.destroy()
            return
        }

        page.send(MapCommand.Unload)

        if (spare?.isAlive == true) {
            page.destroy()
        } else {
            spare = page
        }
    }

    /** A page for an audience to watch on another screen, made in that screen's context so it's sized for it. */
    fun audiencePage(screen: Context): OrbitPage = OrbitPage(screen, mapsDirectory, AUDIENCE_URL)

    /** Runs work on the spare page, making one if needed. */
    suspend fun <T> work(job: suspend (OrbitPage) -> T): T = withContext(Dispatchers.Main) {
        warm()
        job(spare ?: OrbitPage(application, mapsDirectory).also { spare = it })
    }
}
