cmake_minimum_required(VERSION 3.10)
project(fontsubsetting)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# Set optimization flags for Release builds
if(CMAKE_BUILD_TYPE STREQUAL "Release")
    if(MSVC)
        # MSVC optimization flags
        set(CMAKE_CXX_FLAGS_RELEASE "${CMAKE_CXX_FLAGS_RELEASE} /O2 /GL /Gy /Gw")
        set(CMAKE_C_FLAGS_RELEASE "${CMAKE_C_FLAGS_RELEASE} /O2 /GL /Gy /Gw")
        set(CMAKE_SHARED_LINKER_FLAGS_RELEASE "${CMAKE_SHARED_LINKER_FLAGS_RELEASE} /LTCG /OPT:REF /OPT:ICF")
    else()
        # GCC/Clang optimization flags
        set(SIZE_C_FLAGS "-DNDEBUG -Oz -ffunction-sections -fdata-sections")
        # Exceptions are off, so eh_frame and stack canaries are pure overhead.
        set(SIZE_C_FLAGS "${SIZE_C_FLAGS} -fno-unwind-tables -fno-asynchronous-unwind-tables -fno-stack-protector")

        set(CMAKE_C_FLAGS_RELEASE   "${CMAKE_C_FLAGS_RELEASE} ${SIZE_C_FLAGS}")
        set(CMAKE_CXX_FLAGS_RELEASE "${CMAKE_CXX_FLAGS_RELEASE} ${SIZE_C_FLAGS} -fno-exceptions -fno-rtti")

        # Visibility — only JNI functions get __attribute__((visibility("default"))).
        set(CMAKE_C_FLAGS   "${CMAKE_C_FLAGS} -fvisibility=hidden")
        set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -fvisibility=hidden -fvisibility-inlines-hidden")

        # Link-time optimization. Disabled for APPLE because Zig-clang's
        # Mach-O target refuses LTO at compile time ("LTO requires using
        # LLD") even though Zig itself uses LLD — its detection through the
        # wrapper script doesn't recognize that.
        if(NOT APPLE)
            set(CMAKE_C_FLAGS_RELEASE   "${CMAKE_C_FLAGS_RELEASE} -flto")
            set(CMAKE_CXX_FLAGS_RELEASE "${CMAKE_CXX_FLAGS_RELEASE} -flto")
            set(CMAKE_SHARED_LINKER_FLAGS_RELEASE "${CMAKE_SHARED_LINKER_FLAGS_RELEASE} -flto")
        endif()

        if(APPLE)
            # Zig 0.15.1's lld accepts -S (strip debug) and -x (strip
            # non-global symbols) for Mach-O. Saves ~1MB per dylib vs the
            # unstripped baseline.
            set(CMAKE_SHARED_LINKER_FLAGS_RELEASE "${CMAKE_SHARED_LINKER_FLAGS_RELEASE} -Wl,-dead_strip -Wl,-S -Wl,-x")
        else()
            set(CMAKE_SHARED_LINKER_FLAGS_RELEASE "${CMAKE_SHARED_LINKER_FLAGS_RELEASE} -Wl,--gc-sections -Wl,--as-needed -Wl,--build-id=none -s")
        endif()
    endif()
endif()

# For Windows, configure for static runtime to avoid DLL dependencies
if(WIN32)
    # Use static runtime libraries
    set(CMAKE_MSVC_RUNTIME_LIBRARY "MultiThreaded$<$<CONFIG:Debug>:Debug>")
    
    # For MinGW, link statically
    if(MINGW)
        set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -static-libgcc -static-libstdc++")
        set(CMAKE_C_FLAGS "${CMAKE_C_FLAGS} -static-libgcc")
        # Avoid dependency on libwinpthread
        set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -Wl,-Bstatic,--whole-archive -lwinpthread -Wl,--no-whole-archive")
    endif()
    
    # Build dependencies as static
    set(BUILD_SHARED_LIBS OFF)
    set(CMAKE_FIND_LIBRARY_SUFFIXES ".a" ".lib")
endif()

