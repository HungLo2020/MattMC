#!/usr/bin/env bash
set -euo pipefail

# Default to Java 21 if JAVA_HOME isn't set (edit the path if needed)
export JAVA_HOME="${JAVA_HOME:-/usr/lib/jvm/java-21-openjdk-amd64}"

# Use the Gradle-generated launcher which honors JAVA_HOME
exec "$(dirname -- "$0")/bin/MattMC" "$@"
