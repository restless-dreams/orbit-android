package com.hyuchia.orbit.nearby

import android.annotation.SuppressLint
import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Build
import android.provider.Settings
import com.hyuchia.orbit.Logs
import com.hyuchia.orbit.R
import com.hyuchia.orbit.ResourceWords
import com.hyuchia.orbit.Words
import com.hyuchia.orbit.data.BUNDLE_SIZE
import com.hyuchia.orbit.data.MAP_FORMAT
import com.hyuchia.orbit.data.MEDIA_DIRECTORY
import com.hyuchia.orbit.data.MapBundle
import com.hyuchia.orbit.data.MapRepository
import com.hyuchia.orbit.data.MapSummary
import com.hyuchia.orbit.data.NoRoomForZipException
import com.hyuchia.orbit.data.RefusedZipException
import com.hyuchia.orbit.data.VersionReason
import com.hyuchia.orbit.data.carriedMediaTypes
import com.hyuchia.orbit.data.requireRoom
import com.hyuchia.orbit.page.AppTheme
import com.hyuchia.orbit.page.WebPage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream
import java.net.InetAddress
import java.net.URLEncoder
import java.security.KeyPair
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.zip.ZipException

/** The port other devices send maps to, and computers open shared maps at. */
private const val PORT = 8766
/** How long a device that showed its check code waits for the map, while the person sending checks the codes. */
const val CONFIRMING_LIFETIME_MILLISECONDS = 3 * 60 * 1000L
/** Room a map has to leave on this device, besides itself, for it to be taken: nothing fills the storage to the brim. */
internal const val SPARE_SPACE = 32L * 1024 * 1024
/** Sender keys remembered this launch, so an offer copied off the network can't be sent here again. */
private const val SENDER_KEYS_REMEMBERED = 1_000
/** Offers one device may have waiting here at once. */
private const val OFFERS_PER_DEVICE = 1
/** Offers waiting here at once, from every device together. */
private const val OFFERS_WAITING = 3
/** The longest name a sender shows up with, in characters. */
const val SENDER_NAME_LENGTH = 40
/** The longest name an advertisement carries: a DNS label, in bytes. */
private const val SERVICE_NAME_BYTES = 63
/** The most any request but a map's sends: an offer's hello, a shared map's join form, with a name and a PIN, or a sender's half of a check code. */
private const val FORM_LIMIT = 4L * 1024
/** Shares that ended, kept so their browsers hear it ended rather than that the code is wrong. */
private const val ENDED_SHARES = 16
private const val TAG = "NearbyHost"

/**
 * The largest map, sealed, a device takes from another: a map's zip with all
 * a map may hold ([BUNDLE_SIZE]), its pictures, videos and sounds, and what
 * sealing adds. It opens as it arrives, straight from the connection, into
 * files, so it's never in memory whole.
 */
const val OFFER_LIMIT = BUNDLE_SIZE + 32L * 1024 * 1024

/**
 * The largest map Orbit 0.3.0 and 0.4.0 take, sealed. Neither says it: a
 * device that doesn't is sent no bigger a map ([DeviceReads]).
 */
const val OFFER_LIMIT_UP_TO_0_4_0 = 64L * 1024 * 1024

/** How Orbit advertises itself to devices on the local network. */
const val SERVICE_TYPE = "_orbit._tcp."
/** The advertisement's attribute naming this launch of Orbit, so a device doesn't list itself. */
const val SERVICE_ID = "id"
/** The advertisement's attribute with the key maps sent to this launch of Orbit are sealed to. */
const val SERVICE_KEY = "key"
/** The advertisement's attribute with the version of offers this launch of Orbit takes, [OFFER_VERSION]. Orbit 0.2.0 and earlier have none. */
const val SERVICE_VERSION = "version"

/** What the advertisement carries besides the name: this launch's id, its key for offers, and the version they're made with. */
internal fun serviceAttributes(selfId: String, key: DeviceKey): Map<String, String> =
    mapOf(SERVICE_ID to selfId, SERVICE_KEY to key.text, SERVICE_VERSION to OFFER_VERSION.toString())

/**
 * A map another device offered, waiting for an answer here: what it is
 * (its text, and how many pictures come with it), who says they sent it
 * and from which address. `existing` is this device's copy of the same
 * map, if it has one. The map's pictures, videos and sounds wait in the
 * files the bundle was unpacked to until it's taken in, and go if it isn't.
 * `check` is the code the sender shows too, for the person to see it's
 * their device; `session` proves the answer to it.
 */
class Offer internal constructor(
    val id: String,
    val from: String,
    val address: String,
    val bundle: MapBundle,
    val existing: String?,
    val check: String,
    internal val session: OfferSession,
)

/**
 * A map on its way from another device, which says it's `from` and is at
 * `address`: both devices show `check`, and the map comes once the person
 * sending sees the codes match, if it starts coming before `until`, on the
 * host's clock. It's an [Offer] once it arrives.
 */
class Incoming internal constructor(
    val id: String,
    val from: String,
    val address: String,
    val check: String,
    internal val session: OfferSession,
    internal val until: Long,
) {
    /** Whether the map is arriving now: only one upload at a time. */
    internal var uploading = false
}

/** Where an offer is, as `GET /offer/<id>` says: its code shown and the map on its way, the map waiting for an answer, or answered. */
enum class OfferState {
    Confirming, Waiting, Opened, Declined;

    /** Whether it's answered: opened or declined, and over. */
    val settled: Boolean get() = this == Opened || this == Declined
}

