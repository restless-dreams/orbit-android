package com.hyuchia.orbit.map

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.Html
import com.hyuchia.orbit.Logs

/** What the clipboard holds that a map can take, as far as its description says. */
enum class Pasteable { Ideas, Picture }

/** What was copied, read when someone pastes it. */
sealed interface Clip {
    data class Picture(val uri: Uri) : Clip

    /** Text to paste as ideas. `sourceMap` is the map an idea copied in Orbit came from, whose pictures its text points to. */
    data class Ideas(val text: String, val sourceMap: String?) : Clip
}

/**
 * The clipboard, for copying ideas and pasting text and pictures onto maps.
 *
 * Android tells people when an app reads what they copied, so Orbit only
 * reads it when they paste; to decide whether to offer pasting, it looks at
 * the clip's description, which Android doesn't count as reading it.
 * Ideas copied in Orbit carry their orbit-md and their map in the clip's
 * item, beside the plain outline other apps paste: pasted in Orbit they
 * come back whole. Not in the description, which any app on screen could
 * read without Android saying so.
 */
class MapClipboard(context: Context) {
    private val manager = context.getSystemService(ClipboardManager::class.java)
    val resolver: ContentResolver = context.contentResolver

    /** What pasting would add, or null when there's nothing a map can take. */
    fun pasteable(): Pasteable? {
        val description = manager?.primaryClipDescription ?: return null
        val types = (0 until description.mimeTypeCount).map { description.getMimeType(it) }

        return when {
            types.any { it.startsWith("image/") } -> Pasteable.Picture
            types.any { it.startsWith("text/") } -> Pasteable.Ideas
            else -> null
        }
    }

    /** What was copied, for pasting it now. */
    fun read(): Clip? {
        val clip = manager?.primaryClip ?: return null

        if (clip.itemCount == 0) {
            return null
        }

        val item = clip.getItemAt(0)
        val uri = item.uri

        if (uri != null && isPicture(uri, clip.description)) {
            return Clip.Picture(uri)
        }

        val copied = try {
            // Orbit 0.2.0 put them in the description's extras.
            val extras = item.intent?.extras ?: clip.description.extras?.let(::Bundle)
            extras?.getString(EXTRA_MARKDOWN)?.let { markdown -> Clip.Ideas(markdown, extras.getString(EXTRA_MAP)) }
        } catch (unreadable: RuntimeException) {
            // Another app's clip can carry anything in its item: what Orbit can't read there, it leaves.
            null
        }

        if (copied != null) {
            return copied
        }

        // Text as text: a file's address, or what a file holds, isn't an idea.
        val text = item.text?.toString() ?: item.htmlText?.let { html -> Html.fromHtml(html, Html.FROM_HTML_MODE_COMPACT).toString() }
        return text?.takeIf { it.isNotBlank() }?.let { Clip.Ideas(it, sourceMap = null) }
    }

    private fun isPicture(uri: Uri, description: ClipDescription): Boolean {
        val type = try {
            resolver.getType(uri)
        } catch (refused: SecurityException) {
            null
        }

        return (type ?: description.getMimeType(0)).startsWith("image/")
    }

    /**
     * Copies an idea: `text`, an outline other apps paste, and `markdown`,
     * the idea as Orbit pastes it, from map `mapId`. False when it's too much
     * for the clipboard to hold.
     */
    fun copy(label: String, text: String, markdown: String, mapId: String): Boolean {
        val manager = manager ?: return false
        val orbit = Intent().putExtra(EXTRA_MARKDOWN, markdown).putExtra(EXTRA_MAP, mapId)
        val clip = ClipData(label, arrayOf(ClipDescription.MIMETYPE_TEXT_PLAIN), ClipData.Item(text, orbit, null))

        return try {
            manager.setPrimaryClip(clip)
            true
        } catch (tooBig: RuntimeException) {
            // The clipboard crosses processes: a clip past its transaction's size doesn't get there.
            Logs.warn("MapClipboard", "Couldn't copy an idea of ${markdown.length + text.length} characters", tooBig)
            false
        }
    }

    private companion object {
        const val EXTRA_MARKDOWN = "com.hyuchia.orbit.markdown"
        const val EXTRA_MAP = "com.hyuchia.orbit.map"
    }
}
