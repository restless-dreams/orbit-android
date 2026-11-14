package com.hyuchia.orbit.data

import com.hyuchia.orbit.study.StudyCard
import com.hyuchia.orbit.study.mergedProgress
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.Transient
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.elementNames
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import java.io.File
import java.io.IOException

/** What the app remembers about a map besides its text, in its `meta.json`. */
@Serializable
data class MapMetadata(
    val created: Long = 0,
    val opened: Long = 0,
    val pinned: Boolean = false,

    /**
     * Private: hidden on Home, in widgets and shortcuts, and opened with the
     * phone's own screen lock (see [com.hyuchia.orbit.privacy.PrivateMaps]).
     * It goes where the map's details go (backups, Export all), not with the
     * map itself: sent, shared or synced through a folder, a map arrives as
     * any other. Details that can't be read may have said so, and read as
     * private ([MetadataFile.unreadable]).
     */
    @SerialName("private") val isPrivate: Boolean = false,

    /**
     * What a private map shows instead of its name while it's locked, as its
     * owner chose it (see [LockedCover]); null shows "Private map". It goes
     * where `private` goes: backups and Export all, and copies that stay
     * private.
     */
    val cover: LockedCover? = null,

    /** Where the reader was: the node in focus, and the view. */
    val focus: String? = null,

    val view: String? = null,

    /** The map's lasting identity, the same on every device it's sent to, so it updates there instead of piling up copies. */
    val uuid: String? = null,

    /** The ideas to present, in order, when the presenter chose a path; null presents them all in reading order. */
    val path: List<String>? = null,

    /** Seconds the last rehearsal spent on each idea, by id, for the pace while presenting. */
    val rehearsal: Map<String, Int>? = null,

    /**
     * This device's answers in study mode: how each card it studied is
     * going, by idea id; null before the map is first studied here. With the
     * maps kept in a folder, they go there too ([FolderStudy]).
     */
    val study: Map<String, StudyCard>? = null,

    /**
     * The answers of the other devices that keep their maps in the same
     * folder, by device and idea id, as their files there said when last
     * read ([FolderStudy]); null with no folder. A card goes as its latest
     * answer, here or there, left it ([studyProgress]).
     */
    val studyElsewhere: Map<String, Map<String, StudyCard>>? = null,

    /**
     * The ideas the map's cards have answers for, here or on another device,
     * that weren't cards when this device's page last read the map whole:
     * gone from it, or with no ideas or note under them anymore. No session
     * studies them, so Home doesn't count them as due ([studyCounted]), and
     * their answers stay, for when they're cards again. Null: none, as in
     * details from before it.
     */
    val studyNotCards: List<String>? = null,

    /** The map's file in the folder maps are kept in, and how both sides were when they last matched. */
    val sync: SyncRecord? = null,

    /**
     * In the trash: the folder sent it there as its file left, and the note
     * saying so hasn't been read yet. Until it has, the trash keeps it past
     * its [TRASH_DAYS].
     */
    val removedByFolder: Boolean = false,

    /**
     * What this version doesn't know or can't read, as it was written: the
     * fields a newer version added or changed stay in the file when this one
     * writes it, so going back to an older version and forward again loses
     * nothing.
     */
    @Transient val extras: Map<String, JsonElement> = emptyMap(),

    /**
     * The fields this version reads that hold fields it doesn't know, as
     * they were written: what a newer version added inside [sync] goes back
     * in when this one writes it again. (A study card keeps its own, in
     * [StudyCard.extras].)
     */
    @Transient val extrasWithin: Map<String, JsonElement> = emptyMap(),

    /** Read in place of details that couldn't be read, damaged or out of reach for now ([MetadataFile.unreadable]): they may have said anything. */
    @Transient val unreadable: Boolean = false,
) {
    /**
     * Whether these details can't say if the map has a file in the folder
     * maps are kept in, nor which: they couldn't be read, or their `sync`
     * couldn't. Such a map may have one there that a sync can't tell from a
     * file new to the maps, so its deletion keeps what it can ([SyncRecord.untold]).
     */
    val folderFileUnknown: Boolean get() = unreadable || SYNC_FIELD in extras

    /** How each card is going, from this device's answers and the other devices': each as its latest answer left it. */
    val studyProgress: Map<String, StudyCard> get() = mergedProgress(study, studyElsewhere)

    /** How each of the map's cards is going ([studyProgress]), without the ideas that weren't cards when last read ([studyNotCards]): what Home counts. */
    val studyCounted: Map<String, StudyCard> get() = studyNotCards?.toSet()?.let { aside -> studyProgress.filterKeys { it !in aside } } ?: studyProgress

    /** Whether these details keep something about `idea`, by its id: its place on the path, its rehearsal's time, or how its card is going, here or on another device. */
    fun remembers(idea: String): Boolean =
        idea in path.orEmpty() || rehearsal?.containsKey(idea) == true || study?.containsKey(idea) == true || studyElsewhere.orEmpty().values.any { idea in it }

    /**
     * These details with the ideas new text gave new ids, `renames` from the
     * old to the new, keeping what they keep about them: the path, the
     * rehearsal and the study. A card goes on as its latest answer on any
     * device left it, kept as this device's own, over what an idea that had
     * the new id before left: the other devices see only the new text, and
     * find the card under the new id in this device's record in the folder.
     * Their own answers stay as their records say.
     */
    fun withIdeasRenamed(renames: Map<String, String>): MapMetadata {
        val progress = studyProgress
        val cards = renames.mapNotNull { (from, to) -> progress[from]?.let { to to it } }.toMap()

        return copy(
            path = path?.map { renames[it] ?: it },
            rehearsal = rehearsal?.mapKeys { (idea, _) -> renames[idea] ?: idea },
            study = if (cards.isEmpty()) study else study.orEmpty() - renames.keys + cards,
        )
    }
}

