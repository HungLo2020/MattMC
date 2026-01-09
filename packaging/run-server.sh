#!/bin/bash
# MattMC Server Launcher

# Java version used in this distribution
# Note: Hardcoded because gradle.properties is not included in distributions
JAVA_VERSION=25

# Get the directory containing this script (should be in server directory)
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
cd "$SCRIPT_DIR"

# Require bundled JDK - located in parent run directory
BUNDLED_JAVA="${SCRIPT_DIR}/../run/jdk-${JAVA_VERSION}/bin/java"
if [[ ! -f "$BUNDLED_JAVA" ]]; then
    echo "Error: Bundled JDK not found at: $BUNDLED_JAVA"
    echo "Please ensure the distribution includes the bundled JDK."
    exit 1
fi

# Make sure the bundled Java is executable
chmod +x "$BUNDLED_JAVA" 2>/dev/null || true
if [[ ! -x "$BUNDLED_JAVA" ]]; then
    echo "Error: Bundled JDK found but not executable: $BUNDLED_JAVA"
    echo "Please check file permissions."
    exit 1
fi

JAVA_CMD="$BUNDLED_JAVA"
echo "Using bundled JDK ${JAVA_VERSION}"

# Launch the dedicated server
# Note: Server runs in headless mode by default (--nogui)
# Remove --nogui to run with GUI
$JAVA_CMD -Xmx2G -Xms1G \
    -XX:+UseZGC \
    -XX:+UseCompactObjectHeaders \
    -cp "@CLASSPATH_LINUX@" \
    net.minecraft.server.Main \
    --nogui
