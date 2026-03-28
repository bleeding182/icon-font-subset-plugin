cmake_minimum_required(VERSION 3.14)
project(glyphruntime C CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_C_STANDARD 11)

# --- Fetch HarfBuzz source at configure time ---
include(FetchContent)
FetchContent_Declare(
    harfbuzz
    URL https://github.com/harfbuzz/harfbuzz/releases/download/12.3.2/harfbuzz-12.3.2.tar.xz
    URL_HASH SHA256=6f6db164359a2da5a84ef826615b448b33e6306067ad829d85d5b0bf936f1bb8
)

# --- Minimal HarfBuzz build (no subsetting, no shaping backends) ---
set(HB_BUILD_SUBSET OFF CACHE BOOL "" FORCE)
set(HB_BUILD_TESTS OFF CACHE BOOL "" FORCE)
set(HB_BUILD_UTILS OFF CACHE BOOL "" FORCE)
set(HB_HAVE_FREETYPE OFF CACHE BOOL "" FORCE)
set(HB_HAVE_GLIB OFF CACHE BOOL "" FORCE)
set(HB_HAVE_GOBJECT OFF CACHE BOOL "" FORCE)
set(HB_HAVE_ICU OFF CACHE BOOL "" FORCE)
set(HB_HAVE_GRAPHITE2 OFF CACHE BOOL "" FORCE)
set(HB_HAVE_CORETEXT OFF CACHE BOOL "" FORCE)
set(HB_HAVE_DIRECTWRITE OFF CACHE BOOL "" FORCE)
set(HB_HAVE_UNISCRIBE OFF CACHE BOOL "" FORCE)
set(BUILD_SHARED_LIBS OFF CACHE BOOL "" FORCE)

FetchContent_MakeAvailable(harfbuzz)

# Apply size-reduction defines to HarfBuzz
# Aggressively strip HarfBuzz to only font loading + draw API + variable fonts.
# We do NOT use shaping, layout, subsetting, color, bitmap, or any text processing.
target_compile_definitions(harfbuzz PRIVATE
    # Threading / system
    HB_NO_MT
    HB_NO_MMAP
    HB_NO_ERRNO
    HB_NO_GETENV
    HB_NO_SETLOCALE
    HB_NO_ATEXIT
    # Shaping (keep OT shaper — required by HarfBuzz internals, but disable extras)
    HB_NO_AAT
    HB_NO_LEGACY
    # Buffer operations
    HB_NO_BUFFER_SERIALIZE
    HB_NO_BUFFER_VERIFY
    HB_NO_BUFFER_MESSAGE
    # Font features we don't need
    HB_NO_BITMAP
    HB_NO_CFF
    HB_NO_COLOR
    HB_NO_PAINT
    HB_NO_MATH
    HB_NO_META
    HB_NO_NAME
    HB_NO_STYLE
    HB_NO_METRICS
    HB_NO_HINTING
    HB_NO_VERTICAL
    HB_NO_OT_FONT_GLYPH_NAMES
    HB_NO_FACE_COLLECT_UNICODES
    HB_NO_VAR_COMPOSITES
    HB_NO_OPEN
    HB_NO_UCD_UNASSIGNED
    # Layout (no text layout needed)
    HB_NO_LAYOUT_FEATURE_PARAMS
    HB_NO_LAYOUT_COLLECT_GLYPHS
    HB_NO_LAYOUT_RARELY_USED
    HB_NO_LAYOUT_UNUSED
    HB_NO_OT_SHAPE_FRACTIONS
    # Caches not needed for single-glyph lookups
    HB_NO_OT_FONT_CMAP_CACHE
)

# Size optimization flags for HarfBuzz
target_compile_options(harfbuzz PRIVATE
    -Oz -fno-exceptions -fno-rtti -flto
    -fno-unwind-tables -fno-asynchronous-unwind-tables
    -ffunction-sections -fdata-sections
    -fvisibility=hidden -fvisibility-inlines-hidden
)

# --- Our JNI library (pure C, no STL) ---
find_library(log-lib log)

add_library(glyphruntime SHARED
    glyph_extractor.c
    glyph_extractor_jni.c
)

target_include_directories(glyphruntime PRIVATE
    ${harfbuzz_SOURCE_DIR}/src
)

target_link_libraries(glyphruntime
    harfbuzz
    ${log-lib}
)

# Hide all symbols except JNI exports
set_target_properties(glyphruntime PROPERTIES
    C_VISIBILITY_PRESET hidden
)

target_compile_options(glyphruntime PRIVATE
    -Oz -flto
    -fno-unwind-tables -fno-asynchronous-unwind-tables
    -ffunction-sections -fdata-sections
)

set_target_properties(glyphruntime PROPERTIES
    LINK_FLAGS "-Wl,--gc-sections -Wl,--exclude-libs,ALL -Wl,--discard-all -s -flto"
)
