#!/bin/bash
set -e

echo "Starting HTTP Load Generator Director..."
echo "LoadGen Host: ${LOADGEN_HOST}:${LOADGEN_PORT}"
echo "Load Profile: ${LOAD_PROFILE}"
echo "Lua Script: ${LUA_SCRIPT}"
echo "Output File: ${OUTPUT_FILE}"
echo "Threads: ${NUM_THREADS}"

# Build the command
CMD="java -jar /director/httploadgenerator.jar director"
CMD="${CMD} -s ${LOADGEN_HOST}"
CMD="${CMD} -a ${LOAD_PROFILE}"
CMD="${CMD} -l ${LUA_SCRIPT}"
CMD="${CMD} -o ${OUTPUT_FILE}"
CMD="${CMD} -t ${NUM_THREADS}"

# Add docker monitoring if configured
if [ ! -z "${DOCKER_MONITOR_ENABLED}" ] && [ "${DOCKER_MONITOR_ENABLED}" == "true" ]; then
    CMD="${CMD} -c docker"
    if [ ! -z "${DOCKER_MONITOR_TARGETS}" ]; then
        CMD="${CMD} -p ${DOCKER_MONITOR_TARGETS}"
    fi
fi

echo "Executing: ${CMD}"
exec ${CMD}
