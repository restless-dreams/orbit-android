package com.hyuchia.orbit.nearby

import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.Base64

/**
 * What every answer carries, unless its handler says otherwise: no guessing
 * types, no referrer, no framing, no caching.
 */
internal val SAFETY_HEADERS = listOf(
    "X-Content-Type-Options" to "nosniff",
    "Referrer-Policy" to "no-referrer",
    "X-Frame-Options" to "DENY",
    "Cache-Control" to "no-store",
)

/**
 * The content policy of every answer that isn't a page: a picture or video
 * opened on its own, an SVG with a script inside say, runs nothing, loads
 * nothing and stays apart from the phone's pages.
 */
internal const val FILE_POLICY = "default-src 'none'; style-src 'unsafe-inline'; sandbox"

/**
 * The Content-Security-Policy for a page: scripts only from the phone or
 * inline ones it wrote, by their hashes, so nothing a map's text slips into
 * the page can run; pictures, videos and embeds from the phone, data and
 * https; and never framed, never rebased, forms only to the phone.
 */
internal fun pagePolicy(html: String): String {
    val scripts = inlineScripts(html).joinToString("") { " '${scriptHash(it)}'" }
    return listOf(
        "default-src 'none'",
        "script-src 'self'$scripts",
        "style-src 'self' 'unsafe-inline'",
        "img-src 'self' data: blob: https:",
        "media-src 'self' data: blob: https:",
        "frame-src https:",
        "font-src 'self' data:",
        "connect-src 'self'",
        "form-action 'self'",
        "frame-ancestors 'none'",
        "base-uri 'none'",
    ).joinToString("; ")
}

/** The text of each script element in a page that runs without a src: its own code, or a module. Data blocks aren't scripts. */
internal fun inlineScripts(html: String): List<String> {
    val scripts = ArrayList<String>()
    var position = 0

    while (true) {
        val open = html.indexOf("<script", position, ignoreCase = true)

        if (open < 0) {
            break
        }

        val tagEnd = html.indexOf('>', open)

        if (tagEnd < 0) {
            break
        }

        val attributes = html.substring(open + "<script".length, tagEnd)

        if (attributes.isNotEmpty() && !attributes[0].isWhitespace()) {
            position = open + 1
            continue
        }

        val close = html.indexOf("</script", tagEnd + 1, ignoreCase = true)

        if (close < 0) {
            break
        }

        val type = SCRIPT_TYPE.find(attributes)?.groupValues?.get(1)?.lowercase()

        if (!SCRIPT_SOURCE.containsMatchIn(attributes) && (type == null || type in RUNNABLE_TYPES)) {
            scripts += html.substring(tagEnd + 1, close)
        }

        position = close + "</script".length
    }

    return scripts
}

private val SCRIPT_TYPE = Regex("""\btype\s*=\s*["']?([^"'\s>]*)""", RegexOption.IGNORE_CASE)
private val SCRIPT_SOURCE = Regex("""(^|\s)src\s*=""", RegexOption.IGNORE_CASE)
private val RUNNABLE_TYPES = setOf("", "module", "text/javascript", "application/javascript")

/** A script's CSP hash, of its text as a browser reads it: HTML turns every line break into a line feed first. */
internal fun scriptHash(script: String): String {
    val text = script.replace("\r\n", "\n").replace('\r', '\n')
    val digest = MessageDigest.getInstance("SHA-256").digest(text.toByteArray(StandardCharsets.UTF_8))
    return "sha256-" + Base64.getEncoder().encodeToString(digest)
}
