package com.davidmedenjak.fontsubsetting.runtime

import android.content.Context
import android.util.Log
import androidx.annotation.AnyRes
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
        fun fromResource(
            context: Context,
            @FontRes resourceId: Int
        ): FontPathExtractor {
            val fontData = context.resources.openRawResource(resourceId).use { it.readBytes() }
            return FontPathExtractor(fontData)
        }

        /**
         * Creates a FontPathExtractor from a raw resource.
         */
        fun fromRawResource(
            context: Context,
            @AnyRes resourceId: Int
        ): FontPathExtractor {
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

    // New direct extraction methods using SharedFontData (no per-glyph handles)
    // These are more efficient as they skip the per-glyph handle management

    /**
     * Extracts glyph path directly from SharedFontData without axis variations.
     * Uses the shared HarfBuzz objects for efficiency.
     */
    internal fun extractPath(codepoint: Int): FloatArray? {
        checkNotClosed()
        return nativeExtractPath0(nativeFontPtr, codepoint)
    }

    /**
     * Extracts glyph path directly with 1 axis variation.
     * Zero allocation, uses shared HarfBuzz objects.
     */
    internal fun extractPath1(
        codepoint: Int,
        tag1: Int,
        value1: Float
    ): FloatArray? {
        checkNotClosed()
        return nativeExtractPath1(nativeFontPtr, codepoint, tag1, value1)
    }

    /**
     * Extracts glyph path directly with 2 axis variations.
     * Zero allocation, uses shared HarfBuzz objects.
     */
    internal fun extractPath2(
        codepoint: Int,
        tag1: Int,
        value1: Float,
        tag2: Int,
        value2: Float
    ): FloatArray? {
        checkNotClosed()
        return nativeExtractPath2(nativeFontPtr, codepoint, tag1, value1, tag2, value2)
    }

    /**
     * Extracts glyph path directly with 3 axis variations.
     * Zero allocation, uses shared HarfBuzz objects.
     */
    internal fun extractPath3(
        codepoint: Int,
        tag1: Int,
        value1: Float,
        tag2: Int,
        value2: Float,
        tag3: Int,
        value3: Float
    ): FloatArray? {
        checkNotClosed()
        return nativeExtractPath3(
            nativeFontPtr, codepoint, tag1, value1, tag2, value2, tag3, value3
        )
    }

    /**
     * Extracts glyph path directly with N axis variations (fallback for 4+).
     * Uses array allocation for rare case of many axes.
     */
    internal fun extractPathN(
        codepoint: Int,
        tags: IntArray,
        values: FloatArray
    ): FloatArray? {
        checkNotClosed()
        return nativeExtractPathN(nativeFontPtr, codepoint, tags, values)
    }

    // Native methods for direct extraction (using SharedFontData)
    private external fun nativeExtractPath0(fontPtr: Long, codepoint: Int): FloatArray?
    private external fun nativeExtractPath1(
        fontPtr: Long,
        codepoint: Int,
        tag1: Int,
        value1: Float
    ): FloatArray?

    private external fun nativeExtractPath2(
        fontPtr: Long,
        codepoint: Int,
        tag1: Int,
        value1: Float,
        tag2: Int,
        value2: Float
    ): FloatArray?

    private external fun nativeExtractPath3(
        fontPtr: Long,
        codepoint: Int,
        tag1: Int,
        value1: Float,
        tag2: Int,
        value2: Float,
        tag3: Int,
        value3: Float
    ): FloatArray?

    private external fun nativeExtractPathN(
        fontPtr: Long,
        codepoint: Int,
        tags: IntArray,
        values: FloatArray
    ): FloatArray?
}
