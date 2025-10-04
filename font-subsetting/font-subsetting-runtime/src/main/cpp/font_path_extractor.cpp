#include "font_path_extractor.h"
#include <hb.h>

// Direct declarations to avoid including <cstdlib>
extern "C" {
void *malloc(unsigned long);
void free(void *);
void *realloc(void *, unsigned long);
void *memcpy(void *, const void *, unsigned long);
}

// Direct constant definition to avoid including <cfloat>
#define FLT_MAX 3.40282347e+38F

// Lightweight min/max to avoid <algorithm> header bloat
template<typename T>
static inline T min(T a, T b) { return a < b ? a : b; }

template<typename T>
static inline T max(T a, T b) { return a > b ? a : b; }

namespace fontsubsetting {

// Context for path drawing callbacks
    struct PathDrawContext {
        PathCommandArray *commands;
    };

// PathCommandArray implementation
    PathCommandArray::PathCommandArray() : data(nullptr), size(0), capacity(0) {
        // Pre-allocate reasonable capacity for typical glyphs (avoids ~3-4 reallocs)
        reserve(32);
    }

    PathCommandArray::~PathCommandArray() {
        if (data) {
            free(data);
            data = nullptr;
        }
        size = 0;
        capacity = 0;
    }

    void PathCommandArray::reserve(unsigned long new_capacity) {
        if (new_capacity <= capacity) return;

        PathCommand *new_data = (PathCommand *) malloc(new_capacity * sizeof(PathCommand));
        if (!new_data) {
            return;
        }

        // Copy existing data
        if (size > 0) {
            memcpy(new_data, data, size * sizeof(PathCommand));
        }

        // Free old data if it was heap-allocated
        if (data != inline_storage) {
            free(data);
        }

        data = new_data;
        capacity = new_capacity;
    }

    void PathCommandArray::grow() {
        unsigned long new_capacity = capacity * 2;
        reserve(new_capacity);
    }

    void PathCommandArray::push_back(const PathCommand &cmd) {
        if (size >= capacity) {
            grow();
        }
        if (size < capacity) {
            data[size++] = cmd;
        }
    }

    void PathCommandArray::clear() {
        size = 0;
    }

    // GlyphPath implementation
    GlyphPath::GlyphPath()
            : advanceWidth(0), unitsPerEm(0),
              minX(0), minY(0), maxX(0), maxY(0) {}

    GlyphPath::~GlyphPath() {}

    // HarfBuzz draw callbacks - store coordinates in font units
    __attribute__((always_inline))
    static void move_to_func(hb_draw_funcs_t * /*dfuncs*/, void *draw_data,
                             hb_draw_state_t * /*st*/,
                             float to_x, float to_y,
                             void * /*user_data*/) {
        auto *ctx = static_cast<PathDrawContext *>(draw_data);
        PathCommand cmd;
        cmd.type = PathCommand::MOVE_TO;
        cmd.point.x = to_x;
        cmd.point.y = to_y;
        ctx->commands->push_back(cmd);
    }

    __attribute__((always_inline))
    static void line_to_func(hb_draw_funcs_t * /*dfuncs*/, void *draw_data,
                             hb_draw_state_t * /*st*/,
                             float to_x, float to_y,
                             void * /*user_data*/) {
        auto *ctx = static_cast<PathDrawContext *>(draw_data);
        PathCommand cmd;
        cmd.type = PathCommand::LINE_TO;
        cmd.point.x = to_x;
        cmd.point.y = to_y;
        ctx->commands->push_back(cmd);
    }

    __attribute__((always_inline))
    static void quadratic_to_func(hb_draw_funcs_t * /*dfuncs*/, void *draw_data,
                                  hb_draw_state_t * /*st*/,
                                  float control_x, float control_y,
                                  float to_x, float to_y,
                                  void * /*user_data*/) {
        auto *ctx = static_cast<PathDrawContext *>(draw_data);
        PathCommand cmd;
        cmd.type = PathCommand::QUADRATIC_TO;
        cmd.quadratic.cx = control_x;
        cmd.quadratic.cy = control_y;
        cmd.quadratic.x = to_x;
        cmd.quadratic.y = to_y;
        ctx->commands->push_back(cmd);
    }

