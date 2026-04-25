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
    # Try Ubuntu paths
    if [ -d "/usr/lib/jvm/default-java" ]; then
        export JAVA_HOME="/usr/lib/jvm/default-java"
    elif [ -d "/usr/lib/jvm/java-17-openjdk-amd64" ]; then
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
    mkdir -p "${BUILD_DIR}" "${OUTPUT_DIR}/${TARGET_DIR}"
    cd "${BUILD_DIR}"

    cmake "${SRC_DIR}" \
        -DCMAKE_BUILD_TYPE=Release \
        -DCMAKE_POSITION_INDEPENDENT_CODE=ON \
        -DCMAKE_TOOLCHAIN_FILE="${SCRIPT_DIR}/cmake/toolchains/${TOOLCHAIN}" \
        -G Ninja

    ninja

    if [ ! -f "${OUTPUT_NAME}" ]; then
        echo "✗ Failed to build for ${PLATFORM} - library not found"
        return 1
    fi

    # Belt-and-suspenders strip for Windows. Linux gets -Wl,-s and macOS
    # gets -Wl,-S -Wl,-x via CMakeLists.txt, so they're already stripped at
    # link time.
    if [ "${PLATFORM}" = "windows-x86_64" ]; then
        x86_64-w64-mingw32-strip --strip-unneeded "${OUTPUT_NAME}" 2>/dev/null || true
    fi

    cp "${OUTPUT_NAME}" "${OUTPUT_DIR}/${TARGET_DIR}/"
    echo "✓ Built ${OUTPUT_NAME} for ${PLATFORM}"
    echo "  Size: $(du -h "${OUTPUT_NAME}" | cut -f1)"
}

build_platform "linux-x86_64"   "linux-x86_64.cmake"   "libfontsubsetting.so"    "linux-x86_64"
build_platform "linux-aarch64"  "linux-aarch64.cmake"  "libfontsubsetting.so"    "linux-aarch64"
build_platform "windows-x86_64" "windows-x86_64.cmake" "fontsubsetting.dll"      "windows-x86_64"
build_platform "darwin-x86_64"  "darwin-x86_64.cmake"  "libfontsubsetting.dylib" "darwin-x86_64"
build_platform "darwin-aarch64" "darwin-aarch64.cmake" "libfontsubsetting.dylib" "darwin-aarch64"

echo ""
echo "========================================="
echo "Build Summary"
echo "========================================="
echo "Plugin output:  ${OUTPUT_DIR}"
echo ""
echo "Libraries built:"
find "${OUTPUT_DIR}" -type f \( -name "*.so" -o -name "*.dll" -o -name "*.dylib" \) | while read -r lib; do
    echo "  - $(basename $(dirname "$lib"))/$(basename "$lib") ($(du -h "$lib" | cut -f1))"
done

echo ""
echo "Done!"