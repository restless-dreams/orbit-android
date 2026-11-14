package com.hyuchia.orbit.ui

import android.app.Activity
import android.app.PendingIntent
import android.content.ActivityNotFoundException
import android.content.BroadcastReceiver
import android.content.ClipData
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import com.hyuchia.orbit.Logs
import com.hyuchia.orbit.R
import com.hyuchia.orbit.container
import com.hyuchia.orbit.data.MEDIA_DIRECTORY
import com.hyuchia.orbit.data.requireRoom
import com.hyuchia.orbit.data.watched
import com.hyuchia.orbit.page.findActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.io.OutputStream
import java.util.UUID

/** A file name for a map's export: its title, without characters file systems refuse, or "Map" in the app's language when that leaves nothing. */
fun exportName(context: Context, title: String, extension: String): String {
    val name = title.replace(Regex("""[\\/:*?"<>|\n\r\t]+"""), " ").replace(Regex("""\s+"""), " ").trim().take(60)
        .ifEmpty { context.getString(R.string.export_default_title) }
    return "$name.$extension"
}

/**
 * How long an export stays for the app it went to: one that reads it later,
 * as an email being written does, still finds it.
 */
private const val EXPORT_KEPT = 24 * 60 * 60 * 1000L

/**
 * Writes an export to the cache, where other apps can be given it, and opens
 * the share sheet for it ([shareFile]), unless `goes`, asked right before the
 * sheet would open, says it mustn't: a private map can lock while its file is
 * written, a big one's for seconds, and then the export is deleted and
 * nothing opens. Says whether the sheet opened. Throws when the export can't
 * be written whole (a full phone, a picture that went).
 */
suspend fun shareExport(context: Context, name: String, mimeType: String, title: String, goes: () -> Boolean, write: suspend ExportRoom.(OutputStream) -> Unit): Boolean {
    val file = makeExport(context, name) { output ->
        write(output)
        true
    }

    return shareMade(context, checkNotNull(file), mimeType, title, goes)
}

/**
 * Shares a file of the map `mapId` as [shareExport] does: the sheet opens
 * only while the map doesn't show locked, as it is right before it would
 * ([com.hyuchia.orbit.privacy.PrivateMaps.hidesNow]). Every way out that
 * writes a file of a map and hands it to another app goes through here, or
 * through [shareMapFile] once it's written.
 */
suspend fun shareMapExport(context: Context, mapId: String, name: String, mimeType: String, title: String, write: suspend ExportRoom.(OutputStream) -> Unit): Boolean =
    shareExport(context, name, mimeType, title, goes = { !context.container.privateMaps.hidesNow(mapId) }, write)

/**
 * Opens the share sheet for a file of the map `mapId` already made in the
 * cache ([makeExport]), as [shareMapExport] does: only while the map doesn't
 * show locked, as it is right before it would. Otherwise the file goes, and
 * nothing opens. Says whether the sheet opened.
 */
fun shareMapFile(context: Context, mapId: String, file: File, mimeType: String, title: String): Boolean =
    shareMade(context, file, mimeType, title, goes = { !context.container.privateMaps.hidesNow(mapId) })

/**
 * Makes a file of the map `mapId` in the cache, as [shareMapExport] does,
 * and opens it in the app the phone has for `mimeType` rather than the
 * share sheet: only while the map doesn't show locked, as it is right
 * before it would. One that locked while the file was made opens nothing,
 * and the file goes. A private map's file goes as private maps lock
 * ([com.hyuchia.orbit.privacy.PrivateCopies]), as an idea's file opened in
 * another app does. What makes a big file says how big it is first, to its
 * [ExportRoom]. Says whether an app opened it. Throws when the file can't
 * be made whole: [com.hyuchia.orbit.data.OutputFailed] when there's no room
 * for it.
 */
suspend fun openMapExport(context: Context, mapId: String, name: String, mimeType: String, write: suspend ExportRoom.(OutputStream) -> Unit): Boolean {
    val container = context.container

    return openExport(
        File(context.cacheDir, EXPORTS),
        name,
        write,
        hides = { container.privateMaps.hidesNow(mapId) },
        isPrivate = { container.maps.summary(mapId)?.metadata?.isPrivate == true },
        keep = container.privateCopies::keep,
    ) { file -> openFile(context, sharedFileUri(context, file) ?: throw IOException("An export landed outside the exports"), mimeType) }
}

/**
 * [openMapExport] into `exports`: the file is made, then let go when the
 * map `hides` now, else kept until private maps lock (`keep`) when it
 * `isPrivate`, and opened (`open`).
 */
internal suspend fun openExport(
    exports: File,
    name: String,
    write: suspend ExportRoom.(OutputStream) -> Unit,
    hides: () -> Boolean,
    isPrivate: () -> Boolean,
    keep: (File) -> Unit,
    open: (File) -> Boolean,
): Boolean {
    val file = checkNotNull(
        makeExport(exports, name) { output ->
            write(output)
            true
        },
    )

    if (hides()) {
        forgetExport(file)
        return false
    }

    // The app it opens in reads it while it's open: a private map's goes as soon as private maps lock.
    if (isPrivate()) {
        keep(file)
    }

    return open(file)
}

