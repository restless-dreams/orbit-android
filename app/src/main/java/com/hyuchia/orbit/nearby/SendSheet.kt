package com.hyuchia.orbit.nearby

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.outlined.AttachFile
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Computer
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Devices
import androidx.compose.material.icons.outlined.Pin
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.hyuchia.orbit.R
import com.hyuchia.orbit.Words
import com.hyuchia.orbit.container
import com.hyuchia.orbit.data.MediaSummary
import com.hyuchia.orbit.data.OutputFailed
import com.hyuchia.orbit.data.Uncleaned
import com.hyuchia.orbit.data.readableSize
import com.hyuchia.orbit.data.requireRoom
import com.hyuchia.orbit.data.watched
import com.hyuchia.orbit.present.QrCode
import com.hyuchia.orbit.rememberWords
import com.hyuchia.orbit.theme.LocalAppSettings
import com.hyuchia.orbit.theme.mapTheme
import com.hyuchia.orbit.ui.EscapeCloses
import com.hyuchia.orbit.ui.MediaWords
import com.hyuchia.orbit.ui.SheetPage
import com.hyuchia.orbit.ui.WhatGoes
import com.hyuchia.orbit.ui.exportName
import com.hyuchia.orbit.ui.rememberAsTheyAre
import com.hyuchia.orbit.ui.rememberDefaultButton
import com.hyuchia.orbit.ui.shareMapExport
import com.hyuchia.orbit.ui.thisDeviceIcon
import com.hyuchia.orbit.ui.uncleanedLines
import kotlinx.coroutines.CompletableDeferred
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
    // What the person sending says about the codes, by device, while both show one: the map goes only once they match.
    val checks = remember { mutableStateMapOf<String, Pair<String, CompletableDeferred<Boolean>>>() }
    // Devices reached by their address, for networks where they can't find each other.
    val typed = remember { mutableStateListOf<String>() }
    var typingAddress by remember { mutableStateOf(false) }
    // While the map is open on a computer: those of its files that can't go clean that go as they are.
    var computer by remember { mutableStateOf<Set<String>?>(null) }
    var sharingFailed by remember { mutableStateOf(false) }
    val words = rememberWords()
    val thisDevice = thisDeviceIcon()
    // Files that can't go clean stay on this device unless the person chooses, as the first way to send starts, to send them as they are.
    val asTheyAre = rememberAsTheyAre()
    val chosen = asTheyAre.chosen
    // What goes with the map, for each way to send it to say.
    val uncleaned by produceState(Uncleaned(), mapId) { value = container.maps.uncleaned(mapId) }
    val media by produceState(MediaSummary(), mapId, uncleaned, chosen) { value = container.maps.mediaSummary(mapId, leavingOut = uncleaned.names - chosen) }
    val keptInFolder = LocalAppSettings.current.syncFolder != null
    val pastOlderZips by produceState(false, mapId) { value = container.maps.pastOlderZips(mapId) }

    /** Sends the map to `device`, with those of its files that can't go clean in `names` as they are. */
    fun sendTo(device: NearbyDevice, names: Set<String>) {
        val address = device.address

        scope.launch {
            // The map goes from a file, so even a big one is never in memory whole: made only once there's room for all of it.
            val bundle = try {
                withContext(Dispatchers.IO) {
                    val folder = File(context.cacheDir, "sending")
                    folder.mkdirs()
                    // Left by a send that never finished, when Orbit was stopped halfway.
                    folder.listFiles().orEmpty().filter { it.lastModified() < System.currentTimeMillis() - STALE_MILLISECONDS }.forEach { it.delete() }
                    val file = File.createTempFile("map", ".zip", folder)

                    try {
                        file.outputStream().watched().use { container.maps.writeZip(mapId, it, names) { size -> requireRoom(folder, size) } }
                    } catch (failed: IOException) {
                        file.delete()
                        throw failed
                    }

                    file
                }
            } catch (full: OutputFailed) {
                states[address] = SendState.Failed(R.string.nearby_send_no_room_here)
                return@launch
            } catch (failed: IOException) {
                states[address] = SendState.Failed(R.string.nearby_send_could_not_get_the_map_ready)
                return@launch
            }

            val confirm: suspend (String) -> Boolean = { check ->
                val answer = CompletableDeferred<Boolean>()
                checks[address] = device.name to answer

                try {
                    answer.await()
                } finally {
                    checks.remove(address)
                }
            }

            try {
                sendMap(address, device.key, device.version, host.name, bundle, confirm) { sent -> states[address] = sent }
            } finally {
                bundle.delete()
            }
        }
    }

    fun send(device: NearbyDevice) {
        val address = device.address
        val state = states[address]

        if (state is SendState.Sending || state is SendState.Confirming || state is SendState.Waiting) {
            return
        }

        asTheyAre.then(uncleaned) { names -> sendTo(device, names) }
    }

    val state = rememberBottomSheetState(initialValue = SheetValue.Hidden, enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded))
    // Forward slides in from where reading goes.
    val forward = if (LocalLayoutDirection.current == LayoutDirection.Ltr) 1 else -1

    // Every way to send fits on the screen at once, so it opens all the way; on a phone on its side, it scrolls.
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = state) {
        // From the computer's step, Escape and Back go back to the ways to send; from those, they close the sheet.
        EscapeCloses { if (computer != null) computer = null else onDismiss() }
        BackHandler(enabled = computer != null) { computer = null }

        // Opening the map on a computer is the sheet's next step, in the place of the ways to send, not a sheet over them.
        AnimatedContent(
            targetState = computer,
            contentKey = { it != null },
            transitionSpec = {
                val side = if (targetState != null) forward else -forward

                (slideInHorizontally { width -> side * width / 4 } + fadeIn()) togetherWith (slideOutHorizontally { width -> -side * width / 4 } + fadeOut())
            },
            label = "send step",
        ) { names ->
            if (names != null) {
                ComputerStep(
                    mapId,
                    media,
                    uncleaned,
                    names,
                    onBack = { computer = null },
                    onDone = { scope.launch { state.hide() }.invokeOnCompletion { onDismiss() } },
                )
            } else {
                Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).navigationBarsPadding().padding(bottom = 16.dp)) {
                    Text(
                        stringResource(R.string.nearby_send_map, title),
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.padding(start = 24.dp, end = 24.dp),
                    )

                    // What the other device gets, and what sending isn't: before anything goes.
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 4.dp, bottom = 8.dp)) {
                        WhatGoes(sendsACopy(media, words), Icons.Outlined.ContentCopy, goes = false, style = MaterialTheme.typography.bodyMedium)
                        // What can't go clean, named: staying here, or going as it is once the person chose so.
                        uncleanedLines(uncleaned, chosen, words, thisDevice).forEach { line -> WhatGoes(line.text, line.icon, line.goes, style = MaterialTheme.typography.bodyMedium) }

                        if (!keptInFolder) {
                            WhatGoes(stringResource(R.string.sharing_copies_not_in_step), Icons.Outlined.Sync, goes = false, style = MaterialTheme.typography.bodyMedium)
                        }
                    }

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

                                    // Each step of a send is said as it comes: the code to compare, then whether it was opened.
                                    Text(
                                        describe(states[device.address]) ?: AnnotatedString(stringResource(R.string.nearby_tap_to_send)),
                                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                                    )
                                }
                            },
                            // Devices don't say what they are: a phone and a tablet get the same neutral icon.
                            leadingContent = { Icon(Icons.Outlined.Devices, contentDescription = null) },
                            trailingContent = {
                                when (states[device.address]) {
                                    is SendState.Sending, is SendState.Confirming, is SendState.Waiting -> LoadingIndicator(Modifier.size(28.dp))
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
                        supportingContent = {
                            if (sharingFailed) {
                                Text(
                                    stringResource(R.string.nearby_send_could_not_get_the_map_ready),
                                    color = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                                )
                            } else {
                                Column {
                                    Text(stringResource(R.string.nearby_share_as_a_file_description))
                                    MediaWords.with(media, words)?.let { WhatGoes(it, Icons.Outlined.AttachFile, goes = true, Modifier.padding(top = 4.dp)) }

                                    // A zip past what older versions of Orbit open says to update Orbit wherever it's opened.
                                    if (pastOlderZips) {
                                        WhatGoes(stringResource(R.string.sharing_zip_past_older_orbits), Icons.Outlined.SystemUpdate, goes = false, Modifier.padding(top = 4.dp))
                                    }
                                }
                            }
                        },
                        leadingContent = { Icon(Icons.Outlined.Share, contentDescription = null) },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        modifier = Modifier.clickable {
                            asTheyAre.then(uncleaned) { names ->
                                scope.launch {
                                    sharingFailed = false

                                    // The zip waits in the phone's storage for the app it goes to, and the phone may be out of room.
                                    // A private map that locks while it's written opens nothing.
                                    try {
                                        shareMapExport(context, mapId, exportName(context, title, "zip"), "application/zip", title) { container.maps.writeZip(mapId, it, names, room = this) }
                                    } catch (failed: IOException) {
                                        sharingFailed = true
                                    }
                                }
                            }
                        },
                    )
                    ListItem(
                        headlineContent = { Text(stringResource(R.string.nearby_open_on_a_computer)) },
                        supportingContent = { Text(stringResource(R.string.nearby_open_on_a_computer_description)) },
                        leadingContent = { Icon(Icons.Outlined.Computer, contentDescription = null) },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        // The computer's browser gets what the person chose for the files that can't go clean, asked here before it opens.
                        modifier = Modifier.clickable { asTheyAre.then(uncleaned) { names -> computer = names } },
                    )
                }
            }
        }
    }

    // One device's code at a time: each waits for the person sending to compare it with what that device shows.
    checks.entries.firstOrNull()?.let { (address, waiting) ->
        val (name, answer) = waiting
        val confirming = states[address] as? SendState.Confirming

        if (confirming != null) {
            CheckCodeDialog(name, confirming.check, confirming.older) { matches -> answer.complete(matches) }
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

}

/** What sending does, said before anything goes: the other device gets a copy, with the map's own files. */
private fun sendsACopy(media: MediaSummary, words: Words): String =
    if (media.isEmpty) words.string(R.string.sharing_send_copy) else words.string(R.string.sharing_send_copy_with, MediaWords.list(media, words))

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .padding(start = 24.dp, end = 24.dp, top = 8.dp, bottom = 4.dp)
            .semantics { heading() },
    )
}

