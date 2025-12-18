# MattMC Integration Cleanup Plan

## Overview

This document outlines actionable steps to further integrate Iris, Sodium, and Fabric into the MattMC base project. The current architecture treats these components as separate modules with their own build processes, JAR artifacts, and runtime loading mechanisms. This cleanup plan provides incremental, focused steps to deepen their integration while ensuring each step builds successfully and maintains full functionality.

**Current State:**
- Fabric Loader: Separate source set → `fabric-loader-0.18.2.jar` → Knot launcher
- Sodium: Separate source set → `sodium-0.7.2-mc1.21.10.jar` → Loaded as mod at runtime
- Iris: Separate source set → `iris-1.9.6-mc1.21.10.jar` → Loaded as mod at runtime

**Target State:**
- Progressively tighter integration while maintaining buildability
- Reduced runtime overhead from mod loading
- More maintainable, unified codebase
- Better visibility into advanced rendering features

---

## Actionable Integration Items

### 1. Consolidate Fabric API Stubs Into Main Source Set

**Objective:** Move Fabric API stub implementations from scattered locations into a well-organized, documented part of the main source set.

**Current Issue:**
- Fabric API stubs exist in `src/main/java/net/fabricmc/fabric/api/` 
- These stubs are minimal implementations to satisfy Sodium/Iris dependencies
- They're not clearly documented as "stubs for mod compatibility"

**Actions:**
1. Create `net.minecraft.fabric.api` package in main source set as the canonical location
2. Move all Fabric API stubs from `net.fabricmc.fabric.api.*` to `net.minecraft.fabric.api.*`
3. Add comprehensive JavaDoc explaining each stub's purpose and relationship to full Fabric API
4. Create a mapping/forwarding layer so existing Sodium/Iris code continues to work
5. Update imports in Sodium and Iris modules to reference the new canonical location
6. Document in `docs/FABRIC-API-STUBS.md` which APIs are stubbed and their limitations

**Success Criteria:**
- ✅ All Fabric API stubs in one well-documented location
- ✅ Gradle build completes successfully
- ✅ Sodium and Iris compile without errors
- ✅ Client runs with Sodium and Iris working correctly
- ✅ No runtime ClassNotFoundException or NoSuchMethodError

**Estimated Effort:** 4-6 hours

**Rationale:** This step provides better code organization and documentation without changing behavior. It's a pure refactoring that sets the stage for deeper integration.

---

### 2. Unify Access Widener Applications Into Permanent Source Changes

**Objective:** Replace runtime access widening with permanent source code visibility changes, documenting why each field/method is public.

**Current Issue:**
- Sodium uses `sodium-common.accesswidener` and `sodium-fabric.accesswidener` (18+ declarations)
- Iris uses `iris.accesswidener` (extensive declarations)
- These are applied at runtime by Fabric Loader's access widening system
- Creates dependency on Fabric's runtime transformation

**Actions:**
1. Parse all access widener files to extract declarations:
   ```
   accessible class net/minecraft/client/renderer/LevelRenderer$RenderChunkInfo
   accessible field net/minecraft/client/renderer/LevelRenderer capturedFrustum Lnet/minecraft/client/renderer/culling/Frustum;
   accessible method net/minecraft/client/renderer/GameRenderer renderDistance ()I
   ```

2. For each declaration, locate the corresponding source file and modify visibility:
   ```java
   // Before (private)
   private Frustum capturedFrustum;
   
   // After (public with documentation)
   /**
    * The frustum used for culling during the previous frame.
    * @apiNote Exposed for advanced rendering systems (Sodium chunk culling)
    */
   public Frustum capturedFrustum;
   ```

3. Create `docs/ACCESS-WIDENING-LOG.md` documenting all changes:
   - Original visibility
   - New visibility
   - Reason for widening
   - Which component requires it (Sodium/Iris)

4. Remove access widener processing from build.gradle

5. Remove access widener files from modules

**Success Criteria:**
- ✅ All previously widened members are now directly accessible
- ✅ Sodium and Iris compile without access widener processing
- ✅ Full documentation of exposed internals
- ✅ Runtime behavior identical to before
- ✅ Client runs with all features working

**Estimated Effort:** 8-12 hours

**Rationale:** Makes API exposure explicit and permanent, removing runtime transformation overhead. Enables better compile-time checking and IDE support.

---

### 3. Merge Fabric Loader Source Into Main Source Set

**Objective:** Eliminate the separate `fabricLoader` source set and integrate Fabric Loader code directly into the main source set.

**Current Issue:**
- Fabric Loader has its own source set: `modules/fabric-loader-0.18.2/`
- Compiled to separate JAR: `fabric-loader-0.18.2.jar`
- Creates artificial boundary between Minecraft and Fabric code
- Complicates dependency management

**Actions:**
1. Create new package structure in main source set:
   ```
   net.minecraft.fabricloader/
   ├── api/           # Public Fabric Loader APIs
   ├── impl/          # Implementation details
   ├── launch/        # KnotClient and launch infrastructure
   ├── mixin/         # Mixin support
   └── util/          # Utilities
   ```

