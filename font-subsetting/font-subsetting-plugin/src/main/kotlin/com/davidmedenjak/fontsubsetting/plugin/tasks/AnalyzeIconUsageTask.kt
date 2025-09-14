package com.davidmedenjak.fontsubsetting.plugin.tasks

import com.davidmedenjak.fontsubsetting.analyzer.KotlinIconUsageAnalyzer
import com.davidmedenjak.fontsubsetting.plugin.Constants
import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.SkipWhenEmpty
import org.gradle.api.tasks.TaskAction
import java.util.logging.Handler
import java.util.logging.Level
import java.util.logging.LogRecord
import java.util.logging.Logger

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

    init {
        group = Constants.PLUGIN_GROUP
        description = "Analyzes Kotlin source files to find icon constant usage"
    }

    @TaskAction
    fun analyzeUsage() {
        val targetClassList = targetClasses.get()
        logger.info("Analyzing icon usage for ${targetClassList.size} target class(es)")

        val analyzer = KotlinIconUsageAnalyzer(
            targetClasses = targetClassList,
            logger = Logger.getLogger(this::class.java.name).apply {
                addHandler(object : Handler() {
                    override fun publish(record: LogRecord) {
                        when (record.level) {
                            Level.SEVERE -> logger.error(record.message)
                            Level.WARNING -> logger.warn(record.message)
                            else -> logger.info(record.message)
                        }
                    }

                    override fun flush() {}
                    override fun close() {}
                })
            }
        )

        val result = analyzer.analyze(
            sourceFiles = sourceFiles.files.toList(),
            additionalSourceDirs = emptyList()
        )

        result.writeToFile(outputFile.get().asFile)
        if (result.usedIcons.isNotEmpty()) {
            logger.info("Found ${result.usedIcons.size} used icons in ${result.analyzedFiles} files")
        } else {
            logger.warn("No icons found! Check that the target classes are correct: $targetClassList")
        }

        if (result.errors.isNotEmpty()) {
            logger.warn("Analysis completed with ${result.errors.size} error(s)")
            result.errors.forEach { (file, error) ->
                logger.debug("Error in $file: $error")
            }
        }
    }
}