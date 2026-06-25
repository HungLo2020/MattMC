#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SYSTEM_PYTHON="${PYTHON:-python3}"
VENV="$ROOT/.venv-wiki"
VENV_PYTHON="$VENV/bin/python"
COMMAND="${1:-serve}"

usage() {
  cat <<'EOF'
Usage: ./wiki/RunWiki.sh [serve|build|setup]

Commands:
  serve   Start the local wiki server. This is the default.
  build   Build the static wiki site with strict validation.
  setup   Create/update the local wiki Python environment.
EOF
}

install_wiki_environment() {
  echo "Preparing wiki environment at $VENV"
  "$SYSTEM_PYTHON" -m venv "$VENV"
  "$VENV_PYTHON" -m pip install --upgrade pip
  "$VENV_PYTHON" -m pip install -r "$ROOT/requirements-docs.txt"
}

ensure_wiki_environment() {
  if [ ! -x "$VENV_PYTHON" ]; then
    install_wiki_environment
    return
  fi

  if ! "$VENV_PYTHON" -m mkdocs --version >/dev/null 2>&1; then
    install_wiki_environment
  fi
}

case "$COMMAND" in
  setup)
    install_wiki_environment
    echo "Wiki environment is ready."
    ;;
  serve)
    ensure_wiki_environment
    cd "$ROOT"
    "$VENV_PYTHON" -m mkdocs serve
    ;;
  build)
    ensure_wiki_environment
    cd "$ROOT"
    "$VENV_PYTHON" -m mkdocs build --strict
    ;;
  -h|--help|help)
    usage
    ;;
  *)
    echo "Unknown wiki command: $COMMAND" >&2
    usage >&2
    exit 1
    ;;
esac
