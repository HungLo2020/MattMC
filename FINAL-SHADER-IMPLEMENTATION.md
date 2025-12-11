# Final Shader Implementation Guide

## Overview

This document provides a detailed analysis of what's missing to get IRIS-compatible shaders rendering in MattMC, along with step-by-step implementation instructions. Each step is designed to be completable in one AI session.

## Current State Analysis

### What's Implemented (90% Complete According to Docs)
- ✅ Foundation Phase (Steps 1-5): Core structure, config, repository, properties, pipeline manager
- ✅ Loading System Phase (Steps 6-10): Include processor, source provider, options, dimensions, validation
- ✅ Compilation System Phase (Steps 11-15): Compiler, builder, cache, parallel compilation, program sets
- ✅ Rendering Infrastructure Phase (Steps 16-20): G-buffer manager, render targets, framebuffers, depth, shadows
- ✅ Uniforms Phase (Steps 26-27): 105+ uniforms implemented
- ✅ Composite/Final Renderer Structure (Steps 28-29): Classes exist but are stubs

### What's Actually Missing (Critical Gaps)

After thorough analysis, the shader system is NOT rendering because of these critical missing pieces:

#### 1. **LevelRenderer Integration (CRITICAL)**
The `LevelRenderer.java` has NO integration with the shader system. The hooks exist (`RenderingHooks.java`) but are never called.

**Missing calls in LevelRenderer.renderLevel():**
- `RenderingHooks.onWorldRenderStart()` - at method start
- `RenderingHooks.onWorldRenderEnd()` - at method end
- Phase transitions for terrain, entities, translucent, etc.

#### 2. **Pipeline Activation (CRITICAL)**
`RenderingHooks.activePipeline` is always `null` because nothing ever sets it. The `ShaderPackPipeline` is created but never connected to `ShaderRenderingPipeline`.

#### 3. **ShaderPackPipeline is a Stub**
`ShaderPackPipeline.beginLevelRendering()` and other methods are empty stubs that just log. They need to:
- Bind G-buffer framebuffers for MRT output
- Set up shader programs for geometry passes
- Execute composite passes after geometry
- Execute final pass to screen

#### 4. **No Shader Program Compilation**
The shader pack's GLSL files are never compiled into OpenGL programs. The compilation infrastructure exists but is never invoked.

#### 5. **No G-Buffer Binding During Rendering**
The `GBufferManager` exists but is never bound during rendering. Geometry continues rendering to the vanilla framebuffer.

#### 6. **No Composite/Final Pass Execution**
`CompositeRenderer` and `FinalPassRenderer` exist but their `renderAll()` and `renderFinalPass()` methods are never called.

---

## Implementation Steps

Each step below is designed to be completable in one AI session (~1-2 hours).

---

### Step A1: Add LevelRenderer Hooks

**Goal**: Add calls to `RenderingHooks` in `LevelRenderer.java`

**Files to Modify**: `net/minecraft/client/renderer/LevelRenderer.java`

**Implementation**:

1. Add import at the top:
```java
import net.minecraft.client.renderer.shaders.hooks.RenderingHooks;
```

2. Find the `renderLevel()` method (search for `public void renderLevel(`) and add at the START (after `float f = deltaTracker...`):
```java
// Shader system hooks - begin world rendering
RenderingHooks.onWorldRenderStart();
```

3. Find the END of `renderLevel()` (before `matrix4fStack.popMatrix()`) and add:
```java
// Shader system hooks - end world rendering
RenderingHooks.onWorldRenderEnd();
```

4. Find terrain rendering calls (look for `renderSectionLayer` or `renderChunkLayer`) and wrap with:
```java
RenderingHooks.onBeginTerrainRendering();
// existing terrain rendering...
RenderingHooks.onEndTerrainRendering();
```

**Verification**: Build should compile. Shader logs should show hook calls when in-game.

---

### Step A2: Connect PipelineManager to RenderingHooks

**Goal**: Make `RenderingHooks` use the active pipeline from `PipelineManager`

**Files to Modify**: 
- `net/minecraft/client/renderer/shaders/hooks/RenderingHooks.java`
- `net/minecraft/client/renderer/shaders/pipeline/PipelineManager.java`

**Implementation**:

1. In `RenderingHooks.onWorldRenderStart()`, change to:
```java
public static void onWorldRenderStart() {
    // Get pipeline from PipelineManager
    net.minecraft.client.renderer.shaders.core.ShaderSystem system = 
        net.minecraft.client.renderer.shaders.core.ShaderSystem.getInstance();
    
    if (system.isInitialized() && system.getPipelineManager() != null) {
        WorldRenderingPipeline pipeline = system.getPipelineManager().preparePipeline(
            getCurrentDimension()
        );
        
        if (pipeline != null && !(pipeline instanceof VanillaRenderingPipeline)) {
            pipeline.beginLevelRendering();
            LOGGER.debug("Shader pipeline activated for rendering");
        }
    }
}
```

