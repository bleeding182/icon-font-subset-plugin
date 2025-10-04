cmake_minimum_required(VERSION 3.10)
project(fontsubsetting)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# Enable interprocedural optimization (LTO) for Release builds
if(CMAKE_BUILD_TYPE STREQUAL "Release")
    include(CheckIPOSupported)
    check_ipo_supported(RESULT ipo_supported OUTPUT ipo_output)
    if(ipo_supported)
        set(CMAKE_INTERPROCEDURAL_OPTIMIZATION TRUE)
        message(STATUS "IPO/LTO enabled for Release build")
    else()
        message(STATUS "IPO/LTO not supported: ${ipo_output}")
    endif()
endif()

# Set optimization flags for Release builds
if(CMAKE_BUILD_TYPE STREQUAL "Release")
    if(MSVC)
        # MSVC optimization flags
        set(CMAKE_CXX_FLAGS_RELEASE "${CMAKE_CXX_FLAGS_RELEASE} /O2 /GL /Gy /Gw")
        set(CMAKE_C_FLAGS_RELEASE "${CMAKE_C_FLAGS_RELEASE} /O2 /GL /Gy /Gw")
        set(CMAKE_SHARED_LINKER_FLAGS_RELEASE "${CMAKE_SHARED_LINKER_FLAGS_RELEASE} /LTCG /OPT:REF /OPT:ICF")
    else()
        # GCC/Clang optimization flags - aggressive optimization
        set(CMAKE_CXX_FLAGS_RELEASE "${CMAKE_CXX_FLAGS_RELEASE} -O3 -DNDEBUG")
        set(CMAKE_C_FLAGS_RELEASE "${CMAKE_C_FLAGS_RELEASE} -O3 -DNDEBUG")
        
        # Additional optimization flags for size
        set(CMAKE_CXX_FLAGS_RELEASE "${CMAKE_CXX_FLAGS_RELEASE} -Os -fno-exceptions -fno-rtti")
        set(CMAKE_C_FLAGS_RELEASE "${CMAKE_C_FLAGS_RELEASE} -Os")
        
        # Visibility settings - hide all symbols by default (already configured for target below)
        # These flags help the compiler optimize better when symbols are hidden
        set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -fvisibility=hidden -fvisibility-inlines-hidden")
        set(CMAKE_C_FLAGS "${CMAKE_C_FLAGS} -fvisibility=hidden")
        
        # Enable function and data sections for better dead code elimination
        set(CMAKE_CXX_FLAGS_RELEASE "${CMAKE_CXX_FLAGS_RELEASE} -ffunction-sections -fdata-sections")
        set(CMAKE_C_FLAGS_RELEASE "${CMAKE_C_FLAGS_RELEASE} -ffunction-sections -fdata-sections")
        
        # Link-time optimization
        if(NOT APPLE)
            set(CMAKE_CXX_FLAGS_RELEASE "${CMAKE_CXX_FLAGS_RELEASE} -flto")
            set(CMAKE_C_FLAGS_RELEASE "${CMAKE_C_FLAGS_RELEASE} -flto")
        endif()
        
        # Linker flags for garbage collection of unused sections
        if(NOT APPLE)
            set(CMAKE_SHARED_LINKER_FLAGS_RELEASE "${CMAKE_SHARED_LINKER_FLAGS_RELEASE} -Wl,--gc-sections")
        else()
            # Aggressive dead stripping for macOS (Zig doesn't support -x or -S)
            set(CMAKE_SHARED_LINKER_FLAGS_RELEASE "${CMAKE_SHARED_LINKER_FLAGS_RELEASE} -Wl,-dead_strip")
        endif()
        
        # Strip symbols for Release builds
        set(CMAKE_SHARED_LINKER_FLAGS_RELEASE "${CMAKE_SHARED_LINKER_FLAGS_RELEASE} -s")
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

