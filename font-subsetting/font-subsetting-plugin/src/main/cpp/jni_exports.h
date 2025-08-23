#ifndef FONTSUBSETTING_JNI_EXPORTS_H
#define FONTSUBSETTING_JNI_EXPORTS_H

// Macro to properly export JNI functions with correct visibility
#if defined(_WIN32) || defined(_WIN64)
    #define JNIEXPORT_FONTSUBSETTING __declspec(dllexport)
#else
    #define JNIEXPORT_FONTSUBSETTING __attribute__((visibility("default")))
#endif

// Ensure C linkage for JNI functions
#ifdef __cplusplus
#define EXTERN_C extern "C"
#else
#define EXTERN_C
#endif

// Combined macro for JNI function declarations
#define JNI_EXPORT EXTERN_C JNIEXPORT_FONTSUBSETTING

#endif // FONTSUBSETTING_JNI_EXPORTS_H