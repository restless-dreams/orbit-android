package com.hyuchia.orbit.map

import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.viewinterop.AndroidView
import com.hyuchia.orbit.R
import com.hyuchia.orbit.page.OrbitPage
import com.hyuchia.orbit.theme.parseCssColor

/**
 * The map: the page's WebView, moved into the screen. The page belongs to
 * the map's view model, so it outlives the screen through rotations, a
 * trip to another screen and back, or a fold.
 *
 * With `keyboardFocus`, the map holds the keyboard's focus, so its keys work:
 * as it opens, once whatever was over it closes, and each time the screen
 * moves the page to a new place in its layout, which loses the focus. With
 * `focusable` false, it takes no focus at all and gives up any it has: the
 * screen over it, a study session or a presentation, has the keys and its
 * own buttons for Tab.
 * With `heard` false, screen readers pass the page by: something over it
 * speaks for it, as a sketch's canvas does for its planet.
 *
 * The map speaks the app's language, the screen's, which can differ from
 * the phone's: the page hears it as it moves into the screen, and again
 * if it changes while the map is open.
 */
@Composable
fun MapCanvas(model: MapViewModel, modifier: Modifier = Modifier, keyboardFocus: Boolean = false, focusable: Boolean = true, heard: Boolean = true) {
    val generation by model.pageGeneration.collectAsState()

    key(generation) {
        // The app's pages are OrbitPages, WebViews to show: only a test's view model has a page of its own.
        val page = model.page as OrbitPage
        val attachment = remember { intArrayOf(0) }

        // A WebView can always take the focus, for the keyboard to type into the map.
        DisposableEffect(page, focusable) {
            page.webView.isFocusable = focusable
            page.webView.isFocusableInTouchMode = focusable

            onDispose {
                page.webView.isFocusable = true
                page.webView.isFocusableInTouchMode = true
            }
        }

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

        val locked by model.locked.collectAsState()

        // Behind its lock, the page is invisible under the lock's cover: screen readers and the keyboard can't reach it,
        // nor can anything else that reads the screen. It takes part in no drag either, so neither its own text fields
        // nor the map take a drop, and nothing lights up. An idea on its way out is called back.
        LaunchedEffect(locked, heard) {
            page.webView.visibility = if (locked) View.INVISIBLE else View.VISIBLE
            page.webView.importantForAccessibility = if (locked || !heard) View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS else View.IMPORTANT_FOR_ACCESSIBILITY_AUTO

            if (locked && model.drops.letGo()) {
                page.webView.cancelDragAndDrop()
            }
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

        // Drags from other apps, and ideas dragged out of the window and back: the page is the map's while it's here.
        DisposableEffect(page) {
            page.webView.setOnDragListener(MapDropListener(model.drops))
            onDispose { page.webView.setOnDragListener(null) }
        }

        val untitled = stringResource(R.string.common_untitled)
        val fallbackColor = MaterialTheme.colorScheme.primaryContainer.toArgb()

        LaunchedEffect(page) {
            model.drops.outgoing.collect { id ->
                val nodes = model.ui.value.map?.nodes.orEmpty()
                val color = nodes.firstOrNull { it.id == id }?.drawnColor?.let(::parseCssColor)?.toArgb() ?: fallbackColor

                if (model.drops.canDragOut && startIdeaDrag(page.webView, model.id, nodes, id, untitled, color)) {
                    model.drops.wentOut()
                    // The press is the drag's now: the page hears it end, so the next touch on the map starts afresh.
                    val now = SystemClock.uptimeMillis()
                    val cancel = MotionEvent.obtain(now, now, MotionEvent.ACTION_CANCEL, 0f, 0f, 0)
                    page.webView.dispatchTouchEvent(cancel)
                    cancel.recycle()
                }
            }
        }
    }
}

private fun takeFocus(view: View) {
    if (view.isAttachedToWindow && !view.hasFocus()) {
        view.requestFocus()
    }
}
