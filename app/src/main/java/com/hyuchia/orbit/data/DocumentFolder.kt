package com.hyuchia.orbit.data

import android.content.ContentResolver
import android.content.Intent
import android.database.Cursor
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.DocumentsContract.Document
import android.system.Os
import android.system.OsConstants
import androidx.core.net.toUri
import com.hyuchia.orbit.R
import com.hyuchia.orbit.Words
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.io.OutputStream

/**
 * A folder the user chose with the system's folder picker, reached through
 * the app that holds it: the device's storage, an SD card, or an app that
 * offers its folders, such as a sync app or a cloud drive. None of it needs
 * Google's services. It remembers the folder's files as last listed, to
 * reach each one by name. A folder the app can't learn the name of is
 * called "Folder" in `words`.
 */
class DocumentFolder(
    private val resolver: ContentResolver,
    private val tree: Uri,
    private val words: Words,

    /** How many bytes the storage a document is on has free, or 0 when it can't say ([roomOf]); a test says its own. */
    private val room: (Uri) -> Long = { uri -> roomOf(resolver, uri) },
) : SyncFolder {
    private val root = DocumentsContract.getTreeDocumentId(tree)
    private val documents = mutableMapOf<String, String>()

    /** The markdown files as last listed, or as this folder last wrote them, by name. */
    private val listed = mutableMapOf<String, FolderFile>()

    private var media: String? = null
    private var mediaDocuments: MutableMap<String, Child>? = null

    /** `.orbit/`, where devices keep their records, as last listed; null when it isn't there. */
    private var orbit: String? = null

    /** The records in `.orbit/devices/`, by name, as last listed. */
    private val records = mutableMapOf<String, String>()

    /** `.orbit/study/`, as last listed; null when it isn't there. */
    private var study: String? = null

    /** The devices' folders in `.orbit/study/`, by device, as last listed. */
    private val studyFolders = mutableMapOf<String, String>()

    /** The study records in them, by device and name, as last listed. */
    private val studyRecords = mutableMapOf<Pair<String, String>, String>()

    override val name: String
        get() = describe(document(root))?.name ?: words.string(R.string.data_folder)

    override fun files(): List<FolderFile> {
        val children = children(root)
        documents.clear()
        listed.clear()
        media = children.firstOrNull { it.directory && it.name == MEDIA_DIRECTORY }?.id
        mediaDocuments = null
        orbit = children.firstOrNull { it.directory && it.name == ORBIT_DIRECTORY }?.id

        return children
            .filter { !it.directory && it.name.endsWith(".md", ignoreCase = true) && !it.name.startsWith(".") }
            .map { child ->
                documents[child.name] = child.id
                listed[child.name] = child.file
                child.file
            }
    }

    /**
     * A file's text, in whatever encoding it was written (see [decodeText]).
     * One that stops partway through a character, or isn't the size it was
     * listed at (an empty one too, as a file being written starts), is still
     * being written, by a sync app say: [StillArriving]. One that holds more
     * than a map may is [TooLargeForAMap], whatever size it was listed at.
     */
    override fun read(name: String): String {
        val bytes = readDocument(uri(name), name)
        val size = listed[name]?.size ?: -1

        if (endsMidCharacter(bytes) || (size >= 0 && size != bytes.size.toLong())) {
            throw StillArriving(name, decodeText(bytes))
        }

        return decodeText(bytes)
    }

    override fun describe(name: String): FolderFile? = documents[name]?.let { describe(document(it)) }?.file

    override fun write(name: String, text: String, create: Boolean): FolderFile {
        val uri = if (create) {
            DocumentsContract.createDocument(resolver, document(root), MARKDOWN_TYPE, name) ?: throw IOException("Couldn't make $name")
        } else {
            uri(name)
        }

        // A file made for this write that didn't take it goes, rather than stay empty for a sync to take as a new map.
        removedOnFailure(uri, made = create) {
            writeBytes(uri, text.toByteArray())
        }

        val written = describe(uri) ?: throw FileNotFoundException(name)
        documents[written.name] = written.id
        listed[written.name] = written.file
        return written.file
    }

    override fun delete(name: String) {
        // A file its app didn't delete is still there: the map's deletion waits for the next sync.
        if (!DocumentsContract.deleteDocument(resolver, uri(name))) {
            throw IOException("Couldn't delete $name")
        }

        documents -= name
        listed -= name
    }

    override fun media(): Map<String, FolderFile> = mediaDocuments().mapValues { (_, child) -> child.file }

    override fun readMedia(name: String, into: OutputStream): Boolean {
        val child = mediaDocuments()[name] ?: return false
        val input = resolver.openInputStream(document(child.id)) ?: return false
        val copied = input.use { it.copyTo(into) }

        // One that isn't the size it was listed at (an empty one too) is still being written, by a sync app say: what's there
        // is only its start.
        if (child.file.size >= 0 && copied != child.file.size) {
            throw IOException("$name is still arriving")
        }

        return true
    }

    override fun writeMedia(name: String, from: File): FolderFile {
        val existing = mediaDocuments()[name]

        val uri = if (existing != null) {
            document(existing.id)
        } else {
            val folder = media ?: makeMediaFolder()
            DocumentsContract.createDocument(resolver, document(folder), storedType(name), name) ?: throw IOException("Couldn't make $name")
        }

        // A copy the folder didn't take whole goes, unless it was there before: the next sync tries again.
        val written = try {
            removedOnFailure(uri, made = existing == null) {
                // Where the folder is on this device, one isn't started without room for it, past the one it takes the place of,
                // and some to spare: written over, the one there is left as it was.
                val adding = from.length() - (existing?.file?.size ?: 0).coerceAtLeast(0)

                if (room(uri).let { it > 0 && it < adding + ROOM_LEFT_FREE }) {
                    throw NoRoomForMedia(name)
                }

                from.inputStream().use { input -> writeOver(uri, from.length()) { input.copyTo(it) } }
                val written = describe(uri) ?: throw FileNotFoundException(name)

                if (written.file.size >= 0 && written.file.size != from.length()) {
                    throw IOException("$name didn't keep what was written")
                }

                written
            }
        } catch (full: NoRoomForMedia) {
            throw full
        } catch (refused: SecurityException) {
            throw refused
        } catch (failure: Exception) {
            // What this write left, by its own document, which the folder's app may have named otherwise: nothing once it's gone.
            // Where that can't be told, the failure goes as it came.
            val left = try {
                describe(uri)
            } catch (unknown: Exception) {
                throw failure
            }

            throw MediaWriteStopped(left?.file, failure)
        }

        mediaDocuments()[written.name] = written
        return written.file
    }

    override fun deleteMedia(name: String) {
        val child = mediaDocuments()[name] ?: return

        if (!DocumentsContract.deleteDocument(resolver, document(child.id))) {
            throw IOException("Couldn't delete $name")
        }

        mediaDocuments().remove(name)
    }

    override fun deviceRecords(): List<FolderFile> {
        records.clear()
        val devices = devicesFolder() ?: return emptyList()

        return children(devices)
            .filter { !it.directory && it.name.endsWith(".json", ignoreCase = true) }
            .map { child ->
                records[child.name] = child.id
                child.file
            }
    }

    override fun readDeviceRecord(name: String): String {
        val id = records[name] ?: throw FileNotFoundException(name)
        return readDocument(document(id), name).decodeToString()
    }

    override fun writeDeviceRecord(name: String, text: String) {
        val existing = records[name]?.let(::document)

        val uri = existing ?: run {
            val devices = devicesFolder() ?: makeFolder(makeFolder(root, ORBIT_DIRECTORY).also { orbit = it }, DEVICES_DIRECTORY)
            DocumentsContract.createDocument(resolver, document(devices), RECORD_TYPE, name) ?: throw IOException("Couldn't make $name")
        }

        removedOnFailure(uri, made = existing == null) {
            writeBytes(uri, text.toByteArray())
        }

        describe(uri)?.let { records[it.name] = it.id }
    }

    override fun studyRecords(): Map<String, List<FolderFile>> {
        studyFolders.clear()
        studyRecords.clear()
        study = orbit?.let { folder -> children(folder).firstOrNull { it.directory && it.name == STUDY_DIRECTORY }?.id }
        val folder = study ?: return emptyMap()

        return children(folder).filter { it.directory }.associate { device ->
            studyFolders[device.name] = device.id

            device.name to children(device.id)
                .filter { !it.directory && it.name.endsWith(".json", ignoreCase = true) }
                .map { child ->
                    studyRecords[device.name to child.name] = child.id
                    child.file
                }
        }
    }

    override fun readStudyRecord(device: String, name: String): String {
        val id = studyRecords[device to name] ?: throw FileNotFoundException(name)
        return readDocument(document(id), name).decodeToString()
    }

    override fun writeStudyRecord(device: String, name: String, text: String) {
        val existing = studyRecords[device to name]?.let(::document)

        val uri = existing ?: run {
            val folder = studyFolders[device] ?: run {
                val orbitFolder = orbit ?: makeFolder(root, ORBIT_DIRECTORY).also { orbit = it }
                makeFolder(study ?: makeFolder(orbitFolder, STUDY_DIRECTORY).also { study = it }, device).also { studyFolders[device] = it }
            }

            DocumentsContract.createDocument(resolver, document(folder), RECORD_TYPE, name) ?: throw IOException("Couldn't make $name")
        }

        removedOnFailure(uri, made = existing == null) {
            writeBytes(uri, text.toByteArray())
        }

        describe(uri)?.let { studyRecords[device to it.name] = it.id }
    }

    override fun deleteStudyRecord(device: String, name: String) {
        val id = studyRecords[device to name] ?: return
        DocumentsContract.deleteDocument(resolver, document(id))
        studyRecords -= device to name
    }

    /** `.orbit/devices/`, if the folder has it. */
    private fun devicesFolder(): String? = orbit?.let { folder -> children(folder).firstOrNull { it.directory && it.name == DEVICES_DIRECTORY }?.id }

    /** A folder named `name` in `parent`, made unless it's there. */
    private fun makeFolder(parent: String, name: String): String {
        children(parent).firstOrNull { it.directory && it.name == name }?.let { return it.id }
        val made = DocumentsContract.createDocument(resolver, document(parent), Document.MIME_TYPE_DIR, name) ?: throw IOException("Couldn't make $name")
        return DocumentsContract.getDocumentId(made)
    }

    private fun makeMediaFolder(): String {
        val made = DocumentsContract.createDocument(resolver, document(root), Document.MIME_TYPE_DIR, MEDIA_DIRECTORY)
            ?: throw IOException("Couldn't make the folder for pictures and videos")
        return DocumentsContract.getDocumentId(made).also { media = it }
    }

    private fun mediaDocuments(): MutableMap<String, Child> =
        mediaDocuments ?: media
            ?.let { folder -> children(folder).filter { !it.directory }.associateByTo(mutableMapOf()) { it.name } }
            .let { it ?: mutableMapOf() }
            .also { mediaDocuments = it }

    private fun uri(name: String): Uri = documents[name]?.let(::document) ?: throw FileNotFoundException(name)

    private fun document(id: String): Uri = DocumentsContract.buildDocumentUriUsingTree(tree, id)

    /**
     * A document's bytes, no more than a map's text may hold: one with more
     * is [TooLargeForAMap], whatever size the folder said it was, or when it
     * couldn't say.
     */
    private fun readDocument(uri: Uri, name: String): ByteArray {
        val bytes = resolver.openInputStream(uri)?.use { it.readAtMost(MAP_TEXT_LIMIT.toInt() + 1) } ?: throw FileNotFoundException(name)

        if (bytes.size > MAP_TEXT_LIMIT) {
            throw TooLargeForAMap(name)
        }

        return bytes
    }

    private fun writeBytes(uri: Uri, bytes: ByteArray) {
        writeOver(uri, bytes.size.toLong()) { it.write(bytes) }
    }

    /**
     * Writes over a document, emptying it first: "wt" says so, though a few
     * apps only take "w", which may keep the end of a longer document after
     * what's written. That end is cut off where the `length` written ends;
     * a document that keeps it all the same didn't take the write.
     */
    private fun writeOver(uri: Uri, length: Long, write: (OutputStream) -> Unit) {
        val emptied = try {
            resolver.openOutputStream(uri, "wt") ?: throw FileNotFoundException(uri.toString())
        } catch (unsupported: IllegalArgumentException) {
            null
        }

        if (emptied != null) {
            emptied.use(write)
            return
        }

        (resolver.openOutputStream(uri, "w") ?: throw FileNotFoundException(uri.toString())).use(write)

        if ((describe(uri)?.file?.size ?: -1) > length) {
            runCatching { resolver.openFileDescriptor(uri, "rw")?.use { Os.ftruncate(it.fileDescriptor, length) } }

            if ((describe(uri)?.file?.size ?: -1) > length) {
                throw IOException("A document kept the end of what it held before")
            }
        }
    }

    /** Runs `write`, deleting the document at `uri` if it fails and the document was `made` for it. */
    private inline fun <T> removedOnFailure(uri: Uri, made: Boolean, write: () -> T): T =
        try {
            write()
        } catch (failure: Exception) {
            if (made) {
                runCatching { DocumentsContract.deleteDocument(resolver, uri) }
            }

            throw failure
        }

    private class Child(val id: String, val name: String, val directory: Boolean, modified: Long, size: Long) {
        val file = FolderFile(name, modified, size)
    }

    /** A folder's children. A cloud drive may still be fetching them: it gets a few moments to finish. */
    private fun children(parent: String): List<Child> {
        val uri = DocumentsContract.buildChildDocumentsUriUsingTree(tree, parent)

        repeat(LOADING_TRIES) {
            val cursor = resolver.query(uri, COLUMNS, null, null, null) ?: throw IOException("Couldn't list the folder")

            cursor.use {
                // A listing its app marks as failed isn't the folder's contents: none of its files would seem to be there.
                it.extras.getString(DocumentsContract.EXTRA_ERROR)?.let { error -> throw IOException("Couldn't list the folder: $error") }

                if (!it.extras.getBoolean(DocumentsContract.EXTRA_LOADING)) {
                    return rows(it)
                }
            }

            Thread.sleep(LOADING_WAIT)
        }

        throw IOException("The folder is still loading")
    }

    private fun describe(uri: Uri): Child? = resolver.query(uri, COLUMNS, null, null, null)?.use { rows(it).firstOrNull() }

    private fun rows(cursor: Cursor): List<Child> = buildList {
        while (cursor.moveToNext()) {
            val id = cursor.getString(0)
            val name = cursor.getString(1)

            if (id != null && name != null) {
                val modified = if (cursor.isNull(3)) 0 else cursor.getLong(3)
                val size = if (cursor.isNull(4)) -1 else cursor.getLong(4)
                add(Child(id, name, cursor.getString(2) == Document.MIME_TYPE_DIR, modified, size))
            }
        }
    }

    companion object {
        /** Keeps the access the folder picker gave to a folder, across restarts. */
        fun keepAccess(resolver: ContentResolver, tree: Uri) {
            resolver.takePersistableUriPermission(tree, ACCESS)
        }

        /** Gives back the access to a folder the maps aren't kept in anymore. */
        fun releaseAccess(resolver: ContentResolver, address: String) {
            runCatching { resolver.releasePersistableUriPermission(address.toUri(), ACCESS) }
        }
    }
}

private const val ACCESS = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION

private const val MARKDOWN_TYPE = "text/markdown"

private const val RECORD_TYPE = "application/json"

private const val LOADING_TRIES = 5
private const val LOADING_WAIT = 1_000L

private val COLUMNS = arrayOf(
    Document.COLUMN_DOCUMENT_ID,
    Document.COLUMN_DISPLAY_NAME,
    Document.COLUMN_MIME_TYPE,
    Document.COLUMN_LAST_MODIFIED,
    Document.COLUMN_SIZE,
)

/**
 * How many bytes the storage a document is on has free, when it's a file
 * on this device, as one a folder on the phone's storage is; 0 when it
 * can't say, as for a cloud's document, which says so as it's written.
 */
private fun roomOf(resolver: ContentResolver, uri: Uri): Long = try {
    resolver.openFileDescriptor(uri, "r")?.use { document ->
        if (OsConstants.S_ISREG(Os.fstat(document.fileDescriptor).st_mode)) {
            Os.fstatvfs(document.fileDescriptor).let { it.f_bavail * it.f_frsize }
        } else {
            0L
        }
    } ?: 0L
} catch (unknown: Exception) {
    0L
}