/**
 * A private map's cover while it's locked: an emoji and a short name of its
 * owner's own, such as "📓 Journal", either or both, shown instead of the
 * map's name so locked maps tell apart. Nothing of the map itself: its owner
 * writes it. What a newer version adds to it stays in [extras], as its emoji
 * and name change here.
 */
@Serializable(with = LockedCoverSerializer::class)
data class LockedCover(
    val icon: String? = null,
    val name: String? = null,
    val extras: Map<String, JsonElement> = emptyMap(),
) {
    /** Nothing to show: a cover with neither an emoji nor a name is none. */
    val isEmpty: Boolean get() = icon.isNullOrBlank() && name.isNullOrBlank()
}

/** The longest a cover's name may be: a few words, for a card's title line. */
const val COVER_NAME_LIMIT = 32

/** Reads and writes a [LockedCover] as a JSON object, keeping in [LockedCover.extras] the fields this version doesn't know or can't read. */
internal object LockedCoverSerializer : KSerializer<LockedCover> {
    private val texts = listOf("icon", "name")

    override val descriptor: SerialDescriptor = SerialDescriptor("com.hyuchia.orbit.data.LockedCover", JsonObject.serializer().descriptor)

    override fun deserialize(decoder: Decoder): LockedCover {
        val json = decoder as? JsonDecoder ?: throw SerializationException("A cover is read from JSON")
        val fields = json.decodeJsonElement() as? JsonObject ?: throw SerializationException("A cover is a JSON object")
        val read = texts.mapNotNull { name -> (fields[name] as? JsonPrimitive)?.takeIf { it.isString }?.content?.let { name to it } }.toMap()
        return LockedCover(icon = read["icon"], name = read["name"], extras = fields.filterKeys { it !in read })
    }

    override fun serialize(encoder: Encoder, value: LockedCover) {
        val json = encoder as? JsonEncoder ?: throw SerializationException("A cover is written as JSON")
        val written = listOfNotNull(value.icon?.let { "icon" to JsonPrimitive(it) }, value.name?.let { "name" to JsonPrimitive(it) }).toMap()
        json.encodeJsonElement(JsonObject(value.extras.filterKeys { it !in written } + written))
    }
}

