# Advanced Rendering Architecture

## Overview

This document details the unified rendering architecture for MattMC that integrates Sodium and Iris as first-class core subsystems rather than external mods. This architecture represents the target state of the deep integration plan outlined in `INTEGRATION.md`.

## Executive Summary

**Purpose**: Transform Sodium (terrain optimization) and Iris (shader packs) from dynamically-loaded mods into native, deeply-integrated components of the MattMC rendering engine.

**Key Principles**:
1. **First-Class Integration** - No separate JARs, no mod loading, no runtime discovery
2. **Graceful Degradation** - Features can be disabled with fallback to vanilla rendering
3. **Clear Boundaries** - Well-defined interfaces between core renderer and advanced features
4. **Maintainability** - Documented APIs, clear responsibilities, testable components
5. **Performance Parity** - Zero performance regression compared to mod-based approach

---

## Architecture Components

### 1. Package Structure

The advanced rendering subsystems are organized under `net.minecraft.client.renderer.advanced/`:

```
net.minecraft.client.renderer.advanced/
├── terrain/                    # Sodium integration (chunk rendering optimization)
│   ├── api/                    # Public APIs for terrain rendering
│   ├── chunk/                  # Chunk mesh building and rendering
│   ├── vertex/                 # Vertex format optimization
│   ├── gl/                     # OpenGL abstractions and command buffers
│   └── culling/                # Frustum and occlusion culling
│
├── shaders/                    # Iris integration (shader pack support)
│   ├── api/                    # Public APIs for shader system
│   ├── pack/                   # Shader pack loading and parsing
│   ├── pipeline/               # Rendering pipeline management
│   ├── uniforms/               # Shader uniform system
│   ├── framebuffers/           # Framebuffer and render target management
│   └── programs/               # Shader program compilation and management
│
└── options/                    # Configuration and UI
    ├── terrain/                # Terrain rendering options
    ├── shaders/                # Shader options
    └── ui/                     # Options screens and UI components
```

### 2. Component Responsibilities

#### 2.1 Core Renderer (`net.minecraft.client.renderer`)

**Responsibilities**:
- Fundamental rendering infrastructure (unchanged)
- Frame orchestration and render pass management
- Resource management (textures, shaders, buffers)
- Fallback rendering when advanced features are disabled

**Key Classes**:
- `LevelRenderer` - World rendering orchestration
- `GameRenderer` - Frame rendering and post-processing
- `RenderType` - Render state management
- `MultiBufferSource` - Vertex buffer batching

**Modification Strategy**:
- Add integration points for advanced features
- Preserve vanilla code paths as fallbacks
- Use feature flags to switch between vanilla and advanced rendering

#### 2.2 Advanced Terrain (`net.minecraft.client.renderer.advanced.terrain`)

**Responsibilities**:
- Optimized chunk mesh building (multi-threaded)
- Compact vertex formats for reduced memory bandwidth
- Advanced culling (frustum, occlusion)
- Direct OpenGL command buffer management
- Render graph for efficient pass scheduling

**Key Components**:
- **Chunk Renderer** - Replaces vanilla terrain rendering in `LevelRenderer`
- **Mesh Builder** - Multi-threaded chunk meshing pipeline
- **Vertex Formats** - Compact vertex data structures
- **GL Abstractions** - Direct GL command buffers
- **Culling System** - Frustum and occlusion culling

**Migration from Sodium**:
```
Original (Sodium)                           → New (Integrated)
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
net.caffeinemc.mods.sodium.client.render    → terrain.render
net.caffeinemc.mods.sodium.client.gl        → terrain.gl
net.caffeinemc.mods.sodium.api.vertex       → terrain.api.vertex
```

**Integration Points**:
1. `LevelRenderer.addMainPass()` - Replace terrain rendering
2. `GameRenderer.render()` - Setup/teardown hooks
3. `BlockRenderDispatcher` - Custom block model rendering

#### 2.3 Advanced Shaders (`net.minecraft.client.renderer.advanced.shaders`)

