#!/bin/bash
# Build script for HTTP Load Generator Docker images

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

echo "Building TeaStore HTTP Load Generator Docker images..."

# Build LoadGen image
echo "Building LoadGen image..."
docker build -f Dockerfile.loadgen -t 10.10.10.240/library/teastore-httploadgen:latest .
echo "✓ LoadGen image built successfully"

# Build Director image
echo "Building Director image..."
docker build -f Dockerfile.director -t 10.10.10.240/library/teastore-httploaddirector:latest .
echo "✓ Director image built successfully"

echo ""
echo "All images built successfully!"
echo ""
echo "To push to registry:"
echo "  docker push 10.10.10.240/library/teastore-httploadgen:latest"
echo "  docker push 10.10.10.240/library/teastore-httploaddirector:latest"
echo ""
echo "To deploy with Helm:"
echo "  helm upgrade --install teastore ../../examples/helm \\"
echo "    --namespace teastore \\"
echo "    --create-namespace \\"
echo "    --set httploadgen.enabled=true \\"
echo "    --set httploaddirector.enabled=true"
