package com.hyuchia.orbit.nearby

import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Computer
import androidx.compose.material.icons.outlined.Devices
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.TabletAndroid
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.hyuchia.orbit.R
import com.hyuchia.orbit.container
import com.hyuchia.orbit.present.QrCode
import com.hyuchia.orbit.theme.LocalAppSettings
import com.hyuchia.orbit.theme.mapTheme
import com.hyuchia.orbit.ui.EscapeCloses
import com.hyuchia.orbit.ui.exportFile
import com.hyuchia.orbit.ui.exportName
import com.hyuchia.orbit.ui.shareFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

/** How old a map left in the sending folder is before it's cleared away: older than any send takes. */
private const val STALE_MILLISECONDS = 60 * 60 * 1000L

/**
 * Sends a map to another device: to a nearby one with Orbit open on the
 * same Wi-Fi, in one tap; as a file through any app; or to a computer's
 * browser, to explore, present or download it.
 */
@Composable
fun SendSheet(mapId: String, title: String, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val container = context.container
    val host = container.nearby
    val scope = rememberCoroutineScope()
    val devices = rememberNearbyDevices(host.selfId)
    val states = remember { mutableStateMapOf<String, SendState>() }
    // Devices reached by their address, for networks where they can't find each other.
    val typed = remember { mutableStateListOf<String>() }
    var typingAddress by remember { mutableStateOf(false) }
    var computer by remember { mutableStateOf(false) }

    fun send(device: NearbyDevice) {
        val address = device.address

        if (states[address] is SendState.Sending || states[address] is SendState.Waiting) {
            return
        }

        scope.launch {
            // The map goes from a file, so even a big one is never in memory whole.
            val bundle = try {
                withContext(Dispatchers.IO) {
                    val folder = File(context.cacheDir, "sending")
                    folder.mkdirs()
                    // Left by a send that never finished, when Orbit was stopped halfway.
                    folder.listFiles().orEmpty().filter { it.lastModified() < System.currentTimeMillis() - STALE_MILLISECONDS }.forEach { it.delete() }
                    File.createTempFile("map", ".zip", folder).also { file -> file.outputStream().use { container.maps.writeZip(mapId, it) } }
                }
            } catch (failed: IOException) {
                states[address] = SendState.Failed(R.string.nearby_send_could_not_get_the_map_ready)
                return@launch
            }

            try {
                sendMap(address, device.key, host.name, bundle) { state -> states[address] = state }
            } finally {
                bundle.delete()
            }
        }
    }

    // Every way to send fits on the screen at once, so it opens all the way; on a phone on its side, it scrolls.
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        EscapeCloses(onDismiss)

        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).navigationBarsPadding().padding(bottom = 16.dp)) {
            Text(
                stringResource(R.string.nearby_send_map, title),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(start = 24.dp, end = 24.dp, bottom = 8.dp),
            )
            SectionTitle(stringResource(R.string.nearby_devices_nearby))

            if (devices.isEmpty() && typed.isEmpty()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
                ) {
                    LoadingIndicator(Modifier.size(36.dp))
                    Text(
                        stringResource(R.string.nearby_looking_for_devices),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            val listed = devices + typed.filter { address -> devices.none { it.address == address } }.map { NearbyDevice(it, it) }

            listed.forEach { device ->
                ListItem(
                    headlineContent = { Text(device.name) },
                    supportingContent = {
                        Column {
                            // Any device can call itself anything: the address tells two of the same name apart.
                            if (device.address != device.name) {
                                Text(device.address)
                            }

                            Text(describe(states[device.address]) ?: AnnotatedString(stringResource(R.string.nearby_tap_to_send)))
                        }
                    },
                    leadingContent = { Icon(Icons.Outlined.TabletAndroid, contentDescription = null) },
                    trailingContent = {
                        when (states[device.address]) {
                            is SendState.Sending, is SendState.Waiting -> LoadingIndicator(Modifier.size(28.dp))
                            is SendState.Opened -> Icon(
                                Icons.Outlined.CheckCircle,
                                contentDescription = stringResource(R.string.nearby_opened),
                                tint = MaterialTheme.colorScheme.primary,
                            )
                            else -> Unit
                        }
                    },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    modifier = Modifier.clickable { send(device) },
                )
            }

            TextButton(onClick = { typingAddress = true }, modifier = Modifier.padding(horizontal = 12.dp)) {
                Text(stringResource(R.string.nearby_not_listed_enter_its_address))
            }

            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            SectionTitle(stringResource(R.string.nearby_other_ways))
            ListItem(
                headlineContent = { Text(stringResource(R.string.nearby_share_as_a_file)) },
                supportingContent = { Text(stringResource(R.string.nearby_share_as_a_file_description)) },
                leadingContent = { Icon(Icons.Outlined.Share, contentDescription = null) },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                modifier = Modifier.clickable {
                    scope.launch {
                        val uri = exportFile(context, exportName(context, title, "zip")) { container.maps.writeZip(mapId, it) }
                        shareFile(context, uri, "application/zip", title)
                    }
                },
            )
            ListItem(
                headlineContent = { Text(stringResource(R.string.nearby_open_on_a_computer)) },
                supportingContent = { Text(stringResource(R.string.nearby_open_on_a_computer_description)) },
                leadingContent = { Icon(Icons.Outlined.Computer, contentDescription = null) },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                modifier = Modifier.clickable { computer = true },
            )
        }
    }

    if (typingAddress) {
        AddressDialog(
            onSend = { address ->
                typingAddress = false

                if (address !in typed) {
                    typed += address
                }

                send(NearbyDevice(address, address))
            },
            onDismiss = { typingAddress = false },
        )
    }

    if (computer) {
        ComputerDialog(mapId, onDismiss = { computer = false })
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 8.dp, bottom = 4.dp),
    )
}

