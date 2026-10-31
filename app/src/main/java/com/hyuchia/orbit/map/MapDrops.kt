package com.hyuchia.orbit.map

import android.content.ClipData
import android.content.ClipDescription
import android.content.ContentResolver
import android.view.DragEvent
import android.view.View
import androidx.annotation.StringRes
import androidx.core.net.toUri
import com.hyuchia.orbit.Logs
import com.hyuchia.orbit.R
import com.hyuchia.orbit.Words
import com.hyuchia.orbit.data.AddedMotion
import com.hyuchia.orbit.data.AddedPicture
import com.hyuchia.orbit.data.DropKind
import com.hyuchia.orbit.data.DroppedItem
import com.hyuchia.orbit.data.DroppedText
import com.hyuchia.orbit.data.ImportResult
import com.hyuchia.orbit.data.Importer
import com.hyuchia.orbit.data.LiveHalf
import com.hyuchia.orbit.data.LivePairs
import com.hyuchia.orbit.data.MapRepository
import com.hyuchia.orbit.data.MediaRefusedException
import com.hyuchia.orbit.data.dropKinds
import com.hyuchia.orbit.data.droppedItems
import com.hyuchia.orbit.data.droppedText
import com.hyuchia.orbit.data.fromProvider
import com.hyuchia.orbit.data.ideaName
import com.hyuchia.orbit.media.NATIVE_PROVIDER
import com.hyuchia.orbit.page.DropPlace
import com.hyuchia.orbit.page.MapCommand
import com.hyuchia.orbit.page.MapNode
import com.hyuchia.orbit.page.MapPage
import com.hyuchia.orbit.page.Media
import com.hyuchia.orbit.page.PageException
import com.hyuchia.orbit.page.PastedIdeas
import com.hyuchia.orbit.page.ViewMode
import com.hyuchia.orbit.page.findActivity
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.abs

/** What a drag over the map would do there, as the hint over the map says. */
enum class DropOffer { Ideas, Pictures, Videos, Sounds, Maps, Move }

/**
 * The hint over the map while something is dragged onto it: what letting go
 * does, and into which idea. `high` says the finger is in the map's upper
 * part, where the hint would cover what it points at: it moves to the bottom.
 */
data class DropHint(val offer: DropOffer, val target: String?, val high: Boolean = false)

/** Pictures, videos and sounds let go on the map, while they're copied in: how many of how many, and how far along a video or a sound is. */
data class DropCopy(val target: String, val done: Int, val total: Int, val video: Boolean, val progress: Float? = null, val sound: Boolean = false)

/** An idea dragged out of Orbit, from its map or its outline: a drop back on the same map moves it. */
data class OutgoingIdea(val mapId: String, val id: String)

/** A picture and a movie let go together that may be an iPhone's live photo, with nothing in them to say so: the person is asked. */
data class LiveOffer(val picture: String?, val movie: String?)

/** What a drop on a map does, from what it holds. */
sealed interface DropPlan {
    /** Map files open as maps of their own, as "Open a file" opens them. */
    data class OpenMaps(val files: List<DroppedItem.MapFile>) : DropPlan

    /** One picture, video or sound: it becomes the idea's. */
    data class Media(val item: DroppedItem) : DropPlan

    /** Several pictures, videos and sounds: each a new idea under the idea, with it. */
    data class NewIdeas(val items: List<DroppedItem>) : DropPlan

    /** Text, and files of it: ideas under the idea, as pasting makes them. */
    data class Ideas(val items: List<DroppedItem>) : DropPlan

    data object Nothing : DropPlan
}

/**
 * What a drop on a map does with what it holds: map files open as maps,
 * whatever else came with them; one picture, video or sound becomes the
 * idea's, several become ideas of their own; text becomes ideas. Text that
 * comes with pictures, such as a picture's address from a browser, stays out.
 */
