# MattMC Rust Voxel Engine

## Project Overview

MattMC is a high-performance port of Minecraft Java Edition 1.21.10 that is being rewritten from the ground up in Rust with Vulkan graphics. This document outlines the strategy and implementation plan for creating a basic voxel engine in Rust that will eventually replace the Java implementation while maintaining feature parity and asset compatibility.

### Why Rust?

- **Performance**: Native compilation, zero-cost abstractions, and fine-grained memory control
- **Safety**: Memory safety without garbage collection, preventing common bugs
- **Modern Graphics**: Direct Vulkan integration for cutting-edge rendering capabilities
- **Concurrent Design**: Built-in tooling for safe parallel chunk generation and rendering

### Dual-Repository Strategy

**Both Java and Rust codebases will coexist in this repository** during the rewrite process. This approach provides:

1. **Shared Assets**: Both implementations use the same textures, sounds, and resource files from `src/main/resources/assets/`
2. **Reference Implementation**: The Java code serves as a complete reference for game logic and behavior
3. **Incremental Migration**: Features can be ported gradually, with the Java version available for comparison
4. **Build Isolation**: Separate build systems (Gradle for Java, Cargo for Rust) prevent conflicts

## Current State

### Java Implementation (Production)
- **Location**: `src/main/java/`
- **Entry Points**: 
  - Client: `net/minecraft/client/main/Main.java`
  - Server: `net/minecraft/server/Main.java`
- **Build**: `./gradlew runClient` or `./gradlew runServer`
- **Files**: ~8,746 Java source files
- **Status**: Fully functional Minecraft 1.21.10 with mods (Fabric, Sodium, Iris, etc.)

### Rust Implementation (In Development)
- **Location**: `src/main/rust/`
- **Entry Point**: `src/main/rust/main.rs`
- **Build**: `cargo build --release`
- **Run**: `cargo run --release`
- **Status**: Basic Vulkan rendering proof-of-concept (renders a colored square)

### Shared Resources
- **Assets**: `src/main/resources/assets/minecraft/`
  - Textures: Block textures, entity textures, GUI elements
  - Sounds: All game sounds and music
  - Shaders: GLSL shaders (will need conversion to SPIR-V for Vulkan)
  - Models: Block and item models
  - Language files: Translations

## Architecture: Basic Voxel Engine

### Core Components

A minimal voxel engine requires these fundamental systems:

#### 1. **Chunk System**
- **Chunk Size**: 16×16×16 blocks (matches Minecraft)
- **Block Storage**: 3D array of block IDs with metadata
- **Coordinate System**: World coordinates → Chunk coordinates → Local block coordinates
- **Memory Layout**: Flat array for cache efficiency

```rust
// Pseudo-structure
struct Chunk {
    position: ChunkPos,           // World chunk coordinates
    blocks: [BlockId; 16*16*16],  // 4,096 blocks per chunk
    // Future: block states, lighting, entities
}
```

#### 2. **Rendering System**
- **Greedy Meshing**: Combine adjacent identical blocks to reduce triangle count
- **Chunk Meshes**: Pre-generate geometry for each chunk
- **Culling**: Only render visible faces (not adjacent to solid blocks)
- **Batching**: Draw entire chunks in single draw calls

#### 3. **Camera & Input**
- **First-Person Camera**: Position, rotation, FOV
- **Input Handling**: Keyboard/mouse via winit
- **Movement**: WASD movement, mouse look, basic collision

#### 4. **World Generation** (Phase 2)
- **Noise-Based Terrain**: Perlin/Simplex noise for height maps
- **Biomes**: Temperature/humidity-based biome selection
- **Structures**: Trees, caves, ores (later phase)

#### 5. **Block Registry**
- **Block Types**: Air, stone, dirt, grass, etc.
- **Properties**: Solid, transparent, collidable
- **Textures**: Map block IDs to texture atlas coordinates

## Implementation Roadmap

### Phase 1: Minimal Voxel Renderer (Weeks 1-2)

**Goal**: Render a static 3D grid of colored cubes with camera movement

**Tasks**:
- [x] Set up Vulkan window and swap chain (DONE - current main.rs)
- [ ] Implement 3D camera with perspective projection
- [ ] Create vertex structure for cubes (position, color, normal)
- [ ] Generate mesh for a single chunk of random blocks
- [ ] Implement WASD + mouse look camera controls
- [ ] Add basic lighting (directional light)

**Deliverable**: Fly around a single chunk of colored voxels

**Files to Create**:
```
src/main/rust/
├── main.rs                  (existing - update)
├── camera.rs                (camera state and projection)
├── input.rs                 (keyboard/mouse handling)
├── chunk.rs                 (chunk data structure)
├── mesh_builder.rs          (generate vertex buffers from chunks)
└── shaders/
    ├── voxel.vert           (vertex shader with MVP matrix)
    └── voxel.frag           (fragment shader with lighting)
```

### Phase 2: Chunk System & Greedy Meshing (Weeks 3-4)

**Goal**: Efficiently render multiple chunks with optimized geometry

