#ifndef FONTSUBSETTING_LOGGING_H
#define FONTSUBSETTING_LOGGING_H

#include <jni.h>
#include <string>

#ifdef _WIN32
#include <windows.h>
#else
#include <pthread.h>
#endif

enum LogLevel {
    LOG_DEBUG = 0,
    LOG_INFO = 1,
    LOG_WARN = 2,
    LOG_ERROR = 3
};

// Initialize the logging system with Java callback
void init_logging(JavaVM* jvm, jobject logger, jmethodID logMethod);

// Cleanup logging resources
void cleanup_logging(JavaVM* jvm);

// Core logging function
void log_message(LogLevel level, const std::string& message);

// Convenience logging functions
void log_debug(const std::string& msg);
void log_info(const std::string& msg);
void log_warn(const std::string& msg);
void log_error(const std::string& msg);

// Mutex protecting global logger state
#ifdef _WIN32
extern CRITICAL_SECTION g_log_mutex;
#else
extern pthread_mutex_t g_log_mutex;
#endif

// RAII lock guard for the log mutex
struct LogMutexGuard {
    LogMutexGuard() {
#ifdef _WIN32
        EnterCriticalSection(&g_log_mutex);
#else
        pthread_mutex_lock(&g_log_mutex);
#endif
    }
    ~LogMutexGuard() {
#ifdef _WIN32
        LeaveCriticalSection(&g_log_mutex);
#else
        pthread_mutex_unlock(&g_log_mutex);
#endif
    }
};

// Global logger state accessors (for JNI)
extern JavaVM* g_jvm;
extern jobject g_logger;
extern jmethodID g_logMethod;

#endif // FONTSUBSETTING_LOGGING_H