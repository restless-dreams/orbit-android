package com.hyuchia.orbit.privacy

import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.LockOpen
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.toPath
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.Morph
import com.hyuchia.orbit.R
import com.hyuchia.orbit.theme.isDark
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * A locked private map's own look, made from its id alone, never from what
 * it holds: which of the logo's colors it takes, and how the planets sit on
 * its orbit. So two locked maps tell apart at a glance, cover or not.
 */
@Immutable
data class LockedLook(
    /** Which of [LockedHues] it takes. */
    val hue: Int,

    /** How many planets are on its orbit, three to seven. */
    val planets: Int,

    /** How far round the first planet is, as a share of the space between two. */
    val turn: Float,

    /** How many smaller planets are farther out, one to four. */
    val outer: Int,
)

/** The colors locked maps take: the logo's planets', and two more of the same light. */
internal val LockedHues = listOf(
    Color(0xFFFF9E96),
    Color(0xFFF8B478),
    Color(0xFFFAD86F),
    Color(0xFF7FDAAB),
    Color(0xFF7FD6D2),
    Color(0xFF86CDFF),
    Color(0xFFC9B1FE),
    Color(0xFFF7A3D4),
)

/** The look of the locked map with this `id`: the same every time, on every screen. */
fun lockedLook(id: String): LockedLook {
    // The id's hash, its bits spread out, so ids alike in their letters still look apart.
    var bits = id.hashCode()
    bits = (bits xor (bits ushr 16)) * 0x45d9f3b
    bits = (bits xor (bits ushr 16)) * 0x45d9f3b
    bits = bits xor (bits ushr 16)
    return LockedLook(
        hue = (bits and 0xFF) % LockedHues.size,
        planets = 3 + ((bits ushr 8) and 0xFF) % 5,
        turn = ((bits ushr 16) and 0xFF) / 256f,
        outer = 1 + ((bits ushr 24) and 0xFF) % 4,
    )
}

/** A look's colors, in the app's light or dark. */
@Immutable
internal class LockedColors(val container: Color, val content: Color, val ring: Color, val planet: Color, val sky: List<Color>)

/** The colors of `look`, or the theme's own, without one. */
@Composable
internal fun lockedColors(look: LockedLook?, framed: Boolean = true): LockedColors {
    val colors = MaterialTheme.colorScheme
    val sky = listOf(colors.surfaceContainerHigh, colors.surfaceContainerHighest)

    if (look == null) {
        return LockedColors(colors.primaryContainer, colors.onPrimaryContainer, colors.outlineVariant, colors.onSurfaceVariant.copy(alpha = if (framed) 0.16f else 0.12f), sky)
    }

    val hue = LockedHues[look.hue]
    val ink = colors.onSurface

    return if (colors.isDark) {
        LockedColors(lerp(hue, colors.surface, 0.42f), lerp(hue, Color.White, 0.6f), hue.copy(alpha = 0.6f), hue.copy(alpha = 0.5f), sky.map { lerp(it, hue, 0.1f) })
    } else {
        LockedColors(lerp(hue, Color.White, 0.2f), lerp(hue, ink, 0.8f), lerp(hue, ink, 0.25f).copy(alpha = 0.7f), hue.copy(alpha = 0.65f), sky.map { lerp(it, hue, 0.12f) })
    }
}

/**
 * The lock, in Material 3 Expressive's cookie shape, or a cover's emoji in
 * it with a small lock beside. `open` turns the shape round and opens the
 * lock, with the motion the app's theme springs with: the moment private
 * maps unlock. A `look` gives it the map's own colors.
 */