2. Copy Fabric Loader sources from `modules/fabric-loader-0.18.2/src/main/java/` to new location

3. Update package declarations in moved files to new package names

4. Update imports throughout codebase (Fabric Loader, Sodium, Iris)

5. Update `build.gradle`:
   - Remove `fabricLoader` source set definition
   - Remove `fabricLoaderJar` task
   - Remove separate classpath dependencies
   - Ensure Fabric Loader dependencies are in main `implementation`

6. Update `jar` task to no longer combine multiple source sets

7. Update `runClient` task to use single JAR instead of separate fabric-loader.jar + game.jar

**Success Criteria:**
- ✅ Only `main` source set contains Fabric Loader code
- ✅ Single JAR output contains both Minecraft and Fabric Loader
- ✅ Gradle build completes successfully
- ✅ Client launches and runs correctly
- ✅ Sodium and Iris mods still load and function

**Estimated Effort:** 12-16 hours

**Rationale:** Simplifies build system, reduces JAR count, improves build times through unified compilation. First major integration step toward treating Fabric as part of the engine rather than a separate loader.

---

### 4. Create Unified Mod Initialization System

**Objective:** Replace Fabric's dynamic mod discovery and entrypoint system with a hardcoded initialization system for Sodium and Iris.

**Current Issue:**
- Fabric Loader scans `run/mods/` for JAR files at runtime
- Mod metadata parsed from `fabric.mod.json` in each JAR
- Entrypoints discovered and invoked dynamically
- Unnecessary overhead for known, built-in mods

**Actions:**
1. Create `net.minecraft.mods.BuiltInMods` class:
   ```java
   public class BuiltInMods {
       public static void initializePreLaunch() {
           // Sodium pre-launch
           SodiumPreLaunch.onPreLaunch();
       }
       
       public static void initializeClient() {
           // Sodium client init
           SodiumFabricMod sodiumMod = new SodiumFabricMod();
           sodiumMod.onInitializeClient();
           
           // Iris has no explicit entrypoint, relies on mixins
           // No init needed, mixins already applied
       }
   }
   ```

2. Modify Fabric Loader's `KnotClient` to skip mod discovery when in "built-in mode":
   ```java
   if (BUILT_IN_MODS_MODE) {
       // Skip JAR scanning
       // Skip mod metadata parsing
       // Skip dynamic entrypoint discovery
       BuiltInMods.initializePreLaunch();
       BuiltInMods.initializeClient();
   }
   ```

3. Create configuration flag in `gradle.properties`:
   ```properties
   minecraft.builtInMods=true
   ```

4. Update `runClient` task to set system property:
   ```groovy
   jvmArgs += ['-Dminecraft.builtInMods=true']
   ```

5. Keep mod JAR building tasks for now (future cleanup), but they won't be loaded at runtime

**Success Criteria:**
- ✅ No mod directory scanning at runtime
- ✅ Sodium and Iris initialize through hardcoded paths
- ✅ Faster startup (no JAR scanning overhead)
- ✅ Same functionality as dynamic loading
- ✅ Client runs with all mods working

**Estimated Effort:** 6-10 hours

**Rationale:** Eliminates runtime discovery overhead for mods we know are always present. Reduces startup time and complexity while maintaining the same initialization flow.

---

### 5. Inline Critical Sodium Mixins Into Minecraft Source

**Objective:** Apply high-impact Sodium mixins directly to Minecraft source code, eliminating runtime bytecode transformation for the most critical rendering paths.

**Current Issue:**
- Sodium uses ~120 mixin classes for runtime bytecode transformation
- Mixins modify critical rendering methods in `LevelRenderer`, `GameRenderer`, `ChunkRenderer`, etc.
- Runtime overhead from ASM transformation
- Harder to debug (transformed bytecode vs source code)

**Actions:**
1. Identify top 10 highest-impact Sodium mixins (by CPU time / importance):
   - `LevelRendererMixin` - Terrain rendering replacement
   - `GameRendererMixin` - Frame rendering hooks
   - `ChunkRenderCacheMixin` - Chunk meshing
   - `BlockRenderDispatcherMixin` - Block rendering
   - `MinecraftClientMixin` - Client lifecycle hooks

2. For each mixin, manually apply the transformation to target class:
   
   **Example: LevelRendererMixin**
   ```java
   // In LevelRenderer.java
   
   // Add Sodium renderer field
   @Nullable
   private SodiumWorldRenderer sodiumRenderer;
   
   // Modify renderChunks method
   public void renderChunks(Camera camera, Frustum frustum, boolean spectator) {
       // ===== SODIUM INTEGRATION (inline from LevelRendererMixin) =====
       if (SodiumConfig.isEnabled() && this.sodiumRenderer != null) {
           this.sodiumRenderer.renderChunks(camera, frustum, spectator);
           return;
       }
       // ===== END SODIUM INTEGRATION =====
       
       // Original vanilla rendering code preserved below
       // ... existing vanilla chunk rendering ...
   }
   ```