/** How long each stage of an offer lasts here, in milliseconds. */
internal data class OfferTimes(
    /** An offer's hello waits this long for the sender's half of its check code, which the sender sends at once. */
    val arriving: Long = 60 * 1000L,

    /** A device that showed its check code waits this long for the map to start coming, while the person sending checks the codes. */
    val confirming: Long = CONFIRMING_LIFETIME_MILLISECONDS,

    /** A map waits this long for an answer before it's dropped. */
    val waiting: Long = 10 * 60 * 1000L,

    /** An offer answered, opened or declined, is still said to its sender this long. */
    val settled: Long = 10 * 60 * 1000L,
)

/** A map shown to computers' browsers at a code, and who may open it. */
class SharedMap internal constructor(
    val code: String,
    internal val mapId: String,
    internal val theme: AppTheme,
    val joining: Joining,

    /** Where its pages are made, a file for each version of the map. */
    private val pages: File,

    /** The map's files that can't go clean which the person sharing chose to share as they are: its page and zip carry them. */
    internal val asTheyAre: Set<String> = emptySet(),
) {
    private val building = Mutex()

    /** The page last made. */
    private var latest: SharedPage? = null

    /** The page made before it, which answers already on their way may still be sending. */
    private var before: SharedPage? = null

    /** Whether the share ended: its pages are gone, and one made after that goes once it's sent. */
    private var ended = false

    /**
     * The map's page for its version `version`, its text and files as they
     * are: made once for each version, one at a time, by `write`, into a
     * file of its own, which gives the scripts the page runs. So browsers
     * reloading it, or many opening it at once, don't each make one, every
     * picture inside, and none is ever whole in memory: a page with a map's
     * sounds and live photos inside comes to over 100 MB. A version's file
     * goes once two newer ones are made, or as the share ends. What else
     * goes into it, the files shared as they are and the theme, is this
     * share's own.
     */
    internal suspend fun page(version: String, write: suspend (File) -> List<String>): SharedPage = building.withLock {
        synchronized(this) { latest }?.takeIf { it.version == version && it.file.isFile }?.let { return@withLock it }
        pages.mkdirs()
        val file = File(pages, "$code-${UUID.randomUUID()}.html")
        var made = false

        try {
            val page = SharedPage(version, file, write(file))
            made = true

            synchronized(this) {
                if (ended) {
                    // Made for an answer as the share ended: it's sent this once, and goes.
                    return@withLock page.copy(temporary = true)
                }

                before?.file?.delete()
                before = latest
                latest = page
            }

            page
        } finally {
            if (!made) {
                file.delete()
            }
        }
    }

    /** Lets its pages go, as the share ends. */
    internal fun forget() = synchronized(this) {
        ended = true
        listOfNotNull(latest, before).forEach { it.file.delete() }
        latest = null
        before = null
    }
}

/**
 * A shared map's page, made for the map's version `version`, in `file`,
 * with the scripts it runs, for its content policy. A `temporary` one was
 * made for one answer alone, and goes once it's sent.
 */
internal data class SharedPage(val version: String, val file: File, val scripts: List<String>, val temporary: Boolean = false)

/**
 * Which version of a map a computer's page is made from: its text, and its
 * files as they are now, so a picture that reaches the folder late, from a
 * sync app, makes a new page too.
 */
internal fun pageVersion(summary: MapSummary?, folder: File): String {
    val files = File(folder, MEDIA_DIRECTORY).listFiles().orEmpty().sortedBy { it.name }.joinToString("|") { "${it.name}:${it.length()}:${it.lastModified()}" }
    return "${summary?.contentKey}:${summary?.modified}:$files"
}

/**
 * What a sender calls itself, made safe to show: no control characters, no
 * characters that reorder or hide text, spaces run together, and short.
 * Null when nothing's left.
 */
fun senderName(claimed: String?): String? {
    if (claimed == null) {
        return null
    }

    val kept = StringBuilder()
    var space = false
    var count = 0
    var index = 0

    while (index < claimed.length && count < SENDER_NAME_LENGTH) {
        val codePoint = claimed.codePointAt(index)
        index += Character.charCount(codePoint)

        when {
            Character.isWhitespace(codePoint) || Character.isSpaceChar(codePoint) -> space = kept.isNotEmpty()
            isHidden(codePoint) -> Unit
            else -> {
                if (space) {
                    kept.append(' ')
                    count += 1
                    space = false
                }

                kept.appendCodePoint(codePoint)
                count += 1
            }
        }
    }

    return kept.toString().trim().ifEmpty { null }
}

/** `name` cut to fit an advertisement, never in the middle of a character. */
internal fun serviceName(name: String): String {
    var end = 0
    var bytes = 0

    while (end < name.length) {
        val codePoint = name.codePointAt(end)

        val size = when {
            codePoint < 0x80 -> 1
            codePoint < 0x800 -> 2
            codePoint < 0x10000 -> 3
            else -> 4
        }

        if (bytes + size > SERVICE_NAME_BYTES) {
            break
        }

        bytes += size
        end += Character.charCount(codePoint)
    }

    return name.substring(0, end).trim()
}

/** Characters that don't show as themselves: controls, direction marks and overrides, zero-width ones, and ones not yet assigned. */
private fun isHidden(codePoint: Int): Boolean = when (Character.getType(codePoint).toByte()) {
    Character.CONTROL, Character.FORMAT, Character.UNASSIGNED, Character.PRIVATE_USE, Character.SURROGATE,
    Character.LINE_SEPARATOR, Character.PARAGRAPH_SEPARATOR -> true
    else -> false
}

