package com.davidmedenjak.fontsubsetting.runtime

import android.graphics.Paint
import android.graphics.Path
import android.graphics.Typeface
import android.os.Build
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
 * When the HarfBuzz extractor is unavailable (Compose preview / host JVM without native
 * libs) the painter falls back to [GlyphFont.previewTypeface] and renders via Android's
 * built-in Paint stack so previews still display the real glyph.
 */
@Stable
class GlyphPainter internal constructor(
    private val codepoint: Int,
    private val text: String,
    private val font: GlyphFont,
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

        val extractor = font.extractor
        if (extractor != null) {
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
            return
        }

        val typeface = font.previewTypeface
        if (typeface != null) {
            drawWithTypeface(typeface, w, h, argb, v)
            return
        }

        drawPlaceholder(w, h, argb)
    }

    private fun DrawScope.drawWithTypeface(
        typeface: Typeface,
        w: Float,
        h: Float,
        argb: Int,
        variation: FontVariation,
    ) {
        val s = minOf(w, h)
        drawPaint.reset()
        drawPaint.isAntiAlias = true
        drawPaint.typeface = typeface
        drawPaint.color = argb
        drawPaint.style = Paint.Style.FILL
        drawPaint.textSize = s
        drawPaint.textAlign = Paint.Align.CENTER

        if (Build.VERSION.SDK_INT >= 26 && variation.axes.isNotEmpty()) {
            drawPaint.fontVariationSettings = buildVariationSettings(variation)
        }

        val fm = drawPaint.fontMetrics
        val baseline = h / 2f - (fm.ascent + fm.descent) / 2f
        drawContext.canvas.nativeCanvas.drawText(text, w / 2f, baseline, drawPaint)
    }

    private fun buildVariationSettings(variation: FontVariation): String = buildString {
        for (i in variation.axes.indices) {
            if (i > 0) append(',')
            append('\'').append(variation.axes[i]).append("' ").append(variation.values[i])
        }
    }

    private fun DrawScope.drawPlaceholder(w: Float, h: Float, argb: Int) {
        drawPaint.reset()
        drawPaint.isAntiAlias = true
        drawPaint.color = argb
        drawPaint.style = Paint.Style.STROKE
        drawPaint.strokeWidth = 1f * density
        val inset = drawPaint.strokeWidth / 2f
        drawContext.canvas.nativeCanvas.drawRect(inset, inset, w - inset, h - inset, drawPaint)
    }
}
