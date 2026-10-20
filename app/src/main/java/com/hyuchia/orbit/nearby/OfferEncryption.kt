package com.hyuchia.orbit.nearby

import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.security.GeneralSecurityException
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.SecureRandom
import java.security.interfaces.ECPublicKey
import java.security.spec.ECFieldFp
import java.security.spec.ECGenParameterSpec
import java.security.spec.ECParameterSpec
import java.security.spec.X509EncodedKeySpec
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyAgreement
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * The version of how maps are offered and sealed. It's in each device's
 * advertisement and its key's answer, sent with each offer, and named in
 * every key, so versions never mix. Version 2 checks the code on both
 * devices before the map is sent; version 1 (Orbit 0.2.0 and earlier) sent
 * the map first, and isn't spoken any more.
 */
const val OFFER_VERSION = 2

/** How much of an offer each sealed chunk holds: small enough to keep memory flat, big enough to keep the overhead small. */
internal const val CHUNK_BYTES = 64 * 1024

/** What AES-GCM's tag adds to each chunk. */
internal const val TAG_BYTES = 16

/** The size of each random half of a check code. */
internal const val CHECK_NONCE_BYTES = 32

/** The size of the sender's commitment to its half: an HMAC-SHA256. */
internal const val COMMITMENT_BYTES = 32

private const val NONCE_BYTES = 12

/** Before the chunks: the first chunk's nonce, and how many chunks follow. */
internal const val HEADER_BYTES = NONCE_BYTES + Int.SIZE_BYTES

private const val KEY_BYTES = 32
private const val HASH_BYTES = 32
private const val HMAC = "HmacSHA256"
private const val TRANSFORMATION = "AES/GCM/NoPadding"
private const val CURVE = "secp256r1"

/** The longest public key a device may send: P-256's X.509 encoding is 91 bytes. */
private const val PUBLIC_KEY_BYTES_LIMIT = 256

private const val SEALING_INFO = "orbit offer v$OFFER_VERSION key"
private const val MAP_INFO = "orbit offer v$OFFER_VERSION map"
private const val CHECK_INFO = "orbit offer v$OFFER_VERSION check"
private const val COMMITMENT_INFO = "orbit offer v$OFFER_VERSION commitment"

private val random = SecureRandom()

/** An offer that can't be opened here: not sealed to this device, changed or cut short on the way, or with more than was sealed. */
class UnreadableOfferException(message: String, cause: Throwable? = null) : IOException(message, cause)

/** `count` random bytes. */
internal fun randomBytes(count: Int): ByteArray = ByteArray(count).also(random::nextBytes)

internal fun base64Url(bytes: ByteArray): String = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)

/** The bytes of base64url text, padded or not, or null when it isn't base64url. */
internal fun fromBase64Url(text: String?): ByteArray? {
    if (text == null) {
        return null
    }

    return try {
        Base64.getUrlDecoder().decode(text)
    } catch (malformed: IllegalArgumentException) {
        null
    }
}

/** HKDF-SHA256's extract step (RFC 5869): a pseudorandom key from a shared secret. An empty salt is HMAC's length of zeros, as the RFC says. */
internal fun hkdfExtract(salt: ByteArray, secret: ByteArray): ByteArray {
    val mac = Mac.getInstance(HMAC)
    mac.init(SecretKeySpec(salt.takeIf { it.isNotEmpty() } ?: ByteArray(HASH_BYTES), HMAC))
    return mac.doFinal(secret)
}

/** HKDF-SHA256's expand step (RFC 5869): `length` bytes of key for the use `info` names. */
internal fun hkdfExpand(pseudorandomKey: ByteArray, info: ByteArray, length: Int): ByteArray {
    require(length in 0..255 * HASH_BYTES) { "HKDF can't make $length bytes" }
    val mac = Mac.getInstance(HMAC)
    mac.init(SecretKeySpec(pseudorandomKey, HMAC))
    val output = ByteArray(length)
    var block = ByteArray(0)
    var filled = 0
    var counter = 1

    while (filled < length) {
        mac.update(block)
        mac.update(info)
        mac.update(counter.toByte())
        block = mac.doFinal()
        val count = minOf(block.size, length - filled)
        block.copyInto(output, filled, 0, count)
        filled += count
        counter += 1
    }

    return output
}

/** A fresh P-256 key pair. */
internal fun newKeyPair(): KeyPair = KeyPairGenerator.getInstance("EC").run {
    initialize(ECGenParameterSpec(CURVE))
    generateKeyPair()
}

