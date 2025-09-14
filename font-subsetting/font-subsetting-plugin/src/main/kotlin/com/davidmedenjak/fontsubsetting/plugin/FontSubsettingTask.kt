package com.davidmedenjak.fontsubsetting.plugin

import com.davidmedenjak.fontsubsetting.native.HarfBuzzSubsetter
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.FileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import java.io.File
import java.security.MessageDigest

/**
 * Serializable representation of axis configuration for Gradle input tracking.
 */
data class SerializableAxisConfig(
    val tag: String,
    val remove: Boolean,
    val minValue: Float?,
    val maxValue: Float?,
    val defaultValue: Float?
) : java.io.Serializable {
    companion object {
        private const val serialVersionUID = 1L
    }
}

/**
 * Serializable representation of font configuration for Gradle input tracking.
 */
data class SerializableFontConfig(
    val name: String,
    val fontFilePath: String,
    val codepointsFilePath: String,
    val packageName: String,
    val className: String,
    val resourceName: String?,
    val fontFileName: String?,
    val axes: List<SerializableAxisConfig>,
    val stripHinting: Boolean = true,
    val stripGlyphNames: Boolean = true
) : java.io.Serializable {
    companion object {
        private const val serialVersionUID = 2L
    }
}

/**
 * Gradle task that performs font subsetting based on usage data from the compiler plugin.
 */
@CacheableTask
abstract class FontSubsettingTask : DefaultTask() {
    
    @get:InputFile
    @get:Optional
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val usageDataFile: RegularFileProperty
    
    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty
    
    @get:Internal
    abstract val buildDirectory: DirectoryProperty
    
    @get:Internal
    var fontConfigurations: List<FontConfiguration> = emptyList()
    
    @get:Input
    @get:Optional
    var fontConfigurationInputs: List<SerializableFontConfig> = emptyList()
    
    @get:InputFiles
    @get:Optional
    @get:PathSensitive(PathSensitivity.RELATIVE)
    var fontFiles: FileCollection = project.files()
    
    init {
        group = Constants.PLUGIN_GROUP
        description = "Subsets fonts based on usage annotations"
    }
    
    @TaskAction
    fun subsetFonts() {
        validateConfigurations()
        
        val outputDir = outputDirectory.get().asFile
        val usedIconNames = loadUsageData()
        
        if (usedIconNames.isEmpty()) {
            logger.warn("No icon usage data found. All configured fonts will be copied without subsetting.")
            copyAllFonts(outputDir)
        } else {
            subsetAllFonts(usedIconNames, outputDir)
        }
    }
    
    private fun validateConfigurations() {
        fontConfigurations.forEach { fontConfig ->
            try {
                fontConfig.validate()
            } catch (e: IllegalStateException) {
                throw GradleException("Font configuration validation failed: ${e.message}")
            }
        }
    }
    
