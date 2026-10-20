package com.hyuchia.orbit.ui

import android.content.ActivityNotFoundException
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

/** Writes an export to the cache, where other apps can be given it. Throws when it can't be written whole (a full phone, a picture that went). */
suspend fun exportFile(context: Context, name: String, write: suspend (OutputStream) -> Unit): Uri = withContext(Dispatchers.IO) {
    val file = exportDestination(File(context.cacheDir, "exports"), name, System.currentTimeMillis())

    try {
        file.outputStream().use { write(it) }
    } catch (failed: Exception) {
        // Half an export is of no use to the app it would go to.
        file.parentFile?.deleteRecursively()
        throw failed
    }

    FileProvider.getUriForFile(context, "${context.packageName}.files", file)
}

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
    val chooser = Intent.createChooser(send, context.getString(R.string.ui_share_named, title)).putExtra(Intent.EXTRA_EXCLUDE_COMPONENTS, own.toTypedArray())
    context.startActivity(chooser)
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