fun planDrop(items: List<DroppedItem>): DropPlan {
    val maps = items.filterIsInstance<DroppedItem.MapFile>()
    val media = items.filter { it is DroppedItem.Picture || it is DroppedItem.Video || it is DroppedItem.Sound || it is DroppedItem.LivePhoto }
    val text = items.filter { it is DroppedItem.Text || it is DroppedItem.TextFile }

    return when {
        maps.isNotEmpty() -> DropPlan.OpenMaps(maps)
        media.size == 1 -> DropPlan.Media(media.single())
        media.isNotEmpty() -> DropPlan.NewIdeas(media)
        text.isNotEmpty() -> DropPlan.Ideas(text)
        else -> DropPlan.Nothing
    }
}

/** What the hint says a drag offering `kinds` would do: map files open as maps, pictures, videos and sounds join ideas, text becomes ideas. */
fun dropOffer(kinds: Set<DropKind>): DropOffer? = when {
    DropKind.Pictures in kinds -> DropOffer.Pictures
    DropKind.Videos in kinds -> DropOffer.Videos
    DropKind.Sounds in kinds -> DropOffer.Sounds
    DropKind.Ideas in kinds -> DropOffer.Ideas
    DropKind.Maps in kinds -> DropOffer.Maps
    else -> null
}

/** Whether idea `id` is `ancestor` itself or somewhere under it, among `nodes`. */
fun isWithin(nodes: Map<String, MapNode>, id: String, ancestor: String): Boolean =
    generateSequence(nodes[id]) { node -> node.parentId?.let(nodes::get) }.any { it.id == ancestor }

/** The most text a file let go on a map may hold to become ideas: a list, not a book. A longer one is refused, never cut. */
private const val TEXT_FILE_LIMIT = 512 * 1024

/** The finger moves this far (dp) before the page hears where it is again: a drag's every jitter would be a message. */
private const val MOVE_STEP = 1.5f

/** In this upper share of the map, the finger is where the hint shows: it moves to the bottom. */
private const val HIGH_SHARE = 0.4f

/**
 * Whether a map shown as `state` takes drops: drawn, changeable, not behind
 * its lock (`locked`), and not presented, studied, made into a path or shown
 * at an earlier version.
 */
fun takesDrops(state: MapUi, locked: Boolean): Boolean =
    state.ready && !locked && state.readOnly == null && state.presentation == null && state.study == null && !state.pathMaking && !state.showingVersion

/**
 * Drops on a map from other apps: text, web addresses, pictures, videos and
 * other apps' maps, and ideas of the map dragged out and back. While a drag
 * is over the map, the idea under the finger lights up and the hint says
 * what letting go would do; let go, the drop goes through the same changes
 * pasting makes, with one Undo. A drop on a map that can't be changed, is
 * behind its lock, or while it's presented or a path is made, is refused
 * before it starts.
 */
