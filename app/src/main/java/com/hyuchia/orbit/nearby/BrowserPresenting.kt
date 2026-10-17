package com.hyuchia.orbit.nearby

import android.content.Context
import android.os.SystemClock
import com.hyuchia.orbit.R
import com.hyuchia.orbit.ResourceWords
import com.hyuchia.orbit.Words
import com.hyuchia.orbit.page.AudienceCommand
import com.hyuchia.orbit.page.PageWords
import com.hyuchia.orbit.page.escapeHtml
import com.hyuchia.orbit.page.fill
import com.hyuchia.orbit.page.htmlIn
import com.hyuchia.orbit.page.jsonScript
import com.hyuchia.orbit.page.pageWords
import com.hyuchia.orbit.page.pictureType
import com.hyuchia.orbit.page.protocolJson
import com.hyuchia.orbit.present.Audience
import com.hyuchia.orbit.present.ShownMedia
import com.hyuchia.orbit.present.after
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/** The port browsers find presentations at: the same every time, so a room's address doesn't change between talks. */
private const val PORT = 8765
/** The most a browser sends: the join form, with a name and a PIN. */
private const val FORM_LIMIT = 4L * 1024
/** How often people whose browsers stopped following are let go, to free their places. */
private const val TIDY_MILLISECONDS = 15_000L
/** A picture's or video's name in a map's media folder: nothing that could step outside it. */
private val MEDIA_NAME = Regex("[A-Za-z0-9][A-Za-z0-9._-]*")
/** What a removed browser hears before its stream ends. */
private const val REMOVED = """{"type":"removed"}"""
/** Presentations that ended, kept so their browsers hear it ended rather than count as guessing at the next one's code. */
private const val ENDED_PRESENTATIONS = 16

/**
 * A presentation any browser on the local network can follow, such as a
 * laptop on a projector, or phones in the room: the phone serves the
 * audience's page and sends each step to everyone watching. It runs only
 * while turned on during a presentation, at an address with a six-digit
 * code, so joining takes seeing the presenter's screen; a device that
 * types too many wrong codes waits before it may try again. Who may join,
 * and how, is up to the presenter ([joining]).
 *
 * `asset` reads one of the app's map assets by name, and `words` its
 * strings. `policy` says which connections it takes: the room's, unless a
 * test over this machine's loopback says otherwise. `clock` reads
 * milliseconds, for where a video is when a browser joins: the phone's own
 * clock, which its reckoning uses.
 */
