package com.davidmedenjak.fontsubsetting.plugin

import com.davidmedenjak.fontsubsetting.native.HarfBuzzSubsetter
import org.gradle.api.DefaultTask
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import java.io.File

/**
 * Task that displays information about a font file.
 * Can be used standalone without the extension to inspect any font.
 */
abstract class FontInfoTask : DefaultTask() {

    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val fontFile: RegularFileProperty

    @get:Input
    @get:Optional
    abstract val outputFormat: Property<OutputFormat>

    @get:OutputFile
    @get:Optional
    abstract val outputFile: RegularFileProperty

    enum class OutputFormat {
        CONSOLE,
        JSON,
        TEXT
    }

    init {
        group = Constants.PLUGIN_GROUP
        description = "Displays information about a font file"
    }

    @TaskAction
    fun displayFontInfo() {
        val font = fontFile.get().asFile
        if (!font.exists()) {
            logger.error("Font file not found: ${font.absolutePath}")
            return
        }

        val subsetter = NativeSubsetterFactory(logger).getSubsetter()
        val fontInfo = subsetter.getFontInfoDetailed(font.absolutePath)

        if (fontInfo == null) {
            logger.error("Failed to read font information from: ${font.absolutePath}")
            return
        }

        val format = outputFormat.orElse(OutputFormat.CONSOLE).get()
        val output = when (format) {
            OutputFormat.JSON -> formatAsJson(font, fontInfo)
            OutputFormat.TEXT -> formatAsText(font, fontInfo)
            OutputFormat.CONSOLE -> {
                logToConsole(font, fontInfo)
                null
            }
        }

        output?.let { content ->
            val outFile = outputFile.orNull?.asFile
            if (outFile != null) {
                outFile.parentFile?.mkdirs()
                outFile.writeText(content)
                logger.lifecycle("Font info written to: ${outFile.absolutePath}")
            } else {
                println(content)
            }
        }
    }

    private fun logToConsole(font: File, fontInfo: HarfBuzzSubsetter.FontInfo) {
        logger.lifecycle("=" * 60)
        logger.lifecycle("Font Information: ${font.name}")
        logger.lifecycle("=" * 60)
        logger.lifecycle("  File: ${font.absolutePath}")
        logger.lifecycle("  Size: ${formatFileSize(fontInfo.fileSize)}")
        logger.lifecycle("  Glyphs: ${fontInfo.glyphCount}")
        logger.lifecycle("  Units per EM: ${fontInfo.unitsPerEm}")

        fontInfo.axes?.let { axes ->
            if (axes.isNotEmpty()) {
                logger.lifecycle("  Variable Font Axes: ${axes.size}")
                axes.forEach { axis ->
                    logger.lifecycle("    - ${axis.tag}: ${axis.minValue}..${axis.maxValue} (default: ${axis.defaultValue})")
                }
            }
        } ?: run {
            logger.lifecycle("  Type: Static font (no variable axes)")
        }
        logger.lifecycle("=" * 60)
    }

    private fun formatAsText(font: File, fontInfo: HarfBuzzSubsetter.FontInfo): String {
        return buildString {
            appendLine("Font Information Report")
            appendLine("=" * 60)
            appendLine("File: ${font.name}")
            appendLine("Path: ${font.absolutePath}")
            appendLine("Size: ${formatFileSize(fontInfo.fileSize)}")
            appendLine("Glyphs: ${fontInfo.glyphCount}")
            appendLine("Units per EM: ${fontInfo.unitsPerEm}")
            appendLine()

            fontInfo.axes?.let { axes ->
                if (axes.isNotEmpty()) {
                    appendLine("Variable Font Axes (${axes.size}):")
                    axes.forEach { axis ->
                        appendLine("  ${axis.tag}:")
                        appendLine("    Range: ${axis.minValue}..${axis.maxValue}")
                        appendLine("    Default: ${axis.defaultValue}")
                    }
                }
            } ?: appendLine("Type: Static font (no variable axes)")
        }
    }

    private fun formatAsJson(font: File, fontInfo: HarfBuzzSubsetter.FontInfo): String {
        val axes = fontInfo.axes?.map { axis ->
            """
            |    {
            |      "tag": "${axis.tag}",
            |      "minValue": ${axis.minValue},
            |      "maxValue": ${axis.maxValue},
            |      "defaultValue": ${axis.defaultValue}
            |    }""".trimMargin()
        }?.joinToString(",\n") ?: ""

        return """
        |{
        |  "file": "${font.name}",
        |  "path": "${font.absolutePath.replace("\\", "\\\\")}",
        |  "size": ${fontInfo.fileSize},
        |  "glyphCount": ${fontInfo.glyphCount},
        |  "unitsPerEm": ${fontInfo.unitsPerEm},
        |  "axes": [
        |${axes}
        |  ]
        |}
        """.trimMargin()
    }

    private fun formatFileSize(size: Long): String {
        return when {
            size < 1024 -> "$size B"
            size < 1024 * 1024 -> String.format("%.1f KB", size / 1024.0)
            else -> String.format("%.1f MB", size / (1024.0 * 1024.0))
        }
    }

    private operator fun String.times(count: Int): String = repeat(count)
}