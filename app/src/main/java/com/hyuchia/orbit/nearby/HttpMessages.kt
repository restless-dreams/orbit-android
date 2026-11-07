package com.hyuchia.orbit.nearby

import java.io.File
import java.io.InputStream

/** A request from a device on the local network. */
class Request(
    val method: String,
    val path: String,
    val query: Map<String, String>,

    /** Header names in lower case. */
    val headers: Map<String, String>,

    /** The body, when it's small enough to keep in memory; a bigger one is in [bodyFile]. */
    val body: ByteArray,

    /** The address the request came from. */
    val from: String,

    /**
     * A body too big for memory, in a file: deleted once the request is
     * answered, unless the handler moves it somewhere of its own.
     */
    val bodyFile: File? = null,

    /**
     * A body streamed to the handler as it arrives, straight from the
     * connection, as a map sent here is ([Admission.Read.streamed]): it can
     * be read once, and ends where the request says it does.
     */
    private val bodyInput: InputStream? = null,
) {
    /** The body, wherever it is. */
    fun bodyStream(): InputStream = bodyInput ?: bodyFile?.inputStream() ?: body.inputStream()

    /** Every value the request's cookies give `name`. */
    fun cookies(name: String): List<String> =
        headers["cookie"].orEmpty().split(';').map { it.trim() }.filter { it.substringBefore('=') == name }.map { it.substringAfter('=') }
}

/** A request before its body is read: enough to refuse it without taking the body. */
class RequestHead(
    val method: String,
    val path: String,

    /** Header names in lower case. */
    val headers: Map<String, String>,

    /** The address the request came from. */
    val from: String,

    /** How long its body says it is. */
    val length: Long,
)

/** What the server does with a request before reading its body. */
sealed interface Admission {
    /**
     * Reads the body and answers the request; `release` runs once it's done
     * with, whether or not the body arrived whole. A `streamed` body isn't
     * read first: the handler reads it from the connection as it arrives,
     * never in memory or a file of the server's, within the time a body of
     * its length has.
     */
    class Read(val streamed: Boolean = false, val release: () -> Unit = {}) : Admission

    /** Answers at once, without reading any of the body. */
    class Refuse(val response: Response) : Admission
}

sealed interface Response {
    class Bytes(val status: Int, val type: String, val body: ByteArray, val headers: Map<String, String> = emptyMap()) : Response {
        fun withHeaders(more: Map<String, String>) = Bytes(status, type, body, headers + more)
    }

    /**
     * A file, read as it's written, never all at once. The server answers
     * a request for part of it, as a video asks, with that part (see [byteRange]).
     * A `temporary` file, made for this answer alone, goes whole, and is
     * deleted once it's written, or once writing it fails.
     */
    class FromFile(val type: String, val file: File, val headers: Map<String, String> = emptyMap(), val temporary: Boolean = false) : Response

    /** Server-sent events: the connection stays open and gets each event as it's sent, until either side closes. */
    class Events(val stream: EventStream) : Response

    companion object {
        fun text(status: Int, text: String, type: String = "text/plain; charset=utf-8") = Bytes(status, type, text.toByteArray())

        /** A small page, made in memory; its content policy is made from its scripts as it's sent. */
        fun page(status: Int, html: String, headers: Map<String, String> = emptyMap()) = Bytes(status, "text/html; charset=utf-8", html.toByteArray(), headers)

        /**
         * A page in a file, as a map's web page is, with its pictures and
         * sounds inside: sent as it's read, never whole in memory, with
         * `policy`, its content policy (see [pagePolicy]), in place of the
         * policy files go with, which runs nothing. A `temporary` one goes
         * once it's sent, as [FromFile]'s do.
         */
        fun pageFile(file: File, policy: String, headers: Map<String, String> = emptyMap(), temporary: Boolean = false) =
            FromFile("text/html; charset=utf-8", file, headers + ("Content-Security-Policy" to policy), temporary)

        fun json(status: Int, json: String) = Bytes(status, "application/json", json.toByteArray())
        fun notFound() = text(404, "Not found")
        fun redirect(location: String) = Bytes(302, "text/plain; charset=utf-8", "Moved to $location".toByteArray(), mapOf("Location" to location))
    }
}

/**
 * Where a request for a page at a code goes first, if anywhere: the page at
 * `/1634` is the folder `/1634/`, whose script and files its links name
 * relative to it, so an address typed without the last slash gets it added.
 */
internal fun folderRedirect(path: String): String? {
    val parts = path.trim('/').split('/').filter { it.isNotEmpty() }
    return if (parts.size == 1 && !path.endsWith("/")) "/${parts.single()}/" else null
}
