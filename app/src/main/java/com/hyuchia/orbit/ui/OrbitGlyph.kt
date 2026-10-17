package com.hyuchia.orbit.ui

import androidx.compose.foundation.Canvas
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.hyuchia.orbit.theme.OrbitPalette
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/** A map in miniature: its sun and one planet per branch, the way the map opens. */
@Composable
fun OrbitGlyph(branches: Int, modifier: Modifier = Modifier) {
    val sun = MaterialTheme.colorScheme.primaryContainer
    val ring = MaterialTheme.colorScheme.outlineVariant

    Canvas(modifier) {
        val center = Offset(size.width / 2, size.height / 2)
        val orbit = min(size.width, size.height) * 0.38f
        drawCircle(
            color = ring,
            radius = orbit,
            center = center,
            style = Stroke(
                width = 2.dp.toPx(),
                cap = StrokeCap.Round,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(0.01f, 5.dp.toPx())),
            ),
        )
        drawCircle(color = sun, radius = orbit * 0.48f, center = center)
        val planets = branches.coerceIn(0, 8)

        for (index in 0 until planets) {
            val angle = -PI / 2 + 2 * PI * index / planets
            drawCircle(
                color = OrbitPalette[index % OrbitPalette.size],
                radius = orbit * 0.22f,
                center = Offset(center.x + orbit * cos(angle).toFloat(), center.y + orbit * sin(angle).toFloat()),
            )
        }
    }
}
