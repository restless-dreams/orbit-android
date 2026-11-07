package com.hyuchia.orbit.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.CollectionInfo
import androidx.compose.ui.semantics.CollectionItemInfo
import androidx.compose.ui.semantics.collectionInfo
import androidx.compose.ui.semantics.collectionItemInfo
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import com.mikepenz.markdown.annotator.annotatorSettings
import com.mikepenz.markdown.annotator.buildMarkdownAnnotatedString
import com.mikepenz.markdown.compose.components.MarkdownComponentModel
import org.intellij.markdown.ast.ASTNode
import org.intellij.markdown.ast.getTextInNode
import org.intellij.markdown.flavours.gfm.GFMElementTypes
import org.intellij.markdown.flavours.gfm.GFMTokenTypes

/** A column grows to fit its longest cell on one line, up to this: a longer cell wraps. */
private val LONGEST_COLUMN = 240.dp

/** A note's table: its header row and its rows, each a cell for every column, and each column's alignment. */
internal class NoteTableCells(val alignments: List<TextAlign>, val head: List<ASTNode?>, val rows: List<List<ASTNode?>>)

/**
 * The table as the renderer read it, each row given a cell for every column
 * of its header (a row with fewer is filled with empty ones), and each
 * column's alignment from the colons in its row of dashes.
 */
internal fun noteTableCells(content: String, table: ASTNode): NoteTableCells {
    val head = table.children.firstOrNull { it.type == GFMElementTypes.HEADER }?.children?.filter { it.type == GFMTokenTypes.CELL }.orEmpty()
    val dashes = table.children.firstOrNull { it.type == GFMTokenTypes.TABLE_SEPARATOR && it.getTextInNode(content).contains('-') }?.getTextInNode(content)?.toString()

    val alignments = dashes?.trim()?.removePrefix("|")?.removeSuffix("|")?.split('|')?.map { cell ->
        val dash = cell.trim()

        when {
            dash.startsWith(':') && dash.endsWith(':') -> TextAlign.Center
            dash.endsWith(':') -> TextAlign.End
            else -> TextAlign.Start
        }
    }.orEmpty()

    val rows = table.children.filter { it.type == GFMElementTypes.ROW }.map { row ->
        val cells = row.children.filter { it.type == GFMTokenTypes.CELL }
        List(head.size) { cells.getOrNull(it) }
    }

    return NoteTableCells(List(head.size) { alignments.getOrElse(it) { TextAlign.Start } }, head, rows)
}

/**
 * A note's table in Material's look: in an outlined box with a tonal header
 * row and lines between the rows, each column as wide as its cells need, up
 * to [LONGEST_COLUMN], and aligned as the table says. One wider than the
 * note scrolls sideways inside it; a narrower one grows to the note's width.
 * TalkBack reads it as a table, its header cells as headings.
 */
@Composable
internal fun NoteTable(model: MarkdownComponentModel) {
    val table = remember(model.content, model.node) { noteTableCells(model.content, model.node) }

    if (table.head.isEmpty()) {
        return
    }

    val colors = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(12.dp)
    val columns = table.head.size
    val rows = table.rows.size + 1

    TableGrid(
        columns = columns,
        rows = rows,
        line = colors.outlineVariant,
        modifier = Modifier
            .padding(vertical = 4.dp)
            .fillMaxWidth()
            .clip(shape)
            .border(1.dp, colors.outlineVariant, shape)
            .horizontalScroll(rememberScrollState())
            .semantics { collectionInfo = CollectionInfo(rowCount = rows, columnCount = columns) },
    ) {
        table.head.forEachIndexed { column, cell ->
            TableCell(model.content, cell, 0, column, table.alignments[column], header = true)
        }

        table.rows.forEachIndexed { row, cells ->
            cells.forEachIndexed { column, cell ->
                TableCell(model.content, cell, row + 1, column, table.alignments[column], header = false)
            }
        }
    }
}