3. Add feature toggle system:
   ```java
   public class SodiumConfig {
       private static boolean enabled = true;
       
       public static boolean isEnabled() {
           return enabled;
       }
       
       public static void setEnabled(boolean enabled) {
           SodiumConfig.enabled = enabled;
       }
   }
   ```

4. Remove inlined mixins from `sodium-common.mixins.json` and `sodium-fabric.mixins.json`

5. Mark inlined code with clear comments for maintainability

6. Create `docs/SODIUM-INTEGRATION-LOG.md` tracking which mixins are inlined

**Success Criteria:**
- ✅ Top 10 Sodium mixins inlined into source
- ✅ Vanilla rendering path still works (when Sodium disabled)
- ✅ Sodium rendering works identically to before
- ✅ Performance metrics unchanged (FPS, frame time)
- ✅ Client builds and runs successfully
- ✅ Toggle between vanilla and Sodium rendering works

**Estimated Effort:** 20-30 hours (complex, requires careful testing)

**Rationale:** Eliminates runtime transformation overhead for hottest code paths. Makes Sodium integration explicit and easier to debug. Preserves vanilla functionality as fallback.

---

### 6. Merge Sodium Configuration Into Minecraft Options

**Objective:** Integrate Sodium's configuration system into Minecraft's native `Options` class and configuration file.

**Current Issue:**
- Sodium maintains separate configuration: `run/config/sodium-options.json`
- Separate options screen and UI
- Users have to manage two configuration files
- Sodium options not accessible through vanilla options API

**Actions:**
1. Analyze Sodium's configuration structure:
   ```json
   {
     "quality": {
       "weather_quality": "DEFAULT",
       "leaves_quality": "DEFAULT",
       "enable_vignette": true
     },
     "performance": {
       "chunk_builder_threads": 0,
       "always_defer_chunk_updates": false,
       "use_block_face_culling": true
     },
     "advanced": {
       "arena_memory_allocator": "ASYNC",
       "use_advanced_staging_buffers": true
     }
   }
   ```

2. Add corresponding options to Minecraft's `Options` class:
   ```java
   public class Options {
       // Existing vanilla options...
       
       // Sodium rendering options
       public OptionInstance<WeatherQuality> sodiumWeatherQuality;
       public OptionInstance<LeavesQuality> sodiumLeavesQuality;
       public OptionInstance<Boolean> sodiumEnableVignette;
       public OptionInstance<Integer> sodiumChunkBuilderThreads;
       public OptionInstance<Boolean> sodiumAlwaysDeferChunkUpdates;
       public OptionInstance<Boolean> sodiumUseBlockFaceCulling;
       // ... etc
   }
   ```

3. Create migration logic in `Options` constructor:
   ```java
   // Read old sodium-options.json if it exists
   // Populate new fields
   // Write to options.txt
   // Delete old sodium-options.json (after backup)
   ```

4. Update Sodium code to read from `Options` instead of `SodiumGameOptions`

5. Keep Sodium's options screen UI but wire it to `Options` instead of separate config

6. Update `options.txt` file format version

**Success Criteria:**
- ✅ Sodium options stored in `options.txt`
- ✅ Existing Sodium configurations migrated automatically
- ✅ Sodium options screen still works
- ✅ All Sodium settings accessible through vanilla Options API
- ✅ No separate `sodium-options.json` file created
- ✅ Client runs with settings preserved

**Estimated Effort:** 8-12 hours

**Rationale:** Provides unified configuration management. Simplifies user experience. Makes Sodium options accessible to other code without coupling.

---

### 7. Integrate Iris Shader Pipeline Into GameRenderer

**Objective:** Make Iris shader pipeline a first-class citizen in Minecraft's GameRenderer, with native shader pack support.

**Current Issue:**
- Iris injects shader passes through mixins
- Shader pack loading is separate from resource pack loading
- Shader pipeline feels like an addon, not a native feature

**Actions:**
1. Add shader pipeline field to `GameRenderer`:
   ```java
   public class GameRenderer {
       @Nullable
       private IrisShaderPipeline shaderPipeline;
       
       public void loadShaderPack(ResourceLocation shaderPackId) {
           // Load shader pack
           // Initialize pipeline
       }
       
       public void unloadShaderPack() {
           // Cleanup current pipeline
       }
   }
   ```

2. Modify `GameRenderer.render()` to natively support shader passes:
   ```java
   public void render(float partialTicks, long nanoTime, boolean renderWorld) {
       // ===== IRIS SHADER INTEGRATION =====
       if (this.shaderPipeline != null && this.shaderPipeline.isActive()) {
           // Shadow pass
           this.shaderPipeline.beginShadowPass();
           renderShadowPass(partialTicks);
           this.shaderPipeline.endShadowPass();
           
           // GBuffer pass
           this.shaderPipeline.beginGBufferPass();
           renderWorldNormally(partialTicks);
           this.shaderPipeline.endGBufferPass();
           
           // Composite passes
           this.shaderPipeline.runCompositePasses();
       } else {
           // Vanilla rendering
           renderWorldNormally(partialTicks);
       }
       // ===== END IRIS SHADER INTEGRATION =====
   }
   ```