/** A map's stream as a zip reads it, left open for what's after the zip: closing it would stop it before its end is checked. */
private class KeptOpen(input: InputStream) : FilterInputStream(input) {
    override fun close() = Unit
}

/** Reads what's left of a stream, as an opened map's last chunk is checked as it's read. */
private fun InputStream.readToEnd() {
    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
    var read = 0

    while (read >= 0) {
        read = read(buffer)
    }
}

/** How long the phone keeps quiet after saying a device's Orbit is too old to send it maps. */
private const val OLDER_SENDER_QUIET_MILLISECONDS = 10 * 60 * 1000L

/**
 * Room left now in the storage `folder` is on. Offers and shared maps' zips
 * are written as they arrive, with nothing set aside first, so only what's
 * free now counts: not caches the system could clear if room were asked for.
 */
@SuppressLint("UsableSpace")
private fun roomIn(folder: File): Long = folder.usableSpace

/**
 * This device on the local network, while Orbit is on screen and only when
 * there's a reason to be: it tells nearby devices it's here and takes the
 * maps they offer (each one waits for a yes) when the settings allow, and
 * shows a map to computers' browsers at a six-digit code while asked to.
 * Otherwise nothing listens. `policy` says which connections it takes: the
 * room's, unless a test over this machine's loopback says otherwise; and
 * `words` what its pages say. An offer's stages last `times`, on `clock`,
 * `room` says how much room is left where offers and shared maps' zips are
 * made, and `mapLimit` is the largest map it takes, sealed, as it tells
 * senders.
 */
