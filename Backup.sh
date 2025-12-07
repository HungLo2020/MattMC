#!/usr/bin/env bash
set -euo pipefail

# --- Config you can tweak ---
ONEDRIVE_DIR="/home/matt/OneDrive/Apps/Programming/MattMC"
DOWNLOADS_DIR="${HOME}/Downloads"
COPY_TO_DOWNLOADS=false   # set to true if you want a copy in Downloads as well

# Exclusions to keep the backup lean. Remove lines if you truly want *everything*.
EXCLUDES=(
	"-x" ".gradle/*"
	"-x" ".idea/*"
	"-x" ".vscode/*"
	"-x" "out/*"
	"-x" ".DS_Store"
	# "-x" ".git/*"     # uncomment to exclude git history
)

# --- Derive names/paths ---
SCRIPT_DIR="$(cd -- "$(dirname -- "$0")" && pwd)"
cd "$SCRIPT_DIR"

REPO_NAME="$(basename "$SCRIPT_DIR")"   # should be "MattMC"
TS="$(date +%Y%m%d-%H%M%S)"
ARCHIVE_NAME="${REPO_NAME}-${TS}.zip"
ARCHIVE_PATH="${SCRIPT_DIR}/${ARCHIVE_NAME}"

# --- Make sure destinations exist ---
mkdir -p "$ONEDRIVE_DIR"
$COPY_TO_DOWNLOADS && mkdir -p "$DOWNLOADS_DIR"

echo "📦 Creating archive: ${ARCHIVE_PATH}"

# Build the zip from the repo root.
# shellcheck disable=SC2068
zip -r "${ARCHIVE_PATH}" . ${EXCLUDES[@]} >/dev/null

echo "✅ Archive created."

# --- Copy to destinations (overwrite if exists) ---
cp -f "${ARCHIVE_PATH}" "${ONEDRIVE_DIR}/"
$COPY_TO_DOWNLOADS && cp -f "${ARCHIVE_PATH}" "${DOWNLOADS_DIR}/"

# --- Remove temp archive from repo ---
rm -f "${ARCHIVE_PATH}"

echo "➡️  Copied to:"
echo "   • ${ONEDRIVE_DIR}/${ARCHIVE_NAME}"
$COPY_TO_DOWNLOADS && echo "   • ${DOWNLOADS_DIR}/${ARCHIVE_NAME}"
echo "🧹 Cleaned up local temp: ${ARCHIVE_NAME}"
echo "Done."
