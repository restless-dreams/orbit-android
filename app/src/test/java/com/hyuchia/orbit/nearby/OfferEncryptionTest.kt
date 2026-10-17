package com.hyuchia.orbit.nearby

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.file.Files
import java.security.DigestInputStream
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.spec.ECGenParameterSpec
import java.util.Random

/** Where a sealed offer's chunk count is: the header's last 4 bytes. */
private const val COUNT_AT = HEADER_BYTES - 4

class OfferEncryptionTest {
    private val receiver: KeyPair = newKeyPair()
    private val receiverKey = DeviceKey.of(receiver)

    private fun hex(text: String): ByteArray = text.chunked(2).map { it.toInt(16).toByte() }.toByteArray()

    private fun bytes(size: Int, seed: Long = 7): ByteArray = ByteArray(size).also { Random(seed).nextBytes(it) }

    /** A sender's session and the receiver's, agreed as they would be over the network: through the keys' text. */
    private fun pair(): Pair<OfferSession, OfferSession> {
        val sending = OfferSession.forSender(DeviceKey.read(receiverKey.text)!!)
        val receiving = OfferSession.forReceiver(receiver, DeviceKey.read(sending.senderKey.text)!!)
        return sending to receiving
    }

    private fun sealed(session: OfferSession, plain: ByteArray): ByteArray =
        ByteArrayOutputStream().also { session.seal(plain.inputStream(), plain.size.toLong(), it) }.toByteArray()

    private fun opened(session: OfferSession, sealed: ByteArray): ByteArray = session.open(sealed.inputStream(), OFFER_LIMIT).use { it.readBytes() }

    private fun assertUnreadable(session: OfferSession, sealed: ByteArray) {
        assertThrows(UnreadableOfferException::class.java) { opened(session, sealed) }
    }

    /** Chunk `index`'s place in a sealed offer, as (start, end). */
    private fun chunkAt(index: Int, sealed: ByteArray): Pair<Int, Int> {
        val start = HEADER_BYTES + index * (CHUNK_BYTES + TAG_BYTES)
        return start to minOf(start + CHUNK_BYTES + TAG_BYTES, sealed.size)
    }

    @Test
    fun `hkdf matches RFC 5869's test vectors`() {
        val first = hkdfExtract(hex("000102030405060708090a0b0c"), ByteArray(22) { 0x0b })
        assertArrayEquals(hex("077709362c2e32df0ddc3f0dc47bba6390b6c73bb50f9c3122ec844ad7c2b3e5"), first)
        assertArrayEquals(
            hex("3cb25f25faacd57a90434f64d0362f2a2d2d0a90cf1a5a4c5db02d56ecc4c5bf34007208d5b887185865"),
            hkdfExpand(first, hex("f0f1f2f3f4f5f6f7f8f9"), 42),
        )

        val second = hkdfExtract(ByteArray(80) { (0x60 + it).toByte() }, ByteArray(80) { it.toByte() })
        assertArrayEquals(hex("06a6b88c5853361a06104c9ceb35b45cef760014904671014a193f40c15fc244"), second)
        assertArrayEquals(
            hex(
                "b11e398dc80327a1c8e7f78c596a49344f012eda2d4efad8a050cc4c19afa97c59045a99cac7827271cb41c65e590e09da3275600c2f09b8367793a9aca3db71cc30c58179ec3e87c14c01d5c1f3434f1d87",
            ),
            hkdfExpand(second, ByteArray(80) { (0xb0 + it).toByte() }, 82),
        )

        val third = hkdfExtract(ByteArray(0), ByteArray(22) { 0x0b })
        assertArrayEquals(hex("19ef24a32c717b167f33a91d6f648bdf96596776afdb6377ac434c1c293ccb04"), third)
        assertArrayEquals(
            hex("8da4e775a563c18f715f802a063c5a31b8a11f5c5ee1879ec3454e5f3c738d2d9d201395faa4b61a96c8"),
            hkdfExpand(third, ByteArray(0), 42),
        )
    }

