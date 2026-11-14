package com.hyuchia.orbit.map

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.webkit.MimeTypeMap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Notes
import androidx.compose.material.icons.outlined.AccountTree
import androidx.compose.material.icons.outlined.AttachFile
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.DataObject
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.FolderZip
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.LocationOff
import androidx.compose.material.icons.outlined.Polyline
import androidx.compose.material.icons.outlined.Print
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.hyuchia.orbit.Logs
import com.hyuchia.orbit.R
import com.hyuchia.orbit.Words
import com.hyuchia.orbit.container
import com.hyuchia.orbit.data.AsTheyAreFiles
import com.hyuchia.orbit.data.FileChanged
import com.hyuchia.orbit.data.MediaSummary
import com.hyuchia.orbit.data.OutgoingMedia
import com.hyuchia.orbit.data.OutputFailed
import com.hyuchia.orbit.data.Uncleaned
import com.hyuchia.orbit.page.MapPrinter
import com.hyuchia.orbit.page.PictureRequest
import com.hyuchia.orbit.page.PrintRequest
import com.hyuchia.orbit.page.ViewMode
import com.hyuchia.orbit.page.findActivity
import com.hyuchia.orbit.rememberWords
import com.hyuchia.orbit.theme.LocalAppSettings
import com.hyuchia.orbit.theme.mapTheme
import com.hyuchia.orbit.theme.orbitColorScheme
import com.hyuchia.orbit.ui.ConnectedChoice
import com.hyuchia.orbit.ui.ListRow
import com.hyuchia.orbit.ui.MediaWords
import com.hyuchia.orbit.ui.WhatGoes
import com.hyuchia.orbit.ui.exportName
import com.hyuchia.orbit.ui.forgetExport
import com.hyuchia.orbit.ui.makeExport
import com.hyuchia.orbit.ui.rememberAsTheyAre
import com.hyuchia.orbit.ui.shareMapFile
import com.hyuchia.orbit.ui.thisDeviceIcon
import com.hyuchia.orbit.ui.uncleanedLines
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.io.OutputStream

/**
 * The system's save screen, with the type Android knows for the name's
 * extension: asked for a file of any type, it took a name already there to
 * be all name, and numbered it after the extension, "Map.svg (1)", so the
 * file no longer opened as what it is. Now it's "Map (1).svg".
 */
private class SaveDocument : ActivityResultContracts.CreateDocument("application/octet-stream") {
    override fun createIntent(context: Context, input: String): Intent = super.createIntent(context, input).setType(savedType(input.substringAfterLast('.', "")))
}

/** The type Android gives files ending in `extension`, which keeps it when a name is taken; for one it doesn't know, any file's. */
private fun savedType(extension: String): String = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension.lowercase()) ?: "application/octet-stream"

/** A way to take the map out of the app; `named` is how a sentence names it, after Save, Share or Copy ("Save the web page"). */
internal enum class Format(@StringRes val label: Int, @StringRes val description: Int, @StringRes val named: Int, val extension: String, val mimeType: String, val icon: ImageVector) {
    WebPage(R.string.common_web_page, R.string.export_web_page_description, R.string.export_web_page_named, "html", "text/html", Icons.Outlined.Language),

    /** Printed through Android's print dialog, which saves it as a PDF too: it has no file of its own to save or share. */
    Pdf(R.string.export_pdf, R.string.export_pdf_description, R.string.export_pdf_named, "pdf", "application/pdf", Icons.Outlined.Print),

    Png(R.string.export_picture, R.string.export_picture_description, R.string.export_picture_named, "png", "image/png", Icons.Outlined.Image),
    Svg(R.string.export_drawing, R.string.export_drawing_description, R.string.export_drawing_named, "svg", "image/svg+xml", Icons.Outlined.Polyline),
    Markdown(R.string.export_markdown, R.string.export_markdown_description, R.string.export_markdown_named, "md", "text/markdown", Icons.Outlined.Description),
    Yaml(R.string.export_yaml, R.string.export_yaml_description, R.string.export_yaml_named, "yaml", "application/yaml", Icons.Outlined.DataObject),
    Opml(R.string.export_opml, R.string.export_opml_description, R.string.export_opml_named, "opml", "text/x-opml", Icons.Outlined.AccountTree),
    Zip(R.string.export_zip, R.string.export_zip_description, R.string.export_zip_named, "zip", "application/zip", Icons.Outlined.FolderZip),
}

