#!/bin/bash
# MattMC Client Launcher

# Get the directory containing this script (should be project root in distribution)
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
cd "$SCRIPT_DIR"

# Use bundled JDK if available, otherwise use system java
BUNDLED_JAVA="${SCRIPT_DIR}/run/jdk-21/bin/java"
if [[ -x "$BUNDLED_JAVA" ]]; then
    JAVA_CMD="$BUNDLED_JAVA"
    echo "Using bundled JDK"
else
    JAVA_CMD="java"
    echo "Using system Java"
fi

# Launch the game with Fabric Loader
# Note: Minecraft classes are included in the main JAR, no separate game JAR needed
"$JAVA_CMD" -Xmx8G -Xms4G \
    -XX:+UseZGC \
    -XX:+ZGenerational \
    -Dfabric.development=true \
    -cp "@CLASSPATH_LINUX@" \
    net.fabricmc.loader.impl.launch.knot.KnotClient \
    --version @VERSION@ \
    --accessToken 0 \
    --gameDir run \
    --assetsDir run/assets \
    --assetIndex @ASSET_INDEX@
