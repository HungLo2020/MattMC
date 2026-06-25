#!/usr/bin/env bash
set -euo pipefail

# --- Config you can tweak ---
ONEDRIVE_DIR="/mnt/storage/OneDrive/Apps/Programming/MattMC"
DOWNLOADS_DIR="${HOME}/Downloads"
COPY_TO_DOWNLOADS=false   # set to true if you want a copy in Downloads as well

# Exclusions to keep the backup lean. Directories are pruned before zip runs,
# so their contents are never scanned, copied, or added to the archive.
EXCLUDED_DIR_NAMES=(
	".gradle"
	".idea"
	".vscode"
	"out"
	"run"
	"logs"
	"site"
	".git"
)

EXCLUDED_FILE_NAMES=(
	".DS_Store"
)

# --- Derive names/paths ---
SCRIPT_DIR="$(cd -- "$(dirname -- "$0")" && pwd)"

# Find the git repository root
REPO_ROOT="$(cd "$SCRIPT_DIR" && git rev-parse --show-toplevel 2>/dev/null)"
if [ -z "$REPO_ROOT" ]; then
    echo "ERROR: Not in a git repository" >&2
    exit 1
fi
cd "$REPO_ROOT"

REPO_NAME="$(basename "$REPO_ROOT")"   # should be "MattMC"
TS="$(date +%Y%m%d-%H%M%S)"
ARCHIVE_NAME="${REPO_NAME}-${TS}.zip"
ARCHIVE_PATH="${REPO_ROOT}/${ARCHIVE_NAME}"
FILE_LIST="$(mktemp)"

cleanup() {
    rm -f "$FILE_LIST" "$ARCHIVE_PATH"
}
trap cleanup EXIT

# --- Make sure destinations exist ---
mkdir -p "$ONEDRIVE_DIR"
$COPY_TO_DOWNLOADS && mkdir -p "$DOWNLOADS_DIR"

echo "📦 Creating archive: ${ARCHIVE_PATH}"

# Build a pruned file list from the repo root, then zip only those files.
FIND_ARGS=(.)
for dir_name in "${EXCLUDED_DIR_NAMES[@]}"; do
    FIND_ARGS+=(-path "*/${dir_name}" -prune -o)
done
for file_name in "${EXCLUDED_FILE_NAMES[@]}"; do
    FIND_ARGS+=(! -name "$file_name")
done
FIND_ARGS+=(! -name "$ARCHIVE_NAME" -type f -print)

find "${FIND_ARGS[@]}" > "$FILE_LIST"
zip -q "${ARCHIVE_PATH}" -@ < "$FILE_LIST"

echo "✅ Archive created."

# --- Copy to destinations (overwrite if exists) ---
cp -f "${ARCHIVE_PATH}" "${ONEDRIVE_DIR}/"
$COPY_TO_DOWNLOADS && cp -f "${ARCHIVE_PATH}" "${DOWNLOADS_DIR}/"

echo "➡️  Copied to:"
echo "   • ${ONEDRIVE_DIR}/${ARCHIVE_NAME}"
$COPY_TO_DOWNLOADS && echo "   • ${DOWNLOADS_DIR}/${ARCHIVE_NAME}"
echo "🧹 Cleaned up local temp: ${ARCHIVE_NAME}"
echo "Done."
