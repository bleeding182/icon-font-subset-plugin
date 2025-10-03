package com.davidmedenjak.fontsubsetting.runtime

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.min

/**
 * Renders a font glyph as a vector path icon in Compose.
 *
 * @param glyphPath The glyph path data extracted from a font
 * @param modifier Modifier to apply to the icon
 * @param size Size of the icon (default: 24.dp)
 * @param tint Color to tint the icon (default: Color.Unspecified)
 * @param style Drawing style (Fill or Stroke)
 */
@Composable
fun PathIcon(
    glyphPath: GlyphPath,
    modifier: Modifier = Modifier,
    size: Dp = 24.dp,
    tint: Color = Color.Unspecified,
    style: androidx.compose.ui.graphics.drawscope.DrawStyle = Fill
) {
    val path = remember(glyphPath) {
        glyphPath.toComposePath()
    }

    Canvas(modifier = modifier.size(size)) {
        val canvasSize = this.size.minDimension

        // Calculate the glyph bounds
        val glyphWidth = glyphPath.width
        val glyphHeight = glyphPath.height

        // Calculate scale to fit the glyph in the canvas
        val scale = if (glyphWidth > 0 && glyphHeight > 0) {
            min(canvasSize / glyphWidth, canvasSize / glyphHeight)
        } else {
            canvasSize
        }

        // Calculate translation to center the glyph
        // After flipping Y, we need to translate from the top
        val translateX = (canvasSize - glyphWidth * scale) / 2f - glyphPath.minX * scale
        val translateY = (canvasSize - glyphHeight * scale) / 2f + glyphPath.maxY * scale

        translate(translateX, translateY) {
            scale(scale = scale, pivot = Offset.Zero) {
                drawPath(
                    path = path,
                    color = if (tint == Color.Unspecified) Color.Black else tint,
                    style = style
                )
            }
        }
    }
}

/**
 * Renders a font glyph with animated path drawing effect.
 * Optimized to reuse Path object with rewind().
 *
 * @param glyphPath The glyph path data extracted from a font
 * @param progress Animation progress from 0f to 1f
 * @param modifier Modifier to apply to the icon
 * @param size Size of the icon (default: 24.dp)
 * @param tint Color to tint the icon (default: Color.Unspecified)
 * @param strokeWidth Width of the stroke for drawing animation
 */
@Composable
fun AnimatedPathIcon(
    glyphPath: GlyphPath,
    progress: Float = 1f,
    modifier: Modifier = Modifier,
    size: Dp = 24.dp,
    tint: Color = Color.Unspecified,
    strokeWidth: Float = 0.01f
) {
    val path = remember(glyphPath) { Path() }

    Canvas(modifier = modifier.size(size)) {
        val canvasSize = this.size.minDimension

        // Rebuild path with current progress
        path.rewind()
        glyphPath.fillPath(path, progress)

        // Calculate the glyph bounds
        val glyphWidth = glyphPath.width
        val glyphHeight = glyphPath.height

        // Calculate scale to fit the glyph in the canvas
        val scale = if (glyphWidth > 0 && glyphHeight > 0) {
            min(canvasSize / glyphWidth, canvasSize / glyphHeight)
        } else {
            canvasSize
        }

        // Calculate translation to center the glyph
        val translateX = (canvasSize - glyphWidth * scale) / 2f - glyphPath.minX * scale
        val translateY = (canvasSize - glyphHeight * scale) / 2f + glyphPath.maxY * scale

        translate(translateX, translateY) {
            scale(scale = scale, pivot = Offset.Zero) {
                drawPath(
                    path = path,
                    color = if (tint == Color.Unspecified) Color.Black else tint,
                    style = if (progress < 1f) Stroke(width = strokeWidth) else Fill
                )
            }
        }
    }
}

/**
 * Renders a font glyph with automatic animation.
 *
 * @param glyphPath The glyph path data extracted from a font
 * @param animate Whether to animate the icon
 * @param animationSpec Animation specification
 * @param modifier Modifier to apply to the icon
 * @param size Size of the icon (default: 24.dp)
 * @param tint Color to tint the icon (default: Color.Unspecified)
 * @param strokeWidth Width of the stroke for drawing animation
 */
@Composable
fun PathIconAnimated(
    glyphPath: GlyphPath,
    animate: Boolean = true,
    animationSpec: AnimationSpec<Float> = spring(),
    modifier: Modifier = Modifier,
    size: Dp = 24.dp,
    tint: Color = Color.Unspecified,
    strokeWidth: Float = 0.01f
) {
    val progress = remember { Animatable(0f) }

    LaunchedEffect(glyphPath, animate) {
        if (animate) {
            progress.snapTo(0f)
            progress.animateTo(1f, animationSpec)
        } else {
            progress.snapTo(1f)
        }
    }

    AnimatedPathIcon(
        glyphPath = glyphPath,
        progress = progress.value,
        modifier = modifier,
        size = size,
        tint = tint,
        strokeWidth = strokeWidth
    )
}

