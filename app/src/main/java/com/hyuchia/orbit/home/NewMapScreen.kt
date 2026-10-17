package com.hyuchia.orbit.home

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.FileOpen
import androidx.compose.material.icons.automirrored.outlined.NoteAdd
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumFlexibleTopAppBar
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.hyuchia.orbit.R
import com.hyuchia.orbit.container
import com.hyuchia.orbit.data.ImportResult
import com.hyuchia.orbit.data.MapTemplate
import com.hyuchia.orbit.data.MapTemplates
import com.hyuchia.orbit.data.MapText
import com.hyuchia.orbit.data.readTemplate
import com.hyuchia.orbit.rememberWords
import com.hyuchia.orbit.ui.orbitViewModel
import kotlinx.coroutines.launch

/** Ways to start a map: blank, from a template drawn by Orbit, or from a file. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewMapScreen(onCreated: (String) -> Unit, onImported: (ImportResult) -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    val container = context.container
    val model = orbitViewModel { HomeViewModel(it) }
    val scope = rememberCoroutineScope()
    var naming by rememberSaveable { mutableStateOf(false) }

    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            scope.launch { onImported(container.importer.importFile(uri, context.contentResolver)) }
        }
    }

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    fun start(template: MapTemplate) {
        scope.launch {
            val markdown = context.readTemplate(template)
            onCreated(container.maps.add(markdown))
        }
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            MediumFlexibleTopAppBar(
                title = { Text(stringResource(R.string.home_start_a_map)) },
                subtitle = { Text(stringResource(R.string.home_templates_drawn_live)) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.common_back)) }
                },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { padding ->
        val direction = LocalLayoutDirection.current

        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 168.dp),
            contentPadding = PaddingValues(
                start = padding.calculateStartPadding(direction) + 16.dp,
                top = padding.calculateTopPadding() + 8.dp,
                end = padding.calculateEndPadding(direction) + 16.dp,
                bottom = padding.calculateBottomPadding() + 24.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    StartCard(
                        stringResource(R.string.common_blank_map),
                        stringResource(R.string.home_blank_map_description),
                        Icons.AutoMirrored.Outlined.NoteAdd,
                        Modifier.weight(1f),
                    ) { naming = true }

                    StartCard(
                        stringResource(R.string.common_open_a_file),
                        stringResource(R.string.home_open_a_file_description),
                        Icons.Outlined.FileOpen,
                        Modifier.weight(1f),
                    ) {
                        importer.launch(ImportTypes)
                    }
                }
            }

            item(span = { GridItemSpan(maxLineSpan) }) {
                Text(
                    stringResource(R.string.home_templates),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 4.dp, top = 12.dp),
                )
            }

            items(MapTemplates, key = { it.file }) { template ->
                TemplateCard(template) { start(template) }
            }
        }
    }

    if (naming) {
        TitleDialog(
            heading = stringResource(R.string.common_new_map),
            confirm = stringResource(R.string.home_create),
            initial = "",
            onConfirm = { title ->
                naming = false
                model.create(title, onCreated)
            },
            onDismiss = { naming = false },
        )
    }
}

@Composable
private fun StartCard(title: String, description: String, icon: ImageVector, modifier: Modifier, onClick: () -> Unit) {
    OutlinedCard(onClick = onClick, shape = RoundedCornerShape(24.dp), modifier = modifier) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun TemplateCard(template: MapTemplate, onClick: () -> Unit) {
    val context = LocalContext.current
    val words = rememberWords()
    val markdown by produceState<String?>(null, template) { value = context.readTemplate(template) }

    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .clickable(role = Role.Button, onClickLabel = stringResource(R.string.home_start_a_map_from_template), onClick = onClick),
    ) {
        Column {
            val text = markdown

            if (text != null) {
                val outline = MapText.outline(text, words)
                MapThumbnail(
                    id = "template-${template.file.substringBeforeLast('.')}",
                    contentKey = text.hashCode().toUInt().toString(16),
                    style = outline.style,
                    branches = outline.branches,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(16f / 10f)
                        .padding(6.dp)
                        .clip(RoundedCornerShape(19.dp)),
                ) { text }
            } else {
                MapCardPlaceholder()
            }

            Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(template.title, style = MaterialTheme.typography.titleMedium)
                Text(template.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
