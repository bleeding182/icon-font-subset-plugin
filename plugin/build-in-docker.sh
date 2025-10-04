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

echo ""
echo "Running cross-compilation in Docker..."
# Mount the workspace and preserve build directory between runs
docker run --rm \
    -v "${SCRIPT_DIR}:/workspace" \
    -v "${SCRIPT_DIR}/build:/workspace/build" \
    -w /workspace \
    $IMAGE_NAME \
    bash -c "
        # Ensure build directories exist
        mkdir -p /workspace/build/native-cross
        mkdir -p /workspace/src/main/resources/native
        
        # Run the build script
        ./build-all-natives.sh
    "

echo ""
echo "Build complete! Native libraries are in:"
echo "${SCRIPT_DIR}/src/main/resources/native/"
ls -la "${SCRIPT_DIR}/src/main/resources/native/"/* 2>/dev/null || echo "No libraries found"