@Composable
private fun describe(state: SendState?): AnnotatedString? = when (state) {
    null -> null
    SendState.Sending -> AnnotatedString(stringResource(R.string.nearby_sending))
    // Both devices show the code before anything of the map goes.
    is SendState.Confirming -> withCheckCode(stringResource(R.string.nearby_check_that_it_shows, state.check), state.check)
    // The same code shows beside the map there: when it matches, the map reached the device in front of them.
    is SendState.Waiting -> withCheckCode(stringResource(R.string.nearby_other_device_should_show, state.check), state.check)
    SendState.Opened -> AnnotatedString(stringResource(R.string.nearby_opened_there))
    SendState.Declined -> AnnotatedString(stringResource(R.string.nearby_not_taken))
    SendState.Cancelled -> AnnotatedString(stringResource(R.string.nearby_not_sent))
    is SendState.Failed -> AnnotatedString(stringResource(state.reason))
    is SendState.TooBigThere -> AnnotatedString(stringResource(R.string.nearby_send_update_for_size, readableSize(state.size, rememberWords())))
}

/**
 * Asks the person sending whether `device` shows the same code as this one,
 * before anything of the map goes: when it doesn't, another device on the
 * Wi-Fi answered in its place, and it gets nothing. With an older Orbit
 * there, it also says what won't open there (`older`), so the person knows
 * before saying yes.
 */
