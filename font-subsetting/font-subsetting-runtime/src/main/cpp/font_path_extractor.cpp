#include "font_path_extractor.h"
#include <hb.h>

// Direct declarations to avoid including <cstdlib>
extern "C" {
void *malloc(unsigned long);
void free(void *);
void *realloc(void *, unsigned long);
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
    PathCommandArray::PathCommandArray() : data(nullptr), size(0), capacity(0) {}

    PathCommandArray::~PathCommandArray() {
        if (data) {
            free(data);
            data = nullptr;
        }
        size = 0;
        capacity = 0;
    }

    void PathCommandArray::reserve(size_t new_capacity) {
        if (new_capacity <= capacity) return;

        PathCommand *new_data = (PathCommand *) realloc(data, new_capacity * sizeof(PathCommand));
        if (!new_data) {
            return;
        }
        data = new_data;
        capacity = new_capacity;
    }

    void PathCommandArray::push_back(const PathCommand &cmd) {
        if (size >= capacity) {
            size_t new_capacity = capacity == 0 ? 8 : capacity * 2;
            reserve(new_capacity);
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
    static void move_to_func(hb_draw_funcs_t * /*dfuncs*/, void *draw_data,
                             hb_draw_state_t * /*st*/,
                             float to_x, float to_y,
                             void * /*user_data*/) {
        auto *ctx = static_cast<PathDrawContext *>(draw_data);
        PathCommand cmd;
        cmd.type = PathCommand::MOVE_TO;
        cmd.x1 = to_x;
        cmd.y1 = to_y;
        ctx->commands->push_back(cmd);
    }

    static void line_to_func(hb_draw_funcs_t * /*dfuncs*/, void *draw_data,
                             hb_draw_state_t * /*st*/,
                             float to_x, float to_y,
                             void * /*user_data*/) {
        auto *ctx = static_cast<PathDrawContext *>(draw_data);
        PathCommand cmd;
        cmd.type = PathCommand::LINE_TO;
        cmd.x1 = to_x;
        cmd.y1 = to_y;
        ctx->commands->push_back(cmd);
    }

    static void quadratic_to_func(hb_draw_funcs_t * /*dfuncs*/, void *draw_data,
                                  hb_draw_state_t * /*st*/,
                                  float control_x, float control_y,
                                  float to_x, float to_y,
                                  void * /*user_data*/) {
        auto *ctx = static_cast<PathDrawContext *>(draw_data);
        PathCommand cmd;
        cmd.type = PathCommand::QUADRATIC_TO;
        cmd.x1 = control_x;
        cmd.y1 = control_y;
        cmd.x2 = to_x;
        cmd.y2 = to_y;
        ctx->commands->push_back(cmd);
    }

    static void cubic_to_func(hb_draw_funcs_t * /*dfuncs*/, void *draw_data,
                              hb_draw_state_t * /*st*/,
                              float control1_x, float control1_y,
                              float control2_x, float control2_y,
                              float to_x, float to_y,
                              void * /*user_data*/) {
        auto *ctx = static_cast<PathDrawContext *>(draw_data);
        PathCommand cmd;
        cmd.type = PathCommand::CUBIC_TO;
        cmd.x1 = control1_x;
        cmd.y1 = control1_y;
        cmd.x2 = control2_x;
        cmd.y2 = control2_y;
        cmd.x3 = to_x;
        cmd.y3 = to_y;
        ctx->commands->push_back(cmd);
    }

    static void close_path_func(hb_draw_funcs_t * /*dfuncs*/, void *draw_data,
                                hb_draw_state_t * /*st*/,
                                void * /*user_data*/) {
        auto *ctx = static_cast<PathDrawContext *>(draw_data);
        PathCommand cmd;
        cmd.type = PathCommand::CLOSE;
        ctx->commands->push_back(cmd);
    }

    // GlyphHandle implementation
    GlyphHandle::GlyphHandle()
            : blob(nullptr), face(nullptr), font(nullptr), buffer(nullptr),
              draw_funcs(nullptr), glyph_id(0), codepoint(0), upem(0) {}

    GlyphHandle::~GlyphHandle() {
        destroy();
    }

    void GlyphHandle::destroy() {
        if (draw_funcs) {
            hb_draw_funcs_destroy(draw_funcs);
            draw_funcs = nullptr;
        }
        if (buffer) {
            hb_buffer_destroy(buffer);
            buffer = nullptr;
        }
        if (font) {
            hb_font_destroy(font);
            font = nullptr;
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

    bool GlyphHandle::initialize(const void *fontData, size_t fontDataSize, unsigned int cp) {
        if (!fontData || fontDataSize == 0) {
            return false;
        }

        codepoint = cp;

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

        // Create font
        font = hb_font_create(face);
        if (!font) {
            destroy();
            return false;
        }

        // Create buffer for shaping (reusable)
        buffer = hb_buffer_create();
        if (!buffer) {
            destroy();
            return false;
        }

        // Create draw funcs (reusable)
        draw_funcs = hb_draw_funcs_create();
        if (!draw_funcs) {
            destroy();
            return false;
        }

        // Set callbacks on draw_funcs (only needs to be done once)
        hb_draw_funcs_set_move_to_func(draw_funcs, move_to_func, nullptr, nullptr);
        hb_draw_funcs_set_line_to_func(draw_funcs, line_to_func, nullptr, nullptr);
        hb_draw_funcs_set_quadratic_to_func(draw_funcs, quadratic_to_func, nullptr, nullptr);
        hb_draw_funcs_set_cubic_to_func(draw_funcs, cubic_to_func, nullptr, nullptr);
        hb_draw_funcs_set_close_path_func(draw_funcs, close_path_func, nullptr, nullptr);

        // Shape the glyph once to get the glyph ID (with default variations)
        hb_buffer_clear_contents(buffer);
        hb_buffer_add(buffer, codepoint, 0);
        hb_buffer_set_direction(buffer, HB_DIRECTION_LTR);
        hb_buffer_set_script(buffer, HB_SCRIPT_COMMON);
        hb_buffer_set_language(buffer, hb_language_from_string("en", -1));

        hb_shape(font, buffer, nullptr, 0);

        // Get glyph info
        unsigned int glyph_count;
        hb_glyph_info_t *glyph_info = hb_buffer_get_glyph_infos(buffer, &glyph_count);

        if (glyph_count == 0 || !glyph_info) {
            destroy();
            return false;
        }

        // Store the glyph ID (may be substituted by RVRN in extractPath)
        glyph_id = glyph_info[0].codepoint;

        return true;
    }

    GlyphPath GlyphHandle::extractPath(const Variation *variations, size_t variationCount) {
        GlyphPath result;

        if (!font || !buffer || !draw_funcs) {
            return result;
        }

        result.unitsPerEm = static_cast<int>(upem);

        // Apply variable font variations if provided
        if (variationCount > 0 && variations) {
            hb_variation_t hb_variations[16];
            size_t actual_count = variationCount > 16 ? 16 : variationCount;

            for (size_t i = 0; i < actual_count; i++) {
                const char *tag_str = variations[i].tag;
                hb_variations[i].tag = HB_TAG(
                        tag_str[0] ? tag_str[0] : ' ',
                        tag_str[1] ? tag_str[1] : ' ',
                        tag_str[2] ? tag_str[2] : ' ',
                        tag_str[3] ? tag_str[3] : ' '
                );
                hb_variations[i].value = variations[i].value;
            }

            hb_font_set_variations(font, hb_variations, actual_count);
        } else {
            // Clear variations (set to defaults)
            hb_font_set_variations(font, nullptr, 0);
        }

        // Re-shape with new variations to apply RVRN feature
        hb_buffer_clear_contents(buffer);
        hb_buffer_add(buffer, codepoint, 0);
        hb_buffer_set_direction(buffer, HB_DIRECTION_LTR);
        hb_buffer_set_script(buffer, HB_SCRIPT_COMMON);
        hb_buffer_set_language(buffer, hb_language_from_string("en", -1));

        hb_shape(font, buffer, nullptr, 0);

        // Get updated glyph ID (may change with variations)
        unsigned int glyph_count;
        hb_glyph_info_t *glyph_info = hb_buffer_get_glyph_infos(buffer, &glyph_count);

        if (glyph_count == 0 || !glyph_info) {
            return result;
        }

        glyph_id = glyph_info[0].codepoint;

        // Get glyph metrics
        hb_position_t advance_width = hb_font_get_glyph_h_advance(font, glyph_id);
        float scale = 1.0f / static_cast<float>(upem);
        result.advanceWidth = static_cast<float>(advance_width) * scale;

        // Prepare context for path drawing
        PathDrawContext ctx;
        ctx.commands = &result.commands;

        // Extract glyph outline
        hb_font_draw_glyph(font, glyph_id, draw_funcs, &ctx);

        // Scale all path coordinates to normalized space
        for (size_t i = 0; i < result.commands.size; i++) {
            PathCommand &cmd = result.commands.data[i];

            switch (cmd.type) {
                case PathCommand::MOVE_TO:
                case PathCommand::LINE_TO:
                    cmd.x1 *= scale;
                    cmd.y1 *= scale;
                    break;

                case PathCommand::QUADRATIC_TO:
                    cmd.x1 *= scale;
                    cmd.y1 *= scale;
                    cmd.x2 *= scale;
                    cmd.y2 *= scale;
                    break;

                case PathCommand::CUBIC_TO:
                    cmd.x1 *= scale;
                    cmd.y1 *= scale;
                    cmd.x2 *= scale;
                    cmd.y2 *= scale;
                    cmd.x3 *= scale;
                    cmd.y3 *= scale;
                    break;

                case PathCommand::CLOSE:
                    break;
            }
        }

        // Calculate bounding box
        float pathMinX = FLT_MAX, pathMinY = FLT_MAX, pathMaxX = -FLT_MAX, pathMaxY = -FLT_MAX;
        for (size_t i = 0; i < result.commands.size; i++) {
            PathCommand &cmd = result.commands.data[i];

            switch (cmd.type) {
                case PathCommand::MOVE_TO:
                case PathCommand::LINE_TO:
                    pathMinX = min(pathMinX, cmd.x1);
                    pathMinY = min(pathMinY, cmd.y1);
                    pathMaxX = max(pathMaxX, cmd.x1);
                    pathMaxY = max(pathMaxY, cmd.y1);
                    break;

                case PathCommand::QUADRATIC_TO:
                    pathMinX = min(pathMinX, cmd.x1);
                    pathMinY = min(pathMinY, cmd.y1);
                    pathMaxX = max(pathMaxX, cmd.x1);
                    pathMaxY = max(pathMaxY, cmd.y1);
                    pathMinX = min(pathMinX, cmd.x2);
                    pathMinY = min(pathMinY, cmd.y2);
                    pathMaxX = max(pathMaxX, cmd.x2);
                    pathMaxY = max(pathMaxY, cmd.y2);
                    break;

                case PathCommand::CUBIC_TO:
                    pathMinX = min(pathMinX, cmd.x1);
                    pathMinY = min(pathMinY, cmd.y1);
                    pathMaxX = max(pathMaxX, cmd.x1);
                    pathMaxY = max(pathMaxY, cmd.y1);
                    pathMinX = min(pathMinX, cmd.x2);
                    pathMinY = min(pathMinY, cmd.y2);
                    pathMaxX = max(pathMaxX, cmd.x2);
                    pathMaxY = max(pathMaxY, cmd.y2);
                    pathMinX = min(pathMinX, cmd.x3);
                    pathMinY = min(pathMinY, cmd.y3);
                    pathMaxX = max(pathMaxX, cmd.x3);
                    pathMaxY = max(pathMaxY, cmd.y3);
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

    GlyphPath extractGlyphPath(const void *fontData, size_t fontDataSize, unsigned int codepoint) {
        return extractGlyphPathWithVariations(fontData, fontDataSize, codepoint, nullptr, 0);
    }

    GlyphPath extractGlyphPathWithVariations(
            const void *fontData,
            size_t fontDataSize,
            unsigned int codepoint,
            const Variation *variations,
            size_t variationCount
    ) {
        GlyphPath result;

        if (!fontData || fontDataSize == 0) {
            return result;
        }

        // Create HarfBuzz blob from font data
        hb_blob_t *blob = hb_blob_create(
                static_cast<const char *>(fontData),
                static_cast<unsigned int>(fontDataSize),
                HB_MEMORY_MODE_READONLY,
                nullptr,
                nullptr
        );

        if (!blob) {
            return result;
        }

        // Create face
        hb_face_t *face = hb_face_create(blob, 0);
        hb_blob_destroy(blob); // Clean up blob immediately

        if (!face) {
            return result;
        }

        // Get units per EM - this is our coordinate system
        unsigned int upem = hb_face_get_upem(face);
        result.unitsPerEm = static_cast<int>(upem);

        // Create font
        hb_font_t *font = hb_font_create(face);
        if (!font) {
            hb_face_destroy(face);
            return result;
        }

        // Apply variable font variations if provided
        if (variationCount > 0 && variations) {
            // Stack allocate for reasonable number of variations (up to 16)
            hb_variation_t hb_variations[16];
            size_t actual_count = variationCount > 16 ? 16 : variationCount;

            for (size_t i = 0; i < actual_count; i++) {
                const char *tag_str = variations[i].tag;
                // Convert tag string to HarfBuzz tag (4-byte code)
                hb_variations[i].tag = HB_TAG(
                        tag_str[0] ? tag_str[0] : ' ',
                        tag_str[1] ? tag_str[1] : ' ',
                        tag_str[2] ? tag_str[2] : ' ',
                        tag_str[3] ? tag_str[3] : ' '
                );
                hb_variations[i].value = variations[i].value;
            }

            hb_font_set_variations(font, hb_variations, actual_count);
        }

        // Create buffer for shaping (needed to apply RVRN feature for variable fonts)
        hb_buffer_t *buffer = hb_buffer_create();
        if (!buffer) {
            hb_font_destroy(font);
            hb_face_destroy(face);
            return result;
        }

        // Add codepoint to buffer
        hb_buffer_add(buffer, codepoint, 0);
        hb_buffer_set_direction(buffer, HB_DIRECTION_LTR);
        hb_buffer_set_script(buffer, HB_SCRIPT_COMMON);
        hb_buffer_set_language(buffer, hb_language_from_string("en", -1));

        // Shape the buffer - this applies OpenType features including RVRN
        // RVRN (Required Variation Substitutions) is crucial for variable fonts
        hb_shape(font, buffer, nullptr, 0);

        // Get glyph info from shaped buffer
        unsigned int glyph_count;
        hb_glyph_info_t *glyph_info = hb_buffer_get_glyph_infos(buffer, &glyph_count);

        if (glyph_count == 0 || !glyph_info) {
            hb_buffer_destroy(buffer);
            hb_font_destroy(font);
            hb_face_destroy(face);
            return result;
        }

        // Get the actual glyph ID after shaping (may be substituted by RVRN)
        hb_codepoint_t glyph_id = glyph_info[0].codepoint;

        // Get glyph metrics (in font units)
        hb_position_t advance_width = hb_font_get_glyph_h_advance(font, glyph_id);

        // Normalize to 0-1 range based on upem
        float scale = 1.0f / static_cast<float>(upem);
        result.advanceWidth = static_cast<float>(advance_width) * scale;

        // Create draw funcs for path extraction
        hb_draw_funcs_t *draw_funcs = hb_draw_funcs_create();
        if (!draw_funcs) {
            hb_buffer_destroy(buffer);
            hb_font_destroy(font);
            hb_face_destroy(face);
            return result;
        }

        // Set callbacks
        hb_draw_funcs_set_move_to_func(draw_funcs, move_to_func, nullptr, nullptr);
        hb_draw_funcs_set_line_to_func(draw_funcs, line_to_func, nullptr, nullptr);
        hb_draw_funcs_set_quadratic_to_func(draw_funcs, quadratic_to_func, nullptr, nullptr);
        hb_draw_funcs_set_cubic_to_func(draw_funcs, cubic_to_func, nullptr, nullptr);
        hb_draw_funcs_set_close_path_func(draw_funcs, close_path_func, nullptr, nullptr);

        // Prepare context for path drawing
        PathDrawContext ctx;
        ctx.commands = &result.commands;

        // Extract glyph outline - coordinates will be in font units
        hb_font_draw_glyph(font, glyph_id, draw_funcs, &ctx);

        // Scale all path coordinates to normalized space
        for (size_t i = 0; i < result.commands.size; i++) {
            PathCommand &cmd = result.commands.data[i];

            // Scale based on command type
            switch (cmd.type) {
                case PathCommand::MOVE_TO:
                case PathCommand::LINE_TO:
                    cmd.x1 *= scale;
                    cmd.y1 *= scale;
                    break;

                case PathCommand::QUADRATIC_TO:
                    cmd.x1 *= scale;
                    cmd.y1 *= scale;
                    cmd.x2 *= scale;
                    cmd.y2 *= scale;
                    break;

                case PathCommand::CUBIC_TO:
                    cmd.x1 *= scale;
                    cmd.y1 *= scale;
                    cmd.x2 *= scale;
                    cmd.y2 *= scale;
                    cmd.x3 *= scale;
                    cmd.y3 *= scale;
                    break;

                case PathCommand::CLOSE:
                    // No coordinates to scale
                    break;
            }
        }

        // Calculate bounding box from path coordinates
        // Note: This uses control points, which gives a conservative (possibly loose) bound
        // The actual Bezier curves may not reach all control points, but will never exceed them
        float pathMinX = FLT_MAX, pathMinY = FLT_MAX, pathMaxX = -FLT_MAX, pathMaxY = -FLT_MAX;
        for (size_t i = 0; i < result.commands.size; i++) {
            PathCommand &cmd = result.commands.data[i];

            // Update bounds based on command type
            switch (cmd.type) {
                case PathCommand::MOVE_TO:
                case PathCommand::LINE_TO:
                    pathMinX = min(pathMinX, cmd.x1);
                    pathMinY = min(pathMinY, cmd.y1);
                    pathMaxX = max(pathMaxX, cmd.x1);
                    pathMaxY = max(pathMaxY, cmd.y1);
                    break;

                case PathCommand::QUADRATIC_TO:
                    pathMinX = min(pathMinX, cmd.x1);
                    pathMinY = min(pathMinY, cmd.y1);
                    pathMaxX = max(pathMaxX, cmd.x1);
                    pathMaxY = max(pathMaxY, cmd.y1);
                    pathMinX = min(pathMinX, cmd.x2);
                    pathMinY = min(pathMinY, cmd.y2);
                    pathMaxX = max(pathMaxX, cmd.x2);
                    pathMaxY = max(pathMaxY, cmd.y2);
                    break;

                case PathCommand::CUBIC_TO:
                    pathMinX = min(pathMinX, cmd.x1);
                    pathMinY = min(pathMinY, cmd.y1);
                    pathMaxX = max(pathMaxX, cmd.x1);
                    pathMaxY = max(pathMaxY, cmd.y1);
                    pathMinX = min(pathMinX, cmd.x2);
                    pathMinY = min(pathMinY, cmd.y2);
                    pathMaxX = max(pathMaxX, cmd.x2);
                    pathMaxY = max(pathMaxY, cmd.y2);
                    pathMinX = min(pathMinX, cmd.x3);
                    pathMinY = min(pathMinY, cmd.y3);
                    pathMaxX = max(pathMaxX, cmd.x3);
                    pathMaxY = max(pathMaxY, cmd.y3);
                    break;

                case PathCommand::CLOSE:
                    // No coordinates to update bounds
                    break;
            }
        }

        // Use path-calculated bounds (conservative but always correct)
        result.minX = pathMinX;
        result.minY = pathMinY;
        result.maxX = pathMaxX;
        result.maxY = pathMaxY;

        // Clean up
        hb_draw_funcs_destroy(draw_funcs);
        hb_buffer_destroy(buffer);
        hb_font_destroy(font);
        hb_face_destroy(face);

        return result;
    }

} // namespace fontsubsetting
