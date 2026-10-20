package com.hyuchia.orbit.map

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
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
import androidx.compose.material.icons.outlined.AccountTree
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.DataObject
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.FolderZip
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Polyline
import androidx.compose.material.icons.outlined.Print
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
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.hyuchia.orbit.Logs
import com.hyuchia.orbit.R
import com.hyuchia.orbit.page.MapPrinter
import com.hyuchia.orbit.page.PictureRequest
import com.hyuchia.orbit.page.PrintRequest
import com.hyuchia.orbit.page.findActivity
import com.hyuchia.orbit.theme.LocalAppSettings
import com.hyuchia.orbit.theme.mapTheme
import com.hyuchia.orbit.theme.orbitColorScheme
import com.hyuchia.orbit.ui.ListRow
import com.hyuchia.orbit.ui.exportFile
import com.hyuchia.orbit.ui.exportName
import com.hyuchia.orbit.ui.shareFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException

/** A way to take the map out of the app. */
private enum class Format(@StringRes val label: Int, @StringRes val description: Int, val extension: String, val mimeType: String, val icon: ImageVector) {
    WebPage(R.string.common_web_page, R.string.export_web_page_description, "html", "text/html", Icons.Outlined.Language),

    /** Printed through Android's print dialog, which saves it as a PDF too: it has no file of its own to save or share. */
    Pdf(R.string.export_pdf, R.string.export_pdf_description, "pdf", "application/pdf", Icons.Outlined.Print),

