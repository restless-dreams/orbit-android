package com.hyuchia.orbit.page

import com.hyuchia.orbit.data.IdeaDate
import com.hyuchia.orbit.media.MediaKind
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import kotlin.math.ceil

/** The version of a goal's fields this app reads; a change older apps would misread raises it (`goal@2`). */
const val GOAL_VERSION = 1

/** The version of what a Progress idea counts that this app reads, as [GOAL_VERSION] is a goal's. */
const val PROGRESS_VERSION = 1

/**
 * The Material colors maps take on when they don't pick a style: Orbit's
 * Material theme built from [seed] (the app's primary color), with
 * [tokens] for what must match the app exactly, such as its background.
 */
@Serializable
data class AppTheme(val dark: Boolean, val tokens: Map<String, String>, val seed: String? = null)

/** How the app wants maps to behave. */
@Serializable
data class MapSettings(val haptics: Boolean, val snap: Boolean, val longPressDelay: Int)

/** The parts of the map the app covers with its own controls, in dp from each edge: Orbit fits views into the rest. */
@Serializable
data class MapInsets(val top: Float = 0f, val right: Float = 0f, val bottom: Float = 0f, val left: Float = 0f)

@Serializable
data class Media(
    val src: String,
    val alt: String? = null,
    val caption: String? = null,
    val provider: String? = null,
    val thumbnail: String? = null,

    /** A picture's motion, when it's a live photo's: the few seconds of video around it. */
    val motion: MediaMotion? = null,
)

/**
 * One of an idea's files (Orbit's `files`): its address (`media/file-…`),
 * its name as people know it, its media type and its size in bytes, when
 * the map says them. The page sends a size only when it's a whole number
 * of bytes, so it always fits.
 */
@Serializable
data class MapFile(
    val src: String,
    val name: String? = null,
    val type: String? = null,
    val size: Long? = null,
)

/** A live photo's motion: its video's address, and where in it the still was taken, in seconds, when that's known. */
@Serializable
data class MediaMotion(val src: String, val still: Double? = null)

/** A node of the map, as the outline, search and editor see it. */
@Serializable
data class MapNode(
    val id: String,
    val text: String,
    val parentId: String? = null,
    val depth: Int = 0,

    /** Its place among its siblings. */
    val index: Int = 0,

    val childCount: Int = 0,
    val icon: String? = null,

    /** The node's own color. */
    val color: String? = null,

    /** The color the map draws it with: its own, or its branch's. */
    val drawnColor: String? = null,

    val status: String? = null,
    @Serializable(with = MapWholeNumberSerializer::class) val priority: Int? = null,

    /**
     * Its date, as Orbit writes it: `2026-11-20`, or `2026-11-20 09:30` with
     * a time (see [com.hyuchia.orbit.data.IdeaDate]). One Orbit can't read
     * stays in [attrs], as written.
     */
    val due: String? = null,

    val tags: List<String> = emptyList(),
    val collapsed: Boolean = false,
    val type: String? = null,

    /** The version of its type's fields it was written for (`goal@2`), when it says. */
    @Serializable(with = MapWholeNumberSerializer::class) val typeVersion: Int? = null,

    val link: String? = null,
    val note: String? = null,
    val noteFormat: String? = null,
    val image: Media? = null,
    val video: Media? = null,
    val embed: Media? = null,

    /** Attributes Orbit doesn't use itself, as they were read: a goal's numbers, or another app's. */
    val attrs: JsonObject? = null,

    /** Its sound: a voice memo, music or a recording. */
    val audio: Media? = null,

    /** The files it holds: PDFs, spreadsheets, any file. */
    val files: List<MapFile> = emptyList(),
) {
    val isRoot: Boolean get() = parentId == null

    /**
     * A goal idea, which tracks a number toward a target (see `map/Goal.kt`):
     * one written for this version of a goal's fields (`GOAL_VERSION`, as the
     * page's `web/goal.ts` says) or an older one. A goal for a newer one may
     * mean its numbers another way, so it's an idea like any other here.
     */
    val isGoal: Boolean get() = type?.trim().equals("goal", ignoreCase = true) && (typeVersion ?: 1) <= GOAL_VERSION

    /** The node's text with its icon, as the map shows it, with `untitled` for text it doesn't have. */
    fun label(untitled: String): String = listOfNotNull(icon, text.ifBlank { untitled }).joinToString(" ")

    /** Its date, read as Orbit reads it; null without one, or with one Orbit can't read. */
    val date: IdeaDate? get() = IdeaDate.parse(due)

    /** Whether the details sheet has something to show beyond the title. */
    val hasDetails: Boolean
        get() = !note.isNullOrBlank() || image != null || video != null || audio != null || embed != null || link != null ||
            tags.isNotEmpty() || status != null || priority != null || date != null || isGoal || files.isNotEmpty()
}

