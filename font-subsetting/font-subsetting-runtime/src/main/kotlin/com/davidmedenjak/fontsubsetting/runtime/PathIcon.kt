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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawStyle
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
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
 * - A single reusable Compose [Path] (Layer 1: PathData)
 * - Variable font axis configuration (Layer 2: GlyphState)
 *
 * ### `Glyph()` - Render the glyph
 * Renders a [GlyphState] as a vector icon with proper scaling and centering.
 *
 * ## Basic Usage
 *
 * ```kotlin
 * val extractor = remember {
 *     FontPathExtractor.fromResource(context, R.font.my_font)
 * }
 *
 * // Static glyph
 * val glyph = rememberGlyph(extractor, '★')
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
 *     axes = mapOf("FILL" to fillValue)
 * )
 * ```
 *
 * ### Option 2: Manipulate GlyphState directly (imperative)
 * ```kotlin
 * val glyph = rememberGlyph(extractor, '★')
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
 *     // Or replace all axes (clear + set)
 *     it.updateAxes {
 *         clear()
 *         put("FILL", 0f)
 *         put("wght", 300f)
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
 * - Manages a single Compose Path and glyph metrics
 * - Handles path reuse with rewind()
 * - Parses raw native data and fills path directly
 * - Agnostic to variable font axes
 *
 * **Layer 2: GlyphState** (public)
 * - Manages variable font axis configuration
 * - Provides clean API for axis manipulation
 * - Delegates path updates to PathData
 * - Handles native code interaction
 *
 * ## Performance Characteristics
 *
 * - **Single Path**: Only one Compose [Path] object per glyph
 * - **No Intermediate Objects**: Direct parsing into path operations
 * - **Efficient Updates**: Uses [Path.rewind()] for axis changes
 * - **Smart Caching**: [GlyphState] remembered per extractor + codepoint
 * - **Clean Separation**: Independent layers for path and axis management
 *
 * @see rememberGlyph
 * @see Glyph
 * @see GlyphState
 */

/**
 * Layer 2: Glyph State with Axis Management
 *
 * Represents a glyph with a single cached Compose Path and metrics.
 * The path is reused and refilled directly from native code when axis values change.
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
     * Advance height for text layout
     */
    val advanceHeight: Float get() = pathData.advanceHeight

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
     * Updates the glyph path with the current axis configuration.
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

        // Delegate to PathData to update the path
        return pathData.update(rawData)
    }

    /**
     * Initial update with optional axis values.
     * Called during initialization.
     */
    internal fun initialize(initialAxes: Map<String, Float> = emptyMap()): Boolean {
        axes.clear()
        axes.putAll(initialAxes)
        return updatePath()
    }
}

/**
 * Remembers a glyph and its path state from a font.
 * The glyph path is cached and efficiently updated when axis values change.
 *
 * Example usage:
 * ```
 * val extractor = remember { FontPathExtractor.fromResource(context, R.font.my_font) }
 * val glyph = rememberGlyph(extractor, '★', axes = mapOf("FILL" to 1f, "wght" to 400f))
 *
 * glyph?.let {
 *     Glyph(it, size = 48.dp, tint = Color.Blue)
 * }
 * ```
 *
 * @param extractor Font path extractor instance
 * @param char Character to render
 * @param axes Variable font axis values (e.g., "FILL" to 1f, "wght" to 400f)
 * @return GlyphState instance, or null if glyph not found
 */
@Composable
fun rememberGlyph(
    extractor: FontPathExtractor,
    char: Char,
    axes: Map<String, Float> = emptyMap()
): GlyphState? = rememberGlyph(extractor, char.code, axes)

/**
 * Remembers a glyph and its path state from a font using a codepoint.
 *
 * @param extractor Font path extractor instance
 * @param codepoint Unicode codepoint of the glyph
 * @param axes Variable font axis values (e.g., "FILL" to 1f, "wght" to 400f)
 * @return GlyphState instance, or null if glyph not found
 */
@Composable
fun rememberGlyph(
    extractor: FontPathExtractor,
    codepoint: Int,
    axes: Map<String, Float> = emptyMap()
): GlyphState? {
    var glyphState by remember(extractor, codepoint) {
        mutableStateOf<GlyphState?>(null)
    }

    LaunchedEffect(extractor, codepoint, axes) {
        val glyph = glyphState ?: GlyphState(extractor, codepoint).also { glyphState = it }
        glyph.initialize(axes)
    }

    return glyphState
}

/**
 * Renders a glyph as a vector icon.
 *
 * Use `rememberGlyph()` to create the GlyphState instance.
 *
 * @param glyph The glyph state to render
 * @param modifier Modifier to apply to the icon
 * @param size Size of the icon (default: 24.dp)
 * @param tint Color to tint the icon (default: Color.Black)
 * @param style Drawing style (Fill or Stroke)
 */
@Composable
fun Glyph(
    glyph: GlyphState,
    modifier: Modifier = Modifier,
    size: Dp = 24.dp,
    tint: Color = Color.Black,
    style: DrawStyle = Fill
) {
    Canvas(modifier = modifier.size(size)) {
        val canvasSize = this.size.minDimension

        // Calculate the glyph's bounding box center
        val glyphCenterX = (glyph.minX + glyph.maxX) / 2f
        val glyphCenterY = (glyph.minY + glyph.maxY) / 2f

        // Calculate scale to fit the glyph in the canvas
        val glyphWidth = glyph.maxX - glyph.minX
        val glyphHeight = glyph.maxY - glyph.minY
        val maxGlyphDimension = maxOf(glyphWidth, glyphHeight)
        val scaleValue = if (maxGlyphDimension > 0f) canvasSize / maxGlyphDimension else canvasSize

        // Center the canvas and offset by the glyph's center
        // When we flip Y with negative scale, we need to account for that in translation
        val translateX = canvasSize / 2f - glyphCenterX * scaleValue
        val translateY =
            canvasSize / 2f + glyphCenterY * scaleValue  // Add because Y will be flipped

        translate(translateX, translateY) {
            // Use negative Y scale to flip coordinate system
            scale(scaleX = scaleValue, scaleY = -scaleValue, pivot = Offset.Zero) {
                drawPath(
                    path = glyph.composePath,
                    color = tint,
                    style = style
                )
            }
        }
    }
}
