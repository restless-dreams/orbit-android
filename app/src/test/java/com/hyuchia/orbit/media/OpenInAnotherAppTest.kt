package com.hyuchia.orbit.media

import android.app.Activity
import android.content.Intent
import androidx.core.content.FileProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.hyuchia.orbit.OrbitApplication
import com.hyuchia.orbit.container
import com.hyuchia.orbit.data.FileStamp
import com.hyuchia.orbit.data.writeAtomically
import com.hyuchia.orbit.ui.openMapExport
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.io.File
import java.io.IOException
import java.nio.ByteBuffer

/**
 * "Open in another app" for a video in a map: the other app gets a copy
 * without where and when it was filmed and on what, and a video Orbit
 * can't clean goes as it is only once the person chose so.
 */
@RunWith(AndroidJUnit4::class)
@Config(application = OrbitApplication::class)
class OpenInAnotherAppTest {
    private val activity: Activity = Robolectric.buildActivity(Activity::class.java).setup().get()

    /**
     * The file provider keeps the folders it read in a cache of its own,
     * which outlives each test's folders: forgotten before and after, so
     * this test and the next find their own.
     */
    @Before
    @After
    fun forgetTheProvidersFolders() {
        (FileProvider::class.java.getDeclaredField("sCache").apply { isAccessible = true }.get(null) as MutableMap<*, *>).clear()
    }

    /** Where ffmpeg's video below was filmed, as phones write it. */
    private val place = "+38.7139-009.1394/"

    private val located = File("src/test/resources/outgoing/located.mp4").readBytes()

    /** A map, and its media folder with `video` in it as `name`. */
    private fun mapWith(name: String, video: ByteArray): Pair<String, File> = runBlocking {
        val maps = activity.container.maps
        maps.load()
        val id = maps.create("Trip")
        id to File(maps.folder(id), "media/$name").apply { parentFile!!.mkdirs() }.also { it.writeBytes(video) }
    }

    private fun exports(): List<File> = File(activity.cacheDir, "exports").walkTopDown().filter { it.isFile }.toList()

    private fun ByteArray.says(text: String): Boolean = String(this, Charsets.ISO_8859_1).contains(text)

    @Test
    fun `a map's video opens in another app as a clean copy, never as the map's own file`() {
        val (id, video) = mapWith("video-1.mp4", located)
        assertTrue(video.readBytes().says(place))

        // The way that handed the other app the map's own file opens nothing for it any more.
        openVideoElsewhere(activity, VideoSource.InMap(video))
        assertNull(shadowOf(activity).nextStartedActivity)

        assertEquals(Shared.Clean, runBlocking { openVideoInAnotherApp(activity, id, video) })
        val opened = shadowOf(activity).nextStartedActivity
        assertEquals(Intent.ACTION_VIEW, opened.action)
        assertEquals("video/mp4", opened.type)
        assertTrue(opened.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)

        // What it opens is a copy in the exports, without where it was filmed, every frame where it was.
        val copy = exports().single()
        assertEquals("video-1.mp4", copy.name)
        assertEquals("/exports/${copy.parentFile!!.name}/video-1.mp4", opened.data!!.path)
        assertFalse(copy.readBytes().says(place))
        assertEquals(located.size, copy.readBytes().size)
        val media = String(located, Charsets.ISO_8859_1).indexOf("mdat") - 4
        val mediaEnd = media + ByteBuffer.wrap(located, media, 4).int
        assertArrayEquals(located.copyOfRange(media, mediaEnd), copy.readBytes().copyOfRange(media, mediaEnd))
        // The map's own file stays as it was.
        assertArrayEquals(located, video.readBytes())
    }

