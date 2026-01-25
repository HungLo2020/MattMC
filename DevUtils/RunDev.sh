#!/bin/bash

# RunDev.sh - Development script to run the Minecraft client
# Clears ERROR-LOG.txt and writes all output from Gradle to it
# 
# Usage:
#   ./RunDev.sh          - Run in normal JAR mode (default)
#   ./RunDev.sh native   - Run in native compilation mode

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

# Check if native mode is requested
if [ "$1" = "native" ]; then
    echo "🚀 Running in NATIVE compilation mode..."
    echo "   This will compile the application to a native executable using GraalVM."
    echo "   This process may take 10-15 minutes on first run."
    echo ""
    
    # Run Gradle native compilation and execution
    ./gradlew clean nativeCompile 2>&1 | tee "$LOG_FILE"
    
    # Check if compilation succeeded
    if [ $? -eq 0 ]; then
        echo ""
        echo "✅ Native compilation successful!"
        echo "   Native executable created in: build/native/nativeCompile/"
        echo ""
        echo "🎮 Running native executable..."
        
        # Determine the executable name based on OS
        if [[ "$OSTYPE" == "msys" || "$OSTYPE" == "win32" ]]; then
            NATIVE_EXEC="./build/native/nativeCompile/MattMC.exe"
        else
            NATIVE_EXEC="./build/native/nativeCompile/MattMC"
        fi
        
        # Make executable if needed (Unix-like systems)
        chmod +x "$NATIVE_EXEC" 2>/dev/null || true
        
        # Run the native executable
        if [ -f "$NATIVE_EXEC" ]; then
            cd run 2>/dev/null || mkdir run && cd run
            "$NATIVE_EXEC" --version 1.21.10 --accessToken 0 --gameDir . 2>&1 | tee -a "../$LOG_FILE"
        else
            echo "❌ Native executable not found at: $NATIVE_EXEC"
            echo "   Check the build log for errors."
        fi
    else
        echo "❌ Native compilation failed. Check $LOG_FILE for details."
        exit 1
    fi
else
    echo "🚀 Running in NORMAL mode (JAR-based)..."
    echo "   To use native compilation, run: ./DevUtils/RunDev.sh native"
    echo ""
    
    # Run Gradle and pipe all output (stdout + stderr) into the log file
    ./gradlew clean runClient 2>&1 | tee "$LOG_FILE"
fi
