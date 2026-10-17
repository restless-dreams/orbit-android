package com.hyuchia.orbit.nearby

import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

/** Lets every connection in, so tests can talk to a server over the loopback. */
val anyone = ConnectionPolicy { _, _ -> true }

/** An answer as a test reads it: its status, headers (names in lower case, each with every value) and body. */
class Answer(val status: Int, val headers: Map<String, List<String>>, val body: String) {
    fun header(name: String): String? = headers[name.lowercase()]?.firstOrNull()
}

/** A connection to a test server on the loopback, from `from` when it's given (any of 127.0.0.0/8 on Linux). */
fun connect(port: Int, from: String? = null): Socket = Socket().apply {
    from?.let { bind(InetSocketAddress(it, 0)) }
    connect(InetSocketAddress("127.0.0.1", port), 5_000)
    soTimeout = 10_000
}

/** One request, written out as is, and the whole answer up to the server closing the connection. */
fun exchange(port: Int, request: String, from: String? = null): Answer = connect(port, from).use { socket ->
    socket.getOutputStream().write(request.toByteArray(StandardCharsets.ISO_8859_1))
    parse(socket.getInputStream().readBytes())
}

fun request(
    port: Int,
    path: String,
    method: String = "GET",
    headers: Map<String, String> = emptyMap(),
    body: String = "",
    host: String? = "127.0.0.1:$port",
    from: String? = null,
): Answer {
    val text = buildString {
        append("$method $path HTTP/1.1\r\n")
        host?.let { append("Host: $it\r\n") }
        headers.forEach { (name, value) -> append("$name: $value\r\n") }

        if (method == "POST" || body.isNotEmpty()) {
            append("Content-Length: ${body.toByteArray().size}\r\n")
        }

        append("\r\n")
        append(body)
    }

    return exchange(port, text, from)
}

fun parse(bytes: ByteArray): Answer {
    val text = bytes.decodeToString()
    val head = text.substringBefore("\r\n\r\n")
    val lines = head.split("\r\n")
    val status = lines.first().split(' ').getOrNull(1)?.toIntOrNull() ?: 0
    val headers = lines.drop(1).filter { ':' in it }.groupBy({ it.substringBefore(':').trim().lowercase() }, { it.substringAfter(':').trim() })
    return Answer(status, headers, text.substringAfter("\r\n\r\n", ""))
}

/** A browser on one device: it keeps the cookies it's given, for the paths they're for, and sends them back. */
class Browser(private val port: Int, val from: String = "127.0.0.1") {
    private class Cookie(val name: String, val value: String, val path: String)

    private val cookies = mutableListOf<Cookie>()

    val cookieHeaders: List<String> get() = cookies.map { "${it.name}=${it.value}" }

    fun get(path: String, headers: Map<String, String> = emptyMap()): Answer = keep(request(port, path, headers = withCookies(path, headers), from = from))

    fun post(path: String, fields: Map<String, String>, headers: Map<String, String> = emptyMap()): Answer {
        val body = fields.entries.joinToString("&") { (name, value) -> "${encode(name)}=${encode(value)}" }
        val all = withCookies(path, headers) + ("Content-Type" to "application/x-www-form-urlencoded")
        return keep(request(port, path, method = "POST", headers = all, body = body, from = from))
    }

    /** Opens `path` as an event stream, and hands back the socket and its lines. */
    fun follow(path: String): Pair<Socket, BufferedReader> {
        val socket = connect(port, from)

        val head = buildString {
            append("GET $path HTTP/1.1\r\nHost: 127.0.0.1:$port\r\nAccept: text/event-stream\r\n")
            withCookies(path, emptyMap()).forEach { (name, value) -> append("$name: $value\r\n") }
            append("\r\n")
        }

        socket.getOutputStream().write(head.toByteArray())
        return socket to BufferedReader(InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8))
    }

    private fun withCookies(path: String, headers: Map<String, String>): Map<String, String> {
        val sent = cookies.filter { path.startsWith(it.path) }
        return if (sent.isEmpty()) headers else headers + ("Cookie" to sent.joinToString("; ") { "${it.name}=${it.value}" })
    }

    private fun keep(answer: Answer): Answer {
        answer.headers["set-cookie"].orEmpty().forEach { line ->
            val parts = line.split(';').map { it.trim() }
            val (name, value) = parts.first().split('=', limit = 2)
            val path = parts.firstOrNull { it.startsWith("Path=") }?.removePrefix("Path=") ?: "/"
            cookies.removeAll { it.name == name && it.path == path }
            cookies += Cookie(name, value, path)
        }

        return answer
    }

    private fun encode(text: String) = URLEncoder.encode(text, "UTF-8")
}

/** Reads an event stream's head, up to its blank line: its status line first. */
fun BufferedReader.head(): List<String> = generateSequence { readLine() }.takeWhile { it.isNotEmpty() }.toList()
