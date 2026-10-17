package com.hyuchia.orbit.ui

import android.content.ActivityNotFoundException
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.hyuchia.orbit.R
import com.hyuchia.orbit.page.SystemWebView

/**
 * Says the phone's WebView is too old to draw maps, and what to update, with
 * a button to its page in the phone's app store when there's a store that
 * opens it: Google Play, Huawei's AppGallery or another.
 */
@Composable
fun OldWebViewNotice(webView: SystemWebView, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val store = remember(webView) { webView.storePage(context) }
    val colors = MaterialTheme.colorScheme

    Card(colors = CardDefaults.cardColors(containerColor = colors.errorContainer, contentColor = colors.onErrorContainer), modifier = modifier) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(20.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.SystemUpdate, contentDescription = null)
                Text(stringResource(R.string.ui_web_view_update_to_see_maps, webView.name), style = MaterialTheme.typography.titleMedium)
            }

            Text(
                // Shown only for a WebView whose version is known, and too old.
                stringResource(R.string.ui_web_view_too_old, webView.name, webView.chromium ?: 0, SystemWebView.MINIMUM_CHROMIUM),
                style = MaterialTheme.typography.bodyMedium,
            )

            if (store != null) {
                Button(
                    onClick = {
                        try {
                            context.startActivity(store)
                        } catch (missing: ActivityNotFoundException) {
                            // The store went away since: the words above still say what to do.
                        }
                    },
                ) { Text(stringResource(R.string.ui_web_view_update, webView.name)) }
            }
        }
    }
}
