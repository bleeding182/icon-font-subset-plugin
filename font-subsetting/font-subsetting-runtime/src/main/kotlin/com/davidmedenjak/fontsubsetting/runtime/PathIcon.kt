package com.davidmedenjak.fontsubsetting.runtime

import android.content.Context
import androidx.annotation.FontRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawStyle
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import java.io.InputStream

/**
 * Remembers a [FontPathExtractor] and automatically cleans up native resources when disposed.
 *
 * This is the recommended way to create a FontPathExtractor in Compose, as it ensures
 * proper cleanup of native memory when the composable leaves the composition.
 *
 * Example usage:
 * ```kotlin
 * val extractor = rememberFontPathExtractor(R.font.my_font)
 * // Static glyph - path is pre-scaled to 48dp
 * val glyph = rememberGlyph(extractor, '★', size = 48.dp)
 * ```
 *
 * @param resourceId Font resource ID
 * @param context Optional context (defaults to LocalContext.current)
 * @return FontPathExtractor instance, or null if loading failed
 */
@Composable
fun rememberFontPathExtractor(
    @FontRes resourceId: Int,
    context: Context = LocalContext.current
): FontPathExtractor? {
    val extractor = remember(resourceId) {
        try {
            FontPathExtractor.fromResource(context, resourceId)
        } catch (e: Exception) {
            null
        }
    }

    DisposableEffect(extractor) {
        onDispose {
            extractor?.close()
        }
    }

    return extractor
}

/**
 * Remembers a [FontPathExtractor] from raw bytes and automatically cleans up native resources when disposed.
 *
 * @param fontData Font file bytes
 * @return FontPathExtractor instance, or null if loading failed
 */
@Composable
fun rememberFontPathExtractor(fontData: ByteArray): FontPathExtractor? {
    val extractor = remember(fontData) {
        try {
            FontPathExtractor.fromBytes(fontData)
        } catch (e: Exception) {
            null
        }
    }

    DisposableEffect(extractor) {
        onDispose {
            extractor?.close()
        }
    }

    return extractor
}

/**
 * Remembers a [FontPathExtractor] from an input stream and automatically cleans up native resources when disposed.
 *
 * Note: The input stream will be read and closed immediately during initial composition.
 *
 * @param inputStream Font file input stream
 * @return FontPathExtractor instance, or null if loading failed
 */
@Composable
fun rememberFontPathExtractor(inputStream: InputStream): FontPathExtractor? {
    val extractor = remember(inputStream) {
        try {
            FontPathExtractor.fromStream(inputStream)
        } catch (e: Exception) {
            null
        }
    }

    DisposableEffect(extractor) {
        onDispose {
            extractor?.close()
        }
    }

    return extractor
}

