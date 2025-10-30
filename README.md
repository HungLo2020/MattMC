# MattMC

A blocky sandbox game built with Java and OpenGL (LWJGL).

## Quick Start

### Building and Running

```bash
# Build the project
./gradlew build

# Run the game
./gradlew run
```

### System Requirements

- Java 21 or later
- Linux (native libraries included for Linux)
- OpenGL-capable graphics card

## Features

- Title screen with animated cubemap panorama
- Singleplayer menu system
- 3D rotating cube demonstration (Devplay mode)
- Custom UI buttons and text rendering

## Drawing a Cube

This repository includes a complete implementation for drawing 3D cubes. For detailed instructions, see:

**[HOW_TO_DRAW_A_CUBE.md](HOW_TO_DRAW_A_CUBE.md)** - Complete guide on how to draw cubes in this repo

### Quick Access to Cube Demo

1. Run the game: `./gradlew run`
2. Click "Singleplayer"
3. Click "Devplay" (top-left)
4. View the rotating colored cube!
5. Press ESC to exit

### Code References

- **Live Example**: `src/main/java/MattMC/screens/DevplayScreen.java` - Full rotating cube implementation
- **Simple Example**: `src/main/java/MattMC/examples/SimpleCubeExample.java` - Reusable cube drawing utilities

## Project Structure

```
MattMC/
├── src/main/java/MattMC/
│   ├── Main.java              # Application entry point
│   ├── core/                  # Core game systems
│   │   ├── Game.java          # Main game loop
│   │   └── Window.java        # Window management
│   ├── screens/               # Different screens/views
│   │   ├── TitleScreen.java
│   │   ├── SingleplayerScreen.java
│   │   └── DevplayScreen.java # Cube demonstration
│   ├── gfx/                   # Graphics utilities
│   │   ├── CubeMap.java
│   │   └── Texture.java
│   ├── ui/                    # UI components
│   └── examples/              # Example code
│       └── SimpleCubeExample.java
└── build.gradle.kts           # Build configuration
```

## Technologies

- **Java 21**: Modern Java with records and enhanced language features
- **LWJGL 3.3.4**: Lightweight Java Game Library
  - OpenGL for 3D graphics
  - GLFW for window and input management
  - STB for image loading and font rendering
- **Gradle 8.13**: Build and dependency management

## Development

### Building

```bash
./gradlew build
```

### Running

```bash
./gradlew run
```

### Creating Distribution

```bash
./gradlew portableZip
```

This creates a distributable ZIP file in `build/releases/`.

## License

See project repository for license information.