/**
 * A map's file in the folder maps are kept in (see [FolderSync]): the folder,
 * the file's name there, the text both sides had when they last matched, and
 * the file's time and size then, to tell at a glance it hasn't changed since.
 */
@Serializable
data class SyncRecord(
    val folder: String,
    val file: String,
    val hash: String,
    val modified: Long = 0,
    val size: Long = -1,

    /**
     * When a sync first found the file gone from the folder. A map only
     * follows its file into the trash once a later sync agrees, so a folder
     * still being downloaded, or listed in part, deletes nothing.
     */
    val missingSince: Long? = null,

    /**
     * The [digest] of a text this device began writing to the file and
     * hasn't seen there whole yet. While it's set, a file that holds only the
     * start of the map's text is this write, cut short, and is written again,
     * even if the map changed since; a shorter file otherwise is someone's
     * change.
     */
    val writing: String? = null,

    /**
     * On a deleted map's record, kept until its file goes from the folder
     * (`folder-deletions.json`): that the map was private, so its file,
     * back in the folder, comes back as a private map. A private map's
     * deletion whose file is gone, as the folder's sync sent the map to the
     * trash or deleted its file, has no text to tell it by for a while
     * ([gone], [textless]), so a file by its name is never deleted, and
     * comes back private.
     */
    val private: Boolean? = null,

    /**
     * The install of Orbit that last matched the file ([FolderDevice.id],
     * which is never backed up): a record another install left, as a phone
     * restored from a backup has, is trusted less until this one matches it.
     */
    val install: String? = null,
) {
    /**
     * A deleted map's record whose file its details couldn't tell
     * ([MapMetadata.folderFileUnknown]), with no `file`: `folder` is the
     * folder the maps were kept in as it was deleted, `hash` the map's text
     * then, and `private` whether it was a private map. A sync of that folder
     * deletes the one file that holds that text, and takes a file new to the
     * maps that may be its file changed as a private map's when it was one
     * ([planSync]). One of the folder's deletions this version can't read
     * whole is one too, with what of it can be read ([MapStore.readTombstones]):
     * with no `folder` either, it's about whichever folder the maps are kept
     * in. Earlier versions read it as a deletion whose file is gone, and
     * forget it at their first sync.
     */
    val untold: Boolean get() = file.isEmpty()

    /**
     * A private map's deletion as [gone] leaves it, with no text to tell its
     * file by: no `hash` (an empty one, which no text has), and no time or
     * size, so its file is always read, and never taken for the map's. A
     * file by its name comes back as a private map, and one that may be the
     * map renamed is told by the text of the private map in the trash that
     * had the file ([MapTrash.deletedText]). One of the folder's deletions
     * this version can't read the `hash` of keeps its time and size, and
     * isn't one: any file new to the maps may be it.
     */
    val textless: Boolean get() = !untold && hash.isEmpty() && modified == 0L && size == -1L

    /**
     * This record as a private map's deletion whose file, `file` in the
     * folder at `folder`, is gone from there since `since` ([textless]): it
     * goes, as any deletion whose file is gone does, once a sync
     * [MISSING_CONFIRMATION] later that left nothing for later agrees, or
     * [DELETION_PATIENCE] on.
     */
    fun gone(since: Long, folder: String = this.folder, file: String = this.file) =
        copy(folder = folder, file = file, hash = "", modified = 0, size = -1, missingSince = since, writing = null, private = true)

    companion object {
        /** The deletion of a map kept in the folder at `folder` whose details can't say which file is its: its text's [digest] (`hash`), and whether it was private. */
        fun untoldDeletion(folder: String, hash: String, private: Boolean) = SyncRecord(folder = folder, file = "", hash = hash, private = true.takeIf { private })
    }
}

/** [MapMetadata.sync]'s field in `meta.json`. */
private const val SYNC_FIELD = "sync"

