# Mattcraft - Minecraft Source

This repository contains the decompiled source code for Minecraft Java Edition 1.21.10 (both client and server).

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

## Building the Project

1. Clone this repository:
   ```bash
   git clone https://github.com/HungLo2020/Mattcraft.git
   cd Mattcraft
   ```

2. Build the project:
   ```bash
   ./gradlew build
   ```
   
   On Windows:
   ```cmd
   gradlew.bat build
   ```

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

This creates `build/libs/Mattcraft-1.21.10-all.jar` which can be run standalone.

## Project Structure

```
Mattcraft/
├── build.gradle          # Gradle build configuration
├── settings.gradle       # Gradle settings
├── gradle.properties     # Build properties
├── gradlew / gradlew.bat # Gradle wrapper scripts
├── gradle/               # Gradle wrapper files
├── com/                  # com.mojang source files (blaze3d, realmsclient, etc.)
├── net/minecraft/        # Main Minecraft source code
│   ├── client/           # Client-specific code
│   │   ├── main/Main.java # Client entry point
│   │   └── ...
│   ├── server/           # Server-specific code
│   │   ├── Main.java     # Server entry point
│   │   └── ...
│   ├── world/            # World generation and entities
│   ├── network/          # Networking code
│   └── ...
├── src/main/resources/   # Resource files
│   └── version.json      # Version information
└── run/                  # Runtime directory (created on first run)
```

## Notes

### EULA

By running the server, you automatically accept the [Minecraft EULA](https://aka.ms/MinecraftEULA). The `acceptEula` task creates the required `eula.txt` file.

### Legal Notice

This source code is for educational and development purposes. Minecraft is a trademark of Mojang Studios/Microsoft. Please ensure you have the appropriate rights to use this code.

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

## License

This is decompiled Minecraft source code. See Minecraft's EULA and terms of service for usage restrictions.
