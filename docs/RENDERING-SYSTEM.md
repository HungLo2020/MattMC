# MattMC Rendering System Documentation

## Overview

MattMC employs a sophisticated rendering architecture designed around the principle of **backend abstraction**. This architecture ensures that the entire codebase remains agnostic to the underlying graphics API, enabling portability, testability, and future extensibility.

The rendering system is built on three core layers:

1. **Game/World Layer** - Game logic with no graphics API knowledge
2. **Rendering Front-End** - Decides WHAT to draw, produces abstract commands
3. **Rendering Back-End** - Decides HOW to draw, executes graphics API calls

```
┌─────────────────────────────────────────────────────────────────┐
│                      Game/World Layer                           │
│   Blocks, Chunks, Entities, Items, Game Logic                   │
│   (No graphics API knowledge)                                   │
└───────────────────────────┬─────────────────────────────────────┘
                            │
                            ▼
┌─────────────────────────────────────────────────────────────────┐
│                   Rendering Front-End                           │
│   ChunkRenderLogic, UIRenderLogic, ItemRenderLogic              │
│   Builds DrawCommands (API-agnostic)                            │
└───────────────────────────┬─────────────────────────────────────┘
                            │ DrawCommand
                            ▼
┌─────────────────────────────────────────────────────────────────┐
│                   Rendering Back-End                            │
│   RenderBackend interface → OpenGLRenderBackend (or Vulkan)     │
│   Translates commands to graphics API calls                     │
└─────────────────────────────────────────────────────────────────┘
```

## Design Principles

### Backend Agnosticism

The fundamental principle is that **code outside the `backend/` directory never knows which graphics API is being used**. This is achieved through:

- **Interface-based design**: All rendering operations are defined in the `RenderBackend` interface
- **Abstract resource IDs**: Meshes, textures, and materials are referenced by integer IDs, not API handles
- **Factory pattern**: Backend creation is abstracted through `RenderBackendFactory`

### Why This Matters

| Benefit | Description |
|---------|-------------|
| **Portability** | Switch between OpenGL/Vulkan/DirectX without changing game code |
| **Testability** | Test rendering logic without a graphics context (headless testing) |
| **Maintainability** | Backend changes don't ripple through the codebase |
| **Future-Proofing** | Add new backends without touching existing code |

---

## Core Components

### RenderBackend Interface

Located at: `mattmc.client.renderer.backend.RenderBackend`

The `RenderBackend` interface is the contract between game logic and graphics implementation. It defines:

#### Frame Management
```java
void beginFrame();     // Called at the start of each frame
void endFrame();       // Called at the end of each frame
```

#### Draw Command Submission
```java
void submit(DrawCommand cmd);  // Submit an abstract draw command
```

#### 2D/UI Rendering
```java
void setup2DProjection(int screenWidth, int screenHeight);
void restore2DProjection();
void drawText(String text, float x, float y, float scale);
void drawCenteredText(String text, float centerX, float y, float scale);
void fillRect(float x, float y, float width, float height);
void drawRect(float x, float y, float width, float height);
void drawLine(float x1, float y1, float x2, float y2);
void setColor(int rgb, float alpha);
void resetColor();
```

#### 3D Rendering
```java
void setupPerspectiveProjection(float fov, float aspect, float near, float far);
void enableDepthTest();
void disableDepthTest();
void enableCullFace();
void disableCullFace();
void enableLighting();
void disableLighting();
void setupDirectionalLight(float dirX, float dirY, float dirZ, float brightness);
```

#### Resource Management
```java
int loadTexture(String path);
void drawTexture(int textureId, float x, float y, float width, float height);
void releaseTexture(int textureId);
```

#### Matrix Operations
```java
void pushMatrix();
void popMatrix();
void translateMatrix(float x, float y, float z);
void rotateMatrix(float angle, float x, float y, float z);
void loadIdentityMatrix();
```

#### Input Handling
```java
void setCursorPosCallback(long windowHandle, CursorPosCallback callback);
void setMouseButtonCallback(long windowHandle, MouseButtonCallback callback);
void setKeyCallback(long windowHandle, KeyCallback callback);
void setScrollCallback(long windowHandle, ScrollCallback callback);
```

#### System Queries
```java
String getGPUName();
String getDisplayResolution(long windowHandle);
int getGPUUsage();
String getGPUVRAMUsage();
```

### DrawCommand

Located at: `mattmc.client.renderer.backend.DrawCommand`

A `DrawCommand` represents a single rendering operation in an API-agnostic way:

```java
public final class DrawCommand {
    public final int meshId;        // Abstract mesh reference
    public final int materialId;    // Abstract material/shader reference
    public final int transformIndex; // Transform data reference
    public final RenderPass pass;   // Which render pass this belongs to
}
```

