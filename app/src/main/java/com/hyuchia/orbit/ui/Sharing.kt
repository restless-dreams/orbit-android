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
suspend fun shareExport(context: Context, name: String, mimeType: String, title: String, goes: () -> Boolean, write: suspend (OutputStream) -> Unit): Boolean {
    val file = writeExport(context, name, write)

    if (!goes()) {
        file.parentFile?.deleteRecursively()
        return false
    }

    shareFile(context, sharedFileUri(context, file) ?: throw IOException("An export landed outside the exports"), mimeType, title)
    return true
}

/**
 * Shares a file of the map `mapId` as [shareExport] does: the sheet opens
 * only while the map doesn't show locked, as it is right before it would
 * ([com.hyuchia.orbit.privacy.PrivateMaps.hidesNow]). Every way out that
 * writes a file of a map and hands it to another app goes through here.
 */
suspend fun shareMapExport(context: Context, mapId: String, name: String, mimeType: String, title: String, write: suspend (OutputStream) -> Unit): Boolean =
    shareExport(context, name, mimeType, title, goes = { !context.container.privateMaps.hidesNow(mapId) }, write)

/** Writes an export to the cache, a folder of its own, and gives its file. Throws when it can't be written whole. */
private suspend fun writeExport(context: Context, name: String, write: suspend (OutputStream) -> Unit): File = withContext(Dispatchers.IO) {
    val file = exportDestination(File(context.cacheDir, "exports"), name, System.currentTimeMillis())

    try {
        file.outputStream().use { write(it) }
    } catch (failed: Exception) {
        // Half an export is of no use to the app it would go to.
        file.parentFile?.deleteRecursively()
        throw failed
    }

    file
}

/**
 * The address another app is given to read `file`, or null for a file Orbit
 * never hands out. The app's file provider reaches all of the maps' folder,
 * since a video in a map plays in the phone's video app; this gives out only
 * an export in the cache or a picture, video or sound in a map's media
 * folder ([sharesFile]), never a map's text, its versions or its details.
 */
fun sharedFileUri(context: Context, file: File): Uri? {
    val exports = File(context.cacheDir, "exports")
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
