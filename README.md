# MattMC

> **A high-performance, modular port of Minecraft Java Edition 1.21.10 — No Bullshit.**

This repository contains a complete, decompiled source code port of Minecraft Java Edition 1.21.10 (both client and server), with a focus on performance optimization and modular architecture.

## What Makes MattMC Different

### 🚀 Performance First
- **Optimized JVM Arguments**: Carefully tuned G1GC settings for both client and server
- **Efficient Memory Management**: Custom heap sizing and garbage collection parameters
- **Native Performance**: Direct access to LWJGL 3.3.3 with platform-specific optimizations
- **Minimal Overhead**: Clean, decompiled source without unnecessary abstractions

### 🧩 Modular Architecture
- **Clean Separation**: Distinct client and server entry points
- **Flexible Build System**: Gradle-based with customizable tasks for different use cases
- **Distribution Options**: Fat JAR, classpath-based distributions, or source builds
- **Bundled Runtime**: Optional bundled JDK for consistent execution environments

### 🎯 No Bullshit
- **Full Source Access**: Over 6,100 Java source files available for inspection and modification
- **Transparent Build Process**: Clear Gradle configuration with documented tasks
- **No Proprietary Launchers**: Direct execution via standard Java tooling
- **Offline Capable**: Run and develop without forced authentication or telemetry

## Prerequisites

Before building and running, ensure you have:

