package com.hyuchia.orbit.privacy

import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.Window
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.LockOpen
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.hyuchia.orbit.R
import com.hyuchia.orbit.container
import com.hyuchia.orbit.data.LockedCover
import com.hyuchia.orbit.data.MapSummary
import com.hyuchia.orbit.theme.LocalAppSettings
import com.hyuchia.orbit.theme.appIsDark
import com.hyuchia.orbit.ui.EscapeCloses
import com.hyuchia.orbit.ui.Hint
import com.hyuchia.orbit.ui.hasHardwareKeyboard
import com.hyuchia.orbit.ui.isShort
import kotlinx.coroutines.delay

/** Unlocked, the lock opens for a moment before the map shows. */
private const val OPENING_MILLISECONDS = 260L
private const val FADING_MILLISECONDS = 320

/** A short window narrower than this, as half a phone is in split screen, has no room beside the lock. */
private val RoomBeside = 560.dp

/** As tall as this or more, the lock's whole orbit shows above the words. */
private val RoomForOrbit = 640.dp

/** How a private map's lock lays out, for the window it's in. */
internal enum class CoverLayout {
    /** A phone on its side: the lock's orbit beside its words. */
    Beside,

    /** Half a phone in split screen, short and narrow: the lock alone, above smaller words. */
    Small,

    /** Not quite tall enough for the whole orbit, as half a tablet held upright: a smaller one above the words. */
    Stacked,

    /** The lock's whole orbit, above the words. */
    Whole,
}

/** The layout for a window `width` by `height`, once the system bars are left out. */
internal fun coverLayout(width: Dp, height: Dp): CoverLayout = when {
    // A phone on its side: the lock's words stand beside it when there's room.
    isShort(height) && width >= RoomBeside -> CoverLayout.Beside
    isShort(height) -> CoverLayout.Small
    height < RoomForOrbit -> CoverLayout.Stacked
    else -> CoverLayout.Whole
}

/**
 * Whether the map on screen is behind its lock (see [PrivateMapGate]): what
 * runs over it, such as a study session, holds still until it's unlocked,
 * taking no keys and keeping nothing of the map awake.
 */
val LocalMapLocked = compositionLocalOf { false }

/** What a map's screen shows behind [PrivateMapGate]. */
internal enum class Gate {
    /** Whether the map is private isn't known yet, as at a cold start: nothing of it, nor a lock it may not have. */
    Waiting,

    /** The map. */
    Open,

    /** The lock alone, asking for the screen lock: nothing of the map has loaded. */
    Locked,

    /**
     * The lock over the map, which was on screen here before it locked and stays as it was underneath. A screen
     * made again meanwhile, as for a new language or font size, shows the lock alone until it's unlocked: what
     * opened over the map, its sheets and dialogs, would open again above the lock.
     */
    LockedOver,
}

/**
 * Whether the map with this `id` is private, from the maps' `summaries`; null while they're read, as
 * at a cold start, when it isn't known yet.
 */
internal fun mapIsPrivate(summaries: List<MapSummary>?, id: String): Boolean? =
    summaries?.let { list -> list.firstOrNull { it.id == id }?.metadata?.isPrivate == true }

/**
 * What the screen shows for a map that's `private` (null: not known yet), which private maps' view
 * `hides` or not, `shown` when it was on screen in this run of Orbit, and `openHere` when it was open
 * on this very screen, not one made again since. Not known counts as locked.
 */
internal fun gate(private: Boolean?, hides: Boolean, shown: Boolean, openHere: Boolean = shown): Gate = when {
    private == null -> Gate.Waiting
    !private || !hides -> Gate.Open
    shown && openHere -> Gate.LockedOver
    else -> Gate.Locked
}

