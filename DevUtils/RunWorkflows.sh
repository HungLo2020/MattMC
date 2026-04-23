#!/bin/bash

# RunWorkflows.sh - Trigger GitHub Actions workflows for MattMC
# Default behavior: run the "Release Latest" workflow manually.

set -euo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "$0")" && pwd)"
PROJECT_ROOT="$SCRIPT_DIR"

while [ ! -f "$PROJECT_ROOT/gradlew" ] && [ "$PROJECT_ROOT" != "/" ]; do
    PROJECT_ROOT="$(dirname "$PROJECT_ROOT")"
done

if [ ! -f "$PROJECT_ROOT/gradlew" ]; then
    echo "ERROR: Could not find gradlew. Are you in the MattMC project?"
    exit 1
fi

cd "$PROJECT_ROOT"

WORKFLOW_NAME="${1:-Release Latest}"
WORKFLOW_REF="${WORKFLOW_REF:-$(git rev-parse --abbrev-ref HEAD)}"

if ! command -v gh >/dev/null 2>&1; then
    echo "ERROR: GitHub CLI (gh) is not installed."
    exit 1
fi

if ! gh auth status >/dev/null 2>&1; then
    echo "ERROR: gh is not authenticated. Run: gh auth login"
    exit 1
fi

echo "========================================="
echo "  Run GitHub Workflow"
echo "========================================="
echo "Workflow: $WORKFLOW_NAME"
echo "Ref:      $WORKFLOW_REF"
echo ""

gh workflow run "$WORKFLOW_NAME" --ref "$WORKFLOW_REF"

echo "Workflow dispatch submitted."
echo ""
echo "To watch progress:"
echo "  gh run list --workflow \"$WORKFLOW_NAME\" --limit 5"
echo "  gh run watch"
echo ""
