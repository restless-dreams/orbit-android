package com.hyuchia.orbit.data

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract
import android.provider.DocumentsContract.Document
import android.webkit.MimeTypeMap
import androidx.core.os.BundleCompat
import java.io.File
import java.io.FileNotFoundException

/**
 * A folder's app, as Android's storage access reaches one (a sync app's, a
 * cloud drive's, the device's own storage): a content provider whose
 * documents are the files of a folder on disk, listed, read, written, made
 * and deleted as [DocumentFolder] reaches any app's, through
 * [DocumentsContract]. Whether it says each file's time and size, and how it
 * names a new one, are the test's to choose, as apps differ there; its hooks
 * change the folder at the moments a race would.
 */
class FolderApp : ContentProvider() {
    override fun onCreate(): Boolean = true

    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor {
        val file = fileOf(uri)
        val columns = projection ?: COLUMNS
        val cursor = MatrixCursor(columns)

        if (uri.pathSegments.last() == CHILDREN) {
            file.listFiles().orEmpty().sortedBy { it.name }.forEach { cursor.addRow(row(it, columns)) }
            afterListing?.invoke(file)
        } else if (file.exists()) {
            cursor.addRow(row(file, columns))
        }

        return cursor
    }

    override fun call(method: String, arg: String?, extras: Bundle?): Bundle? {
        val target = extras?.let { BundleCompat.getParcelable(it, EXTRA_URI, Uri::class.java) }?.let(::fileOf) ?: return super.call(method, arg, extras)

        return when (method) {
            CREATE_DOCUMENT -> {
                val type = extras.getString(Document.COLUMN_MIME_TYPE).orEmpty()
                val made = File(target, naming(target, type, extras.getString(Document.COLUMN_DISPLAY_NAME).orEmpty()))

                if (type == Document.MIME_TYPE_DIR) {
                    made.mkdir()
                } else {
                    made.createNewFile()
                }

                Bundle().apply { putParcelable(EXTRA_URI, DocumentsContract.buildDocumentUriUsingTree(tree, idOf(made))) }
            }
            DELETE_DOCUMENT -> {
                if (refusesDeleting) {
                    throw UnsupportedOperationException("This app doesn't delete documents")
                }

                target.deleteRecursively()
                Bundle()
            }
            else -> super.call(method, arg, extras)
        }
    }

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
        val file = fileOf(uri)

        beforeOpening?.invoke(file, mode)

        if (!file.isFile) {
            throw FileNotFoundException(uri.toString())
        }

        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.parseMode(mode))
    }

    override fun getType(uri: Uri): String? = fileOf(uri).takeIf { it.isFile }?.let { typeOf(it.name) }

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0

    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0

    /** A document's row: its id, name and type, and its time and size when the app says them. */
    private fun row(file: File, columns: Array<out String>): Array<Any?> = columns.map { column ->
        when (column) {
            Document.COLUMN_DOCUMENT_ID -> idOf(file)
            Document.COLUMN_DISPLAY_NAME -> file.name
            Document.COLUMN_MIME_TYPE -> if (file.isDirectory) Document.MIME_TYPE_DIR else typeOf(file.name)
            Document.COLUMN_LAST_MODIFIED -> file.lastModified().takeIf { times }
            Document.COLUMN_SIZE -> if (file.isFile) sizeOf(file) else null
            else -> null
        }
    }.toTypedArray()

    companion object {
        const val AUTHORITY = "com.hyuchia.orbit.test.folder"

        /** The folder's own document: the root of the tree the folder picker gave. */
        private const val ROOT = "root"

        private const val CHILDREN = "children"

        // What DocumentsContract asks a folder's app for, as Android names it.
        private const val CREATE_DOCUMENT = "android:createDocument"
        private const val DELETE_DOCUMENT = "android:deleteDocument"
        private const val EXTRA_URI = "uri"

        private val COLUMNS = arrayOf(
            Document.COLUMN_DOCUMENT_ID,
            Document.COLUMN_DISPLAY_NAME,
            Document.COLUMN_MIME_TYPE,
            Document.COLUMN_LAST_MODIFIED,
            Document.COLUMN_SIZE,
        )

        /** Where the app keeps its documents: the tree is its `root` folder. */
        private lateinit var disk: File

        /** Whether its listings say when each file was last written: apps that can't leave it out, as the contract allows. */
        var times = true

        /** What its listings say of a file's size: null when it can't tell, or a size it hasn't caught up with. */
        var sizeOf: (File) -> Long? = { it.length() }

        /** How it names a document made in a folder, of a type, asked for by a name: as the device's own storage does, by default. */
        var naming: (parent: File, type: String, name: String) -> String = ::storageName

        /** Runs as a folder has been listed, with the folder: another app writing right then. */
        var afterListing: ((File) -> Unit)? = null

        /** Runs as a document is about to be opened, with its file and how (`r`, `wt`…): another app writing right then. */
        var beforeOpening: ((File, String) -> Unit)? = null

        /** Whether it refuses to delete documents, as some cloud drives' apps do. */
        var refusesDeleting = false

        /** The folder as the system's folder picker gives it. */
        val tree: Uri get() = DocumentsContract.buildTreeDocumentUri(AUTHORITY, ROOT)

        /** The folder's files, in `disk`'s `root`, as another app sees them. */
        val folder: File get() = File(disk, ROOT)

        /** The document for the file `name` in the folder, as the system's save screen gives one. */
        fun document(name: String): Uri = DocumentsContract.buildDocumentUriUsingTree(tree, idOf(File(folder, name)))

        /** Starts again with an empty folder in `disk`, and an app that says every file's time and size and names files as the device's storage does. */
        fun reset(disk: File) {
            this.disk = disk
            File(disk, ROOT).mkdirs()
            times = true
            sizeOf = { it.length() }
            naming = ::storageName
            afterListing = null
            beforeOpening = null
            refusesDeleting = false
        }

        private fun fileOf(uri: Uri): File = File(disk, DocumentsContract.getDocumentId(uri))

        private fun idOf(file: File): String = file.relativeTo(disk).path

        private fun typeOf(name: String): String = MimeTypeMap.getSingleton().getMimeTypeFromExtension(name.substringAfterLast('.', "").lowercase()) ?: ANY_TYPE

        /**
         * A new document's name as the device's own storage gives it: the
         * name asked for, but with the extension its type has unless the
         * name's extension says that type already, and numbered when it's
         * taken (`photo (1).jpg`).
         */
        fun storageName(parent: File, type: String, name: String): String {
            val dot = name.lastIndexOf('.')
            val extension = if (dot >= 0) name.substring(dot + 1) else null
            val typeOfName = extension?.let { MimeTypeMap.getSingleton().getMimeTypeFromExtension(it.lowercase()) } ?: ANY_TYPE
            val extensionOfType = if (type == ANY_TYPE || type == Document.MIME_TYPE_DIR) null else MimeTypeMap.getSingleton().getExtensionFromMimeType(type)
            val keeps = type == Document.MIME_TYPE_DIR || type.equals(typeOfName, ignoreCase = true) || extension.equals(extensionOfType, ignoreCase = true)
            val stem = if (keeps && dot >= 0) name.substring(0, dot) else name
            val ending = if (keeps) extension else extensionOfType

            return generateSequence(0) { it + 1 }
                .map { number -> (if (number == 0) stem else "$stem ($number)") + (ending?.let { ".$it" } ?: "") }
                .first { !File(parent, it).exists() }
        }

        private const val ANY_TYPE = "application/octet-stream"
    }
}