/** The map's frontmatter options the app shows and changes, by the names Orbit and the file give them. */
@Serializable
data class MapOptions(
    val theme: String? = null,
    val palette: List<String>? = null,
    val showRings: Boolean? = null,

    /** Whether the orbit drifts by itself, one turn every [speed] seconds. */
    val animation: Boolean? = null,

    val speed: Double? = null,
    @Serializable(with = MapWholeNumberSerializer::class) val maxDepth: Int? = null,

    /** The view the map opens in: `focus` or `all`. */
    val view: String? = null,

    /** The app's own: `false` when ideas with to-dos or goals don't show progress rings by themselves. */
    val progressRings: Boolean? = null,

    /** The app's own: the map's tags, which Home groups maps by (see `data/MapTags.kt`). */
    val tags: List<String>? = null,
)

/** The map's two views, as the page names them (Orbit's `view`): one idea with its orbit around it, or the whole map. */
object ViewMode {
    const val FOCUS = "focus"
    const val OVERVIEW = "all"
}

/** The whole map, as the page reports it after every change. */
@Serializable
data class MapState(
    val title: String,

    /** Every node, the root first, each followed by its children. */
    val nodes: List<MapNode>,

    val options: MapOptions = MapOptions(),

    /** Ids from the root to the node in focus. */
    val path: List<String> = emptyList(),

    /** `focus` or `all` ([ViewMode]). */
    val mode: String = ViewMode.FOCUS,

    /** The style the map picks, when Orbit knows it. */
    val style: String? = null,

    /** That style's background color, if it has one. */
    val surface: String? = null,

    /**
     * Why the map can't be changed here, when it can't: `unreadable` (Orbit
     * couldn't read all of its text), `newer` (a newer Orbit made it),
     * `deleted` or `storage` (see [MapCommand.Lock]).
     */
    val readOnly: String? = null,

    /** What Orbit couldn't read, in its words, when that's why. */
    val problem: String? = null,
) {
    val root: MapNode? get() = nodes.firstOrNull()
}

/** A node that got a new id from new text. */
@Serializable
data class Renamed(val from: String, val to: String)

/** Something Orbit noticed while reading a map's text. */
@Serializable
data class Diagnostic(val message: String, val line: Int? = null, val severity: String = "warning") {
    val isError: Boolean get() = severity == "error"
}

/** What a file someone opens makes, before it becomes a map. */
@Serializable
data class CheckedMap(val title: String, val nodes: Int, val diagnostics: List<Diagnostic>, val markdown: String)

/**
 * A map's text with a change made from outside its screen ([MapCommand.EditMap]),
 * or null, with why it was `refused`: Orbit can only show the map, which a
 * newer Orbit made (`newer`) or it can't read all of (`unreadable`). The ideas
 * the change gave new ids are `renamed`: a new name gives the center one.
 */
@Serializable
data class EditedMap(val markdown: String? = null, val refused: String? = null, val renamed: List<Renamed> = emptyList())

/** What a file from another app makes: its maps (one, or one for each sheet of an XMind workbook), or why it makes none. */
@Serializable
data class ImportedFile(val format: String? = null, val maps: List<ImportedFileMap> = emptyList(), val error: ImportProblem? = null)

/** One map from another app's file: its orbit-md, its pictures as base64 by name, and what didn't come over as it was. */
@Serializable
data class ImportedFileMap(val markdown: String, val media: Map<String, String> = emptyMap(), val warnings: List<ImportWarning> = emptyList())

/** Something the other app's map had that Orbit keeps another way, or couldn't keep, with how many places it's about. */
@Serializable
data class ImportWarning(val code: String, val count: Int = 1, val message: String = "")

/** Why a file from another app makes no map, by Orbit's code (`unreadable`, `empty`, `password-protected`, …). */
@Serializable
data class ImportProblem(val code: String, val message: String = "")