# Find JNI
# Set JAVA_HOME from environment if available
if(DEFINED ENV{JAVA_HOME})
    set(JAVA_HOME $ENV{JAVA_HOME})
    message(STATUS "Using JAVA_HOME: ${JAVA_HOME}")
endif()

# Handle JNI for different platforms
if(CMAKE_SYSTEM_NAME STREQUAL "Windows" OR WIN32 OR MINGW)
    # Windows or MinGW cross-compilation
    # For cross-compilation, we use the Linux JDK's headers which are compatible
    if(JAVA_HOME)
        set(JNI_INCLUDE_DIRS
            ${JAVA_HOME}/include
            ${JAVA_HOME}/include/linux  # Use linux headers for cross-compilation
        )
    else()
        # Fallback for manylinux2014 (CentOS 7)
        set(JNI_INCLUDE_DIRS
            /usr/lib/jvm/java-1.8.0-openjdk/include
            /usr/lib/jvm/java-1.8.0-openjdk/include/linux
        )
    endif()
    set(JNI_LIBRARIES "")  # We don't need to link JVM libraries for JNI
    set(JNI_FOUND TRUE)
elseif(CMAKE_SYSTEM_NAME STREQUAL "Darwin")
    # macOS cross-compilation from Linux - use Linux JNI headers
    # The JNI headers are platform-independent for the most part
    if(JAVA_HOME)
        set(JNI_INCLUDE_DIRS
            ${JAVA_HOME}/include
            ${JAVA_HOME}/include/linux  # Use Linux headers for cross-compilation
        )
    else()
        # Fallback for Ubuntu
        set(JNI_INCLUDE_DIRS
            /usr/lib/jvm/java-11-openjdk-amd64/include
            /usr/lib/jvm/java-11-openjdk-amd64/include/linux
        )
    endif()
    set(JNI_LIBRARIES "")
    set(JNI_FOUND TRUE)
else()
    # Linux native build
    if(JAVA_HOME)
        set(JNI_INCLUDE_DIRS 
            ${JAVA_HOME}/include
            ${JAVA_HOME}/include/linux
        )
        set(JNI_LIBRARIES "")
        set(JNI_FOUND TRUE)
    else()
        # Try to find JNI, but don't require AWT
        find_path(JNI_INCLUDE_DIRS jni.h
            PATHS
            /usr/lib/jvm/java-17-openjdk-amd64/include
            /usr/lib/jvm/java-11-openjdk-amd64/include
            /usr/lib/jvm/default-java/include
            ${JAVA_HOME}/include
        )
        
        if(JNI_INCLUDE_DIRS)
            list(APPEND JNI_INCLUDE_DIRS "${JNI_INCLUDE_DIRS}/linux")
            set(JNI_FOUND TRUE)
            set(JNI_LIBRARIES "")
        else()
            message(FATAL_ERROR "Could not find JNI headers. Please set JAVA_HOME environment variable.")
        endif()
    endif()
endif()

message(STATUS "JNI include dirs: ${JNI_INCLUDE_DIRS}")

# Find HarfBuzz
# We've pre-installed HarfBuzz in the Docker container
if(CMAKE_SYSTEM_NAME STREQUAL "Windows" OR WIN32 OR MINGW)
    # For Windows cross-compilation (Ubuntu MinGW paths)
    set(HARFBUZZ_INCLUDE_DIRS /usr/x86_64-w64-mingw32/include/harfbuzz)
    # Link both libraries - subset needs base library symbols
    set(HARFBUZZ_LIBRARIES
        /usr/x86_64-w64-mingw32/lib/libharfbuzz-subset.a
        /usr/x86_64-w64-mingw32/lib/libharfbuzz.a
    )
    set(HARFBUZZ_FOUND TRUE)
