# Iris Shader Integration Plan: 30-Step Deep Integration

## Executive Summary

This document provides a comprehensive, systematic 30-step plan to fully integrate Iris Shaders into MattMC as a first-class citizen, following the successful pattern established by Sodium integration (Steps 7-8 in INTEGRATION.md). The goal is to transform Iris from an external mod JAR loaded through Fabric's mod system into a deeply integrated shader rendering subsystem that works natively with Minecraft's core renderer and the integrated Sodium chunk renderer.

**Current State**: 
- Iris lives in `modules/Iris-1.21.9/` with multiplatform structure (common/fabric/neoforge)
- 724 Java files including ~159 mixin classes
- Compiled to `iris-1.9.6-mc1.21.10.jar` in `build/mods/`
- Loaded dynamically via Fabric Loader at runtime
- Deep dependency on integrated Sodium renderer

**Target State**:
- All Iris implementation code in `src/main/java/net/minecraft/client/renderer/shaders/`
- No separate Iris JAR or module directory
- Native shader pipeline integrated with Minecraft's renderer
- Direct integration with Sodium (now also integrated)
- Dual rendering paths: vanilla shaders + Iris shader packs
- Zero mod loading overhead
- Build system simplified to single unified codebase

**Key Principles**:
- Each step must compile successfully before proceeding
- Each step must maintain zero regressions
- Each step must produce a runnable game
- Incremental approach with abstraction layers first
- Progressive mixin elimination through inlining

---

## Architecture Overview

### Current Iris Architecture

**Package Structure** (modules/Iris-1.21.9/):
```
common/
├── src/main/java/net/irisshaders/iris/
│   ├── api/                    (Public API for shader packs)
│   ├── compat/                 (Compatibility layers)
│   ├── gl/                     (OpenGL abstractions)
│   ├── gui/                    (Shader selection UI)
│   ├── helpers/                (Utility classes)
│   ├── layer/                  (Rendering layers)
│   ├── mixin/                  (~159 mixin classes)
│   ├── pathways/               (Rendering pathways)
│   ├── pipeline/               (Shader pipeline system)
│   ├── pbr/                    (PBR material system)
│   ├── postprocess/            (Post-processing effects)
│   ├── samplers/               (Texture samplers)
│   ├── shaderpack/             (Shader pack loading/parsing)
│   ├── shadows/                (Shadow rendering)
│   ├── targets/                (Render targets/framebuffers)
│   ├── uniforms/               (Shader uniform system)
│   └── vertices/               (Vertex format extensions)
fabric/src/main/java/net/irisshaders/iris/
└── fabric/                     (Fabric-specific code)
```

**Key Subsystems**:
1. **Shader Pack Loading** - Parses OptiFine/Iris shader pack format
2. **Pipeline System** - Multi-pass rendering pipeline (shadow → gbuffer → composite → final)
3. **Uniform System** - Exposes game state to shaders
4. **Framebuffer Management** - Complex render target orchestration
5. **Sodium Integration** - Deep hooks into Sodium's chunk renderer
6. **Post-Processing** - Deferred rendering and effects
7. **PBR Support** - Physically-based rendering materials

**Mixin Categories** (~159 mixins):
- Core Minecraft renderer hooks (~40 mixins)
- Sodium compatibility mixins (~35 mixins)
- Vertex format extensions (~20 mixins)
- Framebuffer and texture mixins (~25 mixins)
- Shader program loading (~15 mixins)
- World/lighting integration (~15 mixins)
- Miscellaneous compatibility (~9 mixins)

### Target Integrated Architecture

**Package Structure** (after integration):
```
src/main/java/net/minecraft/client/renderer/shaders/
├── api/                        (Shader pack API)
├── compat/                     (Compatibility layers)
├── gl/                         (OpenGL extensions)
├── gui/                        (Integrated shader UI)
├── iris/                       (Core Iris implementation)
│   ├── layer/
│   ├── pathways/
│   ├── pipeline/
│   ├── pbr/
│   ├── postprocess/
│   ├── samplers/
│   ├── shaderpack/
│   ├── shadows/
│   ├── targets/
│   ├── uniforms/
│   └── vertices/
└── mixin/                      (Remaining mixins, to be eliminated)
```

**Integration Points**:
- `GameRenderer` - Shader pipeline lifecycle
- `LevelRenderer` - Shadow passes, shader pass injection
- `RenderTarget` - Custom framebuffer management
- Sodium's chunk renderer - Shader wrapping
- `Options` - Shader pack selection and configuration

---

## 30-Step Integration Plan

Each step is designed to be completable in a single session (2-4 hours) with a working, testable build at completion.

---

## PHASE 1: Foundation & Configuration (Steps 1-6)

### Step 1: Create Shader Rendering Configuration System

**Objective**: Establish configuration infrastructure for toggling shader features.

**Actions**:
1. Create `ShaderRenderingConfig.java`:
   - Feature flag: `enableShaderPacks` (default: false)
   - Feature flag: `enableShadows` (default: false)
   - Feature flag: `enablePostProcessing` (default: false)
   - Method: `isShaderPacksEnabled()` - checks flag + Options
   - Method: `isShadowsEnabled()` - checks flag + shader pack capability
   - Method: `isPostProcessingEnabled()` - checks flag + shader pack capability

2. Add to `Options.java`:
   - `OptionInstance<String> shaderPackName` (already exists from Step 5)
   - `OptionInstance<Boolean> enableShaderPacks`
   - `OptionInstance<Boolean> enableShadows`
   - `OptionInstance<Boolean> enablePostProcessing`
   - Save/load in `processOptions()`

3. Initialize all flags to `false` by default (vanilla behavior)

**Testing**:
- Build compiles successfully
- All flags default to false
- Options save and load correctly
- No behavior changes (flags disabled)

**Why This Step**:
- Creates foundation for dual rendering paths
- Allows incremental feature activation
- Enables safe testing of each shader subsystem

**Zero Regression Strategy**:
- All flags disabled by default
- Vanilla rendering unchanged
- Configuration only, no functional code

**Completion Criteria**:
- ShaderRenderingConfig class created
- Options integration complete
- Build successful
- Game launches and runs normally

---

### Step 2: Create Shader Pipeline Abstraction Interfaces

**Objective**: Define interfaces for switchable shader pipeline components.

**Actions**:
1. Create `ShaderPipeline.java` interface:
   - `void initialize()` - Pipeline initialization
   - `void beginFrame()` - Frame setup
   - `void beginShadowPass()` - Shadow rendering start
   - `void endShadowPass()` - Shadow rendering end
   - `void beginMainPass()` - Main rendering start
   - `void endMainPass()` - Main rendering end
   - `void applyPostProcessing()` - Post-processing
   - `void endFrame()` - Frame cleanup
   - `void cleanup()` - Pipeline shutdown

