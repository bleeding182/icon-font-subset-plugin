#include <jni.h>

// Direct declarations to avoid including <cstdlib> and <cstring>
extern "C" {
void *malloc(unsigned long);
void free(void *);
void *memcpy(void *, const void *, unsigned long);
void *realloc(void *, unsigned long);
}

#include "font_path_extractor.h"

// Native font handle structure - stores font data in native memory
// and a shared HarfBuzz font object that is reused by all glyphs
struct NativeFontHandle {
    void *fontData;                                // Raw font file data
    unsigned long fontDataSize;                    // Size of font data
    fontsubsetting::SharedFontData *sharedFont;    // Shared HarfBuzz objects (blob, face, font, buffer, draw_funcs)
};

// Helper function to convert integer tag to 4-byte tag string
static inline void intToTag(jint tag, char *dest) {
    dest[0] = static_cast<char>((tag >> 24) & 0xFF);
    dest[1] = static_cast<char>((tag >> 16) & 0xFF);
    dest[2] = static_cast<char>((tag >> 8) & 0xFF);
    dest[3] = static_cast<char>(tag & 0xFF);
    dest[4] = '\0';
}

// Helper function to pack GlyphPath into a jfloatArray
// Returns null on allocation failure
static jfloatArray packGlyphPathToArray(JNIEnv *env, const fontsubsetting::GlyphPath &glyphPath) {
    // Calculate result array size
    // Format: [numCommands, advanceWidth, unitsPerEm, minX, minY, maxX, maxY, commands...]
    // Each command: [type, padding(3 floats), union_data(6 floats)] = 10 floats total
    unsigned long commandFloats = glyphPath.commands.size * 10;
    unsigned long totalSize = 7 + commandFloats; // header (7 values) + commands

    jfloatArray result = env->NewFloatArray(static_cast<jsize>(totalSize));
    if (!result) {
        return nullptr;
    }

    unsigned long offset = 0;

    // For small paths (< 256 floats), use critical section for speed
    // For large paths, use regular path to avoid blocking GC too long
    if (totalSize < 256) {
        // Fast path: Use GetPrimitiveArrayCritical for zero-copy direct memory access
        float *data = static_cast<float *>(env->GetPrimitiveArrayCritical(result, nullptr));
        if (!data) {
            env->DeleteLocalRef(result);
            return nullptr;
        }

        // Header: [numCommands, advanceWidth, unitsPerEm, minX, minY, maxX, maxY]
        data[offset++] = static_cast<float>(glyphPath.commands.size);
        data[offset++] = glyphPath.advanceWidth;
        data[offset++] = static_cast<float>(glyphPath.unitsPerEm);
        data[offset++] = glyphPath.minX;
        data[offset++] = glyphPath.minY;
        data[offset++] = glyphPath.maxX;
        data[offset++] = glyphPath.maxY;

        // Commands: [type, padding(3 floats), union data(6 floats)]
        // We simply copy all 6 floats from the union regardless of type
        for (unsigned long i = 0; i < glyphPath.commands.size; i++) {
            const auto &cmd = glyphPath.commands.data[i];
            data[offset++] = static_cast<float>(cmd.type);
            data[offset++] = 0.0f;  // padding
            data[offset++] = 0.0f;  // padding
            data[offset++] = 0.0f;  // padding

            // Copy all 6 floats from union (simpler than type switching)
            // The union is always 24 bytes = 6 floats, so we can safely access cubic
            data[offset++] = cmd.cubic.cx1;  // Also covers: point.x, quadratic.cx
            data[offset++] = cmd.cubic.cy1;  // Also covers: point.y, quadratic.cy
            data[offset++] = cmd.cubic.cx2;  // Also covers: quadratic.x
            data[offset++] = cmd.cubic.cy2;  // Also covers: quadratic.y
            data[offset++] = cmd.cubic.x;
            data[offset++] = cmd.cubic.y;
        }

        // Release the critical section (mode 0 = copy back and free)
        env->ReleasePrimitiveArrayCritical(result, data, 0);
    } else {
        // Slow path: Use temporary buffer for large paths to avoid blocking GC
        float *data = (float *) malloc(totalSize * sizeof(float));
        if (!data) {
            env->DeleteLocalRef(result);
            return nullptr;
        }

        // Header
        data[offset++] = static_cast<float>(glyphPath.commands.size);
        data[offset++] = glyphPath.advanceWidth;
        data[offset++] = static_cast<float>(glyphPath.unitsPerEm);
        data[offset++] = glyphPath.minX;
        data[offset++] = glyphPath.minY;
        data[offset++] = glyphPath.maxX;
        data[offset++] = glyphPath.maxY;

        // Commands - same as fast path
        for (unsigned long i = 0; i < glyphPath.commands.size; i++) {
            const auto &cmd = glyphPath.commands.data[i];
            data[offset++] = static_cast<float>(cmd.type);
            data[offset++] = 0.0f;
            data[offset++] = 0.0f;
            data[offset++] = 0.0f;

            data[offset++] = cmd.cubic.cx1;
            data[offset++] = cmd.cubic.cy1;
            data[offset++] = cmd.cubic.cx2;
            data[offset++] = cmd.cubic.cy2;
            data[offset++] = cmd.cubic.x;
            data[offset++] = cmd.cubic.y;
        }

        env->SetFloatArrayRegion(result, 0, static_cast<jsize>(totalSize), data);
        free(data);
    }

    return result;
}

