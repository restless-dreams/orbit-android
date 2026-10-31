package com.hyuchia.orbit.data

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.time.LocalDateTime

/**
 * Live photos as phones write them, made small by `scripts/live-photos/make-samples.py`
 * in `resources/live/`: each keeps the same 1.5-second MP4 its own way.
 */
class LivePhotosTest {
    private fun sample(name: String) = File(javaClass.getResource("/live/$name")!!.toURI())

    private val video: ByteArray by lazy { sample("motion.mp4").readBytes() }

    /** The motion copied out of `name`, and how it was found. */
    private fun motion(name: String): Pair<EmbeddedMotion, ByteArray> {
        val file = sample(name)
        val motion = LivePhotos.motionIn(file) ?: error("No motion found in $name")
        val copy = ByteArrayOutputStream().also { LivePhotos.copyMotion(file, motion, it) }.toByteArray()
        return motion to copy
    }

    @Test
    fun `Google's motion photo gives its video, the file's last bytes, and when its still was taken`() {
        val (motion, copy) = motion("google-motion-photo.jpg")
        assertEquals(MotionKind.Google, motion.kind)
        assertArrayEquals(video, copy)
        assertEquals(sample("google-motion-photo.jpg").length() - video.size, motion.start)
        assertEquals(733_333L, motion.stillMicros)
    }

    @Test
    fun `an older Pixel's MicroVideo is read by its namespace, whatever its prefix`() {
        val (motion, copy) = motion("micro-video.jpg")
        assertEquals(MotionKind.MicroVideo, motion.kind)
        assertArrayEquals(video, copy)
        assertEquals(1_100_000L, motion.stillMicros)
    }

    @Test
    fun `Samsung's trailer gives the video in its MotionPhoto_Data block, and nothing of the trailer after it`() {
        val (motion, copy) = motion("samsung-motion-photo.jpg")
        assertEquals(MotionKind.Samsung, motion.kind)
        assertArrayEquals(video, copy)
        assertNull(motion.stillMicros)
    }

    @Test
    fun `Huawei's trailer says where the video starts, past a gain map and padding, and its frame table stays out`() {
        val (motion, copy) = motion("huawei-moving-picture.jpg")
        assertEquals(MotionKind.Huawei, motion.kind)
        assertArrayEquals(video, copy)
    }

    @Test
    fun `an MP4 right after a JPEG counts, and a plain picture has no motion`() {
        val (motion, copy) = motion("appended-video.jpg")
        assertEquals(MotionKind.Appended, motion.kind)
        assertArrayEquals(video, copy)
        assertNull(LivePhotos.motionIn(sample("plain.jpg")))
        assertNull(LivePhotos.motionIn(sample("motion.mp4")))
    }

    @Test
    fun `a HEIC motion photo keeps its video in an mpvd box, its still's time in an XMP item`() {
        val (motion, copy) = motion("google-motion-photo.heic")
        assertEquals(MotionKind.Heif, motion.kind)
        assertArrayEquals(video, copy)
        assertEquals(500_000L, motion.stillMicros)
        assertNull(LivePhotos.motionIn(sample("iphone-live.heic")))
    }

    @Test
    fun `a video that isn't whole, or a motion photo whose video is gone, has no motion`() {
        val whole = sample("google-motion-photo.jpg").readBytes()
        val cut = File.createTempFile("cut", ".jpg").apply { writeBytes(whole.copyOf(whole.size - 1000)); deleteOnExit() }
        assertNull(LivePhotos.motionIn(cut))
        // XMP still says it's a motion photo, but the video was taken off.
        val stripped = File.createTempFile("stripped", ".jpg").apply { writeBytes(whole.copyOf(whole.size - video.size)); deleteOnExit() }
        assertNull(LivePhotos.motionIn(stripped))
    }

