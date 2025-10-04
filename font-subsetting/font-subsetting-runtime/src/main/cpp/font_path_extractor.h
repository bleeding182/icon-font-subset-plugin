#ifndef FONTSUBSETTING_RUNTIME_FONT_PATH_EXTRACTOR_H
#define FONTSUBSETTING_RUNTIME_FONT_PATH_EXTRACTOR_H

// Direct typedef to avoid including <stddef.h>
typedef __SIZE_TYPE__ size_t;

#include <hb.h>

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

        void reserve(size_t new_capacity);

        bool empty() const { return size == 0; }
    };

    // Simple key-value pair for variations (replaces std::map)
    struct Variation {
        char tag[5];  // 4 chars + null terminator
        float value;
    };

    struct GlyphPath {
        PathCommandArray commands;
        float advanceWidth;
        int unitsPerEm;
        float minX, minY, maxX, maxY;  // Bounding box

        GlyphPath();

        ~GlyphPath();

        inline bool isEmpty() const { return commands.empty(); }
    };

    /**
     * Reusable glyph handle that caches HarfBuzz objects for efficient axis updates.
     * This avoids recreating HarfBuzz face/font/buffer/draw_funcs on every extraction.
     */
    struct GlyphHandle {
        hb_blob_t *blob;
        hb_face_t *face;
        hb_font_t *font;
        hb_buffer_t *buffer;
        hb_draw_funcs_t *draw_funcs;
        hb_codepoint_t glyph_id;
        unsigned int codepoint;
        unsigned int upem;

        GlyphHandle();

        ~GlyphHandle();

        // Initialize the handle with font data and codepoint
        bool initialize(const void *fontData, size_t fontDataSize, unsigned int cp);

        // Extract path with current variations (reuses HarfBuzz objects)
        GlyphPath extractPath(const Variation *variations, size_t variationCount);

        // Clean up all HarfBuzz resources
        void destroy();
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
