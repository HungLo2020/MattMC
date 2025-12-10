# Step 17: Render Target System Implementation

## Overview

Implemented render target system (framebuffer management) following IRIS 1.21.9 patterns exactly.

## Date Completed

December 10, 2025

## Implementation Details

### GlResource Base Class

**File:** `net/minecraft/client/renderer/shaders/gl/GlResource.java`  
**Lines:** 34  
**IRIS Reference:** `gl/GlResource.java` (VERBATIM COPY)

Base class for OpenGL resources requiring lifecycle management:
- Resource ID tracking
- Validity state management
- Abstract destroy pattern
- Resource cleanup enforcement

**Key Methods:**
```java
protected GlResource(int id)        // Constructor with OpenGL ID
public final void destroy()          // Mark resource as destroyed
protected abstract void destroyInternal()  // Subclass cleanup
protected int getGlId()             // Access OpenGL ID (validates first)
protected void assertValid()        // Throws if resource destroyed
```

**Pattern:**
- Prevents use-after-free errors
- Enforces proper cleanup via destroyInternal()
- Single responsibility: lifecycle management

### GlFramebuffer Class

**File:** `net/minecraft/client/renderer/shaders/framebuffer/GlFramebuffer.java`  
**Lines:** 133  
**IRIS Reference:** `gl/framebuffer/GlFramebuffer.java` (structure exact)

Wrapper for OpenGL framebuffer objects with attachment management:
- Framebuffer creation (glGenFramebuffers)
- Color attachment management (colortex0-15)
- Depth attachment support (depth, depth-stencil)
- Draw buffers configuration
- Read buffer configuration
- Multiple bind modes (framebuffer, read, draw)

**Constructor:**
```java
public GlFramebuffer()
```
- Creates OpenGL framebuffer
- Queries GPU capabilities (MAX_DRAW_BUFFERS, MAX_COLOR_ATTACHMENTS)
- Initializes attachment tracking

**Color Attachments:**
```java
public void addColorAttachment(int index, int texture)
```
- Binds texture to GL_COLOR_ATTACHMENT0 + index
- Validates index < maxColorAttachments
- Tracks attachment mapping

**Depth Attachments:**
```java
public void addDepthAttachment(int texture)
public void addDepthStencilAttachment(int texture)
```
- Depth: GL_DEPTH_ATTACHMENT
- Depth-Stencil: GL_DEPTH_STENCIL_ATTACHMENT
- Sets hasDepthAttachment flag

**Draw Buffers:**
```java
public void noDrawBuffers()                  // GL_NONE (no color output)
public void drawBuffers(int[] buffers)       // Multiple color outputs
```
- Configures color buffer outputs
- Validates buffer count <= maxDrawBuffers
- Validates buffer indices < maxColorAttachments

**Read Buffer:**
```java
public void readBuffer(int buffer)
```
- Configures source for glReadPixels/glBlitFramebuffer
- Sets GL_READ_BUFFER to GL_COLOR_ATTACHMENT0 + buffer

**Binding:**
```java
public void bind()                    // GL_FRAMEBUFFER (read+write)
public void bindAsReadBuffer()       // GL_READ_FRAMEBUFFER
public void bindAsDrawBuffer()       // GL_DRAW_FRAMEBUFFER
```
- Three bind modes for different operations
- Read-only: blit source
- Draw-only: render target
- Both: normal rendering

**Status:**
```java
public int getStatus()
```
- Returns glCheckFramebufferStatus result
- GL_FRAMEBUFFER_COMPLETE = success
- Other values indicate configuration errors

**Cleanup:**
```java
@Override
protected void destroyInternal()
```
- Calls glDeleteFramebuffers
- Inherited from GlResource
- Automatic lifecycle management

## IRIS Adherence

**100% Structural Match:**
- GlResource: Exact copy from IRIS
- GlFramebuffer: Structure matches IRIS exactly

**API Differences:**
- MattMC uses `com.mojang.blaze3d.opengl.GlStateManager`
- IRIS uses `net.irisshaders.iris.gl.IrisRenderSystem`
- Equivalent functionality, different wrapper

**IRIS Lines Referenced:**
- `gl/GlResource.java:1-30` (base class)
- `gl/framebuffer/GlFramebuffer.java:1-117` (framebuffer wrapper)

## Testing

### Test Files Created

**GlResourceTest.java** (77 lines, 7 tests):
1. `testResourceCreation()` - Constructor and ID access
2. `testResourceDestroy()` - Destruction marking
3. `testDestroyedResourceThrows()` - Use-after-destroy validation
4. `testMultipleDestroyCalls()` - Idempotent destroy
5. `testDifferentResourceIds()` - Multiple resource independence
6. `testZeroId()` - Zero ID handling
7. `testNegativeId()` - Negative ID handling

**GlFramebufferTest.java** (88 lines, 15 tests):
1. `testFramebufferStructure()` - Class exists
2. `testHasColorAttachmentMethod()` - addColorAttachment method
3. `testHasDepthAttachmentMethod()` - addDepthAttachment method
4. `testHasDepthStencilAttachmentMethod()` - addDepthStencilAttachment method
5. `testHasDrawBuffersMethod()` - drawBuffers method
6. `testHasNoDrawBuffersMethod()` - noDrawBuffers method
7. `testHasReadBufferMethod()` - readBuffer method
8. `testHasGetColorAttachmentMethod()` - getColorAttachment method
9. `testHasDepthAttachmentCheckMethod()` - hasDepthAttachment method
10. `testHasBindMethod()` - bind method
11. `testHasBindAsReadBufferMethod()` - bindAsReadBuffer method
12. `testHasBindAsDrawBufferMethod()` - bindAsDrawBuffer method
13. `testHasGetStatusMethod()` - getStatus method
14. `testHasGetIdMethod()` - getId method
15. `testHasDestroyMethod()` - destroy method (inherited)

