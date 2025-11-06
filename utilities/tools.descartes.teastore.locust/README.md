# TeaStore Locust Load Generator

This directory contains a Docker image for running Locust load tests against the TeaStore application.

## Overview

[Locust](https://locust.io/) is an easy-to-use, scriptable, and scalable performance testing tool written in Python. This Docker image packages Locust with a pre-configured test script (`locustfile.py`) designed to simulate realistic user behavior on the TeaStore web application.

## Building the Docker Image

To build the Docker image locally:

```bash
docker build -t teastore-locust .
```

## Running the Container

### Headless Mode (Default)

The container runs in headless mode by default, which is suitable for automated testing:

```bash
docker run -e TARGET_HOST=http://teastore-webui:8080 \
           -e USERS=10 \
           -e SPAWN_RATE=1 \
           -e RUN_TIME=5m \
           teastore-locust
```

### With Web UI

To run with the Locust web UI for interactive testing:

```bash
docker run -p 8089:8089 \
           -e TARGET_HOST=http://teastore-webui:8080 \
           teastore-locust \
           locust -f /locust/locustfile.py --host=http://teastore-webui:8080
```

Then open your browser to http://localhost:8089

## Configuration

The following environment variables can be configured:

- `TARGET_HOST`: The URL of the TeaStore WebUI service (default: `http://teastore-webui:8080`)
- `USERS`: Number of concurrent users to simulate (default: `10`)
- `SPAWN_RATE`: Rate at which users are spawned, in users per second (default: `1`)
- `RUN_TIME`: How long to run the test (e.g., `5m`, `1h`, `300s`) (default: `5m`)

## Test Scenario

The test script simulates realistic user behavior with the following actions:

1. Visit the home page
2. Login with a random user (userid between 1-99)
3. Browse random categories and products (2-4 iterations)
4. Add products to cart
5. 50% chance to complete purchase
6. Visit user profile
7. Logout

## Using with Kubernetes/Helm

This image is integrated into the TeaStore Helm chart. To deploy it:

```bash
helm install teastore ./examples/helm \
  --set locust.enabled=true \
  --set locust.users=50 \
  --set locust.spawnRate=5 \
  --set locust.runTime=10m
```

## Using with Skaffold

The image is included in the `skaffold.yaml` build configuration:

```bash
skaffold build -p locust
```

## Files

- `Dockerfile`: Defines the Docker image
- `locustfile.py`: The Locust test script that defines user behavior

## Further Reading

For more information on Locust and how to customize test scenarios, see the [official Locust documentation](https://docs.locust.io/).
