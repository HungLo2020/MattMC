#!/usr/bin/env bash
set -Eeuo pipefail

DEST_DIR="/home/matt/OneDrive/Apps/Games/Storage/MattMC"

# ---- Locate app root (works from app root or packaging/) ----
SCRIPT_DIR="$(cd -- "$(dirname -- "$0")" && pwd)"
APP_DIR="$(dirname "$SCRIPT_DIR")"

APP_NAME="$(basename "$APP_DIR")"
TS="$(date +%Y%m%d-%H%M%S)"
FINAL_NAME="${APP_NAME}-${TS}.zip"
FINAL_PATH="${DEST_DIR}/${FINAL_NAME}"

echo "📦 Backing up folder: $APP_DIR"
echo "➡️  Destination:       $FINAL_PATH"

# ---- Ensure dest exists & is writable ----
if [[ ! -d "$DEST_DIR" ]]; then
	echo "❌ Destination directory does not exist: $DEST_DIR"
	exit 1
fi
PROBE="${DEST_DIR}/._write_probe_${TS}.$$"
if ! (echo "probe" > "$PROBE"); then
	echo "❌ Cannot write to destination: $DEST_DIR"
	ls -ld "$DEST_DIR" || true
	exit 1
fi
rm -f "$PROBE"

# ---- Check tools ----
command -v zip >/dev/null 2>&1 || { echo "❌ 'zip' not found. Install it (e.g., sudo apt install zip)"; exit 1; }

# ---- Create a temp directory and define zip path inside it ----
TMP_DIR="$(mktemp -d)"
cleanup() { rm -rf "$TMP_DIR" 2>/dev/null || true; }
trap cleanup EXIT
TMP_ZIP="${TMP_DIR}/${FINAL_NAME}"

echo "🧪 Temp dir:          $TMP_DIR"
echo "🧪 Temp archive path: $TMP_ZIP"

# Zip the top-level app folder so the archive contains 'MattMC/...'
APP_PARENT="$(dirname "$APP_DIR")"
pushd "$APP_PARENT" >/dev/null

echo "🗜️  Running: zip -r \"$TMP_ZIP\" \"$APP_NAME\""
set +e
zip -r "$TMP_ZIP" "$APP_NAME"
ZIP_RC=$?
set -e
popd >/dev/null

if [[ $ZIP_RC -ne 0 ]]; then
	echo "❌ zip failed with exit code $ZIP_RC"
	exit $ZIP_RC
fi

# Sanity check temp archive
if [[ ! -s "$TMP_ZIP" ]]; then
	echo "❌ Backup failed: temp archive is missing or empty: $TMP_ZIP"
	exit 1
fi

# ---- Copy to OneDrive with verbosity ----
echo "📥 Copying to destination…"
cp -v "$TMP_ZIP" "$FINAL_PATH"

# Verify destination
if [[ ! -s "$FINAL_PATH" ]]; then
	echo "❌ Destination file missing or empty after copy: $FINAL_PATH"
	ls -l "$DEST_DIR" || true
	exit 1
fi

echo "✅ Backup complete: $FINAL_PATH"
# temp cleaned by trap
