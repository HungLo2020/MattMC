#!/bin/bash
# Build script for MattMC Rust/Vulkan implementation

set -e

echo "Building MattMC Rust/Vulkan implementation..."
echo "=========================================="

# Check if Rust is installed
if ! command -v cargo &> /dev/null; then
    echo "Error: Cargo not found. Please install Rust from https://rustup.rs/"
    exit 1
fi

# Build the project
if [ "$1" == "release" ]; then
    echo "Building in RELEASE mode..."
    cargo build --release
    echo ""
    echo "Build complete! Binary location:"
    echo "  rusttarget/release/mattmc-rust"
else
    echo "Building in DEBUG mode..."
    cargo build
    echo ""
    echo "Build complete! Binary location:"
    echo "  rusttarget/debug/mattmc-rust"
    echo ""
    echo "Tip: Run './build-rust.sh release' for an optimized build"
fi

echo ""
echo "To run: cargo run"
echo "  or:   cargo run --release"
