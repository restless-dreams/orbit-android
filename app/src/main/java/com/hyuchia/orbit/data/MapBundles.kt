package com.hyuchia.orbit.data

import com.hyuchia.orbit.BuildConfig
import com.hyuchia.orbit.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import java.io.Closeable
import java.io.File
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import kotlin.coroutines.cancellation.CancellationException

/** In a map's bundle, what it carries besides its text and pictures: its identity and where the reader was, and what made it. */
const val BUNDLE_INFO = "orbit.json"

/** What a zip of every map (Export all) says of itself, as its first entry: what made it, and which version of such a zip it is. */
const val EXPORT_MANIFEST = "orbit-export.json"

/**
 * The versions of a map's zip and of a zip of every map that this app
 * writes. Their fields are only ever added; a change older apps would
 * misread raises a zip's `compatible`, and they refuse it, saying to update
 * Orbit (see `docs/architecture/files-and-upgrades.md`).
 */
internal const val BUNDLE_FORMAT = 1
internal const val EXPORT_FORMAT = 1

/** The most files a zip of one map may hold: its text, its details and its pictures. */
private const val BUNDLE_ENTRIES = 4_000

/**
 * The most a zip of one map may unpack to, its files together: all a map's
 * pictures, videos and sounds may come to ([MAP_MEDIA_LIMIT]), with its
 * text and the rest. It's unpacked to files as it's read, never into
 * memory, and only while there's room for it ([readBundle]).
 */
internal const val BUNDLE_SIZE = MAP_MEDIA_LIMIT + 64L * 1024 * 1024

/** The most a zip of one map may unpack to in Orbit 0.4.0, and every release before it: they refuse a bigger one. */
internal const val BUNDLE_SIZE_UP_TO_0_4_0 = 96L * 1024 * 1024

/** The most a zip's entry takes besides its file: its headers, its name twice, and what a cleaned picture may gain. */
private const val ZIP_ENTRY_ROOM = 4L * 1024

/** The most files a zip of every map may hold. */
private const val ARCHIVE_ENTRIES = 200_000

/**
 * The most a zip of every map may unpack to: more than a phone's maps come
 * to, with their videos. What it writes is bounded by the room on the
 * device too, checked as it unpacks.
 */
private const val ARCHIVE_SIZE = 64L * 1024 * 1024 * 1024

/** Room on the device that unpacking a zip of maps always leaves free. */
private const val SPARE_ROOM = 256L * 1024 * 1024

/**
 * The most of a map's files a zip of every map opens as it reads the map's
 * text, under the lock ([MapBundles.exportAll]): each open file keeps what
 * it holds, whatever happens to it after. More at once could take the app
 * past the files Android lets it have open; a map's versions, at most a few
 * hundred, come first, and only a map with more pictures than this opens the
 * rest as it copies them.
 */
internal const val PINNED_FILES = 256

/**
 * Whether `name`, the first part of a path in a zip of maps, can be a map's
 * folder: any name a map's folder has, or had in an earlier version
 * ("co₂-cycle", "reforma-80-m²", "٣-أفكار"), never a hidden one. It only
 * groups a map's files, since each map comes in under a folder of its own;
 * names that point outside the zip are refused before ([checkEntryName]).
 */
private fun isMapFolder(name: String): Boolean = name.isNotEmpty() && !name.startsWith('.') && name.none(Char::isISOControl)

/**
 * A map on its way to or from another device: its text and title, its
 * pictures, and what it carries with it: its identity and where the reader was.
 */
class MapBundle(
    val markdown: String,

    /** The map's title, as its outline reads it. */
    val title: String,

    /**
     * Its pictures, videos and sounds, by name: files they were unpacked to as the zip was read,
     * never memory, since a map's can come to a gigabyte. [MapRepository.receive] moves them into
     * the map, and [MapBundles.discard] lets go of them when it's not taken in; files a bundle
     * that's never answered leaves are cleared later.
     */
    val pictures: Map<String, File>,

    val uuid: String?,
    val focus: String?,
    val view: String?,
    val path: List<String>? = null,

    /** What comes with it, by kind: its sounds are the files its text plays as sounds, a voice memo's `.mp4` among them. */
    val media: MediaSummary = MediaSummary(),

    /** Files of kinds this version doesn't know (a newer Orbit's, say), which stay out: the import says so. */
    val leftOut: Int = 0,

    /** The folder its zip was unpacked into, where [pictures] are (made only once one is), or null when it wasn't unpacked. */
    val unpacked: File? = null,
)

@Serializable
private data class BundleInfo(
    val uuid: String? = null,
    val focus: String? = null,
    val view: String? = null,
    val path: List<String>? = null,

    /** The version of a map's zip it is ([BUNDLE_FORMAT]); none before 0.4.0. */
    val bundle: Int? = null,

    /** The oldest version of a map's zip that reads it right: a newer one than this app's is refused. None means 1. */
    val compatible: Int? = null,

    /** The version of the app that made it, to say which one. */
    val app: String? = null,
)

/**
 * What a zip says of itself, read field by field: a field a newer version
 * reshaped is left out alone, so the rest, the map's identity first, still
 * reads. Nothing when it isn't JSON.
 */
private fun zipFields(text: String): JsonObject? = runCatching { mapsJson.parseToJsonElement(text).jsonObject }.getOrNull()

private fun JsonObject.text(key: String): String? = (get(key) as? JsonPrimitive)?.takeIf { it.isString }?.content

private fun JsonObject.number(key: String): Int? = (get(key) as? JsonPrimitive)?.takeIf { !it.isString }?.intOrNull

private fun bundleInfoOf(text: String): BundleInfo {
    val fields = zipFields(text) ?: return BundleInfo()
    val path = (fields["path"] as? JsonArray)?.map { (it as? JsonPrimitive)?.takeIf { word -> word.isString }?.content ?: return@map null }

    return BundleInfo(
        uuid = fields.text("uuid"),
        focus = fields.text("focus"),
        view = fields.text("view"),
        path = path?.takeIf { words -> words.all { it != null } }?.filterNotNull(),
        bundle = fields.number("bundle"),
        compatible = fields.number("compatible"),
        app = fields.text("app"),
    )
}

/**
 * What importing every map did: how many were `added`, and how many were
 * `alreadyHere`, `privateAlreadyHere` of them private here; how many of the
 * zip's folders were `skipped`, with no map in them; how many files of kinds
 * this version doesn't know stayed out (`leftOut`); how many files the
 * maps already here were missing came in (`filledIn`), `privateFilledIn` of
 * them into private ones; how many pictures, videos, sounds and files
 * stayed in the zip because they'd have taken their map past what a map
 * carries (`pastRoom`), `privatePastRoom` of them a private map's here; and
 * how many versions and files of maps private in the zip stayed in it,
 * since their map is here and open to anyone (`stayedPrivate`).
 */
