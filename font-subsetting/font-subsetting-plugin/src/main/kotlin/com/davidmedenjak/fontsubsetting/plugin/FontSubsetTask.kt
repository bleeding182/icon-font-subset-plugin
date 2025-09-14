package com.davidmedenjak.fontsubsetting.plugin

import com.davidmedenjak.fontsubsetting.native.HarfBuzzSubsetter
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFile
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import java.io.File
import java.security.MessageDigest

/**
 * Standalone task for subsetting a single font file.
 * Can be used independently without the extension.
 */
@CacheableTask
abstract class FontSubsetTask : DefaultTask() {

    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val inputFont: RegularFileProperty

    @get:OutputFile
    abstract val outputFont: RegularFileProperty

    @get:InputFile
    @get:Optional
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val codepointsFile: RegularFileProperty

    @get:InputFile
    @get:Optional
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val glyphsFile: RegularFileProperty

    @get:Input
    @get:Optional
    abstract val glyphs: ListProperty<String>

    @get:Input
    @get:Optional
    abstract val stripHinting: Property<Boolean>

    @get:Input
    @get:Optional
    abstract val stripGlyphNames: Property<Boolean>

    @get:Input
    @get:Optional
    abstract val axes: ListProperty<AxisSpec>

    @get:Internal
    abstract val buildDirectory: DirectoryProperty

    @get:Input
    @get:Optional
    abstract val enableCache: Property<Boolean>

    @get:Input
    @get:Optional
    abstract val reportProgress: Property<Boolean>

    data class AxisSpec(
        val tag: String,
        val minValue: Float? = null,
        val maxValue: Float? = null,
        val defaultValue: Float? = null,
        val remove: Boolean = false
    ) : java.io.Serializable

    init {
        group = Constants.PLUGIN_GROUP
        description = "Subsets a font file to include only specified glyphs"
    }

    /**
     * Sets glyphs from usage data file and codepoints file using providers.
     * This allows proper task wiring without execution-time hacks.
     */
    fun setGlyphsFromUsageData(
        usageDataFile: Provider<RegularFile>,
        codepointsFile: Provider<RegularFile>
    ) {
        val glyphsProvider = usageDataFile.zip(codepointsFile) { usage, codepoints ->
            extractGlyphsFromUsageData(usage.asFile, codepoints.asFile)
        }
        this.glyphs.set(glyphsProvider)
    }

    private fun extractGlyphsFromUsageData(usageFile: File, codepointsFile: File): List<String> {
        if (!usageFile.exists() || !codepointsFile.exists()) {
            return emptyList()
        }

        val usageJson = usageFile.readText()
        val usedIcons = parseUsageData(usageJson)

        if (usedIcons.isEmpty()) {
            return emptyList()
        }

        val glyphsList = mutableListOf<String>()
        val iconNameVariants = createIconNameVariants(usedIcons)

        codepointsFile.readLines().forEach { line ->
            val parts = line.split(' ', limit = 2)
            if (parts.size >= 2) {
                val iconNameInFile = parts[0]
                val codepointHex = parts[1]

                if (iconNameVariants.containsKey(iconNameInFile)) {
                    val codepointEscaped = "\\u${codepointHex.padStart(4, '0').uppercase()}"
                    glyphsList.add(codepointEscaped)
                }
            }
        }

        return glyphsList
    }

