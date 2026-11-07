package com.hyuchia.orbit.ui

import android.content.Context
import android.text.format.Formatter
import android.webkit.MimeTypeMap
import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Article
import androidx.compose.material.icons.automirrored.outlined.InsertDriveFile
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.FolderZip
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.outlined.Slideshow
import androidx.compose.material.icons.outlined.TableChart
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.core.net.toUri
import com.hyuchia.orbit.R
import com.hyuchia.orbit.page.MapFile
import java.util.Locale

/**
 * The kinds of file an idea's files are told apart by, each with its icon
 * and its word, as the map's page tells them (Orbit's `fileKind`): by the
 * file's type, else by its name's extension.
 */
enum class IdeaFileKind(val icon: ImageVector, @param:StringRes val word: Int) {
    Pdf(Icons.Outlined.PictureAsPdf, R.string.files_kind_pdf),
    Document(Icons.Outlined.Description, R.string.files_kind_document),
    Spreadsheet(Icons.Outlined.TableChart, R.string.files_kind_spreadsheet),
    Presentation(Icons.Outlined.Slideshow, R.string.files_kind_presentation),
    Picture(Icons.Outlined.Image, R.string.files_kind_picture),
    Video(Icons.Outlined.Movie, R.string.files_kind_video),
    Sound(Icons.Outlined.MusicNote, R.string.files_kind_sound),
    Archive(Icons.Outlined.FolderZip, R.string.files_kind_archive),
    Text(Icons.AutoMirrored.Outlined.Article, R.string.files_kind_text),
    Other(Icons.AutoMirrored.Outlined.InsertDriveFile, R.string.files_kind_other),
}

private val KindsByExtension = mapOf(
    "pdf" to IdeaFileKind.Pdf,
    "doc" to IdeaFileKind.Document, "docx" to IdeaFileKind.Document, "odt" to IdeaFileKind.Document, "rtf" to IdeaFileKind.Document, "pages" to IdeaFileKind.Document,
    "xls" to IdeaFileKind.Spreadsheet, "xlsx" to IdeaFileKind.Spreadsheet, "ods" to IdeaFileKind.Spreadsheet, "csv" to IdeaFileKind.Spreadsheet,
    "tsv" to IdeaFileKind.Spreadsheet, "numbers" to IdeaFileKind.Spreadsheet,
    "ppt" to IdeaFileKind.Presentation, "pptx" to IdeaFileKind.Presentation, "odp" to IdeaFileKind.Presentation, "key" to IdeaFileKind.Presentation,
    "png" to IdeaFileKind.Picture, "jpg" to IdeaFileKind.Picture, "jpeg" to IdeaFileKind.Picture, "gif" to IdeaFileKind.Picture, "webp" to IdeaFileKind.Picture,
    "avif" to IdeaFileKind.Picture, "heic" to IdeaFileKind.Picture, "heif" to IdeaFileKind.Picture, "bmp" to IdeaFileKind.Picture, "svg" to IdeaFileKind.Picture,
    "tif" to IdeaFileKind.Picture, "tiff" to IdeaFileKind.Picture,
    "mp4" to IdeaFileKind.Video, "m4v" to IdeaFileKind.Video, "mov" to IdeaFileKind.Video, "webm" to IdeaFileKind.Video, "mkv" to IdeaFileKind.Video, "avi" to IdeaFileKind.Video,
    "mp3" to IdeaFileKind.Sound, "m4a" to IdeaFileKind.Sound, "aac" to IdeaFileKind.Sound, "ogg" to IdeaFileKind.Sound, "oga" to IdeaFileKind.Sound,
    "opus" to IdeaFileKind.Sound, "wav" to IdeaFileKind.Sound, "flac" to IdeaFileKind.Sound,
    "zip" to IdeaFileKind.Archive, "rar" to IdeaFileKind.Archive, "7z" to IdeaFileKind.Archive, "gz" to IdeaFileKind.Archive, "tgz" to IdeaFileKind.Archive,
    "tar" to IdeaFileKind.Archive, "bz2" to IdeaFileKind.Archive, "xz" to IdeaFileKind.Archive,
    "txt" to IdeaFileKind.Text, "md" to IdeaFileKind.Text, "markdown" to IdeaFileKind.Text,
)

