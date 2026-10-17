package com.hyuchia.orbit.nearby

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Build
import android.provider.Settings
import com.hyuchia.orbit.Logs
import com.hyuchia.orbit.R
import com.hyuchia.orbit.ResourceWords
import com.hyuchia.orbit.Words
import com.hyuchia.orbit.data.MapBundle
import com.hyuchia.orbit.data.MapRepository
import com.hyuchia.orbit.page.AppTheme
import com.hyuchia.orbit.page.WebPage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.net.URLEncoder
import java.security.KeyPair
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/** The port other devices send maps to, and computers open shared maps at. */
private const val PORT = 8766
/** How long an offer waits for an answer before it's dropped. */
private const val OFFER_LIFETIME_MILLISECONDS = 10 * 60 * 1000L
/** How long an offer that arrived waits for the sender's half of its check code, which the sender sends at once. */
private const val ARRIVING_LIFETIME_MILLISECONDS = 60 * 1000L
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
/** The most any request but a map's sends: a shared map's join form, with a name and a PIN, or a sender's half of a check code. */
private const val FORM_LIMIT = 4L * 1024
/** Shares that ended, kept so their browsers hear it ended rather than that the code is wrong. */
private const val ENDED_SHARES = 16
private const val TAG = "NearbyHost"

/** The largest map, pictures included, a device may send another. */
const val OFFER_LIMIT = 64L * 1024 * 1024

/** How Orbit advertises itself to devices on the local network. */
const val SERVICE_TYPE = "_orbit._tcp."
/** The advertisement's attribute naming this launch of Orbit, so a device doesn't list itself. */
const val SERVICE_ID = "id"
/** The advertisement's attribute with the key maps sent to this launch of Orbit are sealed to. */
const val SERVICE_KEY = "key"

/** What the advertisement carries besides the name: this launch's id, and its key for offers. */
internal fun serviceAttributes(selfId: String, key: DeviceKey): Map<String, String> = mapOf(SERVICE_ID to selfId, SERVICE_KEY to key.text)

/**
 * A map another device offered, waiting for an answer here: what it is
 * (its text, and how many pictures come with it), who says they sent it
 * and from which address. `existing` is this device's copy of the same
 * map, if it has one. The map itself waits in `file` until it's taken in.
 * `check` is the code the sender shows too, for the person to see it's
 * their device.
 */
class Offer(val id: String, val from: String, val address: String, val bundle: MapBundle, val existing: String?, internal val file: File, val check: String)

enum class OfferState { Waiting, Opened, Declined }

/** A map shown to computers' browsers at a code, and who may open it. */
class SharedMap internal constructor(val code: String, internal val mapId: String, internal val theme: AppTheme, val joining: Joining)

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

/**
 * This device on the local network, while Orbit is on screen and only when
 * there's a reason to be: it tells nearby devices it's here and takes the
 * maps they offer (each one waits for a yes) when the settings allow, and
 * shows a map to computers' browsers at a six-digit code while asked to.
 * Otherwise nothing listens. `policy` says which connections it takes: the
 * room's, unless a test over this machine's loopback says otherwise; and
 * `words` what its pages say.
 */