/** Messages from the page. */
@Serializable
sealed interface MapMessage {
    @Serializable @SerialName("hello")
    data object Hello : MapMessage

    /** The map is drawn. */
    @Serializable @SerialName("ready")
    data class Ready(val state: MapState) : MapMessage

    /**
     * The map changed: an edit, new options, or an earlier version drawn again
     * (`restore`). `renamed` says when the change gave a node a new id: one
     * the app asked for, or text typed on the map.
     */
    @Serializable @SerialName("saved")
    data class Saved(
        val kind: String,
        val markdown: String,
        val state: MapState,
        val added: String? = null,
        val renamed: Renamed? = null,

        /** The text of an idea removed on the page itself, by its Delete key, for the app's Undo. */
        val removed: String? = null,

        /** The revision of the text the page last drew whole (`Load`, `Restore`) when it made this save; null from a page before revisions. */
        val revision: Int? = null,
    ) : MapMessage

    /** The map's state changed without anything to save, such as its colors. */
    @Serializable @SerialName("state")
    data class State(val state: MapState) : MapMessage

    @Serializable @SerialName("focus")
    data class Focus(val path: List<String>, val mode: String) : MapMessage

    /**
     * A node's menu, at (`x`, `y`) dp from the map's top left: a long press
     * let go without moving, a right click, or the context menu key.
     */
    @Serializable @SerialName("context")
    data class Context(val id: String, val x: Float, val y: Float) : MapMessage

    /** A long press held on a node: its menu opens when it's let go, unless it moves on to drag the idea. */
    @Serializable @SerialName("held")
    data object Held : MapMessage

    /**
     * An idea dragged somewhere else: `start` as it leaves its place (under
     * `parentId`, at `index`), `over` when where it would go changes, `end`
     * when it's let go (where it went; no `parentId` when it went back).
     */
    @Serializable @SerialName("drag")
    data class Drag(val phase: String, val id: String, val parentId: String? = null, val index: Int? = null) : MapMessage

    /** A node's note, picture, video or page, asked for on the map. */
    @Serializable @SerialName("details")
    data class Details(val id: String) : MapMessage

    /** Ctrl+C, Ctrl+X or Ctrl+V on the map, for idea `id`: `copy`, `cut` or `paste`. The app has the clipboard. */
    @Serializable @SerialName("clipboard")
    data class Clipboard(val action: String, val id: String) : MapMessage

    /** The field for typing on the map opened: on node `id`, or for a new idea under `parentId`. */
    @Serializable @SerialName("editorOpen")
    data class EditorOpen(val id: String? = null, val parentId: String? = null) : MapMessage

    @Serializable @SerialName("editorClose")
    data object EditorClose : MapMessage

    /** What Orbit made of the text in the source editor. */
    @Serializable @SerialName("source")
    data class Source(val diagnostics: List<Diagnostic>, val drawn: Boolean) : MapMessage

    @Serializable @SerialName("open")
    data class Open(val url: String) : MapMessage

    @Serializable @SerialName("haptic")
    data object Haptic : MapMessage

    /** The answer to a job. */
    @Serializable @SerialName("result")
    data class Result(val job: Int, val value: JsonElement? = null, val error: String? = null) : MapMessage

    @Serializable @SerialName("error")
    data class Error(val message: String) : MapMessage

    /**
     * Orbit didn't make a change the app asked for (`add`, `update` or
     * `options`): a value in it can't be read. Or a box to check
     * (`checkNote`) isn't on that line of the note any more. The map is as it
     * was.
     */
    @Serializable @SerialName("declined")
    data class Declined(val change: String, val message: String) : MapMessage

    /** The system stopped the page's process (to free memory, say): the map has to load again. */
    @Serializable @SerialName("stopped")
    data object Stopped : MapMessage

    /** The page itself didn't load, so it can't draw the map: a new page has to load it. The app says it, as [Stopped]. */
    @Serializable @SerialName("didNotLoad")
    data object DidNotLoad : MapMessage

    /**
     * How the video an audience's screen shows is going (web/audience.ts):
     * idea `id`'s, `time` seconds in of `duration` once that's known,
     * playing or not, at its end, or with its sound off.
     */
    @Serializable @SerialName("mediaState")
    data class MediaState(
        val id: String,
        val playing: Boolean,
        val time: Double,
        val duration: Double? = null,
        val ended: Boolean = false,
        val muted: Boolean = false,
    ) : MapMessage

