package com.hyuchia.orbit.data

import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.util.Random
import javax.imageio.ImageIO

/**
 * Small files of the kinds a map's media folder keeps, whole enough to go
 * to other people ([OutgoingMedia]): tests that send a map's media use
 * these, as bytes of no kind in their place stay on the phone.
 */
internal object TestMedia {
    /** A movie of `size` bytes, at least 32, as a voice memo is too: its type, an empty movie, and media data that fills the rest. */
    fun movie(size: Int): ByteArray {
        val type = ByteBuffer.allocate(16).putInt(16).put("ftypisom".toByteArray(Charsets.ISO_8859_1)).putInt(0).array()
        val movie = ByteBuffer.allocate(8).putInt(8).put("moov".toByteArray(Charsets.ISO_8859_1)).array()
        val data = ByteBuffer.allocate(size - type.size - movie.size).putInt(size - type.size - movie.size).put("mdat".toByteArray(Charsets.ISO_8859_1)).array()
        return type + movie + data
    }

    /**
     * A live photo's movie as an iPhone records its sound: its type, then a
     * movie whose sound track says it has `frames` frames of 16-bit sound on
     * `channels` channels at `rate`, uncompressed (`sowt`), in one chunk of
     * the media data, which holds `samples`, as many as are there.
     */
    fun liveMovie(samples: ByteArray, frames: Int, channels: Int = 2, rate: Int = 48_000): ByteArray {
        fun box(type: String, vararg content: ByteArray): ByteArray {
            val body = content.fold(ByteArray(0)) { all, part -> all + part }
            return ByteBuffer.allocate(8).putInt(8 + body.size).put(type.toByteArray(Charsets.ISO_8859_1)).array() + body
        }

        fun int(value: Int): ByteArray = ByteBuffer.allocate(4).putInt(value).array()

        fun short(value: Int): ByteArray = ByteBuffer.allocate(2).putShort(value.toShort()).array()

        fun full(vararg content: ByteArray): ByteArray = ByteArray(4) + content.fold(ByteArray(0)) { all, part -> all + part }

        // QuickTime's first version of a sound description: its channels, bits and rate, in 16.16.
        val description = box("sowt", ByteArray(6), short(1), short(0), short(0), int(0), short(channels), short(16), short(0), short(0), int(rate shl 16))

        val movie = { chunkAt: Int ->
            val table = box(
                "stbl",
                box("stsd", full(int(1)), description),
                box("stts", full(int(1), int(frames), int(1))),
                box("stsc", full(int(1), int(1), int(frames), int(1))),
                box("stsz", full(int(1), int(frames))),
                box("stco", full(int(1), int(chunkAt))),
            )

            box("moov", box("trak", box("mdia", box("hdlr", full(int(0), "soun".toByteArray(Charsets.ISO_8859_1), ByteArray(12), byteArrayOf(0))), box("minf", table))))
        }

        val type = box("ftyp", "qt  ".toByteArray(Charsets.ISO_8859_1), int(0), "qt  ".toByteArray(Charsets.ISO_8859_1))
        val chunkAt = type.size + movie(0).size + 8
        return type + movie(chunkAt) + ByteBuffer.allocate(8).putInt(8 + frames * channels * 2).put("mdat".toByteArray(Charsets.ISO_8859_1)).array() + samples
    }

    /** A JPEG of `width` by `height` pixels, of colors that change across it, or of `noise`, which takes far more bytes. */
    fun picture(width: Int = 24, height: Int = 16, noise: Long? = null): ByteArray {
        val image = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
        val random = noise?.let(::Random)

        for (x in 0 until width) {
            for (y in 0 until height) {
                image.setRGB(x, y, random?.nextInt() ?: ((x * 9 shl 16) or (y * 13 shl 8) or 0x40))
            }
        }

        return ByteArrayOutputStream().also { ImageIO.write(image, "jpg", it) }.toByteArray()
    }

    /** An MP3's frames, without its tags: `outgoing/tagged.mp3`'s, half a second of a tone. */
    fun mp3(): ByteArray = File("src/test/resources/outgoing/tagged.mp3").readBytes().let { tagged ->
        val frames = 10 + (6..9).fold(0) { size, at -> (size shl 7) or (tagged[at].toInt() and 0x7F) }
        tagged.copyOfRange(frames, tagged.size - 128)
    }

    /** A FLAC with its tags and cover, `outgoing/tagged.flac`. */
    fun flac(): ByteArray = File("src/test/resources/outgoing/tagged.flac").readBytes()
}