private val KindsByType = mapOf(
    "application/pdf" to IdeaFileKind.Pdf,
    "application/msword" to IdeaFileKind.Document,
    "application/rtf" to IdeaFileKind.Document,
    "application/vnd.oasis.opendocument.text" to IdeaFileKind.Document,
    "application/vnd.ms-excel" to IdeaFileKind.Spreadsheet,
    "application/vnd.oasis.opendocument.spreadsheet" to IdeaFileKind.Spreadsheet,
    "application/vnd.ms-powerpoint" to IdeaFileKind.Presentation,
    "application/vnd.oasis.opendocument.presentation" to IdeaFileKind.Presentation,
    "application/zip" to IdeaFileKind.Archive,
    "application/x-zip-compressed" to IdeaFileKind.Archive,
    "application/x-7z-compressed" to IdeaFileKind.Archive,
    "application/vnd.rar" to IdeaFileKind.Archive,
    "application/x-rar-compressed" to IdeaFileKind.Archive,
    "application/gzip" to IdeaFileKind.Archive,
    "application/x-tar" to IdeaFileKind.Archive,
)

/** Office's own types, which name their kind in the middle. */
private val OfficeType = Regex("""^application/vnd\.openxmlformats-officedocument\.(wordprocessingml|spreadsheetml|presentationml)\.""")

/** The last part of a file's address, as its letters read: `media/Q3%20plan.pdf` is `Q3 plan.pdf`. */
private fun lastPart(src: String): String = runCatching { src.toUri().lastPathSegment }.getOrNull() ?: src.substringAfterLast('/')

/** A file's name as people know it: its `name`, else the last part of its address. */
fun fileName(file: MapFile): String = file.name?.trim()?.takeIf { it.isNotEmpty() } ?: lastPart(file.src).ifEmpty { file.src }

/** A file's extension, in lower case, from its name, else its address: empty without one. */
fun fileExtension(file: MapFile): String = listOf(file.name.orEmpty(), lastPart(file.src)).firstNotNullOfOrNull { name ->
    val dot = name.lastIndexOf('.')
    name.substring(dot + 1).lowercase(Locale.ROOT).takeIf { dot > 0 && dot < name.length - 1 }
}.orEmpty()

/** What kind of file it is: by its type, else by its extension. */
fun fileKind(file: MapFile): IdeaFileKind {
    val type = file.type?.substringBefore(';')?.trim()?.lowercase(Locale.ROOT).orEmpty()

    OfficeType.find(type)?.let { office ->
        return when (office.groupValues[1]) {
            "wordprocessingml" -> IdeaFileKind.Document
            "spreadsheetml" -> IdeaFileKind.Spreadsheet
            else -> IdeaFileKind.Presentation
        }
    }

    KindsByType[type]?.let { return it }

    return when {
        type.startsWith("image/") -> IdeaFileKind.Picture
        type.startsWith("video/") -> IdeaFileKind.Video
        type.startsWith("audio/") -> IdeaFileKind.Sound
        type == "text/csv" -> IdeaFileKind.Spreadsheet
        type.startsWith("text/") -> IdeaFileKind.Text
        else -> KindsByExtension[fileExtension(file)] ?: IdeaFileKind.Other
    }
}

/**
 * The word for a file's kind, in the app's language: "PDF", "Spreadsheet".
 * A kind Orbit doesn't name is its extension in capitals ("EPUB"), else
 * "File".
 */
fun fileKindName(file: MapFile, context: Context): String {
    val kind = fileKind(file)
    val extension = fileExtension(file)
    return if (kind == IdeaFileKind.Other && extension.matches(Regex("[a-z0-9]{1,6}"))) extension.uppercase(Locale.ROOT) else context.getString(kind.word)
}

/** Under a file's name: its kind, and its size when the map says it ("PDF · 48 kB"), as the phone writes sizes. */
fun fileFacts(file: MapFile, context: Context): String =
    listOfNotNull(fileKindName(file, context), file.size?.let { Formatter.formatShortFileSize(context, it) }).joinToString(" · ")

/**
 * The media type a file opens in another app as: its own, as the map says
 * it, else the one its extension gives, else any file's.
 */
fun fileOpenType(file: MapFile): String =
    file.type?.substringBefore(';')?.trim()?.takeIf { it.contains('/') }
        ?: MimeTypeMap.getSingleton().getMimeTypeFromExtension(fileExtension(file))
        ?: "application/octet-stream"