data class ImportedMaps(
    val added: Int,
    val alreadyHere: Int,
    val privateAlreadyHere: Int = 0,
    val skipped: Int = 0,
    val leftOut: Int = 0,
    val filledIn: Int = 0,
    val privateFilledIn: Int = 0,
    val pastRoom: Int = 0,
    val privatePastRoom: Int = 0,
    val stayedPrivate: Int = 0,
)

/**
 * An import of a zip of every map ([MapBundles.importAll]) that `cause`
 * stopped once some of it had come in: `soFar` says exactly what, each map
 * that came whole and listed, and each file counted as it came into a map
 * here. Importing the zip again brings in the rest, leaving what came as it
 * is.
 */
class ImportStoppedException(val soFar: ImportedMaps, cause: Throwable) : IOException(cause.message, cause)

/**
 * The map's text among a zip's markdown files, as a zip of one map is read: its `map.md` at the top, else the first
 * `map.md` in a folder, else the only markdown file there is. It holds only the text it would pick so far, so a zip
 * of many big markdown files never has more than one of them in memory, and reads none it can no longer pick.
 */
internal class BundleText {
    private var top: String? = null
    private var nested: String? = null
    private var nestedName: String? = null
    private var only: String? = null
    private val names = mutableSetOf<String>()

    /** How many texts it holds now: one at most. */
    val held: Int get() = listOfNotNull(top, nested, only).size

    /** The map's text, once every markdown file was offered; null when the zip has none to pick. */
    val markdown: String? get() = top ?: nested ?: only.takeIf { names.size == 1 }

    /**
     * Takes the markdown file `name`, reading its text with `read` when it may still be the map's, or letting it go
     * by with `skip` when it can't. A name the zip has twice is read as its last.
     */
    fun offer(name: String, read: () -> String, skip: () -> Unit) {
        names += name

        when {
            name == MAP_FILE -> {
                top = read()
                nested = null
                only = null
            }
            top != null -> skip()
            name.endsWith("/$MAP_FILE") && (nested == null || name == nestedName) -> {
                nested = read()
                nestedName = name
                only = null
            }
            nested == null && names.size == 1 -> only = read()
            else -> {
                only = null
                skip()
            }
        }
    }
}

/**
 * A file of map `map`'s (its id) that's there but can't be read now, or
 * that went while the map was going into one zip of every map: a backup
 * that left it out would look whole, so there's none.
 */
class UnreadableMapFileException(val map: String?, cause: Throwable? = null) : IOException("A file of a map couldn't be read", cause)

/**
 * What a zip of every map stopped partway ends with: the start of an entry,
 * `orbit-export-unfinished`, stored as it is and a byte long, whose byte
 * never comes. Whatever reads the zip, from its start or from its end,
 * stops there with an error, instead of taking the entries before it for
 * all of it.
 */
private val UNFINISHED_ZIP: ByteArray = "orbit-export-unfinished".toByteArray().let { name ->
    ByteBuffer.allocate(30 + name.size).order(ByteOrder.LITTLE_ENDIAN)
        // The entry's signature, the version that reads it, no flags, stored, no time, its checksum, and a byte each way.
        .putInt(0x04034b50).putShort(10).putShort(0).putShort(0).putInt(0).putInt(0).putInt(1).putInt(1)
        .putShort(name.size.toShort()).putShort(0).put(name)
        .array()
}

/**
 * Where a zip of every map writes, until it's [cutOff]: from then on it
 * takes nothing, so closing the zip after a failure doesn't finish it, with
 * the list of its files, into one that reads as whole. Closing it closes
 * `output` either way.
 */
private class Unfinishable(private val output: OutputStream) : OutputStream() {
    private var cut = false

    fun cutOff() {
        cut = true
    }

    override fun write(byte: Int) {
        if (!cut) {
            output.write(byte)
        }
    }

    override fun write(bytes: ByteArray, offset: Int, length: Int) {
        if (!cut) {
            output.write(bytes, offset, length)
        }
    }

    override fun flush() {
        if (!cut) {
            output.flush()
        }
    }

    override fun close() {
        output.close()
    }
}

/**
 * Of `arriving`, a map's pictures, videos, sounds and files from a zip in
 * the order its text names them, those that would take it past what a map
 * carries (`limit`, [MAP_MEDIA_LIMIT]), with `carried` bytes of them here
 * already: each that fits is counted in, as one from the folder maps are
 * kept in is, so a smaller one after one too big still comes.
 */
internal fun pastMapRoom(arriving: List<File>, carried: Long, limit: Long = MAP_MEDIA_LIMIT): Set<File> {
    var total = carried

    return arriving.filterTo(LinkedHashSet()) { file ->
        val size = file.length()
        val fits = total + size <= limit

        if (fits) {
            total += size
        }

        !fits
    }
}

/**
 * Maps on their way to and from other devices and files: one map's bundle
 * at a time ([writeZip], [readBundle], [receive]), or every map in one zip
 * ([exportAll], [importAll]), with the lasting identities that say which of
 * them are here already.
 */
