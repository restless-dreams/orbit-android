package com.hyuchia.orbit.settings

import android.os.Build
import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Keyboard
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hyuchia.orbit.R
import com.hyuchia.orbit.container
import com.hyuchia.orbit.data.AppSettings
import com.hyuchia.orbit.data.ThemeMode
import com.hyuchia.orbit.nearby.SENDER_NAME_LENGTH
import com.hyuchia.orbit.nearby.senderName
import com.hyuchia.orbit.theme.LocalAppSettings
import com.hyuchia.orbit.theme.MapStyles
import com.hyuchia.orbit.ui.EscapeCloses
import com.hyuchia.orbit.ui.ListRow
import com.hyuchia.orbit.ui.LocalShortcuts
import com.hyuchia.orbit.ui.ToggleRow
import com.hyuchia.orbit.ui.hasHardwareKeyboard
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(onAbout: () -> Unit, onTrash: () -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    val repository = context.container.settings
    val settings = LocalAppSettings.current
    val scope = rememberCoroutineScope()

    fun change(update: (AppSettings) -> AppSettings) {
        scope.launch { repository.update(update) }
    }

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text(stringResource(R.string.common_settings)) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.common_back)) }
                },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(bottom = 32.dp)
                // On a tablet, lines as long as the screen is wide are hard to read: the settings keep to a column.
                .wrapContentWidth(Alignment.CenterHorizontally)
                .widthIn(max = 720.dp),
        ) {
            Heading(stringResource(R.string.settings_appearance))
            ThemeModeChoice(settings.themeMode) { mode -> change { it.copy(themeMode = mode) } }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                Toggle(stringResource(R.string.settings_wallpaper_colors), stringResource(R.string.settings_wallpaper_colors_description), settings.dynamicColor) { on ->
                    change { it.copy(dynamicColor = on) }
                }
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                LanguageSetting(Modifier.padding(horizontal = 8.dp))
            }

            Heading(stringResource(R.string.settings_new_maps))
            Text(
                stringResource(R.string.settings_new_map_style_description),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 24.dp),
            )

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
            ) {
                MapStyles.forEach { style ->
                    FilterChip(
                        selected = settings.newMapStyle == style.name,
                        onClick = { change { it.copy(newMapStyle = style.name) } },
                        label = { Text(style.label) },
                    )
                }
            }

            Heading(stringResource(R.string.settings_on_the_map))

            Toggle(stringResource(R.string.settings_haptics), stringResource(R.string.settings_haptics_description), settings.haptics) { on ->
                change { it.copy(haptics = on) }
            }

            Toggle(stringResource(R.string.settings_snap_to_planets), stringResource(R.string.settings_snap_to_planets_description), settings.snapPlanets) { on ->
                change { it.copy(snapPlanets = on) }
            }

            Heading(stringResource(R.string.settings_presenting))
            Text(
                stringResource(R.string.settings_presentation_advance),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 24.dp),
            )

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
            ) {
                listOf(0, 5, 8, 12, 20).forEach { seconds ->
                    FilterChip(
                        selected = settings.presentationSeconds == seconds,
                        onClick = { change { it.copy(presentationSeconds = seconds) } },
                        label = {
                            Text(
                                if (seconds == 0) {
                                    stringResource(R.string.settings_presentation_only_when_i_tap)
                                } else {
                                    pluralStringResource(R.plurals.settings_presentation_seconds, seconds, seconds)
                                },
                            )
                        },
                    )
                }
            }

            Heading(stringResource(R.string.settings_nearby_devices))

            Toggle(
                stringResource(R.string.settings_take_maps_from_nearby_devices),
                stringResource(R.string.settings_take_maps_from_nearby_devices_description),
                settings.receiveFromNearby,
            ) { on ->
                change { it.copy(receiveFromNearby = on) }
            }

            val nearby = LocalContext.current.container.nearby
            var naming by remember { mutableStateOf(false) }
            ListRow(
                headline = { Text(stringResource(R.string.settings_your_name_on_nearby_devices)) },
                supporting = {
                    Column {
                        Text(nearby.nameFor(settings.nearbyName))
                        Text(stringResource(R.string.settings_your_name_on_nearby_devices_description))
                    }
                },
                trailing = { Icon(Icons.Outlined.Edit, contentDescription = null) },
                modifier = Modifier.padding(horizontal = 8.dp).clickable(onClickLabel = stringResource(R.string.settings_change_it)) { naming = true },
            )

            if (naming) {
                NearbyNameDialog(
                    chosen = settings.nearbyName,
                    deviceName = nearby.deviceName,
                    onSave = { name ->
                        naming = false
                        change { it.copy(nearbyName = name) }
                    },
                    onDismiss = { naming = false },
                )
            }

            // The phone listens only while it takes maps (or shows one to a computer): only then has it an address.
            val listening by nearby.listening.collectAsStateWithLifecycle()
            val address = remember(listening) { if (listening) nearby.address() else null }

            val reach = when {
                address != null -> stringResource(R.string.settings_this_device_address, address)
                listening -> stringResource(R.string.settings_connect_to_wifi)
                else -> null
            }

            if (reach != null) {
                Text(
                    reach,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp),
                )
            }

            Heading(stringResource(R.string.common_your_maps))
            MapSafety(onTrash)

            Heading(stringResource(R.string.settings_folder))
            FolderSettings()

            Heading(stringResource(R.string.app_name))
            val shortcuts = LocalShortcuts.current

            if (shortcuts != null && hasHardwareKeyboard()) {
                ListItem(
                    onClick = { shortcuts.listOpen = true },
                    supportingContent = { Text(stringResource(R.string.settings_keyboard_shortcuts_description)) },
                    leadingContent = { Icon(Icons.Outlined.Keyboard, contentDescription = null) },
                    modifier = Modifier.padding(horizontal = 8.dp),
                ) {
                    Text(stringResource(R.string.common_keyboard_shortcuts))
                }
            }

            ListItem(
                onClick = onAbout,
                supportingContent = { Text(stringResource(R.string.settings_about_orbit_description)) },
                leadingContent = { Icon(Icons.Outlined.Info, contentDescription = null) },
                trailingContent = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null) },
                modifier = Modifier.padding(horizontal = 8.dp),
            ) {
                Text(stringResource(R.string.settings_about_orbit))
            }
        }
    }
}