**Responsibilities**:
- Shader pack loading (OptiFine/Iris format)
- Multi-pass rendering pipeline
- Shadow map rendering
- Deferred rendering and post-processing
- Shader uniform management
- Framebuffer management

**Key Components**:
- **Shader Pack Loader** - Parse and load shader packs from `shaderpacks/`
- **Pipeline Manager** - Multi-pass rendering orchestration
- **Uniform System** - Expose game state to shaders
- **Framebuffer Manager** - Render target creation and management
- **Program Manager** - Shader compilation and lifecycle

**Migration from Iris**:
```
Original (Iris)                             → New (Integrated)
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
net.irisshaders.iris.shaderpack             → shaders.pack
net.irisshaders.iris.pipeline               → shaders.pipeline
net.irisshaders.iris.uniforms               → shaders.uniforms
net.irisshaders.iris.targets                → shaders.framebuffers
```

**Integration Points**:
1. `LevelRenderer.addMainPass()` - Inject shader passes
2. `GameRenderer` - Replace shader program loading
3. Advanced terrain renderer - Wrap with shader pipeline

#### 2.4 Advanced Options (`net.minecraft.client.renderer.advanced.options`)

**Responsibilities**:
- Unified configuration for all advanced features
- Options UI integrated with vanilla settings
- Config file migration from Sodium/Iris
- Persistence through `options.txt`

**Key Components**:
- **Option Definitions** - `OptionInstance<?>` for all advanced settings
- **UI Screens** - Advanced video settings screens
- **Config Migration** - Read old Sodium/Iris configs
- **Persistence** - Save/load through `Options`

**Migration**:
```
Original                    → New
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
sodium-options.json         → options.txt (advancedRenderingOptions)
iris.properties             → options.txt (shaderOptions)
Sodium options UI           → Advanced Video Settings
Iris shader selection UI    → Shader Packs screen
```

---

## Component Interactions

### 3.1 Initialization Sequence

```
Game Startup
    │
    ├─→ MinecraftBootstrap.initialize()
    │       │
    │       └─→ Register advanced rendering subsystems
    │
    ├─→ Minecraft.run()
    │       │
    │       ├─→ Options.load()
    │       │       │
    │       │       ├─→ Load advanced rendering options
    │       │       └─→ Migrate old Sodium/Iris configs if needed
    │       │
    │       └─→ Initialize rendering
    │               │
    │               ├─→ GameRenderer initialization
    │               │       │
    │               │       └─→ Initialize shader system if enabled
    │               │
    │               └─→ LevelRenderer initialization
    │                       │
    │                       ├─→ Initialize advanced terrain renderer if enabled
    │                       └─→ Connect terrain renderer to shader pipeline
    │
    └─→ Main game loop
            │
            └─→ Render frames using integrated systems
```

### 3.2 Render Loop Integration

```
Frame Rendering (GameRenderer.render())
    │
    ├─→ Setup
    │   ├─→ Shader pipeline: beginFrame() [if enabled]
    │   └─→ Terrain renderer: prepare() [if enabled]
    │
    ├─→ Shadow Pass [if shader pipeline active]
    │   ├─→ Shader pipeline: beginShadowPass()
    │   ├─→ Render shadow casters
    │   │   └─→ Advanced terrain renderer: renderShadows()
    │   └─→ Shader pipeline: endShadowPass()
    │
    ├─→ Main Scene (LevelRenderer.addMainPass())
    │   │
    │   ├─→ Shader pipeline: beginGBufferPass() [if enabled]
    │   │
    │   ├─→ Terrain Rendering
    │   │   │
    │   │   ├─→ If advanced terrain enabled:
    │   │   │   └─→ Advanced terrain renderer: render()
    │   │   │
    │   │   └─→ Else (fallback):
    │   │       └─→ Vanilla chunk rendering
    │   │
    │   ├─→ Entity Rendering
    │   ├─→ Particle Rendering
    │   │
    │   └─→ Shader pipeline: endGBufferPass() [if enabled]
    │
    ├─→ Post-Processing [if shader pipeline active]
    │   ├─→ Shader pipeline: runCompositePasses()
    │   └─→ Shader pipeline: runFinalPass()
    │
    └─→ Teardown
        ├─→ Shader pipeline: endFrame() [if enabled]
        └─→ Terrain renderer: cleanup() [if enabled]
```

