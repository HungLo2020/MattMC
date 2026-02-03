#!/bin/bash
# Script to compile GLSL shaders to SPIR-V
# This requires glslangValidator to be installed (part of Vulkan SDK or glslang-tools package)

set -e

SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
SHADER_SRC_DIR="$SCRIPT_DIR/shaders/src"
SHADER_OUT_DIR="$SCRIPT_DIR/shaders/compiled"

echo "Compiling shaders..."
echo "Source directory: $SHADER_SRC_DIR"
echo "Output directory: $SHADER_OUT_DIR"

# Create output directory if it doesn't exist
mkdir -p "$SHADER_OUT_DIR"

# Check if glslangValidator is available
if ! command -v glslangValidator &> /dev/null; then
    echo "Error: glslangValidator not found!"
    echo "Please install the Vulkan SDK or glslang-tools package:"
    echo "  - Linux: sudo apt-get install glslang-tools"
    echo "  - macOS: brew install glslang"
    echo "  - Windows: Download from https://vulkan.lunarg.com/sdk/home"
    exit 1
fi

# Compile vertex shader
echo "Compiling vertex shader..."
glslangValidator -V "$SHADER_SRC_DIR/vertex.vert" -o "$SHADER_OUT_DIR/vertex.spv"

# Compile fragment shader
echo "Compiling fragment shader..."
glslangValidator -V "$SHADER_SRC_DIR/fragment.frag" -o "$SHADER_OUT_DIR/fragment.spv"

echo "Shader compilation complete!"
ls -lh "$SHADER_OUT_DIR"
