package com.hyuchia.orbit.nearby

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.DataInputStream
import java.io.EOFException
import java.io.InputStream
import java.nio.ByteBuffer

/** The header naming the version an offer is sealed with, [OFFER_VERSION]. */
const val VERSION_HEADER = "X-Orbit-Version"

/** The header carrying the sender's fresh public key for an offer. */
const val KEY_HEADER = "X-Orbit-Key"

/** The longest sender name an offer may carry, in bytes. */
private const val NAME_BYTES_LIMIT = 1024

internal val offerJson = Json { ignoreUnknownKeys = true }

/**
 * What `GET /key` answers: the key maps sent here are sealed to, for this
 * launch, and the version they're sealed with; and what of a map this
 * device opens, as its answer to a hello says ([OfferAnswer]), for a sender
 * that wants to know before it offers anything.
 */
@Serializable
internal class KeyAnswer(val version: Int, val key: String, val mapFormat: Int? = null, val mediaTypes: List<String>? = null)

/**
 * What `POST /offer` answers once the offer is open: its id, and the
 * receiver's half of the check code; and what of a map this device opens,
 * so the sender can say what won't open there before it goes: the newest
 * map format its Orbit reads (`MAP_FORMAT`), and the extensions of the
 * files it keeps in a map (`carriedMediaTypes`). Orbit 0.3.0 says neither
 * ([DeviceReads.RELEASE_0_3_0]).
 */
@Serializable
internal class OfferAnswer(val id: String, val nonce: String, val mapFormat: Int? = null, val mediaTypes: List<String>? = null)

/**
 * What of a map a device taking maps opens: the newest map format its
 * Orbit reads, and the extensions of the files it keeps in a map, in lower
 * case. Answers only ever gain fields, so what one leaves unsaid is as
 * Orbit 0.3.0 had it: the only release that takes offers of this version
 * and says neither.
 */
internal data class DeviceReads(val mapFormat: Int, val mediaTypes: Set<String>) {
    companion object {
        /** What Orbit 0.3.0 opens: maps of format 1, with their pictures and videos. It plays no sounds, but keeps a voice memo, an `.mp4`. */
        val RELEASE_0_3_0 = DeviceReads(
            mapFormat = 1,
            mediaTypes = setOf("png", "jpg", "jpeg", "gif", "webp", "avif", "heic", "heif", "bmp", "svg", "mp4", "m4v", "webm", "mov"),
        )

        /** What a device's answer says it opens, with what it leaves unsaid as Orbit 0.3.0 had it. */
        fun of(mapFormat: Int?, mediaTypes: List<String>?): DeviceReads = DeviceReads(
            mapFormat = mapFormat ?: RELEASE_0_3_0.mapFormat,
            mediaTypes = mediaTypes?.map { it.lowercase() }?.toSet() ?: RELEASE_0_3_0.mediaTypes,
        )
    }
}

/** What the sender sends to `POST /offer/<id>` once it has the receiver's half of the check code: its own half. */
@Serializable
internal class Reveal(val nonce: String)

/** What `GET /offer/<id>` and `POST /offer/<id>` answer: waiting, opened or declined. */
@Serializable
internal class StateAnswer(val state: String)

/** Whether an offer's id is one this device could have given: nothing that could change a request it's put in. */
internal fun isOfferId(id: String): Boolean = id.length in 1..64 && id.all { it in 'a'..'z' || it in 'A'..'Z' || it in '0'..'9' || it == '-' }

/**
 * What a sealed offer carries before the map's zip: the sender's name, and
 * its commitment to its half of the check code. As bytes: the name's length
 * (2 bytes, big-endian), the name in UTF-8, then the commitment.
 */
internal class OfferHead(val from: String, val commitment: ByteArray) {
    fun bytes(): ByteArray {
        val name = from.toByteArray()
        require(name.size <= NAME_BYTES_LIMIT) { "The name is too long to send" }
        require(commitment.size == COMMITMENT_BYTES) { "A commitment has $COMMITMENT_BYTES bytes" }
        return ByteBuffer.allocate(Short.SIZE_BYTES + name.size + commitment.size).putShort(name.size.toShort()).put(name).put(commitment).array()
    }

    companion object {
        /** The head of an opened offer, leaving `input` at the map's zip. */
        fun read(input: InputStream): OfferHead {
            // DataInputStream reads only what it's asked for, so the zip stays whole in `input`.
            val data = DataInputStream(input)

            try {
                val length = data.readUnsignedShort()

                if (length > NAME_BYTES_LIMIT) {
                    throw UnreadableOfferException("The sender's name is too long")
                }

                val name = ByteArray(length).also(data::readFully)
                val commitment = ByteArray(COMMITMENT_BYTES).also(data::readFully)
                return OfferHead(name.decodeToString(), commitment)
            } catch (short: EOFException) {
                throw UnreadableOfferException("The offer ends before its map", short)
            }
        }
    }
}
