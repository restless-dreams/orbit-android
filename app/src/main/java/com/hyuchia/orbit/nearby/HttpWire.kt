package com.hyuchia.orbit.nearby

import com.hyuchia.orbit.data.readUpTo
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

/** The longest request line and headers the server reads, together. */
internal const val HEADERS_LIMIT = 16 * 1024

/** A body bigger than this goes to a file instead of memory, when the server has a folder for them. */
internal const val IN_MEMORY_BODY = 256 * 1024

/** A request's body is past what the server takes for its method and path, or past memory with no folder for it. */
internal class TooBigException : Exception()

/** The parts of a request before its body. */
internal class ParsedHead(val method: String, val path: String, val query: Map<String, String>, val headers: Map<String, String>)

/**
 * The request line and headers, read strictly: a request with control
 * characters in its target or headers, a target that isn't a path,
 * folded or nameless headers, or two Hosts or lengths, isn't one.
 */
internal fun parseHead(head: String): ParsedHead? {
    val lines = head.split("\r\n")
    val requestLine = lines.first()
    val parts = requestLine.split(' ')

    if (parts.size != 3 || !parts[2].startsWith("HTTP/1.")) {
        return null
    }

    val (method, target) = parts

    if (method.isEmpty() || !method.all(::isTokenCharacter) || !target.startsWith("/") || !target.all { it in '!'..'~' }) {
        return null
    }

    val headers = LinkedHashMap<String, String>()

    for (line in lines.drop(1)) {
        val colon = line.indexOf(':')

        if (colon <= 0 || line.any { it != '\t' && (it < ' ' || it == '\u007F') }) {
            return null
        }

        val name = line.substring(0, colon)

        if (!name.all(::isTokenCharacter)) {
            return null
        }

        val key = name.lowercase()
        val value = line.substring(colon + 1).trim(' ', '\t')
        val earlier = headers[key]

        headers[key] = when {
            earlier == null -> value
            // One request, one address and one length: two of either is a request to refuse.
            key == "host" || key == "content-length" -> return null
            key == "cookie" -> "$earlier; $value"
            else -> "$earlier, $value"
        }
    }

    if (headers.containsKey("transfer-encoding")) {
        return null
    }

    val path = urlDecoded(target.substringBefore('?'))

    val query = target.substringAfter('?', "").split('&').filter { it.isNotEmpty() }.associate { pair ->
        urlDecoded(pair.substringBefore('=')) to urlDecoded(pair.substringAfter('=', ""))
    }

    if (path.any(Char::isISOControl) || query.any { (name, value) -> name.any(Char::isISOControl) || value.any(Char::isISOControl) }) {
        return null
    }

    return ParsedHead(method, path, query, headers)
}

private fun isTokenCharacter(character: Char): Boolean =
    character in 'a'..'z' || character in 'A'..'Z' || character in '0'..'9' || character in "!#$%&'*+-.^_`|~"

/** How long a request's body says it is: none without a length, and null when the length isn't a number. */
internal fun bodyLength(head: ParsedHead): Long? {
    val lengthText = head.headers["content-length"] ?: "0"

    if (lengthText.isEmpty() || lengthText.length > 18 || !lengthText.all { it in '0'..'9' }) {
        return null
    }

    return lengthText.toLong()
}

/**
 * The body of a request, `length` bytes of it: in memory, or in a file in
 * `spool` once it's past [IN_MEMORY_BODY] and there's a folder for it (past
 * that with none, [TooBigException]). Null when the input ends first.
 */
internal fun readBody(input: InputStream, head: ParsedHead, from: String, length: Long, spool: File?): Request? {
    if (length > IN_MEMORY_BODY && spool != null) {
        spool.mkdirs()
        val file = File.createTempFile("body", ".part", spool)

        val complete = try {
            file.outputStream().use { output -> copyExactly(input, output, length) }
        } catch (failure: IOException) {
            file.delete()
            throw failure
        }

        if (!complete) {
            file.delete()
            return null
        }

        return Request(head.method, head.path, head.query, head.headers, ByteArray(0), from, file)
    }

    if (length > IN_MEMORY_BODY) {
        throw TooBigException()
    }

    val body = ByteArray(length.toInt())

    if (input.readUpTo(body) < body.size) {
        return null
    }

    return Request(head.method, head.path, head.query, head.headers, body, from)
}

