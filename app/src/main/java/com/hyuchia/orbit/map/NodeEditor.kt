package com.hyuchia.orbit.map

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AddPhotoAlternate
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.EmojiEmotions
import androidx.compose.material.icons.outlined.FormatBold
import androidx.compose.material.icons.outlined.FormatItalic
import androidx.compose.material.icons.automirrored.outlined.FormatListBulleted
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.LinkOff
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.InputChipDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.hyuchia.orbit.R
import com.hyuchia.orbit.page.MapNode
import com.hyuchia.orbit.page.Media
import com.hyuchia.orbit.theme.parseCssColor
import com.hyuchia.orbit.ui.ColorChoices
import com.hyuchia.orbit.ui.EscapeCloses
import com.hyuchia.orbit.ui.MarkdownNote
import com.hyuchia.orbit.ui.ToggleRow
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.text.NumberFormat

/** A kind of idea, drawn its own way by the map page's node types. */
data class NodeKind(val type: String?, @StringRes val label: Int, val icon: String)

val NodeKinds = listOf(
    NodeKind(null, R.string.map_kind_idea, "💡"),
    NodeKind("milestone", R.string.map_kind_milestone, "🏁"),
    NodeKind("question", R.string.map_kind_question, "❓"),
    NodeKind("decision", R.string.map_kind_decision, "⚖️"),
    NodeKind("risk", R.string.map_kind_risk, "⚠️"),
    NodeKind("source", R.string.map_kind_source, "📚"),
    NodeKind("person", R.string.map_kind_person, "👤"),
    NodeKind("progress", R.string.map_kind_progress, "📊"),
    NodeKind("goal", R.string.map_kind_goal, "🎯"),
)