    private fun loadUsageData(): Set<String> {
        val usageFile = usageDataFile.asFile.orNull
        if (usageFile == null || !usageFile.exists()) {
            return emptySet()
        }
        
        return try {
            val usageJson = usageFile.readText()
            // Simple JSON parsing without Gson
            val usedIcons = parseUsageData(usageJson)
            logger.info(String.format(Constants.LogMessages.USAGE_DATA_LOADED, usedIcons.size))
            usedIcons
        } catch (e: Exception) {
            logger.error("Failed to load usage data: ${e.message}")
            emptySet()
        }
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
    
    private fun copyAllFonts(outputDir: File) {
        val fontDir = File(outputDir, "font")
        fontDir.mkdirs()
        fontConfigurations.forEach { fontConfig ->
            copyFont(fontConfig, fontDir)
        }
    }
    
    private fun copyFont(fontConfig: FontConfiguration, outputDir: File) {
        val fontFile = fontConfig.fontFile.get().asFile
        val outputFileName = fontConfig.fontFileName.orElse(
            fontConfig.getDefaultFontFileName()
        ).get()
        
        if (!fontFile.exists()) {
            logger.warn(String.format(Constants.LogMessages.FONT_NOT_FOUND, fontFile.absolutePath))
            return
        }
        
        val outputFile = File(outputDir, outputFileName)
        fontFile.copyTo(outputFile, overwrite = true)
        logger.info("Copied ${fontFile.name} to output directory (no subsetting applied)")
    }
    
    private fun subsetAllFonts(usedIconNames: Set<String>, outputDir: File) {
        val fontDir = File(outputDir, "font")
        fontDir.mkdirs()
        fontConfigurations.forEach { fontConfig ->
            processFont(fontConfig, usedIconNames, fontDir)
        }
    }
    
    private fun processFont(fontConfig: FontConfiguration, usedIconNames: Set<String>, outputDir: File) {
        logger.info("Processing font: ${fontConfig.name}")

        val fontFile = fontConfig.fontFile.get().asFile
        val codepointsFile = fontConfig.codepointsFile.get().asFile
        val outputFileName = fontConfig.fontFileName.orElse(
            fontConfig.getDefaultFontFileName()
        ).get()

        if (!validateFiles(fontFile, codepointsFile)) {
            return
        }

        val codepoints = loadCodepoints(codepointsFile, usedIconNames)
        if (codepoints.isEmpty()) {
            logger.warn("No matching codepoints found for font ${fontConfig.name}")
            copyFont(fontConfig, outputDir)
            return
        }

        val axisConfigs = convertAxisConfigurations(fontConfig.axes)
        val stripHinting = fontConfig.stripHinting.orElse(true).get()
        val stripGlyphNames = fontConfig.stripGlyphNames.orElse(true).get()

        subsetFont(
            fontName = outputFileName,
            icons = codepoints,
            fontFile = fontFile,
            outputDir = outputDir,
            axisConfigs = axisConfigs,
            stripHinting = stripHinting,
            stripGlyphNames = stripGlyphNames
        )
    }
    
    private fun validateFiles(fontFile: File, codepointsFile: File): Boolean {
        if (!fontFile.exists()) {
            logger.error(String.format(Constants.LogMessages.FONT_NOT_FOUND, fontFile.absolutePath))
            return false
        }
        if (!codepointsFile.exists()) {
            logger.error(String.format(Constants.LogMessages.CODEPOINTS_NOT_FOUND, codepointsFile.absolutePath))
            return false
        }
        return true
    }
    
    private fun loadCodepoints(codepointsFile: File, usedIconNames: Set<String>): Set<String> {
        val codepoints = mutableSetOf<String>()
        val iconNameVariants = createIconNameVariants(usedIconNames)
        
        try {
            var foundCount = 0
            codepointsFile.readLines().forEach { line ->
                val parts = line.split(' ', limit = 2)
                if (parts.size >= 2) {
                    val iconNameInFile = parts[0]
                    val codepointHex = parts[1]
                    
                    if (iconNameVariants.containsKey(iconNameInFile)) {
                        val codepointEscaped = "\\u${codepointHex.padStart(4, '0').uppercase()}"
                        codepoints.add(codepointEscaped)
                        foundCount++
                    }
                }
            }
            if (foundCount > 0) {
                logger.info("Found $foundCount of ${usedIconNames.size} requested icons")
            }
        } catch (e: Exception) {
            logger.error("Failed to load codepoints from ${codepointsFile.name}", e)
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
    
    private fun convertAxisConfigurations(axes: Collection<AxisConfiguration>): List<HarfBuzzSubsetter.AxisConfig> {
        return axes.mapNotNull { axisConfig ->
            when {
                axisConfig.remove.get() -> {
                    HarfBuzzSubsetter.AxisConfig(
                        tag = axisConfig.name,
                        remove = true
                    )
                }
                axisConfig.minValue.isPresent && axisConfig.maxValue.isPresent -> {
                    HarfBuzzSubsetter.AxisConfig(
                        tag = axisConfig.name,
                        minValue = axisConfig.minValue.get(),
                        maxValue = axisConfig.maxValue.get(),
                        defaultValue = axisConfig.defaultValue.orNull ?: 0f,
                        remove = false
                    )
                }
                else -> {
                    logger.warn("Axis ${axisConfig.name} has incomplete configuration, skipping")
                    null
                }
            }
        }
    }
    
    private fun subsetFont(
        fontName: String,
        icons: Set<String>,
        fontFile: File,
        outputDir: File,
        axisConfigs: List<HarfBuzzSubsetter.AxisConfig> = emptyList(),
        stripHinting: Boolean = true,
        stripGlyphNames: Boolean = true
    ) {
        val cacheKey = generateCacheKey(fontName, icons, axisConfigs, stripHinting, stripGlyphNames)
        val cacheFile = File(buildDirectory.get().asFile, "${Constants.Directories.BUILD_CACHE}/${cacheKey}.ttf")
        
        if (cacheFile.exists()) {
            val outputFile = File(outputDir, fontName)
            cacheFile.copyTo(outputFile, overwrite = true)
            logger.info("Using cached subset for $fontName")
            return
        }
        
        val outputFile = File(outputDir, fontName)
        
        try {
            val subsetter = NativeSubsetterFactory(logger).getSubsetter()
            val result = subsetter.subset(
                fontFile,
                outputFile,
                icons.toList(),
                axisConfigs,
                stripHinting,
                stripGlyphNames
            )
            
            result.onSuccess { subsetResult ->
                cacheFontResult(subsetResult.outputFile, cacheFile)
                logSubsettingResults(fontName, fontFile, subsetResult)
            }.onFailure { error ->
                logger.error("Failed to subset font $fontName", error)
                throw GradleException("Font subsetting failed: ${error.message}", error)
            }
        } catch (e: Exception) {
            logger.error("Unexpected error during font subsetting", e)
            throw GradleException("Font subsetting failed: ${e.message}", e)
        }
    }
    
    private fun cacheFontResult(outputFile: File, cacheFile: File) {
        cacheFile.parentFile.mkdirs()
        outputFile.copyTo(cacheFile, overwrite = true)
    }
    
    private fun logSubsettingResults(
        fontName: String,
        inputFile: File,
        subsetResult: HarfBuzzSubsetter.SubsetResult
    ) {
        val originalSize = inputFile.length()
        val originalGlyphs = subsetResult.originalInfo.glyphCount
        val finalGlyphs = subsetResult.finalInfo.glyphCount
        val finalSize = subsetResult.outputFile.length()
        
        logger.info(String.format(Constants.LogMessages.SUBSETTING_COMPLETE, fontName))
        logger.info("  Glyphs: $originalGlyphs → $finalGlyphs (${subsetResult.glyphsRequested} requested)")
        logger.info("  Size: ${formatFileSize(originalSize)} → ${formatFileSize(finalSize)}")
        
        val totalReduction = if (originalSize > 0) {
            ((originalSize - finalSize) * 100.0 / originalSize)
        } else 0.0
        logger.info("  Reduction: ${String.format("%.1f", totalReduction)}%")
        
        val originalAxes = subsetResult.originalInfo.axes
        if (!originalAxes.isNullOrEmpty()) {
            logger.info("  Original axes: ${originalAxes.size}")
            val finalAxes = subsetResult.finalInfo.axes
            if (finalAxes != null && finalAxes.size != originalAxes.size) {
                logger.info("  Final axes: ${finalAxes.size}")
            }
        }
    }
    
    private fun generateCacheKey(
        fontName: String,
        icons: Set<String>,
        axisConfigs: List<HarfBuzzSubsetter.AxisConfig>,
        stripHinting: Boolean = true,
        stripGlyphNames: Boolean = true
    ): String {
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(fontName.toByteArray())
        icons.sorted().forEach { icon ->
            digest.update(icon.toByteArray())
        }
        axisConfigs.forEach { axis ->
            digest.update(axis.tag.toByteArray())
            digest.update(axis.remove.toString().toByteArray())
            axis.minValue?.let { digest.update(it.toString().toByteArray()) }
            axis.maxValue?.let { digest.update(it.toString().toByteArray()) }
            axis.defaultValue?.let { digest.update(it.toString().toByteArray()) }
        }
        // Include flag settings in cache key
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