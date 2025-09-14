#include <jni.h>
#include <cstring>
#include <sstream>
#include <cstdlib>
#include <climits>

#include "jni_exports.h"
#include "logging.h"
#include "jni_utils.h"
#include "font_io.h"
#include "font_subsetter.h"
#include "harfbuzz_wrappers.h"
#include <hb-ot.h>

// ==============================================================================
// JNI Initialization and Cleanup
// ==============================================================================

JNI_EXPORT JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM* vm, void* reserved) {
    g_jvm = vm;
    return JNI_VERSION_1_6;
}

JNI_EXPORT JNIEXPORT void JNICALL JNI_OnUnload(JavaVM* vm, void* reserved) {
    cleanup_logging(vm);
    g_jvm = nullptr;
}

// ==============================================================================
// JNI Functions
// ==============================================================================

JNI_EXPORT JNIEXPORT void JNICALL
Java_com_davidmedenjak_fontsubsetting_native_HarfBuzzSubsetter_nativeSetLogger(
    JNIEnv* env,
    jobject /* this */,
    jobject logger) {
    
    if (g_logger != nullptr) {
        env->DeleteGlobalRef(g_logger);
        g_logger = nullptr;
        g_logMethod = nullptr;
    }
    
    if (logger != nullptr) {
        g_logger = env->NewGlobalRef(logger);
        jclass loggerClass = env->GetObjectClass(logger);
        g_logMethod = env->GetMethodID(loggerClass, "log", "(ILjava/lang/String;)V");
        env->DeleteLocalRef(loggerClass);
        init_logging(g_jvm, g_logger, g_logMethod);
    }
}

JNI_EXPORT JNIEXPORT jboolean JNICALL
Java_com_davidmedenjak_fontsubsetting_native_HarfBuzzSubsetter_nativeSubsetFont(
    JNIEnv* env,
    jobject /* this */,
    jstring inputPath,
    jstring outputPath,
    jobjectArray glyphs) {
    
    std::string input_path = jstring_to_string(env, inputPath);
    std::string output_path = jstring_to_string(env, outputPath);
    std::vector<std::string> glyph_list = jarray_to_vector(env, glyphs);
    
    log_info("Starting font subsetting: " + input_path + " -> " + output_path);
    
    // Read input font
    FontData font_data = read_font_file(input_path);
    if (!font_data.valid) {
        return JNI_FALSE;
    }
    
    // Convert glyphs to codepoints
    std::vector<unsigned int> codepoints;
    codepoints.reserve(glyph_list.size());
    
    for (const auto& glyph : glyph_list) {
        // Parse codepoint without exceptions
        char* end_ptr = nullptr;
        unsigned long codepoint = std::strtoul(glyph.c_str(), &end_ptr, 10);
        
        // Check if conversion was successful
        if (end_ptr != glyph.c_str() && *end_ptr == '\0' && codepoint <= UINT_MAX) {
            codepoints.push_back(static_cast<unsigned int>(codepoint));
        } else {
            log_warn("Failed to parse codepoint: " + glyph);
        }
    }
    
    if (codepoints.empty()) {
        log_error("No valid codepoints to subset");
        return JNI_FALSE;
    }
    
    // Perform subsetting with default flags (strip hinting and glyph names)
    hb_face_t* subset_face = perform_subsetting(font_data, codepoints, {}, true, true);
    if (!subset_face) {
        return JNI_FALSE;
    }
    
    // Get subset data
    HBBlob subset_blob(hb_face_reference_blob(subset_face));
    unsigned int subset_length;
    const char* subset_data = hb_blob_get_data(subset_blob, &subset_length);
    
    // Write output
    bool success = write_font_file(output_path, subset_data, subset_length);
    
    // Clean up
    hb_face_destroy(subset_face);
    
    if (success) {
        log_info("Successfully subsetted font: " + format_file_size(font_data.size) + 
                " -> " + format_file_size(subset_length));
    }
    
    return success ? JNI_TRUE : JNI_FALSE;
}

