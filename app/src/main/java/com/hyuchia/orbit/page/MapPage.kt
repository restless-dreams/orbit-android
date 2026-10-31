package com.hyuchia.orbit.page

import kotlinx.serialization.DeserializationStrategy

/**
 * The page drawing a map, as the map's view model works with it: commands
 * it's sent, messages it says, and jobs it answers. [OrbitPage] is the one in
 * a WebView; a test drives the view model with a page of its own.
 */
interface MapPage {
    /** Who hears the page's messages: the screen showing it. */
    var listener: ((MapMessage) -> Unit)?

    /** Keyboards are asked not to learn from what's typed into the page, as for a private map. */
    var incognito: Boolean

    fun send(command: MapCommand)

    /** Sends a job and waits for its answer. */
    suspend fun <T> request(answer: DeserializationStrategy<T>, command: (job: Int) -> MapCommand): T

    /** Waits until what the page shows now is ready to be drawn, so the next frame shows it; never long. */
    suspend fun drawn()
}
