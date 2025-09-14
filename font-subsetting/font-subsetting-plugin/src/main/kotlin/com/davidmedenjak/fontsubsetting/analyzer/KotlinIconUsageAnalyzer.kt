package com.davidmedenjak.fontsubsetting.analyzer

import org.jetbrains.kotlin.com.intellij.openapi.util.Disposer
import org.jetbrains.kotlin.com.intellij.openapi.vfs.VirtualFileManager
import org.jetbrains.kotlin.com.intellij.psi.PsiManager
import org.jetbrains.kotlin.psi.KtFile
import java.io.File
import java.util.logging.Logger

/**
 * Analyzes Kotlin source files to find icon constant usage.
 *
 * This is the main entry point for icon usage analysis, coordinating
 * PSI environment setup, file parsing, and visitor traversal.
 */
class KotlinIconUsageAnalyzer(
    private val targetClasses: List<String>,
    private val logger: Logger? = null
) {

    private val environmentFactory = PsiEnvironmentFactory()

    /**
     * Analyzes the specified Kotlin source files for icon usage.
     *
     * @param sourceFiles Kotlin source files to analyze
     * @param additionalSourceDirs Additional directories containing generated sources
     * @return Analysis result containing used icons and any errors
     */
    fun analyze(
        sourceFiles: Collection<File>,
        additionalSourceDirs: Collection<File> = emptyList()
    ): IconUsageResult {
        val usedIcons = mutableSetOf<String>()
        val errors = mutableListOf<Pair<String, String>>()
        var analyzedCount = 0

        val disposable = Disposer.newDisposable()
        try {
            val environment = environmentFactory.createEnvironment(
                disposable,
                sourceFiles,
                additionalSourceDirs
            )

            val psiManager = PsiManager.getInstance(environment.project)
            val visitor = IconReferenceVisitor(targetClasses)

            sourceFiles.forEach { file ->
                if (file.extension == "kt") {
                    when (val result = analyzeFile(file, psiManager, visitor)) {
                        is FileAnalysisResult.Success -> {
                            usedIcons.addAll(result.icons)
                            analyzedCount++
                        }
                        is FileAnalysisResult.Error -> {
                            errors.add(file.path to result.message)
                            logger?.warning("Failed to analyze ${file.path}: ${result.message}")
                        }
                    }
                }
            }

            logger?.info("Analysis complete: $analyzedCount files analyzed, ${usedIcons.size} icons found")

        } catch (e: Exception) {
            logger?.severe("Fatal error during analysis: ${e.message}")
            errors.add("" to "Fatal error: ${e.message ?: "Unknown error"}")
        } finally {
            Disposer.dispose(disposable)
        }

        return IconUsageResult(usedIcons, analyzedCount, errors)
    }

    private fun analyzeFile(
        file: File,
        psiManager: PsiManager,
        visitor: IconReferenceVisitor
    ): FileAnalysisResult {
        return try {
            val virtualFile = VirtualFileManager.getInstance()
                .findFileByUrl("file://${file.canonicalPath}")

            if (virtualFile == null) {
                return FileAnalysisResult.Error("Could not create virtual file")
            }

            val psiFile = psiManager.findFile(virtualFile) as? KtFile
            if (psiFile == null) {
                return FileAnalysisResult.Error("Could not create PSI file")
            }

            // Reset visitor state for this file
            visitor.reset()
            psiFile.accept(visitor)

            FileAnalysisResult.Success(visitor.usedIcons)
        } catch (e: Exception) {
            FileAnalysisResult.Error(e.message ?: "Unknown error")
        }
    }

    /**
     * Result of analyzing a single file.
     */
    private sealed class FileAnalysisResult {
        data class Success(val icons: Set<String>) : FileAnalysisResult()
        data class Error(val message: String) : FileAnalysisResult()
    }
}