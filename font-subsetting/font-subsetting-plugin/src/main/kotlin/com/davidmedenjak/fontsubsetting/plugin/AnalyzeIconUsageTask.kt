package com.davidmedenjak.fontsubsetting.plugin

import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.FileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.SkipWhenEmpty
import org.gradle.api.tasks.TaskAction
import org.jetbrains.kotlin.cli.common.CLIConfigurationKeys
import org.jetbrains.kotlin.cli.common.config.KotlinSourceRoot
import org.jetbrains.kotlin.cli.common.messages.MessageRenderer
import org.jetbrains.kotlin.cli.common.messages.PrintingMessageCollector
import org.jetbrains.kotlin.cli.jvm.compiler.EnvironmentConfigFiles
import org.jetbrains.kotlin.cli.jvm.compiler.KotlinCoreEnvironment
import org.jetbrains.kotlin.com.intellij.openapi.util.Disposer
import org.jetbrains.kotlin.com.intellij.psi.PsiManager
import org.jetbrains.kotlin.config.CommonConfigurationKeys
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtImportDirective
import org.jetbrains.kotlin.psi.KtReferenceExpression
import org.jetbrains.kotlin.psi.KtVisitorVoid
import java.io.File

/**
 * Gradle task that analyzes Kotlin source files to find icon constant usage.
 * Uses Kotlin PSI (Program Structure Interface) for accurate parsing.
 */
@CacheableTask
abstract class AnalyzeIconUsageTask : DefaultTask() {

    @get:InputFiles
    @get:SkipWhenEmpty
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val sourceFiles: ConfigurableFileCollection

    @get:Input
    abstract val targetClasses: ListProperty<String>

    @get:OutputFile
    abstract val outputFile: RegularFileProperty

    @get:Internal
    var generatedSourceDirs: FileCollection = project.files()


    init {
        group = "Font Subsetting"
        description = "Analyzes Kotlin source files to find icon constant usage"
    }

    @TaskAction
    fun analyzeUsage() {
        val usedIcons = mutableSetOf<String>()
        
        logger.info("Analyzing icon usage for ${targetClasses.get().size} target class(es)")
        val fileList = sourceFiles.files.toList()

        // Create Kotlin PSI environment
        val disposable = Disposer.newDisposable()
        try {
            val configuration = CompilerConfiguration().apply {
                put(CLIConfigurationKeys.MESSAGE_COLLECTOR_KEY, 
                    PrintingMessageCollector(System.err, MessageRenderer.PLAIN_RELATIVE_PATHS, false))
                put(CommonConfigurationKeys.MODULE_NAME, "icon-usage-analysis")
                
                // Add source paths for proper import resolution
                val sourcePaths = (sourceFiles.files.toList() + generatedSourceDirs.files)
                    .map { it.parentFile }
                    .distinct()
                    .filter { it.exists() }
                    .map { KotlinSourceRoot(it.canonicalPath, false, null) }
                put(CLIConfigurationKeys.CONTENT_ROOTS, sourcePaths)
            }

            val environment = KotlinCoreEnvironment.createForProduction(
                disposable,
                configuration,
                EnvironmentConfigFiles.JVM_CONFIG_FILES
            )

            val project = environment.project
            val psiManager = PsiManager.getInstance(project)

            // Process each source file
            sourceFiles.files.forEach { file ->
                if (file.extension == "kt") {
                    analyzeKotlinFile(file, psiManager, usedIcons)
                }
            }

            // Write output as simple JSON
            outputFile.get().asFile.apply {
                parentFile.mkdirs()
                // Simple JSON serialization without Gson
                val json = buildString {
                    appendLine("{")
                    append("  \"usedIcons\": [")
                    if (usedIcons.isNotEmpty()) {
                        appendLine()
                        usedIcons.forEachIndexed { index, icon ->
                            append("    \"$icon\"")
                            if (index < usedIcons.size - 1) append(",")
                            appendLine()
                        }
                        append("  ")
                    }
                    appendLine("]")
                    append("}")
                }
                writeText(json)
            }

            if (usedIcons.isNotEmpty()) {
                logger.info("Found ${usedIcons.size} used icons")
            } else {
                logger.warn("No icons found! Check that the target classes are correct: ${targetClasses.get()}")
            }
        } finally {
            Disposer.dispose(disposable)
        }
    }

    private fun analyzeKotlinFile(
        file: File,
        psiManager: PsiManager,
        usedIcons: MutableSet<String>
    ) {
        try {
            val virtualFile = org.jetbrains.kotlin.com.intellij.openapi.vfs.VirtualFileManager.getInstance()
                .findFileByUrl("file://${file.canonicalPath}") ?: return

            val psiFile = psiManager.findFile(virtualFile) as? KtFile
            if (psiFile == null) {
                logger.warn("Could not create PSI file for ${file.path}")
                return
            }

            // Create a visitor to find icon references
            val visitor = IconReferenceVisitor(targetClasses.get(), usedIcons, file.name)
            psiFile.accept(visitor)
        } catch (e: Exception) {
            logger.warn("Failed to analyze file ${file.path}: ${e.message}")
        }
    }

    /**
     * PSI visitor that finds references to icon constants
     */
    private class IconReferenceVisitor(
        private val targetClasses: List<String>,
        private val usedIcons: MutableSet<String>,
        private val fileName: String = ""
    ) : KtVisitorVoid() {

        override fun visitElement(element: org.jetbrains.kotlin.com.intellij.psi.PsiElement) {
            // Visit all children
            element.acceptChildren(this)
            
            // Check if this is a dot qualified expression
            if (element is KtDotQualifiedExpression) {
                handleDotQualifiedExpression(element)
            }
        }
        
        private fun handleDotQualifiedExpression(expression: KtDotQualifiedExpression) {
            val receiverText = expression.receiverExpression.text
            val selectorText = expression.selectorExpression?.text
            
            if (selectorText != null) {
                if (isTargetClass(receiverText)) {
                    usedIcons.add(selectorText)
                }
            }
        }

        override fun visitReferenceExpression(expression: KtReferenceExpression) {
            super.visitReferenceExpression(expression)
            
            // Handle static imports - check if the parent is an import directive
            val parent = expression.parent
            if (parent is KtImportDirective) {
                // This is an import statement, not a usage
                return
            }
            
            // Check if this might be a statically imported icon constant
            val text = expression.text
            if (text.matches(Regex("[A-Z][A-Z0-9_]*"))) {
                // This looks like a constant - check if the file imports our target classes
                val ktFile = expression.containingKtFile
                if (hasTargetClassImport(ktFile)) {
                    // Found possible static import usage
                    usedIcons.add(text)
                }
            }
        }

        private fun isTargetClass(className: String): Boolean {
            return targetClasses.any { target ->
                className == target.substringAfterLast('.') || 
                className == target ||
                className.endsWith(".${target.substringAfterLast('.')}")
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
    }
}