#!/bin/bash

# Build all native libraries using Docker for reproducible cross-compilation.
#
# We always invoke `docker build` and rely on BuildKit's layer cache to skip
# unchanged work — that's both faster and more correct than the mtime-based
# sentinel we used to keep here.

set -e

SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
REPO_ROOT="$( cd "${SCRIPT_DIR}/.." && pwd )"
IMAGE_NAME="fontsubsetting-cross-compiler"

# HarfBuzz version is defined once in repo-root gradle.properties; pass it
# through to the Docker build so the plugin and runtime can't drift.
HARFBUZZ_VERSION=$(grep '^harfbuzzVersion=' "${REPO_ROOT}/gradle.properties" | cut -d= -f2)
if [ -z "$HARFBUZZ_VERSION" ]; then
    echo "ERROR: harfbuzzVersion not found in ${REPO_ROOT}/gradle.properties" >&2
    exit 1
fi

echo "Building Docker image for cross-compilation (HarfBuzz ${HARFBUZZ_VERSION})..."
DOCKER_BUILDKIT=1 docker build \
    -f "${SCRIPT_DIR}/Dockerfile.cross-compile" \
    -t "${IMAGE_NAME}" \
    --build-arg "HARFBUZZ_VERSION=${HARFBUZZ_VERSION}" \
    "${SCRIPT_DIR}"

echo ""
echo "Running cross-compilation in Docker..."
# Mount the repo root so plugin sources are visible, and preserve the plugin's
# build directory across runs. --user maps to the host UID/GID so build outputs
# aren't root-owned (matters on Linux/WSL hosts; harmless on Docker Desktop).
# HOME=/tmp keeps tools that touch $HOME (CMake, ninja) happy when the mapped
# user has no /etc/passwd entry.
docker run --rm \
    --user "$(id -u):$(id -g)" \
    -e HOME=/tmp \
    -v "${REPO_ROOT}:/workspace" \
    -v "${SCRIPT_DIR}/build:/workspace/plugin/build" \
    -w /workspace/plugin \
    "${IMAGE_NAME}" \
    bash -c "./build-all-natives.sh"

echo ""
echo "Build complete!"
echo ""
echo "Plugin libraries:  ${SCRIPT_DIR}/src/main/resources/native/"
ls -la "${SCRIPT_DIR}/src/main/resources/native/"/* 2>/dev/null || echo "  (no libraries found)"