@Composable
private fun TableCell(content: String, cell: ASTNode?, row: Int, column: Int, alignment: TextAlign, header: Boolean) {
    val type = MaterialTheme.typography
    val style: TextStyle = if (header) type.labelLarge else type.bodyMedium
    val settings = annotatorSettings()
    val text = remember(content, cell, style) { cell?.let { trimmed(content.buildMarkdownAnnotatedString(it, style, settings)) } ?: AnnotatedString("") }

    Box(
        Modifier
            .then(if (header) Modifier.background(MaterialTheme.colorScheme.surfaceContainerHigh) else Modifier)
            // A cell is heard as one, with where it is in the table.
            .semantics(mergeDescendants = true) {
                collectionItemInfo = CollectionItemInfo(rowIndex = row, rowSpan = 1, columnIndex = column, columnSpan = 1)

                if (header) {
                    heading()
                }
            }
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Text(text, style = style, textAlign = alignment, modifier = Modifier.fillMaxWidth())
    }
}

/** The text without the spaces around it, which a cell's text keeps from between its pipes. */
private fun trimmed(text: AnnotatedString): AnnotatedString {
    val start = text.indexOfFirst { !it.isWhitespace() }

    if (start < 0) {
        return AnnotatedString("")
    }

    return text.subSequence(start, text.indexOfLast { !it.isWhitespace() } + 1)
}

/** Where the lines between a table's rows are drawn, as it was last measured. */
private class RowLines {
    var at: FloatArray = FloatArray(0)
}

/**
 * The cells, `columns` to a row, laid out as a table: each column as wide
 * as its widest cell on one line, up to [LONGEST_COLUMN], and the columns
 * sharing what's left of the width they're given at least (the note's,
 * under a sideways scroll) in proportion; each row as tall as its tallest
 * cell, with every cell as tall as its row, and a line between the rows.
 * Text measures without alignment lines, so this lays out in a pane's
 * lookahead pass too.
 */
@Composable
private fun TableGrid(columns: Int, rows: Int, line: Color, modifier: Modifier, cells: @Composable () -> Unit) {
    val lines = remember { RowLines() }
    val longest = with(LocalDensity.current) { LONGEST_COLUMN.roundToPx() }

    Layout(
        content = cells,
        modifier = modifier.drawWithContent {
            drawContent()

            for (y in lines.at) {
                drawLine(line, Offset(0f, y), Offset(size.width, y), strokeWidth = 1.dp.toPx())
            }
        },
    ) { measurables, constraints ->
        val widths = IntArray(columns) { column ->
            (0 until rows).maxOf { row -> measurables[row * columns + column].maxIntrinsicWidth(Constraints.Infinity).coerceAtMost(longest) }
        }

        val natural = widths.sum()
        val width = maxOf(natural, constraints.minWidth)

        // A table narrower than the note grows to its width, each column by its share.
        if (natural == 0) {
            widths.fill(width / columns)
        } else if (natural < width) {
            var given = 0

            for (column in 0 until columns) {
                val more = ((width - natural).toLong() * widths[column] / natural).toInt()
                widths[column] += more
                given += more
            }

            widths[columns - 1] += width - natural - given
        }

        val heights = IntArray(rows) { row ->
            (0 until columns).maxOf { column -> measurables[row * columns + column].minIntrinsicHeight(widths[column]) }
        }

        val placeables = measurables.mapIndexed { index, cell -> cell.measure(Constraints.fixed(widths[index % columns], heights[index / columns])) }
        lines.at = FloatArray(rows - 1) { row -> heights.take(row + 1).sum().toFloat() }

        layout(widths.sum(), heights.sum()) {
            var y = 0

            for (row in 0 until rows) {
                var x = 0

                for (column in 0 until columns) {
                    placeables[row * columns + column].placeRelative(x, y)
                    x += widths[column]
                }

                y += heights[row]
            }
        }
    }
}
