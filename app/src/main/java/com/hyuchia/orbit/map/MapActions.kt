package com.hyuchia.orbit.map

import com.hyuchia.orbit.page.MapNode
import kotlinx.serialization.json.JsonPrimitive
import java.math.BigDecimal

/**
 * Does what an idea's long-press menu asked (`action`, on `node`), through
 * `model`; what the screen asks itself, an icon or a color to pick or a
 * goal's amount, opens in `dialogs`. A step of a goal keeps the menu open, for
 * the next one; anything else closes the panel first.
 */
internal fun actOnIdea(model: MapViewModel, ui: MapUi, dialogs: MapDialogsState, node: MapNode, action: NodeAction) {
    if (action is NodeAction.StepGoal) {
        // The ring behind the menu moves with each step.
        model.addToGoal(node.id, BigDecimal(action.by))
        return
    }

    model.show(null)
    val parent = node.parentId?.let { ui.nodes[it] }

    when (action) {
        NodeAction.AddChild -> model.addIdea(node.id)
        NodeAction.AddSibling -> model.addIdea(node.parentId ?: return, node.index + 1)
        NodeAction.Rename -> model.rename(node.id)
        NodeAction.Edit -> model.show(Panel.Editor(node.id))
        NodeAction.PickIcon -> dialogs.pickingIcon = node.id
        NodeAction.PickColor -> dialogs.pickingColor = node.id
        is NodeAction.SetStatus -> model.update(node.id, "status", action.status?.let(::JsonPrimitive))
        is NodeAction.SetColor -> model.update(node.id, "color", action.color?.let(::JsonPrimitive))
        is NodeAction.SetCollapsed -> model.update(node.id, "collapsed", if (action.collapsed) JsonPrimitive(true) else null)
        NodeAction.Copy -> model.copy(node.id)
        NodeAction.Paste -> model.paste(node.id)
        NodeAction.Duplicate -> model.duplicate(node.id)
        NodeAction.MoveEarlier -> model.move(node.id, node.parentId ?: return, node.index - 1)
        NodeAction.MoveLater -> model.move(node.id, node.parentId ?: return, node.index + 1)
        NodeAction.MoveOut -> {
            val grandparent = parent?.parentId ?: return
            model.move(node.id, grandparent, parent.index + 1)
        }
        NodeAction.MoveIn -> {
            val before = ui.map?.nodes?.firstOrNull { it.parentId == node.parentId && it.index == node.index - 1 } ?: return
            model.move(node.id, before.id, null)
        }
        NodeAction.Delete -> model.remove(node.id)
        NodeAction.RecordMemo -> model.recordMemo(node.id)
        NodeAction.PlaySound -> model.toggleSound(node.id)
        is NodeAction.ChangeGoal -> {
            dialogs.changingGoal = node.id
            dialogs.goalChange = action.change
        }
        is NodeAction.StepGoal -> Unit
    }
}
