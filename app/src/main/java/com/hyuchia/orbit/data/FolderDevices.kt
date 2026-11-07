package com.hyuchia.orbit.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import java.io.IOException

/**
 * Where devices keep their records in the folder maps are kept in:
 * `.orbit/devices/<id>.json`. Every release lists only the folder's
 * top-level files ending in `.md` whose names don't start with a dot, so
 * none of them ever sees it, takes it for a map or changes it.
 */
const val ORBIT_DIRECTORY = ".orbit"
const val DEVICES_DIRECTORY = "devices"

/**
 * The newest version of the map format (Orbit's `schemaVersion`) this build's Orbit reads: its
 * `SUPPORTED_SCHEMA_VERSION`, which the pages' build writes into `assets/map/orbit.json` (`OrbitBuildTest`).
 */
const val MAP_FORMAT = 1

/**
 * The device records this version writes. A newer one keeps every field
 * this one reads, meaning the same, and only adds its own; a change older
 * versions would misread raises `compatible` in its records too, and they
 * leave those out.
 */
const val DEVICE_FORMAT = 1

/**
 * What a device keeps in the folder about itself, so the others can tell
 * what it opens: its Orbit, the newest map format it reads, the kinds of
 * file it carries in maps, and how big they may be there. Fields are only
 * ever added, never renamed, and a device keeps what a newer Orbit wrote in
 * its own record.
 */
@Serializable
data class FolderDevice(
    /** A random id per install of Orbit, never backed up, so a new phone is a new device. */
    val id: String,

    /** What nearby devices see it called. */
    val name: String,

    /** `phone`, `tablet` or `computer`, for its icon; any other word reads as a phone. */
    val kind: String = PHONE,

    val appVersion: String,
    val appVersionCode: Int,

    /** The newest map format it reads: a map written for a newer one only shows there. */
    val mapFormat: Int,

    /**
     * The extensions of the files it carries in maps: one in the folder that
     * isn't among them it leaves out. One that keeps ideas' files ([ideaFiles])
     * lists those of the ones the folder holds too, so a version from before
     * them says it can't open them.
     */
    val mediaTypes: List<String>,

    /** When it last synced with the folder, in milliseconds; a day at most before its last sync, as it writes its record. */
    val synced: Long,

    val format: Int = DEVICE_FORMAT,

    /** The oldest record format that reads this one right: a version whose `DEVICE_FORMAT` is older leaves the record out. */
    val compatible: Int = 1,

    /**
     * The most one of a map's files may be on it ([MEDIA_FILE_LIMIT]): one
     * past it there stays only in the folder. Orbit 0.4.0's record says
     * nothing, and it keeps 50 MB ([keepsFile]).
     */
    val mediaFileLimit: Long? = null,

    /**
     * The most a map's pictures, videos and sounds may come to on it
     * ([MAP_MEDIA_LIMIT]): what's past it there stays only in the folder.
     * Orbit 0.4.0's record says nothing, and it keeps 90 MB ([keepsMapMedia]).
     */
    val mapMediaLimit: Long? = null,

    /**
     * Whether it keeps the files ideas hold ([fileNamesIn]), of any kind.
     * Records from before them say nothing: those devices keep only the
     * kinds in [mediaTypes].
     */
    val ideaFiles: Boolean? = null,
) {
    /** The most one of a map's files may be on it, as its record says, or as Orbit 0.4.0 keeps when it says nothing. */
    val keepsFile: Long get() = mediaFileLimit?.takeIf { it > 0 } ?: MEDIA_FILE_LIMIT_UP_TO_0_4_0

    /** The most a map's pictures, videos and sounds may come to on it, as [keepsFile] says it. */
    val keepsMapMedia: Long get() = mapMediaLimit?.takeIf { it > 0 } ?: MAP_MEDIA_LIMIT_UP_TO_0_4_0

    companion object {
        const val PHONE = "phone"
        const val TABLET = "tablet"
        const val COMPUTER = "computer"
    }
}

/**
 * What one device should hear about another in the folder: that it runs a
 * newer or an older Orbit, and what that means here. `key` names it, so one
 * put away doesn't come back until something new happens.
 */
sealed interface FolderNotice {
    val device: FolderDevice
    val key: String

    /** Whether there's anything this device's person should do about it, beyond knowing. */
    val matters: Boolean