@Composable
fun LockEmblem(open: Boolean, modifier: Modifier = Modifier, iconSize: Dp = 28.dp, look: LockedLook? = null, icon: String? = null) {
    val colors = MaterialTheme.colorScheme
    val own = lockedColors(look)
    val progress by animateFloatAsState(if (open) 1f else 0f, MaterialTheme.motionScheme.defaultSpatialSpec(), label = "unlocking")
    val container by animateColorAsState(if (open) colors.primary else own.container, MaterialTheme.motionScheme.defaultEffectsSpec(), label = "unlocked color")
    val content by animateColorAsState(if (open) colors.onPrimary else own.content, MaterialTheme.motionScheme.defaultEffectsSpec(), label = "unlocked content")
    val morph = remember { Morph(MaterialShapes.Cookie9Sided, MaterialShapes.Circle) }
    val path = remember { Path() }
    val scale = remember { Matrix() }

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier.drawBehind {
            path.reset()
            morph.toPath(progress, path)
            scale.reset()
            scale.scale(size.width, size.height)
            path.transform(scale)
            // The cookie turns a little as it rounds, as the lock opens.
            rotate(progress * 40f) { drawPath(path, container) }
        },
    ) {
        if (icon.isNullOrBlank()) {
            Crossfade(open, label = "lock") { unlocked ->
                Icon(if (unlocked) Icons.Outlined.LockOpen else Icons.Outlined.Lock, contentDescription = null, tint = content, modifier = Modifier.size(iconSize))
            }
        } else {
            val density = LocalDensity.current
            Text(icon, fontSize = with(density) { (iconSize * 1.15f).toSp() }, maxLines = 1)

            // The lock stays in sight beside the cover's emoji, and opens as the map does.
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .size(iconSize * 0.72f)
                    .background(content, CircleShape),
            ) {
                Crossfade(open, label = "lock") { unlocked ->
                    Icon(if (unlocked) Icons.Filled.LockOpen else Icons.Filled.Lock, contentDescription = null, tint = container, modifier = Modifier.size(iconSize * 0.44f))
                }
            }
        }
    }
}

/**
 * A private map's picture while it's locked: its orbit, empty but for a
 * few dimmed planets, with the lock where the sun is. Calm, so a locked
 * map reads as kept, not broken. In a frame, as cards have, it has a sky
 * of its own; without, it floats on the screen. A `look` gives it the map's
 * own colors and planets, and a cover's `icon` takes the lock's place.
 */
@Composable
fun LockedOrbit(modifier: Modifier = Modifier, emblemSize: Dp = 56.dp, open: Boolean = false, framed: Boolean = true, look: LockedLook? = null, icon: String? = null) {
    val own = lockedColors(look, framed)
    val planets = look?.planets ?: 5
    val outer = look?.outer ?: 3
    // Without a look, as every locked map was drawn before they had their own.
    val turn = look?.turn ?: (0.4f * 5 / (2 * PI.toFloat()))
    val outerTurn = look?.turn ?: 0f

    Box(if (framed) modifier.background(Brush.linearGradient(own.sky)) else modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val middle = Offset(size.width / 2, size.height / 2)
            val orbit = min(size.width, size.height) * 0.34f
            val dots = PathEffect.dashPathEffect(floatArrayOf(0.01f, 5.dp.toPx()))
            drawCircle(own.ring, radius = orbit, center = middle, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, pathEffect = dots))
            drawCircle(own.ring.copy(alpha = own.ring.alpha * 0.5f), radius = orbit * 1.62f, center = middle, style = Stroke(width = 1.5.dp.toPx(), cap = StrokeCap.Round, pathEffect = dots))

            // Planets on the orbit, as a map with that many branches opens, and smaller ones farther out.
            for (index in 0 until planets) {
                val angle = -PI / 2 + (turn + index) * 2 * PI / planets
                drawCircle(own.planet, radius = orbit * (0.2f - planets * 0.012f), center = middle + Offset((cos(angle) * orbit).toFloat(), (sin(angle) * orbit).toFloat()))
            }

            for (index in 0 until outer) {
                val angle = PI / 5 + (outerTurn + index) * 2 * PI / outer
                drawCircle(own.planet.copy(alpha = own.planet.alpha * 0.7f), radius = orbit * 0.09f, center = middle + Offset((cos(angle) * orbit * 1.62f).toFloat(), (sin(angle) * orbit * 1.62f).toFloat()))
            }
        }

        LockEmblem(open, Modifier.size(emblemSize), iconSize = emblemSize / 2, look = look, icon = icon)
    }
}

/** On a private map's card, while it's open: a small lock beside the pin, as pinned maps have theirs. */
@Composable
fun PrivateBadge(modifier: Modifier = Modifier) {
    Icon(
        Icons.Filled.Lock,
        contentDescription = stringResource(R.string.private_badge),
        tint = MaterialTheme.colorScheme.onSecondaryContainer,
        modifier = modifier
            .size(28.dp)
            .background(MaterialTheme.colorScheme.secondaryContainer, CircleShape)
            .padding(6.dp),
    )
}
