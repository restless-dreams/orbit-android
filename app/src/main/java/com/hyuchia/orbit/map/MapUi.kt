package com.hyuchia.orbit.map

import android.content.res.Resources
import android.os.SystemClock
import androidx.annotation.PluralsRes
import androidx.annotation.StringRes
import com.hyuchia.orbit.data.MapVersion
import com.hyuchia.orbit.media.GalleryItem
import com.hyuchia.orbit.page.Diagnostic
import com.hyuchia.orbit.page.MapNode
import com.hyuchia.orbit.page.MapState
import com.hyuchia.orbit.page.ViewMode
import com.hyuchia.orbit.present.ShownMedia
import com.hyuchia.orbit.study.AnswersUnsaved
import com.hyuchia.orbit.study.Study

/** What sits over the map: one menu or panel at a time. */
sealed interface Panel {
    /** The long-press menu, at (`x`, `y`) dp from the map's top left. */
    data class Menu(val id: String, val x: Float, val y: Float) : Panel

    /**
     * The idea editor, on idea `id`: `opened` tells each time it opens on an
     * idea from the next, and stays as new text gives the idea a new id.
     * `afterSketch`: it's back from a sketch begun in it.
     */
    data class Editor(val id: String, val opened: Int = 0, val afterSketch: Boolean = false) : Panel

    data class Details(val id: String) : Panel
    data object Outline : Panel
    data object Search : Panel
    data object Style : Panel
    data object Export : Panel
    data object History : Panel
}

/**
 * Whether the panel changes the map: its menu of changes, the editor and
 * the style. A map that can only be looked at opens none of them, and
 * closes the one that's open as it becomes so.
 */
val Panel.changesTheMap: Boolean get() = this is Panel.Menu || this is Panel.Editor || this is Panel.Style

/**
 * The version history, while it's open: the versions kept (null while
 * they're listed), and the one on the map (null: the map as it is now).
 */
data class HistoryUi(
    val versions: List<MapVersion>? = null,
    val showing: Long? = null,

    /** Versions that couldn't be read when they were chosen: gone as the history was thinned, or a file that can't be read. */
    val unreadable: Set<Long> = emptySet(),

    /** What each version holds or changed, as its row asked for it and it was worked out (see [VersionSummaries]). */
    val summaries: Map<Long, VersionSummary> = emptyMap(),

    /** What the map as it is now changed from its newest version (or, with none to compare, holds), once worked out. */
    val now: VersionSummary? = null,
)

/** The map as text, while the source editor is open. */
data class SourceState(
    val text: String,
    val yaml: Boolean = false,
    val diagnostics: List<Diagnostic> = emptyList(),

    /** Converting between markdown and YAML. */
    val converting: Boolean = false,
)

/**
 * A presentation: the map's ideas along its path, or in reading order, one
 * at a time, with each note as its caption unless `captions` is off.
 * `startedAt` is [SystemClock.elapsedRealtime] when it began, for the
 * presenter's clock.
 */
data class Presentation(
    val order: List<String>,
    val index: Int,
    val playing: Boolean = false,
    val captions: Boolean = true,
    val startedAt: Long = SystemClock.elapsedRealtime(),

    /** When the idea on screen came up. */
    val stepStartedAt: Long = startedAt,

    /** A rehearsal: each idea's time is kept, for the pace of the next presentation. */
    val rehearsing: Boolean = false,

    /** Seconds the last rehearsal spent on each idea, when there was one. */
    val planned: Map<String, Int>? = null,

    /** Where the presentation began, for its pace. */
    val firstIndex: Int = index,

    /** The idea's picture or video the audience sees big over the map, and where a video is. */
    val media: ShownMedia? = null,
) {
    val current: String get() = order[index]
    val next: String? get() = order.getOrNull(index + 1)

    /** Seconds spent on the idea on screen, at `now` ([SystemClock.elapsedRealtime]). */
    fun onStep(now: Long): Int = ((now - stepStartedAt) / 1_000).toInt()

    /** Seconds the last rehearsal spent on each step, when there was one (see [stepSeconds]). */
    private val plannedSteps: List<Int?>? by lazy { planned?.let { stepSeconds(order, it) } }

    /** Seconds the last rehearsal spent on the idea on screen, if it got to it. */
    val plannedForStep: Int? get() = plannedSteps?.get(index)

    /**
     * How far behind the last rehearsal's pace the presentation is at `now`,
     * in seconds; ahead when negative, null without a rehearsal. An idea
     * only puts the presenter behind once it runs over its rehearsed time.
     */
    fun behind(now: Long): Int? {
        val planned = plannedSteps?.takeIf { steps -> steps.any { it != null } } ?: return null
        val plannedBefore = (firstIndex until index).sumOf { planned[it] ?: 0 }
        val plannedNow = plannedBefore + minOf(onStep(now), planned[index] ?: 0)
        return ((now - startedAt) / 1_000).toInt() - plannedNow
    }
}