/**
 * Morphs between two glyph paths with animation.
 * This is useful for animating between different axis values in variable fonts.
 *
 * @param fromPath Starting glyph path
 * @param toPath Target glyph path
 * @param progress Morphing progress from 0f (fromPath) to 1f (toPath)
 * @param modifier Modifier to apply to the icon
 * @param size Size of the icon (default: 24.dp)
 * @param tint Color to tint the icon (default: Color.Unspecified)
 */
@Composable
fun MorphingPathIcon(
    fromPath: GlyphPath,
    toPath: GlyphPath,
    progress: Float = 0f,
    modifier: Modifier = Modifier,
    size: Dp = 24.dp,
    tint: Color = Color.Unspecified
) {
    val path = remember(fromPath, toPath) { Path() }

    Canvas(modifier = modifier.size(size)) {
        val canvasSize = this.size.minDimension

        // Morph between paths
        path.rewind()
        morphPaths(fromPath, toPath, progress, path)

        // Interpolate bounding box for smooth scaling during morph
        val t = progress.coerceIn(0f, 1f)
        val invT = 1f - t
        val minX = fromPath.minX * invT + toPath.minX * t
        val minY = fromPath.minY * invT + toPath.minY * t
        val maxX = fromPath.maxX * invT + toPath.maxX * t
        val maxY = fromPath.maxY * invT + toPath.maxY * t
        val glyphWidth = maxX - minX
        val glyphHeight = maxY - minY

        // Calculate scale to fit the glyph in the canvas
        val scale = if (glyphWidth > 0 && glyphHeight > 0) {
            min(canvasSize / glyphWidth, canvasSize / glyphHeight)
        } else {
            canvasSize
        }

        // Calculate translation to center the glyph
        val translateX = (canvasSize - glyphWidth * scale) / 2f - minX * scale
        val translateY = (canvasSize - glyphHeight * scale) / 2f + maxY * scale

        translate(translateX, translateY) {
            scale(scale = scale, pivot = Offset.Zero) {
                drawPath(
                    path = path,
                    color = if (tint == Color.Unspecified) Color.Black else tint,
                    style = Fill
                )
            }
        }
    }
}

/**
 * Animated morphing between two glyph paths.
 *
 * @param fromPath Starting glyph path
 * @param toPath Target glyph path
 * @param animate Whether to animate the morphing
 * @param animationSpec Animation specification
 * @param modifier Modifier to apply to the icon
 * @param size Size of the icon (default: 24.dp)
 * @param tint Color to tint the icon (default: Color.Unspecified)
 */
@Composable
fun MorphingPathIconAnimated(
    fromPath: GlyphPath,
    toPath: GlyphPath,
    animate: Boolean = true,
    animationSpec: AnimationSpec<Float> = spring(),
    modifier: Modifier = Modifier,
    size: Dp = 24.dp,
    tint: Color = Color.Unspecified
) {
    val progress = remember { Animatable(0f) }

    LaunchedEffect(fromPath, toPath, animate) {
        if (animate) {
            progress.snapTo(0f)
            progress.animateTo(1f, animationSpec)
        } else {
            progress.snapTo(1f)
        }
    }

    MorphingPathIcon(
        fromPath = fromPath,
        toPath = toPath,
        progress = progress.value,
        modifier = modifier,
        size = size,
        tint = tint
    )
}

/**
 * Converts a GlyphPath to a Compose Path.
 *
 * @param progress Optional progress from 0f to 1f for partial path drawing
 * @return Compose Path object
 */
fun GlyphPath.toComposePath(progress: Float = 1f): Path {
    val path = Path()
    fillPath(path, progress)
    return path
}

/**
 * Fills an existing Path with this glyph's commands.
 * This is more efficient than creating a new Path when animating.
 *
 * @param path The Path to fill (should be rewound if reusing)
 * @param progress Optional progress from 0f to 1f for partial path drawing
 */
