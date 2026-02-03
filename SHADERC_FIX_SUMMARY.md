# Shaderc CMake Build Issue - Solution Summary

## Problem Statement

When building the Rust portion of MattMC on Windows and macOS, users encountered this error:

```
error: failed to run custom build command for `shaderc-sys v0.8.3`
Caused by:
  process didn't exit successfully: ...
  thread 'main' panicked at ...:
  couldn't find required command: "cmake"
```

This occurred because:
1. The `vulkano-shaders` crate depends on `shaderc` for compile-time shader compilation
2. `shaderc-sys` (a dependency of `shaderc`) tries to build native C++ libraries from source
3. Building from source requires `cmake`, which many users don't have installed

## Solution Implemented

Instead of requiring users to install `cmake` and other build tools, we:

1. **Pre-compiled the shaders** to SPIR-V format using `glslangValidator`
2. **Included the compiled shaders** in the repository (`shaders/compiled/*.spv`)
3. **Removed the `vulkano-shaders` dependency** from `Cargo.toml`
4. **Updated the shader loading code** to load pre-compiled SPIR-V directly using Vulkano's `ShaderModule::new()`

## Technical Details

### Before (with vulkano-shaders)
```rust
pub mod vertex_shader {
    vulkano_shaders::shader! {
        ty: "vertex",
        src: r"
            #version 460
            // GLSL code here
        ",
    }
}
```

This approach:
- ✗ Compiles shaders at build time using shaderc
- ✗ Requires cmake to build shaderc-sys
- ✗ Adds significant build time
- ✗ Fails on systems without cmake

### After (with pre-compiled SPIR-V)
```rust
pub mod vertex_shader {
    pub fn load(device: Arc<Device>) -> Result<Arc<ShaderModule>, ...> {
        let spirv_bytes = include_bytes!("../../../../../shaders/compiled/vertex.spv");
        let spirv_words = spirv::bytes_to_words(spirv_bytes)
            .expect("Failed to convert vertex shader SPIR-V bytes to words");
        let create_info = ShaderModuleCreateInfo::new(&spirv_words);
        unsafe { ShaderModule::new(device, create_info) }
    }
}
```

This approach:
- ✓ Loads pre-compiled shaders at compile time
- ✓ No cmake or build tools required
- ✓ Faster builds
- ✓ Works on Windows, macOS, and Linux out of the box

## Files Added/Modified

### New Files
- `shaders/src/vertex.vert` - GLSL vertex shader source
- `shaders/src/fragment.frag` - GLSL fragment shader source
- `shaders/compiled/vertex.spv` - Pre-compiled vertex shader (SPIR-V)
- `shaders/compiled/fragment.spv` - Pre-compiled fragment shader (SPIR-V)
- `compile_shaders.sh` - Shader compilation script for Linux/macOS
- `compile_shaders.bat` - Shader compilation script for Windows
- `shaders/README.md` - Comprehensive shader documentation

### Modified Files
- `Cargo.toml` - Removed `vulkano-shaders` dependency
- `src/main/rust/client/renderer/shaders.rs` - Updated to load pre-compiled shaders
- `README.md` - Updated to document no cmake requirement
- `RUST-VOXELS.md` - Updated build prerequisites

## Benefits

1. **No Build Dependencies**: Users only need Rust and Vulkan drivers
2. **Cross-Platform**: Works on Windows, macOS, and Linux without modification
3. **Faster Builds**: Shaders compiled once, not on every build
4. **Deterministic**: Everyone uses the same shader bytecode
5. **Better Security**: Fewer dependencies means smaller supply chain attack surface

## Verification

Successfully built the project without cmake installed:
```bash
$ cmake --version
bash: cmake: command not found

$ cargo build
   Compiling mattmc-rust v0.1.0 (/path/to/MattMC)
    Finished `dev` profile [optimized + debuginfo] target(s) in 1m 29s
```

Dependency tree verification:
```bash
$ cargo tree | grep -i "shaderc\|cmake"
# No results - dependencies successfully removed
```

## For Developers

If you need to modify the shaders:

1. Edit the GLSL files in `shaders/src/`
2. Run the compilation script:
   - Linux/macOS: `./compile_shaders.sh`
   - Windows: `compile_shaders.bat`
3. Commit both source and compiled files

See `shaders/README.md` for detailed instructions.

## Conclusion

This fix eliminates the cmake build dependency entirely, allowing the MattMC Rust project to build successfully on Windows, macOS, and Linux without requiring users to install additional build tools.