/** Reads and writes `meta.json`, keeping what this version doesn't know (see [MapMetadata.extras] and [MapMetadata.extrasWithin]). */
internal object MetadataFile {
    /** The field that says a map is private ([MapMetadata.isPrivate]). */
    private const val PRIVATE = "private"

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = false
    }

    private val known: Set<String> = MapMetadata.serializer().descriptor.elementNames.toSet()

    /**
     * The metadata in `text`. A field this version reads differently from
     * the version that wrote it (a newer one, say) is kept in [MapMetadata.extras]
     * instead of failing the whole file. A `private` it can't read may say
     * the map is private, so the map is: locked like any private map, its
     * `private` going back as it came while it stays so ([encode]). Throws
     * when the text isn't a JSON object.
     */
    fun decode(text: String): MapMetadata {
        val element = try {
            json.parseToJsonElement(text).jsonObject
        } catch (unreadable: IllegalArgumentException) {
            throw SerializationException("meta.json isn't a JSON object", unreadable)
        }

        val readable = element.filter { (key, value) ->
            key in known && runCatching { json.decodeFromJsonElement(MapMetadata.serializer(), JsonObject(mapOf(key to value))) }.isSuccess
        }

        val metadata = json.decodeFromJsonElement(MapMetadata.serializer(), JsonObject(readable))
        val written = json.encodeToJsonElement(MapMetadata.serializer(), metadata).jsonObject

        // A field that holds more than this version writes of it: a newer version's fields inside it.
        val within = readable.filter { (key, value) ->
            written[key]?.let { field -> keepUnknownFields(field, value, descriptorOf(key)) != field } == true
        }

        val extras = element.filterKeys { it !in readable }
        return metadata.copy(isPrivate = metadata.isPrivate || PRIVATE in extras, extras = extras, extrasWithin = within)
    }

    /**
     * The metadata as `meta.json` holds it, with the fields it kept as they
     * were, and what they held inside. A `private` this version couldn't
     * read goes back as it came while the map is private, and goes once it
     * isn't.
     */
    fun encode(metadata: MapMetadata): String {
        val fields = json.encodeToJsonElement(MapMetadata.serializer(), metadata).jsonObject
        val kept = fields.mapValues { (key, field) -> metadata.extrasWithin[key]?.let { keepUnknownFields(field, it, descriptorOf(key)) } ?: field }
        val extras = if (metadata.isPrivate) metadata.extras else metadata.extras - PRIVATE
        val written = if (PRIVATE in extras) kept - PRIVATE else kept
        return json.encodeToString(JsonObject.serializer(), JsonObject(extras.filterKeys { it !in written } + written))
    }

    /**
     * The details of a map whose `meta.json` can't be read: `fresh`, but
     * private, since what can't be read may have said so. Nothing shows it
     * unlocked; its owner unlocks it as any private map, and can make it not
     * private. Nor do they say whether it has a file in the folder maps are
     * kept in ([MapMetadata.folderFileUnknown]).
     */
    fun unreadable(fresh: MapMetadata): MapMetadata = fresh.copy(isPrivate = true, unreadable = true)

    /**
     * The details in `file`, a map's `meta.json`, or `fresh` when it has
     * none. Ones that aren't JSON at all, damaged say, read as [unreadable].
     * Throws [IOException] when the file is there but can't be read now:
     * what it holds isn't known, so nothing may write over it.
     */
    fun read(file: File, fresh: MapMetadata): MapMetadata {
        if (!file.isFile) {
            return fresh
        }

        val text = file.readText()

        return try {
            decode(text)
        } catch (damaged: SerializationException) {
            unreadable(fresh)
        }
    }

    /** The details in `file` as [read] reads them, and [unreadable] when they can't be read now: for what only reads them. */
    fun readOrPrivate(file: File, fresh: MapMetadata): MapMetadata = try {
        read(file, fresh)
    } catch (failed: IOException) {
        unreadable(fresh)
    }

    @OptIn(ExperimentalSerializationApi::class)
    private fun descriptorOf(key: String) = MapMetadata.serializer().descriptor.let { it.getElementDescriptor(it.getElementIndex(key)) }
}
