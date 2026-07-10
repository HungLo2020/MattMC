#!/bin/bash

# UpdateLocalInstall.sh - Build MattMC and update the local exported install
# Builds a fresh jar and Rust native library, then copies them into:
#   /home/matt/Games/MattMC/lib/
#   /home/matt/Games/MattMC/natives/
# Only the native library produced for the current platform is overwritten.
# It also refreshes launcher/helper scripts from packaging/.

set -e

# Find the project root (where gradlew is located)
SCRIPT_DIR="$(cd -- "$(dirname -- "$0")" && pwd)"
PROJECT_ROOT="$SCRIPT_DIR"

# If script is in a subdirectory, search upwards for gradlew
while [ ! -f "$PROJECT_ROOT/gradlew" ] && [ "$PROJECT_ROOT" != "/" ]; do
	PROJECT_ROOT="$(dirname "$PROJECT_ROOT")"
done

if [ ! -f "$PROJECT_ROOT/gradlew" ]; then
	echo "ERROR: Could not find gradlew. Are you in the MattMC project?"
	exit 1
fi

# Change to project root
cd "$PROJECT_ROOT"

# Local install root (override with: INSTALL_ROOT=/path ./UpdateLocalInstall.sh)
INSTALL_ROOT="${INSTALL_ROOT:-/home/matt/Games/MattMC}"
LIB_DIR="$INSTALL_ROOT/lib"
NATIVES_DIR="$INSTALL_ROOT/natives"
SERVER_DIR="$INSTALL_ROOT/server"
PACKAGING_DIR="$PROJECT_ROOT/packaging"
BUILT_NATIVES_DIR="$PROJECT_ROOT/build/rust/native"

resolve_rust_native_file_name() {
	local os_name
	local arch_name
	local os_part
	local arch_part
	local extension

	os_name="$(uname -s | tr '[:upper:]' '[:lower:]')"
	arch_name="$(uname -m | tr '[:upper:]' '[:lower:]')"

	case "$os_name" in
		linux*)
			os_part="linux"
			extension="so"
			;;
		darwin*)
			os_part="mac"
			extension="dylib"
			;;
		mingw*|msys*|cygwin*)
			os_part="win"
			extension="dll"
			;;
		*)
			echo "ERROR: Unsupported OS for Rust native library: $(uname -s)" >&2
			exit 1
			;;
	esac

	case "$arch_name" in
		x86_64|amd64)
			arch_part="x64"
			;;
		aarch64|arm64)
			arch_part="aarch64"
			;;
		*)
			echo "ERROR: Unsupported architecture for Rust native library: $(uname -m)" >&2
			exit 1
			;;
	esac

	printf 'mattmc_rust-%s-%s.%s\n' "$os_part" "$arch_part" "$extension"
}

echo "========================================="
echo "  MattMC Local Install Update"
echo "========================================="
echo ""

echo "[1/6] Building fresh jar and optimized Rust native..."
./gradlew clean buildRustNative jar -PmattmcRustProfile=release --rerun-tasks --no-daemon

echo "[2/6] Locating built jar..."
JAR_FILE="$(find "$PROJECT_ROOT/build/libs" -maxdepth 1 -type f -name "MattMC*.jar" ! -name "*-sources.jar" ! -name "*-javadoc.jar" -printf "%T@ %p\n" 2>/dev/null | sort -nr | head -n 1 | cut -d' ' -f2-)"

if [ -z "$JAR_FILE" ] || [ ! -f "$JAR_FILE" ]; then
	echo "ERROR: Could not find built jar in build/libs!"
	echo "Looked for: $PROJECT_ROOT/build/libs/MattMC*.jar"
	exit 1
fi

echo "    Found: $JAR_FILE"

echo "[3/6] Ensuring local install directories exist..."
mkdir -p "$LIB_DIR" "$NATIVES_DIR" "$SERVER_DIR"

echo "[4/6] Copying jar to $LIB_DIR..."
cp -f "$JAR_FILE" "$LIB_DIR/"

echo "[5/6] Refreshing Rust natives in $NATIVES_DIR..."
if [ ! -d "$BUILT_NATIVES_DIR" ]; then
	echo "ERROR: Rust native output directory does not exist: $BUILT_NATIVES_DIR"
	exit 1
fi

RUST_NATIVE_FILE_NAME="$(resolve_rust_native_file_name)"
BUILT_NATIVE_FILE="$BUILT_NATIVES_DIR/$RUST_NATIVE_FILE_NAME"

if [ ! -f "$BUILT_NATIVE_FILE" ]; then
	echo "ERROR: Expected Rust native library was not produced: $BUILT_NATIVE_FILE"
	exit 1
fi

cp -f "$BUILT_NATIVE_FILE" "$NATIVES_DIR/$RUST_NATIVE_FILE_NAME"
echo "    Updated: $NATIVES_DIR/$RUST_NATIVE_FILE_NAME"

VERSION="$(./gradlew properties -q --no-daemon | awk '/^version:/ {print $2; exit}')"
if [ -z "$VERSION" ]; then
	echo "ERROR: Could not determine project version."
	exit 1
fi

copy_template() {
	local source_file="$1"
	local destination_file="$2"
	local executable="${3:-false}"

	sed "s/@VERSION@/$VERSION/g" "$source_file" > "$destination_file"
	if [ "$executable" = "true" ]; then
		chmod +x "$destination_file"
	fi
}

echo "[6/6] Refreshing packaging scripts..."
for script_file in "$PACKAGING_DIR"/*; do
	[ -f "$script_file" ] || continue
	file_name="$(basename "$script_file")"

	case "$file_name" in
		run-server.sh)
			copy_template "$script_file" "$SERVER_DIR/$file_name" true
			;;
		run-server.bat)
			copy_template "$script_file" "$SERVER_DIR/$file_name"
			;;
		SERVER-README.md)
			cp -f "$script_file" "$SERVER_DIR/README.md"
			;;
		*.sh)
			copy_template "$script_file" "$INSTALL_ROOT/$file_name" true
			;;
		*.bat|*.ps1)
			copy_template "$script_file" "$INSTALL_ROOT/$file_name"
			;;
		*)
			cp -f "$script_file" "$INSTALL_ROOT/$file_name"
			;;
	esac
done

echo ""
echo "========================================="
echo "  Export Complete!"
echo "========================================="
echo ""
echo "Updated local install:"
echo "  $INSTALL_ROOT"
echo ""
echo "Jar:"
echo "  $LIB_DIR/$(basename "$JAR_FILE")"
echo ""
echo "Rust natives:"
find "$NATIVES_DIR" -maxdepth 1 -type f \( -name '*.so' -o -name '*.dll' -o -name '*.dylib' \) -printf "  %p\n" | sort
echo ""
