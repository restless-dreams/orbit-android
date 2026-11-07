package com.hyuchia.orbit.map

import androidx.compose.runtime.Composable
import com.hyuchia.orbit.media.MediaViewer
import com.hyuchia.orbit.media.SoundState
import com.hyuchia.orbit.media.VideoScreen

/** Over the whole map's screen: its pictures and videos, as `ui` has them open, with `sound` the small player's. */
@Composable
internal fun MapFullScreen(model: MapViewModel, ui: MapUi, sound: SoundState?) {
    ui.viewing?.let { viewing ->
        MediaViewer(
            mapId = model.id,
            items = viewing.items,
            start = viewing.start,
            source = model::mediaModel,
            onPlay = model::play,
            onClosed = model::closeViewer,
            sound = sound,
            sounds = model.sounds,
            soundLength = model::soundLength,
        )
    }

    // After the pictures, so a video started from them plays over them, and they're still there after it.
    ui.playing?.let { item -> VideoScreen(model.id, item, resolve = model::mediaModel, onClosed = model::stopPlaying) }
}
