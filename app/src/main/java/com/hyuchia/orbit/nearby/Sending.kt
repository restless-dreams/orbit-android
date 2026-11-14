package com.hyuchia.orbit.nearby

import androidx.annotation.StringRes
import com.hyuchia.orbit.R
import com.hyuchia.orbit.data.MAP_FILE
import com.hyuchia.orbit.data.MAP_FORMAT
import com.hyuchia.orbit.data.MEDIA_DIRECTORY
import com.hyuchia.orbit.data.MapText
import com.hyuchia.orbit.data.MediaSummary
import com.hyuchia.orbit.data.decodeText
import com.hyuchia.orbit.data.fileNamesIn
import com.hyuchia.orbit.data.soundNamesIn
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.ByteArrayOutputStream
import java.io.Closeable
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketTimeoutException
import java.util.zip.ZipFile

/**
 * How long a sender waits for the other device's answer once the map is
 * there: as long as any release keeps a map waiting for one (Orbit 0.5.0
 * and earlier, ten minutes), and a little more, to hear the device say
 * itself that its time ran out. So no answer means the device went quiet,
 * never that the map can still be opened there.
 */
internal const val ANSWER_WAIT_MILLISECONDS = ANSWER_LIFETIME_UP_TO_0_5_0 + 30_000L
private const val CONNECT_MILLISECONDS = 5_000
private const val READ_MILLISECONDS = 60_000
/** The most of an answer a sender reads: answers are a line of JSON. */
private const val ANSWER_BYTES_LIMIT = 64 * 1024
/** The ports a device can listen on. */
internal val PORTS = 1..65_535

/**
 * How long each wait of a send lasts, in milliseconds, measured in time
 * however long each request in it takes: a device that stops answering, or
 * answers a byte at a time, holds no request past its time, nor the wait.
 */
internal data class SendTimes(
    /** How often a sender asks where its offer is. */
    val check: Long = 1_000L,

    /** How long it waits for the other device's answer once the map is there. */
    val answer: Long = ANSWER_WAIT_MILLISECONDS,

    /** How long it waits, while both devices show the code, for the person sending to say they match: the other device stops waiting for the map then. */
    val confirming: Long = CONFIRMING_LIFETIME_MILLISECONDS,

    /** The most one ask of where the offer is takes, from connecting to the last of its answer. */
    val ask: Long = 10_000L,

    /** The most its key, the hello or the half of the code take, each. */
    val request: Long = 60_000L,

    /** The most telling the other device the map won't come takes, as sending stops. */
    val cancel: Long = 5_000L,
)

/** Where a map on its way to another device is. */
sealed interface SendState {
    data object Sending : SendState

    /**
     * Both devices show `check`: the map goes once the person sending sees
     * the other device show it too. `older` is what an older Orbit there
     * won't open of the map, for them to know before it goes.
     */
    data class Confirming(val check: String, val older: OlderThere? = null) : SendState

    /** The other device has the map, and asks whether to take it with `check` beside it, the code this one shows. */
    data class Waiting(val check: String) : SendState

    data object Opened : SendState
    data object Declined : SendState

    /** The person sending saw the codes didn't match, or changed their mind: nothing of the map went. */
    data object Cancelled : SendState

    /** Why the map didn't go, to show the sender. */
    data class Failed(@StringRes val reason: Int) : SendState

    /**
     * The other device's Orbit is older, and takes no map as big as this one,
     * `size` bytes as it would go: nothing of it went, and its code never
     * showed there. Orbit has to be updated there to send it.
     */
    data class TooBigThere(val size: Long) : SendState
}

/**
 * What an older Orbit on the other device won't open of the map: the files
 * of kinds it doesn't keep, which stay here (`staysHere`, counted as the
 * sheet counts what goes), and whether the map is written for a newer
 * format than it reads, so it can only show it there (`showsOnly`).
 */
data class OlderThere(val staysHere: MediaSummary, val showsOnly: Boolean)

/**
 * Offers the map bundled in `bundle` to the device at `address` (host:port),
 * as `from`. First the two devices agree an offer and show the same check
 * code, sealed to the device's key: `key` from its advertisement, with the
 * offers' `version` it takes, or both asked of it when there's none. A map
 * bigger than the device says it takes, or than an older Orbit there takes,
 * goes no further than its hello: its code never shows there, and the person
 * sending hears to update Orbit there. The map goes only once `confirm` says
 * the person sending saw the other device show the code too, so a device
 * answering in its place on the Wi-Fi gets nothing of it, and with the code
 * the person hears what an older Orbit there won't open of it. Follows the
 * answer: [onState] hears each step, ending with opened, declined,
 * cancelled or failed.
 */
