# Project Structure

## Overview

MattMC is a decompiled version of Minecraft Java Edition 1.21.10, containing both client and server source code. The project is organized into several major packages representing different aspects of the game engine.

## Directory Tree

```
MattMC/
├── build.gradle              # Gradle build configuration
├── settings.gradle           # Gradle settings
├── gradle.properties         # Build properties
├── gradlew / gradlew.bat     # Gradle wrapper scripts
├── gradle/                   # Gradle wrapper files
│   └── wrapper/
├── libraries/                # External libraries and bundled JDK
├── src/                      # Resource files
│   └── main/
│       └── resources/
│           └── version.json  # Version information
├── com/                      # Mojang library code
│   └── mojang/
│       ├── authlib/          # Authentication library
│       ├── blaze3d/          # 3D rendering engine (OpenGL wrapper)
│       ├── logging/          # Logging utilities
│       ├── math/             # Math utilities (vectors, matrices, transformations)
│       └── realmsclient/     # Minecraft Realms client code
├── net/                      # Main Minecraft code
│   ├── fabricmc/             # Fabric API implementation
│   │   └── fabric/
│   └── minecraft/            # Core Minecraft packages
│       ├── advancements/     # Advancement system
│       ├── client/           # Client-only code
│       │   ├── main/         # Client entry point
│       │   ├── gui/          # GUI and HUD systems
│       │   ├── renderer/     # Rendering pipeline
│       │   ├── multiplayer/  # Client-side multiplayer
│       │   ├── resources/    # Resource loading
│       │   └── sounds/       # Sound system
│       ├── commands/         # Command system (Brigadier)
│       ├── core/             # Core data structures and registries
│       ├── data/             # Data generation system
│       ├── gametest/         # Game testing framework
│       ├── locale/           # Localization
│       ├── nbt/              # NBT (Named Binary Tag) data format
│       ├── network/          # Networking layer
│       │   ├── protocol/     # Network protocol definitions
│       │   ├── chat/         # Chat system
│       │   ├── codec/        # Packet encoding/decoding
│       │   └── syncher/      # Entity data synchronization
│       ├── obfuscate/        # Obfuscation annotations
│       ├── realms/           # Realms integration
│       ├── recipebook/       # Recipe book UI
│       ├── references/       # Reference tracking
│       ├── resources/        # Resource management
│       ├── server/           # Server-only code
│       │   ├── Main.java     # Server entry point
│       │   ├── commands/     # Server commands
│       │   ├── level/        # Server-side world management
│       │   ├── network/      # Server networking
│       │   └── players/      # Player management
│       ├── sounds/           # Sound definitions
│       ├── stats/            # Statistics tracking
│       ├── tags/             # Tag system (block tags, item tags, etc.)
│       ├── util/             # Utility classes
│       └── world/            # World simulation
│           ├── damagesource/ # Damage calculation
│           ├── effect/       # Status effects
│           ├── entity/       # Entity system
│           │   ├── ai/       # AI and pathfinding
│           │   ├── animal/   # Animal entities
│           │   ├── boss/     # Boss entities
│           │   ├── decoration/ # Decoration entities (paintings, etc.)
│           │   ├── item/     # Item entities
│           │   ├── monster/  # Hostile entities
│           │   ├── npc/      # Villagers and NPCs
│           │   ├── player/   # Player entity
│           │   ├── projectile/ # Projectile entities
│           │   └── vehicle/  # Vehicle entities (boats, minecarts)
│           ├── food/         # Food mechanics
│           ├── inventory/    # Inventory and container system
│           ├── item/         # Item system
│           │   ├── alchemy/  # Potion brewing
│           │   ├── armortrim/ # Armor customization
│           │   ├── component/ # Item components (data)
│           │   ├── crafting/  # Crafting recipes
│           │   └── enchantment/ # Enchantment system
│           ├── level/        # World (level) management
│           │   ├── biome/    # Biome system
│           │   ├── block/    # Block system
│           │   │   ├── entity/ # Block entities (tile entities)
│           │   │   └── state/  # Block state management
│           │   ├── chunk/    # Chunk loading and generation
│           │   ├── dimension/ # Dimension management
│           │   ├── gameevent/ # Game event system
│           │   ├── levelgen/  # World generation
│           │   │   ├── blending/ # Terrain blending
│           │   │   ├── carver/   # Cave carvers
│           │   │   ├── feature/  # World features (trees, ores, etc.)
│           │   │   ├── flat/     # Superflat generation
│           │   │   ├── heightmaps/ # Heightmap system
│           │   │   ├── placement/ # Feature placement
│           │   │   ├── structure/  # Structure generation
│           │   │   ├── synth/     # Noise synthesis
│           │   │   └── presets/   # World generation presets
│           │   ├── lighting/  # Lighting system
│           │   ├── portal/    # Portal mechanics
│           │   ├── redstone/  # Redstone system
│           │   ├── saveddata/ # Persistent world data
│           │   ├── storage/   # World save/load
│           │   └── timers/    # World timers
│           ├── phys/         # Physics simulation
│           ├── scores/       # Scoreboard system
│           ├── ticks/        # Tick scheduling
│           └── waypoints/    # Waypoint system
└── run/                      # Runtime directory (created on first run)
    ├── assets/               # Game assets (textures, sounds)
    ├── logs/                 # Log files
    ├── saves/                # World saves
    ├── server.properties     # Server configuration
    └── eula.txt              # EULA acceptance
```

