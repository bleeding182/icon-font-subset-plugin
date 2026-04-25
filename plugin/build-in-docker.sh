#!/bin/bash

# Build all native libraries using Docker for reproducible cross-compilation

set -e

SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
IMAGE_NAME="fontsubsetting-cross-compiler"

# Check if image exists and if Dockerfile has changed
BUILD_IMAGE=false
if [ -z "$(docker images -q $IMAGE_NAME 2> /dev/null)" ]; then
    echo "Docker image not found, building..."
    BUILD_IMAGE=true
else
    # Check if Dockerfile is newer than the image
    if [ "${SCRIPT_DIR}/Dockerfile.cross-compile" -nt "${SCRIPT_DIR}/.docker-image-id" ] 2>/dev/null; then
        echo "Dockerfile has changed, rebuilding image..."
        BUILD_IMAGE=true
    else
        echo "Using existing Docker image..."
    fi
fi

if [ "$BUILD_IMAGE" = true ]; then
    echo "Building Docker image for cross-compilation..."
    docker build -f "${SCRIPT_DIR}/Dockerfile.cross-compile" -t $IMAGE_NAME "${SCRIPT_DIR}"
    docker images -q $IMAGE_NAME > "${SCRIPT_DIR}/.docker-image-id"
fi

REPO_ROOT="$( cd "${SCRIPT_DIR}/.." && pwd )"

echo ""
echo "Running cross-compilation in Docker..."
# Mount the repo root so plugin sources are visible, and preserve the plugin's
# build directory across runs.
docker run --rm \
    -v "${REPO_ROOT}:/workspace" \
    -v "${SCRIPT_DIR}/build:/workspace/plugin/build" \
    -w /workspace/plugin \
    $IMAGE_NAME \
    bash -c "
        mkdir -p /workspace/plugin/build/native-cross
        mkdir -p /workspace/plugin/src/main/resources/native

        ./build-all-natives.sh
    "

echo ""
echo "Build complete!"
echo ""
echo "Plugin libraries:  ${SCRIPT_DIR}/src/main/resources/native/"
ls -la "${SCRIPT_DIR}/src/main/resources/native/"/* 2>/dev/null || echo "  (no libraries found)"