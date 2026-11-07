package com.hyuchia.orbit.data

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.hyuchia.orbit.FileWords
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.io.RandomAccessFile
import java.util.Locale

/**
 * Pictures, live photos' movies, videos and sounds another app hands a map
 * through its content provider, as the photo picker and file managers do:
 * each counted once against the map's room, kept within it whatever size
 * the app says it has, and nothing left behind when one can't come.
 */
@RunWith(AndroidJUnit4::class)
class MapMediaIntakeTest {
    /** Another app's files: each with its name, type and bytes, and the size it says they have (none when it says nothing). */
    class Gallery : ContentProvider() {
        override fun onCreate(): Boolean = true

        override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor? {
            val file = FILES[uri.lastPathSegment] ?: return null
            return MatrixCursor(arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE)).apply { addRow(arrayOf<Any?>(file.name, file.said)) }
        }

        override fun getType(uri: Uri): String? = FILES[uri.lastPathSegment]?.type

        override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
            val file = FILES[uri.lastPathSegment] ?: throw FileNotFoundException(uri.toString())

            if (file.gone) {
                throw FileNotFoundException("$uri went before it was read")
            }

            val copy = File.createTempFile("shared", ".bin").apply {
                deleteOnExit()
                writeBytes(file.bytes)
            }

