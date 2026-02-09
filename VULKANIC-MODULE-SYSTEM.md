# Vulkanic Module System

## Overview

The Vulkanic Graphics Abstraction Layer now uses a Gradle multi-module architecture to enforce strict boundaries between game code and graphics API implementations. This ensures that:

1. **Game code CANNOT directly import `org.lwjgl.opengl` or `org.lwjgl.vulkan`**
2. **Only backend modules can access their respective graphics APIs**
3. **Violations are caught at compile-time, not runtime**

## Module Structure

```
MattMC/
├── (root) - Main game module
│   ├── Can use: Vulkanic API abstraction
│   ├── Can use: LWJGL utilities (GLFW, STB, FreeType, etc.)
│   ├── CANNOT use: org.lwjgl.opengl
│   └── CANNOT use: org.lwjgl.vulkan
│
├── vulkanic-api/ - Graphics abstraction API
│   ├── Exports: net.vulkanic package
│   ├── Contains: VulkanicAPI, GraphicsBackend interface
│   └── Uses: ServiceLoader for backend discovery
│
├── vulkanic-backend-opengl/ - OpenGL implementation
│   ├── ONLY module that can import org.lwjgl.opengl
│   ├── Implements: GraphicsBackend interface
│   └── Provides: GraphicsBackendProvider service
│
└── vulkanic-backend-vulkan/ - Vulkan implementation (stub)
    ├── ONLY module that can import org.lwjgl.vulkan
    ├── Implements: GraphicsBackend interface (stub)
    └── Provides: GraphicsBackendProvider service
```

## How Enforcement Works

The boundary enforcement is achieved through **Gradle dependency scoping**:

### Main Module Dependencies
```gradle
dependencies {
    implementation project(':vulkanic-api')  // ✅ Can use abstraction
    runtimeOnly project(':vulkanic-backend-opengl')  // ✅ Backend loaded at runtime
    
    // LWJGL utilities (NOT graphics APIs)
    implementation 'org.lwjgl:lwjgl-glfw'    // ✅ Window management
    implementation 'org.lwjgl:lwjgl-stb'      // ✅ Image loading
    implementation 'org.lwjgl:lwjgl-freetype' // ✅ Font rendering
    
    // org.lwjgl:lwjgl-opengl is NOT included! ❌
    // org.lwjgl:lwjgl-vulkan is NOT included! ❌
}
```

### OpenGL Backend Dependencies
```gradle
dependencies {
    implementation project(':vulkanic-api')
    implementation 'org.lwjgl:lwjgl-opengl'  // ✅ ONLY this module can use OpenGL
}
```

### Result
If game code tries to import OpenGL:
```java
import org.lwjgl.opengl.GL11;  // ❌ Compile error!
```

Error message:
```
error: package org.lwjgl.opengl does not exist
```

## Backend Discovery

Backends are discovered at runtime using Java's **ServiceLoader** pattern:

1. Each backend module implements `GraphicsBackendProvider`
2. Backends are registered via `META-INF/services` (handled by Gradle)
3. `VulkanicAPI.initialize(BackendType)` uses ServiceLoader to find the appropriate backend

### Example
```java
// In game code
VulkanicAPI.initialize(VulkanicAPI.BackendType.OPENGL);

// Under the hood:
// 1. ServiceLoader finds OpenGLBackendProvider
// 2. Provider creates OpenGLBackend instance
// 3. VulkanicAPI delegates all calls to the backend
```

## Building the Project

```bash
# Build everything
./gradlew build

# Build individual modules
./gradlew :vulkanic-api:build
./gradlew :vulkanic-backend-opengl:build
./gradlew :vulkanic-backend-vulkan:build

# Clean build
./gradlew clean build
```

## Verifying Enforcement

To verify that the boundary enforcement is working:

1. Create a test file in the main module that imports OpenGL:
   ```java
   package com.example;
   import org.lwjgl.opengl.GL11;  // This should fail
   
   public class Test {
       public void bad() {
           GL11.glClear(0);
       }
   }
   ```

2. Try to compile:
   ```bash
   ./gradlew compileJava
   ```

3. Expected result: **Compilation error**
   ```
   error: package org.lwjgl.opengl does not exist
   ```

## What Can Import What?

| Package | Can Import OpenGL? | Can Import Vulkan? | Can Import Vulkanic API? |
|---------|-------------------|-------------------|-------------------------|
| `net.minecraft.*` | ❌ NO | ❌ NO | ✅ YES |
| `net.blaze3d.*` | ❌ NO | ❌ NO | ✅ YES |
| `net.vulkanic` (API) | ❌ NO | ❌ NO | N/A (It IS the API) |
| `net.vulkanic.backends.opengl` | ✅ YES | ❌ NO | ✅ YES |
| `net.vulkanic.backends.vulkan` | ❌ NO | ✅ YES | ✅ YES |

## Benefits

1. **Compile-time safety**: Violations are caught during compilation, not at runtime
2. **Clear architecture**: Module boundaries make the architecture explicit
3. **Future-proof**: Easy to add new backends (e.g., Metal, DirectX) by adding new modules
4. **Testable**: Can test backends independently
5. **Documentation**: Module structure serves as documentation of the architecture

## Migration Notes

- All Vulkanic API code has been moved to the `vulkanic-api` module
- OpenGL backend code has been moved to the `vulkanic-backend-opengl` module  
- Game code in `src/main/java` remains in the main module
- The main module's `build.gradle` no longer includes `org.lwjgl:lwjgl-opengl` as a dependency
- Backends are loaded dynamically via ServiceLoader instead of direct instantiation

## Future Work

- Implement actual Vulkan backend in `vulkanic-backend-vulkan`
- Add Metal backend module for macOS
- Add DirectX backend module for Windows
- Each backend module will enforce that ONLY it can import its respective graphics API