### 3.3 Compatibility Matrix

The architecture supports four operational modes:

| Terrain Optimization | Shader Pipeline | Description |
|---------------------|-----------------|-------------|
| ✗ Disabled | ✗ Disabled | Pure vanilla rendering |
| ✓ Enabled  | ✗ Disabled | Sodium-style optimization without shaders |
| ✗ Disabled | ✓ Enabled  | Shaders with vanilla terrain rendering |
| ✓ Enabled  | ✓ Enabled  | Full advanced rendering (Sodium + Iris) |

**Implementation**:
```java
// In LevelRenderer.addMainPass()
public void renderTerrain(Camera camera, Frustum frustum) {
    // Check if shader pipeline is active
    boolean shadersActive = AdvancedShaderPipeline.isActive();
    
    // Check if advanced terrain is enabled
    boolean advancedTerrainEnabled = AdvancedRenderingConfig.isTerrainOptimizationEnabled();
    
    if (shadersActive) {
        AdvancedShaderPipeline.beginGBufferPass();
    }
    
    if (advancedTerrainEnabled) {
        // Optimized terrain rendering
        this.advancedTerrainRenderer.render(camera, frustum);
    } else {
        // Vanilla fallback
        this.renderChunksVanilla(camera, frustum);
    }
    
    if (shadersActive) {
        AdvancedShaderPipeline.endGBufferPass();
    }
}
```

---

## Interface Contracts

### 4.1 Core Renderer ↔ Advanced Terrain

**Interface**: `AdvancedTerrainRenderer`

```java
/**
 * Interface for advanced terrain rendering systems.
 * Implementations provide optimized chunk rendering as an alternative to vanilla.
 */
public interface AdvancedTerrainRenderer {
    /**
     * Initialize the terrain renderer.
     * Called once during LevelRenderer initialization.
     */
    void initialize(LevelRenderer levelRenderer);
    
    /**
     * Render visible chunks for the current frame.
     * 
     * @param camera The camera position and orientation
     * @param frustum The view frustum for culling
     */
    void render(Camera camera, Frustum frustum);
    
    /**
     * Render chunks for shadow passes.
     * Used when shader pipeline is active.
     * 
     * @param camera Shadow camera position
     * @param frustum Shadow frustum
     */
    void renderShadows(Camera camera, Frustum frustum);
    
    /**
     * Mark a chunk as dirty and needing rebuild.
     * 
     * @param sectionPos The chunk section position
     */
    void setDirty(SectionPos sectionPos);
    
    /**
     * Clean up resources when renderer is destroyed.
     */
    void cleanup();
}
```

**Contract**:
- Must render terrain identically to vanilla (visual parity)
- Must respect render distance and fog settings
- Must respond to chunk updates within 1 frame
- Must handle shader pipeline integration if active

### 4.2 Core Renderer ↔ Shader Pipeline

**Interface**: `AdvancedShaderPipeline`

```java
/**
 * Interface for shader pipeline systems.
 * Implementations manage shader pack loading and multi-pass rendering.
 */
public interface AdvancedShaderPipeline {
    /**
     * Initialize the shader pipeline.
     * Called once during GameRenderer initialization.
     */
    void initialize(GameRenderer gameRenderer);
    
    /**
     * Load a shader pack from the specified path.
     * 
     * @param packPath Path to shader pack directory or zip
     * @return true if loading succeeded
     */
    boolean loadShaderPack(Path packPath);
    
    /**
     * Check if the pipeline is currently active.
     * 
     * @return true if shaders are active
     */
    boolean isActive();
    
    /**
     * Begin a new frame.
     * Called at the start of frame rendering.
     */
    void beginFrame(PoseStack poseStack, Matrix4f projection);
    
    /**
     * Begin the shadow rendering pass.
     */
    void beginShadowPass();
    
    /**
     * End the shadow rendering pass.
     */
    void endShadowPass();
    
    /**
     * Begin the GBuffer (main scene) pass.
     */
    void beginGBufferPass();
    
    /**
     * End the GBuffer pass.
     */
    void endGBufferPass();
    
    /**
     * Run composite post-processing passes.
     */
    void runCompositePasses();
    
    /**
     * Run the final output pass to screen.
     */
    void runFinalPass();
    
    /**
     * End the current frame.
     */
    void endFrame();
    
    /**
     * Clean up resources.
     */
    void cleanup();
}
```

