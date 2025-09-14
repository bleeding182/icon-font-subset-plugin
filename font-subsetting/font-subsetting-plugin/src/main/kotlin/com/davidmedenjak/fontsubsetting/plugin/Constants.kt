package com.davidmedenjak.fontsubsetting.plugin

object Constants {
    const val PLUGIN_GROUP = "Font Subsetting"
    
    object Directories {
        const val GENERATED_SOURCE = "generated/source/fontIcons"
        const val INTERMEDIATES_FONT = "intermediates/font_subset"
        const val BUILD_CACHE = "fontSubsetting/cache"
        const val BUILD_OUTPUT = "fontSubsetting"
        const val FONT_RESOURCE_DIR = "res/font"
    }
    
    object TaskNames {
        const val GENERATE_ICONS_PREFIX = "generate"
        const val GENERATE_ICONS_SUFFIX = "Icons"
        const val ANALYZE_USAGE_PREFIX = "analyze"
        const val ANALYZE_USAGE_SUFFIX = "IconUsage"
        const val SUBSET_FONTS_PREFIX = "subset"
        const val SUBSET_FONTS_SUFFIX = "Fonts"
    }
    
    object FileNames {
        const val USAGE_DATA_PREFIX = "usedIcons_"
        const val USAGE_DATA_EXTENSION = ".txt"
    }
    
    object Defaults {
        const val TTF_EXTENSION = ".ttf"
        const val INVALID_CHAR_REGEX = "[^a-zA-Z0-9_]"
        const val MULTIPLE_UNDERSCORE_REGEX = "_+"
    }
    
    object LogMessages {
        const val NATIVE_LOG_PREFIX = "[Native]"
        const val FONT_NOT_FOUND = "Font file not found: %s"
        const val CODEPOINTS_NOT_FOUND = "Codepoints file not found: %s"
        const val USAGE_DATA_LOADED = "Loaded usage data with %d used icons"
        const val SUBSETTING_COMPLETE = "Font subsetting complete: %s"
        const val NATIVE_LIBRARY_ERROR = "Failed to load native font subsetting library. Please ensure the native library is built for your platform."
    }
}