/**
 * The name devices on the same Wi-Fi see: `chosen`, or the device's own when
 * none was. Left empty, it goes back to the device's own.
 */
@Composable
private fun NearbyNameDialog(chosen: String?, deviceName: String, onSave: (String?) -> Unit, onDismiss: () -> Unit) {
    val initial = chosen ?: deviceName
    var name by rememberSaveable(stateSaver = TextFieldValue.Saver) { mutableStateOf(TextFieldValue(initial, TextRange(0, initial.length))) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }

    fun save() {
        onSave(senderName(name.text)?.takeIf { it != senderName(deviceName) })
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            EscapeCloses(onDismiss)
            Text(stringResource(R.string.settings_your_name_on_nearby_devices))
        },
        text = {
            // Short, so the field and the buttons fit above the keyboard on a phone on its side.
            OutlinedTextField(
                value = name,
                onValueChange = { typed -> if (typed.text.codePointCount(0, typed.text.length) <= SENDER_NAME_LENGTH) name = typed },
                placeholder = { Text(deviceName) },
                supportingText = { Text(stringResource(R.string.settings_nearby_name_empty_hint)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { save() }),
                modifier = Modifier.focusRequester(focus),
            )
        },
        confirmButton = { TextButton(onClick = ::save) { Text(stringResource(R.string.settings_save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) } },
    )
}

@Composable
private fun Heading(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 24.dp, bottom = 4.dp),
    )
}

@Composable
private fun Toggle(title: String, description: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    ToggleRow(title, description, checked, onChange, Modifier.padding(horizontal = 8.dp))
}

@get:StringRes
private val ThemeMode.label: Int
    get() = when (this) {
        ThemeMode.System -> R.string.settings_theme_like_the_phone
        ThemeMode.Light -> R.string.settings_theme_light
        ThemeMode.Dark -> R.string.settings_theme_dark
    }

/** Light, dark or like the phone: segments, or a list once text is large enough to crowd them. */
@Composable
private fun ThemeModeChoice(selected: ThemeMode, onPick: (ThemeMode) -> Unit) {
    if (LocalDensity.current.fontScale < 1.5f) {
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp)) {
            ThemeMode.entries.forEachIndexed { index, mode ->
                SegmentedButton(
                    selected = selected == mode,
                    onClick = { onPick(mode) },
                    shape = SegmentedButtonDefaults.itemShape(index, ThemeMode.entries.size),
                ) {
                    Text(stringResource(mode.label))
                }
            }
        }

        return
    }

    Column(Modifier.selectableGroup().padding(vertical = 4.dp)) {
        ThemeMode.entries.forEach { mode ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .selectable(selected = selected == mode, role = Role.RadioButton) { onPick(mode) }
                    .padding(horizontal = 24.dp, vertical = 8.dp),
            ) {
                RadioButton(selected = selected == mode, onClick = null)
                Text(stringResource(mode.label), style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}
