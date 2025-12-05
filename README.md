# Mattcraft - Minecraft Server Source

This repository contains the decompiled source code for Minecraft Java Edition 1.21.10 (dedicated server).

## Prerequisites

Before building and running, ensure you have:

1. **Java 21** or later installed
   - Download from [Adoptium](https://adoptium.net/) or [Oracle](https://www.oracle.com/java/technologies/downloads/)
   - Verify with: `java -version`

2. **Internet access** to download dependencies from:
   - Maven Central
   - libraries.minecraft.net (Mojang's library server)

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

## Running the Server

### Quick Start (GUI mode)

```bash
./gradlew runServer
```

The first run will:
- Create a `run/` directory for server files
- Automatically accept the EULA (by setting `eula=true` in `run/eula.txt`)
- Start the server in no-GUI mode

### Running with GUI

To run with the server GUI:

```bash
./gradlew runServer --args=""
```

Or modify the `runServer` task in `build.gradle` to remove `--nogui` from the args.

### Manual Execution

After building, you can run the server directly:

```bash
cd run
java -Xmx2G -Xms1G -jar ../build/libs/Mattcraft-1.21.10.jar
```

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
- `-Xms1G`: Initial heap size of 1GB
- G1 garbage collector with optimized settings

Modify the `runServer` task in `build.gradle` to adjust these settings.

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
├── com/                  # com.mojang.math source files
├── net/minecraft/        # Main Minecraft source code
│   ├── server/           # Server-specific code
│   │   ├── Main.java     # Server entry point
│   │   └── ...
│   ├── world/            # World generation and entities
│   ├── network/          # Networking code
│   └── ...
├── src/main/resources/   # Resource files
│   └── version.json      # Version information
└── run/                  # Server runtime directory (created on first run)
```

## Notes

### Client Code

This distribution contains **server-only** source code. The client source code (rendering, GUI, input handling, etc.) is not included. The `runClient` task will display an error message indicating that client code is not available.

### EULA

By running the server, you automatically accept the [Minecraft EULA](https://aka.ms/MinecraftEULA). The `acceptEula` task creates the required `eula.txt` file.

### Legal Notice

This source code is for educational and development purposes. Minecraft is a trademark of Mojang Studios/Microsoft. Please ensure you have the appropriate rights to use this code.

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

Increase the heap size in the `runServer` task:

```groovy
jvmArgs = ['-Xmx4G', '-Xms2G', ...]
```

## License

This is decompiled Minecraft source code. See Minecraft's EULA and terms of service for usage restrictions.
