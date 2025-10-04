package com.davidmedenjak.fontsubsetting.analyzer

import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtImportDirective
import org.jetbrains.kotlin.psi.KtReferenceExpression
import org.jetbrains.kotlin.psi.KtVisitorVoid

/**
 * PSI visitor that finds references to icon constants in Kotlin code.
 *
 * This visitor traverses Kotlin AST to find:
 * - Direct references like Icons.HOME
 * - Statically imported constants
 * - Qualified references with full package names
 */
class IconReferenceVisitor(
    private val targetClasses: List<String>
) : KtVisitorVoid() {

    private val _usedIcons = mutableSetOf<String>()

    /**
     * Returns the set of icon names found during traversal.
     */
    val usedIcons: Set<String>
        get() = _usedIcons.toSet()

    override fun visitElement(element: org.jetbrains.kotlin.com.intellij.psi.PsiElement) {
        element.acceptChildren(this)

        if (element is KtDotQualifiedExpression) {
            handleDotQualifiedExpression(element)
        }
    }

    override fun visitReferenceExpression(expression: KtReferenceExpression) {
        super.visitReferenceExpression(expression)

        // Skip import statements
        if (expression.parent is KtImportDirective) {
            return
        }

        // Check for statically imported constants
        val text = expression.text
        if (looksLikeConstant(text)) {
            val ktFile = expression.containingKtFile
            if (hasTargetClassImport(ktFile)) {
                _usedIcons.add(text)
            }
        }
    }

    private fun handleDotQualifiedExpression(expression: KtDotQualifiedExpression) {
        val receiverText = expression.receiverExpression.text
        val selectorText = expression.selectorExpression?.text

        if (selectorText != null && isTargetClass(receiverText)) {
            _usedIcons.add(selectorText)
        }
    }

    private fun isTargetClass(className: String): Boolean {
        return targetClasses.any { target ->
            val simpleTargetName = target.substringAfterLast('.')
            className == simpleTargetName ||
            className == target ||
            className.endsWith(".$simpleTargetName")
        }
    }

    private fun hasTargetClassImport(file: KtFile): Boolean {
        return file.importDirectives.any { importDirective ->
            val importPath = importDirective.importPath?.pathStr ?: ""
            targetClasses.any { target ->
                importPath == target ||
                importPath == "$target.*" ||
                importPath.startsWith("$target.")
            }
        }
    }

    private fun looksLikeConstant(text: String): Boolean {
        // Constants typically use UPPER_SNAKE_CASE
        return text.matches(Regex("[A-Z][A-Z0-9_]*"))
    }

    /**
     * Resets the visitor state for reuse.
     */
    fun reset() {
        _usedIcons.clear()
    }
}