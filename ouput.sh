#!/usr/bin/env bash
set -euo pipefail

BASE="origin/master"
TARGET="origin/develop"
OUT="OUTPUT.txt"

git fetch origin --prune

{
	echo "=== Change summary: $BASE -> $TARGET (excluding frnsrc/ and *.json) ==="
	echo "Generated: $(date -Is)"
	echo
	echo "Code  Meaning"
	echo "A     Added (new file)"
	echo "M     Modified"
	echo "D     Deleted"
	echo "R###  Renamed (score shown)"
	echo "C###  Copied (score shown)"
	echo "T     Type changed (e.g., file -> symlink)"
	echo "U     Unmerged / conflict"
	echo
	echo "=== Changes ==="
	# Exclude everything under frnsrc/ and all .json files anywhere.
	git -c diff.renameLimit=10000 diff --name-status -M -C "$BASE...$TARGET" -- . ':(exclude)frnsrc/**' ':(exclude)**/*.json'
} > "$OUT"

echo "Wrote $OUT"
