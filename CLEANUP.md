# MattMC Cleanup Plan: Native Sodium & Iris Integration

## Executive Summary

This document provides actionable steps to **completely remove Fabric Loader** and integrate Sodium and Iris as **native, first-class features** of MattMC. The goal is to eliminate all modloader infrastructure and treat advanced rendering (Sodium) and shader support (Iris) as built-in capabilities of the game engine.

**Current Architecture (Modloader-based):**
```
Fabric Loader (Knot) → Loads Mods → [Sodium JAR, Iris JAR]
    ↓
Runtime Mixin Transformation
    ↓
Modified Minecraft Classes
```

**Target Architecture (Native Integration):**
```
MattMC Engine
├── Vanilla Rendering Path (preserved)
├── Sodium Rendering Path (native)
└── Iris Shader Pipeline (native)
```

**Key Principle:** Sodium and Iris are NOT mods. They are advanced rendering features that ship with the game, like any other Minecraft feature.

---

## Current State Analysis

### What We Have Now

**Fabric Loader (199 Java files)**
- Purpose: Mod loading infrastructure
- Location: `modules/fabric-loader-0.18.2/`
- Output: `fabric-loader-0.18.2.jar`
- **Status: TO BE REMOVED ENTIRELY**

**Sodium (581 Java files)**
- Purpose: High-performance chunk rendering
- Location: `modules/sodium-1.21.9/`
- Output: `sodium-0.7.2-mc1.21.10.jar`
- Dependencies: Fabric API stubs, Fabric Loader (mixins)
- **Status: TO BE INTEGRATED AS NATIVE FEATURE**

**Iris (725 Java files)**
- Purpose: Shader pack support (OptiFine compatibility)
- Location: `modules/Iris-1.21.9/`
- Output: `iris-1.9.6-mc1.21.10.jar`
- Dependencies: Fabric API stubs, Fabric Loader (mixins), Sodium
- **Status: TO BE INTEGRATED AS NATIVE FEATURE**

### Problems with Current Architecture

1. **Fabric Loader Overhead**: Runtime mod discovery, class loading, mixin transformation
2. **Artificial Boundaries**: Code separation between "base game" and "mods"
3. **Complex Build**: 4 source sets, 4+ JAR files, complex dependencies
4. **Fragmented Configuration**: Multiple config files (options.txt, sodium-options.json, iris.properties)
5. **Mixin Dependency**: 320+ mixin classes for runtime bytecode transformation
6. **Startup Overhead**: Mod scanning, metadata parsing, entrypoint discovery
7. **User Confusion**: Features feel like add-ons rather than native capabilities

---

## Integration Roadmap: 12 Focused Steps

Each step is designed to:
- ✅ Build successfully with zero errors
- ✅ Maintain full functionality (no regressions)
- ✅ Be independently testable
- ✅ Progress toward complete Fabric removal

---

## Step 1: Apply All Access Wideners Permanently to Source Code

**Objective:** Eliminate runtime access widening by making all necessary visibility changes permanent in Minecraft source.

**Why This First:** Removes dependency on Fabric's access widener system, which is foundational for other steps.

**Current Problem:**
- Sodium: `sodium-common.accesswidener`, `sodium-fabric.accesswidener` (18+ declarations)
- Iris: `iris.accesswidener` (extensive declarations)
- Applied at runtime by Fabric Loader
- Creates hard dependency on Fabric infrastructure

**Actions:**

1. **Extract all access widener declarations:**
   ```bash
   # Parse access widener files
   cat modules/sodium-1.21.9/common/src/main/resources/sodium-common.accesswidener
   cat modules/sodium-1.21.9/fabric/src/main/resources/sodium-fabric.accesswidener
   cat modules/Iris-1.21.9/common/src/main/resources/iris.accesswidener
   ```

2. **For each declaration, modify source code:**
   
   Example widener entry:
   ```
   accessible field net/minecraft/client/renderer/LevelRenderer capturedFrustum Lnet/minecraft/client/renderer/culling/Frustum;
   ```
   
   Apply to source:
   ```java
   // Before
   private Frustum capturedFrustum;
   
   // After (in LevelRenderer.java)
   /**
    * The frustum used for culling during the previous frame.
    * 
    * @apiNote Made public for advanced chunk rendering optimization.
    * Originally widened by: sodium-common.accesswidener
    */
   public Frustum capturedFrustum;
   ```

3. **Create tracking document:**
   
   Create `docs/ACCESS-WIDENING-LOG.md`:
   ```markdown
   # Access Widening Log
   
   This document tracks all visibility changes made to support native
   Sodium and Iris integration.
   
   ## LevelRenderer.java
   - `capturedFrustum`: private → public (Sodium chunk culling)
   - `renderDistance()`: private → public (Sodium render distance)
   ...
   ```

4. **Apply systematically:**
   - Go through each class mentioned in access wideners
   - Change visibility modifiers (private → public/protected)
   - Add JavaDoc explaining why and what needs it
   - Test compilation after each file

5. **Verify no runtime access widening needed:**
   - Remove access widener files
   - Build Sodium and Iris
   - Ensure compilation succeeds

**Success Criteria:**
- ✅ All access widener files deleted
- ✅ Sodium compiles without access widener processing
- ✅ Iris compiles without access widener processing
- ✅ Complete log of all widened members in docs/
- ✅ Build succeeds: `./gradlew build`

**Estimated Effort:** 10-14 hours

---

## Step 2: Inline All Sodium Mixins Into Minecraft Source Code

**Objective:** Replace Sodium's runtime mixin transformations with direct source code modifications.

**Why This Step:** Eliminates ~120 Sodium mixins, removing major dependency on Fabric's mixin system.

**Current Problem:**
- Sodium uses ~120 mixin classes to transform Minecraft at runtime
- Critical mixins: `LevelRendererMixin`, `GameRendererMixin`, `ChunkRenderCacheMixin`
- Runtime ASM transformation overhead
- Hard to debug transformed bytecode

**Actions:**

1. **Analyze all Sodium mixins:**
   ```bash
   # List all mixin classes
   find modules/sodium-1.21.9 -path "*/mixin/*.java" | wc -l
   ```

2. **Create feature toggle system:**
   ```java
   // Create: net/minecraft/client/renderer/SodiumRenderer.java
   package net.minecraft.client.renderer;
   
   public class SodiumRenderer {
       private static boolean enabled = true;
       
       public static boolean isEnabled() {
           return enabled;
       }
       
       public static void setEnabled(boolean enabled) {
           SodiumRenderer.enabled = enabled;
       }
   }
   ```

