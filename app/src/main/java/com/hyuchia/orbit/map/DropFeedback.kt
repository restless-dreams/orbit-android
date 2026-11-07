package com.hyuchia.orbit.map

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Notes
import androidx.compose.material.icons.outlined.AttachFile
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.FileOpen
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.MotionPhotosOn
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.OpenWith
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hyuchia.orbit.R
import com.hyuchia.orbit.ui.Hint
import kotlinx.coroutines.delay
import java.text.NumberFormat

/** A copy shorter than this (ms) just lands: only one that takes a moment says so over the map. */
private const val COPY_SHOWN_AFTER = 250L

/** What the pill over the map says: what a drag would do, or how a drop's copy goes. */
private sealed interface Feedback {
    data class Offer(val offer: DropOffer, val target: String?) : Feedback

    data class Copy(val copy: DropCopy, val target: String) : Feedback
}

/**
 * Over the map while something from another app is dragged onto it: a pill
 * that says what letting go does there ("Add to Flights"), following the idea
 * that lights up under the finger. It stands at the map's top, `top` dp down,
 * and moves to its bottom, `bottom` dp up, while the finger is high on the
 * map, so it never covers what it points at. While pictures, videos and
 * sounds from a drop are copied in, it says how far along they are, with a
 * way to stop.
 */
@Composable
fun DropFeedback(drops: MapDrops, ui: MapUi, top: Dp, bottom: Dp, modifier: Modifier = Modifier) {
    val hint by drops.hint.collectAsStateWithLifecycle()
    val copy by drops.copying.collectAsStateWithLifecycle()
    var copyShown by remember { mutableStateOf(false) }
    // From the top (-1) to the bottom (1) of the room the pill has, gliding as the finger crosses the map.
    val place by animateFloatAsState(if (hint?.high == true) 1f else -1f, spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow), label = "place")

    LaunchedEffect(copy != null) {
        copyShown = false

        if (copy != null) {
            delay(COPY_SHOWN_AFTER)
            copyShown = true
        }
    }

    val untitled = stringResource(R.string.common_untitled)
    // While the person is asked about big videos, nothing is being copied yet.
    val shownCopy = copy?.takeIf { copyShown && !it.asking }

    val feedback: Feedback? = when {
        shownCopy != null -> Feedback.Copy(shownCopy, ui.nodes[shownCopy.target]?.label(untitled) ?: untitled)
        // Until the page says which idea is under the finger, there's nothing to name; maps don't go into one.
        hint != null -> hint?.let { current ->
            val target = current.target?.let { ui.nodes[it] }?.label(untitled)
            Feedback.Offer(current.offer, target).takeIf { current.offer == DropOffer.Maps || target != null }
        }
        else -> null
    }

    val offer by drops.liveOffer.collectAsStateWithLifecycle()

    offer?.let { LiveOfferDialog(it, onAnswer = drops::answerLiveOffer) }

    Box(modifier.fillMaxSize().padding(top = top, bottom = bottom)) {
        AnimatedVisibility(
            visible = feedback != null,
            enter = fadeIn(tween(140)) + scaleIn(spring(dampingRatio = 0.62f, stiffness = Spring.StiffnessMediumLow), initialScale = 0.82f),
            exit = fadeOut(tween(160)) + scaleOut(tween(160), targetScale = 0.92f),
            modifier = Modifier.align(BiasAlignment(horizontalBias = 0f, verticalBias = place)),
        ) {
            // The last thing said stays while the pill leaves.
            val last = remember { LastSaid() }

            if (feedback != null) {
                last.feedback = feedback
            }

            last.feedback?.let { Pill(it, onStop = drops::cancel) }
        }
    }
}

/** What the pill said last, kept for it to leave with. */
private class LastSaid {
    var feedback: Feedback? = null
}

@Composable
private fun Pill(feedback: Feedback, onStop: () -> Unit) {
    val colors = MaterialTheme.colorScheme

    Surface(
        shape = CircleShape,
        color = colors.secondaryContainer,
        contentColor = colors.onSecondaryContainer,
        shadowElevation = 6.dp,
        modifier = Modifier
            .heightIn(min = 48.dp)
            .widthIn(max = 420.dp)
            .semantics { liveRegion = LiveRegionMode.Polite },
    ) {
        AnimatedContent(
            targetState = feedback,
            contentKey = { it.key() },
            transitionSpec = {
                (fadeIn(tween(180, delayMillis = 60)) + slideInVertically(tween(220)) { it / 3 }) togetherWith
                    (fadeOut(tween(100)) + slideOutVertically(tween(160)) { -it / 3 }) using SizeTransform(clip = false)
            },
            contentAlignment = Alignment.Center,
            label = "drop feedback",
        ) { shown ->
            when (shown) {
                is Feedback.Offer -> OfferRow(shown)
                is Feedback.Copy -> CopyRow(shown, onStop)
            }
        }
    }
}

