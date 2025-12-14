#!/bin/bash
# Test script to diagnose LOD rendering issue
# This script verifies all JARs are built and runs the game

set -e

echo "==================================================================="
echo "LOD Rendering Test - Verifying Build"
echo "==================================================================="

# Check if game JAR exists
if [ ! -f "build/libs/minecraft-1.21.10.jar" ]; then
    echo "ERROR: Game JAR missing! Building now..."
    ./gradlew gameJar
fi

# Verify debug string is in game JAR
echo "Checking if RENDER_STATE code is in game JAR..."
if unzip -p build/libs/minecraft-1.21.10.jar net/minecraft/client/renderer/LevelRenderer.class | strings | grep -q "DEBUG-RENDERSTATE"; then
    echo "✓ RENDER_STATE integration code found in game JAR"
else
    echo "✗ RENDER_STATE code NOT in game JAR - rebuilding..."
    ./gradlew clean gameJar
fi

# Check DH JAR
if [ ! -f "build/mods/distanthorizons-"*.jar ]; then
    echo "ERROR: DH JAR missing! Building now..."
    ./gradlew distantHorizonsJar
fi

echo ""
echo "==================================================================="
echo "All JARs verified. Running game..."
echo "==================================================================="
echo "IMPORTANT: Look for these debug messages in the console:"
echo "  [DEBUG-RENDERSTATE] Attempting to set DH RENDER_STATE"
echo "  [DEBUG-RENDERSTATE] Successfully set all RENDER_STATE fields"
echo ""
echo "If you DON'T see these messages, the game JAR is not being used!"
echo "==================================================================="
echo ""

# Run the game
./gradlew runClient --no-daemon