internal suspend fun sendMap(
    address: String,
    key: String?,
    version: Int?,
    from: String,
    bundle: File,
    confirm: suspend (check: String) -> Boolean,
    times: SendTimes = SendTimes(),
    onState: (SendState) -> Unit,
) {
    val name = senderName(from).orEmpty()
    val sealed = sealedLength(bundle.length())

    if (sealed > OFFER_LIMIT) {
        onState(SendState.Failed(R.string.nearby_send_too_big))
        return
    }

    onState(SendState.Sending)
    // The offer the device knows of, as soon as it does: sending that stops before the map goes says so there.
    var offered: String? = null
    var sent = false

    try {
        val agreed = try {
            withContext(Dispatchers.IO) { agree(address, key, version, name, sealed, times) { id -> offered = id } }
        } catch (tooBig: TooBigThere) {
            // The offer it said hello with goes as sending ends, below: the other device never shows its code.
            onState(SendState.TooBigThere(sealed))
            return
        } catch (refused: Refusal) {
            onState(SendState.Failed(refused.reason))
            return
        } catch (failure: IOException) {
            // Sending stopped, as the sheet closed: the request's connection closed with it, and there's no one to tell.
            currentCoroutineContext().ensureActive()
            onState(SendState.Failed(R.string.nearby_send_could_not_reach))
            return
        }

        val older = withContext(Dispatchers.IO) { olderThere(bundle, agreed.reads) }
        onState(SendState.Confirming(agreed.check, older))

        when (checked(address, agreed, confirm, times)) {
            Checked.Cancelled -> {
                onState(SendState.Cancelled)
                return
            }
            Checked.Declined -> {
                onState(SendState.Declined)
                return
            }
            Checked.Matched -> Unit
        }

        val delivered = try {
            deliver(address, agreed, bundle)
        } catch (refused: Refusal) {
            onState(SendState.Failed(refused.reason))
            return
        } catch (failure: IOException) {
            // Sending stopped, as the sheet closed: the map's connection closed with it, and there's no one to tell.
            currentCoroutineContext().ensureActive()
            // The other device may have hung up as it declined, while the map was on its way.
            val state = askState(address, agreed, times.ask)
            onState(if (state == "declined") SendState.Declined else SendState.Failed(R.string.nearby_send_could_not_reach))
            return
        }

        sent = true

        if (!delivered) {
            // A device that proves what it says says itself that it declined: from anyone else, it's no answer.
            val declined = !agreed.proves || askState(address, agreed, times.ask) == "declined"
            onState(if (declined) SendState.Declined else SendState.Failed(R.string.nearby_send_could_not_reach))
            return
        }

        onState(SendState.Waiting(agreed.check))

        when (waitForState(times.answer, times.check, setOf("opened", "declined")) { askState(address, agreed, times.ask) }) {
            "opened" -> onState(SendState.Opened)
            "declined" -> onState(SendState.Declined)
            else -> onState(SendState.Failed(R.string.nearby_send_no_answer))
        }
    } finally {
        val id = offered

        if (!sent && id != null) {
            // Sending stopped before the map went, the sheet closed say: the other device stops waiting for it, if it hears in time.
            withContext(NonCancellable) { runCatching { cancelOffer(address, id, times.cancel) } }
        }
    }
}

/**
 * Asks `ask` every `every` milliseconds until it says one of `wanted`, for
 * `within` milliseconds in all, however long each ask takes: what it said,
 * or null when the time ran out first.
 */
internal suspend fun waitForState(within: Long, every: Long, wanted: Set<String>, ask: suspend () -> String?): String? = withTimeoutOrNull(within) {
    var said: String? = null

    while (said !in wanted) {
        delay(every)
        said = ask()
    }

    said
}

/** What came of checking the codes. */
private enum class Checked { Matched, Cancelled, Declined }

/**
 * Whether the person sending says the codes match, or not, while the other
 * device keeps waiting for the map: it may decline, or stop waiting, first.
 */
private suspend fun checked(address: String, agreed: Agreed, confirm: suspend (String) -> Boolean, times: SendTimes): Checked = coroutineScope {
    val answer = async { confirm(agreed.check) }
    val gone = async { stoppedWaiting(address, agreed, times) }

    select {
        answer.onAwait { matched ->
            gone.cancel()
            if (matched) Checked.Matched else Checked.Cancelled
        }

        gone.onAwait {
            answer.cancel()
            Checked.Declined
        }
    }
}

