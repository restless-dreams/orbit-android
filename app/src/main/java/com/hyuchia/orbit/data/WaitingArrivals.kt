package com.hyuchia.orbit.data

import com.hyuchia.orbit.Logs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/** What a waiting arrival keeps of itself in the folder its zip was unpacked into, beside its files. */
internal const val WAITING_ARRIVAL = ".arrival.json"

/**
 * Where an arrival answered Keep both is made into a map, in its own
 * folder, until the map takes its place among the others in one step: its
 * files never leave the arrival for anywhere a start's cleaning of
 * leftovers would delete them ([MapBundles.receive]).
 */
internal const val ARRIVAL_COPY = ".copy"

/**
 * An arrival answered Update or Keep both (`keepBoth`), kept on disk with
 * its answer until it's in ([WaitingArrivals.accept]). Update writes over
 * the map only while its text hashes to `base`, the text it had when it
 * was answered, or is the arrival's already; a map it adds takes the
 * identity `copy`, so it's never added twice. `opens` its map once it's in.
 */
class AcceptedArrival(val arrival: ImportResult.AlreadyHere, val keepBoth: Boolean, val base: String?, val copy: String, val opens: Boolean)

/**
 * Files of maps that are here already in other versions, waiting for an
 * answer (update the map, keep both, or neither), kept on disk where their
 * zips were unpacked, in Orbit's own files: each one's text and what it says
 * of itself, beside its pictures, videos, sounds and files. Nothing unpacked
 * goes into backups, or anywhere another app reaches. Android may stop Orbit
 * while one is asked about (as the screen lock is asked for, to update a
 * private map), and the screens it brings back ask about it again; each goes
 * once it's answered Neither, or once no screen will ask about it any more.
 *
 * One answered Update or Keep both keeps its answer beside it until it's in
 * ([accept]), whatever stops Orbit meanwhile: Orbit takes it in when it
 * starts again ([answered]). One that couldn't be taken in is asked about
 * again by every screen that starts ([askAgain], [askedAgain]), its files
 * still kept: they're in its folder, in the copy being made of it there
 * ([ARRIVAL_COPY]), or, moved by an Update that stopped, in the map's own.
 */
