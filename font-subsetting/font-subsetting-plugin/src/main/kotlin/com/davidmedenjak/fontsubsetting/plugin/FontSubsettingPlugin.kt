package com.davidmedenjak.fontsubsetting.plugin

import com.android.build.api.variant.AndroidComponentsExtension
import com.android.build.api.variant.Variant
import com.android.build.gradle.AppPlugin
import com.android.build.gradle.LibraryPlugin
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.TaskProvider

class FontSubsettingPlugin : Plugin<Project> {
    
    override fun apply(project: Project) {
        val extension = project.extensions.create(
            "fontSubsetting",
            FontSubsettingExtension::class.java
        )
        extension.setDefaults()
        
        project.plugins.withType(AppPlugin::class.java) {
            configureAndroidProject(project, extension)
        }
        
        project.plugins.withType(LibraryPlugin::class.java) {
            configureAndroidProject(project, extension)
        }
    }
    
    private fun configureAndroidProject(project: Project, extension: FontSubsettingExtension) {
        val androidComponents = project.extensions.getByType(AndroidComponentsExtension::class.java)
        
        androidComponents.onVariants { variant ->
            configureVariant(project, extension, variant)
        }
    }
    
    private fun configureVariant(project: Project, extension: FontSubsettingExtension, variant: Variant) {
        val variantName = variant.name.replaceFirstChar { it.uppercase() }

        val generationTasks = registerGenerationTasks(project, extension, variant, variantName)
        val analysisTask = registerAnalysisTask(project, extension, variant, variantName)

        // Register per-font tasks
        val fontTasks = registerPerFontTasks(project, extension, variant, variantName)

        // Keep the aggregated subsetting task for backwards compatibility
        val subsettingTask = registerSubsettingTask(project, extension, variant, variantName)

        configureSources(variant, generationTasks, subsettingTask)
        configureTaskDependencies(project, variant, variantName, generationTasks, analysisTask, subsettingTask)
        configurePerFontTaskDependencies(project, fontTasks, analysisTask, subsettingTask)
    }
    
    private fun registerGenerationTasks(
        project: Project,
        extension: FontSubsettingExtension,
        variant: Variant,
        variantName: String
    ): List<TaskProvider<GenerateIconConstantsTask>> {
        return extension.fonts.map { fontConfig ->
            val taskName = buildGenerationTaskName(variantName, fontConfig.name)
            
            project.tasks.register(taskName, GenerateIconConstantsTask::class.java) { task ->
                task.group = Constants.PLUGIN_GROUP
                task.description = "Generates icon constants for ${fontConfig.name} in ${variant.name} variant"
                
                configureFontGenerationTask(task, fontConfig)
                
                val outputDir = project.layout.buildDirectory.dir(
                    "${Constants.Directories.GENERATED_SOURCE}/${variant.name}/kotlin"
                )
                task.outputDirectory.set(outputDir)
            }
        }
    }
    
    private fun configureFontGenerationTask(task: GenerateIconConstantsTask, fontConfig: FontConfiguration) {
        task.codepointsFile.set(fontConfig.codepointsFile)
        task.packageName.set(fontConfig.packageName)
        task.className.set(fontConfig.className)
        
        task.resourceName.set(
            fontConfig.resourceName.orElse(fontConfig.getDefaultResourceName())
        )
        
        task.fontFileName.set(
            fontConfig.fontFileName.orElse(fontConfig.getDefaultFontFileName())
        )
        
        task.generateInternal.set(true)
    }
    
