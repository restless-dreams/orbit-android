package com.hyuchia.orbit.settings

import android.os.Build
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
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
import com.hyuchia.orbit.study.NewCardsADayChoice
import com.hyuchia.orbit.theme.LocalAppSettings
import com.hyuchia.orbit.theme.MapStyles
import com.hyuchia.orbit.ui.ChoiceChips
import com.hyuchia.orbit.ui.ConnectedChoice
import com.hyuchia.orbit.ui.EscapeCloses
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(onAbout: () -> Unit, onTrash: () -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    val repository = context.container.settings
    val settings = LocalAppSettings.current
    val scope = rememberCoroutineScope()

    fun change(update: (AppSettings) -> AppSettings) {
        scope.launch { repository.update(update) }
    }

    val snackbars = remember { SnackbarHostState() }

    SettingsScaffold(stringResource(R.string.common_settings), onBack, BarSize.Large, snackbars) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(bottom = 32.dp)
                // On a tablet, lines as long as the screen is wide are hard to read: the settings keep to a column.
                .wrapContentWidth(Alignment.CenterHorizontally)
                .widthIn(max = ReadingWidth),
        ) {
            Heading(stringResource(R.string.settings_appearance))
            ConnectedChoice(
                choices = ThemeMode.entries,
                selected = settings.themeMode,
                onPick = { mode -> change { it.copy(themeMode = mode) } },
                label = { stringResource(it.label) },
                modifier = Modifier.padding(vertical = 8.dp),
            )

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                Toggle(stringResource(R.string.settings_wallpaper_colors), stringResource(R.string.settings_wallpaper_colors_description), settings.dynamicColor) { on ->
                    change { it.copy(dynamicColor = on) }
                }
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                LanguageSetting()
            }

            Heading(stringResource(R.string.settings_new_maps))
            Text(
                stringResource(R.string.settings_new_map_style_description),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 24.dp),
            )

            ChoiceChips(
                choices = MapStyles,
                selected = MapStyles.firstOrNull { it.name == settings.newMapStyle },
                onPick = { style -> change { it.copy(newMapStyle = style.name) } },
                label = { it.label },
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
            )

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

            ChoiceChips(
                choices = listOf(0, 5, 8, 12, 20),
                selected = settings.presentationSeconds,
                onPick = { seconds -> change { it.copy(presentationSeconds = seconds) } },
                label = { seconds ->
                    if (seconds == 0) {
                        stringResource(R.string.settings_presentation_only_when_i_tap)
                    } else {
                        pluralStringResource(R.plurals.settings_presentation_seconds, seconds, seconds)
                    }
                },
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
            )

            Heading(stringResource(R.string.settings_studying))
            Text(
                stringResource(R.string.settings_new_cards_a_day),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 24.dp),
            )

            NewCardsADayChoice(
                selected = settings.newCardsADay,
                onPick = { count -> change { it.copy(newCardsADay = count) } },
                // On a tablet, five numbers as wide as the column read as one long bar: they keep to a phone's width.
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp).widthIn(max = 420.dp).fillMaxWidth(),
            )

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
            val changeIt = stringResource(R.string.settings_change_it)

            ListItem(
                onClick = { naming = true },
                supportingContent = {
                    Column {
                        Text(nearby.nameFor(settings.nearbyName))
                        Text(stringResource(R.string.settings_your_name_on_nearby_devices_description))
                    }
                },
                trailingContent = { Icon(Icons.Outlined.Edit, contentDescription = null) },
                modifier = Modifier
                    .padding(horizontal = 8.dp)
                    .semantics {
                        onClick(changeIt) {
                            naming = true
                            true
                        }
                    },
            ) {
                Text(stringResource(R.string.settings_your_name_on_nearby_devices))
            }

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
            MapSafety(onTrash, snackbars)

            Heading(stringResource(R.string.private_settings_heading))
            PrivateMapsSettings(settings, ::change)

            Heading(stringResource(R.string.settings_folder))
            FolderSettings()

            Heading(stringResource(R.string.app_name))

            // Only in the copies no store updates: see UPDATES_URL in app/build.gradle.kts.
            if (context.container.updates.available) {
                Toggle(stringResource(R.string.settings_check_for_updates), stringResource(R.string.settings_check_for_updates_description), settings.checkForUpdates) { on ->
                    change { it.copy(checkForUpdates = on) }
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
                onValueChange = { typed ->
                    if (typed.text.codePointCount(0, typed.text.length) <= SENDER_NAME_LENGTH) {
                        name = typed
                    }
                },
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
        // A heading to TalkBack, which then jumps from section to section of the longest screen.
        modifier = Modifier
            .padding(start = 24.dp, end = 24.dp, top = 24.dp, bottom = 4.dp)
            .semantics { heading() },
    )
}

@Composable
private fun Toggle(title: String, description: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    SettingToggle(title, description, checked, onChange)
}

@get:StringRes
private val ThemeMode.label: Int
    get() = when (this) {
        ThemeMode.System -> R.string.settings_theme_system
        ThemeMode.Light -> R.string.settings_theme_light
        ThemeMode.Dark -> R.string.settings_theme_dark
    }
