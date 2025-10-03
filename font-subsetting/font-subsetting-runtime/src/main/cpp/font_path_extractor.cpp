#include "font_path_extractor.h"
#include <hb.h>
#include <hb-ot.h>
#include <android/log.h>
#include <cmath>
#include <cstring>
#include <cstdlib>

#define LOG_TAG "FontPathExtractor"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)

namespace fontsubsetting {

// RAII wrapper for HarfBuzz blob
    struct HBBlobDeleter {
        void operator()(hb_blob_t *blob) const {
            if (blob) hb_blob_destroy(blob);
        }
    };

// RAII wrapper for HarfBuzz face
    struct HBFaceDeleter {
        void operator()(hb_face_t *face) const {
            if (face) hb_face_destroy(face);
        }
    };

// RAII wrapper for HarfBuzz font
    struct HBFontDeleter {
        void operator()(hb_font_t *font) const {
            if (font) hb_font_destroy(font);
        }
    };

// Context for path drawing callbacks
    struct PathDrawContext {
        PathCommandArray *commands;
        float scale;
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
            LOGE("Failed to allocate memory for path commands");
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
            : advanceWidth(0), advanceHeight(0), unitsPerEm(0),
              minX(0), minY(0), maxX(0), maxY(0) {}

    GlyphPath::~GlyphPath() {}

// HarfBuzz draw callbacks
    static void move_to_func(hb_draw_funcs_t * /*dfuncs*/, void *draw_data,
                             hb_draw_state_t * /*st*/,
                             float to_x, float to_y,
                             void * /*user_data*/) {
        auto *ctx = static_cast<PathDrawContext *>(draw_data);
        PathCommand cmd;
        cmd.type = PathCommand::MOVE_TO;
        cmd.x1 = to_x * ctx->scale;
        cmd.y1 = to_y * ctx->scale;
        ctx->commands->push_back(cmd);
    }

    static void line_to_func(hb_draw_funcs_t * /*dfuncs*/, void *draw_data,
                             hb_draw_state_t * /*st*/,
                             float to_x, float to_y,
                             void * /*user_data*/) {
        auto *ctx = static_cast<PathDrawContext *>(draw_data);
        PathCommand cmd;
        cmd.type = PathCommand::LINE_TO;
        cmd.x1 = to_x * ctx->scale;
        cmd.y1 = to_y * ctx->scale;
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
    cmd.x1 = control_x * ctx->scale;
    cmd.y1 = control_y * ctx->scale;
    cmd.x2 = to_x * ctx->scale;
    cmd.y2 = to_y * ctx->scale;
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
        cmd.x1 = control1_x * ctx->scale;
        cmd.y1 = control1_y * ctx->scale;
        cmd.x2 = control2_x * ctx->scale;
        cmd.y2 = control2_y * ctx->scale;
        cmd.x3 = to_x * ctx->scale;
        cmd.y3 = to_y * ctx->scale;
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
            LOGE("Invalid font data");
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
            LOGE("Failed to create blob");
            return result;
        }
        HBBlobDeleter blob_deleter;

        // Create face
        hb_face_t *face = hb_face_create(blob, 0);
        blob_deleter(blob); // Clean up blob

        if (!face) {
            LOGE("Failed to create face");
            return result;
        }
        HBFaceDeleter face_deleter;

        // Get units per EM for scaling
        unsigned int upem = hb_face_get_upem(face);
        result.unitsPerEm = static_cast<int>(upem);

        // Create font
        hb_font_t *font = hb_font_create(face);
        if (!font) {
            LOGE("Failed to create font");
            face_deleter(face);
            return result;
        }
        HBFontDeleter font_deleter;

