#!/bin/bash
# MattMC Client Launcher

# Java version used in this distribution
# Note: Hardcoded because gradle.properties is not included in distributions
JAVA_VERSION=25

# Get the directory containing this script (should be project root in distribution)
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
cd "$SCRIPT_DIR"

# Use bundled JDK if available, otherwise use system java
BUNDLED_JAVA="${SCRIPT_DIR}/run/jdk-${JAVA_VERSION}/bin/java"
if [[ -f "$BUNDLED_JAVA" ]]; then
    # Make sure the bundled Java is executable
    chmod +x "$BUNDLED_JAVA" 2>/dev/null || true
    if [[ -x "$BUNDLED_JAVA" ]]; then
        JAVA_CMD="$BUNDLED_JAVA"
        echo "Using bundled JDK ${JAVA_VERSION}"
    else
        echo "Warning: Bundled JDK found but not executable, trying system Java"
        JAVA_CMD="java"
        echo "Using system Java"
    fi
else
    JAVA_CMD="java"
    echo "Using system Java (bundled JDK not found at: $BUNDLED_JAVA)"
fi

# Detect Java version (portable approach using sed/awk instead of grep -P)
# Handles both legacy format (1.8.0) and modern format (11+, 17+, 21+, 25+)
JAVA_VERSION_OUTPUT=$("$JAVA_CMD" -version 2>&1)
DETECTED_JAVA_VERSION=$(echo "$JAVA_VERSION_OUTPUT" | head -1 | sed -n 's/.*version "\(1\.\)\?\([0-9]*\).*/\2/p')

# Validate detected version is a number
if ! [[ "$DETECTED_JAVA_VERSION" =~ ^[0-9]+$ ]]; then
    echo "Warning: Could not detect Java version, using basic JVM arguments"
    DETECTED_JAVA_VERSION=0
fi

# Build JVM arguments based on Java version
JVM_ARGS="-Xmx8G -Xms4G"

# Add garbage collector flags (available in Java 21+)
if [[ "$DETECTED_JAVA_VERSION" -ge 21 ]]; then
    JVM_ARGS="$JVM_ARGS -XX:+UseZGC -XX:+ZGenerational"
fi

# Add Compact Object Headers flag (only available in Java 25+)
if [[ "$DETECTED_JAVA_VERSION" -ge 25 ]]; then
    JVM_ARGS="$JVM_ARGS -XX:+UseCompactObjectHeaders"
    echo "Using Java $DETECTED_JAVA_VERSION with Compact Object Headers enabled"
elif [[ "$DETECTED_JAVA_VERSION" -gt 0 ]]; then
    echo "Warning: Java $DETECTED_JAVA_VERSION detected. Compact Object Headers requires Java 25+"
    echo "         Performance may be suboptimal. Consider using the bundled JDK."
fi

# Launch the game with Fabric Loader
# Note: Minecraft classes are included in the main JAR, no separate game JAR needed
# Note: JVM_ARGS is intentionally not quoted to allow word splitting
$JAVA_CMD $JVM_ARGS \
    -Dfabric.development=true \
    -cp "@CLASSPATH_LINUX@" \
    net.fabricmc.loader.impl.launch.knot.KnotClient \
    --version @VERSION@ \
    --accessToken 0 \
    --gameDir run \
    --assetsDir run/assets \
    --assetIndex @ASSET_INDEX@
