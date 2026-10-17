package com.hyuchia.orbit.ui

import android.content.res.Configuration
import android.view.KeyEvent
import android.view.KeyboardShortcutGroup
import android.view.KeyboardShortcutInfo
import android.view.Window
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogWindowProvider
import com.hyuchia.orbit.R

/** A key as it was pressed or let go: what shortcuts match. `typed` is the character it types, without Ctrl or Alt. */
data class KeyPress(
    val keyCode: Int,
    val action: Int,
    val repeatCount: Int = 0,
    val ctrl: Boolean = false,
    val shift: Boolean = false,
    val alt: Boolean = false,
    val meta: Boolean = false,
    val typed: Int = 0,
) {
    companion object {
        fun of(event: KeyEvent) = KeyPress(
            keyCode = event.keyCode,
            action = event.action,
            repeatCount = event.repeatCount,
            ctrl = event.isCtrlPressed,
            shift = event.isShiftPressed,
            alt = event.isAltPressed,
            meta = event.isMetaPressed,
            typed = event.getUnicodeChar(event.metaState and (KeyEvent.META_CTRL_MASK or KeyEvent.META_ALT_MASK).inv()),
        )
    }
}

/** A key and the modifiers held with it. `character` also matches keyboards that need Shift for it, such as `/` on a German one. */
data class Keys(
    val keyCode: Int,
    val ctrl: Boolean = false,
    val shift: Boolean = false,
    val alt: Boolean = false,
    val character: Char? = null,
) {
    fun matches(press: KeyPress): Boolean {
        if (press.meta || press.ctrl != ctrl || press.alt != alt) {
            return false
        }

        if (press.keyCode == keyCode && press.shift == shift) {
            return true
        }

        return character != null && press.typed == character.code
    }

    /** The modifiers, as Android's list of shortcuts takes them. */
    val modifiers: Int
        get() = (if (ctrl) KeyEvent.META_CTRL_ON else 0) or (if (shift) KeyEvent.META_SHIFT_ON else 0) or (if (alt) KeyEvent.META_ALT_ON else 0)

    /** The keys as they're pressed together, one cap each, in the app's language: Ctrl, Shift, Z. */
    val caps: List<String>
        @Composable @ReadOnlyComposable get() = capLabels.map { it.text() }

    /** What each of [caps] says, before it's put in the app's language. */
    val capLabels: List<KeyLabel>
        get() = listOfNotNull(
            KeyLabel.Named(R.string.ui_key_control).takeIf { ctrl },
            KeyLabel.Named(R.string.ui_key_alt).takeIf { alt },
            KeyLabel.Named(R.string.ui_key_shift).takeIf { shift },
        ) + keyLabel(keyCode)
}

/** What a key's cap says: a word, such as Ctrl or Enter, or what's printed on it in every language, such as Z, 5 or /. */
sealed interface KeyLabel {
    /** The cap's words in the app's language. */
    @Composable
    @ReadOnlyComposable
    fun text(): String

    /** A key named in words. */
    data class Named(@StringRes val resource: Int) : KeyLabel {
        @Composable
        @ReadOnlyComposable
        override fun text(): String = stringResource(resource)
    }

    /** A key with a letter, a number, a symbol or an arrow on it. */
    data class Printed(val label: String) : KeyLabel {
        @Composable
        @ReadOnlyComposable
        override fun text(): String = label
    }
}

