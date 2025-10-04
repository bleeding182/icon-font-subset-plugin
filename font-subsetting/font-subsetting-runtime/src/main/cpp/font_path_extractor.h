#ifndef FONTSUBSETTING_RUNTIME_FONT_PATH_EXTRACTOR_H
#define FONTSUBSETTING_RUNTIME_FONT_PATH_EXTRACTOR_H

// Direct typedef to avoid including <stddef.h>
//typedef __SIZE_TYPE__ size_t;

#include <hb.h>

namespace fontsubsetting {

    struct PathCommand {
        enum Type : uint8_t {
            MOVE_TO = 0,
            LINE_TO = 1,
            QUADRATIC_TO = 2,
            CUBIC_TO = 3,
            CLOSE = 4
        };

        Type type;
        uint8_t _padding[3];  // Maintain 4-byte alignment for better memory access
        union {
            struct {
                float x, y;
            } point;                          // MOVE_TO, LINE_TO
            struct {
                float cx, cy, x, y;
            } quadratic;              // QUADRATIC_TO
            struct {
                float cx1, cy1, cx2, cy2, x, y;
            } cubic;      // CUBIC_TO
        };
    };

    // Simple dynamic array for path commands with Small Buffer Optimization
    struct PathCommandArray {
        static const unsigned long INLINE_CAPACITY = 16;  // Material icons average ~12-20 commands
        PathCommand inline_storage[INLINE_CAPACITY];
        PathCommand *data;
        unsigned long size;
        unsigned long capacity;

        PathCommandArray();

        ~PathCommandArray();

        void push_back(const PathCommand &cmd);

        void clear();

        void reserve(unsigned long new_capacity);

        bool empty() const { return size == 0; }

    private:
        void grow();
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
     * Contains the HarfBuzz blob, face, font, and reusable resources.
     * This reduces memory usage and initialization overhead significantly.
     * 
     * Key optimizations:
     * - Single HarfBuzz font shared across all glyph extractions
     * - Reusable buffer (avoids 1-2KB allocation per extraction)
     * - Reusable draw_funcs (avoids ~300B allocation + setup per extraction)
     * - Direct extraction without intermediate GlyphHandle objects
     */
    struct SharedFontData {
        hb_blob_t *blob;
        hb_face_t *face;
        hb_font_t *prototypeFont;
        hb_buffer_t *reusable_buffer;      // Reused for all shaping operations
        hb_draw_funcs_t *draw_funcs;       // Reused for all path extractions
        unsigned int upem;

        SharedFontData();

        ~SharedFontData();

        // Initialize from font data
        bool initialize(const void *fontData, unsigned long fontDataSize);

        // Extract path directly without creating intermediate objects
        // This is the main optimized path extraction method
        GlyphPath extractPathDirect(
                unsigned int codepoint,
                const Variation *variations,
                unsigned long variationCount
        );

        // Clean up all HarfBuzz resources
        void destroy();
    };

} // namespace fontsubsetting

#endif // FONTSUBSETTING_RUNTIME_FONT_PATH_EXTRACTOR_H