3. **Inline high-impact mixins first** (sorted by render pipeline impact):

   **A. LevelRendererMixin → LevelRenderer.java**
   ```java
   // In: net/minecraft/client/renderer/LevelRenderer.java
   
   import net.minecraft.sodium.render.SodiumChunkRenderer;
   
   public class LevelRenderer {
       // Add Sodium renderer field
       @Nullable
       private SodiumChunkRenderer sodiumChunkRenderer;
       
       // Modify renderChunks method
       public void renderChunks(Camera camera, Frustum frustum, boolean spectator) {
           // ═══════════════════════════════════════════════════════════
           // SODIUM INTEGRATION - Inline from LevelRendererMixin
           // Original mixin: sodium/.../mixin/core/render.LevelRendererMixin
           // ═══════════════════════════════════════════════════════════
           if (SodiumRenderer.isEnabled() && this.sodiumChunkRenderer != null) {
               this.sodiumChunkRenderer.renderChunks(camera, frustum, spectator);
               return; // Skip vanilla rendering
           }
           // ═══════════════════════════════════════════════════════════
           // END SODIUM INTEGRATION
           // ═══════════════════════════════════════════════════════════
           
           // Vanilla chunk rendering (preserved as fallback)
           // ... original code ...
       }
       
       // Add Sodium initialization
       public void initializeSodiumRenderer() {
           if (SodiumRenderer.isEnabled()) {
               this.sodiumChunkRenderer = new SodiumChunkRenderer(this);
           }
       }
   }
   ```

   **B. GameRendererMixin → GameRenderer.java**
   ```java
   // In: net/minecraft/client/renderer/GameRenderer.java
   
   public void render(float partialTicks, long nanoTime, boolean renderWorld) {
       // ═══════════════════════════════════════════════════════════
       // SODIUM INTEGRATION - Inline from GameRendererMixin
       // ═══════════════════════════════════════════════════════════
       if (SodiumRenderer.isEnabled()) {
           SodiumRenderer.setupFrame(this);
       }
       // ═══════════════════════════════════════════════════════════
       
       // ... rest of render method ...
       
       if (renderWorld) {
           this.renderLevel(partialTicks, nanoTime, poseStack);
       }
       
       // ═══════════════════════════════════════════════════════════
       // SODIUM INTEGRATION - Cleanup
       // ═══════════════════════════════════════════════════════════
       if (SodiumRenderer.isEnabled()) {
           SodiumRenderer.cleanupFrame(this);
       }
       // ═══════════════════════════════════════════════════════════
   }
   ```

   **C. Block Rendering Mixins → BlockRenderDispatcher.java**
   ```java
   // Apply similar pattern for block rendering optimizations
   ```

4. **Create integration tracking document:**
   
   Create `docs/SODIUM-MIXIN-INTEGRATION-LOG.md`:
   ```markdown
   # Sodium Mixin Integration Log
   
   ## Inlined Mixins
   
   ### LevelRendererMixin
   - **Target:** net.minecraft.client.renderer.LevelRenderer
   - **Purpose:** Replace chunk rendering with Sodium's optimized renderer
   - **Status:** ✅ Inlined
   - **Lines Modified:** 150-200
   
   ### GameRendererMixin
   - **Target:** net.minecraft.client.renderer.GameRenderer
   - **Purpose:** Setup/cleanup hooks for Sodium renderer
   - **Status:** ✅ Inlined
   - **Lines Modified:** 50-75
   
   ... (continue for all mixins)
   ```

5. **Remove inlined mixins from config:**
   ```json
   // In: modules/sodium-1.21.9/.../sodium-common.mixins.json
   // Remove entries for inlined mixins
   ```

6. **Test both rendering paths:**
   ```java
   // Test vanilla rendering
   SodiumRenderer.setEnabled(false);
   // Verify game renders correctly
   
   // Test Sodium rendering  
   SodiumRenderer.setEnabled(true);
   // Verify performance and correctness
   ```

**Success Criteria:**
- ✅ All ~120 Sodium mixins inlined or removed
- ✅ Vanilla rendering path works (Sodium disabled)
- ✅ Sodium rendering path works (Sodium enabled)
- ✅ No mixin configuration files for Sodium remain
- ✅ Performance parity: FPS unchanged ±5%
- ✅ Build succeeds: `./gradlew build`

**Estimated Effort:** 30-40 hours (largest single step)

**Note:** This can be done incrementally - inline 10-20 mixins at a time, test, commit.

---

## Step 3: Inline All Iris Mixins Into Minecraft Source Code

**Objective:** Replace Iris's runtime mixin transformations with direct source code modifications.

**Why This Step:** Eliminates ~200+ Iris mixins, further reducing Fabric dependency.

**Current Problem:**
- Iris uses ~200+ mixin classes
- Mixins modify both Minecraft AND Sodium classes
- Shader pipeline injected via mixins
- Complex Sodium compatibility layer via mixins

**Actions:**

1. **Create shader pipeline toggle:**
   ```java
   // Create: net/minecraft/client/renderer/IrisShaderPipeline.java
   package net.minecraft.client.renderer;
   
   public class IrisShaderPipeline {
       private static boolean enabled = true;
       
       public static boolean isEnabled() {
           return enabled;
       }
       
       public static void setEnabled(boolean enabled) {
           IrisShaderPipeline.enabled = enabled;
       }
   }
   ```

2. **Inline shader pipeline into GameRenderer:**
   ```java
   // In: net/minecraft/client/renderer/GameRenderer.java
   
   import net.minecraft.iris.pipeline.WorldRenderingPipeline;
   
   public class GameRenderer {
       @Nullable
       private WorldRenderingPipeline shaderPipeline;
       
       public void render(float partialTicks, long nanoTime, boolean renderWorld) {
           // ═══════════════════════════════════════════════════════════
           // IRIS SHADER INTEGRATION - Inline from GameRendererMixin
           // Original mixin: iris/.../mixin.GameRendererMixin
           // ═══════════════════════════════════════════════════════════
           if (IrisShaderPipeline.isEnabled() && this.shaderPipeline != null) {
               // Shadow pass
               this.shaderPipeline.beginShadowPass();
               this.renderShadowPass(partialTicks);
               this.shaderPipeline.endShadowPass();
               
               // Main pass with shader
               this.shaderPipeline.beginMainPass();
               this.renderLevel(partialTicks, nanoTime, poseStack);
               this.shaderPipeline.endMainPass();
               
               // Composite passes
               this.shaderPipeline.runCompositePasses();
           } else {
               // Vanilla rendering
               if (renderWorld) {
                   this.renderLevel(partialTicks, nanoTime, poseStack);
               }
           }
           // ═══════════════════════════════════════════════════════════
           // END IRIS SHADER INTEGRATION
           // ═══════════════════════════════════════════════════════════
       }
   }
   ```

