#include "glyph_extractor.h"
#include <hb-ot.h>
#include <stdlib.h>
#include <string.h>

/* --- FloatBuffer helpers --- */

static void fb_init(FloatBuffer* fb) {
    fb->data = NULL;
    fb->size = 0;
    fb->capacity = 0;
}

static void fb_clear(FloatBuffer* fb) {
    fb->size = 0;
}

static void fb_ensure(FloatBuffer* fb, size_t additional) {
    size_t needed = fb->size + additional;
    if (needed <= fb->capacity) return;
    size_t cap = fb->capacity ? fb->capacity * 2 : 256;
    while (cap < needed) cap *= 2;
    fb->data = (float*)realloc(fb->data, cap * sizeof(float));
    fb->capacity = cap;
}

static void fb_push(FloatBuffer* fb, float v) {
    fb_ensure(fb, 1);
    fb->data[fb->size++] = v;
}

static void fb_free(FloatBuffer* fb) {
    free(fb->data);
    fb->data = NULL;
    fb->size = 0;
    fb->capacity = 0;
}

/* --- HarfBuzz draw callbacks --- */

typedef struct {
    FloatBuffer* buf;
    float inv_upem;
} PathCtx;

static void move_to_cb(hb_draw_funcs_t* df, void* user_data, hb_draw_state_t* st,
                        float x, float y, void* ud) {
    PathCtx* c = (PathCtx*)user_data;
    (void)df; (void)st; (void)ud;
    fb_push(c->buf, PATH_MOVE_TO);
    fb_push(c->buf, x * c->inv_upem);
    fb_push(c->buf, -y * c->inv_upem); /* Negate Y: font Y-up -> Android Y-down */
}

static void line_to_cb(hb_draw_funcs_t* df, void* user_data, hb_draw_state_t* st,
                        float x, float y, void* ud) {
    PathCtx* c = (PathCtx*)user_data;
    (void)df; (void)st; (void)ud;
    fb_push(c->buf, PATH_LINE_TO);
    fb_push(c->buf, x * c->inv_upem);
    fb_push(c->buf, -y * c->inv_upem);
}

static void quadratic_to_cb(hb_draw_funcs_t* df, void* user_data, hb_draw_state_t* st,
                             float cx, float cy,
                             float x, float y, void* ud) {
    PathCtx* c = (PathCtx*)user_data;
    (void)df; (void)st; (void)ud;
    fb_push(c->buf, PATH_QUAD_TO);
    fb_push(c->buf, cx * c->inv_upem);
    fb_push(c->buf, -cy * c->inv_upem);
    fb_push(c->buf, x * c->inv_upem);
    fb_push(c->buf, -y * c->inv_upem);
}

static void cubic_to_cb(hb_draw_funcs_t* df, void* user_data, hb_draw_state_t* st,
                         float cx1, float cy1,
                         float cx2, float cy2,
                         float x, float y, void* ud) {
    PathCtx* c = (PathCtx*)user_data;
    (void)df; (void)st; (void)ud;
    fb_push(c->buf, PATH_CUBIC_TO);
    fb_push(c->buf, cx1 * c->inv_upem);
    fb_push(c->buf, -cy1 * c->inv_upem);
    fb_push(c->buf, cx2 * c->inv_upem);
    fb_push(c->buf, -cy2 * c->inv_upem);
    fb_push(c->buf, x * c->inv_upem);
    fb_push(c->buf, -y * c->inv_upem);
}

static void close_path_cb(hb_draw_funcs_t* df, void* user_data, hb_draw_state_t* st,
                           void* ud) {
    PathCtx* c = (PathCtx*)user_data;
    (void)df; (void)st; (void)ud;
    fb_push(c->buf, PATH_CLOSE);
}

/* --- Public API --- */

