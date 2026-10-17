package com.hyuchia.orbit.page

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class WebPageTest {
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
