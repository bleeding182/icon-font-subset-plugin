#ifndef FONTSUBSETTING_RUNTIME_FONT_PATH_EXTRACTOR_H
#define FONTSUBSETTING_RUNTIME_FONT_PATH_EXTRACTOR_H

#include <vector>
#include <string>
#include <map>

namespace fontsubsetting {

    struct PathCommand {
        enum Type {
            MOVE_TO,
            LINE_TO,
            QUADRATIC_TO,
            CUBIC_TO,
            CLOSE
        };

        Type type;
        float x1, y1;    // First point (or only point for MOVE_TO, LINE_TO)
        float x2, y2;    // Second point (for QUADRATIC_TO, CUBIC_TO)
        float x3, y3;    // Third point (for CUBIC_TO)
    };

    struct GlyphPath {
        std::vector <PathCommand> commands;
        float advanceWidth;
        float advanceHeight;
        int unitsPerEm;
        float minX, minY, maxX, maxY;  // Bounding box

        bool isEmpty() const { return commands.empty(); }
    };

/**
 * Extracts the path data for a specific glyph from a font.
 * 
 * @param fontData The font file data
 * @param fontDataSize Size of font data in bytes
 * @param codepoint Unicode codepoint of the glyph
 * @return GlyphPath containing the path commands, or empty if failed
 */
    GlyphPath extractGlyphPath(const void *fontData, size_t fontDataSize, unsigned int codepoint);

/**
 * Extracts the path data for a specific glyph from a variable font with axis variations.
 * 
 * @param fontData The font file data
 * @param fontDataSize Size of font data in bytes
 * @param codepoint Unicode codepoint of the glyph
 * @param variations Map of axis tag (e.g., "wght", "FILL") to value
 * @return GlyphPath containing the path commands, or empty if failed
 */
    GlyphPath extractGlyphPathWithVariations(
            const void *fontData,
            size_t fontDataSize,
            unsigned int codepoint,
            const std::map<std::string, float> &variations
    );

} // namespace fontsubsetting

#endif // FONTSUBSETTING_RUNTIME_FONT_PATH_EXTRACTOR_H