class NearbyHost internal constructor(
    private val context: Context,
    private val maps: MapRepository,
    private val scope: CoroutineScope,
    policy: ConnectionPolicy,
    private val words: Words,
    private val listenOn: () -> List<InetAddress>?,
    private val times: OfferTimes = OfferTimes(),
    private val clock: () -> Long = { System.nanoTime() / 1_000_000 },
    private val room: () -> Long = { roomIn(context.cacheDir) },
    private val mapLimit: Long = OFFER_LIMIT,
) {
    constructor(context: Context, maps: MapRepository, scope: CoroutineScope) :
        this(context, maps, scope, RoomNetworks(), ResourceWords(context), ::roomAddresses)

    /** This launch of Orbit, so it doesn't list itself among nearby devices: new each launch, so it can't follow the device around. */
    val selfId: String = UUID.randomUUID().toString()

    /** The device's own name, as its settings have it, or its model: what it's called on the network unless the person chose a name. */
    val deviceName: String by lazy { deviceName(context) }

    @Volatile
    private var chosenName: String? = null

    /** What other devices see this one called, in its advertisement and on the maps it sends. */
    val name: String get() = nameFor(chosenName)

    /** What other devices see this one called when the person chose `chosen`, or nothing. */
    fun nameFor(chosen: String?): String = senderName(chosen) ?: senderName(deviceName) ?: "Orbit"

    /**
     * Whether a map is private and locked now. A map shown to computers stops being served as it locks, as a
     * presentation stops: browsers reach this server, whatever the phone's screen shows. The app sets it.
     */
    @Volatile
    var hidden: (mapId: String) -> Boolean = { false }

    /** This launch's key pair for offers: maps sent here are sealed to its public half, so only this launch can open them. */
    private val keys: KeyPair by lazy { newKeyPair() }

    /** The key maps sent here are sealed to, as the advertisement and `GET /key` give it. */
    val key: DeviceKey by lazy { DeviceKey.of(keys) }

    private val offersFolder = File(context.cacheDir, "offers")

    /** The zips of shared maps on their way to browsers. */
    private val sharedFolder = File(context.cacheDir, "shared-maps")

    /** The pages of shared maps, a file for each version of each map. */
    private val sharedPages = File(context.cacheDir, "shared-pages")

    // Only a map sent here is big: an offer's hello, a shared map's join form and a sender's half of a check code are a few lines.
    private val server = LocalServer(
        bodyLimit = { method, path -> if (method == "POST" && isMapUpload(path)) mapLimit else FORM_LIMIT },
        spool = offersFolder,
        policy = policy,
        words = words,
        admit = ::admit,
    ) { answer(it) }
    private val guard = CodeGuard()
    private val waiting = MutableStateFlow<List<Offer>>(emptyList())
    private val announced = MutableStateFlow<List<Incoming>>(emptyList())
    private val olderSenders = MutableStateFlow(0L)
    private val states = OfferStates(clock, times.settled)
    private val arriving = ConcurrentHashMap<String, Arriving>()

    /** Where the offers being opened now came from. Guards the places offers take under the limits, with [arriving] and [usedKeys]. */
    private val opening = mutableListOf<String>()

    /** The sender keys of offers opened this launch, oldest first. */
    private val usedKeys = LinkedHashSet<String>()

    /** Room the maps arriving now will still take, guarded by [opening]: each opens into files as it arrives. */
    private var reserved = 0L

    private val shares = ConcurrentHashMap<String, SharedMap>()

    /** The zips of the maps shown to computers, by their codes: one for each version of the map, for every browser. */
    private val zips = HashMap<String, SharedZip>()

    private val endedShares = object : LinkedHashMap<String, Joining>() {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Joining>?): Boolean = size > ENDED_SHARES
    }
    private var advertisement: NsdManager.RegistrationListener? = null
    private var advertisedPort = 0
    private var onScreen = false
    private var accepting = false

    /** Maps offered here, oldest first, waiting for an answer. */
    val offers: StateFlow<List<Offer>> = waiting.asStateFlow()

    /** Maps on their way here, oldest first: both devices show their codes, and the maps come once the people sending check them. */
    val incoming: StateFlow<List<Incoming>> = announced.asStateFlow()

    /** When a device with an Orbit too old to check codes first last tried to send a map here, or 0: for the phone to say so. */
    val olderSender: StateFlow<Long> = olderSenders.asStateFlow()

    /** When a device was last kept from guessing a share's code, or 0: for the phone to say so. */
    val lockedOut: StateFlow<Long> = guard.lockedOut

    /** Whether other devices can reach this one now, at [address]. */
    val listening: StateFlow<Boolean> = server.listening

    /**
     * An offer's hello, opened whole, until the sender's half of its check
     * code arrives: only then do both devices show the code. It holds a
     * place under the limits until then, and to the end of its time when the
     * half is wrong, so no one can try hello after hello for a code that matches.
     */
    private class Arriving(
        val id: String,
        val from: String,
        val address: String,
        val session: OfferSession,
        val commitment: ByteArray,
        val receiverNonce: ByteArray,
    ) {
        /** Whether a half that wasn't the one committed to came for it: it's dropped, and only keeps its place. */
        var refused = false
    }

    init {
        scope.launch(Dispatchers.IO) {
            // Offers that waited when Orbit last stopped, shared maps it was sending, and the lasting id earlier versions kept.
            offersFolder.listFiles().orEmpty().forEach { it.delete() }
            sharedFolder.listFiles().orEmpty().forEach { it.delete() }
            sharedPages.listFiles().orEmpty().forEach { it.delete() }
            File(context.filesDir, "nearby-id").delete()
        }
    }

    /** Orbit is on screen: `accepting` advertises this device and takes offers. Calling it again changes only that. */
    fun start(accepting: Boolean) {
        onScreen = true
        this.accepting = accepting
        // Private maps shown to computers that locked while Orbit was away aren't served again as it comes back.
        shares.values.filter { hidden(it.mapId) }.forEach { end(it.code) }
        refresh()
    }

    /** Calls this device `chosen` on the network from now on, or by its own name when that's null. An advertisement under the old name is replaced. */
    fun rename(chosen: String?) {
        val advertised = advertisement?.let { name }
        chosenName = chosen

        if (advertised != null && advertised != name) {
            unadvertise()
            advertise()
        }
    }

    /** Orbit left the screen: nothing listens until it's back. */
    fun stop() {
        onScreen = false
        refresh()
    }

    /**
     * Listens while there's something to listen for, on the room's networks
     * alone, and advertises while offers are welcome and it's listening.
     */
    @Synchronized
    private fun refresh() {
        val listening = onScreen && (accepting || shares.isNotEmpty())

        if (listening) {
            // Joining or leaving a network starts the server again at its new addresses, or stops it: the advertisement follows.
            server.start(PORT, listenOn) { scope.launch { refresh() } }
        } else {
            server.stop()
        }

        if (listening && accepting && server.isRunning) {
            advertise()
        } else {
            unadvertise()
        }
    }

    /** Where other devices reach this one, such as 192.168.1.5:8766, while it's listening on a network they can reach. */
    fun address(): String? = if (server.isRunning) localAddress()?.let { "$it:${server.port}" } else null

    /** The port it listens on, while it does. */
    internal val port: Int get() = server.port

    /**
     * Takes in an offered map: opens it as a new one, updates this device's
     * copy, or keeps both. Returns its id, or null when it couldn't be taken
     * in (the offer is then dropped, and the sender hears it was declined).
     */
    suspend fun accept(offer: Offer, keepBoth: Boolean): String? {
        val id = try {
            // Its pictures, videos and sounds are in their files already: they move into the map.
            maps.receive(offer.bundle, replacing = if (keepBoth) null else offer.existing, reason = VersionReason.FromDevice(offer.from))
        } catch (failed: IOException) {
            Logs.warn(TAG, "Couldn't take in an offered map", failed)
            null
        }

        settle(offer, if (id != null) OfferState.Opened else OfferState.Declined)
        return id
    }

    fun decline(offer: Offer) {
        settle(offer, OfferState.Declined)
    }

    /** Refuses a map on its way here: the sender hears it was declined, and sends nothing. */
    fun decline(incoming: Incoming) {
        synchronized(opening) {
            drop(incoming)
        }
    }

    /** Drops a map on its way here: its offer is declined, and its place is free. Called holding [opening]. */
    private fun drop(incoming: Incoming) {
        states.set(incoming.id, OfferState.Declined, incoming.session)
        announced.update { list -> list.filterNot { it.id == incoming.id } }
    }

    /**
     * Shows a map to computers' browsers at a code, until [unshare]: to
     * explore, present or download it, for whoever `options` lets in. Its
     * files that can't go clean stay out of its page and zip, but those of
     * `asTheyAre`, which the person sharing chose to share as they are.
     */
    fun share(mapId: String, theme: AppTheme, options: JoiningOptions = JoiningOptions(askNames = false), asTheyAre: Set<String> = emptySet()): SharedMap {
        if (shares.isEmpty()) {
            // A new code: wrong guesses at old ones no longer keep anyone waiting.
            guard.reset()
        }

        var code = newCode()

        while (shares.containsKey(code)) {
            code = newCode()
        }

        val shared = SharedMap(code, mapId, theme, Joining(code, RoomKind.Share, options, template = { asset("join.html") }, words = words), sharedPages, asTheyAre)
        synchronized(endedShares) { endedShares.remove(code) }
        shares[code] = shared
        refresh()
        return shared
    }

    fun unshare(code: String) {
        end(code)
        refresh()
    }

    /** Ends the share at `code`: the browsers in it hear it ended, its code says so from then on, and its zips go. */
    private fun end(code: String) {
        shares.remove(code)?.let { shared ->
            shared.joining.end()
            shared.forget()
            synchronized(endedShares) { endedShares[code] = shared.joining }
        }

        synchronized(zips) { zips.remove(code) }?.end()
    }

    private fun settle(offer: Offer, state: OfferState) {
        states.set(offer.id, state, offer.session)
        waiting.update { offers -> offers.filterNot { it.id == offer.id } }
        // What it brought that's still in the files it arrived in, all of it when it was declined, goes now.
        maps.discard(offer.bundle)
    }

    private suspend fun answer(request: Request): Response {
        val parts = offerPath(request.path)

        return when {
            request.method == "GET" && request.path == "/key" -> key()
            request.method == "POST" && request.path == "/offer" -> hello(request)
            parts == null -> shared(request)
            request.method == "POST" && parts.second == null -> reveal(parts.first, request)
            request.method == "POST" && parts.second == "map" -> map(parts.first, request)
            request.method == "POST" && parts.second == "cancel" -> cancel(parts.first, request)
            request.method == "GET" && parts.second == null -> stateAnswer(parts.first)
            else -> Response.notFound()
        }
    }

    /**
     * Refuses at once, before reading any of it, an offer this device
     * wouldn't take: one from an Orbit too old to check codes first, any
     * while it takes no maps, and a map no device here is waiting for, or
     * with no room for it.
     */
    private fun admit(head: RequestHead): Admission {
        if (head.method != "POST") {
            return Admission.Read()
        }

        if (head.path == "/offer") {
            val version = head.headers[VERSION_HEADER.lowercase()]?.toIntOrNull()

            if (version != OFFER_VERSION && accepting) {
                // Orbit 0.2.0 and earlier send the map before the codes are checked: never read, and the phone says why.
                val now = System.currentTimeMillis()

                // Said at most once every ten minutes: a device on the Wi-Fi offering again and again can't keep the
                // phone's dialog up.
                if ((version == null || version < OFFER_VERSION) && now - olderSenders.value >= OLDER_SENDER_QUIET_MILLISECONDS) {
                    olderSenders.value = now
                }

                return Admission.Refuse(Response.text(400, "Update Orbit on the other device: maps sent here are checked first."))
            }

            return if (accepting) Admission.Read() else Admission.Refuse(Response.text(403, "This device isn't taking maps"))
        }

        val parts = offerPath(head.path)

        if (parts == null || parts.second != "map") {
            return Admission.Read()
        }

        if (!accepting) {
            return Admission.Refuse(Response.text(403, "This device isn't taking maps"))
        }

        return synchronized(opening) {
            val incoming = announced.value.firstOrNull { it.id == parts.first && it.address == head.from }
            // The map opens as it arrives, straight from the connection, into files: it takes room once, about its sealed size.
            val takes = head.length

            when {
                incoming == null -> Admission.Refuse(Response.notFound())
                incoming.uploading -> Admission.Refuse(Response.text(409, "The map is on its way already"))
                // Too late: the offer is over, as the device stopped showing its code.
                clock() >= incoming.until -> {
                    drop(incoming)
                    Admission.Refuse(Response.notFound())
                }
                room() - reserved < takes + SPARE_SPACE -> Admission.Refuse(Response.text(507, "There's no room for the map here"))
                else -> {
                    incoming.uploading = true
                    reserved += takes

                    Admission.Read(streamed = true) {
                        synchronized(opening) {
                            reserved -= takes
                            uploaded(incoming)
                        }
                    }
                }
            }
        }
    }

    /**
     * A map's upload is over, whether it arrived or not. One cut short once
     * its offer's time is up ends the offer: the time lets an upload that
     * started finish, never begin again. Called holding [opening].
     */
    private fun uploaded(incoming: Incoming) {
        incoming.uploading = false

        if (clock() >= incoming.until && announced.value.any { it === incoming }) {
            drop(incoming)
        }
    }

    /** The key maps sent here are sealed to, for senders without it: an address typed in, or an advertisement out of date. */
    private fun key(): Response {
        if (!accepting) {
            return Response.text(403, "This device isn't taking maps")
        }

        return json(offerJson.encodeToString(KeyAnswer.serializer(), KeyAnswer(OFFER_VERSION, key.text, MAP_FORMAT, carriedMediaTypes().sorted(), mapLimit, files = true)))
    }

    /**
     * An offer's hello, sealed to this launch's key: who the sender says it
     * is, and its commitment to its half of the check code. It waits for
     * that half before both devices show the code. Anything not sealed, or
     * that fails to open, is refused.
     */
    private suspend fun hello(request: Request): Response {
        if (!accepting) {
            return Response.text(403, "This device isn't taking maps")
        }

        val senderKey = DeviceKey.read(request.headers[KEY_HEADER.lowercase()])
            ?: return Response.text(400, "Update Orbit on the other device: maps sent here are checked first.")
        hold(request.from, senderKey)?.let { return it }
        val id = UUID.randomUUID().toString()
        var arrival: Arriving? = null

        try {
            arrival = open(request, senderKey, id)
        } catch (unreadable: UnreadableOfferException) {
            // Refused below.
        } finally {
            arrived(request.from, senderKey, arrival)
        }

        val opened = arrival ?: return Response.text(400, "The offer didn't arrive intact. Send it again.")

        scope.launch {
            delay(times.arriving)
            synchronized(opening) { arriving.remove(id) }
        }

        return json(offerJson.encodeToString(OfferAnswer.serializer(), OfferAnswer(id, base64Url(opened.receiverNonce), MAP_FORMAT, carriedMediaTypes().sorted(), mapLimit, files = true)))
    }

    /** Opens an offer's hello. Throws [UnreadableOfferException] when it doesn't open whole, or has anything after the commitment. */
    private suspend fun open(request: Request, senderKey: DeviceKey, id: String): Arriving {
        val session = OfferSession.forReceiver(keys, senderKey)

        val head = withContext(Dispatchers.IO) {
            // Read to its end, so the whole hello is checked before anything in it is used.
            session.open(request.bodyStream(), FORM_LIMIT).use { opened ->
                val head = OfferHead.read(opened)

                if (opened.read() >= 0) {
                    throw UnreadableOfferException("The hello goes on after its commitment")
                }

                head
            }
        }

        val from = senderName(head.from) ?: words.string(R.string.nearby_a_nearby_device)
        // Its half of the check code says this device proves its answers about the offer.
        return Arriving(id, from, request.from, session, head.commitment, receiverNonce())
    }

    /**
     * The map, sealed in the offer's session once the person sending saw
     * the codes match: opened as it arrives, straight from the connection,
     * and unpacked into files while there's room for it, it waits for an
     * answer here. One that fails to open, or isn't a map, is refused, and
     * the offer with it; so is one the storage runs out of room for as it
     * opens. Nothing of it counts until all of it has opened: a map that
     * doesn't become an offer, whatever stops it, the server stopping
     * included, leaves nothing behind.
     */
    private suspend fun map(id: String, request: Request): Response {
        val incoming = synchronized(opening) { announced.value.firstOrNull { it.id == id && it.address == request.from } } ?: return Response.notFound()
        var bundle: MapBundle? = null
        var offered = false
        // A map cut short on its way, as the connection failed, keeps its offer: it may come whole again while it has time.
        var cutShort = false

        try {
            bundle = try {
                withContext(Dispatchers.IO) {
                    incoming.session.openMap(request.bodyStream(), mapLimit).use { opened ->
                        val read = maps.readBundle(KeptOpen(opened), room = room)

                        try {
                            // Read to its end, so the whole map is checked before anything in it is used.
                            opened.readToEnd()
                        } catch (unchecked: IOException) {
                            read?.let(maps::discard)
                            throw unchecked
                        }

                        read
                    }
                }
            } catch (cut: BodyCutShortException) {
                cutShort = true
                return Response.text(400, "The map was cut short on its way. Send it again.")
            } catch (unreadable: UnreadableOfferException) {
                null
            } catch (full: NoRoomForZipException) {
                Logs.warn(TAG, "No room for an offered map", full)
                return Response.text(507, "There's no room for the map here")
            } catch (refused: RefusedZipException) {
                // Not a map's zip this device takes: too big unpacked, too many files, or a newer Orbit's.
                null
            } catch (damaged: ZipException) {
                null
            } catch (failed: IOException) {
                Logs.warn(TAG, "Couldn't keep an offered map", failed)
                return Response.text(507, "There's no room for the map here")
            }

            // The map here it would update, found before the lock: finding it may read the maps' details on disk.
            val here = bundle?.uuid?.let { uuid -> withContext(Dispatchers.IO) { maps.findByIdentity(uuid) } }

            val offer = synchronized(opening) {
                val still = announced.value.any { it === incoming }

                if (bundle == null || !still) {
                    return if (still) Response.text(400, "The map didn't arrive intact. Send it again.") else Response.notFound()
                }

                announced.update { list -> list.filterNot { it === incoming } }

                Offer(id, incoming.from, incoming.address, bundle, here, incoming.check, incoming.session).also { offer ->
                    states.set(id, OfferState.Waiting, incoming.session)
                    waiting.update { it + offer }
                    offered = true
                }
            }

            scope.launch {
                delay(times.waiting)

                if (states[offer.id] == OfferState.Waiting) {
                    settle(offer, OfferState.Declined)
                }
            }

            return stateAnswer(id)
        } finally {
            if (!offered) {
                bundle?.let(maps::discard)

                if (!cutShort) {
                    synchronized(opening) { announced.value.firstOrNull { it === incoming }?.let(::drop) }
                }
            }
        }
    }

    /**
     * The person sending saw the codes didn't match, or changed their mind,
     * before the map went: the offer goes, and its place with it. A hello
     * whose half didn't match keeps its place to the end of its time.
     */
    private fun cancel(id: String, request: Request): Response {
        synchronized(opening) {
            val hello = arriving[id]?.takeIf { it.address == request.from && !it.refused }
            val incoming = announced.value.firstOrNull { it.id == id && it.address == request.from }
            val session = hello?.session ?: incoming?.session ?: return Response.notFound()

            hello?.let { arriving.remove(it.id) }
            states.set(id, OfferState.Declined, session)
            announced.update { list -> list.filterNot { it.id == id } }
        }

        return stateAnswer(id)
    }

    /**
     * Holds a place under the limits for an offer from `address`, before
     * it's opened, so offers sent at once can't slip past them; or the
     * answer refusing it.
     */
    private fun hold(address: String, senderKey: DeviceKey): Response? = synchronized(opening) {
        val places = waiting.value.map { it.address } + announced.value.map { it.address } + arriving.values.map { it.address } + opening

        when {
            senderKey.text in usedKeys -> Response.text(400, "That map was sent here already.")
            places.size >= OFFERS_WAITING || places.count { it == address } >= OFFERS_PER_DEVICE ->
                Response.text(429, "Maps are waiting for an answer there already")
            else -> {
                opening += address
                usedKeys += senderKey.text
                null
            }
        }
    }

    /** An offer from `address` has opened (as `arrival`) or been refused (null), and leaves its place as it was being opened. */
    private fun arrived(address: String, senderKey: DeviceKey, arrival: Arriving?) {
        synchronized(opening) {
            opening.remove(address)

            if (arrival != null) {
                arriving[arrival.id] = arrival

                if (usedKeys.size > SENDER_KEYS_REMEMBERED) {
                    usedKeys.remove(usedKeys.first())
                }
            } else {
                // A key whose offer didn't open could be anyone's, so it doesn't push out the keys remembered.
                usedKeys.remove(senderKey.text)
            }
        }
    }

    /**
     * The sender's half of an offer's check code: when it's the half the
     * sender committed to, this device shows the code, and waits for the map
     * while the person sending checks it.
     */
    private fun reveal(id: String, request: Request): Response {
        val reveal = runCatching { offerJson.decodeFromString(Reveal.serializer(), request.body.decodeToString()) }.getOrNull()
        val senderNonce = fromBase64Url(reveal?.nonce)?.takeIf { it.size == CHECK_NONCE_BYTES }

        val incoming = synchronized(opening) {
            val arrival = arriving[id]?.takeIf { it.address == request.from && !it.refused } ?: return Response.notFound()

            if (senderNonce == null || !arrival.session.isCommitted(arrival.commitment, senderNonce)) {
                // Orbit never sends a wrong half, but someone trying for a code that matches the sender's would. Freeing
                // the place now would let them try again at once, so it stays taken until the hello's time is up.
                arrival.refused = true
                states.set(id, OfferState.Declined, arrival.session)
                return Response.text(400, "That isn't the check code the offer was made with")
            }

            arriving.remove(id)
            val check = arrival.session.checkCode(arrival.receiverNonce, senderNonce)

            Incoming(id, arrival.from, arrival.address, check, arrival.session, until = clock() + times.confirming).also { incoming ->
                states.set(id, OfferState.Confirming, arrival.session)
                announced.update { it + incoming }
            }
        }

        scope.launch {
            delay(times.confirming)

            // A map on its way by then may finish arriving: if it doesn't, its upload's end ends the offer.
            synchronized(opening) {
                if (!incoming.uploading && announced.value.any { it === incoming }) {
                    drop(incoming)
                }
            }
        }

        return stateAnswer(id)
    }

    private fun json(text: String): Response = Response.Bytes(200, "application/json", text.toByteArray())

    /** What a request about offer `id` answers: where it is, with the proof for its sender; not found once this device no longer says. */
    private fun stateAnswer(id: String): Response =
        states.answer(id)?.let { json(offerJson.encodeToString(StateAnswer.serializer(), it)) } ?: Response.notFound()

    /**
     * A shared map for a computer's browser: its web page at /<code>/, and
     * its bundle at /<code>/map.zip, for those who joined. A device that
     * types too many wrong codes waits before it may try again.
     */
    private suspend fun shared(request: Request): Response {
        if (request.method != "GET" && request.method != "POST") {
            return Response.text(405, "Not here")
        }

        val parts = request.path.trim('/').split('/').filter { it.isNotEmpty() }
        val first = parts.firstOrNull()
        var share = first?.let(shares::get)

        if (share != null && endedAsHidden(share)) {
            share = null
        }

        if (share == null) {
            if (looksLikeCode(first) && guard.waitFor(request.from) > 0) {
                return joinPage(429, guard.waitingMessage(request.from, words))
            }

            val ended = first?.let { synchronized(endedShares) { endedShares[it] } }

            if (ended != null) {
                // A share that ended says so, rather than counting as a wrong code.
                return ended.serve(request, parts.drop(1)) { _, _ -> Response.notFound() }
            }

            if (!looksLikeCode(first)) {
                return if (request.method == "GET") joinPage(200, "") else Response.text(405, "Not here")
            }

            return unknownRoom(request, parts.drop(1), guard, words, R.string.nearby_code_is_not_on_the_other_device) { status, problem ->
                joinPage(status, problem)
            }
        }

        // Someone already in is never kept out by wrong codes, theirs or anyone else's.
        if (share.joining.member(request) == null) {
            if (guard.waitFor(request.from) > 0) {
                return joinPage(429, guard.waitingMessage(request.from, words))
            }

            guard.right(request.from)
        }

        folderRedirect(request.path)?.let { return Response.redirect(it) }
        val answer = share.joining.serve(request, parts.drop(1)) { _, rest -> content(share, rest) }

        // A big map's page or zip takes a while to make: a private map that locked meanwhile gives out none of it, and
        // the browser hears the share ended, as one asking after does.
        if (endedAsHidden(share)) {
            (answer as? Response.FromFile)?.takeIf { it.temporary }?.file?.delete()
            return share.joining.serve(request, parts.drop(1)) { _, _ -> Response.notFound() }
        }

        return answer
    }

    /**
     * Ends the share when its map is private and locked now, as a map that
     * locked while it was shown is: the server then stops if nothing else
     * needs it. Says whether it ended.
     */
    private fun endedAsHidden(share: SharedMap): Boolean {
        if (!hidden(share.mapId)) {
            return false
        }

        end(share.code)
        scope.launch { refresh() }
        return true
    }

    private suspend fun content(share: SharedMap, rest: List<String>): Response {
        val title = maps.summary(share.mapId)?.title ?: words.string(R.string.nearby_map_without_a_title)

        return when (rest.firstOrNull()) {
            null -> {
                val folder = maps.folder(share.mapId)
                // Written a piece at a time while the server listens: a big page still being made stops as it stops.
                val job = currentCoroutineContext().job

                val page = share.page(pageVersion(maps.summary(share.mapId), folder)) { file ->
                    file.outputStream().use { output ->
                        WebPage.write(context, WhileActive(output, job), title, maps.read(share.mapId), share.theme, folder, download = "map.zip", asTheyAre = share.asTheyAre) { size ->
                            requireRoom(sharedPages, size)
                        }
                    }
                }

                // YouTube's player plays only for a page it can name, so this page tells where it is, though never its code.
                Response.pageFile(page.file, pagePolicy(page.scripts), mapOf("Referrer-Policy" to "strict-origin"), temporary = page.temporary)
            }
            "map.zip" -> {
                // Made once for each version of the map, in a file every browser in the room is sent: a map with its
                // videos is more than memory can spare, or the storage a zip for each browser.
                val folder = maps.folder(share.mapId)
                val zips = zipsOf(share) ?: return Response.notFound()

                val bundle = try {
                    zips.of(pageVersion(maps.summary(share.mapId), folder)) { file -> writeZip(share, file) }
                } catch (full: NoRoomException) {
                    return Response.text(507, words.string(R.string.nearby_no_room_for_the_zip))
                } ?: return Response.notFound()

                val name = title.replace(Regex("[^\\p{L}\\p{N} _-]"), "").trim().ifEmpty { words.string(R.string.nearby_map_without_a_title) }
                // The plain name for older browsers, in ASCII alone (Map, when none is left), and the name as written for the rest.
                val plain = name.filter { it.code < 128 }.trim().ifEmpty { "Map" }
                val written = URLEncoder.encode("$name.zip", "UTF-8").replace("+", "%20")
                Response.FromFile("application/zip", bundle, mapOf("Content-Disposition" to "attachment; filename=\"$plain.zip\"; filename*=UTF-8''$written"))
            }
            else -> Response.notFound()
        }
    }

    /** The zips made for `share`, while it's shown. */
    private fun zipsOf(share: SharedMap): SharedZip? = synchronized(zips) {
        if (shares[share.code] !== share) null else zips.getOrPut(share.code) { SharedZip(sharedFolder, share.code) }
    }

    /**
     * Writes `share`'s map as a zip into `file`: only once there's room for
     * the most it may come to, so a big map's zip is never begun where it
     * can't be finished, and stopping as the server stops.
     */
    private suspend fun writeZip(share: SharedMap, file: File) {
        val job = currentCoroutineContext().job

        file.outputStream().use { output ->
            maps.writeZip(share.mapId, WhileActive(output, job), share.asTheyAre) { size ->
                if (room() < size + SPARE_SPACE) {
                    throw NoRoomException()
                }
            }
        }
    }

    private fun joinPage(status: Int, problem: String): Response =
        codePage(asset("join.html"), status, words.string(R.string.nearby_code_on_the_other_device), problem, words.language)

    private fun advertise() {
        if (advertisement != null) {
            if (advertisedPort == server.port) {
                return
            }

            // The server started again on another port, as its network changed: the advertisement says where it is now.
            unadvertise()
        }

        advertisedPort = server.port

        // By name, where unit tests' stand-in context can say there's no such service.
        val manager = context.getSystemService(Context.NSD_SERVICE) as? NsdManager ?: return

        val info = NsdServiceInfo().apply {
            serviceName = serviceName(name)
            serviceType = SERVICE_TYPE
            port = server.port
            serviceAttributes(selfId, key).forEach { (attribute, value) -> setAttribute(attribute, value) }
        }

        val listener = object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(info: NsdServiceInfo) {
                Logs.note(TAG, "Nearby devices can find this one")
            }

            override fun onRegistrationFailed(info: NsdServiceInfo, error: Int) {
                Logs.warn(TAG, "Couldn't tell nearby devices about this one ($error)")
                advertisement = null
            }

            override fun onServiceUnregistered(info: NsdServiceInfo) = Unit
            override fun onUnregistrationFailed(info: NsdServiceInfo, error: Int) = Unit
        }

        advertisement = listener
        runCatching { manager.registerService(info, NsdManager.PROTOCOL_DNS_SD, listener) }
            .onFailure { failure -> Logs.warn(TAG, "Network service discovery isn't available", failure) }
    }

    private fun unadvertise() {
        val listener = advertisement ?: return
        advertisement = null
        runCatching { context.getSystemService(NsdManager::class.java)?.unregisterService(listener) }
    }

    private fun asset(name: String): String = context.assets.open("map/$name").bufferedReader().use { it.readText() }

    private companion object {
        /** An offer's id and what's after it, from `/offer/<id>` or `/offer/<id>/<what>`, or null for any other path. */
        fun offerPath(path: String): Pair<String, String?>? {
            if (!path.startsWith("/offer/")) {
                return null
            }

            val parts = path.removePrefix("/offer/").split('/')

            return when {
                !isOfferId(parts[0]) -> null
                parts.size == 1 -> parts[0] to null
                parts.size == 2 && parts[1].isNotEmpty() -> parts[0] to parts[1]
                else -> null
            }
        }

        /** Whether a request uploads a map: the only one that's big. */
        fun isMapUpload(path: String): Boolean = offerPath(path)?.second == "map"

        fun deviceName(context: Context): String =
            Settings.Global.getString(context.contentResolver, Settings.Global.DEVICE_NAME)?.takeIf { it.isNotBlank() } ?: Build.MODEL
    }
}
