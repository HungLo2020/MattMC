#!/bin/bash

# ExportJar.sh - Build MattMC jar and copy it to external lib folder
# Builds the project to ensure an up-to-date jar, then copies it to:
#   /home/matt/Documents/MattMC/lib/
# Overwrites the existing jar.

set -e

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

# Destination directory (override with: DEST_DIR=/path ./ExportJar.sh)
DEST_DIR="${DEST_DIR:-/home/matt/Documents/MattMC/lib}"

echo "========================================="
echo "  MattMC Jar Export Script"
echo "========================================="
echo ""

echo "[1/4] Building jar..."
./gradlew clean jar --no-daemon

echo "[2/4] Locating built jar..."
JAR_FILE="$(find "$PROJECT_ROOT/build/libs" -maxdepth 1 -type f -name "MattMC*.jar" ! -name "*-sources.jar" ! -name "*-javadoc.jar" -printf "%T@ %p\n" 2>/dev/null | sort -nr | head -n 1 | cut -d' ' -f2-)"

if [ -z "$JAR_FILE" ] || [ ! -f "$JAR_FILE" ]; then
	echo "ERROR: Could not find built jar in build/libs!"
	echo "Looked for: $PROJECT_ROOT/build/libs/MattMC*.jar"
	exit 1
fi

echo "    Found: $JAR_FILE"

echo "[3/4] Ensuring destination exists: $DEST_DIR"
mkdir -p "$DEST_DIR"

echo "[4/4] Copying jar (overwrite)..."
cp -f "$JAR_FILE" "$DEST_DIR/"

echo ""
echo "========================================="
echo "  Export Complete!"
echo "========================================="
echo ""
echo "Copied to:"
echo "  $DEST_DIR/$(basename "$JAR_FILE")"
echo ""