internal class MapBundles(
    store: MapStore,
    private val histories: MapHistory,

    /** Where zips of maps are unpacked while they're taken in. */
    private val unpacking: File,

    /** The most a map's pictures, videos, sounds and files come to as a zip of every map brings them in ([MAP_MEDIA_LIMIT]); less only in tests. */
    private val mapMediaLimit: Long = MAP_MEDIA_LIMIT,
) : MapPart(store) {
    /**
     * The map as a bundle for another device: its text, the pictures,
     * videos and sounds it points at, its identity and place, in a zip that
     * [readBundle] reads back. Files kept only for its earlier versions stay
     * here. It goes to other people, so its pictures, videos and sounds go as
     * [OutgoingMedia] makes them, without what their files say beyond them;
     * one Orbit can't read through stays out, unless it's one of `asTheyAre`,
     * which the person sharing chose to share as they are, as it was then
     * ([AsTheyAreFiles]). Each file is written as it's read, never whole in
     * memory, from the very file it was planned for: one that changed since
     * stops the zip ([FileChanged]), and what was written of it is thrown
     * away with the rest. `room` hears
     * the most the zip may come to before any of it is written, and throws
     * when its storage hasn't room for it ([requireRoom]).
     */
    suspend fun writeZip(id: String, output: OutputStream, asTheyAre: AsTheyAreFiles = AsTheyAreFiles.NONE, room: (Long) -> Unit = {}) = withContext(Dispatchers.IO) {
        val folder = folder(id)
        val metadata = readMetadata(folder)
        val info = BundleInfo(uuid = identity(id), focus = metadata.focus, view = metadata.view, path = metadata.path, bundle = BUNDLE_FORMAT, compatible = 1, app = BuildConfig.VERSION_NAME)
        val text = File(folder, MAP_FILE).readBytes()
        // What the map names only in code stays here: it shows as words, and the person sharing never sees it go.
        val media = sharedFiles(folder, decodeText(text))
        // Each file as big as it is (a cleaned one is no bigger, but for a picture's orientation), its entry's headers, and the rest.
        room(text.size + media.sumOf { it.length() + ZIP_ENTRY_ROOM } + 2 * ZIP_ENTRY_ROOM)

        ZipOutputStream(output).use { zip ->
            zip.putNextEntry(ZipEntry(MAP_FILE))
            zip.write(text)
            zip.closeEntry()

            media.forEach { file ->
                val outgoing = OutgoingMedia.of(file, asTheyAre) ?: return@forEach

                // Open as the file it was planned for before its entry begins.
                outgoing.open().use { source ->
                    zip.putNextEntry(ZipEntry("$MEDIA_DIRECTORY/${file.name}"))
                    source.writeTo(zip)
                    zip.closeEntry()
                }
            }

            zip.putNextEntry(ZipEntry(BUNDLE_INFO))
            zip.write(mapsJson.encodeToString(BundleInfo.serializer(), info).toByteArray())
            zip.closeEntry()
        }
    }

    /** The map's lasting identity, given one now if it's older than identities. */
    suspend fun identity(id: String): String = withContext(Dispatchers.IO) {
        mutex.withLock {
            val folder = folder(id)
            val metadata = readMetadataToChange(folder)

            metadata.uuid ?: UUID.randomUUID().toString().also { uuid ->
                if (canWrite.value) {
                    writeMetadata(folder, metadata.copy(uuid = uuid))
                    replace(summarize(folder))
                }
            }
        }
    }

    /**
     * The map with this identity, if it's here. The list of maps says first,
     * and the maps' details on disk after it: an identity written since the
     * list was read is found all the same, so a map that's here is never
     * taken for a new one, and no two maps ever share an identity. Reads the
     * disk when the list doesn't know it: call it off the main thread.
     */
    fun findByIdentity(uuid: String): String? = listedWithIdentity(uuid) ?: identitiesOnDisk()[uuid]

    private fun listedWithIdentity(uuid: String): String? = summaries.value?.firstOrNull { it.metadata.uuid == uuid }?.id

    /** Each identity the maps' details on disk say, with its map: the first by name, should two say the same. */
    private fun identitiesOnDisk(): Map<String, String> =
        directory.listFiles { file -> file.isDirectory && !file.name.startsWith(".") && File(file, MAP_FILE).isFile }.orEmpty()
            .sortedByDescending { it.name }
            .mapNotNull { folder -> runCatching { MetadataFile.decode(File(folder, METADATA_FILE).readText()).uuid }.getOrNull()?.let { it to folder.name } }
            .toMap()

    /** A map here with exactly this text that `takes` takes, if there is one. */
    private fun findByText(markdown: String, takes: (MapSummary) -> Boolean): String? {
        val key = digest(markdown)
        return summaries.value?.firstOrNull { it.contentKey == key && takes(it) && runCatching { File(folder(it.id), MAP_FILE).readTextSafely() }.getOrNull() == markdown }?.id
    }

    /**
     * A bundle read from a zip, or null when there's no map in it: its
     * `map.md`, or else its only other markdown file. Without `withPictures`
     * the pictures are only counted, to show what's coming before it's taken
     * in. A zip that unpacks to more than a map holds, or whose files point
     * outside it, is refused with an [IOException]. Its pictures, videos and
     * sounds are unpacked to files as they're read, a piece at a time, while
     * `room` says there's room for them and some to spare ([ROOM_LEFT_FREE]);
     * once there isn't, it's refused. Whatever stops it leaves none of them.
     */
    suspend fun readBundle(input: InputStream, withPictures: Boolean = true, room: () -> Long = ::roomToUnpack): MapBundle? = withContext(Dispatchers.IO) {
        val unpacked = File(unpacking, UUID.randomUUID().toString())
        var bundle: MapBundle? = null

        val checkRoom = {
            if (room() < ROOM_LEFT_FREE) {
                throw NoRoomForZipException(words.string(R.string.data_no_room_left_for_zip_map))
            }
        }

        try {
            // A storage already full takes none of it, a map's text alone included.
            checkRoom()
            bundle = unpackBundle(input, withPictures, unpacked, checkRoom)
            bundle
        } finally {
            // A zip refused, cut short or without a map leaves nothing unpacked behind.
            if (bundle == null) {
                unpacked.deleteRecursively()
            }
        }
    }

    /** The room left where zips are unpacked: the folder is made first, since one that isn't there has none. */
    private fun roomToUnpack(): Long {
        unpacking.mkdirs()
        return unpacking.usableSpace
    }

    /**
     * Lets go of a bundle that isn't taken in, as when its map was declined:
     * the files it was unpacked to go now, rather than at the next start.
     */
    fun discard(bundle: MapBundle) {
        bundle.pictures.values.forEach { it.delete() }
        bundle.pictures.values.mapNotNullTo(HashSet()) { it.parentFile }.forEach { it.delete() }
    }

    /** Reads a zip as [readBundle] does, unpacking its files into `unpacked` while `checkRoom` doesn't throw. */
    private fun unpackBundle(input: InputStream, withPictures: Boolean, unpacked: File, checkRoom: () -> Unit): MapBundle? {
        val pictures = mutableMapOf<String, File>()
        val pictureNames = mutableSetOf<String>()
        val pictureSizes = mutableMapOf<String, Long>()
        val text = BundleText()
        var info = BundleInfo()
        var leftOut = 0
        val budget = ZipBudget(BUNDLE_ENTRIES, BUNDLE_SIZE, words)

        ZipInputStream(input).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                budget.entry(entry.name)
                val name = entry.name.substringAfterLast('/')

                when {
                    entry.isDirectory || name.startsWith(".") -> zip.skipEntry(budget)
                    entry.name == BUNDLE_INFO -> info = bundleInfoOf(zip.readLimited(budget, ZIP_TEXT_LIMIT).decodeToString())
                    // Read only while it may still be the map's text; one that can't be goes by unread, refused as too big as any is.
                    entry.name.endsWith(".md") -> text.offer(entry.name, read = { decodeText(zip.readLimited(budget, ZIP_TEXT_LIMIT)) }, skip = { zip.copyLimited(null, budget, ZIP_TEXT_LIMIT) })
                    // Its ideas' files are of any kind: one with a safe name comes in, and stays once the map's text names it ([fileNamesIn]).
                    entry.name.contains("$MEDIA_DIRECTORY/") && MediaNames.NAME.matches(name) -> {
                        pictureNames += name

                        if (withPictures) {
                            val file = fileInside(unpacked, name, words)
                            unpacked.mkdirs()

                            FileOutputStream(file).use { output -> zip.copyLimited(output, budget, checkRoom = checkRoom) }
                            pictures[name] = file
                            pictureSizes[name] = file.length()
                        } else {
                            zip.skipEntry(budget)
                            pictureSizes[name] = entry.size
                        }
                    }
                    else -> {
                        // A file of a kind this version doesn't know, among the map's own: it stays out, and the import says so.
                        if (entry.name.contains("$MEDIA_DIRECTORY/")) {
                            leftOut += 1
                        }

                        zip.skipEntry(budget)
                    }
                }
            }
        }

        // A zip a newer Orbit says this one would misread is refused, before any of it is taken in.
        if ((info.compatible ?: 1) > BUNDLE_FORMAT) {
            throw RefusedZipException(words.string(R.string.data_zip_from_newer_orbit))
        }

        val markdown = text.markdown

        // A file of a kind this version doesn't know that the map's ideas don't hold stays out, and the import says so.
        val files = markdown?.let(::fileNamesIn).orEmpty()

        for (name in pictureNames.filterNot { isCarriedName(it, files) }) {
            pictures.remove(name)?.delete()
            pictureSizes.remove(name)
            leftOut += 1
        }

        return markdown?.let {
            val media = MediaSummary.of(pictureSizes, audio = soundNamesIn(it).toSet(), ideaFiles = files.toSet())
            MapBundle(it, MapText.outline(it, words).title, pictures, info.uuid, info.focus, info.view, info.path, media, leftOut, unpacked)
        }
    }

    /**
     * Why a zip of `size` bytes, a map's, can't be unpacked here, or null
     * when there's room for it: its pictures, videos and sounds come to about
     * as much unpacked as in the zip, since they're compressed already. Said
     * before any of it is read; [readBundle] still looks as it unpacks.
     */
    fun noRoomForBundle(size: Long, room: Long = roomToUnpack()): String? =
        if (room > 0 && room < size + ROOM_LEFT_FREE) {
            words.string(R.string.data_no_room_for_zip_map, readableSize(size, words), readableSize(maxOf(room - ROOM_LEFT_FREE, 0L), words))
        } else {
            null
        }

    /**
     * Whether a zip holds maps a folder each, as [exportAll] writes them
     * (several maps, or one with its details and history), rather than one
     * map's bundle: [importAll] takes those in.
     */
    suspend fun holdsManyMaps(input: InputStream): Boolean = withContext(Dispatchers.IO) {
        val folders = mutableSetOf<String>()
        var detailed = false
        val budget = ZipBudget(ARCHIVE_ENTRIES, ARCHIVE_SIZE, words)

        ZipInputStream(input).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                budget.entry(entry.name)
                val parts = entry.name.split('/')

                when {
                    entry.name == EXPORT_MANIFEST -> return@withContext true
                    entry.name == MAP_FILE -> return@withContext false
                    parts.size == 2 && isMapFolder(parts[0]) && parts[1] == MAP_FILE -> folders += parts[0]
                    parts.size == 2 && isMapFolder(parts[0]) && parts[1] == METADATA_FILE -> detailed = true
                }

                if (folders.size > 1) {
                    return@withContext true
                }

                zip.skipEntry(budget)
            }
        }

        folders.isNotEmpty() && detailed
    }

    /**
     * Takes in a map from another device. With `replacing`, it becomes that
     * map's new version, the old one kept in its history for `reason` (the
     * device or file it came from); otherwise it's
     * added, keeping its identity unless a map here already has it. A copy
     * of a private map here (both kept) is private too.
     *
     * An arrival answered Update or Keep both ([AcceptedArrival]) is taken
     * in once, however often this runs for it, as it does again after
     * Orbit stopped partway: a map with identity `copy` here means it's in
     * already; Update writes over the map only while its text hashes to
     * `base`, the text it was answered for, or is the arrival's already, and
     * throws [ChangedSinceAnsweredException] otherwise; and a map it adds is
     * made in the arrival's own folder ([ARRIVAL_COPY]), where what's moved
     * into it stays until it's whole. A map it adds keeps the arrival's own
     * identity when no map here has it, as when the map it was another
     * version of went meanwhile, which `adding` hears before it's added.
     */
    suspend fun receive(
        bundle: MapBundle,
        replacing: String? = null,
        reason: VersionReason = VersionReason.FromFile(null),
        base: String? = null,
        copy: String? = null,
        adding: (String) -> Unit = {},
    ): String = withContext(Dispatchers.IO) {
        mutex.withLock {
            checkWritable()

            // Added already, before Orbit stopped.
            copy?.let(::findByIdentity)?.let { return@withLock it }

            val existing = replacing?.let(::folder)?.takeIf { File(it, MAP_FILE).isFile }

            if (existing != null) {
                val text = File(existing, MAP_FILE).readTextSafely()

                // Answered for the text it had then: another one since isn't written over, and the question is asked again.
                if (base != null && text != bundle.markdown && textHash(text) != base) {
                    throw ChangedSinceAnsweredException(words.string(R.string.data_map_changed_since_answered))
                }

                // Details that can't be read now leave the map as it is, rather than be written over.
                val metadata = readMetadataToChange(existing)
                // The pictures first, so the text never points at files that didn't come, and only the ones the map
                // hasn't: the same name is the same file, and this device's has all its own copy says.
                val media = File(existing, MEDIA_DIRECTORY)
                val (kept, missing) = bundle.pictures.entries.partition { (name, _) -> fileInside(media, name, words).exists() }
                writePictures(existing, missing.associate { it.key to it.value })
                // An Update that stopped may have moved some already: those are the map's own now.
                kept.filterNot { (name, file) -> file.canonicalPath == fileInside(media, name, words).path }.forEach { (_, file) -> file.delete() }
                histories.keepVersionText(existing.name, text, unless = bundle.markdown, reason = reason)
                writeAtomically(File(existing, MAP_FILE), bundle.markdown)
                writeMetadata(
                    existing,
                    metadata.copy(
                        focus = bundle.focus ?: metadata.focus,
                        view = bundle.view ?: metadata.view,
                        uuid = metadata.uuid ?: bundle.uuid,
                        path = bundle.path ?: metadata.path,
                    ),
                )
                replace(summarize(existing))
                replacedMaps.tryEmit(existing.name)
                return@withLock existing.name
            }

            val id = newFolder(bundle.title)
            val twin = bundle.uuid?.let(::findByIdentity)
            val uuid = bundle.uuid?.takeIf { twin == null } ?: copy ?: UUID.randomUUID().toString()

            // Kept before the map is added, so an arrival taken in again after Orbit stopped finds it added ([copy]).
            if (copy != null && uuid != copy) {
                adding(uuid)
            }

            val here = twin?.let(::summary)?.metadata
            val private = here?.isPrivate == true
            // Kept beside a private map here, it's private too, with its cover: the other device's own version.
            val cover = here?.cover?.takeIf { private }

            val fill = { staging: File ->
                // Private, when it is, from its first file on.
                writeMetadata(staging, MapMetadata(created = clock(), focus = bundle.focus, view = bundle.view, uuid = uuid, path = bundle.path, isPrivate = private, cover = cover))
                writeAtomically(File(staging, MAP_FILE), bundle.markdown)
            }

            val arrival = bundle.unpacked?.takeIf { copy != null && it.parentFile?.canonicalPath == unpacking.canonicalPath }

            val folder = if (arrival != null) {
                settleArrival(id, arrival, bundle.pictures, fill)
            } else {
                settle(id) { staging ->
                    fill(staging)
                    writePictures(staging, bundle.pictures)
                }
            }

            insert(summarize(folder))
            id
        }
    }

    /**
     * Makes an answered arrival into map `id` in its own folder (`arrival`),
     * then gives the map its place among the others in one step, as
     * [MapStore.settle] does. Its files move into it, and any it found in
     * another map, as an Update that stopped left them there, are copied.
     * Stopped partway, or failing, what it made stays in the arrival's
     * folder, its files with it, for the next time it's taken in.
     */
    private fun settleArrival(id: String, arrival: File, pictures: Map<String, File>, fill: (File) -> Unit): File {
        val staging = File(arrival, ARRIVAL_COPY)
        val media = File(staging, MEDIA_DIRECTORY)
        staging.mkdirs()
        fill(staging)
        media.mkdirs()
        val root = arrival.canonicalPath + File.separator

        pictures.forEach { (name, source) ->
            val target = fileInside(media, name, words)

            when {
                target.exists() -> Unit
                source.canonicalPath.startsWith(root) -> moveFile(source, target)
                else -> writeAtomically(target) { output -> source.inputStream().use { it.copyTo(output) } }
            }
        }

        val folder = folder(id)

        if (!staging.renameTo(folder)) {
            throw IOException("Couldn't add the map $id")
        }

        return folder
    }

    // Every map at once: a way out of the app without Google's backups

    /**
     * Writes every map into one zip: a folder each, with its text, details,
     * pictures and history, which [importAll] reads back on any device.
     * Returns how many maps it holds. It's a backup, so every file goes
     * exactly as it is, unlike a map shared on its own ([writeZip]). Each
     * map goes as it was at one moment: its files are opened as its text is
     * read, under the lock ([PINNED_FILES] of them), so one deleted, thinned
     * out or replaced while the zip is written still goes as it was. A file
     * that's there but can't be read, or one opened later that's gone by
     * then, stops it with an [UnreadableMapFileException]: a zip without it
     * would look whole. One stopped by anything is left unfinished, ending
     * partway through an entry ([UNFINISHED_ZIP]), so nothing reads what it
     * has as a whole zip.
     */
    suspend fun exportAll(output: OutputStream): Int = withContext(Dispatchers.IO) {
        val folders = mutex.withLock { mapFolders() }
        var exported = 0
        val written = Unfinishable(output)

        ZipOutputStream(written).use { zip ->
            try {
                // What made the zip, first, so a version that can't read it right says so before reading any of it.
                val manifest = buildJsonObject {
                    put("export", EXPORT_FORMAT)
                    put("compatible", 1)
                    put("app", BuildConfig.VERSION_NAME)
                    put("layout", LAYOUT_VERSION)
                    put("format", MAP_FORMAT)
                }

                zip.putNextEntry(ZipEntry(EXPORT_MANIFEST))
                zip.write(mapsJson.encodeToString(JsonObject.serializer(), manifest).toByteArray())
                zip.closeEntry()

                for (folder in folders) {
                    // Each map's text and details as they are now, and its files opened, under the lock: a save meanwhile
                    // never leaves half of one, and a file deleted or replaced meanwhile still goes as it was.
                    val map = mutex.withLock { exportedMap(folder, pinning = PINNED_FILES) } ?: continue

                    // Pictures, videos and versions are copied outside the lock, from the files opened under it: a big
                    // video doesn't hold up saves and syncs. One past those is opened as it's copied, and one gone by
                    // then stops the zip, which would be missing it.
                    map.use {
                        for ((name, bytes) in map.texts) {
                            zip.putNextEntry(ZipEntry(name))
                            zip.write(bytes)
                            zip.closeEntry()
                        }

                        for (file in map.files) {
                            (file.opened ?: openExported(file.file, folder.name)).use { input ->
                                zip.putNextEntry(ZipEntry(file.name))
                                input.copyTo(zip)
                                zip.closeEntry()
                            }
                        }
                    }

                    exported += 1
                }
            } catch (stopped: Throwable) {
                // Closing the zip would finish it around what it has. It ends instead with the start of an entry that never
                // comes, since a zip read from its start, as every version's Import maps reads one, takes a zip that just
                // stops after an entry for a whole one.
                runCatching { output.write(UNFINISHED_ZIP) }
                written.cutOff()
                throw stopped
            }
        }

        exported
    }

    /**
     * Looks at every map's files as [exportAll] would write them, without
     * writing anything: each folder listed, each text read and each other
     * file opened. Throws an [UnreadableMapFileException] for the first map
     * with one that's there but can't be read, so a file chosen to keep the
     * zip, an earlier backup say, is never emptied for one that can't be
     * whole.
     */
    suspend fun checkExportAll() = withContext(Dispatchers.IO) {
        for (folder in mutex.withLock { mapFolders() }) {
            // Each file opened and closed as its map is listed, under the lock, so one deleted meanwhile isn't taken for one that can't be read.
            mutex.withLock {
                exportedMap(folder, givingIdentity = false)?.use { map ->
                    map.files.forEach { openExported(it.file, folder.name).close() }
                }
            }
        }
    }

    /** The maps' folders, as [exportAll] takes them. */
    private fun mapFolders(): List<File> =
        directory.listFiles { file -> file.isDirectory && !file.name.startsWith(".") && File(file, MAP_FILE).isFile }.orEmpty().toList()

    /** `file` of map `id` opened to go into a zip of every map. One that's gone, or there but can't be read, is an [UnreadableMapFileException]: the zip would be missing it. */
    private fun openExported(file: File, id: String): InputStream = try {
        file.inputStream()
    } catch (unopened: FileNotFoundException) {
        throw UnreadableMapFileException(id, unopened)
    }

    /** A file of a map's as [exportAll] writes it, by its `name` in the zip: `opened` already, under the lock, or to open as it's copied. */
    private class ExportedFile(val file: File, val name: String, val opened: InputStream?)

    /**
     * A map as [exportAll] writes it: its text and details read now, and its
     * other files to copy, some of them open already. Closing it closes
     * those.
     */
    private class ExportedMap(val texts: List<Pair<String, ByteArray>>, val files: List<ExportedFile>) : Closeable {
        override fun close() {
            files.forEach { file -> file.opened?.let { runCatching { it.close() } } }
        }
    }

    /**
     * The map in `folder` as [exportAll] writes it, given an identity first
     * if it has none and `givingIdentity` says to, with up to `pinning` of
     * its files opened now: its versions first, which are thinned out as it's
     * edited, then its pictures, videos, sounds and files. Null once it's
     * gone. Expects the lock held.
     */
    private fun exportedMap(folder: File, givingIdentity: Boolean = true, pinning: Int = 0): ExportedMap? {
        val text = File(folder, MAP_FILE)

        if (!text.isFile) {
            return null
        }

        // Each map leaves with an identity, so bringing it back finds it here instead of copying it: the list of maps knows it too.
        val metadata = try {
            readMetadataToChange(folder)
        } catch (failed: IOException) {
            throw UnreadableMapFileException(folder.name, failed)
        }

        if (givingIdentity && metadata.uuid == null && canWrite.value) {
            writeMetadata(folder, metadata.copy(uuid = UUID.randomUUID().toString()))
            replace(summarize(folder))
        }

        val read = listOf(MAP_FILE, METADATA_FILE)

        // Read by name, and the rest listed: a folder that can't be listed now stops the zip rather than leave any of it out.
        val texts = try {
            read.map { File(folder, it) }.filter { it.isFile }.map { "${folder.name}/${it.name}" to it.readBytes() }
        } catch (failed: IOException) {
            throw UnreadableMapFileException(folder.name, failed)
        }

        // Not what's Orbit's own, named with a dot: a history waiting in the map's folder (`.history`) goes as its versions do,
        // from the history, once it has joined it.
        val files = folder.walkTopDown().onEnter { it == folder || !it.name.startsWith(".") }
            .onFail { _, failed -> throw UnreadableMapFileException(folder.name, failed) }
            .filter { it.isFile && !it.name.startsWith(".") && it.relativeTo(folder).path !in read }
            .map { it to "${folder.name}/${it.relativeTo(folder).invariantSeparatorsPath}" }
            .toList()

        val versions = histories.versionFilesOrNull(folder.name) ?: throw UnreadableMapFileException(folder.name)

        val history = versions.map { (_, file) -> file to "${folder.name}/$HISTORY_DIRECTORY/${file.name}" } +
            listOfNotNull(File(File(historyDirectory, folder.name), REASONS_FILE).takeIf { it.isFile }?.let { it to "${folder.name}/$HISTORY_DIRECTORY/$REASONS_FILE" })

        // The zip takes them in the order it always has; the versions are opened first.
        val listed = files + history
        val opened = arrayOfNulls<InputStream>(listed.size)
        val first = (files.size until listed.size) + files.indices

        try {
            first.take(pinning).forEach { index -> opened[index] = openExported(listed[index].first, folder.name) }
        } catch (failed: Throwable) {
            opened.forEach { input -> input?.let { runCatching { it.close() } } }
            throw failed
        }

        return ExportedMap(texts, listed.mapIndexed { index, (file, name) -> ExportedFile(file, name, opened[index]) })
    }

    /**
     * Adds the maps in a zip [exportAll] wrote. A map that's here already
     * with the same text is left alone: the one with its identity, or else,
     * for maps given an identity apart (two phones' welcome maps, a copy from
     * the folder), one with the same text that's no other map of the zip's,
     * and isn't open to anyone when it's private. Into the one with its
     * identity, open here though it's private in the zip, only the files its
     * text points at come: its versions stay in the zip. One that changed
     * since comes in as a map of its own, so nothing here is written over,
     * private when the one here is. Each map's pictures, videos, sounds and files
     * come in while they fit in what a map carries ([MAP_MEDIA_LIMIT]); the
     * rest stay in the zip. The zip is unpacked to files first, however
     * many pictures it holds; one that would unpack to more than maps hold,
     * or past the room left on the device, or whose files point outside it,
     * is refused with an [IOException] before any map is added. A private
     * map here that's `locked` is found by its identity alone, never by its
     * text: a zip of text someone guessed comes in as it would on a phone
     * without it, telling nothing of it. Whatever stops it once a map or a
     * file has come in is an [ImportStoppedException] saying exactly what
     * did.
     */
    suspend fun importAll(input: InputStream, locked: (MapSummary) -> Boolean = { false }): ImportedMaps = withContext(Dispatchers.IO) {
        // The maps here must be known first, to find the ones the zip has already.
        summaries.filterNotNull().first()
        val unpacked = File(unpacking, UUID.randomUUID().toString())

        try {
            val (incoming, leftOut) = unpackMaps(input, unpacked)

            mutex.withLock {
                checkWritable()
                var added = 0
                var alreadyHere = 0
                var privateAlreadyHere = 0
                var skipped = 0
                var filledIn = 0
                var privateFilledIn = 0
                var pastRoom = 0
                var privatePastRoom = 0
                var stayedPrivate = 0
                // Identities on disk, read once: the maps this import adds are listed as they come.
                val onDisk = identitiesOnDisk()
                val identityOf = onDisk.entries.associate { (uuid, id) -> id to uuid }
                // Details that can't be read at all may have said it's private: it comes in so.
                val details = incoming.associateWith { map -> MetadataFile.read(File(map, METADATA_FILE), MapMetadata(created = clock())) }
                // A map here with the identity of one of the zip's is that one, never another of them.
                val identities = details.values.mapNotNullTo(HashSet()) { it.uuid }
                // And each map here is the zip's map that came to it, or came in as it: no other of the zip's.
                val taken = HashSet<String>()
                // The ids maps have, read once, with each this import gives as it goes: reading them for each map would take longer the more there are.
                val ids = takenIds()

                fun imported() = ImportedMaps(added, alreadyHere, privateAlreadyHere, skipped, leftOut, filledIn, privateFilledIn, pastRoom, privatePastRoom, stayedPrivate)

                try {
                    for (map in incoming) {
                        val markdown = File(map, MAP_FILE).takeIf { it.isFile }?.readTextSafely()

                        if (markdown == null) {
                            // Pictures or versions without their map's text: nothing comes in, and that's said.
                            skipped += 1
                            continue
                        }

                        val metadata = details.getValue(map)
                        val here = metadata.uuid?.let { uuid -> listedWithIdentity(uuid) ?: onDisk[uuid] }
                        // A changed copy of a private map here is private too, with its cover, as one from another device is ([receive]).
                        val local = here?.let { readMetadata(folder(it)) }?.takeIf { it.isPrivate }
                        val private = metadata.isPrivate || local != null
                        // Such a copy comes in with an identity of its own, made from the zip's and its text, so the same
                        // zip brought in again finds it by that, as it finds any map by its identity: locked or not.
                        val copyIdentity = metadata.uuid?.takeIf { here != null }?.let { uuid -> UUID.nameUUIDFromBytes("$uuid\n$markdown".toByteArray()).toString() }
                        val copy = copyIdentity?.let { listedWithIdentity(it) ?: onDisk[it] }
                        val holdsIt = { candidate: String -> File(folder(candidate), MAP_FILE).readTextSafely() == markdown }

                        // Another map with the same text is this one only when nothing says it isn't: its identity
                        // isn't another of the zip's maps', and a private map's files and versions never go into one
                        // that's open to anyone. A locked private map is this one by its identity alone: found by its
                        // text, it would tell whoever made the zip that a map with that text is here.
                        val same = here?.takeIf(holdsIt) ?: copy?.takeIf(holdsIt) ?: findByText(markdown) { other ->
                            other.id !in taken && (other.metadata.uuid ?: identityOf[other.id]) !in identities && (other.metadata.isPrivate || !private) && !locked(other)
                        }

                        if (same != null) {
                            val privateHere = summary(same)?.metadata?.isPrivate == true
                            taken += same
                            alreadyHere += 1

                            if (privateHere) {
                                privateAlreadyHere += 1
                            }

                            // What it's missing here comes in, as after a cloud backup that left its videos or versions out, each
                            // counted as it does. A private map's versions, and files only they point at, never go into the same
                            // map open here.
                            val filled = fillIn(same, map, markdown, fromPrivate = metadata.isPrivate) {
                                filledIn += 1

                                if (privateHere) {
                                    privateFilledIn += 1
                                }
                            }

                            pastRoom += filled.pastRoom
                            stayedPrivate += filled.stayedPrivate

                            if (privateHere) {
                                privatePastRoom += filled.pastRoom
                            }

                            continue
                        }

                        val cover = metadata.cover?.takeIf { metadata.isPrivate } ?: local?.cover ?: metadata.cover
                        val media = File(map, MEDIA_DIRECTORY)
                        val tooBig = pastMapRoom(carriedIn(media, markdown), carried = 0, limit = mapMediaLimit)
                        tooBig.forEach { it.delete() }
                        pastRoom += tooBig.size
                        val id = MapText.folderName(MapText.outline(markdown, words).title, ids)
                        ids += id

                        val folder = settle(id) { staging ->
                            // Details this version can't read at all are kept aside as they're written afresh, as any map's are ([writeMetadata]).
                            File(map, METADATA_FILE).takeIf { it.isFile }?.let { moveFile(it, File(staging, METADATA_FILE)) }
                            // Its file in a folder belongs to the device it was kept on. Private, when it is, from its first file on.
                            writeMetadata(staging, metadata.copy(uuid = metadata.uuid?.takeIf { here == null } ?: copyIdentity?.takeIf { copy == null } ?: UUID.randomUUID().toString(), sync = null, isPrivate = private, cover = cover))
                            writeAtomically(File(staging, MAP_FILE), markdown)
                            moveFiles(media, File(staging, MEDIA_DIRECTORY))
                            // And those an earlier version kept aside come along as they were.
                            moveFiles(map, staging) { UNREADABLE_METADATA.matches(it.name) }
                        }

                        // Listed as soon as it's here, so the list is what's on disk whatever stops the import after.
                        insert(summarize(folder))
                        taken += id
                        added += 1
                        // Why each version was kept first, so the versions that come before anything stops the rest keep it.
                        moveFiles(File(map, HISTORY_DIRECTORY), File(historyDirectory, id)) { it.name == REASONS_FILE }
                        moveFiles(File(map, HISTORY_DIRECTORY), File(historyDirectory, id))
                    }
                } catch (stopped: Exception) {
                    // Once anything came in, what did is said exactly: importing the zip again brings the rest.
                    if (stopped is CancellationException || (added == 0 && filledIn == 0)) {
                        throw stopped
                    }

                    throw ImportStoppedException(imported(), stopped)
                }

                imported()
            }
        } finally {
            unpacked.deleteRecursively()
        }
    }

    /** The files in `media`, a map's folder of them from a zip, that `markdown` points at, in the order it names them. */
    private fun carriedIn(media: File, markdown: String): List<File> {
        val files = media.listFiles { file -> file.isFile }.orEmpty().associateBy { it.name }
        return carriedNames(markdown).mapNotNull { files[it] }
    }

    /**
     * Unpacks a zip of maps into `into`: a numbered folder for each map, in
     * the order they come, with only what a map has (its text, details and
     * those kept aside unread, pictures and versions). Returns the folders.
     */
    private fun unpackMaps(input: InputStream, into: File): Pair<List<File>, Int> {
        val folders = linkedMapOf<String, File>()
        val budget = ZipBudget(ARCHIVE_ENTRIES, ARCHIVE_SIZE, words)
        var leftOut = 0
        into.mkdirs()

        ZipInputStream(input).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                budget.entry(entry.name)
                val parts = entry.name.split('/')

                // What made the zip: one a newer Orbit says this one would misread is refused before any map comes out of it.
                if (entry.name == EXPORT_MANIFEST) {
                    val manifest = zipFields(zip.readLimited(budget, ZIP_TEXT_LIMIT).decodeToString())

                    if ((manifest?.number("compatible") ?: 1) > EXPORT_FORMAT) {
                        throw RefusedZipException(words.string(R.string.data_zip_from_newer_orbit))
                    }

                    continue
                }

                val wanted = !entry.isDirectory && parts.size in 2..3 && isMapFolder(parts[0]) && when {
                    parts.size == 2 -> parts[1] == MAP_FILE || parts[1] == METADATA_FILE || UNREADABLE_METADATA.matches(parts[1])
                    // Its ideas' files are of any kind: one with a safe name comes in, and stays once its map's text names it.
                    parts[1] == MEDIA_DIRECTORY -> MediaNames.NAME.matches(parts[2])
                    parts[1] == HISTORY_DIRECTORY -> VERSION_NAME.matches(parts[2]) || parts[2] == REASONS_FILE
                    else -> false
                }

                if (!wanted) {
                    // A map's file of a kind this version doesn't know (a newer Orbit's, say) stays out, and the import says so.
                    if (!entry.isDirectory && parts.size == 3 && isMapFolder(parts[0]) && parts[1] == MEDIA_DIRECTORY) {
                        leftOut += 1
                    }

                    zip.skipEntry(budget)
                    continue
                }

                val folder = folders.getOrPut(parts[0]) { File(into, folders.size.toString()) }
                val file = fileInside(folder, parts.drop(1).joinToString("/"), words)
                file.parentFile?.mkdirs()

                FileOutputStream(file).use { output ->
                    // Each file a piece at a time, while the device has room for it and some to spare.
                    zip.copyLimited(output, budget, if (parts[1] == MEDIA_DIRECTORY) ZIP_ENTRY_LIMIT else ZIP_TEXT_LIMIT) {
                        if (into.usableSpace < SPARE_ROOM) {
                            throw NoRoomForZipException(words.string(R.string.data_no_room_for_zip_maps))
                        }
                    }

                    output.flush()
                    output.fd.sync()
                }
            }
        }

        // A file of a kind this version doesn't know stays out, unless its map's ideas hold it, or Orbit named it as one of theirs.
        for (folder in folders.values) {
            val files = File(folder, MAP_FILE).takeIf { it.isFile }?.let { runCatching { fileNamesIn(it.readTextSafely()) }.getOrNull() }.orEmpty()

            for (file in File(folder, MEDIA_DIRECTORY).listFiles().orEmpty()) {
                if (!isCarriedName(file.name, files) && !isIdeaFileName(file.name)) {
                    file.delete()
                    leftOut += 1
                }
            }
        }

        return folders.values.toList() to leftOut
    }

    /**
     * What [fillIn] brought into a map: how many pictures, videos, sounds
     * and versions; how many stayed in the zip, past what the map carries;
     * and how many of a private map's stayed there, the map here being open.
     */
    private class Filled(val brought: Int, val pastRoom: Int, val stayedPrivate: Int = 0)

    /**
     * Brings into map `id`, whose text is `markdown`, what a copy of it from
     * a zip (`from`) has and it hasn't: its pictures, videos and sounds,
     * while the map's come to no more than a map carries ([pastMapRoom]),
     * its versions with why they were kept, and details kept aside unread.
     * Nothing the map has changes: the same name is the same file. From a
     * copy that's private (`fromPrivate`) into a map here that isn't, only
     * the files its text points at come, which the map shows anyway: its
     * versions, and files only they point at, stay in the zip. `brought`
     * hears of each as it comes, so what stops it partway is counted.
     */
    private fun fillIn(id: String, from: File, markdown: String, fromPrivate: Boolean = false, brought: () -> Unit): Filled {
        val media = File(folder(id), MEDIA_DIRECTORY)
        val history = File(historyDirectory, id)
        val incomingHistory = File(from, HISTORY_DIRECTORY)
        val incomingMedia = File(from, MEDIA_DIRECTORY)
        val named = carriedIn(incomingMedia, markdown).filterNot { File(media, it.name).exists() }
        val tooBig = pastMapRoom(named, carried = mediaSize(id, markdown), limit = mapMediaLimit)
        val missingMedia = incomingMedia.listFiles { file -> file.isFile }.orEmpty().filterNot { File(media, it.name).exists() || it in tooBig }

        val missingVersions = incomingHistory.listFiles { file -> file.isFile && VERSION_NAME.matches(file.name) }.orEmpty()
            .filterNot { File(history, it.name).exists() }

        if (fromPrivate && !readMetadata(folder(id)).isPrivate) {
            val bringing = missingMedia.filter { it in named }

            try {
                bringing.forEach { file ->
                    moveFile(file, File(media, file.name))
                    brought()
                }
            } finally {
                mediaArrived(id, bringing.mapTo(HashSet()) { it.name })
            }

            return Filled(bringing.size, tooBig.size, stayedPrivate = missingMedia.size - bringing.size + missingVersions.size)
        }

        moveFiles(from, folder(id)) { UNREADABLE_METADATA.matches(it.name) && !File(folder(id), it.name).exists() }
        val reasons = histories.reasonEntries(incomingHistory)

        try {
            missingMedia.forEach { file ->
                moveFile(file, File(media, file.name))
                brought()
            }
        } finally {
            // Those that came are the map's, even when the rest didn't.
            mediaArrived(id, missingMedia.mapTo(HashSet()) { it.name })
        }

        // Why each was kept, as it was written: what a newer version wrote of it stays, read here or not. It's kept first,
        // so the versions that come before anything stops the rest keep it.
        val entries = missingVersions.mapNotNull { file -> reasons[file.name]?.let { file.name to it } }.toMap()

        if (entries.isNotEmpty()) {
            histories.recordReasons(history, entries)
        }

        missingVersions.forEach { file ->
            moveFile(file, File(history, file.name))
            brought()
        }

        return Filled(missingMedia.size + missingVersions.size, tooBig.size)
    }

    /** Moves `file` to `to`, copying it, a little at a time, when it can't simply be renamed. */
    private fun moveFile(file: File, to: File) {
        to.parentFile?.mkdirs()

        if (!file.renameTo(to)) {
            writeAtomically(to) { output -> file.inputStream().use { it.copyTo(output) } }
        }
    }

    /** Moves the files in `from` that `which` takes into `to`, copying, a little at a time, the ones that can't simply be renamed. */
    private fun moveFiles(from: File, to: File, which: (File) -> Boolean = { true }) {
        from.listFiles { file -> file.isFile && which(file) }.orEmpty().forEach { file -> moveFile(file, File(to, file.name)) }
    }

    /** Moves a bundle's pictures and videos into a map's folder, copying one only when it can't move. */
    private fun writePictures(folder: File, pictures: Map<String, File>) {
        val media = File(folder, MEDIA_DIRECTORY)
        media.mkdirs()

        pictures.forEach { (name, source) ->
            val target = fileInside(media, name, words)

            if (!source.renameTo(target)) {
                writeAtomically(target) { output -> source.inputStream().use { it.copyTo(output) } }
                source.delete()
            }

            source.parentFile?.delete()
        }
    }
}
