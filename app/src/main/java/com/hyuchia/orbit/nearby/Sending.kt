package com.hyuchia.orbit.nearby

import androidx.annotation.StringRes
import com.hyuchia.orbit.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.io.SequenceInputStream
import java.net.InetSocketAddress
import java.net.Socket

/** How often a sender asks whether its map was taken. */
private const val CHECK_MILLISECONDS = 1_000L
/** How long a sender waits for the other device to answer. */
private const val ANSWER_WAIT_MILLISECONDS = 3 * 60 * 1000L
private const val CONNECT_MILLISECONDS = 5_000
private const val READ_MILLISECONDS = 60_000
/** The most of an answer a sender reads: answers are a line of JSON. */
private const val ANSWER_BYTES_LIMIT = 64 * 1024

/** Where a map on its way to another device is. */
sealed interface SendState {
    data object Sending : SendState

    /** The other device has the map, and asks whether to take it with `check` beside it, the code this one shows. */
    data class Waiting(val check: String) : SendState

    data object Opened : SendState
    data object Declined : SendState

    /** Why the map didn't go, to show the sender. */
    data class Failed(@StringRes val reason: Int) : SendState
}

/**
 * Offers the map bundled in `bundle` to the device at `address` (host:port),
 * as `from`, sealed to the device's key: `key` from its advertisement, or
 * asked of it when there's none. Follows its answer: [onState] hears each
 * step, ending with opened, declined or failed.
 */
suspend fun sendMap(address: String, key: String?, from: String, bundle: File, onState: (SendState) -> Unit) {
    val name = senderName(from).orEmpty()

    if (sealedLength(OfferHead(name, ByteArray(COMMITMENT_BYTES)).bytes().size + bundle.length()) > OFFER_LIMIT) {
        onState(SendState.Failed(R.string.nearby_send_too_big))
        return
    }

    onState(SendState.Sending)

    val offered = try {
        withContext(Dispatchers.IO) { deliver(address, key, name, bundle) }
    } catch (refused: Refusal) {
        onState(SendState.Failed(refused.reason))
        return
    } catch (failure: IOException) {
        onState(SendState.Failed(R.string.nearby_send_could_not_reach))
        return
    }

    onState(SendState.Waiting(offered.check))
    var waited = 0L

    while (waited < ANSWER_WAIT_MILLISECONDS) {
        delay(CHECK_MILLISECONDS)
        waited += CHECK_MILLISECONDS
        val state = runCatching { withContext(Dispatchers.IO) { state(address, offered.id) } }.getOrNull()

        when (state) {
            "opened" -> return onState(SendState.Opened)
            "declined" -> return onState(SendState.Declined)
        }
    }

    onState(SendState.Failed(R.string.nearby_send_no_answer))
}

/** A map the other device has, and the check code both show. */
private class Offered(val id: String, val check: String)

/** Why the other device didn't take the map, to show the sender. */
private open class Refusal(@StringRes val reason: Int) : Exception()

/** The other device got the map but couldn't open it, as when it was sealed to a key the device no longer has. */
private class NotOpened : Refusal(R.string.nearby_send_could_not_open)

/**
 * Seals the map to the device's key and offers it. An advertised key can be
 * out of date when Orbit started again there since it was found, so when
 * the device can't open the map, its own word on its key settles it.
 */
private fun deliver(address: String, advertised: String?, from: String, bundle: File): Offered {
    val advertisedKey = DeviceKey.read(advertised)
    val key = advertisedKey ?: askKey(address)

    return try {
        offer(address, key, from, bundle)
    } catch (notOpened: NotOpened) {
        val current = if (advertisedKey != null) askKey(address) else throw notOpened

        if (current.text == key.text) {
            throw notOpened
        }

        offer(address, current, from, bundle)
    }
}

/** The key the device at `address` takes maps sealed to, asked of it. */
private fun askKey(address: String): DeviceKey {
    val (status, body) = exchange(address, "GET", "/key")

    if (status == 403) {
        throw Refusal(R.string.nearby_send_not_taking_maps)
    }

    // An Orbit too old to seal maps answers with something else, or nothing.
    val answer = if (status == 200) runCatching { offerJson.decodeFromString(KeyAnswer.serializer(), body) }.getOrNull() else null
    return answer?.takeIf { it.version == OFFER_VERSION }?.let { DeviceKey.read(it.key) } ?: throw Refusal(R.string.nearby_send_update_the_other_device)
}

/**
 * Offers the map sealed to `key`, with this side's commitment to its half
 * of the check code; then, with the receiver's half in hand, sends its own.
 */
private fun offer(address: String, key: DeviceKey, from: String, bundle: File): Offered {
    val session = OfferSession.forSender(key)
    val senderNonce = randomBytes(CHECK_NONCE_BYTES)
    val head = OfferHead(from, session.commitment(senderNonce)).bytes()
    val length = head.size + bundle.length()
    val headers = mapOf("Content-Type" to "application/octet-stream", VERSION_HEADER to OFFER_VERSION.toString(), KEY_HEADER to session.senderKey.text)

    val (status, body) = exchange(address, "POST", "/offer", headers, sealedLength(length)) { output ->
        SequenceInputStream(head.inputStream(), bundle.inputStream()).use { session.seal(it, length, output) }
    }

    when (status) {
        200 -> Unit
        400 -> throw NotOpened()
        413 -> throw Refusal(R.string.nearby_send_too_big)
        429 -> throw Refusal(R.string.nearby_send_maps_already_waiting)
        else -> throw Refusal(R.string.nearby_send_not_taking_maps)
    }

    val answer = runCatching { offerJson.decodeFromString(OfferAnswer.serializer(), body) }.getOrNull()
    val receiverNonce = fromBase64Url(answer?.nonce)?.takeIf { it.size == CHECK_NONCE_BYTES }

    if (answer == null || receiverNonce == null || !isOfferId(answer.id)) {
        throw Refusal(R.string.nearby_send_update_the_other_device)
    }

    val reveal = offerJson.encodeToString(Reveal.serializer(), Reveal(base64Url(senderNonce))).toByteArray()
    val (revealed, _) = exchange(address, "POST", "/offer/${answer.id}", mapOf("Content-Type" to "application/json"), reveal.size.toLong()) { it.write(reveal) }

    if (revealed != 200) {
        throw Refusal(R.string.nearby_send_could_not_open)
    }

    return Offered(answer.id, session.checkCode(receiverNonce, senderNonce))
}

private fun state(address: String, id: String): String? {
    val (status, body) = exchange(address, "GET", "/offer/$id")
    return if (status == 200) runCatching { offerJson.decodeFromString(StateAnswer.serializer(), body).state }.getOrNull() else null
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
    val port = address.substringAfterLast(':').toIntOrNull() ?: throw IOException("No port in $address")

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