    /** Something dragged onto the map from another app would go into idea `id` now, lit up; none when it's off the map. */
    @Serializable @SerialName("dropTarget")
    data class DropTarget(val id: String? = null) : MapMessage

    /** Idea `id`, dragged on the map, went out of the window: it goes on as a drag another app can take. */
    @Serializable @SerialName("dragOut")
    data class DragOut(val id: String) : MapMessage

    /**
     * Idea `id`, brought up close to sketch on ([MapCommand.Spotlight]): its
     * circle on the page, its middle at (`x`, `y`) dp from the page's top left
     * and `radius` dp across, as the flight to it starts (`start`), once it
     * rests there, and again whenever it rests somewhere new (`rest`), and
     * once the map is back as it was (`end`, at once for an idea the map
     * doesn't draw). `scrim` is the CSS color the map dims with.
     */
    @Serializable @SerialName("spotlight")
    data class Spotlight(
        val id: String,
        val phase: String,
        val x: Float = 0f,
        val y: Float = 0f,
        val radius: Float = 0f,
        val scrim: String? = null,
    ) : MapMessage
}

/** Commands for the page. */
@Serializable
sealed interface MapCommand {
    /**
     * Draws a map, opening it on `focus` in `view` when given. Every message
     * about it carries `mapId`, so none reaches another map's screen.
     */
    @Serializable @SerialName("load")
    data class Load(
        val markdown: String,
        val theme: AppTheme,
        val settings: MapSettings,
        val insets: MapInsets = MapInsets(),
        val baseUrl: String? = null,
        val focus: String? = null,
        val view: String? = null,
        val mapId: String? = null,

        /** The app's revision of this text, which the page's saves say until the next `load` or `restore`. */
        val revision: Int = 0,
    ) : MapCommand

    /** Clears the page for the next map. */
    @Serializable @SerialName("unload")
    data object Unload : MapCommand

    @Serializable @SerialName("theme")
    data class Theme(val theme: AppTheme) : MapCommand

    @Serializable @SerialName("settings")
    data class Settings(val settings: MapSettings) : MapCommand

    /** The map's controls moved: with `glide`, a fitted map glides to its new place, for a change made once. */
    @Serializable @SerialName("insets")
    data class Insets(val insets: MapInsets, val glide: Boolean = false) : MapCommand

    /** Types a node's text where it is, on the map. */
    @Serializable @SerialName("edit")
    data class Edit(val id: String) : MapCommand

    /** Types a new idea in `parentId`'s orbit, at `index` among its children (default: after the last). */
    @Serializable @SerialName("editNew")
    data class EditNew(val parentId: String, val index: Int? = null) : MapCommand

    @Serializable @SerialName("stopEditing")
    data class StopEditing(val save: Boolean) : MapCommand

    @Serializable @SerialName("add")
    data class Add(val parentId: String, val node: JsonObject, val index: Int? = null) : MapCommand

    /**
     * `changes` holds the attributes to set; `null` removes one. Its `attrs`
     * are merged into the node's custom attributes, where `null` removes one.
     * With `onto`, it's made only while the node's picture, video, sound or
     * page each of its keys names is still the file it gives (its `src`;
     * null for none), as a live photo's motion goes only onto the picture it
     * was taken with: the page leaves it otherwise. With `job`, the app
     * waits to hear what came of it, and the page answers: true once the
     * idea took the change, its save posted first; false when the map has
     * no idea `id` now (removed, or renamed, meanwhile), `onto` no longer
     * holds, or the map can't be changed now; Orbit's reason, as an error,
     * when it declined the change.
     */
    @Serializable @SerialName("update")
    data class Update(val id: String, val changes: JsonObject, val onto: Map<String, String?>? = null, val job: Int? = null) : MapCommand

    /**
     * Adds `amount` to goal `id`'s current number, or takes it off when it's
     * below 0. The page adds it to the number it has, so quick steps all count.
     */
    @Serializable @SerialName("addToGoal")
    data class AddToGoal(val id: String, val amount: JsonPrimitive) : MapCommand