    __attribute__((always_inline))
    static void cubic_to_func(hb_draw_funcs_t * /*dfuncs*/, void *draw_data,
                              hb_draw_state_t * /*st*/,
                              float control1_x, float control1_y,
                              float control2_x, float control2_y,
                              float to_x, float to_y,
                              void * /*user_data*/) {
        auto *ctx = static_cast<PathDrawContext *>(draw_data);
        PathCommand cmd;
        cmd.type = PathCommand::CUBIC_TO;
        cmd.cubic.cx1 = control1_x;
        cmd.cubic.cy1 = control1_y;
        cmd.cubic.cx2 = control2_x;
        cmd.cubic.cy2 = control2_y;
        cmd.cubic.x = to_x;
        cmd.cubic.y = to_y;
        ctx->commands->push_back(cmd);
    }

    __attribute__((always_inline))
    static void close_path_func(hb_draw_funcs_t * /*dfuncs*/, void *draw_data,
                                hb_draw_state_t * /*st*/,
                                void * /*user_data*/) {
        auto *ctx = static_cast<PathDrawContext *>(draw_data);
        PathCommand cmd;
        cmd.type = PathCommand::CLOSE;
        ctx->commands->push_back(cmd);
    }

    // SharedFontData implementation
    SharedFontData::SharedFontData()
            : blob(nullptr), face(nullptr), prototypeFont(nullptr),
              reusable_buffer(nullptr), draw_funcs(nullptr), upem(0) {}

    SharedFontData::~SharedFontData() {
        destroy();
    }

    void SharedFontData::destroy() {
        // Clean up reusable resources
        if (draw_funcs) {
            hb_draw_funcs_destroy(draw_funcs);
            draw_funcs = nullptr;
        }
        if (reusable_buffer) {
            hb_buffer_destroy(reusable_buffer);
            reusable_buffer = nullptr;
        }
        if (prototypeFont) {
            hb_font_destroy(prototypeFont);
            prototypeFont = nullptr;
        }
        if (face) {
            hb_face_destroy(face);
            face = nullptr;
        }
        if (blob) {
            hb_blob_destroy(blob);
            blob = nullptr;
        }
    }

    bool SharedFontData::initialize(const void *fontData, unsigned long fontDataSize) {
        if (!fontData || fontDataSize == 0) {
            return false;
        }

        // Create HarfBuzz blob from font data
        blob = hb_blob_create(
                static_cast<const char *>(fontData),
                static_cast<unsigned int>(fontDataSize),
                HB_MEMORY_MODE_READONLY,
                nullptr,
                nullptr
        );

        if (!blob) {
            return false;
        }

        // Create face
        face = hb_face_create(blob, 0);
        if (!face) {
            destroy();
            return false;
        }

        // Get units per EM
        upem = hb_face_get_upem(face);

        // Create prototype font (shared across all glyph extractions)
        prototypeFont = hb_font_create(face);
        if (!prototypeFont) {
            destroy();
            return false;
        }

        // Create reusable buffer (saves 1-2KB allocation per extraction)
        reusable_buffer = hb_buffer_create();
        if (!reusable_buffer) {
            destroy();
            return false;
        }

        // Create reusable draw_funcs (saves ~300B + setup per extraction)
        draw_funcs = hb_draw_funcs_create();
        if (!draw_funcs) {
            destroy();
            return false;
        }

        // Set up draw callbacks once (reused for all extractions)
        hb_draw_funcs_set_move_to_func(draw_funcs, move_to_func, nullptr, nullptr);
        hb_draw_funcs_set_line_to_func(draw_funcs, line_to_func, nullptr, nullptr);
        hb_draw_funcs_set_quadratic_to_func(draw_funcs, quadratic_to_func, nullptr, nullptr);
        hb_draw_funcs_set_cubic_to_func(draw_funcs, cubic_to_func, nullptr, nullptr);
        hb_draw_funcs_set_close_path_func(draw_funcs, close_path_func, nullptr, nullptr);

        return true;
    }

