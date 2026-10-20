package com.hyuchia.orbit.present

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.hyuchia.orbit.R
import com.hyuchia.orbit.container
import com.hyuchia.orbit.nearby.AddressText
import com.hyuchia.orbit.nearby.BrowserPresenting
import com.hyuchia.orbit.nearby.JoiningControls
import com.hyuchia.orbit.nearby.JoiningOptions
import com.hyuchia.orbit.nearby.LockoutNote
import com.hyuchia.orbit.nearby.rememberAddress
import com.hyuchia.orbit.theme.LocalAppSettings
import com.hyuchia.orbit.ui.EscapeCloses
import kotlinx.coroutines.launch
import java.io.File

/**
 * The presentation in browsers on the local network while `on`: started,
 * handed to the map as an audience, and stopped when turned off or when the
 * presentation ends. Joining starts the way the last presentation's did.
 */
@Composable
fun rememberBrowserPresenting(on: Boolean, mapFolder: File, onAudience: (Audience?) -> Unit): BrowserPresenting? {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settings = LocalAppSettings.current
    var presenting by remember { mutableStateOf<BrowserPresenting?>(null) }

    DisposableEffect(on) {
        val options = JoiningOptions(askNames = settings.presentAskNames, requirePin = settings.presentRequirePin, limit = settings.presentLimit)
        val made = if (on) BrowserPresenting(context.applicationContext, mapFolder, scope, options).also { it.start() } else null
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
 * Where to open the presentation in a browser: the address, its QR code,
 * the PIN when joining takes one, who's watching, and who may join.
 */
@Composable
fun BrowserAddressDialog(presenting: BrowserPresenting, onStop: () -> Unit, onDismiss: () -> Unit) {
    val address = rememberAddress(presenting) { presenting.address() }
    val lockedOut by presenting.lockedOut.collectAsState()
    val container = LocalContext.current.container
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            EscapeCloses(onDismiss)
            Text(stringResource(R.string.present_show_in_a_browser))
        },
        text = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.verticalScroll(rememberScrollState()),
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

                    Surface(color = Color.White, shape = MaterialTheme.shapes.medium) {
                        QrCode(address, Modifier.padding(12.dp).size(168.dp))
                    }

                    JoiningControls(
                        presenting.joining,
                        since = 0L,
                        onRemove = presenting::remove,
                        onChange = { options ->
                            presenting.joining.change(options)

                            // The next presentation starts the same way, even if this dialog closes at once.
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
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_done)) } },
        dismissButton = { TextButton(onClick = onStop) { Text(stringResource(R.string.present_stop_showing)) } },
    )
}

/** A QR code of `text`, dark on whatever light background is behind it. */
@Composable
fun QrCode(text: String, modifier: Modifier = Modifier) {
    val matrix = remember(text) {
        QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, 0, 0, mapOf(EncodeHintType.MARGIN to 0))
    }

    Canvas(modifier) {
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
