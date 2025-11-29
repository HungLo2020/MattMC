#!/usr/bin/env bash

set -e

echo "=== Grayscale Tool Dependency Installer (Kubuntu) ==="

# Make sure we're on a Debian/Ubuntu-style system
if ! command -v apt-get >/dev/null 2>&1; then
	echo "ERROR: This script expects apt-get (Debian/Ubuntu/Kubuntu)."
	exit 1
fi

echo "Updating package lists..."
sudo apt-get update

echo "Installing Python 3, pip, and venv..."
sudo apt-get install -y python3 python3-pip python3-venv

echo "Python version:"
python3 --version || echo "python3 not found in PATH for some reason."

echo "Installing Pillow (Python image library) for current user..."
python3 -m pip install --user --upgrade pip
python3 -m pip install --user pillow

echo
echo "=== All done! ==="
echo "You should now be able to run your grayscale PNG script with:"
echo "  python3 your_script_name.py"
echo
echo "If 'python3 -m pip' isn't found, try opening a new terminal so your PATH updates."
