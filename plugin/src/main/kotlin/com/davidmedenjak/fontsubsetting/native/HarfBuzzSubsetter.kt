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
                    |  cd plugin
                    |  ./build-in-docker.sh
                    |
                    |This will build libraries for all platforms:
                    |  - Linux x86_64
                    |  - Windows x86_64  
                    |  - macOS x86_64
                    |  - macOS ARM64
                    |
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
     * Subsets a font file with axis configurations and subsetting flags.
     *
     * @param inputFontPath Path to the input font file
     * @param outputFontPath Path where the subsetted font will be saved
     * @param codepoints Unicode codepoints to include
     * @param axisConfigs List of axis configurations
     * @param stripHinting Whether to strip hinting instructions (default: true)
     * @param stripGlyphNames Whether to strip glyph names (default: true)
     * @return true if subsetting was successful, false otherwise
     */
    fun subsetFontWithAxesAndFlags(
        inputFontPath: String,
        outputFontPath: String,
        codepoints: IntArray,
        axisConfigs: List<AxisConfig>,
        stripHinting: Boolean = true,
        stripGlyphNames: Boolean = true
    ): Boolean {
        ensureLibraryLoaded()

        if (axisConfigs.isEmpty()) {
            return nativeSubsetFontWithAxesAndFlags(
                inputFontPath,
                outputFontPath,
                codepoints,
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
            codepoints,
            axisTags,
            axisMinValues,
            axisMaxValues,
            axisDefaultValues,
            axisRemove,
            stripHinting,
            stripGlyphNames
        )
    }

    private external fun nativeSubsetFontWithAxesAndFlags(
        inputFontPath: String,
        outputFontPath: String,
        codepoints: IntArray,
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
    
}