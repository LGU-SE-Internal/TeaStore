#!/bin/bash
#
# TeaStore Docker Build Script
# Builds all Docker images and pushes to custom container registry
#
# Usage:
#   ./build_images.sh [OPTIONS]
#
# Options:
#   -r REGISTRY    Container registry (default: 10.10.10.240/library)
#   -t TAG         Image tag (default: latest)
#   -p             Push images after build
#   -b             Build base image (skip if not specified)
#   -m             Build Maven first
#   -h             Show this help message
#
# Examples:
#   ./build_images.sh -p                    # Build services and push (no base)
#   ./build_images.sh -b -p                 # Build base + services and push
#   ./build_images.sh -r myregistry.com -p  # Use custom registry
#   ./build_images.sh -t v1.0.0 -b -p       # Build all with specific tag
#   ./build_images.sh -m -b -p              # Maven build + base + services
#

set -e

# Default configuration
REGISTRY="${REGISTRY:-10.10.10.240/library}"
TAG="${TAG:-latest}"
PUSH_IMAGES=false
BUILD_BASE=false
BUILD_MAVEN=false
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Print functions
print_info() {
    echo -e "${BLUE}[INFO]${NC} $1"
}

print_success() {
    echo -e "${GREEN}[SUCCESS]${NC} $1"
}

print_warning() {
    echo -e "${YELLOW}[WARNING]${NC} $1"
}

print_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

print_usage() {
    head -26 "$0" | tail -22
    exit 0
}

# Parse command line arguments
while getopts 'r:t:pbmh' flag; do
    case "${flag}" in
        r) REGISTRY="${OPTARG}" ;;
        t) TAG="${OPTARG}" ;;
        p) PUSH_IMAGES=true ;;
        b) BUILD_BASE=true ;;
        m) BUILD_MAVEN=true ;;
        h) print_usage ;;
        *) print_usage ;;
    esac
done

print_info "========================================"
print_info "TeaStore Docker Build Script"
print_info "========================================"
print_info "Registry: ${REGISTRY}"
print_info "Tag: ${TAG}"
print_info "Build Base: ${BUILD_BASE}"
print_info "Push Images: ${PUSH_IMAGES}"
print_info "Build Maven: ${BUILD_MAVEN}"
print_info "========================================"

# Build Maven project if requested
if [ "$BUILD_MAVEN" = true ]; then
    print_info "Building Maven project..."
    cd "${PROJECT_ROOT}"
    mvn clean package -DskipTests
    print_success "Maven build complete"
fi

# Function to build and optionally push an image
build_image() {
    local context_path="$1"
    local image_name="$2"
    local dockerfile="${3:-Dockerfile}"
    local full_image="${REGISTRY}/${image_name}:${TAG}"
    
    print_info "Building ${image_name}..."
    
    if [ ! -f "${context_path}/${dockerfile}" ]; then
        print_error "Dockerfile not found: ${context_path}/${dockerfile}"
        return 1
    fi
    
    docker build -t "${full_image}" \
        --build-arg BASE_IMAGE="${REGISTRY}/teastore-base:${TAG}" \
        -f "${context_path}/${dockerfile}" "${context_path}"
    
    if [ "$PUSH_IMAGES" = true ]; then
        print_info "Pushing ${full_image}..."
        docker push "${full_image}"
    fi
    
    print_success "Built ${full_image}"
}

# Main build process
print_info "Starting Docker builds..."

# 1. Build base images first (only if -b flag is set)
if [ "$BUILD_BASE" = true ]; then
    print_info "======== Building Base Images ========"
    build_image "${PROJECT_ROOT}/utilities/tools.descartes.teastore.database" "teastore-db"
    build_image "${PROJECT_ROOT}/utilities/tools.descartes.teastore.dockerbase" "teastore-base"
else
    print_warning "Skipping base image builds. Use -b flag to build base images."
fi

# 2. Build service images (depend on base image)
print_info "======== Building Service Images ========"
build_image "${PROJECT_ROOT}/services/tools.descartes.teastore.registry" "teastore-registry"
build_image "${PROJECT_ROOT}/services/tools.descartes.teastore.persistence" "teastore-persistence"
build_image "${PROJECT_ROOT}/services/tools.descartes.teastore.image" "teastore-image"
build_image "${PROJECT_ROOT}/services/tools.descartes.teastore.webui" "teastore-webui"
build_image "${PROJECT_ROOT}/services/tools.descartes.teastore.auth" "teastore-auth"
build_image "${PROJECT_ROOT}/services/tools.descartes.teastore.recommender" "teastore-recommender"

# 3. Build utility images
print_info "======== Building Utility Images ========"
build_image "${PROJECT_ROOT}/utilities/tools.descartes.teastore.jmeter" "teastore-jmeter"

# 4. Build HTTP load generator images (if needed)
if [ -f "${PROJECT_ROOT}/utilities/tools.descartes.teastore.httploadgenerator/Dockerfile.loadgen" ]; then
    print_info "======== Building HTTP Load Generator Images ========"
    build_image "${PROJECT_ROOT}/utilities/tools.descartes.teastore.httploadgenerator" "teastore-httploadgen" "Dockerfile.loadgen"
    build_image "${PROJECT_ROOT}/utilities/tools.descartes.teastore.httploadgenerator" "teastore-httploaddirector" "Dockerfile.director"
fi

print_info "========================================"
print_success "All images built successfully!"
print_info "========================================"

# Print image list
print_info "Built images:"
if [ "$BUILD_BASE" = true ]; then
    echo "  - ${REGISTRY}/teastore-db:${TAG}"
    echo "  - ${REGISTRY}/teastore-base:${TAG}"
fi
echo "  - ${REGISTRY}/teastore-registry:${TAG}"
echo "  - ${REGISTRY}/teastore-persistence:${TAG}"
echo "  - ${REGISTRY}/teastore-image:${TAG}"
echo "  - ${REGISTRY}/teastore-webui:${TAG}"
echo "  - ${REGISTRY}/teastore-auth:${TAG}"
echo "  - ${REGISTRY}/teastore-recommender:${TAG}"
echo "  - ${REGISTRY}/teastore-jmeter:${TAG}"
if [ -f "${PROJECT_ROOT}/utilities/tools.descartes.teastore.httploadgenerator/Dockerfile.loadgen" ]; then
    echo "  - ${REGISTRY}/teastore-httploadgen:${TAG}"
    echo "  - ${REGISTRY}/teastore-httploaddirector:${TAG}"
fi

if [ "$PUSH_IMAGES" = true ]; then
    print_success "All images pushed to ${REGISTRY}"
else
    print_warning "Images were built but NOT pushed. Use -p flag to push."
fi
