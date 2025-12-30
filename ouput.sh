#!/usr/bin/env bash
set -euo pipefail

COMMIT="11839ba9b32ead477b2ae053214037cfa29cecce"
OUT="OUTPUT.txt"

# Fresh output file
: > "$OUT"

{
	echo "=== Full diff for commit $COMMIT (vs parent) ==="
	echo "Generated: $(date -Is)"
	echo

	# Metadata + file summary
	echo "=== Commit info ==="
	git show --no-patch --pretty=fuller "$COMMIT"
	echo

	echo "=== Files changed (stat) ==="
	git show --stat --oneline "$COMMIT"
	echo

	echo "=== Full patch (all changes) ==="
	git show --patch "$COMMIT"

} >> "$OUT"

echo "Wrote $OUT"