3. **Inline Sodium compatibility mixins:**
   
   Since Sodium code will be in the main source (Step 4), these mixins become direct method calls:
   ```java
   // Before (Iris mixin into Sodium class):
   // @Mixin(SodiumChunkRenderer.class)
   // public class SodiumChunkRendererMixin {
   //     @Inject(method = "render", at = @At("HEAD"))
   //     private void iris_onRender(CallbackInfo ci) {
   //         IrisShaderPipeline.setupSodiumUniforms();
   //     }
   // }
   
   // After (direct modification of SodiumChunkRenderer):
   public class SodiumChunkRenderer {
       public void render(Camera camera, Frustum frustum) {
           // IRIS INTEGRATION - Direct call instead of mixin
           if (IrisShaderPipeline.isEnabled()) {
               IrisShaderPipeline.setupSodiumUniforms(this);
           }
           
           // ... rest of rendering ...
       }
   }
   ```

4. **Create integration log:**
   
   Create `docs/IRIS-MIXIN-INTEGRATION-LOG.md` tracking all inlined mixins.

**Success Criteria:**
- ✅ All ~200+ Iris mixins inlined or removed
- ✅ Vanilla rendering works (no shaders)
- ✅ Shader rendering works (with shader packs)
- ✅ Sodium + Iris compatibility maintained
- ✅ No Iris mixin configuration files remain
- ✅ Visual output identical to mixin-based version
- ✅ Build succeeds: `./gradlew build`

**Estimated Effort:** 35-45 hours

---

## Step 4: Move Sodium Source Into Main Source Set

**Objective:** Integrate Sodium's 581 Java files directly into the main Minecraft source tree as native code.

**Why This Step:** Sodium becomes part of the engine, not a separate module.

**Current Problem:**
- Sodium in `modules/sodium-1.21.9/` (separate)
- Compiled to separate source set
- Packaged as separate JAR
- Artificial boundary between "Minecraft" and "Sodium"

**Actions:**

1. **Design package structure:**
   ```
   net/minecraft/client/renderer/sodium/
   ├── SodiumRenderer.java           (main entry point)
   ├── chunk/
   │   ├── ChunkRenderManager.java   (chunk render orchestration)
   │   ├── ChunkBuilder.java         (parallel chunk meshing)
   │   ├── RenderSection.java        (individual chunk section)
   │   └── compile/
   │       ├── ChunkBuildTask.java
   │       └── ChunkMeshBuilder.java
   ├── terrain/
   │   ├── TerrainRenderPass.java
   │   ├── BlockRenderer.java        (optimized block rendering)
   │   └── format/
   │       └── CompactVertexFormat.java
   ├── gl/
   │   ├── GlCommandBuffer.java      (OpenGL command buffering)
   │   ├── GlVertexArray.java
   │   └── arena/
   │       └── GlBufferArena.java    (buffer pooling)
   ├── config/
   │   └── SodiumConfig.java         (Sodium settings)
   └── util/
       ├── MathUtil.java
       └── NativeBuffer.java
   ```

2. **Move source files:**
   ```bash
   # Copy Sodium common source
   cp -r modules/sodium-1.21.9/common/src/main/java/net/caffeinemc/mods/sodium/client \
         net/minecraft/client/renderer/sodium/
   
   # Copy Sodium API source
   cp -r modules/sodium-1.21.9/common/src/api/java/net/caffeinemc/mods/sodium/api \
         net/minecraft/client/renderer/sodium/api/
   ```

3. **Update package declarations:**
   ```java
   // Before
   package net.caffeinemc.mods.sodium.client.render.chunk;
   
   // After
   package net.minecraft.client.renderer.sodium.chunk;
   ```

4. **Update all imports globally:**
   ```bash
   # Find and replace across all files
   find . -name "*.java" -type f -exec sed -i \
     's/net\.caffeinemc\.mods\.sodium\.client/net.minecraft.client.renderer.sodium/g' {} +
   ```

5. **Move Sodium resources:**
   ```bash
   # Move shaders, textures, etc.
   cp -r modules/sodium-1.21.9/common/src/main/resources/* \
         src/main/resources/
   ```

6. **Update build.gradle:**
   ```groovy
   // Remove sodium source set
   sourceSets {
       main {
           java {
               srcDir '.'
               // ... excludes ...
               // REMOVED: exclude 'modules/sodium-1.21.9/**'
           }
       }
       // REMOVED: sodium { ... }
   }
   
   // Remove sodiumJar task
   // REMOVED: tasks.register('sodiumJar', Jar) { ... }
   ```

7. **Remove Fabric API references in Sodium code:**
   ```java
   // Before (Sodium entrypoint)
   public class SodiumFabricMod implements ClientModInitializer {
       @Override
       public void onInitializeClient() {
           SodiumRenderer.initialize();
       }
   }
   
   // After (direct initialization in Minecraft startup)
   // In: net/minecraft/client/Minecraft.java
   public Minecraft(GameConfig config) {
       // ... early initialization ...
       
       // Initialize Sodium renderer
       net.minecraft.client.renderer.sodium.SodiumRenderer.initialize();
       
       // ... rest of initialization ...
   }
   ```

**Success Criteria:**
- ✅ All 581 Sodium Java files in main source set
- ✅ Package structure: `net.minecraft.client.renderer.sodium.*`
- ✅ No references to `net.caffeinemc.mods.sodium`
- ✅ Sodium initializes with Minecraft, not as mod
- ✅ No separate Sodium JAR produced
- ✅ Build succeeds: `./gradlew build`
- ✅ Client runs with Sodium working

**Estimated Effort:** 16-20 hours

---

## Step 5: Move Iris Source Into Main Source Set

**Objective:** Integrate Iris's 725 Java files directly into the main Minecraft source tree as native shader support.

**Why This Step:** Shader pack support becomes a native feature, like resource packs.

**Actions:**

1. **Design package structure:**
   ```
   net/minecraft/client/renderer/iris/
   ├── IrisShaderPipeline.java       (main entry point)
   ├── pipeline/
   │   ├── WorldRenderingPipeline.java
   │   ├── ShadowRenderer.java
   │   ├── CompositeRenderer.java
   │   └── newshader/
   │       └── NewWorldRenderingPipeline.java
   ├── shaderpack/
   │   ├── ShaderPack.java           (shader pack loading)
   │   ├── ShaderPackParser.java
   │   ├── option/
   │   │   └── ShaderPackOptions.java
   │   └── include/
   │       └── ShaderIncludes.java
   ├── uniforms/
   │   ├── UniformHolder.java
   │   ├── BuiltinUniforms.java      (time, camera, etc.)
   │   └── custom/
   │       └── CustomUniforms.java
   ├── targets/
   │   ├── RenderTargetManager.java
   │   └── Framebuffer.java
   └── compat/
       └── sodium/
           └── SodiumShaderInterface.java
   ```