/**
 * A map's screen, behind its lock while it's private and private maps are
 * locked. Opened while they're locked, nothing of the map loads until it's
 * unlocked; and until it's known whether it's private, as at a cold start
 * from a shortcut or a widget, it counts as locked and nothing of it shows
 * or loads. One that locks while it's open is covered whole, its sheets and
 * menus included, and stays as it was underneath: unlocked, it's just where
 * it was left. Unlocking, the lock opens and fades, and the map shows.
 */
@Composable
fun PrivateMapGate(id: String, onBack: () -> Unit, content: @Composable () -> Unit) {
    val container = LocalContext.current.container
    val view by container.privateMaps.view.collectAsStateWithLifecycle()
    val summaries by container.maps.maps.collectAsStateWithLifecycle()
    // The run of Orbit the map was last on screen in: then it stays, under the lock. A screen that comes back after
    // Android closed Orbit remembers an earlier run, and starts over behind the lock, as a new start does.
    var shownIn by rememberSaveable(id) { mutableStateOf<String?>(null) }
    val shown = shownIn == container.privateMaps.run
    // Open on this screen, not only in this run: a screen made again while the map is locked makes its sheets and
    // dialogs again too, as windows above the lock's, so the map waits under the lock alone until it's unlocked.
    var openHere by remember(id) { mutableStateOf(false) }
    val known = mapIsPrivate(summaries, id)
    val gate = gate(known, view.hides(id), shown, openHere)

    if (gate == Gate.Waiting) {
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface))
        return
    }

    val private = known == true
    val locked = private && view.hides(id)
    // The lock in the map's own look, with the cover its owner chose: nothing of the map itself.
    val cover = summaries?.firstOrNull { it.id == id }?.metadata?.cover
    val look = remember(id) { lockedLook(id) }

    if (!locked && (!shown || !openHere)) {
        SideEffect {
            shownIn = container.privateMaps.run
            openHere = true
        }
    }

    PrivateOnScreen(private && !locked)
    val focus = LocalFocusManager.current

    // Locked while open: whatever had the keyboard's focus on the map lets it go.
    LaunchedEffect(locked) {
        if (locked && shown) {
            focus.clearFocus(force = true)
        }
    }

    Box(Modifier.fillMaxSize()) {
        if (gate != Gate.Locked) {
            // Under the lock's cover, nothing of the map reaches screen readers or other accessibility services, and
            // the keyboard's focus doesn't go into it.
            Box(if (locked) Modifier.clearAndSetSemantics {}.focusProperties { onEnter = { cancelFocusChange() } }.focusGroup() else Modifier) {
                CompositionLocalProvider(LocalMapLocked provides locked) {
                    content()
                }
            }
        }

        AnimatedVisibility(locked, enter = EnterTransition.None, exit = fadeOut(tween(FADING_MILLISECONDS, delayMillis = OPENING_MILLISECONDS.toInt()))) {
            // Over the map in its own window, the first frame back already shows the lock: the window above follows,
            // and asks. Locked from Orbit's own button, this is the only cover, and asks once it's tapped.
            PrivateMapCover(onBack, open = !locked, ask = !shown, cover = cover, look = look)
        }

        // Locked while Orbit was away, the sheets, menus and pictures open over the map are covered too.
        if (shown) {
            LockedWindow(locked && view.lockedAway, onBack, cover, look)
        }
    }
}

/**
 * Over everything the app shows while a map that was open locks: the sheets,
 * menus and pictures over the map are windows of their own, which this one
 * covers. It fades as the map unlocks.
 */
