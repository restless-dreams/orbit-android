package com.hyuchia.orbit.nearby

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Downloading
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.VerbatimTtsAnnotation
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.hyuchia.orbit.R
import com.hyuchia.orbit.data.LockedCover
import com.hyuchia.orbit.data.MapBundle
import com.hyuchia.orbit.privacy.Unlocked
import com.hyuchia.orbit.privacy.coverTitle
import com.hyuchia.orbit.privacy.rememberUnlocking
import com.hyuchia.orbit.rememberWords
import com.hyuchia.orbit.ui.EscapeCloses
import com.hyuchia.orbit.ui.MediaWords
import com.hyuchia.orbit.ui.rememberDefaultButton

/** What to do with a map that arrived from another device. */
enum class Arrival { Open, KeepBoth, Decline }

/** How the arrival dialog asks about a map. */
internal enum class ArrivalAsk {
    /** A map new here: open it, or decline. */
    New,

    /** Another version of a map here: update it (keeping this version in its history), keep both, or decline. */
    Update,

    /** Another version of a private map here while they're locked: its name isn't said, and Update asks for the screen lock first. */
    UpdateLocked,

    /**
     * The same while private maps hide: asked as a new map would be, so nothing says one is here.
     * Open asks for the screen lock first; unlocked, the question becomes [Update].
     */
    NewUntilUnlocked,
}

/** How to ask about a map that's `alreadyHere` or not, `hidden` when it's private and locked, `concealed` while private maps hide. */
internal fun arrivalAsk(alreadyHere: Boolean, hidden: Boolean, concealed: Boolean): ArrivalAsk = when {
    !alreadyHere -> ArrivalAsk.New
    hidden && concealed -> ArrivalAsk.NewUntilUnlocked
    hidden -> ArrivalAsk.UpdateLocked
    else -> ArrivalAsk.Update
}

/**
 * Asks before taking in a map from another device, `from` at `address` (or
 * from a file, when null), with the `check` code the sender shows too. When
 * this device has the same map, the choice is to update it (keeping this
 * version in its history) or to keep both. `hidden` when that map is
 * private and private maps are locked: its name isn't said, only its
 * `cover` if its owner chose one, and nothing lands in it unseen, so Update
 * asks for the screen lock first; without it
 * the question stays, to keep both instead. `concealed` while private maps
 * hide: then it's asked about as a new map would be (see [ArrivalAsk]). Of
 * several files asked about in turn, it's `number` of `count`, and says so
 * above its title.
 */