    /**
     * Checks or unchecks the box of a checklist on `line` of idea `id`'s note
     * (from 0). The page changes that box in the note it has now, so quick
     * presses all count, and the map's file changes on that line alone. A
     * line without a box is `declined`.
     */
    @Serializable @SerialName("checkNote")
    data class CheckNote(val id: String, val line: Int, val checked: Boolean) : MapCommand

    /**
     * Adds `files` to idea `id`'s, after the ones it has. The page adds them
     * to the files it has now, so files added one after another all stay, and
     * one it has already, by its address, isn't added again. With `job`, the
     * page answers it as it does an [Update]'s: true once the idea has them
     * all, false when the map has no idea `id` now.
     */
    @Serializable @SerialName("addFiles")
    data class AddFiles(val id: String, val files: List<MapFile>, val job: Int? = null) : MapCommand

    /** Takes the file at `src` off idea `id`, its other files as they are. */
    @Serializable @SerialName("removeFile")
    data class RemoveFile(val id: String, val src: String) : MapCommand

    @Serializable @SerialName("remove")
    data class Remove(val id: String) : MapCommand

    @Serializable @SerialName("move")
    data class Move(val id: String, val parentId: String, val index: Int? = null) : MapCommand

    @Serializable @SerialName("duplicate")
    data class Duplicate(val id: String) : MapCommand

    @Serializable @SerialName("focus")
    data class Focus(val id: String) : MapCommand

    @Serializable @SerialName("view")
    data class View(val mode: String) : MapCommand

    @Serializable @SerialName("zoom")
    data class Zoom(val factor: Double) : MapCommand

    @Serializable @SerialName("fit")
    data object Fit : MapCommand

    /**
     * Draws the map's text as the app has it now: an undo or redo, a version
     * brought back, a change from elsewhere. Never saved by the page: the app
     * saves that very text itself.
     */
    @Serializable @SerialName("restore")
    data class Restore(
        val markdown: String,

        /** The app's revision of this text: the same as before for its own undo, a new one for a change from elsewhere. */
        val revision: Int = 0,
    ) : MapCommand

    /**
     * Keeps the map from any change, for `reason` (`deleted`: the map went to
     * the trash while it was open; `storage`: a newer Orbit keeps the app's
     * files), or lets it change again with null.
     */
    @Serializable @SerialName("lock")
    data class Lock(val reason: String?) : MapCommand

    /**
     * Draws a version of the map to look at, without saving it: one from its
     * history, read-only, or with `editable`, the map as it is now again.
     */
    @Serializable @SerialName("preview")
    data class Preview(val markdown: String, val editable: Boolean) : MapCommand

    /** Draws text from the source editor as it is typed. */
    @Serializable @SerialName("source")
    data class Source(val text: String) : MapCommand

    /** Changes frontmatter options; `null` removes one. */
    @Serializable @SerialName("options")
    data class Options(val options: JsonObject) : MapCommand

    /** Keeps the map still to edits, for an audience or while its presentation path is made; flying around still works. */
    @Serializable @SerialName("present")
    data class Present(val on: Boolean) : MapCommand

    /** A picture of a map: `markdown`, or the map on screen without it. Answers with a data URL, or SVG text. */
    @Serializable @SerialName("snapshot")
    data class Snapshot(
        val job: Int,
        val format: String,
        val markdown: String? = null,
        val width: Int? = null,
        val height: Int? = null,
        val scale: Double? = null,
        val theme: AppTheme? = null,
        val transparent: Boolean = false,

        /** Another style to draw the map in; empty draws it in the app's colors. */
        val style: String? = null,

        val view: String? = null,
        val focus: String? = null,
        val baseUrl: String? = null,
    ) : MapCommand

    /**
     * Calls off job `job`, which no one waits for any more: given up on, or
     * no longer wanted, as a thumbnail that scrolled away. The page stops its
     * work at its next step, its pictures stop loading, and it answers nothing.
     */
    @Serializable @SerialName("cancel")
    data class Cancel(val job: Int) : MapCommand

    /** Converts a map's text to orbit-md (`markdown`), `yaml` or `opml`. */
    @Serializable @SerialName("convert")
    data class Convert(val job: Int, val text: String, val to: String) : MapCommand

    /**
     * Reads a map from another app (OPML, FreeMind or Freeplane, XMind), its
     * file's bytes as base64, and answers with an [ImportedFile].
     */
    @Serializable @SerialName("importFile")
    data class ImportFile(val job: Int, val data: String, val name: String? = null) : MapCommand

