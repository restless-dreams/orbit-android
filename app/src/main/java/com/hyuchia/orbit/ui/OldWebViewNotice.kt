package com.hyuchia.orbit.ui

import android.content.ActivityNotFoundException
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
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

    NoticeCard(
        icon = Icons.Outlined.SystemUpdate,
        title = stringResource(R.string.ui_web_view_update_to_see_maps, webView.name),
        modifier = modifier,
        problem = true,
        // Shown only for a WebView whose version is known, and too old.
        body = { Text(stringResource(R.string.ui_web_view_too_old, webView.name, webView.chromium ?: 0, SystemWebView.MINIMUM_CHROMIUM)) },
    ) {
        if (store != null) {
            NoticeButton(
                stringResource(R.string.ui_web_view_update, webView.name),
                onClick = {
                    try {
                        context.startActivity(store)
                    } catch (missing: ActivityNotFoundException) {
                        // The store went away since: the words above still say what to do.
                    }
                },
                problem = true,
            )
        }
    }
}
