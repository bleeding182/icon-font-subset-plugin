package com.davidmedenjak.fontsubsetting.runtime

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

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
 * val extractor = remember {
 *     FontPathExtractor.fromResource(context, R.font.my_font)
 * }
 *
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
 *     axes = mapOf("FILL" to fillValue)
 * )
 * ```
 *
 * ### Option 2: Manipulate GlyphState directly (imperative)
 * ```kotlin
 * val glyph = rememberGlyph(extractor, '★', size = 48.dp)
 * glyph?.let {
 *     // Animate individual axes
 *     LaunchedEffect(fillValue) {
 *         it.updateAxes { put("FILL", fillValue) }
 *     }
 *
 *     // Or update multiple axes at once
 *     it.updateAxes {
 *         put("FILL", 1f)
 *         put("wght", 700f)
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
 */
@Stable
class GlyphState internal constructor(
    private val extractor: FontPathExtractor,
    private val codepoint: Int
) {
    // Layer 1: Path and metadata management
    private val pathData = PathData()

    // Layer 2: Axis configuration management
    private val axes = mutableMapOf<String, Float>()
    private var targetSizePx: Float = 0f

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
     * The Compose path for this glyph
     */
    internal val composePath: Path get() = pathData.composePath

    /**
     * Sets a single axis value and updates the path.
     * This is useful for animating individual axes.
     *
     * @param axis Axis tag (e.g., "FILL", "wght")
     * @param value Axis value
     * @return true if successful, false if glyph not found
     */
    fun setAxis(axis: String, value: Float): Boolean {
        axes[axis] = value
        return updatePath()
    }

    /**
     * Sets multiple axis values and updates the path.
     * This replaces all current axis values.
     *
     * @param newAxes Map of axis tags to values
     * @return true if successful, false if glyph not found
     */
    fun setAxes(newAxes: Map<String, Float>): Boolean {
        axes.clear()
        axes.putAll(newAxes)
        return updatePath()
    }

    /**
     * Updates the axes using a lambda receiver.
     * This is useful for updating multiple axes at once without allocating intermediate maps.
     *
     * @param block Lambda with the axes map as receiver
     * @return true if successful, false if glyph not found
     */
    fun updateAxes(block: MutableMap<String, Float>.() -> Unit): Boolean {
        axes.apply(block)
        return updatePath()
    }

    /**
     * Removes an axis value and updates the path.
     *
     * @param axis Axis tag to remove
     * @return true if successful, false if glyph not found
     */
    fun removeAxis(axis: String): Boolean {
        axes.remove(axis)
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
    fun getAxes(): Map<String, Float> = axes.toMap()

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
     * @return true if successful, false if glyph not found
     */
    private fun updatePath(): Boolean {
        // Get raw data from native code
        val rawData = if (axes.isEmpty()) {
            extractor.nativeExtractGlyphPathInternal(codepoint)
        } else {
            val tags = axes.keys.toTypedArray()
            val values = axes.values.toFloatArray()
            extractor.nativeExtractGlyphPathWithVariationsInternal(codepoint, tags, values)
        } ?: return false

        // Validate minimum size for header
        if (rawData.size < 7) return false

        // Parse header to get bounding box: [numCommands, advanceWidth, unitsPerEm, minX, minY, maxX, maxY]
        val glyphMinX = rawData[3]
        val glyphMinY = rawData[4]
        val glyphMaxX = rawData[5]
        val glyphMaxY = rawData[6]

        // Calculate transformation for the target size
        val glyphCenterX = (glyphMinX + glyphMaxX) / 2f
        val glyphCenterY = (glyphMinY + glyphMaxY) / 2f
        val glyphWidth = glyphMaxX - glyphMinX
        val glyphHeight = glyphMaxY - glyphMinY
        val maxGlyphDimension = maxOf(glyphWidth, glyphHeight)
        val scaleValue = if (maxGlyphDimension > 0f && targetSizePx > 0f) {
            targetSizePx / maxGlyphDimension
        } else {
            1f
        }

        // Calculate translation to center the glyph
        val translateX = if (targetSizePx > 0f) {
            targetSizePx / 2f - glyphCenterX * scaleValue
        } else {
            -glyphCenterX
        }
        val translateY = if (targetSizePx > 0f) {
            targetSizePx / 2f + glyphCenterY * scaleValue  // Add because Y will be flipped
        } else {
            glyphCenterY
        }

        // Delegate to PathData to update the path with transformation
        return pathData.update(
            rawData,
            scaleX = scaleValue,
            scaleY = -scaleValue,  // Flip Y coordinate system
            translateX = translateX,
            translateY = translateY
        )
    }

    /**
     * Initial update with optional axis values and size.
     * Called during initialization.
     */
    internal fun initialize(
        initialAxes: Map<String, Float> = emptyMap(),
        sizePx: Float = 0f
    ): Boolean {
        axes.clear()
        axes.putAll(initialAxes)
        targetSizePx = sizePx
        return updatePath()
    }
}

/**
 * Remembers a glyph and its path state from a font.
 * The glyph path is cached and efficiently updated when axis values or size change.
 * The path is pre-scaled to the target size for efficient rendering without canvas scaling.
 *
 * Example usage:
 * ```
 * val extractor = remember { FontPathExtractor.fromResource(context, R.font.my_font) }
 * val glyph = rememberGlyph(
 *     extractor,
 *     '★',
 *     size = 48.dp,
 *     axes = mapOf("FILL" to 1f, "wght" to 400f)
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
    axes: Map<String, Float> = emptyMap()
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
    axes: Map<String, Float> = emptyMap()
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