class WaitingArrivals internal constructor(
    private val unpacking: File,

    /** The maps' folder, where an Update that stopped left some of its files, in the map it updates. */
    private val maps: File,
) {
    /** The folders of the arrivals being taken in now, which nothing lets go of meanwhile, nor clears as left over. */
    private val takingIn: MutableSet<String> = ConcurrentHashMap.newKeySet()

    /** When the latest answer was kept, so each is later than the one before, even in the same millisecond. */
    private val lastAnswer = AtomicLong()

    /**
     * `arrival` was answered, and is being taken in: its files are moving
     * into its map, so no screen that starts meanwhile lets it go, nor does
     * a start's cleaning of leftovers ([isTakingIn]), until it's let go.
     */
    fun takeIn(arrival: ImportResult.AlreadyHere) {
        arrival.bundle.unpacked?.let { takingIn += it.name }
    }

    /** Whether the arrival in folder `id` is being taken in now ([takeIn]). */
    fun isTakingIn(id: String): Boolean = id in takingIn

    /**
     * Keeps `arrival` on disk, waiting for an answer, until it's let go
     * ([letGo]); false when it can't be, as for a bundle that wasn't
     * unpacked, or a full storage. One answered already stays answered.
     * With `ask`, every screen that starts asks about it ([askedAgain]), as
     * one a file opened with Orbit brought before any screen asked it.
     */
    suspend fun keep(arrival: ImportResult.AlreadyHere, opens: Boolean = false, ask: Boolean = false): Boolean = withContext(Dispatchers.IO) {
        val folder = folderOf(arrival) ?: return@withContext false

        synchronized(this@WaitingArrivals) {
            recordIn(folder)?.answer != null || write(folder, recordOf(arrival, folder, opens).copy(ask = ask))
        }
    }

    /** The arrival kept in folder `id`, as it was kept, while it waits for an answer; null once it's answered, when it isn't there any more, or some of its files went. */
    suspend fun read(id: String): ImportResult.AlreadyHere? = withContext(Dispatchers.IO) {
        val folder = folderOf(id) ?: return@withContext null
        val record = recordIn(folder)?.takeIf { it.answer == null } ?: return@withContext null
        arrivalOf(folder, record)
    }

    /**
     * Keeps `arrival`'s answer beside it, Update or Keep both (`keepBoth`),
     * before any of it is taken in: Update only over the map's text that
     * hashes to `base`, and a map it adds with an identity chosen now.
     * Throws an [IOException] when the answer can't be kept, as on a full
     * storage: it isn't taken in then, and its question is asked again.
     */
    suspend fun accept(arrival: ImportResult.AlreadyHere, keepBoth: Boolean, base: String?, opens: Boolean): AcceptedArrival = withContext(Dispatchers.IO) {
        takeIn(arrival)
        val accepted = AcceptedArrival(arrival, keepBoth, base.takeUnless { keepBoth }, UUID.randomUUID().toString(), opens)
        val folder = folderOf(arrival)

        if (folder != null) {
            val time = lastAnswer.updateAndGet { maxOf(it + 1, System.currentTimeMillis()) }

            val kept = synchronized(this@WaitingArrivals) {
                write(folder, recordOf(arrival, folder, opens).copy(answer = if (keepBoth) KEEP_BOTH else UPDATE, base = accepted.base, copy = accepted.copy, answered = time))
            }

            // Not kept, as on a full storage, it isn't taken in: Orbit stopping then would lose the answer and its question.
            if (!kept) {
                takingIn -= folder.name
                // With no words of its own: Orbit says it couldn't keep that map.
                throw IOException()
            }
        }

        accepted
    }

    /**
     * Every arrival answered but not yet in, as Orbit stopped while it took
     * them in, in the order they were answered, but those being taken in
     * now: each is being taken in from now on ([takeIn]). One whose files
     * can't all be found any more goes.
     */
    suspend fun answered(): List<AcceptedArrival> = withContext(Dispatchers.IO) {
        val records = unpacking.listFiles().orEmpty().filter { it.isDirectory && !isTakingIn(it.name) }.mapNotNull { folder ->
            recordIn(folder)?.takeIf { it.answer != null }?.let { folder to it }
        }

        records.sortedBy { (_, record) -> record.answered }.mapNotNull { (folder, record) ->
            val arrival = arrivalOf(folder, record)

            if (arrival == null) {
                Logs.warn("WaitingArrivals", "An answered map's files went before it was in")
                folder.deleteRecursively()
                return@mapNotNull null
            }

            takeIn(arrival)
            AcceptedArrival(arrival, record.answer == KEEP_BOTH, record.base, record.copy ?: UUID.randomUUID().toString(), record.opens)
        }
    }

    /**
     * `accepted` couldn't be taken in: it waits for an answer again, which
     * every screen that starts asks for ([askedAgain]), its files where it
     * left them. Gives it back as it's kept now, or null when it can't be
     * read any more.
     */
    suspend fun askAgain(accepted: AcceptedArrival): ImportResult.AlreadyHere? = withContext(Dispatchers.IO) {
        val folder = folderOf(accepted.arrival) ?: return@withContext null

        val record = synchronized(this@WaitingArrivals) {
            val kept = recordIn(folder) ?: recordOf(accepted.arrival, folder, accepted.opens)
            write(folder, kept.copy(answer = null, base = null, copy = null, ask = true))
            kept
        }

        takingIn -= folder.name
        arrivalOf(folder, record)
    }

    /** The arrivals to ask about again, as they couldn't be taken in ([askAgain]), and whether each opens its map. */
    suspend fun askedAgain(): List<Pair<ImportResult.AlreadyHere, Boolean>> = withContext(Dispatchers.IO) {
        unpacking.listFiles().orEmpty().filter { it.isDirectory }.mapNotNull { folder ->
            val record = recordIn(folder)?.takeIf { it.ask && it.answer == null } ?: return@mapNotNull null
            arrivalOf(folder, record)?.let { it to record.opens }
        }
    }

    /**
     * `arrival`, answered, adds a map of identity `copy` after all, as the
     * map it's another version of went before it was taken in: kept before
     * the map is added, so it's added only once ([MapBundles.receive]).
     */
    fun addsAs(arrival: ImportResult.AlreadyHere, copy: String) {
        val folder = folderOf(arrival) ?: return

        synchronized(this) {
            recordIn(folder)?.takeIf { it.answer != null && it.copy != copy }?.let { write(folder, it.copy(copy = copy)) }
        }
    }

    /** Whether the arrival in folder `id` was answered Update or Keep both, and waits to be taken in: a start's cleaning of leftovers keeps it. */
    internal fun isAnswered(id: String): Boolean = folderOf(id)?.let { recordIn(it) }?.answer != null

    /** Lets go of `arrival`, answered or no longer asked about: what it was unpacked to goes, its files and what it kept of itself. */
    suspend fun letGo(arrival: ImportResult.AlreadyHere) = withContext(Dispatchers.IO) {
        val folder = arrival.bundle.unpacked?.takeIf { it.parentFile?.canonicalPath == unpacking.canonicalPath }

        if (folder != null) {
            folder.deleteRecursively()
            takingIn -= folder.name
        } else {
            arrival.bundle.pictures.values.forEach { it.delete() }
        }
    }

    /**
     * Lets go of every arrival kept but those in folders `kept`, and those
     * being taken in, answered, or to be asked about again: no screen will
     * ask about the others again.
     */
    suspend fun letGoAllBut(kept: Set<String>) = withContext(Dispatchers.IO) {
        unpacking.listFiles { folder -> folder.name !in kept && !isTakingIn(folder.name) && File(folder, WAITING_ARRIVAL).isFile }
            .orEmpty()
            .filterNot { folder -> recordIn(folder)?.let { it.answer != null || it.ask } == true }
            .forEach { it.deleteRecursively() }
    }

    /** The folder of the arrival `id`, an unpacked zip's, when one keeps it. */
    private fun folderOf(id: String): File? = File(unpacking, id).takeIf { id.isNotEmpty() && '/' !in id && !id.startsWith(".") && File(it, WAITING_ARRIVAL).isFile }

    /** The folder `arrival` was unpacked to, when it's one of the unpacked zips'. */
    private fun folderOf(arrival: ImportResult.AlreadyHere): File? = arrival.bundle.unpacked?.takeIf { it.parentFile?.canonicalPath == unpacking.canonicalPath }

    private fun recordIn(folder: File): WaitingRecord? = try {
        JSON.decodeFromString(WaitingRecord.serializer(), File(folder, WAITING_ARRIVAL).readText())
    } catch (unreadable: IOException) {
        null
    } catch (unreadable: SerializationException) {
        null
    } catch (unreadable: IllegalArgumentException) {
        null
    }

    private fun write(folder: File, record: WaitingRecord): Boolean = try {
        folder.mkdirs()
        writeAtomically(File(folder, WAITING_ARRIVAL), JSON.encodeToString(WaitingRecord.serializer(), record))
        true
    } catch (failed: IOException) {
        Logs.warn("WaitingArrivals", "Couldn't keep a map waiting for an answer", failed)
        false
    }

    /** `arrival` as it's kept in `folder`: its files by name, those found where [whereIs] looks. */
    private fun recordOf(arrival: ImportResult.AlreadyHere, folder: File, opens: Boolean): WaitingRecord {
        val bundle = arrival.bundle
        val media = bundle.media

        return WaitingRecord(
            markdown = bundle.markdown,
            title = bundle.title,
            pictures = bundle.pictures.filter { (name, file) -> whereIs(folder, arrival.existing, name)?.canonicalPath == file.canonicalPath }.keys.toList(),
            uuid = bundle.uuid,
            focus = bundle.focus,
            view = bundle.view,
            path = bundle.path,
            media = listOf(media.pictures, media.videos, media.audio, media.motions, media.files),
            bytes = media.bytes,
            leftOut = bundle.leftOut,
            existing = arrival.existing,
            name = arrival.name,
            opens = opens,
        )
    }

    /** The arrival `record` keeps in `folder`, its files where they are now; null when one of them is nowhere. */
    private fun arrivalOf(folder: File, record: WaitingRecord): ImportResult.AlreadyHere? {
        val pictures = record.pictures.associateWith { whereIs(folder, record.existing, it) }

        if (pictures.values.any { it == null }) {
            return null
        }

        val counts = record.media + List(maxOf(0, 5 - record.media.size)) { 0 }
        val media = MediaSummary(pictures = counts[0], videos = counts[1], audio = counts[2], bytes = record.bytes, motions = counts[3], files = counts[4])
        val bundle = MapBundle(record.markdown, record.title, pictures.mapValues { it.value!! }, record.uuid, record.focus, record.view, record.path, media, record.leftOut, folder)
        return ImportResult.AlreadyHere(bundle, record.existing, record.name)
    }

    /**
     * Where the arrival in `folder`'s file `name` is now: in its folder, as
     * it was unpacked; in the copy being made of it there; or in map
     * `existing`'s, moved by an Update that stopped before it was done.
     */
    private fun whereIs(folder: File, existing: String, name: String): File? {
        if (name.isEmpty() || '/' in name || name == "." || name == "..") {
            return null
        }

        val places = listOf(folder, File(File(folder, ARRIVAL_COPY), MEDIA_DIRECTORY), File(File(maps, existing), MEDIA_DIRECTORY).takeIf { '/' !in existing && existing.isNotEmpty() })
        return places.firstNotNullOfOrNull { place -> place?.let { File(it, name) }?.takeIf { it.isFile } }
    }

    private companion object {
        val JSON = Json { ignoreUnknownKeys = true }

        const val UPDATE = "update"
        const val KEEP_BOTH = "keepBoth"
    }
}

