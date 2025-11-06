FROM python:3.11-slim

# Set working directory
WORKDIR /locust

# Install locust
RUN pip install --no-cache-dir locust

# Copy the locustfile and entrypoint script
COPY locustfile.py /locust/locustfile.py
COPY entrypoint.sh /locust/entrypoint.sh
RUN chmod +x /locust/entrypoint.sh

# Environment variables for configuration
ENV TARGET_HOST="http://teastore-webui:8080"
ENV USERS="10"
ENV SPAWN_RATE="1"
ENV RUN_TIME=""

# Expose Locust web UI port
EXPOSE 8089

# Use entrypoint script to handle optional RUN_TIME
ENTRYPOINT ["/locust/entrypoint.sh"]
