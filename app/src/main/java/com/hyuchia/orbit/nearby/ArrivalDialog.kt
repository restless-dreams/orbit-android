package com.hyuchia.orbit.nearby

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Downloading
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.hyuchia.orbit.R
import com.hyuchia.orbit.data.MapBundle
import com.hyuchia.orbit.ui.EscapeCloses

/** What to do with a map that arrived from another device. */
enum class Arrival { Open, KeepBoth, Decline }

/**
 * Asks before taking in a map from another device, `from` at `address` (or
 * from a file, when null), with the `check` code the sender shows too. When
 * this device has the same map, the choice is to update it (keeping this
 * version in its history) or to keep both.
 */
@Composable
fun ArrivalDialog(from: String?, bundle: MapBundle, alreadyHere: Boolean, address: String? = null, check: String? = null, onAnswer: (Arrival) -> Unit) {
    val pictures = bundle.pictureCount
    AlertDialog(
        onDismissRequest = { onAnswer(Arrival.Decline) },
        icon = {
            EscapeCloses { onAnswer(Arrival.Decline) }
            Icon(Icons.Outlined.Downloading, contentDescription = null)
        },
        title = {
            Text(
                when {
                    alreadyHere && from != null -> stringResource(R.string.nearby_arrival_newer_version_from, bundle.title, from)
                    alreadyHere -> stringResource(R.string.nearby_arrival_another_version, bundle.title)
                    from != null -> stringResource(R.string.nearby_arrival_sent_you_a_map, from)
                    else -> stringResource(R.string.nearby_arrival_a_map_arrived)
                },
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    when {
                        alreadyHere && pictures == 0 -> stringResource(R.string.nearby_arrival_update_yours)
                        alreadyHere -> pluralStringResource(R.plurals.nearby_arrival_update_yours_with_pictures, pictures, pictures)
                        pictures == 0 -> stringResource(R.string.nearby_arrival_title, bundle.title)
                        else -> pluralStringResource(R.plurals.nearby_arrival_title_with_pictures, pictures, bundle.title, pictures)
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
                        withStyledPart(stringResource(R.string.nearby_check_code, check), check, SpanStyle(fontWeight = FontWeight.SemiBold)),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onAnswer(Arrival.Open) }) { Text(stringResource(if (alreadyHere) R.string.nearby_update else R.string.common_open)) }
        },
        dismissButton = {
            Row {
                TextButton(onClick = { onAnswer(Arrival.Decline) }) { Text(stringResource(if (from != null) R.string.nearby_decline else R.string.common_cancel)) }

                if (alreadyHere) {
                    TextButton(onClick = { onAnswer(Arrival.KeepBoth) }) { Text(stringResource(R.string.nearby_keep_both)) }
                }
            }
        },
    )
}

/** `text` with the first `part` of it in `style`, such as a code set apart in a sentence. */
internal fun withStyledPart(text: String, part: String, style: SpanStyle): AnnotatedString = buildAnnotatedString {
    append(text)
    val start = text.indexOf(part)

    if (start >= 0) {
        addStyle(style, start, start + part.length)
    }
}
