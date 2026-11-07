package com.hyuchia.orbit.map

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Compress
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.hyuchia.orbit.R
import com.hyuchia.orbit.rememberWords
import com.hyuchia.orbit.ui.EscapeCloses
import com.hyuchia.orbit.ui.MediaWords

/**
 * Asks whether to make big videos smaller on the phone as they come into the
 * map: how big they are, and about how big they'd be, at 1080p. Keeping them
 * as they are is offered while they fit as they are; dismissed, nothing comes
 * in.
 */
@Composable
fun SmallerOfferDialog(offer: SmallerOffer, onAnswer: (SmallerAnswer) -> Unit) {
    val words = rememberWords()
    val size = MediaWords.size(offer.size, words)
    val estimate = MediaWords.size(offer.estimate, words)

    val text = if (offer.keepable) {
        pluralStringResource(R.plurals.map_smaller_offer, offer.videos, size, estimate, offer.videos)
    } else {
        pluralStringResource(R.plurals.map_smaller_offer_too_big, offer.videos, size, estimate, offer.videos)
    }

    AlertDialog(
        onDismissRequest = { onAnswer(SmallerAnswer.Neither) },
        icon = { Icon(Icons.Outlined.Compress, contentDescription = null) },
        title = {
            EscapeCloses { onAnswer(SmallerAnswer.Neither) }
            Text(pluralStringResource(R.plurals.map_smaller_offer_title, offer.videos))
        },
        text = { Text(text) },
        confirmButton = {
            TextButton(onClick = { onAnswer(SmallerAnswer.Smaller) }) { Text(pluralStringResource(R.plurals.map_make_it_smaller, offer.videos)) }
        },
        dismissButton = {
            if (offer.keepable) {
                TextButton(onClick = { onAnswer(SmallerAnswer.AsTheyAre) }) { Text(pluralStringResource(R.plurals.map_keep_it_as_it_is, offer.videos)) }
            } else {
                TextButton(onClick = { onAnswer(SmallerAnswer.Neither) }) { Text(stringResource(R.string.common_cancel)) }
            }
        },
    )
}