**Contract**:
- Must preserve rendering output when disabled (vanilla behavior)
- Must handle resource pack changes and reloading
- Must expose standard uniforms (time, camera, etc.)
- Must integrate with terrain renderer without conflicts

### 4.3 Advanced Terrain ↔ Shader Pipeline

**Integration**: Direct calls, no mixin indirection

```java
// In AdvancedTerrainRenderer implementation
public void render(Camera camera, Frustum frustum) {
    // Check if shaders are active and prepare uniforms
    if (AdvancedShaderPipeline.isActive()) {
        AdvancedShaderPipeline.setupTerrainUniforms(camera);
        AdvancedShaderPipeline.bindTerrainTargets();
    }
    
    // Render chunks
    this.renderChunks(camera, frustum);
    
    // Cleanup after rendering
    if (AdvancedShaderPipeline.isActive()) {
        AdvancedShaderPipeline.unbindTerrainTargets();
    }
}
```

**Contract**:
- Terrain renderer must check shader state before rendering
- Shader pipeline must provide terrain-specific uniforms
- Both must coordinate on framebuffer bindings
- Must handle transitions between enabled/disabled states

---

## Fallback Mechanisms

### 5.1 Feature Flag System

**Configuration Class**:
```java
public class AdvancedRenderingConfig {
    /**
     * Enable/disable advanced terrain optimization.
     * When false, falls back to vanilla chunk rendering.
     */
    public static boolean isTerrainOptimizationEnabled() {
        return Minecraft.getInstance().options.advancedTerrainOptimization;
    }
    
    /**
     * Enable/disable shader pipeline.
     * When false, uses vanilla rendering pipeline.
     */
    public static boolean isShaderPipelineEnabled() {
        return Minecraft.getInstance().options.shaderPipelineEnabled 
            && AdvancedShaderPipeline.getInstance().isActive();
    }
}
```

### 5.2 Graceful Degradation Strategy

**Principle**: Each advanced feature must have a fallback path to vanilla rendering.

**Terrain Optimization Fallback**:
```java
// In LevelRenderer.addMainPass()
if (AdvancedRenderingConfig.isTerrainOptimizationEnabled() && advancedTerrainRenderer != null) {
    try {
        advancedTerrainRenderer.render(camera, frustum);
    } catch (Exception e) {
        LOGGER.error("Advanced terrain renderer failed, falling back to vanilla", e);
        AdvancedRenderingConfig.disableTerrainOptimization();
        renderChunksVanilla(camera, frustum);
    }
} else {
    renderChunksVanilla(camera, frustum);
}
```

**Shader Pipeline Fallback**:
```java
// In GameRenderer.render()
if (AdvancedRenderingConfig.isShaderPipelineEnabled() && shaderPipeline != null) {
    try {
        shaderPipeline.beginFrame(poseStack, projection);
        renderLevel(poseStack, partialTick);
        shaderPipeline.endFrame();
    } catch (Exception e) {
        LOGGER.error("Shader pipeline failed, falling back to vanilla", e);
        AdvancedRenderingConfig.disableShaderPipeline();
        renderLevelVanilla(poseStack, partialTick);
    }
} else {
    renderLevelVanilla(poseStack, partialTick);
}
```

### 5.3 Runtime Switching

Users can toggle advanced features without restarting:

1. **Change option in settings**
2. **Trigger resource reload** (F3+T equivalent)
3. **Re-initialize renderer** with new configuration
4. **Resume rendering** with updated path