    private fun registerAnalysisTask(
        project: Project,
        extension: FontSubsettingExtension,
        variant: Variant,
        variantName: String
    ): TaskProvider<AnalyzeIconUsageTask> {
        val taskName = buildAnalysisTaskName(variantName)
        
        return project.tasks.register(taskName, AnalyzeIconUsageTask::class.java) { task ->
            task.group = Constants.PLUGIN_GROUP
            task.description = "Analyzes icon usage in ${variant.name} variant"
            
            configureSourceFiles(project, task, variant)
            
            val targetClasses = extension.fonts.map { fontConfig ->
                "${fontConfig.packageName.get()}.${fontConfig.className.get()}"
            }
            task.targetClasses.set(targetClasses)
            
            val outputFile = project.layout.buildDirectory.file(
                "${Constants.Directories.BUILD_OUTPUT}/${Constants.FileNames.USAGE_DATA_PREFIX}${variant.name}${Constants.FileNames.USAGE_DATA_EXTENSION}"
            )
            task.outputFile.set(outputFile)
        }
    }
    
    private fun configureSourceFiles(project: Project, task: AnalyzeIconUsageTask, variant: Variant) {
        val sourceDirs = listOf(
            project.file("src/main/java"),
            project.file("src/main/kotlin"),
            project.file("src/${variant.name}/java"),
            project.file("src/${variant.name}/kotlin")
        ).filter { it.exists() }
        
        val sourceFiles = project.files()
        sourceDirs.forEach { dir ->
            sourceFiles.from(project.fileTree(dir) {
                it.include("**/*.kt")
            })
        }
        task.sourceFiles.setFrom(sourceFiles)
        
        val generatedDir = project.layout.buildDirectory
            .dir("${Constants.Directories.GENERATED_SOURCE}/${variant.name}/kotlin")
            .get().asFile
        task.generatedSourceDirs = project.files(generatedDir)
    }
    
    private fun registerSubsettingTask(
        project: Project,
        extension: FontSubsettingExtension,
        variant: Variant,
        variantName: String
    ): TaskProvider<FontSubsettingTask> {
        val taskName = buildSubsettingTaskName(variantName)
        
        return project.tasks.register(taskName, FontSubsettingTask::class.java) { task ->
            task.group = Constants.PLUGIN_GROUP
            task.description = "Subsets fonts for ${variant.name} variant"
            
            val outputDir = project.layout.buildDirectory.dir(
                "${Constants.Directories.INTERMEDIATES_FONT}/${variant.name}/res"
            )
            task.outputDirectory.set(outputDir)
            
            task.fontConfigurations = extension.fonts.toList()
            
            // Set up serializable input for proper up-to-date checking
            task.fontConfigurationInputs = extension.fonts.map { config ->
                SerializableFontConfig(
                    name = config.name,
                    fontFilePath = config.fontFile.get().asFile.absolutePath,
                    codepointsFilePath = config.codepointsFile.get().asFile.absolutePath,
                    packageName = config.packageName.get(),
                    className = config.className.get(),
                    resourceName = config.resourceName.orNull,
                    fontFileName = config.fontFileName.orNull,
                    axes = config.axes.map { axis ->
                        SerializableAxisConfig(
                            tag = axis.name,
                            remove = axis.remove.orNull ?: false,
                            minValue = axis.minValue.orNull,
                            maxValue = axis.maxValue.orNull,
                            defaultValue = axis.defaultValue.orNull
                        )
                    },
                    stripHinting = config.stripHinting.orElse(true).get(),
                    stripGlyphNames = config.stripGlyphNames.orElse(true).get()
                )
            }
            
            val fontFiles = extension.fonts.mapNotNull { fontConfig ->
                fontConfig.fontFile.orNull?.asFile
            }
            if (fontFiles.isNotEmpty()) {
                task.fontFiles = project.files(fontFiles)
            }
            
            task.buildDirectory.set(project.layout.buildDirectory)
            
            val usageDataFile = project.layout.buildDirectory.file(
                "${Constants.Directories.BUILD_OUTPUT}/${Constants.FileNames.USAGE_DATA_PREFIX}${variant.name}${Constants.FileNames.USAGE_DATA_EXTENSION}"
            )
            task.usageDataFile.set(usageDataFile)
        }
    }
    
