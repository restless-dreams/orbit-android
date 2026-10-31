package com.hyuchia.orbit.nearby

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.nio.file.Files

class HttpWireTest {
    private fun head(vararg lines: String) = lines.joinToString("\r\n")

    private fun parsed(vararg lines: String): ParsedHead = requireNotNull(parseHead(head(*lines))) { lines.joinToString(" / ") }

    private fun stream(text: String) = ByteArrayInputStream(text.toByteArray(Charsets.ISO_8859_1))

    @Test
    fun aRequestLineAndItsHeadersAreReadAsTheyCame() {
        val head = parsed(
            "GET /1634/media/tram%20at%20dusk.jpg?size=small&name=caf%C3%A9 HTTP/1.1",
            "Host: 192.168.1.5:8766",
            "User-Agent:  Firefox\t",
            "Accept: text/html",
            "Accept: image/webp",
            "Cookie: a=1",
            "Cookie: b=2",
        )

        assertEquals("GET", head.method)
        assertEquals("/1634/media/tram at dusk.jpg", head.path)
        assertEquals(mapOf("size" to "small", "name" to "café"), head.query)
        // Names in lower case, values without the spaces and tabs around them.
        assertEquals("192.168.1.5:8766", head.headers["host"])
        assertEquals("Firefox", head.headers["user-agent"])
        // A header given twice is one list; cookies are joined as a browser sends them.
        assertEquals("text/html, image/webp", head.headers["accept"])
        assertEquals("a=1; b=2", head.headers["cookie"])
        // A query part without a value has an empty one.
        assertEquals(mapOf("download" to ""), parsed("GET /1634/map.zip?download HTTP/1.0", "Host: a").query)
    }

    @Test
    fun aHeadThatIsntStrictlyARequestIsRefused() {
        listOf(
            // Not three parts, or not HTTP/1.
            head("GET /1634/ HTTP/1.1 extra", "Host: a"),
            head("GET /1634/", "Host: a"),
            head("GET /1634/ HTTP/2", "Host: a"),
            // A method with characters a token can't have.
            head("GE(T /1634/ HTTP/1.1", "Host: a"),
            // A target that isn't a path, or has spaces, control or non-ASCII characters in it.
            head("GET http://evil.example/ HTTP/1.1", "Host: a"),
            head("GET * HTTP/1.1", "Host: a"),
            head("GET /a\u0001b HTTP/1.1", "Host: a"),
            head("GET /café HTTP/1.1", "Host: a"),
            // Nameless, folded or badly named headers, and ones with control characters.
            head("GET / HTTP/1.1", ": nameless"),
            head("GET / HTTP/1.1", "Host: a", " folded"),
            head("GET / HTTP/1.1", "Bad Name: a"),
            head("GET / HTTP/1.1", "Host: a\u0000b"),
            // Two hosts or two lengths, and any transfer encoding.
            head("GET / HTTP/1.1", "Host: a", "Host: b"),
            head("POST / HTTP/1.1", "Host: a", "Content-Length: 1", "Content-Length: 2"),
            head("POST / HTTP/1.1", "Host: a", "Transfer-Encoding: chunked"),
            // Control characters once the path or the query is decoded.
            head("GET /a%00b HTTP/1.1", "Host: a"),
            head("GET /?name=a%0Ab HTTP/1.1", "Host: a"),
        ).forEach { head ->
            assertNull(head, parseHead(head))
        }
    }

    @Test
    fun aBodysLengthIsADecimalNumberOrNone() {
        assertEquals(0L, bodyLength(parsed("GET / HTTP/1.1", "Host: a")))
        assertEquals(12L, bodyLength(parsed("POST / HTTP/1.1", "Host: a", "Content-Length: 12")))
        assertEquals(999_999_999_999_999_999L, bodyLength(parsed("POST / HTTP/1.1", "Host: a", "Content-Length: 999999999999999999")))

        listOf("", "-1", "+1", "1e3", "0x10", "1 2", "1234567890123456789").forEach { length ->
            assertNull(length, bodyLength(ParsedHead("POST", "/", emptyMap(), mapOf("content-length" to length))))
        }
    }

    @Test
    fun aHeadIsReadUpToTheBlankLineAndNoFurther() {
        val input = stream("GET / HTTP/1.1\r\nHost: a\r\n\r\nthe body")
        assertEquals("GET / HTTP/1.1\r\nHost: a", readHead(input))
        assertEquals("the body", input.readBytes().decodeToString())

        // A carriage return that starts over the match isn't lost.
        assertEquals("GET / HTTP/1.1\r\nX: \r", readHead(stream("GET / HTTP/1.1\r\nX: \r\r\n\r\n")))
        // Ended before the blank line, or longer than the limit: no head.
        assertNull(readHead(stream("GET / HTTP/1.1\r\nHost: a\r\n")))
        assertNull(readHead(stream("GET / HTTP/1.1\r\nX: " + "a".repeat(HEADERS_LIMIT) + "\r\n\r\n")))
    }

