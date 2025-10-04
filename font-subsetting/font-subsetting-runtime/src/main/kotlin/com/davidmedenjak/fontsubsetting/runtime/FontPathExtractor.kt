package com.davidmedenjak.fontsubsetting.runtime

import android.content.Context
import android.util.Log
import androidx.annotation.FontRes
import com.davidmedenjak.fontsubsetting.runtime.AxisTag.fromString
import java.io.InputStream

/**
 * Variable font axis tags as integers for efficient JNI calls.
 *
 * Using integer tags (4-byte codes) instead of strings eliminates:
 * - String allocations in hot path
 * - UTF-8 encoding/decoding overhead
 * - JNI string conversion overhead
 *
 * Common variable font axes are provided as constants.
 * Use [fromString] to convert custom axis names to integer tags.
 *
 * Example:
 * ```kotlin
 * glyph.setAxis(AxisTag.FILL, 1f)
 * glyph.setAxis(AxisTag.WGHT, 700f)
 * ```
 */
object AxisTag {
    /** Fill axis (Material Symbols) - 0=outline, 1=filled */
    const val FILL = 0x46494C4C  // 'FILL'

    /** Weight axis - typically 100-900 */
    const val WGHT = 0x77676874  // 'wght'

    /** Grade axis (Material Symbols) - typically -25 to 200 */
    const val GRAD = 0x47524144  // 'GRAD'

    /** Optical size axis - typically 8-144 */
    const val OPSZ = 0x6F70737A  // 'opsz'

    /** Width axis - typically 50-200 */
    const val WDTH = 0x77647468  // 'wdth'

    /** Slant axis - typically -10 to 10 */
    const val SLNT = 0x736C6E74  // 'slnt'

    /** Italic axis - 0=roman, 1=italic */
    const val ITAL = 0x6974616C  // 'ital'

    /**
     * Converts a 4-character axis tag string to an integer tag.
     *
     * @param tag 4-character axis tag (e.g., "FILL", "wght", "GRAD")
     * @return Integer tag for use with axis methods
     * @throws IllegalArgumentException if tag is not exactly 4 characters
     */
    fun fromString(tag: String): Int {
        require(tag.length == 4) { "Axis tag must be exactly 4 characters, got: '$tag'" }
        return (tag[0].code shl 24) or (tag[1].code shl 16) or
                (tag[2].code shl 8) or tag[3].code
    }

    /**
     * Converts an integer tag back to a string.
     * Useful for debugging.
     */
    fun toString(tag: Int): String {
        return buildString(4) {
            append(((tag shr 24) and 0xFF).toChar())
            append(((tag shr 16) and 0xFF).toChar())
            append(((tag shr 8) and 0xFF).toChar())
            append((tag and 0xFF).toChar())
        }
    }
}

/**
 * Extracts vector paths from font glyphs using HarfBuzz.
 * This allows rendering font glyphs as vector paths in Compose.
 *
 * The font data is stored in native memory once during construction,
 * and only a native pointer is passed across JNI for glyph extraction.
 *
 * Use `rememberGlyph()` composable to render glyphs efficiently.
 */
