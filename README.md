# MattMC

> **A high-performance, modular port of Minecraft Java Edition 1.21.10 — No Bullshit.**

This repository contains a complete, decompiled source code port of Minecraft Java Edition 1.21.10 (both client and server), with a focus on performance optimization and modular architecture.

Wiki for this project is available at [Wiki](docs/index.md)

Source Code: https://github.com/HungLo2020/MattMC

## What Makes MattMC Different

### No Bullshit
- **Full Source Access**: Thousands of Java source files available for inspection and modification
- **Transparent Build Process**: Clear Gradle configuration with documented tasks
- **No Proprietary Launchers**: Direct execution via standard Java tooling
- **Offline Capable**: Run and develop without forced authentication or telemetry

## Quick Start
- download or clone the repository, ```git clone https://github.com/HungLo2020/MattMC.git```
- run ```./libraries/download_jdk.sh``` to download the bundled JDK.
- run ```./gradlew runClient``` to launch the client or ```./gradlew runServer``` to launch the server. Or optioinally use the built in scripts, ```./DevUtils/RunDev.sh``` to launch the game in the dev environment. ```./DevUtils/RunExport.sh``` to export the build.

## Project Structure

```
MattMC/
├── build.gradle          # Gradle build configuration with performance tuning
├── settings.gradle       # Gradle settings
├── gradle.properties     # Optimized build properties (8GB heap, parallel, caching)
├── gradlew / gradlew.bat # Gradle wrapper scripts
├── gradle/               # Gradle wrapper files
├── libraries/            # Bundled JDK and launch scripts
├── src/                  # Source code (standard Maven/Gradle structure)
│   └── main/
│       ├── java/         # All Java source files
│       │   ├── com/mojang/        # Mojang libraries (blaze3d, math, logging)
│       │   └── net/
│       │       ├── minecraft/     # Minecraft source code (thousands of files)
│       │       │   ├── client/    # Client-specific code
│       │       │   │   ├── main/Main.java  # Client entry point
│       │       │   │   ├── renderer/       # Rendering engine
│       │       │   │   └── gui/            # User interface
│       │       │   ├── server/    # Server-specific code
│       │       │   │   ├── Main.java       # Server entry point
│       │       │   │   └── dedicated/      # Dedicated server implementation
│       │       │   ├── world/     # World generation, entities, blocks
│       │       │   ├── network/   # Networking and protocol implementation
│       │       │   ├── commands/  # Command system
│       │       │   └── ...        # Game logic, AI, physics, etc.
│       │       ├── fabricmc/      # Fabric API stubs
│       │       ├── iris/          # Iris API
│       │       └── sodium/        # Sodium API
│       └── resources/    # Resource files
│           └── version.json       # Version information
└── run/                  # Runtime directory (created on first run)
    ├── jdk/              # Bundled JDK(s) by platform (optional)
    ├── assets/           # Game assets
    └── server.properties # Server configuration
```


