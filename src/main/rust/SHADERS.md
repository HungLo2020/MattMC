# Shader Compilation in MattMC Rust

This document explains how shaders are compiled in the MattMC Rust/Vulkan rewrite.

## Approach: Compile-Time Shader Compilation

Shaders are **compiled at build time** from GLSL source code embedded inline in Rust files. This is done using the `vulkano_shaders::shader!` procedural macro.

## Location

Shaders are located in: `src/main/rust/client/renderer/shaders.rs`

## How It Works

1. **Shader Source**: GLSL shader code is written inline in Rust using raw string literals
2. **Build-Time Compilation**: When you run `cargo build`, the `vulkano_shaders::shader!` macro:
   - Compiles the GLSL to SPIR-V using shaderc
   - Generates Rust bindings for shader inputs/outputs
   - Creates type-safe interfaces for push constants, vertex inputs, etc.
3. **Runtime**: The compiled SPIR-V is embedded in the binary and loaded at runtime

## Example

```rust
pub mod vertex_shader {
    vulkano_shaders::shader! {
        ty: "vertex",
        src: r"
            #version 460

            layout(location = 0) in vec3 position;
            layout(location = 1) in vec3 color;

            layout(push_constant) uniform PushConstants {
                mat4 mvp;
            } push_constants;

            layout(location = 0) out vec3 frag_color;

            void main() {
                gl_Position = push_constants.mvp * vec4(position, 1.0);
                frag_color = color;
            }
        ",
    }
}
```

## Benefits

✅ **Type Safety**: The macro generates Rust types that match shader interfaces
✅ **No External Files**: Shaders are kept with the code that uses them
✅ **Compile-Time Validation**: Shader errors are caught during build, not at runtime
✅ **Automatic Reflection**: Push constants, vertex formats, etc. are automatically defined
✅ **Cross-Platform**: Works the same on Linux, Windows, and macOS

## Build Dependencies

The first time you build the project, `shaderc-sys` (a dependency of `vulkano-shaders`) will be built from source. This requires:

- **C++ Compiler**:
  - Linux: `gcc` or `clang` (usually pre-installed)
  - macOS: Xcode Command Line Tools (`xcode-select --install`)
  - Windows: MSVC (Visual Studio) or MinGW

- **Optional**: Vulkan SDK
  - If installed, shaderc may use the SDK's libraries instead of building from source
  - Set `VULKAN_SDK` environment variable to the SDK path

After the first build, subsequent builds are much faster as `shaderc-sys` is cached.

## Modifying Shaders

To modify shaders:

1. Edit the GLSL code in `src/main/rust/client/renderer/shaders.rs`
2. Run `cargo build` to recompile
3. Shader compilation errors will be shown in the build output

Example build error for invalid shader:
```
error: failed to compile shader
  --> src/main/rust/client/renderer/shaders.rs:5:5
   |
5  |     vulkano_shaders::shader! {
   |     ^^^^^^^^^^^^^^^^^^^^^^^^
   |
   = note: ERROR: 0:15: 'unknown_variable' : undeclared identifier
```

## Adding New Shaders

To add a new shader:

1. Create a new module in `shaders.rs`:
```rust
pub mod my_shader {
    vulkano_shaders::shader! {
        ty: "vertex",  // or "fragment", "compute", etc.
        src: r"
            #version 460
            // Your GLSL code here
        ",
    }
}
```

2. Use it in your code:
```rust
use crate::client::renderer::shaders::my_shader;

let shader = my_shader::load(device.clone()).unwrap();
```

## Alternative: External Shader Files

If you prefer to keep shaders in separate `.vert`/`.frag` files, you can use:

```rust
pub mod vertex_shader {
    vulkano_shaders::shader! {
        ty: "vertex",
        path: "src/main/rust/client/renderer/shaders/vertex.glsl",
    }
}
```

This requires creating the shader file at the specified path.

## Troubleshooting

### Build fails with "couldn't find required command: cmake"

This can happen if shaderc-sys tries to build its dependencies from source. Solutions:

1. Install cmake: `apt install cmake` (Linux) or download from cmake.org
2. Install the Vulkan SDK, which includes pre-built shaderc libraries
3. The build will work even without cmake on most systems after shaderc-sys is cached

### Shader compilation is slow

- First build is slow because shaderc-sys must be compiled
- Subsequent builds are much faster
- Use `cargo check` for faster iteration (doesn't generate binary)

### Shader changes not reflected

Make sure you're rebuilding after changes:
```bash
cargo clean  # Optional: force full rebuild
cargo build
```

## Technical Details

- **GLSL Version**: 460 (Vulkan 1.2, SPIR-V 1.5)
- **Shader Compiler**: shaderc (Google's shader compiler)
- **Output Format**: SPIR-V bytecode
- **Macro**: `vulkano_shaders::shader!` procedural macro
- **Build Script**: `build.rs` provides build-time configuration

## References

- [Vulkano Documentation](https://vulkano.rs/)
- [vulkano-shaders Documentation](https://docs.rs/vulkano-shaders/)
- [GLSL Language Specification](https://www.khronos.org/opengl/wiki/Core_Language_(GLSL))
- [Vulkan GLSL Extensions](https://github.com/KhronosGroup/GLSL/blob/master/extensions/khr/GL_KHR_vulkan_glsl.txt)