    /**
     * The other device runs a newer Orbit. `unopened` are the extensions of
     * files in the folder it carries and this one doesn't; `newerFormat`
     * says it reads maps this one can only show.
     */
    data class Newer(override val device: FolderDevice, val unopened: List<String>, val newerFormat: Boolean) : FolderNotice {
        override val key: String get() = "newer:${device.id}:${device.appVersionCode}:${device.mapFormat}"
        override val matters: Boolean get() = unopened.isNotEmpty() || newerFormat
    }

    /**
     * The other device runs an older Orbit. `unopened` are the extensions of
     * files in the folder it leaves out; `showsOnly` says maps written here
     * for a newer format only show there.
     */
    data class Older(override val device: FolderDevice, val unopened: List<String>, val showsOnly: Boolean) : FolderNotice {
        override val key: String get() = "older:${device.id}:${device.appVersionCode}:${device.mapFormat}"
        override val matters: Boolean get() = unopened.isNotEmpty() || showsOnly
    }
}

/** The devices in a folder, from their records, and what this one should make of them. */
internal object FolderRoster {
    /** A record not written for this long is of a device that has stopped using the folder, as far as anyone can tell. */
    const val STALE = 90L * DAY

    /** A device writes its record again at most this often when nothing in it changed, so a sync app isn't kept busy. */
    const val REWRITE = DAY

    /** The other devices whose records are recent, by name. */
    fun current(records: Collection<FolderDevice>, me: String, now: Long): List<FolderDevice> =
        records.filter { it.id != me && now - it.synced <= STALE }.sortedWith(compareBy({ it.name.lowercase() }, { it.id }))

    /** Whether this device's record needs writing: it isn't there, it says something else, or it was written a day ago or more. */
    fun needsWriting(written: FolderDevice?, mine: FolderDevice): Boolean =
        written == null || written.copy(synced = mine.synced) != mine || mine.synced - written.synced >= REWRITE

    /**
     * What `me` should hear about each of `others`, given the extensions of
     * the files the folder's media holds (`present`), and of the ideas'
     * files among them (`ideaFiles`): a device that keeps ideas' files opens
     * those of every kind, whatever its record lists.
     */
    fun notices(me: FolderDevice, others: List<FolderDevice>, present: Set<String>, ideaFiles: Set<String> = emptySet()): List<FolderNotice> = others.mapNotNull { other ->
        val newer = other.appVersionCode > me.appVersionCode || other.mapFormat > me.mapFormat
        val older = other.appVersionCode < me.appVersionCode || other.mapFormat < me.mapFormat
        val opensHere = if (me.ideaFiles == true) ideaFiles else emptySet()
        val opensThere = if (other.ideaFiles == true) ideaFiles else emptySet()

        when {
            newer -> FolderNotice.Newer(other, (other.mediaTypes.toSet() - me.mediaTypes.toSet() - opensHere).intersect(present).sorted(), other.mapFormat > me.mapFormat)
            older -> FolderNotice.Older(other, (me.mediaTypes.toSet() - other.mediaTypes.toSet() - opensThere).intersect(present).sorted(), other.mapFormat < me.mapFormat)
            else -> null
        }
    }

    /**
     * The devices that could only show a map written for `format`. Before a
     * device writes a map for a newer format than another in its folder
     * reads, its notes say so (`FolderNotes.newer`).
     */
    fun leftBehind(others: List<FolderDevice>, format: Int): List<FolderDevice> = others.filter { it.mapFormat < format }

    /**
     * The maps each of `others` keeps only part of, by the device's id: one
     * whose files may be smaller, or fewer, than this device's ([FolderDevice.keepsFile],
     * [FolderDevice.keepsMapMedia]) keeps what's past its room only in the
     * folder, as Orbit 0.4.0 does with what's past 50 MB a file and 90 MB a
     * map. `maps` are the folder's maps, by title, with the sizes of the
     * files each points at; a device that keeps all of them isn't there.
     */
    fun partlyKept(others: List<FolderDevice>, maps: List<Pair<String, List<Long>>>): Map<String, List<String>> =
        others.associate { device ->
            device.id to maps.filter { (_, sizes) -> sizes.sum() > device.keepsMapMedia || sizes.any { it > device.keepsFile } }.map { it.first }
        }.filterValues { it.isNotEmpty() }

