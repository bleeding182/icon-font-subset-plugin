#include <jni.h>
#include <stdlib.h>
#include "glyph_extractor.h"

#ifdef __ANDROID__
#include <android/log.h>
#define LOG_TAG "GlyphExtractor"
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)
#else
#include <stdio.h>
#define LOGE(...) do { fprintf(stderr, "[GlyphExtractor] " __VA_ARGS__); fputc('\n', stderr); } while (0)
#endif

/* JNI export visibility */
#if defined(_WIN32) || defined(__CYGWIN__)
#define JNI_EXPORT __declspec(dllexport)
#else
#define JNI_EXPORT __attribute__((visibility("default")))
#endif

static hb_tag_t tag_from_string(const char* s) {
    return HB_TAG(s[0], s[1], s[2], s[3]);
}

/* Stack buffer size for typical icon font axes (Material Symbols has 4) */
#define STACK_AXES 4

JNI_EXPORT jlong JNICALL
Java_com_davidmedenjak_fontsubsetting_runtime_HarfBuzzGlyphExtractor_nativeCreateFont(
    JNIEnv* env, jobject thiz, jbyteArray fontData
) {
    (void)thiz;
    jsize len = (*env)->GetArrayLength(env, fontData);
    jbyte* bytes = (*env)->GetByteArrayElements(env, fontData, NULL);
    if (!bytes) {
        LOGE("Failed to get font data bytes");
        return 0;
    }

    FontHandle* handle = font_create((const uint8_t*)bytes, (size_t)len);
    (*env)->ReleaseByteArrayElements(env, fontData, bytes, JNI_ABORT);

    if (!handle) {
        LOGE("Failed to create font handle");
        return 0;
    }

    return (jlong)(intptr_t)handle;
}

JNI_EXPORT void JNICALL
Java_com_davidmedenjak_fontsubsetting_runtime_HarfBuzzGlyphExtractor_nativeDestroyFont(
    JNIEnv* env, jobject thiz, jlong handlePtr
) {
    (void)env; (void)thiz;
    font_destroy((FontHandle*)(intptr_t)handlePtr);
}

JNI_EXPORT jfloatArray JNICALL
Java_com_davidmedenjak_fontsubsetting_runtime_HarfBuzzGlyphExtractor_nativeExtractGlyph(
    JNIEnv* env, jobject thiz, jlong handlePtr, jint codepoint,
    jobjectArray axisTags, jfloatArray axisValues
) {
    (void)thiz;
    FontHandle* handle = (FontHandle*)(intptr_t)handlePtr;
    if (!handle) return NULL;

    /* Parse variation axes with stack allocation for common case */
    jsize numAxes = axisTags ? (*env)->GetArrayLength(env, axisTags) : 0;
    hb_variation_t stack_vars[STACK_AXES];
    hb_variation_t* variations = numAxes <= STACK_AXES ? stack_vars :
        (hb_variation_t*)malloc((size_t)numAxes * sizeof(hb_variation_t));

    if (numAxes > 0) {
        jfloat* values = (*env)->GetFloatArrayElements(env, axisValues, NULL);
        jsize i;
        for (i = 0; i < numAxes; i++) {
            jstring tagStr = (jstring)(*env)->GetObjectArrayElement(env, axisTags, i);
            const char* tagChars = (*env)->GetStringUTFChars(env, tagStr, NULL);
            variations[i].tag = tag_from_string(tagChars);
            variations[i].value = values[i];
            (*env)->ReleaseStringUTFChars(env, tagStr, tagChars);
            (*env)->DeleteLocalRef(env, tagStr);
        }
        (*env)->ReleaseFloatArrayElements(env, axisValues, values, JNI_ABORT);
    }

    const float* pathData;
    size_t pathSize;
    int result = glyph_extract(
        handle,
        (uint32_t)codepoint,
        variations,
        (unsigned int)numAxes,
        &pathData,
        &pathSize
    );

    if (numAxes > STACK_AXES) free(variations);

    if (result != 0 || pathSize == 0) return NULL;

    jfloatArray arr = (*env)->NewFloatArray(env, (jsize)pathSize);
    (*env)->SetFloatArrayRegion(env, arr, 0, (jsize)pathSize, pathData);
    return arr;
}

JNI_EXPORT jfloatArray JNICALL
Java_com_davidmedenjak_fontsubsetting_runtime_HarfBuzzGlyphExtractor_nativeExtractGlyphBatch(
    JNIEnv* env, jobject thiz, jlong handlePtr, jint codepoint,
    jobjectArray axisTags, jfloatArray axisValues, jint numSets
) {
    (void)thiz;
    FontHandle* handle = (FontHandle*)(intptr_t)handlePtr;
    if (!handle) return NULL;

    jsize numAxes = axisTags ? (*env)->GetArrayLength(env, axisTags) : 0;

    /* Parse tags (same for all sets) — stack allocate */
    hb_tag_t stack_tags[STACK_AXES];
    hb_tag_t* tags = numAxes <= STACK_AXES ? stack_tags :
        (hb_tag_t*)malloc((size_t)numAxes * sizeof(hb_tag_t));

    jsize i;
    for (i = 0; i < numAxes; i++) {
        jstring tagStr = (jstring)(*env)->GetObjectArrayElement(env, axisTags, i);
        const char* tagChars = (*env)->GetStringUTFChars(env, tagStr, NULL);
        tags[i] = tag_from_string(tagChars);
        (*env)->ReleaseStringUTFChars(env, tagStr, tagChars);
        (*env)->DeleteLocalRef(env, tagStr);
    }

    /* Build flattened variations array: numAxes * numSets */
    size_t totalVars = (size_t)numAxes * (size_t)numSets;
    hb_variation_t* variations = (hb_variation_t*)malloc(totalVars * sizeof(hb_variation_t));

    jfloat* values = (*env)->GetFloatArrayElements(env, axisValues, NULL);
    jint s;
    for (s = 0; s < numSets; s++) {
        jsize a;
        for (a = 0; a < numAxes; a++) {
            size_t idx = (size_t)s * (size_t)numAxes + (size_t)a;
            variations[idx].tag = tags[a];
            variations[idx].value = values[idx];
        }
    }
    (*env)->ReleaseFloatArrayElements(env, axisValues, values, JNI_ABORT);

    if (numAxes > STACK_AXES) free(tags);

    const float* batchData;
    size_t batchSize;
    int result = glyph_extract_batch(
        handle,
        (uint32_t)codepoint,
        variations,
        (unsigned int)numAxes,
        (unsigned int)numSets,
        &batchData,
        &batchSize
    );

    free(variations);

    if (result != 0 || batchSize == 0) return NULL;

    jfloatArray arr = (*env)->NewFloatArray(env, (jsize)batchSize);
    (*env)->SetFloatArrayRegion(env, arr, 0, (jsize)batchSize, batchData);
    return arr;
}
