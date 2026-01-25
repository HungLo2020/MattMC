#!/bin/bash
# MattMC Native Server Launcher
# This script launches the native executable version of MattMC Server

# Get the directory containing this script (should be in server directory)
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
cd "$SCRIPT_DIR"

# Check for native executable in parent directory
NATIVE_EXEC="../MattMC-Server"

if [[ ! -f "$NATIVE_EXEC" ]]; then
    echo "Error: Native server executable not found at: $NATIVE_EXEC"
    echo ""
    echo "This appears to be a JAR-based distribution, not a native executable distribution."
    echo "Please use the JAR-based launcher instead: run-server.sh"
    echo ""
    echo "To build a native executable distribution, run:"
    echo "  ./gradlew nativeServerCompile"
    exit 1
fi

# Make sure the executable has execute permissions
chmod +x "$NATIVE_EXEC" 2>/dev/null || true

if [[ ! -x "$NATIVE_EXEC" ]]; then
    echo "Error: Native server executable found but not executable: $NATIVE_EXEC"
    echo "Please check file permissions."
    exit 1
fi

echo "🚀 Launching MattMC Native Server..."
echo ""

# Launch the native server executable
# Native executables don't need JVM arguments - they're already compiled!
# Server runs in headless mode by default
$NATIVE_EXEC --nogui