2. **Move source files:**
   ```bash
   # Copy Iris source
   cp -r modules/Iris-1.21.9/common/src/main/java/net/irisshaders/iris \
         net/minecraft/client/renderer/iris/
   
   # Copy Iris API
   cp -r modules/Iris-1.21.9/common/src/api/java/* \
         net/minecraft/client/renderer/iris/api/
   ```

3. **Update package declarations:**
   ```java
   // Before
   package net.irisshaders.iris.pipeline;
   
   // After
   package net.minecraft.client.renderer.iris.pipeline;
   ```

4. **Update all imports:**
   ```bash
   find . -name "*.java" -type f -exec sed -i \
     's/net\.irisshaders\.iris/net.minecraft.client.renderer.iris/g' {} +
   ```

5. **Integrate shader pack loading with resource packs:**
   ```java
   // In: net/minecraft/server/packs/PackType.java
   public enum PackType {
       CLIENT_RESOURCES("assets", ...),
       SERVER_DATA("data", ...),
       SHADER_PACKS("shaderpacks", ...); // NEW: Native shader pack type
   }
   
   // In: net/minecraft/client/Minecraft.java
   private void loadShaderPacks() {
       // Load shader packs like resource packs
       List<ShaderPack> shaderPacks = 
           this.resourcePackRepository.getAvailableShaderPacks();
       
       // Allow user selection
       this.shaderPackManager.loadSelected(shaderPacks);
   }
   ```

6. **Remove Fabric API dependencies:**
   ```java
   // Before (Iris has no explicit entrypoint, uses mixins)
   // After (direct initialization)
   
   // In: net/minecraft/client/renderer/GameRenderer.java
   public GameRenderer(Minecraft minecraft, ...) {
       // Initialize Iris shader pipeline
       this.shaderPipeline = new WorldRenderingPipeline(this);
   }
   ```

**Success Criteria:**
- ✅ All 725 Iris Java files in main source set
- ✅ Package structure: `net.minecraft.client.renderer.iris.*`
- ✅ No references to `net.irisshaders.iris`
- ✅ Shader packs load like resource packs
- ✅ No separate Iris JAR produced
- ✅ Build succeeds: `./gradlew build`
- ✅ Client runs with shaders working

**Estimated Effort:** 18-24 hours

---

## Step 6: Merge Configurations Into Minecraft Options System

**Objective:** Unify all configuration into Minecraft's native `Options.java` and `options.txt` file.

**Why This Step:** Single configuration file, native integration, better UX.

**Current Problem:**
- Three config files: `options.txt`, `sodium-options.json`, `iris.properties`
- Separate config systems
- User confusion

**Actions:**

1. **Analyze current configs:**
   
   **Sodium (sodium-options.json):**
   ```json
   {
     "quality": {
       "weather_quality": "DEFAULT",
       "leaves_quality": "DEFAULT",
       "enable_vignette": true
     },
     "performance": {
       "chunk_builder_threads": 0,
       "always_defer_chunk_updates": false
     }
   }
   ```
   
   **Iris (iris.properties):**
   ```properties
   enableShaders=true
   shaderPack=ComplementaryHungLoIfied.zip
   ```

2. **Add options to Options.java:**
   ```java
   // In: net/minecraft/client/Options.java
   
   public class Options {
       // Existing vanilla options...
       
       // ═══════════════════════════════════════════════════════════
       // SODIUM OPTIONS - Native rendering settings
       // ═══════════════════════════════════════════════════════════
       public final OptionInstance<Boolean> sodiumUseAdvancedRenderer;
       public final OptionInstance<Integer> sodiumChunkBuilderThreads;
       public final OptionInstance<Boolean> sodiumAlwaysDeferChunkUpdates;
       public final OptionInstance<Boolean> sodiumUseBlockFaceCulling;
       public final OptionInstance<Boolean> sodiumUseFogOcclusion;
       public final OptionInstance<QualityLevel> sodiumWeatherQuality;
       public final OptionInstance<QualityLevel> sodiumLeavesQuality;
       public final OptionInstance<Boolean> sodiumEnableVignette;
       
       // ═══════════════════════════════════════════════════════════
       // IRIS OPTIONS - Shader pack settings
       // ═══════════════════════════════════════════════════════════
       public final OptionInstance<Boolean> irisEnableShaders;
       public final OptionInstance<String> irisShaderPack;
       public final OptionInstance<Integer> irisShadowDistance;
       
       public Options(Minecraft minecraft, File gameDirectory) {
           // ... existing initialization ...
           
           // Initialize Sodium options
           this.sodiumUseAdvancedRenderer = OptionInstance.createBoolean(
               "options.sodium.useAdvancedRenderer",
               true, // default enabled
               value -> {
                   SodiumRenderer.setEnabled(value);
               }
           );
           
           // Initialize Iris options
           this.irisEnableShaders = OptionInstance.createBoolean(
               "options.iris.enableShaders",
               true,
               value -> {
                   IrisShaderPipeline.setEnabled(value);
               }
           );
           
           // ... etc
       }
   }
   ```

3. **Create migration logic:**
   ```java
   // In: net/minecraft/client/Options.java
   
   private void migrateOldConfigs() {
       // Migrate sodium-options.json
       File sodiumConfig = new File(gameDirectory, "config/sodium-options.json");
       if (sodiumConfig.exists()) {
           JsonObject json = parseSodiumConfig(sodiumConfig);
           
           // Map old settings to new options
           this.sodiumChunkBuilderThreads.set(
               json.get("performance").get("chunk_builder_threads").getAsInt()
           );
           // ... etc
           
           // Backup and remove old file
           sodiumConfig.renameTo(new File(gameDirectory, "config/sodium-options.json.bak"));
       }
       
       // Migrate iris.properties
       File irisConfig = new File(gameDirectory, "config/iris.properties");
       if (irisConfig.exists()) {
           Properties props = loadProperties(irisConfig);
           
           this.irisEnableShaders.set(
               Boolean.parseBoolean(props.getProperty("enableShaders", "true"))
           );
           this.irisShaderPack.set(
               props.getProperty("shaderPack", "")
           );
           
           // Backup and remove old file
           irisConfig.renameTo(new File(gameDirectory, "config/iris.properties.bak"));
       }
   }
   ```