/** Returns once the device declined the offer, or stopped waiting for its map, as it does once its time to come is up. */
private suspend fun stoppedWaiting(address: String, agreed: Agreed, times: SendTimes) {
    waitForState(times.confirming, times.check, setOf("declined")) { askState(address, agreed, times.ask) }
}

/**
 * An offer both devices agreed, its session, the check code both show, and
 * what of a map the device opens; and whether the device proves what it
 * says about the offer, as its half of the check code says.
 */
private class Agreed(val id: String, val session: OfferSession, val check: String, val reads: DeviceReads, val proves: Boolean)

/** Why the other device didn't take the map, to show the sender. */
private open class Refusal(@StringRes val reason: Int) : Exception()

/** The other device got the map but couldn't open it, as when it was sealed to a key the device no longer has. */
private class NotOpened : Refusal(R.string.nearby_send_could_not_open)

/** The other device takes no map as big as this one, as its answer to the hello says, or as an older Orbit's says nothing. */
private class TooBigThere : Exception()

/**
 * Agrees an offer with the device for a map of `size` bytes, sealed: sealed
 * to its key, with this side's commitment to its half of the check code;
 * then, with the device's half in hand, sends its own, and both show the
 * code. An advertised key can be out of date when Orbit started again there
 * since it was found, so when the device can't open the offer, its own word
 * on its key settles it. A device whose advertisement has no version is
 * asked for it with its key.
 */
private suspend fun agree(address: String, advertised: String?, version: Int?, from: String, size: Long, times: SendTimes, onOffered: (String) -> Unit): Agreed {
    val advertisedKey = DeviceKey.read(advertised)?.takeIf { version != null }
    version?.let(::checkVersion)
    val key = advertisedKey ?: askKey(address, times)

    return try {
        greet(address, key, from, size, times, onOffered)
    } catch (notOpened: NotOpened) {
        val current = if (advertisedKey != null) askKey(address, times) else throw notOpened

        if (current.text == key.text) {
            throw notOpened
        }

        greet(address, current, from, size, times, onOffered)
    }
}

/** Refuses to offer anything to a device whose offers are another version than this one's: the older side has to update. */
private fun checkVersion(version: Int) {
    when {
        version < OFFER_VERSION -> throw Refusal(R.string.nearby_send_update_the_other_device)
        version > OFFER_VERSION -> throw Refusal(R.string.nearby_send_update_this_device)
    }
}

/** The key the device at `address` takes maps sealed to, asked of it, with the version of offers it takes. */
private suspend fun askKey(address: String, times: SendTimes): DeviceKey {
    val (status, body) = request(address, "GET", "/key", times.request)

    if (status == 403) {
        throw Refusal(R.string.nearby_send_not_taking_maps)
    }

    // An Orbit too old to seal maps answers with something else, or nothing.
    val answer = (if (status == 200) runCatching { offerJson.decodeFromString(KeyAnswer.serializer(), body) }.getOrNull() else null)
        ?: throw Refusal(R.string.nearby_send_update_the_other_device)
    checkVersion(answer.version)
    return DeviceKey.read(answer.key) ?: throw Refusal(R.string.nearby_send_update_the_other_device)
}

/**
 * Says hello to the device with this side's name and its commitment to its
 * half of the check code, sealed to `key`; then, with the device's half in
 * hand, reveals its own. Both devices then show the code. `onOffered` hears
 * the offer's id as soon as the device gives it. A map of `size` bytes,
 * sealed, past what the device's answer says it takes throws [TooBigThere]
 * before the half is revealed, so the device never shows its code.
 */
