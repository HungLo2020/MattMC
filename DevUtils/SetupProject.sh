#!/usr/bin/env bash

# SetupProject.sh - Prepare this MattMC checkout for local development.
#
# This script is intended to be the first setup step for a fresh developer
# machine. It ensures Python 3 and the Rust Cargo toolchain are available
# because the project builds mandatory Rust native code and uses Python-based
# developer tooling. If Cargo is missing, it installs rustup with the stable
# toolchain using the minimal profile, then verifies that cargo is available
# before exiting.

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

run_as_root() {
	if [ "$(id -u)" -eq 0 ]; then
		"$@"
	elif command -v sudo >/dev/null 2>&1; then
		sudo "$@"
	else
		echo "ERROR: Need root privileges to install packages, but sudo is not installed."
		exit 1
	fi
}

find_python3() {
	if command -v python3 >/dev/null 2>&1; then
		printf '%s' python3
		return 0
	fi

	if command -v python >/dev/null 2>&1 && python - <<'PY' >/dev/null 2>&1
import sys
raise SystemExit(0 if sys.version_info.major >= 3 else 1)
PY
	then
		printf '%s' python
		return 0
	fi

	return 1
}

install_python() {
	case "$(uname -s)" in
		Linux)
			if command -v apt-get >/dev/null 2>&1; then
				run_as_root apt-get update
				run_as_root apt-get install -y python3 python3-pip
			elif command -v dnf >/dev/null 2>&1; then
				run_as_root dnf install -y python3 python3-pip
			elif command -v yum >/dev/null 2>&1; then
				run_as_root yum install -y python3 python3-pip
			elif command -v pacman >/dev/null 2>&1; then
				run_as_root pacman -S --needed --noconfirm python python-pip
			elif command -v zypper >/dev/null 2>&1; then
				run_as_root zypper --non-interactive install python3 python3-pip
			else
				echo "ERROR: Python 3 is missing and no supported package manager was found."
				echo "Install Python 3 and pip, then rerun this script."
				exit 1
			fi
			;;
		Darwin)
			if command -v brew >/dev/null 2>&1; then
				brew install python
			else
				echo "ERROR: Python 3 is missing and Homebrew is not installed."
				echo "Install Python 3 from https://www.python.org/ or install Homebrew, then rerun this script."
				exit 1
			fi
			;;
		*)
			echo "ERROR: Python 3 is missing on unsupported OS: $(uname -s)"
			exit 1
			;;
	esac
}

ensure_python() {
	local python_cmd

	if python_cmd="$(find_python3)"; then
		echo "Python is already installed: $("$python_cmd" --version)"
	else
		echo "Python 3 is missing. Installing Python 3..."
		install_python
		if ! python_cmd="$(find_python3)"; then
			echo "ERROR: Python 3 installation completed, but python is still not available on PATH."
			exit 1
		fi
		echo "Python installed: $("$python_cmd" --version)"
	fi

	if ! "$python_cmd" -m pip --version >/dev/null 2>&1; then
		echo "pip is missing. Attempting to install pip for Python 3..."
		if ! "$python_cmd" -m ensurepip --upgrade >/dev/null 2>&1; then
			echo "ERROR: pip is missing for $python_cmd."
			echo "Install the Python pip package for your system, then rerun this script."
			exit 1
		fi
	fi

	echo "pip is available: $("$python_cmd" -m pip --version)"
}

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

ensure_python
echo ""
ensure_cargo

echo ""
echo "Setup complete."
