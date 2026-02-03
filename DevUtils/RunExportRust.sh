#!/bin/bash

# Build the Rust project in release mode
cargo build --release

# Check if build was successful
if [ $? -ne 0 ]; then
    echo "Build failed. Exiting."
    exit 1
fi

# Define buildrust directory in project root
BUILDRUST_DIR="buildrust"
MATTMCR_DIR="$BUILDRUST_DIR/MattMCr"
BINARY_PATH="rusttarget/release/mattmc-rust"

# Clean buildrust directory if it exists
if [ -d "$BUILDRUST_DIR" ]; then
    echo "Cleaning existing buildrust directory..."
    rm -rf "$BUILDRUST_DIR"/*
else
    echo "Creating buildrust directory..."
    mkdir -p "$BUILDRUST_DIR"
fi

# Create MattMCr subdirectory
echo "Creating MattMCr directory in buildrust..."
mkdir -p "$MATTMCR_DIR"

# Copy release binary to MattMCr folder
echo "Copying binary to MattMCr folder..."
cp "$BINARY_PATH" "$MATTMCR_DIR/"

# Copy release binary directly to buildrust directory
echo "Copying binary to buildrust directory..."
cp "$BINARY_PATH" "$BUILDRUST_DIR/"

# Zip the MattMCr directory
echo "Zipping MattMCr directory..."
cd "$BUILDRUST_DIR"
zip -r MattMCr.zip MattMCr
cd ..

# Check if copy was successful
if [ $? -eq 0 ]; then
    echo "Successfully exported mattmc-rust!"
    echo "  - Binary in: $BUILDRUST_DIR/mattmc-rust"
    echo "  - MattMCr folder in: $MATTMCR_DIR"
    echo "  - Zip file: $BUILDRUST_DIR/MattMCr.zip"
else
    echo "Failed to create export"
    exit 1
fi