# Vulkanic Architectural Boundary Enforcement

## Overview

This directory contains tests that enforce the **Vulkanic abstraction layer principle**: game code must use the Vulkanic API instead of directly calling OpenGL or Vulkan libraries.

## Architecture Rules

The following architectural boundaries are enforced by automated tests:

### Rule 1: OpenGL Backend Isolation
**Only** code in `src/main/java/net/vulkanic/backends/opengl/` may import `org.lwjgl.opengl.*` classes.

**Rationale:** Game code must not depend directly on OpenGL. Instead, it should use the Vulkanic API which abstracts the graphics backend.

### Rule 2: Vulkan Backend Isolation
**Only** code in `src/main/java/net/vulkanic/backends/vulkan/` may import `org.lwjgl.vulkan.*` classes.

**Rationale:** Game code must not depend directly on Vulkan. The Vulkanic API provides a unified interface that works with both OpenGL and Vulkan backends.

## How It Works

The `ArchitecturalBoundaryTest` automatically scans all Java source files during the build process to detect violations:

1. **During every build** (`./gradlew build` or `./gradlew test`), the test executes
2. The test scans all `.java` files in `src/main/java`
3. Using regex patterns, it detects imports of restricted packages
4. If violations are found, the **build fails** with a clear error message
5. The error message shows:
   - Which files contain violations
   - Which specific imports are illegal
   - How to fix the issue

## Example Violation

If game code tries to import OpenGL directly:

```java
// ❌ VIOLATION: This code is NOT in the OpenGL backend directory
package net.minecraft.renderer;

import org.lwjgl.opengl.GL11;  // ← This import is forbidden!

public class MyRenderer {
    public void render() {
        GL11.glClear(GL11.GL_COLOR_BUFFER_BIT);
    }
}
```

The build will fail with:

```
================================================================================
ARCHITECTURAL BOUNDARY VIOLATION: Illegal OpenGL Imports Detected
================================================================================

RULE: Only code in 'src/main/java/net/vulkanic/backends/opengl/'
      may import org.lwjgl.opengl.* classes.

VIOLATIONS FOUND:
--------------------------------------------------------------------------------
File: net/minecraft/renderer/MyRenderer.java
  Illegal OpenGL imports:
    import org.lwjgl.opengl.GL11;

================================================================================
TO FIX: Remove direct OpenGL imports and use the VulkanicAPI instead.
        See src/main/java/net/vulkanic/README.md for architectural guidance.
================================================================================
```

## How to Fix Violations

If you encounter a violation:

1. **Remove the direct OpenGL/Vulkan import**
2. **Use the VulkanicAPI instead**
3. **If the API doesn't support your use case**, extend the Vulkanic API to add the needed functionality

### Example Fix

**Before (violates boundary):**
```java
import org.lwjgl.opengl.GL11;

public class MyRenderer {
    public void clear() {
        GL11.glClear(GL11.GL_COLOR_BUFFER_BIT);
    }
}
```

**After (uses abstraction):**
```java
import net.vulkanic.VulkanicAPI;

public class MyRenderer {
    private VulkanicAPI vulkanic;
    
    public void clear() {
        vulkanic.clear(VulkanicAPI.COLOR_BUFFER_BIT);
    }
}
```

## Allowed LWJGL Imports

Note that the test **only** restricts OpenGL and Vulkan imports. Other LWJGL modules are allowed anywhere:

- ✅ `org.lwjgl.glfw.*` - Window management (allowed everywhere)
- ✅ `org.lwjgl.system.*` - Memory utilities (allowed everywhere)
- ✅ `org.lwjgl.stb.*` - Image loading, fonts, etc. (allowed everywhere)
- ❌ `org.lwjgl.opengl.*` - OpenGL (only in backends/opengl/)
- ❌ `org.lwjgl.vulkan.*` - Vulkan (only in backends/vulkan/)

## Running the Tests Manually

To run only the architectural boundary tests:

```bash
./gradlew test --tests "net.vulkanic.ArchitecturalBoundaryTest"
```

To run all tests (includes architectural boundary tests):

```bash
./gradlew test
```

## For Backend Developers

If you're working on the OpenGL or Vulkan backends:

- **OpenGL Backend**: You can freely import `org.lwjgl.opengl.*` in files under `src/main/java/net/vulkanic/backends/opengl/`
- **Vulkan Backend**: You can freely import `org.lwjgl.vulkan.*` in files under `src/main/java/net/vulkanic/backends/vulkan/`

## Related Documentation

- **src/main/java/net/vulkanic/README.md** - Vulkanic abstraction layer architecture and design principles
- **VULKAN-COMPAT.md** - Comprehensive guide on the migration strategy from deprecated API to new abstraction

## Technical Details

The test uses:
- **JUnit 5** for the testing framework
- **Java NIO** for file system traversal
- **Regex patterns** for import detection
- **Path-based filtering** to allow backend directories

The test is designed to be:
- ⚡ **Fast** - Scans thousands of files in seconds
- 🎯 **Precise** - Only flags actual violations
- 📝 **Clear** - Provides actionable error messages
- 🔒 **Strict** - Fails the build immediately on violation
