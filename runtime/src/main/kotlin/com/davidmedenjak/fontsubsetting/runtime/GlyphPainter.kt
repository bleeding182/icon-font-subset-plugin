package com.davidmedenjak.fontsubsetting.runtime

import android.graphics.Paint
import android.graphics.Path
import android.util.Log
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.toArgb
import java.util.concurrent.ConcurrentHashMap

/**
 * A [Painter] that renders a font glyph using HarfBuzz-extracted [Path] outlines.
 *
 * Glyph outlines are extracted via native HarfBuzz and cached per variation setting.
 * This bypasses Android's Paint/Typeface font stack entirely.
 * Variable font axes work on all API levels (no API 26+ requirement).
 *
 * Coordinates are in em units (1.0 = 1 em), scaled to the target size when drawing.
 */
@Stable
class GlyphPainter internal constructor(
    private val codepoint: Int,
    private val extractor: HarfBuzzGlyphExtractor,
) : Painter() {

    /** Paint for drawing — color is set before each draw call. */
    private val drawPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    /** Cached glyph outlines keyed by variation. Thread-safe for background batch extraction. */
    private val pathCache = ConcurrentHashMap<FontVariation, Path>()

    private var _tint = mutableStateOf(Color.Black)
    var tint: Color
        get() = _tint.value
        internal set(value) {
            if (_tint.value != value) {
                _tint.value = value
            }
        }

    private var _variation = mutableStateOf(FontVariation.Empty)
    var variation: FontVariation
        get() = _variation.value
        internal set(value) {
            if (_variation.value != value) {
                _variation.value = value
            }
        }

    /**
     * Pre-populate the path cache for a variation (used by background batch extraction).
     */
    internal fun putPath(variation: FontVariation, path: Path) {
        pathCache.putIfAbsent(variation, path)
    }

    override val intrinsicSize: Size get() = Size.Unspecified

    override fun DrawScope.onDraw() {
        // Read snapshot state so Compose re-draws when tint/axes change
        val argb = _tint.value.toArgb()
        val v = _variation.value

        val w = size.width
        val h = size.height
        if (w <= 0f || h <= 0f) return

        val path = pathCache.getOrPut(v) {
            extractor.extractPath(codepoint, v.axes, v.values) ?: run {
                Log.w("GlyphPainter", "Glyph not found for codepoint U+${codepoint.toString(16).uppercase()}")
                Path()
            }
        }

        val s = minOf(w, h)
        drawPaint.color = argb
        with(drawContext.canvas.nativeCanvas) {
            save()
            translate(w / 2f, h / 2f)
            scale(s, s)
            translate(-0.5f, 0.5f)
            drawPath(path, drawPaint)
            restore()
        }
    }
}
