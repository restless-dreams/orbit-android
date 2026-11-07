package com.hyuchia.orbit.page

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.hyuchia.orbit.CapturesFailures
import com.hyuchia.orbit.OnDevice.app
import com.hyuchia.orbit.OnDevice.evaluate
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.builtins.serializer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.Timeout
import org.junit.runner.RunWith
import kotlin.time.Duration.Companion.seconds

/**
 * A job the spare page never finishes, on the phone's own WebView: a picture of a map whose picture never arrives,
 * as from a server that never answers. Whoever asked gives up on it, as a thumbnail that scrolled away does, or as
 * the page's own minute running out does (OrbitPageTest): the page hears it's called off and stops its work, the
 * picture's loading included, answers nothing for it, and goes on with the next job.
 */
@RunWith(AndroidJUnit4::class)
class PageJobsTest {
    @get:Rule
    val captures = CapturesFailures()

    /** A test that waits for something that never comes fails, rather than holding up the rest. */
    @get:Rule
    val timeout: Timeout = Timeout.seconds(300)

    /** A small job any page answers at once: a map written as YAML. */
    private suspend fun convert(page: OrbitPage): String = page.request(String.serializer()) { job -> MapCommand.Convert(job, "# Trip\n- Lisbon", "yaml") }

    /** How many pictures from the address that never answers the page asked for, and how many it called off. */
    private suspend fun asked(page: OrbitPage): Int = evaluate(page.webView, "window.orbitHanging ? window.orbitHanging.asked : -1").toInt()

    private suspend fun calledOff(page: OrbitPage): Int = evaluate(page.webView, "window.orbitHanging ? window.orbitHanging.calledOff : -1").toInt()

    @Test
    fun aJobThatNeverEndsIsCalledOffOnThePageWhichStopsItsWorkAndGoesOn() = runBlocking {
        app.pages.work { page ->
            coroutineScope {
                // The page is up and answering.
                assertTrue(convert(page).contains("Lisbon"))

                // Pictures from that address never arrive: their loading waits until it's called off, as the
                // page's own fetch does for a server that never answers.
                evaluate(page.webView, NEVER_ARRIVING)

                val asking = async(start = CoroutineStart.UNDISPATCHED) {
                    page.request(String.serializer()) { job ->
                        MapCommand.Snapshot(job, "png", markdown = MAP, width = 480, height = 300, scale = 1.0, baseUrl = mapBaseUrl("never-arriving"))
                    }
                }

                withTimeout(60.seconds) {
                    while (asked(page) < 1) {
                        delay(100)
                    }
                }

                assertTrue(page.busy)
                assertEquals(0, calledOff(page))

                // Given up on, as a thumbnail that scrolled away.
                asking.cancel()
                asking.join()
                assertFalse(page.busy)

                withTimeout(30.seconds) {
                    while (calledOff(page) < 1) {
                        delay(100)
                    }
                }

                // The next job is answered at once, and the one called off never is: nothing waits for it.
                assertTrue(withTimeout(10.seconds) { convert(page) }.contains("Lisbon"))
                delay(1_000)
                assertEquals(1, asked(page))
                assertEquals(1, calledOff(page))
                assertFalse(page.busy)
            }
        }
    }

    private companion object {
        /** A map whose picture is at an address that never answers. */
        const val MAP = "# Trip\n## Alfama\n![The tram](media/never-arriving.jpg)\n"

        /** The page's fetch, for pictures at that address: waits until whoever asked calls it off. */
        const val NEVER_ARRIVING = """
            (() => {
              const fetching = window.fetch.bind(window);
              window.orbitHanging = { asked: 0, calledOff: 0 };
              window.fetch = (input, init) => {
                if (!String(input).includes('never-arriving')) {
                  return fetching(input, init);
                }

                window.orbitHanging.asked += 1;
                return new Promise((resolve, reject) => {
                  init?.signal?.addEventListener('abort', () => {
                    window.orbitHanging.calledOff += 1;
                    reject(new DOMException('Called off', 'AbortError'));
                  });
                });
              };
            })();
        """
    }
}
