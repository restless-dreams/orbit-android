package com.hyuchia.orbit.ui

import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Material's wavy progress line with its wave held still. By default the wave travels
 * for as long as the line is on screen, so the screen is drawn again on every frame
 * even when nothing changes: on the home screen that kept the phone busy at rest.
 */
@Composable
fun WavyProgress(progress: () -> Float, modifier: Modifier = Modifier) {
    LinearWavyProgressIndicator(progress = progress, modifier = modifier, waveSpeed = 0.dp)
}
