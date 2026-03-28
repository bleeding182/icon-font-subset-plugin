package com.davidmedenjak.fontsubsetting.runtime

import androidx.annotation.FontRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Remembers a [GlyphFont] loaded from a font resource using native HarfBuzz.
 *
 * The font bytes are read from the resource and passed to HarfBuzz for
 * direct glyph outline extraction, bypassing Android's Paint/Typeface stack.
 *
 * @param resourceId Font resource ID (e.g., R.font.symbols)
 */
@Composable
fun rememberGlyphFont(@FontRes resourceId: Int): GlyphFont {
    val context = LocalContext.current
    val font = remember(resourceId) {
        @Suppress("ResourceType")
        val bytes = context.resources.openRawResource(resourceId).use { it.readBytes() }
        GlyphFont(HarfBuzzGlyphExtractor(bytes))
    }
    DisposableEffect(font) {
        onDispose { font.extractor.close() }
    }
    return font
}

/**
 * Remembers a [GlyphPainter] for rendering a font glyph via native HarfBuzz path extraction.
 *
 * Only the first Unicode codepoint of [text] is used — this matches the plugin-generated
 * icon constants which are single-codepoint strings.
 *
 * When [variation] comes from [animateFontVariationAsState], animation frame paths are
 * automatically batch pre-extracted in the background for optimal performance.
 *
 * @param text Unicode string from generated constants (e.g., MaterialSymbols.home)
 * @param font GlyphFont from [rememberGlyphFont]
 * @param tint Color to tint the icon
 * @param variation Font variation axes (from [FontVariation.of] or [animateFontVariationAsState])
 */
@Composable
fun rememberGlyphPainter(
    text: String,
    font: GlyphFont,
    tint: Color = Color.Black,
    variation: FontVariation = FontVariation.Empty,
): Painter {
    val codepoint = remember(text) { text.codePointAt(0) }
    val painter = remember(codepoint, font) {
        GlyphPainter(codepoint, font.extractor)
    }.also {
        it.tint = tint
        it.variation = variation
    }

    // Background batch extraction when variation carries animation frames
    val allFrames = variation.allFrames
    LaunchedEffect(painter, allFrames) {
        if (allFrames == null || allFrames.size <= 1) return@LaunchedEffect
        val extractor = font.extractor
        val axisTags = allFrames[0].axes
        if (axisTags.isEmpty()) return@LaunchedEffect

        // Build flattened values for remaining frames (skip first — already extracted lazily)
        val remainingFrames = allFrames.copyOfRange(1, allFrames.size)
        val flatValues = FloatArray(axisTags.size * remainingFrames.size)
        remainingFrames.forEachIndexed { i, frame ->
            frame.values.copyInto(flatValues, i * axisTags.size)
        }

        // Batch extract on background thread
        val paths = withContext(Dispatchers.Default) {
            extractor.extractPathBatch(codepoint, axisTags, flatValues, remainingFrames.size)
        } ?: return@LaunchedEffect

        // Populate cache on main thread
        paths.forEachIndexed { i, path ->
            painter.putPath(remainingFrames[i], path)
        }
    }

    return painter
}