/**
 * The seconds for each step of the path `order`, from seconds by idea: an
 * idea the path comes back to shares its seconds among its visits, the
 * first ones taking any left over. Null for an idea without seconds.
 */
internal fun stepSeconds(order: List<String>, seconds: Map<String, Int>): List<Int?> {
    val visits = order.groupingBy { it }.eachCount()
    val visited = HashMap<String, Int>()

    return order.map { idea ->
        val total = seconds[idea] ?: return@map null
        val count = visits.getValue(idea)
        val visit = visited.getOrDefault(idea, 0)
        visited[idea] = visit + 1
        total / count + if (visit < total % count) 1 else 0
    }
}

/**
 * How a rehearsal went: each step it came to, in the path's order, and the
 * seconds spent on it. An idea the path comes back to has a step for each
 * visit, with that visit's own time.
 */
data class Rehearsal(val steps: List<Step>) {
    /** A step of the rehearsal: its idea, and the seconds spent on it. */
    data class Step(val idea: String, val seconds: Int)

    val total: Int get() = steps.sumOf { it.seconds }

    /** The seconds spent on each idea, its visits together, by id: the pace of the next presentation. */
    val seconds: Map<String, Int> get() = steps.groupingBy { it.idea }.fold(0) { sum, step -> sum + step.seconds }

    /**
     * The times to keep once this rehearsal of a map with `ideas` is over:
     * its own, over the `earlier` ones. An idea the map no longer has leaves
     * its time behind, but only once the map was read whole (`readWhole`):
     * one Orbit couldn't read all of, or a newer Orbit made, may show only
     * some of its ideas, or give them other ids, so the others keep theirs
     * for when it's read whole, as study mode keeps their cards.
     */
    fun keptOver(earlier: Map<String, Int>, ideas: Set<String>, readWhole: Boolean): Map<String, Int> =
        (if (readWhole) earlier.filterKeys { it in ideas } else earlier) + seconds

    companion object {
        /** The rehearsal of the path `order`, from the milliseconds spent on its steps, by place; steps it never came to are left out. */
        fun of(order: List<String>, milliseconds: Map<Int, Long>): Rehearsal =
            Rehearsal(order.indices.filter { it in milliseconds }.map { place -> Step(order[place], ((milliseconds.getValue(place) + 500) / 1_000).toInt()) })
    }
}

/**
 * A video on its way into the map from the phone, for idea `nodeId`: how far
 * along the copy is, from 0 to 1, or null while that isn't known; and, while
 * it's made smaller, how big it was and about how big it will be.
 */
data class VideoCopy(val nodeId: String, val progress: Float? = null, val smaller: SmallerSizes? = null)

/** A video being made smaller: `from` bytes, to about `about`. */
data class SmallerSizes(val from: Long, val about: Long)

/** A sound on its way into the map from the phone, for idea `nodeId`, as [VideoCopy] is for a video. */
data class SoundCopy(val nodeId: String, val progress: Float? = null)

/** Files on their way into the map from the phone, for idea `nodeId`: the one being copied (`name`), how far along it is, and how many are left after it. */
data class FileCopy(val nodeId: String, val name: String, val progress: Float? = null, val left: Int = 0)

/**
 * Text for the screen to show, read from the app's strings as it's shown, so
 * it's in the app's language: one of its strings, one that changes with a
 * count, or words that come already written, as the page's do.
 */
sealed interface ShownText {
    /** One of the app's strings, with what goes in its placeholders. */
    data class Resource(@StringRes val id: Int, val arguments: List<Any> = emptyList()) : ShownText

    /** One of the app's strings for a count, in the form the language uses for it. */
    data class Plural(@PluralsRes val id: Int, val count: Int, val arguments: List<Any> = listOf(count)) : ShownText

    /** Words that come already written, such as the page's own. */
    data class Plain(val words: String) : ShownText

    /** Several texts in a line: "Added “Flights” · Removed “Food”". */
    data class Joined(val parts: List<ShownText>, val separator: String = " · ") : ShownText