/** Opens a file in the app the phone has for `mimeType`, with leave to read it. False when no app opens it. */
fun openFile(context: Context, uri: Uri, mimeType: String): Boolean {
    val view = Intent(Intent.ACTION_VIEW).setDataAndType(uri, mimeType).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)

    return try {
        context.startActivity(view)
        true
    } catch (missing: ActivityNotFoundException) {
        Logs.warn("OrbitSharing", "No app opens a file of that kind")
        false
    }
}

/** Opens the share sheet for an export made in the cache, unless `goes`, asked right before, says it mustn't: then the export goes. */
internal fun shareMade(context: Context, file: File, mimeType: String, title: String, goes: () -> Boolean): Boolean {
    if (!goes()) {
        forgetExport(file)
        return false
    }

    shareFile(context, sharedFileUri(context, file) ?: throw IOException("An export landed outside the exports"), mimeType, title)
    return true
}

/**
 * Writes an export to the cache, in a folder of its own, a piece at a time
 * as `write` makes it, and gives its file: null, with nothing left behind,
 * when `write` says it couldn't make it. What writes a big file says how big
 * it may be first, to its [ExportRoom], as a page and a zip do. Throws when
 * it can't be written whole: [com.hyuchia.orbit.data.OutputFailed] when the
 * cache can't take it, as on a phone whose storage is full.
 */
suspend fun makeExport(context: Context, name: String, write: suspend ExportRoom.(OutputStream) -> Boolean): File? = makeExport(File(context.cacheDir, EXPORTS), name, write)

/** [makeExport] into `exports`. */
internal suspend fun makeExport(exports: File, name: String, write: suspend ExportRoom.(OutputStream) -> Boolean): File? = withContext(Dispatchers.IO) {
    val file = exportDestination(exports, name, System.currentTimeMillis())
    val room = ExportRoom(file.parentFile ?: exports)
    var made = false

    try {
        made = file.outputStream().watched().buffered().use { room.write(it) }
    } finally {
        // Half an export is of no use to the app it would go to.
        if (!made) {
            forgetExport(file)
        }
    }

    file.takeIf { made }
}

/**
 * The storage an export is written to, for what writes a big one to say how
 * big it may be before it starts: called with that, it throws
 * [com.hyuchia.orbit.data.OutputFailed] when the storage hasn't room for it
 * ([requireRoom]), so a big file isn't started where it can't be finished.
 */
class ExportRoom internal constructor(private val folder: File) : (Long) -> Unit {
    override fun invoke(size: Long) = requireRoom(folder, size)
}

/** Lets an export in the cache go, with the folder of its own it's in, once it's saved, or isn't to be. */
fun forgetExport(file: File) {
    file.parentFile?.deleteRecursively()
}

/** The cache's folder for exports: Orbit hands what's in it to other apps ([sharedFileUri]). */
private const val EXPORTS = "exports"

/**
 * The address another app is given to read `file`, or null for a file Orbit
 * never hands out. The app's file provider reaches all of the maps' folder,
 * as older versions handed a video Orbit couldn't clean to the phone's video
 * app as the map's own file; every way out now hands out a copy in the
 * exports instead ([openMapExport]). This gives out only an export in the
 * cache or a picture, video or sound in a
 * map's media folder ([sharesFile]), never a map's text, its versions or
 * its details.
 */
fun sharedFileUri(context: Context, file: File): Uri? {
    val exports = File(context.cacheDir, EXPORTS)
    val maps = File(context.filesDir, "maps")

    if (!sharesFile(file, exports, maps)) {
        Logs.warn("OrbitSharing", "Didn't hand out a file that isn't an export or a map's media")
        return null
    }

    return FileProvider.getUriForFile(context, "${context.packageName}.files", file.canonicalFile)
}

/**
 * Whether `file` is one Orbit hands out to other apps: an export in
 * `exports`, or a file in a map's media folder in `maps` (`<map>/media/…`),
 * wherever links in its path lead.
 */
internal fun sharesFile(file: File, exports: File, maps: File): Boolean {
    val path = file.canonicalFile
    val media = path.parentFile

    return path.isInside(exports.canonicalFile) || (media?.name == MEDIA_DIRECTORY && media.parentFile?.parentFile == maps.canonicalFile)
}

/** Whether this is somewhere below `folder`, never `folder` itself. */
private fun File.isInside(folder: File): Boolean = path.startsWith(folder.path + File.separator)

/**
 * Where a new export named `name` goes in `directory`: a folder of its own,
 * so it never takes the place of one shared a moment ago that the app it
 * went to hasn't read yet. Exports older than [EXPORT_KEPT] go now.
 */
internal fun exportDestination(directory: File, name: String, now: Long): File {
    directory.listFiles().orEmpty().filter { now - it.lastModified() > EXPORT_KEPT }.forEach { it.deleteRecursively() }
    val folder = File(directory, UUID.randomUUID().toString())

    if (!folder.mkdirs()) {
        throw IOException("Couldn't make a folder for an export")
    }

    return File(folder, name)
}

