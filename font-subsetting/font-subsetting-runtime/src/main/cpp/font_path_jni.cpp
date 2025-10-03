#include <jni.h>
#include <android/log.h>
#include "font_path_extractor.h"

#define LOG_TAG "FontPathJNI"
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

extern "C" {

JNIEXPORT jfloatArray
JNICALL
Java_com_davidmedenjak_fontsubsetting_runtime_FontPathExtractor_nativeExtractGlyphPath(
        JNIEnv *env,
        jobject /* this */,
        jbyteArray fontData,
        jint codepoint) {

    if (!fontData) {
        LOGE("Font data is null");
        return nullptr;
    }

    // Get font data bytes
    jsize fontDataSize = env->GetArrayLength(fontData);
    jbyte *fontBytes = env->GetByteArrayElements(fontData, nullptr);

    if (!fontBytes) {
        LOGE("Failed to get font data bytes");
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

    // Calculate result array size
    // Format: [numCommands, advanceWidth, advanceHeight, unitsPerEm, minX, minY, maxX, maxY, commands...]
    // Each command: [type, x1, y1, x2, y2, x3, y3] (some values unused based on type)
    size_t commandFloats = glyphPath.commands.size() * 7; // type + 6 floats per command
    size_t totalSize = 8 + commandFloats; // header (8 values) + commands

    jfloatArray result = env->NewFloatArray(static_cast<jsize>(totalSize));
    if (!result) {
        LOGE("Failed to allocate result array");
        return nullptr;
    }

    // Fill result array
    std::vector<float> data;
    data.reserve(totalSize);

    // Header
    data.push_back(static_cast<float>(glyphPath.commands.size()));
    data.push_back(glyphPath.advanceWidth);
    data.push_back(glyphPath.advanceHeight);
    data.push_back(static_cast<float>(glyphPath.unitsPerEm));
    data.push_back(glyphPath.minX);
    data.push_back(glyphPath.minY);
    data.push_back(glyphPath.maxX);
    data.push_back(glyphPath.maxY);

    // Commands
    for (const auto &cmd: glyphPath.commands) {
        data.push_back(static_cast<float>(cmd.type));
        data.push_back(cmd.x1);
        data.push_back(cmd.y1);
        data.push_back(cmd.x2);
        data.push_back(cmd.y2);
        data.push_back(cmd.x3);
        data.push_back(cmd.y3);
    }

    env->SetFloatArrayRegion(result, 0, static_cast<jsize>(data.size()), data.data());

    return result;
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
        LOGE("Font data is null");
        return nullptr;
    }

    // Get font data bytes
    jsize fontDataSize = env->GetArrayLength(fontData);
    jbyte *fontBytes = env->GetByteArrayElements(fontData, nullptr);

    if (!fontBytes) {
        LOGE("Failed to get font data bytes");
        return nullptr;
    }

    // Parse variations
    std::map<std::string, float> variations;
    if (variationTags && variationValues) {
        jsize tagCount = env->GetArrayLength(variationTags);
        jsize valueCount = env->GetArrayLength(variationValues);

        if (tagCount == valueCount) {
            jfloat *values = env->GetFloatArrayElements(variationValues, nullptr);

            for (jsize i = 0; i < tagCount; i++) {
                auto tagObj = (jstring) env->GetObjectArrayElement(variationTags, i);
                const char *tagChars = env->GetStringUTFChars(tagObj, nullptr);
                std::string tag(tagChars);
                float value = values[i];

                variations[tag] = value;

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
            variations
    );

    // Release font data
    env->ReleaseByteArrayElements(fontData, fontBytes, JNI_ABORT);

    if (glyphPath.isEmpty()) {
        return nullptr;
    }

    // Calculate result array size
    size_t commandFloats = glyphPath.commands.size() * 7;
    size_t totalSize = 8 + commandFloats;

    jfloatArray result = env->NewFloatArray(static_cast<jsize>(totalSize));
    if (!result) {
        LOGE("Failed to allocate result array");
        return nullptr;
    }

    // Fill result array
    std::vector<float> data;
    data.reserve(totalSize);

    // Header
    data.push_back(static_cast<float>(glyphPath.commands.size()));
    data.push_back(glyphPath.advanceWidth);
    data.push_back(glyphPath.advanceHeight);
    data.push_back(static_cast<float>(glyphPath.unitsPerEm));
    data.push_back(glyphPath.minX);
    data.push_back(glyphPath.minY);
    data.push_back(glyphPath.maxX);
    data.push_back(glyphPath.maxY);

    // Commands
    for (const auto &cmd: glyphPath.commands) {
        data.push_back(static_cast<float>(cmd.type));
        data.push_back(cmd.x1);
        data.push_back(cmd.y1);
        data.push_back(cmd.x2);
        data.push_back(cmd.y2);
        data.push_back(cmd.x3);
        data.push_back(cmd.y3);
    }

    env->SetFloatArrayRegion(result, 0, static_cast<jsize>(data.size()), data.data());

    return result;
}

} // extern "C"
