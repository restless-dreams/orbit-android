package com.hyuchia.orbit.map

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hyuchia.orbit.R
import com.hyuchia.orbit.page.Diagnostic

/** The top bar while the map is edited as text: markdown or YAML, and done. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SourceTopBar(model: MapViewModel, source: SourceState, background: Color) {
    TopAppBar(
        title = { Text(stringResource(R.string.map_edit_as_text)) },
        navigationIcon = {
            IconButton(onClick = model::closeSource) { Icon(Icons.Outlined.Check, contentDescription = stringResource(R.string.common_done)) }
        },
        actions = {
            SingleChoiceSegmentedButtonRow(Modifier.padding(end = 8.dp)) {
                SegmentedButton(
                    selected = !source.yaml,
                    onClick = { model.setSourceYaml(false) },
                    shape = SegmentedButtonDefaults.itemShape(0, 2),
                    enabled = !source.converting,
                ) { Text("Markdown") }
                SegmentedButton(
                    selected = source.yaml,
                    onClick = { model.setSourceYaml(true) },
                    shape = SegmentedButtonDefaults.itemShape(1, 2),
                    enabled = !source.converting,
                ) { Text("YAML") }
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = background),
    )
}

/**
 * The map as text, colored by what Orbit reads in it: headings, items,
 * checkboxes, hashtags, attributes and frontmatter. The map above redraws a
 * moment after typing stops, and what Orbit noticed shows below. On a map
 * that's only shown (`readOnly`, see [canEditText]) the text can only be
 * read, and a line over it says why.
 */
@Composable
fun SourceEditor(model: MapViewModel, source: SourceState, readOnly: ReadOnly?, modifier: Modifier = Modifier, navigationBar: Dp = 0.dp) {
    val colors = MaterialTheme.colorScheme
    val editable = canEditText(readOnly)
    var field by remember { mutableStateOf(TextFieldValue(source.text)) }

    if (field.text != source.text) {
        // The text changed from outside the field: an edit on the map, or a switch to YAML.
        field = TextFieldValue(source.text, TextRange(field.selection.start.coerceAtMost(source.text.length)))
    }

    val highlight = remember(colors, source.yaml) { SourceHighlighting(colors.primary, colors.tertiary, colors.secondary, colors.onSurfaceVariant, source.yaml) }
    val scroll = rememberScrollState()

    Column(modifier.background(colors.surfaceContainerLow)) {
        if (!editable && readOnly != null) {
            ReadOnlyLine(readOnly)
        }

        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(scroll)
                .padding(16.dp),
        ) {
            BasicTextField(
                value = field,
                onValueChange = { value ->
                    field = value

                    if (value.text != source.text) {
                        model.editSource(value.text)
                    }
                },
                readOnly = !editable,
                textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace, color = colors.onSurface, fontSize = 14.sp, lineHeight = 21.sp),
                cursorBrush = SolidColor(colors.primary),
                visualTransformation = highlight,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 200.dp),
            )
        }

        Diagnostics(source.diagnostics) { line ->
            val offset = field.text.lineSequence().take(line - 1).sumOf { it.length + 1 }.coerceAtMost(field.text.length)
            field = field.copy(selection = TextRange(offset))
        }

        // The screen's insets are used up by the map's screen, which knows the navigation bar's height:
        // the editor ends above the bar, or above the keyboard, which the bar's height is already taken from.
        Box(Modifier.background(MaterialTheme.colorScheme.surfaceContainerHigh).padding(bottom = navigationBar).imePadding())
    }
}

