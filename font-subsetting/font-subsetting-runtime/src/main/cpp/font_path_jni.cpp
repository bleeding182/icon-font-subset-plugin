#include <jni.h>

// Direct declarations to avoid including <cstdlib> and <cstring>
extern "C" {
void *malloc(size_t);
void free(void *);
void *memcpy(void *, const void *, size_t);
void *realloc(void *, size_t);
}

#include "font_path_extractor.h"

// Native font handle structure - stores font data in native memory
struct NativeFontHandle {
    void* fontData;
    size_t fontDataSize;
};

// Helper function to pack GlyphPath into a jfloatArray
// Returns null on allocation failure
static jfloatArray packGlyphPathToArray(JNIEnv *env, const fontsubsetting::GlyphPath &glyphPath) {
    // Calculate result array size
    // Format: [numCommands, advanceWidth, unitsPerEm, minX, minY, maxX, maxY, commands...]
    // Each command: [type, x1, y1, x2, y2, x3, y3] (some values unused based on type)
    size_t commandFloats = glyphPath.commands.size * 7; // type + 6 floats per command
    size_t totalSize = 7 + commandFloats; // header (7 values) + commands

    jfloatArray result = env->NewFloatArray(static_cast<jsize>(totalSize));
    if (!result) {
        return nullptr;
    }

    // Allocate temporary buffer for packing
    float *data = static_cast<float *>(malloc(totalSize * sizeof(float)));
    if (!data) {
        return nullptr;
    }

    size_t offset = 0;
    // Header: [numCommands, advanceWidth, unitsPerEm, minX, minY, maxX, maxY]
    data[offset++] = static_cast<float>(glyphPath.commands.size);
    data[offset++] = glyphPath.advanceWidth;
    data[offset++] = static_cast<float>(glyphPath.unitsPerEm);
    data[offset++] = glyphPath.minX;
    data[offset++] = glyphPath.minY;
    data[offset++] = glyphPath.maxX;
    data[offset++] = glyphPath.maxY;

    // Commands: [type, x1, y1, x2, y2, x3, y3]
    for (size_t i = 0; i < glyphPath.commands.size; i++) {
        const auto &cmd = glyphPath.commands.data[i];
        data[offset++] = static_cast<float>(cmd.type);
        data[offset++] = cmd.x1;
        data[offset++] = cmd.y1;
        data[offset++] = cmd.x2;
        data[offset++] = cmd.y2;
        data[offset++] = cmd.x3;
        data[offset++] = cmd.y3;
    }

    env->SetFloatArrayRegion(result, 0, static_cast<jsize>(totalSize), data);
    free(data);

    return result;
}