    /** The text, in the language of `resources`; arguments that are texts themselves are written in it first. */
    fun text(resources: Resources): String = when (this) {
        is Resource -> if (arguments.isEmpty()) resources.getString(id) else resources.getString(id, *written(arguments, resources))
        is Plural -> resources.getQuantityString(id, count, *written(arguments, resources))
        is Plain -> words
        is Joined -> parts.joinToString(separator) { it.text(resources) }
    }

    private fun written(arguments: List<Any>, resources: Resources): Array<Any> = arguments.map { if (it is ShownText) it.text(resources) else it }.toTypedArray()
}

/** Why a picture, video, sound or file from the phone didn't join idea `nodeId`, for its editor to say. */
data class MediaProblem(val nodeId: String, val video: Boolean, val message: ShownText, val sound: Boolean = false, val file: Boolean = false)

/** What came of a big video joining idea `nodeId`, said in its editor while that's open: made smaller, or kept as it is. */
data class VideoNote(val nodeId: String, val message: ShownText)

/** The map's pictures and videos full screen, as they were when the viewer opened, from the one at `start`. */
data class Viewing(val items: List<GalleryItem>, val start: Int)

/** Why a map can only be looked at here. */
enum class ReadOnly {
    /** Orbit couldn't read all of the map's text: changing it would lose what wasn't read. */
    Unreadable,

    /** A newer version of Orbit made the map, maybe with things this one doesn't know. */
    Newer,

    /** The map went to the trash while it was open. */
    Deleted,

    /** A newer version of Orbit keeps the app's files. */
    Storage,
}

data class MapUi(
    val ready: Boolean = false,

    /** The page has drawn the map since it was ready: the map fades in then, rather than over an empty surface first. */
    val drawn: Boolean = false,

    /** Why the page can't draw the map until it loads again, in a new page: its process stopped, or it didn't load. */
    val pageProblem: PageProblem? = null,

    val error: ShownText? = null,
    val map: MapState? = null,

    /** The field for typing on the map is open. */
    val editing: Boolean = false,

    val canUndo: Boolean = false,
    val canRedo: Boolean = false,
    val panel: Panel? = null,
    val source: SourceState? = null,
    val presentation: Presentation? = null,

    /** Choosing the presentation's path: each new idea flown to joins it. */
    val pathMaking: Boolean = false,

    /** The ideas to present, in order; empty presents them all in reading order. */
    val presentPath: List<String> = emptyList(),

    /** The rehearsal just finished, until its summary is closed. */
    val rehearsal: Rehearsal? = null,

    /** Studying the map's cards (study/), while a session is open. */
    val study: Study? = null,

    /** The node a search or the outline just flew to, to point out on the map. */
    val found: String? = null,

    /** Goes up with every change to the map, for previews of it. */
    val revision: Int = 0,

    /** The version history, while it's open. */
    val history: HistoryUi? = null,

    /** Why the map can only be looked at, when it can't be changed. */
    val readOnly: ReadOnly? = null,

    /** The map's latest changes couldn't be written (the device's storage is full, say): they're kept here, and saved once they can be. */
    val unsaved: Boolean = false,

    /** Answers given in study mode couldn't be written, and why: they're kept while Orbit runs, and tried again until they are. */
    val answersUnsaved: AnswersUnsaved? = null,

    /** What Orbit couldn't read, in its words, when that's why. */
    val readProblem: String? = null,

    /** A video being copied in from the phone. */
    val videoCopy: VideoCopy? = null,

    /** Why the last picture or video from the phone didn't join its idea. */
    val mediaProblem: MediaProblem? = null,

    /** What came of the last big video chosen for an idea in its editor: said under it while the editor's open. */
    val videoNote: VideoNote? = null,

    /** The idea whose picture just came from an iPhone, without the movie that would make it a live photo: its editor offers to add it. */
    val iphoneStill: String? = null,

    /** The map's pictures and videos, full screen. */
    val viewing: Viewing? = null,

    /** The video playing full screen, over the pictures when it was started from them. */
    val playing: GalleryItem? = null,

    /** A sound being copied in from the phone. */
    val soundCopy: SoundCopy? = null,

    /** Files being copied in from the phone. */
    val fileCopy: FileCopy? = null,

    /** The idea a voice memo is being recorded for, while the recorder is open. */
    val recording: String? = null,

    /** Boxes of notes' checklists just checked or unchecked, by idea, then by line, shown so until the map's notes say so. */
    val noteChecks: Map<String, Map<Int, Boolean>> = emptyMap(),

    /** Sketching on an idea's planet, from asking until the map is back as it was (sketch/). */
    val sketch: SketchUi? = null,
) {
    /** Whether a picture or video is full screen on this phone. */
    val fullScreen: Boolean get() = viewing != null || playing != null

    /** Whether an earlier version from the history is on the map: it's only looked at, and takes no changes. */
    val showingVersion: Boolean get() = history?.showing != null

    /** Whether Undo takes a change back now: never on a map that can only be looked at, which takes no change. */
    val undoable: Boolean get() = canUndo && readOnly == null

    /** Whether Redo makes a change again now: never on a map that can only be looked at. */
    val redoable: Boolean get() = canRedo && readOnly == null

    /**
     * Whether a press on the map can lead to an edit, its menu of changes:
     * not while a path is made or the map is presented or studied (a
     * keyboard's menu key still reaches the page then), on a map that can't
     * be changed, or on an earlier version.
     */
    val takesEdits: Boolean get() = !pathMaking && presentation == null && study == null && readOnly == null && !showingVersion && sketch == null

    /** Whether a planet is up close to sketch on, the map's own bars and buttons stepping aside for the sketch's. */
    val sketching: Boolean get() = sketch != null && sketch.phase != SketchPhase.Reading && sketch.phase != SketchPhase.Asking

    val nodes: Map<String, MapNode> by lazy { map?.nodes?.associateBy { it.id }.orEmpty() }

    /** How the map's progress rings count, and which ideas show one. */
    val progress: MapProgress by lazy { MapProgress.of(map) }

    /** Whether the idea's details have something to show beyond its title: its ring's progress counts. */
    fun hasDetails(node: MapNode): Boolean = node.hasDetails || progress.followsItsIdeas(node)

    /** Whether any idea has a picture or a video. */
    val hasMedia: Boolean by lazy { map?.nodes?.any { it.image != null || it.video != null } == true }

    /** Whether any idea has a sound. */
    val hasSounds: Boolean by lazy { map?.nodes?.any { it.audio != null } == true }

    val path: List<MapNode> by lazy { map?.path?.mapNotNull { nodes[it] }.orEmpty() }
    val sun: MapNode? get() = path.lastOrNull()
    val mode: String get() = map?.mode ?: ViewMode.FOCUS

    /** Whether the map shows one idea with its orbit, rather than the whole map. */
    val focused: Boolean get() = mode == ViewMode.FOCUS
}

