# ERROR: "couldn't find required command: cmake"

If you're seeing this error when building the Rust portion of MattMC:

```
error: failed to run custom build command for `shaderc-sys v0.8.3`
...
couldn't find required command: "cmake"
```

## Quick Fix

You need to install **either** the Vulkan SDK or cmake before building.

### Windows

**Option 1: Vulkan SDK (Recommended)**
1. Download: https://vulkan.lunarg.com/sdk/home
2. Run installer (use default options)
3. **Restart your terminal/IDE**
4. Run: `cargo clean && cargo build`

**Option 2: cmake**
1. Download: https://cmake.org/download/
2. During install, select "Add CMake to system PATH"
3. **Restart your terminal/IDE**
4. Run: `cargo clean && cargo build`

OR with Chocolatey:
```powershell
choco install cmake
# Restart terminal
cargo clean && cargo build
```

### macOS

```bash
brew install cmake
# Then build
cargo clean && cargo build
```

OR install Vulkan SDK from https://vulkan.lunarg.com/sdk/home

### Linux

```bash
sudo apt install cmake
# OR
sudo apt install libshaderc-dev
# Then build
cargo clean && cargo build
```

## Why Is This Needed?

The Rust project compiles GLSL shaders to SPIR-V at build time using the `vulkano_shaders::shader!` macro. This process requires the `shaderc` library.

When `shaderc` isn't found on your system, it tries to build from source, which requires `cmake`.

**Solutions:**
1. **Install Vulkan SDK** - Includes pre-built `shaderc` (fastest, recommended)
2. **Install cmake** - Allows building `shaderc` from source (slower first build)

## Still Having Issues?

See [WINDOWS_BUILD.md](WINDOWS_BUILD.md) for detailed Windows instructions, or [CMAKE_SOLUTION.md](CMAKE_SOLUTION.md) for complete documentation.

## After Installing

**Important:** You MUST restart your terminal/IDE after installing cmake or Vulkan SDK for the environment variables to be loaded.

Then:
```bash
cargo clean  # Clear old build artifacts
cargo build  # Build the project
```

The first build may take 1-2 minutes as it compiles dependencies. Subsequent builds are much faster.