class MapDrops(
    private val scope: CoroutineScope,
    private val mapId: String,
    private val maps: MapRepository,

    /** Brings in maps from files let go on the map, when there are some. */
    private val importer: () -> Importer,

    private val words: Words,
    private val ui: StateFlow<MapUi>,
    private val locked: StateFlow<Boolean>,
    private val page: () -> MapPage,
    private val update: (String, String, JsonElement?) -> Unit,
    private val tell: (MapEvent) -> Unit,
) {
    private val hintState = MutableStateFlow<DropHint?>(null)
    private val copyState = MutableStateFlow<DropCopy?>(null)
    private val offerState = MutableStateFlow<LiveOffer?>(null)
    private var offerAnswer: CompletableDeferred<Boolean>? = null
    private val opened = Channel<ImportResult>(Channel.BUFFERED)
    private val leaving = Channel<String>(Channel.CONFLATED)

    /** What a drag over the map would do there, while one is over it. */
    val hint: StateFlow<DropHint?> = hintState.asStateFlow()

    /** Pictures and videos let go on the map, while they're copied in. */
    val copying: StateFlow<DropCopy?> = copyState.asStateFlow()

    /** Maps opened from files let go on the map: the screen shows them as "Open a file" would. */
    val openedMaps: Flow<ImportResult> = opened.receiveAsFlow()

    /** A picture and a movie that may be one live photo, while the person is asked whether they are. */
    val liveOffer: StateFlow<LiveOffer?> = offerState.asStateFlow()

    /** The person's answer to [liveOffer]: join them as one live photo, or keep them apart. */
    fun answerLiveOffer(join: Boolean) {
        offerAnswer?.complete(join)
        offerAnswer = null
        offerState.value = null
    }

    /** Ideas dragged out of the window on the map: the screen starts the drag other apps can take. */
    val outgoing: Flow<String> = leaving.receiveAsFlow()

    /** What the drag going on offers the map; null while there's none it takes. */
    private var offer: DropOffer? = null

    private var lastPoint: Pair<Float, Float>? = null
    private var copyJob: Job? = null

    /** Whether one of this map's ideas is out as a drag that started here, until it's let go. */
    private var out = false

    private fun untitled(): String = words.string(R.string.common_untitled)

    private fun label(id: String?): String = id?.let { ui.value.nodes[it] }?.label(untitled()) ?: untitled()

    private fun open(): Boolean = takesDrops(ui.value, locked.value)

    /**
     * A drag started somewhere: whether the map takes it. Ideas typed into on
     * the map take text the way any text field does, so the map leaves those
     * drags to the page.
     */
    fun accepts(description: ClipDescription?, localState: Any?): Boolean {
        // An idea of Orbit's: from this map's page itself, or from its outline, whose sheet is a window of its own.
        val own = (localState as? OutgoingIdea)?.mapId == mapId || isOrbitIdea(description)
        offer = if (own) DropOffer.Move else dropOffer(dropKinds(description))
        return offer != null && open() && !ui.value.editing
    }

    fun entered() {
        val offer = offer ?: return
        hintState.value = DropHint(offer, target = null)
    }

    /** The drag is over the map at (`x`, `y`) dp from its top left, in a map `height` dp tall. */
    fun moved(x: Float, y: Float, height: Float) {
        if (offer == null) {
            return
        }

        hintState.update { hint -> hint?.copy(high = y < height * HIGH_SHARE) }

        if (offer == DropOffer.Maps) {
            return
        }

        val last = lastPoint

        if (last != null && abs(last.first - x) < MOVE_STEP && abs(last.second - y) < MOVE_STEP) {
            return
        }

        lastPoint = x to y
        page().send(MapCommand.DropOver(x, y))
    }

    fun exited() {
        lastPoint = null
        hintState.value = null
        page().send(MapCommand.DropLeave)
    }

    fun ended() {
        offer = null
        out = false
        exited()
    }

    /**
     * The map locked: a drag over it goes out, its light with it, and a drop
     * there won't take. Returns whether one of its ideas was out as a drag,
     * which the screen then calls back.
     */
    fun letGo(): Boolean {
        val wasOut = out
        out = false

        if (offer != null) {
            offer = null
            exited()
        }

        return wasOut
    }

    /** Whether an idea may leave the map as a drag now: a private map's ideas leave only while it's open, and none while it's studied. */
    val canDragOut: Boolean get() = !locked.value && ui.value.study == null

    /** One of the map's ideas went out as a drag, from the map or its outline. */
    fun wentOut() {
        out = true
    }

    /** The page lit up idea `id` for the drag, or put the light out: the hint follows, with a tick for each new idea. */
    fun targeted(id: String?) {
        hintState.update { hint -> hint?.copy(target = id) }

        if (id != null && hintState.value != null) {
            tell(MapEvent.Tick)
        }
    }

    /** An idea dragged on the map went out of the window: it goes on as a drag another app can take. */
    fun draggedOut(id: String) {
        if (canDragOut) {
            leaving.trySend(id)
        }
    }

    /**
     * Let go at (`x`, `y`) dp: the map adds what the drag holds to the idea
     * there. Files are read with the permissions the drag carries, held
     * until they're in. Returns whether the map took the drop.
     */
    fun dropped(view: View, event: DragEvent, x: Float, y: Float): Boolean {
        val offer = offer
        hintState.value = null
        lastPoint = null

        if (offer == null || !open()) {
            page().send(MapCommand.DropLeave)
            return false
        }

        val clip = event.clipData ?: return false
        // An idea of another map goes in as its outline, as text from any app does.
        val own = ((event.localState as? OutgoingIdea) ?: outgoingIdea(clip))?.takeIf { it.mapId == mapId }
        // Lending the drag's files goes across processes: when it fails, they're read without, and say they couldn't be.
        val permissions = fromProvider { view.context.findActivity()?.requestDragAndDropPermissions(event) }
        val resolver = view.context.contentResolver

        scope.launch {
            try {
                val target = place(x, y) ?: return@launch

                if (own != null) {
                    move(own.id, target)
                } else {
                    add(clip, resolver, target)
                }
            } finally {
                permissions?.release()
            }
        }

        return true
    }

    /** The idea a drop at (`x`, `y`) goes into: the page's, or the one in focus when the page can't say. */
    private suspend fun place(x: Float, y: Float): String? {
        val answered = try {
            page().request(DropPlace.serializer()) { job -> MapCommand.DropAt(job, x, y) }.id
        } catch (failed: PageException) {
            Logs.warn(TAG, "The page couldn't say where the drop went: ${failed.message}")
            null
        }

        return answered ?: ui.value.sun?.id ?: ui.value.map?.root?.id
    }

    /** An idea of this map, dragged out and back: it moves into `target`, as dragging it on the map moves it. */
    private fun move(id: String, target: String) {
        val nodes = ui.value.nodes
        val node = nodes[id] ?: return

        when {
            isWithin(nodes, target, id) -> tell(MapEvent.Snackbar(ShownText.Resource(R.string.drop_not_into_itself)))
            node.parentId == target -> Unit
            else -> {
                page().send(MapCommand.Move(id, target, null))
                tell(MapEvent.Moved)
                tell(MapEvent.Snackbar(ShownText.Resource(R.string.map_moved_idea_into_idea, listOf(node.label(untitled()), label(target))), undo = true))
            }
        }
    }

    private suspend fun add(clip: ClipData, resolver: ContentResolver, target: String) {
        val items = withLivePhotos(withContext(Dispatchers.IO) { droppedItems(clip, resolver) }, resolver)

        when (val plan = planDrop(items)) {
            is DropPlan.OpenMaps -> openMaps(plan.files, resolver)
            is DropPlan.Media -> addMedia(plan.item, resolver, target)
            is DropPlan.NewIdeas -> addNewIdeas(plan.items, resolver, target)
            is DropPlan.Ideas -> addText(plan.items, resolver, target)
            DropPlan.Nothing -> tell(MapEvent.Snackbar(ShownText.Resource(R.string.drop_nothing_to_add)))
        }
    }

    /**
     * What was let go, with each iPhone still and its movie made one live
     * photo ([LivePairs]), by their id, their names or when they were taken.
     * A picture and a live photo's movie that nothing ties together are
     * joined only once the person says so.
     */
    private suspend fun withLivePhotos(items: List<DroppedItem>, resolver: ContentResolver): List<DroppedItem> {
        val pictures = items.filterIsInstance<DroppedItem.Picture>()
        val videos = items.filterIsInstance<DroppedItem.Video>()

        if (pictures.isEmpty() || videos.isEmpty()) {
            return items
        }

        val movies = videos.map { LiveHalf<DroppedItem>(it, it.name, video = maps.liveHalf(it.address.toUri(), resolver, movie = true).second) }

        if (movies.none { half -> half.video?.let { it.live || it.identifier != null } == true }) {
            return items
        }

        val stills = pictures.map { LiveHalf<DroppedItem>(it, it.name, still = maps.liveHalf(it.address.toUri(), resolver, movie = false).first) }
        val matched = LivePairs.match(stills, movies)
        val pairs = matched.pairs.toMutableList()

        matched.offer?.let { offer ->
            val (still, movie) = offer.first as DroppedItem.Picture to offer.second as DroppedItem.Video

            if (ask(LiveOffer(still.name, movie.name))) {
                pairs += offer
            }
        }

        val joined = pairs.associate { (still, movie) -> still to DroppedItem.LivePhoto(still as DroppedItem.Picture, movie as DroppedItem.Video) }
        val movieHalves = pairs.mapTo(HashSet()) { it.second }
        return items.mapNotNull { item -> joined[item] ?: item.takeIf { it !in movieHalves } }
    }

    /** Asks whether a picture and a movie are one live photo, and waits for the answer. */
    private suspend fun ask(offer: LiveOffer): Boolean {
        val answer = CompletableDeferred<Boolean>()
        offerAnswer?.complete(false)
        offerAnswer = answer
        offerState.value = offer
        return answer.await()
    }

    private suspend fun openMaps(files: List<DroppedItem.MapFile>, resolver: ContentResolver) {
        val results = files.map { file -> file.name to importer().importFile(file.address.toUri(), resolver) }
        droppedMaps(results, words)?.let { opened.trySend(it) }
    }

    /**
     * Text, and text files, as ideas under `target`: one paste, one Undo. A
     * text file comes whole, up to [TEXT_FILE_LIMIT]; a longer one, or one
     * that can't be read, adds nothing, saying why.
     */
    private suspend fun addText(items: List<DroppedItem>, resolver: ContentResolver, target: String) {
        val dropped = withContext(Dispatchers.IO) {
            droppedText(items, TEXT_FILE_LIMIT, words.string(R.string.drop_text_file_too_long), words) { address -> resolver.openInputStream(address.toUri()) }
        }

        val text = when (dropped) {
            is DroppedText.Text -> dropped.text
            is DroppedText.Refused -> {
                tell(MapEvent.Snackbar(ShownText.Plain(dropped.reason)))
                return
            }
        }

        if (text.isBlank()) {
            tell(MapEvent.Snackbar(ShownText.Resource(R.string.drop_nothing_to_add)))
            return
        }

        try {
            val pasted = page().request(PastedIdeas.serializer()) { job -> MapCommand.Paste(job, target, text) }

            if (pasted.count == 0) {
                tell(MapEvent.Snackbar(ShownText.Resource(R.string.drop_nothing_to_add)))
            } else {
                showIdeasOf(target)
                tell(MapEvent.Snackbar(ShownText.Plural(R.plurals.drop_added_ideas, pasted.count, listOf(pasted.count, label(target))), undo = true))
            }
        } catch (failed: PageException) {
            tell(MapEvent.Snackbar(ShownText.Plain(failed.message.orEmpty())))
        }
    }

    /** One picture, video or sound: copied into the map, and made the idea's, with Undo. */
    private suspend fun addMedia(item: DroppedItem, resolver: ContentResolver, target: String) {
        val video = item is DroppedItem.Video

        copied(target, total = 1, video = video, sound = item is DroppedItem.Sound) {
            try {
                if (item is DroppedItem.Picture) {
                    val added = maps.addPicture(mapId, item.address.toUri(), resolver)
                    update(target, "image", pictureAttribute(added))
                    tell(MapEvent.Added)
                    tell(MapEvent.Snackbar(ShownText.Resource(R.string.drop_added_the_picture, listOf(label(target))), undo = true))
                } else if (item is DroppedItem.LivePhoto) {
                    val added = maps.addLivePhoto(mapId, item.still.address.toUri(), item.movie.address.toUri(), resolver)
                    update(target, "image", pictureAttribute(added))
                    tell(MapEvent.Added)
                    tell(MapEvent.Snackbar(ShownText.Resource(R.string.drop_added_the_live_photo, listOf(label(target))), undo = true))
                } else if (item is DroppedItem.Video) {
                    val added = maps.addVideo(mapId, item.address.toUri(), resolver) { progress -> copyState.update { it?.copy(progress = progress) } }
                    val caption = ui.value.nodes[target]?.video?.caption
                    update(target, "video", videoAttribute(added.src, added.thumbnail, caption))
                    tell(MapEvent.Added)
                    tell(MapEvent.Snackbar(ShownText.Resource(R.string.drop_added_the_video, listOf(label(target))), undo = true))
                } else if (item is DroppedItem.Sound) {
                    // As one added from the phone: the idea's caption stays, else the file's name gives one.
                    val added = maps.addSound(mapId, item.address.toUri(), resolver) { progress -> copyState.update { it?.copy(progress = progress) } }
                    val caption = ui.value.nodes[target]?.audio?.caption ?: added.caption
                    update(target, "audio", soundAttribute(added.src, caption))
                    tell(MapEvent.Added)
                    tell(MapEvent.Snackbar(ShownText.Resource(R.string.drop_added_the_sound, listOf(label(target))), undo = true))
                }
            } catch (refused: MediaRefusedException) {
                tell(MapEvent.Snackbar(ShownText.Plain(refused.message.orEmpty())))
            } catch (stopped: CancellationException) {
                throw stopped
            } catch (unreadable: Exception) {
                Logs.warn(TAG, "Couldn't read what was dropped", unreadable)
                tell(MapEvent.Snackbar(ShownText.Resource(unreadableMessage(item))))
            }
        }
    }

    /**
     * Several pictures, videos and sounds: each copied into the map and made
     * a new idea under `target`, named by its file, all added as one change.
     * One that can't join, too big or unreadable, is left out, and the
     * message says why.
     */
    private suspend fun addNewIdeas(items: List<DroppedItem>, resolver: ContentResolver, target: String) {
        copied(target, total = items.size, video = items.any { it is DroppedItem.Video }, sound = items.any { it is DroppedItem.Sound }) {
            val nodes = mutableListOf<JsonObject>()
            var problem: String? = null

            items.forEachIndexed { index, item ->
                copyState.update { it?.copy(done = index + 1, progress = null) }

                try {
                    nodes += when (item) {
                        is DroppedItem.Picture -> buildJsonObject {
                            put("text", ideaName(item.name) ?: words.string(R.string.drop_picture))
                            put("image", pictureAttribute(maps.addPicture(mapId, item.address.toUri(), resolver)))
                        }
                        is DroppedItem.LivePhoto -> buildJsonObject {
                            put("text", ideaName(item.still.name) ?: words.string(R.string.drop_picture))
                            put("image", pictureAttribute(maps.addLivePhoto(mapId, item.still.address.toUri(), item.movie.address.toUri(), resolver)))
                        }
                        is DroppedItem.Video -> {
                            val added = maps.addVideo(mapId, item.address.toUri(), resolver) { progress -> copyState.update { it?.copy(progress = progress) } }

                            buildJsonObject {
                                put("text", ideaName(item.name) ?: words.string(R.string.drop_video))
                                put("video", videoAttribute(added.src, added.thumbnail, caption = null))
                            }
                        }
                        // Its idea is named by its file: the sound needs no caption of its own.
                        is DroppedItem.Sound -> {
                            val added = maps.addSound(mapId, item.address.toUri(), resolver) { progress -> copyState.update { it?.copy(progress = progress) } }

                            buildJsonObject {
                                put("text", ideaName(item.name) ?: words.string(R.string.drop_sound))
                                put("audio", soundAttribute(added.src, caption = null))
                            }
                        }
                        else -> return@forEachIndexed
                    }
                } catch (refused: MediaRefusedException) {
                    problem = problem ?: refused.message
                } catch (stopped: CancellationException) {
                    throw stopped
                } catch (unreadable: Exception) {
                    Logs.warn(TAG, "Couldn't read one of the dropped pictures, videos or sounds", unreadable)
                    problem = problem ?: words.string(unreadableMessage(item))
                }
            }

            if (nodes.isEmpty()) {
                tell(MapEvent.Snackbar(ShownText.Plain(problem ?: words.string(R.string.drop_nothing_to_add))))
                return@copied
            }

            val count = try {
                page().request(PastedIdeas.serializer()) { job -> MapCommand.AddNodes(job, target, nodes) }.count
            } catch (failed: PageException) {
                tell(MapEvent.Snackbar(ShownText.Plain(failed.message.orEmpty())))
                return@copied
            }

            showIdeasOf(target)
            val pictures = nodes.count { it.containsKey("image") }
            val videos = nodes.count { it.containsKey("video") }
            val sounds = nodes.count { it.containsKey("audio") }

            val message = when {
                problem != null -> ShownText.Plural(R.plurals.drop_added_some, count, listOf(count, items.size, label(target), problem.orEmpty()))
                pictures == nodes.size -> ShownText.Plural(R.plurals.drop_added_pictures, count, listOf(count, label(target)))
                videos == nodes.size -> ShownText.Plural(R.plurals.drop_added_videos, count, listOf(count, label(target)))
                sounds == nodes.size -> ShownText.Plural(R.plurals.drop_added_sounds, count, listOf(count, label(target)))
                sounds == 0 -> ShownText.Plural(R.plurals.drop_added_pictures_and_videos, count, listOf(count, label(target)))
                else -> ShownText.Plural(R.plurals.drop_added_files, count, listOf(count, label(target)))
            }

            tell(MapEvent.Snackbar(message, undo = true))
        }
    }

    /**
     * Ideas added under `target` show on the map: around the sun as planets,
     * and around a planet as moons. Under a moon, or deeper, they wouldn't
     * show, so the map flies into it.
     */
    private fun showIdeasOf(target: String) {
        val state = ui.value
        val sun = state.sun?.id
        val shown = state.mode == ViewMode.OVERVIEW || target == sun || state.nodes[target]?.parentId == sun

        if (!shown) {
            page().send(MapCommand.Focus(target))
        }
    }

    /**
     * Runs a copy into the map, which the hint over the map follows (once it
     * takes more than a moment: see `DropFeedback`), and [cancel] stops: what
     * isn't in by then stays out.
     */
    private suspend fun copied(target: String, total: Int, video: Boolean, sound: Boolean = false, copy: suspend () -> Unit) {
        copyJob?.cancel()

        val job = scope.launch {
            try {
                copyState.value = DropCopy(target, done = 1, total = total, video = video, sound = sound)
                copy()
            } finally {
                copyState.value = null
            }
        }

        copyJob = job
        job.join()
    }

    /** What says an item let go couldn't be read: a picture, a video or a sound. */
    @StringRes
    private fun unreadableMessage(item: DroppedItem): Int = when (item) {
        is DroppedItem.Video -> R.string.map_video_unreadable
        is DroppedItem.Sound -> R.string.audio_sound_unreadable
        else -> R.string.map_picture_unreadable
    }

    /** Stops the pictures, videos and sounds being copied in from a drop: what isn't in yet stays out. */
    fun cancel() {
        copyJob?.cancel()
    }

    private companion object {
        const val TAG = "MapDrops"
    }
}