private suspend fun greet(address: String, key: DeviceKey, from: String, size: Long, times: SendTimes, onOffered: (String) -> Unit): Agreed {
    val session = OfferSession.forSender(key)
    val senderNonce = randomBytes(CHECK_NONCE_BYTES)
    val hello = OfferHead(from, session.commitment(senderNonce)).bytes()
    val headers = mapOf("Content-Type" to "application/octet-stream", VERSION_HEADER to OFFER_VERSION.toString(), KEY_HEADER to session.senderKey.text)

    val (status, body) = request(address, "POST", "/offer", times.request, headers, sealedLength(hello.size.toLong())) { output ->
        session.seal(hello.inputStream(), hello.size.toLong(), output)
    }

    when (status) {
        200 -> Unit
        400 -> throw NotOpened()
        429 -> throw Refusal(R.string.nearby_send_maps_already_waiting)
        else -> throw Refusal(R.string.nearby_send_not_taking_maps)
    }

    val answer = runCatching { offerJson.decodeFromString(OfferAnswer.serializer(), body) }.getOrNull()
    val receiverNonce = fromBase64Url(answer?.nonce)?.takeIf { it.size == CHECK_NONCE_BYTES }

    if (answer == null || receiverNonce == null || !isOfferId(answer.id)) {
        throw Refusal(R.string.nearby_send_update_the_other_device)
    }

    onOffered(answer.id)
    val reads = DeviceReads.of(answer.mapFormat, answer.mediaTypes, answer.mapSize, answer.files)

    if (size > reads.mapSize) {
        throw TooBigThere()
    }

    val reveal = offerJson.encodeToString(Reveal.serializer(), Reveal(base64Url(senderNonce))).toByteArray()
    val (revealed, _) = request(address, "POST", "/offer/${answer.id}", times.request, mapOf("Content-Type" to "application/json"), reveal.size.toLong()) { it.write(reveal) }

    if (revealed != 200) {
        throw Refusal(R.string.nearby_send_could_not_open)
    }

    val check = session.checkCode(receiverNonce, senderNonce)
    return Agreed(answer.id, session, check, reads, proves = provesStates(receiverNonce))
}

/**
 * What a device that opens `reads` won't open of the map in `bundle`, or
 * null when it opens all of it. Only a map this device reads (written for
 * `readsHere` or older) is said to only show there: one it can only show
 * itself shows only there too, as the person sees here already. A bundle
 * that can't be read here says nothing.
 */
internal fun olderThere(bundle: File, reads: DeviceReads, readsHere: Int = MAP_FORMAT): OlderThere? {
    val (text, media) = try {
        ZipFile(bundle).use { zip ->
            val text = zip.getEntry(MAP_FILE)?.let { entry -> zip.getInputStream(entry).use { decodeText(it.readBytes()) } }.orEmpty()
            val media = zip.entries().asSequence().filter { it.name.startsWith("$MEDIA_DIRECTORY/") }.associate { it.name.substringAfter('/') to it.size.coerceAtLeast(0) }
            text to media
        }
    } catch (unreadable: IOException) {
        return null
    }

    // Ideas' files are of any kind: a device that keeps them opens them all, and one from before them keeps only its kinds.
    val files = fileNamesIn(text).toSet()
    val unopened = media.filterKeys { name -> name.substringAfterLast('.', "").lowercase() !in reads.mediaTypes && !(reads.ideaFiles && name in files) }
    val staysHere = MediaSummary.of(unopened, audio = soundNamesIn(text).toSet(), ideaFiles = files)
    val showsOnly = MapText.formatVersion(text) in (reads.mapFormat + 1)..readsHere
    return OlderThere(staysHere, showsOnly).takeIf { !staysHere.isEmpty || showsOnly }
}

/**
 * Sends the map, sealed in the agreed offer's session. True once the device
 * has it, false when it declined, or stopped waiting for it, first. Its
 * connection closes the moment sending is cancelled, as when the sheet
 * closes: a map on its way stops there and then, rather than go on to its
 * end for no one.
 */
private suspend fun deliver(address: String, agreed: Agreed, bundle: File): Boolean {
    val length = bundle.length()
    val headers = mapOf("Content-Type" to "application/octet-stream")
    val socket = Socket()

    val status = closedOnCancel(socket) {
        exchange(address, "POST", "/offer/${agreed.id}/map", headers, sealedLength(length), socket) { output ->
            bundle.inputStream().use { agreed.session.sealMap(it, length, output) }
        }.first
    }

    return when (status) {
        200 -> true
        404, 409 -> false
        413 -> throw Refusal(R.string.nearby_send_too_big)
        507 -> throw Refusal(R.string.nearby_send_no_room_there)
        400 -> throw Refusal(R.string.nearby_send_could_not_open)
        else -> throw Refusal(R.string.nearby_send_not_taking_maps)
    }
}

/**
 * Runs `write`, which blocks on `connection`, closing `connection` the moment
 * the caller is cancelled: a write under way stops only as its connection
 * closes, since an interrupt doesn't reach it. What closes it is in place
 * before `write` starts, rather than waiting for a thread of `closer` to run
 * it first: cancelled before any had, it would never close, and the write
 * would go on to its end.
 */
