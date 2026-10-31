package com.hyuchia.orbit.map

import android.content.ContentResolver
import android.net.Uri
import androidx.annotation.StringRes
import com.hyuchia.orbit.Logs
import com.hyuchia.orbit.R
import com.hyuchia.orbit.data.MapRepository
import com.hyuchia.orbit.data.MediaRefusedException
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
 * put on its idea. A video's or a sound's copy says how far along it is, and
 * one that didn't join says why, in its idea's editor while that's open. The
 * copies and problems live in `ui`.
 */
class MediaAdding(
    private val scope: CoroutineScope,
    private val ui: MutableStateFlow<MapUi>,
    private val maps: MapRepository,
    private val id: String,

    /** Sets an attribute of an idea; null removes it. */
    private val update: (nodeId: String, key: String, value: JsonElement?) -> Unit,

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
     * there had. The editor shows how far along the copy is.
     */
    fun addVideo(nodeId: String, uri: Uri, resolver: ContentResolver) {
        videoJob?.cancel()
        val copy = ++videoCopies
        ui.update { it.copy(videoCopy = VideoCopy(nodeId), mediaProblem = null) }

        videoJob = scope.launch {
            try {
                adding({ MediaProblem(nodeId, video = true, it) }, "Couldn't copy the video", R.string.map_video_unreadable) {
                    val added = maps.addVideo(id, uri, resolver) { progress ->
                        ui.update { state -> if (copy == videoCopies) state.copy(videoCopy = VideoCopy(nodeId, progress)) else state }
                    }

                    val caption = ui.value.nodes[nodeId]?.video?.caption
                    update(nodeId, "video", videoAttribute(added.src, added.thumbnail, caption))
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
}
