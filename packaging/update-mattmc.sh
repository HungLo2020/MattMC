#!/usr/bin/env bash
set -euo pipefail

REPO="${MATTMC_REPO:-HungLo2020/MattMC}"

while [[ $# -gt 0 ]]; do
    case "$1" in
        --repo)
            REPO="${2:?Missing value for --repo}"
            shift 2
            ;;
        *)
            echo "Unknown argument: $1" >&2
            exit 1
            ;;
    esac
done

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
INSTALL_DIR="$SCRIPT_DIR"

normalize_release_tag() {
    local version="${1:-}"
    version="${version#"${version%%[![:space:]]*}"}"
    version="${version%"${version##*[![:space:]]}"}"
    version="${version#v}"
    version="${version#V}"
    printf '%s' "$version"
}

require_command() {
    if ! command -v "$1" >/dev/null 2>&1; then
        echo "Error: required command not found: $1" >&2
        exit 1
    fi
}

detect_platform() {
    local os arch os_token arch_token
    os="$(uname -s)"
    arch="$(uname -m)"

    case "$os" in
        Linux) os_token="linux" ;;
        Darwin) os_token="mac" ;;
        *)
            echo "Error: unsupported operating system: $os" >&2
            exit 1
            ;;
    esac

    case "$arch" in
        x86_64|amd64) arch_token="x64" ;;
        aarch64|arm64) arch_token="aarch64" ;;
        *)
            echo "Error: unsupported architecture: $arch" >&2
            exit 1
            ;;
    esac

    printf '%s-%s' "$os_token" "$arch_token"
}

expected_asset_name() {
    local version="$1"
    local platform="$2"
    printf 'MattMC-Client-%s-%s.zip' "$version" "$platform"
}

json_get_latest_tag() {
    sed -n 's/.*"tag_name"[[:space:]]*:[[:space:]]*"\([^"]*\)".*/\1/p' "$1" | head -n 1
}

json_download_urls() {
    sed 's/"browser_download_url"/\
"browser_download_url"/g' "$1" |
        sed -n 's/.*"browser_download_url"[[:space:]]*:[[:space:]]*"\([^"]*\)".*/\1/p'
}

select_exact_asset() {
    local json_file="$1"
    local platform="$2"
    local release_tag="$3"
    local name url
    local tag_version expected_name version

    tag_version="$(normalize_release_tag "$release_tag")"
    if [[ -n "$tag_version" && "$tag_version" != "latest" ]]; then
        expected_name="$(expected_asset_name "$tag_version" "$platform")"

        while IFS= read -r url; do
            name="${url##*/}"
            if [[ "$name" == "$expected_name" ]]; then
                printf '%s\t%s\t%s' "$url" "$tag_version" "$expected_name"
                return 0
            fi
        done < <(json_download_urls "$json_file")

        return 1
    fi

    while IFS= read -r url; do
        name="${url##*/}"
        case "$name" in
            MattMC-Client-*-"$platform".zip)
                version="${name#MattMC-Client-}"
                version="${version%-"$platform".zip}"
                version="${version%-"$platform"}"
                printf '%s\t%s\t%s' "$url" "$version" "$name"
                return 0
                ;;
        esac
    done < <(json_download_urls "$json_file")

    return 1
}

payload_root() {
    local extract_dir="$1"

    if [[ -d "$extract_dir/MattMC" ]]; then
        printf '%s' "$extract_dir/MattMC"
        return 0
    fi

    local dirs=()
    while IFS= read -r dir; do
        dirs+=("$dir")
    done < <(find "$extract_dir" -mindepth 1 -maxdepth 1 -type d)

    if [[ "${#dirs[@]}" -eq 1 ]]; then
        printf '%s' "${dirs[0]}"
        return 0
    fi

    if [[ -d "$extract_dir/lib" || -f "$extract_dir/run-mattmc.sh" || -f "$extract_dir/run-mattmc.bat" ]]; then
        printf '%s' "$extract_dir"
        return 0
    fi

    echo "Error: unable to find MattMC payload root inside downloaded archive." >&2
    return 1
}

require_command curl
require_command unzip

PLATFORM="$(detect_platform)"
API_URL="https://api.github.com/repos/$REPO/releases/latest"
TMP_ROOT="$(mktemp -d "${TMPDIR:-/tmp}/mattmc-update.XXXXXX")"

cleanup() {
    rm -rf "$TMP_ROOT"
}
trap cleanup EXIT

JSON_PATH="$TMP_ROOT/latest-release.json"
ZIP_PATH="$TMP_ROOT/release.zip"
EXTRACT_DIR="$TMP_ROOT/extract"

echo "MattMC updater"
echo "Install: $INSTALL_DIR"

curl -fsSL \
    -H "Accept: application/vnd.github+json" \
    -H "User-Agent: MattMC-Updater" \
    "$API_URL" \
    -o "$JSON_PATH"

LATEST_VERSION="$(json_get_latest_tag "$JSON_PATH")"
if [[ -z "$LATEST_VERSION" ]]; then
    echo "Error: unable to read latest release tag from GitHub response." >&2
    exit 1
fi

ASSET_INFO="$(select_exact_asset "$JSON_PATH" "$PLATFORM" "$LATEST_VERSION" || true)"
if [[ -z "$ASSET_INFO" ]]; then
    echo "Error: expected exact GitHub release asset not found: MattMC-Client-<version>-$PLATFORM.zip" >&2
    exit 1
fi

ASSET_URL="$(printf '%s' "$ASSET_INFO" | cut -f1)"
LATEST_VERSION="$(printf '%s' "$ASSET_INFO" | cut -f2)"
EXPECTED_ASSET_NAME="$(printf '%s' "$ASSET_INFO" | cut -f3)"

echo "Release: $LATEST_VERSION"
echo "Expected asset: $EXPECTED_ASSET_NAME"
echo "Downloading: $(basename "$ASSET_URL")"

mkdir -p "$EXTRACT_DIR"
curl -fL \
    -H "User-Agent: MattMC-Updater" \
    "$ASSET_URL" \
    -o "$ZIP_PATH"

unzip -q "$ZIP_PATH" -d "$EXTRACT_DIR"

PAYLOAD_ROOT="$(payload_root "$EXTRACT_DIR")"
echo "Applying update from: $PAYLOAD_ROOT"

cp -R "$PAYLOAD_ROOT"/. "$INSTALL_DIR"/

echo "Update complete."
echo "Installed release asset: $EXPECTED_ASSET_NAME"
