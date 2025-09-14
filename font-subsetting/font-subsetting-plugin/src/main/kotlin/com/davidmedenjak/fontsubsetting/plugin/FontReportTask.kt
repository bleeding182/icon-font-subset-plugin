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
 * Task that generates a report comparing original and subsetted fonts.
 */
abstract class FontReportTask : DefaultTask() {

    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val originalFont: RegularFileProperty

    @get:InputFile
    @get:Optional
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val subsettedFont: RegularFileProperty

    @get:Input
    abstract val fontName: Property<String>

    @get:OutputFile
    @get:Optional
    abstract val outputFile: RegularFileProperty

    init {
        group = Constants.PLUGIN_GROUP
        description = "Generate a comparison report for font subsetting"
    }

    @TaskAction
    fun generateReport() {
        val original = originalFont.get().asFile
        val subsetted = subsettedFont.orNull?.asFile

        if (!original.exists()) {
            logger.error("Original font file not found: ${original.absolutePath}")
            return
        }

        val subsetter = NativeSubsetterFactory(logger).getSubsetter()
        val originalInfo = subsetter.getFontInfoDetailed(original.absolutePath)

        if (originalInfo == null) {
            logger.error("Failed to read original font information")
            return
        }

        val report = if (subsetted != null && subsetted.exists()) {
            val subsettedInfo = subsetter.getFontInfoDetailed(subsetted.absolutePath)
            if (subsettedInfo != null) {
                generateComparisonReport(original, originalInfo, subsetted, subsettedInfo)
            } else {
                generateSingleFontReport(original, originalInfo)
            }
        } else {
            generateSingleFontReport(original, originalInfo)
        }

        val outFile = outputFile.orNull?.asFile
        if (outFile != null) {
            outFile.parentFile?.mkdirs()
            outFile.writeText(report)
            logger.lifecycle("Report written to: ${outFile.absolutePath}")
        } else {
            logger.lifecycle(report)
        }
    }

    private fun generateComparisonReport(
        original: File,
        originalInfo: HarfBuzzSubsetter.FontInfo,
        subsetted: File,
        subsettedInfo: HarfBuzzSubsetter.FontInfo
    ): String {
        val originalSize = original.length()
        val subsettedSize = subsetted.length()
        val sizeReduction = if (originalSize > 0) {
            ((originalSize - subsettedSize) * 100.0 / originalSize)
        } else 0.0

        val glyphReduction = if (originalInfo.glyphCount > 0) {
            ((originalInfo.glyphCount - subsettedInfo.glyphCount) * 100.0 / originalInfo.glyphCount)
        } else 0.0

        return buildString {
            appendLine("=" * 70)
            appendLine("Font Subsetting Report: ${fontName.get()}")
            appendLine("=" * 70)
            appendLine()
            appendLine("ORIGINAL FONT")
            appendLine("-" * 40)
            appendLine("  File: ${original.name}")
            appendLine("  Size: ${formatFileSize(originalSize)}")
            appendLine("  Glyphs: ${originalInfo.glyphCount}")
            appendLine("  Units per EM: ${originalInfo.unitsPerEm}")

            originalInfo.axes?.let { axes ->
                if (axes.isNotEmpty()) {
                    appendLine("  Variable Axes: ${axes.size}")
                    axes.forEach { axis ->
                        appendLine("    ${axis.tag}: ${axis.minValue}..${axis.maxValue} (default: ${axis.defaultValue})")
                    }
                }
            }

            appendLine()
            appendLine("SUBSETTED FONT")
            appendLine("-" * 40)
            appendLine("  File: ${subsetted.name}")
            appendLine("  Size: ${formatFileSize(subsettedSize)}")
            appendLine("  Glyphs: ${subsettedInfo.glyphCount}")

            subsettedInfo.axes?.let { axes ->
                if (axes.isNotEmpty()) {
                    appendLine("  Variable Axes: ${axes.size}")
                    axes.forEach { axis ->
                        appendLine("    ${axis.tag}: ${axis.minValue}..${axis.maxValue} (default: ${axis.defaultValue})")
                    }
                }
            }

            appendLine()
            appendLine("OPTIMIZATION RESULTS")
            appendLine("-" * 40)
            appendLine("  Size Reduction: ${formatFileSize(originalSize - subsettedSize)} (${String.format("%.1f", sizeReduction)}%)")
            appendLine("  Glyphs Removed: ${originalInfo.glyphCount - subsettedInfo.glyphCount} (${String.format("%.1f", glyphReduction)}%)")
            appendLine("  Glyphs Retained: ${subsettedInfo.glyphCount} of ${originalInfo.glyphCount}")

            val axesRemoved = (originalInfo.axes?.size ?: 0) - (subsettedInfo.axes?.size ?: 0)
            if (axesRemoved > 0) {
                appendLine("  Axes Removed: $axesRemoved")
            }

            appendLine()
            appendLine("=" * 70)
        }
    }

    private fun generateSingleFontReport(font: File, fontInfo: HarfBuzzSubsetter.FontInfo): String {
        return buildString {
            appendLine("=" * 70)
            appendLine("Font Information Report: ${fontName.get()}")
            appendLine("=" * 70)
            appendLine()
            appendLine("  File: ${font.name}")
            appendLine("  Path: ${font.absolutePath}")
            appendLine("  Size: ${formatFileSize(font.length())}")
            appendLine("  Glyphs: ${fontInfo.glyphCount}")
            appendLine("  Units per EM: ${fontInfo.unitsPerEm}")

            fontInfo.axes?.let { axes ->
                if (axes.isNotEmpty()) {
                    appendLine()
                    appendLine("  Variable Font Axes (${axes.size}):")
                    axes.forEach { axis ->
                        appendLine("    ${axis.tag}:")
                        appendLine("      Range: ${axis.minValue}..${axis.maxValue}")
                        appendLine("      Default: ${axis.defaultValue}")
                    }
                }
            } ?: appendLine("  Type: Static font (no variable axes)")

            appendLine()
            appendLine("=" * 70)
        }
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