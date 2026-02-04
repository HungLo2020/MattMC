# Bug Fix: Terrain Rendering with Shaders

## Problem Report

**Symptoms:**
- ✗ Most terrain invisible when loading into world
- ✗ Sky appears black instead of blue
- ℹ️ Occurs when using shader mods (Iris)

## Root Cause Analysis

### Issue 1: State Tracker Desynchronization

The `GlStateManager` class maintains state trackers for OpenGL state (e.g., `SCISSOR.mode.enabled`). These trackers are used by:
- The game to avoid redundant state changes
- Iris shader mod to track OpenGL state

**Problem:**
When routing through Vulkanic, we called OpenGL functions directly via the backend, but didn't update the state trackers. This caused:
- State trackers to become out of sync with actual OpenGL state
- Iris shader mod to make incorrect assumptions about state
- Rendering operations to be skipped or applied incorrectly

**Solution:**
Update state trackers when calling through Vulkanic:
```java
if (net.vulkanic.Vulkanic.isInitialized()) {
    net.vulkanic.Vulkanic.getDevice().createCommandBuffer().enableScissorTest();
    SCISSOR.mode.enabled = true; // ← Keep tracker in sync!
}
```

### Issue 2: Object Churn

**Problem:**
Creating a new `OpenGLCommandBuffer` instance for every operation:
```java
device.createCommandBuffer().clearBuffers(i);
device.createCommandBuffer().setViewport(x, y, w, h);
device.createCommandBuffer().enableScissorTest();
```

At 60 FPS with hundreds of operations per frame, this creates thousands of short-lived objects per second.

**Solution:**
Use a shared command buffer instance in `OpenGLDevice`:
```java
public class OpenGLDevice implements VulkanicDevice {
    private final OpenGLCommandBuffer sharedCommandBuffer;
    
    public OpenGLDevice() {
        this.sharedCommandBuffer = new OpenGLCommandBuffer();
    }
    
    @Override
    public VulkanicCommandBuffer createCommandBuffer() {
        return sharedCommandBuffer; // Reuse the same instance
    }
}
```

This works because OpenGL backend uses immediate-mode rendering - operations execute immediately, no buffering needed.

### Issue 3: Early Initialization

**Problem:**
Some rendering operations happen before `RenderSystem.initRenderer()` initializes Vulkanic. Calling `Vulkanic.getDevice()` before initialization throws an exception.

**Solution:**
Add initialization guards with fallback:
```java
if (net.vulkanic.Vulkanic.isInitialized()) {
    net.vulkanic.Vulkanic.getDevice().createCommandBuffer().clearBuffers(i);
} else {
    GL11.glClear(i); // Fallback to direct OpenGL
}
```

## Implementation

### Modified Files

**GlStateManager.java:**
- `_disableScissorTest()` - Routes through Vulkanic + updates state tracker
- `_enableScissorTest()` - Routes through Vulkanic + updates state tracker
- `_scissorBox()` - Routes through Vulkanic
- `_viewport()` - Routes through Vulkanic with init check
- `_clear()` - Routes through Vulkanic with init check

**OpenGLDevice.java:**
- Added `sharedCommandBuffer` field
- `createCommandBuffer()` returns shared instance

### Call Flow

```
Game Code (e.g., terrain rendering)
  → GlStateManager._enableScissorTest()
    → Vulkanic.isInitialized()? ✓
      → Vulkanic.getDevice().createCommandBuffer() (returns shared instance)
        → OpenGLCommandBuffer.enableScissorTest()
          → GL11.glEnable(GL_SCISSOR_TEST)
    → SCISSOR.mode.enabled = true (state tracker updated)
```

## Testing

### Expected Behavior After Fix

✓ Terrain renders correctly  
✓ Sky displays proper color/shaders  
✓ Shader mods (Iris) work correctly  
✓ State trackers stay synchronized  
✓ No object churn from command buffers  
✓ Early operations fall back gracefully  

### Verification

1. **Load into world** - Terrain should be visible
2. **Check sky** - Should show blue/shader effects, not black
3. **With shaders** - Iris shaders should render correctly
4. **Without shaders** - Vanilla rendering should work

## Lessons Learned

1. **State Management**: When wrapping OpenGL, must keep ALL state trackers in sync
2. **Object Pooling**: Immediate-mode operations don't need unique instances
3. **Initialization Order**: Always guard against early calls before initialization
4. **Third-party Mods**: Integration must preserve mod compatibility (e.g., Iris)

## Related Documentation

- `VULKANIC-ARCHITECTURE.md` - Architecture rules
- `BUGFIX-COLOR-RENDERING.md` - Previous color bug fix
- `VULKANIC-REAL-INTEGRATION.md` - Integration overview
