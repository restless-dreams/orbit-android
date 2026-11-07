package com.hyuchia.orbit.map

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.AttachFile
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.hyuchia.orbit.Logs
import com.hyuchia.orbit.R
import com.hyuchia.orbit.container
import com.hyuchia.orbit.data.OutgoingFile
import com.hyuchia.orbit.data.OutgoingMedia
import com.hyuchia.orbit.page.MapFile
import com.hyuchia.orbit.page.MapNode
import com.hyuchia.orbit.privacy.WhenPrivateMapsLock
import com.hyuchia.orbit.ui.EscapeCloses
import com.hyuchia.orbit.ui.fileFacts
import com.hyuchia.orbit.ui.fileKind
import com.hyuchia.orbit.ui.fileKindName
import com.hyuchia.orbit.ui.fileName
import com.hyuchia.orbit.ui.fileOpenType
import com.hyuchia.orbit.ui.forgetExport
import com.hyuchia.orbit.ui.makeExport
import com.hyuchia.orbit.ui.openFile
import com.hyuchia.orbit.ui.rememberDefaultButton
import com.hyuchia.orbit.ui.sharedFileUri
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

/**
 * An idea's files in its details, under their heading: a row each, its
 * kind's icon in a tinted square, its name, and its kind and size under it,
 * or that it isn't on this device. A tap opens it in another app, asking
 * first ([FileOpening]).
 */
@Composable
internal fun DetailsFiles(node: MapNode, opening: FileOpening) {
    if (node.files.isEmpty()) {
        return
    }

    val missing by produceState(emptySet<String>(), node.files) { value = opening.missing(node.files) }

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        FilesHeading()

        node.files.forEach { file ->
            val here = file.src !in missing

            FileRow(file, here, onOpen = { opening.open(file) }) {
                if (here) {
                    Icon(Icons.AutoMirrored.Outlined.OpenInNew, contentDescription = null, modifier = Modifier.size(20.dp))
                }
            }
        }
    }
}

/**
 * An idea's files in its editor: the same rows, each with a button that
 * takes it off, with Undo; the copy on its way in, which can be stopped;
 * why one didn't join; and Add files, which picks any files from the
 * device, several at once.
 */
@Composable
internal fun EditorFiles(model: MapViewModel, ui: MapUi, node: MapNode, opening: FileOpening, heading: Boolean = true, onAdd: () -> Unit) {
    val copy = ui.fileCopy?.takeIf { it.nodeId == node.id }
    val problem = ui.mediaProblem?.takeIf { it.nodeId == node.id && it.file }
    val missing by produceState(emptySet<String>(), node.files) { value = opening.missing(node.files) }

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (heading) {
            FilesHeading()
        }

        node.files.forEach { file ->
            val name = fileName(file)

            FileRow(file, here = file.src !in missing, onOpen = { opening.open(file) }) {
                IconButton(onClick = { model.removeFile(node.id, file) }) {
                    Icon(Icons.Outlined.Close, contentDescription = stringResource(R.string.files_remove, name))
                }
            }
        }

        if (copy != null) {
            FileCopying(copy, onStop = model::cancelFiles)
        }

        problem?.let {
            Text(
                it.message.text(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
        }

        FilledTonalButton(onClick = onAdd, modifier = Modifier.padding(top = 4.dp)) {
            Icon(Icons.Outlined.AttachFile, contentDescription = null, modifier = Modifier.size(18.dp))
            Text("  ${stringResource(R.string.files_add)}")
        }
    }
}

@Composable
private fun FilesHeading() {
    Text(stringResource(R.string.files_title), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
}

/**
 * One of an idea's files: its kind's icon in a tinted square, its name and
 * its kind and size, then `trailing`. One that isn't `here` is quieter, and
 * says so. A tap opens it; TalkBack hears its name, kind and size, and what
 * a tap does.
 */
@Composable
private fun FileRow(file: MapFile, here: Boolean, onOpen: () -> Unit, trailing: @Composable () -> Unit) {
    val context = LocalContext.current
    val name = fileName(file)
    val notHere = stringResource(R.string.files_not_here)
    val facts = fileFacts(file, context).let { if (here) it else "$it · $notHere" }
    val open = stringResource(R.string.files_open_in_another_app)
    val square = if (here) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest
    val glyph = if (here) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClickLabel = open, role = Role.Button, onClick = onOpen)
            .semantics { contentDescription = "$name, ${facts.replace(" · ", ", ")}" }
            .padding(horizontal = 4.dp, vertical = 6.dp),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(square),
        ) {
            Icon(fileKind(file).icon, contentDescription = null, tint = glyph, modifier = Modifier.size(24.dp))
        }

        Column(Modifier.weight(1f)) {
            Text(name, style = MaterialTheme.typography.bodyLarge, color = if (here) Color.Unspecified else MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.MiddleEllipsis)
            Text(facts, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        }

        Box(Modifier.padding(end = 4.dp)) { trailing() }
    }
}

/** A file on its way in, as a sound's copy shows: its name, how far along it is, how many follow it, and a way to stop them. */
@Composable
private fun FileCopying(copy: FileCopy, onStop: () -> Unit) {
    Surface(shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.surfaceContainerHighest, modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(start = 20.dp, end = 8.dp, top = 8.dp, bottom = 20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.files_copying, copy.name), style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.MiddleEllipsis)

                    if (copy.left > 0) {
                        Text(pluralStringResource(R.plurals.files_left, copy.left, copy.left), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                TextButton(onClick = onStop) { Text(stringResource(R.string.common_cancel)) }
            }

            val progress = copy.progress

            if (progress != null) {
                LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth().padding(end = 12.dp))
            } else {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(end = 12.dp))
            }
        }
    }
}