    __attribute__((hot))
    GlyphPath SharedFontData::extractPathDirect(
            unsigned int codepoint,
            const Variation *variations,
            unsigned long variationCount) {

        GlyphPath result;

        if (!prototypeFont || !reusable_buffer || !draw_funcs) {
            return result;
        }

        result.unitsPerEm = upem;

        // Apply variable font variations if provided (max 4 axes for Material Symbols)
        if (variationCount > 0 && variations) {
            hb_variation_t hb_variations[4];
            unsigned long actual_count = variationCount > 4 ? 4 : variationCount;

            for (unsigned long i = 0; i < actual_count; i++) {
                const char *tag_str = variations[i].tag;
                hb_variations[i].tag = HB_TAG(
                        tag_str[0] ? tag_str[0] : ' ',
                        tag_str[1] ? tag_str[1] : ' ',
                        tag_str[2] ? tag_str[2] : ' ',
                        tag_str[3] ? tag_str[3] : ' '
                );
                hb_variations[i].value = variations[i].value;
            }

            hb_font_set_variations(prototypeFont, hb_variations, actual_count);
        } else {
            // Clear variations (set to defaults)
            hb_font_set_variations(prototypeFont, nullptr, 0);
        }

        // Shape with variations to get correct glyph ID (applies RVRN feature)
        // Reuse buffer instead of creating new one
        hb_buffer_clear_contents(reusable_buffer);
        hb_buffer_add(reusable_buffer, codepoint, 0);
        hb_buffer_set_direction(reusable_buffer, HB_DIRECTION_LTR);
        hb_buffer_set_script(reusable_buffer, HB_SCRIPT_COMMON);
        hb_buffer_set_language(reusable_buffer, HB_LANGUAGE_INVALID);

        hb_shape(prototypeFont, reusable_buffer, nullptr, 0);

        // Get glyph ID after shaping
        unsigned int glyph_count;
        hb_glyph_info_t *glyph_info = hb_buffer_get_glyph_infos(reusable_buffer, &glyph_count);

        if (glyph_count == 0 || !glyph_info) {
            return result;
        }

        hb_codepoint_t glyph_id = glyph_info[0].codepoint;

        // Get glyph metrics
        hb_position_t advance_width = hb_font_get_glyph_h_advance(prototypeFont, glyph_id);
        float scale = 1.0f / static_cast<float>(upem);
        result.advanceWidth = static_cast<float>(advance_width) * scale;

        // Prepare context for path drawing
        PathDrawContext ctx;
        ctx.commands = &result.commands;

        // Extract glyph outline using reusable draw_funcs
        hb_font_draw_glyph(prototypeFont, glyph_id, draw_funcs, &ctx);

        // Scale path coordinates and calculate bounding box in single pass
        float pathMinX = FLT_MAX, pathMinY = FLT_MAX, pathMaxX = -FLT_MAX, pathMaxY = -FLT_MAX;

        for (unsigned long i = 0; i < result.commands.size; i++) {
            PathCommand &cmd = result.commands.data[i];

            switch (cmd.type) {
                case PathCommand::MOVE_TO:
                case PathCommand::LINE_TO:
                    cmd.point.x *= scale;
                    cmd.point.y *= scale;
                    pathMinX = min(pathMinX, cmd.point.x);
                    pathMinY = min(pathMinY, cmd.point.y);
                    pathMaxX = max(pathMaxX, cmd.point.x);
                    pathMaxY = max(pathMaxY, cmd.point.y);
                    break;

                case PathCommand::QUADRATIC_TO:
                    cmd.quadratic.cx *= scale;
                    cmd.quadratic.cy *= scale;
                    cmd.quadratic.x *= scale;
                    cmd.quadratic.y *= scale;
                    pathMinX = min(pathMinX, min(cmd.quadratic.cx, cmd.quadratic.x));
                    pathMinY = min(pathMinY, min(cmd.quadratic.cy, cmd.quadratic.y));
                    pathMaxX = max(pathMaxX, max(cmd.quadratic.cx, cmd.quadratic.x));
                    pathMaxY = max(pathMaxY, max(cmd.quadratic.cy, cmd.quadratic.y));
                    break;

                case PathCommand::CUBIC_TO:
                    cmd.cubic.cx1 *= scale;
                    cmd.cubic.cy1 *= scale;
                    cmd.cubic.cx2 *= scale;
                    cmd.cubic.cy2 *= scale;
                    cmd.cubic.x *= scale;
                    cmd.cubic.y *= scale;
                    pathMinX = min(pathMinX, min(min(cmd.cubic.cx1, cmd.cubic.cx2), cmd.cubic.x));
                    pathMinY = min(pathMinY, min(min(cmd.cubic.cy1, cmd.cubic.cy2), cmd.cubic.y));
                    pathMaxX = max(pathMaxX, max(max(cmd.cubic.cx1, cmd.cubic.cx2), cmd.cubic.x));
                    pathMaxY = max(pathMaxY, max(max(cmd.cubic.cy1, cmd.cubic.cy2), cmd.cubic.y));
                    break;

                case PathCommand::CLOSE:
                    break;
            }
        }

        result.minX = pathMinX;
        result.minY = pathMinY;
        result.maxX = pathMaxX;
        result.maxY = pathMaxY;

        return result;
    }

} // namespace fontsubsetting