/** Copies exactly `length` bytes; false when the input ends first. */
private fun copyExactly(input: InputStream, output: OutputStream, length: Long): Boolean {
    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
    var left = length

    while (left > 0) {
        val count = input.read(buffer, 0, minOf(buffer.size.toLong(), left).toInt())

        if (count < 0) {
            return false
        }

        output.write(buffer, 0, count)
        left -= count
    }

    return true
}

/** The request line and headers, up to the blank line after them. */
internal fun readHead(input: InputStream): String? {
    val head = ByteArrayOutputStream()
    var matched = 0
    val end = byteArrayOf('\r'.code.toByte(), '\n'.code.toByte(), '\r'.code.toByte(), '\n'.code.toByte())

    while (matched < end.size) {
        val byte = input.read()

        if (byte < 0 || head.size() >= HEADERS_LIMIT) {
            return null
        }

        head.write(byte)
        matched = if (byte.toByte() == end[matched]) matched + 1 else if (byte.toByte() == end[0]) 1 else 0
    }

    return head.toString(StandardCharsets.ISO_8859_1.name()).removeSuffix("\r\n\r\n")
}

/** The head of an event stream's answer: it stays open, unbuffered, with the safety headers every answer carries. */
internal fun eventsHead(): ByteArray = (
    "HTTP/1.1 200 OK\r\nContent-Type: text/event-stream; charset=utf-8\r\nConnection: keep-alive\r\nX-Accel-Buffering: no\r\n" +
        SAFETY_HEADERS.joinToString("") { (name, value) -> "$name: $value\r\n" } + "\r\n"
    ).toByteArray()

/** An event as a stream writes it: each of its lines as data, and a blank line to end it. */
internal fun eventText(event: String): String = event.lines().joinToString("\n", postfix = "\n\n") { "data: $it" }

/**
 * The status line and headers of an answer that ends its connection: the
 * safety headers, unless the handler's `headers` give their own, and only
 * headers that can't break the answer into lines of their own.
 */
internal fun answerHead(status: Int, type: String, length: Long, headers: Map<String, String>): ByteArray {
    val reason = REASONS[status] ?: "OK"
    val all = LinkedHashMap<String, String>()
    SAFETY_HEADERS.forEach { (name, value) -> all[name] = value }

    // A header a handler made from something it was sent can't add lines of its own.
    headers.filter { (name, value) -> name.all(::isTokenCharacter) && value.none { it < ' ' || it == '\u007F' } }.forEach { (name, value) ->
        all.keys.firstOrNull { it.equals(name, ignoreCase = true) }?.let(all::remove)
        all[name] = value
    }

    val lines = all.entries.joinToString("") { (name, value) -> "$name: $value\r\n" }
    return ("HTTP/1.1 $status $reason\r\nContent-Type: $type\r\nContent-Length: $length\r\nConnection: close\r\n$lines\r\n").toByteArray()
}

/** A part of a request's target as it was before percent-encoding; as it is, when it doesn't decode. */
internal fun urlDecoded(text: String): String = try {
    URLDecoder.decode(text, StandardCharsets.UTF_8.name())
} catch (malformed: IllegalArgumentException) {
    text
}

/** The reason phrase that goes with each status the server answers with. */
internal val REASONS = mapOf(
    200 to "OK", 206 to "Partial Content", 302 to "Found", 303 to "See Other", 400 to "Bad Request", 403 to "Forbidden",
    404 to "Not Found", 405 to "Method Not Allowed", 409 to "Conflict", 410 to "Gone", 413 to "Payload Too Large",
    416 to "Range Not Satisfiable", 421 to "Misdirected Request", 429 to "Too Many Requests", 500 to "Internal Server Error",
    503 to "Service Unavailable",
)