@Composable
private fun LockedWindow(locked: Boolean, onBack: () -> Unit, cover: LockedCover?, look: LockedLook) {
    var showing by remember { mutableStateOf(locked) }
    val shown = remember { Animatable(if (locked) 1f else 0f) }

    LaunchedEffect(locked) {
        if (locked) {
            shown.snapTo(1f)
            showing = true
        } else if (showing) {
            delay(OPENING_MILLISECONDS)
            shown.animateTo(0f, tween(FADING_MILLISECONDS))
            showing = false
        }
    }

    if (!locked && !showing) {
        return
    }

    Dialog(
        onDismissRequest = onBack,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false, dismissOnClickOutside = false),
    ) {
        val view = LocalView.current
        val dark = appIsDark()

        SideEffect {
            generateSequence(view.parent) { it.parent }.filterIsInstance<DialogWindowProvider>().firstOrNull()?.window?.let { prepare(it, dark) }
        }

        Box(Modifier.fillMaxSize().graphicsLayer { alpha = shown.value }) {
            PrivateMapCover(onBack, open = !locked, ask = true, cover = cover, look = look)
        }
    }
}

/**
 * No dimming and no window animation: the lock is there at once, edge to
 * edge, with the system bars over it, see-through from the app's theme.
 */
private fun prepare(window: Window, dark: Boolean) {
    window.setDimAmount(0f)
    window.setWindowAnimations(0)

    val controller = WindowCompat.getInsetsController(window, window.decorView)
    controller.isAppearanceLightStatusBars = !dark
    controller.isAppearanceLightNavigationBars = !dark
}

/**
 * A private map's lock: what it is, and the way to open it, in the map's
 * own `look` and with the `cover` its owner chose. `ask` asks for the screen
 * lock as soon as it shows, once; Unlock asks again. `open` opens the lock,
 * as the map unlocks behind it.
 */
@Composable
fun PrivateMapCover(onBack: () -> Unit, open: Boolean = false, ask: Boolean = true, cover: LockedCover? = null, look: LockedLook? = null) {
    val unlocking = rememberUnlocking()
    val view = LocalView.current
    var problem by remember { mutableStateOf<String?>(null) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var asked by rememberSaveable { mutableStateOf(false) }
    val unlockFocus = remember { FocusRequester() }
    val haptics = LocalAppSettings.current.haptics
    val keyboard = hasHardwareKeyboard()

    fun unlock() {
        problem = null

        unlocking.ask { result ->
            when (result) {
                Unlocked.Yes -> if (haptics) {
                    view.performHapticFeedback(if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.KEYBOARD_TAP)
                }
                is Unlocked.Failed -> problem = result.reason.ifBlank { null }
                Unlocked.Cancelled -> Unit
            }
        }
    }

    // Asked as the lock shows, and each time Orbit comes back to it: cancelled, it waits for Unlock rather than
    // asking again and again. Android 10's PIN screen takes Orbit off screen while it asks: that isn't leaving.
    LaunchedEffect(ask) {
        if (ask) {
            lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                if (!asked) {
                    asked = true
                    unlock()
                }
            }
        }
    }

    LifecycleEventEffect(Lifecycle.Event.ON_STOP) {
        if (!unlocking.asking) {
            asked = false
        }
    }

    // With a keyboard, Enter unlocks.
    LaunchedEffect(keyboard) {
        if (keyboard) {
            runCatching { unlockFocus.requestFocus() }
        }
    }

    Surface(color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxSize()) {
        // Over everything, in a window of its own, the lock keeps the keys: no screen's shortcut reaches through it.
        EscapeCloses(shortcuts = false, onDismiss = onBack)

        BoxWithConstraints(Modifier.fillMaxSize().safeDrawingPadding()) {
            val back = stringResource(R.string.private_back_to_your_maps)
            val enabled = !unlocking.asking && !open
            val height = maxHeight

            when (val layout = coverLayout(maxWidth, height)) {
                CoverLayout.Beside -> Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(40.dp),
                    modifier = Modifier
                        .align(Alignment.Center)
                        .widthIn(max = 720.dp)
                        .padding(horizontal = 56.dp, vertical = 16.dp),
                ) {
                    LockedOrbit(emblemSize = 60.dp, open = open, framed = false, look = look, icon = cover?.icon, modifier = Modifier.size(minOf(156.dp, height - 32.dp)))

                    Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) {
                        CoverWords(problem, Alignment.Start, TextAlign.Start, cover = cover)
                        Spacer(Modifier.height(20.dp))
                        UnlockButton(onClick = ::unlock, enabled = enabled, focus = unlockFocus)
                    }
                }
                // Clear of the way back, which a short window brings close.
                CoverLayout.Small -> Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .widthIn(max = 420.dp)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(start = 24.dp, end = 24.dp, top = 52.dp, bottom = 16.dp),
                ) {
                    LockEmblem(open, Modifier.size(64.dp), iconSize = 28.dp, look = look, icon = cover?.icon)
                    Spacer(Modifier.height(16.dp))
                    CoverWords(problem, Alignment.CenterHorizontally, TextAlign.Center, compact = true, cover = cover)
                    Spacer(Modifier.height(20.dp))
                    UnlockButton(onClick = ::unlock, enabled = enabled, focus = unlockFocus, compact = true)
                }
                CoverLayout.Stacked, CoverLayout.Whole -> Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .widthIn(max = 420.dp)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 32.dp, vertical = 24.dp),
                ) {
                    val whole = layout == CoverLayout.Whole
                    LockedOrbit(emblemSize = if (whole) 76.dp else 60.dp, open = open, framed = false, look = look, icon = cover?.icon, modifier = Modifier.size(if (whole) 208.dp else 160.dp))
                    Spacer(Modifier.height(28.dp))
                    CoverWords(problem, Alignment.CenterHorizontally, TextAlign.Center, cover = cover)
                    Spacer(Modifier.height(28.dp))
                    UnlockButton(onClick = ::unlock, enabled = enabled, focus = unlockFocus)
                }
            }

            // Over the rest, so it's always there to tap, however short the window.
            Hint(back) {
                IconButton(onClick = onBack, modifier = Modifier.padding(4.dp)) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = back) }
            }
        }
    }
}

