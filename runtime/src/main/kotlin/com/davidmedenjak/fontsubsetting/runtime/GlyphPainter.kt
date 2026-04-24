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
 * Glyph paths are cached per variation setting and drawn in em-normalized coordinates,
 * scaled to the target size.
 *
 * When [extractor] is null (Compose preview / host JVM without native libs), the painter
 * draws a thin outline box at the draw bounds as a placeholder.
 */
@Stable
class GlyphPainter internal constructor(
    private val codepoint: Int,
    private val extractor: HarfBuzzGlyphExtractor?,
) : Painter() {

    private val drawPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val pathCache = ConcurrentHashMap<FontVariation, Path>()

    private var _tint = mutableStateOf(Color.Black)
    internal var tint: Color
        get() = _tint.value
        set(value) {
            if (_tint.value != value) {
                _tint.value = value
            }
        }

    private var _variation = mutableStateOf(FontVariation.Empty)
    internal var variation: FontVariation
        get() = _variation.value
        set(value) {
            if (_variation.value != value) {
                _variation.value = value
            }
        }

    internal fun putPath(variation: FontVariation, path: Path) {
        pathCache.putIfAbsent(variation, path)
    }

    override val intrinsicSize: Size get() = Size.Unspecified

    override fun DrawScope.onDraw() {
        val argb = _tint.value.toArgb()
        val v = _variation.value

        val w = size.width
        val h = size.height
        if (w <= 0f || h <= 0f) return

        val extractor = extractor
        if (extractor == null) {
            drawPlaceholder(w, h, argb)
            return
        }

        val path = pathCache.getOrPut(v) {
            extractor.extractPath(codepoint, v.axes, v.values) ?: run {
                Log.w("GlyphPainter", "Glyph not found for codepoint U+${codepoint.toString(16).uppercase()}")
                Path()
            }
        }

        val s = minOf(w, h)
        drawPaint.color = argb
        drawPaint.style = Paint.Style.FILL
        with(drawContext.canvas.nativeCanvas) {
            save()
            translate(w / 2f, h / 2f)
            scale(s, s)
            translate(-0.5f, 0.5f)
            drawPath(path, drawPaint)
            restore()
        }
    }

    private fun DrawScope.drawPlaceholder(w: Float, h: Float, argb: Int) {
        drawPaint.color = argb
        drawPaint.style = Paint.Style.STROKE
        drawPaint.strokeWidth = 1f * density
        val inset = drawPaint.strokeWidth / 2f
        drawContext.canvas.nativeCanvas.drawRect(inset, inset, w - inset, h - inset, drawPaint)
    }
}
