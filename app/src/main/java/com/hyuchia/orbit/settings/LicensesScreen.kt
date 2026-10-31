package com.hyuchia.orbit.settings

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.coerceAtLeast
import androidx.compose.ui.unit.dp
import com.hyuchia.orbit.R
import com.hyuchia.orbit.page.OrbitBuild
import com.hyuchia.orbit.ui.EscapeCloses
import com.hyuchia.orbit.ui.openLink
import com.mikepenz.aboutlibraries.Libs
import com.mikepenz.aboutlibraries.entity.Library
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Orbit's own entry in the list (`app/config/libraries/orbit.json`), which has no version of its own. */
internal const val ORBIT_LIBRARY = "@restless-dreams/orbit"

/**
 * A library's version as the list shows it. Orbit's is the one the map's
 * pages are built with (`assets/map/orbit.json`, which `web/build.ts` writes
 * from Orbit's package.json), so it's never a number kept by hand.
 */
internal fun shownVersion(uniqueId: String, artifactVersion: String?, orbit: OrbitBuild?): String? = if (uniqueId == ORBIT_LIBRARY) orbit?.version else artifactVersion

/** Every library and font the app ships, with its license, as the build listed them. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LicensesScreen(onBack: () -> Unit) {
    val resources = LocalResources.current
    val context = LocalContext.current

    val libraries by produceState<List<Library>?>(null) {
        value = withContext(Dispatchers.IO) {
            val json = resources.openRawResource(R.raw.aboutlibraries).bufferedReader().use { it.readText() }
            Libs.Builder().withJson(json).build().libraries.sortedBy { it.name.lowercase() }
        }
    }

    val orbit by produceState<OrbitBuild?>(null) {
        value = withContext(Dispatchers.IO) { OrbitBuild.bundled(context) }
    }

    var shown by remember { mutableStateOf<Library?>(null) }

    SettingsScaffold(stringResource(R.string.settings_open_source_licenses), onBack) { padding ->
        // On a tablet, the list keeps to a column of reading width, and still scrolls from anywhere.
        BoxWithConstraints(Modifier.fillMaxSize().padding(padding)) {
            val side = ((maxWidth - ReadingWidth) / 2).coerceAtLeast(0.dp)

            LazyColumn(contentPadding = PaddingValues(start = side, end = side, bottom = 24.dp), modifier = Modifier.fillMaxSize()) {
                item {
                    Text(
                        stringResource(R.string.settings_licenses_introduction),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }

                items(libraries.orEmpty(), key = { it.uniqueId }) { library ->
                    ListItem(
                        onClick = { shown = library },
                        supportingContent = {
                            Text(listOfNotNull(shownVersion(library.uniqueId, library.artifactVersion, orbit), library.licenses.joinToString { it.name }.ifEmpty { null }).joinToString(" · "))
                        },
                        modifier = Modifier.padding(horizontal = 8.dp),
                    ) {
                        Text(library.name)
                    }
                }
            }
        }
    }

    shown?.let { library ->
        // Whole at once, as a license is read from the top; Escape closes it, as Back does.
        ModalBottomSheet(onDismissRequest = { shown = null }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
            EscapeCloses { shown = null }
            LicenseText(library)
        }
    }
}

@Composable
private fun LicenseText(library: Library) {
    val context = LocalContext.current
    val text = library.licenses.joinToString("\n\n") { license -> license.licenseContent?.takeIf { it.isNotBlank() } ?: license.name }

    Column(
        modifier = Modifier
            .padding(horizontal = 24.dp)
            .padding(bottom = 32.dp)
            .verticalScroll(rememberScrollState()),
    ) {
        Text(library.name, style = MaterialTheme.typography.titleLarge)

        library.developers.mapNotNull { it.name }.takeIf { it.isNotEmpty() }?.let { names ->
            Text(names.joinToString(), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        library.website?.let { website ->
            TextButton(onClick = { openLink(context, website) }) { Text(website) }
        }

        Text(text, style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace), modifier = Modifier.padding(top = 8.dp))
    }
}