**Implementation**:
```java
// In AdvancedVideoOptionsScreen
private void onTerrainOptimizationToggle(boolean enabled) {
    this.minecraft.options.advancedTerrainOptimization = enabled;
    this.minecraft.options.save();
    
    // Schedule re-initialization on next frame
    this.minecraft.execute(() -> {
        this.minecraft.levelRenderer.reinitializeTerrainRenderer();
    });
}
```

---

## Migration Path

### 6.1 From Sodium

**Current State**: Separate mod JAR loaded via Fabric Loader

**Target State**: Native terrain optimization in `net.minecraft.client.renderer.advanced.terrain`

**Migration Steps**:

1. **API Migration**: Move public APIs to new package
   - `net.caffeinemc.mods.sodium.api.vertex` → `terrain.api.vertex`
   - Preserve API compatibility, mark old packages as `@Deprecated`

2. **Implementation Migration**: Move core implementation
   - `net.caffeinemc.mods.sodium.client.render` → `terrain.render`
   - `net.caffeinemc.mods.sodium.client.gl` → `terrain.gl`
   - Update all import statements

3. **Mixin Inlining**: Replace runtime mixins with direct integration
   - `LevelRendererMixin` → Modify `LevelRenderer.java` directly
   - `GameRendererMixin` → Modify `GameRenderer.java` directly
   - Add feature flags to toggle between vanilla and advanced paths

4. **Options Migration**: Consolidate configuration
   - `sodium-options.json` → `options.txt` (advancedRenderingOptions)
   - Migrate UI to Advanced Video Settings

5. **Build System**: Merge into main source set
   - Remove `sourceSets.sodium`
   - Remove `sodiumJar` task
   - Simplify dependencies

**Validation**: Visual parity tests, performance benchmarks

### 6.2 From Iris

**Current State**: Separate mod JAR loaded via Fabric Loader

**Target State**: Native shader support in `net.minecraft.client.renderer.advanced.shaders`

**Migration Steps**:

1. **API Migration**: Move public APIs to new package
   - `net.irisshaders.iris.api` → `shaders.api`
   - Preserve compatibility with deprecation warnings

2. **Implementation Migration**: Move core implementation
   - `net.irisshaders.iris.shaderpack` → `shaders.pack`
   - `net.irisshaders.iris.pipeline` → `shaders.pipeline`
   - `net.irisshaders.iris.uniforms` → `shaders.uniforms`

3. **Mixin Inlining**: Replace Iris mixins
   - Renderer mixins → Modify `GameRenderer`, `LevelRenderer`
   - Sodium compatibility mixins → Direct integration (no mixins needed)

4. **Options Migration**: Consolidate configuration
   - `iris.properties` → `options.txt` (shaderOptions)
   - Integrate shader selection UI

5. **Build System**: Merge into main source set
   - Remove `sourceSets.iris`
   - Remove `irisJar` task

**Validation**: Shader pack compatibility tests, visual regression tests

### 6.3 From Fabric Loader

**Current State**: Knot launcher with custom class loading

**Target State**: Standard Java main class with integrated initialization

**Migration Steps**:

1. **Eliminate Mod Discovery**: Remove JAR scanning
2. **Simplify Class Loading**: Use standard class loader
3. **Inline Event System**: Replace Fabric events with direct calls
4. **Update Entry Point**: Change from `KnotClient` to `Main`
5. **Simplify Launch**: Remove Fabric-specific JVM arguments

**Validation**: Successful game launch, all features functional

---

## Component Lifecycle

### 7.1 Terrain Renderer Lifecycle

```
Initialization
    │
    ├─→ Create worker thread pool for chunk building
    ├─→ Initialize GL state and vertex formats
    ├─→ Allocate vertex buffers
    └─→ Register with LevelRenderer
    
Runtime
    │
    ├─→ Receive chunk dirty notifications
    ├─→ Queue chunk rebuilds
    ├─→ Build chunk meshes on worker threads
    ├─→ Upload meshes to GPU
    └─→ Render visible chunks
    
Shutdown
    │
    ├─→ Cancel pending chunk builds
    ├─→ Shutdown worker thread pool
    ├─→ Free vertex buffers
    └─→ Cleanup GL resources
```