/**
 * # Font Subsetting Runtime - Compose API
 *
 * This module provides an efficient way to render font glyphs as vector paths in Jetpack Compose.
 * The API is built around two main composables with a clean layered architecture:
 *
 * ## Core API
 *
 * ### `rememberGlyph()` - Create and cache glyph state
 * Creates a [GlyphState] instance that manages:
 * - A single reusable Compose [Path] with pre-scaled coordinates (Layer 1: PathData)
 * - Variable font axis configuration and target size (Layer 2: GlyphState)
 *
 * ### `Glyph()` - Render the glyph
 * Renders a [GlyphState] as a vector icon. The path is already scaled to the target size.
 *
 * ## Basic Usage
 *
 * ```kotlin
 * val extractor = rememberFontPathExtractor(R.font.my_font)
 * // Static glyph - path is pre-scaled to 48dp
 * val glyph = rememberGlyph(extractor, '★', size = 48.dp)
 * glyph?.let {
 *     Glyph(it, size = 48.dp, tint = Color.Blue)
 * }
 * ```
 *
 * ## Variable Font Animation
 *
 * The improved API provides multiple ways to animate axes:
 *
 * ### Option 1: Pass axes to rememberGlyph (reactive)
 * ```kotlin
 * val fillValue by animateFloatAsState(targetValue = 1f)
 * val glyph = rememberGlyph(
 *     extractor = extractor,
 *     char = '★',
 *     size = 48.dp,
 *     axes = mapOf(AxisTag.FILL to fillValue)
 * )
 * ```
 *
 * ### Option 2: Manipulate GlyphState directly (imperative)
 * ```kotlin
 * val glyph = rememberGlyph(extractor, '★', size = 48.dp)
 * glyph?.let {
 *     // Animate individual axes
 *     LaunchedEffect(fillValue) {
 *         it.updateAxes { put(AxisTag.FILL, fillValue) }
 *     }
 *
 *     // Or update multiple axes at once
 *     it.updateAxes {
 *         put(AxisTag.FILL, 1f)
 *         put(AxisTag.WGHT, 700f)
 *     }
 *
 *     Glyph(it, size = 48.dp, tint = Color.Red)
 * }
 * ```
 *
 * ## Architecture
 *
 * The implementation uses a clean two-layer architecture:
 *
 * **Layer 1: PathData** (internal)
 * - Manages a single Compose Path with pre-scaled coordinates
 * - Handles path reuse with rewind()
 * - Parses raw native data and applies transformations during path construction
 * - Agnostic to variable font axes
 *
 * **Layer 2: GlyphState** (public)
 * - Manages variable font axis configuration and target size
 * - Provides clean API for axis manipulation and size updates
 * - Calculates transformations based on glyph bounds and target size
 * - Delegates path updates to PathData
 * - Handles native code interaction
 *
 * ## Performance Characteristics
 *
 * - **Single Path**: Only one Compose [Path] object per glyph
 * - **Pre-scaled Coordinates**: Path is built in pixel space, no canvas scaling needed
 * - **Efficient Updates**: Uses [Path.rewind()] for axis/size changes
 * - **Smart Caching**: [GlyphState] remembered per extractor + codepoint
 * - **Clean Separation**: Independent layers for path and axis/size management
 * - **Correct Stroke Widths**: Since path is in pixel space, stroke widths work correctly
 *
 * @see rememberGlyph
 * @see Glyph
 * @see GlyphState
 */

/**
 * Layer 2: Glyph State with Axis Management
 *
 * Represents a glyph with a single cached Compose Path and metrics.
 * The path is reused and refilled directly from native code when axis values or size change.
 *
 * This class is lightweight - it just manages the path and axis state. The actual
 * native HarfBuzz objects are shared via the FontPathExtractor's SharedFontData.
 */