class NearbyHost internal constructor(
    private val context: Context,
    private val maps: MapRepository,
    private val scope: CoroutineScope,
    policy: ConnectionPolicy,
    private val words: Words,
) {
    constructor(context: Context, maps: MapRepository, scope: CoroutineScope) : this(context, maps, scope, RoomNetworks(), ResourceWords(context))

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

    /** This launch's key pair for offers: maps sent here are sealed to its public half, so only this launch can open them. */
    private val keys: KeyPair by lazy { newKeyPair() }

    /** The key maps sent here are sealed to, as the advertisement and `GET /key` give it. */
    val key: DeviceKey by lazy { DeviceKey.of(keys) }

    private val offersFolder = File(context.cacheDir, "offers")
    // Only a map sent here is big: a shared map's join form and a sender's half of a check code are a few lines.
    private val server = LocalServer(
        bodyLimit = { method, path -> if (method == "POST" && path == "/offer") OFFER_LIMIT else FORM_LIMIT },
        spool = offersFolder,
        policy = policy,
    ) { answer(it) }
    private val guard = CodeGuard()
    private val waiting = MutableStateFlow<List<Offer>>(emptyList())
    private val listeningNow = MutableStateFlow(false)
    private val states = ConcurrentHashMap<String, OfferState>()
    private val arriving = ConcurrentHashMap<String, Arriving>()

    /** Where the offers being opened now came from. Guards the places offers take under the limits, with [arriving] and [usedKeys]. */
    private val opening = mutableListOf<String>()

    /** The sender keys of offers opened this launch, oldest first. */
    private val usedKeys = LinkedHashSet<String>()

    private val shares = ConcurrentHashMap<String, SharedMap>()
    private val endedShares = object : LinkedHashMap<String, Joining>() {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Joining>?): Boolean = size > ENDED_SHARES
    }
    private var advertisement: NsdManager.RegistrationListener? = null
    private var onScreen = false
    private var accepting = false

    /** Maps offered here, oldest first, waiting for an answer. */
    val offers: StateFlow<List<Offer>> = waiting.asStateFlow()

    /** When a device was last kept from guessing a share's code, or 0: for the phone to say so. */
    val lockedOut: StateFlow<Long> = guard.lockedOut

    /** Whether other devices can reach this one now, at [address]. */
    val listening: StateFlow<Boolean> = listeningNow.asStateFlow()

    /**
     * An offer that opened whole, until the sender's half of its check code
     * arrives: only then is it shown, with the code. It holds a place under
     * the limits until then, and to the end of its time when the half is
     * wrong, so no one can try offer after offer for a code that matches.
     */
    private class Arriving(
        val id: String,
        val from: String,
        val address: String,
        val bundle: MapBundle,
        val existing: String?,
        val file: File,
        val session: OfferSession,
        val commitment: ByteArray,
        val receiverNonce: ByteArray,
    ) {
        /** Whether a half that wasn't the one committed to came for it: it's dropped, and only keeps its place. */
        var refused = false
    }

    init {
        scope.launch(Dispatchers.IO) {
            // Offers that waited when Orbit last stopped, and the lasting id earlier versions kept.
            offersFolder.listFiles().orEmpty().forEach { it.delete() }
            File(context.filesDir, "nearby-id").delete()
        }
    }

    /** Orbit is on screen: `accepting` advertises this device and takes offers. Calling it again changes only that. */
    fun start(accepting: Boolean) {
        onScreen = true
        this.accepting = accepting
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

    /** Listens while there's something to listen for, and advertises while offers are welcome. */
    private fun refresh() {
        val listening = onScreen && (accepting || shares.isNotEmpty())

        if (listening) {
            server.start(PORT)
        } else {
            server.stop()
        }

        if (listening && accepting) {
            advertise()
        } else {
            unadvertise()
        }

        listeningNow.value = server.isRunning
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
            val bundle = withContext(Dispatchers.IO) { offer.file.inputStream().use { maps.readBundle(it) } } ?: throw IOException("No map in the offer")
            maps.receive(bundle, replacing = if (keepBoth) null else offer.existing)
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

    /**
     * Shows a map to computers' browsers at a code, until [unshare]: to
     * explore, present or download it, for whoever `options` lets in.
     */
    fun share(mapId: String, theme: AppTheme, options: JoiningOptions = JoiningOptions(askNames = false)): SharedMap {
        if (shares.isEmpty()) {
            // A new code: wrong guesses at old ones no longer keep anyone waiting.
            guard.reset()
        }

        var code = newCode()

        while (shares.containsKey(code)) {
            code = newCode()
        }

        val shared = SharedMap(code, mapId, theme, Joining(code, RoomKind.Share, options, template = { asset("join.html") }, words = words))
        synchronized(endedShares) { endedShares.remove(code) }
        shares[code] = shared
        refresh()
        return shared
    }

    fun unshare(code: String) {
        shares.remove(code)?.let { shared ->
            shared.joining.end()
            synchronized(endedShares) { endedShares[code] = shared.joining }
        }

        refresh()
    }

    private fun settle(offer: Offer, state: OfferState) {
        states[offer.id] = state
        waiting.update { offers -> offers.filterNot { it.id == offer.id } }
        offer.file.delete()
    }

    private suspend fun answer(request: Request): Response = when {
        request.method == "GET" && request.path == "/key" -> key()
        request.method == "POST" && request.path == "/offer" -> offer(request)
        request.method == "POST" && request.path.startsWith("/offer/") -> reveal(request.path.removePrefix("/offer/"), request)
        request.method == "GET" && request.path.startsWith("/offer/") -> offerState(request.path.removePrefix("/offer/"))
        else -> shared(request)
    }

    /** The key maps sent here are sealed to, for senders without it: an address typed in, or an advertisement out of date. */
    private fun key(): Response {
        if (!accepting) {
            return Response.text(403, "This device isn't taking maps")
        }

        return json(offerJson.encodeToString(KeyAnswer.serializer(), KeyAnswer(OFFER_VERSION, key.text)))
    }

    /**
     * A map sealed to this launch's key: opened into the offers folder, it
     * waits for the sender's half of the check code before it's shown.
     * Anything not sealed, or that fails to open, is refused.
     */
    private suspend fun offer(request: Request): Response {
        if (!accepting) {
            return Response.text(403, "This device isn't taking maps")
        }

        val sealed = request.headers[VERSION_HEADER.lowercase()] == OFFER_VERSION.toString()
        val senderKey = (if (sealed) DeviceKey.read(request.headers[KEY_HEADER.lowercase()]) else null)
            ?: return Response.text(400, "Update Orbit on the other device: maps sent here have to be encrypted.")
        hold(request.from, senderKey)?.let { return it }
        val id = UUID.randomUUID().toString()
        val file = File(offersFolder, "$id.zip")
        var arrival: Arriving? = null

        try {
            arrival = open(request, senderKey, id, file)
        } catch (unreadable: UnreadableOfferException) {
            // Refused below.
        } finally {
            arrived(request.from, senderKey, arrival)

            if (arrival == null) {
                file.delete()
            }
        }

        val opened = arrival ?: return Response.text(400, "The map didn't arrive intact. Send it again.")

        scope.launch {
            delay(ARRIVING_LIFETIME_MILLISECONDS)
            synchronized(opening) { arriving.remove(id) }?.file?.delete()
        }

        return json(offerJson.encodeToString(OfferAnswer.serializer(), OfferAnswer(id, base64Url(opened.receiverNonce))))
    }

    /** Opens an offer into `file`. Throws [UnreadableOfferException] when it doesn't open whole, or what's sealed in it isn't a map. */
    private suspend fun open(request: Request, senderKey: DeviceKey, id: String, file: File): Arriving {
        val session = OfferSession.forReceiver(keys, senderKey)

        val head = withContext(Dispatchers.IO) {
            offersFolder.mkdirs()

            // Read to its end, so the whole offer is checked before anything in it is used.
            session.open(request.bodyStream(), OFFER_LIMIT).use { opened ->
                val head = OfferHead.read(opened)
                file.outputStream().use { opened.copyTo(it) }
                head
            }
        }

        // Only what the dialog shows is read now: the pictures stay in the file until the map is taken in.
        val bundle = runCatching { file.inputStream().use { maps.readBundle(it, withPictures = false) } }.getOrNull()
            ?: throw UnreadableOfferException("There's no map in the offer")
        val from = senderName(head.from) ?: words.string(R.string.nearby_a_nearby_device)
        return Arriving(id, from, request.from, bundle, bundle.uuid?.let(maps::findByIdentity), file, session, head.commitment, randomBytes(CHECK_NONCE_BYTES))
    }

    /**
     * Holds a place under the limits for an offer from `address`, before
     * it's opened, so offers sent at once can't slip past them; or the
     * answer refusing it.
     */
    private fun hold(address: String, senderKey: DeviceKey): Response? = synchronized(opening) {
        val places = waiting.value.map { it.address } + arriving.values.map { it.address } + opening

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
     * The sender's half of an arriving offer's check code: when it's the half
     * the sender committed to, the offer is shown, with the code.
     */
    private fun reveal(id: String, request: Request): Response {
        val reveal = runCatching { offerJson.decodeFromString(Reveal.serializer(), request.body.decodeToString()) }.getOrNull()
        val senderNonce = fromBase64Url(reveal?.nonce)?.takeIf { it.size == CHECK_NONCE_BYTES }

        val offer = synchronized(opening) {
            val arrival = arriving[id]?.takeIf { it.address == request.from && !it.refused } ?: return Response.notFound()

            if (senderNonce == null || !arrival.session.isCommitted(arrival.commitment, senderNonce)) {
                // Orbit never sends a wrong half, but someone trying for a code that matches the sender's would. Freeing
                // the place now would let them try again at once, so it stays taken until the offer's time is up.
                arrival.refused = true
                arrival.file.delete()
                states[id] = OfferState.Declined
                return Response.text(400, "That isn't the check code the map was sent with")
            }

            arriving.remove(id)
            val check = arrival.session.checkCode(arrival.receiverNonce, senderNonce)

            Offer(id, arrival.from, arrival.address, arrival.bundle, arrival.existing, arrival.file, check).also { offer ->
                states[offer.id] = OfferState.Waiting
                waiting.update { it + offer }
            }
        }

        scope.launch {
            delay(OFFER_LIFETIME_MILLISECONDS)

            if (states[offer.id] == OfferState.Waiting) {
                settle(offer, OfferState.Declined)
            }
        }

        return json(offerJson.encodeToString(StateAnswer.serializer(), StateAnswer("waiting")))
    }

    private fun json(text: String): Response = Response.Bytes(200, "application/json", text.toByteArray())

    private fun offerState(id: String): Response {
        val state = states[id] ?: return Response.notFound()
        return Response.Bytes(200, "application/json", """{"state":"${state.name.lowercase()}"}""".toByteArray())
    }

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
        val share = first?.let(shares::get)

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
        return share.joining.serve(request, parts.drop(1)) { _, rest -> content(share, rest) }
    }

    private suspend fun content(share: SharedMap, rest: List<String>): Response {
        val title = maps.summary(share.mapId)?.title ?: words.string(R.string.nearby_map_without_a_title)

        return when (rest.firstOrNull()) {
            null -> {
                val page = WebPage.build(context, title, maps.read(share.mapId), share.theme, maps.folder(share.mapId), download = "map.zip")
                // YouTube's player plays only for a page it can name, so this page tells where it is, though never its code.
                Response.page(200, page, mapOf("Referrer-Policy" to "strict-origin"))
            }
            "map.zip" -> {
                val bundle = ByteArrayOutputStream().also { maps.writeZip(share.mapId, it) }.toByteArray()
                val name = title.replace(Regex("[^\\p{L}\\p{N} _-]"), "").trim().ifEmpty { words.string(R.string.nearby_map_without_a_title) }
                // The plain name for older browsers, in ASCII alone (Map, when none is left), and the name as written for the rest.
                val plain = name.filter { it.code < 128 }.trim().ifEmpty { "Map" }
                val written = URLEncoder.encode("$name.zip", "UTF-8").replace("+", "%20")
                Response.Bytes(200, "application/zip", bundle, mapOf("Content-Disposition" to "attachment; filename=\"$plain.zip\"; filename*=UTF-8''$written"))
            }
            else -> Response.notFound()
        }
    }

    private fun joinPage(status: Int, problem: String): Response =
        codePage(asset("join.html"), status, words.string(R.string.nearby_code_on_the_other_device), problem, words.language)

    private fun advertise() {
        if (advertisement != null) {
            return
        }

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
        fun deviceName(context: Context): String =
            Settings.Global.getString(context.contentResolver, Settings.Global.DEVICE_NAME)?.takeIf { it.isNotBlank() } ?: Build.MODEL
    }
}
