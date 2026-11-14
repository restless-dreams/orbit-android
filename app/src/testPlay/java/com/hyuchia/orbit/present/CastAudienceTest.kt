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
import org.junit.Assert.assertNull
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

    /** The TV's end of the channel: everything sent to it, in order, but what Cast says didn't reach it. */
    private class Tv : TvChannel {
        val sent = CopyOnWriteArrayList<String>()

        /** Whether what's sent now doesn't reach the TV, as on a Wi-Fi that drops it: Cast says so, with this status. */
        @Volatile
        var failing: Int? = null

        override val name: String = "Living room TV"

        override fun send(message: String, failed: (Int) -> Unit) {
            val status = failing

            if (status == null) {
                sent += message
            } else {
                failed(status)
            }
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
    fun `an end goes to the TV at once, a map still being made ready never after it, and the TV stays for the next presentation`() = runBlocking<Unit> {
        val tv = Tv()
        val shrinking = CountDownLatch(1)
        val shrunk = CountDownLatch(1)

        val audience = withContext(main) {
            CastAudience(tv, scope, words) { markdown ->
                if (markdown.contains("Journal")) {
                    shrinking.countDown()
                    shrunk.await()
                }

                markdown
            }
        }

        withContext(main) { audience.send(AudienceCommand.Show("# Trip", theme)) }
        assertTrue(eventually { tv.heard().size == 3 })

        // A private map whose pictures take a while to shrink, and it locks: the presentation stops, its screen stays.
        withContext(main) {
            audience.send(AudienceCommand.Show("# Journal", theme))
            audience.send(AudienceCommand.Step("journal", 0, 1))
        }

        assertTrue(shrinking.await(5, TimeUnit.SECONDS))
        withContext(main) { audience.send(AudienceCommand.End) }
        assertEquals(listOf("hello", "language", "show # Trip", "end"), tv.heard())

        // What was made ready meanwhile, and the step behind it, never go.
        shrunk.countDown()
        Thread.sleep(200)
        assertEquals(listOf("hello", "language", "show # Trip", "end"), tv.heard())

        // Still connected, the TV shows the next presentation.
        withContext(main) { audience.send(AudienceCommand.Show("# Garden", theme)) }
        assertTrue(eventually { tv.heard().size == 5 })
        assertEquals(listOf("hello", "language", "show # Trip", "end", "show # Garden"), tv.heard())
    }

    @Test
    fun `an end as soon as the TV is there still comes after the hello`() = runBlocking<Unit> {
        val tv = Tv()

        withContext(main) {
            CastAudience(tv, scope, words) { it }.send(AudienceCommand.End)
        }

        assertTrue(eventually { tv.heard().size == 3 })
        assertEquals(listOf("hello", "language", "end"), tv.heard())
    }

    @Test
    fun `a message that doesn't reach the TV is said, and sending the presentation again mends it`() = runBlocking<Unit> {
        val tv = Tv()
        val audience = withContext(main) { CastAudience(tv, scope, words) { it } }
        withContext(main) { audience.send(AudienceCommand.Show("# Trip", theme)) }
        assertTrue(eventually { tv.heard().size == 3 })
        assertNull(audience.trouble.value)

        // A step the Wi-Fi drops: Cast says it didn't go (a network error, 7), and the presenter hears it.
        tv.failing = 7
        withContext(main) { audience.send(AudienceCommand.Step("lisbon", 1, 3)) }
        assertTrue(eventually { audience.trouble.value == ScreenTrouble.Missed })
        assertEquals(listOf("hello", "language", "show # Trip"), tv.heard())

        // Back on the network, the presentation goes to the TV again, the hello and language first.
        tv.failing = null

        withContext(main) {
            audience.loadAgain()
            audience.send(AudienceCommand.Show("# Trip", theme))
            audience.send(AudienceCommand.Step("lisbon", 1, 3))
        }

        assertTrue(eventually { tv.heard().size == 7 })
        assertEquals(listOf("hello", "language", "show # Trip", "hello", "language", "show # Trip", "step"), tv.heard())
        assertNull(audience.trouble.value)
    }

    @Test
    fun `once the presentation is over on the TV, what doesn't reach it is no trouble`() = runBlocking<Unit> {
        val tv = Tv()
        val audience = withContext(main) { CastAudience(tv, scope, words) { it } }
        assertTrue(eventually { tv.heard().size == 2 })
        // The TV went away: the session's last messages can't reach it.
        tv.failing = 15

        withContext(main) {
            audience.close()
            scope.cancel()
        }

        assertNull(audience.trouble.value)
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