@Stable
class GlyphState internal constructor(
    private val extractor: FontPathExtractor,
    private val codepoint: Int
) {
    // Layer 1: Path and metadata management
    private val pathData = PathData()

    // Layer 2: Axis configuration management (using integer tags for efficiency)
    private val axes = mutableMapOf<Int, Float>()
    private var targetSizePx: Float = 0f

    // Observable state to trigger recomposition when path changes
    private var pathVersion by mutableStateOf(0)

    /**
     * Width of the glyph bounding box
     */
    val width: Float get() = pathData.width

    /**
     * Height of the glyph bounding box
     */
    val height: Float get() = pathData.height

    /**
     * Advance width for text layout
     */
    val advanceWidth: Float get() = pathData.advanceWidth

    /**
     * Units per EM from the font
     */
    val unitsPerEm: Int get() = pathData.unitsPerEm

    /**
     * Minimum X coordinate of the glyph bounds
     */
    val minX: Float get() = pathData.minX

    /**
     * Minimum Y coordinate of the glyph bounds
     */
    val minY: Float get() = pathData.minY

    /**
     * Maximum X coordinate of the glyph bounds
     */
    val maxX: Float get() = pathData.maxX

    /**
     * Maximum Y coordinate of the glyph bounds
     */
    val maxY: Float get() = pathData.maxY

    /**
     * The Compose path for this glyph.
     * Reading this property makes the composition observe path changes.
     */
    internal val composePath: Path
        get() {
            // Access pathVersion to make this observable
            @Suppress("UNUSED_EXPRESSION")
            pathVersion
            return pathData.composePath
        }

    /**
     * Sets a single axis value and updates the path.
     * This is useful for animating individual axes.
     *
     * Uses integer axis tags from [AxisTag] for zero-allocation performance.
     *
     * @param axisTag Axis tag (e.g., [AxisTag.FILL], [AxisTag.WGHT])
     * @param value Axis value
     * @return true if successful, false if glyph not found
     */
    fun setAxis(axisTag: Int, value: Float): Boolean {
        // Early exit if value hasn't changed (reduces redundant updates)
        if (axes[axisTag] == value) {
            return true
        }
        axes[axisTag] = value
        return updatePath()
    }

    /**
     * Sets a single axis value using a string tag and updates the path.
     *
     * Note: This allocates a string conversion. For performance-critical code,
     * prefer using the integer overload with [AxisTag] constants.
     *
     * @param axisTag 4-character axis tag string (e.g., "FILL", "wght")
     * @param value Axis value
     * @return true if successful, false if glyph not found
     */
    fun setAxis(axisTag: String, value: Float): Boolean {
        return setAxis(AxisTag.fromString(axisTag), value)
    }

    /**
     * Sets multiple axis values and updates the path.
     * This replaces all current axis values.
     *
     * @param newAxes Map of integer axis tags to values
     * @return true if successful, false if glyph not found
     */
    fun setAxes(newAxes: Map<Int, Float>): Boolean {
        axes.clear()
        axes.putAll(newAxes)
        return updatePath()
    }

    /**
     * Updates the axes using a lambda receiver.
     * This is useful for updating multiple axes at once without allocating intermediate maps.
     *
     * Example:
     * ```kotlin
     * glyph.updateAxes {
     *     put(AxisTag.FILL, fillValue)
     *     put(AxisTag.WGHT, weightValue)
     * }
     * ```
     *
     * @param block Lambda with the axes map as receiver
     * @return true if successful, false if glyph not found
     */
    fun updateAxes(block: MutableMap<Int, Float>.() -> Unit): Boolean {
        axes.apply(block)
        return updatePath()
    }

    /**
     * Removes an axis value and updates the path.
     *
     * @param axisTag Axis tag to remove
     * @return true if successful, false if glyph not found
     */
    fun removeAxis(axisTag: Int): Boolean {
        axes.remove(axisTag)
        return updatePath()
    }

    /**
     * Clears all axis values and updates the path to default.
     *
     * @return true if successful, false if glyph not found
     */
    fun clearAxes(): Boolean {
        axes.clear()
        return updatePath()
    }

    /**
     * Gets the current axis values.
     */
    fun getAxes(): Map<Int, Float> = axes.toMap()

    /**
     * Sets the target size for rendering and updates the path.
     * The path will be scaled and transformed to fit this size.
     *
     * @param sizePx Target size in pixels
     * @return true if successful, false if glyph not found
     */
    fun setSize(sizePx: Float): Boolean {
        targetSizePx = sizePx
        return updatePath()
    }

    /**
     * Updates the glyph path with the current axis configuration and size.
     * Internal method that fetches raw data and delegates to PathData.
     *
     * Uses zero-allocation JNI methods for 0-3 axes (covers 95%+ of use cases).
     * Calls directly through the extractor which uses SharedFontData for efficiency.
     *
     * @return true if successful, false if glyph not found
     */
    private fun updatePath(): Boolean {
        // Calculate hash of current axes to detect changes
        val currentAxesHash = axes.hashCode()

        // Only fetch new raw data if axes have changed
        // Use specialized zero-allocation JNI methods based on axis count
        val rawData = if (cachedRawData == null || cachedAxesHash != currentAxesHash) {
            val newRawData = when (axes.size) {
                0 -> {
                    // No axes - static glyph (zero allocation)
                    extractor.extractGlyphPathDirect(codepoint)
                }

                1 -> {
                    // Single axis (zero allocation)
                    val (tag, value) = axes.entries.first()
                    extractor.extractGlyphPathDirect1(codepoint, tag, value)
                }

                2 -> {
                    // Two axes (zero allocation)
                    val iter = axes.entries.iterator()
                    val (tag1, value1) = iter.next()
                    val (tag2, value2) = iter.next()
                    extractor.extractGlyphPathDirect2(
                        codepoint, tag1, value1, tag2, value2
                    )
                }

                3 -> {
                    // Three axes (zero allocation)
                    val iter = axes.entries.iterator()
                    val (tag1, value1) = iter.next()
                    val (tag2, value2) = iter.next()
                    val (tag3, value3) = iter.next()
                    extractor.extractGlyphPathDirect3(
                        codepoint, tag1, value1, tag2, value2, tag3, value3
                    )
                }

                else -> {
                    // 4+ axes (rare, fallback to array allocation)
                    val tags = axes.keys.toIntArray()
                    val values = axes.values.toFloatArray()
                    extractor.extractGlyphPathDirectN(codepoint, tags, values)
                }
            } ?: return false

            cachedRawData = newRawData
            cachedAxesHash = currentAxesHash
            newRawData
        } else {
            cachedRawData!!
        }

        // Validate minimum size for header
        if (rawData.size < 7) return false

        // Parse header to get bounding box: [numCommands, advanceWidth, unitsPerEm, minX, minY, maxX, maxY]
        val glyphMinX = rawData[3]
        val glyphMinY = rawData[4]
        val glyphMaxX = rawData[5]
        val glyphMaxY = rawData[6]

        // Calculate hash of transform parameters to detect changes
        val transformHash = (glyphMinX.toBits() * 31 + glyphMinY.toBits()) * 31 +
                (glyphMaxX.toBits() * 31 + glyphMaxY.toBits()) * 31 +
                targetSizePx.toBits()

        // Skip transformation calculation if nothing changed
        val (scaleValue, translateX, translateY) = if (cachedTransformHash != transformHash) {
            // Calculate transformation for the target size
            val glyphCenterX = (glyphMinX + glyphMaxX) / 2f
            val glyphCenterY = (glyphMinY + glyphMaxY) / 2f
            val glyphWidth = glyphMaxX - glyphMinX
            val glyphHeight = glyphMaxY - glyphMinY
            val maxGlyphDimension = maxOf(glyphWidth, glyphHeight)
            val scale = if (maxGlyphDimension > 0f && targetSizePx > 0f) {
                targetSizePx / maxGlyphDimension
            } else {
                1f
            }

            // Calculate translation to center the glyph
            val transX = if (targetSizePx > 0f) {
                targetSizePx / 2f - glyphCenterX * scale
            } else {
                -glyphCenterX
            }
            val transY = if (targetSizePx > 0f) {
                targetSizePx / 2f + glyphCenterY * scale  // Add because Y will be flipped
            } else {
                glyphCenterY
            }

            cachedTransformHash = transformHash
            cachedScale = scale
            cachedTranslateX = transX
            cachedTranslateY = transY
            Triple(scale, transX, transY)
        } else {
            // Reuse cached transformation
            Triple(cachedScale, cachedTranslateX, cachedTranslateY)
        }

        // Delegate to PathData to update the path with transformation
        val success = pathData.update(
            rawData,
            scaleX = scaleValue,
            scaleY = -scaleValue,  // Flip Y coordinate system
            translateX = translateX,
            translateY = translateY
        )

        // Increment version to trigger recomposition
        if (success) {
            pathVersion++
        }

        return success
    }

    /**
     * Initial update with optional axis values and size.
     * Called during initialization.
     */
    internal fun initialize(
        initialAxes: Map<Int, Float> = emptyMap(),
        sizePx: Float = 0f
    ): Boolean {
        axes.clear()
        axes.putAll(initialAxes)
        targetSizePx = sizePx
        return updatePath()
    }

    // Cache to avoid redundant JNI calls and path rebuilds
    private var cachedRawData: FloatArray? = null
    private var cachedAxesHash: Int = 0
    private var cachedTransformHash: Int = 0
    private var cachedScale: Float = 1f
    private var cachedTranslateX: Float = 0f
    private var cachedTranslateY: Float = 0f
}

