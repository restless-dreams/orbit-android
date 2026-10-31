package com.hyuchia.orbit.data

import com.hyuchia.orbit.FileWords
import com.hyuchia.orbit.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InputStream
import java.util.Locale
import kotlin.coroutines.cancellation.CancellationException

class DroppedTest {
    private val words = FileWords(Locale.US)

    /** Text whose UTF-8 is exactly `size` bytes, ending in a character of three of them: where a cut there would split it. */
    private fun textOf(size: Int): String = "a".repeat(size - 3) + "€"

    /** A stream that gives `bytes`, then fails, as a provider's pipe does when the app behind it goes. */
    private fun failingAfter(bytes: ByteArray): InputStream = object : InputStream() {
        private val given = ByteArrayInputStream(bytes)

        override fun read(): Int = given.read().takeIf { it >= 0 } ?: throw IOException("The other app went away")
    }

    @Test
    fun `a drag's types say what it would make`() {
        assertEquals(DropKind.Ideas, dropKindOf("text/plain"))
        assertEquals(DropKind.Ideas, dropKindOf("text/html; charset=utf-8"))
        assertEquals(DropKind.Pictures, dropKindOf("image/jpeg"))
        assertEquals(DropKind.Videos, dropKindOf("video/mp4"))
        assertEquals(DropKind.Maps, dropKindOf("text/markdown"))
        assertEquals(DropKind.Maps, dropKindOf("application/zip"))
        assertEquals(DropKind.Maps, dropKindOf("text/x-opml"))
        assertEquals(DropKind.Maps, dropKindOf("application/vnd.xmind.workbook"))
        // A file that says nothing of what it is may be a map: its name says, once it's let go.
        assertEquals(DropKind.Maps, dropKindOf("application/octet-stream"))
        assertNull(dropKindOf("application/pdf"))
        assertEquals(DropKind.Sounds, dropKindOf("audio/mpeg"))
        assertEquals(DropKind.Sounds, dropKindOf("audio/mp4"))
    }

    @Test
    fun `a file is taken by its type, and by its name when the type says nothing`() {
        assertEquals(DroppedItem.Picture("content://photos/1", "Tram.jpg"), droppedFile("content://photos/1", "image/jpeg", "Tram.jpg"))
        assertEquals(DroppedItem.Video("content://videos/2", "Walk.mp4"), droppedFile("content://videos/2", "video/mp4", "Walk.mp4"))
        assertEquals(DroppedItem.Video("content://files/3", "Walk.webm"), droppedFile("content://files/3", "application/octet-stream", "Walk.webm"))
        assertEquals(DroppedItem.MapFile("content://files/4", "Trip.md"), droppedFile("content://files/4", "text/markdown", "Trip.md"))
        assertEquals(DroppedItem.MapFile("content://files/5", "Trip.opml"), droppedFile("content://files/5", "application/octet-stream", "Trip.opml"))
        assertEquals(DroppedItem.MapFile("content://files/6", "Plan.xmind"), droppedFile("content://files/6", null, "Plan.xmind"))
        assertEquals(DroppedItem.MapFile("content://files/7", null), droppedFile("content://files/7", "application/zip", null))
        assertEquals(DroppedItem.TextFile("content://files/8", "Groceries.txt"), droppedFile("content://files/8", "text/plain", "Groceries.txt"))
        assertNull(droppedFile("content://files/9", "application/pdf", "Ticket.pdf"))
        assertNull(droppedFile("content://files/10", "application/octet-stream", "Archive.tar"))
        // A video Orbit can't keep in a map isn't one.
        assertNull(droppedFile("content://files/11", "video/x-matroska", "Film.mkv"))
    }

    @Test
    fun `a sound is taken by its type, and by its name only when the type says nothing`() {
        assertEquals(DroppedItem.Sound("content://sounds/1", "Fado.mp3"), droppedFile("content://sounds/1", "audio/mpeg", "Fado.mp3"))
        // An m4a's type says it's MP4 audio: a sound, not a video.
        assertEquals(DroppedItem.Sound("content://sounds/2", "Interview.m4a"), droppedFile("content://sounds/2", "audio/mp4", "Interview.m4a"))
        assertEquals(DroppedItem.Sound("content://files/3", "Bells.ogg"), droppedFile("content://files/3", "application/octet-stream", "Bells.ogg"))
        assertEquals(DroppedItem.Sound("content://files/4", "Talk.m4a"), droppedFile("content://files/4", null, "Talk.m4a"))
        // One Orbit can't keep is still a sound: adding it says why it can't.
        assertEquals(DroppedItem.Sound("content://sounds/5", "Call.amr"), droppedFile("content://sounds/5", "audio/amr", "Call.amr"))
        assertEquals(DroppedItem.Video("content://videos/6", "Walk.mp4"), droppedFile("content://videos/6", "video/mp4", "Walk.mp4"))
        assertNull(droppedFile("content://files/7", "application/octet-stream", "Call.amr"))
    }

