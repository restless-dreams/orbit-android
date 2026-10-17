package com.hyuchia.orbit.style

import com.pinterest.ktlint.rule.engine.core.api.AutocorrectDecision
import com.pinterest.ktlint.rule.engine.core.api.ElementType
import com.pinterest.ktlint.rule.engine.core.api.Rule
import com.pinterest.ktlint.rule.engine.core.api.RuleAutocorrectApproveHandler
import com.pinterest.ktlint.rule.engine.core.api.RuleId
import com.pinterest.ktlint.rule.engine.core.api.ifAutocorrectAllowed
import org.jetbrains.kotlin.com.intellij.lang.ASTNode
import org.jetbrains.kotlin.psi.KtDeclaration
import org.jetbrains.kotlin.psi.KtNamedDeclaration

/**
 * A member with a doc comment has a blank line before its comment and after
 * it, unless it's the first or the last. This applies in class, object and
 * interface bodies (properties, functions, enum entries…) and in a class's
 * constructor (its properties).
 */
public class BlankLinesAroundDocumentedMembersRule :
    Rule(ruleId = RuleId("orbit:blank-lines-around-documented-members"), about = About(maintainer = "Orbit")),
    RuleAutocorrectApproveHandler {
    override fun beforeVisitChildNodes(
        node: ASTNode,
        emit: (offset: Int, errorMessage: String, canBeAutoCorrected: Boolean) -> AutocorrectDecision,
    ) {
        val members = when {
            node.elementType == ElementType.CLASS_BODY -> {
                node.getChildren(null).filter { it.psi is KtDeclaration }
            }

            node.elementType == ElementType.VALUE_PARAMETER_LIST && node.treeParent?.elementType == ElementType.PRIMARY_CONSTRUCTOR -> {
                node.getChildren(null).filter { it.elementType == ElementType.VALUE_PARAMETER }
            }

            else -> return
        }

        if (!node.text.contains('\n')) {
            return
        }

        members.forEachIndexed { index, member ->
            if (member.findChildByType(ElementType.KDOC) == null) {
                return@forEachIndexed
            }

            val name = (member.psi as? KtNamedDeclaration)?.name ?: "this member"
            val previous = members.getOrNull(index - 1)
            val next = members.getOrNull(index + 1)

            if (previous != null && !hasBlankLineBetween(previous, member)) {
                emit(member.startOffset, "Add a blank line before the doc comment of $name", true).ifAutocorrectAllowed {
                    addBlankLineBetween(previous, member)
                }
            }

            if (next != null && !hasBlankLineBetween(member, next)) {
                emit(member.startOffset, "Add a blank line after $name, which has a doc comment", true).ifAutocorrectAllowed {
                    addBlankLineBetween(member, next)
                }
            }
        }
    }
}
