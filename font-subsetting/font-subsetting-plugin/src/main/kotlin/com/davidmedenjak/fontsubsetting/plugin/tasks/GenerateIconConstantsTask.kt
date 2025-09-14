package com.davidmedenjak.fontsubsetting.plugin.tasks

import com.davidmedenjak.fontsubsetting.plugin.Constants
import com.davidmedenjak.fontsubsetting.plugin.providers.CodepointsFileProvider
import com.davidmedenjak.fontsubsetting.plugin.services.KotlinCodeGenerator
import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import java.io.File

@CacheableTask
abstract class GenerateIconConstantsTask : DefaultTask() {

    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val codepointsFile: RegularFileProperty

    @get:Input
    abstract val fullyQualifiedClassName: Property<String>

    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    init {
        group = Constants.PLUGIN_GROUP
        description = "Generates Kotlin constants from font codepoints file"
    }

    @TaskAction
    fun generate() {
        val codepointsFile = codepointsFile.get().asFile
        val fqcn = fullyQualifiedClassName.get()

        val lastDotIndex = fqcn.lastIndexOf('.')
        val packageName = if (lastDotIndex > 0) fqcn.substring(0, lastDotIndex) else ""
        val className = if (lastDotIndex > 0) fqcn.substring(lastDotIndex + 1) else fqcn

        val provider = CodepointsFileProvider(codepointsFile)
        val mappings = provider.provideMappings()

        val kotlinCode = KotlinCodeGenerator.generate(packageName, className, mappings)
        val outputDir = outputDirectory.get().asFile
        val packageDir = if (packageName.isNotEmpty()) {
            File(outputDir, packageName.replace('.', '/'))
        } else {
            outputDir
        }
        packageDir.mkdirs()

        val outputFile = File(packageDir, "$className.kt")
        outputFile.writeText(kotlinCode)

        logger.info("Generated ${mappings.size} icon constants to $className")
    }
}