/** A video from the phone as Orbit keeps it on an idea: played natively, with its poster. */
internal fun videoAttribute(src: String, thumbnail: String?, caption: String?): JsonObject = buildJsonObject {
    put("src", JsonPrimitive(src))
    put("provider", JsonPrimitive(NATIVE_PROVIDER))
    thumbnail?.let { put("thumbnail", JsonPrimitive(it)) }
    caption?.let { put("caption", JsonPrimitive(it)) }
}

/**
 * A picture from the phone as Orbit keeps it on an idea: its file in the
 * map's folder, and a live photo's motion with it, with where in it the
 * still was taken when the phone said so.
 */
internal fun pictureAttribute(added: AddedPicture): JsonObject = buildJsonObject {
    put("src", JsonPrimitive(added.src))
    added.motion?.let { put("motion", motionValue(it.src, it.still)) }
}

/** An idea's picture made a live photo with `motion`: only the motion goes to the page, and the picture keeps its own fields as they are. */
internal fun motionChange(motion: AddedMotion): JsonObject = buildJsonObject {
    put("motion", motionValue(motion.src, motion.still))
}

/**
 * A change to one field of an idea's own picture, video, sound or page, such
 * as its caption; null takes the field out. Only that field goes to the page,
 * which keeps the rest as they are, the fields the app doesn't know included.
 */
