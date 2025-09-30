#include "logging.h"
#include <cstdio>

// Global references for logging callback
#ifdef _WIN32
CRITICAL_SECTION g_log_mutex;
// Static initializer for CRITICAL_SECTION
static struct LogMutexInit {
    LogMutexInit() { InitializeCriticalSection(&g_log_mutex); }
    ~LogMutexInit() { DeleteCriticalSection(&g_log_mutex); }
} g_log_mutex_init;
#else
pthread_mutex_t g_log_mutex = PTHREAD_MUTEX_INITIALIZER;
#endif
JavaVM* g_jvm = nullptr;
jobject g_logger = nullptr;
jmethodID g_logMethod = nullptr;

void init_logging(JavaVM* jvm, jobject logger, jmethodID logMethod) {
    g_jvm = jvm;
    g_logger = logger;
    g_logMethod = logMethod;
}

void cleanup_logging(JavaVM* jvm) {
    LogMutexGuard lock;
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
    LogMutexGuard lock;

    if (g_jvm == nullptr || g_logger == nullptr || g_logMethod == nullptr) {
        // Fallback to stderr when Java logger is unavailable
        fprintf(stderr, "[fontsubsetting] %s\n", message.c_str());
        return;
    }

    JNIEnv* env = nullptr;
    bool attached = false;

    int getEnvResult = g_jvm->GetEnv((void**)&env, JNI_VERSION_1_6);
    if (getEnvResult == JNI_EDETACHED) {
        if (g_jvm->AttachCurrentThread((void**)&env, nullptr) == JNI_OK) {
            attached = true;
        } else {
            fprintf(stderr, "[fontsubsetting] %s\n", message.c_str());
            return;
        }
    } else if (getEnvResult != JNI_OK) {
        fprintf(stderr, "[fontsubsetting] %s\n", message.c_str());
        return;
    }

    jstring jmessage = env->NewStringUTF(message.c_str());
    if (jmessage == nullptr) {
        // NewStringUTF failed (out of memory) — fallback to stderr
        fprintf(stderr, "[fontsubsetting] %s\n", message.c_str());
        if (attached) {
            g_jvm->DetachCurrentThread();
        }
        return;
    }
    env->CallVoidMethod(g_logger, g_logMethod, level, jmessage);
    if (env->ExceptionCheck()) {
        env->ExceptionClear();
    }
    env->DeleteLocalRef(jmessage);

    if (attached) {
        g_jvm->DetachCurrentThread();
    }
}

void log_debug(const std::string& msg) { log_message(LOG_DEBUG, msg); }
void log_info(const std::string& msg) { log_message(LOG_INFO, msg); }
void log_warn(const std::string& msg) { log_message(LOG_WARN, msg); }
void log_error(const std::string& msg) { log_message(LOG_ERROR, msg); }