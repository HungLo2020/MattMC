#!/usr/bin/env bash
set -euo pipefail

# Script to download Temurin OpenJDK 21 for Linux x64
# This script checks if the JDK is already present and downloads it if needed

SCRIPT_DIR="$(cd -- "$(dirname -- "$0")" && pwd)"
JDK_DIR="${SCRIPT_DIR}/jdk-21"

# JDK version configuration - change these to update version
JDK_VERSION="21.0.5+11"
JDK_BUILD="21.0.5_11"

# Detect architecture
ARCH="$(uname -m)"
OS="$(uname -s | tr '[:upper:]' '[:lower:]')"

if [[ "$OS" == "linux" ]]; then
    if [[ "$ARCH" == "x86_64" || "$ARCH" == "amd64" ]]; then
        PLATFORM="linux-x64"
        JDK_URL="https://github.com/adoptium/temurin21-binaries/releases/download/jdk-${JDK_VERSION}/OpenJDK21U-jdk_x64_linux_hotspot_${JDK_BUILD}.tar.gz"
        JDK_ARCHIVE="OpenJDK21U-jdk_x64_linux_hotspot_${JDK_BUILD}.tar.gz"
        JDK_EXTRACTED_DIR="jdk-${JDK_VERSION}"
    elif [[ "$ARCH" == "aarch64" || "$ARCH" == "arm64" ]]; then
        PLATFORM="linux-aarch64"
        JDK_URL="https://github.com/adoptium/temurin21-binaries/releases/download/jdk-${JDK_VERSION}/OpenJDK21U-jdk_aarch64_linux_hotspot_${JDK_BUILD}.tar.gz"
        JDK_ARCHIVE="OpenJDK21U-jdk_aarch64_linux_hotspot_${JDK_BUILD}.tar.gz"
        JDK_EXTRACTED_DIR="jdk-${JDK_VERSION}"
    else
        echo "❌ Unsupported architecture: $ARCH" >&2
        exit 1
    fi
else
    echo "❌ Unsupported OS: $OS. This script is for Linux only." >&2
    echo "   For Windows/macOS, please download manually from:" >&2
    echo "   https://adoptium.net/temurin/releases/" >&2
    exit 1
fi

# Check if JDK already exists
if [[ -d "$JDK_DIR" && -f "$JDK_DIR/bin/java" ]]; then
    echo "✅ JDK already exists at: $JDK_DIR"
    "$JDK_DIR/bin/java" -version
    exit 0
fi

echo "📥 Downloading Temurin OpenJDK 21 for $PLATFORM..."
echo "   URL: $JDK_URL"

# Create temporary directory
TEMP_DIR="$(mktemp -d)"
trap "rm -rf '$TEMP_DIR'" EXIT

cd "$TEMP_DIR"

# Download JDK
if command -v wget >/dev/null 2>&1; then
    wget -q --show-progress "$JDK_URL" -O "$JDK_ARCHIVE"
elif command -v curl >/dev/null 2>&1; then
    curl -L -# "$JDK_URL" -o "$JDK_ARCHIVE"
else
    echo "❌ Neither wget nor curl is available. Please install one of them." >&2
    exit 1
fi

echo "📦 Extracting JDK..."
tar -xzf "$JDK_ARCHIVE"

# Move to final location
echo "📂 Installing JDK to: $JDK_DIR"
rm -rf "$JDK_DIR"
mv "$JDK_EXTRACTED_DIR" "$JDK_DIR"

echo "✅ JDK installed successfully!"
"$JDK_DIR/bin/java" -version

echo ""
echo "🎉 Temurin OpenJDK 21 is ready to use at: $JDK_DIR"