    /** Reads a map's text: its title, size, diagnostics, and the map as orbit-md. */
    @Serializable @SerialName("check")
    data class Check(val job: Int, val text: String) : MapCommand

    /**
     * Adds the ideas in `text` to a map's text, under `parentId` or its top:
     * a line each, nested as pasting nests them, a long one named by its
     * start and kept whole as its note. Answers with the text as Orbit writes
     * it, or with `markdown` as it was when Orbit reads no idea in `text`.
     * Fails for a map Orbit can't read all of, or one a newer Orbit made.
     * `baseUrl` is where the map's pictures are, which the page's own drawing
     * of it, out of sight, asks for there and nowhere else.
     */
    @Serializable @SerialName("addIdeas")
    data class AddIdeas(val job: Int, val markdown: String, val text: String, val parentId: String? = null, val baseUrl: String? = null) : MapCommand

    /**
     * A map's text with a new `title` or new `tags`, or both (null leaves
     * one as it is), made as the map's screen makes them, through Orbit's
     * reading of it, markdown or YAML (web/map-edits.ts). Answered with an
     * [EditedMap]: the text as Orbit writes it, or why it's left as it is.
     */
    @Serializable @SerialName("editMap")
    data class EditMap(val job: Int, val markdown: String, val title: String? = null, val tags: List<String>? = null) : MapCommand

    /** An idea and everything under it, answered as a [CopiedIdea]. */
    @Serializable @SerialName("copy")
    data class Copy(val job: Int, val id: String) : MapCommand

    /**
     * Text pasted as ideas under `parentId`, after its last (web/paste.ts):
     * one change, one step to undo. Answered with [PastedIdeas].
     */
    @Serializable @SerialName("paste")
    data class Paste(val job: Int, val parentId: String, val text: String) : MapCommand

    /**
     * Something dragged from another app is over the map, at (`x`, `y`) dp
     * from its top left: the idea it would go into lights up, and the page
     * says which ([MapMessage.DropTarget]).
     */
    @Serializable @SerialName("dropOver")
    data class DropOver(val x: Float, val y: Float) : MapCommand

    /** The drag from another app left the map, or ended: the idea lit up for it goes out. */
    @Serializable @SerialName("dropLeave")
    data object DropLeave : MapCommand

    /** Where something let go at (`x`, `y`) dp goes, answered as a [DropPlace]; the light goes out. */
    @Serializable @SerialName("dropAt")
    data class DropAt(val job: Int, val x: Float, val y: Float) : MapCommand

    /**
     * Ideas the app made, each its text and attributes (pictures dropped from
     * another app, say), under `parentId` after its last: one change, one step
     * to undo. Answered with [PastedIdeas].
     */
    @Serializable @SerialName("addNodes")
    data class AddNodes(val job: Int, val parentId: String, val nodes: List<JsonObject>) : MapCommand

    /**
     * A new map's orbit-md from text indented as an outline, nested as
     * pasting nests it (web/paste.ts `mapFromText`): one line with everything
     * else under it in the middle, otherwise the first line, or `subject`
     * when the text came with one. Answers with the map's text.
     */
    @Serializable @SerialName("mapFromText")
    data class MapFromText(val job: Int, val text: String, val subject: String? = null) : MapCommand

    /** Keeps the ideas under each of `ids` out of view until they're revealed, for study mode: how the map looks, never what's saved. */
    @Serializable @SerialName("hideChildren")
    data class HideChildren(val ids: List<String>) : MapCommand

    /** Shows the ideas hidden under `ids` again, growing out of their ideas; null shows every hidden idea. */
    @Serializable @SerialName("revealChildren")
    data class RevealChildren(val ids: List<String>? = null) : MapCommand

    /**
     * Brings idea `id` up close to sketch on: the map flies until it fills
     * `share` of the room `insets` leave (the page's own when null), dims
     * around it, and takes no input until [EndSpotlight] takes it back. The
     * page says where it is ([MapMessage.Spotlight]).
     */
    @Serializable @SerialName("spotlight")
    data class Spotlight(val id: String, val insets: MapInsets, val share: Float? = null) : MapCommand

    /** Takes the idea brought up close back: the map flies back where it was, and takes input again. */
    @Serializable @SerialName("endSpotlight")
    data object EndSpotlight : MapCommand