@Composable
fun ArrivalDialog(
    from: String?,
    bundle: MapBundle,
    alreadyHere: Boolean,
    address: String? = null,
    check: String? = null,
    hidden: Boolean = false,
    concealed: Boolean = false,
    cover: LockedCover? = null,
    number: Int = 1,
    count: Int = 1,
    onAnswer: (Arrival) -> Unit,
) {
    // What comes with it, counted by kind as sharing counts it: its sounds by what its text plays, memos' `.mp4`s among them.
    val media = MediaWords.list(bundle.media, rememberWords())
    val unlocking = rememberUnlocking()
    val ask = arrivalAsk(alreadyHere, hidden, concealed)
    val asNew = ask == ArrivalAsk.New || ask == ArrivalAsk.NewUntilUnlocked

    // What comes in never lands in a locked private map: updating one asks for the screen lock first, and without
    // it the question stays, to keep both instead. While they hide, Open asks first, and the question then becomes Update.
    fun update() {
        if (ask == ArrivalAsk.NewUntilUnlocked) {
            unlocking.ask()
        } else if (ask == ArrivalAsk.UpdateLocked) {
            unlocking.ask { result ->
                if (result == Unlocked.Yes) {
                    onAnswer(Arrival.Open)
                }
            }
        } else {
            onAnswer(Arrival.Open)
        }
    }

    AlertDialog(
        onDismissRequest = { onAnswer(Arrival.Decline) },
        icon = {
            EscapeCloses { onAnswer(Arrival.Decline) }
            Icon(Icons.Outlined.Downloading, contentDescription = null)
        },
        title = {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (count > 1) {
                    // Where this question is among those the files brought, as the media viewer counts its pages.
                    Text(
                        stringResource(R.string.nearby_arrival_position, number, count),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }

                Text(
                    when {
                        ask == ArrivalAsk.UpdateLocked && cover != null && from != null -> stringResource(R.string.private_arrival_newer_version_of_from, coverTitle(cover), from)
                        ask == ArrivalAsk.UpdateLocked && cover != null -> stringResource(R.string.private_arrival_another_version_of, coverTitle(cover))
                        ask == ArrivalAsk.UpdateLocked && from != null -> stringResource(R.string.private_arrival_newer_version_from, from)
                        ask == ArrivalAsk.UpdateLocked -> stringResource(R.string.private_arrival_another_version)
                        ask == ArrivalAsk.Update && from != null -> stringResource(R.string.nearby_arrival_newer_version_from, bundle.title, from)
                        ask == ArrivalAsk.Update -> stringResource(R.string.nearby_arrival_another_version, bundle.title)
                        from != null -> stringResource(R.string.nearby_arrival_sent_you_a_map, from)
                        else -> stringResource(R.string.nearby_arrival_a_map_arrived)
                    },
                    textAlign = TextAlign.Center,
                )
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    when {
                        !asNew && media.isEmpty() -> stringResource(R.string.nearby_arrival_update_yours)
                        !asNew -> stringResource(R.string.nearby_arrival_update_yours_with, media)
                        media.isEmpty() -> stringResource(R.string.nearby_arrival_title, bundle.title)
                        else -> stringResource(R.string.nearby_arrival_title_with, bundle.title, media)
                    },
                )

                if (address != null) {
                    // Names are whatever the sender says: the address is where it really came from.
                    Text(
                        stringResource(R.string.nearby_arrival_sent_from_address, address),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                if (check != null) {
                    // The sender shows the same code: when they match, no other device stepped in between.
                    Text(
                        withCheckCode(stringResource(R.string.nearby_check_code, check), check),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = ::update, enabled = !unlocking.asking, modifier = Modifier.focusRequester(rememberDefaultButton())) { Text(stringResource(if (asNew) R.string.common_open else R.string.nearby_update)) }
        },
        dismissButton = {
            // The two wrap one under the other when they don't fit side by side, as in Spanish at larger text.
            FlowRow(horizontalArrangement = Arrangement.End) {
                TextButton(onClick = { onAnswer(Arrival.Decline) }) { Text(stringResource(if (from != null) R.string.nearby_decline else R.string.common_cancel)) }

                if (!asNew) {
                    TextButton(onClick = { onAnswer(Arrival.KeepBoth) }) { Text(stringResource(R.string.nearby_keep_both)) }
                }
            }
        },
    )
}

/**
 * A map on its way from another device: this one shows the check code the
 * sender shows too, so the person sending can compare them before the map
 * goes. It comes once they do, as an offer to take ([ArrivalDialog]).
 */
@Composable
fun IncomingDialog(incoming: Incoming, onDecline: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDecline,
        icon = {
            EscapeCloses(onDecline)
            Icon(Icons.Outlined.Downloading, contentDescription = null)
        },
        title = { Text(stringResource(R.string.nearby_incoming_title, incoming.from)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    withCheckCode(stringResource(R.string.nearby_check_code, incoming.check), incoming.check),
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(stringResource(R.string.nearby_incoming_explanation))
                // Names are whatever the sender says: the address is where it really came from.
                Text(
                    stringResource(R.string.nearby_arrival_sent_from_address, incoming.address),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDecline, modifier = Modifier.focusRequester(rememberDefaultButton())) { Text(stringResource(R.string.nearby_decline)) }
        },
    )
}

/**
 * `text` with the check code `check` in it set apart, and said digit by digit, as the sender's own
 * dialog says it: comparing it is the safety step, so it sounds the same on both devices.
 */
internal fun withCheckCode(text: String, check: String): AnnotatedString = buildAnnotatedString {
    append(text)
    val start = text.indexOf(check)

    if (start >= 0) {
        addStyle(SpanStyle(fontWeight = FontWeight.SemiBold), start, start + check.length)
        addTtsAnnotation(VerbatimTtsAnnotation(check), start, start + check.length)
    }
}