    /** Whether any of `others` may keep less of a map's files than this version does: only then are the maps' sizes looked at. */
    fun keepLess(others: List<FolderDevice>): Boolean = others.any { it.keepsFile < MEDIA_FILE_LIMIT || it.keepsMapMedia < MAP_MEDIA_LIMIT }
}

/**
 * The records in one folder, read and written as the folder syncs: this
 * device's written when [FolderRoster.needsWriting] says so, the others'
 * read only when their files changed. Another device's record is never
 * written or deleted. `known` is what was read before, for files that
 * haven't changed since.
 */
internal class FolderDeviceRecords(private val folder: SyncFolder, private val known: MutableMap<String, Pair<FolderFile, FolderDevice?>>) {
    /** Writes this device's record if it needs it, and returns the other devices' recent ones. */
    fun update(mine: FolderDevice, now: Long): List<FolderDevice> {
        val listed = folder.deviceRecords().filter { RECORD_NAME.matches(it.name) }
        val own = "${mine.id}.json"
        val records = listed.associate { file -> file.name to read(file) }

        if (FolderRoster.needsWriting(records[own], mine)) {
            write(mine, listed.any { it.name == own })
        }

        return FolderRoster.current(records.filterKeys { it != own }.values.filterNotNull(), mine.id, now)
    }

    /**
     * Writes this device's record, keeping what a newer Orbit wrote in it
     * (one that's there already, when `there`). One that can't be read now
     * stays as it is, and so does one a newer Orbit says this one would
     * misread (`compatible`), as after going back to an older version: the
     * newer one finds it as it left it.
     */
    private fun write(mine: FolderDevice, there: Boolean) {
        val own = "${mine.id}.json"

        val kept = if (there) {
            val text = try {
                folder.readDeviceRecord(own)
            } catch (unreadable: IOException) {
                return
            }

            // Not a record at all, as one cut short leaves it: written afresh.
            val fields = runCatching { recordJson.parseToJsonElement(text).jsonObject }.getOrNull()

            if (fields != null && !readable(fields)) {
                return
            }

            fields
        } else {
            null
        }

        folder.writeDeviceRecord(own, encode(mine, kept))
        known -= own
    }

    /** The other devices' recent records, read without writing this device's (`me`). */
    fun others(me: String, now: Long): List<FolderDevice> {
        val own = "$me.json"
        val listed = folder.deviceRecords().filter { RECORD_NAME.matches(it.name) && it.name != own }
        return FolderRoster.current(listed.mapNotNull(::read), me, now)
    }

    /** A record as it was last read, or read again when its file changed; null when it isn't one. */
    private fun read(file: FolderFile): FolderDevice? {
        val before = known[file.name]

        if (before != null && before.first == file && file.modified != 0L) {
            return before.second
        }

        // A record that can't be read, or isn't one, is left out until its file changes.
        val record = try {
            decode(folder.readDeviceRecord(file.name))
        } catch (unreadable: IOException) {
            null
        } catch (refused: SecurityException) {
            null
        }

        known[file.name] = file to record
        return record
    }

    companion object {
        /** A record's file name: the device's id, which is Orbit's own random one. */
        private val RECORD_NAME = Regex("[A-Za-z0-9-]{8,64}\\.json")

        /** A record, or null when the text isn't one, or one a newer version says this one would misread (`compatible`). */
        fun decode(text: String): FolderDevice? =
            runCatching { recordJson.decodeFromString(FolderDevice.serializer(), text) }.getOrNull()?.takeIf { it.compatible <= DEVICE_FORMAT }

        /**
         * Whether a record's fields are ones this version reads right: its
         * `compatible`, when it has one, is a format no newer than this one's.
         * One it can't make out counts as a newer Orbit's.
         */
        fun readable(fields: JsonObject): Boolean {
            val compatible = fields["compatible"] ?: return true
            return (compatible as? JsonPrimitive)?.intOrNull?.let { it <= DEVICE_FORMAT } == true
        }

        /** The record as JSON, keeping what a newer Orbit wrote in this device's record before (`kept`). */
        fun encode(device: FolderDevice, kept: JsonObject? = null): String {
            val fields = recordJson.encodeToJsonElement(FolderDevice.serializer(), device).jsonObject
            return recordJson.encodeToString(JsonObject.serializer(), JsonObject(kept.orEmpty().filterKeys { it !in fields } + fields))
        }
    }
}

private val recordJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
    prettyPrint = true
}

private const val DAY = 24 * 60 * 60 * 1000L