JNI_EXPORT JNIEXPORT jboolean JNICALL
Java_com_davidmedenjak_fontsubsetting_native_HarfBuzzSubsetter_nativeSubsetFontWithAxes(
    JNIEnv* env,
    jobject /* this */,
    jstring inputPath,
    jstring outputPath,
    jobjectArray glyphs,
    jobjectArray axisTags,
    jfloatArray axisMinValues,
    jfloatArray axisMaxValues,
    jfloatArray axisDefaultValues,
    jbooleanArray axisRemove) {
    
    std::string input_path = jstring_to_string(env, inputPath);
    std::string output_path = jstring_to_string(env, outputPath);
    std::vector<std::string> glyph_list = jarray_to_vector(env, glyphs);
    
    log_info("Starting font subsetting with axes: " + input_path + " -> " + output_path);
    
    // Parse axis configurations
    std::vector<AxisConfig> axis_configs;
    if (axisTags != nullptr) {
        std::vector<std::string> axis_tags = jarray_to_vector(env, axisTags);
        jsize axis_count = axis_tags.size();
        
        axis_configs.reserve(axis_count);
        
        jfloat* mins = axisMinValues ? env->GetFloatArrayElements(axisMinValues, nullptr) : nullptr;
        jfloat* maxs = axisMaxValues ? env->GetFloatArrayElements(axisMaxValues, nullptr) : nullptr;
        jfloat* defaults = axisDefaultValues ? env->GetFloatArrayElements(axisDefaultValues, nullptr) : nullptr;
        jboolean* removes = axisRemove ? env->GetBooleanArrayElements(axisRemove, nullptr) : nullptr;
        
        for (jsize i = 0; i < axis_count; i++) {
            AxisConfig config;
            config.tag = axis_tags[i];
            config.min_value = mins ? mins[i] : 0.0f;
            config.max_value = maxs ? maxs[i] : 0.0f;
            config.default_value = defaults ? defaults[i] : 0.0f;
            config.remove = removes ? removes[i] : false;
            axis_configs.push_back(config);
        }
        
        if (mins) env->ReleaseFloatArrayElements(axisMinValues, mins, JNI_ABORT);
        if (maxs) env->ReleaseFloatArrayElements(axisMaxValues, maxs, JNI_ABORT);
        if (defaults) env->ReleaseFloatArrayElements(axisDefaultValues, defaults, JNI_ABORT);
        if (removes) env->ReleaseBooleanArrayElements(axisRemove, removes, JNI_ABORT);
    }
    
    // Read input font
    FontData font_data = read_font_file(input_path);
    if (!font_data.valid) {
        return JNI_FALSE;
    }
    
    // Convert glyphs to codepoints
    std::vector<unsigned int> codepoints;
    codepoints.reserve(glyph_list.size());
    
    for (const auto& glyph : glyph_list) {
        // Parse codepoint without exceptions
        char* end_ptr = nullptr;
        unsigned long codepoint = std::strtoul(glyph.c_str(), &end_ptr, 10);
        
        // Check if conversion was successful
        if (end_ptr != glyph.c_str() && *end_ptr == '\0' && codepoint <= UINT_MAX) {
            codepoints.push_back(static_cast<unsigned int>(codepoint));
        } else {
            log_warn("Failed to parse codepoint: " + glyph);
        }
    }
    
    if (codepoints.empty()) {
        log_error("No valid codepoints to subset");
        return JNI_FALSE;
    }
    
    // Perform subsetting with axes and default flags (strip hinting and glyph names)
    hb_face_t* subset_face = perform_subsetting(font_data, codepoints, axis_configs, true, true);
    if (!subset_face) {
        return JNI_FALSE;
    }
    
    // Get subset data
    HBBlob subset_blob(hb_face_reference_blob(subset_face));
    unsigned int subset_length;
    const char* subset_data = hb_blob_get_data(subset_blob, &subset_length);
    
    // Write output
    bool success = write_font_file(output_path, subset_data, subset_length);
    
    // Clean up
    hb_face_destroy(subset_face);
    
    if (success) {
        log_info("Successfully subsetted font with axes: " + format_file_size(font_data.size) + 
                " -> " + format_file_size(subset_length));
    }
    
    return success ? JNI_TRUE : JNI_FALSE;
}