3. Add shader pack management to resource reload:
   ```java
   // In ResourceManager reload
   if (shaderPacksChanged) {
       this.minecraft.gameRenderer.reloadShaderPipeline();
   }
   ```

4. Create `docs/SHADER-PIPELINE.md` documenting the native shader system

5. Remove corresponding Iris mixins that are now inline

**Success Criteria:**
- ✅ Shader pipeline directly integrated in GameRenderer
- ✅ Shader packs load and render correctly
- ✅ Vanilla rendering path works when no shader pack loaded
- ✅ Multiple shader packs can be tested
- ✅ Visual output identical to mixin-based Iris
- ✅ Performance unchanged

**Estimated Effort:** 16-24 hours

**Rationale:** Makes shader support feel like a native Minecraft feature. Improves debugging and maintainability. Sets foundation for future shader enhancements.

---

### 8. Consolidate Sodium and Iris Source Sets Into Main

**Objective:** Move Sodium and Iris source code from separate modules into the main source set, eliminating separate compilation.

**Current Issue:**
- Sodium source: `modules/sodium-1.21.9/` (581 Java files)
- Iris source: `modules/Iris-1.21.9/` (725 Java files)
- Compiled to separate source sets and JARs
- Complex build dependency management

**Actions:**
1. Create organized package structure in main source set:
   ```
   net.minecraft.sodium/
   ├── api/              # Sodium public API
   ├── render/           # Rendering implementations
   │   ├── chunk/        # Chunk rendering
   │   ├── terrain/      # Terrain meshing
   │   └── gl/           # OpenGL abstractions
   ├── config/           # Configuration
   └── util/             # Utilities
   
   net.minecraft.iris/
   ├── api/              # Iris public API  
   ├── pipeline/         # Shader pipeline
   ├── shaderpack/       # Shader pack loading
   ├── uniforms/         # Shader uniforms
   └── compat/           # Sodium compatibility
   ```

2. Move source files from modules to new locations:
   ```bash
   # Sodium
   cp -r modules/sodium-1.21.9/common/src/main/java/* src/main/java/
   cp -r modules/sodium-1.21.9/fabric/src/main/java/* src/main/java/
   
   # Iris  
   cp -r modules/Iris-1.21.9/common/src/main/java/* src/main/java/
   cp -r modules/Iris-1.21.9/fabric/src/main/java/* src/main/java/
   ```

3. Update package declarations to new structure:
   ```java
   // Before
   package net.caffeinemc.mods.sodium.client.render.chunk;
   
   // After
   package net.minecraft.sodium.render.chunk;
   ```

4. Update all imports throughout codebase

5. Update `build.gradle`:
   - Remove `sodium` source set
   - Remove `iris` source set
   - Remove `sodiumJar` and `irisJar` tasks
   - Move resource files to main resources
   - Update mixin configs to reference new package structure

6. Update mixin configuration files to new package paths

7. Remove modules directory (after backup)

**Success Criteria:**
- ✅ All Sodium and Iris code in main source set
- ✅ Single compilation phase for entire project
- ✅ Gradle build completes successfully
- ✅ All 1,505 Java files (581 Sodium + 725 Iris + 199 Fabric) compile
- ✅ Client runs with full functionality
- ✅ Faster incremental builds

**Estimated Effort:** 16-24 hours

**Rationale:** Major simplification of build system. Enables better IDE support and refactoring. Treats Sodium and Iris as integral parts of the engine, not external mods.

---

### 9. Replace Fabric Loader's Knot Launcher With Native Bootstrap

**Objective:** Remove dependency on Fabric's Knot launcher and custom class loader, using standard Java application startup.

**Current Issue:**
- Client launches via `KnotClient` main class
- Custom `KnotClassLoader` for class loading and transformation
- Complex classpath setup with separate JARs
- Overhead from custom class loading

**Actions:**
1. Create `net.minecraft.bootstrap.MinecraftBootstrap` class:
   ```java
   public class MinecraftBootstrap {
       public static void main(String[] args) {
           // Early initialization
           initializeLogging();
           initializeMixins(); // If keeping mixins
           
           // Initialize built-in mods
           BuiltInMods.initializePreLaunch();
           
           // Launch Minecraft client
           net.minecraft.client.main.Main.main(args);
       }
       
       private static void initializeLogging() {
           // Setup Log4j2
       }
       
       private static void initializeMixins() {
           // If keeping Mixin support, initialize here
           // Otherwise remove this method
       }
   }
   ```