/** Why the text can only be read, in the words of the note over a map that's only shown. */
@Composable
private fun ReadOnlyLine(readOnly: ReadOnly) {
    val (title, body) = when (readOnly) {
        ReadOnly.Newer -> R.string.map_read_only_newer_title to R.string.map_read_only_newer_body
        ReadOnly.Deleted -> R.string.map_read_only_deleted_title to R.string.map_read_only_deleted_body
        ReadOnly.Storage -> R.string.map_read_only_storage_title to R.string.map_read_only_storage_body
        ReadOnly.Unreadable -> return
    }

    Surface(color = MaterialTheme.colorScheme.tertiaryContainer, contentColor = MaterialTheme.colorScheme.onTertiaryContainer) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
            Icon(Icons.Outlined.Lock, contentDescription = null, modifier = Modifier.padding(top = 2.dp).size(18.dp))

            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(stringResource(title), style = MaterialTheme.typography.labelLarge)
                Text(stringResource(body), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

/** What Orbit noticed in the text: problems it worked around, each with its line. */
@Composable
private fun Diagnostics(diagnostics: List<Diagnostic>, onLine: (Int) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val errors = diagnostics.count { it.isError }
    val colors = MaterialTheme.colorScheme

    Surface(color = colors.surfaceContainerHigh) {
        Column {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = diagnostics.isNotEmpty()) { open = !open }
                    .padding(horizontal = 16.dp, vertical = 10.dp),
            ) {
                val (icon, tint, text) = when {
                    errors > 0 -> Triple(Icons.Outlined.ErrorOutline, colors.error, pluralStringResource(R.plurals.map_problems, errors, errors))
                    diagnostics.isNotEmpty() -> Triple(Icons.Outlined.WarningAmber, colors.tertiary, pluralStringResource(R.plurals.map_notes_from_orbit, diagnostics.size, diagnostics.size))
                    else -> Triple(Icons.Outlined.TaskAlt, colors.primary, stringResource(R.string.map_orbit_reads_it_all))
                }

                Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
                Text(text, style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))

                if (diagnostics.isNotEmpty()) {
                    Text(stringResource(if (open) R.string.map_hide else R.string.map_show), style = MaterialTheme.typography.labelLarge, color = colors.primary)
                }
            }

            AnimatedVisibility(open && diagnostics.isNotEmpty()) {
                Column(Modifier.heightIn(max = 180.dp).verticalScroll(rememberScrollState())) {
                    diagnostics.forEach { diagnostic ->
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(enabled = diagnostic.line != null) { diagnostic.line?.let(onLine) }
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                        ) {
                            Text(
                                diagnostic.line?.let { stringResource(R.string.map_line, it) } ?: "",
                                style = MaterialTheme.typography.labelMedium,
                                color = colors.onSurfaceVariant,
                                modifier = Modifier.padding(top = 2.dp),
                            )
                            Text(diagnostic.message, style = MaterialTheme.typography.bodySmall, maxLines = 3, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }
    }
}

private val HEADING = Regex("""^( {0,3}#{1,6})(\s.*)?$""")
private val ITEM = Regex("""^(\s*)([-*+]|\d+[.)])(\s+)(\[[ xX/]])?""")
private val COMMENT = Regex("""<!--.*?(-->|$)""")
private val HASHTAG = Regex("""(?<=\s)#[\p{L}\p{N}_][\p{L}\p{N}_/-]*""")
private val LINK = Regex("""\[[^\]]*]\([^)]*\)""")
private val YAML_KEY = Regex("""^(\s*-?\s*)([A-Za-z][\w-]*):""")

/** Colors the text the way Orbit reads it; the text itself never changes. */
private class SourceHighlighting(
    private val structure: Color,
    private val marks: Color,
    private val attributes: Color,
    private val muted: Color,
    private val yaml: Boolean,
) : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText = TransformedText(highlight(text.text), OffsetMapping.Identity)

    private fun highlight(text: String): AnnotatedString = buildAnnotatedString {
        append(text)
        var offset = 0
        var inFrontmatter = false
        var inComment = false

        text.split('\n').forEachIndexed { index, line ->
            val end = offset + line.length

            if (index == 0 && line.trim() == "---") {
                inFrontmatter = true
            }

            when {
                yaml -> YAML_KEY.find(line)?.let { match ->
                    val key = match.groups[2]!!.range
                    addStyle(SpanStyle(color = structure, fontWeight = FontWeight.SemiBold), offset + key.first, offset + key.last + 1)
                }
                inFrontmatter -> {
                    addStyle(SpanStyle(color = muted), offset, end)

                    if (index > 0 && line.trim() == "---") {
                        inFrontmatter = false
                    }
                }
                inComment -> {
                    addStyle(SpanStyle(color = attributes, fontStyle = FontStyle.Italic), offset, end)

                    if (line.contains("-->")) {
                        inComment = false
                    }
                }
                else -> {
                    HEADING.find(line)?.let { match ->
                        addStyle(SpanStyle(fontWeight = FontWeight.Bold), offset, end)
                        val hashes = match.groups[1]!!.range
                        addStyle(SpanStyle(color = structure), offset + hashes.first, offset + hashes.last + 1)
                    }

                    ITEM.find(line)?.let { match ->
                        val marker = match.groups[2]!!.range
                        addStyle(SpanStyle(color = structure, fontWeight = FontWeight.Bold), offset + marker.first, offset + marker.last + 1)
                        match.groups[4]?.range?.let { box -> addStyle(SpanStyle(color = marks, fontWeight = FontWeight.Bold), offset + box.first, offset + box.last + 1) }
                    }

                    LINK.findAll(line).forEach { link ->
                        addStyle(SpanStyle(color = structure), offset + link.range.first, offset + link.range.last + 1)
                    }

                    HASHTAG.findAll(line).forEach { tag ->
                        addStyle(SpanStyle(color = marks), offset + tag.range.first, offset + tag.range.last + 1)
                    }

                    COMMENT.findAll(line).forEach { comment ->
                        addStyle(SpanStyle(color = attributes, fontStyle = FontStyle.Italic), offset + comment.range.first, offset + comment.range.last + 1)

                        if (!comment.value.endsWith("-->")) {
                            inComment = true
                        }
                    }
                }
            }

            offset = end + 1
        }
    }
}
