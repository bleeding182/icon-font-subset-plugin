package com.davidmedenjak.fontsubsetting.runtime

import android.content.Context
import android.graphics.Typeface
import androidx.annotation.FontRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.res.ResourcesCompat
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Remembers a [GlyphFont] loaded from a font resource.
 *
 * On Android the font is rendered via the HarfBuzz JNI extractor. When the native
 * library can't be loaded (Compose preview / Paparazzi / plain JVM unit tests),
 * the font falls back to an [android.graphics.Typeface] so previews still draw the
 * real glyph through the platform Paint stack.
 */
@Composable
fun rememberGlyphFont(@FontRes resourceId: Int): GlyphFont {
    val context = LocalContext.current
    val font = remember(resourceId) {
        @Suppress("ResourceType")
        val bytes = runCatching {
            context.resources.openRawResource(resourceId).use { it.readBytes() }
        }.getOrNull() ?: return@remember GlyphFont(extractor = null)

        runCatching { GlyphFont(extractor = HarfBuzzGlyphExtractor(bytes)) }
            .getOrElse {
                GlyphFont(
                    extractor = null,
                    previewTypeface = loadPreviewTypeface(context, resourceId, bytes),
                )
            }
    }
    DisposableEffect(font) {
        onDispose { font.extractor?.close() }
    }
    return font
}

// Layoutlib (Compose preview engine) renders the font resource directly when we
// go through ResourcesCompat — Typeface.createFromFile silently returns the
// default typeface and drawText then renders tofu for Material Symbols' PUA
// codepoints, which is what produced the square placeholders.
private fun loadPreviewTypeface(context: Context, resourceId: Int, bytes: ByteArray): Typeface? {
    runCatching { ResourcesCompat.getFont(context, resourceId) }
        .getOrNull()
        ?.takeIf { it != Typeface.DEFAULT }
        ?.let { return it }

    return runCatching {
        val cacheFile = File(context.cacheDir, "fontsubsetting-preview-$resourceId.ttf")
        if (!cacheFile.exists() || cacheFile.length() != bytes.size.toLong()) {
            cacheFile.writeBytes(bytes)
        }
        Typeface.createFromFile(cacheFile)
    }.getOrNull()
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
    val glyphText = remember(codepoint) { String(Character.toChars(codepoint)) }
    val painter = remember(codepoint, font) {
        GlyphPainter(codepoint, glyphText, font)
    }.also {
        it.tint = tint
        it.variation = variation
    }

    val extractor = font.extractor
    val allFrames = variation.allFrames
    LaunchedEffect(painter, allFrames) {
        if (extractor == null) return@LaunchedEffect
        if (allFrames == null || allFrames.size <= 1) return@LaunchedEffect
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