/** Which part of the map a picture shows. */
internal enum class Framing(@StringRes val label: Int) {
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
    // A file made to save while the save screen is open, in the cache: its format, and where it is. Kept as the window
    // is made again, as it is when the phone turns while the save screen is open.
    var saving by rememberSaveable { mutableStateOf<Pair<Format, String>?>(null) }
    // Why the last file in a format couldn't be made, saved or copied, said in its row until the next try.
    var problem by rememberSaveable { mutableStateOf<Pair<Format, Int>?>(null) }
    // The format last copied, said in its row before Android 13, which says so itself from then on.
    var copied by rememberSaveable { mutableStateOf<Format?>(null) }
    // How many pictures the print just opened goes without: said once Orbit is back from Android's print dialog, which
    // covers the map as it opens, so the message is seen.
    var printedWithout by remember { mutableIntStateOf(0) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle

    DisposableEffect(lifecycle) {
        var away = false

        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE) {
                away = true
            } else if (event == Lifecycle.Event.ON_RESUME && away) {
                away = false

                if (printedWithout > 0) {
                    model.printedWithout(printedWithout)
                    printedWithout = 0
                }
            }
        }

        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }

    val title = ui.map?.title ?: stringResource(R.string.export_default_title)
    val dynamicColor = LocalAppSettings.current.dynamicColor
    // Paper is white: the app's colors in light, whatever mode the phone is in.
    val printTheme = remember(dynamicColor) { orbitColorScheme(context, dark = false, dynamicColor = dynamicColor).mapTheme() }
    val words = rememberWords()
    val device = thisDeviceIcon()
    // Files that can't go clean stay on this device unless the person chooses, as a way out starts, to share them as they are.
    val asTheyAre = rememberAsTheyAre()
    val answers = asTheyAre.chosen
    // What goes with each way out, and whether there's a zip to offer: finding out reads the map's file, so not while the panel draws.
    val uncleaned by produceState(Uncleaned(), ui.revision) { value = model.uncleaned() }
    // Those chosen to go as they are, each as it is now: one that changed since was chosen as it was, and stays.
    val chosen = answers.chosenIn(uncleaned)
    val media by produceState(MediaSummary(), ui.revision, uncleaned, chosen) { value = model.mediaSummary(leavingOut = uncleaned.names - chosen) }
    val clean by produceState(MediaSummary(), ui.revision, uncleaned) { value = model.mediaSummary(leavingOut = uncleaned.names) }
    // The sounds and live photos' motions a web page of the map carries inside, with those of `chosen`: the rest stay here.
    val soundsInPage by produceState(emptySet<String>(), ui.revision, answers) { value = model.soundsInPage(answers) }
    val motionsInPage by produceState(emptySet<String>(), ui.revision, answers) { value = model.motionsInPage(answers) }
    // A zip past what older versions of Orbit open says to update Orbit wherever it's opened.
    val pastOlderZips by produceState(false, ui.revision) { value = model.pastOlderZips() }
    // A web page carries up to 100 MiB of the map's pictures: the rest stay here, as its sounds and motions past theirs do.
    val picturesOutOfPage by produceState(0, ui.revision) { value = model.picturesOutOfPage() }
    // A YAML map's own text is its YAML, and its markdown is Orbit's writing of it.
    val yaml by produceState(false, ui.revision) { value = model.isYaml() }

    /** A picture or a drawing of the map, as the chips choose; a drawing's pictures that can't go clean go only when they're among `files`. */
    suspend fun picture(format: Format, files: AsTheyAreFiles): ByteArray? {
        val markdown = if (framing == Framing.View) null else model.currentMarkdown()
        val drawn = model.picture(
            PictureRequest(
                markdown = markdown,
                format = format.extension,
                width = if (framing == Framing.Whole) 1600 else 1200,
                height = if (framing == Framing.Whole) 1100 else 1200,
                scale = if (framing == Framing.View) 3.0 else 2.0,
                transparent = !background,
                view = framedView(framing, opensIn = ui.map?.options?.view),
            ),
        )

        // A drawing carries its pictures inside it, so they go as they would in a zip: each as the person chose for it.
        return if (format == Format.Svg && drawn != null) {
            withContext(Dispatchers.IO) { OutgoingMedia.drawing(drawn.decodeToString(), files).toByteArray() }
        } else {
            drawn
        }
    }

    /** The map as text in `format`, to copy, save or share: markdown, YAML or OPML; null when it can't be written so. */
    suspend fun text(format: Format): String? = when (format) {
        Format.Markdown -> model.markdownText()
        Format.Yaml -> model.yaml()
        Format.Opml -> model.opml()
        else -> null
    }

    /**
     * Writes the map in `format` to `output`, with those of the map's files
     * that can't go clean that are in `files` going as they are; false when
     * it couldn't be made. A web page and a zip are written as they're made,
     * a piece at a time, once `room` has room for the most they may come to:
     * either can be bigger than the map's files together.
     */
    suspend fun write(format: Format, files: AsTheyAreFiles, output: OutputStream, room: (Long) -> Unit): Boolean = when (format) {
        Format.Png, Format.Svg -> picture(format, files)?.also(output::write) != null
        Format.WebPage -> model.writeWebPage(context, output, files, room)
        Format.Pdf -> false
        Format.Markdown, Format.Yaml, Format.Opml -> text(format)?.toByteArray()?.also(output::write) != null
        Format.Zip -> {
            model.writeZip(output, files, room)
            true
        }
    }

    /**
     * The map in `format`, in a file of its own among the cache's exports,
     * or null, with its row saying so, when it couldn't be made: a picture or
     * video that went as it was read, a file that can't be read, a page that
     * couldn't convert it, a storage too full for it. Those of the map's
     * files that can't go clean that are in `files` go as they are; one that
     * changed since the person was asked about it says [FileChanged], to be
     * asked about again, and nothing is made.
     */
    suspend fun made(format: Format, files: AsTheyAreFiles): File? {
        val file = try {
            makeExport(context, exportName(context, title, format.extension)) { output -> write(format, files, output, room = this) }
        } catch (changed: FileChanged) {
            throw changed
        } catch (full: OutputFailed) {
            Logs.warn("OrbitExport", "No room for the map's ${format.extension}", full)
            problem = format to R.string.export_no_room
            return null
        } catch (failed: IOException) {
            Logs.warn("OrbitExport", "Couldn't make the map's ${format.extension}", failed)
            problem = format to R.string.export_could_not_make
            return null
        }

        // A picture that couldn't be drawn says so itself.
        if (file == null && format != Format.Png && format != Format.Svg) {
            problem = format to couldNotMake(format, ui.readOnly, yaml)
        }

        return file
    }

    val saver = rememberLauncherForActivityResult(SaveDocument()) { uri: Uri? ->
        val (format, path) = saving ?: return@rememberLauncherForActivityResult
        saving = null
        val file = File(path)

        scope.launch {
            val saved = withContext(Dispatchers.IO) {
                try {
                    uri == null || save(context, uri, file)
                } finally {
                    forgetExport(file)
                }
            }

            if (!saved) {
                problem = format to R.string.export_could_not_save
            }
        }
    }

    /**
     * Makes the map in `format` and shares or saves it, with the files of
     * `files` as they are. One of them that changed since the person
     * answered about it stops it, and it's asked about again as it is now,
     * and started again: once, after which it says it couldn't be made.
     */
    fun go(format: Format, share: Boolean, files: AsTheyAreFiles, again: Boolean) {
        scope.launch {
            problem = null
            copied = null
            working = format

            val file = try {
                made(format, files)
            } catch (changed: FileChanged) {
                working = null

                if (again) {
                    problem = format to R.string.export_could_not_make
                } else {
                    asTheyAre.askAgain(model.uncleaned().carried(format)) { now -> go(format, share, now, again = true) }
                }

                return@launch
            }

            working = null
            file ?: return@launch

            if (share) {
                // A private map that locked while its file was made opens nothing, and its file goes.
                try {
                    shareMapFile(context, model.id, file, format.mimeType, title)
                } catch (failed: IOException) {
                    Logs.warn("OrbitExport", "Couldn't share the ${format.extension}", failed)
                    forgetExport(file)
                    problem = format to R.string.export_could_not_make
                }
            } else if (!context.container.privateMaps.hidesNow(model.id)) {
                // The save screen shows the file's name, the map's: not for a private map that locked while its file was made.
                saving = format to file.path
                saver.launch(file.name)
            } else {
                forgetExport(file)
            }
        }
    }

    fun run(format: Format, share: Boolean) = asTheyAre.then(uncleaned.carried(format)) { files -> go(format, share, files, again = false) }

    /**
     * Lays the map out for paper with the pictures of `files` as they are,
     * and opens Android's print dialog. One of them that changed since the
     * person answered about it is asked about again, once, as [go] does.
     */
    fun startPrinting(activity: Activity, request: PrintRequest, files: AsTheyAreFiles, again: Boolean = false) {
        scope.launch {
            working = Format.Pdf

            val page = try {
                model.printPage(context, printTheme, request, files)
            } catch (changed: FileChanged) {
                working = null

                if (again) {
                    model.printFailed()
                } else {
                    asTheyAre.askAgain(model.uncleaned().carried(Format.Pdf)) { now -> startPrinting(activity, request, now, again = true) }
                }

                return@launch
            }

            if (page == null) {
                working = null
                model.printFailed()
                return@launch
            }

            // Android's print dialog shows the map: not for a private map that locked while its page was laid out.
            val goes = { !context.container.privateMaps.hidesNow(model.id) }

            if (!goes()) {
                page.delete()
                working = null
                return@launch
            }

            MapPrinter.print(activity, model.pageTitle(context), page, goes) { printing, missing ->
                working = null

                if (!printing && goes()) {
                    model.printFailed()
                } else if (printing && missing > 0) {
                    printedWithout = missing
                }
            }
        }
    }

    /** Lays the map out for paper, with the picture the chips choose, and opens Android's print dialog. */
    fun print() {
        val activity = context.findActivity() ?: return
        val request = printRequest(framing, ui.mode, focus = ui.map?.path?.lastOrNull(), opensIn = ui.map?.options?.view)
        asTheyAre.then(uncleaned.carried(Format.Pdf)) { files -> startPrinting(activity, request, files) }
    }

    fun copy(format: Format) {
        // A private map that locks while its text is made puts nothing on the clipboard, however quickly it's unlocked again.
        val locks = model.locks()

        scope.launch {
            problem = null
            copied = null
            working = format

            val copying = try {
                text(format)
            } catch (failed: IOException) {
                Logs.warn("OrbitExport", "Couldn't make the map's ${format.extension} to copy", failed)
                working = null
                problem = format to R.string.export_could_not_make
                return@launch
            }

            working = null

            if (copying == null) {
                problem = format to couldNotMake(format, ui.readOnly, yaml)
                return@launch
            }

            if (model.lockedSince(locks)) {
                return@launch
            }

            // A private map's text is marked sensitive, as its ideas are when they're copied.
            if (!context.container.clipboard.copyText(title, copying, sensitive = model.isPrivate())) {
                problem = format to R.string.export_too_much_to_copy
            } else if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                copied = format
            }
        }
    }

    PanelFrame(title = stringResource(R.string.export_and_share), sheet = true, onDismiss = { model.show(null) }) {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(bottom = 32.dp),
        ) {
            Column(Modifier.padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    stringResource(R.string.export_pictures_show),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .padding(horizontal = 24.dp)
                        .semantics { heading() },
                )
                // One of three, as connected buttons; the background is on or off beside them, so it stays a chip.
                ConnectedChoice(Framing.entries, framing, onPick = { framing = it }, label = { stringResource(it.label) })
                FilterChip(
                    selected = background,
                    onClick = { background = !background },
                    label = { Text(stringResource(R.string.export_background)) },
                    modifier = Modifier.padding(horizontal = 24.dp),
                )
            }

            // A zip is offered for a map with files of its own, even when none of them can go clean: they may yet go as they are.
            Format.entries.filter { it != Format.Zip || !media.isEmpty || !uncleaned.isEmpty }.forEach { format ->
                val label = stringResource(format.label)
                val named = stringResource(format.named)
                val failure = problem?.takeIf { it.first == format }?.second
                val busy = working == format
                ListRow(
                    headline = { Text(label) },
                    supporting = {
                        Column {
                            Text(stringResource(format.description))

                            carries(format, media, !uncleaned.isEmpty, PageCarries(soundsInPage.size, motionsInPage.size, picturesOutOfPage, files = clean.files + uncleaned.files.size), words, device, pastOlderZips).forEach { line ->
                                WhatGoes(line.text, line.icon, line.goes, Modifier.padding(top = 4.dp))
                            }

                            uncleanedLines(uncleaned.named(format, chosen, soundsInPage, motionsInPage), chosen, words, device).forEach { line ->
                                WhatGoes(line.text, line.icon, line.goes, Modifier.padding(top = 4.dp))
                            }

                            // What the last try came to, under what the format is: why it didn't work, or that it was copied.
                            val said = failure ?: R.string.export_copied.takeIf { copied == format }

                            if (said != null) {
                                Text(
                                    stringResource(said),
                                    color = if (failure != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(top = 4.dp).semantics { liveRegion = LiveRegionMode.Polite },
                                )
                            }

                            // Its buttons, named, on a line of their own, so the words keep the row's width. While its file is
                            // made, they wait beside an indicator. Their first icon, or word, lines up with the words above.
                            BoxWithConstraints(Modifier.padding(top = 4.dp)) {
                                val icons = buttonsKeepIcons(maxWidth)

                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(ExportButtonsSpace),
                                    itemVerticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.offset(x = (-12).dp),
                                ) {
                                    if (format == Format.Pdf) {
                                        ExportButton(R.string.export_print_button, Icons.Outlined.Print.takeIf { icons }, stringResource(R.string.export_print), enabled = !busy, onClick = ::print)
                                    } else {
                                        if (format == Format.Markdown || format == Format.Yaml || format == Format.Opml) {
                                            ExportButton(R.string.export_copy, Icons.Outlined.ContentCopy.takeIf { icons }, stringResource(R.string.export_copy_format, named), enabled = !busy) { copy(format) }
                                        }

                                        ExportButton(R.string.export_save, Icons.Outlined.Save.takeIf { icons }, stringResource(R.string.export_save_format, named), enabled = !busy) { run(format, share = false) }
                                        ExportButton(R.string.export_share, Icons.Outlined.Share.takeIf { icons }, stringResource(R.string.export_share_format, named), enabled = !busy) { run(format, share = true) }
                                    }

                                    if (busy) {
                                        LoadingIndicator(Modifier.size(32.dp))
                                    }
                                }
                            }
                        }
                    },
                    leading = { Icon(format.icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                    // The icon stays by the way's name, above its words and buttons.
                    verticalAlignment = Alignment.Top,
                    modifier = Modifier.padding(horizontal = 8.dp),
                )
            }

            if (clean.pictures > 0 || clean.videos > 0 || clean.audio > 0) {
                WhatGoes(
                    // Once some files that couldn't go clean go as they are, this speaks of the others.
                    stringResource(if (chosen.isEmpty()) R.string.sharing_without_where else R.string.sharing_others_without_where),
                    Icons.Outlined.LocationOff,
                    goes = false,
                    Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

/** Between a way out's buttons. */
private val ExportButtonsSpace = 4.dp

/**
 * Whether a way out's buttons keep their icons: while the most any way out
 * has, Copy, Save and Share, fit on one line of `width` with them. Otherwise
 * every way out's go without, alike, and keep to one line, as they wouldn't
 * in Spanish on a narrow phone.
 */
@Composable
private fun buttonsKeepIcons(width: Dp): Boolean {
    val labels = listOf(R.string.export_copy, R.string.export_save, R.string.export_share).map { stringResource(it) }
    val style = MaterialTheme.typography.labelLarge
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current

    val needed = remember(labels, style, density) {
        val buttons = labels.map { label ->
            val word = with(density) { measurer.measure(label, style, maxLines = 1, softWrap = false).size.width.toDp() }
            // As Material pads a text button with an icon: 12 dp before the icon, 16 dp after the word.
            maxOf(ButtonDefaults.MinWidth, 12.dp + ButtonDefaults.IconSize + ButtonDefaults.IconSpacing + word + 16.dp)
        }

        buttons.fold(ExportButtonsSpace * (buttons.size - 1)) { total, button -> total + button }
    }

    return needed <= width
}

/** One of a way out's buttons: a word, and its icon when there's room, saying the whole of it to TalkBack, as "Share Web page". */
@Composable
private fun ExportButton(@StringRes text: Int, icon: ImageVector?, said: String, enabled: Boolean, onClick: () -> Unit) {
    TextButton(
        onClick = onClick,
        enabled = enabled,
        contentPadding = if (icon != null) ButtonDefaults.TextButtonWithIconContentPadding else ButtonDefaults.TextButtonContentPadding,
        modifier = Modifier.semantics { contentDescription = said },
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
            Spacer(Modifier.size(ButtonDefaults.IconSpacing))
        }

        Text(stringResource(text), modifier = Modifier.clearAndSetSemantics {})
    }
}

/** A line under a way out that says what goes with the map (`goes`), or what doesn't. */
private class Carried(val text: String, val goes: Boolean, val icon: ImageVector)

/** The files that can't go clean that a map in `format` would carry: all of them in a zip, the pictures, sounds and motions in a page, the pictures on paper or in a drawing. */
private fun Uncleaned.carried(format: Format): Uncleaned = when (format) {
    Format.Zip -> this
    Format.WebPage -> inPage
    Format.Pdf, Format.Svg -> inPictures
    Format.Png, Format.Markdown, Format.Yaml, Format.Opml -> Uncleaned()
}

/**
 * The files that can't go clean that a way out's row names ([uncleanedLines]):
 * those it would carry ([carried]), but a sound or a live photo's motion
 * that was chosen to go as it is (`chosen`) and is past a page's room. A page
 * carries `sounds` and `motions`, those that fit, so one past its room goes
 * no more than the map's other sounds and motions past it: it's counted with
 * what stays on this device rather than named as going.
 */
internal fun Uncleaned.named(format: Format, chosen: Set<String>, sounds: Set<String>, motions: Set<String>): Uncleaned {
    val carried = carried(format)

    if (format != Format.WebPage) {
        return carried
    }

    return carried.copy(
        sounds = carried.sounds.filterTo(HashSet()) { it !in chosen || it in sounds },
        motions = carried.motions.filterTo(HashSet()) { it !in chosen || it in motions },
    )
}

/**
 * The view a picture of the map shows for `framing`, beyond this view, which
 * the map's page draws as it is: the whole map, or the map as it opens, which
 * is the whole map when it opens on it (`opensIn`, its own `view`), or else
 * its center with the ideas around it.
 */
internal fun framedView(framing: Framing, opensIn: String?): String = when (framing) {
    Framing.Whole -> ViewMode.OVERVIEW
    Framing.Opening -> if (opensIn == ViewMode.OVERVIEW) ViewMode.OVERVIEW else ViewMode.FOCUS
    Framing.View -> ViewMode.FOCUS
}

/**
 * What a printed map's picture shows for `framing`: this view, `mode` on
 * the idea in focus, `focus`, or as [framedView] says, for the whole map or
 * the map as it opens (`opensIn`, its own `view`).
 */
internal fun printRequest(framing: Framing, mode: String, focus: String?, opensIn: String?): PrintRequest = when (framing) {
    Framing.View -> PrintRequest(view = mode, focus = focus)
    Framing.Whole, Framing.Opening -> PrintRequest(view = framedView(framing, opensIn))
}

/**
 * What goes with the map in `format`, beyond its description, when it has
 * pictures, videos or audio of its own: all of it in a zip; in a page, the
 * pictures, with the live photos' motions (`motionsInPage`) and the sounds
 * (`soundsInPage`) that fit; the pictures in a print; and only the text in
 * markdown, YAML and OPML. `media` counts what goes clean or as it is; the
 * map's other files (`uncleaned`) have lines of their own.
 */
/**
 * What a web page of the map carries of what goes with it: how many of its
 * sounds and motions go inside, and how many of its pictures stay out. Its
 * ideas' `files` all stay out, as they do of a print: the page and the
 * paper name them, without them.
 */
private class PageCarries(val sounds: Int, val motions: Int, val picturesOut: Int, val files: Int = 0)

private fun carries(format: Format, media: MediaSummary, uncleaned: Boolean, page: PageCarries, words: Words, device: ImageVector, pastOlderZips: Boolean): List<Carried> {
    if (media.isEmpty && !uncleaned) {
        return emptyList()
    }

    return when (format) {
        Format.Zip -> listOfNotNull(
            MediaWords.with(media, words)?.let { Carried(it, goes = true, Icons.Outlined.AttachFile) },
            Carried(words.string(R.string.sharing_zip_past_older_orbits), goes = false, Icons.Outlined.SystemUpdate).takeIf { pastOlderZips },
        )
        Format.WebPage -> {
            val picturesOut = page.picturesOut.coerceIn(0, media.pictures)
            // A live photo whose picture stays out takes its motion with it: the motions left are among the pictures that go.
            val motions = page.motions.coerceAtMost(media.motions)

            listOfNotNull(
                MediaWords.inside(media.copy(pictures = media.pictures - picturesOut, videos = 0, audio = page.sounds, motions = motions), words)?.let { Carried(it, goes = true, Icons.Outlined.AttachFile) },
                MediaWords.staysHere(media.copy(pictures = picturesOut, motions = 0, audio = media.audio - page.sounds, files = page.files), words, motions = (media.motions - motions).coerceAtLeast(0))?.let { Carried(it, goes = false, device) },
            )
        }
        Format.Pdf -> listOfNotNull(
            MediaWords.printed(media, words)?.let { Carried(it, goes = true, Icons.Outlined.AttachFile) },
            MediaWords.staysHere(MediaSummary(files = page.files), words)?.let { Carried(it, goes = false, device) },
        )
        Format.Markdown, Format.Yaml, Format.Opml -> listOf(Carried(words.string(R.string.sharing_just_the_text), goes = false, Icons.AutoMirrored.Outlined.Notes))
        Format.Png, Format.Svg -> emptyList()
    }
}

/**
 * Why a file in `format` couldn't be made. OPML, and YAML from a markdown map
 * or markdown from a YAML one (`yaml`), are the map written anew, which
 * Orbit doesn't do for a map it couldn't read all of, or that a newer
 * version made: it would lose what it didn't read or doesn't know. Its own
 * text can always be exported, and the words say so.
 */
@StringRes
private fun couldNotMake(format: Format, readOnly: ReadOnly?, yaml: Boolean): Int {
    val writtenAnew = format == Format.Opml || format == (if (yaml) Format.Markdown else Format.Yaml)

    return when {
        !writtenAnew -> R.string.export_could_not_make
        readOnly == ReadOnly.Newer -> if (yaml) R.string.export_newer_yaml_map_not_written else R.string.export_newer_map_not_written
        readOnly == ReadOnly.Unreadable -> if (yaml) R.string.export_unreadable_yaml_map_not_written else R.string.export_unreadable_map_not_written
        else -> R.string.export_could_not_make
    }
}

/** Copies `file` where the person chose to save it; false when it couldn't go there (a card that's full or gone, a place Orbit may not write to). */
private fun save(context: Context, uri: Uri, file: File): Boolean = try {
    context.contentResolver.openOutputStream(uri)?.use { output -> file.inputStream().use { it.copyTo(output) } } != null
} catch (failed: IOException) {
    Logs.warn("OrbitExport", "Couldn't save an export", failed)
    false
} catch (refused: SecurityException) {
    Logs.warn("OrbitExport", "Not allowed to save an export there", refused)
    false
}
