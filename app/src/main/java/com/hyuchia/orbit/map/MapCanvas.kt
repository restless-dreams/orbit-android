package com.hyuchia.orbit.map

import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.viewinterop.AndroidView

/**
 * The map: the page's WebView, moved into the screen. The page belongs to
 * the map's view model, so it outlives the screen through rotations, a
 * trip to another screen and back, or a fold.
 *
 * With `keyboardFocus`, the map holds the keyboard's focus, so its keys work:
 * as it opens, once whatever was over it closes, and each time the screen
 * moves the page to a new place in its layout, which loses the focus.
 *
 * The map speaks the app's language, the screen's, which can differ from
 * the phone's: the page hears it as it moves into the screen, and again
 * if it changes while the map is open.
 */
@Composable
fun MapCanvas(model: MapViewModel, modifier: Modifier = Modifier, keyboardFocus: Boolean = false) {
    val generation by model.pageGeneration.collectAsState()

    key(generation) {
        val page = model.page
        val attachment = remember { intArrayOf(0) }
        AndroidView(
            factory = { context ->
                attachment[0] = page.attach(context)
                page.webView
            },
            onRelease = { page.detach(attachment[0]) },
            modifier = modifier,
        )
        val locales = LocalConfiguration.current.locales

        LaunchedEffect(locales) {
            page.speak()
        }

        val wantsFocus by rememberUpdatedState(keyboardFocus)

        LaunchedEffect(keyboardFocus) {
            if (keyboardFocus) {
                // A frame later, once a panel that closed has handed its own focus back.
                withFrameNanos {}
                takeFocus(page.webView)
            }
        }

        // Moving the page within the layout, as a panel beside it closes, takes it out of the window
        // for a moment, and its focus with it: it takes the focus again once it's back.
        DisposableEffect(page) {
            val back = object : View.OnAttachStateChangeListener {
                override fun onViewAttachedToWindow(view: View) {
                    view.post {
                        if (wantsFocus) {
                            takeFocus(view)
                        }
                    }
                }

                override fun onViewDetachedFromWindow(view: View) = Unit
            }

            page.webView.addOnAttachStateChangeListener(back)
            onDispose { page.webView.removeOnAttachStateChangeListener(back) }
        }
    }
}

private fun takeFocus(view: View) {
    if (view.isAttachedToWindow && !view.hasFocus()) {
        view.requestFocus()
    }
}
