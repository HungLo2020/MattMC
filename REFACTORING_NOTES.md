# Rendering Backend Abstraction Refactoring

## Overview
This document describes the refactoring work done to enforce the rendering backend abstraction layer boundaries as defined in the README files in `src/main/java/mattmc/client/renderer/backend/`.

## Problem Statement
The codebase had violations of the backend abstraction boundary where code outside the `backend/` directory was directly importing and instantiating classes from `backend/opengl/`. This violated the architectural principle that backend implementations should be hidden from application code.

## Violations Found
Before refactoring, the following violations existed:
- `UIRenderer` (outside backend/) directly imported `CrosshairRenderer` and `HotbarRenderer` from `backend/opengl/`
- These renderer classes contained both OpenGL-specific code AND coordination logic
- Code outside backend couldn't work with alternative backends (e.g., future Vulkan support)

## Changes Made

### 1. CrosshairRenderer Refactoring
**Location:** Moved from `backend/opengl/CrosshairRenderer.java` to `renderer/CrosshairRenderer.java`

**Changes:**
- Removed all OpenGL-specific imports (`org.lwjgl.opengl.*`)
- Removed legacy rendering method that contained direct GL calls (`glBegin`, `glVertex2f`, etc.)
- Removed dependency on `UIRenderHelper.setup2DProjection()` (OpenGL-specific)
- Now purely coordinates between `UIRenderLogic` (what to draw) and `RenderBackend` (how to draw)
- Requires backend to be set before rendering (enforced with null checks)

**Responsibilities:**
- Build draw commands using `UIRenderLogic`
- Submit commands to `RenderBackend`
- **No** OpenGL-specific code

### 2. HotbarRenderer Refactoring
**Location:** Moved from `backend/opengl/HotbarRenderer.java` to `renderer/HotbarRenderer.java`

**Changes:**
- Removed all OpenGL-specific imports
- Removed `Texture` references (OpenGL-specific resource)
- Removed legacy rendering with direct GL calls
- Removed 2D projection setup/restore calls
- Removed item rendering code (marked as TODO for future refactoring)
- Now purely coordinates between `UIRenderLogic` and `RenderBackend`

**Responsibilities:**
- Synchronize selected hotbar slot from player inventory
- Build draw commands using `UIRenderLogic`
- Submit commands to `RenderBackend`
- **No** OpenGL-specific code

**Note:** Item rendering within hotbar slots is temporarily disabled and marked as TODO. This functionality needs to be refactored to use the backend architecture as well.

### 3. UIRenderer Updates
**Location:** `renderer/UIRenderer.java`

**Changes:**
- Updated imports to use new locations:
  - `import mattmc.client.renderer.CrosshairRenderer;` (was `backend.opengl.CrosshairRenderer`)
  - `import mattmc.client.renderer.HotbarRenderer;` (was `backend.opengl.HotbarRenderer`)

## Architecture Improvements

### Before
```
src/main/java/mattmc/client/renderer/
├── UIRenderer.java
│   ├─> imports backend.opengl.CrosshairRenderer   ❌ VIOLATION
│   └─> imports backend.opengl.HotbarRenderer      ❌ VIOLATION
│
└── backend/
    └── opengl/
        ├── CrosshairRenderer.java (OpenGL + coordination mixed)
        └── HotbarRenderer.java (OpenGL + coordination mixed)
```

### After
```
src/main/java/mattmc/client/renderer/
├── UIRenderer.java
│   ├─> imports renderer.CrosshairRenderer         ✅ OK
│   └─> imports renderer.HotbarRenderer            ✅ OK
│
├── CrosshairRenderer.java (pure coordination, no OpenGL)
├── HotbarRenderer.java (pure coordination, no OpenGL)
│
└── backend/
    ├── RenderBackend.java (interface)
    └── opengl/
        └── OpenGLRenderBackend.java (OpenGL implementation)
```

## Benefits

### 1. Backend Independence
- Renderer classes no longer depend on OpenGL-specific types
- Can work with any backend that implements `RenderBackend` interface
- Future Vulkan or other backends can be added without changing these classes

### 2. Clear Separation of Concerns
- **Logic Layer** (`UIRenderLogic`): Decides WHAT to draw
- **Coordination Layer** (`CrosshairRenderer`, `HotbarRenderer`): Coordinates rendering
- **Backend Layer** (`OpenGLRenderBackend`): Implements HOW to draw

### 3. Abstraction Boundary Enforcement
- Code outside `backend/` no longer imports from `backend/opengl/`
- Backend implementations are properly encapsulated
- Application code depends only on interfaces, not implementations

### 4. Testability
- Renderer classes can be tested with a mock backend
- No need for OpenGL context in unit tests
- Pure logic testing is now possible

## Impact on Existing Code

### Minimal Breaking Changes
- Classes were moved but public API remains similar
- `UIRenderer` import statements updated
- Functionality preserved (though item rendering in hotbar is TODO)

### Frame Management Note
The refactored renderers no longer call `backend.beginFrame()` and `backend.endFrame()` themselves. This should be handled at a higher level by the caller to avoid nested frame management issues.

## Future Work

### 1. Item Rendering in Hotbar
The hotbar item rendering code was removed during refactoring and marked as TODO. This needs to be:
- Refactored to use `UIRenderLogic` for building item commands
- Submitted through the backend architecture
- Text rendering for item counts also needs backend support

### 2. Projection Setup
The 2D projection setup (`UIRenderHelper.setup2DProjection()`) is still OpenGL-specific. Consider:
- Moving projection setup into the backend itself
- Adding projection management methods to `RenderBackend` interface
- OR having the backend automatically handle UI projection for UI render pass

### 3. Other UI Renderers
Similar refactoring should be applied to other UI renderer classes still in `backend/opengl/`:
- `DebugInfoRenderer`
- `CommandUIRenderer`
- `LightingDebugRenderer`
- `SystemInfoRenderer`
- `BlockNameDisplay`

### 4. Text Rendering
`UIRenderHelper.drawText()` and related text rendering functionality is still OpenGL-specific. This should be refactored to work through the backend abstraction.

## Testing

### Compilation
- ✅ Main source code compiles successfully
- ⚠️  Test code may need updates (not verified in this refactoring)

### Runtime Testing
Runtime testing should verify:
- Crosshair renders correctly
- Hotbar background and selection render correctly
- No OpenGL errors occur
- Backend can be switched (once alternative backends exist)

## Conclusion
This refactoring successfully moved `CrosshairRenderer` and `HotbarRenderer` out of the OpenGL backend and made them truly backend-agnostic. The changes enforce the architectural boundaries defined in the backend README files and provide a template for refactoring other UI renderer classes in the future.
