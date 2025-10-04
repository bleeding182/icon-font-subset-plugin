cmake_minimum_required(VERSION 3.22.1)

project(fontsubsetting-runtime C CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# Strip debug info and disable asserts for Release builds
add_compile_options(-DNDEBUG -g0)

# Compiler optimizations for minimal binary size
add_compile_options(
        -Oz                      # Optimize aggressively for size (more aggressive than -Os)
        -ffunction-sections      # Each function in its own section (enables --gc-sections)
        -fdata-sections          # Each data item in its own section (enables --gc-sections)
        -fvisibility=hidden      # Hide symbols by default (reduces dynamic symbol table)
        -fno-exceptions          # Disable C++ exceptions
        -fno-rtti                # Disable C++ RTTI
        -fno-unwind-tables       # Don't emit unwind tables
        -fno-asynchronous-unwind-tables  # Don't emit async unwind tables
        -fno-stack-protector     # Disable stack protection (acceptable for this use case)
        -fomit-frame-pointer     # Omit frame pointer (better code density)
        -fmerge-all-constants    # Merge identical constants across compilation units
)

# HarfBuzz configuration flags
add_compile_definitions(
        # Core features - DO NOT DISABLE THESE
        # HB_NO_OT          - REQUIRED for OpenType support
        # HB_NO_OT_FONT     - REQUIRED for font functions
        # HB_NO_OT_SHAPE    - REQUIRED for shaping
        # HB_NO_DRAW        - REQUIRED for glyph path extraction (hb_draw_funcs)
        # HB_NO_SHAPER      - REQUIRED for variable font support

        # Shaping features - safe to disable
        HB_NO_AAT                      # No Apple Advanced Typography
        HB_NO_LEGACY                   # No legacy fonts (pre-OpenType)
        HB_NO_FALLBACK_SHAPE           # No fallback shaper

        # Font format support - safe to disable
        HB_NO_CFF                      # No CFF/PostScript fonts
        HB_NO_OT_FONT_GLYPH_NAMES      # No glyph name lookups (we use codepoints)

        # Color fonts & emoji - safe to disable
        HB_NO_COLOR
        HB_NO_PAINT
        HB_NO_EMOJI_SEQUENCES

        # Subsetting - safe to disable (we don't subset fonts)
        HB_NO_SUBSET
        HB_NO_SUBSET_LAYOUT

        # Variable fonts - DO NOT DISABLE
        # HB_NO_VAR - KEEP enabled for FILL/wght axes support

        # Metadata - safe to disable
        HB_NO_META
        HB_NO_NAME
        HB_NO_GLYPH_NAMES

        # Unicode functions - DISABLED for 44KB savings
        # Note: Variable font substitution (FILL axis) still works via hb_shape()
        # These are only needed for complex text shaping (RTL, ligatures, etc.)
        HB_NO_UCD                      # No Unicode Character Database (~44KB saved)
        HB_NO_UNICODE_FUNCS            # No unicode functions

        # Buffer features - safe to disable
        HB_NO_BUFFER_SERIALIZE
        HB_NO_BUFFER_MESSAGE
        HB_NO_BUFFER_VERIFY

        # System integration - safe to disable
        HB_NO_GETENV
        HB_NO_ERRNO
        HB_NO_ATEXIT
        HB_NO_SETLOCALE

        # Threading - safe to disable (we're single-threaded)
        HB_NO_MT

        # Platform-specific features - already disabled via CMake options
        # (GLIB, ICU, FREETYPE, CORETEXT, UNISCRIBE, GDI, DIRECTWRITE)

        # Optimization flags
        HB_OPTIMIZE_SIZE

        # Advanced features - safe to disable
        HB_NO_MATH
        HB_NO_STYLE
        HB_NO_STAT
        HB_NO_METRICS

        # Additional optimizations - safe to disable
        HB_NO_BORING_EXPANSION         # Internal optimization
        HB_NO_BEYOND_64K               # No support for fonts with >64K glyphs
)

# Enable LTO (Link Time Optimization) for Release builds
if (CMAKE_BUILD_TYPE STREQUAL "Release")
    add_compile_options(-flto=thin)
    add_link_options(-flto=thin)
endif ()

# Find Android log library
find_library(log-lib log)

# Disable pthread linking (HB_NO_MT = no threading)
set(CMAKE_THREAD_LIBS_INIT "")
set(CMAKE_HAVE_THREADS_LIBRARY 0)
set(THREADS_PREFER_PTHREAD_FLAG OFF)

# Fetch HarfBuzz
include(FetchContent)

FetchContent_Declare(
        harfbuzz
        URL https://github.com/harfbuzz/harfbuzz/releases/download/10.0.1/harfbuzz-10.0.1.tar.xz
        URL_HASH SHA256=b2cb13bd351904cb9038f907dc0dee0ae07127061242fe3556b2795c4e9748fc
)

# Disable HarfBuzz features not needed for path extraction
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

# Apply size optimizations to our library
target_compile_options(fontsubsetting-runtime PRIVATE
        $<$<CONFIG:Release>:-Oz>
        -fno-unwind-tables
        -fno-asynchronous-unwind-tables
        -fno-exceptions
        -fno-rtti
)

# Apply same optimizations to HarfBuzz
target_compile_options(harfbuzz PRIVATE
        -fno-unwind-tables
        -fno-asynchronous-unwind-tables
        -fno-exceptions
        -fno-rtti
)

# Apply LTO to HarfBuzz for cross-module optimization
if (CMAKE_BUILD_TYPE STREQUAL "Release")
    target_compile_options(harfbuzz PRIVATE -flto=thin)
    target_link_options(harfbuzz PRIVATE -flto=thin)
endif ()

# Linker optimizations
target_link_options(fontsubsetting-runtime PRIVATE
        -Wl,--gc-sections                    # Dead code elimination
        -Wl,--as-needed                      # Only link needed libraries
        -Wl,--strip-all                      # Strip all symbols
        -Wl,--strip-debug                    # Strip debug symbols
        -Wl,--exclude-libs,ALL               # Don't export dependency symbols
        -Wl,-z,max-page-size=16384           # 16KB page alignment (Android 15+)
        -Wl,--icf=all                        # Identical code folding (aggressive)
        -Wl,--hash-style=gnu                 # GNU hash style (smaller)
        -Wl,--no-undefined                   # No undefined symbols
        -Wl,--no-allow-shlib-undefined       # Reject undefined symbols in shared libs
        -Wl,--no-eh-frame-hdr                # No exception frame header
        -Wl,--discard-all                    # Discard local symbols
        -Wl,--build-id=none                  # No build ID
        -Wl,--no-gnu-unique                  # No GNU unique symbols
)

target_link_libraries(fontsubsetting-runtime
        harfbuzz
)

# Link log library for Debug builds only
if (CMAKE_BUILD_TYPE STREQUAL "Debug")
    target_link_libraries(fontsubsetting-runtime ${log-lib})
endif ()

# Post-build stripping for Release builds
if (CMAKE_BUILD_TYPE STREQUAL "Release")
    add_custom_command(TARGET fontsubsetting-runtime POST_BUILD
            COMMENT "Stripping binary for maximum size reduction..."
            COMMAND ${CMAKE_STRIP} --strip-unneeded --strip-debug $<TARGET_FILE:fontsubsetting-runtime>
    )
endif ()
