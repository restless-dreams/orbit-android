package com.hyuchia.orbit.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isSpecified
import com.mikepenz.markdown.annotator.annotatorSettings
import com.mikepenz.markdown.annotator.buildMarkdownAnnotatedString
import com.mikepenz.markdown.compose.LocalBulletListHandler
import com.mikepenz.markdown.compose.LocalMarkdownComponents
import com.mikepenz.markdown.compose.LocalMarkdownPadding
import com.mikepenz.markdown.compose.LocalMarkdownTypography
import com.mikepenz.markdown.compose.LocalOrderedListHandler
import com.mikepenz.markdown.compose.MarkdownElement
import com.mikepenz.markdown.compose.components.MarkdownComponentModel
import com.mikepenz.markdown.compose.elements.MarkdownBulletList
import com.mikepenz.markdown.compose.elements.MarkdownOrderedList
import com.mikepenz.markdown.compose.elements.MarkdownParagraph
import com.mikepenz.markdown.compose.elements.listDepth
import org.intellij.markdown.MarkdownElementTypes
import org.intellij.markdown.MarkdownTokenTypes
import org.intellij.markdown.ast.ASTNode
import org.intellij.markdown.ast.getTextInNode
import org.intellij.markdown.flavours.gfm.GFMTokenTypes

/**
 * What a note's checklists show and do: the note as it is now, boxes just
 * checked or unchecked, by line, and what checks one, if anything.
 */
internal class NoteChecklist(val note: String, val checks: Map<Int, Boolean>, val onCheck: ((NoteCheck) -> Unit)?)

internal val LocalNoteChecklist = staticCompositionLocalOf { NoteChecklist("", emptyMap(), null) }

/** A checkbox's room, the Material one's: 20 dp with 2 dp around it. */
private val BOX = 24.dp

/** Between a checkbox, or a bullet in a list with checkboxes, and its item's text. */
private val BOX_GAP = 12.dp

/** A checklist item that changes takes at least this much height, so a finger finds it, as Material's touch targets do. */
private val TOUCH_HEIGHT = 48.dp

/** An item's box, when it's an item of a checklist: the renderer reads `[ ]`, `[x]` and `[X]` after its marker as one. */
private val ASTNode.box: ASTNode? get() = children.getOrNull(1)?.takeIf { it.type == GFMTokenTypes.CHECK_BOX }

/**
 * A list, as the renderer draws one; one with checkboxes in it is drawn
 * here: each of its checklist items is a row that checks with a press
 * anywhere on it, and TalkBack hears as one checkbox named by its text. Its
 * other items keep their bullets, beside the boxes.
 */
@Composable
internal fun NoteList(model: MarkdownComponentModel, ordered: Boolean) {
    val items = remember(model.node) { model.node.children.filter { it.type == MarkdownElementTypes.LIST_ITEM } }

    if (items.none { it.box != null }) {
        if (ordered) {
            MarkdownOrderedList(model.content, model.node, style = model.typography.ordered, depth = model.listDepth)
        } else {
            MarkdownBulletList(model.content, model.node, style = model.typography.bullet, depth = model.listDepth)
        }

        return
    }

    val padding = LocalMarkdownPadding.current
    val bullets = if (ordered) LocalOrderedListHandler.current else LocalBulletListHandler.current
    // Numbered lists count on from the number they start at.
    val first = remember(model.node) { items.firstOrNull()?.getTextInNode(model.content)?.takeWhile(Char::isDigit)?.toString()?.toIntOrNull() ?: 1 }

    Column(Modifier.padding(start = padding.listIndent * model.listDepth, top = padding.list, bottom = padding.list)) {
        items.forEachIndexed { index, item ->
            val box = item.box

            if (box != null) {
                ChecklistItem(model.content, item, box, model.typography.paragraph)
            } else {
                val marker = item.children.firstOrNull { it.type == MarkdownTokenTypes.LIST_BULLET || it.type == MarkdownTokenTypes.LIST_NUMBER }
                val type = if (ordered) MarkdownTokenTypes.LIST_NUMBER else MarkdownTokenTypes.LIST_BULLET
                val bullet = bullets.transform(type, marker?.getTextInNode(model.content), index, first, model.listDepth)
                BulletItem(model.content, item, bullet, if (ordered) model.typography.ordered else model.typography.bullet)
            }
        }
    }
}

/**
 * A checklist item: a Material checkbox and the item's text, checked with a
 * press anywhere on them when the note's boxes change, or shown as it is.
 * Lists under it come below, beside its text, and aren't part of its press.
 */