fun GlyphPath.fillPath(path: Path, progress: Float = 1f) {
    if (commands.isEmpty()) return

    val totalCommands = (commands.size * progress.coerceIn(0f, 1f)).toInt()

    for (i in 0 until totalCommands) {
        val cmd = commands[i]
        when (cmd.type) {
            PathCommandType.MOVE_TO -> {
                path.moveTo(cmd.x1, -cmd.y1)
            }

            PathCommandType.LINE_TO -> {
                path.lineTo(cmd.x1, -cmd.y1)
            }

            PathCommandType.QUADRATIC_TO -> {
                path.quadraticBezierTo(cmd.x1, -cmd.y1, cmd.x2, -cmd.y2)
            }

            PathCommandType.CUBIC_TO -> {
                path.cubicTo(cmd.x1, -cmd.y1, cmd.x2, -cmd.y2, cmd.x3, -cmd.y3)
            }

            PathCommandType.CLOSE -> {
                path.close()
            }
        }
    }
}

/**
 * Morphs between two glyph paths by interpolating their commands.
 * Handles different command counts by using the smaller count.
 *
 * @param fromPath Starting glyph path
 * @param toPath Target glyph path
 * @param progress Morphing progress from 0f to 1f
 * @param targetPath Path object to fill with morphed result
 */
private fun morphPaths(
    fromPath: GlyphPath,
    toPath: GlyphPath,
    progress: Float,
    targetPath: Path
) {
    if (fromPath.commands.isEmpty() && toPath.commands.isEmpty()) return

    val t = progress.coerceIn(0f, 1f)
    val invT = 1f - t

    // Use the smaller command count to avoid index issues
    val commandCount = min(fromPath.commands.size, toPath.commands.size)

    for (i in 0 until commandCount) {
        val fromCmd = fromPath.commands[i]
        val toCmd = toPath.commands[i]

        // Only morph if command types match, otherwise just use fromPath
        if (fromCmd.type == toCmd.type) {
            when (fromCmd.type) {
                PathCommandType.MOVE_TO -> {
                    targetPath.moveTo(
                        fromCmd.x1 * invT + toCmd.x1 * t,
                        -(fromCmd.y1 * invT + toCmd.y1 * t)
                    )
                }

                PathCommandType.LINE_TO -> {
                    targetPath.lineTo(
                        fromCmd.x1 * invT + toCmd.x1 * t,
                        -(fromCmd.y1 * invT + toCmd.y1 * t)
                    )
                }

                PathCommandType.QUADRATIC_TO -> {
                    targetPath.quadraticBezierTo(
                        fromCmd.x1 * invT + toCmd.x1 * t,
                        -(fromCmd.y1 * invT + toCmd.y1 * t),
                        fromCmd.x2 * invT + toCmd.x2 * t,
                        -(fromCmd.y2 * invT + toCmd.y2 * t)
                    )
                }

                PathCommandType.CUBIC_TO -> {
                    targetPath.cubicTo(
                        fromCmd.x1 * invT + toCmd.x1 * t,
                        -(fromCmd.y1 * invT + toCmd.y1 * t),
                        fromCmd.x2 * invT + toCmd.x2 * t,
                        -(fromCmd.y2 * invT + toCmd.y2 * t),
                        fromCmd.x3 * invT + toCmd.x3 * t,
                        -(fromCmd.y3 * invT + toCmd.y3 * t)
                    )
                }

                PathCommandType.CLOSE -> {
                    targetPath.close()
                }
            }
        } else {
            // Fallback: just use the from command
            when (fromCmd.type) {
                PathCommandType.MOVE_TO -> targetPath.moveTo(fromCmd.x1, -fromCmd.y1)
                PathCommandType.LINE_TO -> targetPath.lineTo(fromCmd.x1, -fromCmd.y1)
                PathCommandType.QUADRATIC_TO -> targetPath.quadraticBezierTo(
                    fromCmd.x1,
                    -fromCmd.y1,
                    fromCmd.x2,
                    -fromCmd.y2
                )

                PathCommandType.CUBIC_TO -> targetPath.cubicTo(
                    fromCmd.x1,
                    -fromCmd.y1,
                    fromCmd.x2,
                    -fromCmd.y2,
                    fromCmd.x3,
                    -fromCmd.y3
                )

                PathCommandType.CLOSE -> targetPath.close()
            }
        }
    }
}

/**
 * Creates a mirrored version of this path (useful for some icon transformations).
 */
fun GlyphPath.mirror(): GlyphPath {
    val mirroredCommands = commands.map { cmd ->
        cmd.copy(
            x1 = -cmd.x1,
            x2 = -cmd.x2,
            x3 = -cmd.x3
        )
    }
    return copy(commands = mirroredCommands)
}

/**
 * Creates a vertically flipped version of this path.
 */
fun GlyphPath.flipVertical(): GlyphPath {
    val flippedCommands = commands.map { cmd ->
        cmd.copy(
            y1 = -cmd.y1,
            y2 = -cmd.y2,
            y3 = -cmd.y3
        )
    }
    return copy(commands = flippedCommands)
}
