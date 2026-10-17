package com.hyuchia.orbit.style

import com.pinterest.ktlint.rule.engine.core.api.AutocorrectDecision
import com.pinterest.ktlint.rule.engine.core.api.ElementType
import com.pinterest.ktlint.rule.engine.core.api.Rule
import com.pinterest.ktlint.rule.engine.core.api.RuleAutocorrectApproveHandler
import com.pinterest.ktlint.rule.engine.core.api.RuleId
import com.pinterest.ktlint.rule.engine.core.api.ifAutocorrectAllowed
import org.jetbrains.kotlin.com.intellij.lang.ASTNode
import org.jetbrains.kotlin.com.intellij.psi.PsiComment
import org.jetbrains.kotlin.com.intellij.psi.PsiWhiteSpace

/** Nodes whose closing brace ends a block: blocks and function bodies, lambdas, `when`s and class bodies. */
private val blockOwners = setOf(ElementType.BLOCK, ElementType.FUNCTION_LITERAL, ElementType.WHEN, ElementType.CLASS_BODY)

/** Statements that are blocks even when they're written on one line, if they have braces. */
private val controlStatements = setOf(ElementType.IF, ElementType.WHEN, ElementType.FOR, ElementType.WHILE, ElementType.DO_WHILE, ElementType.TRY)

/** What sits between statements without being one. */
private val notStatements = setOf(ElementType.LBRACE, ElementType.RBRACE, ElementType.SEMICOLON)

/**
 * A block has a blank line before and after it, unless it starts or ends the
 * block it's in. Blocks are `if`s, `when`s, loops and `try`s, and statements on
 * several lines whose last line starts with the brace that closes a block: a
 * function, a lambda (so a composable with content), a class or an object.
 */
public class BlankLinesAroundBlocksRule :
    Rule(ruleId = RuleId("orbit:blank-lines-around-blocks"), about = About(maintainer = "Orbit")),
    RuleAutocorrectApproveHandler {
    override fun beforeVisitChildNodes(
        node: ASTNode,
        emit: (offset: Int, errorMessage: String, canBeAutoCorrected: Boolean) -> AutocorrectDecision,
    ) {
        if (node.elementType != ElementType.BLOCK && node.elementType != ElementType.FILE) {
            return
        }

        val statements = node.getChildren(null).filter { child ->
            child.psi !is PsiWhiteSpace && child.psi !is PsiComment && child.elementType !in notStatements && child.textLength > 0
        }

        statements.zipWithNext { previous, next ->
            val previousIsBlock = isBlock(previous)

            if ((previousIsBlock || isBlock(next)) && !hasBlankLineBetween(previous, next)) {
                val message = if (previousIsBlock) "Add a blank line after this block" else "Add a blank line before this block"

                emit(next.startOffset, message, true).ifAutocorrectAllowed {
                    addBlankLineBetween(previous, next)
                }
            }
        }
    }

    private fun isBlock(statement: ASTNode): Boolean {
        val text = statement.text

        if (statement.elementType in controlStatements && text.trimEnd().endsWith('}')) {
            return true
        }

        val lastLineStart = text.lastIndexOf('\n') + 1

        if (lastLineStart == 0) {
            return false
        }

        val indent = text.substring(lastLineStart).indexOfFirst { !it.isWhitespace() }

        if (indent < 0) {
            return false
        }

        val firstOnLastLine = statement.findLeafElementAt(lastLineStart + indent)

        return firstOnLastLine?.elementType == ElementType.RBRACE && firstOnLastLine.treeParent?.elementType in blockOwners
    }
}