2. Create `VanillaShaderPipeline.java`:
   - Implements `ShaderPipeline`
   - All methods are no-ops or delegate to vanilla rendering
   - Preserves existing shader behavior

3. Create `IrisShaderPipeline.java` stub:
   - Implements `ShaderPipeline`
   - All methods throw `UnsupportedOperationException` initially
   - Will be implemented in later steps

4. Create `ShaderPipelineManager.java`:
   - Manages active pipeline (vanilla vs Iris)
   - Method: `getActivePipeline()` - returns current pipeline
   - Method: `switchPipeline(ShaderPipeline)` - swaps pipelines
   - Initialization logic based on `ShaderRenderingConfig`

**Testing**:
- Build compiles successfully
- VanillaShaderPipeline delegates correctly
- IrisShaderPipeline exists but not used
- No behavior changes

**Why This Step**:
- Establishes abstraction for dual shader paths
- Enables safe incremental implementation
- Clear separation of concerns

**Zero Regression Strategy**:
- Vanilla pipeline wraps existing behavior
- Iris pipeline not activated
- Interface-based design allows switching

**Completion Criteria**:
- All pipeline interfaces defined
- VanillaShaderPipeline complete
- IrisShaderPipeline stub created
- ShaderPipelineManager implemented
- Build successful, game runs

---

### Step 3: Integrate Pipeline Selection in GameRenderer

**Objective**: Add shader pipeline lifecycle hooks to GameRenderer without changing behavior.

**Actions**:
1. Modify `GameRenderer.java`:
   - Add field: `private ShaderPipeline activePipeline;`
   - Add field: `private ShaderPipeline vanillaPipeline;`
   - Add field: `private ShaderPipeline irisPipeline;`
   
2. In constructor:
   - Initialize `vanillaPipeline = new VanillaShaderPipeline(this);`
   - Initialize `irisPipeline = new IrisShaderPipeline();` (stub)
   - Initialize `activePipeline = vanillaPipeline;` (default)

3. Add method `selectShaderPipeline()`:
   - If `ShaderRenderingConfig.isShaderPacksEnabled()`: use irisPipeline
   - Else: use vanillaPipeline
   - Log pipeline switches

4. Hook into render methods:
   - At start of `render()`: call `activePipeline.beginFrame()`
   - At end of `render()`: call `activePipeline.endFrame()`
   - Always call `selectShaderPipeline()` before begin

**Testing**:
- Build compiles successfully
- With flags disabled, vanilla pipeline used
- VanillaPipeline methods called correctly
- No visual changes
- Logging shows vanilla pipeline active

**Why This Step**:
- Establishes pipeline lifecycle integration
- GameRenderer now aware of shader pipelines
- Foundation for Iris pipeline activation

**Zero Regression Strategy**:
- Vanilla pipeline selected by default
- IrisShaderPipeline not activated (stub only)
- All existing render paths preserved

**Completion Criteria**:
- GameRenderer modified with pipeline fields
- Pipeline selection logic implemented
- Lifecycle hooks in place
- Build successful, game runs
- Logging confirms vanilla pipeline

---

### Step 4: Integrate Pipeline Selection in LevelRenderer

**Objective**: Add shader pass injection points to LevelRenderer for shadow rendering.

**Actions**:
1. Modify `LevelRenderer.java`:
   - Add method: `prepareShadowPass()` - prepares for shadow rendering
   - Add method: `renderShadowPass()` - executes shadow rendering
   - Add method: `cleanupShadowPass()` - cleanup after shadows

2. In `renderLevel()` method:
   - Before terrain rendering: call `activePipeline.beginMainPass()`
   - After terrain rendering: call `activePipeline.endMainPass()`
   
3. Add shadow pass invocation:
   - If `ShaderRenderingConfig.isShadowsEnabled()`:
     - Call `activePipeline.beginShadowPass()`
     - Call `prepareShadowPass()`
     - Call `renderShadowPass()` (vanilla: no-op)
     - Call `cleanupShadowPass()`
     - Call `activePipeline.endShadowPass()`

4. Vanilla implementations:
   - `prepareShadowPass()` - no-op
   - `renderShadowPass()` - no-op
   - `cleanupShadowPass()` - no-op

**Testing**:
- Build compiles successfully
- Shadow pass methods exist but are no-ops
- Main pass hooks don't affect rendering
- No visual changes
- Performance unchanged

**Why This Step**:
- Creates injection points for shader passes
- LevelRenderer now shader-pipeline aware
- Foundation for shadow rendering

**Zero Regression Strategy**:
- All new methods are no-ops
- Existing rendering unchanged
- Hooks in place but inactive

**Completion Criteria**:
- LevelRenderer modified with shadow methods
- Main pass hooks added
- Build successful, game runs
- No regressions

---

### Step 5: Add Telemetry and Validation

**Objective**: Add logging and validation for shader pipeline operation.

**Actions**:
1. In `ShaderPipelineManager`:
   - Add logging for pipeline switches
   - Track pipeline statistics (frame count, switch count)
   - Add method: `dumpPipelineStats()` for debugging

2. In `GameRenderer`:
   - Add validation in debug mode:
     - Verify pipeline lifecycle order (begin before end)
     - Detect missing begin/end calls
     - Assert pipeline state consistency

3. Add debug overlay:
   - Show active pipeline name (vanilla/iris)
   - Show enabled shader features
   - Show shader pack name (if loaded)
   - FPS impact measurement

4. Add configuration validation:
   - Verify shader pack exists if enabled
   - Warn if Iris enabled but no pack loaded
   - Validate feature flag combinations

**Testing**:
- Build compiles successfully
- Logging shows pipeline activity
- Debug overlay displays correctly
- Validation catches configuration errors
- No crashes or assertions failed

**Why This Step**:
- Enables debugging of pipeline behavior
- Validates configuration consistency
- Provides visibility into shader system state

**Zero Regression Strategy**:
- Telemetry is passive (logging only)
- Validation in debug mode only
- No functional changes

**Completion Criteria**:
- Logging implemented
- Validation added
- Debug overlay functional
- Build successful, game runs
- Can toggle debug overlay

---

### Step 6: Create Shader Pack Discovery and Metadata System

**Objective**: Implement shader pack discovery without loading/parsing.

**Actions**:
1. Create `ShaderPackMetadata.java`:
   - Field: `String name`
   - Field: `Path location`
   - Field: `boolean isValid`
   - Field: `String version`
   - Method: `validate()` - checks if pack structure is valid