2. Update `build.gradle`:
   ```groovy
   jar {
       manifest {
           attributes(
               'Main-Class': 'net.minecraft.bootstrap.MinecraftBootstrap'
           )
       }
   }
   
   tasks.named('runClient') {
       mainClass = 'net.minecraft.bootstrap.MinecraftBootstrap'
       // Remove Fabric-specific JVM args
       jvmArgs = clientJvmArgs // No more -Dfabric.*
   }
   ```

3. Simplify classpath setup:
   ```groovy
   // Remove separate JAR setup
   classpath = sourceSets.main.runtimeClasspath
   ```

4. Update launch scripts in `packaging/` directory

5. Remove `KnotClassLoader` usage (if not needed for mixins)

6. Test all launch paths:
   - Gradle runClient
   - Fat JAR execution  
   - Distribution package

**Success Criteria:**
- ✅ Client launches with standard Java main class
- ✅ No custom class loader (unless needed for remaining mixins)
- ✅ Simpler classpath configuration
- ✅ All functionality preserved
- ✅ Faster startup time
- ✅ Standard Java application structure

**Estimated Effort:** 12-16 hours

**Rationale:** Removes complexity from Fabric's custom launcher. Makes MattMC a more standard Java application. Simplifies debugging and tooling integration.

---

### 10. Optimize Mixin Usage: Inline Most, Keep Only Essentials

**Objective:** Minimize runtime mixin transformations by inlining most mixins into source, keeping only those necessary for Sodium-Iris compatibility.

**Current Issue:**
- ~120 Sodium mixins
- ~200+ Iris mixins  
- Runtime bytecode transformation overhead
- Makes code harder to debug and maintain

**Actions:**
1. Categorize all mixins by necessity:
   - **Category A**: Can be inlined (majority) - apply to source code
   - **Category B**: Sodium-Iris compatibility mixins - keep for now
   - **Category C**: Runtime hooks that need transformation - keep minimal set

2. Inline Category A mixins (building on Item 5):
   - Additional Sodium mixins beyond top 10
   - Iris mixins that modify vanilla classes
   - Document each inlining in code comments

3. Keep Category B mixins:
   - Iris compatibility mixins for Sodium classes
   - These modify Sodium's code to add Iris hooks
   - Eventually can be inlined once Sodium code is in main source set

4. Keep minimal Category C mixins:
   - Dynamic hooks that truly need runtime transformation
   - Example: Optional compatibility with other mods (if applicable)

5. Update mixin configuration files to include only remaining mixins

6. Consider compile-time mixin application as alternative:
   - Use Gradle task to apply remaining mixins at build time
   - Generate transformed classes during compilation
   - No runtime transformation needed

7. Create `docs/MIXIN-REDUCTION-LOG.md` tracking the reduction:
   - Original count: ~320 mixins
   - Inlined: ~280 mixins
   - Remaining: ~40 mixins (Sodium-Iris compat)
   - Plan to reduce further: ~0 mixins (eventual goal)

**Success Criteria:**
- ✅ <50 runtime mixins remaining (from ~320)
- ✅ All inlined code documented
- ✅ Build completes successfully
- ✅ Client runs with full functionality
- ✅ Performance improvement from reduced transformation overhead
- ✅ Easier debugging (less transformed code)

**Estimated Effort:** 30-40 hours (large undertaking, incremental progress)

**Rationale:** Massive reduction in runtime complexity. Makes codebase more maintainable. Improves performance by eliminating transformation overhead. Sets path to eventually removing Mixin dependency entirely.

---

### 11. Create Unified Advanced Rendering API

**Objective:** Design and implement a clean, well-documented API that exposes Sodium and Iris capabilities as first-class Minecraft features.

**Current Issue:**
- Sodium and Iris APIs scattered across many packages
- Not clearly distinguished from implementation details
- No unified entry point for advanced rendering features

**Actions:**
1. Create new package structure:
   ```
   net.minecraft.client.renderer.advanced/
   ├── AdvancedRenderer.java         # Main entry point
   ├── api/
   │   ├── ChunkRenderAPI.java       # Sodium chunk rendering
   │   ├── ShaderAPI.java            # Iris shader system
   │   ├── RenderCapabilities.java   # Feature detection
   │   └── RenderSettings.java       # Unified settings
   ├── chunk/                        # Sodium implementation
   ├── shaders/                      # Iris implementation
   └── util/
   ```

2. Design `AdvancedRenderer` as unified entry point:
   ```java
   /**
    * Advanced rendering features including optimized chunk rendering
    * and shader pack support.
    * 
    * This API provides access to features from Sodium (chunk optimization)
    * and Iris (shader packs).
    */
   public class AdvancedRenderer {
       private final ChunkRenderAPI chunkRenderer;
       private final ShaderAPI shaderSystem;
       
       public static AdvancedRenderer getInstance() { ... }
       
       public ChunkRenderAPI getChunkRenderer() { ... }
       public ShaderAPI getShaderSystem() { ... }
       public RenderCapabilities getCapabilities() { ... }
       public RenderSettings getSettings() { ... }
   }
   ```

