package com.davidmedenjak.fontsubsetting.plugin.tasks

import com.davidmedenjak.fontsubsetting.analyzer.IconUsageResult
import com.davidmedenjak.fontsubsetting.native.HarfBuzzSubsetter
import com.davidmedenjak.fontsubsetting.plugin.Constants
import com.davidmedenjak.fontsubsetting.plugin.NativeSubsetterFactory
import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import java.io.File
import java.io.Serializable

@CacheableTask
abstract class FontSubsettingTask : DefaultTask() {

    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val fontFile: RegularFileProperty

    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val codepointsFile: RegularFileProperty

    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val usageDataFile: RegularFileProperty

    @get:Input
    abstract val stripHinting: Property<Boolean>

    @get:Input
    abstract val stripGlyphNames: Property<Boolean>

    @get:Input
    abstract val axes: ListProperty<AxisConfig>

    @get:Input
    abstract val outputFileName: Property<String>

    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty


    init {
        group = Constants.PLUGIN_GROUP
        description = "Subsets font based on icon usage"
    }

    @TaskAction
    fun subsetFont() {
        val fontFile = fontFile.get().asFile
        val codepointsFile = codepointsFile.get().asFile
        val usageFile = usageDataFile.get().asFile
        val outputFile = prepareOutputFile()

        if (!validateInputFiles(fontFile, codepointsFile)) {
            return
        }

        val usedIcons = loadUsedIcons(usageFile)
        if (usedIcons.isEmpty()) {
            copyFontWithoutSubsetting(fontFile, outputFile, "no icons used")
            return
        }

        val codepoints = loadCodepoints(codepointsFile, usedIcons)
        if (codepoints.isEmpty()) {
            logger.warn("No matching codepoints found")
            copyFontWithoutSubsetting(fontFile, outputFile, "no matching codepoints")
            return
        }

        performSubsetting(fontFile, outputFile, codepoints)
    }

    private fun prepareOutputFile(): File {
        val outputDir = outputDirectory.get().asFile
        val fontDir = File(outputDir, "font")
        fontDir.mkdirs()
        return File(fontDir, outputFileName.get())
    }

    private fun validateInputFiles(fontFile: File, codepointsFile: File): Boolean {
        if (!fontFile.exists()) {
            logger.error("Font file not found: ${fontFile.absolutePath}")
            return false
        }
        if (!codepointsFile.exists()) {
            logger.error("Codepoints file not found: ${codepointsFile.absolutePath}")
            return false
        }
        return true
    }

    private fun loadUsedIcons(usageFile: File): Set<String> {
        return if (usageFile.exists()) {
            IconUsageResult.Companion.readFromFile(usageFile).usedIcons
        } else {
            logger.warn("No usage data found, copying font without subsetting")
            emptySet()
        }
    }

    private fun copyFontWithoutSubsetting(fontFile: File, outputFile: File, reason: String) {
        outputFile.parentFile?.mkdirs()
        fontFile.copyTo(outputFile, overwrite = true)
        logger.lifecycle("Copied font without subsetting ($reason)")
    }

    private fun performSubsetting(fontFile: File, outputFile: File, codepoints: Set<String>) {
        try {
            val subsetter = NativeSubsetterFactory(logger).getSubsetter()
            val axisConfigs = convertAxisConfigs()

            subsetter.subsetFontWithAxesAndFlags(
                inputFontPath = fontFile.absolutePath,
                outputFontPath = outputFile.absolutePath,
                glyphs = codepoints.toTypedArray(),
                axisConfigs = axisConfigs,
                stripHinting = stripHinting.get(),
                stripGlyphNames = stripGlyphNames.get()
            )

            logSubsettingResults(fontFile, outputFile, codepoints.size)
        } catch (e: Exception) {
            logger.error("Failed to subset font: ${e.message}")
            fontFile.copyTo(outputFile, overwrite = true)
        }
    }

    private fun convertAxisConfigs(): List<HarfBuzzSubsetter.AxisConfig> {
        return axes.get().map { axis ->
            HarfBuzzSubsetter.AxisConfig(
                tag = axis.tag,
                minValue = axis.minValue ?: 0f,
                maxValue = axis.maxValue ?: 0f,
                defaultValue = axis.defaultValue ?: 0f,
                remove = axis.remove
            )
        }
    }

    private fun logSubsettingResults(original: File, subsetted: File, glyphCount: Int) {
        val originalSize = original.length()
        val subsettedSize = subsetted.length()
        val reduction = ((originalSize - subsettedSize) * 100.0 / originalSize).toInt()

        logger.lifecycle(
            "Subsetted font: $glyphCount glyphs, " +
            "${subsettedSize / 1024}KB (${reduction}% reduction)"
        )
    }

    private fun loadCodepoints(codepointsFile: File, usedIcons: Set<String>): Set<String> {
        val codepoints = mutableSetOf<String>()
        val iconNameVariants = createIconNameVariants(usedIcons)

        codepointsFile.readLines().forEach { line ->
            val parts = line.split(' ', limit = 2)
            if (parts.size >= 2) {
                val iconNameInFile = parts[0]
                val codepointHex = parts[1]

                if (iconNameVariants.containsKey(iconNameInFile)) {
                    val codepointDecimal = codepointHex.toInt(16).toString()
                    codepoints.add(codepointDecimal)
                }
            }
        }

        return codepoints
    }

    private fun createIconNameVariants(usedIconNames: Set<String>): Map<String, String> {
        val iconNameVariants = mutableMapOf<String, String>()
        usedIconNames.forEach { name ->
            iconNameVariants[name] = name
            val snakeCase = name.replace(Regex("([a-z])([A-Z])")) {
                "${it.groupValues[1]}_${it.groupValues[2].lowercase()}"
            }
            if (snakeCase != name) {
                iconNameVariants[snakeCase] = name
            }
        }
        return iconNameVariants
    }

    data class AxisConfig(
        val tag: String,
        val remove: Boolean,
        val minValue: Float?,
        val maxValue: Float?,
        val defaultValue: Float?
    ) : Serializable
}