/** Everything an idea can be, edited live: each change shows on the map behind as it's made. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun NodeEditor(model: MapViewModel, ui: MapUi, node: MapNode, sheet: Boolean, onPickIcon: (String) -> Unit) {
    val context = LocalContext.current
    var linking by remember { mutableStateOf(false) }

    val pickPicture = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            model.addPicture(node.id, uri, context.contentResolver)
        }
    }

    val pickVideo = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            model.addVideo(node.id, uri, context.contentResolver)
        }
    }

    fun set(key: String, value: JsonElement?) = model.update(node.id, key, value)
    val scroll = rememberScrollState()
    val scope = rememberCoroutineScope()

    // The sheet stops halfway and leaves the map clear, so each change shows on it as it's made.
    PanelFrame(title = stringResource(if (node.isRoot) R.string.map_editor_title_center else R.string.map_editor_title_idea), sheet = sheet, onDismiss = { model.show(null) }, partial = true, scrim = false) {
        Column(
            verticalArrangement = Arrangement.spacedBy(22.dp),
            modifier = Modifier
                .verticalScroll(scroll)
                .imePadding()
                .padding(horizontal = 24.dp)
                .padding(top = 8.dp, bottom = 32.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = parseCssColor(node.drawnColor) ?: MaterialTheme.colorScheme.secondaryContainer,
                    modifier = Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .clickable(onClickLabel = stringResource(R.string.map_choose_an_icon)) { onPickIcon(node.id) },
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        if (node.icon != null) {
                            Text(node.icon, style = MaterialTheme.typography.headlineMedium)
                        } else {
                            Icon(Icons.Outlined.EmojiEmotions, contentDescription = stringResource(R.string.map_add_an_icon), tint = MaterialTheme.colorScheme.onSecondaryContainer)
                        }
                    }
                }

                EditorField(
                    key = node.id,
                    value = node.text,
                    label = stringResource(R.string.map_text),
                    onCommit = { text ->
                        if (text.isNotBlank()) {
                            set("text", JsonPrimitive(text.replace('\n', ' ').trim()))
                        }
                    },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
            }

            // A goal's numbers are what it's about: they come first.
            if (node.isGoal) {
                Section(stringResource(R.string.map_section_goal)) {
                    GoalSection(model, node, ui.progress.under(node))
                }
            }

            Section(stringResource(R.string.common_color)) {
                ColorChoices(
                    current = node.color,
                    automatic = parseCssColor(node.drawnColor).takeIf { node.color == null },
                    onPick = { color -> set("color", color?.let(::JsonPrimitive)) },
                )
            }

            Section(stringResource(R.string.common_status)) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Choice(stringResource(R.string.map_status_none), node.status == null) { set("status", null) }

                    Statuses.forEach { (word, label, icon) ->
                        Choice(stringResource(label), node.status.equals(word, ignoreCase = true), icon = { Icon(icon, contentDescription = null, Modifier.size(18.dp)) }) {
                            set("status", JsonPrimitive(word))
                        }
                    }
                }
            }

            Section(stringResource(R.string.map_section_priority)) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Choice(stringResource(R.string.map_priority_none), node.priority == null) { set("priority", null) }

                    (1..5).forEach { level ->
                        Choice("$level", node.priority == level) { set("priority", JsonPrimitive(level)) }
                    }
                }
            }

            Section(stringResource(R.string.map_section_kind)) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    NodeKinds.forEach { kind ->
                        Choice("${kind.icon}  ${stringResource(kind.label)}", node.type.equals(kind.type, ignoreCase = true) || (kind.type == null && node.type == null)) {
                            val type = kind.type?.let(::JsonPrimitive)

                            if (node.isGoal && kind.type != GOAL_TYPE) {
                                // An idea that stops being a goal takes its numbers with it, in the same step to undo.
                                model.update(node.id, mapOf("type" to type, "attrs" to JsonObject(Goal.removed)))
                            } else {
                                set("type", type)
                            }

                            if (kind.type == GOAL_TYPE && !node.isGoal) {
                                // Its numbers come at the top.
                                scope.launch { scroll.animateScrollTo(0) }
                            }
                        }
                    }
                }
            }

            Section(stringResource(R.string.map_section_tags)) {
                TagEditor(node, allTags(ui)) { tags -> set("tags", if (tags.isEmpty()) null else JsonArray(tags.map(::JsonPrimitive))) }
            }

            Section(stringResource(R.string.map_section_note)) {
                NoteEditor(node) { note -> set("note", note.ifBlank { null }?.let(::JsonPrimitive)) }
            }

            Section(stringResource(R.string.map_section_link)) {
                val target = node.link?.takeIf { it.startsWith("#") }?.let { ui.nodes[it.removePrefix("#")] }

                if (target != null) {
                    InputChip(
                        selected = false,
                        onClick = { linking = true },
                        label = { Text(stringResource(R.string.map_goes_to, target.label), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        leadingIcon = { Icon(Icons.Outlined.Link, contentDescription = null, Modifier.size(18.dp)) },
                        trailingIcon = {
                            Icon(
                                Icons.Outlined.LinkOff,
                                contentDescription = stringResource(R.string.map_remove_the_link),
                                modifier = Modifier
                                    .size(InputChipDefaults.AvatarSize)
                                    .clickable { set("link", null) },
                            )
                        },
                    )
                } else {
                    EditorField(
                        key = node.id,
                        value = node.link.orEmpty(),
                        label = stringResource(R.string.map_web_address),
                        placeholder = "https://",
                        singleLine = true,
                        keyboardType = KeyboardType.Uri,
                        onCommit = { url -> set("link", url.trim().ifEmpty { null }?.let(::JsonPrimitive)) },
                    )

                    TextButton(onClick = { linking = true }) {
                        Icon(Icons.Outlined.Link, contentDescription = null, Modifier.size(18.dp))
                        Text("  ${stringResource(R.string.map_link_to_another_idea)}")
                    }
                }
            }

            Section(stringResource(R.string.map_section_picture)) {
                val image = node.image

                if (image != null) {
                    AsyncImage(
                        model = model.mediaModel(image.src),
                        contentDescription = image.alt,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(16f / 10f)
                            .clip(RoundedCornerShape(20.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                    )
                    EditorField(
                        key = node.id + image.src,
                        value = image.alt.orEmpty(),
                        label = stringResource(R.string.map_describe_the_picture),
                        onCommit = { alt -> set("image", media(image.copy(alt = alt.ifBlank { null }))) },
                    )
                    EditorField(
                        key = node.id + image.src,
                        value = image.caption.orEmpty(),
                        label = stringResource(R.string.map_caption),
                        onCommit = { caption -> set("image", media(image.copy(caption = caption.ifBlank { null }))) },
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { pickPicture.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }) {
                            Text(stringResource(R.string.map_replace))
                        }

                        TextButton(onClick = { set("image", null) }) { Text(stringResource(R.string.common_remove)) }
                    }

                    MediaProblemText(ui, node, video = false)
                } else {
                    FilledTonalButton(onClick = { pickPicture.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }) {
                        Icon(Icons.Outlined.AddPhotoAlternate, contentDescription = null, Modifier.size(18.dp))
                        Text("  ${stringResource(R.string.map_add_a_photo)}")
                    }

                    MediaProblemText(ui, node, video = false)
                    EditorField(
                        key = node.id,
                        value = "",
                        label = stringResource(R.string.map_or_a_picture_web_address),
                        placeholder = "https://",
                        singleLine = true,
                        keyboardType = KeyboardType.Uri,
                        onCommit = { url -> url.trim().takeIf { it.startsWith("http") }?.let { set("image", media(Media(it))) } },
                    )
                }
            }

            Section(stringResource(R.string.map_section_video)) {
                val video = node.video
                val fromPhone = video != null && model.mediaModel(video.src) is java.io.File
                val chooseVideo = { pickVideo.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)) }
                VideoPreview(model, video, ui.videoCopy?.takeIf { it.nodeId == node.id }, onPlay = { model.play(node.id) }, onCancel = model::cancelVideo)

                if (video == null) {
                    FilledTonalButton(onClick = chooseVideo) {
                        Icon(Icons.Outlined.VideoLibrary, contentDescription = null, Modifier.size(18.dp))
                        Text("  ${stringResource(R.string.map_choose_a_video)}")
                    }

                    MediaProblemText(ui, node, video = true)
                }

                // The same field before and after an address is first set, so typing one goes on uninterrupted.
                key("address") {
                    if (!fromPhone) {
                        EditorField(
                            key = node.id,
                            value = video?.src.orEmpty(),
                            label = stringResource(if (video == null) R.string.map_or_a_video_address else R.string.map_video_address),
                            placeholder = "https://youtu.be/…",
                            singleLine = true,
                            keyboardType = KeyboardType.Uri,
                            // A new address may be another kind of video: only the caption stays.
                            onCommit = { url -> set("video", url.trim().ifEmpty { null }?.let { media(Media(it, caption = video?.caption)) }) },
                        )
                    }
                }

                if (video != null) {
                    EditorField(
                        key = node.id + video.src,
                        value = video.caption.orEmpty(),
                        label = stringResource(R.string.map_caption),
                        onCommit = { caption -> set("video", media(video.copy(caption = caption.ifBlank { null }))) },
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = chooseVideo) { Text(stringResource(R.string.map_replace)) }
                        TextButton(onClick = { set("video", null) }) { Text(stringResource(R.string.common_remove)) }
                    }

                    MediaProblemText(ui, node, video = true)
                }
            }

            Section(stringResource(R.string.common_web_page)) {
                EditorField(
                    key = node.id,
                    value = node.embed?.src.orEmpty(),
                    label = stringResource(R.string.map_page_to_show),
                    placeholder = "https://",
                    singleLine = true,
                    keyboardType = KeyboardType.Uri,
                    onCommit = { url -> set("embed", url.trim().ifEmpty { null }?.let { media((node.embed ?: Media(it)).copy(src = it)) }) },
                )
            }

            if (node.childCount > 0 && !node.isRoot) {
                ToggleRow(
                    title = stringResource(R.string.map_fold_its_ideas_away),
                    description = stringResource(R.string.map_folded_description),
                    checked = node.collapsed,
                    onChange = { folded -> set("collapsed", if (folded) JsonPrimitive(true) else null) },
                )
            }

            if (!node.isRoot) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { model.duplicate(node.id) }) {
                        Icon(Icons.Outlined.ContentCopy, contentDescription = null, Modifier.size(18.dp))
                        Text("  ${stringResource(R.string.common_duplicate)}")
                    }

                    TextButton(onClick = { model.remove(node.id) }) {
                        Icon(Icons.Outlined.Delete, contentDescription = null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.error)
                        Text("  ${stringResource(R.string.common_delete)}", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }

    if (linking) {
        NodePicker(
            title = stringResource(R.string.map_link_to_an_idea),
            nodes = ui.map?.nodes.orEmpty().filter { it.id != node.id },
            onPick = { target ->
                linking = false
                set("link", JsonPrimitive("#${target.id}"))
            },
            onDismiss = { linking = false },
        )
    }
}

private fun media(value: Media): JsonElement = buildJsonObject {
    put("src", value.src)
    value.alt?.let { put("alt", it) }
    value.caption?.let { put("caption", it) }
    value.provider?.let { put("provider", it) }
    value.thumbnail?.let { put("thumbnail", it) }
}

private fun allTags(ui: MapUi): List<String> = ui.map?.nodes.orEmpty().flatMap { it.tags }.distinct().sorted()

/** Why the last picture or video chosen for this idea didn't join it. */
@Composable
private fun MediaProblemText(ui: MapUi, node: MapNode, video: Boolean) {
    val problem = ui.mediaProblem?.takeIf { it.nodeId == node.id && it.video == video } ?: return
    Text(
        problem.message.text(),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.error,
        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
    )
}