4. **Update options.txt format:**
   ```
   # Minecraft options (vanilla)
   version:3700
   fov:0.5
   renderDistance:12
   
   # Sodium rendering options
   sodiumUseAdvancedRenderer:true
   sodiumChunkBuilderThreads:0
   sodiumAlwaysDeferChunkUpdates:false
   sodiumUseBlockFaceCulling:true
   sodiumWeatherQuality:DEFAULT
   sodiumLeavesQuality:DEFAULT
   
   # Iris shader options
   irisEnableShaders:true
   irisShaderPack:ComplementaryHungLoIfied.zip
   irisShadowDistance:12
   ```

**Success Criteria:**
- ✅ Single config file: `options.txt`
- ✅ Old configs automatically migrated
- ✅ All Sodium/Iris settings accessible via Options API
- ✅ Settings persist correctly
- ✅ Build succeeds: `./gradlew build`
- ✅ Client saves and loads all settings

**Estimated Effort:** 10-12 hours

---

## Step 7: Create Unified Video Settings UI

**Objective:** Integrate Sodium and Iris options into Minecraft's native Video Settings screen.

**Why This Step:** Cohesive user experience, no separate mod screens.

**Actions:**

1. **Design new screen hierarchy:**
   ```
   Options
   └── Video Settings
       ├── Graphics (vanilla + Sodium quality options)
       ├── Performance (vanilla + Sodium performance options)
       ├── Quality Details (vanilla)
       └── Shaders... (new button)
           ├── Enable Shaders [ON/OFF]
           ├── Shader Pack: [dropdown]
           ├── Shader Options... [button]
           └── Shadow Distance: [slider]
   ```

2. **Modify VideoSettingsScreen.java:**
   ```java
   // In: net/minecraft/client/gui/screens/VideoSettingsScreen.java
   
   protected void init() {
       // ... existing buttons ...
       
       // Add Sodium performance options
       this.addRenderableWidget(
           this.options.sodiumChunkBuilderThreads.createButton(
               this.options,
               this.width / 2 - 155 + 160,
               this.height / 6 + 48,
               150
           )
       );
       
       // Add shaders button
       this.addRenderableWidget(
           Button.builder(
               Component.translatable("options.video.shaders"),
               button -> this.minecraft.setScreen(
                   new ShaderSettingsScreen(this, this.options)
               )
           ).bounds(this.width / 2 - 155, this.height / 6 + 96, 150, 20)
           .build()
       );
   }
   ```

3. **Create ShaderSettingsScreen:**
   ```java
   // Create: net/minecraft/client/gui/screens/ShaderSettingsScreen.java
   
   public class ShaderSettingsScreen extends OptionsSubScreen {
       public ShaderSettingsScreen(Screen parent, Options options) {
           super(parent, options, Component.translatable("options.shaders.title"));
       }
       
       protected void init() {
           // Enable shaders toggle
           this.addRenderableWidget(
               this.options.irisEnableShaders.createButton(...)
           );
           
           // Shader pack selector
           this.addRenderableWidget(
               new ShaderPackButton(...)
           );
           
           // Shader pack options (if pack has options)
           if (currentShaderPack.hasOptions()) {
               this.addRenderableWidget(
                   Button.builder(
                       Component.translatable("options.shaders.packOptions"),
                       button -> this.minecraft.setScreen(
                           new ShaderPackOptionsScreen(this, currentShaderPack)
                       )
                   ).build()
               );
           }
       }
   }
   ```

4. **Create shader pack selection widget:**
   ```java
   public class ShaderPackButton extends Button {
       // Shows current shader pack
       // Opens shader pack selection screen on click
       // Lists available shader packs from shaderpacks/ folder
   }
   ```

5. **Adapt Sodium's custom widgets to vanilla style:**
   ```java
   // Sodium uses custom slider widgets
   // Convert to vanilla OptionInstance sliders
   
   // Before (Sodium custom widget)
   // After (vanilla OptionInstance)
   this.options.sodiumChunkBuilderThreads.createButton(...)
   ```

**Success Criteria:**
- ✅ All options accessible from Video Settings
- ✅ Consistent vanilla UI style
- ✅ Shader pack selection integrated
- ✅ No separate mod screens
- ✅ Tooltips explaining each option
- ✅ Build succeeds: `./gradlew build`

**Estimated Effort:** 14-18 hours

---

## Step 8: Remove All Fabric Loader Code and Infrastructure

**Objective:** Completely delete Fabric Loader - no mod loading, no Knot launcher, no mixins.

**Why This Step:** We've eliminated all dependencies on Fabric; now remove it entirely.

**Current State After Steps 1-7:**
- ✅ Access wideners applied (no runtime widening)
- ✅ Mixins inlined (no runtime transformation)
- ✅ Sodium and Iris in main source (no separate modules)
- ✅ Unified config (no mod configs)
- ✅ Unified UI (no mod screens)

**Actions:**

1. **Verify no Fabric dependencies remain:**
   ```bash
   # Search for Fabric API usage
   grep -r "net.fabricmc" --include="*.java" net/minecraft/
   grep -r "net.fabricmc" --include="*.java" net/minecraft/client/renderer/sodium/
   grep -r "net.fabricmc" --include="*.java" net/minecraft/client/renderer/iris/
   
   # Should return: No matches found
   ```

2. **Remove Fabric Loader module:**
   ```bash
   # Archive for reference
   tar -czf modules-fabric-loader-backup.tar.gz modules/fabric-loader-0.18.2/
   
   # Delete module
   rm -rf modules/fabric-loader-0.18.2/
   ```

3. **Remove Fabric Loader source set from build.gradle:**
   ```groovy
   sourceSets {
       // REMOVED: fabricLoader { ... }
       
       main {
           java {
               srcDir '.'
               // ... excludes ...
           }
       }
   }
   
   // REMOVED: configurations { fabricLoaderImplementation.extendsFrom ... }
   ```

4. **Remove Fabric dependencies from build.gradle:**
   ```groovy
   dependencies {
       // REMOVED: Fabric Loader dependencies
       // implementation 'org.ow2.asm:asm:9.9'
       // implementation 'net.fabricmc:sponge-mixin:...'
       // implementation 'net.fabricmc:tiny-remapper:...'
       // ... etc
       
       // Keep only Minecraft dependencies
       implementation 'com.mojang:brigadier:1.3.10'
       implementation 'com.google.guava:guava:32.1.2-jre'
       // ... etc
   }
   ```

5. **Remove Knot launcher:**
   ```groovy
   // In build.gradle
   
   application {
       // REMOVED: mainClass = 'net.fabricmc.loader.impl.launch.knot.KnotClient'
       mainClass = 'net.minecraft.client.main.Main' // Back to vanilla
   }
   
   jar {
       manifest {
           attributes(
               // REMOVED: 'Main-Class': 'net.fabricmc.loader.impl.launch.knot.KnotClient'
               'Main-Class': 'net.minecraft.client.main.Main'
           )
       }
   }
   ```