/** The map an arrival was answered Update for changed since, so it isn't written over: the question is asked again. */
class ChangedSinceAnsweredException(message: String) : IOException(message)

/** A map's text as an answer to another version of it keeps it ([AcceptedArrival.base]): its SHA-256, in hex. */
internal fun textHash(text: String): String = MessageDigest.getInstance("SHA-256").digest(text.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }

/** An arrival as it's kept: its bundle's text and what the bundle says of itself, its files by name, and the map here it's another version of. */
@Serializable
private data class WaitingRecord(
    val markdown: String,
    val title: String,
    val pictures: List<String> = emptyList(),
    val uuid: String? = null,
    val focus: String? = null,
    val view: String? = null,
    val path: List<String>? = null,

    /** How many pictures, videos, sounds, live photos and ideas' files come with it, in that order (see [MediaSummary]). */
    val media: List<Int> = emptyList(),

    val bytes: Long = 0,
    val leftOut: Int = 0,
    val existing: String,
    val name: String? = null,

    /** How it was answered, once it was: Update or Keep both; none while it waits for an answer. Earlier versions read past it, and take it for a waiting one. */
    val answer: String? = null,

    /** For Update, the hash of the map's text as it was answered ([textHash]), which only that text is written over. */
    val base: String? = null,

    /** The identity of a map its answer adds, chosen as it was answered, so it's only ever added once. */
    val copy: String? = null,

    /** Whether its map opens once it's in. */
    val opens: Boolean = false,

    /** Answered, but it couldn't be taken in: every screen that starts asks about it again. */
    val ask: Boolean = false,

    /** When it was answered: those Orbit stopped before they were in are taken in in that order. */
    val answered: Long = 0,
)