# JNI headers are header-only for our purposes (we don't link libjvm), and the
# Linux JDK headers in $JAVA_HOME/include + include/linux are portable enough
# to use when cross-compiling for Windows and macOS too. Docker pre-sets
# JAVA_HOME; native Linux builds outside Docker need it set as well.
if(NOT DEFINED ENV{JAVA_HOME})
    message(FATAL_ERROR "JAVA_HOME must be set. Inside the build Docker image this is automatic; for direct native builds, point it at a JDK install.")
endif()
set(JNI_INCLUDE_DIRS
    $ENV{JAVA_HOME}/include
    $ENV{JAVA_HOME}/include/linux
)
set(JNI_LIBRARIES "")
message(STATUS "JNI include dirs: ${JNI_INCLUDE_DIRS}")

# Find HarfBuzz
# We've pre-installed HarfBuzz in the Docker container
# HarfBuzz is pre-built per-target in the cross-compile Docker image and
# installed under /usr/local/<system>-<arch>/. The Windows MinGW build sits
# in the mingw sysroot for find_package compatibility. libharfbuzz-subset.a
# has its duplicate-of-harfbuzz.cc objects stripped in the Dockerfile, so we
# don't need --allow-multiple-definition.
if(CMAKE_SYSTEM_NAME STREQUAL "Windows" OR WIN32 OR MINGW)
    set(_HB_PREFIX /usr/x86_64-w64-mingw32)
elseif(CMAKE_SYSTEM_NAME STREQUAL "Darwin")
    set(_HB_PREFIX /usr/local/darwin-${CMAKE_SYSTEM_PROCESSOR})
elseif(CMAKE_SYSTEM_NAME STREQUAL "Linux")
    set(_HB_PREFIX /usr/local/linux-${CMAKE_SYSTEM_PROCESSOR})
endif()

if(_HB_PREFIX AND EXISTS "${_HB_PREFIX}/lib/libharfbuzz-subset.a")
    set(HARFBUZZ_INCLUDE_DIRS ${_HB_PREFIX}/include/harfbuzz)
    set(HARFBUZZ_LIBRARIES
        ${_HB_PREFIX}/lib/libharfbuzz-subset.a
        ${_HB_PREFIX}/lib/libharfbuzz.a
    )
    set(HARFBUZZ_FOUND TRUE)
endif()

# Fallback for native Linux builds outside the Docker image (developer
# convenience — e.g. distro-packaged HarfBuzz). Not used by CI.
if(NOT HARFBUZZ_FOUND AND CMAKE_SYSTEM_NAME STREQUAL "Linux")
    find_package(PkgConfig)
    if(PkgConfig_FOUND)
        pkg_check_modules(HARFBUZZ_SUBSET harfbuzz-subset)
        if(HARFBUZZ_SUBSET_FOUND)
            set(HARFBUZZ_INCLUDE_DIRS ${HARFBUZZ_SUBSET_INCLUDE_DIRS})
            find_library(HARFBUZZ_SUBSET_LIB harfbuzz-subset PATHS ${HARFBUZZ_SUBSET_LIBRARY_DIRS})
            find_library(HARFBUZZ_LIB        harfbuzz        PATHS ${HARFBUZZ_SUBSET_LIBRARY_DIRS})
            set(HARFBUZZ_LIBRARIES ${HARFBUZZ_SUBSET_LIB} ${HARFBUZZ_LIB})
            set(HARFBUZZ_FOUND TRUE)
        endif()
    endif()
endif()

if(NOT HARFBUZZ_FOUND)
    message(FATAL_ERROR "HarfBuzz not found at ${_HB_PREFIX}. Inside the cross-compile Docker image this is built automatically; for direct host builds, install distro HarfBuzz dev packages.")
endif()

message(STATUS "HarfBuzz include dirs: ${HARFBUZZ_INCLUDE_DIRS}")
message(STATUS "HarfBuzz libraries: ${HARFBUZZ_LIBRARIES}")

# Create the JNI library
add_library(fontsubsetting SHARED
    fontsubsetting_jni.cpp
    logging.cpp
    jni_utils.cpp
    font_io.cpp
    font_subsetter.cpp
    font_metrics.cpp
)