/**
 * The idea's video, to play, or how far along one from the phone is as it's
 * copied in, with a way to stop it. Nothing when there's neither.
 */
@Composable
private fun VideoPreview(model: MapViewModel, video: Media?, copying: VideoCopy?, onPlay: () -> Unit, onCancel: () -> Unit) {
    if (copying != null) {
        Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceContainerHighest, modifier = Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(start = 20.dp, end = 8.dp, top = 8.dp, bottom = 20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val locale = LocalConfiguration.current.locales[0]
                    val copied = copying.progress
                    Text(
                        if (copied != null) {
                            // Whole percents, rounded down, as the copy goes.
                            stringResource(R.string.map_copying_the_video_percent, NumberFormat.getPercentInstance(locale).format((copied * 100).toInt() / 100.0))
                        } else {
                            stringResource(R.string.map_copying_the_video)
                        },
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = onCancel) { Text(stringResource(R.string.common_cancel)) }
                }

                val progress = copying.progress

                if (progress != null) {
                    LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth().padding(end = 12.dp))
                } else {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(end = 12.dp))
                }
            }
        }
    } else if (video != null) {
        VideoCard(model, video, onPlay)
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
        content()
    }
}

@Composable
private fun Choice(label: String, selected: Boolean, icon: (@Composable () -> Unit)? = null, onClick: () -> Unit) {
    FilterChip(selected = selected, onClick = onClick, label = { Text(label) }, leadingIcon = icon)
}

