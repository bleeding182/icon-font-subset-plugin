cmake_minimum_required(VERSION 3.22.1)

project(fontsubsetting-runtime C CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# Enable LTO (Link Time Optimization) only for Release builds
# Debug builds skip LTO to avoid linker issues and speed up development
if (CMAKE_BUILD_TYPE STREQUAL "Release")
    set(CMAKE_INTERPROCEDURAL_OPTIMIZATION TRUE)
    message(STATUS "LTO enabled for Release build")
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
)

# Add LTO flag only for Release builds
if (CMAKE_BUILD_TYPE STREQUAL "Release")
    add_compile_options(-flto=thin)
endif ()

# Find Android libraries
find_library(log-lib log)
find_library(android-lib android)

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
)

target_link_libraries(fontsubsetting-runtime
        harfbuzz
        ${log-lib}
        ${android-lib}
)

# Additional stripping in release builds
if (CMAKE_BUILD_TYPE STREQUAL "Release")
    add_custom_command(TARGET fontsubsetting-runtime POST_BUILD
            COMMAND ${CMAKE_STRIP} --strip-unneeded $<TARGET_FILE:fontsubsetting-runtime>
            COMMAND ${CMAKE_STRIP} --strip-debug $<TARGET_FILE:fontsubsetting-runtime>
    )
endif ()
