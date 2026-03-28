package com.davidmedenjak.fontsubsetting.plugin

internal object Constants {
    const val PLUGIN_GROUP = "Font Subsetting"

    object LogMessages {
        const val NATIVE_LOG_PREFIX = "[Native]"
        const val NATIVE_LIBRARY_ERROR = "Failed to load native font subsetting library. Please ensure the native library is built for your platform."
    }
}