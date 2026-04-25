package com.davidmedenjak.fontsubsetting.runtime

import android.graphics.Typeface
import androidx.compose.runtime.Immutable

@Immutable
class GlyphFont internal constructor(
    internal val extractor: HarfBuzzGlyphExtractor?,
    internal val previewTypeface: Typeface? = null,
)