class BrowserPresenting internal constructor(
    private val asset: (String) -> String,
    private val words: Words,
    private val mapFolder: File,
    private val scope: CoroutineScope,
    options: JoiningOptions,
    private val preferredPort: Int = PORT,
    policy: ConnectionPolicy = RoomNetworks(),
    private val clock: () -> Long = { System.nanoTime() / 1_000_000 },
) {
    constructor(context: Context, mapFolder: File, scope: CoroutineScope, options: JoiningOptions) :
        this(
            { name -> context.assets.open("map/$name").bufferedReader().use { it.readText() } },
            ResourceWords(context),
            mapFolder,
            scope,
            options,
            clock = SystemClock::elapsedRealtime,
        )

    val code: String = newCode()

    private val guard = CodeGuard()

    /** When a device was last kept from guessing the code, or 0: for the presenter to see. */
    val lockedOut: StateFlow<Long> = guard.lockedOut

    /** Who's following, and who may join. */
    val joining = asset.let { read -> Joining(code, RoomKind.Presentation, options, template = { read("join.html") }, words = words) }

    /** Each browser's stream, and the member it belongs to. */
    private val streams = ConcurrentHashMap<EventStream, Long>()

    /** The latest of each kind of command, for a browser that joins halfway. */
    private val latest = ConcurrentHashMap<String, String>()

    /** The picture or video over the map, and where a video is, reckoned from what was sent: a browser that joins halfway opens it there. */
    @Volatile private var shown: ShownMedia? = null

    private val watchingNow = MutableStateFlow(0)
    private var tidying: Job? = null

    /** How many browsers are following. */
    val watching: StateFlow<Int> = watchingNow.asStateFlow()

    private val server = LocalServer(
        bodyLimit = { method, _ -> if (method == "POST") FORM_LIMIT else 0L },
        policy = policy,
    ) { request -> answer(request) }

    val audience: Audience = object : Audience {
        override val name = "browsers"

        override fun send(command: AudienceCommand) {
            val json = encode(command)

            when (command) {
                is AudienceCommand.Show -> {
                    latest.clear()
                    latest[SHOW] = json
                }
                is AudienceCommand.Captions -> latest[CAPTIONS] = json
                is AudienceCommand.Step -> latest[STEP] = json
                AudienceCommand.End -> latest.clear()
                is AudienceCommand.Media, is AudienceCommand.Playback, is AudienceCommand.Seek, AudienceCommand.CloseMedia -> Unit
            }

            shown = shown.after(command, clock())
            streams.keys.forEach { it.send(json) }
        }

        override fun reckon(media: ShownMedia?) {
            shown = media
        }

        override fun close() {
            send(AudienceCommand.End)
        }
    }

    fun start() {
        server.start(preferredPort)

        tidying = scope.launch {
            while (true) {
                delay(TIDY_MILLISECONDS)
                joining.tidy()
            }
        }
    }

    fun stop() {
        tidying?.cancel()
        joining.end()
        synchronized(ended) { ended[code] = joining }
        streams.keys.forEach { it.close() }
        server.stop()
    }

    /** What to type in a browser, or null when the phone isn't on a network others can reach. */
    fun address(): String? = localAddress()?.let { "http://$it:${server.port}/$code" }

    /** The port it listens on, once started. */
    internal val port: Int get() = server.port

    /** Takes someone out of the presentation: their browser says so and stops following, and their address can't join again. */
    fun remove(member: Member) {
        val gone = joining.remove(member.id)

        streams.forEach { (stream, id) ->
            if (id in gone) {
                stream.send(REMOVED)
                stream.close()
            }
        }
    }

    private suspend fun answer(request: Request): Response {
        if (request.method != "GET" && request.method != "POST") {
            return Response.text(405, "Only reading here")
        }

        val parts = request.path.trim('/').split('/').filter { it.isNotEmpty() }
        val first = parts.firstOrNull()

        if (first != code) {
            if (looksLikeCode(first) && guard.waitFor(request.from) > 0) {
                return codePage(429, guard.waitingMessage(request.from, words))
            }

            // A browser still following an earlier presentation, as it tries again and again, hears that it ended.
            val earlier = first?.let { synchronized(ended) { ended[it] } }

            if (earlier != null) {
                return earlier.serve(request, parts.drop(1)) { _, _ -> Response.notFound() }
            }

            // Someone who typed only the address, or an old code: ask for the one on the phone.
            if (!looksLikeCode(first)) {
                return if (request.method == "GET") codePage(200, "") else Response.text(405, "Only reading here")
            }

            return unknownRoom(request, parts.drop(1), guard, words, R.string.nearby_code_is_not_on_the_presenter_phone) { status, problem ->
                codePage(status, problem)
            }
        }

        // Someone already in is never kept out by wrong codes, theirs or anyone else's.
        if (joining.member(request) == null) {
            if (guard.waitFor(request.from) > 0) {
                return codePage(429, guard.waitingMessage(request.from, words))
            }

            guard.right(request.from)
        }

        folderRedirect(request.path)?.let { return Response.redirect(it) }
        return joining.serve(request, parts.drop(1)) { joined, rest -> content(joined, rest) }
    }

    private fun content(member: Member, rest: List<String>): Response = when (rest.firstOrNull()) {
        null -> Response.page(200, audiencePage())
        "browser.js" -> Response.Bytes(200, "text/javascript; charset=utf-8", asset("browser.js").toByteArray())
        "events" -> Response.Events(follow(member))
        "media" -> media(rest.getOrNull(1))
        else -> Response.notFound()
    }

    /** A member's stream, which first catches up with the presentation so far. */
    private fun follow(member: Member): EventStream {
        lateinit var stream: EventStream

        stream = EventStream {
            if (streams.remove(stream) != null) {
                joining.streamClosed(member.id)
            }

            watchingNow.value = streams.size
        }

        listOf(SHOW, CAPTIONS, STEP).mapNotNull { latest[it] }.forEach(stream::send)
        // A video playing, at about where it is now.
        shown?.takeIf { latest.containsKey(SHOW) }?.let { stream.send(encode(it.command(clock()))) }
        streams[stream] = member.id
        joining.streamOpened(member.id)
        watchingNow.value = streams.size

        // Removed while this stream was starting: it hears so, like the others.
        if (!joining.has(member.id)) {
            stream.send(REMOVED)
            stream.close()
        }

        return stream
    }

    /** A picture or video of the map; a video in the parts the browser asks for, as it plays and seeks. */
    private fun media(name: String?): Response {
        if (name == null || !MEDIA_NAME.matches(name)) {
            return Response.notFound()
        }

        // Only what a map's media folder keeps: pictures and videos, by their types.
        val type = pictureType(name)

        if (!type.startsWith("image/") && !type.startsWith("video/")) {
            return Response.notFound()
        }

        val file = File(File(mapFolder, "media"), name)
        return if (file.isFile) Response.FromFile(type, file) else Response.notFound()
    }

    private fun encode(command: AudienceCommand): String = protocolJson.encodeToString(AudienceCommand.serializer(), command)

    /**
     * The audience's page in the presenter's language: its words for the
     * page's script, and its waiting text, which shows before the script
     * has come over the network.
     */
    private fun audiencePage(): String {
        val audienceWords = words.pageWords()
        return fill(
            asset("browser.html"),
            mapOf(
                htmlIn(audienceWords.language),
                "<!-- waiting -->" to escapeHtml(audienceWords.waiting),
                "<!-- words -->" to jsonScript("orbit-words", protocolJson.encodeToString(PageWords.serializer(), audienceWords)),
            ),
        )
    }

    private fun codePage(status: Int, problem: String): Response =
        codePage(asset("join.html"), status, words.string(R.string.nearby_code_on_the_presenter_phone), problem, words.language)

    private companion object {
        const val SHOW = "show"
        const val CAPTIONS = "captions"
        const val STEP = "step"

        /** Presentations this launch of Orbit showed in browsers and ended, by their codes. */
        val ended = object : LinkedHashMap<String, Joining>() {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Joining>?): Boolean = size > ENDED_PRESENTATIONS
        }
    }
}