2. Create `ShaderPackDiscovery.java`:
   - Method: `discoverShaderPacks()` - scans `shaderpacks/` directory
   - Returns `List<ShaderPackMetadata>`
   - Validates each pack structure (checks for required files)
   - Does NOT parse shader files yet

3. Create `shaderpacks/` directory structure:
   - Location: `run/shaderpacks/` (analogous to `run/mods/`)
   - README explaining directory purpose

4. Integrate with Options:
   - Load available packs on game start
   - Populate shader pack dropdown in video settings (UI in Step 19)
   - Persist selected pack name

**Testing**:
- Build compiles successfully
- `shaderpacks/` directory created
- Discovery scans directory correctly
- Empty directory returns empty list
- Invalid packs marked as invalid
- No crashes if directory missing

**Why This Step**:
- Foundation for shader pack loading
- Enables pack selection before implementation
- Tests filesystem interaction

**Zero Regression Strategy**:
- Discovery only, no loading
- Does not affect rendering
- Gracefully handles missing directory

**Completion Criteria**:
- ShaderPackMetadata class created
- ShaderPackDiscovery implemented
- shaderpacks/ directory created
- Build successful, game runs
- Discovery works correctly

---

## PHASE 2: Core Mixin Analysis and Accessor Creation (Steps 7-12)

### Step 7: Analyze and Document Iris Mixins

**Objective**: Comprehensive analysis of all 159 Iris mixins to plan inlining strategy.

**Actions**:
1. Categorize all mixins by type:
   - **Accessor mixins** - Expose private fields/methods
   - **Injection mixins** - Inject code into existing methods
   - **Overwrite mixins** - Replace method implementations
   - **Redirect mixins** - Redirect method calls
   - **Interface mixins** - Add interface implementations

2. Categorize by target:
   - Minecraft core renderer (~40)
   - Sodium integration (~35)
   - Vertex formats (~20)
   - Framebuffers/textures (~25)
   - Shader programs (~15)
   - World/lighting (~15)
   - Other (~9)

3. For each mixin, document:
   - Target class and method
   - Transformation type
   - Purpose and functionality
   - Dependencies on other mixins
   - Priority for inlining (critical vs optional)

4. Create prioritization plan:
   - Critical mixins (must inline first)
   - Core functionality mixins
   - Optimization mixins
   - Compatibility mixins (can defer)

5. Create document: `docs/IRIS-MIXIN-ANALYSIS.md`

**Testing**:
- Documentation complete and accurate
- All 159 mixins categorized
- Dependencies mapped
- Priority order established

**Why This Step**:
- Informs inlining strategy
- Identifies dependencies
- Enables parallel work planning

**Zero Regression Strategy**:
- Documentation only
- No code changes

**Completion Criteria**:
- IRIS-MIXIN-ANALYSIS.md created
- All mixins categorized
- Dependencies documented
- Priority plan established

---

### Step 8: Inline Critical Accessor Mixins - Part 1 (Framebuffers)

**Objective**: Make framebuffer and render target fields/methods public.

**Actions**:
1. Analyze framebuffer accessor mixins:
   - `RenderTargetAccessor` - Exposes RenderTarget internals
   - `PostChainAccessor` - Exposes PostChain fields
   - `PostPassAccessor` - Exposes PostPass configuration

2. Modify vanilla classes:
   - `RenderTarget.java`:
     - Make required fields public or add getters
     - Add `iris$getDepthTextureId()` method
     - Add `iris$getFramebufferId()` method
   
   - `PostChain.java`:
     - Make passes list accessible
     - Add `iris$getPasses()` method
   
   - `PostPass.java`:
     - Expose shader program reference
     - Add `iris$getProgram()` method

3. Document changes:
   - Add JavaDoc explaining Iris integration
   - Mark with `@IrisIntegration` annotation comments

**Testing**:
- Build compiles successfully
- New methods exist but not yet called
- No behavior changes
- Accessors tested in isolation

**Why This Step**:
- Framebuffers are core to Iris
- Required for render target management
- Foundation for pipeline implementation

**Zero Regression Strategy**:
- Only adds methods, doesn't change behavior
- Methods not yet called
- Vanilla paths unchanged

**Completion Criteria**:
- Framebuffer accessors added
- Build successful
- Methods tested
- JavaDoc complete

---

### Step 9: Inline Critical Accessor Mixins - Part 2 (Shaders)

**Objective**: Expose shader program internals for Iris integration.

**Actions**:
1. Analyze shader accessor mixins:
   - `ProgramAccessor` - Exposes shader program fields
   - `ShaderInstanceAccessor` - Exposes ShaderInstance internals
   - `UniformAccessor` - Exposes uniform handling

2. Modify vanilla classes:
   - `Program.java`:
     - Add `iris$getProgramId()` - OpenGL program ID
     - Add `iris$getType()` - Shader type
   
   - `ShaderInstance.java`:
     - Add `iris$getProgram()` - Program reference
     - Add `iris$getUniforms()` - Uniform map
     - Add `iris$getSamplers()` - Sampler map
   
   - `Uniform.java`:
     - Add `iris$getLocation()` - Uniform location
     - Add `iris$setDirect(...)` - Direct value setting

3. Document shader integration points

**Testing**:
- Build compiles successfully
- Shader accessors functional
- No behavior changes
- Can query shader state

**Why This Step**:
- Shaders are core to Iris functionality
- Required for uniform system
- Enables shader program replacement

**Zero Regression Strategy**:
- Accessors only, no behavioral changes
- Vanilla shader system unchanged
- New methods not yet used

**Completion Criteria**:
- Shader accessors added
- Build successful
- Methods tested
- Documentation complete

---

### Step 10: Inline Critical Accessor Mixins - Part 3 (World/Lighting)

**Objective**: Expose world and lighting information for shader uniforms.

**Actions**:
1. Analyze world/lighting accessor mixins:
   - `ClientLevelAccessor` - Exposes level data
   - `LevelRendererAccessor` - Exposes renderer state
   - `LightTextureAccessor` - Exposes light texture

2. Modify vanilla classes:
   - `ClientLevel.java`:
     - Add `iris$getRainStrength()` - Rain intensity
     - Add `iris$getThunderStrength()` - Thunder intensity
     - Add `iris$getSkyDarken()` - Sky darkening
   
   - `LevelRenderer.java`:
     - Add `iris$getTicks()` - Render tick count
     - Add `iris$getCapturedFrustum()` - Frustum (already exists via Sodium)
   
   - `LightTexture.java`:
     - Add `iris$getLightTexture()` - Texture ID
     - Add `iris$updateLightTexture(float)` - Manual update

