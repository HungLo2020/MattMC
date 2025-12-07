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

"$JAVA_CMD" -Xmx2G -Xms512M \
    -XX:+UseG1GC \
    -XX:+ParallelRefProcEnabled \
    -XX:MaxGCPauseMillis=200 \
    -XX:+UnlockExperimentalVMOptions \
    -XX:+DisableExplicitGC \
    -XX:G1NewSizePercent=30 \
    -XX:G1MaxNewSizePercent=40 \
    -XX:G1HeapRegionSize=8M \
    -XX:G1ReservePercent=20 \
    -XX:G1HeapWastePercent=5 \
    -XX:G1MixedGCCountTarget=4 \
    -XX:InitiatingHeapOccupancyPercent=15 \
    -XX:G1MixedGCLiveThresholdPercent=90 \
    -XX:G1RSetUpdatingPauseTimePercent=5 \
    -XX:SurvivorRatio=32 \
    -XX:+PerfDisableSharedMem \
    -XX:MaxTenuringThreshold=1 \
    -cp "@CLASSPATH_LINUX@" \
    net.minecraft.client.main.Main \
    --version @VERSION@ \
    --accessToken 0 \
    --gameDir run \
    --assetsDir run/assets \
    --assetIndex @ASSET_INDEX@
