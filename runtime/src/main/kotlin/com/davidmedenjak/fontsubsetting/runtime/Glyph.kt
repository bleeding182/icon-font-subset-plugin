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
 * Remembers a [GlyphFont] loaded from a font resource.
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
 * Remembers a [Painter] for rendering a font glyph.
 *
 * Only the first Unicode codepoint of [text] is used, matching the plugin-generated
 * icon constants which are single-codepoint strings.
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

    val allFrames = variation.allFrames
    LaunchedEffect(painter, allFrames) {
        if (allFrames == null || allFrames.size <= 1) return@LaunchedEffect
        val extractor = font.extractor
        val axisTags = allFrames[0].axes
        if (axisTags.isEmpty()) return@LaunchedEffect

        // Skip first frame — it will be extracted lazily on first render
        val remainingFrames = allFrames.copyOfRange(1, allFrames.size)
        val flatValues = FloatArray(axisTags.size * remainingFrames.size)
        remainingFrames.forEachIndexed { i, frame ->
            frame.values.copyInto(flatValues, i * axisTags.size)
        }

        val paths = withContext(Dispatchers.Default) {
            extractor.extractPathBatch(codepoint, axisTags, flatValues, remainingFrames.size)
        } ?: return@LaunchedEffect

        paths.forEachIndexed { i, path ->
            painter.putPath(remainingFrames[i], path)
        }
    }

    return painter
}
