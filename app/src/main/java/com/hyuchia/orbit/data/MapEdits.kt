package com.hyuchia.orbit.data

import com.hyuchia.orbit.R
import com.hyuchia.orbit.Words
import com.hyuchia.orbit.page.EditedMap
import com.hyuchia.orbit.page.MapCommand
import com.hyuchia.orbit.page.PagePool
import java.io.IOException

/** How many times an edit is made again on a map that changed while it was being made. */
private const val EDIT_TRIES = 3

/** What a change made to a map from outside its screen did. */
enum class MapEditResult {
    /** The map has the change now. */
    Written,

    /** A newer version of Orbit made the map: this one only shows it, so it's as it was. */
    Newer,

    /** Orbit can't read all of the map, so it's as it was: its text is fixed on its screen first. */
    Unreadable,

    /** The maps here can only be read, or the map's file couldn't be: it's as it was. */
    Refused,

    /** Storage that's full or gone couldn't take the change: the map's as it was. */
    NotWritten,

    /** The map isn't here anymore. */
    Missing,

    /** The map changed on every try. */
    KeptChanging,
}

/** How a tag renamed in every map went: how many maps `changed`, and how many Orbit `left` as they were, which it can only show. */
data class TagRename(val changed: Int, val left: Int)

/** A copy of a map ([MapEdits.duplicate]): its id, or null with why there's none. */
data class MapCopy(val id: String?, val result: MapEditResult)

/**
 * A map's text with a change made as Orbit makes it: a new `title` or new
 * `tags`, or both (null leaves one as it is). The app's is a spare page's
 * `editMap` ([onPages]), Orbit's own reading of the map, markdown or YAML.
 */
fun interface MapTextEditor {
    suspend fun edit(markdown: String, title: String?, tags: List<String>?): EditedMap

    companion object {
        /** The page's own edit, on the spare page. */
        fun onPages(pages: PagePool) = MapTextEditor { markdown, title, tags ->
            pages.work { page -> page.request(EditedMap.serializer()) { job -> MapCommand.EditMap(job, markdown, title, tags) } }
        }
    }
}

/**
 * Changes Home makes to maps without opening them: a map's name and tags, a
 * tag across every map, and a copy's name. Each is made by [editor], as the
 * map's screen would make it, for markdown and YAML alike, and a map Orbit
 * can only show (a newer Orbit made it, or it can't read all of it) is left
 * as it is, with why. A version is kept before each change, a screen showing
 * the map catches up, and a map that changes meanwhile is edited again as it
 * is then.
 */
class MapEdits(private val maps: MapRepository, private val editor: MapTextEditor, private val words: Words) {
    suspend fun rename(id: String, title: String): MapEditResult = edit(id, VersionReason.Renaming) { text -> editor.edit(text, title, null) }

    suspend fun setTags(id: String, tags: List<String>): MapEditResult = edit(id, VersionReason.Tags) { text -> editor.edit(text, null, MapTags.normalize(tags)) }

    /**
     * Renames a tag in every map that has it, whatever its capitals, as one
     * tag with any it's renamed to; `to` null takes it out of them.
     */
    suspend fun renameTag(from: String, to: String?): TagRename {
        val key = MapTags.key(from)
        val tagged = maps.maps.value.orEmpty().filter { map -> !map.unreadable && map.outline.tags.any { MapTags.key(it) == key } }
        var changed = 0
        var left = 0

        for (map in tagged) {
            val result = edit(map.id, VersionReason.Tags) { text ->
                val tags = MapTags.read(text).flatMap { tag -> if (MapTags.key(tag) == key) listOfNotNull(to) else listOf(tag) }
                editor.edit(text, null, MapTags.normalize(tags))
            }

            when (result) {
                MapEditResult.Written -> changed += 1
                MapEditResult.Newer, MapEditResult.Unreadable -> left += 1
                else -> Unit
            }
        }

        return TagRename(changed, left)
    }

    /**
     * A copy of a map, its pictures included, with "(copy)" after its name,
     * as Orbit names it. The id of the copy, or why there's none: a map Orbit
     * can only show isn't copied under another name.
     */
    suspend fun duplicate(id: String): MapCopy {
        val text = try {
            maps.read(id)
        } catch (unreadable: IOException) {
            return MapCopy(null, unread(id))
        }

        val title = words.string(R.string.data_copy_title, MapText.outline(text, words).title)
        val copy = editor.edit(text, title, null)
        refusal(copy)?.let { return MapCopy(null, it) }
        val markdown = copy.markdown ?: return MapCopy(null, MapEditResult.Refused)
        return MapCopy(maps.duplicate(id, markdown), MapEditResult.Written)
    }

    /** Makes `change` on the map's text as it is, until the map stays that text while it's written. */
    private suspend fun edit(id: String, reason: VersionReason, change: suspend (String) -> EditedMap): MapEditResult {
        repeat(EDIT_TRIES) {
            val text = try {
                maps.read(id)
            } catch (unreadable: IOException) {
                return unread(id)
            }

            val edited = change(text)
            refusal(edited)?.let { return it }
            val markdown = edited.markdown ?: return MapEditResult.Refused

            if (markdown == text) {
                return MapEditResult.Written
            }

            when (maps.change(id, markdown, base = text, reason = reason)) {
                WriteResult.Written -> return MapEditResult.Written
                WriteResult.Changed -> Unit
                WriteResult.Refused -> return MapEditResult.Refused
                WriteResult.Missing -> return MapEditResult.Missing
                WriteResult.NotWritten -> return MapEditResult.NotWritten
            }
        }

        return MapEditResult.KeptChanging
    }

    /** Why a map's text couldn't be read: it isn't here anymore, or its file can't be read, which Orbit never writes over. */
    private fun unread(id: String): MapEditResult = if (maps.summary(id) == null) MapEditResult.Missing else MapEditResult.Refused

    /** Why the page left the map as it is, when it did. */
    private fun refusal(edited: EditedMap): MapEditResult? = when (edited.refused) {
        null -> null
        "newer" -> MapEditResult.Newer
        else -> MapEditResult.Unreadable
    }
}
