#!/bin/bash

# RunDev.sh - Development script to run the Minecraft client
# Clears ERROR-LOG.txt and writes all output from Gradle to it

LOG_FILE="ERROR-LOG.txt"

# Truncate the log file (create it if it doesn't exist)
: > "$LOG_FILE"

# Run Gradle and pipe all output (stdout + stderr) into the log file
./gradlew clean runClient 2>&1 | tee "$LOG_FILE"
