#!/usr/bin/env bash

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
LOCAL_DIR="$SCRIPT_DIR"
LOCAL_NAME="$(basename "$LOCAL_DIR")"

REMOTE_SYNC_ROOT="/mnt/storage/Storage/Sync"
REMOTE_DIR="$REMOTE_SYNC_ROOT/$LOCAL_NAME"

require_command() {
  local cmd="$1"
  if ! command -v "$cmd" >/dev/null 2>&1; then
    echo "Error: required command '$cmd' is not installed."
    exit 1
  fi
}

sync_with_lib_exception() {
  local source_dir="$1"
  local destination_dir="$2"

  rsync -a --delete --human-readable --info=stats2,progress2 \
    --exclude='/lib/***' \
    --exclude='/run/jdk/***' \
    "$source_dir/" "$destination_dir/"

  if [[ -d "$source_dir/lib" ]]; then
    rsync -a --human-readable --info=stats2,progress2 \
      "$source_dir/lib/" "$destination_dir/lib/"
  fi

  if [[ -d "$source_dir/run/jdk" ]]; then
    rsync -a --human-readable --info=stats2,progress2 \
      "$source_dir/run/jdk/" "$destination_dir/run/jdk/"
  fi
}

sync_up() {
  mkdir -p "$REMOTE_DIR"

  echo "Sync direction: up"
  echo "Local  -> Remote"
  echo "From: $LOCAL_DIR/"
  echo "To:   $REMOTE_DIR/"

  sync_with_lib_exception "$LOCAL_DIR" "$REMOTE_DIR"

  echo "Done. Remote mirrored from local (deletes enabled except destination lib/ and run/jdk/)."
}

sync_down() {
  if [[ ! -d "$REMOTE_DIR" ]]; then
    echo "Error: remote directory does not exist: $REMOTE_DIR"
    exit 1
  fi

  echo "Sync direction: down"
  echo "Remote -> Local"
  echo "From: $REMOTE_DIR/"
  echo "To:   $LOCAL_DIR/"

  sync_with_lib_exception "$REMOTE_DIR" "$LOCAL_DIR"

  echo "Done. Local mirrored from remote (deletes enabled except destination lib/ and run/jdk/)."
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
