package com.hyuchia.orbit.map

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Label
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.BrokenImage
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.NearMe
import androidx.compose.material.icons.outlined.OpenInFull
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material3.AssistChip
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import coil3.asDrawable
import coil3.compose.AsyncImage
import com.hyuchia.orbit.R
import com.hyuchia.orbit.media.FirstFrame
import com.hyuchia.orbit.media.GalleryItem
import com.hyuchia.orbit.media.LiveMark
import com.hyuchia.orbit.media.MediaKind
import com.hyuchia.orbit.media.SoundCard
import com.hyuchia.orbit.media.posterOf
import com.hyuchia.orbit.media.soundOf
import com.hyuchia.orbit.page.MapNode
import com.hyuchia.orbit.page.Media
import com.hyuchia.orbit.sketch.isSketchName
import com.hyuchia.orbit.ui.Hint
import com.hyuchia.orbit.ui.MarkdownNote
import com.hyuchia.orbit.ui.openLink
import com.hyuchia.orbit.ui.rememberTimeWords
import java.io.File

/** The color a status's dot takes, by its tone as the map colors it (see [statusTone]); none for no status. */
fun statusColor(status: String?): Color? = when (statusTone(status)) {
    StatusTone.Done -> Color(0xFF2E9E5B)
    StatusTone.Active -> Color(0xFF2F7CF6)
    StatusTone.Blocked -> Color(0xFFE5484D)
    StatusTone.ToDo -> Color(0xFF8A93A3)
    null -> if (status.isNullOrBlank()) null else Color(0xFF8E4EC6)
}

/** A status in words: one of the app's in its language ("Done"), or as written, without the spaces around it. */
@Composable
fun statusLabel(status: String): String {
    val word = status.trim()
    return Statuses.firstOrNull { it.first.equals(word, ignoreCase = true) }?.let { stringResource(it.second) } ?: word.replaceFirstChar { it.uppercase() }
}

/** The idea's text with its icon, as the map shows it, and "Untitled" in the app's language when it has no text. */
val MapNode.label: String
    @Composable @ReadOnlyComposable get() = label(stringResource(R.string.common_untitled))

