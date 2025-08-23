#!/bin/bash

# Build all native libraries for all platforms from Linux
# This script can be run directly or inside the Docker container

set -e

SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
SRC_DIR="${SCRIPT_DIR}/src/main/cpp"
BUILD_ROOT="${SCRIPT_DIR}/build/native-cross"
OUTPUT_DIR="${SCRIPT_DIR}/src/main/resources/native"

# Ensure JAVA_HOME is set
if [ -z "$JAVA_HOME" ]; then
    if [ -d "/usr/lib/jvm/java-17-openjdk-amd64" ]; then
        export JAVA_HOME="/usr/lib/jvm/java-17-openjdk-amd64"
    elif [ -d "/usr/lib/jvm/java-11-openjdk-amd64" ]; then
        export JAVA_HOME="/usr/lib/jvm/java-11-openjdk-amd64"
    else
        echo "ERROR: JAVA_HOME not set and could not find Java installation"
        exit 1
    fi
fi

echo "Using JAVA_HOME: $JAVA_HOME"

echo "========================================="
echo "Building native libraries for all platforms"
echo "========================================="

# Create output directories
mkdir -p "${OUTPUT_DIR}/linux-x86_64"
mkdir -p "${OUTPUT_DIR}/windows-x86_64"
mkdir -p "${OUTPUT_DIR}/darwin-x86_64"
mkdir -p "${OUTPUT_DIR}/darwin-aarch64"

# Function to build for a specific platform
build_platform() {
    local PLATFORM=$1
    local TOOLCHAIN=$2
    local OUTPUT_NAME=$3
    local TARGET_DIR=$4
    
    echo ""
    echo "Building for ${PLATFORM}..."
    echo "-----------------------------------------"
    
    local BUILD_DIR="${BUILD_ROOT}/${PLATFORM}"
    mkdir -p "${BUILD_DIR}"
    cd "${BUILD_DIR}"
    
    # Configure with CMake
    if [ -z "${TOOLCHAIN}" ]; then
        # Native build (Linux)
        cmake "${SRC_DIR}" \
            -DCMAKE_BUILD_TYPE=Release \
            -DCMAKE_POSITION_INDEPENDENT_CODE=ON \
            -G Ninja
    else
        # Cross-compilation
        cmake "${SRC_DIR}" \
            -DCMAKE_BUILD_TYPE=Release \
            -DCMAKE_POSITION_INDEPENDENT_CODE=ON \
            -DCMAKE_TOOLCHAIN_FILE="${SCRIPT_DIR}/cmake/toolchains/${TOOLCHAIN}" \
            -G Ninja
    fi
    
    # Build
    ninja
    
    # Strip the binary if not already done by linker (belt and suspenders approach)
    if [ -f "${OUTPUT_NAME}" ]; then
        # Strip symbols for even smaller size (if not already stripped by linker)
        if [ "${PLATFORM}" = "linux-x86_64" ]; then
            strip --strip-unneeded "${OUTPUT_NAME}" 2>/dev/null || true
        elif [ "${PLATFORM}" = "windows-x86_64" ]; then
            x86_64-w64-mingw32-strip --strip-unneeded "${OUTPUT_NAME}" 2>/dev/null || true
        fi
        # Note: macOS binaries cross-compiled with Zig may not strip properly with Linux tools
        
        cp "${OUTPUT_NAME}" "${OUTPUT_DIR}/${TARGET_DIR}/"
        echo "✓ Built ${OUTPUT_NAME} for ${PLATFORM}"
        echo "  Size: $(du -h "${OUTPUT_NAME}" | cut -f1)"
    else
        echo "✗ Failed to build for ${PLATFORM} - library not found"
        return 1
    fi
}

# Build for Linux x86_64 (native)
build_platform "linux-x86_64" "" "libfontsubsetting.so" "linux-x86_64"

# Build for Windows x86_64 (MinGW cross-compilation)
build_platform "windows-x86_64" "windows-x86_64.cmake" "fontsubsetting.dll" "windows-x86_64"

# Build for macOS x86_64 (Zig cross-compilation)
build_platform "darwin-x86_64" "darwin-x86_64.cmake" "libfontsubsetting.dylib" "darwin-x86_64"

# Build for macOS ARM64 (Zig cross-compilation)
build_platform "darwin-aarch64" "darwin-aarch64.cmake" "libfontsubsetting.dylib" "darwin-aarch64"

echo ""
echo "========================================="
echo "Build Summary"
echo "========================================="
echo "Output directory: ${OUTPUT_DIR}"
echo ""
echo "Libraries built:"
find "${OUTPUT_DIR}" -type f \( -name "*.so" -o -name "*.dll" -o -name "*.dylib" \) | while read -r lib; do
    echo "  - $(basename $(dirname "$lib"))/$(basename "$lib") ($(du -h "$lib" | cut -f1))"
done

echo ""
echo "Done!"