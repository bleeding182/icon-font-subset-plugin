package com.davidmedenjak.fontsubsetting.runtime

import android.graphics.Path
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * Native HarfBuzz-based glyph outline extractor.
 *
 * Loads a font from raw bytes and extracts glyph outlines as [Path] objects,
 * bypassing Android's Paint/Typeface stack. Variable font axes work on all API levels.
 *
 * Thread-safe: extraction methods are synchronized, allowing background batch
 * extraction alongside main-thread rendering.
 */
class HarfBuzzGlyphExtractor internal constructor(fontData: ByteArray) : AutoCloseable {

    private var handle: Long
    private val lock = ReentrantLock()

    init {
        ensureLibraryLoaded()
        handle = nativeCreateFont(fontData)
        check(handle != 0L) { "Failed to create HarfBuzz font from data" }
    }

    fun extractPath(codepoint: Int, axisTags: Array<String>, axisValues: FloatArray): Path? =
        lock.withLock {
            val data = nativeExtractGlyph(handle, codepoint, axisTags, axisValues) ?: return null
            data.toAndroidPath()
        }

    fun extractPathBatch(
        codepoint: Int,
        axisTags: Array<String>,
        axisValues: FloatArray,
        numSets: Int,
    ): List<Path>? = lock.withLock {
        val data = nativeExtractGlyphBatch(handle, codepoint, axisTags, axisValues, numSets)
            ?: return null
        parseBatchResult(data, numSets)
    }

    override fun close() {
        lock.withLock {
            if (handle != 0L) {
                nativeDestroyFont(handle)
                handle = 0L
            }
        }
    }

    companion object {
        private val LOAD_LOCK = Any()

        @Volatile
        private var loaded = false

        @Volatile
        private var loadError: Throwable? = null

        fun create(fontData: ByteArray): HarfBuzzGlyphExtractor = HarfBuzzGlyphExtractor(fontData)

        /**
         * Returns true if the native library is available on this platform.
         * Never throws — useful for probing support (e.g. Paparazzi/Roborazzi checks).
         */
        fun isNativeLibraryAvailable(): Boolean = try {
            ensureLibraryLoaded()
            true
        } catch (e: Throwable) {
            false
        }

        internal fun ensureLibraryLoaded() {
            if (loaded) return
            synchronized(LOAD_LOCK) {
                if (loaded) return
                loadError?.let { throw it }
                try {
                    loadNativeLibrary()
                    loaded = true
                } catch (t: Throwable) {
                    loadError = t
                    throw t
                }
            }
        }

        private fun loadNativeLibrary() {
            System.loadLibrary("glyphruntime")
        }
    }

    private external fun nativeCreateFont(data: ByteArray): Long
    private external fun nativeDestroyFont(handle: Long)
    private external fun nativeExtractGlyph(
        handle: Long, codepoint: Int,
        axisTags: Array<String>, axisValues: FloatArray,
    ): FloatArray?
    private external fun nativeExtractGlyphBatch(
        handle: Long, codepoint: Int,
        axisTags: Array<String>, axisValues: FloatArray, numSets: Int,
    ): FloatArray?
}

private fun parseBatchResult(data: FloatArray, numSets: Int): List<Path> {
    val paths = ArrayList<Path>(numSets)
    var offset = 0
    for (i in 0 until numSets) {
        val count = data[offset].toInt()
        offset++
        paths.add(data.toAndroidPath(offset, count))
        offset += count
    }
    return paths
}

private fun FloatArray.toAndroidPath(start: Int = 0, length: Int = size - start): Path {
    val path = Path()
    var i = start
    val end = start + length
    while (i < end) {
        when (this[i].toInt()) {
            0 -> { path.moveTo(this[i + 1], this[i + 2]); i += 3 }
            1 -> { path.lineTo(this[i + 1], this[i + 2]); i += 3 }
            2 -> { path.quadTo(this[i + 1], this[i + 2], this[i + 3], this[i + 4]); i += 5 }
            3 -> {
                path.cubicTo(
                    this[i + 1], this[i + 2],
                    this[i + 3], this[i + 4],
                    this[i + 5], this[i + 6],
                )
                i += 7
            }
            4 -> { path.close(); i += 1 }
            else -> i++ // Skip unknown commands
        }
    }
    return path
}