internal suspend fun <T> closedOnCancel(connection: Closeable, closer: CoroutineDispatcher = Dispatchers.IO, write: () -> T): T = coroutineScope {
    val closing = launch(closer, start = CoroutineStart.UNDISPATCHED) {
        try {
            awaitCancellation()
        } finally {
            runCatching { connection.close() }
        }
    }

    try {
        runInterruptible(Dispatchers.IO, write)
    } finally {
        closing.cancel()
    }
}

/** Tells the device the map won't come, so it stops showing the code, within `within` milliseconds. */
private suspend fun cancelOffer(address: String, id: String, within: Long) {
    request(address, "POST", "/offer/$id/cancel", within)
}

/**
 * Where the offer is, as the device says within `within` milliseconds:
 * opened, declined, or still going. A device that proves what it says is
 * believed only with its proof, so no one else on the Wi-Fi can say the
 * map was opened, or declined, in its place; Orbit 0.3.0 and 0.4.0 prove
 * nothing, and are taken at their word, as before. Null when it says
 * nothing, nothing proven, or nothing in time.
 */
private suspend fun askState(address: String, agreed: Agreed, within: Long): String? {
    val (status, body) = try {
        request(address, "GET", "/offer/${agreed.id}", within)
    } catch (failure: IOException) {
        return null
    }

    val answer = (if (status == 200) runCatching { offerJson.decodeFromString(StateAnswer.serializer(), body) }.getOrNull() else null) ?: return null

    if (!agreed.proves) {
        return answer.state
    }

    val proof = fromBase64Url(answer.proof) ?: return null
    return answer.state.takeIf { agreed.session.isProof(agreed.id, it, proof) }
}

/**
 * One request to the other device, as [exchange] makes it, in at most
 * `within` milliseconds from connecting to the last of its answer: past
 * that, or as sending is cancelled, its connection closes there and then,
 * and one out of time throws [SocketTimeoutException], as a device that
 * can't be reached does.
 */
private suspend fun request(
    address: String,
    method: String,
    path: String,
    within: Long,
    headers: Map<String, String> = emptyMap(),
    length: Long = 0,
    body: ((OutputStream) -> Unit)? = null,
): Pair<Int, String> {
    val socket = Socket()

    return withTimeoutOrNull(within) { closedOnCancel(socket) { exchange(address, method, path, headers, length, socket, body) } }
        ?: throw SocketTimeoutException("No answer from $address in $within milliseconds")
}

/**
 * One request to another device's Orbit, over a plain socket: Android keeps
 * its HTTP clients off plain HTTP, and can't allow just the local network,
 * which is all this ever talks to. What travels is sealed where it matters
 * (see [OfferSession]). `body` writes exactly `length` bytes. It goes over
 * `socket`, which it closes: closing it from elsewhere stops it. Returns
 * the status and the body.
 */
private fun exchange(
    address: String,
    method: String,
    path: String,
    headers: Map<String, String> = emptyMap(),
    length: Long = 0,
    socket: Socket = Socket(),
    body: ((OutputStream) -> Unit)? = null,
): Pair<Int, String> {
    val host = address.substringBeforeLast(':')

    val port = address.substringAfterLast(':').toIntOrNull()?.takeIf { it in PORTS } ?: run {
        socket.close()
        throw IOException("No port in $address")
    }

    socket.use {
        socket.connect(InetSocketAddress(host, port), CONNECT_MILLISECONDS)
        socket.soTimeout = READ_MILLISECONDS

        val head = buildString {
            append("$method $path HTTP/1.1\r\nHost: $address\r\nConnection: close\r\n")
            headers.forEach { (name, value) -> append("$name: $value\r\n") }
            append("Content-Length: $length\r\n\r\n")
        }

        val output = socket.getOutputStream().buffered()
        output.write(head.toByteArray())
        body?.invoke(output)
        output.flush()
        val response = socket.getInputStream().readAnswer().decodeToString()
        val status = response.substringBefore("\r\n").split(' ').getOrNull(1)?.toIntOrNull() ?: 0
        return status to response.substringAfter("\r\n\r\n", "")
    }
}

/** The whole answer, up to [ANSWER_BYTES_LIMIT]: a device that answers with more isn't one to trust with memory. */
private fun InputStream.readAnswer(): ByteArray {
    val answer = ByteArrayOutputStream()
    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)

    while (true) {
        val count = read(buffer)

        if (count < 0) {
            return answer.toByteArray()
        }

        answer.write(buffer, 0, count)

        if (answer.size() > ANSWER_BYTES_LIMIT) {
            throw IOException("The answer is too long")
        }
    }
}