    private fun configureSources(
        variant: Variant,
        generationTasks: List<TaskProvider<GenerateIconConstantsTask>>,
        subsettingTask: TaskProvider<FontSubsettingTask>
    ) {
        // Register generated Kotlin sources from each generation task
        generationTasks.forEach { generationTask ->
            variant.sources.java?.addGeneratedSourceDirectory(
                generationTask,
                GenerateIconConstantsTask::outputDirectory
            )
        }
        
        // Use addGeneratedSourceDirectory for resources generated by the FontSubsettingTask
        // This properly registers the resources as task outputs
        variant.sources.res?.addGeneratedSourceDirectory(
            subsettingTask,
            FontSubsettingTask::outputDirectory
        )
    }
    
    private fun configureTaskDependencies(
        project: Project,
        variant: Variant,
        variantName: String,
        generationTasks: List<TaskProvider<GenerateIconConstantsTask>>,
        analysisTask: TaskProvider<AnalyzeIconUsageTask>,
        subsettingTask: TaskProvider<FontSubsettingTask>
    ) {
        project.afterEvaluate {
            configureGenerationTaskDependencies(project, variant, variantName, generationTasks)
            configureAnalysisTaskDependencies(analysisTask, generationTasks)
            configureSubsettingTaskDependencies(subsettingTask, analysisTask)
            configureResourceTaskDependencies(project, variantName, subsettingTask)
        }
    }
    
    private fun configureGenerationTaskDependencies(
        project: Project,
        variant: Variant,
        variantName: String,
        generationTasks: List<TaskProvider<GenerateIconConstantsTask>>
    ) {
        // The addGeneratedSourceDirectory call in configureSources already handles
        // the dependency and source directory registration
        // We don't need to manually add dependencies or sources here
    }
    
    private fun configureAnalysisTaskDependencies(
        analysisTask: TaskProvider<AnalyzeIconUsageTask>,
        generationTasks: List<TaskProvider<GenerateIconConstantsTask>>
    ) {
        analysisTask.configure { task ->
            generationTasks.forEach { generateTask ->
                task.dependsOn(generateTask)
            }
        }
    }
    
    private fun configureSubsettingTaskDependencies(
        subsettingTask: TaskProvider<FontSubsettingTask>,
        analysisTask: TaskProvider<AnalyzeIconUsageTask>
    ) {
        subsettingTask.configure {
            it.dependsOn(analysisTask)
        }
    }
    
    private fun configureResourceTaskDependencies(
        project: Project,
        variantName: String,
        subsettingTask: TaskProvider<FontSubsettingTask>
    ) {
        listOf(
            "generate${variantName}Resources",
            "merge${variantName}Resources"
        ).forEach { taskName ->
            project.tasks.findByName(taskName)?.dependsOn(subsettingTask)
        }
        
        project.tasks.findByName("package${variantName}Resources")?.mustRunAfter(subsettingTask)
    }
    
    private fun buildGenerationTaskName(variantName: String, fontName: String): String {
        return "${Constants.TaskNames.GENERATE_ICONS_PREFIX}${variantName}${fontName.replaceFirstChar { it.uppercase() }}${Constants.TaskNames.GENERATE_ICONS_SUFFIX}"
    }
    
    private fun buildAnalysisTaskName(variantName: String): String {
        return "${Constants.TaskNames.ANALYZE_USAGE_PREFIX}${variantName}${Constants.TaskNames.ANALYZE_USAGE_SUFFIX}"
    }
    
    private fun buildSubsettingTaskName(variantName: String): String {
        return "${Constants.TaskNames.SUBSET_FONTS_PREFIX}${variantName}${Constants.TaskNames.SUBSET_FONTS_SUFFIX}"
    }

    /**
     * Data class to hold per-font task providers
     */
    private data class PerFontTasks(
        val fontName: String,
        val infoTask: TaskProvider<FontInfoTask>,
        val subsetTask: TaskProvider<FontSubsetTask>,
        val reportTask: TaskProvider<FontReportTask>
    )

