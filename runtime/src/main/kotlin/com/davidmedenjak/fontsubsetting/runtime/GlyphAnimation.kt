package com.davidmedenjak.fontsubsetting.runtime

import android.content.Context
import android.os.Build
import android.view.WindowManager
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.util.lerp
import kotlin.math.ceil
import kotlin.math.roundToInt

data class FontAxisAnimation(
    val tag: String,
    val initialValue: Float,
    val targetValue: Float,
)

@Immutable
data class GlyphVariationPreset(
    val axes: List<FontAxisAnimation>,
    val durationMillis: Int = 500,
    val easing: Easing = FastOutSlowInEasing,
    val repeatMode: RepeatMode = RepeatMode.Reverse,
    val label: String = "FontVariation",
)

@Composable
fun animateFontVariationAsState(preset: GlyphVariationPreset): State<FontVariation> =
    animateFontVariationAsState(
        axes = preset.axes,
        durationMillis = preset.durationMillis,
        easing = preset.easing,
        repeatMode = preset.repeatMode,
        label = preset.label,
    )

/**
 * Animates font variation axes as a [State] of [FontVariation].
 *
 * Pre-computes all frames at the device's refresh rate to avoid per-frame allocations.
 * Each returned [FontVariation] carries a reference to all frames, enabling
 * [rememberGlyphPainter] to batch pre-extract glyph paths in the background.
 */
@Composable
fun animateFontVariationAsState(
    axes: List<FontAxisAnimation>,
    durationMillis: Int = 500,
    easing: Easing = FastOutSlowInEasing,
    repeatMode: RepeatMode = RepeatMode.Reverse,
    label: String = "FontVariation",
): State<FontVariation> {
    val fps = getDisplayRefreshRate()
    val frameCount = ceil(durationMillis * fps / 1000f).toInt()

    val frames = remember(axes, frameCount) {
        val axisTags = Array(axes.size) { axes[it].tag }
        val framesArray = Array(frameCount + 1) { i ->
            val fraction = i.toFloat() / frameCount
            FontVariation(
                axes = axisTags,
                values = FloatArray(axes.size) { j ->
                    lerp(axes[j].initialValue, axes[j].targetValue, fraction)
                },
            )
        }
        // Link each frame to the full set for batch pre-extraction
        val linked = Array(frameCount + 1) { i ->
            FontVariation(
                axes = framesArray[i].axes,
                values = framesArray[i].values,
                allFrames = framesArray,
            )
        }
        linked
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