    @Test
    fun `an iPhone's still and movie share an id, and say when they were taken and where the still is`() {
        val still = LivePhotos.stillFacts(sample("iphone-live.heic"))!!
        assertEquals("C0633EC8-48F1-43C9-84DC-23C480CA1FA4", still.identifier)
        assertEquals(LocalDateTime.of(2022, 11, 19, 11, 14, 36), still.taken)

        val movie = LivePhotos.liveVideo(sample("iphone-live.mov"))!!
        assertEquals("C0633EC8-48F1-43C9-84DC-23C480CA1FA4", movie.identifier)
        assertTrue(movie.live)
        assertEquals(LocalDateTime.of(2022, 11, 19, 11, 14, 36), movie.created)

        // The JPEG an iPhone sends lost the id on the way; when it was taken is left.
        val sent = LivePhotos.stillFacts(sample("iphone-still.jpg"))!!
        assertNull(sent.identifier)
        assertEquals(LocalDateTime.of(2022, 11, 19, 11, 14, 37), sent.taken)

        val plain = LivePhotos.liveVideo(sample("motion.mp4"))!!
        assertNull(plain.identifier)
        assertFalse(plain.live)
    }

    @Test
    fun `a web page takes the map's motions in its order while they fit, skipping one too big for what's left`() {
        val folder = java.nio.file.Files.createTempDirectory("orbit-motions").toFile()

        try {
            val media = File(folder, "media").apply { mkdirs() }
            File(media, "picture-1-motion.mp4").writeBytes(TestMedia.movie(100))
            File(media, "picture-2-motion.mov").writeBytes(TestMedia.movie(400))
            File(media, "picture-3-motion.mp4").writeBytes(TestMedia.movie(200))
            File(media, "video-1.mp4").writeBytes(TestMedia.movie(50))
            val markdown = """
                # Trip
                - Lake <!-- orbit image: { src: media/picture-1.jpg, motion: media/picture-1-motion.mp4 } -->
                - Pier <!-- orbit image: { src: media/picture-2.jpg, motion: media/picture-2-motion.mov } -->
                - Summit <!-- orbit image: { src: media/picture-3.jpg, motion: { src: media/picture-3-motion.mp4, still: 1.5 } } -->
                - Gone <!-- orbit image: { src: media/picture-4.jpg, motion: media/gone-motion.mp4 } -->
                - Hike <!-- orbit video: media/video-1.mp4 -->
            """.trimIndent()

            assertEquals(listOf("picture-1-motion.mp4", "picture-3-motion.mp4"), motionsFitting(markdown, folder, limit = 350).map { it.name })
        } finally {
            folder.deleteRecursively()
        }
    }

    @Test
    fun `a movie's key whose size would wrap around past the check is passed over, and the movie still reads`() {
        val movie = sample("iphone-live.mov").readBytes()
        // The first key's size, after the `keys` box's type, its version and flags, and its count.
        val keys = String(movie, Charsets.ISO_8859_1).indexOf("keys")
        ByteBuffer.wrap(movie, keys + 4 + 8, 4).putInt(Int.MAX_VALUE)

        val read = LivePhotos.liveVideo(ArrayBytes(movie))
        assertNotNull(read)
        assertNull(read!!.identifier)
    }

    @Test
    fun `XMP with a document type isn't read, whatever the parser allows`() {
        val packet = "<x:xmpmeta xmlns:x=\"adobe:ns:meta/\"><rdf:RDF xmlns:rdf=\"http://www.w3.org/1999/02/22-rdf-syntax-ns#\">" +
            "<rdf:Description xmlns:GCamera=\"http://ns.google.com/photos/1.0/camera/\" GCamera:MotionPhoto=\"1\"/></rdf:RDF></x:xmpmeta>"

        assertEquals(1, Xmp.read(packet)?.motionPhoto)
        assertNull(Xmp.read("<!DOCTYPE x [<!ENTITY a SYSTEM \"file:///etc/hosts\">]>$packet"))
        assertNull(Xmp.read("<!doctype x>$packet"))
    }
}