3. Create comprehensive JavaDoc:
   - Document all public APIs
   - Explain Sodium vs Iris features
   - Provide usage examples
   - Document performance implications

4. Create `docs/ADVANCED-RENDERING-API.md`:
   - API overview
   - Usage guide
   - Feature comparison (Sodium vs vanilla, Iris vs vanilla)
   - Performance tuning guide

5. Refactor existing code to use new API:
   - Update options screens
   - Update settings persistence
   - Update renderer initialization

6. Add API version tracking:
   ```java
   public class AdvancedRenderer {
       public static final String API_VERSION = "1.0.0";
       public static final String SODIUM_VERSION = "0.7.2";
       public static final String IRIS_VERSION = "1.9.6";
   }
   ```

**Success Criteria:**
- ✅ Clean, well-documented API
- ✅ All advanced rendering features accessible through API
- ✅ Usage examples in documentation
- ✅ Existing code refactored to use new API
- ✅ Client builds and runs successfully
- ✅ No behavioral changes, only API reorganization

**Estimated Effort:** 12-16 hours

**Rationale:** Provides clean interface to advanced features. Makes it clear what's part of the "advanced rendering system". Enables future enhancements and extensions. Documents capabilities for users and developers.

---

### 12. Integrate Sodium and Iris Options Screens Into Video Settings

**Objective:** Merge Sodium and Iris options screens into Minecraft's native video settings, creating a cohesive user experience.

**Current Issue:**
- Sodium replaces entire video settings screen
- Iris adds separate shader pack selection screen
- Inconsistent UI between vanilla and mod options
- Fragmented user experience

**Actions:**
1. Analyze current screen hierarchy:
   ```
   Video Settings (Sodium replacement)
   ├── Quality
   ├── Performance  
   ├── Advanced
   └── (Vanilla options missing)
   
   Shader Packs (Separate screen)
   ```

2. Design unified hierarchy:
   ```
   Video Settings (Native Minecraft)
   ├── Graphics (vanilla)
   ├── Quality (vanilla + Sodium)
   ├── Performance (vanilla + Sodium)
   ├── Advanced Rendering... (new button)
   │   ├── Chunk Rendering (Sodium)
   │   ├── Terrain Quality (Sodium)
   │   ├── Shader Packs... (Iris)
   │   └── Shader Settings (Iris)
   └── Details (vanilla)
   ```

3. Create `AdvancedRenderingScreen` class:
   ```java
   public class AdvancedRenderingScreen extends Screen {
       // Tabbed interface:
       // - Chunk Rendering (Sodium options)
       // - Shader Packs (Iris options)
       // - Performance (mixed Sodium/Iris)
   }
   ```

4. Modify `VideoSettingsScreen` to add "Advanced Rendering..." button:
   ```java
   this.addRenderableWidget(Button.builder(
       Component.translatable("options.video.advanced_rendering"),
       button -> this.minecraft.setScreen(new AdvancedRenderingScreen(this))
   ).build());
   ```

5. Adapt Sodium's widgets to Minecraft's native widget system:
   - Convert Sodium's custom widgets to vanilla equivalents
   - Maintain same functionality with native look and feel

6. Integrate Iris shader pack selector:
   - Adapt shader pack list to vanilla screen style
   - Add preview thumbnails
   - Add apply/cancel buttons

7. Add tooltips and help text:
   - Explain each option
   - Show performance impact indicators
   - Link to documentation

**Success Criteria:**
- ✅ All video options in one cohesive screen
- ✅ Consistent UI style across vanilla and advanced options
- ✅ Easy navigation between option categories
- ✅ All functionality from separate screens preserved
- ✅ User-friendly tooltips and help
- ✅ Settings persist correctly

**Estimated Effort:** 16-20 hours

**Rationale:** Creates professional, polished user experience. Makes advanced features discoverable. Reduces UI fragmentation. Feels like native Minecraft feature, not bolted-on mods.

---

### 13. Simplify Build System: Single Source Set, Single JAR

**Objective:** Complete the build system simplification by having only the main source set, producing a single JAR.

**Current Issue:**
- Multiple source sets: main, fabricLoader, sodium, iris
- Multiple JAR tasks: jar, fabricLoaderJar, sodiumJar, irisJar, gameJar
- Complex dependency chains
- Slower incremental builds

**Actions:**
1. Verify previous integration steps are complete:
   - ✅ Fabric Loader merged (Item 3)
   - ✅ Sodium and Iris merged (Item 8)
   - All code in main source set

2. Clean up `build.gradle`:
   ```groovy
   // Remove all source set definitions except main
   sourceSets {
       main {
           java {
               srcDir '.'
               // Excludes...
           }
           resources {
               srcDirs = ['src/main/resources']
           }
       }
       test { ... }
   }
   ```

