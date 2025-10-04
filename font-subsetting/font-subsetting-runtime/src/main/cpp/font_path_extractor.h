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
     * Shared font data that can be reused across multiple glyphs.
     * Contains the HarfBuzz blob, face, and a prototype font.
     * This reduces memory usage and initialization overhead.
     */
    struct SharedFontData {
        hb_blob_t *blob;
        hb_face_t *face;
        hb_font_t *prototypeFont;  // Reusable font object
        unsigned int upem;

        SharedFontData();

        ~SharedFontData();

        // Initialize from font data
        bool initialize(const void *fontData, size_t fontDataSize);

        // Clean up all HarfBuzz resources
        void destroy();
    };

    /**
     * Reusable glyph handle that caches per-glyph HarfBuzz objects.
     * References a shared SharedFontData to avoid duplicating font resources.
     * Each glyph has its own buffer and draw_funcs, but shares the font.
     */
    struct GlyphHandle {
        SharedFontData *sharedFont;  // Reference to shared font data (not owned)
        hb_buffer_t *buffer;
        hb_draw_funcs_t *draw_funcs;
        hb_codepoint_t glyph_id;
        unsigned int codepoint;

        GlyphHandle();

        ~GlyphHandle();

        // Initialize the handle with shared font data and codepoint
        bool initialize(SharedFontData *sharedFontData, unsigned int cp);

        // Extract path with current variations (reuses HarfBuzz objects)
        GlyphPath extractPath(const Variation *variations, size_t variationCount);

        // Clean up per-glyph HarfBuzz resources (doesn't touch shared font)
        void destroy();
    };

} // namespace fontsubsetting

#endif // FONTSUBSETTING_RUNTIME_FONT_PATH_EXTRACTOR_H