class FontPathExtractor : AutoCloseable {

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
        fun fromResource(context: Context, @FontRes resourceId: Int): FontPathExtractor {
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

    // Native pointer to font data stored in native memory
    internal var nativeFontPtr: Long = 0

    private constructor(fontData: ByteArray) {
        if (!nativeLibraryLoaded) {
            throw IllegalStateException("Native library not loaded")
        }
        // Store font data in native memory once
        nativeFontPtr = nativeCreateFontHandle(fontData)
        if (nativeFontPtr == 0L) {
            throw IllegalStateException("Failed to create native font handle")
        }
    }

    /**
     * Extracts the raw glyph path data for internal use.
     *
     * @param codepoint Unicode codepoint of the glyph
     * @param variations Map of axis tag to value (empty for static glyphs)
     * @return Raw float array containing path data, or null if not found
     */
    internal fun extractGlyphPathData(
        codepoint: Int,
        variations: Map<Int, Float> = emptyMap()
    ): FloatArray? {
        checkNotClosed()

        if (variations.isEmpty()) {
            return nativeExtractGlyphPath(nativeFontPtr, codepoint)
        }

        val tags = variations.keys.toIntArray()
        val values = variations.values.toFloatArray()
        return nativeExtractGlyphPathWithVariations(nativeFontPtr, codepoint, tags, values)
    }

    /**
     * Extracts the raw glyph path for a codepoint without variations.
     */
    internal fun nativeExtractGlyphPathInternal(codepoint: Int): FloatArray? {
        checkNotClosed()
        return nativeExtractGlyphPath(nativeFontPtr, codepoint)
    }

    /**
     * Extracts the raw glyph path for a codepoint with axis variations.
     */
    internal fun nativeExtractGlyphPathWithVariationsInternal(
        codepoint: Int,
        variationTags: IntArray,
        variationValues: FloatArray
    ): FloatArray? {
        checkNotClosed()
        return nativeExtractGlyphPathWithVariations(
            nativeFontPtr,
            codepoint,
            variationTags,
            variationValues
        )
    }

    override fun close() {
        if (nativeFontPtr != 0L) {
            nativeDestroyFontHandle(nativeFontPtr)
            nativeFontPtr = 0
        }
    }

    private fun checkNotClosed() {
        if (nativeFontPtr == 0L) {
            throw IllegalStateException("FontPathExtractor has been closed")
        }
    }

    @Throws(Throwable::class)
    protected fun finalize() {
        // Fallback cleanup in case close() wasn't called
        close()
    }

    /**
     * Creates a native font handle from font data.
     * Returns native pointer (as long) or 0 on failure.
     */
    private external fun nativeCreateFontHandle(fontData: ByteArray): Long

    /**
     * Destroys a native font handle and frees associated memory.
     */
    private external fun nativeDestroyFontHandle(fontPtr: Long)

    /**
     * Extracts glyph path using native font pointer.
     */
    private external fun nativeExtractGlyphPath(fontPtr: Long, codepoint: Int): FloatArray?

    /**
     * Extracts glyph path with variations using native font pointer.
     */
    private external fun nativeExtractGlyphPathWithVariations(
        fontPtr: Long,
        codepoint: Int,
        variationTags: IntArray,
        variationValues: FloatArray
    ): FloatArray?

    /**
     * Creates a native glyph handle that caches HarfBuzz objects for efficient updates.
     * Returns native pointer (as long) or 0 on failure.
     */
    private external fun nativeCreateGlyphHandle(fontPtr: Long, codepoint: Int): Long

    /**
     * Destroys a native glyph handle and frees associated HarfBuzz objects.
     */
    private external fun nativeDestroyGlyphHandle(glyphHandlePtr: Long)

    /**
     * Extracts glyph path from a glyph handle with variations.
     * This reuses cached HarfBuzz objects for better performance.
     */
    private external fun nativeExtractGlyphPathFromHandle(
        glyphHandlePtr: Long,
        variationTags: IntArray,
        variationValues: FloatArray
    ): FloatArray?

    /**
     * Zero-allocation glyph path extraction - no axis variations.
     * Use this for static glyphs.
     */
    private external fun nativeExtractGlyphPathFromHandle0(
        glyphHandlePtr: Long
    ): FloatArray?

    /**
     * Zero-allocation glyph path extraction - 1 axis variation.
     * Avoids array allocation for single axis case (most common).
     */
    private external fun nativeExtractGlyphPathFromHandle1(
        glyphHandlePtr: Long,
        tag1: Int,
        value1: Float
    ): FloatArray?

    /**
     * Zero-allocation glyph path extraction - 2 axis variations.
     * Avoids array allocation for two axes case (e.g., FILL + wght).
     */
    private external fun nativeExtractGlyphPathFromHandle2(
        glyphHandlePtr: Long,
        tag1: Int,
        value1: Float,
        tag2: Int,
        value2: Float
    ): FloatArray?

    /**
     * Zero-allocation glyph path extraction - 3 axis variations.
     * Avoids array allocation for three axes case (e.g., FILL + wght + GRAD).
     */
    private external fun nativeExtractGlyphPathFromHandle3(
        glyphHandlePtr: Long,
        tag1: Int,
        value1: Float,
        tag2: Int,
        value2: Float,
        tag3: Int,
        value3: Float
    ): FloatArray?

    // Internal wrappers for GlyphState to access without name mangling
    internal fun createGlyphHandle(codepoint: Int): Long {
        checkNotClosed()
        return nativeCreateGlyphHandle(nativeFontPtr, codepoint)
    }

    internal fun destroyGlyphHandle(glyphHandlePtr: Long) {
        checkNotClosed()
        nativeDestroyGlyphHandle(glyphHandlePtr)
    }

    internal fun extractGlyphPathFromHandle(
        glyphHandlePtr: Long,
        variationTags: IntArray,
        variationValues: FloatArray
    ): FloatArray? {
        checkNotClosed()
        return nativeExtractGlyphPathFromHandle(glyphHandlePtr, variationTags, variationValues)
    }

    internal fun extractGlyphPathFromHandle0(glyphHandlePtr: Long): FloatArray? {
        checkNotClosed()
        return nativeExtractGlyphPathFromHandle0(glyphHandlePtr)
    }

    internal fun extractGlyphPathFromHandle1(
        glyphHandlePtr: Long,
        tag1: Int,
        value1: Float
    ): FloatArray? {
        checkNotClosed()
        return nativeExtractGlyphPathFromHandle1(glyphHandlePtr, tag1, value1)
    }

    internal fun extractGlyphPathFromHandle2(
        glyphHandlePtr: Long,
        tag1: Int,
        value1: Float,
        tag2: Int,
        value2: Float
    ): FloatArray? {
        checkNotClosed()
        return nativeExtractGlyphPathFromHandle2(glyphHandlePtr, tag1, value1, tag2, value2)
    }

    internal fun extractGlyphPathFromHandle3(
        glyphHandlePtr: Long,
        tag1: Int,
        value1: Float,
        tag2: Int,
        value2: Float,
        tag3: Int,
        value3: Float
    ): FloatArray? {
        checkNotClosed()
        return nativeExtractGlyphPathFromHandle3(
            glyphHandlePtr, tag1, value1, tag2, value2, tag3, value3
        )
    }

    // New direct extraction methods using SharedFontData (no per-glyph handles)
    // These are more efficient as they skip the per-glyph handle management

    /**
     * Extracts glyph path directly from SharedFontData without axis variations.
     * Uses the shared HarfBuzz objects for efficiency.
     */
    internal fun extractGlyphPathDirect(codepoint: Int): FloatArray? {
        checkNotClosed()
        return nativeExtractGlyphPathDirect0(nativeFontPtr, codepoint)
    }

    /**
     * Extracts glyph path directly with 1 axis variation.
     * Zero allocation, uses shared HarfBuzz objects.
     */
    internal fun extractGlyphPathDirect1(
        codepoint: Int,
        tag1: Int,
        value1: Float
    ): FloatArray? {
        checkNotClosed()
        return nativeExtractGlyphPathDirect1(nativeFontPtr, codepoint, tag1, value1)
    }

    /**
     * Extracts glyph path directly with 2 axis variations.
     * Zero allocation, uses shared HarfBuzz objects.
     */
    internal fun extractGlyphPathDirect2(
        codepoint: Int,
        tag1: Int,
        value1: Float,
        tag2: Int,
        value2: Float
    ): FloatArray? {
        checkNotClosed()
        return nativeExtractGlyphPathDirect2(nativeFontPtr, codepoint, tag1, value1, tag2, value2)
    }

    /**
     * Extracts glyph path directly with 3 axis variations.
     * Zero allocation, uses shared HarfBuzz objects.
     */
    internal fun extractGlyphPathDirect3(
        codepoint: Int,
        tag1: Int,
        value1: Float,
        tag2: Int,
        value2: Float,
        tag3: Int,
        value3: Float
    ): FloatArray? {
        checkNotClosed()
        return nativeExtractGlyphPathDirect3(
            nativeFontPtr, codepoint, tag1, value1, tag2, value2, tag3, value3
        )
    }

    /**
     * Extracts glyph path directly with N axis variations (fallback for 4+).
     * Uses array allocation for rare case of many axes.
     */
    internal fun extractGlyphPathDirectN(
        codepoint: Int,
        tags: IntArray,
        values: FloatArray
    ): FloatArray? {
        checkNotClosed()
        return nativeExtractGlyphPathDirectN(nativeFontPtr, codepoint, tags, values)
    }

    // Native methods for direct extraction (using SharedFontData)
    private external fun nativeExtractGlyphPathDirect0(fontPtr: Long, codepoint: Int): FloatArray?
    private external fun nativeExtractGlyphPathDirect1(
        fontPtr: Long,
        codepoint: Int,
        tag1: Int,
        value1: Float
    ): FloatArray?

    private external fun nativeExtractGlyphPathDirect2(
        fontPtr: Long,
        codepoint: Int,
        tag1: Int,
        value1: Float,
        tag2: Int,
        value2: Float
    ): FloatArray?

    private external fun nativeExtractGlyphPathDirect3(
        fontPtr: Long,
        codepoint: Int,
        tag1: Int,
        value1: Float,
        tag2: Int,
        value2: Float,
        tag3: Int,
        value3: Float
    ): FloatArray?

    private external fun nativeExtractGlyphPathDirectN(
        fontPtr: Long,
        codepoint: Int,
        tags: IntArray,
        values: FloatArray
    ): FloatArray?
}
