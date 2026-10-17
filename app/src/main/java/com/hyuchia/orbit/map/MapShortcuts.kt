package com.hyuchia.orbit.map

import android.view.KeyEvent
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.hyuchia.orbit.R
import com.hyuchia.orbit.ui.Keys
import com.hyuchia.orbit.ui.Shortcut
import com.hyuchia.orbit.ui.ShortcutGroup

/** The keys for the map's own shortcuts, which its buttons' hints show too. */
internal object MapKeys {
    val Undo = Keys(KeyEvent.KEYCODE_Z, ctrl = true)
    val Redo = Keys(KeyEvent.KEYCODE_Z, ctrl = true, shift = true)
    val Search = Keys(KeyEvent.KEYCODE_F, ctrl = true)
    val Orbit = Keys(KeyEvent.KEYCODE_1, ctrl = true)
    val WholeMap = Keys(KeyEvent.KEYCODE_2, ctrl = true)
    val Outline = Keys(KeyEvent.KEYCODE_3, ctrl = true)
    val Edit = Keys(KeyEvent.KEYCODE_E, ctrl = true)
    val Present = Keys(KeyEvent.KEYCODE_F5)
}

/**
 * The map's keyboard shortcuts: the app's own, then the keys the map's page
 * answers itself, which the list shows alongside. `back` is what Back does
 * on the map: a panel closes, the map goes up a level, or the map closes.
 */
@Composable
internal fun mapShortcuts(model: MapViewModel, ui: MapUi, present: () -> Unit, back: () -> Unit): List<ShortcutGroup> {
    val idea = ui.sun.takeIf { ui.mode == "focus" }
    val map = listOfNotNull(
        Shortcut(stringResource(R.string.common_undo), listOf(MapKeys.Undo)) { model.undo() },
        Shortcut(stringResource(R.string.common_redo), listOf(MapKeys.Redo, Keys(KeyEvent.KEYCODE_Y, ctrl = true))) { model.redo() },
        Shortcut(stringResource(R.string.common_search_the_map), listOf(MapKeys.Search), anywhere = true) { model.show(Panel.Search) },
        Shortcut(stringResource(R.string.map_shortcut_the_orbit), listOf(MapKeys.Orbit), anywhere = true) {
            if (ui.mode != "focus") {
                model.toggleView()
            }
        },
        Shortcut(stringResource(R.string.map_shortcut_the_whole_map), listOf(MapKeys.WholeMap), anywhere = true) {
            if (ui.mode == "focus") {
                model.toggleView()
            }
        },
        Shortcut(stringResource(R.string.common_outline), listOf(MapKeys.Outline), anywhere = true) { model.show(Panel.Outline) },
        idea?.let { node -> Shortcut(stringResource(R.string.map_shortcut_edit_the_idea_in_focus), listOf(MapKeys.Edit), anywhere = true) { model.show(Panel.Editor(node.id)) } },
        Shortcut(stringResource(R.string.common_present), listOf(MapKeys.Present, Keys(KeyEvent.KEYCODE_ENTER, ctrl = true))) { present() },
        // With a panel open, Escape closes it wherever the focus is, before a field in it could take the key.
        Shortcut(stringResource(R.string.map_shortcut_back_up_a_level), listOf(Keys(KeyEvent.KEYCODE_ESCAPE)), anywhere = ui.panel != null) { back() },
    )
    return listOf(
        ShortcutGroup(stringResource(R.string.map_shortcuts_this_map), map),
        ShortcutGroup(
            stringResource(R.string.map_shortcuts_on_the_map),
            listOf(
                Shortcut(stringResource(R.string.map_shortcut_move_between_ideas), listOf(Keys(KeyEvent.KEYCODE_DPAD_LEFT), Keys(KeyEvent.KEYCODE_DPAD_RIGHT))),
                Shortcut(stringResource(R.string.map_shortcut_into_the_orbit), listOf(Keys(KeyEvent.KEYCODE_DPAD_DOWN), Keys(KeyEvent.KEYCODE_DPAD_UP))),
                Shortcut(stringResource(R.string.map_shortcut_fly_into_the_idea), listOf(Keys(KeyEvent.KEYCODE_ENTER), Keys(KeyEvent.KEYCODE_SPACE))),
                Shortcut(stringResource(R.string.map_shortcut_add_an_idea_under_it), listOf(Keys(KeyEvent.KEYCODE_TAB))),
                Shortcut(stringResource(R.string.map_shortcut_add_an_idea_beside_it), listOf(Keys(KeyEvent.KEYCODE_ENTER, shift = true))),
                Shortcut(stringResource(R.string.map_shortcut_rename_it), listOf(Keys(KeyEvent.KEYCODE_F2))),
                Shortcut(stringResource(R.string.map_shortcut_delete_it), listOf(Keys(KeyEvent.KEYCODE_FORWARD_DEL))),
                Shortcut(stringResource(R.string.map_shortcut_its_menu), listOf(Keys(KeyEvent.KEYCODE_MENU), Keys(KeyEvent.KEYCODE_F10, shift = true))),
                Shortcut(stringResource(R.string.map_shortcut_zoom_in), listOf(Keys(KeyEvent.KEYCODE_PLUS))),
                Shortcut(stringResource(R.string.map_shortcut_zoom_out), listOf(Keys(KeyEvent.KEYCODE_MINUS))),
                Shortcut(stringResource(R.string.map_shortcut_fit_the_view), listOf(Keys(KeyEvent.KEYCODE_0))),
            ),
        ),
        ShortcutGroup(
            stringResource(R.string.map_shortcuts_typing_an_idea),
            listOf(
                Shortcut(stringResource(R.string.map_shortcut_add_it_and_type_the_next), listOf(Keys(KeyEvent.KEYCODE_ENTER))),
                Shortcut(stringResource(R.string.map_shortcut_add_it_and_go_under_it), listOf(Keys(KeyEvent.KEYCODE_TAB))),
                Shortcut(stringResource(R.string.map_shortcut_add_it_and_go_back_up), listOf(Keys(KeyEvent.KEYCODE_TAB, shift = true))),
                Shortcut(stringResource(R.string.map_shortcut_stop_typing), listOf(Keys(KeyEvent.KEYCODE_ESCAPE))),
            ),
        ),
    )
}

/** While presenting, the presentation's own keys, for the list: the presentation answers them itself. */
@Composable
internal fun presentingShortcuts(): List<ShortcutGroup> = listOf(
    ShortcutGroup(
        stringResource(R.string.map_shortcuts_presenting),
        listOf(
            Shortcut(stringResource(R.string.map_shortcut_next), listOf(Keys(KeyEvent.KEYCODE_DPAD_RIGHT), Keys(KeyEvent.KEYCODE_SPACE), Keys(KeyEvent.KEYCODE_PAGE_DOWN))),
            Shortcut(stringResource(R.string.common_back), listOf(Keys(KeyEvent.KEYCODE_DPAD_LEFT), Keys(KeyEvent.KEYCODE_PAGE_UP))),
            Shortcut(stringResource(R.string.map_shortcut_play_or_pause), listOf(Keys(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE))),
            Shortcut(stringResource(R.string.map_shortcut_back_to_the_map), listOf(Keys(KeyEvent.KEYCODE_ESCAPE))),
            Shortcut(stringResource(R.string.common_stop_presenting), listOf(Keys(KeyEvent.KEYCODE_ESCAPE))),
        ),
    ),
)
