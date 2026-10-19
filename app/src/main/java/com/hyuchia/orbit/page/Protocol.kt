package com.hyuchia.orbit.page

import com.hyuchia.orbit.media.MediaKind
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

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
)

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
    val priority: Int? = null,
    val tags: List<String> = emptyList(),
    val collapsed: Boolean = false,
    val type: String? = null,
    val link: String? = null,
    val note: String? = null,
    val noteFormat: String? = null,
    val image: Media? = null,
    val video: Media? = null,
    val embed: Media? = null,

    /** Attributes Orbit doesn't use itself, as they were read: a goal's numbers, or another app's. */
    val attrs: JsonObject? = null,
) {
    val isRoot: Boolean get() = parentId == null

    /** A goal idea, which tracks a number toward a target (see `map/Goal.kt`). */
    val isGoal: Boolean get() = type?.trim().equals("goal", ignoreCase = true)

    /** The node's text with its icon, as the map shows it, with `untitled` for text it doesn't have. */
    fun label(untitled: String): String = listOfNotNull(icon, text.ifBlank { untitled }).joinToString(" ")

    /** Whether the details sheet has something to show beyond the title. */
    val hasDetails: Boolean
        get() = !note.isNullOrBlank() || image != null || video != null || embed != null || link != null ||
            tags.isNotEmpty() || status != null || priority != null || isGoal
}

/** The map's frontmatter options the app shows and changes. */
@Serializable
data class MapOptions(
    val theme: String? = null,
    val palette: List<String>? = null,
    val showRings: Boolean? = null,
    val orbitAnimation: Boolean? = null,
    val orbitSpeed: Double? = null,
    val maxDepth: Int? = null,
    val viewMode: String? = null,

    /** The app's own: `false` when ideas with to-dos or goals don't show progress rings by themselves. */
    val progressRings: Boolean? = null,

    /** The app's own: the map's tags, which Home groups maps by (see `data/MapTags.kt`). */
    val tags: List<String>? = null,
)

/** The whole map, as the page reports it after every change. */
@Serializable
data class MapState(
    val title: String,

    /** Every node, the root first, each followed by its children. */
    val nodes: List<MapNode>,

    val options: MapOptions = MapOptions(),

    /** Ids from the root to the node in focus. */
    val path: List<String> = emptyList(),

    /** `focus` or `all`. */
    val mode: String = "focus",

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

/** An idea caught on the go: its text, and the whole of it as its note when the text was cut short. */
@Serializable
data class CapturedIdea(val text: String, val note: String? = null)

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
     * (`restore`). `renamed` says when the app's change gave a node a new id.
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

    /** The system stopped the page's process (to free memory, say): the map has to load again. */
    @Serializable @SerialName("stopped")
    data object Stopped : MapMessage

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
    ) : MapCommand

    /** Clears the page for the next map. */
    @Serializable @SerialName("unload")
    data object Unload : MapCommand

    @Serializable @SerialName("theme")
    data class Theme(val theme: AppTheme) : MapCommand

    @Serializable @SerialName("settings")
    data class Settings(val settings: MapSettings) : MapCommand

    @Serializable @SerialName("insets")
    data class Insets(val insets: MapInsets) : MapCommand

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
     */
    @Serializable @SerialName("update")
    data class Update(val id: String, val changes: JsonObject) : MapCommand

    /**
     * Adds `amount` to goal `id`'s current number, or takes it off when it's
     * below 0. The page adds it to the number it has, so quick steps all count.
     */
    @Serializable @SerialName("addToGoal")
    data class AddToGoal(val id: String, val amount: JsonPrimitive) : MapCommand

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
    data class Restore(val markdown: String) : MapCommand

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

        val viewMode: String? = null,
        val focus: String? = null,
        val baseUrl: String? = null,
    ) : MapCommand

    /** Converts a map's text to orbit-md (`markdown`) or `yaml`. */
    @Serializable @SerialName("convert")
    data class Convert(val job: Int, val text: String, val to: String) : MapCommand

    /** Reads a map's text: its title, size, diagnostics, and the map as orbit-md. */
    @Serializable @SerialName("check")
    data class Check(val job: Int, val text: String) : MapCommand

    /**
     * Adds `ideas` to a map's text, under `parentId` or its top, and answers
     * with the text as Orbit writes it. Fails for a map Orbit can't read all
     * of, or one a newer Orbit made.
     */
    @Serializable @SerialName("addIdeas")
    data class AddIdeas(val job: Int, val markdown: String, val ideas: List<CapturedIdea>, val parentId: String? = null) : MapCommand

    /** An idea and everything under it, answered as a [CopiedIdea]. */
    @Serializable @SerialName("copy")
    data class Copy(val job: Int, val id: String) : MapCommand

    /**
     * Text pasted as ideas under `parentId`, after its last (web/paste.ts):
     * one change, one step to undo. Answered with [PastedIdeas].
     */
    @Serializable @SerialName("paste")
    data class Paste(val job: Int, val parentId: String, val text: String) : MapCommand
}

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

    /** Plays the video on screen, or pauses it, where it is. */
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
