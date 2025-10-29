#!/usr/bin/env bash
set -euo pipefail

# --- Paths ---
SCRIPT_DIR="$(cd -- "$(dirname -- "$0")" && pwd)"
PROJECT_DIR="$SCRIPT_DIR"
DOWNLOADS="${HOME}/Downloads"

APP_NAME="MattMC"
APP_VERSION="0.1.0"

# JRE info
JRE_URL="https://download.oracle.com/java/21/latest/jdk-21_linux-x64_bin.tar.gz"
JRE_DIR="${PROJECT_DIR}/packaging/libraries/jre"
JRE_PARENT="$(dirname "$JRE_DIR")"

# --- Ensure JRE present ---
if [[ ! -d "$JRE_DIR" ]]; then
	echo "☕ No local JRE found in packaging/libraries/jre — downloading..."
	mkdir -p "$JRE_PARENT"
	TMP_TAR="$(mktemp --suffix=.tar.gz)"
	curl -L "$JRE_URL" -o "$TMP_TAR"
	echo "📦 Extracting JRE..."
	tar -xzf "$TMP_TAR" -C "$JRE_PARENT"
	rm -f "$TMP_TAR"

	# Rename extracted folder to 'jre' if it's a JDK directory (Oracle’s naming)
	FOUND_DIR="$(find "$JRE_PARENT" -maxdepth 1 -type d -name 'jdk-*' | head -n 1)"
	if [[ -n "$FOUND_DIR" ]]; then
		mv "$FOUND_DIR" "$JRE_DIR"
	fi
	echo "✅ JRE installed at $JRE_DIR"
else
	echo "☕ Found existing JRE at $JRE_DIR"
fi

# --- Build ---
cd "$PROJECT_DIR"
./gradlew clean portableZip -PappVersion="${APP_VERSION}"

# --- Locate the produced ZIP ---
ZIP_FILE=""
if compgen -G "build/releases/*.zip" > /dev/null; then
	ZIP_FILE="$(ls -t build/releases/*.zip | head -n 1)"
elif compgen -G "releases/*.zip" > /dev/null; then
	ZIP_FILE="$(ls -t releases/*.zip | head -n 1)"
else
	echo "❌ No zip found. Did the Gradle task write to build/releases or releases/?"
	exit 1
fi

echo "📦 Found zip: $ZIP_FILE"

# --- Clean Downloads ---
echo "🧹 Cleaning ${DOWNLOADS}…"
rm -f "${DOWNLOADS}/${APP_NAME}"*.zip || true
rm -rf "${DOWNLOADS}/${APP_NAME}" || true

# --- Copy and extract ---
cp -f "$ZIP_FILE" "$DOWNLOADS/"
BASENAME="$(basename -- "$ZIP_FILE")"

echo "📂 Extracting ${BASENAME} to ${DOWNLOADS}…"
unzip -o "${DOWNLOADS}/${BASENAME}" -d "${DOWNLOADS}" >/dev/null

echo "✅ Export complete!"
echo "   Zip: ${DOWNLOADS}/${BASENAME}"
echo "   Folder: ${DOWNLOADS}/${APP_NAME}/"