2. Add helper method to get current dimension:
```java
private static String getCurrentDimension() {
    Minecraft mc = Minecraft.getInstance();
    if (mc.level != null) {
        return mc.level.dimension().location().toString();
    }
    return "minecraft:overworld";
}
```

3. Update `onWorldRenderEnd()` similarly to call `finalizeLevelRendering()`.

**Verification**: Pipeline should now be activated when rendering. Check logs for "Shader pipeline activated".

---

### Step A3: Implement ShaderPackPipeline.beginLevelRendering()

**Goal**: Make `ShaderPackPipeline.beginLevelRendering()` actually bind G-buffers

**Files to Modify**: `net/minecraft/client/renderer/shaders/pipeline/ShaderPackPipeline.java`

**Implementation**:

1. Add field for GBufferManager:
```java
private final GBufferManager gBufferManager;
```

2. Initialize in constructor:
```java
this.gBufferManager = new GBufferManager();
```

3. Implement `beginLevelRendering()`:
```java
@Override
public void beginLevelRendering() {
    LOGGER.debug("Begin level rendering with shader pack: {}", packName);
    
    // Initialize G-buffers if needed
    Minecraft mc = Minecraft.getInstance();
    int width = mc.getWindow().getWidth();
    int height = mc.getWindow().getHeight();
    
    gBufferManager.initialize(width, height);
    
    // Bind G-buffer framebuffer for MRT output
    gBufferManager.bindForWriting();
    
    // Clear all buffers
    org.lwjgl.opengl.GL11.glClear(
        org.lwjgl.opengl.GL11.GL_COLOR_BUFFER_BIT | 
        org.lwjgl.opengl.GL11.GL_DEPTH_BUFFER_BIT
    );
}
```

