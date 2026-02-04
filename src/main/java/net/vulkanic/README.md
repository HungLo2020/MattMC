# Vulkanic - Rendering Abstraction Layer

## Overview

Vulkanic is the rendering abstraction layer for this game. It provides a unified interface for all rendering operations, allowing the game to support multiple graphics backends without requiring code changes outside of this package.

## Purpose

The primary goals of Vulkanic are:

1. **Abstraction**: Decouple the game's rendering logic from specific graphics API implementations
2. **Flexibility**: Enable support for multiple rendering backends (OpenGL, Vulkan, etc.)
3. **Maintainability**: Centralize all rendering code in a single, well-defined module

## Architecture

### Directory Structure

```
vulkanic/
├── backends/           # Backend implementations (DO NOT call directly!)
│   ├── opengl/        # OpenGL backend implementation
│   └── vulkan/        # Vulkan backend implementation (future)
└── [API classes]      # Public API classes that game code calls
```

### How It Works

1. **Game Code** → Calls Vulkanic's public API classes
2. **Vulkanic API** → Delegates to the appropriate backend
3. **Backend** → Implements rendering using specific graphics API (OpenGL, Vulkan, etc.)

The backend selection mechanism is yet to be determined and will be implemented in a future update.

## Critical Rules

### ⛔ DO NOT

1. **DO NOT** make direct OpenGL (or any graphics API) calls from code outside the `vulkanic` package
2. **DO NOT** directly call or import classes from the `vulkanic/backends` directory from outside the `vulkanic` package
3. **DO NOT** bypass the abstraction layer

### ✅ DO

1. **DO** use only the public API classes provided in the `vulkanic` package for all rendering operations
2. **DO** implement new rendering features within the Vulkanic abstraction layer
3. **DO** ensure all backends implement the same interface/contract

## Usage

All game rendering code should interact exclusively with Vulkanic's public API classes. These classes will handle the delegation to the appropriate backend based on the configured graphics API.

Example (conceptual):
```java
// ✅ CORRECT: Using Vulkanic API
VulkanicRenderer renderer = Vulkanic.getRenderer();
renderer.draw(...);

// ❌ WRONG: Direct OpenGL call from game code
GL11.glDrawArrays(...);

// ❌ WRONG: Direct backend access from game code
OpenGLBackend backend = new OpenGLBackend();
```

## Backend Implementation

Backend implementations are located in the `backends` directory and should only be accessed internally by Vulkanic's API layer. Each backend must implement the same contract to ensure compatibility.

Currently supported/planned backends:
- **OpenGL**: Initial backend implementation
- **Vulkan**: Planned for future implementation

## Future Development

- Define and implement backend selection mechanism
- Create public API classes for common rendering operations
- Migrate existing rendering code to use Vulkanic
- Implement additional backends as needed