/**
 * Where a sketch is: reading the idea's picture, to tell a sketch to draw on
 * further from another picture; asking first whether it may replace that
 * one; the map flying to the planet, drawing on it, describing it as it's
 * done, keeping it, or the map flying back.
 */
enum class SketchPhase { Reading, Asking, Opening, Drawing, Describing, Saving, Closing }

/** Where a planet is on the map's page while it's sketched on: its middle, in dp from the page's top left, and its radius. */
data class PlanetCircle(val x: Float, val y: Float, val radius: Float)

/**
 * Sketching on idea `nodeId`'s planet: where it is, its circle on the
 * page once the page holds it up close, the color the map dims with around
 * it (ARGB), whether the sketch takes the place of the idea's picture, and
 * whether the person is asked to let go of strokes drawn.
 */
data class SketchUi(
    val nodeId: String,
    val phase: SketchPhase = SketchPhase.Opening,
    val circle: PlanetCircle? = null,
    val scrim: Int? = null,
    val replacing: Boolean = false,
    val discarding: Boolean = false,

    /** The description the sketch starts with: its picture's, when it's drawn on further. */
    val description: String = "",

    /** Whether the person is asked, as Done is tapped, to let the sketch take the place of a picture that landed while they drew. */
    val confirming: Boolean = false,

    /**
     * Whether the page that drew the map stopped, or didn't load: the drawing
     * waits, whole, with no planet up close, until a page draws the map again
     * and brings it back up (see [Sketching.pageStopped]).
     */
    val held: Boolean = false,
)

/** One-off things the screen does for the map. */
sealed interface MapEvent {
    data object Tick : MapEvent
    data object LongPress : MapEvent
    data object Added : MapEvent

    /** An idea dragged somewhere else landed there. */
    data object Moved : MapEvent

    /** A goal just got where it was going. */
    data object GoalReached : MapEvent

    /** A message; with `undo`, it offers to undo the last change, or to run `onUndo` instead when there is one. */
    data class Snackbar(val message: ShownText, val undo: Boolean = false, val onUndo: (() -> Unit)? = null) : MapEvent

    /** An edit went into the history rather than over a change from elsewhere: said, with the way to the history. */
    data object KeptInHistory : MapEvent

    data class Open(val url: String) : MapEvent
    data object Deleted : MapEvent

    /** The presentation stopped as private maps locked, and they're unlocked now: presenting again is offered. */
    data object PresentAgain : MapEvent
}