@Composable
private fun CheckCodeDialog(device: String, check: String, older: OlderThere?, onAnswer: (matches: Boolean) -> Unit) {
    val words = rememberWords()
    AlertDialog(
        onDismissRequest = { onAnswer(false) },
        icon = {
            EscapeCloses { onAnswer(false) }
            Icon(Icons.Outlined.Pin, contentDescription = null)
        },
        title = { Text(stringResource(R.string.nearby_check_the_code_title, device)) },
        text = {
            // A phone on its side has little height for all of it.
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.verticalScroll(rememberScrollState()),
            ) {
                Text(
                    check,
                    style = MaterialTheme.typography.displayMedium.copy(fontFeatureSettings = "tnum"),
                    // Read out digit by digit, as people compare them.
                    modifier = Modifier.semantics { contentDescription = check.toList().joinToString(" ") },
                )
                Text(stringResource(R.string.nearby_check_the_code_explanation), style = MaterialTheme.typography.bodyMedium)

                if (older != null) {
                    WhatGoes(wontOpenThere(older, words), Icons.Outlined.SystemUpdate, goes = false, Modifier.fillMaxWidth(), style = MaterialTheme.typography.bodyMedium)
                }
            }
        },
        confirmButton = { TextButton(onClick = { onAnswer(true) }, modifier = Modifier.focusRequester(rememberDefaultButton())) { Text(stringResource(R.string.nearby_yes_send)) } },
        dismissButton = { TextButton(onClick = { onAnswer(false) }) { Text(stringResource(R.string.common_cancel)) } },
    )
}