### 7.2 Shader Pipeline Lifecycle

```
Initialization
    │
    ├─→ Scan shaderpacks directory
    ├─→ Load configured shader pack
    ├─→ Parse shader programs
    ├─→ Compile shaders
    ├─→ Create framebuffers and render targets
    ├─→ Initialize uniform system
    └─→ Activate pipeline
    
Runtime
    │
    ├─→ Update uniforms per frame
    ├─→ Execute shadow pass
    ├─→ Execute GBuffer pass
    ├─→ Execute composite passes
    └─→ Execute final pass
    
Resource Reload (F3+T)
    │
    ├─→ Preserve pipeline state
    ├─→ Reload shader pack
    ├─→ Recompile shaders
    ├─→ Recreate framebuffers if needed
    └─→ Resume rendering
    
Shutdown
    │
    ├─→ Deactivate pipeline
    ├─→ Delete shader programs
    ├─→ Free framebuffers
    └─→ Cleanup GL resources
```

---

## Performance Considerations

### 8.1 Expected Performance Characteristics

**Terrain Optimization**:
- **FPS Improvement**: 2-3x in typical scenarios
- **Memory Bandwidth**: 40-60% reduction (compact vertex formats)
- **Chunk Build Time**: 30-50% faster (multi-threading)
- **Culling Efficiency**: 20-40% fewer chunks rendered

**Shader Pipeline**:
- **FPS Impact**: 10-60% depending on shader complexity
- **Memory Usage**: +100-500MB for framebuffers
- **Shader Compilation**: 2-10 seconds on first load
- **Uniform Updates**: <0.1ms per frame

**Integration Overhead**:
- **vs. Mod-based**: <1% difference (within noise)
- **Class Loading**: Faster (no Knot overhead)
- **Initialization**: Slightly faster (no mod discovery)

### 8.2 Performance Validation

**Benchmarking Strategy**:
1. Measure baseline (vanilla)
2. Enable terrain optimization only
3. Enable shader pipeline only
4. Enable both together
5. Compare against mod-based implementation

**Metrics to Track**:
- Average FPS
- 1% low FPS (worst frame times)
- Frame time variance
- Memory usage (heap + native)
- Chunk build queue size
- Shader compilation time

**Acceptance Criteria**:
- FPS within 2% of mod-based implementation
- Memory usage equal or lower
- No increase in frame time variance
- Shader pack loading within 5% of mod-based

---

## Testing Strategy

### 9.1 Unit Tests

**Terrain Renderer**:
- Vertex format encoding/decoding
- Chunk mesh building logic
- Culling algorithm correctness
- GL command buffer generation

**Shader Pipeline**:
- Shader pack parsing
- Uniform value computation
- Framebuffer management
- Pipeline state transitions

### 9.2 Integration Tests

**Terrain + Vanilla**:
- Terrain optimization with vanilla rendering
- Visual parity with vanilla output
- Performance improvement verification

**Shaders + Vanilla**:
- Shader pipeline with vanilla terrain
- Shader pack loading and activation
- Visual correctness

**Terrain + Shaders**:
- Full advanced rendering stack
- Sodium-Iris compatibility
- Visual and performance parity with mod-based

### 9.3 Visual Regression Tests

**Approach**: Capture screenshots and compare against known-good baselines

**Test Scenarios**:
- Various biomes and terrain features
- Different times of day and weather
- Underwater rendering
- Entity rendering with shaders
- Particle effects
- Multiple shader packs (Complementary, BSL, SEUS)

**Tools**: Automated screenshot capture, image diff comparison

### 9.4 Performance Tests

**Scenarios**:
- Low-end hardware (Intel integrated)
- Mid-range hardware (GTX 1060)
- High-end hardware (RTX 3080)
- Various render distances (8, 16, 32 chunks)
- Different shader packs

**Metrics**:
- FPS (average, 1% low)
- Frame times
- Memory usage
- GPU utilization

