#!/bin/bash

# RunExport.sh - Build and export Mattcraft client distribution
# Builds the project, creates a runnable distribution, zips it,
# copies to Downloads folder, and extracts it

set -e

# Downloads directory - defaults to matt's home on Kubuntu
# Override with: DOWNLOADS_DIR=/path/to/dir ./RunExport.sh
DOWNLOADS_DIR="${DOWNLOADS_DIR:-/home/matt/Downloads}"
PROJECT_NAME="Mattcraft"

echo "========================================="
echo "  Mattcraft Client Export Script"
echo "========================================="
echo ""

# Build the client distribution
echo "[1/5] Building client distribution..."
./gradlew clientDistZip --no-daemon

# Get the built zip file
ZIP_FILE="build/distributions/Mattcraft-Client-$(./gradlew properties -q | grep '^version:' | awk '{print $2}').zip"

# Fallback: find the zip if version detection fails
if [ ! -f "$ZIP_FILE" ]; then
    ZIP_FILE=$(find build/distributions -name "Mattcraft-Client-*.zip" -type f | head -n 1)
fi

if [ ! -f "$ZIP_FILE" ]; then
    echo "ERROR: Could not find the built zip file!"
    exit 1
fi

echo "[2/5] Found distribution: $ZIP_FILE"

# Ensure Downloads directory exists
echo "[3/5] Ensuring Downloads directory exists..."
mkdir -p "$DOWNLOADS_DIR"

# Copy zip to Downloads
echo "[4/5] Copying to $DOWNLOADS_DIR..."
cp "$ZIP_FILE" "$DOWNLOADS_DIR/"

# Get the filename
ZIP_FILENAME=$(basename "$ZIP_FILE")

# Extract in Downloads folder
echo "[5/5] Extracting in $DOWNLOADS_DIR..."
cd "$DOWNLOADS_DIR"

# Remove old extraction if exists
if [ -d "$PROJECT_NAME" ]; then
    rm -rf "$PROJECT_NAME"
fi

unzip -o "$ZIP_FILENAME"

echo ""
echo "========================================="
echo "  Export Complete!"
echo "========================================="
echo ""
echo "Your game has been exported to:"
echo "  $DOWNLOADS_DIR/$PROJECT_NAME"
echo ""
echo "To run the game:"
echo "  cd $DOWNLOADS_DIR/$PROJECT_NAME"
echo "  ./run-mattcraft.sh"
echo ""