**Key design points:**
- No graphics API handles (GLuint, Vulkan handles, etc.)
- Uses integer IDs that the backend maps to real resources
- Includes `RenderPass` for proper render ordering
- Immutable (final fields)

### RenderPass

Located at: `mattmc.client.renderer.backend.RenderPass`

Defines the logical rendering order:

```java
public enum RenderPass {
    OPAQUE,       // Solid geometry, rendered first
    TRANSPARENT,  // Alpha-blended geometry
    SHADOW,       // Shadow map generation (reserved)
    UI            // User interface, rendered last
}
```

The backend processes commands grouped by pass to ensure correct rendering order and optimal state changes.

### RenderBackendFactory

Located at: `mattmc.client.renderer.backend.RenderBackendFactory`

Factory interface for creating backends without exposing implementation details:

```java
public interface RenderBackendFactory {
    WindowHandle createWindow(int width, int height, String title);
    RenderBackend createBackend();
    WorldRenderer createWorldRenderer();
    ItemRenderer getItemRenderer();
    
    static RenderBackendFactory createDefault();  // Returns OpenGL factory
    static RenderBackendFactory createOpenGL();   // Explicitly creates OpenGL factory
}
```

---

## Rendering Front-End

The rendering front-end determines WHAT to draw by building `DrawCommand` objects. This layer is completely API-agnostic.

### ChunkRenderLogic

Located at: `mattmc.client.renderer.ChunkRenderLogic`

Handles world/chunk rendering decisions:

```java
public class ChunkRenderLogic {
    private final ChunkMeshRegistry meshRegistry;
    private final Frustum frustum;
    
    public void buildCommands(Level world, CommandBuffer buffer) {
        for (LevelChunk chunk : world.getLoadedChunks()) {
            // Frustum culling
            if (!frustum.isChunkVisible(chunk)) continue;
            
            // Skip chunks without mesh data
            if (!meshRegistry.hasChunkMesh(chunk)) continue;
            
            // Build draw command
            int meshId = meshRegistry.getMeshIdForChunk(chunk);
            int materialId = meshRegistry.getDefaultMaterialId();
            int transformId = getTransformIdForChunk(chunk);
            
            buffer.add(new DrawCommand(meshId, materialId, transformId, RenderPass.OPAQUE));
        }
    }
}
```

**Responsibilities:**
- Frustum culling (skip chunks outside camera view)
- Check mesh availability
- Compute chunk transforms
- Create DrawCommands for visible chunks

### UIRenderLogic

Located at: `mattmc.client.renderer.UIRenderLogic`

Handles UI element rendering decisions:

```java
public class UIRenderLogic {
    public void buildCrosshairCommands(int screenWidth, int screenHeight, CommandBuffer buffer);
    public void buildHotbarCommands(int screenWidth, int screenHeight, int selectedSlot, CommandBuffer buffer);
    public void buildDebugInfoCommands(..., CommandBuffer buffer);
    public void buildTooltipCommands(String text, float mouseX, float mouseY, ...);
}
```

**Responsibilities:**
- Calculate UI element positions
- Determine what UI elements should be visible
- Encode UI data into DrawCommands
- Manage text rendering registry

### ItemRenderLogic

Located at: `mattmc.client.renderer.ItemRenderLogic`

Handles item rendering for hotbar and inventory:

```java
public class ItemRenderLogic {
    public void buildItemCommands(ItemStack stack, float x, float y, float size, CommandBuffer buffer);
}
```

### CommandBuffer

Located at: `mattmc.client.renderer.CommandBuffer`

Simple container for accumulating DrawCommands:

```java
public class CommandBuffer {
    public void add(DrawCommand cmd);
    public List<DrawCommand> getCommands();
    public void clear();
}
```

---

## Rendering Back-End (OpenGL)

Located at: `mattmc.client.renderer.backend.opengl`

The OpenGL backend implements `RenderBackend` using LWJGL to make OpenGL and GLFW calls.

### OpenGLRenderBackend

Main implementation class that:

1. **Maintains resource registries:**
   - `meshRegistry`: Maps meshId → ChunkVAO
   - `materialRegistry`: Maps materialId → Shader + TextureAtlas
   - `transformRegistry`: Maps transformId → TransformInfo
   - `textureCache`: Maps path → Texture

2. **Processes DrawCommands:**
   ```java
   @Override
   public void submit(DrawCommand cmd) {
       // Handle UI pass differently
       if (cmd.pass == RenderPass.UI) {
           submitUICommand(cmd);
           return;
       }
       
       // Look up resources
       ChunkVAO vao = meshRegistry.get(cmd.meshId);
       MaterialInfo material = materialRegistry.get(cmd.materialId);
       TransformInfo transform = transformRegistry.get(cmd.transformIndex);
       
       // Bind material if changed
       material.shader.use();
       material.atlas.bind();
       
       // Apply transform and render
       glPushMatrix();
       glTranslatef(transform.translateX, transform.translateY, transform.translateZ);
       vao.render();
       glPopMatrix();
   }
   ```

