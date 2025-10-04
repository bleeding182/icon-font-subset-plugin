package com.davidmedenjak.fontsubsetting.plugin

import com.android.build.api.variant.AndroidComponentsExtension
import com.android.build.api.variant.Variant
import com.android.build.gradle.AppPlugin
import com.android.build.gradle.BaseExtension
import com.android.build.gradle.LibraryPlugin
import com.davidmedenjak.fontsubsetting.plugin.tasks.AnalyzeIconUsageTask
import com.davidmedenjak.fontsubsetting.plugin.tasks.FontSubsettingTask
import com.davidmedenjak.fontsubsetting.plugin.tasks.GenerateIconConstantsTask
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.TaskProvider

class FontSubsettingPlugin : Plugin<Project> {

    companion object {
        private const val KOTLIN_COMPILER_EMBEDDABLE = "org.jetbrains.kotlin:kotlin-compiler-embeddable"
        // Use the same version as the project's Kotlin version
        private const val KOTLIN_VERSION = "2.2.10"
    }

    override fun apply(project: Project) {
        val extension = project.extensions.create(
            "fontSubsetting",
            FontSubsettingExtension::class.java
        )

        extension.outputDirectory.convention(
            project.layout.buildDirectory.dir("generated/res/fontSubsetting")
        )

        // Configure isolated Kotlin compiler classpath for Workers API
        // This prevents classloader conflicts with KGP (Kotlin 2.1+ requirement)
        val kotlinCompilerClasspath = createKotlinCompilerConfiguration(project)

        project.plugins.withType(AppPlugin::class.java) {
            configureAndroidProject(project, extension, kotlinCompilerClasspath)
        }

        project.plugins.withType(LibraryPlugin::class.java) {
            configureAndroidProject(project, extension, kotlinCompilerClasspath)
        }
    }

    private fun createKotlinCompilerConfiguration(project: Project): org.gradle.api.file.FileCollection {
        // Create a detached configuration to avoid affecting other parts of the build
        val compilerDependencyScope = project.configurations.create("fontSubsettingKotlinCompiler") {
            it.isVisible = false
            it.isCanBeConsumed = false
            it.isCanBeResolved = false
        }

        project.dependencies.add(compilerDependencyScope.name, "$KOTLIN_COMPILER_EMBEDDABLE:$KOTLIN_VERSION")

        // Create resolvable configuration
        val resolvableConfiguration = project.configurations.create("fontSubsettingKotlinCompilerResolvable") {
            it.isVisible = false
            it.isCanBeConsumed = false
            it.isCanBeResolved = true
            it.extendsFrom(compilerDependencyScope)
        }

        return resolvableConfiguration
    }

    private fun configureAndroidProject(
        project: Project,
        extension: FontSubsettingExtension,
        kotlinCompilerClasspath: org.gradle.api.file.FileCollection
    ) {
        val androidComponents = project.extensions.getByType(AndroidComponentsExtension::class.java)

        androidComponents.onVariants { variant ->
            var firstSubsetTask: TaskProvider<FontSubsettingTask>? = null

            extension.fonts.configureEach { fontConfig ->
                val variantName = variant.name.replaceFirstChar { it.uppercase() }
                val fontName = fontConfig.name.replaceFirstChar { it.uppercase() }

                val generateTask =
                    registerGenerateTask(project, variant, fontConfig, variantName, fontName)
                val analyzeTask = registerAnalyzeTask(
                    project,
                    variant,
                    fontConfig,
                    generateTask,
                    variantName,
                    fontName,
                    kotlinCompilerClasspath
                )
                val subsetTask = registerSubsetTask(
                    project,
                    extension,
                    variant,
                    fontConfig,
                    analyzeTask,
                    variantName,
                    fontName
                )

                if (firstSubsetTask == null) {
                    firstSubsetTask = subsetTask
                    variant.sources.res?.addGeneratedSourceDirectory(
                        subsetTask,
                        FontSubsettingTask::outputDirectory
                    )
                }
            }
        }
    }