# Set default symbol visibility to hidden (only export JNI functions)
set_target_properties(fontsubsetting PROPERTIES
    C_VISIBILITY_PRESET hidden
    CXX_VISIBILITY_PRESET hidden
    VISIBILITY_INLINES_HIDDEN ON
)

# Symbol visibility is controlled via attributes in the source code

# Include directories
target_include_directories(fontsubsetting PRIVATE 
    ${JNI_INCLUDE_DIRS}
    ${HARFBUZZ_INCLUDE_DIRS}
    ${CMAKE_CURRENT_SOURCE_DIR}/../../../build/generated/jni-headers
)

# Link libraries
if(WIN32)
    if(MINGW)
        # For MinGW, link everything statically except Windows system DLLs.
        # --start-group lets ld revisit archives to satisfy forward references
        # between libharfbuzz-subset.a and libharfbuzz.a without pulling
        # duplicate object files.
        target_link_libraries(fontsubsetting
            -Wl,--exclude-libs,ALL
            -Wl,--start-group ${HARFBUZZ_LIBRARIES} -Wl,--end-group
            -Wl,-Bstatic
            -lwinpthread
            -static-libgcc
            -static-libstdc++
            -Wl,-Bdynamic
            -lkernel32
            -luser32
        )
        set_target_properties(fontsubsetting PROPERTIES
            LINK_FLAGS "-Wl,--no-undefined -Wl,--enable-runtime-pseudo-reloc"
        )
    else()
        # For MSVC
        target_link_libraries(fontsubsetting
            ${HARFBUZZ_LIBRARIES}
            kernel32.lib
            user32.lib
        )
    endif()
elseif(NOT APPLE)
    # Linux - link HarfBuzz statically. --start-group resolves forward refs
    # between libharfbuzz-subset.a and libharfbuzz.a. Version script hides
    # HarfBuzz symbols so only JNI functions are exported.
    target_link_libraries(fontsubsetting
        -Wl,--start-group ${HARFBUZZ_LIBRARIES} -Wl,--end-group
    )
    set_target_properties(fontsubsetting PROPERTIES
        LINK_FLAGS "-Wl,--no-undefined -Wl,--version-script=${CMAKE_CURRENT_SOURCE_DIR}/fontsubsetting.map"
    )
else()
    # macOS - hide HarfBuzz symbols and only export JNI functions
    target_link_libraries(fontsubsetting
        ${HARFBUZZ_LIBRARIES}
    )
    # Zig linker doesn't support -exported_symbols_list, but we already have -fvisibility=hidden
    # which hides all symbols by default. JNI functions are exported via __attribute__((visibility("default")))
    # in the source code, so they will be visible.
    # Use dead_strip to remove unused code
    set_target_properties(fontsubsetting PROPERTIES
        LINK_FLAGS "-Wl,-dead_strip"
    )
endif()

# Platform-specific configurations
if(WIN32)
    # On Windows, ensure we get fontsubsetting.dll (without lib prefix)
    set_target_properties(fontsubsetting PROPERTIES
        PREFIX ""
        RUNTIME_OUTPUT_DIRECTORY ${CMAKE_BINARY_DIR}
        LIBRARY_OUTPUT_DIRECTORY ${CMAKE_BINARY_DIR}
        ARCHIVE_OUTPUT_DIRECTORY ${CMAKE_BINARY_DIR}
    )
    
elseif(APPLE)
    # On macOS, we want libfontsubsetting.dylib
    set_target_properties(fontsubsetting PROPERTIES
        PREFIX "lib"
        SUFFIX ".dylib"
        LIBRARY_OUTPUT_DIRECTORY ${CMAKE_BINARY_DIR}
    )
else()
    # On Linux, we want libfontsubsetting.so
    set_target_properties(fontsubsetting PROPERTIES
        PREFIX "lib"
        SUFFIX ".so"
        LIBRARY_OUTPUT_DIRECTORY ${CMAKE_BINARY_DIR}
    )
endif()

# Ensure the library is built with position-independent code
set_target_properties(fontsubsetting PROPERTIES
    POSITION_INDEPENDENT_CODE ON
)