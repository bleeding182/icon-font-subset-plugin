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
import androidx.compose.ui.graphics.PathFillType
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
 * The API is built around two main composables:
 *
 * ## Core API
 *
 * ### `rememberGlyph()` - Create and cache glyph state
 * Creates a [GlyphState] instance that holds a single reusable Compose [Path]. The path is
 * efficiently updated (rewind + refill directly from native code) when variable font axes change.
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
 * The API automatically handles path updates when axes change:
 *
 * ```kotlin
 * val infiniteTransition = rememberInfiniteTransition()
 * val fillValue by infiniteTransition.animateFloat(
 *     initialValue = 0f,
 *     targetValue = 1f,
 *     animationSpec = infiniteRepeatable(
 *         animation = tween(durationMillis = 1500),
 *         repeatMode = RepeatMode.Reverse
 *     )
 * )
 *
 * // Path is automatically refilled when fillValue changes
 * val glyph = rememberGlyph(
 *     extractor = extractor,
 *     char = '★',
 *     axes = mapOf("FILL" to fillValue, "wght" to 400f)
 * )
 * glyph?.let {
 *     Glyph(it, size = 48.dp, tint = Color.Red)
 * }
 * ```
 *
 * ## Performance Characteristics
 *
 * - **Single Path**: Only one Compose [Path] object, no intermediate allocations
 * - **Direct JNI Calls**: Native code fills the Path directly via callbacks
 * - **Efficient Updates**: Uses [Path.rewind()] + direct native fill
 * - **Smart Caching**: [GlyphState] is remembered per extractor + codepoint
 *
 * @see rememberGlyph
 * @see Glyph
 * @see GlyphState
 */

/**
 * Represents a glyph with a single cached Compose Path and metrics.
 * The path is reused and refilled directly from native code when axis values change.
 */
@Stable
class GlyphState internal constructor(
    private val extractor: FontPathExtractor,
    private val codepoint: Int
) {
    internal val path = Path().apply {
        fillType = PathFillType.EvenOdd
    }

    var advanceWidth: Float = 0f
        internal set
    var advanceHeight: Float = 0f
        internal set
    var unitsPerEm: Int = 0
        internal set
    var minX: Float = 0f
        internal set
    var minY: Float = 0f
        internal set
    var maxX: Float = 0f
        internal set
    var maxY: Float = 0f
        internal set

    /**
     * Width of the glyph bounding box
     */
    val width: Float get() = maxX - minX

    /**
     * Height of the glyph bounding box
     */
    val height: Float get() = maxY - minY

    /**
     * The Compose path for this glyph
     */
    internal val composePath: Path get() = path

    /**
     * Updates the glyph path with the given axis variations.
     * Rewinds the path and fills it directly from native data.
     *
     * @param axes Map of axis tag (e.g., "FILL", "wght") to value
     * @return true if successful, false if glyph not found
     */
    fun updateAxes(axes: Map<String, Float>): Boolean {
        return updateGlyphPath(this, extractor, codepoint, axes)
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
        glyph.updateAxes(axes)
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
