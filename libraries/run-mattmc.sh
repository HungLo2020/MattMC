#!/bin/bash
# MattMC Client Launcher

cd "$(dirname "$0")"

# Use bundled JDK if available, otherwise use system java
if [[ -x "run/jdk-21/bin/java" ]]; then
    JAVA_CMD="run/jdk-21/bin/java"
    echo "Using bundled JDK: $(run/jdk-21/bin/java -version 2>&1 | head -n 1)"
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
