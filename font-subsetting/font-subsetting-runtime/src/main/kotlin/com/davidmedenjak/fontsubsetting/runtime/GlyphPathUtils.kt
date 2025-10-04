package com.davidmedenjak.fontsubsetting.runtime

import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType

/**
 * Path command type constants from native code.
 * These correspond to the command types in the C++ implementation.
 */
private const val PATH_COMMAND_MOVE_TO = 0
private const val PATH_COMMAND_LINE_TO = 1
private const val PATH_COMMAND_QUADRATIC_TO = 2
private const val PATH_COMMAND_CUBIC_TO = 3
private const val PATH_COMMAND_CLOSE = 4

/**
 * Layer 1: Path and Metadata
 *
 * Encapsulates a Compose Path with its associated glyph metrics.
 * This is the low-level path management layer that handles:
 * - Path reuse and updates
 * - Parsing native data
 * - Direct path operations
 *
 * This class is agnostic to variable font axes - it just updates the path
 * when given new raw data.
 */
internal class PathData {
    /**
     * The Compose path for this glyph.
     */
    val composePath = Path().apply {
        fillType = PathFillType.NonZero
    }

    // Glyph metrics
    var advanceWidth: Float = 0f
        private set
    var unitsPerEm: Int = 0
        private set
    var minX: Float = 0f
        private set
    var minY: Float = 0f
        private set
    var maxX: Float = 0f
        private set
    var maxY: Float = 0f
        private set

    /**
     * Width of the glyph bounding box.
     */
    val width: Float get() = maxX - minX

    /**
     * Height of the glyph bounding box.
     */
    val height: Float get() = maxY - minY

    /**
     * Updates the path with new raw glyph data from native code.
     * Rewinds the existing path and refills it with the new data.
     * Applies the given transformations during path construction.
     *
     * @param rawData Raw float array from native code containing header + commands
     * @param scaleX Horizontal scale factor to apply
     * @param scaleY Vertical scale factor to apply (typically negative to flip Y)
     * @param translateX Horizontal translation to apply after scaling
     * @param translateY Vertical translation to apply after scaling
     * @return true if successful, false if data is invalid
     */
    fun update(
        rawData: FloatArray,
        scaleX: Float = 1f,
        scaleY: Float = 1f,
        translateX: Float = 0f,
        translateY: Float = 0f
    ): Boolean {
        // Validate minimum size for header
        if (rawData.size < 7) return false

        // Parse header: [numCommands, advanceWidth, unitsPerEm, minX, minY, maxX, maxY]
        val numCommands = rawData[0].toInt()
        advanceWidth = rawData[1]
        unitsPerEm = rawData[2].toInt()
        minX = rawData[3]
        minY = rawData[4]
        maxX = rawData[5]
        maxY = rawData[6]

        // Rewind the existing path for reuse
        composePath.rewind()
        composePath.fillType = PathFillType.NonZero

        // Fill path directly from raw command data with transformations applied
        // Each command: [type, x1, y1, x2, y2, x3, y3]
        var offset = 7
        for (i in 0 until numCommands) {
            if (offset + 7 > rawData.size) break

            val type = rawData[offset].toInt()
            val x1 = rawData[offset + 1]
            val y1 = rawData[offset + 2]
            val x2 = rawData[offset + 3]
            val y2 = rawData[offset + 4]
            val x3 = rawData[offset + 5]
            val y3 = rawData[offset + 6]

            when (type) {
                PATH_COMMAND_MOVE_TO -> {
                    val tx = x1 * scaleX + translateX
                    val ty = y1 * scaleY + translateY
                    composePath.moveTo(tx, ty)
                }

                PATH_COMMAND_LINE_TO -> {
                    val tx = x1 * scaleX + translateX
                    val ty = y1 * scaleY + translateY
                    composePath.lineTo(tx, ty)
                }

                PATH_COMMAND_QUADRATIC_TO -> {
                    val tx1 = x1 * scaleX + translateX
                    val ty1 = y1 * scaleY + translateY
                    val tx2 = x2 * scaleX + translateX
                    val ty2 = y2 * scaleY + translateY
                    composePath.quadraticTo(tx1, ty1, tx2, ty2)
                }

                PATH_COMMAND_CUBIC_TO -> {
                    val tx1 = x1 * scaleX + translateX
                    val ty1 = y1 * scaleY + translateY
                    val tx2 = x2 * scaleX + translateX
                    val ty2 = y2 * scaleY + translateY
                    val tx3 = x3 * scaleX + translateX
                    val ty3 = y3 * scaleY + translateY
                    composePath.cubicTo(tx1, ty1, tx2, ty2, tx3, ty3)
                }
                PATH_COMMAND_CLOSE -> composePath.close()
            }

            offset += 7
        }

        return true
    }
}