internal fun mediaChange(field: String, value: String?): JsonObject = buildJsonObject {
    put(field, value?.let(::JsonPrimitive) ?: JsonNull)
}

/**
 * A live photo's motion as the page takes it: always `{ src }`, with where in
 * it the still was taken to the millisecond when that's known. Orbit writes
 * the address alone in the map when that's all there is, but an idea's
 * picture holds its motion as an object, and a bare address would be lost.
 */
internal fun motionValue(src: String, still: Double?): JsonObject = buildJsonObject {
    put("src", JsonPrimitive(src))
    still?.let { put("still", JsonPrimitive(Math.round(it * 1000) / 1000.0)) }
}

/** A sound from the phone as Orbit keeps it on an idea: its file in the map's folder, and its caption, when it has one. */
internal fun soundAttribute(src: String, caption: String?): JsonObject = buildJsonObject {
    put("src", JsonPrimitive(src))
    caption?.let { put("caption", JsonPrimitive(it)) }
}

/**
 * Maps from several files let go together, as one result: the one file's
 * own, or how many came in, with a line for each that didn't, by its name.
 * A file that's another version of a map here is asked about, as one on its
 * own is: those come first, one after another, then what the rest brought
 * ([ImportResult.SeveralFiles]). Null when there were none.
 */
