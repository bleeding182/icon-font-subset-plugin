package com.davidmedenjak.fontsubsetting

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation

private val cachedFontResource = R.font.symbols

@OptIn(ExperimentalTextApi::class)
@Stable
@Composable
fun rememberVariableFontFamily(config: VariableFontConfig): FontFamily {
    val context = LocalContext.current
    
    return remember(config) {
        FontFamily(
            Font(
                resId = cachedFontResource,
                variationSettings = FontVariation.Settings(
                    FontVariation.Setting("FILL", config.fill),
                    FontVariation.Setting("wght", config.weight),
                    FontVariation.Setting("GRAD", config.grade),
                    FontVariation.Setting("opsz", config.opticalSize)
                )
            )
        )
    }
}