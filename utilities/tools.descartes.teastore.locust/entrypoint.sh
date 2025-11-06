#!/bin/bash

# Build the base locust command
CMD="locust -f /locust/locustfile.py --host=${TARGET_HOST} --users=${USERS} --spawn-rate=${SPAWN_RATE} --headless"

# Only add --run-time if RUN_TIME is set and not empty
if [ -n "${RUN_TIME}" ]; then
    CMD="${CMD} --run-time=${RUN_TIME}"
fi

# Execute the command
exec $CMD