---

## Maintenance and Evolution

### 10.1 Update Strategy

**Upstream Changes**:
- Subscribe to Sodium and Iris releases
- Review changelogs for critical fixes
- Periodically merge important changes
- Maintain changelog of divergence

**Version Tracking**:
```
Current integration based on:
- Sodium 0.7.2 for MC 1.21.10
- Iris 1.9.6 for MC 1.21.10
- Fabric Loader 0.18.2
```

### 10.2 Extension Points

**For Future Modifications**:
1. Keep interface contracts stable
2. Use dependency injection where appropriate
3. Maintain clear separation of concerns
4. Document extension APIs

**Example Extension APIs**:
```java
/**
 * Registry for custom terrain rendering extensions.
 */
public interface TerrainRendererExtension {
    void onChunkBuild(ChunkBuildContext context);
    void onChunkRender(ChunkRenderContext context);
}

/**
 * Registry for custom shader uniforms.
 */
public interface CustomUniformProvider {
    void registerUniforms(UniformRegistry registry);
    void updateUniforms(UniformUpdateContext context);
}
```

### 10.3 Documentation Requirements

**For All Components**:
- JavaDoc on all public APIs
- Architecture decision records (ADRs) for major choices
- Integration guides for future modifications
- Performance characteristics documentation

**For Each Migration Step**:
- Document what changed
- Document why it changed
- Document validation performed
- Document any divergence from upstream

---

## Risk Mitigation

### 11.1 Identified Risks

1. **Performance Regression**
   - Mitigation: Extensive benchmarking, preserve fallback paths
   - Rollback: Feature flags allow disabling problematic features

2. **Breaking Functionality**
   - Mitigation: Comprehensive testing, gradual migration
   - Rollback: Keep vanilla paths functional at all times

3. **Difficult Upstream Updates**
   - Mitigation: Clear documentation, maintain clean boundaries
   - Acceptance: Trade-off for tighter integration

4. **Increased Complexity**
   - Mitigation: Clear architecture, well-documented interfaces
   - Monitoring: Regular code review and refactoring

### 11.2 Contingency Plans

**If Performance Degrades**:
1. Profile to identify bottleneck
2. Compare with mod-based implementation
3. Optimize or revert specific change
4. Document limitation if unavoidable

**If Functionality Breaks**:
1. Identify affected code path
2. Check if feature flag can disable
3. Fix bug or fall back to vanilla
4. Add regression test

**If Upstream Diverges Significantly**:
1. Evaluate importance of change
2. Decide: merge, adapt, or skip
3. Document decision and rationale
4. Consider periodic full re-sync

---

## Success Criteria

Integration is complete and successful when:

1. ✅ **No Separate JARs** - All functionality in main MattMC JAR
2. ✅ **No Mod Loading** - No runtime mod discovery or dynamic loading
3. ✅ **No Fabric Loader** - Standard Java entry point, no Knot
4. ✅ **Performance Parity** - FPS within 2% of mod-based implementation
5. ✅ **Feature Parity** - All Sodium and Iris features work identically
6. ✅ **Native UI** - Options integrated seamlessly with Minecraft UI
7. ✅ **Single Entry Point** - Standard `Main` class, no custom launchers
8. ✅ **Simplified Build** - Single source set, single JAR output
9. ✅ **Maintainable Code** - Clear architecture, documented APIs
10. ✅ **User Transparency** - Users experience no difference (except simpler setup)

---

## Conclusion

This architecture provides a clear roadmap for integrating Sodium and Iris as first-class components of MattMC. The design prioritizes:

- **Clarity** - Well-defined boundaries and responsibilities
- **Maintainability** - Documented interfaces and clean code
- **Performance** - Zero regression from mod-based approach
- **Reliability** - Fallback mechanisms and graceful degradation
- **Extensibility** - Clear extension points for future enhancements

By following this architecture through the 20-step integration plan outlined in `INTEGRATION.md`, MattMC will achieve a unified rendering engine that delivers advanced features natively, without the complexity and overhead of a mod loading system.
