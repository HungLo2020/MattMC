#!/usr/bin/env bash

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
LOCAL_DIR="$SCRIPT_DIR"
LOCAL_NAME="$(basename "$LOCAL_DIR")"

# Only MattMC/run/ is synchronized: worlds, settings, shader packs and the
# dedicated server (run/server/). The bundled JDK (run/jdk/) is replaced by
# updates instead and is never synced. Application files outside run/ are
# never touched in either direction.
REMOTE_SYNC_ROOT="${MATTMC_SYNC_ROOT:-/mnt/storage/Storage/Sync}"
LOCAL_RUN_DIR="$LOCAL_DIR/run"
REMOTE_RUN_DIR="$REMOTE_SYNC_ROOT/$LOCAL_NAME/run"

require_command() {
  local cmd="$1"
  if ! command -v "$cmd" >/dev/null 2>&1; then
    echo "Error: required command '$cmd' is not installed."
    exit 1
  fi
}

# Mirrors one run/ tree onto the other. --delete removes destination files
# that are absent from the source; the excluded jdk/ is neither copied nor
# deleted on either side.
sync_run_dir() {
  local source_dir="$1"
  local destination_dir="$2"

  rsync -a --delete --human-readable --info=stats2,progress2 \
    --exclude='/jdk/' \
    "$source_dir/" "$destination_dir/"
}

sync_up() {
  if [[ ! -d "$LOCAL_RUN_DIR" ]]; then
    echo "Error: local run directory does not exist: $LOCAL_RUN_DIR"
    exit 1
  fi
  mkdir -p "$REMOTE_RUN_DIR"

  echo "Sync direction: up"
  echo "Local  -> Remote"
  echo "From: $LOCAL_RUN_DIR/"
  echo "To:   $REMOTE_RUN_DIR/"

  sync_run_dir "$LOCAL_RUN_DIR" "$REMOTE_RUN_DIR"

  echo "Done. Remote run/ mirrored from local (deletes enabled, run/jdk/ excluded)."
}

sync_down() {
  if [[ ! -d "$REMOTE_RUN_DIR" ]]; then
    echo "Error: remote run directory does not exist: $REMOTE_RUN_DIR"
    exit 1
  fi
  mkdir -p "$LOCAL_RUN_DIR"

  echo "Sync direction: down"
  echo "Remote -> Local"
  echo "From: $REMOTE_RUN_DIR/"
  echo "To:   $LOCAL_RUN_DIR/"

  sync_run_dir "$REMOTE_RUN_DIR" "$LOCAL_RUN_DIR"

  echo "Done. Local run/ mirrored from remote (deletes enabled, run/jdk/ excluded)."
}

main() {
  require_command rsync

  if [[ "$LOCAL_NAME" != "MattMC" ]]; then
    echo "Warning: script is currently located in '$LOCAL_DIR'."
    echo "Expected final location is inside a directory named 'MattMC'."
    echo "Remote target will use directory name: '$LOCAL_NAME'."
  fi

  if [[ ! -d "/mnt/storage" ]]; then
    echo "Warning: /mnt/storage is not present right now."
    echo "If this is your SMB mount, make sure it is mounted before syncing."
  fi

  local direction
  while true; do
    read -r -p "Enter sync direction ('up' or 'down'): " direction
    case "${direction,,}" in
      up)
        sync_up
        break
        ;;
      down)
        sync_down
        break
        ;;
      *)
        echo "Please enter exactly: up or down"
        ;;
    esac
  done
}

main "$@"
