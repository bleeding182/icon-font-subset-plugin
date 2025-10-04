cmake_minimum_required(VERSION 3.22.1)

project(fontsubsetting-runtime C CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# =============================================================================
# SIZE OPTIMIZATION STRATEGY
# =============================================================================
# This library is optimized for minimal binary size using the following techniques:
#
# 1. COMPILER FLAGS (-Os, -ffunction-sections, -fdata-sections, -fvisibility=hidden)
#    Impact: ~20-30% size reduction
#
# 2. LINKER FLAGS (--gc-sections, -Wl,--strip-all, --exclude-libs,ALL, -Wl,--icf=all)
#    Impact: ~15-20% size reduction via dead code elimination
#
# 3. LINK TIME OPTIMIZATION (-flto=thin)
#    Impact: ~5-10% additional size reduction
#
# 4. HARFBUZZ SIZE OPTIMIZATION (HarfBuzz configuration flags)
#    Impact: ~30-40% reduction in HarfBuzz contribution (HarfBuzz is ~60% of library)
#    - HB_NO_UCD + HB_NO_UNICODE_FUNCS saves ~44KB in .rodata (Unicode tables)
#    Expected savings: ~100-150KB
#
# 5. DISABLED FEATURES (-fno-exceptions, -fno-rtti, -fno-unwind-tables)
#    Impact: ~10-15% size reduction
#
# 6. ABI SELECTION (Currently building 4 ABIs: arm64-v8a, armeabi-v7a, x86, x86_64)
#    Current total: ~1.6MB across all ABIs
#    With arm64-v8a only: ~400KB (75% reduction)
#
# CURRENT SIZES (with all optimizations):
# - x86:         ~411KB (optimized)
# - arm64-v8a:   ~380-400KB
# - armeabi-v7a: ~320-340KB
# - x86_64:      ~390-410KB
#
# =============================================================================

# Strip ALL debug info (-g0) and disable asserts (-DNDEBUG)
add_compile_options(-DNDEBUG -g0)

# Enable LTO (Link Time Optimization) only for Release builds
# Debug builds skip LTO to avoid linker issues and speed up development
# NOTE: CMAKE_INTERPROCEDURAL_OPTIMIZATION disabled due to Windows/MinGW gold linker conflicts
# Using manual -flto=thin instead
# 
# To verify LTO is working, check for these in build logs:
# - "LTO" or "IPO" messages during linking
# - Linker taking noticeably longer
# - Binary size reduction of 5-10%+ compared to non-LTO builds
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

# HarfBuzz configuration flags
add_compile_definitions(
        # === PRE-DEFINED PROFILES ===
        # HB_MINI                      # Disables AAT & legacy fonts (~20KB saved) - USING INDIVIDUAL FLAGS INSTEAD
        # HB_LEAN                      # Disables non-essential features - BREAKS hb_draw_funcs!
        # HB_TINY                      # HB_MINI + HB_LEAN + HB_NO_MT - BREAKS hb_draw_funcs!

        # === CORE FEATURES (keep for path extraction) ===
        # HB_NO_OT                     # Disable OpenType entirely - NEED THIS
        # HB_NO_OT_FONT                # Disable OT font funcs - NEED THIS
        # HB_NO_OT_SHAPE               # Disable OT shaping - NEED THIS
        # HB_NO_DRAW                   # Disable draw API - NEED THIS for glyph paths!

        # === SHAPING FEATURES ===
        HB_NO_AAT                      # Disable Apple Advanced Typography
        HB_NO_LEGACY                   # Disable legacy fonts (pre-OpenType)
        HB_NO_FALLBACK_SHAPE           # Remove unused fallback shaper
        # HB_NO_SHAPER                 # Disable all shapers - NEED shaping

        # === FONT FORMAT SUPPORT ===
        HB_NO_CFF                      # No CFF/PostScript fonts
        HB_NO_OT_FONT_GLYPH_NAMES      # No glyph name lookups (we work with codepoints)

        # === COLOR FONTS & EMOJI ===
        HB_NO_COLOR                    # No color font support
        HB_NO_PAINT                    # No COLR/CPAL paint API
        HB_NO_EMOJI_SEQUENCES          # No emoji sequences

        # === SUBSETTING ===
        HB_NO_SUBSET                   # No font subsetting
        HB_NO_SUBSET_LAYOUT            # No layout subsetting

        # === VARIABLE FONTS ===
        # HB_NO_VAR                    # Keep variable font support for FILL/wght axes

        # === METADATA & NAMES ===
        HB_NO_META                     # No metadata table
        HB_NO_NAME                     # No name table
        HB_NO_GLYPH_NAMES              # No Adobe Glyph List (~3-5KB)

        # === UNICODE FUNCTIONS (DISABLED FOR SIZE SAVINGS - 44KB reduction!) ===
        HB_NO_UCD                      # No Unicode Character Database (~44KB saved in .rodata!)
        HB_NO_UNICODE_FUNCS            # No unicode funcs (saves additional KB)
        # NOTE: Variable font glyph substitution (FILL axis) still works via hb_shape()
        # These are only needed for complex text shaping (RTL, ligatures, etc.)

        # === BUFFER FEATURES ===
        HB_NO_BUFFER_SERIALIZE         # No buffer serialization
        HB_NO_BUFFER_MESSAGE           # No buffer messages
        HB_NO_BUFFER_VERIFY            # No buffer verification

        # === SYSTEM INTEGRATION ===
        HB_NO_GETENV                   # No environment variable checks
        HB_NO_ERRNO                    # No errno usage
        HB_NO_ATEXIT                   # No atexit handlers
        HB_NO_SETLOCALE                # No setlocale calls

        # === THREADING ===
        HB_NO_MT                       # No multi-threading support

        # === PLATFORM-SPECIFIC ===
        # HB_NO_GLIB                   # Already disabled in CMake
        # HB_NO_ICU                    # Already disabled in CMake
        # HB_NO_FREETYPE               # Already disabled in CMake
        # HB_NO_CORETEXT               # Already disabled in CMake
        # HB_NO_UNISCRIBE              # Already disabled in CMake
        # HB_NO_GDI                    # Already disabled in CMake
        # HB_NO_DIRECTWRITE            # Already disabled in CMake

        # === OPTIMIZATION FLAGS ===
        HB_OPTIMIZE_SIZE               # Enable size optimizations

        # === ADVANCED FEATURES ===
        HB_NO_MATH                     # No OpenType MATH table
        HB_NO_STYLE                    # No style API
        HB_NO_STAT                     # No STAT table
        HB_NO_METRICS                  # No metrics API

)

# Add LTO flag only for Release builds
if (CMAKE_BUILD_TYPE STREQUAL "Release")
    add_compile_options(-flto=thin)
endif ()

# Find Android libraries
find_library(log-lib log)
# libandroid not needed - we only use logging
# pthread not needed - HB_NO_MT (no threading)

# Disable pthread linking (HB_NO_MT means we don't need threading)
set(CMAKE_THREAD_LIBS_INIT "")
set(CMAKE_HAVE_THREADS_LIBRARY 0)
set(THREADS_PREFER_PTHREAD_FLAG OFF)

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

# Apply aggressive exception removal to both our code and HarfBuzz
target_compile_options(fontsubsetting-runtime PRIVATE
        $<$<CONFIG:Release>:-Os>
        -fno-unwind-tables
        -fno-asynchronous-unwind-tables
        -fno-exceptions
        -fno-rtti
)

# Also apply exception removal to HarfBuzz
target_compile_options(harfbuzz PRIVATE
        -fno-unwind-tables
        -fno-asynchronous-unwind-tables
        -fno-exceptions
        -fno-rtti
)

# Linker optimizations - dead code elimination and strip unused sections
target_link_options(fontsubsetting-runtime PRIVATE
        -Wl,--gc-sections
        -Wl,--as-needed
        -Wl,--strip-all
        -Wl,--strip-debug            # Remove debug symbols
        -Wl,--exclude-libs,ALL
        -Wl,-z,max-page-size=16384   # 16KB page alignment for Android 15+
        -Wl,--icf=all                # Identical Code Folding - merge identical functions (aggressive)
        -Wl,--hash-style=gnu         # Use GNU hash style (smaller than both)
        -Wl,--no-undefined           # Ensure no undefined symbols
        -Wl,--no-allow-shlib-undefined  # Reject undefined symbols in shared libs
        -Wl,--no-eh-frame-hdr        # Don't create exception frame header (saves ~2-3KB)
        -Wl,--discard-all            # Discard all local symbols (additional stripping)
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
