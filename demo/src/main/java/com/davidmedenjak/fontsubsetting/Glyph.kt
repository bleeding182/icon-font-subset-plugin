package com.davidmedenjak.fontsubsetting

import android.graphics.Paint
import android.graphics.Paint.FontMetrics
import android.graphics.Typeface
import android.os.Build
import androidx.annotation.FontRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.core.content.res.ResourcesCompat

/**
 * Lightweight wrapper around a [Typeface] for use with the [Glyph] composable.
 */
@Immutable
class GlyphFont internal constructor(val typeface: Typeface)

/**
 * Remembers a [GlyphFont] loaded from a font resource.
 *
 * @param resourceId Font resource ID (e.g., R.font.symbols)
 * @return GlyphFont instance
 */
@Composable
fun rememberGlyphFont(@FontRes resourceId: Int): GlyphFont {
    val context = LocalContext.current
    return remember(resourceId) {
        val typeface = ResourcesCompat.getFont(context, resourceId)
            ?: error("Failed to load font resource $resourceId")
        GlyphFont(typeface)
    }
}

/**
 * Builds a font variation settings string from axis tag/value pairs.
 *
 * Returns null if no axes are provided.
 *
 * @param axes Pairs of axis tag to value (e.g., "FILL" to 1f, "wght" to 400f)
 * @return CSS-style variation string (e.g., "'FILL' 1.0, 'wght' 400.0") or null
 */
fun buildFontVariationSettings(vararg axes: Pair<String, Float>): String? {
    if (axes.isEmpty()) return null
    return buildString(axes.size * 16) {
        axes.forEachIndexed { index, (tag, value) ->
            if (index > 0) append(", ")
            append('\'').append(tag).append("' ").append(value)
        }
    }
}

/**
 * Renders a font glyph as an icon using Android's Paint + Canvas.
 *
 * Uses dp sizing (not sp) so icons don't scale with text size preferences.
 * Variable font axes are applied via [Paint.fontVariationSettings] on API 26+;
 * on API 24-25, icons render with the font's default axis values.
 *
 * This is the primary overload — accepts a pre-built variation settings string
 * for zero-allocation rendering during animation. Use [buildFontVariationSettings]
 * to create the string, wrapped in `remember(...)` at the call site.
 *
 * @param text Unicode string from generated constants (e.g., MaterialSymbols.home)
 * @param font GlyphFont from [rememberGlyphFont]
 * @param size Icon size in dp
 * @param modifier Modifier to apply
 * @param tint Color to tint the icon
 * @param fontVariationSettings Pre-built variation string (from [buildFontVariationSettings])
 */
@Composable
fun Glyph(
    text: String,
    font: GlyphFont,
    size: Dp,
    modifier: Modifier = Modifier,
    tint: Color = Color.Black,
    fontVariationSettings: String? = null,
) {
    val sizePx = with(LocalDensity.current) { size.toPx() }
    val colorArgb = tint.toArgb()

    val paint = remember { Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER } }
    val metrics = remember { FontMetrics() }

    // Configure paint during composition so Compose tracks these dependencies
    val baselineY = remember(font, sizePx, fontVariationSettings) {
        paint.typeface = font.typeface
        paint.textSize = sizePx
        if (Build.VERSION.SDK_INT >= 26) {
            paint.fontVariationSettings = fontVariationSettings
        }
        paint.getFontMetrics(metrics)
        (sizePx - (metrics.ascent + metrics.descent)) / 2f
    }
    Canvas(modifier = modifier.size(size)) {
        drawIntoCanvas { canvas ->
            if (Build.VERSION.SDK_INT >= 26 && fontVariationSettings != null) {
                paint.fontVariationSettings = fontVariationSettings
            }
            paint.color = colorArgb
            canvas.nativeCanvas.drawText(text, sizePx / 2f, baselineY, paint)
        }
    }
}

/**
 * Renders a font glyph as an icon using Android's Paint + Canvas.
 *
 * Convenience overload that accepts axis values as a Map. For optimal animation
 * performance, prefer the overload that takes a [fontVariationSettings] string
 * with [buildFontVariationSettings].
 *
 * @param text Unicode string from generated constants (e.g., MaterialSymbols.home)
 * @param font GlyphFont from [rememberGlyphFont]
 * @param size Icon size in dp
 * @param modifier Modifier to apply
 * @param tint Color to tint the icon
 * @param axes Variable font axis values (e.g., mapOf("FILL" to 1f, "wght" to 400f))
 */
@Composable
fun Glyph(
    text: String,
    font: GlyphFont,
    size: Dp,
    modifier: Modifier = Modifier,
    tint: Color = Color.Black,
    axes: Map<String, Float> = emptyMap(),
) {
    val variationSettings = remember(axes) {
        if (axes.isNotEmpty()) {
            axes.entries.joinToString(", ") { (tag, value) -> "'$tag' $value" }
        } else {
            null
        }
    }
    Glyph(
        text = text,
        font = font,
        size = size,
        modifier = modifier,
        tint = tint,
        fontVariationSettings = variationSettings,
    )
}
