# MattMC

MattMC is a performance-focused Minecraft clone built from the ground up with an emphasis on modularity, efficiency, and a personal vision of what Minecraft could be. This project represents my take on how Minecraft's development would unfold if I were in charge—prioritizing clean architecture, optimal performance, and thoughtful design decisions.

## Vision

This project reimagines Minecraft with a focus on:

- **Performance First**: Every system is designed with efficiency in mind, from chunk rendering to world storage
- **Modularity**: Clean separation of concerns with a well-organized codebase that follows modern software engineering practices
- **Technical Excellence**: Leveraging advanced techniques like OpenGL display lists, face culling, and optimized data structures
- **Transparency**: Comprehensive documentation of technical decisions and architectural choices

## Key Features

### Performance Optimizations
- **Advanced Chunk Rendering**: Uses OpenGL display lists to cache compiled geometry for significant performance improvements over immediate mode rendering
- **Intelligent Face Culling**: Only renders block faces adjacent to air, dramatically reducing polygon count
- **Chunk Sections**: Divides chunks into 16×16×16 sections, skipping empty sections entirely
- **Efficient World Storage**: Minecraft-inspired Anvil format with region files and NBT compression

### Minecraft-Compatible Architecture
- **Chunk System**: 16×384×16 chunks following Minecraft 1.18+ specifications
- **Y-Coordinate Range**: -64 to 319 (384 blocks total)
- **World Save Format**: Region-based storage with NBT data structures
- **Block Models**: JSON-based block models and blockstates system
- **Light Storage**: Per-voxel light data with 16 levels each for sky and block light
  - 1 byte per block: high nibble = skyLight (0-15), low nibble = blockLight (0-15)
  - Stored per 16×16×16 section for efficiency
  - Column heightmaps track topmost non-air blocks for optimization
- **Smooth Lighting**: Mesh-time per-vertex light sampling with ambient occlusion
  - Samples 8 nearby voxels per vertex for smooth gradients
  - 3-block corner rule for ambient occlusion darkening
  - Runtime toggle via `smooth_lighting` setting
  - See [SMOOTH_LIGHTING.md](docs/SMOOTH_LIGHTING.md) for details
- **Cascaded Shadow Maps (CSM)**: Real-time shadows from directional sunlight
  - 3 cascades (near, mid, far) for optimal quality distribution
  - 2048×2048 depth map per cascade
  - PCF filtering for soft shadow edges
  - Integrated with day/night cycle (only renders during daytime)
  - Runtime toggle via `shadows` setting (disabled by default)
  - Similar to Minecraft Complementary shaders approach
  - See [CASCADED_SHADOW_MAPS.md](docs/CASCADED_SHADOW_MAPS.md) for details

### Game Features
- World creation and management
- Player movement and controls
- Customizable keybindings and settings
- Development/testing screen for experimentation
- Save/load functionality with automatic backups

## Technical Stack

- **Language**: Java 21
- **Graphics**: LWJGL 3.3.4 (OpenGL)
- **Build System**: Gradle with Kotlin DSL
- **Libraries**:
  - LWJGL (OpenGL, GLFW, STB)
  - Gson for JSON parsing

## Project Structure

```
src/main/java/mattmc/
├── client/              # Client-side code
│   ├── main/           # Entry point (Main.java)
│   ├── gui/            # User interface
│   │   ├── screens/    # Game screens (title, pause, options, etc.)
│   │   └── components/ # UI components (buttons, text boxes, etc.)
│   ├── resources/      # Resource management (models, textures)
│   ├── settings/       # Game settings and keybindings
│   └── renderer/       # Rendering systems
│       ├── chunk/      # Chunk rendering
│       ├── block/      # Block rendering
│       └── texture/    # Texture management
├── world/              # World and level management
│   ├── entity/         # Entities (player, mobs, etc.)
│   └── level/          # Level/world systems
│       ├── chunk/      # Chunk system
│       ├── block/      # Block types and properties
│       └── storage/    # World save/load (Anvil format, NBT)
├── nbt/                # NBT (Named Binary Tag) data structures
└── util/               # Utility classes
```

## Building and Running

### Prerequisites
- Java 21 or higher
- Gradle (included via wrapper)

### Build
```bash
./gradlew build
```

### Run Development Version

**Linux/Mac:**
```bash
./RunDev.sh
```

**Windows/Alternative:**
```bash
./gradlew run
```

### Create Distribution
```bash
./gradlew portableZip
```

The distributable zip will be created in `build/releases/`.

## Documentation

For in-depth technical information, see:

- [**Chunk System**](CHUNK_SYSTEM.md) - Details on the chunk-based voxel rendering system
- [**Smooth Lighting**](docs/SMOOTH_LIGHTING.md) - Per-vertex light sampling and ambient occlusion
- [**Cascaded Shadow Maps**](docs/CASCADED_SHADOW_MAPS.md) - CSM implementation similar to Complementary shaders
- [**Day/Night Cycle**](docs/DAY_NIGHT_CYCLE.md) - Sun movement and sky brightness calculations
- [**Efficiency Analysis**](EFFICIENCY_ANALYSIS.md) - Performance optimizations and analysis
- [**World Save Format**](WORLD_SAVE_FORMAT.md) - World storage format and NBT structures
- [**Refactoring Summary**](REFACTORING_SUMMARY.md) - Architectural decisions and code organization

## Development Philosophy

MattMC follows a philosophy of:

1. **Performance over convenience**: Making the hard optimization choices that lead to smooth gameplay
2. **Clean code**: Following Java conventions and Minecraft's proven architectural patterns
3. **Documentation**: Comprehensive technical documentation for all major systems
4. **Modularity**: Clear separation between client, server, and shared code
5. **Maintainability**: Code that's easy to understand, modify, and extend

## Current Status

MattMC is in active development. Current features include:

- ✅ Core rendering engine with optimized chunk rendering
- ✅ Player controls and camera system
- ✅ World generation and storage
- ✅ Per-voxel light storage (skyLight and blockLight)
- ✅ Smooth lighting with per-vertex sampling and ambient occlusion
- ✅ Cascaded Shadow Maps (CSM) with 3-cascade system for realistic shadows
- ✅ Day/night cycle with dynamic lighting
- ✅ Column heightmap tracking
- ✅ GUI system with multiple screens
- ✅ Settings and keybinding management
- ✅ Save/load functionality
- 🚧 Block interactions (in progress)
- 🚧 Multiplayer support (planned)

## License

This is a personal project created for educational purposes and to explore game engine development.

## Acknowledgments

MattMC draws inspiration from Minecraft Java Edition's architecture while implementing a personal vision for performance and design. Special thanks to the Minecraft community and modding scene for demonstrating what's possible with thoughtful optimization and clean code.
