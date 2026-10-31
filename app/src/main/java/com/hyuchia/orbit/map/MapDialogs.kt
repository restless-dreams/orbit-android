package com.hyuchia.orbit.map

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hyuchia.orbit.R
import com.hyuchia.orbit.container
import com.hyuchia.orbit.data.TRASH_DAYS
import com.hyuchia.orbit.home.tagCounts
import com.hyuchia.orbit.nearby.SendSheet
import com.hyuchia.orbit.privacy.CoverDialog
import com.hyuchia.orbit.theme.OrbitPalette
import com.hyuchia.orbit.theme.parseCssColor
import com.hyuchia.orbit.ui.ColorPickerDialog
import com.hyuchia.orbit.ui.EmojiPickerSheet
import com.hyuchia.orbit.ui.EscapeCloses
import com.hyuchia.orbit.ui.TagsSheet
import com.hyuchia.orbit.ui.TitleDialog
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive

/**
 * What the map's screen has open over itself besides its panels: an idea's
 * icon or color being picked, a goal's amount, and the map's own rename,
 * cover, tags, sending and deleting. The ones a turned screen keeps are
 * saved, each as it was before they were gathered here.
 */
@Stable
internal class MapDialogsState(
    changingGoal: MutableState<String?>,
    goalChange: MutableState<GoalChange?>,
    renamingMap: MutableState<Boolean>,
    coveringMap: MutableState<Boolean>,
    taggingMap: MutableState<Boolean>,
    deletingMap: MutableState<Boolean>,
    sending: MutableState<Boolean>,
) {
    /** The idea whose icon is being picked. */
    var pickingIcon by mutableStateOf<String?>(null)

    /** The idea whose color is being picked. */
    var pickingColor by mutableStateOf<String?>(null)

    /** The goal an amount is being added to, taken off or paid, from its long-press menu. */
    var changingGoal by changingGoal

    /** Which of those. */
    var goalChange by goalChange

    var renamingMap by renamingMap
    var coveringMap by coveringMap
    var taggingMap by taggingMap
    var deletingMap by deletingMap
    var sending by sending
}

@Composable
internal fun rememberMapDialogs(): MapDialogsState {
    val changingGoal = rememberSaveable { mutableStateOf<String?>(null) }
    val goalChange = rememberSaveable { mutableStateOf<GoalChange?>(null) }
    val renamingMap = rememberSaveable { mutableStateOf(false) }
    val coveringMap = rememberSaveable { mutableStateOf(false) }
    val taggingMap = rememberSaveable { mutableStateOf(false) }
    val deletingMap = rememberSaveable { mutableStateOf(false) }
    val sending = rememberSaveable { mutableStateOf(false) }
    return remember { MapDialogsState(changingGoal, goalChange, renamingMap, coveringMap, taggingMap, deletingMap, sending) }
}

/**
 * The dialogs and sheets `dialogs` has open over the map titled `title`, and
 * the ones the map's state opens: the voice memo recorder and the rehearsal's
 * summary.
 */
@Composable
internal fun MapDialogs(model: MapViewModel, ui: MapUi, dialogs: MapDialogsState, title: String) {
    dialogs.pickingIcon?.let { nodeId ->
        val node = ui.nodes[nodeId]
        EmojiPickerSheet(
            title = stringResource(R.string.map_icon_for, node?.text.orEmpty()),
            hasIcon = node?.icon != null,
            onPick = { emoji ->
                dialogs.pickingIcon = null
                model.update(nodeId, "icon", emoji?.let(::JsonPrimitive))
            },
            onDismiss = { dialogs.pickingIcon = null },
        )
    }

    dialogs.pickingColor?.let { nodeId ->
        val node = ui.nodes[nodeId]

        if (node == null) {
            dialogs.pickingColor = null
        } else {
            ColorPickerDialog(
                title = stringResource(R.string.map_color_for, node.text),
                label = node.label(stringResource(R.string.common_untitled)),
                initial = parseCssColor(node.color) ?: parseCssColor(node.drawnColor) ?: OrbitPalette.first(),
                onPick = { hex ->
                    dialogs.pickingColor = null
                    model.update(nodeId, "color", JsonPrimitive(hex))
                },
                onDismiss = { dialogs.pickingColor = null },
            )
        }
    }

    val goalNode = dialogs.changingGoal?.let { ui.nodes[it] }
    val goal = goalNode?.let(Goal::of)
    val change = dialogs.goalChange

    if (goalNode != null && goal != null && change != null) {
        GoalAmountDialog(
            goal = goal,
            change = change,
            onDone = { amount ->
                dialogs.changingGoal = null
                model.addToGoal(goalNode.id, goal.signed(change, amount))
            },
            onDismiss = { dialogs.changingGoal = null },
        )
    }

    if (dialogs.coveringMap) {
        model.summary()?.takeIf { it.metadata.isPrivate }?.let { map ->
            CoverDialog(map, onDismiss = { dialogs.coveringMap = false })
        }
    }

    if (dialogs.renamingMap) {
        TitleDialog(
            heading = stringResource(R.string.map_rename_map),
            confirm = stringResource(R.string.common_rename),
            initial = ui.map?.root?.text ?: title,
            onConfirm = { text ->
                dialogs.renamingMap = false
                ui.map?.root?.let { root -> model.update(root.id, "text", JsonPrimitive(text)) }
            },
            onDismiss = { dialogs.renamingMap = false },
        )
    }

    if (dialogs.taggingMap) {
        // Every tag the maps have, to pick from: the map's own come from the page, which writes them. Locked private maps' stay unsaid.
        val summaries by LocalContext.current.container.maps.maps.collectAsStateWithLifecycle()
        val privacy by LocalContext.current.container.privateMaps.view.collectAsStateWithLifecycle()
        val known = remember(summaries, privacy) { tagCounts(summaries.orEmpty().filterNot(privacy::hides)).map { it.name } }

        TagsSheet(
            title = title,
            tags = ui.map?.options?.tags.orEmpty(),
            known = known,
            onChange = { tags -> model.setOptions(mapOf("tags" to JsonArray(tags.map(::JsonPrimitive)))) },
            onDismiss = { dialogs.taggingMap = false },
        )
    }

    if (dialogs.sending) {
        SendSheet(model.id, title, onDismiss = { dialogs.sending = false })
    }

    ui.recording?.let { nodeId ->
        val node = ui.nodes[nodeId]

        if (node == null) {
            LaunchedEffect(nodeId) { model.closeRecorder() }
        } else {
            RecorderSheet(model, node, onDismiss = model::closeRecorder)
        }
    }

    ui.rehearsal?.let { rehearsal ->
        RehearsalSheet(
            rehearsal,
            ui.nodes,
            onAgain = {
                model.closeRehearsal()
                model.startPresentation(0, fromStart = true, rehearse = true)
            },
            onDismiss = model::closeRehearsal,
        )
    }

    if (dialogs.deletingMap) {
        AlertDialog(
            onDismissRequest = { dialogs.deletingMap = false },
            title = {
                EscapeCloses { dialogs.deletingMap = false }
                Text(stringResource(R.string.map_delete_dialog_title, title))
            },
            text = { Text(pluralStringResource(R.plurals.map_delete_dialog_body, TRASH_DAYS, TRASH_DAYS)) },
            confirmButton = {
                TextButton(onClick = {
                    dialogs.deletingMap = false
                    model.deleteMap()
                }) { Text(stringResource(R.string.common_delete)) }
            },
            dismissButton = { TextButton(onClick = { dialogs.deletingMap = false }) { Text(stringResource(R.string.common_cancel)) } },
        )
    }
}