private fun keyLabel(keyCode: Int): KeyLabel = when (keyCode) {
    KeyEvent.KEYCODE_DPAD_LEFT -> KeyLabel.Printed("←")
    KeyEvent.KEYCODE_DPAD_RIGHT -> KeyLabel.Printed("→")
    KeyEvent.KEYCODE_DPAD_UP -> KeyLabel.Printed("↑")
    KeyEvent.KEYCODE_DPAD_DOWN -> KeyLabel.Printed("↓")
    KeyEvent.KEYCODE_ENTER -> KeyLabel.Named(R.string.ui_key_enter)
    KeyEvent.KEYCODE_SPACE -> KeyLabel.Named(R.string.ui_key_space)
    KeyEvent.KEYCODE_ESCAPE -> KeyLabel.Named(R.string.ui_key_escape)
    KeyEvent.KEYCODE_DEL -> KeyLabel.Named(R.string.ui_key_backspace)
    KeyEvent.KEYCODE_FORWARD_DEL -> KeyLabel.Named(R.string.ui_key_delete)
    KeyEvent.KEYCODE_TAB -> KeyLabel.Named(R.string.ui_key_tab)
    KeyEvent.KEYCODE_INSERT -> KeyLabel.Named(R.string.ui_key_insert)
    KeyEvent.KEYCODE_MENU -> KeyLabel.Named(R.string.ui_key_menu)
    KeyEvent.KEYCODE_PAGE_UP -> KeyLabel.Named(R.string.ui_key_page_up)
    KeyEvent.KEYCODE_PAGE_DOWN -> KeyLabel.Named(R.string.ui_key_page_down)
    KeyEvent.KEYCODE_MOVE_HOME -> KeyLabel.Named(R.string.ui_key_home)
    KeyEvent.KEYCODE_MOVE_END -> KeyLabel.Named(R.string.ui_key_end)
    KeyEvent.KEYCODE_PLUS -> KeyLabel.Printed("+")
    KeyEvent.KEYCODE_MINUS -> KeyLabel.Printed("−")
    KeyEvent.KEYCODE_SLASH -> KeyLabel.Printed("/")
    KeyEvent.KEYCODE_COMMA -> KeyLabel.Printed(",")
    KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> KeyLabel.Named(R.string.ui_key_play)
    in KeyEvent.KEYCODE_F1..KeyEvent.KEYCODE_F12 -> KeyLabel.Printed("F${keyCode - KeyEvent.KEYCODE_F1 + 1}")
    in KeyEvent.KEYCODE_0..KeyEvent.KEYCODE_9 -> KeyLabel.Printed("${keyCode - KeyEvent.KEYCODE_0}")
    in KeyEvent.KEYCODE_A..KeyEvent.KEYCODE_Z -> KeyLabel.Printed("${'A' + (keyCode - KeyEvent.KEYCODE_A)}")
    else -> KeyLabel.Printed(KeyEvent.keyCodeToString(keyCode).removePrefix("KEYCODE_"))
}

/**
 * Something the keyboard does: its name in the list of shortcuts, the keys
 * for it, and what it does. Without an action, the map's page answers the
 * keys itself, and the list only shows them.
 *
 * `anywhere`: the keys are none a text field uses, so they work wherever
 * the focus is. Other shortcuts wait until nothing focused used the keys,
 * so Ctrl+Z still undoes typing in a field.
 */
class Shortcut(
    val label: String,
    val keys: List<Keys>,
    val anywhere: Boolean = false,
    val run: (() -> Unit)? = null,
)

class ShortcutGroup(val title: String, val shortcuts: List<Shortcut>)

/** One screen's shortcuts, kept current as it recomposes. */
class ScreenShortcuts(val priority: Int) {
    var groups: List<ShortcutGroup> = emptyList()
}

/** Which screen's shortcuts win where two on display use the same keys: a map's over the list beside it. */
object ShortcutPriority {
    const val List = 0
    const val Map = 1
}

/**
 * The keyboard shortcuts of the screens on display. On a tablet a map open
 * beside the list wins where both use a key, as it has the higher priority.
 */
class ShortcutRegistry {
    private val screens = mutableStateListOf<ScreenShortcuts>()

    /** Whether the list of shortcuts is open. */
    var listOpen by mutableStateOf(false)

    /**
     * The name of Orbit's own shortcut, the one that opens the list, in the
     * app's language. The registry has no words of its own: the screens give
     * it this name as they give it their shortcuts (see [KeyboardShortcuts]).
     */
    internal var listName = ""

    private val orbit: ShortcutGroup
        get() = ShortcutGroup(
            "Orbit",
            listOf(Shortcut(listName, listOf(ShortcutListKeys), anywhere = true) { listOpen = true }),
        )

    /** What the screens on display offer, the first screen's to win first, then Orbit's own. */
    val groups: List<ShortcutGroup>
        get() = screens.asReversed().sortedByDescending { it.priority }.flatMap { it.groups } + orbit

    fun add(screen: ScreenShortcuts) {
        screens += screen
    }

    fun remove(screen: ScreenShortcuts) {
        screens -= screen
    }

    /**
     * Runs the shortcut for a key. With `anywhere`, the screen sees the key
     * before anything focused does, and only shortcuts no text field could
     * want run; without, nothing focused used the key, and any shortcut can.
     * A key's release goes wherever its press went.
     */
    fun dispatch(event: KeyEvent, anywhere: Boolean): Boolean = dispatch(KeyPress.of(event), anywhere)

    fun dispatch(press: KeyPress, anywhere: Boolean): Boolean {
        if (press.action == KeyEvent.ACTION_UP) {
            return pressed.remove(press.keyCode)
        }

        if (press.action != KeyEvent.ACTION_DOWN) {
            return false
        }

        val shortcut = groups.asSequence()
            .flatMap { it.shortcuts }
            .firstOrNull { shortcut -> shortcut.run != null && (shortcut.anywhere || !anywhere) && shortcut.keys.any { it.matches(press) } }
            ?: return false
        pressed += press.keyCode

        if (press.repeatCount == 0) {
            shortcut.run?.invoke()
        }

        return true
    }