@Composable
private fun describe(state: SendState?): AnnotatedString? = when (state) {
    null -> null
    SendState.Sending -> AnnotatedString(stringResource(R.string.nearby_sending))
    // The same code shows beside the map there: when it matches, the map reached the device in front of them.
    is SendState.Waiting -> withStyledPart(
        stringResource(R.string.nearby_other_device_should_show, state.check),
        state.check,
        SpanStyle(fontWeight = FontWeight.SemiBold),
    )
    SendState.Opened -> AnnotatedString(stringResource(R.string.nearby_opened_there))
    SendState.Declined -> AnnotatedString(stringResource(R.string.nearby_not_taken))
    is SendState.Failed -> AnnotatedString(stringResource(state.reason))
}

/** For networks where devices can't find each other: the address the other device shows in its settings. */
@Composable
private fun AddressDialog(onSend: (String) -> Unit, onDismiss: () -> Unit) {
    var address by remember { mutableStateOf("") }
    val valid = Regex("[A-Za-z0-9.-]+:[0-9]{2,5}").matches(address.trim())
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            EscapeCloses(onDismiss)
            Icon(Icons.Outlined.Devices, contentDescription = null)
        },
        title = { Text(stringResource(R.string.nearby_send_to_an_address)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    stringResource(R.string.nearby_send_to_an_address_explanation),
                    style = MaterialTheme.typography.bodyMedium,
                )
                OutlinedTextField(
                    value = address,
                    onValueChange = { address = it },
                    placeholder = { Text("192.168.1.5:8766") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                )
            }
        },
        confirmButton = { TextButton(onClick = { onSend(address.trim()) }, enabled = valid) { Text(stringResource(R.string.nearby_send)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) } },
    )
}

/**
 * The map at an address for a computer's browser, with its QR code, while
 * this dialog is open: who opened it, and who may. Opening it starts the
 * way it did last time.
 */
@Composable
private fun ComputerDialog(mapId: String, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val container = context.container
    val host = container.nearby
    val theme = MaterialTheme.colorScheme.mapTheme()
    val settings = LocalAppSettings.current

    val shared = remember(mapId) {
        host.share(mapId, theme, JoiningOptions(askNames = false, requirePin = settings.shareRequirePin, limit = settings.shareLimit))
    }

    DisposableEffect(shared) { onDispose { host.unshare(shared.code) } }
    val address = remember(shared) { host.address()?.let { "http://$it/${shared.code}" } }
    val opened = remember { System.currentTimeMillis() }
    val lockedOut by host.lockedOut.collectAsState()
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            EscapeCloses(onDismiss)
            Icon(Icons.Outlined.Computer, contentDescription = null)
        },
        title = { Text(stringResource(R.string.nearby_open_on_a_computer)) },
        text = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.verticalScroll(rememberScrollState()),
            ) {
                if (address == null) {
                    Text(stringResource(R.string.nearby_connect_to_the_computer_wifi), style = MaterialTheme.typography.bodyLarge)
                } else {
                    Text(
                        stringResource(R.string.nearby_open_in_the_computer_browser),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    AddressText(address.removePrefix("http://"), Modifier.fillMaxWidth())

                    Surface(color = Color.White, shape = MaterialTheme.shapes.medium) {
                        QrCode(address, Modifier.padding(12.dp).size(168.dp))
                    }

                    Text(
                        stringResource(R.string.nearby_shared_map_stays_while_this_is_open),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    JoiningControls(
                        shared.joining,
                        since = opened,
                        onRemove = { member -> shared.joining.remove(member.id) },
                        onChange = { options ->
                            shared.joining.change(options)

                            // The next time starts the same way, even if this dialog closes at once.
                            container.applicationScope.launch {
                                container.settings.update { it.copy(shareRequirePin = options.requirePin, shareLimit = options.limit) }
                            }
                        },
                    )
                    LockoutNote(lockedOut, since = opened, message = R.string.nearby_lockout_stop_sharing)
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_done)) } },
    )
}
