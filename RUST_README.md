# MattMC Rust/Vulkan Implementation

This directory contains the Rust/Vulkan reimplementation of MattMC.

## Requirements

- Rust 1.70 or later (install via [rustup](https://rustup.rs/))
  - The project uses features from Rust 2021 edition
  - Vulkano 0.34 and winit 0.28 compatibility tested with Rust 1.70+
- Vulkan SDK installed on your system
  - Windows: Download from [LunarG](https://vulkan.lunarg.com/)
  - Linux: Install via package manager (e.g., `sudo apt install vulkan-tools libvulkan-dev` on Ubuntu)
  - macOS: Install via MoltenVK

## Building

The project is configured to use custom build directories:
- Build artifacts: `rusttarget/` (instead of default `target/`)
- Runtime files: `rustrun/` (for future use)

To build the project:

```bash
# From the repository root
cargo build

# For release build (optimized)
cargo build --release
```

## Running

To run the demo:

```bash
# Debug build
cargo run

# Release build
cargo run --release
```

The demo will:
1. Create a window titled "MattMC Rust/Vulkan - Square Demo"
2. Initialize Vulkan and select the best available GPU
3. Render an orange square in the center of the window
4. Window can be closed by clicking the X button or pressing Alt+F4

## Project Structure

```
.
├── Cargo.toml                    # Rust project configuration
├── .cargo/
│   └── config.toml              # Cargo configuration (custom build directory)
├── src/main/rust/
│   └── main.rs                  # Main Rust application
├── rusttarget/                  # Build artifacts (gitignored)
├── rustrun/                     # Runtime directory (gitignored, for future use)
└── rust-core/                   # Core dump directory (gitignored)
```

**Note about rust-core directory**: If the Rust application crashes, it may create a `rust-core/` directory containing core dump files for debugging. This directory is automatically gitignored. If you see this directory and want to investigate crashes, the core dump files inside can be analyzed with debuggers like `gdb` or `lldb`. You can safely delete this directory if you don't need to debug crashes.

## Shared Resources

The Rust implementation shares resources with the Java version:
- Resources are located in `src/main/resources/`
- Future work will integrate texture loading and other assets from this directory

## Technical Details

### Dependencies

- **winit**: Cross-platform window creation and event handling
- **vulkano**: Safe Rust wrapper for Vulkan
- **vulkano-shaders**: Compile-time GLSL shader compilation
- **bytemuck**: Safe type conversions for GPU data

### Graphics Pipeline

The current demo implements:
- Vulkan instance and device initialization
- Swapchain for presentation
- Render pass with color attachment
- Graphics pipeline with vertex and fragment shaders
- Simple vertex buffer with 6 vertices (2 triangles forming a square)
- Window resizing support
- Continuous rendering loop

### Shaders

Shaders are embedded in the Rust code using the `vulkano_shaders::shader!` macro:
- **Vertex Shader**: Passes vertex positions directly to clip space
- **Fragment Shader**: Outputs a solid orange color (RGB: 1.0, 0.5, 0.2)

## Future Development

The Rust/Vulkan implementation will eventually include:
- 3D rendering with perspective projection
- Texture loading and mapping
- Model loading (blocks, items, entities)
- Chunk rendering system
- Lighting and shaders
- GUI rendering
- Integration with Java resources

## Building for Production

For production builds:

```bash
cargo build --release
```

The optimized binary will be located at:
- `rusttarget/release/mattmc-rust` (Linux/macOS)
- `rusttarget\release\mattmc-rust.exe` (Windows)

## Troubleshooting

### rust-core Directory Appears

If you see a `rust-core/` directory in the project root, this indicates the application crashed and created core dump files. This directory is gitignored automatically.

**What is it?** 
- Core dump files from Rust program crashes
- Contains memory dumps for debugging

**What to do?**
- The directory is safe to delete if you don't need crash debugging
- If the crashes persist, the core files can be analyzed with debuggers (`gdb`, `lldb`)
- Report persistent crashes as issues with the core dump information

**Why gitignored?**
- Core dumps can be very large (hundreds of MB)
- They contain system-specific information
- Not useful for version control
