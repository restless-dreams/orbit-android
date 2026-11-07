package com.hyuchia.orbit.data

import com.hyuchia.orbit.study.StudyCard
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject

/** The folder in `.orbit/` where devices keep how their study goes: a folder for each device, with a record for each map. */
internal const val STUDY_DIRECTORY = "study"

/**
 * The records this version writes. A newer one keeps every field this one
 * reads, meaning the same, and only adds its own; a change older versions
 * would misread raises `compatible` in its records too, and they leave
 * those out.
 */
internal const val STUDY_FORMAT = 1

/**
 * A device's answers to a map's cards, kept in the folder the maps are kept
 * in, as `.orbit/study/<device>/<map's file>.json`: each device writes only
 * its own records, and reads everyone's, so devices studying at the same
 * time never write the same file. `map` is the map's file in the folder,
 * and `was` the files it had before, so a map renamed on one device keeps
 * the answers the others still keep under its old name. Beside how each
 * card is going, a record says the map's ideas' ids, which its file says
 * already, and its file's names, earlier ones included, which the privacy
 * policy names along with the device's id.
 */
@Serializable
internal data class StudyRecord(
    val format: Int = STUDY_FORMAT,

    /** The oldest record format that reads this one right: a version whose `STUDY_FORMAT` is older leaves the record out. */
    val compatible: Int = 1,

    val map: String,
    val was: List<String> = emptyList(),
    val cards: Map<String, StudyCard> = emptyMap(),
)

/** What a study record's text is to this version (see [FolderStudy.read]). */
internal sealed interface StudyRead {
    /** A record this version reads. */
    data class Record(val record: StudyRecord) : StudyRead

    /** A record a newer version says this one would misread (`compatible`): it's left out, as if it weren't there. */
    data object Incompatible : StudyRead

    /** Text this version can't make out as a record at all, as one still being written leaves it: a later sync reads it again. */
    data object Garbled : StudyRead
}

internal object FolderStudy {
    /** How many of a map's earlier files its records remember. */
    const val NAMES_KEPT = 8

    /** The name of a device's record for the map in `file`. */
    fun recordName(file: String): String = "$file.json"

    /**
     * The other devices' answers to the map in `file`, by device, from their
     * records (`records`, by device): each one's record for that file, or
     * else for a file the map had before, as a record of it says (`was`) or
     * this device knows (`known`), while the folder has no map of that name
     * (`present`). `me` is this device, whose answers are its own already.
     */
    fun elsewhere(
        file: String,
        me: String,
        records: Map<String, List<StudyRecord>>,
        present: Set<String>,
        known: Collection<String> = emptyList(),
    ): Map<String, Map<String, StudyCard>> {
        val earlier = (records.values.flatten().filter { it.map == file }.flatMap { it.was } + known).filterTo(HashSet()) { it != file && it !in present }

        return records.filterKeys { it != me }.mapNotNull { (device, kept) ->
            val record = kept.firstOrNull { it.map == file } ?: kept.firstOrNull { it.map in earlier }
            record?.cards?.takeIf { it.isNotEmpty() }?.let { device to it }
        }.toMap()
    }

    /**
     * This device's record of the map in `file`: its answers (`cards`), and
     * the map's earlier files, the one it had before this sync (`renamedFrom`)
     * first, then the ones its records remembered (`remembered`).
     */
    fun record(file: String, cards: Map<String, StudyCard>, renamedFrom: String?, remembered: List<String>): StudyRecord =
        StudyRecord(map = file, was = (listOfNotNull(renamedFrom) + remembered).filter { it != file }.distinct().take(NAMES_KEPT), cards = cards)

    /** What a record's text is: a record this version reads, one a newer version says it would misread, or none it can make out. */
    fun read(text: String): StudyRead {
        val record = runCatching { studyJson.decodeFromString(StudyRecord.serializer(), text) }.getOrNull() ?: return StudyRead.Garbled
        return if (record.compatible <= STUDY_FORMAT) StudyRead.Record(record) else StudyRead.Incompatible
    }

    /**
     * A record, or null when the text isn't one, or one a newer version says
     * this one would misread (`compatible`): see [read] to tell which.
     */
    fun decode(text: String): StudyRecord? = (read(text) as? StudyRead.Record)?.record

    /** The record as JSON, keeping what a newer Orbit wrote in this device's record before (`kept`). */
    fun encode(record: StudyRecord, kept: JsonObject? = null): String {
        val fields = studyJson.encodeToJsonElement(StudyRecord.serializer(), record).jsonObject
        return studyJson.encodeToString(JsonObject.serializer(), JsonObject(kept.orEmpty().filterKeys { it !in fields } + fields))
    }

    /** The fields of a record's text as they were, to keep what a newer Orbit wrote; null when it isn't JSON. */
    fun fields(text: String): JsonObject? = runCatching { studyJson.parseToJsonElement(text).jsonObject }.getOrNull()
}

private val studyJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
}