        // Apply variable font variations if any
        if (variationCount > 0 && variations) {
            // Stack allocate for reasonable number of variations (usually 1-3)
            hb_variation_t hb_variations[16];
            size_t actual_count = variationCount > 16 ? 16 : variationCount;

            for (size_t i = 0; i < actual_count; i++) {
                hb_variation_t var;
                const char *tag_str = variations[i].tag;
                // Convert tag string to HarfBuzz tag (4-byte code)
                var.tag = HB_TAG(
                        tag_str[0] ? tag_str[0] : ' ',
                        tag_str[1] ? tag_str[1] : ' ',
                        tag_str[2] ? tag_str[2] : ' ',
                        tag_str[3] ? tag_str[3] : ' '
                );
                var.value = variations[i].value;
                hb_variations[i] = var;

                LOGD("Applying variation: %s = %.2f", tag_str, variations[i].value);
            }

            hb_font_set_variations(font, hb_variations, actual_count);
        }

        // Get glyph ID from codepoint
        hb_codepoint_t glyph_id;
        if (!hb_font_get_nominal_glyph(font, codepoint, &glyph_id)) {
            LOGD("Glyph not found for codepoint U+%04X", codepoint);
            font_deleter(font);
            face_deleter(face);
            return result;
        }

        // Get glyph advance metrics
        hb_position_t advance_width = hb_font_get_glyph_h_advance(font, glyph_id);
        hb_position_t advance_height = hb_font_get_glyph_v_advance(font, glyph_id);

        // Scale to normalized coordinates (0-1 range based on upem)
        float scale = 1.0f / static_cast<float>(upem);
        result.advanceWidth = static_cast<float>(advance_width) * scale;
        result.advanceHeight = static_cast<float>(advance_height) * scale;

        // Get glyph extents (bounding box)
        hb_glyph_extents_t extents;
        if (hb_font_get_glyph_extents(font, glyph_id, &extents)) {
            result.minX = static_cast<float>(extents.x_bearing) * scale;
            result.minY = static_cast<float>(extents.y_bearing + extents.height) * scale;
            result.maxX = static_cast<float>(extents.x_bearing + extents.width) * scale;
            result.maxY = static_cast<float>(extents.y_bearing) * scale;

            LOGD("Glyph extents: minX=%.3f, minY=%.3f, maxX=%.3f, maxY=%.3f",
                 result.minX, result.minY, result.maxX, result.maxY);
        } else {
            // Fallback: use default values
            result.minX = 0.0f;
            result.minY = 0.0f;
            result.maxX = result.advanceWidth;
            result.maxY = 1.0f;
            LOGD("Failed to get glyph extents, using defaults");
        }

        // Create draw funcs
        hb_draw_funcs_t *draw_funcs = hb_draw_funcs_create();
        if (!draw_funcs) {
            LOGE("Failed to create draw funcs");
            font_deleter(font);
            face_deleter(face);
            return result;
        }

        // Set callbacks
        hb_draw_funcs_set_move_to_func(draw_funcs, move_to_func, nullptr, nullptr);
        hb_draw_funcs_set_line_to_func(draw_funcs, line_to_func, nullptr, nullptr);
        hb_draw_funcs_set_quadratic_to_func(draw_funcs, quadratic_to_func, nullptr, nullptr);
        hb_draw_funcs_set_cubic_to_func(draw_funcs, cubic_to_func, nullptr, nullptr);
        hb_draw_funcs_set_close_path_func(draw_funcs, close_path_func, nullptr, nullptr);

        // Prepare context for drawing
        PathDrawContext ctx;
        ctx.commands = &result.commands;
        ctx.scale = scale;

        // Extract glyph outline
        hb_font_get_glyph_shape(font, glyph_id, draw_funcs, &ctx);

        // Clean up
        hb_draw_funcs_destroy(draw_funcs);
        font_deleter(font);
        face_deleter(face);

        LOGD("Extracted %zu path commands for codepoint U+%04X (glyph %u) with %zu variations",
             result.commands.size, codepoint, glyph_id, variationCount);

        return result;
    }

} // namespace fontsubsetting
