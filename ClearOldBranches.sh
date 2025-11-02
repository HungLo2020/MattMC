#!/usr/bin/env bash
set -euo pipefail

# Delete local branches that no longer exist on the remote (by short name).
# Default remote: origin. Force-deletes (-D). Protects current, main, master, develop.

REMOTE="origin"

usage() {
	printf "Usage: %s [-r remote]\n" "$(basename "$0")"
	exit 1
}

while getopts ":r:h" opt; do
	case "$opt" in
		r) REMOTE="$OPTARG" ;;
		h|*) usage ;;
	esac
done

# Ensure repo
git rev-parse --git-dir >/dev/null 2>&1 || { echo "Not a git repo." >&2; exit 2; }

# Refresh/prune remote refs
git fetch "$REMOTE" --prune --prune-tags >/dev/null

# Build set of remote branch short names (e.g., "feature/foo")
declare -A REMOTE_SET=()
while IFS= read -r rb; do
	# Skip the HEAD symref line if present
	[[ "$rb" == "HEAD -> "* ]] && continue
	REMOTE_SET["$rb"]=1
done < <(git for-each-ref --format='%(refname:strip=3)' "refs/remotes/$REMOTE")

CURRENT_BRANCH="$(git symbolic-ref --quiet --short HEAD || true)"
PROTECTED=("main" "master" "develop" "$CURRENT_BRANCH")

is_protected() {
	local b="$1"
	for p in "${PROTECTED[@]}"; do
		[[ -n "$p" && "$b" == "$p" ]] && return 0
	done
	return 1
}

# Gather local branches to delete: present locally, absent on remote by name
mapfile -t LOCALS < <(git for-each-ref --format='%(refname:short)' refs/heads)

TO_DELETE=()
for lb in "${LOCALS[@]}"; do
	# protect current/main/master/develop
	if is_protected "$lb"; then
		continue
	fi
	# If remote has branch with same short name, keep it
	if [[ -n "${REMOTE_SET[$lb]:-}" ]]; then
		continue
	fi
	TO_DELETE+=("$lb")
done

if ((${#TO_DELETE[@]} == 0)); then
	echo "No local-only branches to delete relative to '$REMOTE'."
	exit 0
fi

echo "Deleting local branches not present on '$REMOTE':"
for b in "${TO_DELETE[@]}"; do
	echo "  $b"
done

# Force delete
for b in "${TO_DELETE[@]}"; do
	# Double-check it's not the current branch (paranoia)
	if [[ -n "$CURRENT_BRANCH" && "$b" == "$CURRENT_BRANCH" ]]; then
		echo "Skipping current branch: $b"
		continue
	fi
	git branch -D "$b" >/dev/null && echo "Deleted $b" || echo "Failed to delete $b"
done

echo "Done."