## Major Package Groups

### 1. Core Engine (`com.mojang`)
Low-level engine code maintained by Mojang, including:
- **blaze3d**: OpenGL rendering abstraction layer
- **math**: Mathematical utilities (matrices, vectors, quaternions)
- **authlib**: Authentication and profile management
- **realmsclient**: Minecraft Realms integration

### 2. Client Code (`net.minecraft.client`)
Client-specific functionality:
- **Minecraft.java**: Main client class, game loop
- **renderer/**: Complete rendering pipeline
- **gui/**: User interface, HUD, screens
- **resources/**: Asset loading (textures, models, sounds)
- **multiplayer/**: Client-side multiplayer logic

### 3. Server Code (`net.minecraft.server`)
Server-specific functionality:
- **MinecraftServer.java**: Main server class, tick loop
- **level/**: Server-side world management
- **players/**: Player connection and management
- **network/**: Server network handlers

### 4. World System (`net.minecraft.world`)
Game world simulation:
- **entity/**: All entity types and behaviors
- **level/**: World management, chunks, blocks
- **item/**: Item system and mechanics
- **inventory/**: Container and storage systems

### 5. Network Layer (`net.minecraft.network`)
Client-server communication:
- **protocol/**: Packet definitions for all game phases
- **codec/**: Packet serialization/deserialization
- **syncher/**: Entity data synchronization

### 6. Commands (`net.minecraft.commands`)
Command system built on Brigadier:
- Command parsing and execution
- Argument types
- Permission system

### 7. Data System (`net.minecraft.data`)
Data generation and management:
- Recipe generation
- Loot table generation
- World generation data
- Tag definitions

## Build System

The project uses Gradle as its build system:

- **build.gradle**: Main build configuration
  - Dependencies (LWJGL, Netty, GSON, etc.)
  - Source sets
  - Run tasks (runClient, runServer)
  - Fat JAR generation

- **gradle.properties**: Build properties
  - Version information
  - Memory settings

## Entry Points

### Client
- **Main Class**: `net.minecraft.client.main.Main`
- **Launcher**: Uses bundled JDK or system Java 21
- **Task**: `./gradlew runClient`

### Server
- **Main Class**: `net.minecraft.server.Main`
- **Headless**: `./gradlew runServer`
- **With GUI**: `./gradlew runServerGui`

## Key Technologies

- **Java 21**: Primary programming language
- **LWJGL 3**: OpenGL bindings and native access
- **Netty**: Network communication framework
- **Brigadier**: Command parsing library
- **GSON**: JSON serialization
- **FastUtil**: Optimized collections
- **JOML**: Java OpenGL Math Library
- **Fabric API**: Modding API implementation

## Code Organization Principles

1. **Separation**: Client and server code are clearly separated
2. **Modularity**: Systems are organized by functionality
3. **Registries**: Most game content uses registry pattern
4. **Data-driven**: Many features configured via JSON data files
5. **Event-driven**: Game logic responds to events and ticks

## Build Artifacts

- **Source Code**: Organized in flat package structure
- **Resources**: Located in `src/main/resources/`
- **Compiled Classes**: Generated in `build/classes/`
- **Fat JAR**: Generated in `build/libs/` (includes all dependencies)
- **Runtime**: Generated in `run/` directory during execution

## Development Workflow

1. **Edit Source**: Modify Java files in appropriate packages
2. **Build**: Run `./gradlew build`
3. **Test Client**: Run `./gradlew runClient`
4. **Test Server**: Run `./gradlew runServer`
5. **Package**: Run `./gradlew fatJar` for distribution

## Additional Documentation

For detailed information about specific systems, see:
- [Render System](RENDER-SYSTEM.md)
- [Networking System](NETWORKING-SYSTEM.md)
- [Entity System](ENTITY-SYSTEM.md)
- [World Generation System](WORLD-GENERATION-SYSTEM.md)
- [Command System](COMMAND-SYSTEM.md)
- [Data System](DATA-SYSTEM.md)
- [Inventory System](INVENTORY-SYSTEM.md)
- [Server System](SERVER-SYSTEM.md)
