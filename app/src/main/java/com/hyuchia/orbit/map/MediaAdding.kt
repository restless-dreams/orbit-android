package com.hyuchia.orbit.map

import android.content.ContentResolver
import android.net.Uri
import androidx.annotation.StringRes
import com.hyuchia.orbit.Logs
import com.hyuchia.orbit.R
import com.hyuchia.orbit.Words
import com.hyuchia.orbit.data.MapRepository
import com.hyuchia.orbit.data.MediaRefusedException
import com.hyuchia.orbit.data.nameAndSize
import com.hyuchia.orbit.media.VideoMaker
import com.hyuchia.orbit.page.MapFile
import com.hyuchia.orbit.ui.MediaWords
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonElement
import java.io.File
import java.io.IOException

/**
 * Pictures, live photos' movies, videos, sounds and voice memos from the
 * phone, joining map `id`'s ideas: each is copied into the map's folder and
 * put on its idea. A big video is offered smaller first (`bigVideos`), and
 * made so by `maker`. A video's or a sound's copy says how far along it is,
 * and one that didn't join says why, in its idea's editor while that's open.
 * The copies and problems live in `ui`; sizes are said in `words`.
 */
class MediaAdding(
    private val scope: CoroutineScope,
    private val ui: MutableStateFlow<MapUi>,
    private val maps: MapRepository,
    private val id: String,
    private val bigVideos: BigVideos,
    private val maker: () -> VideoMaker,
    private val words: Words,

    /** Sets an attribute of an idea; null removes it. */
    private val update: (nodeId: String, key: String, value: JsonElement?) -> Unit,

    /** Adds files copied into the map to an idea's, with Undo. */
    private val addFiles: (nodeId: String, files: List<MapFile>) -> Unit,

    /** Says something on the screen. */
    private val say: (MapEvent) -> Unit,

    /** What an idea without text is called, in the app's language. */
    private val untitled: () -> String,
) {
    /** The video being copied in from the phone, and how many copies have started, so one replaced doesn't clear the next one's progress. */
    private var videoJob: Job? = null

    private var videoCopies = 0

    /** The sound being copied in from the phone, as for videos. */
    private var soundJob: Job? = null

    private var soundCopies = 0

    /** The files being copied in from the phone: files chosen while others copy follow them. */
    private var fileJob: Job? = null

    /** Gives an idea a picture from the phone; `pasted` says so once it's there, with Undo. */
    fun addPicture(nodeId: String, uri: Uri, resolver: ContentResolver, pasted: Boolean = false) {
        ui.update { it.copy(mediaProblem = null) }

        scope.launch {
            val added = adding({ MediaProblem(nodeId, video = false, it) }, "Couldn't read the picture", R.string.map_picture_unreadable) {
                maps.addPicture(id, uri, resolver)
            } ?: return@launch

            update(nodeId, "image", pictureAttribute(added))
            ui.update { it.copy(iphoneStill = nodeId.takeIf { added.fromIphone }) }

            if (added.motionLeftOut) {
                say(MapEvent.Snackbar(ShownText.Resource(R.string.map_live_photo_motion_left_out)))
            }

            if (pasted) {
                say(MapEvent.Snackbar(ShownText.Resource(R.string.map_pasted_the_picture), undo = true))
            }
        }
    }

    /**
     * Makes an idea's picture a live photo with the movie an iPhone took with
     * it: the movie is kept beside the picture as its motion, the picture's
     * own fields as they were.
     */
    fun addMotion(nodeId: String, movie: Uri, resolver: ContentResolver) {
        val image = ui.value.nodes[nodeId]?.image ?: return
        ui.update { it.copy(mediaProblem = null) }

        scope.launch {
            val motion = adding({ MediaProblem(nodeId, video = true, it) }, "Couldn't read the live photo's movie", R.string.map_video_unreadable) {
                maps.addMotion(id, image.src, movie, resolver)
            } ?: return@launch

            ui.update { it.copy(iphoneStill = null) }
            update(nodeId, "image", motionChange(motion))
            say(MapEvent.Snackbar(ShownText.Resource(R.string.map_made_a_live_photo), undo = true))
        }
    }

    /**
     * What `add` brought in, or null when it didn't: refused, the copy's own
     * reason is said (the map's room for media, say); failed, it's logged as
     * `failure` and `unreadable` is said. A copy stopped on the way says
     * nothing.
     */
    private suspend fun <Added> adding(problem: (ShownText) -> MediaProblem, failure: String, @StringRes unreadable: Int, add: suspend () -> Added): Added? = try {
        add()
    } catch (refused: MediaRefusedException) {
        mediaProblem(problem(ShownText.Plain(refused.message.orEmpty())))
        null
    } catch (stopped: CancellationException) {
        throw stopped
    } catch (failed: Exception) {
        Logs.warn("OrbitMap", failure, failed)
        mediaProblem(problem(ShownText.Resource(unreadable)))
        null
    }

    /** Says what came of a big video joining its idea, made smaller or kept as it is: in its editor while that's open, as a message otherwise. */
    private fun videoNote(nodeId: String, message: ShownText) {
        if (ui.value.panel.let { it is Panel.Editor && it.id == nodeId }) {
            ui.update { it.copy(videoNote = VideoNote(nodeId, message)) }
        } else {
            say(MapEvent.Snackbar(message))
        }
    }

    /** Says why a picture or video didn't join its idea: in its editor while that's open, as a message otherwise. */
    private fun mediaProblem(problem: MediaProblem) {
        val editing = ui.value.panel.let { it is Panel.Editor && it.id == problem.nodeId }

        if (editing) {
            ui.update { it.copy(mediaProblem = problem) }
        } else {
            say(MapEvent.Snackbar(problem.message))
        }
    }

    /**
     * Copies a video from the phone into the map's folder, with a frame from
     * it as its poster, and puts it on the node, keeping the caption a video
     * there had. A big one is offered smaller first, and made so on the phone
     * when the person says; one this phone can't make smaller comes as it is,
     * which is said. The editor shows how far along the copy, or the making,
     * is, with a way to stop it.
     */
    fun addVideo(nodeId: String, uri: Uri, resolver: ContentResolver) {
        videoJob?.cancel()
        val copy = ++videoCopies
        ui.update { it.copy(videoCopy = VideoCopy(nodeId), mediaProblem = null, videoNote = null) }

        fun copying(progress: Float?, smaller: SmallerSizes? = null) {
            ui.update { state -> if (copy == videoCopies) state.copy(videoCopy = VideoCopy(nodeId, progress, smaller)) else state }
        }

        videoJob = scope.launch {
            try {
                adding({ MediaProblem(nodeId, video = true, it) }, "Couldn't copy the video", R.string.map_video_unreadable) {
                    // While the person is asked, the editor shows the video as it was: nothing is being copied yet.
                    val way = bigVideos.way(uri, maps.sizeOf(uri, resolver), maps.mediaSizeNow(id)) {
                        ui.update { state -> if (copy == videoCopies) state.copy(videoCopy = null) else state }
                    }

                    val added = when (way) {
                        VideoWay.Neither -> return@adding
                        is VideoWay.Smaller -> {
                            val sizes = SmallerSizes(way.size, way.plan.estimate)
                            copying(0f, sizes)
                            maps.addMadeVideo(id, way.plan.estimate) { made -> maker().make(uri, way.plan, made) { progress -> copying(progress, sizes) } }
                        }
                        else -> {
                            copying(null)
                            maps.addVideo(id, uri, resolver) { progress -> copying(progress) }
                        }
                    }

                    val caption = ui.value.nodes[nodeId]?.video?.caption
                    update(nodeId, "video", videoAttribute(added.src, added.thumbnail, caption))

                    when (way) {
                        is VideoWay.Smaller -> videoNote(nodeId, ShownText.Resource(R.string.map_made_the_video_smaller, listOf(MediaWords.size(added.bytes, words), MediaWords.size(way.size, words))))
                        VideoWay.AsItIsHere -> videoNote(nodeId, ShownText.Resource(R.string.map_cannot_make_videos_smaller))
                        else -> Unit
                    }
                }
            } finally {
                if (copy == videoCopies) {
                    ui.update { it.copy(videoCopy = null) }
                }
            }
        }
    }

    /** Stops the video being copied in: nothing of it stays. */
    fun cancelVideo() {
        videoJob?.cancel()
    }

    /**
     * Copies a sound from the phone into the map's folder and puts it on the
     * idea, its caption from the file's name when the idea's sound had none.
     * The editor shows how far along the copy is.
     */
    fun addSound(nodeId: String, uri: Uri, resolver: ContentResolver) {
        soundJob?.cancel()
        val copy = ++soundCopies
        ui.update { it.copy(soundCopy = SoundCopy(nodeId), mediaProblem = null) }

        soundJob = scope.launch {
            try {
                adding({ MediaProblem(nodeId, video = false, it, sound = true) }, "Couldn't copy the sound", R.string.audio_sound_unreadable) {
                    val added = maps.addSound(id, uri, resolver) { progress ->
                        ui.update { state -> if (copy == soundCopies) state.copy(soundCopy = SoundCopy(nodeId, progress)) else state }
                    }

                    setSound(nodeId, added.src, ui.value.nodes[nodeId]?.audio?.caption ?: added.caption)
                }
            } finally {
                if (copy == soundCopies) {
                    ui.update { it.copy(soundCopy = null) }
                }
            }
        }
    }

    /** Stops the sound being copied in: nothing of it stays. */
    fun cancelSound() {
        soundJob?.cancel()
    }

    /**
     * Keeps a voice memo, `recording`, on `nodeId`, keeping the caption its
     * sound had, with Undo. Returns why it couldn't, or null once it's on.
     */
    suspend fun saveRecording(nodeId: String, recording: File): ShownText? {
        val src = try {
            maps.addRecording(id, recording)
        } catch (refused: MediaRefusedException) {
            return ShownText.Plain(refused.message.orEmpty())
        } catch (failed: IOException) {
            Logs.warn("OrbitMap", "Couldn't keep the voice memo", failed)
            return ShownText.Resource(R.string.audio_recording_failed)
        }

        val node = ui.value.nodes[nodeId]
        setSound(nodeId, src, node?.audio?.caption)
        say(MapEvent.Snackbar(ShownText.Resource(R.string.audio_memo_saved, listOf(node?.label(untitled()) ?: untitled())), undo = true))
        ui.update { it.copy(recording = null) }
        return null
    }

    private fun setSound(nodeId: String, src: String, caption: String?) {
        update(nodeId, "audio", soundAttribute(src, caption))
    }

    /**
     * Copies files from the phone into the map's folder, one after another,
     * as they are, and adds them to idea `nodeId`'s files together: one
     * change, one Undo, which a message offers. One refused or unreadable
     * says why, in its editor or a message, and the rest go on. Stopped
     * partway, the ones copied so far join it. Files chosen while others
     * copy follow them.
     */
    fun addFiles(nodeId: String, uris: List<Uri>, resolver: ContentResolver) {
        if (uris.isEmpty()) {
            return
        }

        val before = fileJob

        fileJob = scope.launch {
            before?.join()
            ui.update { it.copy(mediaProblem = null) }
            val added = mutableListOf<MapFile>()

            try {
                uris.forEachIndexed { index, uri ->
                    val name = runCatching { nameAndSize(uri, resolver).first }.getOrNull() ?: words.string(R.string.files_kind_other)
                    ui.update { it.copy(fileCopy = FileCopy(nodeId, name, left = uris.size - index - 1)) }

                    adding({ MediaProblem(nodeId, video = false, it, file = true) }, "Couldn't copy a file", R.string.files_unreadable) {
                        maps.addFile(id, uri, resolver) { progress ->
                            ui.update { state -> state.fileCopy?.let { copy -> state.copy(fileCopy = copy.copy(progress = progress)) } ?: state }
                        }
                    }?.let { file -> added += MapFile(file.src, file.name, file.type, file.size) }
                }
            } finally {
                ui.update { it.copy(fileCopy = null) }

                // What came in joins the idea, stopped partway or not: nothing copied stays without its idea knowing it.
                if (added.isNotEmpty()) {
                    addFiles(nodeId, added.toList())
                }
            }
        }
    }

    /** Stops the files being copied in: the ones copied so far join their idea. */
    fun cancelFiles() {
        fileJob?.cancel()
    }
}
