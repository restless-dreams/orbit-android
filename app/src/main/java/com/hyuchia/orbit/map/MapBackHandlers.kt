package com.hyuchia.orbit.map

import androidx.activity.compose.BackHandler
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException

/**
 * Back beside the list on a tablet: it closes the map, as the arrow in its
 * bar does, once nothing on the map or over it is left to close or fly up
 * from, the screen shrinking under the finger by `scale`. The list-detail
 * scene leaves Back to the map there, as closing the map leaves the list
 * beside its placeholder, the same two panes. Added before the map's own
 * handlers ([MapBackHandlers]) and its lock's, so each of them goes first,
 * and so does whatever opens over the map, whatever order the scenes add
 * theirs in as panels beside the map come and go.
 */
@Composable
internal fun BesideListBackHandler(enabled: Boolean, scale: Animatable<Float, AnimationVector1D>, onClose: () -> Unit) {
    val scope = rememberCoroutineScope()

    PredictiveBackHandler(enabled = enabled) { events ->
        try {
            events.collect { event -> scale.snapTo(1f - 0.06f * event.progress) }
            onClose()
        } catch (cancelled: CancellationException) {
            // Swiped back in: the map stays.
        } finally {
            scope.launch { scale.animateTo(1f, tween(220)) }
        }
    }
}

/**
 * Back on the map's screen: swiping back flies up a level, with the map
 * shrinking under the finger by `backScale` like any screen does, and closes
 * what's over the map, the field being typed in first.
 */
@Composable
internal fun MapBackHandlers(model: MapViewModel, ui: MapUi, talk: Talk, backScale: Animatable<Float, AnimationVector1D>) {
    val scope = rememberCoroutineScope()
    val canGoUp = ui.path.size > 1 || !ui.focused

    PredictiveBackHandler(enabled = canGoUp && !ui.editing && ui.source == null && ui.presentation == null && ui.study == null && ui.panel == null && ui.sketch == null) { events ->
        try {
            events.collect { event -> backScale.snapTo(1f - 0.06f * event.progress) }
            model.goUp()
        } catch (cancelled: CancellationException) {
            // Swiped back in: stay.
        } finally {
            scope.launch { backScale.animateTo(1f, tween(220)) }
        }
    }

    // Back while typing keeps what was typed and closes the field.
    BackHandler(enabled = ui.editing) { model.stopEditing(save = true) }
    BackHandler(enabled = ui.source != null && !ui.editing) { model.closeSource() }
    BackHandler(enabled = ui.presentation != null) { model.stopPresentation() }
    BackHandler(enabled = ui.study != null) { model.stopStudy() }
    BackHandler(enabled = talk.active && ui.presentation == null) { talk.stop() }
    // Making a path, back flies up the map like always, and at its top keeps the path.
    BackHandler(enabled = ui.pathMaking && !canGoUp && !ui.editing && ui.panel == null) { model.finishPath() }
}