/** P-256's parameters, as this device's provider describes them, to hold other devices' keys to. */
private val p256: ECParameterSpec by lazy { (newKeyPair().public as ECPublicKey).params }

/**
 * A device's public key for offers, with the X.509 bytes it travels as:
 * keys are derived from those exact bytes on both sides.
 */
class DeviceKey internal constructor(val key: ECPublicKey, internal val encoded: ByteArray) {
    /** How the key travels: X.509, in base64url without padding. */
    val text: String get() = base64Url(encoded)

    companion object {
        /** This device's own key, from its key pair. */
        internal fun of(pair: KeyPair): DeviceKey = DeviceKey(pair.public as ECPublicKey, pair.public.encoded)

        /**
         * A key from its text, or null when it isn't a P-256 public key: text
         * that isn't base64url, another kind of key, another curve, or a point
         * that isn't on the curve.
         */
        fun read(text: String?): DeviceKey? {
            val bytes = fromBase64Url(text)?.takeIf { it.size in 1..PUBLIC_KEY_BYTES_LIMIT } ?: return null

            val key = try {
                KeyFactory.getInstance("EC").generatePublic(X509EncodedKeySpec(bytes)) as? ECPublicKey
            } catch (invalid: GeneralSecurityException) {
                null
            } catch (invalid: RuntimeException) {
                null
            }

            if (key == null || !isP256(key.params) || !onCurve(key)) {
                return null
            }

            return DeviceKey(key, bytes)
        }

        private fun isP256(parameters: ECParameterSpec): Boolean =
            parameters.curve == p256.curve && parameters.generator == p256.generator && parameters.order == p256.order && parameters.cofactor == p256.cofactor

        /** Whether the key's point satisfies the curve's equation, so it can't lead the key agreement off the curve. */
        private fun onCurve(key: ECPublicKey): Boolean {
            val curve = key.params.curve
            val prime = (curve.field as? ECFieldFp)?.p ?: return false
            // The point at infinity has no coordinates.
            val x = key.w.affineX ?: return false
            val y = key.w.affineY ?: return false

            if (x.signum() < 0 || x >= prime || y.signum() < 0 || y >= prime) {
                return false
            }

            val left = y.multiply(y).mod(prime)
            val right = x.multiply(x).multiply(x).add(curve.a.multiply(x)).add(curve.b).mod(prime)
            return left == right
        }
    }
}

/**
 * The keys of one offer: ECDH between the sender's fresh key pair and the
 * receiver's key for this launch, then HKDF-SHA256 salted with both public
 * keys, the receiver's first, with one key for each use.
 *
 * What's sealed is sealed with AES-256-GCM in chunks of [CHUNK_BYTES], every
 * one full but the last: a random 12-byte nonce, the number of chunks
 * (4 bytes, big-endian), then the chunks, each encrypted with its 16-byte
 * tag. Chunk `i` uses the nonce with `i` XORed into its last 8 bytes, and
 * authenticates the number of chunks and `i` with itself, so no chunk can be
 * moved, dropped, added or cut short unnoticed. An offer seals two things,
 * each with a key of its own: first its hello, with the sender's name and
 * its commitment ([seal]), then the map itself ([sealMap]).
 *
 * The check code both devices show comes from the check key and a random
 * half from each device. The sender commits to its half before it learns the
 * receiver's, and reveals it after, so no one answering in either device's
 * place can steer the code to match the other's. The map is sealed only
 * once the person sending has seen both devices show it.
 */
class OfferSession private constructor(receiverKey: DeviceKey, val senderKey: DeviceKey, sharedSecret: ByteArray) {
    private val sealingKey: SecretKeySpec
    private val mapKey: SecretKeySpec
    private val checkKey: ByteArray
    private val commitmentKey: ByteArray

    init {
        val pseudorandomKey = hkdfExtract(receiverKey.encoded + senderKey.encoded, sharedSecret)
        sealingKey = SecretKeySpec(hkdfExpand(pseudorandomKey, SEALING_INFO.toByteArray(), KEY_BYTES), "AES")
        mapKey = SecretKeySpec(hkdfExpand(pseudorandomKey, MAP_INFO.toByteArray(), KEY_BYTES), "AES")
        checkKey = hkdfExpand(pseudorandomKey, CHECK_INFO.toByteArray(), KEY_BYTES)
        commitmentKey = hkdfExpand(pseudorandomKey, COMMITMENT_INFO.toByteArray(), KEY_BYTES)
        pseudorandomKey.fill(0)
        sharedSecret.fill(0)
    }