/**
 * A text field that saves a moment after typing stops, when the keyboard's
 * action is pressed, and when it leaves the screen. `key` starts it over
 * with the node's value when the node changes.
 */
@Composable
fun EditorField(
    key: String,
    value: String,
    label: String,
    onCommit: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    singleLine: Boolean = false,
    minLines: Int = 1,
    keyboardType: KeyboardType = KeyboardType.Text,
    capitalization: KeyboardCapitalization = if (keyboardType == KeyboardType.Text) KeyboardCapitalization.Sentences else KeyboardCapitalization.None,
) {
    var text by remember(key) { mutableStateOf(value) }
    var committed by remember(key) { mutableStateOf(value) }
    val commit by rememberUpdatedState(onCommit)

    fun save() {
        if (text != committed) {
            committed = text
            commit(text)
        }
    }

    LaunchedEffect(key, text) {
        delay(700)
        save()
    }

    DisposableEffect(key) {
        onDispose { save() }
    }

    OutlinedTextField(
        value = text,
        onValueChange = { text = it },
        label = { Text(label) },
        placeholder = placeholder?.let { { Text(it) } },
        singleLine = singleLine,
        minLines = minLines,
        keyboardOptions = KeyboardOptions(
            capitalization = capitalization,
            keyboardType = keyboardType,
            imeAction = if (singleLine) ImeAction.Done else ImeAction.Default,
        ),
        keyboardActions = KeyboardActions(onDone = { save() }),
        modifier = modifier.fillMaxWidth(),
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TagEditor(node: MapNode, known: List<String>, onTags: (List<String>) -> Unit) {
    var draft by remember(node.id) { mutableStateOf("") }

    fun add(tag: String) {
        val clean = tag.trim().removePrefix("#").trim()

        if (clean.isNotEmpty() && clean !in node.tags) {
            onTags(node.tags + clean)
        }

        draft = ""
    }

    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        node.tags.forEach { tag ->
            InputChip(
                selected = true,
                onClick = { onTags(node.tags - tag) },
                label = { Text("#$tag") },
                trailingIcon = { Icon(Icons.Outlined.Delete, contentDescription = stringResource(R.string.map_remove_tag, tag), Modifier.size(InputChipDefaults.AvatarSize)) },
            )
        }

        known.filter { it !in node.tags }.take(8).forEach { tag ->
            FilterChip(selected = false, onClick = { add(tag) }, label = { Text("+ #$tag") })
        }
    }

    OutlinedTextField(
        value = draft,
        onValueChange = { text ->
            if (text.endsWith(",") || text.endsWith("\n")) {
                add(text.dropLast(1))
            } else {
                draft = text
            }
        },
        label = { Text(stringResource(R.string.map_add_a_tag)) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { add(draft) }),
        modifier = Modifier.fillMaxWidth(),
    )
}

/** The note, in markdown, with buttons for the marks people forget and a preview of how it reads. */
@Composable
private fun NoteEditor(node: MapNode, onNote: (String) -> Unit) {
    var previewing by remember(node.id) { mutableStateOf(false) }
    var field by remember(node.id) { mutableStateOf(TextFieldValue(node.note.orEmpty())) }
    var committed by remember(node.id) { mutableStateOf(node.note.orEmpty()) }
    val commit by rememberUpdatedState(onNote)

    fun save() {
        if (field.text != committed) {
            committed = field.text
            commit(field.text)
        }
    }

    LaunchedEffect(node.id, field.text) {
        delay(800)
        save()
    }

    DisposableEffect(node.id) {
        onDispose { save() }
    }

    fun wrap(mark: String) {
        val selection = field.selection
        val text = field.text
        val chosen = text.substring(selection.min, selection.max)
        val next = text.substring(0, selection.min) + mark + chosen + mark + text.substring(selection.max)
        field = TextFieldValue(next, TextRange(selection.min + mark.length, selection.max + mark.length))
    }

    fun bullet() {
        val text = field.text
        val lineStart = text.lastIndexOf('\n', (field.selection.min - 1).coerceAtLeast(0)).let { if (it < 0 || field.selection.min == 0) 0 else it + 1 }
        val next = text.substring(0, lineStart) + "- " + text.substring(lineStart)
        field = TextFieldValue(next, TextRange(field.selection.min + 2))
    }

    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = { wrap("**") }, enabled = !previewing) { Icon(Icons.Outlined.FormatBold, contentDescription = stringResource(R.string.map_bold)) }
        IconButton(onClick = { wrap("*") }, enabled = !previewing) { Icon(Icons.Outlined.FormatItalic, contentDescription = stringResource(R.string.map_italic)) }
        IconButton(onClick = ::bullet, enabled = !previewing) { Icon(Icons.AutoMirrored.Outlined.FormatListBulleted, contentDescription = stringResource(R.string.map_bulleted_list)) }
        Box(Modifier.weight(1f))
        FilterChip(
            selected = previewing,
            onClick = {
                save()
                previewing = !previewing
            },
            label = { Text(stringResource(if (previewing) R.string.common_edit else R.string.map_preview)) },
            leadingIcon = { Icon(if (previewing) Icons.Outlined.Edit else Icons.Outlined.Visibility, contentDescription = null, Modifier.size(18.dp)) },
        )
    }

    if (previewing) {
        Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceContainerHighest, modifier = Modifier.fillMaxWidth()) {
            Box(Modifier.padding(16.dp)) {
                if (field.text.isBlank()) {
                    Text(stringResource(R.string.map_nothing_written_yet), color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    MarkdownNote(field.text, plain = node.noteFormat == "plain", onNodeLink = {})
                }
            }
        }
    } else {
        OutlinedTextField(
            value = field,
            onValueChange = { field = it },
            placeholder = { Text(stringResource(R.string.map_note_placeholder)) },
            minLines = 4,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 140.dp),
        )
    }
}

