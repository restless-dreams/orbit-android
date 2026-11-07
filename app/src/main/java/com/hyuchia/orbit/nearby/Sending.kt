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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.util.zip.ZipFile

/** How often a sender asks whether its map was taken. */
private const val CHECK_MILLISECONDS = 1_000L
/** How long a sender waits for the other device to answer. */
private const val ANSWER_WAIT_MILLISECONDS = 3 * 60 * 1000L
private const val CONNECT_MILLISECONDS = 5_000
private const val READ_MILLISECONDS = 60_000
/** The most of an answer a sender reads: answers are a line of JSON. */
private const val ANSWER_BYTES_LIMIT = 64 * 1024
/** The ports a device can listen on. */
internal val PORTS = 1..65_535

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
suspend fun sendMap(
    address: String,
    key: String?,
    version: Int?,
    from: String,
    bundle: File,
    confirm: suspend (check: String) -> Boolean,
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
            withContext(Dispatchers.IO) { agree(address, key, version, name, sealed) { id -> offered = id } }
        } catch (tooBig: TooBigThere) {
            // The offer it said hello with goes as sending ends, below: the other device never shows its code.
            onState(SendState.TooBigThere(sealed))
            return
        } catch (refused: Refusal) {
            onState(SendState.Failed(refused.reason))
            return
        } catch (failure: IOException) {
            onState(SendState.Failed(R.string.nearby_send_could_not_reach))
            return
        }

        val older = withContext(Dispatchers.IO) { olderThere(bundle, agreed.reads) }
        onState(SendState.Confirming(agreed.check, older))

        when (checked(address, agreed, confirm)) {
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
            withContext(Dispatchers.IO) { deliver(address, agreed, bundle) }
        } catch (refused: Refusal) {
            onState(SendState.Failed(refused.reason))
            return
        } catch (failure: IOException) {
            // The other device may have hung up as it declined, while the map was on its way.
            val state = runCatching { withContext(Dispatchers.IO) { state(address, agreed) } }.getOrNull()
            onState(if (state == "declined") SendState.Declined else SendState.Failed(R.string.nearby_send_could_not_reach))
            return
        }

        sent = true

        if (!delivered) {
            // A device that proves what it says says itself that it declined: from anyone else, it's no answer.
            val declined = !agreed.proves || runCatching { withContext(Dispatchers.IO) { state(address, agreed) } }.getOrNull() == "declined"
            onState(if (declined) SendState.Declined else SendState.Failed(R.string.nearby_send_could_not_reach))
            return
        }

        onState(SendState.Waiting(agreed.check))
        var waited = 0L

        while (waited < ANSWER_WAIT_MILLISECONDS) {
            delay(CHECK_MILLISECONDS)
            waited += CHECK_MILLISECONDS
            val state = runCatching { withContext(Dispatchers.IO) { state(address, agreed) } }.getOrNull()

            when (state) {
                "opened" -> return onState(SendState.Opened)
                "declined" -> return onState(SendState.Declined)
            }
        }

        onState(SendState.Failed(R.string.nearby_send_no_answer))
    } finally {
        val id = offered

        if (!sent && id != null) {
            // Sending stopped before the map went, the sheet closed say: the other device stops waiting for it.
            withContext(NonCancellable + Dispatchers.IO) { runCatching { cancelOffer(address, id) } }
        }
    }
}

/** What came of checking the codes. */
private enum class Checked { Matched, Cancelled, Declined }

/**
 * Whether the person sending says the codes match, or not, while the other
 * device keeps waiting for the map: it may decline, or stop waiting, first.
 */