/**
 * What the lock says: that the map is private, or its cover's name, how to open it, and why it couldn't be
 * when the phone says. `compact` in a small window: smaller words.
 */
@Composable
private fun CoverWords(problem: String?, alignment: Alignment.Horizontal, textAlign: TextAlign, compact: Boolean = false, cover: LockedCover? = null) {
    val typography = MaterialTheme.typography
    // Centered, lines of even length, so none ends on a word alone.
    val lines = if (textAlign == TextAlign.Center) LineBreak(LineBreak.Strategy.Balanced, LineBreak.Strictness.Normal, LineBreak.WordBreak.Default) else LineBreak.Paragraph

    Column(horizontalAlignment = alignment) {
        val name = cover?.name?.takeIf { it.isNotBlank() }

        Text(
            name ?: stringResource(R.string.private_cover_title),
            style = (if (compact) typography.titleLarge else typography.headlineSmall).copy(lineBreak = lines),
            textAlign = textAlign,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(if (name != null) R.string.private_cover_named_body else R.string.private_cover_body),
            style = (if (compact) typography.bodyMedium else typography.bodyLarge).copy(lineBreak = lines),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = textAlign,
        )

        problem?.let { reason ->
            Spacer(Modifier.height(12.dp))
            Text(reason, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error, textAlign = textAlign)
        }
    }
}

/** Unlock, on one line however narrow the window: `compact`, a little lower. */
@Composable
private fun UnlockButton(onClick: () -> Unit, enabled: Boolean, focus: FocusRequester, compact: Boolean = false) {
    Button(
        onClick = onClick,
        enabled = enabled,
        contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
        modifier = Modifier
            .height(if (compact) 48.dp else 56.dp)
            .focusRequester(focus),
    ) {
        Icon(Icons.Outlined.LockOpen, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
        Spacer(Modifier.size(ButtonDefaults.IconSpacing))
        Text(stringResource(R.string.private_unlock), style = MaterialTheme.typography.titleMedium, maxLines = 1, softWrap = false)
    }
}
