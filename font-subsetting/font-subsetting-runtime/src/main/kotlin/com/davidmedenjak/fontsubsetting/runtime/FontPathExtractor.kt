package com.davidmedenjak.fontsubsetting.runtime

import android.content.Context
import android.util.Log
import androidx.annotation.FontRes
import java.io.InputStream

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
        variations: Map<String, Float> = emptyMap()
    ): FloatArray? {
        checkNotClosed()

        if (variations.isEmpty()) {
            return nativeExtractGlyphPath(nativeFontPtr, codepoint)
        }

        val tags = variations.keys.toTypedArray()
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
        variationTags: Array<String>,
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
        variationTags: Array<String>,
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
        variationTags: Array<String>,
        variationValues: FloatArray
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
        variationTags: Array<String>,
        variationValues: FloatArray
    ): FloatArray? {
        checkNotClosed()
        return nativeExtractGlyphPathFromHandle(glyphHandlePtr, variationTags, variationValues)
    }
}