**Testing**:
- Build compiles successfully
- World data accessible
- Lighting information available
- No behavior changes

**Why This Step**:
- Required for shader uniform system
- World state needed for shader effects
- Lighting crucial for shader rendering

**Zero Regression Strategy**:
- Read-only accessors
- No state modification
- Vanilla rendering unchanged

**Completion Criteria**:
- World/lighting accessors added
- Build successful
- Getters functional
- No regressions

---

### Step 11: Inline Critical Accessor Mixins - Part 4 (Vertex Formats)

**Objective**: Extend vertex format system for Iris vertex attributes.

**Actions**:
1. Analyze vertex format accessor mixins:
   - `VertexFormatAccessor` - Exposes format internals
   - `VertexConsumerAccessor` - Exposes consumer state
   - `BufferBuilderAccessor` - Exposes builder state

2. Modify vanilla classes:
   - `VertexFormat.java`:
     - Add `iris$getElementOffset(element)` - Element offset calculation
     - Add `iris$supportsExtension(extension)` - Extension check
     - Already has Sodium integration fields
   
   - `VertexConsumer.java`:
     - Add `iris$putBulkData(data)` - Bulk data insertion
   
   - `BufferBuilder.java`:
     - Add `iris$getVertexCount()` - Current vertex count
     - Already has Sodium buffer strategy fields

3. Coordinate with Sodium vertex integration:
   - Ensure Iris and Sodium vertex extensions compatible
   - Share infrastructure where possible

**Testing**:
- Build compiles successfully
- Vertex accessors functional
- Compatible with Sodium vertex system
- No conflicts

**Why This Step**:
- Vertex formats crucial for custom attributes
- Iris needs extended vertex data
- Must work with Sodium's vertex system

**Zero Regression Strategy**:
- Extends existing Sodium integration
- No changes to vanilla vertex paths
- Accessors only

**Completion Criteria**:
- Vertex accessors added
- Sodium compatibility verified
- Build successful
- No regressions

---

### Step 12: Inline Sodium Compatibility Accessor Mixins

**Objective**: Expose integrated Sodium internals for Iris compatibility.

**Actions**:
1. Analyze Sodium compatibility mixins:
   - Mixins targeting integrated Sodium classes
   - Located in Iris's `mixins.iris.compat.sodium.json`

2. For each Sodium integration point:
   - Identify what Iris needs from Sodium
   - Add accessor methods to integrated Sodium classes
   - Examples:
     - `SodiumChunkRenderer.iris$getRegionManager()`
     - `RenderSectionManager.iris$getSectionByPosition(x,y,z)`
     - `ChunkRenderData.iris$getMeshData()`

3. Coordinate with Sodium integration:
   - Ensure Iris accessors use proper Sodium namespaces
   - Follow Sodium's integration patterns
   - Maintain compatibility with Sodium's dual paths

4. Update Sodium classes:
   - Add `iris$` prefixed methods
   - Document Iris-Sodium integration points
   - Test in isolation

**Testing**:
- Build compiles successfully
- Iris can access Sodium data
- No circular dependencies
- Both systems compatible

**Why This Step**:
- Iris deeply integrated with Sodium
- Must work with integrated Sodium
- Critical for shader + optimization

**Zero Regression Strategy**:
- Accessors only, no behavior changes
- Sodium dual paths maintained
- Iris accessors inactive until later steps

**Completion Criteria**:
- Sodium compatibility accessors added
- Build successful
- Integration points documented
- No conflicts

---

## PHASE 3: Core Implementation Migration (Steps 13-20)

### Step 13: Migrate Iris Core API

**Objective**: Move Iris public API to Minecraft's shader package structure.

**Actions**:
1. Identify Iris public API:
   - `net.irisshaders.iris.api.*` packages
   - Shader pack API interfaces
   - Pipeline API

2. Migrate to new locations:
   - `net.irisshaders.iris.api.v0` → `net.minecraft.client.renderer.shaders.api.v0`
   - Maintain API package versioning

3. Files to migrate (~15 API files):
   - Shader pack interfaces
   - Pipeline interfaces
   - Event callbacks

4. Update package declarations and imports

5. Add comprehensive JavaDoc:
   - Document API stability
   - Migration notes
   - Integration with vanilla systems

**Testing**:
- Build compiles successfully
- API accessible from new location
- Old package references updated
- No functional changes

**Why This Step**:
- Establishes Iris API as Minecraft API
- Enables future extensions
- Clarifies public vs internal

**Zero Regression Strategy**:
- Pure package move
- No logic changes
- API not yet activated

**Completion Criteria**:
- ~15 API files migrated
- Package structure established
- JavaDoc complete
- Build successful

---

### Step 14: Migrate Shader Pack Loading System

**Objective**: Move shader pack loading and parsing implementation.

**Actions**:
1. Migrate packages:
   - `net.irisshaders.iris.shaderpack.*` → `net.minecraft.client.renderer.shaders.pack.*`

2. Files to migrate (~45 files):
   - Shader pack parser
   - Property file parser
   - Include file resolver
   - Shader source transformer
   - Pack validation

3. Update all imports:
   - Internal references
   - API references
   - Minecraft class references

4. Integrate with Step 6 discovery:
   - Connect `ShaderPackDiscovery` to loader
   - Wire loading into configuration system

5. Create abstraction:
   - `ShaderPackLoader.load(metadata)` - loads pack
   - Returns `ShaderPack` object
   - Handle errors gracefully

**Testing**:
- Build compiles successfully
- Can load shader pack metadata
- Parser works correctly
- Errors handled properly
- No actual rendering yet

**Why This Step**:
- Core to Iris functionality
- Required before pipeline implementation
- Validates shader pack format

**Zero Regression Strategy**:
- Loading only, no rendering
- Packs loaded but not applied
- Vanilla rendering unchanged

**Completion Criteria**:
- ~45 files migrated
- Shader pack loading functional
- Error handling complete
- Build successful

---

### Step 15: Migrate Pipeline System Foundation

**Objective**: Move pipeline architecture without rendering implementation.

**Actions**:
1. Migrate packages:
   - `net.irisshaders.iris.pipeline.*` → `net.minecraft.client.renderer.shaders.pipeline.*`

2. Files to migrate (~55 files):
   - Pipeline architecture
   - Pipeline builder
   - Pass definitions
   - Pipeline state machine
   - Pipeline validation

3. Connect to Step 2 interfaces:
   - Implement `IrisShaderPipeline` using migrated pipeline system
   - Wire pipeline lifecycle methods
   - Integrate with `ShaderPipelineManager`