JNI_EXPORT JNIEXPORT jboolean JNICALL
Java_com_davidmedenjak_fontsubsetting_native_HarfBuzzSubsetter_nativeSubsetFontWithAxesAndFlags(
    JNIEnv* env,
    jobject /* this */,
    jstring inputPath,
    jstring outputPath,
    jobjectArray glyphs,
    jobjectArray axisTags,
    jfloatArray axisMinValues,
    jfloatArray axisMaxValues,
    jfloatArray axisDefaultValues,
    jbooleanArray axisRemove,
    jboolean stripHinting,
    jboolean stripGlyphNames) {

    std::string input_path = jstring_to_string(env, inputPath);
    std::string output_path = jstring_to_string(env, outputPath);
    std::vector<std::string> glyph_list = jarray_to_vector(env, glyphs);

    log_info("Starting font subsetting with axes and flags: " + input_path + " -> " + output_path);

    // Parse axis configurations
    std::vector<AxisConfig> axis_configs;
    if (axisTags != nullptr) {
        std::vector<std::string> axis_tags = jarray_to_vector(env, axisTags);
        jsize axis_count = axis_tags.size();

        axis_configs.reserve(axis_count);

        jfloat* mins = axisMinValues ? env->GetFloatArrayElements(axisMinValues, nullptr) : nullptr;
        jfloat* maxs = axisMaxValues ? env->GetFloatArrayElements(axisMaxValues, nullptr) : nullptr;
        jfloat* defaults = axisDefaultValues ? env->GetFloatArrayElements(axisDefaultValues, nullptr) : nullptr;
        jboolean* removes = axisRemove ? env->GetBooleanArrayElements(axisRemove, nullptr) : nullptr;

        for (jsize i = 0; i < axis_count; i++) {
            AxisConfig config;
            config.tag = axis_tags[i];
            config.min_value = mins ? mins[i] : 0.0f;
            config.max_value = maxs ? maxs[i] : 0.0f;
            config.default_value = defaults ? defaults[i] : 0.0f;
            config.remove = removes ? removes[i] : false;
            axis_configs.push_back(config);
        }

        if (mins) env->ReleaseFloatArrayElements(axisMinValues, mins, JNI_ABORT);
        if (maxs) env->ReleaseFloatArrayElements(axisMaxValues, maxs, JNI_ABORT);
        if (defaults) env->ReleaseFloatArrayElements(axisDefaultValues, defaults, JNI_ABORT);
        if (removes) env->ReleaseBooleanArrayElements(axisRemove, removes, JNI_ABORT);
    }

    // Read input font
    FontData font_data = read_font_file(input_path);
    if (!font_data.valid) {
        return JNI_FALSE;
    }

    // Convert glyphs to codepoints
    std::vector<unsigned int> codepoints;
    codepoints.reserve(glyph_list.size());

    for (const auto& glyph : glyph_list) {
        // Parse codepoint without exceptions
        char* end_ptr = nullptr;
        unsigned long codepoint = std::strtoul(glyph.c_str(), &end_ptr, 10);

        // Check if conversion was successful
        if (end_ptr != glyph.c_str() && *end_ptr == '\0' && codepoint <= UINT_MAX) {
            codepoints.push_back(static_cast<unsigned int>(codepoint));
        } else {
            log_warn("Failed to parse codepoint: " + glyph);
        }
    }

    if (codepoints.empty()) {
        log_error("No valid codepoints to subset");
        return JNI_FALSE;
    }

    // Perform subsetting with axes and custom flags
    hb_face_t* subset_face = perform_subsetting(
        font_data, codepoints, axis_configs,
        stripHinting == JNI_TRUE,
        stripGlyphNames == JNI_TRUE
    );
    if (!subset_face) {
        return JNI_FALSE;
    }

    // Get subset data
    HBBlob subset_blob(hb_face_reference_blob(subset_face));
    unsigned int subset_length;
    const char* subset_data = hb_blob_get_data(subset_blob, &subset_length);

    // Write output
    bool success = write_font_file(output_path, subset_data, subset_length);

    // Clean up
    hb_face_destroy(subset_face);

    if (success) {
        log_info("Successfully subsetted font with axes and flags: " + format_file_size(font_data.size) +
                " -> " + format_file_size(subset_length));
    }

    return success ? JNI_TRUE : JNI_FALSE;
}

