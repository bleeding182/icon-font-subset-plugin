package com.davidmedenjak.fontsubsetting.runtime

import androidx.compose.runtime.Immutable

@JvmInline
@Immutable
value class GlyphFont internal constructor(
    internal val extractor: HarfBuzzGlyphExtractor?,
)
