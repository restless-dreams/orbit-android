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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.NearMe
import androidx.compose.material.icons.outlined.OpenInFull
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material3.AssistChip
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import coil3.compose.AsyncImage
import com.hyuchia.orbit.R
import com.hyuchia.orbit.media.MediaKind
import com.hyuchia.orbit.media.posterOf
import com.hyuchia.orbit.page.MapNode
import com.hyuchia.orbit.page.Media
import com.hyuchia.orbit.ui.MarkdownNote
import com.hyuchia.orbit.ui.openLink

/** The colors status dots take, by tone. */
fun statusColor(status: String?): Color? = when (status?.lowercase()?.trim()) {
    "done", "complete", "completed", "shipped", "finished" -> Color(0xFF2E9E5B)
    "active", "doing", "in progress" -> Color(0xFF2F7CF6)
    "blocked", "stuck", "at risk" -> Color(0xFFE5484D)
    "todo", "to do", "planned", "next" -> Color(0xFF8A93A3)
    null, "" -> null
    else -> Color(0xFF8E4EC6)
}

@Composable
fun statusLabel(status: String): String =
    Statuses.firstOrNull { it.first.equals(status, ignoreCase = true) }?.let { stringResource(it.second) } ?: status.replaceFirstChar { it.uppercase() }

/** The idea's text with its icon, as the map shows it, and "Untitled" in the app's language when it has no text. */
val MapNode.label: String
    @Composable @ReadOnlyComposable get() = label(stringResource(R.string.common_untitled))

/** An idea's details, read natively: its goal, note, picture, video, page, link and tags. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DetailsPanel(model: MapViewModel, ui: MapUi, node: MapNode, sheet: Boolean) {
    val context = LocalContext.current
    val children = ui.map?.nodes.orEmpty().filter { it.parentId == node.id }
    val kind = NodeKinds.firstOrNull { it.type != null && it.type.equals(node.type, ignoreCase = true) }

    PanelFrame(
        title = node.label,
        sheet = sheet,
        onDismiss = { model.show(null) },
        partial = true,
        scrim = false,
        actions = {
            IconButton(onClick = { model.show(Panel.Editor(node.id)) }) { Icon(Icons.Outlined.Edit, contentDescription = stringResource(R.string.common_edit)) }
        },
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(18.dp),
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(top = 4.dp, bottom = 32.dp),
        ) {
            val tags = node.tags

            if (kind != null || node.status != null || node.priority != null || tags.isNotEmpty()) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    kind?.let { SuggestionChip(onClick = {}, label = { Text("${it.icon}  ${stringResource(it.label)}") }) }

                    node.status?.let { status ->
                        SuggestionChip(
                            onClick = {},
                            label = { Text(statusLabel(status)) },
                            icon = { Box(Modifier.size(10.dp).background(statusColor(status) ?: Color.Gray, CircleShape)) },
                        )
                    }

                    node.priority?.let { SuggestionChip(onClick = {}, label = { Text(stringResource(R.string.map_priority, it)) }) }
                    tags.forEach { tag -> SuggestionChip(onClick = {}, label = { Text("#$tag") }) }
                }
            }

            val goal = Goal.of(node)
            val following = ui.progress.followsItsIdeas(node)
            val under = ui.progress.under(node)

            if (goal != null && (goal.target != null || under.total == 0)) {
                GoalDetails(model, node, goal)
            } else if (following) {
                ProgressDetails(under)
            }

            node.image?.let { image -> Picture(model, image, onView = { model.view(node.id, MediaKind.Picture) }) }
            node.video?.let { video -> VideoCard(model, video, onPlay = { model.play(node.id) }) }

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
                MarkdownNote(note, plain = node.noteFormat == "plain", onNodeLink = { id ->
                    model.show(null)
                    model.focus(id, point = true)
                })
            } else if (node.image == null && node.video == null && node.embed == null && node.link == null && !node.isGoal && !following) {
                Text(stringResource(R.string.map_no_note_yet), color = MaterialTheme.colorScheme.onSurfaceVariant)
                FilledTonalButton(onClick = { model.show(Panel.Editor(node.id)) }) { Text(stringResource(R.string.map_write_one)) }
            }

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

/** The idea's picture; a tap shows it full screen. */
@Composable
private fun Picture(model: MapViewModel, image: Media, onView: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
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
                contentScale = ContentScale.Crop,
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
                    Icon(Icons.Outlined.OpenInFull, contentDescription = null, modifier = Modifier.size(18.dp))
                }
            }
        }

        image.caption?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
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
        val from = video.src.takeIf { model.mediaModel(it) !is java.io.File }?.let { it.toUri().host ?: it }
        (video.caption ?: from)?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis) }
    }
}

@Composable
private fun LinkCard(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, url: String, onClick: () -> Unit) {
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
