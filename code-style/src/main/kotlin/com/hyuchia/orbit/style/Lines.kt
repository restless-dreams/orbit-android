package com.hyuchia.orbit.style

import org.jetbrains.kotlin.com.intellij.lang.ASTNode
import org.jetbrains.kotlin.com.intellij.psi.PsiWhiteSpace
import org.jetbrains.kotlin.com.intellij.psi.impl.source.tree.LeafPsiElement

/** Whether the space between two nodes of the same parent, which may hold comments, has a blank line in it. */
internal fun hasBlankLineBetween(first: ASTNode, second: ASTNode): Boolean {
    var node = first.treeNext

    while (node != null && node != second) {
        if (node.psi is PsiWhiteSpace && node.text.count { it == '\n' } > 1) {
            return true
        }

        node = node.treeNext
    }

    return false
}

/**
 * Adds a blank line where the line `first` ends on, before any comments that
 * belong to `second`. Returns false when they're on the same line.
 */
internal fun addBlankLineBetween(first: ASTNode, second: ASTNode): Boolean {
    var node = first.treeNext

    while (node != null && node != second) {
        if (node.psi is PsiWhiteSpace && node.text.contains('\n')) {
            (node as LeafPsiElement).rawReplaceWithText("\n" + node.text)
            return true
        }

        node = node.treeNext
    }

    return false
}