/** A list of the map's ideas to choose one from. */
@Composable
fun NodePicker(title: String, nodes: List<MapNode>, onPick: (MapNode) -> Unit, onDismiss: () -> Unit) {
    var query by remember { mutableStateOf("") }
    val shown = nodes.filter { query.isBlank() || it.text.contains(query, ignoreCase = true) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            EscapeCloses(onDismiss)
            Text(title)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = query, onValueChange = { query = it }, label = { Text(stringResource(R.string.map_find_an_idea)) }, singleLine = true, modifier = Modifier.fillMaxWidth())

                LazyColumn(Modifier.heightIn(max = 360.dp)) {
                    items(shown, key = { it.id }) { node ->
                        ListItem(
                            onClick = { onPick(node) },
                            leadingContent = { NodeDot(node) },
                            modifier = Modifier.padding(start = (node.depth * 12).dp),
                        ) {
                            Text(node.label, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) } },
    )
}

/** The small colored disc an idea is drawn as, for lists. */
@Composable
fun NodeDot(node: MapNode, size: androidx.compose.ui.unit.Dp = 14.dp) {
    Box(
        Modifier
            .size(size)
            .background(parseCssColor(node.drawnColor) ?: MaterialTheme.colorScheme.primaryContainer, androidx.compose.foundation.shape.CircleShape),
    )
}
