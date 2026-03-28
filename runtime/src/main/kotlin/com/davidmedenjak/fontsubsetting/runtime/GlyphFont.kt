package com.davidmedenjak.fontsubsetting.runtime

import androidx.compose.runtime.Immutable

/**
 * Holds a [HarfBuzzGlyphExtractor] for native glyph path extraction.
 */
@JvmInline
@Immutable
value class GlyphFont internal constructor(
    internal val extractor: HarfBuzzGlyphExtractor,
)
