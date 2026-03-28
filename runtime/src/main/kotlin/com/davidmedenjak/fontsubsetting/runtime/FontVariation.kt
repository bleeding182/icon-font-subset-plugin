package com.davidmedenjak.fontsubsetting.runtime

import androidx.compose.runtime.Immutable

/**
 * Structured font variation axes — carries axis tags and values directly to HarfBuzz
 * without string serialization.
 *
 * Use [FontVariation.of] to create instances, or [FontVariation.Empty] for no variation.
 */
@Immutable
class FontVariation internal constructor(
    val axes: Array<String>,
    val values: FloatArray,
    /**
     * Links animation-produced instances to the full frame set for batch pre-extraction.
     * Null for manually-constructed or static variations.
     */
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
