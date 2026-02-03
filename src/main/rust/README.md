# MattMC Rust Implementation

This directory contains the Rust/Vulkan rewrite of MattMC, organized to mirror the Java implementation structure.

## Structure

```
src/main/rust/
├── main.rs                  # Entry point, window/Vulkan setup
└── client/                  # Client-side code (mirrors Java client/)
    └── renderer/            # Rendering subsystem (mirrors Java renderer/)
        ├── mod.rs           # Module declarations
        ├── cube.rs          # Cube geometry and vertex definitions
        ├── camera.rs        # Camera and transformation matrices
        └── shaders.rs       # Vertex and fragment shaders
```

## Current Implementation

The current implementation demonstrates:

- **3D Rendering**: A colorful rotating cube rendered with Vulkan
- **Modular Architecture**: Code organized similar to Java's `net.minecraft.client.renderer` structure
- **Camera System**: Perspective projection with Model-View-Projection matrices
- **Animation**: Time-based rotation on multiple axes
- **Colored Faces**: Each face of the cube has a different color:
  - Front: Red
  - Back: Green
  - Top: Blue
  - Bottom: Yellow
  - Right: Magenta
  - Left: Cyan

## Building and Running

```bash
# Build the project
cargo build

# Run in development mode
cargo run

# Build optimized release version
cargo build --release

# Run release version
cargo run --release
```

## Technical Details

### Vertex Format
- **Position**: 3D float vector (xyz)
- **Color**: RGB float vector

### Rendering Pipeline
1. Cube vertices generated with per-face colors (36 vertices total)
2. MVP matrix calculated each frame:
   - Model: Rotation based on elapsed time
   - View: Camera at (0, 0, 3) looking at origin
   - Projection: Vulkan-compatible perspective (Y-flipped, depth [0,1])
   - Projection: Perspective with 45° FOV
3. Push constants send MVP matrix to vertex shader
4. Vertex shader transforms positions, passes colors to fragment shader
5. Fragment shader outputs interpolated colors

### Shaders
- **Vertex Shader**: Transforms vertex positions using MVP matrix, passes colors
- **Fragment Shader**: Outputs interpolated vertex colors

## Next Steps

See [RUST-VOXELS.md](../../../RUST-VOXELS.md) in the project root for the full roadmap:

1. ✅ **Phase 1 (Partial)**: Basic 3D rendering with rotating cube
2. **Phase 1 (Remaining)**: Add WASD + mouse look camera controls
3. **Phase 2**: Implement chunk system with greedy meshing
4. **Phase 3**: Add textures from shared assets
5. **Phase 4**: Implement world generation
6. **Phase 5**: Add player physics and interaction
7. **Phase 6**: Advanced rendering features

## Dependencies

- **vulkano**: Safe Vulkan bindings for Rust
- **winit**: Cross-platform window creation
- **glam**: Fast 3D math library for vectors and matrices
- **bytemuck**: Safe transmutation between types
