package com.hyuchia.orbit.settings

import android.app.LocaleConfig
import android.app.LocaleManager
import android.content.Context
import android.os.Build
import android.os.LocaleList
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.hyuchia.orbit.R
import com.hyuchia.orbit.ui.ConnectedChoice
import java.text.Collator
import java.util.Locale

/**
 * The language Orbit speaks: the device's, or one of the languages the app has (its locale
 * config, which the build makes from res/, so a new language shows up here by itself), each
 * named in itself, as people look for it. Android keeps the choice for this app and opens the
 * screen again in it. Before Android 13 apps can't choose, and Orbit speaks the device's language.
 */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
@Composable
fun LanguageSetting() {
    val context = LocalContext.current
    val manager = remember { context.getSystemService(LocaleManager::class.java) }
    // By tag; an empty tag is the device's language.
    val languages = remember { listOf("") + appLanguages(context) }
    var chosen by remember { mutableStateOf(manager.applicationLocales.takeIf { !it.isEmpty }?.get(0)?.language.orEmpty()) }

    // The globe is what people look for when the app speaks a language they can't read.
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 12.dp),
    ) {
        Icon(Icons.Outlined.Language, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
        Text(stringResource(R.string.settings_language), style = MaterialTheme.typography.bodyLarge)
    }

    ConnectedChoice(
        choices = languages,
        selected = chosen,
        onPick = { tag ->
            if (tag != chosen) {
                chosen = tag
                // Android opens the screen again in the new language.
                manager.applicationLocales = if (tag.isEmpty()) LocaleList.getEmptyLocaleList() else LocaleList.forLanguageTags(tag)
            }
        },
        label = { tag -> if (tag.isEmpty()) stringResource(R.string.settings_language_system) else languageName(tag) },
        modifier = Modifier.padding(vertical = 8.dp),
    )
}

/** The languages the app speaks, by tag: "en", "es", "pt". */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
private fun appLanguages(context: Context): List<String> {
    val supported = LocaleConfig(context).supportedLocales ?: return emptyList()
    return inNameOrder((0 until supported.size()).map { supported[it].language })
}

/**
 * Languages by tag, each once, in the order of their names in themselves: English, Español,
 * Português. Android's locale config hands them over in an order of its own making, which
 * changes as languages come.
 */
internal fun inNameOrder(tags: List<String>): List<String> {
    val collator = Collator.getInstance(Locale.ROOT)
    return tags.distinct().sortedWith(compareBy(collator) { languageName(it) })
}

/** A language's name in itself, as people look for it: "English", "Español". */
internal fun languageName(tag: String): String {
    val locale = Locale.forLanguageTag(tag)
    return locale.getDisplayLanguage(locale).replaceFirstChar { it.titlecase(locale) }
}
