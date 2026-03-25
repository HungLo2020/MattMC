#!/bin/bash
# MattMC Client Launcher

# Java version used in this distribution
# Note: Hardcoded because gradle.properties is not included in distributions
JAVA_VERSION=25

# Get the directory containing this script (should be project root in distribution)
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
cd "$SCRIPT_DIR"

# Require bundled JDK - do not fall back to system Java
BUNDLED_JAVA="${SCRIPT_DIR}/run/jdk-${JAVA_VERSION}/bin/java"
if [[ ! -f "$BUNDLED_JAVA" ]]; then
    echo "Error: Bundled JDK not found at: $BUNDLED_JAVA"
    echo "Please ensure the distribution includes the bundled JDK."
    echo "To build with bundled JDK, run: ./gradlew downloadJdk copyJdkToRun clientDist"
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

# Prefer bundled SPIR-V compiler when present
SPIRV_COMPILER="${SCRIPT_DIR}/libraries/deps/glslangValidator"
SPIRV_JVM_ARG=""
if [[ -x "$SPIRV_COMPILER" ]]; then
    SPIRV_JVM_ARG="-Dvulkanic.spirv.compiler=$SPIRV_COMPILER"
    echo "Using bundled SPIR-V compiler"
fi

# Launch the game with Fabric Loader
# Note: Minecraft classes are included in the main JAR, no separate game JAR needed
# Note: Assets are loaded directly from JAR classpath - no --assetsDir needed
# Note: JVM_ARGS is intentionally not quoted to allow word splitting

# Build JVM arguments - Use G1GC on macOS due to ZGC stability issues (SIGBUS crashes)
if [[ "$(uname -s)" == "Darwin" ]]; then
    # macOS - use G1GC to avoid SIGBUS crashes in Arena/Chunk allocation
    # Additional stability flags for macOS to prevent crashes during shader loading
    JVM_ARGS="-Xmx8G -Xms4G -XX:+UseG1GC -XX:ReservedCodeCacheSize=512m -XX:+DisableExplicitGC -XX:MaxMetaspaceSize=512m -Djava.awt.headless=false -XX:+UnlockExperimentalVMOptions -XX:G1NewSizePercent=20 -XX:G1MaxNewSizePercent=80 -XX:+UseStringDeduplication -XX:MaxGCPauseMillis=200"
else
    # Linux/Unix - use ZGC with UseCompactObjectHeaders for better performance
    JVM_ARGS="-Xmx8G -Xms4G -XX:+UseZGC -XX:+UseCompactObjectHeaders"
fi

$JAVA_CMD $JVM_ARGS \
    $SPIRV_JVM_ARG \
    -Dfabric.development=true \
    -cp "@CLASSPATH_LINUX@" \
    net.fabricmc.loader.impl.launch.knot.KnotClient \
    --version @VERSION@ \
    --accessToken 0 \
    --gameDir run
