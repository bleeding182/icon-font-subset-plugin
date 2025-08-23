#include "logging.h"

// Global references for logging callback
JavaVM* g_jvm = nullptr;
jobject g_logger = nullptr;
jmethodID g_logMethod = nullptr;

void init_logging(JavaVM* jvm, jobject logger, jmethodID logMethod) {
    g_jvm = jvm;
    g_logger = logger;
    g_logMethod = logMethod;
}

void cleanup_logging(JavaVM* jvm) {
    if (g_logger != nullptr && jvm != nullptr) {
        JNIEnv* env;
        if (jvm->GetEnv((void**)&env, JNI_VERSION_1_6) == JNI_OK) {
            env->DeleteGlobalRef(g_logger);
            g_logger = nullptr;
        }
    }
    g_logMethod = nullptr;
}

void log_message(LogLevel level, const std::string& message) {
    if (g_jvm == nullptr || g_logger == nullptr || g_logMethod == nullptr) {
        return; // Logging not configured
    }
    
    JNIEnv* env = nullptr;
    bool attached = false;
    
    int getEnvResult = g_jvm->GetEnv((void**)&env, JNI_VERSION_1_6);
    if (getEnvResult == JNI_EDETACHED) {
        if (g_jvm->AttachCurrentThread((void**)&env, nullptr) == JNI_OK) {
            attached = true;
        } else {
            return; // Failed to attach
        }
    } else if (getEnvResult != JNI_OK) {
        return; // Failed to get environment
    }
    
    jstring jmessage = env->NewStringUTF(message.c_str());
    env->CallVoidMethod(g_logger, g_logMethod, level, jmessage);
    env->DeleteLocalRef(jmessage);
    
    if (attached) {
        g_jvm->DetachCurrentThread();
    }
}

void log_debug(const std::string& msg) { log_message(LOG_DEBUG, msg); }
void log_info(const std::string& msg) { log_message(LOG_INFO, msg); }
void log_warn(const std::string& msg) { log_message(LOG_WARN, msg); }
void log_error(const std::string& msg) { log_message(LOG_ERROR, msg); }