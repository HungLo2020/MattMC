#!/bin/bash

# RunDev.sh - Development script to run the Minecraft client
# Clears ERROR-LOG.txt and writes all output from Gradle to it

# Find the project root (where gradlew is located)
SCRIPT_DIR="$(cd -- "$(dirname -- "$0")" && pwd)"
PROJECT_ROOT="$SCRIPT_DIR"

# If script is in a subdirectory, search upwards for gradlew
while [ ! -f "$PROJECT_ROOT/gradlew" ] && [ "$PROJECT_ROOT" != "/" ]; do
    PROJECT_ROOT="$(dirname "$PROJECT_ROOT")"
done

if [ ! -f "$PROJECT_ROOT/gradlew" ]; then
    echo "ERROR: Could not find gradlew. Are you in the MattMC project?"
    exit 1
fi

# Change to project root
cd "$PROJECT_ROOT"

LOG_FILE="ERROR-LOG.txt"

# Truncate the log file (create it if it doesn't exist)
: > "$LOG_FILE"

# Run Gradle and pipe all output (stdout + stderr) into the log file
./gradlew clean runClient 2>&1 | tee "$LOG_FILE"
