package com.hyuchia.orbit.media

import android.graphics.Color as AndroidColor
import android.os.Build
import android.view.KeyEvent
import android.view.Window
import androidx.activity.BackEventCompat
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.activity.compose.PredictiveBackHandler
import androidx.activity.findViewTreeOnBackPressedDispatcherOwner
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.navigationevent.compose.LocalNavigationEventDispatcherOwner
import androidx.navigationevent.findViewTreeNavigationEventDispatcherOwner
import com.hyuchia.orbit.theme.OrbitTheme
import com.hyuchia.orbit.ui.EscapeCloses
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** How long the window takes to come up or go. */
private const val SHOW_MILLISECONDS = 220

/** At the end of a back swipe, the window has shrunk to this much of its size. */
private const val BACK_SCALE = 0.86f

/**
 * A window that's going: [close] lets it fade out first, [closeNow] is for
 * content that animated itself out already, as a flicked picture does.
 */
@Stable
class FullScreenState internal constructor(private val scope: CoroutineScope, private val onClosed: () -> Unit) {
    /** How far the window is from fully shown, from 0 (shown) to 1 (gone). */
    internal val hidden = Animatable(1f)

    /** How far a back swipe has gone, from 0 to 1. */
    internal val back = Animatable(0f)

    /** Which side the back swipe came from: the window drifts away from it. */
    internal var fromLeft by mutableStateOf(true)

    private var closing = false

    fun close() {
        if (closing) {
            return
        }

        closing = true

        scope.launch {
            hidden.animateTo(1f, tween(SHOW_MILLISECONDS))
            onClosed()
        }
    }

    fun closeNow() {
        if (!closing) {
            closing = true
            onClosed()
        }
    }
}

/**
 * Full screen, over everything the app shows, the list beside the map on a
 * tablet included, for pictures and videos: its own window, edge to edge,
 * with light system bars for the dark behind. Back closes it, the back
 * swipe shrinking it as it goes, and so does Escape, unless `onEscape` does
 * something else first. `immersive` hides the system bars, which a swipe in
 * from an edge brings back for a moment. The window's own background is
 * clear: the content paints its own. `onKey` sees each key before anything
 * in the window does, and keeps the ones it takes: a presentation's steps.
 */
@Composable
fun FullScreenWindow(
    onClosed: () -> Unit,
    immersive: Boolean = false,
    onEscape: ((FullScreenState) -> Unit)? = null,
    onKey: (KeyEvent) -> Boolean = { false },
    content: @Composable (FullScreenState) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val closed by rememberUpdatedState(onClosed)
    val state = remember { FullScreenState(scope) { closed() } }

    Dialog(
        onDismissRequest = { state.close() },
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        // Back goes to this window: the handlers below are its own, whatever the screen under it provides.
        val view = LocalView.current

        val owners = remember(view) {
            listOfNotNull(
                view.findViewTreeOnBackPressedDispatcherOwner()?.let { LocalOnBackPressedDispatcherOwner provides it },
                view.findViewTreeNavigationEventDispatcherOwner()?.let { LocalNavigationEventDispatcherOwner provides it },
            )
        }

        CompositionLocalProvider(*owners.toTypedArray()) {
            val window = remember(view) { generateSequence(view.parent) { it.parent }.filterIsInstance<DialogWindowProvider>().firstOrNull()?.window }

            DisposableEffect(window) {
                window?.let(::prepare)
                onDispose {}
            }

            val key by rememberUpdatedState(onKey)

            DisposableEffect(window) {
                val original = window?.callback

                if (window == null || original == null) {
                    return@DisposableEffect onDispose {}
                }

                window.callback = object : Window.Callback by original {
                    override fun dispatchKeyEvent(event: KeyEvent): Boolean = key(event) || original.dispatchKeyEvent(event)
                }

                onDispose { window.callback = original }
            }

            DisposableEffect(window, immersive) {
                window?.let { showBars(it, !immersive) }
                onDispose {}
            }

            LaunchedEffect(Unit) { state.hidden.animateTo(0f, tween(SHOW_MILLISECONDS)) }

            EscapeCloses {
                if (onEscape != null) {
                    onEscape(state)
                } else {
                    state.close()
                }
            }

            PredictiveBackHandler { events ->
                try {
                    events.collect { event ->
                        state.fromLeft = event.swipeEdge == BackEventCompat.EDGE_LEFT
                        state.back.snapTo(event.progress)
                    }

                    state.close()
                } catch (cancelled: CancellationException) {
                    // Swiped back in: the window settles where it was.
                    scope.launch { state.back.animateTo(0f, tween(SHOW_MILLISECONDS)) }
                    throw cancelled
                }
            }

            val drift = with(LocalDensity.current) { 32.dp.toPx() }

            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        val back = state.back.value
                        val hidden = state.hidden.value
                        alpha = 1f - hidden
                        val scale = (1f - (1f - BACK_SCALE) * back) * (1f - 0.06f * hidden)
                        scaleX = scale
                        scaleY = scale
                        translationX = (if (state.fromLeft) drift else -drift) * back
                    },
            ) {
                // Whatever the map's style, pictures and videos sit on the dark, in the app's dark colors.
                OrbitTheme(dark = true) {
                    content(state)
                }
            }
        }
    }
}

/** No dimming, no window animation (the content fades itself), and system bars for a dark screen. */
private fun prepare(window: Window) {
    window.setDimAmount(0f)
    window.setWindowAnimations(0)

    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM) {
        @Suppress("DEPRECATION")
        window.statusBarColor = AndroidColor.TRANSPARENT
        @Suppress("DEPRECATION")
        window.navigationBarColor = AndroidColor.TRANSPARENT
    }

    val controller = WindowCompat.getInsetsController(window, window.decorView)
    controller.isAppearanceLightStatusBars = false
    controller.isAppearanceLightNavigationBars = false
}

private fun showBars(window: Window, shown: Boolean) {
    val controller = WindowCompat.getInsetsController(window, window.decorView)

    if (shown) {
        controller.show(WindowInsetsCompat.Type.systemBars())
    } else {
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller.hide(WindowInsetsCompat.Type.systemBars())
    }
}