/**
 * Opens the share sheet for a file, without Orbit's own ways in: they'd only
 * open the map again, and on a phone where nothing else takes the file, the
 * sheet would pick Orbit without asking.
 */
fun shareFile(context: Context, uri: Uri, mimeType: String, title: String) {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = mimeType
        putExtra(Intent.EXTRA_STREAM, uri)
        putExtra(Intent.EXTRA_SUBJECT, title)
        putExtra(Intent.EXTRA_TITLE, title)
        clipData = ClipData.newRawUri(title, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }

    share(context, send, title)
}

/** Opens the share sheet for a web address, without Orbit's own ways in. */
fun shareLink(context: Context, url: String, title: String) {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, cleanLink(url))
        putExtra(Intent.EXTRA_SUBJECT, title)
        putExtra(Intent.EXTRA_TITLE, title)
    }

    share(context, send, title)
}

private fun share(context: Context, send: Intent, title: String) {
    val own = context.packageManager.queryIntentActivities(send, 0)
        .map { it.activityInfo }
        .filter { it.packageName == context.packageName }
        .map { ComponentName(it.packageName, it.name) }
    // The share sheet says when something is chosen in it (ShareChosenReceiver).
    val chosen = PendingIntent.getBroadcast(
        context,
        SHARE_SHEET,
        Intent(context, ShareChosenReceiver::class.java),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )
    val chooser = Intent.createChooser(send, context.getString(R.string.ui_share_named, title), chosen.intentSender)
        .putExtra(Intent.EXTRA_EXCLUDE_COMPONENTS, own.toTypedArray())
    val activity = context.findActivity()

    if (activity == null) {
        context.startActivity(chooser)
        return
    }

    // Opened for a result only so it can be closed again: a share sheet left open goes as private maps lock (closeShareSheet).
    shareSheetWaiting = true
    @Suppress("DEPRECATION")
    activity.startActivityForResult(chooser, SHARE_SHEET)
}

/** The request code Android's share sheet is opened with, to close it again. */
private const val SHARE_SHEET = 4801

/** Whether the share sheet opened last may still be open, with nothing chosen in it. */
@Volatile
private var shareSheetWaiting = false

/**
 * Closes the share sheet `activity` opened, while it's still open and
 * nothing was chosen: as private maps lock, nothing of theirs stays offered
 * to other apps over the lock. Once something is chosen, the share sheet
 * hands its request on to the app chosen, whose screen would close too:
 * that share is the other app's, and stays.
 */
fun closeShareSheet(activity: Activity) {
    if (shareSheetWaiting) {
        shareSheetWaiting = false
        activity.finishActivity(SHARE_SHEET)
    }
}

/** Hears from the share sheet that something was chosen in it: what it opened is the other app's, and stays as Orbit locks. */
class ShareChosenReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        shareSheetWaiting = false
    }
}

/** Links a map may open: web pages, in a tab, and mail, calls and texts, in their apps. */
private val LINK_SCHEMES = setOf("http", "https", "mailto", "tel", "sms")

/**
 * A link as browsers read it: no spaces or control characters at its ends,
 * no tabs or line breaks inside, so " javascript:" reads as "javascript:".
 */
fun cleanLink(url: String): String = url.trim { it <= ' ' }.replace(Regex("[\\t\\n\\r]"), "")

/** The link's scheme when a map may open it, else null. */
fun linkScheme(url: String): String? =
    Regex("^([A-Za-z][A-Za-z0-9+.-]*):").find(cleanLink(url))?.groupValues?.get(1)?.lowercase()?.takeIf { it in LINK_SCHEMES }

/**
 * A picture's web address as the app loads it: an http one over https, since
 * the app sends nothing over the web in the clear (Android's default), as the
 * map's page does, its WebView upgrading such pictures. Links still open as
 * they are, in the browser, and videos at http addresses play there.
 */
fun pictureAddress(url: String): String = if (linkScheme(url) == "http") "https${cleanLink(url).substring(4)}" else url

/**
 * Whether the app loads a picture a map gives at `address`: a web one, or
 * one inside the map's text (`data:image/…`), as the map's page does. Any
 * other kind, such as a file: or content: address, which would reach Orbit's
 * own files or other apps', loads nothing.
 */
fun isWebPicture(address: String): Boolean {
    val clean = cleanLink(address)
    return clean.startsWith("data:image/", ignoreCase = true) || linkScheme(clean).let { it == "http" || it == "https" }
}

/** Opens a web page in a tab that keeps the app's colors, or a mail, call or text link in its app. Other links stay closed. */
fun openLink(context: Context, url: String) {
    val scheme = linkScheme(url)

    if (scheme == null) {
        Logs.warn("Orbit", "Not opening a link of that kind from a map")
        return
    }

    val uri = cleanLink(url).toUri()

    try {
        if (scheme == "http" || scheme == "https") {
            CustomTabsIntent.Builder().setShowTitle(true).build().launchUrl(context, uri)
        } else {
            context.startActivity(Intent(Intent.ACTION_VIEW, uri))
        }
    } catch (missing: ActivityNotFoundException) {
        Logs.warn("Orbit", "Nothing can open that link")
    }
}
