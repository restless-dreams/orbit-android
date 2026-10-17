package com.hyuchia.orbit.map

import android.content.ClipData
import android.content.ClipboardManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.annotation.StringRes
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.DataObject
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.FolderZip
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Polyline
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.hyuchia.orbit.R
import com.hyuchia.orbit.page.PictureRequest
import com.hyuchia.orbit.ui.ListRow
import com.hyuchia.orbit.ui.exportFile
import com.hyuchia.orbit.ui.exportName
import com.hyuchia.orbit.ui.shareFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** A way to take the map out of the app. */
private enum class Format(@StringRes val label: Int, @StringRes val description: Int, val extension: String, val mimeType: String, val icon: ImageVector) {
    WebPage(R.string.common_web_page, R.string.export_web_page_description, "html", "text/html", Icons.Outlined.Language),
    Png(R.string.export_picture, R.string.export_picture_description, "png", "image/png", Icons.Outlined.Image),
    Svg(R.string.export_drawing, R.string.export_drawing_description, "svg", "image/svg+xml", Icons.Outlined.Polyline),
    Markdown(R.string.export_markdown, R.string.export_markdown_description, "md", "text/markdown", Icons.Outlined.Description),
    Yaml(R.string.export_yaml, R.string.export_yaml_description, "yaml", "application/yaml", Icons.Outlined.DataObject),
    Zip(R.string.export_zip, R.string.export_zip_description, "zip", "application/zip", Icons.Outlined.FolderZip),
}

/** Which part of the map a picture shows. */
private enum class Framing(@StringRes val label: Int) {
    View(R.string.export_this_view),
    Whole(R.string.export_whole_map),
    Opening(R.string.export_as_it_opens),
}

/** Pictures, drawings and files of the map, to share or save. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ExportPanel(model: MapViewModel, ui: MapUi) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var framing by rememberSaveable { mutableStateOf(Framing.View) }
    var background by rememberSaveable { mutableStateOf(true) }
    var working by remember { mutableStateOf<Format?>(null) }
    var saving by remember { mutableStateOf<Pair<Format, ByteArray>?>(null) }
    val title = ui.map?.title ?: stringResource(R.string.export_default_title)

    suspend fun bytes(format: Format): ByteArray? = when (format) {
        Format.Png, Format.Svg -> {
            val markdown = if (framing == Framing.View) null else model.currentMarkdown()
            model.picture(
                PictureRequest(
                    markdown = markdown,
                    format = format.extension,
                    width = if (framing == Framing.Whole) 1600 else 1200,
                    height = if (framing == Framing.Whole) 1100 else 1200,
                    scale = if (framing == Framing.View) 3.0 else 2.0,
                    transparent = !background,
                    viewMode = if (framing == Framing.Whole) "all" else "focus",
                ),
            )
        }
        Format.WebPage -> model.webPage(context)?.toByteArray()
        Format.Markdown -> model.currentMarkdown().toByteArray()
        Format.Yaml -> model.yaml()?.toByteArray()
        Format.Zip -> withContext(Dispatchers.IO) {
            val output = java.io.ByteArrayOutputStream()
            model.writeZip(output)
            output.toByteArray()
        }
    }

    val saver = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("*/*")) { uri: Uri? ->
        val (_, data) = saving ?: return@rememberLauncherForActivityResult
        saving = null

        if (uri != null) {
            scope.launch(Dispatchers.IO) { context.contentResolver.openOutputStream(uri)?.use { it.write(data) } }
        }
    }

    fun run(format: Format, share: Boolean) {
        scope.launch {
            working = format
            val data = bytes(format)
            working = null
            data ?: return@launch
            val name = exportName(context, title, format.extension)

            if (share) {
                val uri = exportFile(context, name) { it.write(data) }
                shareFile(context, uri, format.mimeType, title)
            } else {
                saving = format to data
                saver.launch(name)
            }
        }
    }

    fun copy(format: Format) {
        scope.launch {
            val data = bytes(format) ?: return@launch
            val clipboard = context.getSystemService(ClipboardManager::class.java)
            clipboard?.setPrimaryClip(ClipData.newPlainText(title, data.decodeToString()))
        }
    }

    PanelFrame(title = stringResource(R.string.export_and_share), sheet = true, onDismiss = { model.show(null) }) {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(bottom = 32.dp),
        ) {
            Column(Modifier.padding(horizontal = 24.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.export_pictures_show), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)

                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Framing.entries.forEach { option ->
                        FilterChip(selected = framing == option, onClick = { framing = option }, label = { Text(stringResource(option.label)) })
                    }

                    FilterChip(selected = background, onClick = { background = !background }, label = { Text(stringResource(R.string.export_background)) })
                }
            }

            Format.entries.filter { it != Format.Zip || model.hasPictures() }.forEach { format ->
                val label = stringResource(format.label)
                ListRow(
                    headline = { Text(label) },
                    supporting = { Text(stringResource(format.description)) },
                    leading = { Icon(format.icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                    trailing = {
                        if (working == format) {
                            LoadingIndicator(Modifier.size(40.dp))
                        } else {
                            Row {
                                if (format == Format.Markdown || format == Format.Yaml) {
                                    IconButton(onClick = { copy(format) }) { Icon(Icons.Outlined.ContentCopy, contentDescription = stringResource(R.string.export_copy_format, label)) }
                                }

                                IconButton(onClick = { run(format, share = false) }) { Icon(Icons.Outlined.Save, contentDescription = stringResource(R.string.export_save_format, label)) }
                                IconButton(onClick = { run(format, share = true) }) { Icon(Icons.Outlined.Share, contentDescription = stringResource(R.string.export_share_format, label)) }
                            }
                        }
                    },
                    modifier = Modifier.padding(horizontal = 8.dp),
                )
            }
        }
    }
}