4. Create pipeline factory:
   - `PipelineFactory.create(shaderPack)` - builds pipeline from pack
   - Validates pipeline configuration
   - Returns configured pipeline object

**Testing**:
- Build compiles successfully
- Pipeline can be created from pack
- Lifecycle methods work
- No rendering yet (stubs okay)

**Why This Step**:
- Pipeline is Iris's core architecture
- Required for all rendering features
- Establishes shader pass structure

**Zero Regression Strategy**:
- Pipeline created but not activated
- Vanilla path still default
- No rendering changes

**Completion Criteria**:
- ~55 files migrated
- Pipeline creation functional
- Build successful
- IrisShaderPipeline partially implemented

---

### Step 16: Migrate Framebuffer and Render Target System

**Objective**: Move custom framebuffer management implementation.

**Actions**:
1. Migrate packages:
   - `net.irisshaders.iris.targets.*` → `net.minecraft.client.renderer.shaders.targets.*`

2. Files to migrate (~20 files):
   - Render target manager
   - Composite render targets
   - Shadow render targets
   - Depth texture management
   - Framebuffer lifecycle

3. Integrate with Step 8 accessors:
   - Use exposed `RenderTarget` methods
   - Work with vanilla framebuffer system
   - Extend with Iris-specific targets

4. Wire into pipeline:
   - Pipeline creates render targets on initialization
   - Targets bound during appropriate passes
   - Cleanup on pipeline shutdown

**Testing**:
- Build compiles successfully
- Custom render targets created
- No memory leaks
- Targets cleaned up properly
- No visual changes yet

**Why This Step**:
- Render targets core to multi-pass rendering
- Required for shadows and post-processing
- Must integrate with vanilla system

**Zero Regression Strategy**:
- Targets created but not used for rendering
- Vanilla framebuffers unchanged
- Proper cleanup ensures no leaks

**Completion Criteria**:
- ~20 files migrated
- Render target creation works
- Build successful
- No memory leaks

---

### Step 17: Migrate Uniform System

**Objective**: Move shader uniform management and game state exposure.

**Actions**:
1. Migrate packages:
   - `net.irisshaders.iris.uniforms.*` → `net.minecraft.client.renderer.shaders.uniforms.*`

2. Files to migrate (~40 files):
   - Uniform holder
   - Uniform updaters (time, camera, weather, etc.)
   - Custom uniform handling
   - Sampler binding
   - Frame update system

3. Integrate with Step 9 accessors:
   - Use exposed shader program methods
   - Use exposed uniform methods
   - Extend vanilla uniform system

4. Connect to Step 10 world accessors:
   - Read world state for uniforms
   - Update uniforms each frame
   - Cache values when appropriate

5. Wire into pipeline:
   - Update uniforms before each pass
   - Bind samplers correctly
   - Handle shader-specific uniforms

**Testing**:
- Build compiles successfully
- Uniforms can be updated
- Values read from world correctly
- No crashes during update
- No rendering yet

**Why This Step**:
- Uniforms expose game state to shaders
- Critical for shader functionality
- Connects world to rendering

**Zero Regression Strategy**:
- Uniforms updated but shaders not active
- No visual impact yet
- Proper binding tested

**Completion Criteria**:
- ~40 files migrated
- Uniform system functional
- Build successful
- Updates work correctly

---

### Step 18: Migrate GL Extensions and Utilities

**Objective**: Move OpenGL abstraction and utility layer.

**Actions**:
1. Migrate packages:
   - `net.irisshaders.iris.gl.*` → `net.minecraft.client.renderer.shaders.gl.*`

2. Files to migrate (~30 files):
   - Texture management extensions
   - Framebuffer utilities
   - Shader compilation utilities
   - GL state tracking
   - Buffer management

3. Coordinate with Sodium GL:
   - Already have `net.minecraft.client.renderer.gl.advanced.*` from Sodium
   - Ensure no conflicts
   - Share abstractions where possible
   - Iris-specific extensions in separate package

4. Update references:
   - Pipeline uses Iris GL utilities
   - Uniforms use GL extensions
   - Targets use GL utilities

**Testing**:
- Build compiles successfully
- No conflicts with Sodium GL
- GL utilities functional
- Proper error handling
- No GL errors

**Why This Step**:
- GL utilities needed throughout Iris
- Must coexist with Sodium GL
- Provides shader-specific GL operations

**Zero Regression Strategy**:
- Utilities created but not used in vanilla path
- No conflicts with existing GL code
- Proper namespace separation

**Completion Criteria**:
- ~30 files migrated
- No Sodium conflicts
- Build successful
- GL utilities work

---

### Step 19: Migrate Shadow Rendering System

**Objective**: Implement shadow pass rendering.

**Actions**:
1. Migrate packages:
   - `net.irisshaders.iris.shadows.*` → `net.minecraft.client.renderer.shaders.shadows.*`

2. Files to migrate (~25 files):
   - Shadow renderer
   - Shadow frustum calculator
   - Shadow map allocator
   - Shadow culling
   - Shadow matrix computation

3. Implement Step 4 shadow methods:
   - `prepareShadowPass()` - sets up shadow frustum, matrices
   - `renderShadowPass()` - renders world from light perspective
   - `cleanupShadowPass()` - restores main frustum, matrices

4. Integrate with pipeline:
   - Shadow pass part of pipeline lifecycle
   - Shadow targets from Step 16
   - Shadow uniforms from Step 17

5. Wire into LevelRenderer:
   - Use integrated Sodium chunk renderer for shadow chunks
   - Render entities in shadow pass
   - Apply culling for shadow frustum

**Testing**:
- Build compiles successfully
- Shadow pass can be enabled via config
- Shadow rendering works (visual verification)
- Performance acceptable
- Can disable shadows (fallback to vanilla)

**Why This Step**:
- Shadows major Iris feature
- First visible shader functionality
- Tests full pipeline integration

**Zero Regression Strategy**:
- Shadow pass only when flag enabled
- Vanilla path when disabled
- Graceful degradation

**Completion Criteria**:
- ~25 files migrated
- Shadow rendering functional
- Build successful
- Shadows visible when enabled
- Performance tested

---

### Step 20: Migrate Main Pass Shader Integration

**Objective**: Wrap terrain rendering with shader pipeline.

**Actions**:
1. Migrate remaining pipeline components:
   - Main pass implementation
   - GBuffer pass handling
   - Terrain shader wrapping

2. Integrate with Sodium:
   - Wrap Sodium's terrain renderer
   - Apply shaders to chunk rendering
   - Maintain Sodium optimizations

3. Modify shader pass hooks (Step 4):
   - `beginMainPass()` - activates main pass shaders
   - `endMainPass()` - deactivates shaders

