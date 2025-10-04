package com.davidmedenjak.fontsubsetting.runtime

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
 * Updates a glyph path with the given axis variations.
 *
 * This function:
 * 1. Rewinds the existing path
 * 2. Extracts raw glyph data from native code
 * 3. Parses the header (metrics and bounds)
 * 4. Fills the path directly with commands (no intermediate objects)
 *
 * @param glyphState Glyph state to update
 * @param extractor Font path extractor
 * @param codepoint Unicode codepoint of the glyph
 * @param axes Variable font axis values (e.g., "FILL" to 1f, "wght" to 400f)
 * @return true if successful, false if glyph not found
 */
internal fun updateGlyphPath(
    glyphState: GlyphState,
    extractor: FontPathExtractor,
    codepoint: Int,
    axes: Map<String, Float>
): Boolean {
    // Rewind the existing path for reuse
    glyphState.path.rewind()

    // Get raw data from native code
    val rawData = if (axes.isEmpty()) {
        extractor.nativeExtractGlyphPathInternal(codepoint)
    } else {
        val tags = axes.keys.toTypedArray()
        val values = axes.values.toFloatArray()
        extractor.nativeExtractGlyphPathWithVariationsInternal(codepoint, tags, values)
    } ?: return false

    // Validate minimum size for header
    if (rawData.size < 8) return false

    // Parse header: [numCommands, advanceWidth, advanceHeight, unitsPerEm, minX, minY, maxX, maxY]
    val numCommands = rawData[0].toInt()
    glyphState.advanceWidth = rawData[1]
    glyphState.advanceHeight = rawData[2]
    glyphState.unitsPerEm = rawData[3].toInt()
    glyphState.minX = rawData[4]
    glyphState.minY = rawData[5]
    glyphState.maxX = rawData[6]
    glyphState.maxY = rawData[7]

    // Fill path directly from raw command data
    // Each command: [type, x1, y1, x2, y2, x3, y3]
    var offset = 8
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
            PATH_COMMAND_MOVE_TO -> glyphState.path.moveTo(x1, y1)
            PATH_COMMAND_LINE_TO -> glyphState.path.lineTo(x1, y1)
            PATH_COMMAND_QUADRATIC_TO -> glyphState.path.quadraticTo(x1, y1, x2, y2)
            PATH_COMMAND_CUBIC_TO -> glyphState.path.cubicTo(x1, y1, x2, y2, x3, y3)
            PATH_COMMAND_CLOSE -> glyphState.path.close()
        }

        offset += 7
    }

    return true
}
