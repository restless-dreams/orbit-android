package com.hyuchia.orbit.nearby

import android.content.Context
import android.os.SystemClock
import com.hyuchia.orbit.R
import com.hyuchia.orbit.ResourceWords
import com.hyuchia.orbit.Words
import com.hyuchia.orbit.data.MEDIA_DIRECTORY
import com.hyuchia.orbit.data.MediaNames
import com.hyuchia.orbit.data.OutgoingCopies
import com.hyuchia.orbit.data.isSoundFile
import com.hyuchia.orbit.data.mediaType
import com.hyuchia.orbit.data.soundNamesIn
import com.hyuchia.orbit.data.withoutFiles
import com.hyuchia.orbit.page.AudienceCommand
import com.hyuchia.orbit.page.PageWords
import com.hyuchia.orbit.page.escapeHtml
import com.hyuchia.orbit.page.fill
import com.hyuchia.orbit.page.htmlIn
import com.hyuchia.orbit.page.jsonScript
import com.hyuchia.orbit.page.pageWords
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
import java.net.InetAddress
import java.util.concurrent.ConcurrentHashMap

/** The port browsers find presentations at: the same every time, so a room's address doesn't change between talks. */
private const val PORT = 8765
/** The most a browser sends: the join form, with a name and a PIN. */
private const val FORM_LIMIT = 4L * 1024
/** How often people whose browsers stopped following are let go, to free their places. */
private const val TIDY_MILLISECONDS = 15_000L
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
 * strings. `policy` says which connections it takes, and `listenOn` where
 * it listens: the room's, unless a test over this machine's loopback says
 * otherwise. `clock` reads milliseconds, for where a video is when a
 * browser joins: the phone's own clock, which its reckoning uses.
 * `outgoing` gives the file to send for one of the map's: in the app, a
 * copy without what its file says beyond what it shows ([OutgoingCopies]),
 * or the file as it is when it can't go clean and it's among `asTheyAre`,
 * the files the presenter chose to show as they are.
 */