    @Test
    fun `a public key travels as X·509 in base64url, without padding`() {
        val text = receiverKey.text
        assertEquals(122, text.length)
        assertTrue(text, text.all { it.isLetterOrDigit() || it == '-' || it == '_' })
        val read = DeviceKey.read(text)!!
        assertArrayEquals(receiver.public.encoded, read.key.encoded)
        assertEquals(text, read.text)
    }

    @Test
    fun `only P-256 public keys are read`() {
        assertNull(DeviceKey.read(null))
        assertNull(DeviceKey.read(""))
        assertNull(DeviceKey.read("not a key at all"))
        assertNull(DeviceKey.read(base64Url(bytes(91))))
        val p384 = KeyPairGenerator.getInstance("EC").apply { initialize(ECGenParameterSpec("secp384r1")) }.generateKeyPair()
        assertNull(DeviceKey.read(base64Url(p384.public.encoded)))
        val rsa = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
        assertNull(DeviceKey.read(base64Url(rsa.public.encoded)))
    }

    @Test
    fun `a point off the curve is refused`() {
        val encoded = receiver.public.encoded.copyOf()
        // The last byte is the end of the point's y coordinate: changed, the point leaves the curve.
        encoded[encoded.size - 1] = (encoded[encoded.size - 1].toInt() xor 1).toByte()
        assertNull(DeviceKey.read(base64Url(encoded)))
    }

    @Test
    fun `offers open to what was sealed, at every size`() {
        val (sending, receiving) = pair()
        val sizes = listOf(0, 1, 1_000, CHUNK_BYTES - 1, CHUNK_BYTES, CHUNK_BYTES + 1, 3 * CHUNK_BYTES + 17, 2_000_000)

        for (size in sizes) {
            val plain = bytes(size, seed = size.toLong())
            val sealed = sealed(sending, plain)
            assertEquals("sealed length of $size", sealedLength(size.toLong()), sealed.size.toLong())
            assertEquals("chunks of $size", maxOf(1, (size + CHUNK_BYTES - 1) / CHUNK_BYTES), ByteBuffer.wrap(sealed, COUNT_AT, 4).int)
            assertArrayEquals("opened $size", plain, opened(receiving, sealed))
        }
    }

    @Test
    fun `nothing of the map shows in the sealed offer`() {
        val (sending, _) = pair()
        val plain = ("# Our secret plan\n- Buy the island\n".repeat(4_000)).toByteArray()
        val sealed = sealed(sending, plain).decodeToString()
        assertFalse(sealed.contains("secret"))
        assertFalse(sealed.contains("island"))
    }

    @Test
    fun `an offer as big as the limit opens, read as a stream`() {
        // The largest offer whose sealed form fits OFFER_LIMIT.
        val size = OFFER_LIMIT - HEADER_BYTES - chunkCount(OFFER_LIMIT) * TAG_BYTES.toLong()
        assertEquals(OFFER_LIMIT, sealedLength(size))
        assertTrue(sealedLength(size + 1) > OFFER_LIMIT)
        val (sending, receiving) = pair()
        val file = File.createTempFile("sealed", ".bin")

        try {
            val sent = MessageDigest.getInstance("SHA-256")

            file.outputStream().buffered().use { output ->
                DigestInputStream(SeededStream(size), sent).use { sending.seal(it, size, output) }
            }

            assertEquals(OFFER_LIMIT, file.length())
            val received = MessageDigest.getInstance("SHA-256")

            val count = DigestInputStream(receiving.open(file.inputStream().buffered(), OFFER_LIMIT), received).use { input ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                var total = 0L

                while (true) {
                    val read = input.read(buffer)

                    if (read < 0) {
                        break
                    }

                    total += read
                }

                total
            }

            assertEquals(size, count)
            assertArrayEquals(sent.digest(), received.digest())
        } finally {
            Files.deleteIfExists(file.toPath())
        }
    }

