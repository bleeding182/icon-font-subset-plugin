#ifndef FONTSUBSETTING_RUNTIME_FONT_PATH_EXTRACTOR_H
#define FONTSUBSETTING_RUNTIME_FONT_PATH_EXTRACTOR_H

#include <stddef.h>

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

    // Simple dynamic array for path commands (replaces std::vector)
    struct PathCommandArray {
        PathCommand *data;
        size_t size;
        size_t capacity;

        PathCommandArray();

        ~PathCommandArray();

        void push_back(const PathCommand &cmd);

        void clear();

        bool empty() const { return size == 0; }

    private:
        void reserve(size_t new_capacity);
    };

    // Simple key-value pair for variations (replaces std::map)
    struct Variation {
        char tag[5];  // 4 chars + null terminator
        float value;
    };

    struct GlyphPath {
        PathCommandArray commands;
        float advanceWidth;
        float advanceHeight;
        int unitsPerEm;
        float minX, minY, maxX, maxY;  // Bounding box

        GlyphPath();

        ~GlyphPath();
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
 * @param variations Array of variation key-value pairs
 * @param variationCount Number of variations
 * @return GlyphPath containing the path commands, or empty if failed
 */
    GlyphPath extractGlyphPathWithVariations(
            const void *fontData,
            size_t fontDataSize,
            unsigned int codepoint,
            const Variation *variations,
            size_t variationCount
    );

} // namespace fontsubsetting

#endif // FONTSUBSETTING_RUNTIME_FONT_PATH_EXTRACTOR_H
