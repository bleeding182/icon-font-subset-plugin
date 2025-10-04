package com.davidmedenjak.fontsubsetting.plugin.tasks

import com.davidmedenjak.fontsubsetting.analyzer.KotlinAnalysisWorkerAction
import com.davidmedenjak.fontsubsetting.plugin.Constants
import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.SkipWhenEmpty
import org.gradle.api.tasks.TaskAction
import org.gradle.workers.WorkerExecutor
import javax.inject.Inject

/**
 * Task that analyzes Kotlin source files to find icon constant usage.
 *
 * This task uses the Gradle Workers API with classloader isolation to run the analysis,
 * preventing classloader conflicts with the Kotlin Gradle Plugin (KGP) as recommended
 * by Kotlin 2.1+.
 */
@CacheableTask
abstract class AnalyzeIconUsageTask : DefaultTask() {

    @get:Inject
    abstract val workerExecutor: WorkerExecutor

    @get:InputFiles
    @get:SkipWhenEmpty
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val sourceFiles: ConfigurableFileCollection

    @get:Input
    abstract val targetClasses: ListProperty<String>

    @get:OutputFile
    abstract val outputFile: RegularFileProperty

    /**
     * Classpath containing kotlin-compiler-embeddable for isolated execution.
     * This is configured by the plugin to prevent conflicts with KGP.
     */
    @get:Classpath
    abstract val kotlinCompilerClasspath: ConfigurableFileCollection

    init {
        group = Constants.PLUGIN_GROUP
        description = "Analyzes Kotlin source files to find icon constant usage"
    }

    @TaskAction
    fun analyzeUsage() {
        val targetClassList = targetClasses.get()
        logger.info("Analyzing icon usage for ${targetClassList.size} target class(es)")

        // Submit work to worker with isolated classloader
        val workQueue = workerExecutor.classLoaderIsolation { spec ->
            spec.classpath.from(kotlinCompilerClasspath)
        }

        workQueue.submit(KotlinAnalysisWorkerAction::class.java) { parameters ->
            parameters.sourceFiles.from(sourceFiles)
            parameters.targetClasses.set(targetClasses)
            parameters.outputFile.set(outputFile)
        }

        // Wait for the worker to complete
        workQueue.await()

        logger.info("Icon usage analysis completed")
    }
}