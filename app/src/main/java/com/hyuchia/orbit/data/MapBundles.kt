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
import java.io.File
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/** In a map's bundle, what it carries besides its text and pictures: its identity and where the reader was, and what made it. */
const val BUNDLE_INFO = "orbit.json"

/** What a zip of every map (Export all) says of itself, as its first entry: what made it, and which version of such a zip it is. */
const val EXPORT_MANIFEST = "orbit-export.json"

/**
 * The versions of a map's zip and of a zip of every map that this app
 * writes. Their fields are only ever added; a change older apps would
 * misread raises a zip's `compatible`, and they refuse it, saying to update
 * Orbit (see `docs/architecture.md`).
 */
internal const val BUNDLE_FORMAT = 1
internal const val EXPORT_FORMAT = 1

/** The most files a zip of one map may hold: its text, its details and its pictures. */
private const val BUNDLE_ENTRIES = 4_000

/** The most a zip of one map may unpack to, its files together: it's read into memory. */
internal const val BUNDLE_SIZE = 96L * 1024 * 1024

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
     * Its pictures and videos, by name: files they were unpacked to as the zip was read, never
     * memory, since a map's can come to nearly a hundred megabytes. [MapRepository.receive] moves
     * them into the map, and files a bundle that's never taken in leaves are cleared later.
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
 * this version doesn't know stayed out (`leftOut`); and how many files the
 * maps already here were missing came in (`filledIn`), `privateFilledIn` of
 * them into private ones.
 */
