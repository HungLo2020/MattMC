#!/usr/bin/env bash

# SetupProject.sh - Prepare this MattMC checkout for local development.
#
# This script is intended to be the first setup step for a fresh developer
# machine. It ensures Python 3, the Rust Cargo toolchain, and the bounded
# graphics-audit diagnostic tools are available. The graphics tooling is kept
# idempotent and reports the resolved executable/library paths so missing
# observability dependencies fail loudly instead of silently weakening audits.

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

SETUP_FAILURES=0
PYTHON_CMD=""
DEVUTILS_CACHE_ROOT="$PROJECT_ROOT/DevUtils/.cache"

record_setup_failure() {
	echo "ERROR: $*"
	SETUP_FAILURES=1
}

can_run_as_root() {
	if [ "$(id -u)" -eq 0 ]; then
		return 0
	fi
	command -v sudo >/dev/null 2>&1 && sudo -n true >/dev/null 2>&1
}

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
	PYTHON_CMD="$python_cmd"
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

install_packages() {
	if [ "$#" -eq 0 ]; then
		return
	fi
	case "$(uname -s)" in
		Linux)
			if command -v apt-get >/dev/null 2>&1; then
				run_as_root apt-get update
				run_as_root apt-get install -y "$@"
			else
				echo "ERROR: Automatic graphics tool provisioning currently supports apt-based Linux first."
				echo "Install these packages manually, then rerun this script: $*"
				exit 1
			fi
			;;
		*)
			echo "ERROR: Automatic graphics tool provisioning is currently implemented for Linux/Kubuntu."
			echo "Install these tools manually, then rerun this script: $*"
			exit 1
			;;
	esac
}

try_install_packages() {
	if [ "$#" -eq 0 ]; then
		return 0
	fi
	if ! can_run_as_root; then
		echo "Cannot install packages without passwordless sudo/root: $*"
		return 1
	fi
	install_packages "$@"
}

command_path() {
	command -v "$1" 2>/dev/null || true
}

download_url() {
	local url="$1"
	local destination="$2"
	if command -v curl >/dev/null 2>&1; then
		curl -L --fail --show-error --silent "$url" -o "$destination"
	elif command -v wget >/dev/null 2>&1; then
		wget -qO "$destination" "$url"
	else
		echo "ERROR: Neither curl nor wget is installed; cannot download $url"
		return 1
	fi
}

remove_stale_cmake_cache() {
	local build_dir="$1"
	local expected_source="$2"
	local cache="$build_dir/CMakeCache.txt"
	if [ ! -f "$cache" ]; then
		return
	fi
	if ! grep -F "CMAKE_HOME_DIRECTORY:INTERNAL=$expected_source" "$cache" >/dev/null 2>&1; then
		echo "Removing stale CMake cache: $build_dir"
		rm -rf "$build_dir"
	fi
}

require_command() {
	local name="$1"
	local purpose="$2"
	local path
	path="$(command_path "$name")"
	if [ -z "$path" ]; then
		echo "ERROR: Missing $name ($purpose)."
		exit 1
	fi
	echo "$name: $path"
}

find_validation_layer_manifest() {
	for path in \
		"${VULKAN_SDK:-}/share/vulkan/explicit_layer.d/VkLayer_khronos_validation.json" \
		"/usr/share/vulkan/explicit_layer.d/VkLayer_khronos_validation.json" \
		"/usr/local/share/vulkan/explicit_layer.d/VkLayer_khronos_validation.json" \
		"/etc/vulkan/explicit_layer.d/VkLayer_khronos_validation.json"; do
		if [ -f "$path" ]; then
			printf '%s\n' "$path"
			return 0
		fi
	done
	return 1
}

ensure_vulkan_validation_tools() {
	local missing=()
	if ! command -v vulkaninfo >/dev/null 2>&1; then
		missing+=(vulkan-tools)
	fi
	if ! find_validation_layer_manifest >/dev/null 2>&1; then
		missing+=(vulkan-validationlayers)
	fi
	if [ "${#missing[@]}" -gt 0 ]; then
		echo "Installing Vulkan SDK/runtime audit tools: ${missing[*]}"
		install_packages "${missing[@]}"
	fi

	require_command vulkaninfo "Vulkan runtime/device inspection"
	report_shader_compiler
	local manifest
	if ! manifest="$(find_validation_layer_manifest)"; then
		echo "ERROR: VK_LAYER_KHRONOS_validation manifest was not found."
		exit 1
	fi
	echo "VK_LAYER_KHRONOS_validation manifest: $manifest"
	vulkaninfo --summary >/dev/null
}