/**
 * Remembers a glyph and its path state from a font.
 * The glyph path is cached and efficiently updated when axis values or size change.
 * The path is pre-scaled to the target size for efficient rendering without canvas scaling.
 *
 * Example usage:
 * ```
 * val extractor = rememberFontPathExtractor(R.font.my_font)
 * val glyph = rememberGlyph(
 *     extractor,
 *     '★',
 *     size = 48.dp,
 *     axes = mapOf(AxisTag.FILL to 1f, AxisTag.WGHT to 400f)
 * )
 *
 * glyph?.let {
 *     Glyph(it, size = 48.dp, tint = Color.Blue)
 * }
 * ```
 *
 * @param extractor Font path extractor instance
 * @param char Character to render
 * @param size Target size for the glyph (the path will be pre-scaled to this size)
 * @param axes Variable font axis values (e.g., "FILL" to 1f, "wght" to 400f)
 * @return GlyphState instance, or null if glyph not found
 */
@Composable
fun rememberGlyph(
    extractor: FontPathExtractor,
    char: Char,
    size: Dp = 24.dp,
    axes: Map<Int, Float> = emptyMap()
): GlyphState? = rememberGlyph(extractor, char.code, size, axes)

/**
 * Remembers a glyph and its path state from a font using a codepoint.
 *
 * @param extractor Font path extractor instance
 * @param codepoint Unicode codepoint of the glyph
 * @param size Target size for the glyph (the path will be pre-scaled to this size)
 * @param axes Variable font axis values (e.g., "FILL" to 1f, "wght" to 400f)
 * @return GlyphState instance, or null if glyph not found
 */