**Tasks**:
- [ ] Implement chunk coordinate system
- [ ] Create chunk manager (load/unload chunks)
- [ ] Implement greedy meshing algorithm
- [ ] Add face culling (don't render hidden faces)
- [ ] Render distance management (load chunks in radius)
- [ ] Add simple block types (air, stone, dirt, grass)

**Deliverable**: Render 10×10 grid of optimized chunks (100 chunks, ~400k blocks)

**Files to Create**:
```
src/main/rust/
├── world/
│   ├── chunk.rs             (chunk data and operations)
│   ├── chunk_manager.rs     (load/unload chunks)
│   ├── block.rs             (block types and registry)
│   └── coord.rs             (coordinate conversion utilities)
└── rendering/
    ├── greedy_mesher.rs     (mesh optimization)
    └── chunk_renderer.rs    (batch rendering)
```

### Phase 3: Textures & Block Registry (Weeks 5-6)

**Goal**: Use actual Minecraft textures from shared assets

**Tasks**:
- [ ] Load textures from `src/main/resources/assets/minecraft/textures/block/`
- [ ] Create texture atlas (combine all block textures)
- [ ] Implement UV mapping for block faces
- [ ] Add block definition system (properties from Java code)
- [ ] Support transparent blocks (glass, water)
- [ ] Implement proper block lighting model

**Deliverable**: Textured world with recognizable Minecraft blocks

**Files to Create**:
```
src/main/rust/
├── assets/
│   ├── texture_atlas.rs     (build atlas from individual textures)
│   ├── texture_loader.rs    (load PNG files)
│   └── block_textures.rs    (map block IDs to atlas coordinates)
└── blocks/
    ├── registry.rs          (block type definitions)
    └── properties.rs        (solid, transparent, collidable, etc.)
```

### Phase 4: World Generation (Weeks 7-8)

**Goal**: Generate infinite terrain procedurally

**Tasks**:
- [ ] Implement noise generation (Perlin/Simplex)
- [ ] Create basic terrain generator (height map)
- [ ] Add biome system (plains, forest, desert)
- [ ] Generate chunks on-demand as player moves
- [ ] Implement chunk saving/loading (NBT format)
- [ ] Add basic structures (trees)

**Deliverable**: Infinite world generation compatible with Minecraft seeds

**Files to Create**:
```
src/main/rust/
├── worldgen/
│   ├── noise.rs             (noise functions)
│   ├── terrain.rs           (height map generation)
│   ├── biomes.rs            (biome selection and features)
│   └── structures.rs        (trees, caves, etc.)
└── storage/
    ├── region.rs            (region file format)
    └── nbt.rs               (NBT serialization - use existing crate)
```

### Phase 5: Player & Physics (Weeks 9-10)

**Goal**: Basic player mechanics and collision

**Tasks**:
- [ ] Implement player entity with bounding box
- [ ] Add collision detection (AABB vs world)
- [ ] Implement gravity and jumping
- [ ] Add block breaking/placing
- [ ] Implement creative flying mode
- [ ] Add hotbar and inventory (basic)

**Deliverable**: Playable survival/creative mode basics

### Phase 6: Advanced Rendering (Weeks 11-12)

**Goal**: Modern rendering features

**Tasks**:
- [ ] Implement skybox rendering
- [ ] Add fog (distance-based)
- [ ] Implement proper lighting (smooth lighting, AO)
- [ ] Add shadow mapping
- [ ] Optimize with frustum culling
- [ ] Add basic particle system

## Development Workflow

### Building the Rust Project

**Prerequisites**:
- Rust 1.70+ (`rustup install stable`)
- Vulkan SDK installed
- C++ compiler (for building dependencies)

**Build Commands**:
```bash
# Development build (faster compile, slower runtime)
cargo build

# Release build (slower compile, optimized)
cargo build --release

# Run development build
cargo run

# Run release build
cargo run --release

# Check code without building
cargo check

# Run tests
cargo test
```

### Project Structure

```
MattMC/
├── Cargo.toml                   # Rust project configuration
├── src/
│   └── main/
│       ├── java/                # Java implementation (reference)
│       ├── rust/                # Rust implementation (in progress)
│       │   ├── main.rs         # Entry point
│       │   └── ...             # Module files
│       └── resources/          # Shared assets
│           └── assets/
│               └── minecraft/  # Textures, sounds, models
├── target/                      # Rust build output (gitignored)
└── run/                         # Runtime directory
```

### Cargo.toml Configuration

Current dependencies:
- **winit**: Cross-platform window creation and input
- **vulkano**: Safe Vulkan bindings
- **vulkano-shaders**: Compile GLSL to SPIR-V at build time
- **bytemuck**: Safe casting between types
- **raw-window-handle**: Platform window handles for Vulkan

Future dependencies to add:
- **glam**: Fast vector/matrix math
- **noise**: Noise generation (Perlin, Simplex)
- **image**: Texture loading
- **fastnbt**: NBT serialization

### Development Tips

1. **Use Java as Reference**: When unsure how a system works, check the Java implementation
2. **Asset Compatibility**: Ensure Rust code can read the same assets as Java
3. **Iterative Development**: Get each phase working before moving to the next
4. **Profile Early**: Use `cargo flamegraph` to identify performance bottlenecks
5. **Safety First**: Leverage Rust's type system to prevent bugs at compile time

## Comparing Java and Rust Implementations

### Finding Equivalent Code

When implementing a feature in Rust, locate the Java equivalent:

**Example: Finding block rendering logic**
```bash
# Search Java codebase
find src/main/java -name "*BlockRenderer*"
grep -r "renderBlock" src/main/java/net/minecraft/client/renderer/
```

**Java locations to reference**:
- **World/Chunks**: `net/minecraft/world/level/chunk/`
- **Blocks**: `net/minecraft/world/level/block/`
- **Rendering**: `net/minecraft/client/renderer/`
- **Entities**: `net/minecraft/world/entity/`
- **World Gen**: `net/minecraft/world/level/levelgen/`

### Key Differences

| Aspect | Java Implementation | Rust Implementation |
|--------|-------------------|-------------------|
| **Graphics** | OpenGL 3.3 | Vulkan (modern, explicit) |
| **Memory** | Garbage collected | Manual (with safety) |
| **Concurrency** | Threads + locks | Async/await + channels |
| **Assets** | Loaded at runtime | Pre-compiled where possible |
| **Mods** | Fabric loader | TBD (future consideration) |

## Testing & Validation

### Manual Testing Checklist

After each phase, verify:
- [ ] Project compiles without errors
- [ ] Application launches and creates window
- [ ] Rendering is visible and correct
- [ ] Frame rate is acceptable (>60 FPS)
- [ ] Controls are responsive
- [ ] No crashes or panics

### Performance Targets

**Minimum Viable Performance**:
- 60+ FPS at 1080p
- 16 chunk render distance
- < 100ms chunk generation time
- < 5ms frame time

**Stretch Goals**:
- 144+ FPS at 1080p
- 32 chunk render distance
- < 50ms chunk generation time
- Multi-threaded chunk loading

## Asset Integration

### Using Minecraft Assets

Both Java and Rust implementations share the same assets:

**Texture Location**: `src/main/resources/assets/minecraft/textures/block/`

**Example blocks**:
- `stone.png` - Stone texture
- `dirt.png` - Dirt texture  
- `grass_block_top.png` - Grass top face
- `grass_block_side.png` - Grass side face

**Loading in Rust**:
```rust
// Load texture from shared assets
let texture_path = "src/main/resources/assets/minecraft/textures/block/stone.png";
let image = image::open(texture_path)?;
```

### Shader Conversion

Java uses GLSL shaders for OpenGL. These need conversion for Vulkan:

**Java shader location**: `src/main/resources/assets/minecraft/shaders/`

**Conversion needed**:
- Update syntax for Vulkan GLSL (version 450+)
- Use explicit bindings for uniforms/samplers
- Convert to SPIR-V via `vulkano-shaders` or `glslc`

## FAQ

### Q: Can I remove the Java code once Rust is working?
**A**: Not recommended during development. Keep both for reference and fallback. Once Rust achieves feature parity, the Java code can be archived or removed.

### Q: Will Rust version support mods like Fabric/Sodium?
**A**: The initial implementation focuses on vanilla features. Mod support is a potential future enhancement requiring a plugin system design.

### Q: How do I debug rendering issues?
**A**: Use Vulkan validation layers (enable in debug builds) and tools like RenderDoc for frame capture and analysis.

### Q: What if I want to contribute?
**A**: Start with simple features in early phases. Familiarize yourself with both codebases. Test thoroughly before submitting changes.

### Q: Can the Rust version load Java worlds?
**A**: This is a goal. The NBT format and region file system should be compatible, allowing world transfer between implementations.

## Next Steps

1. **Set up Rust development environment** (if not already done)
2. **Study the existing proof of concept** in `src/main/rust/main.rs`
3. **Start Phase 1**: Extend the current renderer to support a 3D camera
4. **Iterate**: Build, test, and refine each system
5. **Document**: Keep notes on design decisions and compatibility issues

## Resources

### Learning Vulkan with Rust
- [Vulkano Guide](https://vulkano.rs/guide/)
- [Vulkan Tutorial](https://vulkan-tutorial.com/) (C++, but concepts apply)

### Voxel Engine Development  
- [0fps: Meshing in a Minecraft Game](https://0fps.net/2012/06/30/meshing-in-a-minecraft-game/)
- [Greedy Meshing Article](https://0fps.net/2012/06/30/meshing-in-a-minecraft-game/)

### Minecraft Internals
- [Minecraft Wiki - Java Edition Data Values](https://minecraft.wiki/)
- Current Java implementation in this repository

### Rust Resources
- [The Rust Book](https://doc.rust-lang.org/book/)
- [Rust by Example](https://doc.rust-lang.org/rust-by-example/)

---

**Good luck building the future of MattMC! 🦀⛏️**