extern "C" {

JNIEXPORT jlong JNICALL
Java_com_davidmedenjak_fontsubsetting_runtime_FontPathExtractor_nativeCreateFontHandle(
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
    handle->fontDataSize = static_cast<size_t>(fontDataSize);

    return reinterpret_cast<jlong>(handle);
}

JNIEXPORT void JNICALL
Java_com_davidmedenjak_fontsubsetting_runtime_FontPathExtractor_nativeDestroyFontHandle(
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
    delete handle;
}

JNIEXPORT jfloatArray JNICALL
Java_com_davidmedenjak_fontsubsetting_runtime_FontPathExtractor_nativeExtractGlyphPath(
        JNIEnv *env,
        jobject /* this */,
        jlong fontPtr,
        jint codepoint) {

    if (fontPtr == 0) {
        return nullptr;
    }

    NativeFontHandle* handle = reinterpret_cast<NativeFontHandle*>(fontPtr);

    // Extract glyph path using stored font data
    auto glyphPath = fontsubsetting::extractGlyphPath(
            handle->fontData,
            handle->fontDataSize,
            static_cast<unsigned int>(codepoint)
    );

    if (glyphPath.isEmpty()) {
        return nullptr;
    }

    return packGlyphPathToArray(env, glyphPath);
}

JNIEXPORT jfloatArray JNICALL
Java_com_davidmedenjak_fontsubsetting_runtime_FontPathExtractor_nativeExtractGlyphPathWithVariations(
        JNIEnv *env,
        jobject /* this */,
        jlong fontPtr,
        jint codepoint,
        jobjectArray variationTags,
        jfloatArray variationValues) {

    if (fontPtr == 0) {
        return nullptr;
    }

    NativeFontHandle* handle = reinterpret_cast<NativeFontHandle*>(fontPtr);

    // Parse variations - stack allocate, max 16 variations
    fontsubsetting::Variation variations[16];
    size_t variationCount = 0;

    if (variationTags && variationValues) {
        jsize tagCount = env->GetArrayLength(variationTags);
        jsize valueCount = env->GetArrayLength(variationValues);

        if (tagCount == valueCount && tagCount > 0) {
            jfloat *values = env->GetFloatArrayElements(variationValues, nullptr);
            variationCount = tagCount > 16 ? 16 : tagCount;  // Limit to 16

            for (size_t i = 0; i < variationCount; i++) {
                auto tagObj = static_cast<jstring>(env->GetObjectArrayElement(variationTags, i));
                const char *tagChars = env->GetStringUTFChars(tagObj, nullptr);

                // Copy tag (max 4 chars)
                size_t len = 0;
                while (tagChars[len] && len < 4) {
                    variations[i].tag[len] = tagChars[len];
                    len++;
                }
                variations[i].tag[len] = '\0';  // Null terminate
                variations[i].value = values[i];

                env->ReleaseStringUTFChars(tagObj, tagChars);
                env->DeleteLocalRef(tagObj);
            }

            env->ReleaseFloatArrayElements(variationValues, values, JNI_ABORT);
        }
    }

    // Extract glyph path with variations using stored font data
    auto glyphPath = fontsubsetting::extractGlyphPathWithVariations(
            handle->fontData,
            handle->fontDataSize,
            static_cast<unsigned int>(codepoint),
            variations,
            variationCount
    );

    if (glyphPath.isEmpty()) {
        return nullptr;
    }

    return packGlyphPathToArray(env, glyphPath);
}

JNIEXPORT jlong JNICALL
Java_com_davidmedenjak_fontsubsetting_runtime_FontPathExtractor_nativeCreateGlyphHandle(
        JNIEnv* /* env */,
        jobject /* this */,
        jlong fontPtr,
        jint codepoint) {

    if (fontPtr == 0) {
        return 0;
    }

    NativeFontHandle* handle = reinterpret_cast<NativeFontHandle*>(fontPtr);

    auto* glyphHandle = new fontsubsetting::GlyphHandle();
    if (!glyphHandle->initialize(handle->fontData, handle->fontDataSize, 
                                   static_cast<unsigned int>(codepoint))) {
        delete glyphHandle;
        return 0;
    }

    return reinterpret_cast<jlong>(glyphHandle);
}

JNIEXPORT void JNICALL
Java_com_davidmedenjak_fontsubsetting_runtime_FontPathExtractor_nativeDestroyGlyphHandle(
        JNIEnv* /* env */,
        jobject /* this */,
        jlong glyphHandlePtr) {

    if (glyphHandlePtr == 0) {
        return;
    }

    auto* glyphHandle = reinterpret_cast<fontsubsetting::GlyphHandle*>(glyphHandlePtr);
    delete glyphHandle;
}

JNIEXPORT jfloatArray JNICALL
Java_com_davidmedenjak_fontsubsetting_runtime_FontPathExtractor_nativeExtractGlyphPathFromHandle(
        JNIEnv *env,
        jobject /* this */,
        jlong glyphHandlePtr,
        jobjectArray variationTags,
        jfloatArray variationValues) {

    if (glyphHandlePtr == 0) {
        return nullptr;
    }

    auto* glyphHandle = reinterpret_cast<fontsubsetting::GlyphHandle*>(glyphHandlePtr);

    fontsubsetting::Variation variations[16];
    size_t variationCount = 0;

    if (variationTags && variationValues) {
        jsize tagCount = env->GetArrayLength(variationTags);
        jsize valueCount = env->GetArrayLength(variationValues);

        if (tagCount == valueCount && tagCount > 0) {
            jfloat *values = env->GetFloatArrayElements(variationValues, nullptr);
            variationCount = tagCount > 16 ? 16 : tagCount;

            for (size_t i = 0; i < variationCount; i++) {
                auto tagObj = static_cast<jstring>(env->GetObjectArrayElement(variationTags, i));
                const char *tagChars = env->GetStringUTFChars(tagObj, nullptr);

                size_t len = 0;
                while (tagChars[len] && len < 4) {
                    variations[i].tag[len] = tagChars[len];
                    len++;
                }
                variations[i].tag[len] = '\0';
                variations[i].value = values[i];

                env->ReleaseStringUTFChars(tagObj, tagChars);
                env->DeleteLocalRef(tagObj);
            }

            env->ReleaseFloatArrayElements(variationValues, values, JNI_ABORT);
        }
    }

    auto glyphPath = glyphHandle->extractPath(variations, variationCount);

    if (glyphPath.isEmpty()) {
        return nullptr;
    }

    return packGlyphPathToArray(env, glyphPath);
}

} // extern "C"