/**
 * Opens an idea's files in other apps, as the privacy rule says: a file
 * Orbit can clean (a picture, a video or a sound held as a file) goes as a
 * clean copy without a question; any other, a PDF or a spreadsheet, goes
 * only once the person chose it, knowing Orbit can't take out what it may
 * say beyond what it shows. Either way, the other app gets a copy in the
 * exports, never the map's own file, and a copy of a private map's file is
 * deleted as private maps lock ([com.hyuchia.orbit.privacy.PrivateCopies]).
 * A file that isn't here, or that no app on the device opens, is said.
 */
@Stable
class FileOpening internal constructor(private val context: Context, private val scope: CoroutineScope, private val model: MapViewModel) {
    /** The file waiting for the person to say whether it goes as it is. */
    var asking by mutableStateOf<MapFile?>(null)
        private set

    /** The addresses of `files` that aren't here to open: a folder's older device, or one sending from it, may not have kept them. */
    suspend fun missing(files: List<MapFile>): Set<String> = withContext(Dispatchers.IO) { files.filter { model.ideaFile(it.src)?.isFile != true }.mapTo(HashSet()) { it.src } }

    fun open(file: MapFile) {
        val local = model.ideaFile(file.src)?.takeIf { it.isFile }

        if (local == null) {
            model.say(ShownText.Resource(R.string.files_missing))
            return
        }

        scope.launch {
            val clean = withContext(Dispatchers.IO) { OutgoingMedia.of(local) }

            if (clean != null) {
                openCopy(file, local, clean)
            } else {
                asking = file
            }
        }
    }

    internal fun answer(open: Boolean) {
        val file = asking ?: return
        asking = null

        if (open) {
            val local = model.ideaFile(file.src)?.takeIf { it.isFile } ?: return model.say(ShownText.Resource(R.string.files_missing))
            scope.launch { openCopy(file, local, null) }
        }
    }

    /** Hands another app a copy of `local`, clean when `clean` says how, else as it is, under the name people know it by. */
    private suspend fun openCopy(file: MapFile, local: File, clean: OutgoingFile?) {
        val opened = try {
            val copy = makeExport(context, copyName(file, local)) { output ->
                // Not begun on a storage without room for all of it, and some to spare.
                invoke(clean?.size ?: local.length())

                if (clean != null) {
                    clean.writeTo(output)
                } else {
                    local.inputStream().use { it.copyTo(output) }
                }

                true
            } ?: return

            // A private map that locked while its copy was made opens nothing.
            if (context.container.privateMaps.hidesNow(model.id)) {
                forgetExport(copy)
                return
            }

            if (model.isPrivate()) {
                context.container.privateCopies.keep(copy)
            }

            sharedFileUri(context, copy)?.let { openFile(context, it, fileOpenType(file)) } ?: false
        } catch (failed: IOException) {
            Logs.warn("OrbitFiles", "Couldn't open an idea's file in another app", failed)
            model.say(ShownText.Resource(R.string.files_open_failed))
            return
        }

        if (!opened) {
            model.say(ShownText.Resource(R.string.files_no_app, listOf(fileKindName(file, context))))
        }
    }
}

/**
 * What a copy of an idea's file is called for the app it opens in: the name
 * people know it by, without characters file systems refuse, short enough
 * for any of them, else the name it's kept under.
 */
internal fun copyName(file: MapFile, local: File): String {
    val name = fileName(file).replace(Regex("""[\\/:*?"<>|\u0000-\u001f]+"""), " ").replace(Regex("\\s+"), " ").trimStart('.', ' ').trim()
    val extension = name.substringAfterLast('.', "").takeIf { it.length in 1..10 && it != name }?.let { ".$it" }.orEmpty()
    val stem = name.removeSuffix(extension).take(100).trimEnd()
    return if (stem.isEmpty()) local.name else "$stem$extension"
}

/** A [FileOpening] for the map `model` shows, whose question shows here. */
@Composable
internal fun rememberFileOpening(model: MapViewModel): FileOpening {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val opening = remember(model) { FileOpening(context, scope, model) }

    opening.asking?.let { file -> OpenFileDialog(file, private = model.isPrivate(), onChoice = opening::answer) }
    // A question left unanswered as private maps lock goes with them: nothing of the map shows over its lock.
    WhenPrivateMapsLock { opening.answer(false) }
    return opening
}

/**
 * Asks before a file Orbit can't clean leaves for another app: it names the
 * file, and says what may go with it. Enter keeps it here: it goes only
 * when someone chooses Open.
 */
@Composable
private fun OpenFileDialog(file: MapFile, private: Boolean, onChoice: (open: Boolean) -> Unit) {
    AlertDialog(
        onDismissRequest = { onChoice(false) },
        icon = { Icon(fileKind(file).icon, contentDescription = null) },
        title = {
            EscapeCloses { onChoice(false) }
            Text(stringResource(R.string.files_open_title, fileName(file)))
        },
        text = { Text(stringResource(if (private) R.string.files_open_text_private else R.string.files_open_text)) },
        confirmButton = { TextButton(onClick = { onChoice(true) }) { Text(stringResource(R.string.files_open)) } },
        dismissButton = { TextButton(onClick = { onChoice(false) }, modifier = Modifier.focusRequester(rememberDefaultButton())) { Text(stringResource(R.string.common_cancel)) } },
    )
}