3. Remove JAR tasks:
   - Remove `fabricLoaderJar`
   - Remove `sodiumJar`  
   - Remove `irisJar`
   - Remove `gameJar`
   - Keep only `jar` (main JAR)

4. Simplify `jar` task:
   ```groovy
   jar {
       from sourceSets.main.output
       manifest {
           attributes(
               'Main-Class': 'net.minecraft.bootstrap.MinecraftBootstrap',
               'Implementation-Title': 'MattMC',
               'Implementation-Version': version
           )
       }
   }
   ```

5. Update `runClient` task:
   ```groovy
   tasks.named('runClient', JavaExec) {
       dependsOn 'classes'
       classpath = sourceSets.main.runtimeClasspath
       mainClass = 'net.minecraft.bootstrap.MinecraftBootstrap'
       // ... rest of config
   }
   ```

6. Remove mod directory creation and copying:
   - No more `run/mods/` directory needed
   - No more copying JAR files

7. Update distribution tasks:
   - `clientDist` uses single JAR
   - `fatJar` simplified

8. Remove modules directory (archived separately):
   ```bash
   # Archive for reference
   tar -czf modules-backup.tar.gz modules/
   # Remove from build
   rm -rf modules/
   ```

**Success Criteria:**
- ✅ Only `main` and `test` source sets
- ✅ Single JAR output: `MattMC-1.21.10.jar`
- ✅ Simpler build.gradle (<50% current size)
- ✅ Faster builds (unified compilation)
- ✅ All functionality preserved
- ✅ Client runs correctly from single JAR

**Estimated Effort:** 8-12 hours

**Rationale:** Ultimate build system simplification. Fastest builds possible. Clearest project structure. Easiest to understand and maintain. Makes MattMC truly unified.

---

### 14. Performance Validation and Benchmarking

**Objective:** Validate that all integration work maintains or improves performance compared to the modular architecture.

**Current Issue:**
- No formal benchmarking of integration changes
- Need to ensure no performance regressions
- Want to measure improvements from reduced overhead

**Actions:**
1. Create benchmark suite in `src/test/java/benchmarks/`:
   ```java
   @Test
   public class RenderingBenchmark {
       @Test
       public void benchmarkChunkRendering() {
           // Measure FPS in test world
           // Compare vanilla vs Sodium vs integrated
       }
       
       @Test
       public void benchmarkShaderLoading() {
           // Measure shader pack load time
       }
       
       @Test
       public void benchmarkStartupTime() {
           // Measure application startup
       }
   }
   ```

2. Define performance metrics:
   ```
   Metric                  | Baseline (Mods) | Target (Integrated) | Actual
   ----------------------- | --------------- | ------------------- | ------
   Startup time            | 15.2s           | <15.0s              | TBD
   FPS (vanilla)           | 60 FPS          | 60 FPS              | TBD
   FPS (Sodium)            | 180 FPS         | 180 FPS ±5%         | TBD
   FPS (Sodium + Iris)     | 120 FPS         | 120 FPS ±5%         | TBD
   Memory (baseline)       | 1.2 GB          | <1.3 GB             | TBD
   Shader load time        | 3.5s            | <3.5s               | TBD
   Mod init time           | 2.1s            | <1.0s               | TBD
   ```

3. Create automated benchmark runner:
   ```bash
   #!/bin/bash
   # Run client with benchmark mode
   # Capture metrics
   # Compare with baseline
   # Generate report
   ```

4. Test with various scenarios:
   - Vanilla rendering
   - Sodium rendering (no shaders)
   - Sodium + Iris (with shaders)
   - Different shader packs
   - Different render distances
   - Different world types

5. Create `docs/PERFORMANCE-VALIDATION.md`:
   - Benchmark methodology
   - Results comparison
   - Analysis of changes
   - Recommendations for optimization

6. Profile with JFR (Java Flight Recorder):
   - Before integration (modular)
   - After integration (unified)
   - Compare CPU profiles
   - Identify any hotspots

7. Memory profiling:
   - Heap usage comparison
   - GC behavior
   - Object allocation rates

**Success Criteria:**
- ✅ Startup time improved or within 5%
- ✅ FPS maintained within 5% margin
- ✅ Memory usage not increased significantly
- ✅ Shader loading time improved or maintained
- ✅ No new GC pressure
- ✅ Comprehensive performance documentation

**Estimated Effort:** 12-16 hours

**Rationale:** Ensures integration work doesn't degrade performance. Quantifies improvements from reduced overhead. Provides confidence in the integrated architecture. Documents performance characteristics for future work.

---

### 15. Documentation and Migration Guide

**Objective:** Create comprehensive documentation for the integrated architecture and provide migration guide for users.

**Current Issue:**
- Documentation still refers to separate mods
- No migration guide for users updating from modular version
- Integration changes not documented