FontHandle* font_create(const uint8_t* data, size_t size) {
    hb_blob_t* blob = hb_blob_create(
        (const char*)data,
        (unsigned int)size,
        HB_MEMORY_MODE_DUPLICATE,
        NULL, NULL
    );
    if (!blob) return NULL;

    hb_face_t* face = hb_face_create(blob, 0);
    if (!face) {
        hb_blob_destroy(blob);
        return NULL;
    }

    hb_font_t* font = hb_font_create(face);
    if (!font) {
        hb_face_destroy(face);
        hb_blob_destroy(blob);
        return NULL;
    }

    /* Create draw funcs (reusable, immutable after creation) */
    hb_draw_funcs_t* draw_funcs = hb_draw_funcs_create();
    hb_draw_funcs_set_move_to_func(draw_funcs, move_to_cb, NULL, NULL);
    hb_draw_funcs_set_line_to_func(draw_funcs, line_to_cb, NULL, NULL);
    hb_draw_funcs_set_quadratic_to_func(draw_funcs, quadratic_to_cb, NULL, NULL);
    hb_draw_funcs_set_cubic_to_func(draw_funcs, cubic_to_cb, NULL, NULL);
    hb_draw_funcs_set_close_path_func(draw_funcs, close_path_cb, NULL, NULL);
    hb_draw_funcs_make_immutable(draw_funcs);

    unsigned int upem = hb_face_get_upem(face);

    FontHandle* handle = (FontHandle*)malloc(sizeof(FontHandle));
    handle->blob = blob;
    handle->face = face;
    handle->font = font;
    handle->draw_funcs = draw_funcs;
    handle->upem = upem;
    handle->inv_upem = 1.0f / (float)upem;
    fb_init(&handle->collector);

    return handle;
}

void font_destroy(FontHandle* handle) {
    if (!handle) return;
    hb_draw_funcs_destroy(handle->draw_funcs);
    hb_font_destroy(handle->font);
    hb_face_destroy(handle->face);
    hb_blob_destroy(handle->blob);
    fb_free(&handle->collector);
    free(handle);
}

int glyph_extract(
    FontHandle* handle,
    uint32_t codepoint,
    const hb_variation_t* variations,
    unsigned int num_variations,
    const float** out_data,
    size_t* out_size
) {
    /* Apply variation axes */
    hb_font_set_variations(handle->font, variations, num_variations);

    /* Map codepoint to glyph ID */
    hb_codepoint_t glyph_id;
    if (!hb_font_get_nominal_glyph(handle->font, codepoint, &glyph_id)) {
        return -1;
    }

    /* Extract outline into reusable buffer */
    fb_clear(&handle->collector);
    PathCtx ctx = { &handle->collector, handle->inv_upem };
    hb_font_draw_glyph(handle->font, glyph_id, handle->draw_funcs, &ctx);

    *out_data = handle->collector.data;
    *out_size = handle->collector.size;
    return 0;
}

int glyph_extract_batch(
    FontHandle* handle,
    uint32_t codepoint,
    const hb_variation_t* variations,
    unsigned int num_axes,
    unsigned int num_sets,
    const float** out_data,
    size_t* out_size
) {
    /* Map codepoint to glyph ID (same for all variations) */
    hb_codepoint_t glyph_id;
    if (!hb_font_get_nominal_glyph(handle->font, codepoint, &glyph_id)) {
        return -1;
    }

    /* Use a separate temporary buffer for per-set extraction */
    FloatBuffer tmp;
    fb_init(&tmp);

    /* Accumulate all sets into the main collector */
    fb_clear(&handle->collector);

    unsigned int i;
    for (i = 0; i < num_sets; i++) {
        hb_font_set_variations(handle->font, variations + (i * num_axes), num_axes);

        fb_clear(&tmp);
        PathCtx ctx = { &tmp, handle->inv_upem };
        hb_font_draw_glyph(handle->font, glyph_id, handle->draw_funcs, &ctx);

        /* Append: [count, ...path_data...] */
        fb_push(&handle->collector, (float)tmp.size);
        fb_ensure(&handle->collector, tmp.size);
        memcpy(handle->collector.data + handle->collector.size, tmp.data, tmp.size * sizeof(float));
        handle->collector.size += tmp.size;
    }

    fb_free(&tmp);

    *out_data = handle->collector.data;
    *out_size = handle->collector.size;
    return 0;
}