    @Test
    fun `a video Orbit can't clean opens only once the person chose to open it as it is`() {
        // Cut short, it can't be read through: what it holds can't be told.
        val cut = located.copyOfRange(0, located.size - 100)
        val (id, video) = mapWith("video-2.mp4", cut)

        val asked = runBlocking { openVideoInAnotherApp(activity, id, video) }
        assertTrue(asked is Shared.NotClean)
        assertNull(shadowOf(activity).nextStartedActivity)
        assertEquals(emptyList<File>(), exports())

        // Chosen, the other app gets a copy of the very file asked about, never the map's own file.
        assertEquals(Shared.Clean, runBlocking { openVideoAsItIs(activity, id, video, (asked as Shared.NotClean).stamp) })
        val opened = shadowOf(activity).nextStartedActivity
        assertEquals(Intent.ACTION_VIEW, opened.action)
        val copy = exports().single()
        assertEquals("/exports/${copy.parentFile!!.name}/video-2.mp4", opened.data!!.path)
        assertArrayEquals(cut, copy.readBytes())
        assertTrue(opened.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
    }

    @Test
    fun `a video replaced after the person chose to open it as it is doesn't go, and is asked about again`() {
        val cut = located.copyOfRange(0, located.size - 100)
        val (id, video) = mapWith("video-5.mp4", cut)
        val asked = runBlocking { openVideoInAnotherApp(activity, id, video) } as Shared.NotClean

        // Another video Orbit can't clean takes its place under its name, the same size and time, before the person answers.
        val time = video.lastModified()
        writeAtomically(video, located.copyOfRange(100, located.size))
        video.setLastModified(time)

        assertEquals(Shared.Changed, runBlocking { openVideoAsItIs(activity, id, video, asked.stamp) })
        assertNull(shadowOf(activity).nextStartedActivity)
        assertEquals(emptyList<File>(), exports())

        // One Orbit can clean put there instead goes clean, without asking.
        writeAtomically(video, located)
        assertEquals(Shared.Clean, runBlocking { openVideoAsItIs(activity, id, video, asked.stamp) })
        assertFalse(exports().single().readBytes().says(place))
    }

    @Test
    fun `a video that leads elsewhere, through a link in the map's folder, never opens`() {
        val (id, video) = mapWith("video-6.mp4", located)
        val outside = File(activity.cacheDir, "outside.mp4").apply { writeBytes(located) }
        video.delete()
        java.nio.file.Files.createSymbolicLink(video.toPath(), outside.toPath())

        assertEquals(Shared.Failed, runBlocking { openVideoInAnotherApp(activity, id, video) })
        assertNull(shadowOf(activity).nextStartedActivity)
        assertEquals(emptyList<File>(), exports())
    }

    @Test
    fun `nothing opens for a private map that's locked, and its copy goes`() {
        val (id, video) = mapWith("video-3.mp4", located)
        runBlocking { activity.container.maps.setPrivate(id, true) }
        // Private maps lock on a phone with a screen lock.
        activity.container.privateMaps.lock.screenLockIs(true)
        activity.container.privateMaps.lock.lock()
        assertTrue(activity.container.privateMaps.hidesNow(id))

        runBlocking { openVideoInAnotherApp(activity, id, video) }
        runBlocking { openVideoAsItIs(activity, id, video, FileStamp.of(video.canonicalFile)!!) }

        assertNull(shadowOf(activity).nextStartedActivity)
        assertEquals(emptyList<File>(), exports())
    }

    @Test
    fun `a copy there's no room for isn't begun`() {
        val (id, _) = mapWith("video-4.mp4", located)
        var written = false

        val failed = try {
            runBlocking {
                openMapExport(activity, id, "video-4.mp4", "video/mp4") { output ->
                    // A copy says how big it is before any of it is written, as openVideoInAnotherApp's does.
                    invoke(Long.MAX_VALUE / 4)
                    written = true
                    output.write(ByteArray(64))
                }
            }

            false
        } catch (noRoom: IOException) {
            true
        }

        assertTrue(failed)
        assertFalse(written)
        assertNull(shadowOf(activity).nextStartedActivity)
        assertEquals(emptyList<File>(), exports())
    }
}
