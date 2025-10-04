#ifndef FONTSUBSETTING_LOGGING_H
#define FONTSUBSETTING_LOGGING_H

#include <jni.h>
#include <string>

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

// Global logger state accessors (for JNI)
extern JavaVM* g_jvm;
extern jobject g_logger;
extern jmethodID g_logMethod;

#endif // FONTSUBSETTING_LOGGING_H