elseif(CMAKE_SYSTEM_NAME STREQUAL "Darwin")
    # For macOS cross-compilation using Zig
    if(CMAKE_SYSTEM_PROCESSOR STREQUAL "aarch64")
        # ARM64 macOS
        set(HARFBUZZ_INCLUDE_DIRS /usr/local/darwin-aarch64/include/harfbuzz)
        set(HARFBUZZ_LIBRARIES
            /usr/local/darwin-aarch64/lib/libharfbuzz-subset.a
            /usr/local/darwin-aarch64/lib/libharfbuzz.a
        )
    else()
        # x86_64 macOS
        set(HARFBUZZ_INCLUDE_DIRS /usr/local/darwin-x86_64/include/harfbuzz)
        set(HARFBUZZ_LIBRARIES
            /usr/local/darwin-x86_64/lib/libharfbuzz-subset.a
            /usr/local/darwin-x86_64/lib/libharfbuzz.a
        )
    endif()
    set(HARFBUZZ_FOUND TRUE)
else()
    # For Linux native build
    find_package(PkgConfig)
    if(PkgConfig_FOUND)
        # Link only the subset library which includes all needed symbols
        pkg_check_modules(HARFBUZZ_SUBSET harfbuzz-subset)
        if(HARFBUZZ_SUBSET_FOUND)
            set(HARFBUZZ_INCLUDE_DIRS ${HARFBUZZ_SUBSET_INCLUDE_DIRS})
            # Link both libraries - subset needs base library symbols
            set(HARFBUZZ_LIBRARIES
                "/usr/local/lib/libharfbuzz-subset.a"
                "/usr/local/lib/libharfbuzz.a"
            )
            set(HARFBUZZ_FOUND TRUE)
        endif()
    endif()

    if(NOT HARFBUZZ_FOUND)
        # Fallback to manual search
        find_path(HARFBUZZ_INCLUDE_DIRS
            NAMES hb.h
            PATHS /usr/local/include/harfbuzz /usr/include/harfbuzz
        )
        find_library(HARFBUZZ_LIBRARY
            NAMES harfbuzz
            PATHS /usr/local/lib /usr/lib
        )
        find_library(HARFBUZZ_SUBSET_LIBRARY
            NAMES harfbuzz-subset
            PATHS /usr/local/lib /usr/lib
        )
        if(HARFBUZZ_INCLUDE_DIRS AND HARFBUZZ_LIBRARY AND HARFBUZZ_SUBSET_LIBRARY)
            # Link both libraries - subset needs base library symbols
            set(HARFBUZZ_LIBRARIES
                ${HARFBUZZ_SUBSET_LIBRARY}
                ${HARFBUZZ_LIBRARY}
            )
            set(HARFBUZZ_FOUND TRUE)
        endif()
    endif()
endif()

if(NOT HARFBUZZ_FOUND)
    message(FATAL_ERROR "HarfBuzz not found. Please install HarfBuzz development packages.")
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
        # For MinGW, link everything statically except Windows system DLLs
        # Static libs must come before -static-libgcc/-static-libstdc++ flags
        target_link_libraries(fontsubsetting
            -Wl,--allow-multiple-definition
            -Wl,--exclude-libs,ALL
            ${HARFBUZZ_LIBRARIES}
            -Wl,-Bstatic
            -lwinpthread
            -static-libgcc
            -static-libstdc++
            -Wl,-Bdynamic
            -lkernel32
            -luser32
        )
        # Set link flags to avoid undefined symbols
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
    # Linux - link HarfBuzz statically
    # Force inclusion of all needed symbols by putting static libs at the end
    target_link_libraries(fontsubsetting
        ${HARFBUZZ_LIBRARIES}
    )
    # Ensure we don't have undefined symbols at link time
    # Allow multiple definitions due to overlap between libharfbuzz-subset and libharfbuzz
    # Use version script to hide HarfBuzz symbols and only export JNI functions
    set_target_properties(fontsubsetting PROPERTIES
        LINK_FLAGS "-Wl,--no-undefined -Wl,--allow-multiple-definition -Wl,--version-script=${CMAKE_CURRENT_SOURCE_DIR}/fontsubsetting.map"
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