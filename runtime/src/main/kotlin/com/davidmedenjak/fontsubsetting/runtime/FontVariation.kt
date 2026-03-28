package com.davidmedenjak.fontsubsetting.runtime

import androidx.compose.runtime.Immutable

/**
 * Font variation axes for variable font rendering.
 *
 * Use [FontVariation.of] to create instances, or [FontVariation.Empty] for no variation.
 */
@Immutable
class FontVariation internal constructor(
    internal val axes: Array<String>,
    internal val values: FloatArray,
    // All animation frames for batch pre-extraction, or null for static variations.
    internal val allFrames: Array<FontVariation>? = null,
) {

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is FontVariation) return false
        return axes.contentEquals(other.axes) && values.contentEquals(other.values)
    }

    override fun hashCode(): Int {
        var result = axes.contentHashCode()
        result = 31 * result + values.contentHashCode()
        return result
    }

    companion object {
        val Empty = FontVariation(emptyArray(), FloatArray(0))

        /**
         * Creates a [FontVariation] from axis tag/value pairs.
         *
         * Example: `FontVariation.of("FILL" to 1f, "wght" to 700f)`
         */
        fun of(vararg axes: Pair<String, Float>): FontVariation =
            if (axes.isEmpty()) Empty
            else FontVariation(
                axes = Array(axes.size) { axes[it].first },
                values = FloatArray(axes.size) { axes[it].second },
            )
    }
}