    private fun registerGenerateTask(
        project: Project,
        variant: Variant,
        fontConfig: FontConfiguration,
        variantName: String,
        fontName: String
    ): TaskProvider<GenerateIconConstantsTask> {
        val generateTask = project.tasks.register(
            "generate${variantName}${fontName}Icons",
            GenerateIconConstantsTask::class.java
        ) { task ->
            task.group = Constants.PLUGIN_GROUP
            task.description = "Generate icon constants for $fontName ($variantName)"
            task.codepointsFile.set(fontConfig.codepointsFile)
            task.fullyQualifiedClassName.set(fontConfig.className)

            val outputDir = project.layout.buildDirectory.dir(
                "generated/source/fontIcons/${variant.name}/kotlin"
            )
            task.outputDirectory.set(outputDir)
        }

        variant.sources.java?.addGeneratedSourceDirectory(
            generateTask,
            GenerateIconConstantsTask::outputDirectory
        )

        return generateTask
    }

    private fun registerAnalyzeTask(
        project: Project,
        variant: Variant,
        fontConfig: FontConfiguration,
        generateTask: TaskProvider<GenerateIconConstantsTask>,
        variantName: String,
        fontName: String,
        kotlinCompilerClasspath: org.gradle.api.file.FileCollection
    ): TaskProvider<AnalyzeIconUsageTask> {
        return project.tasks.register(
            "analyze${variantName}${fontName}Usage",
            AnalyzeIconUsageTask::class.java
        ) { task ->
            task.group = Constants.PLUGIN_GROUP
            task.description = "Analyze usage of $fontName icons ($variantName)"

            task.targetClasses.set(
                generateTask.flatMap { it.fullyQualifiedClassName }.map { listOf(it) }
            )

            // Configure isolated Kotlin compiler classpath
            task.kotlinCompilerClasspath.from(kotlinCompilerClasspath)

            configureSourceSets(project, task)

            task.sourceFiles.from(
                generateTask.map { genTask ->
                    project.fileTree(genTask.outputDirectory) {
                        it.include("**/*.kt")
                    }
                }
            )

            val outputFile = project.layout.buildDirectory.file(
                "fontSubsetting/usage_${variant.name}_${fontConfig.name}.txt"
            )
            task.outputFile.set(outputFile)
        }
    }

    private fun configureSourceSets(project: Project, task: AnalyzeIconUsageTask) {
        val android = project.extensions.findByType(BaseExtension::class.java)
        android?.sourceSets?.forEach { sourceSet ->
            sourceSet.java.srcDirs.forEach { dir ->
                if (dir.exists()) {
                    task.sourceFiles.from(
                        project.fileTree(dir) {
                            it.include("**/*.kt")
                        }
                    )
                }
            }
        }
    }

    private fun registerSubsetTask(
        project: Project,
        extension: FontSubsettingExtension,
        variant: Variant,
        fontConfig: FontConfiguration,
        analyzeTask: TaskProvider<AnalyzeIconUsageTask>,
        variantName: String,
        fontName: String
    ): TaskProvider<FontSubsettingTask> {
        return project.tasks.register(
            "subset${variantName}${fontName}Font",
            FontSubsettingTask::class.java
        ) { task ->
            task.group = Constants.PLUGIN_GROUP
            task.description = "Subset $fontName font ($variantName)"

            task.fontFile.set(fontConfig.fontFile)
            task.codepointsFile.set(fontConfig.codepointsFile)
            task.usageDataFile.set(analyzeTask.flatMap { it.outputFile })

            task.stripHinting.set(fontConfig.stripHinting.orElse(true))
            task.stripGlyphNames.set(fontConfig.stripGlyphNames.orElse(true))

            task.axes.set(createAxesProvider(project, fontConfig))

            val outputFileName = fontConfig.resourceName.map { name ->
                val originalExtension = fontConfig.fontFile.get().asFile.extension
                if (originalExtension.isNotEmpty()) "$name.$originalExtension" else name
            }.orElse(fontConfig.fontFile.map { it.asFile.name })

            task.outputDirectory.set(
                extension.outputDirectory.dir(variant.name)
            )
            task.outputFileName.set(outputFileName)
        }
    }

    private fun createAxesProvider(
        project: Project,
        fontConfig: FontConfiguration
    ): Provider<List<FontSubsettingTask.AxisConfig>> {
        return project.provider {
            fontConfig.axes.map { axis ->
                FontSubsettingTask.AxisConfig(
                    tag = axis.name,
                    remove = axis.remove.orElse(false).get(),
                    minValue = axis.minValue.orNull,
                    maxValue = axis.maxValue.orNull,
                    defaultValue = axis.defaultValue.orNull
                )
            }
        }
    }
}