package com.hyuchia.orbit.style

import com.pinterest.ktlint.rule.engine.core.api.AutocorrectDecision
import com.pinterest.ktlint.rule.engine.core.api.ElementType
import com.pinterest.ktlint.rule.engine.core.api.Rule
import com.pinterest.ktlint.rule.engine.core.api.RuleAutocorrectApproveHandler
import com.pinterest.ktlint.rule.engine.core.api.RuleId
import org.jetbrains.kotlin.com.intellij.lang.ASTNode
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtImportDirective

/**
 * AGENTS.md's rules about the app's own components, each there because
 * breaking it crashed a tablet or looked wrong:
 * - rows in the map's panels and on Home are `ui/ListRow`, not Material's
 *   `ListItem`, whose supporting text throws in a tablet pane's lookahead
 *   pass. A dialog or sheet is a window of its own, outside the pane, and
 *   may use it;
 * - sliders are `ui/OrbitSlider`, and snackbars show in
 *   `ui/OrbitSnackbarHost`: only `ui/` uses Material's own.
 */
public class AppComponentsRule :
    Rule(ruleId = RuleId("orbit:app-components"), about = About(maintainer = "Orbit")),
    RuleAutocorrectApproveHandler {
    override fun beforeVisitChildNodes(
        node: ASTNode,
        emit: (offset: Int, errorMessage: String, canBeAutoCorrected: Boolean) -> AutocorrectDecision,
    ) {
        when (node.elementType) {
            ElementType.IMPORT_DIRECTIVE -> checkImport(node, emit)
            ElementType.CALL_EXPRESSION -> checkListItem(node, emit)
            else -> Unit
        }
    }

    private fun checkImport(node: ASTNode, emit: (Int, String, Boolean) -> AutocorrectDecision) {
        val imported = (node.psi as? KtImportDirective)?.importedFqName?.asString() ?: return

        if (areaOf(node) == "ui") {
            return
        }

        when (imported) {
            "androidx.compose.material3.Slider", "androidx.compose.material3.RangeSlider" ->
                emit(node.startOffset, "Use ui/OrbitSlider, not Material's slider (AGENTS.md)", false)
            "androidx.compose.material3.SnackbarHost", "androidx.compose.material3.Snackbar" ->
                emit(node.startOffset, "Show snackbars in ui/OrbitSnackbarHost, not Material's (AGENTS.md)", false)
        }
    }

    private fun checkListItem(node: ASTNode, emit: (Int, String, Boolean) -> AutocorrectDecision) {
        val call = node.psi as? KtCallExpression ?: return

        if (call.calleeExpression?.text != "ListItem" || areaOf(node) !in PANES || !importsMaterialListItem(node)) {
            return
        }

        if (generateSequence(node.treeParent) { it.treeParent }.any { (it.psi as? KtCallExpression)?.calleeExpression?.text in WINDOWS }) {
            return
        }

        emit(node.startOffset, "Use ui/ListRow for rows here, not Material's ListItem: its supporting text throws in a tablet pane's lookahead pass (AGENTS.md)", false)
    }

    /** What part of the app a file is in, by its package: `map` for `com.hyuchia.orbit.map`. */
    private fun areaOf(node: ASTNode): String =
        (node.psi.containingFile as? KtFile)?.packageFqName?.asString()?.removePrefix(APP_PACKAGE)?.substringBefore('.').orEmpty()

    private fun importsMaterialListItem(node: ASTNode): Boolean =
        (node.psi.containingFile as? KtFile)?.importDirectives?.any { it.importedFqName?.asString() == "androidx.compose.material3.ListItem" } == true

    private companion object {
        const val APP_PACKAGE = "com.hyuchia.orbit."

        /** The areas whose screens sit in a tablet's panes. */
        val PANES = setOf("map", "home")

        /** Calls that open a window of their own, outside the pane's lookahead pass. */
        val WINDOWS = setOf("AlertDialog", "BasicAlertDialog", "Dialog", "ModalBottomSheet", "DropdownMenu")
    }
}