6. **Remove Fabric JVM arguments:**
   ```groovy
   tasks.named('runClient', JavaExec) {
       mainClass = 'net.minecraft.client.main.Main'
       
       jvmArgs = clientJvmArgs // No more Fabric args
       // REMOVED: '-Dfabric.development=true'
       // REMOVED: '-Dfabric.gameJarPath.client=...'
   }
   ```

7. **Remove mods directory logic:**
   ```groovy
   tasks.named('runClient', JavaExec) {
       doFirst {
           workingDir.mkdirs()
           file('run/assets').mkdirs()
           
           // REMOVED: Mods directory creation
           // REMOVED: JAR copying to run/mods/
       }
   }
   ```

8. **Remove Fabric JAR tasks:**
   ```groovy
   // REMOVED: tasks.register('fabricLoaderJar', Jar) { ... }
   // REMOVED: tasks.register('sodiumJar', Jar) { ... }
   // REMOVED: tasks.register('irisJar', Jar) { ... }
   // REMOVED: tasks.register('gameJar', Jar) { ... }
   ```

9. **Simplify to single JAR:**
   ```groovy
   jar {
       from sourceSets.main.output
       
       manifest {
           attributes(
               'Main-Class': 'net.minecraft.client.main.Main',
               'Implementation-Title': 'MattMC',
               'Implementation-Version': version
           )
       }
   }
   ```

10. **Remove Fabric API stubs:**
    ```bash
    # Remove stub implementations
    rm -rf net/fabricmc/
    ```

11. **Update documentation:**
    - Remove references to Fabric from README.md
    - Update INTEGRATION.md to reflect native integration
    - Update docs/ to remove Fabric mentions

**Success Criteria:**
- ✅ No Fabric code in project
- ✅ No Fabric dependencies in build.gradle
- ✅ Single source set: `main`
- ✅ Single JAR output: `MattMC-1.21.10.jar`
- ✅ Direct launch via vanilla Main class
- ✅ No mods directory needed
- ✅ Build succeeds: `./gradlew build`
- ✅ Client launches and runs correctly
- ✅ Sodium and Iris work as native features

**Estimated Effort:** 8-12 hours

---

## Step 9: Simplify Build System to Single Source Set

**Objective:** Clean up build.gradle to reflect the new simplified architecture.

**Why This Step:** Makes build system maintainable and fast.

**Actions:**

1. **Final build.gradle cleanup:**
   ```groovy
   sourceSets {
       main {
           java {
               srcDir '.'
               
               // Global excludes
               exclude 'gradle/**'
               exclude 'build/**'
               exclude '.git/**'
               exclude '.idea/**'
               exclude 'frnsrc/**'
               exclude 'src/test/**'
               // REMOVED: exclude 'modules/**'
           }
           resources {
               srcDirs = ['src/main/resources']
           }
       }
       
       test {
           java {
               srcDirs = ['src/test']
           }
       }
   }
   ```

2. **Remove unused tasks:**
   ```bash
   # In build.gradle, search for and remove:
   # - fabricLoaderJar
   # - sodiumJar
   # - irisJar
   # - gameJar
   # - shaderPackZip (if not needed)
   ```

3. **Simplify run tasks:**
   ```groovy
   tasks.register('runClient', JavaExec) {
       group = 'minecraft'
       description = 'Runs the Minecraft client'
       
       dependsOn 'classes', 'copyJdkToRun'
       
       classpath = sourceSets.main.runtimeClasspath
       mainClass = 'net.minecraft.client.main.Main'
       
       workingDir = file('run')
       jvmArgs = clientJvmArgs
       
       args = [
           '--version', '1.21.10',
           '--accessToken', '0',
           '--gameDir', file('run').absolutePath,
           '--assetsDir', file('run/assets').absolutePath,
           '--assetIndex', '27'
       ]
       
       doFirst {
           workingDir.mkdirs()
           file('run/assets').mkdirs()
           file('run/shaderpacks').mkdirs()
       }
       
       configureBundledJdk(it)
   }
   ```

4. **Archive old modules:**
   ```bash
   # Create archive of module sources for reference
   tar -czf modules-archive.tar.gz modules/
   
   # Remove from project
   rm -rf modules/
   ```

5. **Update .gitignore:**
   ```gitignore
   # Remove Fabric-specific entries
   # Add if not already present:
   run/
   build/
   .gradle/
   *.jar
   !gradle/wrapper/gradle-wrapper.jar
   ```

**Success Criteria:**
- ✅ Clean, minimal build.gradle
- ✅ Only essential tasks remain
- ✅ Single source set compilation
- ✅ Fast incremental builds
- ✅ No modules directory
- ✅ Build time reduced by 30%+

**Estimated Effort:** 6-8 hours

---

## Step 10: Update Documentation to Reflect Native Integration

**Objective:** Rewrite all documentation to accurately describe the new architecture.

**Actions:**

1. **Update README.md:**
   ```markdown
   # MattMC
   
   > **A high-performance Minecraft 1.21.10 with native Sodium and Iris integration**
   
   ## Features
   
   ### Native Advanced Rendering (Sodium)
   MattMC includes Sodium's high-performance rendering engine as a **native feature**:
   - Optimized chunk rendering with parallel meshing
   - Advanced culling and occlusion optimization
   - Compact vertex formats for reduced memory bandwidth
   - 3-5x FPS improvement over vanilla
   
   **Not a mod** - fully integrated into the game engine.
   
   ### Native Shader Support (Iris)
   MattMC includes Iris shader pack support as a **native feature**:
   - OptiFine shader pack compatibility
   - Advanced rendering pipeline with shadow passes
   - Deferred rendering support
   - Post-processing effects
   
   **Not a mod** - built into the renderer like any other feature.
   
   ### No Mod Loader
   MattMC does not use Fabric, Forge, or any mod loader.
   All features are native and integrated.
   ```

