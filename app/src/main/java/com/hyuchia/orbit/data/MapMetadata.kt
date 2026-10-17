package com.hyuchia.orbit.data

import kotlinx.serialization.SerializationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlinx.serialization.descriptors.elementNames
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject

/** What the app remembers about a map besides its text, in its `meta.json`. */
@Serializable
data class MapMetadata(
    val created: Long = 0,
    val opened: Long = 0,
    val pinned: Boolean = false,

    /** Where the reader was: the node in focus, and the view. */
    val focus: String? = null,

    val view: String? = null,

    /** The map's lasting identity, the same on every device it's sent to, so it updates there instead of piling up copies. */
    val uuid: String? = null,

    /** The ideas to present, in order, when the presenter chose a path; null presents them all in reading order. */
    val path: List<String>? = null,

    /** Seconds the last rehearsal spent on each idea, by id, for the pace while presenting. */
    val rehearsal: Map<String, Int>? = null,

    /** The map's file in the folder maps are kept in, and how both sides were when they last matched. */
    val sync: SyncRecord? = null,

    /**
     * What this version doesn't know or can't read, as it was written: the
     * fields a newer version added or changed stay in the file when this one
     * writes it, so going back to an older version and forward again loses
     * nothing.
     */
    @Transient val extras: Map<String, JsonElement> = emptyMap(),
)

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
)

/** Reads and writes `meta.json`, keeping what this version doesn't know (see [MapMetadata.extras]). */
internal object MetadataFile {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = false
    }

    private val known: Set<String> = MapMetadata.serializer().descriptor.elementNames.toSet()

    /**
     * The metadata in `text`. A field this version reads differently from
     * the version that wrote it (a newer one, say) is kept in [MapMetadata.extras]
     * instead of failing the whole file. Throws when the text isn't a JSON object.
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
        return metadata.copy(extras = element.filterKeys { it !in readable })
    }

    /** The metadata as `meta.json` holds it, with the fields it kept as they were. */
    fun encode(metadata: MapMetadata): String {
        val fields = json.encodeToJsonElement(MapMetadata.serializer(), metadata).jsonObject
        return json.encodeToString(JsonObject.serializer(), JsonObject(metadata.extras.filterKeys { it !in fields } + fields))
    }
}
