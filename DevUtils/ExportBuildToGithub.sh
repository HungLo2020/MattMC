#!/bin/bash

set -euo pipefail

# ExportBuildToGithub.sh
# 1) Runs RunExport.sh
# 2) Finds newest MattMC client zip in Downloads
# 3) Replaces GitHub release/tag "latest" with that zip using gh CLI

SCRIPT_DIR="$(cd -- "$(dirname -- "$0")" && pwd)"
PROJECT_ROOT="$SCRIPT_DIR"

while [ ! -f "$PROJECT_ROOT/gradlew" ] && [ "$PROJECT_ROOT" != "/" ]; do
    PROJECT_ROOT="$(dirname "$PROJECT_ROOT")"
done

if [ ! -f "$PROJECT_ROOT/gradlew" ]; then
    echo "ERROR: Could not find gradlew. Are you in the MattMC project?"
    exit 1
fi

cd "$PROJECT_ROOT"

DOWNLOADS_DIR="${DOWNLOADS_DIR:-/home/matt/Downloads}"
ZIP_PATTERN="${ZIP_PATTERN:-MattMC-Client-*.zip}"
RELEASE_TAG="latest"
RELEASE_TITLE="MattMC Latest Build"

if ! command -v gh >/dev/null 2>&1; then
    echo "ERROR: GitHub CLI (gh) is not installed."
    exit 1
fi

if ! gh auth status >/dev/null 2>&1; then
    echo "ERROR: gh is not authenticated. Run: gh auth login"
    exit 1
fi

REPO="${GITHUB_REPOSITORY:-}"
if [ -z "$REPO" ]; then
    REMOTE_URL="$(git remote get-url origin 2>/dev/null || true)"
    if [ -n "$REMOTE_URL" ]; then
        REPO="$(echo "$REMOTE_URL" | sed -E 's#^(git@|https://)github.com[:/]##; s#\.git$##')"
    fi
fi

REPO_ARGS=()
if [ -n "$REPO" ]; then
    REPO_ARGS=(--repo "$REPO")
fi

echo "========================================="
echo "  Export + GitHub Release (latest)"
echo "========================================="
echo ""

echo "[1/4] Running export build script..."
"$SCRIPT_DIR/RunExport.sh"

echo "[2/4] Locating exact exported zip in $DOWNLOADS_DIR..."
VERSION="$(./gradlew properties -q | awk '/^version:/ {print $2; exit}')"
EXPECTED_ZIP_FILENAME="MattMC-Client-${VERSION}.zip"
ZIP_FILE="$DOWNLOADS_DIR/$EXPECTED_ZIP_FILENAME"

if [ ! -f "$ZIP_FILE" ]; then
    echo "Expected zip not found: $ZIP_FILE"
    echo "Falling back to newest zip matching: $ZIP_PATTERN"
    ZIP_FILE="$(find "$DOWNLOADS_DIR" -maxdepth 1 -type f -name "$ZIP_PATTERN" -printf '%T@ %p\n' | sort -nr | head -n 1 | cut -d' ' -f2-)"
fi

if [ -z "$ZIP_FILE" ] || [ ! -f "$ZIP_FILE" ]; then
    echo "ERROR: No exported zip found in $DOWNLOADS_DIR matching $ZIP_PATTERN"
    exit 1
fi

ZIP_FILENAME="$(basename "$ZIP_FILE")"
echo "Found zip: $ZIP_FILENAME"

if ! unzip -Z1 "$ZIP_FILE" | head -n 4000 | grep -qE '(^|/)run-mattmc\.sh$|(^|/)run-mattmc\.bat$'; then
    echo "ERROR: Selected archive does not look like the exported client distribution."
    echo "Refusing to upload: $ZIP_FILE"
    exit 1
fi

if unzip -Z1 "$ZIP_FILE" | head -n 4000 | grep -qE '(^|/)src/main/java/|(^|/)build\.gradle$|(^|/)settings\.gradle$|(^|/)gradlew$'; then
    echo "ERROR: Selected archive appears to be a source/project archive, not the export build zip."
    echo "Refusing to upload: $ZIP_FILE"
    exit 1
fi

echo "[3/4] Replacing release '$RELEASE_TAG'..."
if gh release view "$RELEASE_TAG" "${REPO_ARGS[@]}" >/dev/null 2>&1; then
    gh release delete "$RELEASE_TAG" --yes --cleanup-tag "${REPO_ARGS[@]}"
fi

RELEASE_NOTES="Automated export build upload.\n\nAsset: $ZIP_FILENAME"

echo "[4/4] Creating release '$RELEASE_TAG' and uploading asset..."
gh release create "$RELEASE_TAG" "$ZIP_FILE" \
    --title "$RELEASE_TITLE" \
    --notes "$RELEASE_NOTES" \
    --latest \
    "${REPO_ARGS[@]}"

echo ""
echo "========================================="
echo "  GitHub Release Updated"
echo "========================================="
echo "Tag: $RELEASE_TAG"
if [ -n "$REPO" ]; then
    echo "Repo: $REPO"
fi
echo "Asset: $ZIP_FILENAME"
echo ""