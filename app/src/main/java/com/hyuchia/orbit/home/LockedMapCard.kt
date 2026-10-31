package com.hyuchia.orbit.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.LockOpen
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.hyuchia.orbit.R
import com.hyuchia.orbit.data.LockedCover
import com.hyuchia.orbit.privacy.LockedLook
import com.hyuchia.orbit.privacy.LockedOrbit
import com.hyuchia.orbit.privacy.ShowPrivateMapsButton
import com.hyuchia.orbit.theme.LocalAppSettings
import com.hyuchia.orbit.ui.Hint

/**
 * A map's card, or its locked card while it's private and private maps are
 * locked. As they unlock, each locked card gives way to the map's own, its
 * picture growing in where the lock was; as they lock, the lock comes back
 * at once.
 */
@Composable
fun PrivateOrOpenCard(locked: Boolean, modifier: Modifier = Modifier, lockedCard: @Composable () -> Unit, card: @Composable () -> Unit) {
    val growing = MaterialTheme.motionScheme.slowSpatialSpec<Float>()

    AnimatedContent(
        targetState = locked,
        transitionSpec = {
            if (targetState) {
                // Locking hides at once: nothing private lingers in a fade.
                EnterTransition.None togetherWith ExitTransition.None
            } else {
                // The lock opens first, then the map's own card grows in where it was.
                (fadeIn(tween(300, delayMillis = 240)) + scaleIn(growing, initialScale = 0.94f)) togetherWith
                    (fadeOut(tween(220, delayMillis = 260)) + scaleOut(tween(480), targetScale = 1.02f))
            } using SizeTransform(clip = false)
        },
        label = "private card",
        modifier = modifier,
    ) { hidden ->
        if (hidden) {
            lockedCard()
        } else {
            card()
        }
    }
}

/**
 * A private map's card while private maps are locked: the card's own shape,
 * an empty orbit with a lock where the map's picture was, and nothing of the
 * map, its name, tags and progress included. Its `look`, made from its id,
 * gives each one colors and planets of its own, and the `cover` its owner
 * chose, an emoji in the lock and a name in place of "Private map". A tap,
 * or its one menu item, asks for the phone's screen lock, which opens every
 * private map. `open` opens the lock as they unlock.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LockedMapCard(
    pinned: Boolean,
    onUnlock: () -> Unit,
    modifier: Modifier = Modifier,
    open: Boolean = false,
    cover: LockedCover? = null,
    look: LockedLook? = null,
) {
    val haptics = LocalHapticFeedback.current
    var menuOpen by remember { mutableStateOf(false) }
    val name = cover?.name?.takeIf { it.isNotBlank() }
    val described = name?.let { stringResource(R.string.private_locked_card_description_named, it) } ?: stringResource(R.string.private_locked_card_description)

    Surface(
        shape = CardShape,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = modifier
            .fillMaxWidth()
            .clip(CardShape)
            .combinedClickable(
                interactionSource = null,
                indication = ripple(focusRingShape = CardShape),
                onClickLabel = stringResource(R.string.private_unlock_private_maps),
                onLongClickLabel = stringResource(R.string.home_more_options),
                onClick = onUnlock,
                onLongClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    menuOpen = true
                },
            )
            .semantics(mergeDescendants = true) {
                contentDescription = described
                // Heard before its menu button, as a map's own card is.
                isTraversalGroup = true
            },
    ) {
        Column {
            Box {
                LockedOrbit(
                    open = open,
                    look = look,
                    icon = cover?.icon,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(16f / 10f)
                        .padding(6.dp)
                        .clip(RoundedCornerShape(19.dp)),
                )

                if (pinned) {
                    Icon(
                        Icons.Filled.PushPin,
                        contentDescription = stringResource(R.string.home_pinned),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(14.dp)
                            .size(28.dp)
                            .background(MaterialTheme.colorScheme.primaryContainer, CircleShape)
                            .padding(6.dp),
                    )
                }
            }

            Row(verticalAlignment = Alignment.Top, modifier = Modifier.padding(start = 16.dp, end = 4.dp, bottom = 12.dp)) {
                Column(Modifier.weight(1f).padding(top = 6.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(name ?: stringResource(R.string.private_map), style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    // It wraps, so larger text keeps the way in.
                    Text(
                        stringResource(R.string.private_locked_card_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                Box {
                    Hint(stringResource(R.string.home_more_options)) {
                        IconButton(onClick = { menuOpen = true }) {
                            Icon(Icons.Outlined.MoreVert, contentDescription = stringResource(R.string.private_more_options))
                        }
                    }

                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.private_unlock_private_maps)) },
                            leadingIcon = { Icon(Icons.Outlined.LockOpen, contentDescription = null) },
                            onClick = {
                                menuOpen = false
                                onUnlock()
                            },
                        )
                    }
                }
            }
        }
    }
}

/** What Home's bar offers for private maps. */
private enum class PrivateMapsAction { Lock, Show, Nothing }

/**
 * Home's bar's lock for private maps. While any shows (`canLock`), it locks them all at once, before
 * handing the phone to someone, say. While they hide (`canShow`), it shows them again, once the phone's
 * owner unlocks them: quiet, as the bar's other buttons are, and never saying how many there are. One
 * gives way to the other in place.
 */
@Composable
fun PrivateMapsButton(canLock: Boolean, canShow: Boolean, onLock: () -> Unit, onShow: () -> Unit) {
    val haptics = LocalHapticFeedback.current
    val hapticsOn = LocalAppSettings.current.haptics
    val effects = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
    val spatial = MaterialTheme.motionScheme.defaultSpatialSpec<Float>()

    val action = when {
        canLock -> PrivateMapsAction.Lock
        canShow -> PrivateMapsAction.Show
        else -> PrivateMapsAction.Nothing
    }

    AnimatedContent(
        action,
        transitionSpec = {
            (fadeIn(effects) + scaleIn(spatial, initialScale = 0.6f)) togetherWith (fadeOut(effects) + scaleOut(spatial, targetScale = 0.6f)) using SizeTransform(clip = false)
        },
        contentAlignment = Alignment.Center,
        label = "private maps",
    ) { shown ->
        when (shown) {
            PrivateMapsAction.Lock -> {
                val name = stringResource(R.string.private_lock_private_maps)

                Hint(name) {
                    IconButton(onClick = {
                        // A tap as they lock, when Settings has haptics on, as unlocking does.
                        if (hapticsOn) {
                            haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                        }

                        onLock()
                    }) {
                        Icon(Icons.Outlined.Lock, contentDescription = name)
                    }
                }
            }
            PrivateMapsAction.Show -> ShowPrivateMapsButton(onShow)
            PrivateMapsAction.Nothing -> Unit
        }
    }
}