    @Test
    fun `a changed byte anywhere fails`() {
        val (sending, receiving) = pair()
        val sealed = sealed(sending, bytes(3 * CHUNK_BYTES + 100))
        val places = mapOf(
            "nonce" to 0,
            "chunk count" to COUNT_AT + 3,
            "first chunk" to HEADER_BYTES + 5,
            "middle chunk" to chunkAt(1, sealed).first + 1_000,
            "last chunk" to chunkAt(3, sealed).first + 3,
            "last tag" to sealed.size - 1,
        )

        for ((place, at) in places) {
            val changed = sealed.copyOf()
            changed[at] = (changed[at].toInt() xor 0x01).toByte()
            assertThrows(place, UnreadableOfferException::class.java) { opened(receiving, changed) }
        }
    }

    @Test
    fun `chunks in another order fail`() {
        val (sending, receiving) = pair()
        val sealed = sealed(sending, bytes(3 * CHUNK_BYTES))
        val (firstStart, firstEnd) = chunkAt(0, sealed)
        val (secondStart, secondEnd) = chunkAt(1, sealed)
        val swapped = sealed.copyOfRange(0, firstStart) + sealed.copyOfRange(secondStart, secondEnd) +
            sealed.copyOfRange(firstStart, firstEnd) + sealed.copyOfRange(secondEnd, sealed.size)
        assertEquals(sealed.size, swapped.size)
        assertUnreadable(receiving, swapped)
    }

    @Test
    fun `an offer cut short, or with more after it, fails`() {
        val (sending, receiving) = pair()
        val sealed = sealed(sending, bytes(2 * CHUNK_BYTES + 500))
        val (lastStart, _) = chunkAt(2, sealed)
        assertUnreadable(receiving, sealed.copyOf(lastStart))
        assertUnreadable(receiving, sealed.copyOf(sealed.size - 1))
        assertUnreadable(receiving, sealed.copyOf(chunkAt(1, sealed).first))
        assertUnreadable(receiving, sealed.copyOf(HEADER_BYTES))
        assertUnreadable(receiving, sealed.copyOf(HEADER_BYTES - 1))
        assertUnreadable(receiving, ByteArray(0))
        assertUnreadable(receiving, sealed + byteArrayOf(0))
        assertUnreadable(receiving, sealed + sealed.copyOfRange(lastStart, sealed.size))
    }

    @Test
    fun `an offer whose chunk count is changed fails, none included`() {
        val (sending, receiving) = pair()
        assertUnreadable(receiving, ByteArray(HEADER_BYTES))
        val sealed = sealed(sending, ByteArray(0))
        assertArrayEquals(ByteArray(0), opened(receiving, sealed))

        // No chunks would authenticate nothing at all; more chunks than sealed would read on past the end.
        for (count in listOf(0, 2, Int.MAX_VALUE)) {
            val changed = sealed.copyOf()
            ByteBuffer.wrap(changed, COUNT_AT, 4).putInt(count)
            assertUnreadable(receiving, changed)
        }

        // Without its tag, the one chunk an empty offer has fails too.
        assertUnreadable(receiving, sealed.copyOf(HEADER_BYTES))
    }

    @Test
    fun `an offer sealed to another device, or opened with another sender's key, fails`() {
        val (sending, receiving) = pair()
        val sealed = sealed(sending, bytes(1_000))
        val elsewhere = OfferSession.forReceiver(newKeyPair(), sending.senderKey)
        assertUnreadable(elsewhere, sealed)
        val someoneElse = OfferSession.forReceiver(receiver, DeviceKey.of(newKeyPair()))
        assertUnreadable(someoneElse, sealed)
        assertArrayEquals(bytes(1_000), opened(receiving, sealed))
    }

    @Test
    fun `chunks that failed give nothing, and the chunks before them are each authenticated`() {
        val (sending, receiving) = pair()
        val plain = bytes(2 * CHUNK_BYTES + 10)
        val sealed = sealed(sending, plain)
        val changed = sealed.copyOf()
        val (secondStart, _) = chunkAt(1, sealed)
        changed[secondStart] = (changed[secondStart].toInt() xor 0x80).toByte()
        val opened = receiving.open(changed.inputStream(), OFFER_LIMIT)
        val first = ByteArray(CHUNK_BYTES)
        assertEquals(CHUNK_BYTES, opened.read(first))
        assertArrayEquals(plain.copyOf(CHUNK_BYTES), first)
        assertThrows(UnreadableOfferException::class.java) { opened.read(ByteArray(10)) }
        // Trying again doesn't get past it to the chunks after.
        assertThrows(UnreadableOfferException::class.java) { opened.read(ByteArray(10)) }
        assertThrows(UnreadableOfferException::class.java) { opened.read() }
    }