/** Changes with what's said, so a new idea under the finger slides in, and a copy's progress just moves. */
private fun Feedback.key(): String = when (this) {
    is Feedback.Offer -> "$offer:$target"
    is Feedback.Copy -> "copy:${copy.done}:${copy.total}"
}

@Composable
private fun OfferRow(feedback: Feedback.Offer) {
    val text = when {
        feedback.offer == DropOffer.Maps -> stringResource(R.string.drop_open_as_a_map)
        feedback.offer == DropOffer.Move -> stringResource(R.string.drop_move_into, feedback.target.orEmpty())
        else -> stringResource(R.string.drop_add_to, feedback.target.orEmpty())
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.padding(start = 18.dp, end = 22.dp, top = 12.dp, bottom = 12.dp),
    ) {
        Icon(feedback.offer.icon(), contentDescription = null, modifier = Modifier.size(20.dp))
        Text(text, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun CopyRow(feedback: Feedback.Copy, onStop: () -> Unit) {
    val copy = feedback.copy
    val locale = LocalConfiguration.current.locales[0]
    val progress = copy.progress

    val text = when {
        copy.total > 1 -> pluralStringResource(R.plurals.drop_adding_some_to, copy.total, copy.done, copy.total, feedback.target)
        // Whole percents, rounded down, as the copy goes, as the editor says them.
        copy.video && copy.smaller && progress != null -> stringResource(R.string.drop_making_the_video_smaller_for, feedback.target, NumberFormat.getPercentInstance(locale).format((progress * 100).toInt() / 100.0))
        copy.video && progress != null -> stringResource(R.string.drop_adding_the_video_to, feedback.target, NumberFormat.getPercentInstance(locale).format((progress * 100).toInt() / 100.0))
        copy.sound && progress != null -> stringResource(R.string.drop_adding_the_sound_to, feedback.target, NumberFormat.getPercentInstance(locale).format((progress * 100).toInt() / 100.0))
        else -> stringResource(R.string.drop_adding_to, feedback.target)
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.padding(start = 16.dp, end = 4.dp),
    ) {
        Box(Modifier.size(22.dp), contentAlignment = Alignment.Center) {
            if (progress != null) {
                CircularProgressIndicator(progress = { progress }, strokeWidth = 2.5.dp, modifier = Modifier.size(20.dp))
            } else {
                CircularProgressIndicator(strokeWidth = 2.5.dp, modifier = Modifier.size(20.dp))
            }
        }

        Text(text, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))

        val stop = stringResource(R.string.drop_stop_adding)

        Hint(stop) {
            IconButton(onClick = onStop) { Icon(Icons.Outlined.Close, contentDescription = stop) }
        }
    }
}

private fun DropOffer.icon(): ImageVector = when (this) {
    DropOffer.Ideas -> Icons.AutoMirrored.Outlined.Notes
    DropOffer.Pictures -> Icons.Outlined.Image
    DropOffer.Videos -> Icons.Outlined.Movie
    DropOffer.Sounds -> Icons.Outlined.GraphicEq
    DropOffer.Maps -> Icons.Outlined.FileOpen
    DropOffer.Files -> Icons.Outlined.AttachFile
    DropOffer.Move -> Icons.Outlined.OpenWith
}

/** Asks whether a picture and a movie let go together are one live photo: joining them makes the picture play its motion. */
@Composable
private fun LiveOfferDialog(offer: LiveOffer, onAnswer: (Boolean) -> Unit) {
    val text = if (offer.picture != null && offer.movie != null) {
        stringResource(R.string.drop_live_offer, offer.picture, offer.movie)
    } else {
        stringResource(R.string.drop_live_offer_unnamed)
    }

    AlertDialog(
        onDismissRequest = { onAnswer(false) },
        icon = { Icon(Icons.Outlined.MotionPhotosOn, contentDescription = null) },
        title = { Text(stringResource(R.string.drop_live_offer_title)) },
        text = { Text(text) },
        confirmButton = { TextButton(onClick = { onAnswer(true) }) { Text(stringResource(R.string.drop_live_join)) } },
        dismissButton = { TextButton(onClick = { onAnswer(false) }) { Text(stringResource(R.string.drop_live_keep_apart)) } },
    )
}