/**
 * An idea's details, read natively: its kind, status, priority, date and
 * tags, its goal, note, picture, video, page, link and files. Its note's checklists
 * check here. On a map that can only be looked at (`ui.readOnly`), nothing
 * here offers to change it.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DetailsPanel(model: MapViewModel, ui: MapUi, node: MapNode, sheet: Boolean) {
    val context = LocalContext.current
    val children = ui.map?.nodes.orEmpty().filter { it.parentId == node.id }
    val changeable = ui.readOnly == null
    val opening = rememberFileOpening(model)

    PanelFrame(
        title = node.label,
        sheet = sheet,
        onDismiss = { model.show(null) },
        partial = true,
        scrim = false,
        actions = {
            if (changeable) {
                val edit = stringResource(R.string.common_edit)

                Hint(edit) {
                    IconButton(onClick = { model.show(Panel.Editor(node.id)) }) { Icon(Icons.Outlined.Edit, contentDescription = edit) }
                }
            }
        },
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(18.dp),
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(top = 4.dp, bottom = 32.dp),
        ) {
            IdeaFacts(node)

            val goal = Goal.of(node)
            val following = ui.progress.followsItsIdeas(node)
            val under = ui.progress.under(node)

            if (goal != null && (goal.target != null || under.total == 0)) {
                GoalDetails(model, node, goal, changeable)
            } else if (following) {
                ProgressDetails(under)
            }

            node.image?.let { image -> Picture(model, image, onView = { model.view(node.id, MediaKind.Picture) }) }
            node.video?.let { video -> VideoCard(model, video, onPlay = { model.play(node.id) }) }
            soundOf(node, stringResource(R.string.common_untitled))?.let { item -> DetailsSound(model, item) }

            node.embed?.let { embed ->
                LinkCard(Icons.Outlined.Language, embed.caption ?: stringResource(R.string.common_web_page), embed.src) { openLink(context, embed.src) }
            }

            node.link?.let { link ->
                if (link.startsWith("#")) {
                    val target = ui.nodes[link.removePrefix("#")]

                    FilledTonalButton(onClick = {
                        model.show(null)
                        target?.let { model.focus(it.id, point = true) }
                    }) {
                        Icon(Icons.Outlined.NearMe, contentDescription = null, Modifier.size(18.dp))
                        Text("  ${stringResource(R.string.map_go_to, target?.label ?: link)}", maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                } else {
                    LinkCard(Icons.AutoMirrored.Outlined.OpenInNew, stringResource(R.string.map_open_link), link) { openLink(context, link) }
                }
            }

            val note = node.note

            if (!note.isNullOrBlank()) {
                // Its checklists check here, on a map that takes changes: that line of its file changes, with Undo.
                val checkable = changeable && !ui.showingVersion && !ui.pathMaking

                MarkdownNote(
                    note,
                    plain = node.noteFormat == "plain",
                    onNodeLink = { id ->
                        model.show(null)
                        model.focus(id, point = true)
                    },
                    checks = ui.noteChecks[node.id].orEmpty(),
                    onCheck = if (checkable) { check -> model.checkNote(node.id, check) } else null,
                )
            } else if (node.image == null && node.video == null && node.audio == null && node.embed == null && node.link == null && node.files.isEmpty() && !node.isGoal && !following) {
                Text(stringResource(R.string.map_no_note_yet), color = MaterialTheme.colorScheme.onSurfaceVariant)

                if (changeable) {
                    FilledTonalButton(onClick = { model.show(Panel.Editor(node.id)) }) { Text(stringResource(R.string.map_write_one)) }
                }
            }

            // Its files, after its note, as attachments come after a message: each opens in another app, asking first.
            DetailsFiles(node, opening)

            if (children.isNotEmpty()) {
                Text(stringResource(R.string.map_in_its_orbit), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)

                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    children.forEach { child ->
                        AssistChip(
                            onClick = {
                                model.show(null)
                                model.focus(child.id)
                            },
                            label = { Text(child.label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            leadingIcon = { NodeDot(child, 12.dp) },
                        )
                    }
                }
            }
        }
    }
}

/**
 * What the idea is, at the top of its details: its kind, status, priority,
 * date and tags, each a few words after its emoji, dot or icon, as Home's
 * cards show their tags. They're read, not tapped: the details' Edit changes them.
 * So they aren't chips, which act, as the ideas in its orbit below do, and
 * screen readers hear each as words ("Status: Done"), not as a button.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun IdeaFacts(node: MapNode) {
    // An idea of no kind is an idea like any other: it says nothing of it.
    val kind = NodeKinds.firstOrNull { it.type != null && it.type.equals(node.type, ignoreCase = true) }
    val status = node.shownStatus
    val date = shownDate(node, rememberTimeWords())
    val tags = node.tags

    if (kind == null && status == null && node.priority == null && date == null && tags.isEmpty()) {
        return
    }

    FlowRow(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp), itemVerticalAlignment = Alignment.CenterVertically) {
        kind?.let {
            val name = stringResource(it.label)
            // The emoji shows, and screen readers say the name alone ("Decision", not "balance scale, Decision").
            Fact(name, said = name) { Text(it.icon, style = MaterialTheme.typography.titleSmall) }
        }

        status?.let {
            val label = statusLabel(it)

            Fact(label, said = stringResource(R.string.map_status_said, label)) {
                Box(Modifier.size(10.dp).background(statusColor(it) ?: MaterialTheme.colorScheme.outline, CircleShape))
            }
        }

        node.priority?.let {
            val priority = stringResource(R.string.map_priority, it)
            Fact(priority, said = priority) { Icon(Icons.Outlined.Flag, contentDescription = null, modifier = Modifier.size(18.dp)) }
        }

        // Its date, past and not done in the error color, saying so in words too.
        date?.let {
            val said = stringResource(if (it.overdue) R.string.map_date_overdue_label else R.string.map_date_label, it.text)
            val color = if (it.overdue) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant

            Fact(if (it.overdue) said else it.text, said = said, color = color) { Icon(Icons.Outlined.Event, contentDescription = null, modifier = Modifier.size(18.dp)) }
        }

        if (tags.isNotEmpty()) {
            Fact(tags.joinToString(" · "), said = stringResource(R.string.ui_tags_list, tags.joinToString(", "))) {
                Icon(Icons.AutoMirrored.Outlined.Label, contentDescription = null, modifier = Modifier.size(18.dp))
            }
        }
    }
}

/** One of an idea's facts: `text` after its `mark`, in `color`, which screen readers hear as `said`. */
@Composable
private fun Fact(text: String, said: String, color: Color = MaterialTheme.colorScheme.onSurfaceVariant, mark: @Composable () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .heightIn(min = 32.dp)
            .clearAndSetSemantics { contentDescription = said },
    ) {
        CompositionLocalProvider(LocalContentColor provides color) {
            mark()
            Text(text, style = MaterialTheme.typography.labelLarge)
        }
    }
}