fun droppedMaps(results: List<Pair<String?, ImportResult>>, words: Words): ImportResult? {
    if (results.size <= 1) {
        return results.singleOrNull()?.second
    }

    val otherVersions = results.map { it.second }.filterIsInstance<ImportResult.AlreadyHere>()
    val imported = results.map { it.second }.filterIsInstance<ImportResult.Imported>()
    val many = results.map { it.second }.filterIsInstance<ImportResult.ImportedMany>()
    val alreadyHere = many.sumOf { it.alreadyHere }
    val privateAlreadyHere = many.sumOf { it.privateAlreadyHere }
    val added = imported.size + many.sumOf { it.added }

    val failed = results.mapNotNull { (name, result) ->
        (result as? ImportResult.Failed)?.let { failure -> name?.let { words.string(R.string.drop_file_problem, it, failure.reason) } ?: failure.reason }
    }

    // Questions alone need nothing said after them; files that brought nothing at all say so.
    val summary = when {
        added > 0 || alreadyHere > 0 -> ImportResult.ImportedMany(
            added = added,
            alreadyHere = alreadyHere,
            notes = imported.flatMap { it.notes } + many.flatMap { it.notes } + failed,
            privateAlreadyHere = privateAlreadyHere,
            filledIn = many.sumOf { it.filledIn },
            privateFilledIn = many.sumOf { it.privateFilledIn },
        )
        failed.isNotEmpty() -> ImportResult.Failed(failed.joinToString("\n"))
        otherVersions.isNotEmpty() -> null
        else -> ImportResult.ImportedMany(added = 0, alreadyHere = 0)
    }

    return if (otherVersions.isEmpty()) summary else ImportResult.SeveralFiles(otherVersions, summary)
}

/** Takes drags onto the map's page, from other apps and from the map itself out and back, for [MapDrops]. */
class MapDropListener(private val drops: MapDrops) : View.OnDragListener {
    override fun onDrag(view: View, event: DragEvent): Boolean {
        val density = view.resources.displayMetrics.density

        return when (event.action) {
            DragEvent.ACTION_DRAG_STARTED -> drops.accepts(event.clipDescription, event.localState)
            DragEvent.ACTION_DRAG_ENTERED -> {
                drops.entered()
                true
            }
            DragEvent.ACTION_DRAG_LOCATION -> {
                drops.moved(event.x / density, event.y / density, view.height / density)
                true
            }
            DragEvent.ACTION_DRAG_EXITED -> {
                drops.exited()
                true
            }
            DragEvent.ACTION_DROP -> drops.dropped(view, event, event.x / density, event.y / density)
            DragEvent.ACTION_DRAG_ENDED -> {
                drops.ended()
                true
            }
            else -> false
        }
    }
}
