#!/usr/bin/env bash

# SetupProject.sh - Prepare this MattMC checkout for local development.
#
# This script is intended to be the first setup step for a fresh developer
# machine. For now it ensures the Rust Cargo toolchain is available because the
# project builds mandatory Rust native code. If Cargo is missing, it installs
# rustup with the stable toolchain using the minimal profile, then verifies that
# cargo is available before exiting.

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

load_cargo_env() {
	if [ -f "$HOME/.cargo/env" ]; then
		# shellcheck disable=SC1091
		. "$HOME/.cargo/env"
	fi
}

download_rustup_installer() {
	local destination="$1"

	if command -v curl >/dev/null 2>&1; then
		curl --proto '=https' --tlsv1.2 -sSf https://sh.rustup.rs -o "$destination"
	elif command -v wget >/dev/null 2>&1; then
		wget -qO "$destination" https://sh.rustup.rs
	else
		echo "ERROR: Cargo is missing and neither curl nor wget is installed."
		echo "Install curl or wget, then rerun this script."
		exit 1
	fi
}

ensure_cargo() {
	load_cargo_env

	if command -v cargo >/dev/null 2>&1; then
		echo "Cargo is already installed: $(cargo --version)"
		return
	fi

	if command -v rustup >/dev/null 2>&1; then
		echo "Cargo is missing, but rustup is installed. Installing stable Rust toolchain..."
		rustup toolchain install stable --profile minimal
		rustup default stable
		load_cargo_env
	else
		echo "Cargo is missing. Installing rustup and the stable Rust toolchain..."
		local installer
		installer="$(mktemp)"
		trap 'rm -f "$installer"' EXIT
		download_rustup_installer "$installer"
		sh "$installer" -y --profile minimal --default-toolchain stable
		load_cargo_env
	fi

	if ! command -v cargo >/dev/null 2>&1; then
		echo "ERROR: Cargo installation completed, but cargo is still not available on PATH."
		echo "Try opening a new shell or source: $HOME/.cargo/env"
		exit 1
	fi

	echo "Cargo installed: $(cargo --version)"
}

echo "========================================="
echo "  MattMC Project Setup"
echo "========================================="
echo ""

ensure_cargo

echo ""
echo "Setup complete."
