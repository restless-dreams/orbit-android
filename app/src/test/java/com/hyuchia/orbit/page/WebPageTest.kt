package com.hyuchia.orbit.page

import com.hyuchia.orbit.FileWords
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.Locale

class WebPageTest {
    private val words = FileWords(Locale.US).pageWords()

    private fun asset(name: String): String = File("src/main/assets/map/$name").readText()

    /** What a page tells its script, from its #orbit-map element. */
    private fun dataOf(page: String) = protocolJson.parseToJsonElement(
        Regex("<script type=\"application/json\" id=\"orbit-map\">(.*?)</script>").find(page)!!.groupValues[1],
    ).jsonObject

    @Test
    fun aPageToPrintSaysWhatItsPictureShows() {
        val theme = AppTheme(dark = false, tokens = mapOf("text" to "#1B1B21"), seed = "#4F5B92")
        val page = WebPage.page(asset("web-page.html"), "", "Trip", "# Trip\n", theme, emptyList(), null, words, PrintRequest(viewMode = "focus", focus = "pack"))
        val print = dataOf(page)["print"]!!.jsonObject
        assertEquals("focus", print["viewMode"]!!.jsonPrimitive.content)
        assertEquals("pack", print["focus"]!!.jsonPrimitive.content)
        // The layout has its place, shown only in print.
        assertTrue(page.contains("<div id=\"print\" class=\"page__print\"></div>"))
        assertTrue(page.contains("@media print"))
    }

    @Test
    fun aPageToShareHasNothingToPrintAsked() {
        val page = WebPage.page(asset("web-page.html"), "", "Trip", "# Trip\n", AppTheme(dark = true, tokens = emptyMap()), emptyList(), null, words)
        assertEquals(JsonNull, dataOf(page)["print"])
    }

    @Test
    fun eachPlaceholderIsFilledOnceEvenWhenAValueMentionsAnother() {
        val page = fill(
            "<title><!-- title --></title><!-- data --><!-- script -->",
            mapOf(
                "<!-- title -->" to "Notes about <!-- script -->",
                "<!-- data -->" to "[data]",
                "<!-- script -->" to "[script]",
            ),
        )
        assertEquals("<title>Notes about <!-- script --></title>[data][script]", page)
    }

    @Test
    fun scriptsKeepTheirMeaningButCantEndTheirElement() {
        val safe = scriptSafe("""const a = "</script><!-- x"; const b = /<SCRIPT/i;""")
        assertFalse(safe.contains("</script", ignoreCase = true))
        assertFalse(safe.contains("<!--"))
        assertFalse(safe.contains("<script", ignoreCase = true))
        assertEquals("""const a = "\x3c/script>\x3c!-- x"; const b = /\x3cSCRIPT/i;""", safe)
    }

    @Test
    fun titlesAreText() {
        assertEquals("Q&amp;A &lt;draft&gt; &quot;v2&quot;", escapeHtml("Q&A <draft> \"v2\""))
    }
}
