package com.hyuchia.orbit.nearby

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign

/**
 * An address to type into a browser, such as 192.168.1.5:8766/408377: on
 * one line when it fits, else broken after its last slash, so the code
 * stays whole on a line of its own (a line breaker would split it anywhere:
 * nothing in an address is a place to break).
 */
@Composable
fun AddressText(address: String, modifier: Modifier = Modifier) {
    val style = MaterialTheme.typography.titleLarge.copy(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.SemiBold)
    val measurer = rememberTextMeasurer()

    BoxWithConstraints(modifier) {
        val width = constraints.maxWidth

        val shown = remember(address, style, width, measurer) {
            val fits = measurer.measure(address, style, softWrap = false, maxLines = 1).size.width <= width
            if (fits || '/' !in address) address else address.substringBeforeLast('/') + "/\n" + address.substringAfterLast('/')
        }

        SelectionContainer {
            Text(shown, style = style, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        }
    }
}
