package com.davidmedenjak.fontsubsetting

import androidx.compose.runtime.Immutable

@Immutable
data class VariableFontConfig(
    val fill: Float = 0f,
    val weight: Float = 400f,
    val grade: Float = 0f,
    val opticalSize: Float = 48f
)