private suspend fun checked(address: String, agreed: Agreed, confirm: suspend (String) -> Boolean): Checked = coroutineScope {
    val answer = async { confirm(agreed.check) }
    val gone = async { stoppedWaiting(address, agreed) }

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

/** Returns once the device declined the offer, or stopped waiting for its map. */
private suspend fun stoppedWaiting(address: String, agreed: Agreed) {
    var waited = 0L

    while (waited < CONFIRMING_LIFETIME_MILLISECONDS) {
        delay(CHECK_MILLISECONDS)
        waited += CHECK_MILLISECONDS

        if (runCatching { withContext(Dispatchers.IO) { state(address, agreed) } }.getOrNull() == "declined") {
            return
        }
    }
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
private fun agree(address: String, advertised: String?, version: Int?, from: String, size: Long, onOffered: (String) -> Unit): Agreed {
    val advertisedKey = DeviceKey.read(advertised)?.takeIf { version != null }
    version?.let(::checkVersion)
    val key = advertisedKey ?: askKey(address)

    return try {
        greet(address, key, from, size, onOffered)
    } catch (notOpened: NotOpened) {
        val current = if (advertisedKey != null) askKey(address) else throw notOpened

        if (current.text == key.text) {
            throw notOpened
        }

        greet(address, current, from, size, onOffered)
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
private fun askKey(address: String): DeviceKey {
    val (status, body) = exchange(address, "GET", "/key")

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
private fun greet(address: String, key: DeviceKey, from: String, size: Long, onOffered: (String) -> Unit): Agreed {
    val session = OfferSession.forSender(key)
    val senderNonce = randomBytes(CHECK_NONCE_BYTES)
    val hello = OfferHead(from, session.commitment(senderNonce)).bytes()
    val headers = mapOf("Content-Type" to "application/octet-stream", VERSION_HEADER to OFFER_VERSION.toString(), KEY_HEADER to session.senderKey.text)

    val (status, body) = exchange(address, "POST", "/offer", headers, sealedLength(hello.size.toLong())) { output ->
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
    val (revealed, _) = exchange(address, "POST", "/offer/${answer.id}", mapOf("Content-Type" to "application/json"), reveal.size.toLong()) { it.write(reveal) }

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
 * has it, false when it declined, or stopped waiting for it, first.
 */
private fun deliver(address: String, agreed: Agreed, bundle: File): Boolean {
    val length = bundle.length()
    val headers = mapOf("Content-Type" to "application/octet-stream")

    val (status, _) = exchange(address, "POST", "/offer/${agreed.id}/map", headers, sealedLength(length)) { output ->
        bundle.inputStream().use { agreed.session.sealMap(it, length, output) }
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

/** Tells the device the map won't come, so it stops showing the code. */
private fun cancelOffer(address: String, id: String) {
    exchange(address, "POST", "/offer/$id/cancel")
}

/**
 * Where the offer is, as the device says: opened, declined, or still going.
 * A device that proves what it says is believed only with its proof, so no
 * one else on the Wi-Fi can say the map was opened, or declined, in its
 * place; Orbit 0.3.0 and 0.4.0 prove nothing, and are taken at their word,
 * as before. Null when it says nothing, or nothing proven.
 */
private fun state(address: String, agreed: Agreed): String? {
    val (status, body) = exchange(address, "GET", "/offer/${agreed.id}")
    val answer = (if (status == 200) runCatching { offerJson.decodeFromString(StateAnswer.serializer(), body) }.getOrNull() else null) ?: return null

    if (!agreed.proves) {
        return answer.state
    }

    val proof = fromBase64Url(answer.proof) ?: return null
    return answer.state.takeIf { agreed.session.isProof(agreed.id, it, proof) }
}

/**
 * One request to another device's Orbit, over a plain socket: Android keeps
 * its HTTP clients off plain HTTP, and can't allow just the local network,
 * which is all this ever talks to. What travels is sealed where it matters
 * (see [OfferSession]). `body` writes exactly `length` bytes. Returns the
 * status and the body.
 */
private fun exchange(
    address: String,
    method: String,
    path: String,
    headers: Map<String, String> = emptyMap(),
    length: Long = 0,
    body: ((OutputStream) -> Unit)? = null,
): Pair<Int, String> {
    val host = address.substringBeforeLast(':')
    val port = address.substringAfterLast(':').toIntOrNull()?.takeIf { it in PORTS } ?: throw IOException("No port in $address")

    Socket().use { socket ->
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
