# CMake toolchain file for cross-compiling to macOS x86_64 from Linux using Zig

set(CMAKE_SYSTEM_NAME Darwin)
set(CMAKE_SYSTEM_PROCESSOR x86_64)

# Use pre-created wrapper scripts in Docker, otherwise create them
if(EXISTS "/usr/local/bin/zig-cc-x86_64-macos")
    set(CMAKE_C_COMPILER "/usr/local/bin/zig-cc-x86_64-macos")
    set(CMAKE_CXX_COMPILER "/usr/local/bin/zig-c++-x86_64-macos")
    set(CMAKE_AR "/usr/local/bin/zig-ar")
    set(CMAKE_RANLIB "/usr/local/bin/zig-ranlib")
else()
    # Create wrapper scripts for standalone use
    set(ZIG_WRAPPER_DIR "${CMAKE_CURRENT_BINARY_DIR}/zig-wrappers")
    file(MAKE_DIRECTORY "${ZIG_WRAPPER_DIR}")
    
    file(WRITE "${ZIG_WRAPPER_DIR}/zig-cc-x86_64-macos" "#!/bin/bash\nexec zig cc -target x86_64-macos-none \"$@\"\n")
    file(WRITE "${ZIG_WRAPPER_DIR}/zig-c++-x86_64-macos" "#!/bin/bash\nexec zig c++ -target x86_64-macos-none \"$@\"\n")
    file(WRITE "${ZIG_WRAPPER_DIR}/zig-ar" "#!/bin/bash\nzig ar \"$@\"\n")
    file(WRITE "${ZIG_WRAPPER_DIR}/zig-ranlib" "#!/bin/bash\nzig ranlib \"$@\"\n")
    
    execute_process(COMMAND chmod +x "${ZIG_WRAPPER_DIR}/zig-cc-x86_64-macos")
    execute_process(COMMAND chmod +x "${ZIG_WRAPPER_DIR}/zig-c++-x86_64-macos")
    execute_process(COMMAND chmod +x "${ZIG_WRAPPER_DIR}/zig-ar")
    execute_process(COMMAND chmod +x "${ZIG_WRAPPER_DIR}/zig-ranlib")
    
    set(CMAKE_C_COMPILER "${ZIG_WRAPPER_DIR}/zig-cc-x86_64-macos")
    set(CMAKE_CXX_COMPILER "${ZIG_WRAPPER_DIR}/zig-c++-x86_64-macos")
    set(CMAKE_AR "${ZIG_WRAPPER_DIR}/zig-ar")
    set(CMAKE_RANLIB "${ZIG_WRAPPER_DIR}/zig-ranlib")
endif()

# Set compiler AR and RANLIB variables
set(CMAKE_C_COMPILER_AR ${CMAKE_AR})
set(CMAKE_CXX_COMPILER_AR ${CMAKE_AR})
set(CMAKE_C_COMPILER_RANLIB ${CMAKE_RANLIB})
set(CMAKE_CXX_COMPILER_RANLIB ${CMAKE_RANLIB})

# macOS specific settings
set(CMAKE_OSX_ARCHITECTURES x86_64)
set(CMAKE_OSX_DEPLOYMENT_TARGET 10.12)

# Shared library settings for macOS
set(CMAKE_SHARED_LIBRARY_PREFIX "lib")
set(CMAKE_SHARED_LIBRARY_SUFFIX ".dylib")

# Custom link commands for Zig
set(CMAKE_C_LINK_EXECUTABLE "<CMAKE_C_COMPILER> <FLAGS> <CMAKE_C_LINK_FLAGS> <LINK_FLAGS> <OBJECTS> -o <TARGET> <LINK_LIBRARIES>")
set(CMAKE_CXX_LINK_EXECUTABLE "<CMAKE_CXX_COMPILER> <FLAGS> <CMAKE_CXX_LINK_FLAGS> <LINK_FLAGS> <OBJECTS> -o <TARGET> <LINK_LIBRARIES>")

set(CMAKE_C_CREATE_SHARED_LIBRARY "<CMAKE_C_COMPILER> -dynamiclib <CMAKE_SHARED_LIBRARY_C_FLAGS> <LANGUAGE_COMPILE_FLAGS> <LINK_FLAGS> <CMAKE_SHARED_LIBRARY_CREATE_C_FLAGS> <SONAME_FLAG><TARGET_SONAME> -o <TARGET> <OBJECTS> <LINK_LIBRARIES>")
set(CMAKE_CXX_CREATE_SHARED_LIBRARY "<CMAKE_CXX_COMPILER> -dynamiclib <CMAKE_SHARED_LIBRARY_CXX_FLAGS> <LANGUAGE_COMPILE_FLAGS> <LINK_FLAGS> <CMAKE_SHARED_LIBRARY_CREATE_CXX_FLAGS> <SONAME_FLAG><TARGET_SONAME> -o <TARGET> <OBJECTS> <LINK_LIBRARIES>")

# Optimization flags for Release builds
set(CMAKE_C_FLAGS_RELEASE "-O3 -DNDEBUG -ffunction-sections -fdata-sections -fvisibility=hidden")
set(CMAKE_CXX_FLAGS_RELEASE "-O3 -DNDEBUG -ffunction-sections -fdata-sections -fvisibility=hidden -fvisibility-inlines-hidden")

# macOS-specific optimization flags
set(CMAKE_SHARED_LINKER_FLAGS_RELEASE "-Wl,-dead_strip")

# Disable CMake's compiler tests for cross-compilation
set(CMAKE_C_COMPILER_WORKS 1)
set(CMAKE_CXX_COMPILER_WORKS 1)
set(CMAKE_C_ABI_COMPILED 1)
set(CMAKE_CXX_ABI_COMPILED 1)