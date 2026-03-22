package com.davidmedenjak.fontsubsetting

import android.content.Context
import android.graphics.Paint
import android.graphics.Paint.FontMetrics
import android.graphics.Path
import android.graphics.Typeface
import android.os.Build
import android.view.WindowManager
import androidx.annotation.FontRes
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.util.lerp
import androidx.core.content.res.ResourcesCompat
import kotlin.math.ceil
import kotlin.math.roundToInt

/**
 * Lightweight wrapper around a [Typeface] for use with [GlyphPainter].
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
 * A [Painter] that renders a font glyph using Android's Paint + Canvas.
 *
 * Uses dp sizing (not sp) so icons don't scale with text size preferences.
 * Variable font axes are applied via [Paint.fontVariationSettings] on API 26+;
 * on API 24-25, icons render with the font's default axis values.
 *
 * Glyph outlines are cached as [Path] objects at a fixed reference size.
 * After the first encounter of each variation setting, subsequent draws use
 * the cached path scaled to the target size, avoiding repeated font variation
 * interpolation and text shaping.
 */
@Stable
class GlyphPainter(
    private val text: String,
    typeface: Typeface,
) : Painter() {

    private val referenceSizePx = 100f

    /** Paint used to extract glyph paths at the reference size. */
    private val extractPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        this.typeface = typeface
        textSize = referenceSizePx
    }

    /** Paint used to draw cached paths (color only, no font config needed). */
    private val drawPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    /** Cached glyph outlines keyed by variation settings string. */
    private val pathCache = HashMap<String?, Path>()

    /** Baseline Y at the reference size, for vertically centering the glyph. */
    private val referenceBaselineY: Float

    init {
        val metrics = FontMetrics()
        extractPaint.getFontMetrics(metrics)
        referenceBaselineY = (referenceSizePx - (metrics.ascent + metrics.descent)) / 2f
    }

    private var _tint = mutableStateOf(Color.Black)
    var tint: Color
        get() = _tint.value
        set(value) {
            if (_tint.value != value) {
                _tint.value = value
                drawPaint.color = value.toArgb()
            }
        }

    private var _fontVariationSettings = mutableStateOf<String?>(null)
    var fontVariationSettings: String?
        get() = _fontVariationSettings.value
        set(value) {
            if (_fontVariationSettings.value != value) {
                _fontVariationSettings.value = value
            }
        }

    override val intrinsicSize: Size get() = Size.Unspecified

    override fun DrawScope.onDraw() {
        // Read snapshot state so Compose re-draws when tint/axes change
        _tint.value
        val settings = _fontVariationSettings.value

        val path = pathCache.getOrPut(settings) {
            if (Build.VERSION.SDK_INT >= 26) {
                extractPaint.fontVariationSettings = settings
            }
            Path().also {
                extractPaint.getTextPath(text, 0, text.length, 0f, referenceBaselineY, it)
            }
        }

        val sizePx = size.minDimension
        val scale = sizePx / referenceSizePx
        val nativeCanvas = drawContext.canvas.nativeCanvas
        nativeCanvas.save()
        nativeCanvas.translate(size.width / 2f, 0f)
        nativeCanvas.scale(scale, scale)
        nativeCanvas.drawPath(path, drawPaint)
        nativeCanvas.restore()
    }
}

data class FontAxisAnimation(
    val tag: String,
    val initialValue: Float,
    val targetValue: Float,
)

/**
 * Reusable preset for font variation animations.
 *
 * Define once and pass to [animateFontVariationAsState] so multiple call sites
 * share the same animation configuration without repeating axis definitions.
 */
@Immutable
data class GlyphVariationPreset(
    val axes: List<FontAxisAnimation>,
    val durationMillis: Int = 500,
    val easing: Easing = FastOutSlowInEasing,
    val repeatMode: RepeatMode = RepeatMode.Reverse,
    val label: String = "FontVariation",
)

@Composable
private fun getDisplayRefreshRate(): Float {
    val view = LocalView.current
    return if (Build.VERSION.SDK_INT >= 30) {
        view.display?.refreshRate ?: 60f
    } else {
        @Suppress("DEPRECATION")
        (view.context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager)
            ?.defaultDisplay?.refreshRate ?: 60f
    }
}

/**
 * Animates font variation settings from a [GlyphVariationPreset].
 *
 * Delegates to the vararg overload, unpacking the preset's axes and parameters.
 */
@Composable
fun animateFontVariationAsState(preset: GlyphVariationPreset): State<String?> =
    animateFontVariationAsState(
        axes = preset.axes.toTypedArray(),
        durationMillis = preset.durationMillis,
        easing = preset.easing,
        repeatMode = preset.repeatMode,
        label = preset.label,
    )

/**
 * Animates font variation settings with zero per-frame allocations.
 *
 * Pre-computes all frame strings at the device's refresh rate, then uses
 * [derivedStateOf] to return cached instances during animation.
 */
@Composable
fun animateFontVariationAsState(
    vararg axes: FontAxisAnimation,
    durationMillis: Int = 500,
    easing: Easing = FastOutSlowInEasing,
    repeatMode: RepeatMode = RepeatMode.Reverse,
    label: String = "FontVariation",
): State<String?> {
    val fps = getDisplayRefreshRate()
    val frameCount = ceil(durationMillis * fps / 1000f).toInt()

    val frames = remember(*axes, frameCount) {
        Array(frameCount + 1) { i ->
            val fraction = i.toFloat() / frameCount
            buildFontVariationSettings(*axes.map { axis ->
                axis.tag to lerp(axis.initialValue, axis.targetValue, fraction)
            }.toTypedArray())
        }
    }

    val transition = rememberInfiniteTransition(label)
    val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            tween(durationMillis, easing = easing),
            repeatMode,
        ),
        label = "${label}_progress",
    )

    return remember(frames) {
        derivedStateOf {
            frames[(progress * frameCount).roundToInt().coerceIn(0, frameCount)]
        }
    }
}

/**
 * Remembers a [GlyphPainter] for rendering a font glyph with [Icon][androidx.compose.material3.Icon].
 *
 * @param text Unicode string from generated constants (e.g., MaterialSymbols.home)
 * @param font GlyphFont from [rememberGlyphFont]
 * @param tint Color to tint the icon
 * @param fontVariationSettings Pre-built variation string (from [buildFontVariationSettings])
 */
@Composable
fun rememberGlyphPainter(
    text: String,
    font: GlyphFont,
    tint: Color = Color.Black,
    fontVariationSettings: String? = null,
): Painter {
    return remember(text, font) {
        GlyphPainter(text, font.typeface)
    }.also {
        it.tint = tint
        it.fontVariationSettings = fontVariationSettings
    }
}