**Actions:**
1. Update `README.md`:
   ```markdown
   ## Features
   
   ### Integrated Advanced Rendering
   MattMC includes deeply integrated versions of Sodium and Iris:
   - **Sodium**: High-performance chunk rendering and optimization
   - **Iris**: Shader pack support compatible with OptiFine packs
   
   These are no longer separate mods but integral parts of the engine.
   
   ### Configuration
   All settings are in `options.txt`:
   - Sodium settings: `sodium.*`
   - Iris settings: `iris.*`
   - Access via Video Settings → Advanced Rendering
   ```

2. Create `docs/INTEGRATION-ARCHITECTURE.md`:
   ```markdown
   # Integration Architecture
   
   ## Overview
   This document describes how Sodium, Iris, and Fabric Loader are
   integrated into MattMC.
   
   ## Package Structure
   [Detailed package organization]
   
   ## Initialization Flow
   [Startup sequence diagram]
   
   ## API Usage
   [How to use advanced rendering APIs]
   ```

3. Create `MIGRATION.md` for users:
   ```markdown
   # Migration Guide: Modular → Integrated
   
   ## Configuration Migration
   Your settings will automatically migrate:
   - `run/config/sodium-options.json` → `run/options.txt`
   - Shader pack selection preserved
   
   ## File Changes
   - No more `run/mods/` directory
   - Single `MattMC-1.21.10.jar` instead of multiple JARs
   
   ## Options Screen
   - Video Settings now includes all options
   - Advanced Rendering submenu for Sodium/Iris settings
   ```

4. Update `INTEGRATION.md`:
   - Document current state (post-cleanup)
   - Update architecture diagrams
   - Remove outdated information about separate mods

5. Create API documentation:
   - `docs/ADVANCED-RENDERING-API.md` (from Item 11)
   - JavaDoc for all public APIs
   - Usage examples

6. Create troubleshooting guide:
   ```markdown
   # Troubleshooting Advanced Rendering
   
   ## FPS Issues
   - Try disabling Sodium
   - Adjust chunk render distance
   - Check GPU compatibility
   
   ## Shader Issues
   - Verify shader pack compatibility
   - Check shader errors in log
   - Try different shader pack
   ```

7. Update build documentation:
   - New build process (simplified)
   - Removed source sets
   - Single JAR output

8. Create video tutorial script:
   - How to use integrated features
   - Configuring advanced rendering
   - Selecting shader packs

**Success Criteria:**
- ✅ README.md accurately reflects integrated architecture
- ✅ Migration guide for users
- ✅ Comprehensive API documentation
- ✅ Troubleshooting guide available
- ✅ Build documentation updated
- ✅ All integration work documented

**Estimated Effort:** 12-16 hours

**Rationale:** Essential for user adoption and maintainability. Helps users understand the integrated architecture. Provides reference for future development. Makes the project more professional and approachable.

---

## Summary and Priorities

### Priority Levels

**High Priority (Do First):**
1. Consolidate Fabric API Stubs Into Main Source Set
2. Unify Access Widener Applications Into Permanent Source Changes
3. Merge Fabric Loader Source Into Main Source Set
4. Merge Sodium Configuration Into Minecraft Options

**Medium Priority (Do Next):**
5. Create Unified Mod Initialization System
6. Inline Critical Sodium Mixins Into Minecraft Source
7. Integrate Iris Shader Pipeline Into GameRenderer
8. Consolidate Sodium and Iris Source Sets Into Main

**Lower Priority (Nice to Have):**
9. Replace Fabric Loader's Knot Launcher With Native Bootstrap
10. Optimize Mixin Usage: Inline Most, Keep Only Essentials
11. Create Unified Advanced Rendering API
12. Integrate Sodium and Iris Options Screens Into Video Settings

**Final Steps:**
13. Simplify Build System: Single Source Set, Single JAR
14. Performance Validation and Benchmarking
15. Documentation and Migration Guide

### Total Estimated Effort

- High Priority Items: 32-42 hours
- Medium Priority Items: 64-88 hours
- Lower Priority Items: 70-92 hours
- Final Steps: 32-44 hours

**Total: 198-266 hours** (roughly 5-7 weeks of full-time work)

### Key Success Factors

1. **Incremental Approach**: Each item is independently testable
2. **Build Verification**: Every step must build successfully
3. **Functionality Preservation**: No regressions in features
4. **Performance Maintenance**: Performance parity or improvement
5. **Documentation**: Track all changes comprehensively

### Benefits of Completion

- **Simpler Architecture**: Single source set, clear package structure
- **Better Performance**: Reduced overhead from mod loading and transformations
- **Easier Maintenance**: Less build complexity, clearer code organization
- **Better UX**: Unified options, cohesive features
- **Professional Polish**: First-class integration of advanced features

---

## Next Steps

To begin this cleanup plan:

1. Review and prioritize items based on project goals
2. Set up a tracking board (GitHub Projects, Jira, etc.)
3. Start with Item 1 (lowest risk, good foundation)
4. Test thoroughly after each item
5. Document as you go
6. Celebrate progress!

Each item is designed to be a focused, achievable unit of work that builds toward a more integrated, maintainable MattMC codebase.
