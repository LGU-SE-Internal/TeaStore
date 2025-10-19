# TeaStore HTTP Load Generator

This directory contains the Docker images and configurations for the TeaStore HTTP Load Generator, which consists of two components:

## Architecture

The HTTP Load Generator uses a **director-worker architecture**:

1. **LoadGen (Worker)**: The load generator container that actually sends HTTP requests
2. **Director (Controller)**: The director container that configures the test, controls the load generator, and collects results

## Components

### 1. LoadGen Container (`Dockerfile.loadgen`)
- Runs in load generator mode
- Listens on port 2223 for director commands
- Generates HTTP requests based on director instructions

### 2. Director Container (`Dockerfile.director`)
- Runs as a Kubernetes Job
- Reads load profile (CSV) and request script (Lua)
- Connects to LoadGen container
- Controls test execution and collects results

## Files

- `Dockerfile.loadgen` - Docker image for load generator (worker)
- `Dockerfile.director` - Docker image for director (controller)
- `start-director.sh` - Entrypoint script for director with configuration
- `profiles/` - Load intensity profiles (CSV files)
  - `increasingLowIntensity.csv` - Up to 100 req/s
  - `increasingMedIntensity.csv` - Up to 1000 req/s
  - `increasingHighIntensity.csv` - Up to 2000 req/s
- `scripts/` - Request definition scripts (Lua)
  - `teastore_browse.lua` - Browse profile (recommended, no DB changes)
  - `teastore_buy.lua` - Buy profile (modifies DB)

## Building Docker Images

```bash
# Build LoadGen image
cd /home/nn/workspace/TeaStore/utilities/tools.descartes.teastore.httploadgenerator
docker build -f Dockerfile.loadgen -t descartesresearch/teastore-httploadgen:latest .

# Build Director image
docker build -f Dockerfile.director -t descartesresearch/teastore-httploaddirector:latest .
```

## Helm Deployment

The load generator can be deployed via Helm. Configuration is in `examples/helm/values.yaml`:

```yaml
# Enable load generator
httploadgen:
  enabled: true
  replicaCount: 1
  
# Enable and configure director
httploaddirector:
  enabled: true
  loadProfile: "/director/profiles/increasingLowIntensity.csv"
  luaScript: "/director/scripts/teastore_browse.lua"
  numThreads: 256
```

Deploy with:
```bash
helm upgrade --install teastore ./examples/helm \
  --namespace teastore \
  --create-namespace \
  --set httploadgen.enabled=true \
  --set httploaddirector.enabled=true
```

## Configuration Options

### Director Environment Variables

All configurable via Helm values or environment variables:

- `LOADGEN_HOST` - LoadGen service hostname (default: `loadgen`)
- `LOADGEN_PORT` - LoadGen service port (default: `2223`)
- `LOAD_PROFILE` - Path to load intensity CSV file
- `LUA_SCRIPT` - Path to Lua request script
- `OUTPUT_FILE` - Path for results output
- `NUM_THREADS` - Number of load generation threads
- `DOCKER_MONITOR_ENABLED` - Enable Docker API monitoring (default: `false`)
- `DOCKER_MONITOR_TARGETS` - Docker monitoring targets (format: `<IP>:<PORT>:<CONTAINER_ID>`)

### Load Profiles

Choose a profile based on your needs:
- **Low Intensity**: 100 req/s max - Good for getting started
- **Medium Intensity**: 1000 req/s max - Standard stress test
- **High Intensity**: 2000 req/s max - Heavy stress test

### Request Scripts

- **teastore_browse.lua** (Recommended): Emulates browsing and cart updates without database changes
- **teastore_buy.lua**: Emulates purchases, modifies database (less stable)

## Viewing Results

Results are written to `/director/results/output.csv` inside the director container.

To retrieve results:
```bash
# Get the director job pod name
kubectl get pods -n teastore | grep httploaddirector

# Copy results from the pod
kubectl cp teastore/<director-pod-name>:/director/results/output.csv ./output.csv
```

## Notes

- The LoadGen container must be running before the Director starts
- The Director runs as a Kubernetes Job (runs once and completes)
- To run multiple tests, delete and recreate the Director Job
- The Lua scripts are configured to target `teastore-webui:8080` service
