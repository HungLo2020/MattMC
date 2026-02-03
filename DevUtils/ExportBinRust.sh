#!/bin/bash

# Build the Rust project in release mode
cargo build --release

# Check if build was successful
if [ $? -ne 0 ]; then
    echo "Build failed. Exiting."
    exit 1
fi

# Define target directory
TARGET_DIR="/home/matt/Documents/MattMCr"

# Create the target directory if it doesn't exist (but don't wipe if it does)
if [ ! -d "$TARGET_DIR" ]; then
    echo "Creating directory: $TARGET_DIR"
    mkdir -p "$TARGET_DIR"
else
    echo "Directory already exists: $TARGET_DIR"
fi

# Copy the executable to the target directory (only replace the binary)
echo "Copying executable to $TARGET_DIR"
cp rusttarget/release/mattmc-rust "$TARGET_DIR/"

# Check if copy was successful
if [ $? -eq 0 ]; then
    echo "Successfully exported mattmc-rust to $TARGET_DIR"
else
    echo "Failed to copy executable"
    exit 1
fi