    private fun parseUsageData(json: String): Set<String> {
        // Simple JSON parsing for {"usedIcons": ["icon1", "icon2", ...]}
        val regex = "\"usedIcons\"\\s*:\\s*\\[([^\\]]*)\\]".toRegex()
        val match = regex.find(json) ?: return emptySet()

        val iconsContent = match.groupValues[1]
        if (iconsContent.isBlank()) return emptySet()

        // Extract quoted strings
        val iconRegex = "\"([^\"]*)\"".toRegex()
        return iconRegex.findAll(iconsContent)
            .map { it.groupValues[1] }
            .toSet()
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

    @TaskAction
    fun subsetFont() {
        val input = inputFont.get().asFile
        val output = outputFont.get().asFile

        if (!input.exists()) {
            throw GradleException("Input font file does not exist: ${input.absolutePath}")
        }

        val glyphList = collectGlyphs()
        if (glyphList.isEmpty()) {
            logger.warn("No glyphs specified for subsetting. Copying font as-is.")
            input.copyTo(output, overwrite = true)
            return
        }

        val axisConfigs = convertAxisSpecs()
        val shouldStripHinting = stripHinting.orElse(true).get()
        val shouldStripGlyphNames = stripGlyphNames.orElse(true).get()
        val shouldReportProgress = reportProgress.orElse(true).get()

        if (enableCache.orElse(true).get()) {
            val cachedFile = getCachedFont(input.name, glyphList, axisConfigs, shouldStripHinting, shouldStripGlyphNames)
            if (cachedFile?.exists() == true) {
                cachedFile.copyTo(output, overwrite = true)
                if (shouldReportProgress) {
                    logger.lifecycle("Using cached subset for ${input.name}")
                }
                return
            }
        }

        performSubsetting(
            input,
            output,
            glyphList,
            axisConfigs,
            shouldStripHinting,
            shouldStripGlyphNames,
            shouldReportProgress
        )

        if (enableCache.orElse(true).get()) {
            cacheSubsettedFont(input.name, output, glyphList, axisConfigs, shouldStripHinting, shouldStripGlyphNames)
        }
    }

    private fun collectGlyphs(): Set<String> {
        val glyphSet = mutableSetOf<String>()

        // From direct glyphs property
        glyphs.orNull?.forEach { glyphSet.add(it) }

        // From glyphs file (one glyph per line or Unicode escapes)
        glyphsFile.orNull?.asFile?.let { file ->
            if (file.exists()) {
                file.readLines().forEach { line ->
                    line.trim().takeIf { it.isNotEmpty() }?.let { glyphSet.add(it) }
                }
            }
        }

        // From codepoints file (format: iconName hexCode)
        codepointsFile.orNull?.asFile?.let { file ->
            if (file.exists()) {
                file.readLines().forEach { line ->
                    val parts = line.split(' ', limit = 2)
                    if (parts.size >= 2) {
                        val codepointHex = parts[1]
                        val codepointEscaped = "\\u${codepointHex.padStart(4, '0').uppercase()}"
                        glyphSet.add(codepointEscaped)
                    }
                }
            }
        }

        return glyphSet
    }

    private fun convertAxisSpecs(): List<HarfBuzzSubsetter.AxisConfig> {
        return axes.orNull?.mapNotNull { spec ->
            when {
                spec.remove -> {
                    HarfBuzzSubsetter.AxisConfig(
                        tag = spec.tag,
                        remove = true
                    )
                }
                spec.minValue != null && spec.maxValue != null -> {
                    HarfBuzzSubsetter.AxisConfig(
                        tag = spec.tag,
                        minValue = spec.minValue,
                        maxValue = spec.maxValue,
                        defaultValue = spec.defaultValue ?: 0f,
                        remove = false
                    )
                }
                else -> {
                    logger.warn("Axis ${spec.tag} has incomplete configuration, skipping")
                    null
                }
            }
        } ?: emptyList()
    }

    private fun performSubsetting(
        input: File,
        output: File,
        glyphs: Set<String>,
        axisConfigs: List<HarfBuzzSubsetter.AxisConfig>,
        stripHinting: Boolean,
        stripGlyphNames: Boolean,
        reportProgress: Boolean
    ) {
        output.parentFile?.mkdirs()

        try {
            val subsetter = NativeSubsetterFactory(logger).getSubsetter()
            val result = subsetter.subset(
                input,
                output,
                glyphs.toList(),
                axisConfigs,
                stripHinting,
                stripGlyphNames
            )

            result.onSuccess { subsetResult ->
                if (reportProgress) {
                    logSubsettingResult(input, subsetResult)
                }
            }.onFailure { error ->
                throw GradleException("Font subsetting failed: ${error.message}", error)
            }
        } catch (e: Exception) {
            throw GradleException("Font subsetting failed: ${e.message}", e)
        }
    }

    private fun logSubsettingResult(input: File, result: HarfBuzzSubsetter.SubsetResult) {
        val originalSize = input.length()
        val finalSize = result.outputFile.length()
        val reduction = if (originalSize > 0) {
            ((originalSize - finalSize) * 100.0 / originalSize)
        } else 0.0

        logger.lifecycle("Subset ${input.name}: ${result.originalInfo.glyphCount} → ${result.finalInfo.glyphCount} glyphs")
        logger.lifecycle("  Size: ${formatFileSize(originalSize)} → ${formatFileSize(finalSize)} (-${String.format("%.1f", reduction)}%)")
    }

    private fun getCachedFont(
        fontName: String,
        glyphs: Set<String>,
        axisConfigs: List<HarfBuzzSubsetter.AxisConfig>,
        stripHinting: Boolean,
        stripGlyphNames: Boolean
    ): File? {
        val buildDir = buildDirectory.orNull?.asFile ?: return null
        val cacheKey = generateCacheKey(fontName, glyphs, axisConfigs, stripHinting, stripGlyphNames)
        val cacheFile = File(buildDir, "${Constants.Directories.BUILD_CACHE}/${cacheKey}.ttf")
        return if (cacheFile.exists()) cacheFile else null
    }

    private fun cacheSubsettedFont(
        fontName: String,
        output: File,
        glyphs: Set<String>,
        axisConfigs: List<HarfBuzzSubsetter.AxisConfig>,
        stripHinting: Boolean,
        stripGlyphNames: Boolean
    ) {
        val buildDir = buildDirectory.orNull?.asFile ?: return
        val cacheKey = generateCacheKey(fontName, glyphs, axisConfigs, stripHinting, stripGlyphNames)
        val cacheFile = File(buildDir, "${Constants.Directories.BUILD_CACHE}/${cacheKey}.ttf")
        cacheFile.parentFile.mkdirs()
        output.copyTo(cacheFile, overwrite = true)
    }

    private fun generateCacheKey(
        fontName: String,
        glyphs: Set<String>,
        axisConfigs: List<HarfBuzzSubsetter.AxisConfig>,
        stripHinting: Boolean,
        stripGlyphNames: Boolean
    ): String {
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(fontName.toByteArray())
        glyphs.sorted().forEach { glyph ->
            digest.update(glyph.toByteArray())
        }
        axisConfigs.forEach { axis ->
            digest.update(axis.tag.toByteArray())
            digest.update(axis.remove.toString().toByteArray())
            axis.minValue?.let { digest.update(it.toString().toByteArray()) }
            axis.maxValue?.let { digest.update(it.toString().toByteArray()) }
            axis.defaultValue?.let { digest.update(it.toString().toByteArray()) }
        }
        digest.update(stripHinting.toString().toByteArray())
        digest.update(stripGlyphNames.toString().toByteArray())
        return digest.digest().joinToString("") { "%02x".format(it) }.take(16)
    }

    private fun formatFileSize(size: Long): String {
        return when {
            size < 1024 -> "$size B"
            size < 1024 * 1024 -> String.format("%.1f KB", size / 1024.0)
            else -> String.format("%.1f MB", size / (1024.0 * 1024.0))
        }
    }
}