    /** Keys whose press ran a shortcut: their release is the shortcut's too, and goes no further. */
    private val pressed = mutableSetOf<Int>()

    /** The shortcuts for Android's own list, opened with Meta+/ (or Search+/ on a Chromebook). */
    fun forSystem(): List<KeyboardShortcutGroup> = groups.map { group ->
        KeyboardShortcutGroup(
            group.title,
            group.shortcuts.flatMap { shortcut -> shortcut.keys.map { KeyboardShortcutInfo(shortcut.label, it.keyCode, it.modifiers) } },
        )
    }
}

val LocalShortcuts = staticCompositionLocalOf<ShortcutRegistry?> { null }

/** Ctrl+/ opens the list of shortcuts anywhere, as in many apps. */
val ShortcutListKeys = Keys(KeyEvent.KEYCODE_SLASH, ctrl = true, character = '/')

/** Gives the keyboard a screen's shortcuts while it's on display, and lists them. */
@Composable
fun KeyboardShortcuts(groups: List<ShortcutGroup>, priority: Int = ShortcutPriority.List) {
    val registry = LocalShortcuts.current ?: return
    val screen = remember(priority) { ScreenShortcuts(priority) }
    val listName = stringResource(R.string.common_keyboard_shortcuts)

    SideEffect {
        screen.groups = groups
        registry.listName = listName
    }

    DisposableEffect(registry, screen) {
        screen.groups = groups
        registry.add(screen)
        onDispose { registry.remove(screen) }
    }
}

/** Whether a keyboard with keys is connected: a tablet's keyboard cover, a Chromebook's, or one over Bluetooth. */
@Composable
fun hasHardwareKeyboard(): Boolean {
    val configuration = LocalConfiguration.current
    return configuration.keyboard == Configuration.KEYBOARD_QWERTY && configuration.hardKeyboardHidden != Configuration.HARDKEYBOARDHIDDEN_YES
}

/** Every shortcut of the screens on display, group by group. */
@Composable
fun KeyboardShortcutsDialog(groups: List<ShortcutGroup>, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            EscapeCloses(onDismiss)
            Text(stringResource(R.string.common_keyboard_shortcuts))
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(20.dp),
                modifier = Modifier
                    .heightIn(max = 560.dp)
                    .widthIn(max = 520.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                groups.forEach { group -> ShortcutGroupList(group) }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_close)) } },
    )
}

@Composable
private fun ShortcutGroupList(group: ShortcutGroup) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(group.title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)

        group.shortcuts.forEach { shortcut ->
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text(shortcut.label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                ShortcutKeys(shortcut.keys)
            }
        }
    }
}

/** Each way to press it, as key caps; ways separated by "or". */
@Composable
private fun ShortcutKeys(ways: List<Keys>) {
    val spoken = ways.map { it.caps.joinToString(" ") }.reduceOrNull { either, next -> stringResource(R.string.ui_keys_either, either, next) }.orEmpty()

    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.End),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        itemVerticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.semantics(mergeDescendants = true) { contentDescription = spoken },
    ) {
        ways.forEachIndexed { index, keys ->
            if (index > 0) {
                Text(stringResource(R.string.ui_keys_or), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 2.dp))
            }

            keys.caps.forEach { cap -> KeyCap(cap) }
        }
    }
}

@Composable
private fun KeyCap(name: String) {
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        Text(name, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp))
    }
}

/**
 * Escape closes the dialog or sheet this is placed in, as Back does: with
 * predictive back on, Android no longer treats Escape as Back there.
 */
@Composable
fun EscapeCloses(onDismiss: () -> Unit) {
    val view = LocalView.current
    val dismiss by rememberUpdatedState(onDismiss)

    DisposableEffect(view) {
        val window = generateSequence(view.parent) { it.parent }.filterIsInstance<DialogWindowProvider>().firstOrNull()?.window
        val original = window?.callback

        if (window == null || original == null) {
            return@DisposableEffect onDispose {}
        }

        window.callback = object : Window.Callback by original {
            override fun dispatchKeyEvent(event: KeyEvent): Boolean {
                if (event.keyCode != KeyEvent.KEYCODE_ESCAPE) {
                    return original.dispatchKeyEvent(event)
                }

                if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
                    dismiss()
                }

                return true
            }
        }

        onDispose { window.callback = original }
    }
}
