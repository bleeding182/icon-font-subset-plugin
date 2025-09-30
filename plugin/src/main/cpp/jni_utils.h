#ifndef FONTSUBSETTING_JNI_UTILS_H
#define FONTSUBSETTING_JNI_UTILS_H

#include <jni.h>
#include <string>
#include <vector>

// Convert jstring to std::string
std::string jstring_to_string(JNIEnv* env, jstring jstr);

// Convert jobjectArray to std::vector<std::string>
std::vector<std::string> jarray_to_vector(JNIEnv* env, jobjectArray array);

// String formatting utilities
std::string format_file_size(size_t bytes);

#endif // FONTSUBSETTING_JNI_UTILS_H