FROM python:3.11-slim

# Set working directory
WORKDIR /locust

# Install locust
RUN pip install --no-cache-dir locust

# Copy the locustfile
COPY locustfile.py /locust/locustfile.py

# Environment variables for configuration
ENV TARGET_HOST="http://teastore-webui:8080"
ENV USERS="10"
ENV SPAWN_RATE="1"
ENV RUN_TIME="5m"

# Expose Locust web UI port
EXPOSE 8089

# Default command runs Locust in headless mode
# Override with --web-ui to run with GUI
CMD locust -f /locust/locustfile.py \
    --host=${TARGET_HOST} \
    --users=${USERS} \
    --spawn-rate=${SPAWN_RATE} \
    --run-time=${RUN_TIME} \
    --headless
