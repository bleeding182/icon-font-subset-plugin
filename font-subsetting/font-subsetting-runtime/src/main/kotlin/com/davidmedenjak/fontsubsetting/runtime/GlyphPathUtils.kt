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
        // Command format (union-based, 10 floats per command):
        // [type, _padding(3), cx1/x/cx, cy1/y/cy, cx2/x, cy2/y, x, y]
        // The union is always 6 floats, we read based on command type:
        // - MOVE_TO/LINE_TO: use first 2 floats (x, y)
        // - QUADRATIC_TO: use first 4 floats (cx, cy, x, y)
        // - CUBIC_TO: use all 6 floats (cx1, cy1, cx2, cy2, x, y)
        var offset = 7
        for (i in 0 until numCommands) {
            if (offset + 10 > rawData.size) break

            val type = rawData[offset].toInt()
            // Skip padding (offset+1, offset+2, offset+3)
            // Union data starts at offset+4 (6 floats total)

            when (type) {
                PATH_COMMAND_MOVE_TO, PATH_COMMAND_LINE_TO -> {
                    val x = rawData[offset + 4]
                    val y = rawData[offset + 5]
                    val tx = x * scaleX + translateX
                    val ty = y * scaleY + translateY
                    if (type == PATH_COMMAND_MOVE_TO) {
                        composePath.moveTo(tx, ty)
                    } else {
                        composePath.lineTo(tx, ty)
                    }
                }

                PATH_COMMAND_QUADRATIC_TO -> {
                    val cx = rawData[offset + 4]
                    val cy = rawData[offset + 5]
                    val x = rawData[offset + 6]
                    val y = rawData[offset + 7]
                    val tx1 = cx * scaleX + translateX
                    val ty1 = cy * scaleY + translateY
                    val tx2 = x * scaleX + translateX
                    val ty2 = y * scaleY + translateY
                    composePath.quadraticTo(tx1, ty1, tx2, ty2)
                }

                PATH_COMMAND_CUBIC_TO -> {
                    val cx1 = rawData[offset + 4]
                    val cy1 = rawData[offset + 5]
                    val cx2 = rawData[offset + 6]
                    val cy2 = rawData[offset + 7]
                    val x = rawData[offset + 8]
                    val y = rawData[offset + 9]
                    val tx1 = cx1 * scaleX + translateX
                    val ty1 = cy1 * scaleY + translateY
                    val tx2 = cx2 * scaleX + translateX
                    val ty2 = cy2 * scaleY + translateY
                    val tx3 = x * scaleX + translateX
                    val ty3 = y * scaleY + translateY
                    composePath.cubicTo(tx1, ty1, tx2, ty2, tx3, ty3)
                }

                PATH_COMMAND_CLOSE -> composePath.close()
            }

            offset += 10
        }

        return true
    }
}