4. Connect to integrated Sodium:
   - Use Sodium's chunk renderer within shader pass
   - Apply shader programs to terrain
   - Manage shader state around Sodium calls

5. Handle dual paths:
   - Vanilla: no shader wrapping
   - Iris enabled: shader wrapping active
   - Sodium + Iris: both optimizations

**Testing**:
- Build compiles successfully
- Terrain shaders apply correctly
- Sodium optimizations preserved
- Visual quality correct
- Performance tested (Sodium + Iris)

**Why This Step**:
- Core rendering with shaders
- Tests Sodium-Iris integration
- Main visual feature

**Zero Regression Strategy**:
- Wrapped rendering matches vanilla visually
- Performance should equal/exceed vanilla
- Both systems work independently and together

**Completion Criteria**:
- Main pass integration complete
- Build successful
- Terrain shaders visible
- Sodium + Iris working together
- Performance acceptable

---

## PHASE 4: Advanced Features (Steps 21-25)

### Step 21: Migrate Post-Processing System

**Objective**: Implement post-processing effects and composite passes.

**Actions**:
1. Migrate packages:
   - `net.irisshaders.iris.postprocess.*` → `net.minecraft.client.renderer.shaders.postprocess.*`

2. Files to migrate (~35 files):
   - Composite pass renderer
   - Deferred rendering system
   - Effect chain executor
   - Buffer ping-ponging
   - Post-process scheduler

3. Integrate with pipeline:
   - Composite passes after main pass
   - Use render targets from Step 16
   - Apply shaders sequentially

4. Implement in GameRenderer:
   - After main rendering complete
   - Before UI rendering
   - Apply post-processing chain

**Testing**:
- Build compiles successfully
- Post-processing effects visible
- Composite passes execute in order
- No performance issues
- Can disable post-processing

**Why This Step**:
- Post-processing is key visual feature
- Bloom, DOF, motion blur, etc.
- Tests multi-pass rendering

**Zero Regression Strategy**:
- Post-processing only when enabled
- Vanilla path unaffected
- Feature flag controlled

**Completion Criteria**:
- ~35 files migrated
- Post-processing functional
- Build successful
- Effects visible
- Performance acceptable

---

### Step 22: Migrate PBR Material System

**Objective**: Implement physically-based rendering materials.

**Actions**:
1. Migrate packages:
   - `net.irisshaders.iris.pbr.*` → `net.minecraft.client.renderer.shaders.pbr.*`

2. Files to migrate (~20 files):
   - Material property system
   - Normal map handling
   - Specular map handling
   - Texture atlas integration
   - PBR shader bindings

3. Integrate with texture system:
   - Normal maps loaded with textures
   - Specular maps loaded
   - Material properties parsed

4. Connect to rendering:
   - PBR data available to shaders
   - Proper texture binding
   - Material uniforms updated

**Testing**:
- Build compiles successfully
- PBR textures load correctly
- Material properties accessible
- Shaders receive PBR data
- Visual quality improved

**Why This Step**:
- PBR enables realistic materials
- Modern rendering feature
- Adds visual depth

**Zero Regression Strategy**:
- PBR only with compatible shader packs
- Vanilla materials without PBR
- Graceful fallback

**Completion Criteria**:
- ~20 files migrated
- PBR system functional
- Build successful
- Materials render correctly
- No texture issues

---

### Step 23: Migrate Vertex Extensions and Custom Attributes

**Objective**: Implement Iris vertex format extensions.

**Actions**:
1. Migrate packages:
   - `net.irisshaders.iris.vertices.*` → `net.minecraft.client.renderer.shaders.vertices.*`

2. Files to migrate (~15 files):
   - Extended vertex formats
   - Custom attribute handling
   - Vertex transformation utilities
   - Attribute injection

3. Integrate with Step 11:
   - Use vertex format accessors
   - Extend Sodium vertex system
   - Add Iris-specific attributes

4. Custom attributes:
   - Tangent/bitangent for normal mapping
   - Custom UVs
   - Custom colors

**Testing**:
- Build compiles successfully
- Custom attributes work
- Compatible with Sodium
- No vertex corruption
- Performance acceptable

**Why This Step**:
- Custom attributes needed for advanced shaders
- Normal mapping requires tangents
- Extends vertex system

**Zero Regression Strategy**:
- Extended formats only with Iris
- Vanilla formats unchanged
- Sodium compatibility maintained

**Completion Criteria**:
- ~15 files migrated
- Custom attributes functional
- Build successful
- No vertex issues
- Sodium compatible

---

### Step 24: Migrate Compatibility and Helper Systems

**Objective**: Move compatibility layers and utility systems.

**Actions**:
1. Migrate packages:
   - `net.irisshaders.iris.compat.*` → `net.minecraft.client.renderer.shaders.compat.*`
   - `net.irisshaders.iris.helpers.*` → `net.minecraft.client.renderer.shaders.helpers.*`
   - `net.irisshaders.iris.layer.*` → `net.minecraft.client.renderer.shaders.layer.*`

2. Files to migrate (~30 files):
   - Environment compatibility
   - OptiFine compatibility stubs
   - Rendering layer helpers
   - State management utilities

3. Update references throughout codebase

**Testing**:
- Build compiles successfully
- Compatibility layers work
- Helpers functional
- No broken dependencies

**Why This Step**:
- Compatibility ensures broad shader pack support
- Helpers reduce code duplication
- Cleanly organized utilities

**Zero Regression Strategy**:
- Pure migration
- No logic changes
- Maintains compatibility

**Completion Criteria**:
- ~30 files migrated
- All imports updated
- Build successful
- Utilities work

---

### Step 25: Migrate Sampler and Texture Management

**Objective**: Implement custom texture sampling and management.

**Actions**:
1. Migrate packages:
   - `net.irisshaders.iris.samplers.*` → `net.minecraft.client.renderer.shaders.samplers.*`

2. Files to migrate (~15 files):
   - Sampler manager
   - Custom sampler binding
   - Texture ID management
   - Sampler state tracking

3. Integrate with GL system:
   - Proper texture unit management
   - Sampler binding coordination
   - State tracking

4. Connect to uniforms:
   - Samplers exposed as uniforms
   - Proper binding before shader use
   - Cleanup after rendering

**Testing**:
- Build compiles successfully
- Samplers bind correctly
- No texture corruption
- Proper cleanup
- Performance acceptable

**Why This Step**:
- Samplers expose textures to shaders
- Proper management prevents issues
- Multiple textures need coordination

**Zero Regression Strategy**:
- Custom samplers only with Iris
- Vanilla texture system unchanged
- Proper resource management

