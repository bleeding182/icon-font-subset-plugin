package com.davidmedenjak.fontsubsetting.analyzer

import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.workers.WorkAction
import org.gradle.workers.WorkParameters
import java.util.logging.Handler
import java.util.logging.Level
import java.util.logging.LogRecord
import java.util.logging.Logger

internal abstract class KotlinAnalysisWorkerAction : WorkAction<KotlinAnalysisWorkerAction.Parameters> {

    interface Parameters : WorkParameters {
        val sourceFiles: ConfigurableFileCollection
        val targetClasses: ListProperty<String>
        val outputFile: RegularFileProperty
    }

    override fun execute() {
        val targetClassList = parameters.targetClasses.get()
        val sourceFilesList = parameters.sourceFiles.files.toList()
        val outputFile = parameters.outputFile.get().asFile

        val logger = Logger.getLogger(this::class.java.name).apply {
            addHandler(object : Handler() {
                override fun publish(record: LogRecord) {
                    val message = "[KotlinAnalysisWorker] ${record.message}"
                    when (record.level) {
                        Level.SEVERE -> System.err.println("ERROR: $message")
                        Level.WARNING -> System.err.println("WARN: $message")
                        else -> println("INFO: $message")
                    }
                }

                override fun flush() {}
                override fun close() {}
            })
        }

        logger.info("Analyzing icon usage for ${targetClassList.size} target class(es)")
        logger.info("Analyzing ${sourceFilesList.size} source files")

        val analyzer = KotlinIconUsageAnalyzer(
            targetClasses = targetClassList,
            logger = logger
        )

        val result = analyzer.analyze(
            sourceFiles = sourceFilesList,
            additionalSourceDirs = emptyList()
        )

        result.writeToFile(outputFile)

        if (result.usedIcons.isNotEmpty()) {
            logger.info("Found ${result.usedIcons.size} used icons in ${result.analyzedFiles} files")
        } else {
            logger.warning("No icons found! Check that the target classes are correct: $targetClassList")
        }

        if (result.errors.isNotEmpty()) {
            logger.warning("Analysis completed with ${result.errors.size} error(s)")
            result.errors.forEach { (file, error) ->
                logger.fine("Error in $file: $error")
            }
        }
    }
}
