#!/usr/bin/env bash
set -euo pipefail

# Script to download GraalVM for Linux and macOS (x64 and ARM64)
# This script checks if the JDK is already present and downloads it if needed

SCRIPT_DIR="$(cd -- "$(dirname -- "$0")" && pwd)"
PROJECT_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"

# Read Java version from gradle.properties
if [[ -f "${PROJECT_DIR}/gradle.properties" ]]; then
    JAVA_VERSION=$(grep '^java_version=' "${PROJECT_DIR}/gradle.properties" | cut -d'=' -f2)
    echo "📋 Using Java version ${JAVA_VERSION} from gradle.properties"
else
    echo "⚠️  gradle.properties not found, using default Java version"
    JAVA_VERSION=25
fi

JDK_DIR="${SCRIPT_DIR}/jdk-${JAVA_VERSION}"

# GraalVM version configuration
# Using GraalVM for JDK 21 as JDK 25 is not yet available for GraalVM
# GraalVM typically lags behind latest JDK releases
GRAALVM_VERSION="21.0.5"
GRAALVM_BUILD="21.0.5+9.1"

# Detect architecture
ARCH="$(uname -m)"
OS="$(uname -s | tr '[:upper:]' '[:lower:]')"

if [[ "$OS" == "linux" ]]; then
    if [[ "$ARCH" == "x86_64" || "$ARCH" == "amd64" ]]; then
        PLATFORM="linux-x64"
        JDK_URL="https://download.oracle.com/graalvm/21/latest/graalvm-jdk-21_linux-x64_bin.tar.gz"
        JDK_ARCHIVE="graalvm-jdk-21_linux-x64_bin.tar.gz"
        JDK_EXTRACTED_DIR="graalvm-jdk-${GRAALVM_VERSION}+9.1"
    elif [[ "$ARCH" == "aarch64" || "$ARCH" == "arm64" ]]; then
        PLATFORM="linux-aarch64"
        JDK_URL="https://download.oracle.com/graalvm/21/latest/graalvm-jdk-21_linux-aarch64_bin.tar.gz"
        JDK_ARCHIVE="graalvm-jdk-21_linux-aarch64_bin.tar.gz"
        JDK_EXTRACTED_DIR="graalvm-jdk-${GRAALVM_VERSION}+9.1"
    else
        echo "❌ Unsupported architecture: $ARCH" >&2
        exit 1
    fi
elif [[ "$OS" == "darwin" ]]; then
    if [[ "$ARCH" == "x86_64" || "$ARCH" == "amd64" ]]; then
        PLATFORM="mac-x64"
        JDK_URL="https://download.oracle.com/graalvm/21/latest/graalvm-jdk-21_macos-x64_bin.tar.gz"
        JDK_ARCHIVE="graalvm-jdk-21_macos-x64_bin.tar.gz"
        JDK_EXTRACTED_DIR="graalvm-jdk-${GRAALVM_VERSION}+9.1"
    elif [[ "$ARCH" == "aarch64" || "$ARCH" == "arm64" ]]; then
        PLATFORM="mac-aarch64"
        JDK_URL="https://download.oracle.com/graalvm/21/latest/graalvm-jdk-21_macos-aarch64_bin.tar.gz"
        JDK_ARCHIVE="graalvm-jdk-21_macos-aarch64_bin.tar.gz"
        JDK_EXTRACTED_DIR="graalvm-jdk-${GRAALVM_VERSION}+9.1"
    else
        echo "❌ Unsupported architecture: $ARCH" >&2
        exit 1
    fi
else
    echo "❌ Unsupported OS: $OS. This script is for Linux and macOS only." >&2
    echo "   For Windows, please use download-jdk.ps1" >&2
    echo "   Or download manually from:" >&2
    echo "   https://www.graalvm.org/downloads/" >&2
    exit 1
fi

# Check if JDK already exists
if [[ -d "$JDK_DIR" && -f "$JDK_DIR/bin/java" && -f "$JDK_DIR/bin/native-image" ]]; then
    echo "✅ GraalVM already exists at: $JDK_DIR"
    exit 0
fi

echo "📥 Downloading GraalVM ${GRAALVM_VERSION} for $PLATFORM..."
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

echo "📦 Extracting GraalVM..."
tar -xzf "$JDK_ARCHIVE"

# Move to final location
echo "📂 Installing GraalVM to: $JDK_DIR"
rm -rf "$JDK_DIR"

# Handle macOS directory structure (Contents/Home)
if [[ "$OS" == "darwin" ]]; then
    # On macOS, GraalVM is extracted to graalvm-jdk-X.X.X+X/Contents/Home/
    if [[ -d "$JDK_EXTRACTED_DIR/Contents/Home" ]]; then
        mv "$JDK_EXTRACTED_DIR/Contents/Home" "$JDK_DIR"
    else
        # Fallback if structure is different
        mv "$JDK_EXTRACTED_DIR" "$JDK_DIR"
    fi
else
    # On Linux, GraalVM is directly in graalvm-jdk-X.X.X+X/
    mv "$JDK_EXTRACTED_DIR" "$JDK_DIR"
fi

echo "✅ GraalVM installed successfully!"
"$JDK_DIR/bin/java" -version
echo ""
echo "📦 Verifying native-image tool..."
if [[ -f "$JDK_DIR/bin/native-image" ]]; then
    echo "✅ native-image tool is available"
else
    echo "⚠️  native-image tool not found, it should be included in GraalVM"
fi

echo ""
echo "🎉 GraalVM ${GRAALVM_VERSION} is ready to use at: $JDK_DIR"
echo "   Native Image compilation is now available!"