    /** Seals exactly `length` bytes of an offer's hello from `input` into `output`, [sealedLength] bytes in all. */
    fun seal(input: InputStream, length: Long, output: OutputStream) = seal(sealingKey, input, length, output)

    /** Seals exactly `length` bytes of the map from `input` into `output`, [sealedLength] bytes in all. */
    fun sealMap(input: InputStream, length: Long, output: OutputStream) = seal(mapKey, input, length, output)

    private fun seal(key: SecretKeySpec, input: InputStream, length: Long, output: OutputStream) {
        require(length >= 0) { "An offer can't be $length bytes" }
        val count = chunkCount(length)
        val firstNonce = randomBytes(NONCE_BYTES)
        output.write(firstNonce)
        output.write(ByteBuffer.allocate(Int.SIZE_BYTES).putInt(count).array())
        val cipher = Cipher.getInstance(TRANSFORMATION)
        val plain = ByteArray(CHUNK_BYTES)
        val sealed = ByteArray(CHUNK_BYTES + TAG_BYTES)
        var left = length

        for (index in 0 until count) {
            val size = minOf(CHUNK_BYTES.toLong(), left).toInt()

            if (input.readUpTo(plain, size) < size) {
                throw IOException("The offer ended before its length")
            }

            cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(TAG_BYTES * Byte.SIZE_BITS, chunkNonce(firstNonce, index)))
            cipher.updateAAD(chunkData(count, index))
            output.write(sealed, 0, cipher.doFinal(plain, 0, size, sealed, 0))
            left -= size
        }
    }

    /**
     * The hello `input` holds, opened as it's read. Reading throws
     * [UnreadableOfferException] at the first chunk that fails, when it ends
     * early or has more after its last chunk, or when it would open to more
     * than `limit` bytes. Only what's read to its end has been checked whole.
     */
    fun open(input: InputStream, limit: Long): InputStream = OpenedOffer(sealingKey, input, limit)

    /** The map `input` holds, opened as it's read, as [open] opens a hello. */
    fun openMap(input: InputStream, limit: Long): InputStream = OpenedOffer(mapKey, input, limit)

    /** The sender's commitment to its half of the check code. */
    fun commitment(senderNonce: ByteArray): ByteArray = hmac(commitmentKey, senderNonce)

    /** Whether `senderNonce` is the half the sender committed to. */
    fun isCommitted(commitment: ByteArray, senderNonce: ByteArray): Boolean = MessageDigest.isEqual(commitment, commitment(senderNonce))

    /** The four digits both devices show, from the receiver's half of the check code and the sender's. */
    fun checkCode(receiverNonce: ByteArray, senderNonce: ByteArray): String {
        require(receiverNonce.size == CHECK_NONCE_BYTES && senderNonce.size == CHECK_NONCE_BYTES) { "Each half has $CHECK_NONCE_BYTES bytes" }
        val digest = hmac(checkKey, receiverNonce + senderNonce)
        val number = ByteBuffer.wrap(digest, 0, Long.SIZE_BYTES).long.toULong() % 10_000u
        return number.toString().padStart(4, '0')
    }

    private inner class OpenedOffer(private val key: SecretKeySpec, private val input: InputStream, private val limit: Long) : InputStream() {
        private val cipher = Cipher.getInstance(TRANSFORMATION)
        private val sealed = ByteArray(CHUNK_BYTES + TAG_BYTES)
        private val plain = ByteArray(CHUNK_BYTES)
        private val one = ByteArray(1)
        private var firstNonce: ByteArray? = null
        private var count = 0
        private var index = 0
        private var position = 0
        private var filled = 0
        private var failure: UnreadableOfferException? = null

        override fun read(): Int = if (read(one, 0, 1) < 0) -1 else one[0].toInt() and 0xFF

        override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
            // Once a chunk fails, nothing after it is opened, however often reading is tried again.
            failure?.let { throw it }

            if (length == 0) {
                return 0
            }

            try {
                while (position == filled) {
                    if (!openNext()) {
                        return -1
                    }
                }
            } catch (unreadable: UnreadableOfferException) {
                failure = unreadable
                throw unreadable
            }

            val served = minOf(length, filled - position)
            plain.copyInto(buffer, offset, position, position + served)
            position += served
            return served
        }

        override fun close() {
            input.close()
        }

        /** Opens the next chunk; false once every chunk is open. */
        private fun openNext(): Boolean {
            val nonce = firstNonce ?: readHeader()

            if (index == count) {
                return false
            }

            val last = index == count - 1
            val size = input.readUpTo(sealed, sealed.size)

            if (size < TAG_BYTES || (!last && size < sealed.size)) {
                throw UnreadableOfferException("The offer ends early")
            }

            filled = try {
                cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_BYTES * Byte.SIZE_BITS, chunkNonce(nonce, index)))
                cipher.updateAAD(chunkData(count, index))
                cipher.doFinal(sealed, 0, size, plain, 0)
            } catch (failed: GeneralSecurityException) {
                throw UnreadableOfferException("The offer isn't sealed to this device, or it changed on the way", failed)
            }

            if (last && input.read() >= 0) {
                throw UnreadableOfferException("The offer goes on after its last chunk")
            }

            position = 0
            index += 1
            return true
        }

        private fun readHeader(): ByteArray {
            val header = ByteArray(HEADER_BYTES)

            if (input.readUpTo(header, HEADER_BYTES) < HEADER_BYTES) {
                throw UnreadableOfferException("The offer ends early")
            }

            val nonce = header.copyOf(NONCE_BYTES)
            val chunks = ByteBuffer.wrap(header, NONCE_BYTES, Int.SIZE_BYTES).int

            // No chunks would mean nothing authenticated at all.
            if (chunks < 1 || chunks > chunkCount(limit)) {
                throw UnreadableOfferException("The offer has $chunks chunks")
            }

            count = chunks
            firstNonce = nonce
            return nonce
        }
    }

    companion object {
        /** The sender's side: a fresh key pair agreed with the receiver's key. Its public half, [senderKey], goes with the offer. */
        fun forSender(receiverKey: DeviceKey): OfferSession {
            val pair = newKeyPair()
            return OfferSession(receiverKey, DeviceKey.of(pair), agree(pair.private, receiverKey.key))
        }

        /** The receiver's side: its key pair for this launch, agreed with the key the sender sent. */
        fun forReceiver(receiver: KeyPair, senderKey: DeviceKey): OfferSession =
            OfferSession(DeviceKey.of(receiver), senderKey, agree(receiver.private, senderKey.key))

        private fun agree(own: PrivateKey, other: ECPublicKey): ByteArray = KeyAgreement.getInstance("ECDH").run {
            init(own)
            doPhase(other, true)
            generateSecret()
        }
    }
}

