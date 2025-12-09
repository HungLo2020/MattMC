# Iris Integration Guide for MattMC
## Complete Implementation Roadmap for 1:1 Shader Pack Compatibility

---

## Executive Summary

This document provides a comprehensive guide for integrating Iris shader mod capabilities into MattMC to achieve **1:1 compatibility with OptiFine/Iris shader packs** (including Complimentary Reimagined). It details exactly WHERE and HOW Iris hooks into vanilla Minecraft's rendering, resource loading, and GUI systems, with specific implementation instructions adapted for MattMC's direct code access approach.

**Key Difference**: Iris uses Mixin to inject code at runtime. MattMC has direct access to source code, so we can make direct modifications at the same logical points Iris targets.

---

## Table of Contents

1. [Initialization System](#1-initialization-system)
2. [Rendering Pipeline Hooks](#2-rendering-pipeline-hooks)
3. [Shader Program Interception](#3-shader-program-interception)
4. [Resource Loading Integration](#4-resource-loading-integration)
5. [GUI Integration](#5-gui-integration)
6. [Pipeline Management](#6-pipeline-management)
7. [Framebuffer Management](#7-framebuffer-management)
8. [Implementation Phases](#8-implementation-phases)
9. [Critical Integration Points Summary](#9-critical-integration-points-summary)
10. [Testing Strategy](#10-testing-strategy)

---

## 1. Initialization System

### 1.1 Early Initialization (Before OpenGL Available)

**Iris Hook**: `MixinOptions_Entrypoint` @ `Minecraft.<init>()` before Options creation

**Location in Vanilla**:
```java
// net/minecraft/client/Minecraft.java
public Minecraft(...) {
    // ... early initialization ...
    this.options = new Options(this, gameDirectory); // <- INJECT HERE
    // ...
}
```

**What Iris Does**:
```java
// Iris.onEarlyInitialize()
- Register keybindings (R=reload, K=toggle, O=shader selection)
- Create shaderpacks directory if not exists
- Load iris.properties configuration
- Initialize UpdateChecker
- Set initialization flag
```

**MattMC Implementation**:
```java
// In Minecraft.java constructor, BEFORE Options creation:
public Minecraft(...) {
    // ... early initialization ...
    
    // Initialize shader system early
    ShaderSystem.getInstance().earlyInitialize(gameDirectory);
    
    this.options = new Options(this, gameDirectory);
    // ...
}
```

**ShaderSystem.earlyInitialize() should**:
1. Register keybindings for shader control
2. Ensure shader resources directory exists (already in JAR for MattMC)
3. Load shader configuration
4. Initialize shader pack repository (scan resources)
5. Set up logging

### 1.2 RenderSystem Initialization (OpenGL Available)

**Iris Hook**: Called after `RenderSystem.initRenderer()` completes

**Location in Vanilla**:
```java
// net/minecraft/client/main/Main.java or Minecraft initialization
// After RenderSystem.initRenderer() is called
```

**What Iris Does**:
```java
// Iris.onRenderSystemInit()
- Enable parallel shader compilation (if supported)
- Initialize PBR texture manager
- Register vertex serializers for Sodium compatibility
- Load initial shader pack
```

**MattMC Implementation**:
```java
// After RenderSystem.initRenderer() in initialization flow:
ShaderSystem.getInstance().onRenderSystemInit();

// In ShaderSystem:
public void onRenderSystemInit() {
    // Enable parallel compilation
    if (GL.getCapabilities().GL_KHR_parallel_shader_compile) {
        KHRParallelShaderCompile.glMaxShaderCompilerThreadsKHR(10);
    }
    
    // Initialize texture management
    ShaderTextureManager.getInstance().initialize();
    
    // Register extended vertex formats
    registerVertexFormats();
    
    // Load configured shader pack
    loadShaderPack();
}
```

### 1.3 Loading Complete (Title Screen Ready)

**Iris Hook**: `MixinTitleScreen.init()` @ `TitleScreen.init()` RETURN

**Location in Vanilla**:
```java
// net/minecraft/client/gui/screens/TitleScreen.java
protected void init() {
    // ... button creation ...
    // <- INJECT AT RETURN
}
```

**What Iris Does**:
```java
// Iris.onLoadingComplete()
- Set lastDimension to OVERWORLD (default assumption)
- Pre-prepare pipeline for OVERWORLD to reduce first-load time
```

**MattMC Implementation**:
```java
// In TitleScreen.init(), at the end:
protected void init() {
    // ... existing button setup ...
    
    // Prepare shader pipeline
    if (!shaderSystemLoadingCompleted) {
        ShaderSystem.getInstance().onLoadingComplete();
        shaderSystemLoadingCompleted = true;
    }
}
```

---

## 2. Rendering Pipeline Hooks

### 2.1 Level Rendering Start

**Iris Hook**: `MixinLevelRenderer.renderLevel()` @ HEAD and after clear

**Location in Vanilla**:
```java
// net/minecraft/client/renderer/LevelRenderer.java
public void renderLevel(
    GraphicsResourceAllocator allocator,
    DeltaTracker deltaTracker,
    boolean renderBlockOutline,
    Camera camera,
    Matrix4f modelView,
    Matrix4f projection,
    Matrix4f projectionMatrix,
    GpuBufferSlice fogParameters,
    Vector4f fogColor,
    boolean renderDistance
) {
    // <- INJECT HEAD: Setup pipeline
    
    // ... clear operations ...
    // <- INJECT AFTER CLEAR: Begin rendering
    
    // ... terrain rendering ...
    // ... entity rendering ...
    // ... translucent rendering ...
    
    // <- INJECT BEFORE RETURN: Finalize rendering
}
```

**What Iris Does**:
```java
// AT HEAD:
1. Update time uniforms
2. Capture model-view and projection matrices
3. Get or create pipeline for current dimension
4. Call pipeline.beginLevelRendering()
5. Set phase to NONE
6. Disable culling if shader requires it

// AFTER CLEAR:
1. Call pipeline.onBeginClear()

// BEFORE RETURN:
1. Render translucent hand
2. Call pipeline.finalizeLevelRendering()
3. Restore GL state
```

**MattMC Implementation**:
```java
public void renderLevel(...) {
    // === SETUP (at HEAD) ===
    ShaderPipeline pipeline = ShaderSystem.getInstance()
        .getPipelineManager()
        .preparePipeline(getCurrentDimension());
    
    // Update shader uniforms
    ShaderUniforms.updateTimeUniforms();
    ShaderUniforms.captureMatrices(modelView, projection);
    ShaderUniforms.setTickDelta(deltaTracker.getGameTimeDeltaPartialTick(false));
    
    // Begin shader rendering
    pipeline.beginLevelRendering();
    pipeline.setPhase(WorldRenderingPhase.NONE);
    
    // Handle culling
    if (pipeline.shouldDisableFrustumCulling()) {
        // Use non-culling frustum
    }
    
    // === CLEAR OPERATIONS ===
    // ... existing clear code ...
    
    // === AFTER CLEAR ===
    pipeline.onBeginClear();
    
    // === TERRAIN RENDERING ===
    // ... existing terrain rendering ...
    // (with shader phase updates - see below)
    
    // === FINALIZATION ===
    pipeline.finalizeLevelRendering();
    
    // Cleanup
    ShaderSystem.getInstance().getPipelineManager().clearCurrentPipeline();
}
```

### 2.2 Shadow Pass

**Iris Hook**: `MixinLevelRenderer.renderLevel()` after frustum preparation

**Location**: After `prepareCullFrustum()` call

**What Iris Does**:
```java
pipeline.renderShadows(levelRenderer, camera, cameraRenderState);
```

**MattMC Implementation**:
```java
// After frustum setup, before sky rendering:
Frustum frustum = this.prepareCullFrustum(modelView, projection, camera.getPosition());

// Render shadow map
if (pipeline.hasShadowPass()) {
    pipeline.renderShadows(this, camera, this.cameraRenderState);
}

// Continue with normal rendering...
```

### 2.3 Phase Transitions

**Iris Hooks**: Throughout `LevelRenderer.renderLevel()` at specific render stages

**Phases and Injection Points**:

```java
// 1. SKY PHASE
// Before renderSky() call:
pipeline.setPhase(WorldRenderingPhase.SKY);
this.renderSky(...);

// 2. TERRAIN_SOLID PHASE  
// Before solid terrain rendering:
pipeline.setPhase(WorldRenderingPhase.TERRAIN_SOLID);
// Render solid terrain chunks

// 3. TERRAIN_CUTOUT PHASE
// Before cutout terrain:
pipeline.setPhase(WorldRenderingPhase.TERRAIN_CUTOUT);
// Render cutout terrain

// 4. TERRAIN_CUTOUT_MIPPED
// Before mipped cutout:
pipeline.setPhase(WorldRenderingPhase.TERRAIN_CUTOUT_MIPPED);
// Render mipped cutout

// 5. ENTITIES PHASE
// Before entity rendering:
pipeline.setPhase(WorldRenderingPhase.ENTITIES);
// Render entities

// 6. BLOCK_ENTITIES PHASE
// Before block entity rendering:
pipeline.setPhase(WorldRenderingPhase.BLOCK_ENTITIES);
// Render block entities

// 7. TRANSLUCENT_TERRAIN PHASE
// Before translucent rendering:
pipeline.setPhase(WorldRenderingPhase.TRANSLUCENT_TERRAIN);
// Render translucent blocks/water

// 8. PARTICLES PHASE
// Before particle rendering:
pipeline.setPhase(WorldRenderingPhase.PARTICLES);
// Render particles

// 9. HAND PHASE
// When rendering first-person hand:
pipeline.setPhase(WorldRenderingPhase.HAND);
// Render hand/held items

// 10. COMPOSITE PHASE
// In finalizeLevelRendering():
pipeline.setPhase(WorldRenderingPhase.COMPOSITE);
// Run composite passes
```

**Implementation Pattern**:
```java
// Before each rendering operation in LevelRenderer.renderLevel():
pipeline.setPhase(WorldRenderingPhase.APPROPRIATE_PHASE);

// This allows shader programs to know what they're rendering
// and use the correct gbuffers_* program variant
```

---

## 3. Shader Program Interception

### 3.1 Shader Program Override System

**Iris Hook**: `MixinShaderManager_Overrides.getOrCompilePipeline()` @ HEAD

**Location in Vanilla**:
```java
// com/mojang/blaze3d/opengl/GlDevice.java (or equivalent)
public GlRenderPipeline getOrCompilePipeline(RenderPipeline renderPipeline) {
    // <- INJECT HEAD: Check for shader override
    
    // ... normal compilation ...
}
```

**What Iris Does**:
```java
// Check if we have a shader pack loaded
if (pipeline instanceof IrisRenderingPipeline && shouldOverrideShaders) {
    // Get shader key for this render pipeline
    ShaderKey key = IrisPipelines.getPipeline(pipeline, renderPipeline);
    
    // Get our custom shader program
    GlProgram program = pipeline.getShaderMap().getShader(key);
    
    if (program != null) {
        // Return our shader instead of vanilla
        return new GlRenderPipeline(renderPipeline, program);
    }
}

// Fall through to vanilla
```

**MattMC Implementation**:

Create `ShaderProgramInterceptor` class:
```java
public class ShaderProgramInterceptor {
    public static GlRenderPipeline getOrCompilePipeline(
        RenderPipeline renderPipeline,
        OriginalCompiler compiler
    ) {
        ShaderPipeline pipeline = ShaderSystem.getInstance()
            .getPipelineManager()
            .getCurrentPipeline();
        
        if (pipeline instanceof ShaderPackPipeline shaderPipeline) {
            if (shaderPipeline.shouldOverrideShaders()) {
                // Map vanilla program to shader pack program
                ShaderProgram overrideProgram = 
                    shaderPipeline.getOverrideProgram(renderPipeline);
                
                if (overrideProgram != null) {
                    return new GlRenderPipeline(
                        renderPipeline, 
                        overrideProgram.getGlProgram()
                    );
                }
            }
        }
        
        // No override, use vanilla
        return compiler.compile(renderPipeline);
    }
}
```

**Integration in GlDevice**:
```java
// Modify GlDevice.getOrCompilePipeline():
public GlRenderPipeline getOrCompilePipeline(RenderPipeline renderPipeline) {
    // Check for shader override
    return ShaderProgramInterceptor.getOrCompilePipeline(
        renderPipeline,
        rp -> {
            // Original compilation logic
            // ... existing code ...
        }
    );
}
```

### 3.2 Program Mapping

**Key Insight**: Iris maps vanilla `RenderPipeline` names to shader pack programs:

```java
// Vanilla -> Shader Pack Mapping Examples:
"minecraft:rendertype_solid"           -> gbuffers_terrain
"minecraft:rendertype_cutout"          -> gbuffers_terrain
"minecraft:rendertype_cutout_mipped"   -> gbuffers_terrain
"minecraft:rendertype_translucent"     -> gbuffers_water
"minecraft:rendertype_entity_solid"    -> gbuffers_entities
"minecraft:rendertype_text"            -> gbuffers_textured_lit
// etc...
```

**Implementation**:
```java
public class ProgramMapper {
    private static final Map<String, String> VANILLA_TO_SHADER = Map.ofEntries(
        entry("minecraft:rendertype_solid", "gbuffers_terrain"),
        entry("minecraft:rendertype_cutout", "gbuffers_terrain"),
        entry("minecraft:rendertype_cutout_mipped", "gbuffers_terrain"),
        entry("minecraft:rendertype_translucent", "gbuffers_water"),
        entry("minecraft:rendertype_entity_solid", "gbuffers_entities"),
        entry("minecraft:rendertype_entity_cutout", "gbuffers_entities"),
        entry("minecraft:rendertype_text", "gbuffers_textured_lit"),
        entry("minecraft:rendertype_text_background", "gbuffers_textured"),
        // ... complete mapping from Iris IrisPipelines.java
    );
    
    public static String getShaderProgram(String vanillaProgram, WorldRenderingPhase phase) {
        // Consider current phase for context
        if (phase == WorldRenderingPhase.SHADOW) {
            // Use shadow variants
            return VANILLA_TO_SHADER.getOrDefault(vanillaProgram, "shadow");
        }
        
        return VANILLA_TO_SHADER.getOrDefault(vanillaProgram, "gbuffers_textured");
    }
}
```

---

## 4. Resource Loading Integration

### 4.1 Shader Pack Discovery

**Iris Approach**: Scan filesystem `shaderpacks/` directory for ZIP files

**MattMC Approach**: Scan JAR resources via ResourceManager

**Location**: During early initialization

**Implementation**:
```java
public class ShaderPackRepository {
    private final ResourceManager resourceManager;
    private final List<ShaderPack> availablePacks = new ArrayList<>();
    
    public void scanForPacks() {
        try {
            // Scan assets/minecraft/shaders/ for shader packs
            String shaderPath = "assets/minecraft/shaders/";
            
            // List all subdirectories
            Collection<ResourceLocation> resources = resourceManager.listResources(
                shaderPath,
                path -> true // Accept all
            );
            
            Set<String> packNames = new HashSet<>();
            for (ResourceLocation resource : resources) {
                String path = resource.getPath();
                // Extract pack name from path
                String[] parts = path.split("/");
                if (parts.length > 3) { // assets/minecraft/shaders/PACKNAME/...
                    String packName = parts[3];
                    // Skip vanilla directories
                    if (!packName.equals("core") && 
                        !packName.equals("post") && 
                        !packName.equals("include")) {
                        packNames.add(packName);
                    }
                }
            }
            
            // Load each pack
            for (String packName : packNames) {
                try {
                    ShaderPack pack = loadPack(packName);
                    availablePacks.add(pack);
                    LOGGER.info("Discovered shader pack: {}", packName);
                } catch (Exception e) {
                    LOGGER.error("Failed to load shader pack: {}", packName, e);
                }
            }
            
        } catch (IOException e) {
            LOGGER.error("Failed to scan for shader packs", e);
        }
    }
    
    private ShaderPack loadPack(String packName) throws IOException {
        String basePath = "assets/minecraft/shaders/" + packName + "/";
        
        // Create resource-based shader pack loader
        return new ShaderPack(
            new ResourceShaderPackSource(resourceManager, basePath),
            packName
        );
    }
}
```

### 4.2 Shader File Loading

**Key Difference**: Iris uses `Files.readAllBytes()`, MattMC uses `ResourceManager.getResource()`

**Implementation**:
```java
public class ResourceShaderPackSource implements ShaderPackSource {
    private final ResourceManager resourceManager;
    private final String basePath;
    
    @Override
    public Optional<String> readFile(String relativePath) {
        try {
            ResourceLocation location = ResourceLocation.fromNamespaceAndPath(
                "minecraft",
                basePath + relativePath
            );
            
            Resource resource = resourceManager.getResource(location)
                .orElseThrow();
            
            try (InputStream stream = resource.open()) {
                return Optional.of(new String(
                    stream.readAllBytes(),
                    StandardCharsets.UTF_8
                ));
            }
        } catch (IOException e) {
            return Optional.empty();
        }
    }
    
    @Override
    public boolean fileExists(String relativePath) {
        ResourceLocation location = ResourceLocation.fromNamespaceAndPath(
            "minecraft",
            basePath + relativePath
        );
        return resourceManager.getResource(location).isPresent();
    }
}
```

### 4.3 Properties File Parsing

**Files to Load**:
1. `shaders.properties` - Main shader pack configuration
2. `dimension.properties` - Per-dimension settings  
3. `block.properties` - Block ID mappings
4. `item.properties` - Item ID mappings
5. `entity.properties` - Entity ID mappings

**Implementation**:
```java
public class ShaderProperties {
    public static ShaderProperties load(ShaderPackSource source) {
        Properties props = new Properties();
        
        Optional<String> content = source.readFile("shaders.properties");
        if (content.isPresent()) {
            props.load(new StringReader(content.get()));
        }
        
        return new ShaderProperties(props);
    }
    
    // Parse all properties
    public int getShadowMapResolution() { ... }
    public float getSunPathRotation() { ... }
    public boolean shouldRenderSun() { ... }
    // ... etc
}
```

---

## 5. GUI Integration

### 5.1 Video Settings Button

**Iris Hook**: `MixinVideoSettingsScreen.addOptions()` - adds "Shader Packs..." button

**Location in Vanilla**:
```java
// net/minecraft/client/gui/screens/options/VideoSettingsScreen.java
private void addOptions() {
    OptionsList list = ...;
    
    // <- MODIFY: Add shader pack button to options array
    
    list.addSmall(options);
}
```

**What Iris Does**:
```java
// Adds 2 new options:
1. "Shader Pack Selection" button -> Opens ShaderPackScreen
2. Modified render distance option (for shader compatibility)
```

**MattMC Implementation**:
```java
private void addOptions() {
    // ... existing options ...
    
    List<OptionInstance<?>> optionsList = new ArrayList<>(Arrays.asList(
        // ... existing options ...
    ));
    
    // Add shader pack selection button
    optionsList.add(OptionInstance.createButton(
        Component.translatable("options.shaders"),
        Component.empty(),
        (parent) -> minecraft.setScreen(new ShaderPackSelectionScreen(this))
    ));
    
    // Convert to array and add to list
    list.addSmall(optionsList.toArray(new OptionInstance[0]));
}
```

### 5.2 Shader Pack Selection Screen

**Create New Screen**:
```java
public class ShaderPackSelectionScreen extends Screen {
    private final Screen parent;
    private ShaderPackList packList;
    
    public ShaderPackSelectionScreen(Screen parent) {
        super(Component.translatable("options.shaders.title"));
        this.parent = parent;
    }
    
    @Override
    protected void init() {
        // Create shader pack list widget
        this.packList = new ShaderPackList(
            this,
            minecraft,
            this.width,
            this.height - 64,
            32,
            36
        );
        
        // Populate with available packs
        ShaderPackRepository repo = ShaderSystem.getInstance().getRepository();
        for (ShaderPack pack : repo.getAvailablePacks()) {
            packList.addEntry(new ShaderPackEntry(pack));
        }
        
        addRenderableWidget(packList);
        
        // Add buttons
        addRenderableWidget(Button.builder(
            Component.translatable("options.shaders.apply"),
            btn -> applyShaderPack()
        ).bounds(this.width / 2 - 100, this.height - 38, 200, 20).build());
        
        addRenderableWidget(Button.builder(
            CommonComponents.GUI_DONE,
            btn -> minecraft.setScreen(parent)
        ).bounds(this.width / 2 - 100, this.height - 26, 200, 20).build());
    }
    
    private void applyShaderPack() {
        ShaderPackEntry selected = packList.getSelected();
        if (selected != null) {
            ShaderSystem.getInstance().loadShaderPack(selected.getPack());
            minecraft.levelRenderer.allChanged(); // Reload chunks
        }
    }
}
```

### 5.3 Shader Options Screen

**For shader packs that define options**:

```java
public class ShaderOptionsScreen extends Screen {
    private final ShaderPack pack;
    private final List<ShaderOption> options;
    
    // Parse options from shader source code comments:
    // const int shadowMapResolution = 2048; // [1024 2048 4096 8192]
    // #define WAVING_GRASS // Toggle option
    
    // Display options as sliders, toggles, dropdowns
    // Save changed values
    // Reload shader pack when options change
}
```

---

## 6. Pipeline Management

### 6.1 Pipeline Creation

**Implementation**:
```java
public class PipelineManager {
    private Map<DimensionId, ShaderPipeline> pipelines = new HashMap<>();
    private ShaderPipeline currentPipeline;
    
    public ShaderPipeline preparePipeline(DimensionId dimension) {
        if (!pipelines.containsKey(dimension)) {
            // Create new pipeline for this dimension
            ShaderPack pack = ShaderSystem.getInstance().getCurrentPack();
            
            if (pack != null) {
                // Create shader rendering pipeline
                currentPipeline = new ShaderPackPipeline(pack, dimension);
            } else {
                // Use vanilla pipeline
                currentPipeline = new VanillaPipeline();
            }
            
            pipelines.put(dimension, currentPipeline);
        } else {
            currentPipeline = pipelines.get(dimension);
        }
        
        return currentPipeline;
    }
    
    public void destroyPipeline() {
        // Clean up all pipelines
        for (ShaderPipeline pipeline : pipelines.values()) {
            pipeline.destroy();
        }
        pipelines.clear();
        currentPipeline = null;
    }
}
```

### 6.2 Dimension Change Handling

**Iris Hook**: `MixinMinecraft_PipelineManagement.updateLevelInEngines()` @ HEAD

**Location in Vanilla**:
```java
// net/minecraft/client/Minecraft.java
private void updateLevelInEngines(@Nullable ClientLevel level) {
    // <- INJECT HEAD: Handle dimension change
    
    // ... update renderers ...
}
```

**What Iris Does**:
```java
if (currentDimension != lastDimension) {
    // Destroy old pipeline
    pipelineManager.destroyPipeline();
    
    // Create new pipeline for new dimension
    if (level != null) {
        pipelineManager.preparePipeline(getCurrentDimension());
    }
}
```

**MattMC Implementation**:
```java
private void updateLevelInEngines(@Nullable ClientLevel level) {
    // Check for dimension change
    DimensionId currentDim = level != null ? 
        DimensionId.fromLevel(level) : null;
    DimensionId lastDim = ShaderSystem.getInstance().getLastDimension();
    
    if (!Objects.equals(currentDim, lastDim)) {
        // Dimension changed - reload pipeline
        ShaderSystem.getInstance().onDimensionChange(currentDim);
    }
    
    // ... existing engine update code ...
}
```

---

## 7. Framebuffer Management

### 7.1 G-Buffer Creation

**Implementation**:
```java
public class GBufferManager {
    private final Map<String, Framebuffer> gBuffers = new HashMap<>();
    
    public void createGBuffers(int width, int height, ShaderPackDirectives directives) {
        // Create colortex0-15
        for (int i = 0; i < 16; i++) {
            String name = "colortex" + i;
            TextureFormat format = directives.getColorTexFormat(i);
            
            Framebuffer fb = new Framebuffer(
                width, height,
                format.hasDepth(),
                Minecraft.ON_OSX
            );
            fb.setClearColor(0, 0, 0, 0);
            
            gBuffers.put(name, fb);
        }
        
        // Create depth textures
        gBuffers.put("depthtex0", createDepthTexture(width, height));
        gBuffers.put("depthtex1", createDepthTexture(width, height));
        gBuffers.put("depthtex2", createDepthTexture(width, height));
        
        // Create shadow textures
        int shadowRes = directives.getShadowMapResolution();
        gBuffers.put("shadowtex0", createShadowTexture(shadowRes, shadowRes));
        gBuffers.put("shadowtex1", createShadowTexture(shadowRes, shadowRes));
        gBuffers.put("shadowcolor0", createColorTexture(shadowRes, shadowRes));
        gBuffers.put("shadowcolor1", createColorTexture(shadowRes, shadowRes));
    }
    
    public Framebuffer getGBuffer(String name) {
        return gBuffers.get(name);
    }
}
```

### 7.2 Framebuffer Binding

**Hook into RenderTarget operations**:

```java
// Modify places where framebuffers are bound
public class FramebufferBinder {
    public static void bindForRender(String target) {
        ShaderPipeline pipeline = ShaderSystem.getInstance()
            .getCurrentPipeline();
        
        if (pipeline instanceof ShaderPackPipeline shaderPipeline) {
            // Bind appropriate G-buffer
            Framebuffer fb = shaderPipeline.getGBuffer(target);
            if (fb != null) {
                fb.bindWrite(true);
                return;
            }
        }
        
        // Fall back to main framebuffer
        Minecraft.getInstance().getMainRenderTarget().bindWrite(true);
    }
}
```

---

## 8. Implementation Phases

### Phase 1: Foundation (Week 1-2)
**Goal**: Basic infrastructure without rendering

**Tasks**:
1. Create `ShaderSystem` singleton class
2. Implement `ShaderPackRepository` with ResourceManager scanning
3. Create `ShaderPack` class for pack representation
4. Implement `ShaderProperties` parser
5. Create `PipelineManager` stub
6. Add initialization hooks (early init, RenderSystem init, loading complete)

**Verification**:
- Shader packs are discovered and listed in logs
- Configuration is loaded successfully
- No crashes during initialization

### Phase 2: GUI Integration (Week 2-3)
**Goal**: User can select shader packs from menu

**Tasks**:
1. Create `ShaderPackSelectionScreen`
2. Add button to Video Settings
3. Implement pack switching (no rendering yet)
4. Create configuration persistence

**Verification**:
- Shader pack screen opens from video settings
- Available packs are listed
- Selected pack is saved and persisted

### Phase 3: Pipeline Architecture (Week 3-5)
**Goal**: Pipeline structure ready for shader programs

**Tasks**:
1. Implement `ShaderPackPipeline` class
2. Create `WorldRenderingPhase` enum
3. Implement phase transition tracking
4. Create `GBufferManager` for framebuffer management
5. Add pipeline hooks to `LevelRenderer.renderLevel()`

**Verification**:
- Pipeline is created when shader pack is selected
- Phases transition correctly during rendering
- G-buffers are created (but not used yet)

### Phase 4: Shader Compilation (Week 5-7)
**Goal**: Compile shader programs from pack

**Tasks**:
1. Implement `IncludeProcessor` for #include directives
2. Create `ShaderCompiler` for GLSL compilation
3. Implement `ProgramSet` (gbuffers, shadow, composite, final)
4. Parse shader options from source comments
5. Compile all programs for a pack

**Verification**:
- Shader programs compile without errors
- Include files are resolved correctly
- Shader options are discovered

### Phase 5: Program Interception (Week 7-9)
**Goal**: Use shader programs instead of vanilla

**Tasks**:
1. Implement `ShaderProgramInterceptor`
2. Create `ProgramMapper` for vanilla->shader mapping
3. Hook into `GlDevice.getOrCompilePipeline()`
4. Implement shader program activation

**Verification**:
- Custom shaders are used for rendering
- Can see shader effects on screen (even if incorrect)
- No crashes during rendering

### Phase 6: Uniforms System (Week 9-11)
**Goal**: Provide data to shaders

**Tasks**:
1. Implement ~200+ uniform providers
2. Create `UniformUpdater` for frame-by-frame updates
3. Integrate matrix capture from rendering
4. Implement time uniforms, camera uniforms, world uniforms

**Verification**:
- Shaders receive correct uniform values
- Time-based effects work (day/night cycle visible)
- Camera movement affects rendering correctly

### Phase 7: Shadow Rendering (Week 11-13)
**Goal**: Render shadow maps

**Tasks**:
1. Implement `ShadowRenderer`
2. Create shadow pass before main rendering
3. Set up shadow framebuffers
4. Render geometry to shadow map
5. Provide shadow uniforms to main pass

**Verification**:
- Shadow maps are rendered
- Shadows appear in world (if shader uses them)
- Shadow distance/resolution configurable

### Phase 8: Composite Passes (Week 13-15)
**Goal**: Post-processing effects

**Tasks**:
1. Implement `CompositeRenderer`
2. Chain composite passes (composite, composite1, composite2...)
3. Implement `FinalPassRenderer` for screen output
4. Handle G-buffer ping-pong (reading/writing)

**Verification**:
- Post-processing effects visible (bloom, DOF, etc.)
- Multiple composite passes execute in order
- Final output appears on screen

### Phase 9: Compatibility & Polish (Week 15-17)
**Goal**: 1:1 compatibility with Complimentary

**Tasks**:
1. Test Complimentary Reimagined shader pack
2. Fix any compatibility issues
3. Implement missing features (if any)
4. Performance optimization
5. Add shader reload hotkey

**Verification**:
- Complimentary renders identically to Iris
- No visual glitches
- Acceptable performance (within 10% of Iris)

### Phase 10: Testing & Validation (Week 17-18)
**Goal**: Production ready

**Tasks**:
1. Test 10+ popular shader packs
2. Test in all 3 dimensions
3. Test shader switching
4. Test with various video settings
5. Memory leak testing

**Verification**:
- No crashes with any tested pack
- All dimensions work correctly
- No memory leaks detected

---

## 9. Critical Integration Points Summary

### Rendering Hooks (in order of execution):

1. **LevelRenderer.renderLevel() HEAD**
   - Setup pipeline
   - Capture matrices
   - Begin rendering

2. **After Clear Operations**
   - Initialize render state
   - Prepare G-buffers

3. **After Frustum Preparation**
   - Render shadow pass

4. **Before/During Render Phases**
   - Set appropriate phase (SKY, TERRAIN, ENTITIES, etc.)
   - Allow shader to choose correct program

5. **Before Return from renderLevel()**
   - Finalize rendering
   - Run composite passes
   - Cleanup state

### Shader Program Hooks:

1. **GlDevice.getOrCompilePipeline()**
   - Intercept program requests
   - Return shader program instead of vanilla

### Resource Hooks:

1. **Early Initialization**
   - Scan for shader packs
   - Load configuration

2. **Texture Loading**
   - Load custom textures from pack
   - Set up PBR textures (if enabled)

### GUI Hooks:

1. **VideoSettingsScreen.addOptions()**
   - Add shader pack button

2. **New Screens**
   - ShaderPackSelectionScreen
   - ShaderOptionsScreen

### Lifecycle Hooks:

1. **Minecraft Constructor**
   - Early initialization

2. **After RenderSystem.initRenderer()**
   - OpenGL-dependent initialization
   - Load shader pack

3. **TitleScreen.init()**
   - Complete loading
   - Prepare default pipeline

4. **Minecraft.updateLevelInEngines()**
   - Handle dimension changes
   - Reload pipeline

---

## 10. Testing Strategy

### Unit Tests:
1. Shader pack discovery
2. Properties file parsing
3. Include file resolution
4. Uniform value calculation
5. Program mapping

### Integration Tests:
1. Pipeline creation/destruction
2. Dimension switching
3. Shader pack switching
4. G-buffer management
5. Framebuffer binding

### Visual Tests:
1. Complimentary Reimagined renders correctly
2. All 3 dimensions work
3. Day/night cycle
4. Weather effects
5. Entity rendering
6. Particles
7. Translucent blocks
8. Water/fluids

### Performance Tests:
1. FPS comparison with vanilla
2. FPS comparison with Iris
3. Memory usage
4. Shader compilation time
5. Reload time

---

## Appendix A: Key Files to Study

**From Iris Source (frnsrc/Iris-1.21.9/):**

1. **Core Architecture**:
   - `Iris.java` - Main entry point
   - `pipeline/IrisRenderingPipeline.java` - Core rendering
   - `pipeline/PipelineManager.java` - Pipeline management

2. **Shader Loading**:
   - `shaderpack/ShaderPack.java` - Pack representation
   - `shaderpack/loading/ProgramSet.java` - Program organization
   - `shaderpack/include/IncludeGraph.java` - Include resolution

3. **Rendering Hooks**:
   - `mixin/MixinLevelRenderer.java` - World rendering hooks
   - `mixin/MixinGameRenderer.java` - Game rendering hooks
   - `mixin/MixinShaderManager_Overrides.java` - Program interception

4. **Uniforms**:
   - `uniforms/CommonUniforms.java` - Uniform providers
   - `uniforms/CapturedRenderingState.java` - State capture

5. **GUI**:
   - `gui/screen/ShaderPackScreen.java` - Pack selection UI
   - `mixin/gui/MixinVideoSettingsScreen.java` - Settings integration

---

## Appendix B: MattMC-Specific Advantages

**Direct Code Access Benefits**:

1. **No Mixin Complexity**: Modify code directly instead of injecting at runtime
2. **Better Debugging**: Full source access, no obfuscation issues
3. **Compile-Time Validation**: Catch errors during compilation
4. **Cleaner Integration**: Natural method calls instead of injection
5. **Better IDE Support**: Full autocomplete and refactoring

**Baked-In Advantages**:

1. **No ZIP Extraction**: Resources already in JAR, faster loading
2. **No Filesystem I/O**: Use ResourceManager, more reliable
3. **Guaranteed Availability**: Shaders always present, no missing packs
4. **Smaller Distribution**: One JAR includes everything

---

## Conclusion

This guide provides a complete roadmap for implementing Iris shader capabilities in MattMC. The key is to implement the same logical hooks that Iris uses via Mixin, but as direct code modifications. By following the phased approach and focusing on one integration point at a time, you can achieve 1:1 compatibility with OptiFine/Iris shader packs like Complimentary Reimagined.

**Next Steps**:
1. Begin with Phase 1 (Foundation)
2. Implement and test each phase thoroughly
3. Reference Iris source code in `frnsrc/Iris-1.21.9/` for implementation details
4. Test continuously with target shader pack (Complimentary)

**Expected Timeline**: 15-18 weeks for complete implementation with testing

**Success Criteria**: Complimentary Reimagined renders identically to Iris/OptiFine with acceptable performance

---

*Document Version: 1.0*  
*Date: December 2024*  
*Project: MattMC Shader System Integration*  
*Reference: Iris 1.21.9 Source Code*
