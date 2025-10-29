#!/usr/bin/env bash
set -euo pipefail

# Navigate to the script’s directory (your project root)
cd -- "$(dirname -- "$0")"

# Optional: print a header for clarity
echo "🚀 Launching MattMC in development mode..."
echo "----------------------------------------"

# Use system or project JDK; uncomment below to force a specific JDK:
# export JAVA_HOME="/usr/lib/jvm/java-21-openjdk-amd64"
# export PATH="$JAVA_HOME/bin:$PATH"

# Run Gradle's built-in application runner
./gradlew run

# Optional: print exit message
echo "----------------------------------------"
echo "✅ MattMC exited cleanly."
