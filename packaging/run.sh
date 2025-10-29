#!/usr/bin/env bash
set -euo pipefail

# Resolve script location (handles symlinks, spaces, etc.)
SCRIPT_DIR="$(cd -- "$(dirname -- "$0")" && pwd)"

# Assume the script lives in .../MattMC/
# So JRE is at: ./libraries/jre/
export JAVA_HOME="${SCRIPT_DIR}/libraries/jre"

# Use the Gradle-generated launcher which honors JAVA_HOME
exec "${SCRIPT_DIR}/bin/MattMC" "$@"
