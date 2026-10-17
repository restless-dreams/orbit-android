package com.hyuchia.orbit.settings

import android.app.LocaleConfig
import android.app.LocaleManager
import android.os.Build
import android.os.LocaleList
import androidx.annotation.RequiresApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.hyuchia.orbit.R
import com.hyuchia.orbit.ui.EscapeCloses
import com.hyuchia.orbit.ui.ListRow
import java.util.Locale

/**
 * The language Orbit speaks: the phone's, or one of the languages the app has (its locale
 * config, which the build makes from res/, so a new language shows up here by itself).
 * Android keeps the choice for this app and opens the screen again in it. Before Android 13
 * apps can't choose, and Orbit speaks the phone's language.
 */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
@Composable
fun LanguageSetting(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val manager = remember { context.getSystemService(LocaleManager::class.java) }
    val languages = remember { appLanguages(context) }
    // The chosen language, by its tag; an empty tag is the phone's language.
    var chosen by remember { mutableStateOf(manager.applicationLocales.takeIf { !it.isEmpty }?.get(0)?.language.orEmpty()) }
    var picking by remember { mutableStateOf(false) }
    val phonesLanguage = stringResource(R.string.settings_language_phone)
    fun nameOf(tag: String): String = if (tag.isEmpty()) phonesLanguage else languageName(tag)
    ListRow(
        headline = { Text(stringResource(R.string.settings_language)) },
        supporting = { Text(nameOf(chosen)) },
        leading = { Icon(Icons.Outlined.Language, contentDescription = null) },
        modifier = modifier.clickable(onClickLabel = stringResource(R.string.settings_change_it)) { picking = true },
    )

    if (picking) {
        AlertDialog(
            onDismissRequest = { picking = false },
            title = {
                EscapeCloses { picking = false }
                Text(stringResource(R.string.settings_language))
            },
            text = {
                Column(Modifier.selectableGroup()) {
                    (listOf("") + languages).forEach { tag ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .selectable(selected = chosen == tag, role = Role.RadioButton) {
                                    picking = false

                                    if (tag != chosen) {
                                        chosen = tag
                                        // Android opens the screen again in the new language.
                                        manager.applicationLocales = if (tag.isEmpty()) LocaleList.getEmptyLocaleList() else LocaleList.forLanguageTags(tag)
                                    }
                                }
                                .padding(vertical = 12.dp),
                        ) {
                            RadioButton(selected = chosen == tag, onClick = null)
                            Text(nameOf(tag), style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { picking = false }) { Text(stringResource(R.string.common_cancel)) } },
        )
    }
}

/** The languages the app speaks, by tag: "en", "es". */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
private fun appLanguages(context: android.content.Context): List<String> {
    val supported = LocaleConfig(context).supportedLocales ?: return emptyList()
    return (0 until supported.size()).map { supported[it].language }.distinct()
}

/** A language's name in itself, as people look for it: "English", "Español". */
private fun languageName(tag: String): String {
    val locale = Locale.forLanguageTag(tag)
    return locale.getDisplayLanguage(locale).replaceFirstChar { it.titlecase(locale) }
}
