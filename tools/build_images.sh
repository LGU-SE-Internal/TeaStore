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
#   -b             Build only (no push, for local testing)
#   -m             Build Maven first
#   -h             Show this help message
#
# Examples:
#   ./build_images.sh -p                    # Build and push with default registry
#   ./build_images.sh -r myregistry.com -p  # Use custom registry
#   ./build_images.sh -t v1.0.0 -p          # Build with specific tag
#   ./build_images.sh -b                    # Build locally only
#

set -e

# Default configuration
REGISTRY="${REGISTRY:-10.10.10.240/library}"
TAG="${TAG:-latest}"
PUSH_IMAGES=false
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
    head -25 "$0" | tail -21
    exit 0
}

# Parse command line arguments
while getopts 'r:t:pbmh' flag; do
    case "${flag}" in
        r) REGISTRY="${OPTARG}" ;;
        t) TAG="${OPTARG}" ;;
        p) PUSH_IMAGES=true ;;
        b) PUSH_IMAGES=false ;;
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
    
    docker build -t "${full_image}" -f "${context_path}/${dockerfile}" "${context_path}"
    
    if [ "$PUSH_IMAGES" = true ]; then
        print_info "Pushing ${full_image}..."
        docker push "${full_image}"
    fi
    
    print_success "Built ${full_image}"
}

# Backup original Dockerfiles that reference the base image
backup_dockerfiles() {
    print_info "Backing up Dockerfiles..."
    for dockerfile in "${PROJECT_ROOT}"/services/tools.descartes.teastore.*/Dockerfile; do
        if [ -f "$dockerfile" ]; then
            cp "$dockerfile" "${dockerfile}.bak"
        fi
    done
    # Also backup jmeter Dockerfile which uses base image
    if [ -f "${PROJECT_ROOT}/utilities/tools.descartes.teastore.jmeter/Dockerfile" ]; then
        cp "${PROJECT_ROOT}/utilities/tools.descartes.teastore.jmeter/Dockerfile" \
           "${PROJECT_ROOT}/utilities/tools.descartes.teastore.jmeter/Dockerfile.bak"
    fi
}

# Update Dockerfiles to use custom registry
update_dockerfiles() {
    print_info "Updating Dockerfiles to use registry: ${REGISTRY}..."
    for dockerfile in "${PROJECT_ROOT}"/services/tools.descartes.teastore.*/Dockerfile; do
        if [ -f "$dockerfile" ]; then
            sed -i "s|FROM descartesresearch/teastore-base|FROM ${REGISTRY}/teastore-base:${TAG}|g" "$dockerfile"
        fi
    done
    # Update jmeter Dockerfile
    if [ -f "${PROJECT_ROOT}/utilities/tools.descartes.teastore.jmeter/Dockerfile" ]; then
        sed -i "s|FROM descartesresearch/teastore-base|FROM ${REGISTRY}/teastore-base:${TAG}|g" \
            "${PROJECT_ROOT}/utilities/tools.descartes.teastore.jmeter/Dockerfile"
    fi
}

# Restore original Dockerfiles
restore_dockerfiles() {
    print_info "Restoring original Dockerfiles..."
    for dockerfile in "${PROJECT_ROOT}"/services/tools.descartes.teastore.*/Dockerfile.bak; do
        if [ -f "$dockerfile" ]; then
            mv "$dockerfile" "${dockerfile%.bak}"
        fi
    done
    if [ -f "${PROJECT_ROOT}/utilities/tools.descartes.teastore.jmeter/Dockerfile.bak" ]; then
        mv "${PROJECT_ROOT}/utilities/tools.descartes.teastore.jmeter/Dockerfile.bak" \
           "${PROJECT_ROOT}/utilities/tools.descartes.teastore.jmeter/Dockerfile"
    fi
}

# Set up cleanup trap
cleanup() {
    restore_dockerfiles
}
trap cleanup EXIT

# Main build process
print_info "Starting Docker builds..."

# Backup and update Dockerfiles
backup_dockerfiles
update_dockerfiles

# 1. Build base images first (no dependencies)
print_info "======== Building Base Images ========"
build_image "${PROJECT_ROOT}/utilities/tools.descartes.teastore.database" "teastore-db"
build_image "${PROJECT_ROOT}/utilities/tools.descartes.teastore.dockerbase" "teastore-base"
build_image "${PROJECT_ROOT}/utilities/tools.descartes.teastore.kieker.rabbitmq" "teastore-kieker-rabbitmq"

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
echo "  - ${REGISTRY}/teastore-db:${TAG}"
echo "  - ${REGISTRY}/teastore-base:${TAG}"
echo "  - ${REGISTRY}/teastore-kieker-rabbitmq:${TAG}"
echo "  - ${REGISTRY}/teastore-registry:${TAG}"
echo "  - ${REGISTRY}/teastore-persistence:${TAG}"
echo "  - ${REGISTRY}/teastore-image:${TAG}"
echo "  - ${REGISTRY}/teastore-webui:${TAG}"
echo "  - ${REGISTRY}/teastore-auth:${TAG}"
echo "  - ${REGISTRY}/teastore-recommender:${TAG}"
echo "  - ${REGISTRY}/teastore-jmeter:${TAG}"
echo "  - ${REGISTRY}/teastore-httploadgen:${TAG}"
echo "  - ${REGISTRY}/teastore-httploaddirector:${TAG}"

if [ "$PUSH_IMAGES" = true ]; then
    print_success "All images pushed to ${REGISTRY}"
else
    print_warning "Images were built but NOT pushed. Use -p flag to push."
fi
