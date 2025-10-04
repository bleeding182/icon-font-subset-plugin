cmake_minimum_required(VERSION 3.22.1)

project(fontsubsetting-runtime C CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# Strip ALL debug info (-g0) and disable asserts (-DNDEBUG)
add_compile_options(-DNDEBUG -g0)

# Enable LTO (Link Time Optimization) only for Release builds
# Debug builds skip LTO to avoid linker issues and speed up development
# NOTE: CMAKE_INTERPROCEDURAL_OPTIMIZATION disabled due to Windows/MinGW gold linker conflicts
# Using manual -flto=thin instead
if (CMAKE_BUILD_TYPE STREQUAL "Release")
    set(CMAKE_INTERPROCEDURAL_OPTIMIZATION FALSE)
    message(STATUS "Using manual -flto=thin for Release build (CMAKE_INTERPROCEDURAL_OPTIMIZATION disabled due to linker conflicts)")
else ()
    set(CMAKE_INTERPROCEDURAL_OPTIMIZATION FALSE)
    message(STATUS "LTO disabled for Debug build")
endif ()

# Compiler optimizations for size and performance
add_compile_options(
        -Os                      # Optimize for size
        -ffunction-sections      # Each function in its own section (for --gc-sections)
        -fdata-sections          # Each data item in its own section (for --gc-sections)
        -fvisibility=hidden      # Hide symbols by default (reduces binary size)
        -fno-exceptions          # Disable C++ exceptions (significant size reduction)
        -fno-rtti                # Disable RTTI (reduces size, we don't use dynamic_cast/typeid)
        -fno-unwind-tables       # Don't emit unwind tables (saves ~12-14KB since we disabled exceptions)
        -fno-asynchronous-unwind-tables  # Don't emit async unwind tables
)

# HarfBuzz size optimization flags
add_compile_definitions(
        HB_NO_FALLBACK_SHAPE     # Remove unused fallback shaper
        HB_NO_BUFFER_SERIALIZE   # We don't serialize buffers
        HB_NO_BUFFER_MESSAGE     # We don't use buffer messages
        HB_NO_PAINT              # We don't use COLR/CPAL paint API
        HB_NO_SUBSET_LAYOUT      # We're not subsetting, just extracting paths
        HB_NO_CFF                # We don't need CFF extents (saves ~10KB)
        HB_NO_GETENV             # Don't check environment variables
        HB_NO_ERRNO              # Don't use errno
        HB_NO_ATEXIT             # Don't register atexit handlers
        HB_NO_SETLOCALE          # Don't use setlocale
        HB_OPTIMIZE_SIZE         # Enable internal size optimizations
        HB_NO_UCD                # Don't include Unicode Character Database (~15-20KB saved!)
        HB_NO_UNICODE_FUNCS      # We don't use Unicode functions for shaping
        HB_NO_GLYPH_NAMES        # Don't include Adobe Glyph List (~174 glyph names, saves ~3-5KB)
)

# Add LTO flag only for Release builds
if (CMAKE_BUILD_TYPE STREQUAL "Release")
    add_compile_options(-flto=thin)
endif ()

# Find Android libraries
find_library(log-lib log)
# libandroid not needed - we only use logging

# HarfBuzz configuration - minimal build for path extraction only
include(FetchContent)

FetchContent_Declare(
        harfbuzz
        URL https://github.com/harfbuzz/harfbuzz/releases/download/10.0.1/harfbuzz-10.0.1.tar.xz
        URL_HASH SHA256=b2cb13bd351904cb9038f907dc0dee0ae07127061242fe3556b2795c4e9748fc
)

# Disable all HarfBuzz features we don't need
set(HB_BUILD_SUBSET OFF CACHE BOOL "Build harfbuzz-subset" FORCE)
set(HB_HAVE_FREETYPE OFF CACHE BOOL "Enable freetype" FORCE)
set(HB_HAVE_GLIB OFF CACHE BOOL "Enable glib" FORCE)
set(HB_HAVE_ICU OFF CACHE BOOL "Enable ICU" FORCE)
set(HB_HAVE_GRAPHITE2 OFF CACHE BOOL "Enable Graphite2" FORCE)
set(HB_BUILD_UTILS OFF CACHE BOOL "Build harfbuzz utils" FORCE)
set(HB_HAVE_CORETEXT OFF CACHE BOOL "Enable CoreText" FORCE)
set(HB_HAVE_UNISCRIBE OFF CACHE BOOL "Enable Uniscribe" FORCE)
set(HB_HAVE_GDI OFF CACHE BOOL "Enable GDI" FORCE)
set(HB_HAVE_DIRECTWRITE OFF CACHE BOOL "Enable DirectWrite" FORCE)

FetchContent_MakeAvailable(harfbuzz)

# Main JNI library
add_library(fontsubsetting-runtime SHARED
        font_path_extractor.cpp
        font_path_jni.cpp
)

target_include_directories(fontsubsetting-runtime PRIVATE
        ${harfbuzz_SOURCE_DIR}/src
)

# Compiler-specific optimizations (additional per-target, but most are now global)
target_compile_options(fontsubsetting-runtime PRIVATE
        $<$<CONFIG:Release>:-Os>
)

# Linker optimizations - dead code elimination and strip unused sections
target_link_options(fontsubsetting-runtime PRIVATE
        -Wl,--gc-sections
        -Wl,--as-needed
        -Wl,--strip-all
        -Wl,--exclude-libs,ALL
        -Wl,-z,max-page-size=16384  # 16KB page alignment for Android 15+
        -Wl,--icf=all                # Identical Code Folding - merge identical functions
        -Wl,--hash-style=gnu         # Use GNU hash style (smaller than both)
        -Wl,-O3                      # Linker optimization level
)

target_link_libraries(fontsubsetting-runtime
        harfbuzz
)

# Only link log library for Debug builds (Release has logging disabled)
if (CMAKE_BUILD_TYPE STREQUAL "Debug")
    target_link_libraries(fontsubsetting-runtime ${log-lib})
endif ()

# Additional stripping in release builds
if (CMAKE_BUILD_TYPE STREQUAL "Release")
    add_custom_command(TARGET fontsubsetting-runtime POST_BUILD
            COMMAND ${CMAKE_STRIP} --strip-unneeded $<TARGET_FILE:fontsubsetting-runtime>
            COMMAND ${CMAKE_STRIP} --strip-debug $<TARGET_FILE:fontsubsetting-runtime>
    )
endif ()