**Completion Criteria**:
- ~15 files migrated
- Sampler system functional
- Build successful
- Textures render correctly
- No leaks

---

## PHASE 5: UI, Cleanup, and Finalization (Steps 26-30)

### Step 26: Migrate Shader Selection UI

**Objective**: Integrate shader pack selection into video settings.

**Actions**:
1. Migrate packages:
   - `net.irisshaders.iris.gui.*` → `net.minecraft.client.renderer.shaders.gui.*`

2. Files to migrate (~25 files):
   - Shader pack screen
   - Pack selection UI
   - Shader options screen
   - Pack info display

3. Integrate with Minecraft UI:
   - Add "Shader Packs..." button to Video Settings
   - Use Minecraft's screen framework
   - Match vanilla UI style

4. Connect to configuration:
   - Selection updates Options
   - Reload pipeline on pack change
   - Handle pack errors gracefully

**Testing**:
- Build compiles successfully
- UI renders correctly
- Pack selection works
- Shader reloading works
- No UI crashes

**Why This Step**:
- User-facing feature
- Enables shader pack switching
- Professional integration

**Zero Regression Strategy**:
- New screens, no changes to existing UI
- Vanilla video settings unchanged
- Additional button only

**Completion Criteria**:
- ~25 files migrated
- UI functional
- Build successful
- Pack selection works
- UI matches vanilla style

---

### Step 27: Migrate Pathway and Layer Systems

**Objective**: Finalize rendering pathway abstraction.

**Actions**:
1. Migrate remaining packages:
   - `net.irisshaders.iris.pathways.*` → `net.minecraft.client.renderer.shaders.pathways.*`

2. Files to migrate (~10 files):
   - Rendering pathways
   - Path selection logic
   - State machine

3. Ensure all rendering paths work:
   - Vanilla only
   - Sodium only
   - Iris only
   - Sodium + Iris

**Testing**:
- Build compiles successfully
- All path combinations work
- State transitions clean
- No conflicts

**Why This Step**:
- Completes rendering architecture
- Validates all combinations
- Clean state management

**Zero Regression Strategy**:
- Existing paths preserved
- New combinations tested
- Fallbacks work

**Completion Criteria**:
- ~10 files migrated
- All paths functional
- Build successful
- Combinations tested

---

### Step 28: Remove Iris Module Dependencies

**Objective**: Eliminate dependency on Iris module directory.

**Actions**:
1. Verify all Iris code migrated:
   - Check for remaining files in `modules/Iris-1.21.9/`
   - Ensure all ~724 files accounted for

2. Update build.gradle:
   - Remove `iris` sourceset
   - Remove `irisImplementation` configuration
   - Remove `irisJar` task
   - Update dependencies

3. Remove module directory:
   - Delete `modules/Iris-1.21.9/` (after backup)
   - Remove from version control

4. Update fabric configurations:
   - Remove Iris from mod loading
   - Update mixin configs

5. Clean up build artifacts:
   - Remove `build/mods/iris-*.jar`
   - Clean build cache

**Testing**:
- Build compiles successfully without module
- All Iris functionality works
- No missing classes
- No runtime errors
- Full feature parity

**Why This Step**:
- Completes integration
- Removes duplication
- Simplifies build

**Zero Regression Strategy**:
- All functionality verified before deletion
- Backup created
- Can revert if needed

**Completion Criteria**:
- Module directory removed
- Build successful
- All features work
- No dependencies on module

---

### Step 29: Inline Remaining Iris Mixins

**Objective**: Eliminate remaining mixin files through abstraction.

**Actions**:
1. Review remaining mixins:
   - Identify which can be inlined
   - Which require abstraction layers
   - Which are compatibility only

2. For each mixin:
   - Add abstraction layer OR
   - Inline functionality directly OR
   - Document as acceptable runtime mixin

3. Priority inlining:
   - Performance-critical mixins first
   - Compatibility mixins can stay
   - Workaround mixins can stay temporarily

4. Track mixin elimination:
   - Document which mixins remain
   - Why they remain
   - Plan for future elimination

**Testing**:
- Build compiles successfully
- Each inlined mixin tested
- No regressions
- Performance maintained

**Why This Step**:
- Reduces runtime overhead
- Makes code more maintainable
- Progressive elimination

**Zero Regression Strategy**:
- One mixin at a time
- Test after each change
- Keep working mixins as fallback

**Completion Criteria**:
- Major mixins inlined
- Documentation complete
- Build successful
- Performance improved

---

### Step 30: Performance Validation and Final Polish

**Objective**: Comprehensive testing, optimization, and documentation.

**Actions**:
1. **Performance Benchmarking**:
   - Measure FPS: Vanilla vs Iris vs Sodium+Iris
   - Memory usage profiling
   - Frame time analysis
   - Shader compilation time

2. **Feature Validation**:
   - Test all shader packs (Complementary, BSL, SEUS, etc.)
   - Verify shadow rendering
   - Verify post-processing
   - Verify PBR materials
   - Test all rendering path combinations

3. **Compatibility Testing**:
   - Different graphics cards (NVIDIA, AMD, Intel)
   - Different operating systems
   - Different OpenGL versions
   - Resource pack compatibility

4. **Code Cleanup**:
   - Remove dead code
   - Remove debug logging
   - Clean up TODOs
   - Consistent code style

5. **Documentation**:
   - Update README.md
   - Create SHADER-PACKS.md guide
   - Document configuration options
   - Create troubleshooting guide
   - Update INTEGRATION.md (mark Steps 9-12 complete)

6. **Final Testing**:
   - Clean build from scratch
   - Full game playthrough
   - Stress testing (large worlds, many shaders)
   - Memory leak detection
   - Crash testing

**Testing Metrics**:
- FPS targets:
  - Vanilla baseline
  - Iris within 5% of vanilla (no shaders)
  - Iris with shaders: depends on pack
  - Sodium+Iris: maintain Sodium performance benefits
- Memory:
  - No leaks over extended play
  - Shader pack loading memory acceptable
- Stability:
  - No crashes in normal gameplay
  - Graceful error handling
  - Robust shader pack validation

**Why This Step**:
- Ensures production quality
- Validates full integration
- Provides user confidence

**Zero Regression Strategy**:
- Extensive testing catches issues
- Benchmarking proves parity
- Multiple configurations tested

**Completion Criteria**:
- All tests pass
- Performance targets met
- Documentation complete
- Zero critical bugs
- Iris 100% integrated
- Ready for production use

---

## Success Criteria

Upon completion of all 30 steps:

