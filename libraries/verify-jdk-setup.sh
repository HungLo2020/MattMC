#!/usr/bin/env bash
set -euo pipefail

# Script to verify the bundled JDK setup

SCRIPT_DIR="$(cd -- "$(dirname -- "$0")" && pwd)"
PROJECT_ROOT="$(dirname "$SCRIPT_DIR")"
cd "$PROJECT_ROOT"

echo "================================================"
echo "  MattMC Bundled JDK Verification"
echo "================================================"
echo ""

# Check if JDK exists in libraries
JDK_LIB_DIR="libraries/jdk-21"
if [[ -d "$JDK_LIB_DIR" && -x "$JDK_LIB_DIR/bin/java" ]]; then
    echo "✅ Bundled JDK found in libraries/"
    echo "   Location: $JDK_LIB_DIR"
    echo "   Version: $("$JDK_LIB_DIR/bin/java" -version 2>&1 | head -n 1 || echo 'unknown')"
else
    echo "❌ Bundled JDK NOT found in libraries/"
    echo "   Expected location: $JDK_LIB_DIR"
    echo ""
    echo "   Run: ./gradlew downloadJdk"
    echo "   Or: bash libraries/download-jdk.sh"
    exit 1
fi

echo ""

# Check if JDK exists in run directory
JDK_RUN_DIR="run/jdk-21"
if [[ -d "$JDK_RUN_DIR" && -x "$JDK_RUN_DIR/bin/java" ]]; then
    echo "✅ Bundled JDK found in run/"
    echo "   Location: $JDK_RUN_DIR"
else
    echo "⚠️  Bundled JDK NOT found in run/"
    echo "   Expected location: $JDK_RUN_DIR"
    echo ""
    echo "   This is normal if you haven't run the game yet."
    echo "   Run: ./gradlew copyJdkToRun"
fi

echo ""

# Check gitignore
if grep -q "libraries/jdk-21/" .gitignore; then
    echo "✅ JDK directories properly gitignored"
else
    echo "⚠️  JDK directories not in .gitignore"
fi

echo ""

# Check Gradle tasks exist
if grep -q "downloadJdk" build.gradle; then
    echo "✅ Gradle task 'downloadJdk' configured"
else
    echo "❌ Gradle task 'downloadJdk' NOT found"
fi

if grep -q "copyJdkToRun" build.gradle; then
    echo "✅ Gradle task 'copyJdkToRun' configured"
else
    echo "❌ Gradle task 'copyJdkToRun' NOT found"
fi

echo ""
echo "================================================"
echo "  Verification Complete!"
echo "================================================"
echo ""
echo "The bundled JDK is ready to use."
echo ""
echo "Next steps:"
echo "  1. Run the client: ./gradlew runClient"
echo "  2. Run the server: ./gradlew runServer"
echo "  3. Build distribution: ./gradlew clientDistZip"
echo ""