    @Test
    fun aSmallBodyIsReadIntoMemoryAndAShortOneIsNone() {
        val head = ParsedHead("POST", "/offer", mapOf("q" to "1"), mapOf("host" to "a"))
        val request = readBody(stream("hello and more"), head, "10.0.0.2", 5, spool = null)!!

        assertEquals("POST", request.method)
        assertEquals("/offer", request.path)
        assertEquals(mapOf("q" to "1"), request.query)
        assertEquals("10.0.0.2", request.from)
        assertEquals("hello", request.body.decodeToString())
        assertNull(request.bodyFile)

        assertNull(readBody(stream("hel"), head, "10.0.0.2", 5, spool = null))
    }

    @Test
    fun aBigBodyGoesToAFileWhenThereIsAFolderForIt() {
        val spool = Files.createTempDirectory("orbit-spool").toFile()

        try {
            val head = ParsedHead("POST", "/offer/map", emptyMap(), emptyMap())
            val bytes = ByteArray(IN_MEMORY_BODY + 10) { (it % 251).toByte() }
            val request = readBody(ByteArrayInputStream(bytes), head, "10.0.0.2", bytes.size.toLong(), spool)!!

            assertEquals(0, request.body.size)
            assertArrayEquals(bytes, request.bodyFile!!.readBytes())
            assertArrayEquals(bytes, request.bodyStream().readBytes())
            request.bodyFile!!.delete()

            // One that ends early leaves no file behind.
            assertNull(readBody(ByteArrayInputStream(bytes, 0, IN_MEMORY_BODY + 1), head, "10.0.0.2", bytes.size.toLong(), spool))
            assertTrue(spool.list().isNullOrEmpty())

            // Without a folder, it's too big.
            val tooBig = runCatching { readBody(ByteArrayInputStream(bytes), head, "10.0.0.2", bytes.size.toLong(), spool = null) }
            assertTrue(tooBig.exceptionOrNull() is TooBigException)
        } finally {
            spool.deleteRecursively()
        }
    }

    @Test
    fun anAnswersHeadCarriesTheSafetyHeadersAndOnlyHeadersThatStayOneLine() {
        val head = answerHead(
            404,
            "text/plain; charset=utf-8",
            9,
            mapOf("cache-control" to "max-age=60", "Set-Cookie" to "a=1\r\nX-Injected: yes", "Bad Name" to "x", "Location" to "/1634/"),
        ).decodeToString()

        assertTrue(head, head.startsWith("HTTP/1.1 404 Not Found\r\nContent-Type: text/plain; charset=utf-8\r\nContent-Length: 9\r\nConnection: close\r\n"))
        assertTrue(head, head.endsWith("\r\n\r\n"))
        // The handler's own value takes the place of a safety header's, whatever its case.
        assertTrue(head, "cache-control: max-age=60\r\n" in head)
        assertFalse(head, "Cache-Control: no-store" in head)
        SAFETY_HEADERS.filter { (name, _) -> name != "Cache-Control" }.forEach { (name, value) -> assertTrue(head, "$name: $value\r\n" in head) }
        // A value that would break into lines of its own, or a name that isn't a token, is left out.
        assertFalse(head, "X-Injected" in head)
        assertFalse(head, "Set-Cookie" in head)
        assertFalse(head, "Bad Name" in head)
        assertTrue(head, "Location: /1634/\r\n" in head)
        // A status without a phrase of its own still makes a status line.
        assertTrue(answerHead(418, "text/plain", 0, emptyMap()).decodeToString().startsWith("HTTP/1.1 418 OK\r\n"))
    }

    @Test
    fun eventsAreDataLinesEndedByABlankLine() {
        assertEquals("data: step\n\n", eventText("step"))
        assertEquals("data: {\"a\":1,\ndata: \"b\":2}\n\n", eventText("{\"a\":1,\n\"b\":2}"))

        val head = eventsHead().decodeToString()
        assertTrue(head, head.startsWith("HTTP/1.1 200 OK\r\nContent-Type: text/event-stream; charset=utf-8\r\n"))
        SAFETY_HEADERS.forEach { (name, value) -> assertTrue(head, "$name: $value\r\n" in head) }
        assertTrue(head, head.endsWith("\r\n\r\n"))
    }

    @Test
    fun aTargetsPartsAreDecodedOrKeptAsTheyCame() {
        assertEquals("tram at dusk", urlDecoded("tram%20at%20dusk"))
        assertEquals("a b", urlDecoded("a+b"))
        assertEquals("café", urlDecoded("caf%C3%A9"))
        assertEquals("100%zz", urlDecoded("100%zz"))
        assertEquals("Not Found", REASONS[404])
    }
}
