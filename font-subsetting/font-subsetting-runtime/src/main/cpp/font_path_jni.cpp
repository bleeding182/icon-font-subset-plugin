#include <jni.h>
#include <cstdlib>
#include "font_path_extractor.h"

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

JNIEXPORT jfloatArray
JNICALL
Java_com_davidmedenjak_fontsubsetting_runtime_FontPathExtractor_nativeExtractGlyphPath(
        JNIEnv *env,
        jobject /* this */,
        jbyteArray fontData,
        jint codepoint) {

    if (!fontData) {
        return nullptr;
    }

    // Get font data bytes
    jsize fontDataSize = env->GetArrayLength(fontData);
    jbyte *fontBytes = env->GetByteArrayElements(fontData, nullptr);

    if (!fontBytes) {
        return nullptr;
    }

    // Extract glyph path
    auto glyphPath = fontsubsetting::extractGlyphPath(
            fontBytes,
            static_cast<size_t>(fontDataSize),
            static_cast<unsigned int>(codepoint)
    );

    // Release font data
    env->ReleaseByteArrayElements(fontData, fontBytes, JNI_ABORT);

    if (glyphPath.isEmpty()) {
        return nullptr;
    }

    return packGlyphPathToArray(env, glyphPath);
}

JNIEXPORT jfloatArray
JNICALL
Java_com_davidmedenjak_fontsubsetting_runtime_FontPathExtractor_nativeExtractGlyphPathWithVariations(
        JNIEnv *env,
        jobject /* this */,
        jbyteArray fontData,
        jint codepoint,
        jobjectArray variationTags,
        jfloatArray variationValues) {

    if (!fontData) {
        return nullptr;
    }

    // Get font data bytes
    jsize fontDataSize = env->GetArrayLength(fontData);
    jbyte *fontBytes = env->GetByteArrayElements(fontData, nullptr);

    if (!fontBytes) {
        return nullptr;
    }

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

    // Extract glyph path with variations
    auto glyphPath = fontsubsetting::extractGlyphPathWithVariations(
            fontBytes,
            static_cast<size_t>(fontDataSize),
            static_cast<unsigned int>(codepoint),
            variations,
            variationCount
    );

    // Release font data
    env->ReleaseByteArrayElements(fontData, fontBytes, JNI_ABORT);

    if (glyphPath.isEmpty()) {
        return nullptr;
    }

    return packGlyphPathToArray(env, glyphPath);
}

} // extern "C"