    @Test
    fun `an offer bigger than the receiver takes fails before it's read`() {
        val (sending, receiving) = pair()
        val sealed = sealed(sending, bytes(3 * CHUNK_BYTES))
        assertThrows(UnreadableOfferException::class.java) { receiving.open(sealed.inputStream(), 2L * CHUNK_BYTES).read() }
    }

    @Test
    fun `both sides get the same four-digit check code`() {
        val (sending, receiving) = pair()
        val receiverHalf = randomBytes(CHECK_NONCE_BYTES)
        val senderHalf = randomBytes(CHECK_NONCE_BYTES)
        val code = sending.checkCode(receiverHalf, senderHalf)
        assertTrue(code, Regex("[0-9]{4}").matches(code))
        assertEquals(code, receiving.checkCode(receiverHalf, senderHalf))
    }

    @Test
    fun `check codes differ from offer to offer`() {
        val receiverHalf = randomBytes(CHECK_NONCE_BYTES)
        val senderHalf = randomBytes(CHECK_NONCE_BYTES)
        val codes = List(20) { pair().first.checkCode(receiverHalf, senderHalf) }
        assertTrue(codes.toString(), codes.toSet().size >= 15)
        // Within one offer, each half changes the code too.
        val (session, _) = pair()
        val fromReceivers = List(20) { session.checkCode(randomBytes(CHECK_NONCE_BYTES), senderHalf) }
        val fromSenders = List(20) { session.checkCode(receiverHalf, randomBytes(CHECK_NONCE_BYTES)) }
        assertTrue(fromReceivers.toString(), fromReceivers.toSet().size >= 15)
        assertTrue(fromSenders.toString(), fromSenders.toSet().size >= 15)
    }

    @Test
    fun `check codes are spread evenly, leading zeros included`() {
        val (session, _) = pair()
        val receiverHalf = randomBytes(CHECK_NONCE_BYTES)
        val firstDigits = IntArray(10)

        repeat(20_000) {
            val code = session.checkCode(receiverHalf, randomBytes(CHECK_NONCE_BYTES))
            assertEquals(4, code.length)
            firstDigits[code[0] - '0'] += 1
        }

        // Each first digit is expected 2,000 times, give or take about 42.
        firstDigits.forEachIndexed { digit, count -> assertTrue("$digit: $count", count in 1_600..2_400) }
    }

    @Test
    fun `the sender's half opens only its own commitment`() {
        val (sending, receiving) = pair()
        val half = randomBytes(CHECK_NONCE_BYTES)
        val commitment = sending.commitment(half)
        assertEquals(COMMITMENT_BYTES, commitment.size)
        assertTrue(receiving.isCommitted(commitment, half))
        assertFalse(receiving.isCommitted(commitment, randomBytes(CHECK_NONCE_BYTES)))
        // A commitment made for another offer doesn't open in this one.
        val (other, _) = pair()
        assertFalse(receiving.isCommitted(other.commitment(half), half))
    }

    /** `size` bytes of a seeded generator, without keeping them. */
    private class SeededStream(private var left: Long) : InputStream() {
        private val random = Random(64)

        override fun read(): Int {
            val one = ByteArray(1)
            return if (read(one, 0, 1) < 0) -1 else one[0].toInt() and 0xFF
        }

        override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
            if (left == 0L) {
                return -1
            }

            val count = minOf(length.toLong(), left).toInt()
            val chunk = ByteArray(count).also(random::nextBytes)
            chunk.copyInto(buffer, offset)
            left -= count
            return count
        }
    }
}
