package com.davidmedenjak.fontsubsetting.runtime

import android.content.Context
import android.util.Log
import androidx.annotation.RawRes
import java.io.InputStream

/**
 * Extracts vector paths from font glyphs using HarfBuzz.
 * This allows rendering font glyphs as vector paths in Compose.
 *
 * Use `rememberGlyph()` composable to render glyphs efficiently.
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
     * Extracts the raw glyph path data for internal use.
     *
     * @param codepoint Unicode codepoint of the glyph
     * @param variations Map of axis tag to value (empty for static glyphs)
     * @return Raw float array containing path data, or null if not found
     */
    internal fun extractGlyphPathData(
        codepoint: Int,
        variations: Map<String, Float> = emptyMap()
    ): FloatArray? {
        if (variations.isEmpty()) {
            return nativeExtractGlyphPath(fontData, codepoint)
        }

        val tags = variations.keys.toTypedArray()
        val values = variations.values.toFloatArray()
        return nativeExtractGlyphPathWithVariations(fontData, codepoint, tags, values)
    }

    /**
     * Extracts the raw glyph path for a codepoint without variations.
     */
    internal fun nativeExtractGlyphPathInternal(codepoint: Int): FloatArray? {
        return nativeExtractGlyphPath(fontData, codepoint)
    }

    /**
     * Extracts the raw glyph path for a codepoint with axis variations.
     */
    internal fun nativeExtractGlyphPathWithVariationsInternal(
        codepoint: Int,
        variationTags: Array<String>,
        variationValues: FloatArray
    ): FloatArray? {
        return nativeExtractGlyphPathWithVariations(
            fontData,
            codepoint,
            variationTags,
            variationValues
        )
    }

    private external fun nativeExtractGlyphPath(fontData: ByteArray, codepoint: Int): FloatArray?
    private external fun nativeExtractGlyphPathWithVariations(
        fontData: ByteArray,
        codepoint: Int,
        variationTags: Array<String>,
        variationValues: FloatArray
    ): FloatArray?
}