class BrowserPresenting internal constructor(
    private val asset: (String) -> String,
    private val words: Words,
    private val mapFolder: File,
    private val scope: CoroutineScope,
    options: JoiningOptions,
    private val preferredPort: Int = PORT,
    policy: ConnectionPolicy = RoomNetworks(),
    private val listenOn: () -> List<InetAddress>? = ::roomAddresses,
    private val clock: () -> Long = { System.nanoTime() / 1_000_000 },
    private val outgoing: (File) -> File? = { it },
) {
    constructor(context: Context, mapFolder: File, scope: CoroutineScope, options: JoiningOptions, asTheyAre: () -> Set<String> = { emptySet() }) :
        this(
            { name -> context.assets.open("map/$name").bufferedReader().use { it.readText() } },
            ResourceWords(context),
            mapFolder,
            scope,
            options,
            clock = SystemClock::elapsedRealtime,
            outgoing = OutgoingCopies(File(context.cacheDir, "outgoing")).let { copies -> { file: File -> copies.of(file, asTheyAre()) } },
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

    /**
     * Held while a command goes out and while a browser catches up and joins
     * the streams, so a browser hears each command once: in what it catches
     * up with, or as it's sent, never in neither.
     */
    private val sending = Any()

    /** The files the map being presented plays as sounds: they play from the presenter's phone, and stay there. */
    @Volatile private var sounds: Set<String> = emptySet()

    /**
     * The files of its media folder the map being presented shows
     * ([MediaNames.references]): the room gets none but these, as a zip of the
     * map carries none but these. The folder keeps others for the map's
     * earlier versions, such as a picture taken off it, which stay on the
     * phone whatever name a browser guesses, and its ideas' files, which stay
     * there too ([withoutFiles]).
     */
    @Volatile private var shownFiles: Set<String> = emptySet()

    /** The picture or video over the map, and where a video is, reckoned from what was sent: a browser that joins halfway opens it there. */
    @Volatile private var shown: ShownMedia? = null

    private val watchingNow = MutableStateFlow(0)
    private var tidying: Job? = null

    /** How many browsers are following. */
    val watching: StateFlow<Int> = watchingNow.asStateFlow()

    private val server = LocalServer(
        bodyLimit = { method, _ -> if (method == "POST") FORM_LIMIT else 0L },
        policy = policy,
        words = words,
    ) { request -> answer(request) }

    val audience: Audience = object : Audience {
        override val name = "browsers"

        override fun send(command: AudienceCommand) {
            val json = encode(command)
            // What a map points at is worked out before the lock: a big map's text takes a while to read.
            val files = (command as? AudienceCommand.Show)?.let { soundNamesIn(it.markdown).toSet() to MediaNames.references(withoutFiles(it.markdown)).toSet() }

            synchronized(sending) {
                when (command) {
                    is AudienceCommand.Show -> {
                        latest.clear()
                        latest[SHOW] = json

                        files?.let { (soundFiles, pointedAt) ->
                            sounds = soundFiles
                            shownFiles = pointedAt
                        }
                    }
                    is AudienceCommand.Captions -> latest[CAPTIONS] = json
                    is AudienceCommand.Step -> latest[STEP] = json
                    AudienceCommand.End -> latest.clear()
                    is AudienceCommand.Media, is AudienceCommand.Playback, is AudienceCommand.Seek, AudienceCommand.CloseMedia -> Unit
                }

                shown = shown.after(command, clock())
                // Streams only queue what they're sent, so the lock is held for no write.
                streams.keys.forEach { it.send(json) }
            }
        }

        override fun reckon(media: ShownMedia?) {
            synchronized(sending) { shown = media }
        }

        override fun close() {
            send(AudienceCommand.End)
        }
    }

    fun start() {
        server.start(preferredPort, listenOn)

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

    /** What to type in a browser, or null when the phone isn't on a network others can reach, so nothing listens. */
    fun address(): String? = if (server.isRunning) localAddress()?.let { "http://$it:${server.port}/$code" } else null

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
        MEDIA_DIRECTORY -> media(rest.getOrNull(1))
        else -> Response.notFound()
    }

    /**
     * A member's stream, which first catches up with the presentation so
     * far, and from then on hears each command: at once, as one step, so a
     * command sent meanwhile is in one or the other.
     */
    private fun follow(member: Member): EventStream {
        lateinit var stream: EventStream

        stream = EventStream {
            if (streams.remove(stream) != null) {
                joining.streamClosed(member.id)
            }

            watchingNow.value = streams.size
        }

        synchronized(sending) {
            listOf(SHOW, CAPTIONS, STEP).mapNotNull { latest[it] }.forEach(stream::send)
            // A video playing, at about where it is now.
            shown?.takeIf { latest.containsKey(SHOW) }?.let { stream.send(encode(it.command(clock()))) }
            streams[stream] = member.id
        }

        joining.streamOpened(member.id)
        watchingNow.value = streams.size

        // Removed while this stream was starting: it hears so, like the others.
        if (!joining.has(member.id)) {
            stream.send(REMOVED)
            stream.close()
        }

        return stream
    }

    /** A picture or video the map points at; a video in the parts the browser asks for, as it plays and seeks. */
    private fun media(name: String?): Response {
        if (name == null || !MediaNames.NAME.matches(name)) {
            return Response.notFound()
        }

        // Only while the map is shown. Once the presentation ended, nothing of it goes: as when a private map locks
        // with the phone's screen off, and the room hears the end before Orbit is back on screen to stop listening.
        if (!latest.containsKey(SHOW)) {
            return Response.notFound()
        }

        // Only what the map shows: its pictures, a video's poster, a live photo's motion, the pictures in its notes.
        if (name !in shownFiles) {
            return Response.notFound()
        }

        // Only what a map's media folder keeps: pictures and videos, by their types. Its sounds play from the
        // presenter's phone, and a voice memo is an `.mp4` too: what Orbit named a sound, or the map plays as one, stays.
        val type = mediaType(name)

        if ((!type.startsWith("image/") && !type.startsWith("video/")) || isSoundFile(name) || name in sounds) {
            return Response.notFound()
        }

        val file = File(File(mapFolder, MEDIA_DIRECTORY), name)

        if (!file.isFile) {
            return Response.notFound()
        }

        // The room gets it as it would in a zip, without where it was taken; one that can't be read through doesn't go.
        val sent = outgoing(file) ?: return Response.notFound()

        // A big video's copy takes a while: a presentation that ended meanwhile, as when a private map locks, gives out
        // nothing of it.
        if (!latest.containsKey(SHOW)) {
            return Response.notFound()
        }

        return Response.FromFile(type, sent)
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
