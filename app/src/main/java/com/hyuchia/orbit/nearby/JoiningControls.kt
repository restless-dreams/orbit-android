package com.hyuchia.orbit.nearby

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PersonRemove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hyuchia.orbit.R
import com.hyuchia.orbit.rememberWords
import com.hyuchia.orbit.ui.EscapeCloses
import kotlinx.coroutines.delay
import java.text.NumberFormat

/**
 * The phone's side of joining a room: the PIN while one is needed, who's
 * in with a button to remove each, and the choices for who may join. Wrong
 * PINs are noted from `since` on. `onChange` takes new choices.
 */
@Composable
fun JoiningControls(joining: Joining, since: Long, onRemove: (Member) -> Unit, onChange: (JoiningOptions) -> Unit) {
    val options by joining.options.collectAsState()
    val pin by joining.pin.collectAsState()
    val members by joining.members.collectAsState()
    val pinLockedOut by joining.pinLockedOut.collectAsState()
    val kind = joining.kind
    var removing by remember { mutableStateOf<Member?>(null) }

    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        pin?.let { PinCard(it, kind, onNewPin = joining::replacePin) }
        Members(members, kind, onRemove = { removing = it })
        Choices(options, kind, onChange)
        LockoutNote(pinLockedOut, since, message = R.string.nearby_lockout_make_a_new_pin)
    }

    removing?.let { member ->
        val close = { removing = null }
        AlertDialog(
            onDismissRequest = close,
            title = {
                EscapeCloses(close)
                Text(stringResource(R.string.nearby_remove_member_question, member.name))
            },
            text = {
                Text(
                    if (kind == RoomKind.Presentation) {
                        stringResource(R.string.nearby_remove_from_the_presentation_explanation)
                    } else {
                        stringResource(R.string.nearby_remove_from_the_shared_map_explanation)
                    },
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    onRemove(member)
                    close()
                }) { Text(stringResource(R.string.common_remove)) }
            },
            dismissButton = { TextButton(onClick = close) { Text(stringResource(R.string.common_cancel)) } },
        )
    }
}

/** The PIN, large enough to read out across a room, and a button for a new one. */
@Composable
private fun PinCard(pin: String, kind: RoomKind, onNewPin: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth()) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 4.dp),
        ) {
            Text(stringResource(R.string.nearby_pin), style = MaterialTheme.typography.labelLarge)
            // Read out digit by digit, as it's said aloud.
            val spoken = stringResource(R.string.nearby_pin_read_aloud, pin.toList().joinToString(" "))
            Text(
                pin,
                style = MaterialTheme.typography.displayMedium.copy(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.SemiBold, letterSpacing = 6.sp),
                modifier = Modifier.semantics { contentDescription = spoken },
            )
            Text(
                if (kind == RoomKind.Presentation) {
                    stringResource(R.string.nearby_pin_say_it_aloud)
                } else {
                    stringResource(R.string.nearby_pin_type_it_on_the_computer)
                },
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center,
            )

            // The card's own colors, whatever the map's look makes them.
            TextButton(onClick = onNewPin, colors = ButtonDefaults.textButtonColors(contentColor = LocalContentColor.current)) {
                Text(stringResource(R.string.nearby_new_pin))
            }
        }
    }
}

/** Who's in, with when each joined, and a button to remove each. */
@Composable
private fun Members(members: List<Member>, kind: RoomKind, onRemove: (Member) -> Unit) {
    val now by produceState(System.currentTimeMillis()) {
        while (true) {
            delay(15_000)
            value = System.currentTimeMillis()
        }
    }

    val words = rememberWords()

    Column(Modifier.fillMaxWidth()) {
        Heading(stringResource(if (kind == RoomKind.Presentation) R.string.nearby_who_is_watching else R.string.nearby_who_opened_it))

        if (members.isEmpty()) {
            Text(
                stringResource(if (kind == RoomKind.Presentation) R.string.nearby_no_one_is_watching_yet else R.string.nearby_no_one_has_opened_it_yet),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 8.dp),
            )
        }

        members.forEach { member ->
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
                Column(Modifier.weight(1f)) {
                    Text(
                        member.name,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    // A computer is easier to tell apart by its address than by "Guest".
                    val joined = joinedAgo(member.joined, now, words)
                    Text(
                        if (kind == RoomKind.Share) "$joined · ${member.address}" else joined,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                IconButton(onClick = { onRemove(member) }) {
                    Icon(Icons.Outlined.PersonRemove, contentDescription = stringResource(R.string.nearby_remove_member, member.name))
                }
            }
        }
    }
}

/** Who may join: names, the PIN, how many at once, and whether anyone new may. */
@Composable
private fun Choices(options: JoiningOptions, kind: RoomKind, onChange: (JoiningOptions) -> Unit) {
    val presentation = kind == RoomKind.Presentation
    val locale = LocalConfiguration.current.locales[0]
    val numbers = remember(locale) { NumberFormat.getIntegerInstance(locale) }

    Column(Modifier.fillMaxWidth()) {
        Heading(stringResource(if (presentation) R.string.nearby_who_can_join else R.string.nearby_who_can_open_it))

        if (presentation) {
            Choice(
                stringResource(R.string.nearby_ask_for_names),
                stringResource(R.string.nearby_ask_for_names_description),
                options.askNames,
            ) { onChange(options.copy(askNames = it)) }
        }

        Choice(
            stringResource(R.string.nearby_require_a_pin),
            stringResource(if (presentation) R.string.nearby_require_a_pin_to_join_description else R.string.nearby_require_a_pin_to_open_description),
            options.requirePin,
        ) { onChange(options.copy(requirePin = it)) }
        Text(
            stringResource(R.string.nearby_how_many_at_once),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(top = 8.dp),
        )

        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            JOINING_LIMITS.forEach { limit ->
                FilterChip(
                    selected = options.limit == limit,
                    onClick = { onChange(options.copy(limit = limit)) },
                    label = { Text(if (limit == 0) stringResource(R.string.nearby_no_limit) else numbers.format(limit)) },
                )
            }
        }

        Choice(
            stringResource(R.string.nearby_let_new_people_in),
            stringResource(R.string.nearby_let_new_people_in_description),
            options.open,
        ) { onChange(options.copy(open = it)) }
    }
}

/** A choice that's on or off: the whole row switches it. */
@Composable
private fun Choice(title: String, description: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .toggleable(value = checked, role = Role.Switch, onValueChange = onChange)
            .padding(vertical = 8.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
            Text(description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        Switch(checked = checked, onCheckedChange = null)
    }
}

@Composable
private fun Heading(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(vertical = 4.dp).semantics { heading() },
    )
}