/** The idea's sound, to play here and on as the details close; its art opens it full screen. */
@Composable
private fun DetailsSound(model: MapViewModel, item: GalleryItem) {
    val sound by model.sound.collectAsState()
    val length by produceState<Long?>(null, item.media.src) { value = model.soundLength(item.media.src) }
    // A sound on the web says where it's from.
    val from = item.media.src.takeIf { model.mediaModel(it) !is File }?.let { it.toUri().host ?: it }
    SoundCard(item, sound, model.sounds, model::mediaModel, length, subtitle = from)
}

/**
 * The idea's picture; a tap shows it full screen. An animated GIF or WebP
 * plays here too, and while Android's animations are off, its first frame
 * shows with a play button, which plays it full screen. A live photo shows
 * its mark, and plays full screen.
 */
@Composable
private fun Picture(model: MapViewModel, image: Media, onView: () -> Unit) {
    val resources = LocalResources.current
    var still by remember(image.src) { mutableStateOf(false) }
    var failed by remember(image.src) { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        // A picture that can't load (a dead address, or one the app won't fetch) says so in a line, rather than leaving
        // a blank as big as a picture above the note.
        if (failed) {
            PictureProblem()
            image.caption?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            return@Column
        }

        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(4f / 3f)
                .clip(RoundedCornerShape(20.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                .clickable(onClickLabel = stringResource(R.string.map_view_it_full_screen), onClick = onView),
        ) {
            AsyncImage(
                model = model.mediaModel(image.src),
                contentDescription = image.alt,
                // A sketch is round, as its planet: all of it shows.
                contentScale = if (isSketchName(image.src)) ContentScale.Fit else ContentScale.Crop,
                onSuccess = { loaded -> still = loaded.result.image.asDrawable(resources) is FirstFrame },
                onError = { failed = true },
                modifier = Modifier.fillMaxSize(),
            )

            Surface(
                shape = CircleShape,
                color = Color.Black.copy(alpha = 0.5f),
                contentColor = Color.White,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(12.dp)
                    .size(36.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(if (still) Icons.Outlined.PlayArrow else Icons.Outlined.OpenInFull, contentDescription = null, modifier = Modifier.size(18.dp))
                }
            }

            if (image.motion != null) {
                LiveMark(onPlay = null, modifier = Modifier.align(Alignment.TopStart).padding(12.dp))
            }
        }

        image.caption?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}

/** In place of a picture that can't load: a line that says so, as small as a line of the note. */
@Composable
internal fun PictureProblem() {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(Icons.Outlined.BrokenImage, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
        Text(stringResource(R.string.media_picture_unreadable), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** A video's poster with a play button; a tap plays it full screen. */
@Composable
internal fun VideoCard(model: MapViewModel, video: Media, onPlay: () -> Unit) {
    val thumbnail = posterOf(video)?.let(model::mediaModel)

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(20.dp))
                .background(Color(0xFF101418))
                .clickable(onClickLabel = stringResource(R.string.common_play_the_video), onClick = onPlay),
        ) {
            if (thumbnail != null) {
                AsyncImage(model = thumbnail, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f))
            }

            Surface(shape = CircleShape, color = Color.Black.copy(alpha = 0.55f), modifier = Modifier.size(64.dp)) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Outlined.PlayArrow, contentDescription = stringResource(R.string.map_play), tint = Color.White, modifier = Modifier.size(36.dp))
                }
            }
        }

        // A video in the map's folder goes by its caption alone; a link says where it's from.
        val from = video.src.takeIf { model.mediaModel(it) !is File }?.let { it.toUri().host ?: it }
        (video.caption ?: from)?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis) }
    }
}

@Composable
private fun LinkCard(icon: ImageVector, title: String, url: String, onClick: () -> Unit) {
    OutlinedCard(onClick = onClick, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(16.dp)) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)

            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                Text(url.toUri().host ?: url, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}
