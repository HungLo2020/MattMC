# Building MattMC Rust on Windows

This guide helps you build the Rust portion of MattMC on Windows.

## Quick Start

If you're getting a build error about `cmake` not found, follow these steps:

### Option 1: Install Vulkan SDK (Recommended)

1. **Download Vulkan SDK**
   - Go to https://vulkan.lunarg.com/sdk/home
   - Download the latest Windows installer
   - Run the installer (default options are fine)

2. **Restart your terminal/IDE**
   - Close PowerShell, Command Prompt, or your IDE
   - Open it again (this loads the new environment variables)

3. **Build**
   ```powershell
   cargo clean
   cargo build
   ```

### Option 2: Install cmake

1. **Download cmake**
   - Go to https://cmake.org/download/
   - Download "Windows x64 Installer"
   - During installation, select "Add CMake to system PATH"

   OR if you have Chocolatey:
   ```powershell
   choco install cmake
   ```

2. **Restart your terminal/IDE**
   - Close and reopen your terminal/IDE

3. **Build**
   ```powershell
   cargo clean
   cargo build
   ```

## Why is this needed?

The Rust project uses **compile-time shader compilation**. Shaders are written in GLSL and compiled to SPIR-V at build time using the `vulkano_shaders::shader!` macro.

This process requires the `shaderc` library, which has two options:
1. Use pre-built libraries from the Vulkan SDK (fastest, easiest)
2. Build from source using cmake (requires cmake installed)

## Troubleshooting

### "VULKAN_SDK environment variable not set"

This is just a warning. The build will work if cmake is installed.

To use the Vulkan SDK instead:
1. Install Vulkan SDK as described above
2. Make sure you've restarted your terminal
3. Check: `echo $env:VULKAN_SDK` should show a path
4. If empty, you may need to log out and back in to Windows

### "couldn't find required command: cmake"

You need either the Vulkan SDK or cmake. See installation steps above.

### Build works but takes forever the first time

This is normal! The first build:
- Compiles `shaderc-sys` from source (if Vulkan SDK not found)
- This can take 2-3 minutes
- Subsequent builds are much faster (seconds)

### "error: linker 'link.exe' not found"

You need the Microsoft C++ build tools:
1. Install Visual Studio 2019 or later (Community edition is free)
2. During install, select "Desktop development with C++"
3. OR install just the build tools: https://visualstudio.microsoft.com/downloads/ → "Build Tools for Visual Studio"

### Still having issues?

1. Make sure you have Rust installed: `rustc --version`
2. Update Rust: `rustup update`
3. Clean and rebuild:
   ```powershell
   cargo clean
   cargo build
   ```
4. Check that cmake or Vulkan SDK is in PATH:
   ```powershell
   cmake --version
   # OR
   echo $env:VULKAN_SDK
   ```

## Build Commands

```powershell
# Development build
cargo build

# Release build (optimized)
cargo build --release

# Run the application
cargo run

# Clean build artifacts
cargo clean

# Check code without building binary (faster)
cargo check
```

## Requirements Summary

**Required:**
- Rust 1.70+ (`rustup install stable`)
- Visual Studio C++ build tools OR MinGW
- Either Vulkan SDK OR cmake

**Optional:**
- Vulkan-capable GPU with drivers (for running the application)

## Next Steps

After a successful build, the binary will be at:
- Debug: `rusttarget\debug\mattmc-rust.exe`
- Release: `rusttarget\release\mattmc-rust.exe`

Note: The Rust rewrite is still in early development. See `RUST-VOXELS.md` for the current implementation status.