            return ParcelFileDescriptor.open(copy, ParcelFileDescriptor.MODE_READ_ONLY)
        }

        override fun insert(uri: Uri, values: ContentValues?): Uri? = null

        override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0

        override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0
    }

    /** One of the other app's files: `said` is the size it says it has, true or not; `gone` ones can't be read. */
    class Shared(val name: String, val type: String, val bytes: ByteArray, val said: Long? = bytes.size.toLong(), val gone: Boolean = false)

    @get:Rule
    val folder = TemporaryFolder()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val locale = Locale.getDefault()
    private val english = FileWords(Locale.US)

    private val starters = object : StarterMaps {
        override fun samples() = emptyList<String>()

        override fun welcome() = StarterMap("# Welcome\n")
    }

    private lateinit var maps: MapRepository

    private fun address(key: String): Uri = Uri.parse("content://$AUTHORITY/files/$key")

    private fun live(name: String): ByteArray = File("src/test/resources/live/$name").readBytes()

    /** Where pictures and movies coming in are copied to be read whole, beside the maps. */
    private val incoming: File get() = File(folder.root, INCOMING_DIRECTORY)

    /** Makes `file` `size` bytes long without writing them, as a sparse file: a map's room is reached in a moment. */
    private fun sized(file: File, size: Long) {
        RandomAccessFile(file, "rw").use { it.setLength(size) }
    }

    /** A map with these files in its media folder, as many bytes as said, which its text points at. */
    private suspend fun mapWith(vararg files: Pair<String, Long>): String {
        val id = maps.add("# Trip\n" + files.joinToString("") { (name, _) -> "- ${name.substringBefore('.')} ![](media/$name)\n" })
        val media = File(maps.folder(id), MEDIA_DIRECTORY).apply { mkdirs() }
        files.forEach { (name, size) -> sized(File(media, name), size) }
        return id
    }

    private fun media(id: String): List<String> = File(maps.folder(id), MEDIA_DIRECTORY).list().orEmpty().sorted()

    @Before
    fun start() = runBlocking {
        Locale.setDefault(Locale.US)
        Robolectric.setupContentProvider(Gallery::class.java, AUTHORITY)
        FILES.clear()
        maps = MapRepository(folder.newFolder("maps"), folder.newFolder("trash"), starters, english)
        maps.load()
    }

    @After
    fun restoreLocale() {
        Locale.setDefault(locale)
    }

    @Test
    fun aPictureGivenItsMotionCountsOnceAgainstTheMapsRoom() = runBlocking {
        // A map holding a 970 MB video and a 10 MB picture, which an iPhone's movie of a few kilobytes makes a live photo of.
        val id = mapWith("video-1.mp4" to 970_000_000, "picture-1.jpg" to 10_000_000)
        FILES["movie"] = Shared("IMG_0911.MOV", "video/quicktime", live("iphone-live.mov"))

        // With it the map comes to 980 MB, within its 1 GB: the picture is counted with its motion, and not again among the map's media.
        val motion = maps.addMotion(id, "media/picture-1.jpg", address("movie"), context.contentResolver)

        assertTrue(motion.src, motion.src.startsWith("media/picture-1-motion."))
        assertTrue(File(maps.folder(id), motion.src).isFile)
    }

    @Test
    fun aMotionPastTheMapsRoomIsRefusedAndNothingOfItKept() = runBlocking {
        // 989.99 MB of video, a 10 MB picture and the movie come to more than the map's 1 GB.
        val id = mapWith("video-1.mp4" to 989_990_000, "picture-1.jpg" to 10_000_000)
        FILES["movie"] = Shared("IMG_0911.MOV", "video/quicktime", live("iphone-live.mov"))

        try {
            maps.addMotion(id, "media/picture-1.jpg", address("movie"), context.contentResolver)
            fail("The motion should have been refused")
        } catch (refused: MediaRefusedException) {
            assertTrue(refused.message!!, refused.message!!.startsWith("This map's pictures, videos and sounds come to 990\u00A0MB already."))
        }

        assertEquals(listOf("picture-1.jpg", "video-1.mp4"), media(id))
        assertEquals(emptyList<String>(), incoming.list().orEmpty().toList())
    }

    @Test
    fun aLivePhotoWhoseMovieCantBeCopiedLeavesNoCopyOfItsStillBehind() = runBlocking {
        val id = maps.add("# Trip\n")
        FILES["still"] = Shared("IMG_0911.HEIC", "image/heic", live("iphone-live.heic"))
        FILES["movie"] = Shared("IMG_0911.MOV", "video/quicktime", live("iphone-live.mov"), gone = true)

        try {
            maps.addLivePhoto(id, address("still"), address("movie"), context.contentResolver)
            fail("A movie that can't be read can't be copied")
        } catch (unreadable: IOException) {
            // The still was copied in to be read, and goes with the movie's failed copy.
        }

        assertEquals(emptyList<String>(), incoming.list().orEmpty().toList())
        assertEquals(emptyList<String>(), media(id))
    }

    @Test
    fun aSoundBiggerThanItsAppSaysCantTakeTheMapPastItsRoom() = runBlocking {
        // A map one megabyte short of its room, and a sound its app says is a kilobyte that comes to two megabytes.
        val id = mapWith("video-1.mp4" to MAP_MEDIA_LIMIT - 1_000_000)
        FILES["sound"] = Shared("Interview.mp3", "audio/mpeg", TestMedia.mp3() + ByteArray(2_000_000), said = 1_000)

        try {
            maps.addSound(id, address("sound"), context.contentResolver) {}
            fail("The sound should have been refused")
        } catch (refused: MediaRefusedException) {
            assertTrue(refused.message!!, refused.message!!.startsWith("This map's pictures, videos and sounds come to 999\u00A0MB already. With this sound"))
        }

        // Nothing of it stays: not the sound, nor a part of it.
        assertEquals(listOf("video-1.mp4"), media(id))
    }

    @Test
    fun aVideoMadeSmallerTakesItsPlaceOnlyOnceItsWhole() = runBlocking {
        val id = maps.add("# Trip\n")
        val made = TestMedia.movie(3_000_000)
        var madeInto: File? = null

        val added = maps.addMadeVideo(id, estimate = 3_000_000) { file ->
            // Made beside its place, hidden, as a write's partial file is.
            assertTrue(file.name, file.name.startsWith(".") && file.name.endsWith(".partial"))
            madeInto = file
            file.writeBytes(made)
        }

        assertTrue(added.src, added.src.startsWith("media/video-") && added.src.endsWith(".mp4"))
        assertTrue(File(maps.folder(id), added.src).readBytes().contentEquals(made))
        assertEquals(made.size.toLong(), added.bytes)
        assertFalse(madeInto!!.exists())
        assertEquals(listOf(added.src.removePrefix("media/")), media(id).filterNot { it.endsWith(".jpg") })
    }

    @Test
    fun aVideoMadeSmallerThatsStoppedOrFailsLeavesNothingBehind() = runBlocking {
        val id = maps.add("# Trip\n")

        // Stopped partway, as its Stop stops it.
        val stopped = runCatching { maps.addMadeVideo(id, estimate = 3_000_000) { file -> file.writeBytes(ByteArray(1_000)); throw CancellationException("Stopped") } }
        assertTrue(stopped.exceptionOrNull() is CancellationException)
        assertEquals(emptyList<String>(), media(id))

        // A phone that couldn't make it after all.
        val failed = runCatching { maps.addMadeVideo(id, estimate = 3_000_000) { file -> file.writeBytes(ByteArray(1_000)); throw IOException("The encoder stopped") } }
        assertTrue(failed.exceptionOrNull() is IOException)
        assertEquals(emptyList<String>(), media(id))
    }

    @Test
    fun aVideoMadeSmallerPastTheMapsRoomIsRefusedBeforeItsMadeAndAsItComesOut() = runBlocking {
        val id = mapWith("video-1.mp4" to MAP_MEDIA_LIMIT - 1_000_000)
        var made = false

        // About 2 MB, which the map hasn't room for: refused before anything's made.
        try {
            maps.addMadeVideo(id, estimate = 2_000_000) { made = true }
            fail("The video should have been refused")
        } catch (refused: MediaRefusedException) {
            assertTrue(refused.message!!, refused.message!!.startsWith("This map's pictures, videos and sounds come to 999\u00A0MB already. With this video"))
        }

        assertFalse(made)

        // Planned within the room, it came out past it: refused all the same, and nothing of it stays.
        try {
            maps.addMadeVideo(id, estimate = 500_000) { file -> sized(file, 2_000_000) }
            fail("The video should have been refused")
        } catch (refused: MediaRefusedException) {
            assertTrue(refused.message!!, refused.message!!.startsWith("This map's pictures, videos and sounds come to 999\u00A0MB already. With this video"))
        }

        assertEquals(listOf("video-1.mp4"), media(id))
    }

    @Test
    fun aVideoBiggerThanItsAppSaysCantTakeTheMapPastItsRoom() = runBlocking {
        val id = mapWith("picture-1.jpg" to MAP_MEDIA_LIMIT - 1_000_000)
        FILES["video"] = Shared("Waves.mp4", "video/mp4", TestMedia.movie(2_000_000), said = 1_000)

        try {
            maps.addVideo(id, address("video"), context.contentResolver) {}
            fail("The video should have been refused")
        } catch (refused: MediaRefusedException) {
            assertTrue(refused.message!!, refused.message!!.startsWith("This map's pictures, videos and sounds come to 999\u00A0MB already. With this video"))
        }

        assertEquals(listOf("picture-1.jpg"), media(id))
    }

    @Test
    fun aSoundThatSaysNoSizeComesInWhileItFitsTheMapsRoom() = runBlocking {
        val id = mapWith("video-1.mp4" to MAP_MEDIA_LIMIT - 1_000_000)
        val sound = TestMedia.mp3()
        FILES["sound"] = Shared("Interview.mp3", "audio/mpeg", sound, said = null)

        val added = maps.addSound(id, address("sound"), context.contentResolver) {}

        assertEquals("Interview", added.caption)
        assertEquals(sound.size.toLong(), File(maps.folder(id), added.src).length())
    }

    private companion object {
        const val AUTHORITY = "com.example.gallery"

        /** The files the other app shares, by the last part of their address. */
        val FILES = mutableMapOf<String, Shared>()
    }
}