report_shader_compiler() {
	local cargo_toml="$PROJECT_ROOT/src/main/rust/Cargo.toml"
	local compiler_source="$PROJECT_ROOT/src/main/rust/render/vulkanic/backends/vulkan/shaderc_spirv_compiler.rs"
	if [ -f "$cargo_toml" ]; then
		local version
		version="$(sed -n 's/.*shaderc *=.*version *= *"\([^"]*\)".*/\1/p' "$cargo_toml" | head -n 1)"
		echo "shader compiler: mattmc_rust:shaderc${version:+ crate version $version}"
	else
		echo "shader compiler: mattmc_rust:shaderc (Cargo.toml unavailable)"
	fi
	if [ -f "$compiler_source" ]; then
		echo "shader compiler source: $compiler_source"
	fi
	echo "glslangValidator: not required by MattMC shader compilation; RenderDoc may bundle its own copy for replay tooling"
}

ensure_renderdoc() {
	local renderdoc_root="$DEVUTILS_CACHE_ROOT/tools/renderdoc"
	local local_renderdoccmd="$renderdoc_root/bin/renderdoccmd"
	local local_qrenderdoc="$renderdoc_root/bin/qrenderdoc"

	if ! command -v renderdoccmd >/dev/null 2>&1 && [ ! -x "$local_renderdoccmd" ]; then
		echo "Installing RenderDoc command-line/replay tooling..."
		if ! try_install_packages renderdoc; then
			echo "System package install unavailable; provisioning RenderDoc under $renderdoc_root"
			local renderdoc_url
			renderdoc_url="$("$PYTHON_CMD" - <<'PY'
import re
import sys
import urllib.request

html = urllib.request.urlopen("https://renderdoc.org/builds", timeout=20).read().decode("utf-8", "replace")
match = re.search(r'https://renderdoc\.org/stable/[^"]+/renderdoc_[^"]+\.tar\.gz', html)
if not match:
    raise SystemExit("Could not find a stable Linux RenderDoc tarball URL")
print(match.group(0))
PY
)" || {
				record_setup_failure "RenderDoc is unavailable and the official Linux tarball URL could not be resolved."
				return
			}
			mkdir -p "$renderdoc_root"
			local tarball
			tarball="$(mktemp --suffix=.tar.gz)"
			if ! download_url "$renderdoc_url" "$tarball"; then
				rm -f "$tarball"
				record_setup_failure "RenderDoc is unavailable and the official Linux tarball could not be downloaded."
				return
			fi
			rm -rf "$renderdoc_root/extracted"
			mkdir -p "$renderdoc_root/extracted" "$renderdoc_root/bin"
			if ! tar -xzf "$tarball" -C "$renderdoc_root/extracted"; then
				rm -f "$tarball"
				record_setup_failure "RenderDoc tarball downloaded but could not be extracted."
				return
			fi
			rm -f "$tarball"
			local extracted_renderdoccmd extracted_qrenderdoc
			extracted_renderdoccmd="$(find "$renderdoc_root/extracted" -type f -perm -111 -name renderdoccmd -print -quit)"
			extracted_qrenderdoc="$(find "$renderdoc_root/extracted" -type f -perm -111 -name qrenderdoc -print -quit)"
			if [ -z "$extracted_renderdoccmd" ]; then
				record_setup_failure "RenderDoc tarball did not contain renderdoccmd."
				return
			fi
			ln -sf "$extracted_renderdoccmd" "$local_renderdoccmd"
			if [ -n "$extracted_qrenderdoc" ]; then
				ln -sf "$extracted_qrenderdoc" "$local_qrenderdoc"
			fi
		fi
	fi
	local renderdoccmd_bin
	renderdoccmd_bin="$(command -v renderdoccmd || printf '%s' "$local_renderdoccmd")"
	if [ ! -x "$renderdoccmd_bin" ]; then
		record_setup_failure "RenderDoc is unavailable. Install renderdoc so renderdoccmd is on PATH."
		return
	fi
	echo "renderdoccmd: $renderdoccmd_bin"
	"$renderdoccmd_bin" --version 2>/dev/null || "$renderdoccmd_bin" --help >/dev/null
	if command -v qrenderdoc >/dev/null 2>&1; then
		echo "qrenderdoc: $(command -v qrenderdoc)"
	elif [ -x "$local_qrenderdoc" ]; then
		echo "qrenderdoc: $local_qrenderdoc"
	fi
}

