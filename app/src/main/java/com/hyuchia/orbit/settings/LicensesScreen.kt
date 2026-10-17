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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
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
import com.mikepenz.aboutlibraries.Libs
import com.mikepenz.aboutlibraries.entity.Library
import com.hyuchia.orbit.R
import com.hyuchia.orbit.ui.openLink
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Every library and font the app ships, with its license, as the build listed them. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LicensesScreen(onBack: () -> Unit) {
    val resources = LocalResources.current

    val libraries by produceState<List<Library>?>(null) {
        value = withContext(Dispatchers.IO) {
            val json = resources.openRawResource(R.raw.aboutlibraries).bufferedReader().use { it.readText() }
            Libs.Builder().withJson(json).build().libraries.sortedBy { it.name.lowercase() }
        }
    }

    var shown by remember { mutableStateOf<Library?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_open_source_licenses)) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.common_back)) }
                },
            )
        },
    ) { padding ->
        // On a tablet, the list keeps to a column of reading width, and still scrolls from anywhere.
        BoxWithConstraints(Modifier.fillMaxSize().padding(padding)) {
            val side = ((maxWidth - 720.dp) / 2).coerceAtLeast(0.dp)

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
                            Text(listOfNotNull(library.artifactVersion, library.licenses.joinToString { it.name }.ifEmpty { null }).joinToString(" · "))
                        },
                    ) {
                        Text(library.name)
                    }
                }
            }
        }
    }

    shown?.let { library ->
        ModalBottomSheet(onDismissRequest = { shown = null }) {
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
