#!/usr/bin/env bash

set -euo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
DEPS_DIR="${SCRIPT_DIR}/libraries/deps"

BLUE='\033[0;34m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m'

mkdir -p "${DEPS_DIR}"

if [[ "$(uname -s)" == "MINGW"* || "$(uname -s)" == "MSYS"* || "$(uname -s)" == "CYGWIN"* ]]; then
    TARGET_BIN="${DEPS_DIR}/glslangValidator.exe"
else
    TARGET_BIN="${DEPS_DIR}/glslangValidator"
fi

echo -e "${BLUE}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
echo -e "${BLUE}  MattMC Installer: Bundled SPIR-V Compiler${NC}"
echo -e "${BLUE}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"

if [[ -x "${TARGET_BIN}" ]]; then
    echo -e "${GREEN}✓${NC} glslangValidator already bundled at ${TARGET_BIN}"
    exit 0
fi

copy_from_path() {
    local source_bin="$1"
    cp "${source_bin}" "${TARGET_BIN}"
    chmod +x "${TARGET_BIN}"
    echo -e "${GREEN}✓${NC} Bundled glslangValidator from PATH: ${source_bin}"
    echo -e "${GREEN}✓${NC} Output: ${TARGET_BIN}"
    return 0
}

if command -v glslangValidator >/dev/null 2>&1; then
    copy_from_path "$(command -v glslangValidator)"
    exit 0
fi

install_from_apt_deb() {
    local work_dir
    work_dir="$(mktemp -d)"
    trap 'rm -rf "${work_dir}"' RETURN

    echo -e "${YELLOW}⬇${NC} Trying to download glslang-tools package (no system install)..."
    pushd "${work_dir}" >/dev/null

    if ! apt-get download glslang-tools >/dev/null 2>&1; then
        popd >/dev/null
        return 1
    fi

    local deb_file
    deb_file="$(ls -1 glslang-tools_*.deb 2>/dev/null | head -n 1 || true)"
    if [[ -z "${deb_file}" ]]; then
        popd >/dev/null
        return 1
    fi

    dpkg-deb -x "${deb_file}" extracted
    local extracted_bin="${work_dir}/extracted/usr/bin/glslangValidator"
    if [[ ! -x "${extracted_bin}" ]]; then
        popd >/dev/null
        return 1
    fi

    cp "${extracted_bin}" "${TARGET_BIN}"
    chmod +x "${TARGET_BIN}"
    popd >/dev/null

    echo -e "${GREEN}✓${NC} Bundled glslangValidator from downloaded .deb package"
    echo -e "${GREEN}✓${NC} Output: ${TARGET_BIN}"
    return 0
}

install_with_sudo() {
    if ! command -v sudo >/dev/null 2>&1; then
        return 1
    fi

    echo -e "${YELLOW}⬇${NC} Attempting system install via sudo apt-get (fallback)..."
    if ! sudo -n true >/dev/null 2>&1; then
        echo -e "${YELLOW}!${NC} Passwordless sudo unavailable; skipping system install fallback"
        return 1
    fi

    sudo apt-get update -y >/dev/null
    sudo apt-get install -y glslang-tools >/dev/null

    if command -v glslangValidator >/dev/null 2>&1; then
        copy_from_path "$(command -v glslangValidator)"
        return 0
    fi

    return 1
}

if [[ "$(uname -s)" == "Linux" ]] && command -v apt-get >/dev/null 2>&1 && command -v dpkg-deb >/dev/null 2>&1; then
    if install_from_apt_deb; then
        exit 0
    fi

    if install_with_sudo; then
        exit 0
    fi
fi

echo -e "${RED}✗ Failed to provision glslangValidator${NC}"
echo ""
echo "Manual options:"
echo "  1) Install glslangValidator on PATH"
echo "  2) Copy it to: ${TARGET_BIN}"
echo ""
echo "Then rerun: ./Install.sh"
exit 1
