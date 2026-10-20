package com.hyuchia.orbit.present

import com.hyuchia.orbit.page.AppTheme
import com.hyuchia.orbit.page.AudienceCommand
import com.hyuchia.orbit.page.PageWords
import com.hyuchia.orbit.page.protocolJson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/** An audience on a TV, over a stand-in for the Cast session's channel, on one thread as on the phone's main one. */
class CastAudienceTest {
    private val main = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
    private val scope = CoroutineScope(SupervisorJob() + main)
    private val words = PageWords.from("en") { "" }
    private val theme = AppTheme(dark = false, tokens = emptyMap())

    /** The TV's end of the channel: everything sent to it, in order. */
    private class Tv : TvChannel {
        val sent = CopyOnWriteArrayList<String>()

        override val name: String = "Living room TV"

        override fun send(message: String) {
            sent += message
        }

        override fun listen(listener: ((String) -> Unit)?) = Unit

        /** What the TV heard, by type, and a map's text where one came. */
        fun heard(): List<String> = sent.map { message ->
            val json = protocolJson.parseToJsonElement(message).jsonObject
            val type = (json["type"] as JsonPrimitive).content
            (json["markdown"] as? JsonPrimitive)?.let { "$type ${it.content}" } ?: type
        }
    }

    @After
    fun stop() {
        scope.cancel()
        main.close()
    }

    /** Waits up to five seconds for `condition`. */
    private fun eventually(condition: () -> Boolean): Boolean {
        val until = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)

        while (System.nanoTime() < until) {
            if (condition()) {
                return true
            }

            Thread.sleep(10)
        }

        return condition()
    }

    @Test
    fun `the TV hears the end at once, even while a map is still on its way and the screen's work stops with it`() = runBlocking<Unit> {
        val tv = Tv()
        val shrinking = CountDownLatch(1)
        val shrunk = CountDownLatch(1)

        val audience = withContext(main) {
            CastAudience(tv, scope, words) { markdown ->
                if (markdown.contains("Museums")) {
                    shrinking.countDown()
                    shrunk.await()
                }

                markdown
            }
        }

        audience.send(AudienceCommand.Show("# Trip", theme))
        audience.send(AudienceCommand.Step("trip", 0, 1))
        assertTrue(eventually { tv.heard().size == 4 })
        // A map whose pictures take a while to shrink, then the presentation ends: the screen closes its audience, and its work stops.
        audience.send(AudienceCommand.Show("# Museums", theme))
        assertTrue(shrinking.await(5, TimeUnit.SECONDS))

        withContext(main) {
            audience.close()
            scope.cancel()
        }

        shrunk.countDown()
        Thread.sleep(200)
        assertEquals(listOf("hello", "language", "show # Trip", "step", "end"), tv.heard())
    }

    @Test
    fun `a map whose pictures can't be read reaches the TV without them, and the presentation goes on`() = runBlocking<Unit> {
        val tv = Tv()
        val audience = withContext(main) { CastAudience(tv, scope, words) { markdown -> if (markdown.contains("gone")) throw IOException("gone") else "$markdown, pictures inside" } }
        audience.send(AudienceCommand.Show("# Trip ![](media/gone.jpg)", theme))
        audience.send(AudienceCommand.Step("trip", 0, 2))
        audience.send(AudienceCommand.Show("# Trip ![](media/here.jpg)", theme))
        assertTrue(eventually { tv.heard().size == 5 })
        assertEquals(listOf("hello", "language", "show # Trip ![](media/gone.jpg)", "step", "show # Trip ![](media/here.jpg), pictures inside"), tv.heard())
    }
}