extern "C" {

JNIEXPORT jlong JNICALL
Java_com_davidmedenjak_compose_glyphs_FontPathExtractor_nativeCreateFontHandle(
        JNIEnv *env,
        jobject /* this */,
        jbyteArray fontData) {

    if (!fontData) {
        return 0;
    }

    jsize fontDataSize = env->GetArrayLength(fontData);
    if (fontDataSize <= 0) {
        return 0;
    }

    // Allocate native memory for font data
    void* nativeFontData = malloc(fontDataSize);
    if (!nativeFontData) {
        return 0;
    }

    // Copy font data to native memory
    jbyte* fontBytes = env->GetByteArrayElements(fontData, nullptr);
    if (!fontBytes) {
        free(nativeFontData);
        return 0;
    }

    memcpy(nativeFontData, fontBytes, fontDataSize);
    env->ReleaseByteArrayElements(fontData, fontBytes, JNI_ABORT);

    // Create font handle
    NativeFontHandle* handle = new NativeFontHandle();
    handle->fontData = nativeFontData;
    handle->fontDataSize = static_cast<unsigned long>(fontDataSize);
    handle->sharedFont = new fontsubsetting::SharedFontData();
    handle->sharedFont->initialize(handle->fontData, handle->fontDataSize);

    return reinterpret_cast<jlong>(handle);
}

JNIEXPORT void JNICALL
Java_com_davidmedenjak_compose_glyphs_FontPathExtractor_nativeDestroyFontHandle(
        JNIEnv* /* env */,
        jobject /* this */,
        jlong fontPtr) {

    if (fontPtr == 0) {
        return;
    }

    NativeFontHandle* handle = reinterpret_cast<NativeFontHandle*>(fontPtr);
    if (handle->fontData) {
        free(handle->fontData);
        handle->fontData = nullptr;
    }
    if (handle->sharedFont) {
        delete handle->sharedFont;
        handle->sharedFont = nullptr;
    }
    delete handle;
}

JNIEXPORT jfloatArray JNICALL
Java_com_davidmedenjak_compose_glyphs_FontPathExtractor_nativeExtractPath0(
        JNIEnv *env,
        jobject /* this */,
        jlong fontPtr,
        jint codepoint) {

    if (fontPtr == 0) {
        return nullptr;
    }

    NativeFontHandle *handle = reinterpret_cast<NativeFontHandle *>(fontPtr);

    // Direct extraction without creating temporary GlyphHandle
    auto glyphPath = handle->sharedFont->extractPathDirect(
            static_cast<unsigned int>(codepoint),
            nullptr,
            0
    );

    if (glyphPath.isEmpty()) {
        return nullptr;
    }

    return packGlyphPathToArray(env, glyphPath);
}

JNIEXPORT jfloatArray JNICALL
Java_com_davidmedenjak_compose_glyphs_FontPathExtractor_nativeExtractPath1(
        JNIEnv *env,
        jobject /* this */,
        jlong fontPtr,
        jint codepoint,
        jint tag1,
        jfloat value1) {

    if (fontPtr == 0) {
        return nullptr;
    }

    NativeFontHandle *handle = reinterpret_cast<NativeFontHandle *>(fontPtr);

    // Single axis variation - stack allocated
    fontsubsetting::Variation variations[1];
    intToTag(tag1, variations[0].tag);
    variations[0].value = value1;

    // Direct extraction using shared resources
    auto glyphPath = handle->sharedFont->extractPathDirect(
            static_cast<unsigned int>(codepoint),
            variations,
            1
    );

    if (glyphPath.isEmpty()) {
        return nullptr;
    }

    return packGlyphPathToArray(env, glyphPath);
}

JNIEXPORT jfloatArray JNICALL
Java_com_davidmedenjak_compose_glyphs_FontPathExtractor_nativeExtractPath2(
        JNIEnv *env,
        jobject /* this */,
        jlong fontPtr,
        jint codepoint,
        jint tag1,
        jfloat value1,
        jint tag2,
        jfloat value2) {

    if (fontPtr == 0) {
        return nullptr;
    }

    NativeFontHandle *handle = reinterpret_cast<NativeFontHandle *>(fontPtr);

    // Two axis variations - stack allocated
    fontsubsetting::Variation variations[2];
    intToTag(tag1, variations[0].tag);
    variations[0].value = value1;
    intToTag(tag2, variations[1].tag);
    variations[1].value = value2;

    // Direct extraction using shared resources
    auto glyphPath = handle->sharedFont->extractPathDirect(
            static_cast<unsigned int>(codepoint),
            variations,
            2
    );

    if (glyphPath.isEmpty()) {
        return nullptr;
    }

    return packGlyphPathToArray(env, glyphPath);
}

JNIEXPORT jfloatArray JNICALL
Java_com_davidmedenjak_compose_glyphs_FontPathExtractor_nativeExtractPath3(
        JNIEnv *env,
        jobject /* this */,
        jlong fontPtr,
        jint codepoint,
        jint tag1,
        jfloat value1,
        jint tag2,
        jfloat value2,
        jint tag3,
        jfloat value3) {

    if (fontPtr == 0) {
        return nullptr;
    }

    NativeFontHandle *handle = reinterpret_cast<NativeFontHandle *>(fontPtr);

    // Three axis variations - stack allocated
    fontsubsetting::Variation variations[3];
    intToTag(tag1, variations[0].tag);
    variations[0].value = value1;
    intToTag(tag2, variations[1].tag);
    variations[1].value = value2;
    intToTag(tag3, variations[2].tag);
    variations[2].value = value3;

    // Direct extraction using shared resources
    auto glyphPath = handle->sharedFont->extractPathDirect(
            static_cast<unsigned int>(codepoint),
            variations,
            3
    );

    if (glyphPath.isEmpty()) {
        return nullptr;
    }

    return packGlyphPathToArray(env, glyphPath);
}

JNIEXPORT jfloatArray JNICALL
Java_com_davidmedenjak_compose_glyphs_FontPathExtractor_nativeExtractPathN(
        JNIEnv *env,
        jobject /* this */,
        jlong fontPtr,
        jint codepoint,
        jintArray variationTags,
        jfloatArray variationValues) {

    if (fontPtr == 0) {
        return nullptr;
    }

    NativeFontHandle *handle = reinterpret_cast<NativeFontHandle *>(fontPtr);

    // Parse variations - stack allocate, max 4 variations (sufficient for Material Symbols)
    fontsubsetting::Variation variations[4];
    unsigned long variationCount = 0;

    if (variationTags && variationValues) {
        jsize tagCount = env->GetArrayLength(variationTags);
        jsize valueCount = env->GetArrayLength(variationValues);

        if (tagCount == valueCount && tagCount > 0) {
            jint *tags = env->GetIntArrayElements(variationTags, nullptr);
            jfloat *values = env->GetFloatArrayElements(variationValues, nullptr);
            variationCount = tagCount > 4 ? 4 : tagCount;

            for (unsigned long i = 0; i < variationCount; i++) {
                intToTag(tags[i], variations[i].tag);
                variations[i].value = values[i];
            }

            env->ReleaseIntArrayElements(variationTags, tags, JNI_ABORT);
            env->ReleaseFloatArrayElements(variationValues, values, JNI_ABORT);
        }
    }

    // Direct extraction using shared resources
    auto glyphPath = handle->sharedFont->extractPathDirect(
            static_cast<unsigned int>(codepoint),
            variations,
            variationCount
    );

    if (glyphPath.isEmpty()) {
        return nullptr;
    }

    return packGlyphPathToArray(env, glyphPath);
}

} // extern "C"
