package com.davidmedenjak.fontsubsetting

import androidx.compose.runtime.Immutable

@Immutable
data class VariableFontConfig(
    val fill: Float = 0f,
    val weight: Float = 400f,
    val grade: Float = 0f,
    val opticalSize: Float = 48f
) {
    companion object {
        val FILL_RANGE = 0f..1f
        val WEIGHT_RANGE = 100f..700f
        val GRADE_RANGE = -25f..200f
        val OPTICAL_SIZE_RANGE = 20f..48f
        
        val DEFAULT = VariableFontConfig()
    }
}