ensure_tracy() {
	local tracy_root="$DEVUTILS_CACHE_ROOT/tools/tracy"
	local tracy_version="v0.13.1"
	local tracy_capture="$tracy_root/bin/tracy-capture"
	local tracy_csvexport="$tracy_root/bin/tracy-csvexport"
	local tracy_profiler="$tracy_root/bin/tracy-profiler"
	local tracy_needs_build=false

	if { ! command -v tracy-capture >/dev/null 2>&1 && [ ! -x "$tracy_capture" ]; } || { ! command -v tracy-csvexport >/dev/null 2>&1 && [ ! -x "$tracy_csvexport" ]; }; then
		tracy_needs_build=true
	fi
	if [ ! -d "$tracy_root/src/.git" ]; then
		tracy_needs_build=true
	elif [ "$(git -C "$tracy_root/src" describe --tags --always 2>/dev/null || true)" != "$tracy_version" ]; then
		tracy_needs_build=true
	fi

	if [ "$tracy_needs_build" = true ]; then
		echo "Provisioning Tracy capture tooling under $tracy_root"
		local missing_commands=()
		for command_name in git cmake make g++ pkg-config; do
			if ! command -v "$command_name" >/dev/null 2>&1; then
				missing_commands+=("$command_name")
			fi
		done
		if [ "${#missing_commands[@]}" -gt 0 ]; then
			if ! try_install_packages "${missing_commands[@]}"; then
				record_setup_failure "Tracy build prerequisites are unavailable: ${missing_commands[*]}"
				return
			fi
		fi
		mkdir -p "$tracy_root"
		if [ ! -d "$tracy_root/src/.git" ]; then
			git clone --depth 1 --branch "$tracy_version" https://github.com/wolfpld/tracy.git "$tracy_root/src"
		else
			git -C "$tracy_root/src" fetch --depth 1 origin "refs/tags/$tracy_version:refs/tags/$tracy_version"
			git -C "$tracy_root/src" checkout --detach "$tracy_version"
		fi
		remove_stale_cmake_cache "$tracy_root/build-capture" "$tracy_root/src/capture"
		remove_stale_cmake_cache "$tracy_root/build-csvexport" "$tracy_root/src/csvexport"
		if ! cmake \
			-S "$tracy_root/src/capture" \
			-B "$tracy_root/build-capture" \
			-DCMAKE_BUILD_TYPE=Release \
			-DCMAKE_CXX_FLAGS="-Wno-error=stringop-overflow"; then
			record_setup_failure "Tracy capture configure failed. Install Tracy build dependencies and rerun setup."
			return
		fi
		if ! cmake --build "$tracy_root/build-capture" --parallel; then
			record_setup_failure "Tracy capture build failed. Install Tracy build dependencies and rerun setup."
			return
		fi
		if ! cmake \
			-S "$tracy_root/src/csvexport" \
			-B "$tracy_root/build-csvexport" \
			-DCMAKE_BUILD_TYPE=Release \
			-DCMAKE_CXX_FLAGS="-Wno-error=stringop-overflow"; then
			record_setup_failure "Tracy csvexport configure failed. Install Tracy build dependencies and rerun setup."
			return
		fi
		if ! cmake --build "$tracy_root/build-csvexport" --parallel; then
			record_setup_failure "Tracy csvexport build failed. Install Tracy build dependencies and rerun setup."
			return
		fi
		mkdir -p "$tracy_root/bin"
		find "$tracy_root/build-capture" -type f -perm -111 -name 'tracy-capture*' -exec cp {} "$tracy_capture" \; -quit
		find "$tracy_root/build-csvexport" -type f -perm -111 -name 'tracy-csvexport*' -exec cp {} "$tracy_csvexport" \; -quit
	fi

	if [ ! -x "$tracy_capture" ] && ! command -v tracy-capture >/dev/null 2>&1; then
		record_setup_failure "Tracy capture tool could not be provisioned."
		return
	fi
	if [ ! -x "$tracy_csvexport" ] && ! command -v tracy-csvexport >/dev/null 2>&1; then
		record_setup_failure "Tracy csvexport tool could not be provisioned."
		return
	fi
	echo "tracy-capture: $(command -v tracy-capture || printf '%s' "$tracy_capture")"
	echo "tracy-csvexport: $(command -v tracy-csvexport || printf '%s' "$tracy_csvexport")"
	if [ -x "$tracy_profiler" ] || command -v tracy-profiler >/dev/null 2>&1; then
		echo "tracy-profiler: $(command -v tracy-profiler || printf '%s' "$tracy_profiler")"
	else
		echo "tracy-profiler: unavailable (capture CLI is sufficient for automated harness capture)"
	fi
	if [ -d "$tracy_root/src/public" ]; then
		echo "Tracy headers/client sources: $tracy_root/src/public"
	fi
}

echo "========================================="
echo "  MattMC Project Setup"
echo "========================================="
echo ""

ensure_python
echo ""
ensure_cargo

echo ""
ensure_vulkan_validation_tools

echo ""
ensure_renderdoc

echo ""
ensure_tracy

echo ""
if [ "$SETUP_FAILURES" -ne 0 ]; then
	echo "Setup completed with missing required graphics audit tooling."
	exit 1
fi

echo "Setup complete."
