#include "jni_utils.h"
#include <sstream>
#include <iomanip>
#include <locale>

std::string format_file_size(size_t bytes) {
    const char* units[] = {"B", "KB", "MB", "GB"};
    int unit_index = 0;
    double size = static_cast<double>(bytes);
    
    while (size >= 1024 && unit_index < 3) {
        size /= 1024;
        unit_index++;
    }
    
    std::stringstream ss;
    ss.imbue(std::locale::classic());  // Use C locale for consistent formatting
    ss << std::fixed << std::setprecision(2) << size << " " << units[unit_index];
    return ss.str();
}

std::string jstring_to_string(JNIEnv* env, jstring jstr) {
    if (!jstr) return "";
    
    const char* chars = env->GetStringUTFChars(jstr, nullptr);
    std::string result(chars);
    env->ReleaseStringUTFChars(jstr, chars);
    return result;
}

std::vector<std::string> jarray_to_vector(JNIEnv* env, jobjectArray array) {
    std::vector<std::string> result;
    if (!array) return result;
    
    jsize length = env->GetArrayLength(array);
    result.reserve(length);
    
    for (jsize i = 0; i < length; i++) {
        jstring jstr = (jstring)env->GetObjectArrayElement(array, i);
        result.push_back(jstring_to_string(env, jstr));
        env->DeleteLocalRef(jstr);
    }
    
    return result;
}