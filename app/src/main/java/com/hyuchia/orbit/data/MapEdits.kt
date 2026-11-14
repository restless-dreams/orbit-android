package com.hyuchia.orbit.data

import com.hyuchia.orbit.R
import com.hyuchia.orbit.Words
import com.hyuchia.orbit.page.EditedMap
import com.hyuchia.orbit.page.MapCommand
import com.hyuchia.orbit.page.PageException
import com.hyuchia.orbit.page.PagePool
import java.io.IOException

/** How many times an edit is made again on a map that changed while it was being made. */
private const val EDIT_TRIES = 3

/** What a change made to a map from outside its screen did. */
sealed interface MapEditResult {
    /** The map has the change now. */
    data object Written : MapEditResult

    /** A newer version of Orbit made the map: this one only shows it, so it's as it was. */
    data object Newer : MapEditResult

    /** Orbit can't read all of the map, so it's as it was: its text is fixed on its screen first. */
    data object Unreadable : MapEditResult

    /** The maps here can only be read, or the map's file couldn't be: it's as it was. */
    data object Refused : MapEditResult

    /** Storage that's full or gone couldn't take the change: the map's as it was. */
    data object NotWritten : MapEditResult

    /** The map isn't here anymore. */
    data object Missing : MapEditResult

    /** The map changed on every try. */
    data object KeptChanging : MapEditResult

    /**
     * The page that makes the change couldn't, and `reason` says why, in the
     * app's language: a WebView too old to draw maps, a page that didn't load,
     * stopped or didn't answer, or one that couldn't make this change. The
     * map's as it was. `lasting` when no page can make changes for a while
     * (see [PageException.Kind.lasting]): the next map's would fail the same.
     */
    data class PageFailed(val reason: String, val lasting: Boolean = true) : MapEditResult
}

/**
 * How a tag renamed in every map went: how many maps `changed`, and the maps
 * that kept it, by id, with why: Orbit can only show them
 * ([MapEditResult.Newer], [MapEditResult.Unreadable]), or couldn't change
 * them ([MapEditResult.Refused], [MapEditResult.NotWritten],
 * [MapEditResult.KeptChanging], [MapEditResult.PageFailed]).
 */
data class TagRename(val changed: Int, val kept: Map<String, MapEditResult> = emptyMap())

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
        /** The page's own edit, on the spare page. A page that failed it is let go, so another try has a new one. */
        fun onPages(pages: PagePool) = MapTextEditor { markdown, title, tags ->
            pages.work { page ->
                try {
                    page.request(EditedMap.serializer()) { job -> MapCommand.EditMap(job, markdown, title, tags) }
                } catch (failed: PageException) {
                    pages.retire(page)
                    throw failed
                }
            }
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
 * is then. When the page can't make a change (a WebView too old to draw maps,
 * a page that stopped or didn't answer), the map stays as it is, and the
 * result says why ([MapEditResult.PageFailed]): nothing here throws for it.
 */
class MapEdits(private val maps: MapRepository, private val editor: MapTextEditor, private val words: Words) {
    suspend fun rename(id: String, title: String): MapEditResult = edit(id, VersionReason.Renaming) { text -> editor.edit(text, title, null) }

    suspend fun setTags(id: String, tags: List<String>): MapEditResult = edit(id, VersionReason.Tags) { text -> editor.edit(text, null, MapTags.normalize(tags)) }

    /**
     * Renames a tag in every map that has it, whatever its capitals, as one
     * tag with any it's renamed to; `to` null takes it out of them. A map it
     * couldn't change keeps the tag, and says why. When no page can make
     * changes for a while (a WebView too old to draw maps, a page that didn't
     * load or didn't answer), the maps not changed yet keep it too, for the
     * same reason, rather than each waiting on a page that can't: the maps
     * changed before then keep their change. A page that stopped, or failed
     * one map's change, costs only that map, once another try on a new page
     * failed too.
     */
    suspend fun renameTag(from: String, to: String?): TagRename {
        val key = MapTags.key(from)
        val tagged = maps.maps.value.orEmpty().filter { map -> !map.unreadable && map.outline.tags.any { MapTags.key(it) == key } }
        var changed = 0
        val kept = LinkedHashMap<String, MapEditResult>()

        for ((index, map) in tagged.withIndex()) {
            val result = edit(map.id, VersionReason.Tags) { text ->
                val tags = MapTags.read(text).flatMap { tag -> if (MapTags.key(tag) == key) listOfNotNull(to) else listOf(tag) }
                editor.edit(text, null, MapTags.normalize(tags))
            }

            when (result) {
                MapEditResult.Written -> changed += 1
                // Gone meanwhile, it has no tag to keep.
                MapEditResult.Missing -> Unit
                is MapEditResult.PageFailed -> {
                    if (!result.lasting) {
                        kept[map.id] = result
                        continue
                    }

                    tagged.drop(index).forEach { left -> kept[left.id] = result }
                    break
                }
                else -> kept[map.id] = result
            }
        }

        return TagRename(changed, kept)
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

        val copy = try {
            onPage { editor.edit(text, title, null) }
        } catch (failed: PageException) {
            return MapCopy(null, failed.result())
        }

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

            val edited = try {
                onPage { change(text) }
            } catch (failed: PageException) {
                return failed.result()
            }

            refusal(edited)?.let { return it }
            val markdown = edited.markdown ?: return MapEditResult.Refused

            if (markdown == text) {
                return MapEditResult.Written
            }

            // A new name gives the center a new id: its place on the path, its rehearsal's time and its card follow it.
            when (maps.change(id, markdown, base = text, reason = reason, renamed = edited.renamed.associate { it.from to it.to })) {
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

    /**
     * What `make` makes on a page, tried once more when the page failed but a new one may not: it stopped, or
     * failed this map's change (the editor lets such a page go, so the second try has a new one).
     */
    private suspend fun <T> onPage(make: suspend () -> T): T = try {
        make()
    } catch (failed: PageException) {
        if (failed.kind.lasting) {
            throw failed
        }

        make()
    }

    /**
     * The page couldn't make a change, saying why: the map's as it was. Its own reason for a change it refused
     * is in its words, which may not be the app's language: the app says it couldn't make the change instead.
     */
    private fun PageException.result(): MapEditResult {
        val reason = if (kind == PageException.Kind.Refused) words.string(R.string.page_could_not_make_change) else message.orEmpty()
        return MapEditResult.PageFailed(reason, kind.lasting)
    }

    /** Why the page left the map as it is, when it did. */
    private fun refusal(edited: EditedMap): MapEditResult? = when (edited.refused) {
        null -> null
        "newer" -> MapEditResult.Newer
        else -> MapEditResult.Unreadable
    }
}
