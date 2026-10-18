#!/bin/bash
# MattMC Server Launcher

# Java version used in this distribution
# Note: Hardcoded because gradle.properties is not included in distributions
JAVA_VERSION=25

# This script lives in MattMC/run/server, which is also the server's working
# directory: server.properties and the world are created here.
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
cd "$SCRIPT_DIR"
# The install root holds lib/ and natives/; run/ holds the bundled JDK.
INSTALL_DIR="$(cd "$SCRIPT_DIR/../.." && pwd)"
RUN_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"

# Require bundled platform-specific JDK in run/jdk
OS="$(uname -s)"
ARCH="$(uname -m)"

if [[ "$OS" == "Linux" ]]; then
    if [[ "$ARCH" == "x86_64" || "$ARCH" == "amd64" ]]; then
        PLATFORM="linux-x64"
    elif [[ "$ARCH" == "aarch64" || "$ARCH" == "arm64" ]]; then
        PLATFORM="linux-aarch64"
    else
        echo "Error: Unsupported Linux architecture: $ARCH"
        exit 1
    fi
elif [[ "$OS" == "Darwin" ]]; then
    if [[ "$ARCH" == "x86_64" || "$ARCH" == "amd64" ]]; then
        PLATFORM="mac-x64"
    elif [[ "$ARCH" == "aarch64" || "$ARCH" == "arm64" ]]; then
        PLATFORM="mac-aarch64"
    else
        echo "Error: Unsupported macOS architecture: $ARCH"
        exit 1
    fi
else
    echo "Error: Unsupported operating system: $OS"
    exit 1
fi

BUNDLED_JAVA="${RUN_DIR}/jdk/${PLATFORM}/bin/java"
if [[ ! -f "$BUNDLED_JAVA" ]]; then
    echo "Error: Bundled JDK not found at: $BUNDLED_JAVA"
    echo "Please ensure the distribution includes run/jdk/${PLATFORM}."
    exit 1
fi

# Make sure the bundled Java is executable
chmod +x "$BUNDLED_JAVA" 2>/dev/null || true
if [[ ! -x "$BUNDLED_JAVA" ]]; then
    echo "Error: Bundled JDK found but not executable: $BUNDLED_JAVA"
    echo "Please check file permissions."
    exit 1
fi

JAVA_CMD="$BUNDLED_JAVA"
echo "Using bundled JDK ${JAVA_VERSION} (${PLATFORM})"

# Build classpath dynamically from all jars in lib/ so mixed-platform native jars are safe.
CLASSPATH=""
for jar in "$INSTALL_DIR"/lib/*.jar; do
    if [[ -f "$jar" ]]; then
        if [[ -z "$CLASSPATH" ]]; then
            CLASSPATH="$jar"
        else
            CLASSPATH="$CLASSPATH:$jar"
        fi
    fi
done

if [[ -z "$CLASSPATH" ]]; then
    echo "Error: no JAR files found in $INSTALL_DIR/lib"
    exit 1
fi

# Launch the dedicated server
# Note: Server runs in headless mode by default (--nogui)
# Remove --nogui to run with GUI

# Build JVM arguments - Use G1GC on macOS due to ZGC stability issues (SIGBUS crashes)
if [[ "$(uname -s)" == "Darwin" ]]; then
    # macOS - use G1GC to avoid SIGBUS crashes in Arena::destruct_contents
    JVM_ARGS="-Xmx2G -Xms1G -XX:+UseG1GC"
else
    # Linux/Unix - use ZGC with UseCompactObjectHeaders for better performance
    JVM_ARGS="-Xmx2G -Xms1G -XX:+UseZGC -XX:+UseCompactObjectHeaders"
fi

$JAVA_CMD $JVM_ARGS \
    --enable-native-access=ALL-UNNAMED \
    -Dmattmc.rust.natives.dir="$INSTALL_DIR/natives" \
    -cp "$CLASSPATH" \
    net.minecraft.server.Main \
    --nogui
