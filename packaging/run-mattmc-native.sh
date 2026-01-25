#!/bin/bash
# MattMC Native Client Launcher
# This script launches the native executable version of MattMC

# Get the directory containing this script (should be project root in distribution)
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
cd "$SCRIPT_DIR"

# Check for native executable
NATIVE_EXEC="./MattMC"

if [[ ! -f "$NATIVE_EXEC" ]]; then
    echo "Error: Native executable not found at: $NATIVE_EXEC"
    echo ""
    echo "This appears to be a JAR-based distribution, not a native executable distribution."
    echo "Please use the JAR-based launcher instead: run-mattmc.sh"
    echo ""
    echo "To build a native executable distribution, run:"
    echo "  ./gradlew nativeClientDist"
    exit 1
fi

# Make sure the executable has execute permissions
chmod +x "$NATIVE_EXEC" 2>/dev/null || true

if [[ ! -x "$NATIVE_EXEC" ]]; then
    echo "Error: Native executable found but not executable: $NATIVE_EXEC"
    echo "Please check file permissions."
    exit 1
fi

echo "🚀 Launching MattMC Native Client..."
echo ""

# Launch the native executable
# Native executables don't need JVM arguments - they're already compiled!
$NATIVE_EXEC --version @VERSION@ --accessToken 0 --gameDir run