    /** Answers true once the picture at `src`, in the map's media, has loaded and been drawn; false when it can't load. */
    @Serializable @SerialName("pictureShown")
    data class PictureShown(val job: Int, val src: String) : MapCommand
}

/** The idea something dropped from another app goes into: the one under it, or the one in focus. None on a map that can't change. */
@Serializable
data class DropPlace(val id: String? = null)

/** An idea copied with everything under it: as orbit-md, for Orbit to paste, and as an outline for other apps. */
@Serializable
data class CopiedIdea(val markdown: String, val text: String)

/** How many ideas a paste added, the ones under others included. */
@Serializable
data class PastedIdeas(val count: Int)

/**
 * What the presenter's phone tells the audience's screen, a second screen or
 * a TV the map is cast to (web/audience.ts): the map, then each step.
 */
@Serializable
sealed interface AudienceCommand {
    /** The map to present, opening on `focus`. `baseUrl` is where its pictures are, if the screen can reach them. */
    @Serializable @SerialName("show")
    data class Show(
        val markdown: String,
        val theme: AppTheme,
        val baseUrl: String? = null,
        val focus: String? = null,
        val captions: Boolean = true,
    ) : AudienceCommand

    /** The idea the presenter moved to, and where it is in the reading order. */
    @Serializable @SerialName("step")
    data class Step(val id: String, val index: Int, val total: Int) : AudienceCommand

    @Serializable @SerialName("captions")
    data class Captions(val on: Boolean) : AudienceCommand

    /**
     * Shows idea `id`'s picture, or its video, big over the map: a video
     * `at` seconds in, playing or paused. A step, a new map or the end
     * closes it, as [CloseMedia] does.
     */
    @Serializable @SerialName("media")
    data class Media(val id: String, val kind: MediaKind, val playing: Boolean = false, val at: Double = 0.0) : AudienceCommand

    /**
     * Plays the video on screen, or pauses it, where it is. Screens from 0.4.0 on play a live photo's
     * motion through once for it, or settle it back on its picture: the app sends it for a live photo
     * shown over the map as the presenter plays it again.
     */
    @Serializable @SerialName("playback")
    data class Playback(val playing: Boolean) : AudienceCommand

    /** Moves the video on screen to `at` seconds from its start. */
    @Serializable @SerialName("seek")
    data class Seek(val at: Double) : AudienceCommand

    /** Back to the map from the picture or video on screen. */
    @Serializable @SerialName("closeMedia")
    data object CloseMedia : AudienceCommand

    /** The presentation ended: the screen goes back to waiting. */
    @Serializable @SerialName("end")
    data object End : AudienceCommand
}

/**
 * A whole number a map writes (how many levels it shows, an idea's priority,
 * the version of a kind of idea), read from any number the page sends. Orbit
 * takes any number for these (`maxDepth: 1.5`, `priority: 1e12`,
 * `type: goal@99999999999`), and one an Int can't hold would lose the whole
 * message: a map that never gets ready, or a save with its edit. The page
 * sends each as the whole number nearest what it means (web/whole-numbers.ts);
 * any other is rounded up, as a level or a version past a whole one counts
 * as the next, and one past an Int's range is the nearest end of it.
 */
internal object MapWholeNumberSerializer : KSerializer<Int> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("com.hyuchia.orbit.page.MapWholeNumber", PrimitiveKind.INT)

    override fun deserialize(decoder: Decoder): Int {
        val json = decoder as? JsonDecoder ?: return decoder.decodeInt()
        val number = (json.decodeJsonElement() as? JsonPrimitive)?.takeIf { !it.isString }?.doubleOrNull
            ?: throw SerializationException("A map's whole number is a number")
        // Double.toInt() takes a number past an Int's range to the nearest end of it.
        return ceil(number).toInt()
    }

    override fun serialize(encoder: Encoder, value: Int) {
        encoder.encodeInt(value)
    }
}

/**
 * How the app and the pages read each other. `ignoreUnknownKeys` carries
 * weight: every message from the map's page names its map (`map`, which
 * [OrbitPage] reads before decoding) outside the message classes, and a
 * newer page may say more than this app knows. ProtocolTest checks both.
 */
internal val protocolJson = Json {
    ignoreUnknownKeys = true
    explicitNulls = true
    encodeDefaults = true
}