    Png(R.string.export_picture, R.string.export_picture_description, "png", "image/png", Icons.Outlined.Image),
    Svg(R.string.export_drawing, R.string.export_drawing_description, "svg", "image/svg+xml", Icons.Outlined.Polyline),
    Markdown(R.string.export_markdown, R.string.export_markdown_description, "md", "text/markdown", Icons.Outlined.Description),
    Yaml(R.string.export_yaml, R.string.export_yaml_description, "yaml", "application/yaml", Icons.Outlined.DataObject),
    Opml(R.string.export_opml, R.string.export_opml_description, "opml", "text/x-opml", Icons.Outlined.AccountTree),
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
    // Why the last file in a format couldn't be made, saved or copied, said in its row until the next try.
    var problem by rememberSaveable { mutableStateOf<Pair<Format, Int>?>(null) }
    val title = ui.map?.title ?: stringResource(R.string.export_default_title)
    val dynamicColor = LocalAppSettings.current.dynamicColor
    // Paper is white: the app's colors in light, whatever mode the phone is in.
    val printTheme = remember(dynamicColor) { orbitColorScheme(context, dark = false, dynamicColor = dynamicColor).mapTheme() }
    // Whether there's a zip to offer: finding out reads the map's file, so not while the panel draws.
    val hasPictures by produceState(false, ui.revision) { value = model.hasPictures() }

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
        Format.Pdf -> null
        Format.Markdown -> model.currentMarkdown().toByteArray()
        Format.Yaml -> model.yaml()?.toByteArray()
        Format.Opml -> model.opml()?.toByteArray()
        Format.Zip -> withContext(Dispatchers.IO) {
            val output = java.io.ByteArrayOutputStream()
            model.writeZip(output)
            output.toByteArray()
        }
    }

    /**
     * The map in `format`, or null, with its row saying so, when it couldn't
     * be made: a picture or video that went as it was read, a file that
     * can't be read, a page that couldn't convert it.
     */
    suspend fun made(format: Format): ByteArray? {
        val data = try {
            bytes(format)
        } catch (failed: IOException) {
            Logs.warn("OrbitExport", "Couldn't make the map's ${format.extension}", failed)
            problem = format to R.string.export_could_not_make
            return null
        }

        // A picture that couldn't be drawn says so itself.
        if (data == null && format != Format.Png && format != Format.Svg) {
            problem = format to couldNotMake(format, ui.readOnly)
        }

        return data
    }

    val saver = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("*/*")) { uri: Uri? ->
        val (format, data) = saving ?: return@rememberLauncherForActivityResult
        saving = null

        if (uri != null) {
            scope.launch {
                if (!withContext(Dispatchers.IO) { save(context, uri, data) }) {
                    problem = format to R.string.export_could_not_save
                }
            }
        }
    }

    fun run(format: Format, share: Boolean) {
        scope.launch {
            problem = null
            working = format
            val data = made(format)
            working = null
            data ?: return@launch
            val name = exportName(context, title, format.extension)

            if (share) {
                // The app it goes to reads it from the phone's storage, which may have no room for it.
                val uri = try {
                    exportFile(context, name) { it.write(data) }
                } catch (failed: IOException) {
                    Logs.warn("OrbitExport", "Couldn't keep the ${format.extension} to share", failed)
                    problem = format to R.string.export_no_room_to_share
                    return@launch
                }

                shareFile(context, uri, format.mimeType, title)
            } else {
                saving = format to data
                saver.launch(name)
            }
        }
    }

    /** Lays the map out for paper, with the picture the chips choose, and opens Android's print dialog. */
    fun print() {
        val activity = context.findActivity() ?: return

        val request = when (framing) {
            Framing.View -> PrintRequest(viewMode = ui.map?.mode ?: "focus", focus = ui.map?.path?.lastOrNull())
            Framing.Whole -> PrintRequest(viewMode = "all")
            Framing.Opening -> PrintRequest(viewMode = "focus")
        }

        scope.launch {
            working = Format.Pdf
            val page = model.printPage(context, printTheme, request)

            if (page == null) {
                working = null
                model.printFailed()
                return@launch
            }

            MapPrinter.print(activity, model.pageTitle(context), page) { printing ->
                working = null

                if (!printing) {
                    model.printFailed()
                }
            }
        }
    }

    fun copy(format: Format) {
        scope.launch {
            problem = null
            val data = made(format) ?: return@launch
            val clipboard = context.getSystemService(ClipboardManager::class.java)

            try {
                clipboard?.setPrimaryClip(ClipData.newPlainText(title, data.decodeToString()))
            } catch (tooBig: RuntimeException) {
                // The clipboard crosses processes: a map past its transaction's size doesn't get there.
                Logs.warn("OrbitExport", "Couldn't copy ${data.size} bytes of ${format.extension}", tooBig)
                problem = format to R.string.export_too_much_to_copy
            }
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

            Format.entries.filter { it != Format.Zip || hasPictures }.forEach { format ->
                val label = stringResource(format.label)
                val failure = problem?.takeIf { it.first == format }?.second
                ListRow(
                    headline = { Text(label) },
                    supporting = {
                        if (failure != null) {
                            Text(stringResource(failure), color = MaterialTheme.colorScheme.error, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                        } else {
                            Text(stringResource(format.description))
                        }
                    },
                    leading = { Icon(format.icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                    trailing = {
                        if (working == format) {
                            LoadingIndicator(Modifier.size(40.dp))
                        } else {
                            Row {
                                if (format == Format.Pdf) {
                                    IconButton(onClick = ::print) { Icon(Icons.Outlined.Print, contentDescription = stringResource(R.string.export_print)) }
                                } else {
                                    if (format == Format.Markdown || format == Format.Yaml || format == Format.Opml) {
                                        IconButton(onClick = { copy(format) }) { Icon(Icons.Outlined.ContentCopy, contentDescription = stringResource(R.string.export_copy_format, label)) }
                                    }

                                    IconButton(onClick = { run(format, share = false) }) { Icon(Icons.Outlined.Save, contentDescription = stringResource(R.string.export_save_format, label)) }
                                    IconButton(onClick = { run(format, share = true) }) { Icon(Icons.Outlined.Share, contentDescription = stringResource(R.string.export_share_format, label)) }
                                }
                            }
                        }
                    },
                    modifier = Modifier.padding(horizontal = 8.dp),
                )
            }
        }
    }
}

/**
 * Why a file in `format` couldn't be made. YAML and OPML are the map written
 * anew, which Orbit doesn't do for a map it couldn't read all of, or that a
 * newer version made: it would lose what it didn't read or doesn't know.
 */
@StringRes
private fun couldNotMake(format: Format, readOnly: ReadOnly?): Int = when {
    format != Format.Yaml && format != Format.Opml -> R.string.export_could_not_make
    readOnly == ReadOnly.Newer -> R.string.export_newer_map_not_written
    readOnly == ReadOnly.Unreadable -> R.string.export_unreadable_map_not_written
    else -> R.string.export_could_not_make
}

/** Writes `data` where the person chose to save it; false when it couldn't go there (a card that's full or gone, a place Orbit may not write to). */
private fun save(context: Context, uri: Uri, data: ByteArray): Boolean = try {
    context.contentResolver.openOutputStream(uri)?.use { it.write(data) } != null
} catch (failed: IOException) {
    Logs.warn("OrbitExport", "Couldn't save an export", failed)
    false
} catch (refused: SecurityException) {
    Logs.warn("OrbitExport", "Not allowed to save an export there", refused)
    false
}