/** What an older Orbit on the other device won't open of the map, said before it goes: "it" is that device. */
internal fun wontOpenThere(older: OlderThere, words: Words): String {
    val count = older.staysHere.count
    val staying = MediaWords.list(older.staysHere, words)

    return when {
        older.showsOnly && count > 0 -> words.plural(R.plurals.nearby_send_older_orbit_shows_only_without, count, staying)
        older.showsOnly -> words.string(R.string.nearby_send_older_orbit_shows_only)
        else -> words.plural(R.plurals.nearby_send_older_orbit_without, count, staying)
    }
}

/** The address typed for another device, a host and its port, or null when it can't be one. */
internal fun typedAddress(typed: String): String? {
    val address = typed.trim()
    val port = address.substringAfterLast(':').toIntOrNull()
    return address.takeIf { TYPED_ADDRESS.matches(it) && port != null && port in PORTS }
}

private val TYPED_ADDRESS = Regex("[A-Za-z0-9.-]+:[0-9]{2,5}")

/** For networks where devices can't find each other: the address the other device shows in its settings. */
@Composable
private fun AddressDialog(onSend: (String) -> Unit, onDismiss: () -> Unit) {
    var address by remember { mutableStateOf("") }
    val valid = typedAddress(address) != null
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
        confirmButton = { TextButton(onClick = { typedAddress(address)?.let(onSend) }, enabled = valid) { Text(stringResource(R.string.nearby_send)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) } },
    )
}

/**
 * The send sheet's step for a computer's browser: the map at an address,
 * with its QR code, while the step shows, who opened it, and who may.
 * Opening it starts the way it did last time. The browser gets the map's
 * files that can't go clean (`uncleaned`) only when they're among
 * `asTheyAre`, as the person chose before it opened. `onBack` goes back to
 * the ways to send, and `onDone` closes the sheet.
 */
@Composable
private fun ComputerStep(mapId: String, media: MediaSummary, uncleaned: Uncleaned, asTheyAre: Set<String>, onBack: () -> Unit, onDone: () -> Unit) {
    val context = LocalContext.current
    val words = rememberWords()
    val device = thisDeviceIcon()
    val container = context.container
    val host = container.nearby
    val theme = MaterialTheme.colorScheme.mapTheme()
    val settings = LocalAppSettings.current

    val shared = remember(mapId) {
        host.share(mapId, theme, JoiningOptions(askNames = false, requirePin = settings.shareRequirePin, limit = settings.shareLimit), asTheyAre)
    }

    DisposableEffect(shared) { onDispose { host.unshare(shared.code) } }
    val address = rememberAddress(shared) { host.address()?.let { "http://$it/${shared.code}" } }
    val opened = remember { System.currentTimeMillis() }
    val lockedOut by host.lockedOut.collectAsState()

    SheetPage(
        stringResource(R.string.nearby_open_on_a_computer),
        onBack = onBack,
        buttons = { TextButton(onClick = onDone) { Text(stringResource(R.string.common_done)) } },
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

            Surface(color = Color.White, shape = MaterialTheme.shapes.medium, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                QrCode(address, Modifier.padding(12.dp).size(168.dp))
            }

            if (!media.isEmpty) {
                WhatGoes(
                    stringResource(R.string.sharing_computer_gets, MediaWords.list(media, words)),
                    Icons.Outlined.AttachFile,
                    goes = true,
                    Modifier.fillMaxWidth(),
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            uncleanedLines(uncleaned, asTheyAre, words, device).forEach { line ->
                WhatGoes(line.text, line.icon, line.goes, Modifier.fillMaxWidth(), style = MaterialTheme.typography.bodySmall)
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

                    // The next time starts the same way, even if this step closes at once.
                    container.applicationScope.launch {
                        container.settings.update { it.copy(shareRequirePin = options.requirePin, shareLimit = options.limit) }
                    }
                },
            )
            LockoutNote(lockedOut, since = opened, message = R.string.nearby_lockout_stop_sharing)
        }
    }
}
