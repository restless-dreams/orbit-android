package com.hyuchia.orbit.data

import android.content.ContentResolver
import android.content.Intent
import android.database.Cursor
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.DocumentsContract.Document
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
class DocumentFolder(private val resolver: ContentResolver, private val tree: Uri, private val words: Words) : SyncFolder {
    private val root = DocumentsContract.getTreeDocumentId(tree)
    private val documents = mutableMapOf<String, String>()
    private var media: String? = null
    private var mediaDocuments: MutableMap<String, Child>? = null

    override val name: String
        get() = describe(document(root))?.name ?: words.string(R.string.data_folder)

    override fun files(): List<FolderFile> {
        val children = children(root)
        documents.clear()
        media = children.firstOrNull { it.directory && it.name == MEDIA_DIRECTORY }?.id
        mediaDocuments = null

        return children
            .filter { !it.directory && it.name.endsWith(".md", ignoreCase = true) && !it.name.startsWith(".") }
            .map { child ->
                documents[child.name] = child.id
                child.file
            }
    }

    /** A file's text, in whatever encoding it was written (see [decodeText]). */
    override fun read(name: String): String =
        resolver.openInputStream(uri(name))?.use { decodeText(it.readBytes()) } ?: throw FileNotFoundException(name)

    override fun write(name: String, text: String, create: Boolean): FolderFile {
        val uri = if (create) {
            DocumentsContract.createDocument(resolver, document(root), MARKDOWN_TYPE, name) ?: throw IOException("Couldn't make $name")
        } else {
            uri(name)
        }

        writeBytes(uri, text.toByteArray())
        val written = describe(uri) ?: throw FileNotFoundException(name)
        documents[written.name] = written.id
        return written.file
    }

    override fun delete(name: String) {
        DocumentsContract.deleteDocument(resolver, uri(name))
        documents -= name
    }

    override fun media(): Map<String, Long> = mediaDocuments().mapValues { (_, child) -> child.file.size }

    override fun readMedia(name: String, into: OutputStream): Boolean {
        val child = mediaDocuments()[name] ?: return false
        val input = resolver.openInputStream(document(child.id)) ?: return false
        input.use { it.copyTo(into) }
        return true
    }

    override fun writeMedia(name: String, from: File) {
        val existing = mediaDocuments()[name]

        val uri = if (existing != null) {
            document(existing.id)
        } else {
            val folder = media ?: makeMediaFolder()
            DocumentsContract.createDocument(resolver, document(folder), mediaType(name), name) ?: throw IOException("Couldn't make $name")
        }

        from.inputStream().use { input -> output(uri).use { input.copyTo(it) } }
        val written = describe(uri) ?: throw FileNotFoundException(name)

        // A copy the folder didn't take whole goes, unless it was there before: the next sync tries again.
        if (written.file.size >= 0 && written.file.size != from.length()) {
            if (existing == null) {
                runCatching { DocumentsContract.deleteDocument(resolver, uri) }
            }

            throw IOException("$name didn't keep what was written")
        }

        mediaDocuments()[written.name] = written
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

    /** Writes over a document, emptying it first: "wt" says so, though a few apps only take "w". */
    private fun writeBytes(uri: Uri, bytes: ByteArray) {
        output(uri).use { it.write(bytes) }
    }

    private fun output(uri: Uri): OutputStream =
        try {
            resolver.openOutputStream(uri, "wt")
        } catch (unsupported: IllegalArgumentException) {
            resolver.openOutputStream(uri, "w")
        } ?: throw FileNotFoundException(uri.toString())

    private class Child(val id: String, val name: String, val directory: Boolean, modified: Long, size: Long) {
        val file = FolderFile(name, modified, size)
    }

    /** A folder's children. A cloud drive may still be fetching them: it gets a few moments to finish. */
    private fun children(parent: String): List<Child> {
        val uri = DocumentsContract.buildChildDocumentsUriUsingTree(tree, parent)

        repeat(LOADING_TRIES) {
            val cursor = resolver.query(uri, COLUMNS, null, null, null) ?: throw IOException("Couldn't list the folder")

            cursor.use {
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

private const val LOADING_TRIES = 5
private const val LOADING_WAIT = 1_000L

private val COLUMNS = arrayOf(
    Document.COLUMN_DOCUMENT_ID,
    Document.COLUMN_DISPLAY_NAME,
    Document.COLUMN_MIME_TYPE,
    Document.COLUMN_LAST_MODIFIED,
    Document.COLUMN_SIZE,
)
