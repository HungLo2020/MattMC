#!/usr/bin/env bash
set -euo pipefail

# Run from project root
cd -- "$(dirname -- "$0")"

echo "📦 Exporting MattMC…"
./Export.sh

# Find the exported app in Downloads (Export.sh puts it there)
DOWNLOADS_DIR="${XDG_DOWNLOAD_DIR:-$HOME/Downloads}"
APP_DIR="${DOWNLOADS_DIR}/MattMC"
RUN_SH="${APP_DIR}/run.sh"

# Sanity checks
if [[ ! -d "$APP_DIR" ]]; then
	echo "❌ Exported app folder not found: $APP_DIR"
	exit 1
fi
if [[ ! -f "$RUN_SH" ]]; then
	echo "❌ run.sh not found at: $RUN_SH"
	exit 1
fi

# Ensure it’s executable
chmod +x "$RUN_SH"

echo "🚀 Launching exported build from: $RUN_SH"
# Forward any args given to RunExport.sh down to run.sh
exec "$RUN_SH" "$@"