2. **Update INTEGRATION.md:**
   ```markdown
   # Integration Architecture
   
   ## Overview
   
   MattMC has **fully integrated** Sodium and Iris into the base game.
   They are not mods - they are native rendering features.
   
   ## Package Structure
   
   ```
   net/minecraft/
   ├── client/
   │   ├── main/Main.java              (entry point)
   │   ├── Minecraft.java              (game class)
   │   └── renderer/
   │       ├── GameRenderer.java       (vanilla + Iris pipeline)
   │       ├── LevelRenderer.java      (vanilla + Sodium chunks)
   │       ├── sodium/                 (Sodium integration)
   │       │   ├── SodiumRenderer.java
   │       │   ├── chunk/
   │       │   ├── terrain/
   │       │   └── gl/
   │       └── iris/                   (Iris integration)
   │           ├── IrisShaderPipeline.java
   │           ├── pipeline/
   │           ├── shaderpack/
   │           └── uniforms/
   ```
   
   ## No Fabric Loader
   
   - No mod loading
   - No Knot launcher
   - No runtime mixins
   - No access widening
   - Direct source integration
   ```

3. **Create docs/SODIUM-INTEGRATION.md:**
   ```markdown
   # Sodium Integration Guide
   
   ## Architecture
   
   Sodium is integrated as a native rendering backend that can be toggled.
   
   ## Toggle Sodium
   
   ```java
   // Programmatically
   net.minecraft.client.renderer.sodium.SodiumRenderer.setEnabled(true/false);
   
   // Via options
   Options.sodiumUseAdvancedRenderer
   
   // In-game
   Video Settings → Use Advanced Renderer
   ```
   
   ## How It Works
   
   When enabled, Sodium replaces vanilla chunk rendering:
   
   1. LevelRenderer.renderChunks() checks if Sodium is enabled
   2. If yes, delegates to SodiumChunkRenderer
   3. If no, uses vanilla rendering
   
   Both paths always available - no mods to install/uninstall.
   ```

4. **Create docs/IRIS-INTEGRATION.md:**
   ```markdown
   # Iris Shader Integration Guide
   
   ## Architecture
   
   Iris is integrated as a native shader pipeline.
   
   ## Using Shaders
   
   1. Place shader packs in `run/shaderpacks/`
   2. Open Video Settings → Shaders
   3. Select shader pack
   4. Click "Shader Options" to configure
   
   ## How It Works
   
   When a shader pack is loaded:
   
   1. GameRenderer.render() checks if shaders are active
   2. If yes, runs shader pipeline (shadow → gbuffer → composite)
   3. If no, uses vanilla rendering
   
   Shaders can be toggled without restarting.
   ```

5. **Update build documentation:**
   - Document simplified build process
   - Remove references to mod JARs
   - Update task descriptions

**Success Criteria:**
- ✅ README.md updated
- ✅ INTEGRATION.md reflects current architecture
- ✅ New Sodium/Iris integration docs created
- ✅ Build documentation current
- ✅ No references to "mods" or "Fabric" as current architecture

**Estimated Effort:** 8-12 hours

---

## Step 11: Performance Validation and Optimization

**Objective:** Verify native integration equals or exceeds modded performance.

**Actions:**

1. **Create benchmark suite:**
   ```java
   // In: src/test/java/benchmarks/NativeIntegrationBenchmark.java
   
   public class NativeIntegrationBenchmark {
       @Test
       public void benchmarkVanillaRendering() {
           SodiumRenderer.setEnabled(false);
           measureFPS(60); // 60 seconds
       }
       
       @Test
       public void benchmarkSodiumRendering() {
           SodiumRenderer.setEnabled(true);
           measureFPS(60);
       }
       
       @Test
       public void benchmarkShadersDisabled() {
           IrisShaderPipeline.setEnabled(false);
           measureFPS(60);
       }
       
       @Test
       public void benchmarkShadersEnabled() {
           IrisShaderPipeline.loadShaderPack("ComplementaryHungLoIfied.zip");
           IrisShaderPipeline.setEnabled(true);
           measureFPS(60);
       }
   }
   ```

2. **Measure key metrics:**
   ```
   Metric                    | Before (Mods) | After (Native) | Change
   ------------------------- | ------------- | -------------- | ------
   Startup time              | 15.2s         | TBD            | TBD
   FPS (vanilla)             | 60 FPS        | TBD            | TBD
   FPS (Sodium)              | 180 FPS       | TBD            | TBD
   FPS (Sodium + shaders)    | 120 FPS       | TBD            | TBD
   Memory (baseline)         | 1.2 GB        | TBD            | TBD
   Build time                | 110s          | TBD            | TBD
   JAR size                  | 150 MB total  | TBD            | TBD
   ```

3. **Profile with JFR:**
   ```bash
   # Run with Java Flight Recorder
   ./gradlew runClient -Djava.flight.recorder=true
   
   # Analyze:
   # - CPU hotspots
   # - Memory allocation
   # - GC behavior
   # - Method call counts
   ```

4. **Expected improvements:**
   - **Startup time**: 2-3s faster (no mod loading)
   - **FPS**: Equal or better (no transformation overhead)
   - **Memory**: 100-200 MB less (no duplicate class loading)
   - **Build time**: 30-40% faster (single compilation)

5. **Create performance report:**
   
   Create `docs/PERFORMANCE-VALIDATION.md`

**Success Criteria:**
- ✅ Startup time equal or better
- ✅ FPS within 5% or better
- ✅ Memory usage equal or less
- ✅ Build time significantly improved
- ✅ Comprehensive performance documentation

**Estimated Effort:** 10-14 hours

---

## Step 12: Final Polish and User Experience

**Objective:** Professional polish, user-friendly features, and complete integration.

**Actions:**

1. **Add feature discovery:**
   ```java
   // Show tip on first launch
   if (isFirstLaunch()) {
       showInfoScreen(
           "Welcome to MattMC!",
           "This version includes:\n" +
           "• Sodium - High-performance rendering\n" +
           "• Iris - Shader pack support\n\n" +
           "Check Video Settings → Shaders to get started!"
       );
   }
   ```

2. **Add Sodium/Iris branding in F3 debug:**
   ```java
   // In: net/minecraft/client/gui/components/DebugScreenOverlay.java
   
   protected List<String> getGameInformation() {
       List<String> list = Lists.newArrayList();
       list.add("Minecraft 1.21.10 (MattMC)");
       list.add("");
       
       // Add renderer info
       if (SodiumRenderer.isEnabled()) {
           list.add("§aSodium Renderer: Enabled");
           list.add("Chunk Renderer: " + SodiumRenderer.getVersion());
       } else {
           list.add("Renderer: Vanilla");
       }
       
       if (IrisShaderPipeline.isEnabled()) {
           list.add("§bShaders: " + IrisShaderPipeline.getCurrentPack());
       }
       
       // ... rest of debug info
   }
   ```

3. **Add visual indicator for active features:**
   ```java
   // In main menu, show active rendering features
   // Small icons or text: "Sodium Enabled" "Shaders: Complementary"
   ```

