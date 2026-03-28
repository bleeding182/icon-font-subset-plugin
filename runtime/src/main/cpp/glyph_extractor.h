#ifndef GLYPH_EXTRACTOR_H
#define GLYPH_EXTRACTOR_H

#include <stdint.h>
#include <stddef.h>
#include <hb.h>

#ifdef __cplusplus
extern "C" {
#endif

/* Path command markers (stored as float in the path data array) */
#define PATH_MOVE_TO  0.0f
#define PATH_LINE_TO  1.0f
#define PATH_QUAD_TO  2.0f
#define PATH_CUBIC_TO 3.0f
#define PATH_CLOSE    4.0f

/* Growable float buffer (replaces std::vector<float>) */
typedef struct {
    float* data;
    size_t size;
    size_t capacity;
} FloatBuffer;

typedef struct {
    hb_blob_t* blob;
    hb_face_t* face;
    hb_font_t* font;
    hb_draw_funcs_t* draw_funcs;
    unsigned int upem;
    float inv_upem;
    FloatBuffer collector; /* reusable path buffer */
} FontHandle;

FontHandle* font_create(const uint8_t* data, size_t size);
void font_destroy(FontHandle* handle);

/*
 * Extract a single glyph path for the given codepoint and variation axes.
 * Returns path commands via out_data/out_size (em-normalized coordinates).
 * Caller must NOT free out_data — it points into the FontHandle's reusable buffer.
 * Returns 0 on success, -1 if codepoint not found.
 */
int glyph_extract(
    FontHandle* handle,
    uint32_t codepoint,
    const hb_variation_t* variations,
    unsigned int num_variations,
    const float** out_data,
    size_t* out_size
);

/*
 * Batch extract: same glyph with multiple variation settings.
 * |variations| is flattened: num_axes * num_sets entries.
 * Returns concatenated: [count0, ...path0..., count1, ...path1..., ...]
 * via out_data/out_size. Caller must NOT free out_data.
 * Returns 0 on success, -1 if codepoint not found.
 */
int glyph_extract_batch(
    FontHandle* handle,
    uint32_t codepoint,
    const hb_variation_t* variations,
    unsigned int num_axes,
    unsigned int num_sets,
    const float** out_data,
    size_t* out_size
);

#ifdef __cplusplus
}
#endif

#endif /* GLYPH_EXTRACTOR_H */
