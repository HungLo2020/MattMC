#!/bin/bash

# Rust Development Environment Setup Script for Linux (Debian-based)
# This script installs Rust and Cargo on Debian-based Linux systems

set -e  # Exit on error

echo "=========================================="
echo "Rust Development Environment Setup"
echo "=========================================="
echo ""

# Check if running on a Debian-based system
if ! command -v apt &> /dev/null; then
    echo "Error: This script requires APT package manager (Debian-based systems)"
    exit 1
fi

# Check if Rust is already installed
if command -v rustc &> /dev/null && command -v cargo &> /dev/null; then
    echo "Rust is already installed!"
    rustc --version
    cargo --version
    echo ""
    read -p "Do you want to update Rust? (y/n) " -n 1 -r
    echo ""
    if [[ $REPLY =~ ^[Yy]$ ]]; then
        echo "Updating Rust via rustup..."
        if command -v rustup &> /dev/null; then
            rustup update
            echo "Rust updated successfully!"
        else
            echo "rustup not found. Please reinstall Rust."
        fi
    fi
    exit 0
fi

echo "Rust is not installed. Proceeding with installation..."
echo ""

# Offer two installation methods
echo "Choose installation method:"
echo "1) rustup (Official Rust installer)"
echo "2) APT package manager (RECOMENDED. System packages)"
echo ""
read -p "Enter your choice (1 or 2): " choice

case $choice in
    1)
        echo ""
        echo "Installing Rust using rustup (recommended method)..."
        echo "This will download and install the latest stable Rust toolchain."
        echo ""
        echo "WARNING: This will download and execute the rustup installer script."
        read -p "Do you want to proceed? (y/n) " -n 1 -r
        echo ""
        if [[ ! $REPLY =~ ^[Yy]$ ]]; then
            echo "Installation cancelled."
            exit 0
        fi

        # Update package list
        echo "Updating package list..."
        sudo apt update

        # Install dependencies needed for rustup
        echo "Installing dependencies (curl, build-essential)..."
        sudo apt install -y curl build-essential

        # Download and run rustup installer
        echo "Downloading rustup installer..."
        curl --proto '=https' --tlsv1.2 -sSf https://sh.rustup.rs | sh -s -- -y

        # Source the cargo environment
        source "$HOME/.cargo/env"

        echo ""
        echo "✓ Rust installed successfully via rustup!"
        ;;

    2)
        echo ""
        echo "Installing Rust using APT package manager..."
        echo "Note: APT packages may not be the latest version."
        echo ""

        # Update package list
        echo "Updating package list..."
        sudo apt update

        # Install Rust and Cargo from APT
        echo "Installing rustc and cargo..."
        sudo apt install -y rustup build-essential

        echo ""
        echo "✓ Rust installed successfully via APT!"
        ;;

    *)
        echo "Invalid choice. Exiting."
        exit 1
        ;;
esac

# Verify installation
echo ""
echo "=========================================="
echo "Verifying installation..."
echo "=========================================="

if command -v rustc &> /dev/null; then
    rustc --version
else
    echo "Error: rustc command not found. Installation may have failed."
    exit 1
fi

if command -v cargo &> /dev/null; then
    cargo --version
else
    echo "Error: cargo command not found. Installation may have failed."
    exit 1
fi

echo ""
echo "=========================================="
echo "Setup Complete!"
echo "=========================================="
echo ""
echo "Rust has been installed successfully!"
echo ""

if [ "$choice" = "1" ]; then
    echo "To use Rust in your current shell, run:"
    echo "  source \$HOME/.cargo/env"
    echo ""
    echo "Or restart your terminal for the changes to take effect."
    echo ""
    echo "To update Rust in the future, run:"
    echo "  rustup update"
else
    echo "To update Rust in the future, run:"
    echo "  sudo apt update && sudo apt upgrade rustc cargo"
fi

echo ""
echo "You can now build the project with:"
echo "  cargo build"
echo "  cargo run"