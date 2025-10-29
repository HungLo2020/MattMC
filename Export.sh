#!/usr/bin/env bash
set -euo pipefail

# --- Paths ---
SCRIPT_DIR="$(cd -- "$(dirname -- "$0")" && pwd)"
PROJECT_DIR="$SCRIPT_DIR"
DOWNLOADS="${HOME}/Downloads"

APP_NAME="MattMC"
APP_VERSION="0.1.0"   # change if you pass a different -PappVersion
# Build zip name is usually MattMC-<version>-<platform>.zip per your Gradle task,
# but we’ll discover it dynamically just in case.

# --- Build ---
cd "$PROJECT_DIR"
./gradlew clean portableZip -PappVersion="${APP_VERSION}"

# --- Locate the produced ZIP (prefer build/releases, fallback to releases) ---
ZIP_FILE=""
if compgen -G "build/releases/*.zip" > /dev/null; then
	ZIP_FILE="$(ls -t build/releases/*.zip | head -n 1)"
elif compgen -G "releases/*.zip" > /dev/null; then
	ZIP_FILE="$(ls -t releases/*.zip | head -n 1)"
else
	echo "❌ No zip found. Did the Gradle task write to build/releases or releases/?"
	exit 1
fi

echo "Found zip: $ZIP_FILE"

# --- Clean Downloads (remove any prior MattMC zip or folder) ---
echo "Cleaning ${DOWNLOADS}…"
rm -f "${DOWNLOADS}/${APP_NAME}"*.zip || true
rm -rf "${DOWNLOADS}/${APP_NAME}" || true

# --- Copy and extract ---
cp -f "$ZIP_FILE" "$DOWNLOADS/"
BASENAME="$(basename -- "$ZIP_FILE")"

echo "Extracting ${BASENAME} to ${DOWNLOADS}…"
unzip -o "${DOWNLOADS}/${BASENAME}" -d "${DOWNLOADS}" >/dev/null

echo "✅ Export complete:"
echo "   Zip: ${DOWNLOADS}/${BASENAME}"
echo "   Folder: ${DOWNLOADS}/${APP_NAME}/"
