package com.davidmedenjak.fontsubsetting.runtime

import android.content.Context
import android.util.Log
import androidx.annotation.RawRes
import java.io.InputStream

/**
 * Extracts vector paths from font glyphs using HarfBuzz.
 * This allows rendering font glyphs as vector paths in Compose.
 */
class FontPathExtractor {

    companion object {
        private const val TAG = "FontPathExtractor"
        private var nativeLibraryLoaded = false

        init {
            try {
                System.loadLibrary("fontsubsetting-runtime")
                nativeLibraryLoaded = true
                Log.d(TAG, "Native library loaded successfully")
            } catch (e: UnsatisfiedLinkError) {
                Log.e(TAG, "Failed to load native library", e)
            }
        }

        /**
         * Creates a FontPathExtractor from a font resource.
         */
        fun fromResource(context: Context, @RawRes resourceId: Int): FontPathExtractor {
            val fontData = context.resources.openRawResource(resourceId).use { it.readBytes() }
            return FontPathExtractor(fontData)
        }

        /**
         * Creates a FontPathExtractor from font file bytes.
         */
        fun fromBytes(fontData: ByteArray): FontPathExtractor {
            return FontPathExtractor(fontData)
        }

        /**
         * Creates a FontPathExtractor from an input stream.
         */
        fun fromStream(inputStream: InputStream): FontPathExtractor {
            val fontData = inputStream.use { it.readBytes() }
            return FontPathExtractor(fontData)
        }
    }

    private val fontData: ByteArray

    private constructor(fontData: ByteArray) {
        if (!nativeLibraryLoaded) {
            throw IllegalStateException("Native library not loaded")
        }
        this.fontData = fontData
    }

    /**
     * Extracts the path for a specific glyph.
     *
     * @param codepoint Unicode codepoint of the glyph
     * @return GlyphPath containing the path data, or null if not found
     */
    fun extractGlyphPath(codepoint: Int): GlyphPath? {
        val rawData = nativeExtractGlyphPath(fontData, codepoint) ?: return null
        return parseGlyphPath(rawData)
    }

    /**
     * Extracts the path for a specific glyph with variable font axis variations.
     *
     * @param codepoint Unicode codepoint of the glyph
     * @param variations Map of axis tag (e.g., "FILL", "wght") to value
     * @return GlyphPath containing the path data, or null if not found
     */
    fun extractGlyphPath(codepoint: Int, variations: Map<String, Float>): GlyphPath? {
        if (variations.isEmpty()) {
            return extractGlyphPath(codepoint)
        }

        val tags = variations.keys.toTypedArray()
        val values = variations.values.toFloatArray()

        val rawData =
            nativeExtractGlyphPathWithVariations(fontData, codepoint, tags, values) ?: return null
        return parseGlyphPath(rawData)
    }

    /**
     * Extracts the path for a character.
     *
     * @param char Character to extract path for
     * @return GlyphPath containing the path data, or null if not found
     */
    fun extractGlyphPath(char: Char): GlyphPath? {
        return extractGlyphPath(char.code)
    }

    /**
     * Extracts the path for a character with variable font axis variations.
     *
     * @param char Character to extract path for
     * @param variations Map of axis tag to value
     * @return GlyphPath containing the path data, or null if not found
     */
    fun extractGlyphPath(char: Char, variations: Map<String, Float>): GlyphPath? {
        return extractGlyphPath(char.code, variations)
    }

    /**
     * Extracts paths for a string of characters.
     *
     * @param text String to extract paths for
     * @return List of GlyphPath objects (may contain nulls for missing glyphs)
     */
    fun extractGlyphPaths(text: String): List<GlyphPath?> {
        return text.map { extractGlyphPath(it) }
    }

    private fun parseGlyphPath(rawData: FloatArray): GlyphPath {
        if (rawData.size < 8) {
            throw IllegalArgumentException("Invalid glyph path data")
        }

        val numCommands = rawData[0].toInt()
        val advanceWidth = rawData[1]
        val advanceHeight = rawData[2]
        val unitsPerEm = rawData[3].toInt()
        val minX = rawData[4]
        val minY = rawData[5]
        val maxX = rawData[6]
        val maxY = rawData[7]

        val commands = mutableListOf<PathCommand>()
        var offset = 8

        for (i in 0 until numCommands) {
            if (offset + 7 > rawData.size) break

            val type = PathCommandType.fromInt(rawData[offset].toInt())
            val x1 = rawData[offset + 1]
            val y1 = rawData[offset + 2]
            val x2 = rawData[offset + 3]
            val y2 = rawData[offset + 4]
            val x3 = rawData[offset + 5]
            val y3 = rawData[offset + 6]

            commands.add(PathCommand(type, x1, y1, x2, y2, x3, y3))
            offset += 7
        }

        return GlyphPath(commands, advanceWidth, advanceHeight, unitsPerEm, minX, minY, maxX, maxY)
    }

    private external fun nativeExtractGlyphPath(fontData: ByteArray, codepoint: Int): FloatArray?
    private external fun nativeExtractGlyphPathWithVariations(
        fontData: ByteArray,
        codepoint: Int,
        variationTags: Array<String>,
        variationValues: FloatArray
    ): FloatArray?
}

/**
 * Type of path command.
 */
enum class PathCommandType {
    MOVE_TO,
    LINE_TO,
    QUADRATIC_TO,
    CUBIC_TO,
    CLOSE;

    companion object {
        fun fromInt(value: Int): PathCommandType {
            return entries.getOrNull(value) ?: MOVE_TO
        }
    }
}

/**
 * A single path command.
 */
data class PathCommand(
    val type: PathCommandType,
    val x1: Float = 0f,
    val y1: Float = 0f,
    val x2: Float = 0f,
    val y2: Float = 0f,
    val x3: Float = 0f,
    val y3: Float = 0f
)

/**
 * Complete glyph path with metrics.
 */
data class GlyphPath(
    val commands: List<PathCommand>,
    val advanceWidth: Float,
    val advanceHeight: Float,
    val unitsPerEm: Int,
    val minX: Float,
    val minY: Float,
    val maxX: Float,
    val maxY: Float
) {
    val isEmpty: Boolean get() = commands.isEmpty()

    /**
     * Width of the glyph bounding box
     */
    val width: Float get() = maxX - minX

    /**
     * Height of the glyph bounding box
     */
    val height: Float get() = maxY - minY
}
