package com.davidmedenjak.fontsubsetting.analyzer

import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtImportDirective
import org.jetbrains.kotlin.psi.KtReferenceExpression
import org.jetbrains.kotlin.psi.KtVisitorVoid

internal class IconReferenceVisitor(
    private val targetClasses: List<String>
) : KtVisitorVoid() {

    private val _usedIcons = mutableSetOf<String>()

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
        return text.matches(CONSTANT_NAME_REGEX)
    }

    fun reset() {
        _usedIcons.clear()
    }

    companion object {
        private val CONSTANT_NAME_REGEX = Regex("[a-zA-Z][a-zA-Z0-9_]*")
    }
}