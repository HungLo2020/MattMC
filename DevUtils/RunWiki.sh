#!/usr/bin/env bash
# If someone runs this via zsh (or anything else), re-run it under bash.
if [ -z "${BASH_VERSION:-}" ]; then
	exec /usr/bin/env bash "$0" "$@"
fi

set -euo pipefail

log() {
	echo "[RunWiki] $*"
}

have_cmd() {
	command -v "$1" >/dev/null 2>&1
}

# Absolute path to this script (bash)
SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd -- "$SCRIPT_DIR/.." && pwd)"
cd "$PROJECT_ROOT"

os_name() {
	uname -s
}

sanity_checks() {
	if [[ ! -f "$PROJECT_ROOT/mkdocs.yml" ]]; then
		log "ERROR: mkdocs.yml not found in repo root: $PROJECT_ROOT"
		exit 1
	fi
	if [[ ! -d "$PROJECT_ROOT/docs" ]]; then
		log "ERROR: docs/ directory not found in repo root: $PROJECT_ROOT"
		exit 1
	fi
	if [[ ! -f "$PROJECT_ROOT/docs/index.md" ]]; then
		log "ERROR: docs/index.md not found. MkDocs expects index.md as your home page."
		exit 1
	fi
}

ensure_python3() {
	if have_cmd python3; then
		log "python3 found: $(python3 --version 2>/dev/null || true)"
		return 0
	fi

	local os
	os="$(os_name)"

	if [[ "$os" == "Darwin" ]]; then
		log "python3 not found. Installing via Homebrew (macOS)..."
		if ! have_cmd brew; then
			cat <<'EOF'
[RunWiki] Homebrew is required to auto-install Python on macOS, but brew is not installed.

Install Homebrew, then re-run:
  /bin/bash -c "$(curl -fsSL https://raw.githubusercontent.com/Homebrew/install/HEAD/install.sh)"
EOF
			exit 1
		fi
		brew install python
	elif [[ "$os" == "Linux" ]]; then
		log "python3 not found. Installing via apt (Kubuntu/Debian/Ubuntu)..."
		if have_cmd apt-get; then
			sudo apt-get update
			sudo apt-get install -y python3 python3-pip python3-venv
		else
			log "apt-get not found. Please install python3 manually for your distro."
			exit 1
		fi
	else
		log "Unsupported OS: $os"
		exit 1
	fi
}

ensure_pip() {
	if python3 -m pip --version >/dev/null 2>&1; then
		log "pip found: $(python3 -m pip --version)"
		return 0
	fi

	log "pip not found; attempting to install/repair..."
	local os
	os="$(os_name)"

	if [[ "$os" == "Darwin" ]]; then
		python3 -m ensurepip --upgrade || true
	elif [[ "$os" == "Linux" ]]; then
		if have_cmd apt-get; then
			sudo apt-get update
			sudo apt-get install -y python3-pip
		else
			log "Cannot install pip automatically on this Linux distro."
			exit 1
		fi
	fi

	python3 -m pip --version >/dev/null 2>&1 || {
		log "pip still not available. Install python3-pip manually."
		exit 1
	}
}

ensure_mkdocs() {
	if python3 -m mkdocs --version >/dev/null 2>&1; then
		log "MkDocs already installed: $(python3 -m mkdocs --version)"
		return 0
	fi

	log "MkDocs not found. Installing with pip (user install)..."
	python3 -m pip install --user mkdocs
	log "MkDocs installed: $(python3 -m mkdocs --version)"
}

run_wiki() {
	log "Starting MkDocs dev server..."
	log "Open: http://127.0.0.1:9000/"
	exec python3 -m mkdocs serve -a 127.0.0.1:9000
}

sanity_checks
ensure_python3
ensure_pip
ensure_mkdocs
run_wiki
