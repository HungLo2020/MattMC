# Shaders

This directory contains the GLSL shaders used by the Rust/Vulkan renderer, organized into source and compiled directories.

## Directory Structure

```
shaders/
├── src/              # GLSL source files
│   ├── vertex.vert   # Vertex shader
│   └── fragment.frag # Fragment shader
└── compiled/         # Pre-compiled SPIR-V binaries
    ├── vertex.spv    # Compiled vertex shader
    └── fragment.spv  # Compiled fragment shader
```

## Why Pre-compiled Shaders?

The shaders are **pre-compiled to SPIR-V** and checked into the repository. This design choice:

1. **Eliminates build dependencies**: No need for cmake, Python, or other build tools required by shaderc
2. **Cross-platform compatibility**: Works out-of-the-box on Windows, macOS, and Linux
3. **Faster builds**: Shaders are compiled once, not every time you build the project
4. **Deterministic builds**: Everyone uses the exact same shader bytecode

## Modifying Shaders

If you need to modify the shaders:

1. Edit the GLSL source files in `shaders/src/`
2. Compile them to SPIR-V using one of the provided scripts:
   - **Linux/macOS**: `./compile_shaders.sh`
   - **Windows**: `compile_shaders.bat`
3. Commit both the source and compiled files

### Prerequisites for Shader Compilation

To compile shaders, you need `glslangValidator` installed:

- **Linux (Ubuntu/Debian)**:
  ```bash
  sudo apt-get install glslang-tools
  ```

- **macOS**:
  ```bash
  brew install glslang
  ```

- **Windows**:
  Download and install the [LunarG Vulkan SDK](https://vulkan.lunarg.com/sdk/home)

### Manual Compilation

If you prefer to compile manually:

```bash
# Vertex shader
glslangValidator -V shaders/src/vertex.vert -o shaders/compiled/vertex.spv

# Fragment shader
glslangValidator -V shaders/src/fragment.frag -o shaders/compiled/fragment.spv
```

## Technical Details

The shaders are loaded at compile-time using Rust's `include_bytes!()` macro, which embeds the SPIR-V bytecode directly into the binary. This happens in `src/main/rust/client/renderer/shaders.rs`.

The previous implementation used `vulkano-shaders` with the `shader!` macro, which compiled shaders at build time using `shaderc`. This required cmake and other native build tools, causing build failures on systems without these dependencies.

## Shader Versions

All shaders use GLSL version 460 (`#version 460`), which corresponds to:
- Vulkan 1.2
- SPIR-V 1.5
- OpenGL 4.6

## Adding New Shaders

To add a new shader:

1. Create the GLSL source file in `shaders/src/`
2. Compile it to SPIR-V: `glslangValidator -V shaders/src/your_shader.ext -o shaders/compiled/your_shader.spv`
3. Update `compile_shaders.sh` and `compile_shaders.bat` to include the new shader
4. Load it in your Rust code using the same pattern as the existing shaders

## Troubleshooting

### "glslangValidator not found"

Install the Vulkan SDK or glslang-tools package as described above.

### Shader compilation errors

Check your GLSL syntax. Common issues:
- Version mismatch (make sure you're using `#version 460`)
- Missing semicolons
- Incorrect layout bindings
- Type mismatches

### Changes not reflected in the app

Make sure you:
1. Recompiled the shaders (run `./compile_shaders.sh` or `compile_shaders.bat`)
2. Rebuilt the Rust project (`cargo build`)
3. The compiled `.spv` files are up to date