**Verification**: G-buffers should be created. May see black screen initially (that's progress!).

---

### Step A4: Implement ShaderPackPipeline.finalizeLevelRendering()

**Goal**: Execute composite and final passes, output to screen

**Files to Modify**: `net/minecraft/client/renderer/shaders/pipeline/ShaderPackPipeline.java`

**Implementation**:

1. Implement `finalizeLevelRendering()`:
```java
@Override
public void finalizeLevelRendering() {
    LOGGER.debug("Finalize level rendering with shader pack: {}", packName);
    
    // Unbind G-buffer framebuffer
    gBufferManager.unbindForWriting();
    
    // TODO: Execute composite passes (Step A7)
    // compositeRenderer.renderAll();
    
    // TODO: Execute final pass (Step A8)
    // finalPassRenderer.renderFinalPass();
    
    // For now, just copy colortex0 to screen
    copyColorToScreen();
}

private void copyColorToScreen() {
    Minecraft mc = Minecraft.getInstance();
    RenderTarget mainTarget = mc.getMainRenderTarget();
    
    if (mainTarget == null) {
        LOGGER.warn("Main render target is null, cannot copy to screen");
        return;
    }
    
    // Bind screen framebuffer
    mainTarget.bindWrite(true);
    
    // Bind colortex0 for reading
    RenderTarget colorTex0 = gBufferManager.getOrCreate(0);
    if (colorTex0 != null) {
        org.lwjgl.opengl.GL30.glBindFramebuffer(
            org.lwjgl.opengl.GL30.GL_READ_FRAMEBUFFER,
            colorTex0.getFramebufferId()
        );
        
        // Blit to screen
        org.lwjgl.opengl.GL30.glBlitFramebuffer(
            0, 0, colorTex0.getWidth(), colorTex0.getHeight(),
            0, 0, mainTarget.width, mainTarget.height,
            org.lwjgl.opengl.GL11.GL_COLOR_BUFFER_BIT,
            org.lwjgl.opengl.GL11.GL_NEAREST
        );
    }
}
```

**Verification**: Should now see the scene rendered (through G-buffer). May look vanilla but confirms pipeline is working.

---

### Step A5: Compile Shader Pack Programs

**Goal**: Actually compile the GLSL shaders from the pack into OpenGL programs

**Files to Modify**: `net/minecraft/client/renderer/shaders/pipeline/ShaderPackPipeline.java`

**Implementation**:

1. Add program cache field:
```java
private final ProgramCache programCache;
private Program gbuffersTerrainProgram;
```

2. In constructor, after loading source provider, compile programs:
```java
this.programCache = new ProgramCache();
compilePrograms();
```

3. Add compile method:
```java
private void compilePrograms() {
    try {
        // Try to load gbuffers_terrain
        Optional<String> vertexSource = sourceProvider.readFile("gbuffers_terrain.vsh");
        Optional<String> fragmentSource = sourceProvider.readFile("gbuffers_terrain.fsh");
        
        if (vertexSource.isPresent() && fragmentSource.isPresent()) {
            ProgramSource source = new ProgramSource(
                "gbuffers_terrain",
                vertexSource.get(),
                null, // geometry
                null, // tessControl
                null, // tessEval
                fragmentSource.get()
            );
            
            gbuffersTerrainProgram = ProgramBuilder.create()
                .setName("gbuffers_terrain")
                .setSource(source)
                .build();
            
            LOGGER.info("Compiled gbuffers_terrain program");
        }
    } catch (Exception e) {
        LOGGER.error("Failed to compile shader programs", e);
    }
}
```

**Verification**: Should see "Compiled gbuffers_terrain program" in logs.

---

### Step A6: Bind Shader Programs During Rendering

**Goal**: Use compiled shader programs instead of vanilla shaders

**Files to Modify**: 
- `net/minecraft/client/renderer/shaders/pipeline/ShaderPackPipeline.java`
- `net/minecraft/client/renderer/shaders/interception/` (new package)

**Implementation**:

This is complex - requires intercepting vanilla shader binding. Two approaches:

**Approach A (Simple - Override at RenderSystem level)**:
```java
// In beginLevelRendering(), after binding G-buffers:
if (gbuffersTerrainProgram != null) {
    gbuffersTerrainProgram.use();
}
```

**Approach B (Proper - Shader interception system)**:
Create interceptor that replaces vanilla programs with shader pack programs based on current phase.

See IRIS `MixinShaderManager_Overrides.java` for reference.

**Verification**: Rendering should now use shader pack shaders. Visual changes expected.

---

### Step A7: Implement Composite Passes

**Goal**: Execute composite post-processing passes

**Files to Modify**: `net/minecraft/client/renderer/shaders/pipeline/CompositeRenderer.java`

**Implementation**:

1. Fully implement `renderAll()`:
```java
public void renderAll() {
    if (passes.isEmpty()) {
        return;
    }
    
    for (Pass pass : passes) {
        // Bind framebuffer for this pass
        pass.framebuffer.bind();
        
        // Set viewport
        ViewportData viewport = pass.viewportData;
        GL11.glViewport(0, 0, viewport.width(), viewport.height());
        
        // Bind input textures
        bindInputTextures(pass);
        
        // Use program
        pass.program.use();
        
        // Update uniforms
        updatePassUniforms(pass);
        
        // Render full-screen quad
        FullScreenQuadRenderer.INSTANCE.render();
        
        // Flip buffers for ping-pong
        flipBuffers(pass);
    }
}
```

2. Implement texture binding and uniform updates.

**Verification**: Post-processing effects should now be visible.

---

### Step A8: Implement Final Pass

**Goal**: Output final result to screen with optional final.fsh shader

**Files to Modify**: `net/minecraft/client/renderer/shaders/pipeline/FinalPassRenderer.java`

**Implementation**:

1. Implement `renderFinalPass()`:
```java
public void renderFinalPass() {
    // First, swap any flipped buffers back to main
    for (SwapPass swap : swapPasses) {
        swapBuffer(swap);
    }
    
    // Bind screen framebuffer
    Minecraft.getInstance().getMainRenderTarget().bindWrite(true);
    
    if (finalPass != null && finalPass.program != null) {
        // Use final shader program
        finalPass.program.use();
        
        // Bind all textures
        bindFinalTextures();
        
        // Render full-screen quad
        FullScreenQuadRenderer.INSTANCE.render();
    } else {
        // Fallback: direct copy from colortex0
        copyColorBufferToScreen();
    }
}
```

**Verification**: Final output should match shader pack's intended look.

---

### Step A9: Shadow Pass Integration

**Goal**: Render shadow maps before main geometry

**Files to Modify**: 
- `net/minecraft/client/renderer/shaders/shadows/ShadowRenderer.java`
- `net/minecraft/client/renderer/shaders/pipeline/ShaderPackPipeline.java`

**Implementation**:

1. Add shadow renderer field to ShaderPackPipeline
2. In `beginLevelRendering()`, render shadows first:
```java
if (shadowRenderer != null && shadowRenderer.areShadowsEnabled()) {
    renderShadowPass();
}
```

3. Implement shadow pass that:
   - Binds shadow framebuffer
   - Sets up orthographic projection from sun direction
   - Renders all shadow-casting geometry
   - Stores depth in shadowtex0/shadowtex1

**Verification**: Shadow maps should be populated. Check with debug output.

---

### Step A10: Uniform System Integration

**Goal**: Pass all uniforms to shader programs each frame

**Files to Modify**: `net/minecraft/client/renderer/shaders/pipeline/ShaderPackPipeline.java`

**Implementation**:

1. Add uniform manager:
```java
private final UniformHolder uniformHolder;
```

2. Before rendering each pass, update uniforms:
```java
private void updateUniforms(Program program) {
    // Matrix uniforms
    program.setUniform("gbufferModelView", getModelViewMatrix());
    program.setUniform("gbufferProjection", getProjectionMatrix());
    
    // Time uniforms
    program.setUniform("frameTimeCounter", SystemTimeUniforms.TIMER.getAsFloat());
    
    // Camera uniforms
    Camera camera = Minecraft.getInstance().gameRenderer.getMainCamera();
    program.setUniform("cameraPosition", camera.getPosition());
    
    // ... all other uniforms from providers
}
```

**Verification**: Shader effects that depend on time/camera should animate.

---

### Step A11: GBufferManager Fixes

**Goal**: Ensure all G-buffer operations work correctly

**Files to Modify**: `net/minecraft/client/renderer/shaders/targets/GBufferManager.java`

**Implementation**:

1. Verify `bindForWriting()` correctly sets up MRT:
```java
public void bindForWriting() {
    if (framebuffer == null) {
        createFramebuffer();
    }
    
    framebuffer.bind();
    
    // Set draw buffers for MRT
    int[] drawBuffers = new int[activeColorBuffers];
    for (int i = 0; i < activeColorBuffers; i++) {
        drawBuffers[i] = GL30.GL_COLOR_ATTACHMENT0 + i;
    }
    GL20.glDrawBuffers(drawBuffers);
}
```

2. Add `unbindForWriting()`:
```java
public void unbindForWriting() {
    GlFramebuffer.unbind();
}
```

**Verification**: Multiple render targets should receive different data.

---

### Step A12: Test with Complementary Shaders

**Goal**: Verify full pipeline works with real shader pack

**Files to Modify**: None (testing step)

**Verification Steps**:

1. Build the game
2. Launch and create a new world
3. Open Video Settings → Shaders
4. Select "ComplementaryHungLoIfied"
5. Apply and enter world
6. Verify visual effects:
   - Shadows rendering correctly
   - Lighting looks different from vanilla
   - Post-processing effects visible (bloom, DOF if enabled)
   - No magenta/black missing textures

**Expected Issues to Fix**:
- Missing include files (need to process #include)
- Missing uniforms (add as discovered)
- Shader compilation errors (debug and fix)

---

## Quick Reference: File Locations

| Component | Location |
|-----------|----------|
| LevelRenderer | `net/minecraft/client/renderer/LevelRenderer.java` |
| RenderingHooks | `net/minecraft/client/renderer/shaders/hooks/RenderingHooks.java` |
| ShaderPackPipeline | `net/minecraft/client/renderer/shaders/pipeline/ShaderPackPipeline.java` |
| PipelineManager | `net/minecraft/client/renderer/shaders/pipeline/PipelineManager.java` |
| GBufferManager | `net/minecraft/client/renderer/shaders/targets/GBufferManager.java` |
| CompositeRenderer | `net/minecraft/client/renderer/shaders/pipeline/CompositeRenderer.java` |
| FinalPassRenderer | `net/minecraft/client/renderer/shaders/pipeline/FinalPassRenderer.java` |
| ShadowRenderer | `net/minecraft/client/renderer/shaders/shadows/ShadowRenderer.java` |
| IRIS Reference | `frnsrc/Iris-1.21.9/common/src/main/java/net/irisshaders/iris/` |

## IRIS Reference Files

For each implementation step, reference these IRIS files:

| MattMC Class | IRIS Reference |
|--------------|----------------|
| ShaderPackPipeline | `pipeline/IrisRenderingPipeline.java` |
| RenderingHooks | `mixin/MixinLevelRenderer.java` |
| GBufferManager | `targets/RenderTargets.java` |
| CompositeRenderer | `pipeline/CompositeRenderer.java` |
| FinalPassRenderer | `pipeline/FinalPassRenderer.java` |
| ShadowRenderer | `shadows/ShadowRenderer.java` |
| Uniform Providers | `uniforms/*.java` |

---

## Summary

The shader system has extensive infrastructure but is missing the critical integration layer that connects everything together. The key missing pieces are:

1. **LevelRenderer doesn't call RenderingHooks** - Easy fix
2. **RenderingHooks doesn't get the pipeline** - Easy fix
3. **ShaderPackPipeline methods are stubs** - Medium complexity
4. **Shader programs never compiled** - Medium complexity
5. **Composite/final passes never executed** - Medium complexity
6. **Shadow pass not integrated** - Higher complexity

Following the steps above in order (A1 → A12) will progressively enable shader rendering. Each step builds on the previous, so they should be done sequentially.

---

*Document created: December 11, 2024*
*For MattMC shader system integration*
