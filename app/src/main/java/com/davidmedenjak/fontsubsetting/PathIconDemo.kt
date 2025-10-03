package com.davidmedenjak.fontsubsetting

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.davidmedenjak.fontsubsetting.runtime.AnimatedPathIcon
import com.davidmedenjak.fontsubsetting.runtime.FontPathExtractor
import com.davidmedenjak.fontsubsetting.runtime.GlyphPath
import com.davidmedenjak.fontsubsetting.runtime.MorphingPathIcon
import com.davidmedenjak.fontsubsetting.runtime.PathIcon

enum class IconRenderMode {
    STATIC,
    DRAWING_ANIMATION,
    MORPHING
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PathIconDemo() {
    val context = LocalContext.current
    var selectedMode by remember { mutableIntStateOf(0) }

    // Load font path extractor
    val pathExtractor = remember {
        try {
            FontPathExtractor.fromResource(context, R.font.symbols)
        } catch (e: Exception) {
            null
        }
    }

    // Extract paths for some common icons
    val iconPaths = remember(pathExtractor) {
        if (pathExtractor == null) return@remember emptyList()

        listOf(
            MaterialSymbols.home to "home",
            MaterialSymbols.favorite to "favorite",
            MaterialSymbols.star to "star",
            MaterialSymbols.search to "search",
            MaterialSymbols.accountCircle to "account",
            MaterialSymbols.settings to "settings",
            MaterialSymbols.notifications to "notifications",
            MaterialSymbols.edit to "edit",
            MaterialSymbols.delete to "delete",
            MaterialSymbols.send to "send",
            MaterialSymbols.share to "share",
            MaterialSymbols.menu to "menu"
        ).mapNotNull { (icon, name) ->
            val codepoint = icon.codePointAt(0)
            // Extract path at default axis values
            val defaultPath = pathExtractor.extractGlyphPath(codepoint)
            // Extract path at FILL=0 (outline) and FILL=1 (filled)
            val outlinePath = pathExtractor.extractGlyphPath(codepoint, mapOf("FILL" to 0f))
            val filledPath = pathExtractor.extractGlyphPath(codepoint, mapOf("FILL" to 1f))

            if (defaultPath != null && outlinePath != null && filledPath != null) {
                Triple(name, defaultPath, Pair(outlinePath, filledPath))
            } else {
                null
            }
        }
    }

    val modes = listOf(
        IconRenderMode.STATIC to "Static",
        IconRenderMode.DRAWING_ANIMATION to "Drawing",
        IconRenderMode.MORPHING to "Morphing"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer
            )
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Text(
                    text = "Path Icon Rendering Demo",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Icons rendered as vector paths extracted from the font at runtime",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Segmented button for mode selection
                SingleChoiceSegmentedButtonRow(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    modes.forEachIndexed { index, (_, label) ->
                        SegmentedButton(
                            selected = selectedMode == index,
                            onClick = { selectedMode = index },
                            shape = SegmentedButtonDefaults.itemShape(
                                index = index,
                                count = modes.size
                            )
                        ) {
                            Text(label)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Description of current mode
                val modeDescription = when (modes[selectedMode].first) {
                    IconRenderMode.STATIC -> "Static icons rendered once with no animation"
                    IconRenderMode.DRAWING_ANIMATION -> "Icons drawn progressively with stroke animation"
                    IconRenderMode.MORPHING -> "Variable font FILL axis morphing (outline ↔ filled)"
                }

                Text(
                    text = modeDescription,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.6f)
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (pathExtractor == null) {
            Text(
                text = "Failed to load font path extractor. Make sure the native library is built.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(16.dp)
            )
        } else if (iconPaths.isEmpty()) {
            Text(
                text = "No icon paths extracted. Check the font resource.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(16.dp)
            )
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 100.dp),
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(4.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(iconPaths) { (name, defaultPath, paths) ->
                    PathIconCard(
                        name = name,
                        defaultGlyphPath = defaultPath,
                        outlineGlyphPath = paths.first,
                        filledGlyphPath = paths.second,
                        mode = modes[selectedMode].first
                    )
                }
            }
        }
    }
}

@Composable
fun PathIconCard(
    name: String,
    defaultGlyphPath: GlyphPath,
    outlineGlyphPath: GlyphPath,
    filledGlyphPath: GlyphPath,
    mode: IconRenderMode
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            when (mode) {
                IconRenderMode.STATIC -> {
                    PathIcon(
                        glyphPath = defaultGlyphPath,
                        size = 48.dp,
                        tint = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }

                IconRenderMode.DRAWING_ANIMATION -> {
                    // Continuous drawing animation
                    val infiniteTransition = rememberInfiniteTransition(label = "drawing_$name")
                    val progress by infiniteTransition.animateFloat(
                        initialValue = 0f,
                        targetValue = 1f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(durationMillis = 1000, easing = FastOutSlowInEasing),
                            repeatMode = RepeatMode.Reverse
                        ),
                        label = "progress"
                    )

                    AnimatedPathIcon(
                        glyphPath = defaultGlyphPath,
                        progress = progress,
                        size = 48.dp,
                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                        strokeWidth = 0.015f
                    )
                }

                IconRenderMode.MORPHING -> {
                    // Morphing between outline and filled paths
                    val infiniteTransition = rememberInfiniteTransition(label = "morphing_$name")
                    val progress by infiniteTransition.animateFloat(
                        initialValue = 0f,
                        targetValue = 1f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(durationMillis = 1000, easing = FastOutSlowInEasing),
                            repeatMode = RepeatMode.Reverse
                        ),
                        label = "progress"
                    )

                    MorphingPathIcon(
                        fromPath = outlineGlyphPath,
                        toPath = filledGlyphPath,
                        progress = progress,
                        size = 48.dp,
                        tint = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = name,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
            )
        }
    }
}
