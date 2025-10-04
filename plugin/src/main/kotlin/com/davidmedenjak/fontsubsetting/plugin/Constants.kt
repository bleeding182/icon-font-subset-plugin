package com.davidmedenjak.fontsubsetting.plugin

object Constants {
    const val PLUGIN_GROUP = "Font Subsetting"
    
    object Defaults {
        const val INVALID_CHAR_REGEX = "[^a-zA-Z0-9_]"
        const val MULTIPLE_UNDERSCORE_REGEX = "_+"
    }
    
    object LogMessages {
        const val NATIVE_LOG_PREFIX = "[Native]"
        const val NATIVE_LIBRARY_ERROR = "Failed to load native font subsetting library. Please ensure the native library is built for your platform."
    }
}