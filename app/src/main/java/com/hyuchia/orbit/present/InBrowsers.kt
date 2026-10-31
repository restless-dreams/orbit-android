package com.hyuchia.orbit.present

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AttachFile
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.hyuchia.orbit.R
import com.hyuchia.orbit.container
import com.hyuchia.orbit.data.MediaSummary
import com.hyuchia.orbit.data.Uncleaned
import com.hyuchia.orbit.nearby.AddressText
import com.hyuchia.orbit.nearby.BrowserPresenting
import com.hyuchia.orbit.nearby.JoiningControls
import com.hyuchia.orbit.nearby.JoiningOptions
import com.hyuchia.orbit.nearby.LockoutNote
import com.hyuchia.orbit.nearby.rememberAddress
import com.hyuchia.orbit.rememberWords
import com.hyuchia.orbit.theme.LocalAppSettings
import com.hyuchia.orbit.ui.EscapeCloses
import com.hyuchia.orbit.ui.MediaWords
import com.hyuchia.orbit.ui.SheetPage
import com.hyuchia.orbit.ui.WhatGoes
import com.hyuchia.orbit.ui.thisDeviceIcon
import com.hyuchia.orbit.ui.uncleanedLines
import kotlinx.coroutines.launch
import java.io.File

/**
 * The presentation in browsers on the local network while `on`: started,
 * handed to the map as an audience, and stopped when turned off or when the
 * presentation ends. Joining starts the way the last presentation's did.
 * The browsers get the map's files that can't go clean only when they're
 * among `asTheyAre`, as the presenter chose.
 */
@Composable
fun rememberBrowserPresenting(on: Boolean, mapFolder: File, asTheyAre: () -> Set<String>, onAudience: (Audience?) -> Unit): BrowserPresenting? {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settings = LocalAppSettings.current
    var presenting by remember { mutableStateOf<BrowserPresenting?>(null) }

    DisposableEffect(on) {
        val options = JoiningOptions(askNames = settings.presentAskNames, requirePin = settings.presentRequirePin, limit = settings.presentLimit)
        val made = if (on) BrowserPresenting(context.applicationContext, mapFolder, scope, options, asTheyAre).also { it.start() } else null
        presenting = made
        made?.let { onAudience(it.audience) }

        onDispose {
            if (made != null) {
                onAudience(null)
                made.audience.close()
                made.stop()
            }

            presenting = null
        }
    }

    return presenting
}

/**
 * Where to open the presentation in a browser, in a sheet: the address, its
 * QR code, the PIN when joining takes one, who's watching, and who may
 * join, and what goes with the map to those who do (`media`), with its
 * files that can't go clean (`uncleaned`) named: going as they are when
 * they're among `asTheyAre`, else staying on this device.
 */
@Composable
fun BrowserAddressSheet(presenting: BrowserPresenting, media: MediaSummary, uncleaned: Uncleaned, asTheyAre: Set<String>, onStop: () -> Unit, onDismiss: () -> Unit) {
    val address = rememberAddress(presenting) { presenting.address() }
    val words = rememberWords()
    val device = thisDeviceIcon()
    val lockedOut by presenting.lockedOut.collectAsState()
    val container = LocalContext.current.container
    val state = rememberBottomSheetState(initialValue = SheetValue.Hidden, enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded))
    val scope = rememberCoroutineScope()

    // The buttons slide the sheet away as swiping it down does, and then act.
    fun closing(then: () -> Unit) {
        scope.launch { state.hide() }.invokeOnCompletion { then() }
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = state) {
        EscapeCloses(onDismiss)
        KeepsBarsHidden()

        SheetPage(
            stringResource(R.string.present_show_in_a_browser),
            buttons = {
                TextButton(onClick = { closing(onStop) }) { Text(stringResource(R.string.present_stop_showing)) }
                TextButton(onClick = { closing(onDismiss) }) { Text(stringResource(R.string.common_done)) }
            },
        ) {
            if (address == null) {
                Text(
                    stringResource(R.string.present_connect_to_the_wifi),
                    style = MaterialTheme.typography.bodyLarge,
                )
            } else {
                Text(
                    stringResource(R.string.present_open_in_a_browser),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                AddressText(address.removePrefix("http://"), Modifier.fillMaxWidth())

                Surface(color = Color.White, shape = MaterialTheme.shapes.medium, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                    QrCode(address, Modifier.padding(12.dp).size(168.dp))
                }

                if (!media.isEmpty) {
                    WhatGoes(
                        stringResource(R.string.sharing_browsers_get, MediaWords.list(media, words)),
                        Icons.Outlined.AttachFile,
                        goes = true,
                        Modifier.fillMaxWidth(),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }

                uncleanedLines(uncleaned, asTheyAre, words, device).forEach { line ->
                    WhatGoes(line.text, line.icon, line.goes, Modifier.fillMaxWidth(), style = MaterialTheme.typography.bodySmall)
                }

                JoiningControls(
                    presenting.joining,
                    since = 0L,
                    onRemove = presenting::remove,
                    onChange = { options ->
                        presenting.joining.change(options)

                        // The next presentation starts the same way, even if this sheet closes at once.
                        container.applicationScope.launch {
                            container.settings.update {
                                it.copy(presentAskNames = options.askNames, presentRequirePin = options.requirePin, presentLimit = options.limit)
                            }
                        }
                    },
                )
                LockoutNote(lockedOut, since = 0L, message = R.string.present_lockout)
            }
        }
    }
}

/**
 * Keeps Android's bars hidden in the sheet this is placed in, as the
 * presentation under it keeps them: a sheet's own window brings them back,
 * and on a tablet the taskbar then floats over the sheet's buttons.
 */
@Composable
private fun KeepsBarsHidden() {
    val view = LocalView.current

    DisposableEffect(view) {
        val window = generateSequence(view.parent) { it.parent }.filterIsInstance<DialogWindowProvider>().firstOrNull()?.window

        if (window != null) {
            val controller = WindowCompat.getInsetsController(window, view)
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller.hide(WindowInsetsCompat.Type.systemBars())
        }

        onDispose {}
    }
}

/** A QR code of `text`, dark on whatever light background is behind it. TalkBack hears what it's for. */
@Composable
fun QrCode(text: String, modifier: Modifier = Modifier) {
    val matrix = remember(text) {
        QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, 0, 0, mapOf(EncodeHintType.MARGIN to 0))
    }

    val said = stringResource(R.string.present_qr_code_for_address)

    Canvas(modifier.semantics { contentDescription = said }) {
        val cell = size.minDimension / matrix.width

        for (y in 0 until matrix.height) {
            for (x in 0 until matrix.width) {
                if (matrix[x, y]) {
                    drawRect(Color.Black, topLeft = Offset(x * cell, y * cell), size = Size(cell + 0.5f, cell + 0.5f))
                }
            }
        }
    }
}