3. **Manages OpenGL state** for different scenarios (2D, 3D, blending, depth testing)

### Key OpenGL Components

| Component | Purpose |
|-----------|---------|
| `Window.java` | GLFW window + OpenGL context creation |
| `Shader.java` | GLSL shader loading, compilation, uniform management |
| `Texture.java` | Image loading, OpenGL texture creation |
| `TextureManager.java` | Texture caching with LRU eviction |
| `TextureAtlas.java` | Runtime texture atlas for block textures |
| `ChunkVAO.java` | VAO/VBO/EBO for chunk mesh data |
| `Framebuffer.java` | FBO for render-to-texture effects |
| `BlurEffect.java` | Gaussian blur post-processing |

---

## Typical Render Frame

Here's how a typical frame flows through the system:

```java
// 1. Game logic prepares data (Game/World Layer)
Level world = game.getWorld();
LocalPlayer player = game.getPlayer();

// 2. Front-end builds commands (Rendering Front-End)
CommandBuffer commands = new CommandBuffer();

// Update frustum from camera
frustum.update(camera);

// Build chunk commands
chunkRenderLogic.buildCommands(world, commands);

// Build UI commands
uiRenderLogic.buildCrosshairCommands(screenWidth, screenHeight, commands);
uiRenderLogic.buildHotbarCommands(screenWidth, screenHeight, selectedSlot, commands);
if (showDebug) {
    uiRenderLogic.buildDebugInfoCommands(..., commands);
}

// 3. Submit to backend (Rendering Back-End)
backend.beginFrame();
backend.setClearColor(0.5f, 0.7f, 1.0f, 1.0f);
backend.clearBuffers();

// Setup 3D rendering
backend.setupPerspectiveProjection(fov, aspect, 0.1f, 1000f);
backend.enableDepthTest();
backend.enableCullFace();

// Apply camera transform
backend.loadIdentityMatrix();
backend.rotateMatrix(-pitch, 1, 0, 0);
backend.rotateMatrix(-yaw, 0, 1, 0);
backend.translateMatrix(-eyeX, -eyeY, -eyeZ);

// Submit 3D commands (OPAQUE, TRANSPARENT passes)
for (DrawCommand cmd : commands.getCommands()) {
    if (cmd.pass != RenderPass.UI) {
        backend.submit(cmd);
    }
}

// Switch to 2D for UI
backend.disableDepthTest();
backend.setup2DProjection(screenWidth, screenHeight);

// Submit UI commands
for (DrawCommand cmd : commands.getCommands()) {
    if (cmd.pass == RenderPass.UI) {
        backend.submit(cmd);
    }
}

backend.restore2DProjection();
backend.endFrame();
```

---

## Abstraction Boundary Rules

### The Golden Rule

**Code outside `mattmc.client.renderer.backend` must NEVER import classes from `mattmc.client.renderer.backend.opengl`.**

### Violations to Avoid

```java
// ❌ WRONG - Direct OpenGL import
import mattmc.client.renderer.backend.opengl.Texture;
import mattmc.client.renderer.backend.opengl.Window;
import mattmc.client.renderer.backend.opengl.Shader;

// ❌ WRONG - Direct instantiation
OpenGLRenderBackend backend = new OpenGLRenderBackend();
Texture texture = Texture.load("/path/to/image.png");
```

### Correct Approach

```java
// ✅ CORRECT - Use factory
RenderBackendFactory factory = RenderBackendFactory.createDefault();
RenderBackend backend = factory.createBackend();

// ✅ CORRECT - Use backend interface
int textureId = backend.loadTexture("/path/to/image.png");
backend.drawTexture(textureId, x, y, width, height);

// ✅ CORRECT - Use DrawCommands
backend.submit(new DrawCommand(meshId, materialId, transformId, RenderPass.OPAQUE));
```

### If You Need New Functionality

1. **Define it** in the `RenderBackend` interface
2. **Implement it** in `OpenGLRenderBackend`
3. **Call it** through the interface

---

## Directory Structure