### Functionality
- ✅ All Iris shader features fully integrated
- ✅ Shader pack loading and switching works
- ✅ Shadow rendering functional
- ✅ Post-processing effects working
- ✅ PBR materials supported
- ✅ Custom vertex attributes working
- ✅ All rendering paths functional (vanilla/Sodium/Iris/both)

### Code Quality
- ✅ All code properly documented
- ✅ No code duplication with Sodium
- ✅ Clean package structure
- ✅ Integration points clearly marked
- ✅ Minimal mixin count (progressive elimination)

### Build System
- ✅ Builds successfully without Iris module
- ✅ Zero compilation errors
- ✅ Single unified codebase
- ✅ Simplified build configuration

### Performance
- ✅ Iris overhead minimal when disabled
- ✅ Shader performance comparable to mod version
- ✅ Sodium optimizations preserved
- ✅ Efficient memory usage

### User Experience
- ✅ Seamless shader pack switching
- ✅ Professional UI integration
- ✅ Clear configuration options
- ✅ Graceful error handling
- ✅ Compatible with major shader packs

---

## Risk Mitigation

### Risk 1: Sodium-Iris Integration Complexity

**Likelihood**: High  
**Impact**: High

**Mitigation**:
- Steps 12 and 19-20 specifically address Sodium integration
- Test both systems independently and together
- Use abstraction layers to isolate concerns
- Maintain dual paths (Iris without Sodium, Sodium without Iris)

### Risk 2: Mixin Elimination Difficulty

**Likelihood**: Medium  
**Impact**: Medium

**Mitigation**:
- Progressive approach (not all mixins must be eliminated)
- Accept some runtime mixins as acceptable
- Document which mixins remain and why
- Prioritize performance-critical mixins for inlining

### Risk 3: Shader Pack Compatibility

**Likelihood**: Medium  
**Impact**: High

**Mitigation**:
- Test with major shader packs at each step
- Maintain compatibility layers
- Robust error handling for malformed packs
- Clear validation messages

### Risk 4: Performance Regression

**Likelihood**: Low  
**Impact**: High

**Mitigation**:
- Benchmark at each major step
- Profile hotspots
- Maintain performance targets
- Keep Sodium optimizations intact

### Risk 5: UI Integration Complexity

**Likelihood**: Low  
**Impact**: Medium

**Mitigation**:
- Use Minecraft's native UI framework
- Match vanilla style closely
- Test across different screen sizes
- Graceful error handling in UI

---

## Timeline Estimation

**Per-Phase Estimates** (1-2 developers):

- **Phase 1** (Steps 1-6): 1-2 weeks
  - Foundation work, mostly infrastructure
  - Low risk, straightforward implementation

- **Phase 2** (Steps 7-12): 2-3 weeks
  - Mixin analysis and accessor creation
  - Requires careful analysis
  - Coordination with Sodium integration

- **Phase 3** (Steps 13-20): 5-7 weeks
  - Largest phase, core implementation
  - Complex integration work
  - Iterative testing required

- **Phase 4** (Steps 21-25): 3-4 weeks
  - Advanced features
  - Builds on Phase 3 foundation
  - Visual features, easier to validate

- **Phase 5** (Steps 26-30): 2-3 weeks
  - UI and finalization
  - Testing and polish
  - Documentation

**Total Estimated Timeline**: 13-19 weeks (~3-5 months)

**Recommended Approach**: 
- Complete 1-2 steps per work session (2-4 hours each)
- Thorough testing after each step
- Document learnings and issues
- Maintain momentum but don't rush

---

## Relationship to INTEGRATION.md

This document is a detailed expansion of Steps 9-12 from INTEGRATION.md:
- **Step 9** (INTEGRATION.md): Migrate Iris Core API → **This doc Steps 13**
- **Step 10** (INTEGRATION.md): Migrate Iris Implementation → **This doc Steps 14-25**
- **Step 11** (INTEGRATION.md): Inline Iris Mixins → **This doc Steps 7-12, 29**
- **Step 12** (INTEGRATION.md): Integrate Sodium-Iris Compatibility → **This doc Steps 12, 19-20**

This plan provides the detailed roadmap that was successfully used for Sodium (STEP7-8PLAN.md) but adapted for Iris's specific architecture and requirements.

---

## Notes and Caveats

### Known Challenges

1. **Iris-Sodium Deep Coupling**: Iris is deeply integrated with Sodium's chunk renderer. Integration must carefully maintain this relationship.

2. **Mixin Count**: Iris has ~159 mixins compared to Sodium's 97. Full elimination may not be practical; progressive reduction is acceptable.

3. **Shader Pack Complexity**: Shader packs vary widely in quality and features. Robust validation and error handling essential.

4. **OpenGL Complexity**: Iris uses advanced OpenGL features. Must ensure compatibility across different drivers and hardware.

### Future Work (Beyond This Plan)

After completing this 30-step integration:
- Further mixin elimination (ongoing)
- Performance optimizations
- New shader pack features
- Extended PBR capabilities
- Better debugging tools

### Maintenance

- Keep integration points clearly marked for future maintenance
- Document any deviations from Iris upstream
- Plan for incorporating Iris updates
- Maintain compatibility with upstream shader packs

---

## Appendix: Iris Mixin Categories

For reference during implementation (detailed analysis in Step 7):

### By Target System
1. **Minecraft Core Renderer** (~40 mixins)
   - GameRenderer hooks
   - LevelRenderer integration
   - RenderType handling

2. **Sodium Integration** (~35 mixins)
   - Chunk renderer compatibility
   - Vertex format coordination
   - State management

3. **Vertex Formats** (~20 mixins)
   - Custom attributes
   - Format extensions
   - Buffer handling

4. **Framebuffers/Textures** (~25 mixins)
   - Render target management
   - Texture handling
   - Mipmap support

5. **Shader Programs** (~15 mixins)
   - Program loading
   - Uniform handling
   - Shader compilation

6. **World/Lighting** (~15 mixins)
   - World state access
   - Lighting information
   - Sky rendering

7. **Miscellaneous** (~9 mixins)
   - GUI integration
   - Configuration
   - Utilities

### By Transformation Type
- **Accessor Mixins**: ~40 (expose private fields/methods)
- **Injection Mixins**: ~70 (inject code into methods)
- **Overwrite Mixins**: ~15 (replace methods)
- **Redirect Mixins**: ~20 (redirect method calls)
- **Interface Mixins**: ~14 (add interface implementations)

---

## Document Version

- **Version**: 1.0
- **Date**: 2025-12-17
- **Status**: Ready for Implementation
- **Author**: GitHub Copilot
- **Based On**: INTEGRATION.md Steps 9-12, STEP7-8PLAN.md patterns

---

**END OF PLAN**