    /**
     * Registers individual tasks for each font configuration
     */
    private fun registerPerFontTasks(
        project: Project,
        extension: FontSubsettingExtension,
        variant: Variant,
        variantName: String
    ): List<PerFontTasks> {
        return extension.fonts.map { fontConfig ->
            val fontNameCapitalized = fontConfig.name.replaceFirstChar { it.uppercase() }

            // Register font info task
            val infoTaskName = "font${variantName}${fontNameCapitalized}Info"
            val infoTask = project.tasks.register(infoTaskName, FontInfoTask::class.java) { task ->
                task.group = Constants.PLUGIN_GROUP
                task.description = "Display information about ${fontConfig.name} font for ${variant.name} variant"
                task.fontFile.set(fontConfig.fontFile)
                task.outputFormat.set(FontInfoTask.OutputFormat.CONSOLE)
            }

            // Register individual subset task
            val subsetTaskName = "subset${variantName}${fontNameCapitalized}Font"
            val subsetTask = project.tasks.register(subsetTaskName, FontSubsetTask::class.java) { task ->
                task.group = Constants.PLUGIN_GROUP
                task.description = "Subset ${fontConfig.name} font for ${variant.name} variant"

                task.inputFont.set(fontConfig.fontFile)

                val outputDir = project.layout.buildDirectory.dir(
                    "${Constants.Directories.INTERMEDIATES_FONT}/${variant.name}/res/font"
                )
                val outputFileName = fontConfig.fontFileName.orElse(
                    fontConfig.getDefaultFontFileName()
                ).get()
                task.outputFont.set(outputDir.map { it.file(outputFileName) })

                task.codepointsFile.set(fontConfig.codepointsFile)
                task.stripHinting.set(fontConfig.stripHinting.orElse(true))
                task.stripGlyphNames.set(fontConfig.stripGlyphNames.orElse(true))
                task.buildDirectory.set(project.layout.buildDirectory)

                // Convert axes configuration
                val axisSpecs = fontConfig.axes.map { axis ->
                    FontSubsetTask.AxisSpec(
                        tag = axis.name,
                        minValue = axis.minValue.orNull,
                        maxValue = axis.maxValue.orNull,
                        defaultValue = axis.defaultValue.orNull,
                        remove = axis.remove.orElse(false).get()
                    )
                }
                task.axes.set(axisSpecs)
            }

            // Register font report task
            val reportTaskName = "font${variantName}${fontNameCapitalized}Report"
            val reportTask = project.tasks.register(reportTaskName, FontReportTask::class.java) { task ->
                task.group = Constants.PLUGIN_GROUP
                task.description = "Generate subsetting report for ${fontConfig.name} font in ${variant.name} variant"

                task.originalFont.set(fontConfig.fontFile)
                task.subsettedFont.set(subsetTask.flatMap { it.outputFont })
                task.fontName.set(fontConfig.name)
            }

            PerFontTasks(fontConfig.name, infoTask, subsetTask, reportTask)
        }
    }

    /**
     * Configure dependencies for per-font tasks
     */
    private fun configurePerFontTaskDependencies(
        project: Project,
        fontTasks: List<PerFontTasks>,
        analysisTask: TaskProvider<AnalyzeIconUsageTask>,
        aggregatedSubsettingTask: TaskProvider<FontSubsettingTask>
    ) {
        project.afterEvaluate {
            // Each subset task depends on analysis task
            fontTasks.forEach { tasks ->
                tasks.subsetTask.configure { task ->
                    task.dependsOn(analysisTask)

                    // Use the new provider-based approach to set glyphs
                    val usageDataFile = analysisTask.flatMap { it.outputFile }
                    val codepointsFile = task.codepointsFile // Already set from fontConfig

                    // Wire the providers properly - no doFirst needed!
                    task.setGlyphsFromUsageData(usageDataFile, codepointsFile)
                }

                // Report task depends on subset task
                tasks.reportTask.configure { task ->
                    task.dependsOn(tasks.subsetTask)
                }

                // Aggregated task depends on individual subset tasks
                aggregatedSubsettingTask.configure { task ->
                    task.dependsOn(tasks.subsetTask)
                }
            }
        }
    }
}