1. **Bundled JDK (Recommended)**
   - The project uses Temurin OpenJDK 21 bundled with the application
   - On Linux, it's automatically downloaded when needed
   - For Windows/macOS or manual setup, see [libraries/JDK-README.md](libraries/JDK-README.md)
   
   **OR**

   **System Java 21** (Alternative)
   - Download from [Adoptium](https://adoptium.net/) or [Oracle](https://www.oracle.com/java/technologies/downloads/)
   - Verify with: `java -version`

2. **Internet access** to download dependencies from:
   - Maven Central
   - libraries.minecraft.net (Mojang's library server)
   - Adoptium (for automatic JDK download on Linux)

## Quick Start

### Building the Project

1. Clone this repository:
   ```bash
   git clone https://github.com/HungLo2020/MattMC.git
   cd MattMC
   ```

2. Build the project:
   ```bash
   ./gradlew build
   ```
   
   On Windows:
   ```cmd
   gradlew.bat build
   ```
   
   The build uses optimized Gradle settings with parallel execution and caching enabled for faster builds.

## Running the Client

```bash
./gradlew runClient
```

The client will:
- Create a `run/` directory for game files
- Launch with offline mode (no authentication required)

**Note:** You will need game assets (textures, sounds, etc.) in the `run/assets` directory for full functionality.

## Running the Server

### Headless Mode (No GUI)

```bash
./gradlew runServer
```

### With GUI

```bash
./gradlew runServerGui
```

The first run will:
- Create a `run/` directory for server files
- Automatically accept the EULA (by setting `eula=true` in `run/eula.txt`)
- Start the server

## Configuration

### Server Properties

After the first run, edit `run/server.properties` to configure:
- `server-port`: Default 25565
- `max-players`: Default 20
- `difficulty`: peaceful, easy, normal, hard
- `gamemode`: survival, creative, adventure, spectator
- And many more options...

### JVM Arguments

The default JVM arguments in `build.gradle` are optimized for performance:
- `-Xmx2G`: Maximum heap size of 2GB
- `-Xms1G`: Initial heap size of 1GB (server) / 512M (client)
- G1 garbage collector with optimized settings

Modify the tasks in `build.gradle` to adjust these settings.

## Creating a Fat JAR

To create a single JAR file with all dependencies bundled:

```bash
./gradlew fatJar
```

This creates `build/libs/MattMC-1.21.10-all.jar` which can be run standalone.

## Project Structure

```
MattMC/
├── build.gradle          # Gradle build configuration with performance tuning
├── settings.gradle       # Gradle settings
├── gradle.properties     # Optimized build properties (8GB heap, parallel, caching)
├── gradlew / gradlew.bat # Gradle wrapper scripts
├── gradle/               # Gradle wrapper files
├── libraries/            # Bundled JDK and launch scripts
├── com/                  # Mojang source files (blaze3d, realmsclient, etc.)
├── net/minecraft/        # Main Minecraft source code (~6,100 files)
│   ├── client/           # Client-specific code
│   │   ├── main/Main.java # Client entry point
│   │   ├── renderer/     # Rendering engine
│   │   └── gui/          # User interface
│   ├── server/           # Server-specific code
│   │   ├── Main.java     # Server entry point
│   │   └── dedicated/    # Dedicated server implementation
│   ├── world/            # World generation, entities, blocks
│   ├── network/          # Networking and protocol implementation
│   ├── commands/         # Command system
│   └── ...               # Game logic, AI, physics, etc.
├── src/main/resources/   # Resource files
│   └── version.json      # Version information
└── run/                  # Runtime directory (created on first run)
    ├── jdk-21/           # Bundled JDK (optional)
    ├── assets/           # Game assets
    └── server.properties # Server configuration
```

## Performance Optimization Details

### JVM Tuning

MattMC includes carefully optimized JVM arguments for both client and server:

#### Server Configuration
- **Heap Size**: 2GB max, 1GB initial (adjustable based on player count)
- **Garbage Collector**: G1GC with tuned pause times (200ms target)
- **GC Optimization**:
  - 30-40% heap for new generation
  - 8MB heap regions for better large object handling
  - 15% initiating heap occupancy for early GC cycles
  - Parallel reference processing enabled

#### Client Configuration  
- **Heap Size**: 2GB max, 512MB initial (balanced for gameplay and rendering)
- **Same G1GC tuning** as server for consistent performance
- **LWJGL Debug Mode**: Optional debugging for graphics issues

### Build Performance

The `gradle.properties` file configures Gradle for optimal build speeds:
- **8GB Heap**: Ample memory for large compilation tasks
- **Parallel Execution**: Builds multiple modules simultaneously
- **Build Cache**: Reuses outputs from previous builds
- **Daemon Mode**: Keeps Gradle process running for faster subsequent builds

## Development

### Modifying Source Code

The entire Minecraft codebase is available in the `net/minecraft/` and `com/` directories. You can:

1. **Modify Game Mechanics**: Edit entity behavior, world generation, etc.
2. **Add Features**: Implement new blocks, items, or game modes
3. **Optimize Performance**: Profile and improve hot paths
4. **Debug Issues**: Full source access for troubleshooting

After making changes, rebuild with:
```bash
./gradlew build
```

### IDE Setup

#### IntelliJ IDEA (Recommended)
1. Open the project directory in IntelliJ
2. IntelliJ will automatically detect the Gradle configuration
3. Wait for Gradle sync to complete
4. Set Java 21 as the project SDK
5. Run configurations are in `build.gradle` (runClient, runServer, etc.)

#### Eclipse
1. Import as Gradle project
2. Configure Java 21 compiler
3. Run Gradle tasks from Gradle view

### Creating Custom Distributions

#### Fat JAR (Single File Distribution)
```bash
./gradlew fatJar           # Server JAR
./gradlew clientFatJar     # Client JAR
```

#### Full Distribution (with JDK)
```bash
./gradlew clientDist       # Creates distributable client in build/client-dist
./gradlew clientDistZip    # Creates ZIP archive for distribution
```

The distribution includes:
- All dependencies
- Launch scripts for Windows and Linux
- Bundled JDK (no separate Java installation needed)
- Asset directories pre-configured

## Notes

### EULA

By running the server, you automatically accept the [Minecraft EULA](https://aka.ms/MinecraftEULA). The `acceptEula` task creates the required `eula.txt` file.

### Legal Notice

This source code is for educational and development purposes. Minecraft is a trademark of Mojang Studios/Microsoft. Please ensure you have the appropriate rights to use this code.

## Technical Details

### Dependencies

MattMC uses the official Minecraft dependencies with careful version management:

#### Core Mojang Libraries
- **Brigadier 1.3.10**: Command parsing and execution
- **DataFixerUpper 8.0.16**: World data migration and fixes
- **Authlib 6.0.55**: Authentication (offline mode supported)
- **JTracy 1.0.29**: Performance profiling integration

#### Graphics & Audio (Client)
- **LWJGL 3.3.3**: OpenGL, GLFW, OpenAL, STB, FreeType, jemalloc
- **JOML 1.10.5**: OpenGL Math Library
- **JOrbis 0.0.17**: Ogg Vorbis audio decoding

#### Networking & I/O
- **Netty 4.1.97**: High-performance networking
- **Apache HttpClient 4.5.14**: HTTP operations
- **Apache Commons Compress 1.26.0**: Archive handling

#### Data & Collections
- **FastUtil 8.5.12**: High-performance primitive collections
- **Gson 2.11.0**: JSON parsing with modern features
- **Google Guava 32.1.2**: Utility collections and caching

#### System Integration
- **OSHI 6.4.10**: System and hardware information
- **JNA 5.14.0**: Native library access
- **ICU4J 74.2**: Unicode and internationalization

### Architecture Highlights

#### Modular Packaging
The codebase is organized into logical packages:
- `net.minecraft.server.*`: Dedicated server implementation
- `net.minecraft.client.*`: Client-specific rendering and UI
- `net.minecraft.world.*`: World generation, entities, blocks
- `net.minecraft.network.*`: Protocol and packet handling
- `net.minecraft.commands.*`: Command system using Brigadier
- `com.mojang.blaze3d.*`: 3D rendering engine
- `com.mojang.realmsclient.*`: Realms integration

#### Build System Features
- **Flexible Source Sets**: Direct compilation from decompiled sources
- **Platform Detection**: Automatic LWJGL native selection
- **Dependency Resolution**: Multiple Maven repositories with fallbacks
- **Task Composition**: Reusable Gradle tasks for common operations

## Bundled JDK

This project uses **Temurin OpenJDK 21** bundled with the application to ensure consistency across all environments. The bundled JDK is:

- **Automatically downloaded** on Linux when you run build/launch tasks
- **Not committed to git** (too large, ~200MB)
- **Included in distributions** so users don't need to install Java separately

### Using the Bundled JDK

The bundled JDK is automatically used when you run:
```bash
./gradlew runClient      # Client with bundled JDK
./gradlew runServer      # Server with bundled JDK
./gradlew clientDist     # Distribution with bundled JDK
```

### Manual JDK Setup

For Windows/macOS or if automatic download fails, see detailed instructions in [libraries/JDK-README.md](libraries/JDK-README.md).

## Troubleshooting

### Build fails with "Could not resolve dependencies"

Ensure you have internet access to:
- https://libraries.minecraft.net
- https://repo.maven.apache.org/maven2

Some networks may block these URLs.

### Java version errors

Make sure you have Java 21 installed and it's the active version:

```bash
java -version
```

If you have multiple Java versions, set `JAVA_HOME` to Java 21's path.

### Out of memory errors

Increase the heap size in the run tasks:

```groovy
jvmArgs = ['-Xmx4G', '-Xms2G', ...]
```

### Client crashes on startup

Make sure you have:
- A graphics driver that supports OpenGL 4.4+
- Game assets in `run/assets` directory

## Use Cases

### Research & Education
- **Game Engine Study**: Learn from a production-quality game engine
- **Networking Protocols**: Study client-server architecture at scale
- **Performance Analysis**: Profile and understand optimization techniques
- **Algorithm Implementation**: Examine world generation, pathfinding, AI

### Development & Experimentation
- **Custom Game Modes**: Implement new gameplay mechanics
- **Performance Testing**: Benchmark modifications and optimizations
- **Bug Fixing**: Identify and fix issues in the source
- **Feature Prototyping**: Test new ideas with full source access

### Server Administration
- **Custom Server Logic**: Modify server behavior for specific needs
- **Performance Tuning**: Optimize for your specific hardware and player count
- **Debug Capabilities**: Troubleshoot issues with full stack traces

## Contributing

While this is a personal port, contributions are welcome:

1. **Report Issues**: Use GitHub Issues for bugs or problems
2. **Suggest Optimizations**: Share performance improvements
3. **Documentation**: Help improve setup guides and documentation
4. **Testing**: Report compatibility issues on different platforms

### Coding Standards
- Follow existing code style (matches Minecraft conventions)
- Test changes with both client and server
- Document non-obvious optimizations
- Keep commits focused and atomic

## FAQ

**Q: Can I use this for multiplayer servers?**  
A: Yes, the server component is fully functional. Remember to accept the EULA.

**Q: Does this support mods?**  
A: This is vanilla source code. For mod support, consider Fabric or Forge integration.

**Q: Why Java 21?**  
A: Minecraft 1.21+ officially requires Java 21 for modern JVM features and performance.

**Q: How is this different from official Minecraft?**  
A: This is decompiled source code for development and research. It's functionally equivalent to vanilla Minecraft.

**Q: Can I distribute modified versions?**  
A: Review Minecraft's EULA and terms of service. This is for educational and development purposes.

**Q: What about game assets?**  
A: Assets (textures, sounds, models) are not included. You need a legitimate Minecraft installation or asset pack.

## Acknowledgments

- **Mojang Studios / Microsoft**: Original Minecraft creators
- **Adoptium**: Temurin OpenJDK builds
- **LWJGL**: Lightweight Java Game Library
- **Fabric Project**: Development tooling inspiration

## License & Legal

This is decompiled Minecraft source code provided for educational and development purposes. Minecraft is a trademark of Mojang Studios/Microsoft.

**Important**: By using this code and running the server, you agree to the [Minecraft EULA](https://aka.ms/MinecraftEULA). Ensure you have appropriate rights and comply with Mojang's terms of service.

This project is not affiliated with or endorsed by Mojang Studios or Microsoft.