@Composable
fun rememberGlyph(
    extractor: FontPathExtractor,
    codepoint: Int,
    size: Dp = 24.dp,
    axes: Map<Int, Float> = emptyMap()
): GlyphState? {
    val sizePx = with(LocalDensity.current) { size.toPx() }

    var glyphState by remember(extractor, codepoint) {
        mutableStateOf<GlyphState?>(null)
    }

    LaunchedEffect(extractor, codepoint, sizePx, axes) {
        val glyph = glyphState ?: GlyphState(extractor, codepoint).also { glyphState = it }
        glyph.initialize(axes, sizePx)
    }

    return glyphState
}

/**
 * Renders a glyph as a vector icon.
 *
 * Use `rememberGlyph()` to create the GlyphState instance with the target size.
 * The path is already scaled to pixel space, so stroke widths work correctly.
 *
 * @param glyph The glyph state to render (path is already scaled)
 * @param size The display size (should match the size passed to rememberGlyph)
 * @param modifier Modifier to apply to the icon
 * @param tint Color to tint the icon (default: Color.Black)
 * @param style Drawing style (Fill or Stroke with pixel-based width)
 */
@Composable
fun Glyph(
    glyph: GlyphState,
    size: Dp,
    modifier: Modifier = Modifier,
    tint: Color = Color.Black,
    style: DrawStyle = Fill
) {
    Canvas(modifier = modifier.size(size)) {
        // Path is already scaled and centered in pixel space - just draw it!
        drawPath(
            path = glyph.composePath,
            color = tint,
            style = style
        )
    }
}