data class ImportedMaps(
    val added: Int,
    val alreadyHere: Int,
    val privateAlreadyHere: Int = 0,
    val skipped: Int = 0,
    val leftOut: Int = 0,
    val filledIn: Int = 0,
    val privateFilledIn: Int = 0,
)

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
) : MapPart(store) {
    /**
     * The map as a bundle for another device: its text, the pictures,
     * videos and sounds it points at, its identity and place, in a zip that
     * [readBundle] reads back. Files kept only for its earlier versions stay
     * here. It goes to other people, so its pictures, videos and sounds go as
     * [OutgoingMedia] makes them, without what their files say beyond them;
     * one Orbit can't read through stays out, unless it's one of `asTheyAre`,
     * which the person sharing chose to share as they are ([Uncleaned]).
     */
    suspend fun writeZip(id: String, output: OutputStream, asTheyAre: Set<String> = emptySet()) = withContext(Dispatchers.IO) {
        val folder = folder(id)
        val metadata = readMetadata(folder)
        val info = BundleInfo(uuid = identity(id), focus = metadata.focus, view = metadata.view, path = metadata.path, bundle = BUNDLE_FORMAT, compatible = 1, app = BuildConfig.VERSION_NAME)
        val text = File(folder, MAP_FILE).readBytes()

        ZipOutputStream(output).use { zip ->
            zip.putNextEntry(ZipEntry(MAP_FILE))
            zip.write(text)
            zip.closeEntry()

            mediaFiles(folder, decodeText(text)).forEach { file ->
                val outgoing = OutgoingMedia.of(file, asTheyAre) ?: return@forEach
                zip.putNextEntry(ZipEntry("$MEDIA_DIRECTORY/${file.name}"))
                outgoing.writeTo(zip)
                zip.closeEntry()
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

    /** A map here with exactly this text, if there is one. */
    private fun findByText(markdown: String): String? {
        val key = digest(markdown)
        return summaries.value?.firstOrNull { it.contentKey == key && runCatching { File(folder(it.id), MAP_FILE).readTextSafely() }.getOrNull() == markdown }?.id
    }

    /**
     * A bundle read from a zip, or null when there's no map in it: its
     * `map.md`, or else its only other markdown file. Without `withPictures`
     * the pictures are only counted, to show what's coming before it's taken
     * in. A zip that unpacks to more than a map holds, or whose files point
     * outside it, is refused with an [IOException].
     */
    suspend fun readBundle(input: InputStream, withPictures: Boolean = true): MapBundle? = withContext(Dispatchers.IO) {
        val pictures = mutableMapOf<String, File>()
        val pictureNames = mutableSetOf<String>()
        val pictureSizes = mutableMapOf<String, Long>()
        val texts = mutableMapOf<String, String>()
        var info = BundleInfo()
        var leftOut = 0
        val budget = ZipBudget(BUNDLE_ENTRIES, BUNDLE_SIZE, words)
        val unpacked = File(unpacking, UUID.randomUUID().toString())

        ZipInputStream(input).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                budget.entry(entry.name)
                val name = entry.name.substringAfterLast('/')

                when {
                    entry.isDirectory || name.startsWith(".") -> zip.skipEntry(budget)
                    entry.name == BUNDLE_INFO -> info = bundleInfoOf(zip.readLimited(budget, ZIP_TEXT_LIMIT).decodeToString())
                    entry.name.endsWith(".md") -> texts[entry.name] = decodeText(zip.readLimited(budget, ZIP_TEXT_LIMIT))
                    entry.name.contains("$MEDIA_DIRECTORY/") && isMediaName(name) -> {
                        pictureNames += name

                        if (withPictures) {
                            val file = fileInside(unpacked, name, words)
                            unpacked.mkdirs()

                            FileOutputStream(file).use { output -> zip.copyLimited(output, budget) }
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
            unpacked.deleteRecursively()
            throw RefusedZipException(words.string(R.string.data_zip_from_newer_orbit))
        }

        val markdown = texts[MAP_FILE] ?: texts.entries.firstOrNull { it.key.endsWith("/$MAP_FILE") }?.value ?: texts.values.singleOrNull()

        markdown?.let {
            val media = MediaSummary.of(pictureSizes, audio = soundNamesIn(it).toSet())
            MapBundle(it, MapText.outline(it, words).title, pictures, info.uuid, info.focus, info.view, info.path, media, leftOut)
        }
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
     */
    suspend fun receive(bundle: MapBundle, replacing: String? = null, reason: VersionReason = VersionReason.FromFile(null)): String = withContext(Dispatchers.IO) {
        mutex.withLock {
            checkWritable()
            val existing = replacing?.let(::folder)?.takeIf { File(it, MAP_FILE).isFile }

            if (existing != null) {
                // Details that can't be read now leave the map as it is, rather than be written over.
                val metadata = readMetadataToChange(existing)
                // The pictures first, so the text never points at files that didn't come, and only the ones the map
                // hasn't: the same name is the same file, and this device's has all its own copy says.
                val (kept, missing) = bundle.pictures.entries.partition { (name, _) -> fileInside(File(existing, MEDIA_DIRECTORY), name, words).exists() }
                writePictures(existing, missing.associate { it.key to it.value })
                kept.forEach { (_, file) -> file.delete() }
                histories.keepVersionText(existing.name, File(existing, MAP_FILE).readTextSafely(), unless = bundle.markdown, reason = reason)
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
            val uuid = bundle.uuid?.takeIf { twin == null } ?: UUID.randomUUID().toString()
            val here = twin?.let(::summary)?.metadata
            val private = here?.isPrivate == true
            // Kept beside a private map here, it's private too, with its cover: the other device's own version.
            val cover = here?.cover?.takeIf { private }

            val folder = settle(id) { staging ->
                // Private, when it is, from its first file on.
                writeMetadata(staging, MapMetadata(created = clock(), focus = bundle.focus, view = bundle.view, uuid = uuid, path = bundle.path, isPrivate = private, cover = cover))
                writeAtomically(File(staging, MAP_FILE), bundle.markdown)
                writePictures(staging, bundle.pictures)
            }

            insert(summarize(folder))
            id
        }
    }

    // Every map at once: a way out of the app without Google's backups

    /**
     * Writes every map into one zip: a folder each, with its text, details,
     * pictures and history, which [importAll] reads back on any device.
     * Returns how many maps it holds. It's a backup, so every file goes
     * exactly as it is, unlike a map shared on its own ([writeZip]).
     */
    suspend fun exportAll(output: OutputStream): Int = withContext(Dispatchers.IO) {
        val folders = mutex.withLock {
            directory.listFiles { file -> file.isDirectory && !file.name.startsWith(".") && File(file, MAP_FILE).isFile }.orEmpty().toList()
        }

        var exported = 0

        ZipOutputStream(output).use { zip ->
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
                // Each map's text and details as they are now, under the lock, so a save meanwhile never leaves half of one.
                val map = mutex.withLock { exportedMap(folder) } ?: continue
                exported += 1

                for ((name, bytes) in map.texts) {
                    zip.putNextEntry(ZipEntry(name))
                    zip.write(bytes)
                    zip.closeEntry()
                }

                // Pictures, videos and versions are only ever replaced whole, never written into, so they're copied
                // outside the lock: a big video doesn't hold up saves and syncs. One deleted meanwhile is left out.
                for ((file, name) in map.files) {
                    val input = try {
                        file.inputStream()
                    } catch (gone: FileNotFoundException) {
                        continue
                    }

                    input.use {
                        zip.putNextEntry(ZipEntry(name))
                        it.copyTo(zip)
                        zip.closeEntry()
                    }
                }
            }
        }

        exported
    }

    /** A map as [exportAll] writes it: its text and details read now, and its other files to copy. Null once it's gone. */
    private class ExportedMap(val texts: List<Pair<String, ByteArray>>, val files: List<Pair<File, String>>)

    private fun exportedMap(folder: File): ExportedMap? {
        val text = File(folder, MAP_FILE)

        if (!text.isFile) {
            return null
        }

        // Each map leaves with an identity, so bringing it back finds it here instead of copying it: the list of maps knows it too.
        val metadata = readMetadataToChange(folder)

        if (metadata.uuid == null && canWrite.value) {
            writeMetadata(folder, metadata.copy(uuid = UUID.randomUUID().toString()))
            replace(summarize(folder))
        }

        val read = setOf(MAP_FILE, METADATA_FILE)

        val texts = folder.listFiles { file -> file.name in read && file.isFile }.orEmpty()
            .map { "${folder.name}/${it.name}" to it.readBytes() }

        val files = folder.walkTopDown().filter { it.isFile && !it.name.startsWith(".") && it.relativeTo(folder).path !in read }
            .map { it to "${folder.name}/${it.relativeTo(folder).invariantSeparatorsPath}" }
            .toList() + histories.versionFiles(folder.name).map { (_, file) -> file to "${folder.name}/$HISTORY_DIRECTORY/${file.name}" } +
            listOfNotNull(File(File(historyDirectory, folder.name), REASONS_FILE).takeIf { it.isFile }?.let { it to "${folder.name}/$HISTORY_DIRECTORY/$REASONS_FILE" })

        return ExportedMap(texts, files)
    }

    /**
     * Adds the maps in a zip [exportAll] wrote. A map that's here already
     * with the same text is left alone; one that changed since comes in as
     * a map of its own, so nothing here is written over. The zip is unpacked
     * to files first, however many pictures it holds; one that would unpack
     * to more than maps hold, or past the room left on the device, or whose
     * files point outside it, is refused with an [IOException] before any
     * map is added.
     */
    suspend fun importAll(input: InputStream): ImportedMaps = withContext(Dispatchers.IO) {
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
                // Identities on disk, read once: the maps this import adds are listed as they come.
                val onDisk = identitiesOnDisk()

                for (map in incoming) {
                    val markdown = File(map, MAP_FILE).takeIf { it.isFile }?.readTextSafely()

                    if (markdown == null) {
                        // Pictures or versions without their map's text: nothing comes in, and that's said.
                        skipped += 1
                        continue
                    }

                    // Details that can't be read at all may have said it's private: it comes in so.
                    val metadata = MetadataFile.read(File(map, METADATA_FILE), MapMetadata(created = clock()))
                    val here = metadata.uuid?.let { uuid -> listedWithIdentity(uuid) ?: onDisk[uuid] }
                    val same = here?.takeIf { File(folder(it), MAP_FILE).readTextSafely() == markdown } ?: findByText(markdown)

                    if (same != null) {
                        val private = summary(same)?.metadata?.isPrivate == true
                        // What it's missing here comes in, as after a cloud backup that left its videos or versions out.
                        val brought = fillIn(same, map)
                        alreadyHere += 1
                        filledIn += brought

                        if (private) {
                            privateAlreadyHere += 1
                            privateFilledIn += brought
                        }

                        continue
                    }

                    val id = newFolder(MapText.outline(markdown, words).title)

                    val folder = settle(id) { staging ->
                        // Its file in a folder belongs to the device it was kept on. Private, when it is, from its first file on.
                        writeMetadata(staging, metadata.copy(uuid = metadata.uuid?.takeIf { here == null } ?: UUID.randomUUID().toString(), sync = null))
                        writeAtomically(File(staging, MAP_FILE), markdown)
                        moveFiles(File(map, MEDIA_DIRECTORY), File(staging, MEDIA_DIRECTORY))
                    }

                    moveFiles(File(map, HISTORY_DIRECTORY), File(historyDirectory, id))
                    insert(summarize(folder))
                    added += 1
                }

                ImportedMaps(added, alreadyHere, privateAlreadyHere, skipped, leftOut, filledIn, privateFilledIn)
            }
        } finally {
            unpacked.deleteRecursively()
        }
    }

    /**
     * Unpacks a zip of maps into `into`: a numbered folder for each map, in
     * the order they come, with only what a map has (its text, details,
     * pictures and versions). Returns the folders.
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
                    parts.size == 2 -> parts[1] == MAP_FILE || parts[1] == METADATA_FILE
                    parts[1] == MEDIA_DIRECTORY -> isMediaName(parts[2])
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

                if (into.usableSpace < SPARE_ROOM) {
                    throw RefusedZipException(words.string(R.string.data_no_room_for_zip_maps))
                }

                val folder = folders.getOrPut(parts[0]) { File(into, folders.size.toString()) }
                val file = fileInside(folder, parts.drop(1).joinToString("/"), words)
                file.parentFile?.mkdirs()

                FileOutputStream(file).use { output ->
                    zip.copyLimited(output, budget, if (parts[1] == MEDIA_DIRECTORY) ZIP_ENTRY_LIMIT else ZIP_TEXT_LIMIT)
                    output.flush()
                    output.fd.sync()
                }
            }
        }

        return folders.values.toList() to leftOut
    }

    /**
     * Brings into map `id` what a copy of it from a zip (`from`) has and it
     * hasn't: its pictures, videos and sounds, and its versions with why
     * they were kept. Nothing the map has changes: the same name is the same
     * file. Returns how many files came in.
     */
    private fun fillIn(id: String, from: File): Int {
        val media = File(folder(id), MEDIA_DIRECTORY)
        val history = File(historyDirectory, id)
        val incomingHistory = File(from, HISTORY_DIRECTORY)
        val reasons = histories.reasonEntries(incomingHistory)
        val missingMedia = File(from, MEDIA_DIRECTORY).listFiles { file -> file.isFile }.orEmpty().filterNot { File(media, it.name).exists() }

        val missingVersions = incomingHistory.listFiles { file -> file.isFile && VERSION_NAME.matches(file.name) }.orEmpty()
            .filterNot { File(history, it.name).exists() }

        missingMedia.forEach { file -> moveFile(file, File(media, file.name)) }

        missingVersions.forEach { file ->
            moveFile(file, File(history, file.name))
            (reasons[file.name] as? JsonObject)?.let(VersionReason::read)?.let { histories.recordReason(history, file.name, it) }
        }

        return missingMedia.size + missingVersions.size
    }

    /** Moves `file` to `to`, copying it, a little at a time, when it can't simply be renamed. */
    private fun moveFile(file: File, to: File) {
        to.parentFile?.mkdirs()

        if (!file.renameTo(to)) {
            writeAtomically(to) { output -> file.inputStream().use { it.copyTo(output) } }
        }
    }

    /** Moves the files in `from` into `to`, copying the ones that can't simply be renamed. */
    private fun moveFiles(from: File, to: File) {
        val files = from.listFiles { file -> file.isFile }.orEmpty()

        if (files.isEmpty()) {
            return
        }

        to.mkdirs()

        for (file in files) {
            val target = File(to, file.name)

            if (!file.renameTo(target)) {
                writeAtomically(target, file.readBytes())
            }
        }
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