JNI_EXPORT JNIEXPORT jboolean JNICALL
Java_com_davidmedenjak_fontsubsetting_native_HarfBuzzSubsetter_nativeValidateFont(
    JNIEnv* env,
    jobject /* this */,
    jstring fontPath) {
    
    std::string font_path = jstring_to_string(env, fontPath);
    
    FILE* font_file = fopen(font_path.c_str(), "rb");
    if (!font_file) {
        log_debug("Font validation failed - cannot open file: " + font_path);
        return JNI_FALSE;
    }
    
    // Read first few bytes to check font signature
    unsigned char header[4];
    size_t read = fread(header, 1, 4, font_file);
    fclose(font_file);
    
    if (read != 4) {
        log_debug("Font validation failed - cannot read header: " + font_path);
        return JNI_FALSE;
    }
    
    // Check for common font signatures
    // OTF: 0x00010000 or "OTTO"
    // TTF: 0x00010000 or "true"
    // WOFF: "wOFF"
    // WOFF2: "wOF2"
    bool valid = (header[0] == 0x00 && header[1] == 0x01 && header[2] == 0x00 && header[3] == 0x00) ||
                 (memcmp(header, "OTTO", 4) == 0) ||
                 (memcmp(header, "true", 4) == 0) ||
                 (memcmp(header, "wOFF", 4) == 0) ||
                 (memcmp(header, "wOF2", 4) == 0);
    
    if (valid) {
        log_debug("Font validation successful: " + font_path);
    } else {
        log_debug("Font validation failed - invalid signature: " + font_path);
    }
    
    return valid ? JNI_TRUE : JNI_FALSE;
}

JNI_EXPORT JNIEXPORT jstring JNICALL
Java_com_davidmedenjak_fontsubsetting_native_HarfBuzzSubsetter_nativeGetFontInfo(
    JNIEnv* env,
    jobject /* this */,
    jstring fontPath) {
    
    std::string font_path = jstring_to_string(env, fontPath);
    
    // Read font file
    FontData font_data = read_font_file(font_path);
    if (!font_data.valid) {
        return nullptr;
    }
    
    // Create HarfBuzz face
    HBBlob blob(hb_blob_create(
        font_data.data.data(),
        font_data.size,
        HB_MEMORY_MODE_READONLY,
        nullptr,
        nullptr
    ));
    
    HBFace face(hb_face_create(blob, 0));
    if (!face.valid()) {
        log_error("Failed to create face for font info");
        return nullptr;
    }
    
    // Get font information
    unsigned int glyph_count = hb_face_get_glyph_count(face);
    unsigned int upem = hb_face_get_upem(face);
    
    // Create properties format output
    std::stringstream info;
    info << "glyphCount=" << glyph_count << "\n";
    info << "unitsPerEm=" << upem << "\n";
    info << "fileSize=" << font_data.size << "\n";
    
    // Get variable font axis information
    unsigned int axis_count = hb_ot_var_get_axis_count(face);
    if (axis_count > 0) {
        std::vector<hb_ot_var_axis_info_t> axes_info(axis_count);
        unsigned int axes_returned = axis_count;
        hb_ot_var_get_axis_infos(face, 0, &axes_returned, axes_info.data());
        
        for (unsigned int i = 0; i < axes_returned; i++) {
            hb_ot_var_axis_info_t& axis_info = axes_info[i];
            
            char tag_str[5] = {0};
            tag_str[0] = (char)((axis_info.tag >> 24) & 0xFF);
            tag_str[1] = (char)((axis_info.tag >> 16) & 0xFF);
            tag_str[2] = (char)((axis_info.tag >> 8) & 0xFF);
            tag_str[3] = (char)(axis_info.tag & 0xFF);
            
            // Output format: axis.N=<tag>,<min>,<default>,<max>
            info << "axis." << i << "=" << tag_str << ","
                 << axis_info.min_value << ","
                 << axis_info.default_value << ","
                 << axis_info.max_value << "\n";
        }
    }
    
    return env->NewStringUTF(info.str().c_str());
}