/** How many chunks an offer of `length` bytes is sealed in: at least one, so even an empty offer has a tag. */
internal fun chunkCount(length: Long): Int = maxOf(1L, (length + CHUNK_BYTES - 1) / CHUNK_BYTES).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()

/** How many bytes an offer of `length` bytes takes once sealed. */
fun sealedLength(length: Long): Long = HEADER_BYTES + chunkCount(length).toLong() * TAG_BYTES + length

/** Chunk `index`'s nonce: the first one with the index XORed into its last 8 bytes. */
private fun chunkNonce(first: ByteArray, index: Int): ByteArray {
    val nonce = first.copyOf()

    for (place in 0 until Long.SIZE_BYTES) {
        val at = NONCE_BYTES - 1 - place
        nonce[at] = (nonce[at].toInt() xor ((index.toLong() ushr (Byte.SIZE_BITS * place)).toInt() and 0xFF)).toByte()
    }

    return nonce
}

/** What each chunk authenticates besides itself: how many chunks there are, and which one it is. */
private fun chunkData(count: Int, index: Int): ByteArray = ByteBuffer.allocate(2 * Int.SIZE_BYTES).putInt(count).putInt(index).array()

private fun hmac(key: ByteArray, data: ByteArray): ByteArray {
    val mac = Mac.getInstance(HMAC)
    mac.init(SecretKeySpec(key, HMAC))
    return mac.doFinal(data)
}

/** Reads until `count` bytes are in `buffer` or the input ends. Returns how many were read. */
private fun InputStream.readUpTo(buffer: ByteArray, count: Int): Int {
    var read = 0

    while (read < count) {
        val got = read(buffer, read, count - read)

        if (got < 0) {
            break
        }

        read += got
    }

    return read
}