4. **Create user guide:**
   
   Create `docs/USER-GUIDE.md`:
   ```markdown
   # MattMC User Guide
   
   ## Getting Better Performance
   
   MattMC includes Sodium rendering for much better FPS:
   
   1. Open Video Settings
   2. Ensure "Use Advanced Renderer" is ON
   3. Adjust "Chunk Builder Threads" based on your CPU
   4. Enjoy 3-5x better FPS!
   
   ## Using Shader Packs
   
   MattMC includes Iris for shader pack support:
   
   1. Download shader pack (e.g., Complementary, BSL, SEUS)
   2. Place ZIP file in: run/shaderpacks/
   3. Open Video Settings → Shaders
   4. Select your shader pack
   5. Click Apply
   
   ## Troubleshooting
   
   ### Low FPS
   - Disable shaders temporarily
   - Lower render distance
   - Reduce chunk builder threads
   
   ### Shader Issues
   - Try different shader pack
   - Check compatibility
   - Disable Sodium (if conflict)
   ```

5. **Add configuration presets:**
   ```java
   // Quick settings presets
   public enum PerformancePreset {
       POTATO,        // Maximum FPS, minimal quality
       BALANCED,      // Default settings
       QUALITY,       // Best visuals, lower FPS
       SCREENSHOTS    // Maximum quality for screenshots
   }
   ```

6. **Error messages and logging:**
   ```java
   // Clear, helpful error messages
   if (sodiumInitFailed) {
       LOGGER.error("Sodium initialization failed. Falling back to vanilla rendering.");
       LOGGER.error("Check graphics drivers are up to date.");
       
       // Show user-friendly message
       showErrorDialog(
           "Advanced Rendering Unavailable",
           "Sodium could not initialize.\n" +
           "Vanilla rendering will be used instead.\n\n" +
           "This may be due to outdated graphics drivers."
       );
   }
   ```

**Success Criteria:**
- ✅ Clear feature visibility
- ✅ User-friendly configuration
- ✅ Helpful error messages
- ✅ Comprehensive user guide
- ✅ Professional polish
- ✅ Positive user experience

**Estimated Effort:** 12-16 hours

---

## Summary and Timeline

### All 12 Steps

| Step | Description | Effort | Cumulative |
|------|-------------|--------|------------|
| 1 | Apply Access Wideners Permanently | 10-14h | 10-14h |
| 2 | Inline Sodium Mixins | 30-40h | 40-54h |
| 3 | Inline Iris Mixins | 35-45h | 75-99h |
| 4 | Move Sodium to Main Source | 16-20h | 91-119h |
| 5 | Move Iris to Main Source | 18-24h | 109-143h |
| 6 | Merge Configurations | 10-12h | 119-155h |
| 7 | Unified Video Settings UI | 14-18h | 133-173h |
| 8 | Remove Fabric Loader Entirely | 8-12h | 141-185h |
| 9 | Simplify Build System | 6-8h | 147-193h |
| 10 | Update Documentation | 8-12h | 155-205h |
| 11 | Performance Validation | 10-14h | 165-219h |
| 12 | Final Polish | 12-16h | 177-235h |

**Total Estimated Effort: 177-235 hours** (4-6 weeks full-time)

### Critical Path

**Phase 1: Remove Fabric Dependencies (Steps 1-3)**
- Apply access wideners
- Inline all mixins
- **Milestone:** No runtime Fabric dependencies

**Phase 2: Integrate Source Code (Steps 4-5)**
- Move Sodium to main source
- Move Iris to main source
- **Milestone:** Single source set, no modules

**Phase 3: User Experience (Steps 6-7)**
- Merge configurations
- Unified UI
- **Milestone:** Native user experience

**Phase 4: Complete Removal (Steps 8-9)**
- Delete Fabric Loader
- Simplify build
- **Milestone:** No Fabric code remains

**Phase 5: Polish (Steps 10-12)**
- Documentation
- Performance
- UX polish
- **Milestone:** Production ready

### Key Success Factors

1. **Test After Each Step**: `./gradlew build && ./gradlew runClient`
2. **Incremental Commits**: Commit after each major change
3. **Performance Tracking**: Monitor FPS, startup time throughout
4. **Backup**: Keep module backups until fully validated
5. **Documentation**: Update docs as you go

### Expected Benefits

**Technical Benefits:**
- ✅ No mod loader overhead
- ✅ No runtime bytecode transformation
- ✅ Direct method calls (JIT optimization)
- ✅ Simpler architecture
- ✅ Faster builds (30-40% improvement)
- ✅ Smaller distribution (~30% smaller)

**User Benefits:**
- ✅ Faster startup (2-3s improvement)
- ✅ Native features, no mod installation
- ✅ Single configuration file
- ✅ Cohesive UI experience
- ✅ Professional polish

**Maintenance Benefits:**
- ✅ Clearer codebase
- ✅ Easier debugging
- ✅ Better IDE support
- ✅ Simpler dependency management
- ✅ More maintainable long-term

---

## Getting Started

### Recommended Order

1. **Start with Step 1** (Access Wideners)
   - Lowest risk
   - Good learning experience
   - Enables later steps

2. **Then Step 2** (Sodium Mixins)
   - Can do incrementally (10-20 mixins at a time)
   - Test frequently
   - High impact

3. **Continue sequentially through Step 12**

### Daily Workflow

```bash
# Morning: Pick next step/task
# Work on integration
# Test frequently

# Build and test
./gradlew clean build
./gradlew runClient

# If everything works
git add .
git commit -m "Step X: [description]"
git push

# If issues arise
# Debug, fix, re-test
# Don't proceed until working

# End of day: Update tracking doc
```

### Tracking Progress

Create a GitHub Project or tracking doc with checkboxes:

```markdown
## Integration Progress

### Phase 1: Remove Fabric Dependencies
- [ ] Step 1: Apply Access Wideners
  - [ ] Parse all widener files
  - [ ] Apply to LevelRenderer
  - [ ] Apply to GameRenderer
  - [ ] ... etc
- [ ] Step 2: Inline Sodium Mixins
  - [ ] Create toggle system
  - [ ] Inline LevelRendererMixin
  - [ ] Inline GameRendererMixin
  - [ ] ... etc
```

---

## Conclusion

This cleanup plan provides a **complete roadmap** to transform MattMC from a modded architecture to a **native, integrated engine** with Sodium and Iris as first-class features.

The end result:
- **No Fabric Loader**
- **No mod loading**
- **No runtime transformation**
- **Native Sodium rendering**
- **Native shader support**
- **Single, unified codebase**

Each step builds on the previous, ensuring the project always builds and runs. By the end, MattMC will be a professional, polished Minecraft implementation with advanced rendering capabilities built directly into the engine.

Good luck with the integration! 🚀