    @Test
    fun `a file's name names its idea, without its extension`() {
        assertEquals("Tram in Alfama", ideaName("Tram in Alfama.jpg"))
        assertEquals("notes.v2", ideaName("notes.v2.md"))
        assertEquals("README", ideaName("README"))
        assertNull(ideaName(".jpg"))
        assertNull(ideaName(null))
    }

    @Test
    fun `a text file comes whole up to its limit, its last character too`() {
        val text = textOf(64)
        assertEquals(64, text.toByteArray().size)
        assertEquals(TextFileRead.Read(text), readTextFile(64) { text.toByteArray().inputStream() })
        // Accents read as what they are, not as Windows-1252 from a cut that split one.
        assertEquals(TextFileRead.Read("Café, crème brûlée\n"), readTextFile(64) { "Café, crème brûlée\n".toByteArray().inputStream() })
        assertEquals(TextFileRead.Read("Lisboa ✈ Oporto"), readTextFile(64) { (byteArrayOf(0xFF.toByte(), 0xFE.toByte()) + "Lisboa ✈ Oporto".toByteArray(Charsets.UTF_16LE)).inputStream() })
    }

    @Test
    fun `a text file past its limit is refused whole, never cut`() {
        assertEquals(TextFileRead.TooLong, readTextFile(64) { textOf(65).toByteArray().inputStream() })
        assertEquals(TextFileRead.TooLong, readTextFile(64) { ByteArray(1_000) { 'a'.code.toByte() }.inputStream() })
    }

    @Test
    fun `a text file whose app fails says so, whatever the app throws`() {
        assertEquals(TextFileRead.NotAllowed, readTextFile(64) { throw SecurityException("Permission Denial: the drag's permission was taken back") })

        val failures = listOf<() -> InputStream?>(
            { throw IllegalArgumentException("Unknown URI") },
            { throw IllegalStateException("Couldn't read row 0") },
            { throw UnsupportedOperationException("No files here") },
            { throw FileNotFoundException("No content provider") },
            { throw RuntimeException("Something of the provider's own") },
            { null },
            { failingAfter("Milk\nEggs".toByteArray()) },
        )

        for (open in failures) {
            assertEquals(TextFileRead.Unreadable, readTextFile(64, open))
        }
    }

    @Test
    fun `what a provider fails to answer is nothing, and stopping still stops`() {
        assertEquals("text/plain", fromProvider { "text/plain" })
        assertNull(fromProvider<String> { throw SecurityException("Revoked") })
        assertNull(fromProvider<String> { throw IllegalStateException("Closed cursor") })

        val stopped = runCatching { fromProvider<String> { throw CancellationException("Left the screen") } }.exceptionOrNull()
        assertEquals(CancellationException::class.java, stopped?.javaClass)
    }

    @Test
    fun `a drop's text joins its text files whole, in the order they came`() {
        val files = mapOf("content://notes/1" to "Eggs\nBread", "content://notes/2" to "Café")
        val items = listOf(DroppedItem.Text("Milk"), DroppedItem.TextFile("content://notes/1", "Groceries.txt"), DroppedItem.TextFile("content://notes/2", "Later.txt"))

        assertEquals(DroppedText.Text("Milk\nEggs\nBread\nCafé"), droppedText(items, 64, "Too long", words) { files.getValue(it).toByteArray().inputStream() })
    }

    @Test
    fun `a drop with a text file that can't come in adds nothing, saying why, by its name when there's more`() {
        val long = DroppedItem.TextFile("content://notes/1", "Book.txt")
        val revoked = DroppedItem.TextFile("content://notes/2", "Groceries.txt")
        val open = { address: String -> if (address.endsWith("1")) textOf(65).toByteArray().inputStream() else throw SecurityException("Revoked") }

        assertEquals(DroppedText.Refused("That file is too big to be a map."), droppedText(listOf(long), 64, words.string(R.string.data_file_too_big_for_map), words, open))
        assertEquals(DroppedText.Refused("Book.txt: Too long"), droppedText(listOf(DroppedItem.Text("Milk"), long), 64, "Too long", words, open))
        assertEquals(DroppedText.Refused("Orbit isn't allowed to read that file."), droppedText(listOf(revoked), 64, "Too long", words, open))
        assertEquals(DroppedText.Refused("Groceries.txt: Orbit isn't allowed to read that file."), droppedText(listOf(revoked, long), 64, "Too long", words, open))

        val spanish = FileWords(Locale.forLanguageTag("es"))
        assertEquals(DroppedText.Refused("Orbit no tiene permiso para leer ese archivo."), droppedText(listOf(revoked), 64, "", spanish, open))
        assertEquals(
            DroppedText.Refused("Ese archivo es demasiado largo para agregarlo como ideas. Suéltalo en tus mapas para hacer un mapa con él."),
            droppedText(listOf(long), 64, spanish.string(R.string.drop_text_file_too_long), spanish, open),
        )
    }
}
