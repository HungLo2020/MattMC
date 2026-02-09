# Vulkanic Graphics Abstraction Layer

## ⚠️ This Directory Has Moved

The Vulkanic API and backend implementations have been moved to separate Gradle modules to enforce architectural boundaries:

- **API**: `vulkanic-api/src/main/java/net/vulkanic/`
- **OpenGL Backend**: `vulkanic-backend-opengl/src/main/java/net/vulkanic/backends/opengl/`
- **Vulkan Backend**: `vulkanic-backend-vulkan/src/main/java/net/vulkanic/backends/vulkan/`

## Module Boundary Enforcement

### ✅ What Game Code CAN Do

```java
import net.vulkanic.VulkanicAPI;

// Initialize with OpenGL backend
VulkanicAPI.initialize(VulkanicAPI.BackendType.OPENGL);

// Use the abstraction layer
VulkanicAPI.setDynamicViewport(0, 0, 1920, 1080);
VulkanicAPI.clear(VulkanicAPI.GL_COLOR_BUFFER_BIT);
```

### ❌ What Game Code CANNOT Do

```java
// This will cause a COMPILE ERROR:
import org.lwjgl.opengl.GL11;  // ❌ ERROR: package org.lwjgl.opengl does not exist

// This will also fail:
import org.lwjgl.vulkan.VK10;  // ❌ ERROR: package org.lwjgl.vulkan does not exist
```

### Why This Matters

The module system ensures that:
1. **Game code is decoupled from specific graphics APIs** (OpenGL, Vulkan, etc.)
2. **Future graphics API changes** (e.g., adding Vulkan support) won't require changes to game code
3. **Architecture is enforced at compile-time**, not just by convention
4. **Only backend modules** can import graphics API classes

## Implementation

The Vulkanic API uses a **multi-module Gradle architecture** where:

1. **Main Module** (game code)
   - Has `vulkanic-api` as a dependency
   - Does NOT have `org.lwjgl.opengl` as a dependency
   - **Cannot** import OpenGL or Vulkan classes

2. **Backend Modules**
   - `vulkanic-backend-opengl` is the ONLY module with `org.lwjgl.opengl` dependency
   - `vulkanic-backend-vulkan` is the ONLY module with `org.lwjgl.vulkan` dependency
   - Implement `GraphicsBackendProvider` service

3. **Runtime Discovery**
   - Backends are discovered via Java's ServiceLoader pattern
   - No compile-time dependency from API to backend implementations

## Documentation

See [`VULKANIC-MODULE-SYSTEM.md`](../../../VULKANIC-MODULE-SYSTEM.md) in the project root for complete documentation.

## Example: Adding a New Graphics Operation

### In vulkanic-api module:
```java
// Add to GraphicsBackend interface
void setPolygonMode(int face, int mode);

// Add to VulkanicAPI class
public static void setPolygonMode(int face, int mode) {
    getBackend().setPolygonMode(face, mode);
}
```

### In vulkanic-backend-opengl module:
```java
@Override
public void setPolygonMode(int face, int mode) {
    // ONLY this module can use GL classes directly
    GL11.glPolygonMode(face, mode);
}
```

### In game code (main module):
```java
// Game code uses the abstraction
VulkanicAPI.setPolygonMode(VulkanicAPI.GL_FRONT_AND_BACK, VulkanicAPI.GL_LINE);
```

The game code will never need to change, even when we add Vulkan support!