```
src/main/java/mattmc/client/renderer/
├── backend/
│   ├── RenderBackend.java          # Core interface
│   ├── RenderBackendFactory.java   # Factory interface
│   ├── DrawCommand.java            # Abstract draw command
│   ├── RenderPass.java             # Render pass enum
│   ├── README.md                   # Backend documentation
│   └── opengl/
│       ├── OpenGLRenderBackend.java
│       ├── OpenGLBackendFactory.java
│       ├── Window.java
│       ├── Shader.java
│       ├── Texture.java
│       ├── TextureManager.java
│       ├── TextureAtlas.java
│       ├── ChunkVAO.java
│       ├── Framebuffer.java
│       ├── BlurEffect.java
│       ├── OpenGLChunkRenderer.java
│       ├── OpenGLItemRenderer.java
│       ├── OpenGLPanoramaRenderer.java
│       ├── UIRenderHelper.java
│       ├── README.md
│       └── gui/
│           ├── components/         # Button, text renderers
│           └── screens/            # Menu screens
├── ChunkRenderLogic.java           # Chunk front-end logic
├── UIRenderLogic.java              # UI front-end logic
├── ItemRenderLogic.java            # Item front-end logic
├── CommandBuffer.java              # Command accumulator
├── Frustum.java                    # View frustum culling
├── WorldRenderer.java              # World rendering coordinator
├── UIRenderer.java                 # UI rendering coordinator
└── ...
```

---

## Adding a New Graphics Backend

To add a new backend (e.g., Vulkan):

### 1. Create the Directory
```
backend/vulkan/
```

### 2. Implement RenderBackend
```java
package mattmc.client.renderer.backend.vulkan;

public class VulkanRenderBackend implements RenderBackend {
    // Implement all interface methods using Vulkan
    
    @Override
    public void beginFrame() {
        // Acquire swapchain image
        // Begin command buffer recording
    }
    
    @Override
    public void submit(DrawCommand cmd) {
        // Map meshId to Vulkan vertex buffer
        // Map materialId to Vulkan pipeline
        // Record draw command
    }
    
    @Override
    public void endFrame() {
        // End command buffer
        // Submit to queue
        // Present
    }
    
    // ... implement all other methods
}
```

### 3. Implement RenderBackendFactory
```java
package mattmc.client.renderer.backend.vulkan;

public class VulkanBackendFactory implements RenderBackendFactory {
    @Override
    public WindowHandle createWindow(int width, int height, String title) {
        // Create GLFW window with Vulkan surface
    }
    
    @Override
    public RenderBackend createBackend() {
        return new VulkanRenderBackend();
    }
}
```

### 4. Add Factory Method
```java
// In RenderBackendFactory interface
static RenderBackendFactory createVulkan() {
    return new VulkanBackendFactory();
}
```

### 5. Zero Changes to Game Code
The game code continues to work unchanged - it only uses the `RenderBackend` interface.

---

## Testing and Debugging

### Headless Testing

The abstraction enables testing without a graphics context:

```java
// Create a debug backend that records commands
public class DebugRenderBackend implements RenderBackend {
    private final List<DrawCommand> commands = new ArrayList<>();
    
    @Override
    public void submit(DrawCommand cmd) {
        commands.add(cmd);  // Just record, don't render
    }
    
    public List<DrawCommand> getCommands() {
        return Collections.unmodifiableList(commands);
    }
}

// In tests
DebugRenderBackend debug = new DebugRenderBackend();
chunkRenderLogic.buildCommands(testWorld, buffer);
for (DrawCommand cmd : buffer.getCommands()) {
    debug.submit(cmd);
}

// Assert on recorded commands
assertEquals(expectedChunkCount, debug.getCommands().size());
```

### Debug Output

The `DrawCommand.toString()` method provides readable output:
```
DrawCommand{meshId=42, materialId=1, transformIndex=1048576, pass=OPAQUE}
```

---

## Performance Considerations

### Command Batching
The backend can batch commands by material to reduce state changes:
```java
// Sort commands by materialId before processing
commands.sort(Comparator.comparingInt(cmd -> cmd.materialId));
```

### Resource Caching
- Textures are cached by path in `TextureManager`
- Chunk VAOs are pooled in `ChunkMeshRegistry`
- Materials are registered once and reused

### Frustum Culling
`ChunkRenderLogic` performs frustum culling to skip non-visible chunks before creating DrawCommands.

### Minimal State Changes
The backend tracks current shader/texture to avoid redundant binds:
```java
if (material.shader != currentShader) {
    material.shader.use();
    currentShader = material.shader;
}
```

---

## Future Directions

1. **Vulkan Backend** - The architecture is ready; implementation is future work
2. **WebGPU Backend** - Could enable browser-based deployment
3. **Render Graph** - Automatic pass dependency management
4. **GPU Instancing** - Batch similar meshes in single draw calls
5. **Async Resource Loading** - Background texture/mesh streaming

---

## Related Documentation

- `src/main/java/mattmc/client/renderer/backend/README.md` - Backend abstraction details
- `src/main/java/mattmc/client/renderer/backend/opengl/README.md` - OpenGL implementation details
- `docs/JSON_MODEL_SYSTEM.md` - Model loading system documentation