@Composable
private fun ChecklistItem(content: String, item: ASTNode, box: ASTNode, style: TextStyle) {
    val checklist = LocalNoteChecklist.current
    val components = LocalMarkdownComponents.current
    val line = remember(content, box) { lineAt(content, box.startOffset) }
    val written = remember(content, box) { box.getTextInNode(content).any { it == 'x' || it == 'X' } }
    // The note as it is now says, as the renderer reads a changed note again: a box keeps its line when it's checked.
    val checked = checklist.checks[line] ?: noteBoxOn(checklist.note, line) ?: written
    val onCheck = checklist.onCheck
    val parts = item.children.filter { it.type != MarkdownTokenTypes.LIST_BULLET && it.type != MarkdownTokenTypes.LIST_NUMBER && it.type != GFMTokenTypes.CHECK_BOX }
    val text = parts.filter { it.type == MarkdownElementTypes.PARAGRAPH }
    val under = parts.filter { it.type != MarkdownElementTypes.PARAGRAPH && it.type != MarkdownTokenTypes.EOL && it.type != MarkdownTokenTypes.WHITE_SPACE }
    // A checked item reads as done: dimmed, and struck through, its links too, which keep their underline.
    val shown = if (checked) style.copy(color = MaterialTheme.colorScheme.onSurfaceVariant, textDecoration = TextDecoration.LineThrough) else style
    val links = LocalMarkdownTypography.current.textLink
    val settings = annotatorSettings()
    val shownSettings = if (checked) annotatorSettings(linkTextSpanStyle = links.struckThrough()) else settings
    val label = remember(content, text) { text.joinToString(" ") { content.buildMarkdownAnnotatedString(it, style, settings).text.trim() } }
    // The box sits on the middle of the text's first line, whatever size the text is.
    val lineHeight = with(LocalDensity.current) { style.lineHeight.takeIf { it.isSpecified }?.toDp() ?: style.fontSize.toDp() * 1.5f }
    val boxTop = ((lineHeight - BOX) / 2).coerceAtLeast(0.dp)

    val pressing = if (onCheck != null) {
        Modifier
            .heightIn(min = TOUCH_HEIGHT)
            .clip(RoundedCornerShape(12.dp))
            .toggleable(value = checked, role = Role.Checkbox, onValueChange = { onCheck(NoteCheck(line, it, label)) })
            .padding(vertical = ((TOUCH_HEIGHT - lineHeight) / 2).coerceAtLeast(4.dp))
    } else {
        Modifier
            .semantics(mergeDescendants = true) {
                role = Role.Checkbox
                toggleableState = ToggleableState(checked)
            }
            .padding(vertical = 4.dp)
    }

    Column {
        Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(BOX_GAP), modifier = Modifier.fillMaxWidth().then(pressing)) {
            Checkbox(checked = checked, onCheckedChange = null, modifier = Modifier.padding(top = boxTop))

            Column(Modifier.weight(1f)) {
                text.forEach { paragraph -> MarkdownParagraph(content, paragraph, style = shown, annotatorSettings = shownSettings) }
            }
        }

        if (under.isNotEmpty()) {
            Column(Modifier.padding(start = BOX + BOX_GAP)) {
                under.forEach { child -> MarkdownElement(child, components, content, includeSpacer = false) }
            }
        }
    }
}

/** Link styles with each one's line through it too, beside any it has. */
private fun TextLinkStyles.struckThrough(): TextLinkStyles {
    val through = { span: SpanStyle? -> (span ?: SpanStyle()).let { it.copy(textDecoration = TextDecoration.combine(listOfNotNull(it.textDecoration, TextDecoration.LineThrough))) } }
    return TextLinkStyles(style = through(style), focusedStyle = focusedStyle?.let(through), hoveredStyle = hoveredStyle?.let(through), pressedStyle = pressedStyle?.let(through))
}

/** An item without a box in a list with boxes: its bullet or number where their boxes are, and its text beside it. */
@Composable
private fun BulletItem(content: String, item: ASTNode, bullet: String, style: TextStyle) {
    val components = LocalMarkdownComponents.current
    val parts = item.children.filter { it.type != MarkdownTokenTypes.LIST_BULLET && it.type != MarkdownTokenTypes.LIST_NUMBER }
    val touch = LocalNoteChecklist.current.onCheck != null
    val lineHeight = with(LocalDensity.current) { style.lineHeight.takeIf { it.isSpecified }?.toDp() ?: style.fontSize.toDp() * 1.5f }

    Row(
        horizontalArrangement = Arrangement.spacedBy(BOX_GAP),
        modifier = Modifier
            .fillMaxWidth()
            // Beside rows that check with a press, as tall as they are.
            .padding(vertical = if (touch) ((TOUCH_HEIGHT - lineHeight) / 2).coerceAtLeast(4.dp) else 4.dp),
    ) {
        Box(Modifier.width(BOX), contentAlignment = Alignment.TopCenter) {
            Text(bullet.trim(), style = style)
        }

        Column(Modifier.weight(1f)) {
            parts.forEach { child -> MarkdownElement(child, components, content, includeSpacer = false) }
        }
    }
}
