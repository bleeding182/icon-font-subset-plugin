package com.davidmedenjak.fontsubsetting.analyzer

import org.jetbrains.kotlin.cli.common.CLIConfigurationKeys
import org.jetbrains.kotlin.cli.common.config.KotlinSourceRoot
import org.jetbrains.kotlin.cli.common.messages.MessageRenderer
import org.jetbrains.kotlin.cli.common.messages.PrintingMessageCollector
import org.jetbrains.kotlin.cli.jvm.compiler.EnvironmentConfigFiles
import org.jetbrains.kotlin.cli.jvm.compiler.KotlinCoreEnvironment
import org.jetbrains.kotlin.com.intellij.openapi.Disposable
import org.jetbrains.kotlin.config.CommonConfigurationKeys
import org.jetbrains.kotlin.config.CompilerConfiguration
import java.io.File

internal class PsiEnvironmentFactory {

    fun createEnvironment(
        disposable: Disposable,
        sourceFiles: Collection<File>,
        additionalSourceDirs: Collection<File> = emptyList(),
        moduleName: String = "icon-usage-analysis"
    ): KotlinCoreEnvironment {
        val configuration = createCompilerConfiguration(sourceFiles, additionalSourceDirs, moduleName)

        return KotlinCoreEnvironment.createForProduction(
            disposable,
            configuration,
            EnvironmentConfigFiles.JVM_CONFIG_FILES
        )
    }

    private fun createCompilerConfiguration(
        sourceFiles: Collection<File>,
        additionalSourceDirs: Collection<File>,
        moduleName: String
    ): CompilerConfiguration {
        return CompilerConfiguration().apply {
            put(
                CLIConfigurationKeys.MESSAGE_COLLECTOR_KEY,
                PrintingMessageCollector(System.err, MessageRenderer.PLAIN_RELATIVE_PATHS, false)
            )

            put(CommonConfigurationKeys.MODULE_NAME, moduleName)

            val sourcePaths = collectSourcePaths(sourceFiles, additionalSourceDirs)
            put(CLIConfigurationKeys.CONTENT_ROOTS, sourcePaths)
        }
    }

    private fun collectSourcePaths(
        sourceFiles: Collection<File>,
        additionalSourceDirs: Collection<File>
    ): List<KotlinSourceRoot> {
        val allDirs = (sourceFiles.mapNotNull { it.parentFile } + additionalSourceDirs)
            .distinct()
            .filter { it.exists() }

        return allDirs.map { dir ->
            KotlinSourceRoot(dir.canonicalPath, false, null)
        }
    }
}