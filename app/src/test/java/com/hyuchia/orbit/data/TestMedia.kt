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
