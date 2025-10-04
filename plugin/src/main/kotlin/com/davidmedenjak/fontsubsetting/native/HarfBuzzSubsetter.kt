package com.davidmedenjak.fontsubsetting.native

import java.io.File

/**
 * Logger interface for native code callbacks
 */
interface NativeLogger {
    fun log(level: Int, message: String)
}

/**
 * JNI wrapper for HarfBuzz font subsetting operations.
 * Provides high-level Kotlin API for font subsetting with comprehensive logging.
 */
class HarfBuzzSubsetter(private val logger: NativeLogger? = null) {
    
    init {
        // Ensure library is loaded when instance is created
        ensureLibraryLoaded()
        // Set up native logging if logger provided
        logger?.let { nativeSetLogger(it) }
    }
    
    companion object {
        private var nativeLibraryLoaded = false
        private val LIBRARY_LOADED_LOCK = Object()
        
        // Log levels matching C++ enum
        const val LOG_DEBUG = 0
        const val LOG_INFO = 1
        const val LOG_WARN = 2
        const val LOG_ERROR = 3
        
        @JvmStatic
        fun ensureLibraryLoaded() {
            synchronized(LIBRARY_LOADED_LOCK) {
                if (!nativeLibraryLoaded) {
                    loadNativeLibrary()
                    nativeLibraryLoaded = true
                }
            }
        }
        
        private fun loadNativeLibrary() {
            try {
                // Try to load from system path first
                System.loadLibrary("fontsubsetting")
            } catch (e: UnsatisfiedLinkError) {
                // Try to load from resources
                loadFromResources()
            }
        }
        
        private fun loadFromResources() {
            val (osName, archName, libName) = getPlatformInfo()

//            // For Linux, load musl first for zero-dependency operation
//            if (osName == "linux") {
//                loadMuslFromResources(archName)
//            }

            // Build list of paths to try, from most specific to least specific
            val resourcePaths = buildList {
                // Platform-specific paths with normalized architecture
                add("/native/$osName-$archName/$libName")
                
                // Check native-cross directory (from Docker cross-compilation)
                add("/native-cross/$osName-$archName/$libName")
                
                // Alternative architecture names
                when (archName) {
                    "x86_64" -> {
                        add("/native/$osName-amd64/$libName")
                        add("/native/$osName-x64/$libName")
                        add("/native-cross/$osName-amd64/$libName")
                        add("/native-cross/$osName-x64/$libName")
                    }
                    "aarch64" -> {
                        add("/native/$osName-arm64/$libName")
                        add("/native-cross/$osName-arm64/$libName")
                    }
                    "x86" -> {
                        add("/native/$osName-i386/$libName")
                        add("/native/$osName-i686/$libName")
                        add("/native-cross/$osName-i386/$libName")
                        add("/native-cross/$osName-i686/$libName")
                    }
                }
                
                // Generic fallback
                add("/native/$libName")
                add("/native-cross/$libName")
            }
            
            var inputStream: java.io.InputStream? = null
            var successPath: String? = null
            
            for (path in resourcePaths) {
                inputStream = HarfBuzzSubsetter::class.java.getResourceAsStream(path)
                if (inputStream != null) {
                    successPath = path
                    break
                }
            }
            
            if (inputStream == null) {
                val triedPaths = resourcePaths.joinToString("\n  - ")
                throw UnsatisfiedLinkError(
                    """
                    |================================================================================
                    |FATAL ERROR: Native font subsetting library not found!
                    |================================================================================
                    |Platform: ${System.getProperty("os.name")} / ${System.getProperty("os.arch")}
                    |Expected library: $libName
                    |
                    |Searched in JAR resources:
                    |$triedPaths
                    |
                    |SOLUTION:
                    |The native libraries must be built using Docker cross-compilation:
                    |
                    |  cd font-subsetting/font-subsetting-plugin
                    |  ./build-in-docker.sh
                    |
                    |This will build libraries for all platforms:
                    |  - Linux x86_64
                    |  - Windows x86_64  
                    |  - macOS x86_64
                    |  - macOS ARM64
                    |
                    |After building, run:
                    |  ./gradlew :font-subsetting:publishToMavenLocal
                    |
                    |================================================================================
                    """.trimMargin()
                )
            }
            
            val tempFile = File.createTempFile("fontsubsetting", "." + libName.substringAfterLast('.'))
            tempFile.deleteOnExit()
            
            inputStream.use { input ->
                tempFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            
            System.load(tempFile.absolutePath)
        }

//        private fun loadMuslFromResources(archName: String) {
//            // Try to load bundled musl loader for zero-dependency Linux operation
//            val muslName = "ld-musl-$archName.so.1"
//
//            // Build list of paths to try
//            val resourcePaths = buildList {
//                add("/native/linux-$archName/$muslName")
//                add("/native-cross/linux-$archName/$muslName")
//
//                // Alternative architecture names
//                when (archName) {
//                    "x86_64" -> {
//                        add("/native/linux-amd64/ld-musl-amd64.so.1")
//                        add("/native/linux-x86_64/ld-musl-x86_64.so.1")
//                    }
//                    "aarch64" -> {
//                        add("/native/linux-arm64/ld-musl-arm64.so.1")
//                        add("/native/linux-aarch64/ld-musl-aarch64.so.1")
//                    }
//                }
//            }
//
//            var inputStream: java.io.InputStream? = null
//
//            for (path in resourcePaths) {
//                inputStream = HarfBuzzSubsetter::class.java.getResourceAsStream(path)
//                if (inputStream != null) {
//                    break
//                }
//            }
//
//            if (inputStream == null) {
//                // Musl not bundled - will fall back to system musl if available
//                return
//            }
//
//            try {
//                // Extract musl to temp file
//                val tempFile = File.createTempFile("ld-musl-", ".so.1")
//                tempFile.deleteOnExit()
//
//                inputStream.use { input ->
//                    tempFile.outputStream().use { output ->
//                        input.copyTo(output)
//                    }
//                }
//
//                // Make executable
//                tempFile.setExecutable(true)
//
//                // Load musl - this makes it available for fontsubsetting.so to use
//                System.load(tempFile.absolutePath)
//            } catch (e: Exception) {
//                // If loading bundled musl fails, continue - system musl may work
//                // Silent failure is acceptable here as this is an optimization
//            }
//        }

        private fun getPlatformInfo(): Triple<String, String, String> {
            val os = System.getProperty("os.name").lowercase()
            val arch = System.getProperty("os.arch").lowercase()
            
            val osName = when {
                os.contains("win") -> "windows"
                os.contains("mac") || os.contains("darwin") -> "darwin"
                os.contains("linux") -> "linux"
                else -> os.replace(" ", "_").lowercase()
            }
            
            val archName = when (arch) {
                "x86_64", "amd64" -> "x86_64"
                "x86", "i386", "i686" -> "x86"
                "aarch64", "arm64" -> "aarch64"
                "arm", "armv7", "armv7l" -> "arm"
                else -> arch
            }
            
            val libName = when (osName) {
                "windows" -> "fontsubsetting.dll"
                "darwin" -> "libfontsubsetting.dylib"
                else -> "libfontsubsetting.so"
            }
            
            return Triple(osName, archName, libName)
        }
        
        fun isNativeLibraryAvailable(): Boolean {
            return try {
                ensureLibraryLoaded()
                true
            } catch (e: Exception) {
                false
            }
        }
    }
    
    /**
     * Sets the logger for native code callbacks
     */
    private external fun nativeSetLogger(logger: NativeLogger)
    
    /**
     * Subsets a font file to include only the specified glyphs.
     * 
     * @param inputFontPath Path to the input font file
     * @param outputFontPath Path where the subsetted font will be saved
     * @param glyphs List of Unicode codepoints to include
     * @return true if subsetting was successful, false otherwise
     */
    fun subsetFont(
        inputFontPath: String,
        outputFontPath: String,
        glyphs: Array<String>
    ): Boolean {
        ensureLibraryLoaded()
        return nativeSubsetFont(inputFontPath, outputFontPath, glyphs)
    }
    
    private external fun nativeSubsetFont(
        inputFontPath: String,
        outputFontPath: String,
        glyphs: Array<String>
    ): Boolean
    
    /**
     * Subsets a font file with axis configurations for variable fonts.
     *
     * @param inputFontPath Path to the input font file
     * @param outputFontPath Path where the subsetted font will be saved
     * @param glyphs List of Unicode codepoints to include
     * @param axisConfigs List of axis configurations
     * @return true if subsetting was successful, false otherwise
     */
    fun subsetFontWithAxes(
        inputFontPath: String,
        outputFontPath: String,
        glyphs: Array<String>,
        axisConfigs: List<AxisConfig>
    ): Boolean {
        ensureLibraryLoaded()

        // Use the new method with flags, defaulting to stripping both hinting and glyph names
        return subsetFontWithAxesAndFlags(
            inputFontPath,
            outputFontPath,
            glyphs,
            axisConfigs,
            stripHinting = true,
            stripGlyphNames = true
        )
    }
    
    /**
     * Subsets a font file with axis configurations and subsetting flags.
     *
     * @param inputFontPath Path to the input font file
     * @param outputFontPath Path where the subsetted font will be saved
     * @param glyphs List of Unicode codepoints to include
     * @param axisConfigs List of axis configurations
     * @param stripHinting Whether to strip hinting instructions (default: true)
     * @param stripGlyphNames Whether to strip glyph names (default: true)
     * @return true if subsetting was successful, false otherwise
     */
    fun subsetFontWithAxesAndFlags(
        inputFontPath: String,
        outputFontPath: String,
        glyphs: Array<String>,
        axisConfigs: List<AxisConfig>,
        stripHinting: Boolean = true,
        stripGlyphNames: Boolean = true
    ): Boolean {
        ensureLibraryLoaded()

        if (axisConfigs.isEmpty()) {
            // If no axis configs, still use the new method with empty arrays
            return nativeSubsetFontWithAxesAndFlags(
                inputFontPath,
                outputFontPath,
                glyphs,
                emptyArray(),
                floatArrayOf(),
                floatArrayOf(),
                floatArrayOf(),
                booleanArrayOf(),
                stripHinting,
                stripGlyphNames
            )
        }

        val axisTags = axisConfigs.map { it.tag }.toTypedArray()
        val axisMinValues = axisConfigs.map { it.minValue }.toFloatArray()
        val axisMaxValues = axisConfigs.map { it.maxValue }.toFloatArray()
        val axisDefaultValues = axisConfigs.map { it.defaultValue }.toFloatArray()
        val axisRemove = axisConfigs.map { it.remove }.toBooleanArray()

        return nativeSubsetFontWithAxesAndFlags(
            inputFontPath,
            outputFontPath,
            glyphs,
            axisTags,
            axisMinValues,
            axisMaxValues,
            axisDefaultValues,
            axisRemove,
            stripHinting,
            stripGlyphNames
        )
    }

    private external fun nativeSubsetFontWithAxes(
        inputFontPath: String,
        outputFontPath: String,
        glyphs: Array<String>,
        axisTags: Array<String>,
        axisMinValues: FloatArray,
        axisMaxValues: FloatArray,
        axisDefaultValues: FloatArray,
        axisRemove: BooleanArray
    ): Boolean

    private external fun nativeSubsetFontWithAxesAndFlags(
        inputFontPath: String,
        outputFontPath: String,
        glyphs: Array<String>,
        axisTags: Array<String>,
        axisMinValues: FloatArray,
        axisMaxValues: FloatArray,
        axisDefaultValues: FloatArray,
        axisRemove: BooleanArray,
        stripHinting: Boolean,
        stripGlyphNames: Boolean
    ): Boolean
    
    /**
     * Validates that a font file can be processed.
     * 
     * @param fontPath Path to the font file
     * @return true if the font is valid and can be processed
     */
    fun validateFont(fontPath: String): Boolean {
        ensureLibraryLoaded()
        return nativeValidateFont(fontPath)
    }
    
    private external fun nativeValidateFont(fontPath: String): Boolean
    
    /**
     * Gets information about a font file.
     * 
     * @param fontPath Path to the font file
     * @return Font information as a string (JSON format)
     */
    fun getFontInfo(fontPath: String): String? {
        ensureLibraryLoaded()
        return nativeGetFontInfo(fontPath)
    }
    
    private external fun nativeGetFontInfo(fontPath: String): String?
    
    /**
     * Gets detailed font information including axis details.
     * 
     * @param fontPath Path to the font file
     * @return FontInfo object with font details, or null if error
     */
    fun getFontInfoDetailed(fontPath: String): FontInfo? {
        ensureLibraryLoaded()
        val infoString = nativeGetFontInfo(fontPath) ?: return null
        return try {
            parseFontInfo(infoString)
        } catch (e: Exception) {
            logger?.log(LOG_ERROR, "Failed to parse font info: ${e.message}")
            null
        }
    }
    
    /**
     * Parses font info from a properties-like format using Properties class.
     * Expected format:
     * glyphCount=<number>
     * unitsPerEm=<number>
     * fileSize=<number>
     * axis.0=<tag>,<min>,<default>,<max>
     * axis.1=<tag>,<min>,<default>,<max>
     * ...
     */
    private fun parseFontInfo(infoString: String): FontInfo {
        val props = java.util.Properties()
        props.load(infoString.reader())
        
        val glyphCount = props.getProperty("glyphCount")?.toInt() ?: 0
        val unitsPerEm = props.getProperty("unitsPerEm")?.toInt() ?: 0
        val fileSize = props.getProperty("fileSize")?.toLong() ?: 0L
        
        // Collect all axis properties
        val axes = mutableListOf<FontInfo.AxisInfo>()
        var index = 0
        while (true) {
            val axisValue = props.getProperty("axis.$index") ?: break
            val parts = axisValue.split(",")
            if (parts.size == 4) {
                axes.add(FontInfo.AxisInfo(
                    tag = parts[0],
                    minValue = parts[1].toFloat(),
                    defaultValue = parts[2].toFloat(),
                    maxValue = parts[3].toFloat()
                ))
            }
            index++
        }
        
        return FontInfo(
            glyphCount = glyphCount,
            unitsPerEm = unitsPerEm,
            fileSize = fileSize,
            axes = if (axes.isNotEmpty()) axes else null
        )
    }
    
    /**
     * Data class for font information.
     */
    data class FontInfo(
        val glyphCount: Int,
        val unitsPerEm: Int,
        val fileSize: Long,
        val axes: List<AxisInfo>? = null
    ) {
        data class AxisInfo(
            val tag: String,
            val minValue: Float,
            val defaultValue: Float,
            val maxValue: Float
        )
    }
    
    /**
     * Data class for axis configuration.
     */
    data class AxisConfig(
        val tag: String,
        val minValue: Float = 0f,
        val maxValue: Float = 0f,
        val defaultValue: Float = 0f,
        val remove: Boolean = false
    )
    
    /**
     * Result of subsetting with detailed metrics.
     */
    data class SubsetResult(
        val outputFile: File,
        val originalInfo: FontInfo,
        val finalInfo: FontInfo,
        val glyphsRequested: Int,
        val axisConfigs: List<AxisConfig>
    )
    
    /**
     * High-level Kotlin wrapper for subsetting with better error handling.
     * This is the recommended API for most use cases.
     */
    fun subset(
        inputFile: File,
        outputFile: File,
        icons: List<String>,
        axisConfigs: List<AxisConfig> = emptyList(),
        stripHinting: Boolean = true,
        stripGlyphNames: Boolean = true
    ): Result<SubsetResult> {
        return try {
            if (!inputFile.exists()) {
                return Result.failure(IllegalArgumentException("Input font file does not exist: $inputFile"))
            }
            
            if (!validateFont(inputFile.absolutePath)) {
                return Result.failure(IllegalArgumentException("Invalid font file: $inputFile"))
            }
            
            // Get original font info
            val originalInfo = getFontInfoDetailed(inputFile.absolutePath)
                ?: return Result.failure(RuntimeException("Failed to get font info"))
            
            // Convert icon values to codepoints
            val codepoints = icons.mapNotNull { icon ->
                val codepoint = when {
                    // Handle Unicode escape sequences like "\uE87C"
                    icon.startsWith("\\u") && icon.length >= 6 -> {
                        try {
                            icon.substring(2, 6).toInt(16)
                        } catch (e: NumberFormatException) {
                            logger?.log(LOG_WARN, "Failed to parse Unicode escape: $icon")
                            null
                        }
                    }
                    // Handle direct Unicode characters
                    icon.isNotEmpty() -> {
                        icon.codePointAt(0)
                    }
                    else -> null
                }
                
                if (codepoint != null && codepoint > 0) {
                    codepoint.toString()
                } else {
                    null
                }
            }
            
            if (codepoints.isEmpty()) {
                return Result.failure(IllegalArgumentException("No valid codepoints found in icons"))
            }
            
            // Ensure output directory exists
            outputFile.parentFile?.mkdirs()
            
            // Perform subsetting with flags
            val success = subsetFontWithAxesAndFlags(
                inputFile.absolutePath,
                outputFile.absolutePath,
                codepoints.toTypedArray(),
                axisConfigs,
                stripHinting,
                stripGlyphNames
            )
            
            if (!success || !outputFile.exists()) {
                return Result.failure(RuntimeException("Font subsetting failed"))
            }
            
            // Get final info
            val finalInfo = getFontInfoDetailed(outputFile.absolutePath)
                ?: return Result.failure(RuntimeException("Failed to get final font info"))
            
            Result.success(SubsetResult(
                outputFile = outputFile,
                originalInfo = originalInfo,
                finalInfo = finalInfo,
                glyphsRequested = codepoints.size,
                axisConfigs = axisConfigs
            ))
        } catch (e: Exception) {
            logger?.log(LOG_ERROR, "Exception during subsetting: ${e.message}")
            Result.failure(e)
        }
    }
}