### Test Results

```
GlResourceTest: 7/7 passing
GlFramebufferTest: 15/15 passing
Total: 22/22 passing (100%)
```

**Overall Shader Test Suite:** 351 tests passing

### Test Strategy

**Structure Tests:**
- Verify all IRIS methods present
- Validate inheritance from GlResource
- Check method signatures match IRIS

**Note:** 
- Tests verify class structure, not OpenGL behavior
- OpenGL behavior requires GPU/context (tested via integration later)
- This matches testing pattern from Steps 10-16

## Usage Examples

### Basic Framebuffer Creation

```java
// Create framebuffer
GlFramebuffer fbo = new GlFramebuffer();

// Add color attachment
int colorTexture = GlStateManager.glGenTextures();
// ... configure texture ...
fbo.addColorAttachment(0, colorTexture);

// Add depth attachment
int depthTexture = GlStateManager.glGenTextures();
// ... configure texture ...
fbo.addDepthAttachment(depthTexture);

// Configure for rendering
fbo.bind();
fbo.drawBuffers(new int[]{0});  // Write to colortex0

// Check completeness
int status = fbo.getStatus();
if (status != GL30C.GL_FRAMEBUFFER_COMPLETE) {
    throw new RuntimeException("Framebuffer incomplete: " + status);
}

// Cleanup
fbo.destroy();
```

### Multiple Render Targets (MRT)

```java
GlFramebuffer fbo = new GlFramebuffer();

// Add multiple color attachments
fbo.addColorAttachment(0, colorTex0);
fbo.addColorAttachment(1, colorTex1);
fbo.addColorAttachment(2, colorTex2);
fbo.addColorAttachment(3, colorTex3);

// Write to all 4 attachments
fbo.bind();
fbo.drawBuffers(new int[]{0, 1, 2, 3});

// Render...
// Fragment shader outputs to gl_FragData[0-3]
```

### Framebuffer Blitting

```java
GlFramebuffer sourceFbo = ...;
GlFramebuffer destFbo = ...;

// Bind for read/write
sourceFbo.bindAsReadBuffer();
sourceFbo.readBuffer(0);  // Read from colortex0

destFbo.bindAsDrawBuffer();
destFbo.drawBuffers(new int[]{1});  // Write to colortex1

// Blit
GlStateManager._glBlitFrameBuffer(
    0, 0, width, height,  // Source
    0, 0, width, height,  // Dest
    GL30C.GL_COLOR_BUFFER_BIT,
    GL30C.GL_NEAREST
);
```

### Depth-Only Rendering (Shadow Maps)

```java
GlFramebuffer shadowFbo = new GlFramebuffer();

// Add depth texture only
shadowFbo.addDepthAttachment(shadowDepthTexture);

// No color output
shadowFbo.bind();
shadowFbo.noDrawBuffers();

// Render depth-only
// Fragment shader doesn't output color
```

## Integration with Existing Code

### G-Buffer Manager Integration (Step 16)

GlFramebuffer will be used by GBufferManager to manage colortex0-15:

```java
public class GBufferManager {
    private GlFramebuffer mainFramebuffer;
    
    public void createFramebuffer() {
        mainFramebuffer = new GlFramebuffer();
        
        // Attach all active render targets
        for (int i = 0; i < 16; i++) {
            RenderTarget target = getOrCreate(i);
            if (target != null) {
                mainFramebuffer.addColorAttachment(i, target.getMainTexture());
            }
        }
        
        // Attach depth
        mainFramebuffer.addDepthAttachment(depthTexture);
    }
}
```

### Future Steps

**Step 18 (Framebuffer Binding System):**
- State tracking for active framebuffer
- Bind/unbind management
- Integration with vanilla rendering

**Step 19 (Depth Buffer Management):**
- depthtex0, depthtex1, depthtex2 creation
- Depth texture formats
- Depth copying (pre-translucent, pre-hand)

**Step 20 (Shadow Framebuffer System):**
- Shadow map framebuffers
- shadowtex0, shadowtex1
- shadowcolor0, shadowcolor1
- Shadow pass rendering

## Files Modified

**Created:**
- `net/minecraft/client/renderer/shaders/gl/GlResource.java`
- `net/minecraft/client/renderer/shaders/framebuffer/GlFramebuffer.java`
- `src/test/java/net/minecraft/client/renderer/shaders/gl/GlResourceTest.java`
- `src/test/java/net/minecraft/client/renderer/shaders/framebuffer/GlFramebufferTest.java`

**Modified:**
- None (new subsystem)

## Key Achievements

1. ✅ **IRIS-Exact Structure**: GlResource verbatim copy, GlFramebuffer structure match
2. ✅ **Comprehensive API**: All framebuffer operations supported
3. ✅ **Lifecycle Management**: Proper creation and cleanup
4. ✅ **GPU Capability Aware**: Respects MAX_DRAW_BUFFERS and MAX_COLOR_ATTACHMENTS
5. ✅ **Multiple Bind Modes**: Read, write, both
6. ✅ **MRT Support**: Multiple render targets (shader pack requirement)
7. ✅ **22/22 Tests Passing**: Full structure validation

## Next Steps

**Step 18:** Framebuffer binding system with state tracking  
**Goal:** Manage framebuffer bindings during rendering phases

## References

- IRIS `gl/GlResource.java` - Base class pattern
- IRIS `gl/framebuffer/GlFramebuffer.java` - Framebuffer wrapper
- OpenGL 3.0+ Specification - Framebuffer Objects
- MattMC `com.mojang.blaze3d.opengl.GlStateManager` - OpenGL wrapper
