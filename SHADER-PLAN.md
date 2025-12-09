# Comprehensive Shader System Implementation Plan for MattMC
## Baked-In Shader Architecture with OptiFine/Iris-Style Rendering

---

## Executive Summary

This document outlines a complete, in-depth implementation plan to add **advanced shader rendering capabilities** to the MattMC Minecraft client (a fork of Minecraft Java 1.21.10). Unlike traditional shader loaders like OptiFine and Iris, MattMC uses a **compile-time baked-in approach** where shader packs are bundled directly into the game JAR during compilation.

### Key Architectural Differences from OptiFine/Iris

**MattMC's Approach:**
- Shaders are **baked into the JAR** at compile time from `src/main/resources/assets/minecraft/shaders/`
- Each shader pack is stored as an **unzipped directory** in the resources folder
- Shader packs are **dynamically discovered** at runtime (not hardcoded)
- No runtime shader pack loading or `shaderpacks/` folder needed in the finished game
- Shader selection happens in-game, but all available shaders ship with the game

**This differs from:**
- **Vanilla Minecraft**: Uses only basic core shaders for fixed rendering pipeline, no deferred rendering or advanced effects
- **OptiFine/Iris**: Load shader packs dynamically from a `shaderpacks/` folder as ZIP files, allowing users to add/remove packs without recompiling

### Technical Capabilities

The implementation will transform MattMC from using vanilla Minecraft's basic shader system into a comprehensive shader architecture that supports OptiFine/Iris-style rendering features, including:
- Deferred rendering pipeline with G-buffers (multiple render targets)
- Shadow mapping with cascaded shadow maps
- Custom uniforms for world/player state (~200+ uniforms)
- In-game shader pack selection and configuration
- Per-pack configuration options
- Advanced post-processing effects (bloom, DOF, motion blur, etc.)
- Full rendering compatibility with OptiFine/Iris shader pack structure

**Target Compatibility**: Shader packs designed for OptiFine/Iris (Complimentary Reimagined r5.6.1+, BSL, Sildurs, SEUS PTGI, Vanilla Plus, etc.) can be adapted to MattMC's baked-in format by placing them unzipped in the resources directory.

**Estimated Implementation Scope**: ~15,000-25,000 lines of new code across 80-120 new classes plus modifications to existing rendering pipeline.

---

## Iris Mod In-Depth Research Analysis

This section contains comprehensive research into the Iris shader mod architecture (version 1.21.9, 668 Java files) to inform MattMC's shader system implementation.

### Iris Architecture Overview

Iris is a high-performance shader mod that provides OptiFine shader pack compatibility while offering superior performance through modern OpenGL techniques and Sodium integration. The reference implementation in `frnsrc/Iris-1.21.9/` demonstrates a sophisticated architecture that MattMC can adapt for its baked-in approach.

### Core Components

#### 1. Entry Point and Initialization (`Iris.java`)

**Initialization Flow:**
1. `onEarlyInitialize()` - First initialization before RenderSystem available
2. `onRenderSystemInit()` - Called after OpenGL context is ready
3. `onLoadingComplete()` - Final initialization on title screen display

**Key Insight:** Iris carefully stages initialization to ensure OpenGL is available before shader compilation.

#### 2. Shader Pack Structure (`shaderpack/ShaderPack.java`)

The `ShaderPack` class is the core data structure representing a loaded shader pack (~700 lines). It handles:

- **Properties Parsing**: Reads `shaders.properties` for pack configuration
- **Dimension Support**: Processes `dimension.properties` for world-specific shaders (Overworld, Nether, End)
- **Include Graph**: Builds dependency tree of all shader files via `IncludeGraph`
- **Options System**: Dynamic shader options and profiles
- **Source Provider**: Interface for shader compilation to access processed GLSL code

**Directory Structure (Iris expects):**
```
shaderpack/
├── shaders/              # GLSL shader files
│   ├── gbuffers_*.vsh/.fsh    # Geometry passes
│   ├── shadow.vsh/.fsh        # Shadow rendering
│   ├── composite*.fsh         # Post-processing
│   ├── final.fsh              # Final output
│   └── lib/                   # Include files
├── shaders.properties    # Main configuration
├── dimension.properties  # Per-dimension settings
├── lang/                 # Translations
└── textures/             # Custom textures
```

#### 3. Pipeline Architecture

##### IrisRenderingPipeline (`pipeline/IrisRenderingPipeline.java`)
The heart of shader rendering (~1,500 lines), implementing `WorldRenderingPipeline`:

**Key Components:**
- `RenderTargets` - Manages G-buffers (colortex0-15, depthtex, shadowtex)
- `ShadowRenderer` - Shadow map rendering system
- `CompositeRenderer` - Multi-pass post-processing
- `FinalPassRenderer` - Final screen output
- `CustomTextureManager` - Shader pack custom textures
- `ShaderStorageBufferHolder` - SSBO management for compute shaders

**Rendering Phases:**
```java
NONE → SKY → SHADOW → SETUP → 
TERRAIN_SOLID → TERRAIN_CUTOUT → TERRAIN_CUTOUT_MIPPED → 
TRANSLUCENT_TERRAIN → PARTICLES → ENTITIES → 
BLOCK_ENTITIES → HAND → COMPOSITE
```

#### 4. Shader Program Organization

##### Program Types (`shaderpack/programs/ProgramSet.java`)

**Geometry Passes (gbuffers_*):**
- `gbuffers_terrain` - Terrain rendering (blocks)
- `gbuffers_water` - Water/fluid rendering
- `gbuffers_textured` - Textured geometry
- `gbuffers_entities` - Entity rendering
- `gbuffers_hand` - First-person hand/held items
- `gbuffers_skybasic`, `gbuffers_skytextured` - Sky rendering

**Shadow Passes (shadow):**
- Same structure as gbuffers but render to shadow map from light's perspective

**Post-Processing Passes:**
- `composite*` - Deferred rendering composites (multiple passes possible: composite, composite1, composite2...)
- `deferred*` - Deferred lighting passes
- `prepare*` - Pre-render preparation
- `final` - Final screen output pass

**Compute Shaders:**
- `setup*` - Pre-render compute operations
- `begin*` - Per-frame initialization compute

#### 5. Uniform System (~200+ Uniforms)

The uniform system (`uniforms/`) provides shader-accessible data:

**Categories:**
- **Time**: `frameTimeCounter`, `worldTime`, `sunAngle`, `moonAngle`
- **Camera**: `cameraPosition`, `gbufferProjection`, `gbufferModelView`, `gbufferProjectionInverse`
- **World**: `fogColor`, `skyColor`, `rainStrength`, `wetness`, `biomeTemperature`, `biomePrecipitation`
- **Player**: `eyeBrightnessSmooth`, `entityColor`, `heldItemId`, `heldBlockLightValue`
- **System**: `viewWidth`, `viewHeight`, `aspectRatio`, `frameCounter`, `frameTime`
- **Lighting**: `shadowLightPosition`, `shadowModelView`, `shadowProjection`
- **Matrices**: All transformation matrices (model, view, projection, inverse, transpose)

**Implementation Pattern:**
```java
public interface CommonUniforms {
    void addDynamicUniforms(UniformHolder holder, FrameUpdateNotifier updateNotifier);
    void addStaticUniforms(UniformHolder holder);
}
```

#### 6. Render Target System

##### RenderTargets (`targets/RenderTargets.java`)

**G-Buffer Structure:**
- `colortex0-15` - 16 available color textures for shader data
- `depthtex0` - Hardware depth buffer copy
- `depthtex1` - Terrain-only depth (no transparents)
- `depthtex2` - Translucent depth
- `shadowtex0`, `shadowtex1` - Shadow maps
- `shadowcolor0`, `shadowcolor1` - Shadow color buffers
- `noisetex` - Noise texture for dithering/randomness

**Configuration via shaders.properties:**
```properties
# Example render target configuration
colortex0 = RGBA16F
colortex1 = RGB16
depthtex0 = DEPTH_COMPONENT32F
shadowMapResolution = 2048
```

#### 7. Include Processing System

##### IncludeGraph (`shaderpack/include/IncludeGraph.java`)

**Features:**
- Resolves `#include` directives in GLSL files
- Builds dependency graph with cycle detection
- Preprocessor handles `#ifdef`, `#ifndef`, `#define`, `#undef`
- Supports nested includes
- Caches processed files for performance

**Example Usage in Shader:**
```glsl
#include "/lib/settings.glsl"
#include "/lib/common.glsl"
```

#### 8. Mixin Integration Strategy

Iris uses Mixin extensively to hook into Minecraft's rendering pipeline without modifying vanilla code:

**Key Injection Points:**
- `LevelRenderer` - World rendering control
- `GameRenderer` - Camera and view management
- `RenderType` - Geometry batching and render states
- `PostChain` - Post-processing pipeline
- `Framebuffer` - Framebuffer operations

**Sodium Compatibility (`compat/sodium/mixin/`):**
- Specialized mixins for Sodium mod integration
- Vertex format compatibility
- Chunk rendering integration
- Ensures Iris + Sodium work together

#### 9. Vertex Format Extensions

##### Extended Attributes (`vertices/IrisVertexFormats.java`)

Iris adds custom vertex attributes that shaders can use:

- `mc_Entity` - Entity/block ID for material detection
- `mc_midTexCoord` - Texture coordinate center for sprite detection
- `at_tangent` - Tangent vector for normal mapping
- Block metadata and flags

**Why This Matters:** OptiFine/Iris shader packs expect these attributes to be available for advanced effects like PBR (physically-based rendering).

#### 10. Shadow Rendering System

##### ShadowRenderer (`shadows/ShadowRenderer.java`)

**Features:**
- Renders scene from light's perspective
- Creates shadow map textures (configurable resolution)
- Supports cascaded shadow maps for quality
- Culling optimization for shadow geometry
- Configurable shadow distance

**Configuration:**
```properties
shadowMapResolution = 2048    # Shadow texture size
shadowDistance = 128.0         # Render distance for shadows
shadowIntervalSize = 2.0       # Cascade sizing
```

#### 11. Shader Pack Options System

##### Dynamic Options (`shaderpack/option/`)

**Features:**
- Boolean toggles (ON/OFF)
- Numeric sliders (range-based values)
- Dropdown selections (multiple choices)
- Profile system (preset configurations)
- Conditional options (dependencies)

**Defined in Shader Files:**
```glsl
// In shader source code
const int shadowMapResolution = 2048; // [1024 2048 4096 8192]
const float sunPathRotation = 0.0;    // [-60.0 -30.0 0.0 30.0 60.0]
#define WAVING_GRASS // Toggle feature
```

**Parsed and Presented in UI:**
Iris GUI dynamically builds options menu based on what shaders define.

#### 12. Loading Flow

**Iris Shader Pack Loading:**
```
User Selection in GUI
    ↓
ShaderpackDirectoryManager.scanForPacks() (filesystem scan)
    ↓
ShaderPack(path) constructor
    ↓
IncludeGraph.build() - Parse all files, resolve includes
    ↓
ShaderPackOptions.parse() - Read options from shader comments
    ↓
ShaderProperties.parse() - Read shaders.properties
    ↓
ProgramSet.create() - Compile GLSL to OpenGL programs
    ↓
IrisRenderingPipeline(programSet) - Build rendering pipeline
    ↓
Pipeline activated for rendering
```

#### 13. Rendering Loop Integration

**How Iris Intercepts Minecraft Rendering:**
```
Minecraft.renderFrame()
    ↓
GameRenderer.render()
    ↓
LevelRenderer.renderLevel()
    ↓
[Iris Mixin Hook] pipeline.beginWorldRendering()
    ↓
pipeline.renderShadows() (if shadow pass enabled)
    │   └─> Render geometry to shadow FBO using shadow programs
    ↓
[Phase: TERRAIN_SOLID] 
    │   └─> Use gbuffers_terrain for terrain
    ↓
[Phase: TRANSLUCENT_TERRAIN]
    │   └─> Use gbuffers_water for water/translucents
    ↓
[Phase: ENTITIES]
    │   └─> Use gbuffers_entities for mobs/players
    ↓
[Phase: HAND]
    │   └─> Use gbuffers_hand for first-person hand
    ↓
pipeline.finalizeWorldRendering()
    ↓
[Phase: COMPOSITE] Run composite passes
    │   └─> composite.fsh, composite1.fsh, ... (post-processing)
    ↓
[Phase: FINAL] Run final pass
    │   └─> final.fsh (output to screen)
    ↓
Result displayed to player
```

### Critical Differences: Iris vs. MattMC Baked-In

#### Iris (Runtime Loading):
1. **Discovery**: Filesystem scan of `shaderpacks/` folder
2. **Format**: ZIP files extracted to temp directory during load
3. **Loading**: Fully dynamic - users add/remove packs without recompiling game
4. **I/O**: Continuous file system access during loading phase
5. **Storage**: External files, not embedded in JAR
6. **Management**: `ShaderpackDirectoryManager` handles filesystem operations

#### MattMC (Baked-In Approach):
1. **Discovery**: ResourceManager scan of JAR resources at runtime
2. **Format**: Unzipped directories in `src/main/resources/assets/minecraft/shaders/`
3. **Loading**: Dynamic discovery but fixed set at compile-time
4. **I/O**: ResourceManager API (no temp files, no extraction)
5. **Storage**: Compiled directly into JAR
6. **Management**: Custom `ShaderPackRepository` using ResourceManager

### Adaptation Strategy for MattMC

#### What to Keep from Iris:
1. **Pipeline Architecture** - Phase-based rendering (proven pattern)
2. **Program Organization** - gbuffers, shadow, composite, final structure
3. **Uniform System** - The ~200+ uniforms that shader packs expect
4. **Render Targets** - colortex0-15, depthtex, shadowtex structure
5. **Include Processing** - GLSL `#include` directive support
6. **Properties Parsing** - shaders.properties, dimension.properties format
7. **Options System** - Dynamic shader options and profiles
8. **Vertex Attributes** - Extended attributes like mc_Entity, at_tangent

#### What to Change for Baked-In:
1. **Discovery Mechanism**: 
   - **Iris**: `Files.walk(shaderpacksDir)` filesystem traversal
   - **MattMC**: `ResourceManager.listResources("assets/minecraft/shaders/")` scan
   
2. **Pack Loading**:
   - **Iris**: ZIP extraction, filesystem `Files.readAllBytes()`
   - **MattMC**: `ResourceManager.getResource()` for direct access
   
3. **ZIP Handling**:
   - **Iris**: Needs `ZipFileSystem` and temp directory extraction
   - **MattMC**: Already unzipped in resources, no extraction needed
   
4. **Path Resolution**:
   - **Iris**: `Path` objects pointing to filesystem
   - **MattMC**: `ResourceLocation` objects pointing to JAR resources
   
5. **Caching**:
   - **Iris**: Caches on filesystem between runs
   - **MattMC**: No need - resources are in JAR, always available

#### Critical Implementation Insights

**1. G-Buffer Usage Pattern:**
```
colortex0: Main color output (what vanilla would render)
colortex1: Normals (for lighting calculations)
colortex2: Specular/Roughness (for PBR)
colortex3: Lightmaps (for custom lighting)
colortex4+: Pack-specific usage (varies by shader)
```

**2. Shadow Map Technique:**
- Render scene from sun/moon direction into shadow FBO
- Store depth in shadowtex0, optional color in shadowcolor0
- During main pass, sample shadow map to determine if pixel is in shadow
- Advanced: Cascaded shadow maps for better quality at varying distances

**3. Composite Pass Chaining:**
```
gbuffers passes write to colortex0-N
    ↓
composite.fsh reads colortex0-N, writes to different colortex
    ↓
composite1.fsh reads previous outputs, writes to more colortex
    ↓
composite2.fsh (if exists) continues processing
    ↓
final.fsh reads all, writes directly to screen framebuffer
```

**4. Uniform Update Timing:**
- **Static uniforms**: Set once during shader program init
- **Per-frame uniforms**: Updated every frame (time, camera position)
- **Per-render-type uniforms**: Updated when switching programs (entity ID, block ID)
- **Lazy uniforms**: Only updated when value changes (world time, rain strength)

**5. Mixin Insertion Points:**

Iris carefully chooses where to inject code:
- `LevelRenderer.renderLevel()` - Start/end of world rendering
- `RenderType.end()` - After each render type batch completes
- `GameRenderer.renderLevel()` - Composite and final passes
- Vertex format setup - Add extended attributes

For MattMC: We have direct code access, so no mixins needed - but we can implement at the same logical points.

### Key Files to Study in Detail

When implementing MattMC's shader system, these Iris files provide the best reference:

1. **ShaderPack.java** - Core pack representation and loading
2. **IrisRenderingPipeline.java** - Complete rendering implementation
3. **ProgramSet.java** - Shader program organization
4. **RenderTargets.java** - G-buffer management
5. **ShaderProperties.java** - Properties file parsing
6. **IncludeProcessor.java** - GLSL include handling
7. **CommonUniforms.java** - Uniform value providers
8. **ShadowRenderer.java** - Shadow map rendering
9. **CompositeRenderer.java** - Post-processing passes

### Performance Considerations from Iris

**Optimizations Iris Uses:**
1. **Parallel shader compilation** - Uses GL_KHR_parallel_shader_compile when available
2. **Program caching** - Caches compiled programs between runs
3. **Smart uniform updates** - Only updates uniforms that changed
4. **Framebuffer reuse** - Recycles FBOs between frames
5. **Texture pooling** - Reuses texture objects
6. **Lazy initialization** - Delays heavy operations until needed
7. **Sodium integration** - Leverages Sodium's chunk rendering optimizations

**MattMC Should Adopt:**
- Parallel compilation support
- Efficient uniform update system
- FBO/texture reuse patterns
- Lazy initialization where applicable

### Conclusion of Iris Research

Iris represents approximately 25,000 lines of carefully architected shader rendering code. Its architecture is battle-tested with thousands of users and dozens of shader packs. The key insight is its phase-based rendering pipeline that intercepts Minecraft's rendering at strategic points, redirects output to G-buffers, and processes those buffers through multiple post-processing passes.

For MattMC's baked-in approach, the core rendering architecture can remain very similar to Iris, but the loading mechanism must be adapted to use Minecraft's ResourceManager system instead of filesystem I/O. The shader pack structure, program organization, uniform system, and G-buffer layout should closely follow Iris's patterns to maximize compatibility with existing OptiFine/Iris shader packs.

**Reference Location:** Complete Iris 1.21.9 source code is available in `frnsrc/Iris-1.21.9/` for detailed study during implementation.

---

## How to Add Shader Packs to MattMC

Since MattMC uses a baked-in shader system, shader packs must be added to the source code before compilation:

### Step-by-Step Guide

1. **Obtain a shader pack** (e.g., Complimentary Reimagined, BSL Shaders)
   - Download from CurseForge, Modrinth, or shader pack website
   - Usually comes as a ZIP file

2. **Unzip the shader pack**
   - Extract the contents to a temporary folder
   - Verify it contains a `shaders/` directory with `.vsh` and `.fsh` files

3. **Copy to MattMC resources**
   ```bash
   # Create a directory for the shader pack
   mkdir -p src/main/resources/assets/minecraft/shaders/complementary_reimagined
   
   # Copy the shader pack contents
   cp -r /path/to/extracted/shader/* src/main/resources/assets/minecraft/shaders/complementary_reimagined/
   ```

4. **Verify structure**
   ```
   src/main/resources/assets/minecraft/shaders/
   └── complementary_reimagined/
       ├── shaders/
       │   ├── gbuffers_terrain.fsh
       │   ├── gbuffers_terrain.vsh
       │   ├── composite.fsh
       │   └── ... (all shader files)
       ├── textures/ (optional)
       └── pack.mcmeta (optional)
   ```

5. **Recompile the game**
   ```bash
   ./gradlew build
   ```

6. **Launch and select**
   - Start MattMC
   - Go to Video Settings → Shaders
   - Your shader pack will be listed automatically (dynamically discovered)
   - Select it and enjoy!

### Important Notes

- ✅ **No code changes required** - the system dynamically discovers shader packs
- ✅ **Multiple packs supported** - add as many as you want to the shaders directory
- ✅ **Automatic discovery** - all directories in `assets/minecraft/shaders/` (except core, post, include) are scanned
- ❌ **Requires recompilation** - users cannot add packs at runtime
- ⚠️ **JAR size increases** - each shader pack adds to the final JAR size

### Example Structure with Multiple Packs

```
src/main/resources/assets/minecraft/shaders/
├── core/                          # Vanilla core shaders (don't modify)
├── post/                          # Vanilla post-processing (don't modify)
├── include/                       # Vanilla includes (don't modify)
├── complementary_reimagined/      # Custom shader pack 1
│   └── shaders/
│       ├── gbuffers_*.fsh
│       └── ...
├── bsl_shaders/                   # Custom shader pack 2
│   └── shaders/
│       └── ...
└── vanilla_plus/                  # Custom shader pack 3
    └── shaders/
        └── ...
```

---

## Table of Contents

1. [Current State Analysis](#current-state-analysis)
2. [Shader Pack Architecture Deep Dive](#shader-pack-architecture-deep-dive)
3. [Technical Requirements](#technical-requirements)
4. [Architecture Overview](#architecture-overview)
5. [Phase 1: Foundation Infrastructure](#phase-1-foundation-infrastructure)
6. [Phase 2: Shader Pack Loading System](#phase-2-shader-pack-loading-system)
7. [Phase 3: Rendering Pipeline Transformation](#phase-3-rendering-pipeline-transformation)
8. [Phase 4: G-Buffer and Deferred Rendering](#phase-4-g-buffer-and-deferred-rendering)
9. [Phase 5: Shadow Mapping System](#phase-5-shadow-mapping-system)
10. [Phase 6: Uniforms and Shader Interface](#phase-6-uniforms-and-shader-interface)
11. [Phase 7: Configuration and UI Integration](#phase-7-configuration-and-ui-integration)
12. [Phase 8: Advanced Features](#phase-8-advanced-features)
13. [Phase 9: Performance Optimization](#phase-9-performance-optimization)
14. [Phase 10: Testing and Validation](#phase-10-testing-and-validation)
15. [Implementation Timeline](#implementation-timeline)
16. [Risk Assessment and Mitigation](#risk-assessment-and-mitigation)
17. [Appendices](#appendices)

---

## Current State Analysis

### Existing Shader Infrastructure in MattMC

Based on analysis of the MattMC codebase (Minecraft Java 1.21.10 fork), the current shader system includes:

#### 1. **Core Shader System** (`net.minecraft.client.renderer.ShaderManager`)
- **Purpose**: Manages vanilla Minecraft's built-in shaders for basic rendering
- **Location**: Core shaders in `src/main/resources/assets/minecraft/shaders/core/`
- **Functionality**:
  - Loads vertex shaders (`.vsh`) and fragment shaders (`.fsh`)
  - Uses GLSL preprocessor (`GlslPreprocessor`) for `#moj_import` directives
  - Compiles shaders via `com.mojang.blaze3d.shaders` package
  - Fixed shader pipeline for specific render types (terrain, entities, particles, GUI, etc.)
- **Limitations**:
  - No dynamic shader pack loading
  - Fixed shader programs (no gbuffers_* architecture)
  - No G-buffer support
  - No shadow mapping capabilities
  - Limited uniforms (only basic transformation matrices)

#### 2. **Post-Processing System** (`PostChain`, `PostPass`, `PostChainConfig`)
- **Purpose**: Applies post-processing effects like blur, invert, creeper vision
- **Location**: Post-effect definitions in `src/main/resources/assets/minecraft/post_effect/`
- **Functionality**:
  - JSON-based effect configuration
  - Multiple passes with intermediate textures
  - Screen-space effects only
  - Used for specific game effects (spider vision, creeper flash, entity outlines)
- **Limitations**:
  - Hardcoded effect selection
  - No shader pack integration
  - Limited to full-screen post-processing
  - Cannot modify main rendering pipeline

#### 3. **Rendering Pipeline** (`com.mojang.blaze3d.pipeline.RenderPipeline`)
- **Purpose**: Defines rendering state and shader programs
- **Functionality**:
  - Manages render state (blend modes, depth testing, culling)
  - Links vertex/fragment shaders
  - Defines uniforms and samplers
  - Vertex format specifications
- **Limitations**:
  - Static pipeline definitions
  - No runtime shader switching
  - Fixed render pass architecture

#### 4. **Render Targets and Framebuffers** (`RenderTarget`, `MainTarget`)
- **Purpose**: Off-screen rendering for effects and screenshots
- **Functionality**:
  - Framebuffer object (FBO) management
  - Color and depth attachments
  - Used for post-processing chains
- **Limitations**:
  - Single main render target
  - No G-buffer architecture
  - No multiple render target (MRT) support for deferred rendering

### Gap Analysis: What's Missing for Advanced Shader Support

To achieve OptiFine/Iris-style rendering capabilities with MattMC's baked-in architecture, the following features need to be implemented:

| Feature | Current State | Required State |
|---------|---------------|----------------|
| **Shader Pack Discovery** | None | Dynamic discovery of shader packs from `assets/minecraft/shaders/` at runtime |
| **G-Buffers** | Single color + depth buffer | Multiple color attachments (8+) for deferred data |
| **Shadow Mapping** | None | Shadow pass with depth textures, cascaded shadow maps |
| **Gbuffers Programs** | Fixed core shaders | Dynamic gbuffers_* programs per geometry type |
| **Composite Passes** | Basic post-processing | Multiple composite stages with access to all buffers |
| **Uniforms System** | ~20 basic uniforms | 200+ uniforms for world/player/camera state |
| **Shader Properties** | None | Parse `shaders.properties` for configuration |
| **Custom Textures** | None | Custom texture loading per shader pack |
| **Dimension-Specific Shaders** | None | Per-dimension shader programs |
| **Block/Entity ID Buffers** | None | Entity and block ID encoding for advanced effects |
| **Dynamic Shader Switching** | Compile-time only | Runtime shader pack selection (from baked-in options) |
| **Shader Options Menu** | None | In-game UI for per-pack settings |

**Note**: Unlike OptiFine/Iris, MattMC shaders are **discovered at runtime but bundled at compile time**. The game scans `assets/minecraft/shaders/` for shader pack directories and makes them available for in-game selection, but all shaders ship with the compiled JAR.

---

## Shader Pack Architecture Deep Dive

### Understanding OptiFine/Iris Shader Pack Format (Adapted for MattMC)

In OptiFine/Iris, shader packs are ZIP files placed in a `shaderpacks/` folder. In MattMC, shader packs use the same internal structure but are **unzipped and stored directly in the resources directory** at `src/main/resources/assets/minecraft/shaders/`. Each shader pack is a subdirectory with the following structure:

```
src/main/resources/assets/minecraft/shaders/
├── complementary_reimagined/     # Shader pack directory (unzipped)
│   ├── shaders/
│   ├── gbuffers_basic.fsh              # Geometry pass shaders
│   ├── gbuffers_basic.vsh
│   ├── gbuffers_terrain.fsh
│   ├── gbuffers_terrain.vsh
│   ├── gbuffers_terrain_solid.fsh      # Iris-specific variants
│   ├── gbuffers_terrain_solid.vsh
│   ├── gbuffers_water.fsh
│   ├── gbuffers_water.vsh
│   ├── gbuffers_entities.fsh
│   ├── gbuffers_entities.vsh
│   ├── gbuffers_entities_translucent.fsh
│   ├── gbuffers_entities_translucent.vsh
│   ├── gbuffers_skybasic.fsh
│   ├── gbuffers_skybasic.vsh
│   ├── gbuffers_skytextured.fsh
│   ├── gbuffers_skytextured.vsh
│   ├── gbuffers_clouds.fsh
│   ├── gbuffers_clouds.vsh
│   ├── gbuffers_particles.fsh
│   ├── gbuffers_particles.vsh
│   ├── gbuffers_hand.fsh
│   ├── gbuffers_hand.vsh
│   ├── gbuffers_weather.fsh
│   ├── gbuffers_weather.vsh
│   ├── gbuffers_beaconbeam.fsh
│   ├── gbuffers_beaconbeam.vsh
│   ├── gbuffers_armor_glint.fsh
│   ├── gbuffers_armor_glint.vsh
│   ├── gbuffers_textured.fsh           # Fallback for textured geometry
│   ├── gbuffers_textured.vsh
│   ├── gbuffers_textured_lit.fsh
│   ├── gbuffers_textured_lit.vsh
│   ├── shadow.fsh                      # Shadow mapping pass
│   ├── shadow.vsh
│   ├── shadowcomp.fsh                  # Shadow composition (optional)
│   ├── shadowcomp.vsh
│   ├── prepare.fsh                     # Pre-composite preparation
│   ├── prepare.vsh
│   ├── composite.fsh                   # Post-processing composites
│   ├── composite.vsh
│   ├── composite1.fsh                  # Multiple composite stages
│   ├── composite1.vsh
│   ├── composite2.fsh
│   ├── composite2.vsh
│   ├── ...
│   ├── composite15.fsh                 # Up to 16 composite passes
│   ├── composite15.vsh
│   ├── deferred.fsh                    # Deferred lighting pass
│   ├── deferred.vsh
│   ├── deferred1.fsh                   # Multiple deferred passes
│   ├── deferred1.vsh
│   ├── ...
│   ├── final.fsh                       # Final output pass
│   ├── final.vsh
│   ├── shaders.properties              # Main configuration file
│   ├── shaders.dimensions              # Dimension-specific configs
│   ├── block.properties                # Block ID mapping
│   ├── entity.properties               # Entity ID mapping
│   ├── item.properties                 # Item rendering customization
│   ├── lang/
│   │   ├── en_US.lang                  # Localization for options
│   │   └── ...
│   ├── include/                        # Shared GLSL code
│   │   ├── common.glsl
│   │   ├── lighting.glsl
│   │   ├── shadowDistortion.glsl
│   │   ├── atmospherics.glsl
│   │   └── ...
│   │   └── world-1/                        # Dimension-specific shaders
│   │       └── composite.fsh
│   ├── textures/                           # Custom textures
│   │   ├── noise.png
│   │   ├── waterNormal.png
│   │   ├── clouds.png
│   │   └── ...
│   └── pack.mcmeta                         # Pack metadata (optional)
│
├── bsl_shaders/                      # Another shader pack
│   ├── shaders/
│   │   ├── gbuffers_terrain.fsh
│   │   └── ...
│   └── pack.mcmeta
│
└── vanilla_plus/                     # Another shader pack
    ├── shaders/
    └── ...

# Vanilla Minecraft shaders remain in their original locations:
# src/main/resources/assets/minecraft/shaders/core/     - Vanilla core shaders
# src/main/resources/assets/minecraft/shaders/post/     - Vanilla post-processing
# src/main/resources/assets/minecraft/shaders/include/  - Vanilla includes
```

**Key Differences from OptiFine/Iris:**
- **Storage**: Unzipped directories in resources, not ZIP files in a runtime folder
- **Discovery**: Dynamically scanned from `assets/minecraft/shaders/` at game startup
- **Bundling**: All shader packs ship with the compiled game JAR (baked-in)
- **Modification**: To add/modify shaders, you must edit resources and recompile the game
- **No Runtime Loading**: Users cannot add shader packs without recompiling

### Rendering Pipeline Flow

The shader pack rendering pipeline (inspired by OptiFine/Iris) operates in distinct stages:

```
┌─────────────────────────────────────────────────────────────────┐
│                    SHADOW PASS (Light POV)                      │
│  Programs: shadow.vsh/fsh                                       │
│  Output: shadowtex0, shadowtex1, shadowcolor0, shadowcolor1     │
│  Purpose: Generate shadow maps from light source perspective    │
└─────────────────────────────────────────────────────────────────┘
                               ↓
┌─────────────────────────────────────────────────────────────────┐
│                   PREPARE PASS (Optional)                       │
│  Programs: prepare.vsh/fsh                                      │
│  Purpose: Pre-processing before main geometry render            │
└─────────────────────────────────────────────────────────────────┘
                               ↓
┌─────────────────────────────────────────────────────────────────┐
│               GBUFFERS PASS (Geometry Rendering)                │
│  Programs: gbuffers_*.vsh/fsh (terrain, water, entities, etc.)  │
│  Output: colortex0-7, depthtex0-2, normals, specular, etc.     │
│  Purpose: Render all world geometry to G-buffers                │
│  - Solid terrain (gbuffers_terrain, gbuffers_terrain_solid)    │
│  - Transparent terrain (gbuffers_terrain_cutout)                │
│  - Water (gbuffers_water)                                       │
│  - Entities (gbuffers_entities, gbuffers_entities_translucent)  │
│  - Sky (gbuffers_skybasic, gbuffers_skytextured)                │
│  - Particles (gbuffers_particles)                               │
│  - Hand/held items (gbuffers_hand)                              │
│  - Weather (gbuffers_weather)                                   │
└─────────────────────────────────────────────────────────────────┘
                               ↓
┌─────────────────────────────────────────────────────────────────┐
│              DEFERRED PASS (Deferred Lighting)                  │
│  Programs: deferred.vsh/fsh, deferred1-15.vsh/fsh              │
│  Input: All G-buffers, shadow maps                              │
│  Purpose: Calculate lighting using deferred rendering           │
└─────────────────────────────────────────────────────────────────┘
                               ↓
┌─────────────────────────────────────────────────────────────────┐
│           COMPOSITE PASS (Post-Processing Effects)              │
│  Programs: composite.vsh/fsh, composite1-15.vsh/fsh            │
│  Input: G-buffers, deferred results, all previous outputs       │
│  Purpose: Apply effects (bloom, DOF, motion blur, tone map)     │
│  - Bloom extraction and blur                                    │
│  - Depth of field                                               │
│  - Motion blur                                                  │
│  - Volumetric fog and clouds                                    │
│  - Ambient occlusion                                            │
│  - Color grading                                                │
└─────────────────────────────────────────────────────────────────┘
                               ↓
┌─────────────────────────────────────────────────────────────────┐
│                  FINAL PASS (Screen Output)                     │
│  Programs: final.vsh/fsh                                        │
│  Purpose: Final tone mapping and output to screen               │
└─────────────────────────────────────────────────────────────────┘
```

### G-Buffer Architecture

Modern shader packs use **deferred rendering** with multiple render targets (MRT):

| Buffer Name | Type | Format | Purpose |
|-------------|------|--------|---------|
| **colortex0** | Color | RGBA8/RGBA16F | Main color buffer (albedo) |
| **colortex1** | Color | RGBA16F | Lighting accumulation / Bright areas for bloom |
| **colortex2** | Color | RGBA16 | Normal vectors (world space or view space) |
| **colortex3** | Color | RGBA16 | Specular/roughness/metalness (PBR data) |
| **colortex4** | Color | RGBA16F | Custom data (sky color, AO, fog) |
| **colortex5** | Color | RGBA16F | Volumetric data |
| **colortex6** | Color | RGBA16 | Custom effects |
| **colortex7** | Color | RGBA16 | Custom effects |
| **colortex8-15** | Color | Various | Iris extended buffers |
| **depthtex0** | Depth | DEPTH24 | Main depth buffer |
| **depthtex1** | Depth | DEPTH24 | Translucent depth (no transparency) |
| **depthtex2** | Depth | DEPTH24 | Alternative depth |
| **shadowtex0** | Depth | DEPTH24 | Shadow map (all geometry) |
| **shadowtex1** | Depth | DEPTH24 | Shadow map (opaque only) |
| **shadowcolor0** | Color | RGBA16F | Colored shadow data |
| **shadowcolor1** | Color | RGBA16F | Additional shadow data |
| **noisetex** | Color | RGB8 | Noise texture for dithering |

### Uniform Variables

Shader packs rely on hundreds of uniform variables provided by the shader loader. Major categories:

#### **Transformation Matrices**
```glsl
uniform mat4 gbufferModelView;        // Model-view matrix
uniform mat4 gbufferModelViewInverse;
uniform mat4 gbufferProjection;        // Projection matrix
uniform mat4 gbufferProjectionInverse;
uniform mat4 gbufferPreviousModelView; // For motion blur
uniform mat4 gbufferPreviousProjection;
uniform mat4 shadowModelView;          // Shadow pass matrices
uniform mat4 shadowProjection;
uniform mat4 shadowModelViewInverse;
uniform mat4 shadowProjectionInverse;
```

#### **Time and World State**
```glsl
uniform float frameTime;               // Frame delta time
uniform float frameTimeCounter;        // Accumulated time
uniform int worldTime;                 // Minecraft ticks (0-24000)
uniform float sunAngle;                // Sun position (0.0-1.0)
uniform float rainStrength;            // Rain intensity
uniform float wetness;                 // Wetness accumulation
uniform int moonPhase;                 // Moon phase (0-7)
uniform int isEyeInWater;              // 0=air, 1=water, 2=lava
uniform float nightVision;             // Night vision effect strength
uniform float blindness;               // Blindness effect strength
```

#### **Camera and Player**
```glsl
uniform vec3 cameraPosition;           // Camera world position
uniform vec3 previousCameraPosition;   // For motion blur
uniform float near;                    // Near clip plane
uniform float far;                     // Far clip plane
uniform float aspectRatio;             // Screen aspect ratio
uniform float viewWidth;               // Viewport width
uniform float viewHeight;              // Viewport height
uniform int heldItemId;                // Held item ID
uniform int heldBlockLightValue;       // Light emission of held item
uniform int heldItemId2;               // Off-hand item
uniform int heldBlockLightValue2;
```

#### **Block and Entity Data**
```glsl
uniform sampler2D lightmap;            // Minecraft light map
uniform sampler2D texture;             // Block/entity texture atlas
uniform sampler2D normals;             // Normal map
uniform sampler2D specular;            // Specular map
uniform int entityId;                  // Current entity ID (vertex shader)
uniform int blockEntityId;             // Block entity ID
```

#### **Custom Properties**
```glsl
// Defined in shaders.properties
#define SHADOW_QUALITY 2
#define BLOOM_STRENGTH 1.0
#define VOLUMETRIC_FOG
```

---

## Technical Requirements

### MattMC Shader Architecture Principles

**Baked-In Design Philosophy:**
1. **Compile-Time Integration**: Shaders are compiled into the game JAR, not loaded at runtime
2. **Dynamic Discovery**: The game scans `assets/minecraft/shaders/` to find available shader packs (not hardcoded)
3. **Resource-Based Loading**: Uses Minecraft's `ResourceManager` for shader access (same as textures, sounds, etc.)
4. **In-Game Selection**: Players can switch between baked-in shaders via video settings
5. **No User-Added Packs**: Users cannot add new shader packs without recompiling the game

**Advantages of This Approach:**
- ✅ **No Runtime File I/O**: Faster shader loading (already in JAR)
- ✅ **Guaranteed Availability**: All shaders tested and verified before distribution
- ✅ **Simplified Distribution**: Single JAR contains everything
- ✅ **Better Security**: No arbitrary shader code loaded from user filesystem
- ✅ **Consistent Behavior**: No shader pack compatibility issues across installations

**Trade-offs:**
- ❌ **No User Customization**: Players can't add their own shader packs
- ❌ **Requires Recompilation**: Adding shaders requires rebuilding the game
- ⚠️ **Larger JAR Size**: All shaders included in distribution

### Minimum System Requirements
- **OpenGL**: 4.3+ (for compute shaders in advanced packs)
- **GLSL Version**: 330 core minimum, 420+ recommended
- **GPU**: Support for:
  - Multiple render targets (MRT) - at least 8 color attachments
  - Framebuffer objects (FBO) with depth attachments
  - Depth textures for shadow mapping
  - Floating-point textures (RGBA16F, RGBA32F)
  - Vertex array objects (VAO)
  - Uniform buffer objects (UBO) for efficient uniform management
  - Shader storage buffer objects (SSBO) for advanced features

### Dependencies and Libraries
- **LWJGL 3.3.3**: Already present in MattMC
  - OpenGL bindings
  - GLFW for window/input management
  - STB for image loading
- **JOML 1.10.5**: Matrix mathematics library (already present)
- **FastUtil 8.5.12**: Efficient collections (already present)
- **Gson 2.11.0**: JSON parsing for shader configuration (already present)

### New Code Modules Required

1. **Shader Pack Management** (~3,000 lines)
   - `net.minecraft.client.renderer.shader.pack.ShaderPackLoader`
   - `net.minecraft.client.renderer.shader.pack.ShaderPack`
   - `net.minecraft.client.renderer.shader.pack.ShaderPackRepository`
   - `net.minecraft.client.renderer.shader.pack.ShaderPackConfig`
   
2. **G-Buffer System** (~2,000 lines)
   - `net.minecraft.client.renderer.shader.gbuffer.GBufferManager`
   - `net.minecraft.client.renderer.shader.gbuffer.ColorTextureManager`
   - `net.minecraft.client.renderer.shader.gbuffer.DepthTextureManager`
   
3. **Shadow Mapping** (~2,500 lines)
   - `net.minecraft.client.renderer.shader.shadow.ShadowRenderer`
   - `net.minecraft.client.renderer.shader.shadow.ShadowMapManager`
   - `net.minecraft.client.renderer.shader.shadow.CascadedShadowMap`
   
4. **Shader Program Management** (~4,000 lines)
   - `net.minecraft.client.renderer.shader.program.GBuffersProgram`
   - `net.minecraft.client.renderer.shader.program.CompositeProgram`
   - `net.minecraft.client.renderer.shader.program.ShadowProgram`
   - `net.minecraft.client.renderer.shader.program.ShaderProgramCache`
   
5. **Uniform System** (~3,500 lines)
   - `net.minecraft.client.renderer.shader.uniform.UniformManager`
   - `net.minecraft.client.renderer.shader.uniform.MatrixUniforms`
   - `net.minecraft.client.renderer.shader.uniform.WorldStateUniforms`
   - `net.minecraft.client.renderer.shader.uniform.CameraUniforms`
   
6. **Shader Properties Parser** (~1,500 lines)
   - `net.minecraft.client.renderer.shader.config.ShaderPropertiesParser`
   - `net.minecraft.client.renderer.shader.config.ShaderOption`
   - `net.minecraft.client.renderer.shader.config.ShaderProfile`
   
7. **Render Pipeline Integration** (~4,000 lines)
   - Modifications to `LevelRenderer`
   - Modifications to `GameRenderer`
   - New `ShaderRenderPipeline`
   - Integration with existing `RenderType` system
   
8. **UI Components** (~1,500 lines)
   - `net.minecraft.client.gui.screens.ShaderPackSelectionScreen`
   - `net.minecraft.client.gui.screens.ShaderOptionsScreen`
   - Video settings integration

---

## Architecture Overview

### High-Level System Architecture

```
┌─────────────────────────────────────────────────────────────────────────┐
│                          MattMC Shader System                            │
├─────────────────────────────────────────────────────────────────────────┤
│                                                                           │
│  ┌─────────────────────────────────────────────────────────────────┐   │
│  │                   Shader Pack Repository                         │   │
│  │  - Scans assets/minecraft/shaders/ in JAR resources             │   │
│  │  - Dynamically discovers available shader packs                 │   │
│  │  - Provides pack selection API                                   │   │
│  └──────────────────────────┬───────────────────────────────────────┘   │
│                             ↓                                             │
│  ┌─────────────────────────────────────────────────────────────────┐   │
│  │                    Shader Pack Loader                            │   │
│  │  - Loads shaders from ResourceManager (JAR resources)           │   │
│  │  - Parses shaders.properties                                     │   │
│  │  - Loads GLSL shader files via ResourceLocation                 │   │
│  │  - Processes #include directives                                 │   │
│  │  - Handles dimension-specific overrides                          │   │
│  └──────────────────────────┬───────────────────────────────────────┘   │
│                             ↓                                             │
│  ┌─────────────────────────────────────────────────────────────────┐   │
│  │                  Shader Program Compiler                         │   │
│  │  - Compiles vertex shaders                                       │   │
│  │  - Compiles fragment shaders                                     │   │
│  │  - Links shader programs                                         │   │
│  │  - Validates program compatibility                               │   │
│  │  - Caches compiled programs                                      │   │
│  └──────────────────────────┬───────────────────────────────────────┘   │
│                             ↓                                             │
│  ┌─────────────────────────────────────────────────────────────────┐   │
│  │                   Rendering Pipeline Manager                     │   │
│  │  ┌───────────────────────────────────────────────────────────┐  │   │
│  │  │  Shadow Pass                                               │  │   │
│  │  │  - Shadow map generation from light POV                    │  │   │
│  │  │  - Cascaded shadow maps for large view distances          │  │   │
│  │  └───────────────────────────────────────────────────────────┘  │   │
│  │  ┌───────────────────────────────────────────────────────────┐  │   │
│  │  │  Prepare Pass (optional)                                   │  │   │
│  │  │  - Pre-processing setup                                    │  │   │
│  │  └───────────────────────────────────────────────────────────┘  │   │
│  │  ┌───────────────────────────────────────────────────────────┐  │   │
│  │  │  GBuffers Pass                                             │  │   │
│  │  │  - Terrain rendering to G-buffers                          │  │   │
│  │  │  - Entity rendering                                        │  │   │
│  │  │  - Water, particles, sky, weather                          │  │   │
│  │  │  - Multiple render targets (MRT)                           │  │   │
│  │  └───────────────────────────────────────────────────────────┘  │   │
│  │  ┌───────────────────────────────────────────────────────────┐  │   │
│  │  │  Deferred Pass                                             │  │   │
│  │  │  - Lighting calculations using G-buffer data               │  │   │
│  │  │  - Shadow sampling and soft shadows                        │  │   │
│  │  └───────────────────────────────────────────────────────────┘  │   │
│  │  ┌───────────────────────────────────────────────────────────┐  │   │
│  │  │  Composite Passes (0-15)                                   │  │   │
│  │  │  - Bloom, DOF, motion blur                                 │  │   │
│  │  │  - Volumetric effects                                      │  │   │
│  │  │  - Color grading and tone mapping                          │  │   │
│  │  └───────────────────────────────────────────────────────────┘  │   │
│  │  ┌───────────────────────────────────────────────────────────┐  │   │
│  │  │  Final Pass                                                │  │   │
│  │  │  - Output to screen with final adjustments                 │  │   │
│  │  └───────────────────────────────────────────────────────────┘  │   │
│  └─────────────────────────────────────────────────────────────────┘   │
│                             ↓                                             │
│  ┌─────────────────────────────────────────────────────────────────┐   │
│  │                     G-Buffer Manager                             │   │
│  │  - Manages colortex0-15 (color attachments)                     │   │
│  │  - Manages depthtex0-2 (depth buffers)                          │   │
│  │  - Handles buffer allocation/resizing                           │   │
│  │  - Swap chain management                                        │   │
│  └─────────────────────────────────────────────────────────────────┘   │
│                             ↓                                             │
│  ┌─────────────────────────────────────────────────────────────────┐   │
│  │                     Uniform Manager                              │   │
│  │  - World state (time, weather, dimension)                       │   │
│  │  - Camera state (position, rotation, matrices)                  │   │
│  │  - Player state (held items, effects)                           │   │
│  │  - Transformation matrices                                      │   │
│  │  - Custom shader-defined uniforms                               │   │
│  └─────────────────────────────────────────────────────────────────┘   │
│                                                                           │
└───────────────────────────────────────────────────────────────────────────┘
```

### Integration Points with Existing MattMC Code

| Existing Component | Integration Point | Modification Type |
|--------------------|-------------------|-------------------|
| `GameRenderer` | Add shader pipeline hooks before/after rendering | Major modification |
| `LevelRenderer` | Replace terrain/entity rendering with gbuffers system | Major modification |
| `ShaderManager` | Extend to support shader pack programs | Moderate modification |
| `RenderType` | Map render types to gbuffers programs | Moderate modification |
| `PostChain` | Integrate with composite pass system | Major modification |
| `Options` | Add shader pack selection option | Minor addition |
| `VideoSettingsScreen` | Add "Shaders..." button | Minor addition |
| `RenderTarget` | Extend for G-buffer management | Moderate modification |

---

## Phase 1: Foundation Infrastructure

**Goal**: Establish the base infrastructure for shader pack support without breaking existing rendering.

**Duration**: 2-3 weeks

### 1.1 Directory Structure Setup

Create new package structure:
```
net/minecraft/client/renderer/shader/
├── pack/
│   ├── ShaderPackLoader.java
│   ├── ShaderPack.java
│   ├── ShaderPackRepository.java
│   └── ShaderPackConfig.java
├── program/
│   ├── ShaderProgram.java
│   ├── GBuffersProgram.java
│   ├── CompositeProgram.java
│   └── ShadowProgram.java
├── gbuffer/
│   ├── GBufferManager.java
│   ├── ColorTexture.java
│   └── DepthTexture.java
├── shadow/
│   ├── ShadowRenderer.java
│   └── ShadowMapManager.java
├── uniform/
│   ├── UniformManager.java
│   ├── UniformProvider.java
│   └── UniformValue.java
├── config/
│   ├── ShaderPropertiesParser.java
│   ├── ShaderOption.java
│   └── ShaderProfile.java
└── ShaderRenderPipeline.java
```

### 1.2 Shader Pack Repository

**Purpose**: Discover and manage baked-in shader packs from the resources directory.

**Implementation**:

```java
package net.minecraft.client.renderer.shader.pack;

public class ShaderPackRepository {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String SHADER_PACKS_RESOURCE_PATH = "assets/minecraft/shaders";
    
    private final ResourceManager resourceManager;
    private final List<ShaderPackMetadata> availablePacks;
    private ShaderPack activePack;
    
    public ShaderPackRepository(ResourceManager resourceManager) {
        this.resourceManager = resourceManager;
        this.availablePacks = new ArrayList<>();
    }
    
    public void scanForPacks() {
        availablePacks.clear();
        
        // Scan for shader pack directories in resources
        // Each directory in assets/minecraft/shaders/ (except core, post, include) is a shader pack
        Map<ResourceLocation, Resource> shaderResources = resourceManager.listResources(
            "shaders", 
            location -> !location.getPath().contains("/core/") 
                     && !location.getPath().contains("/post/")
                     && !location.getPath().contains("/include/")
        );
        
        // Group resources by shader pack directory
        Set<String> shaderPackNames = new HashSet<>();
        for (ResourceLocation location : shaderResources.keySet()) {
            String[] pathParts = location.getPath().split("/");
            if (pathParts.length > 2 && pathParts[0].equals("shaders")) {
                shaderPackNames.add(pathParts[1]); // Extract shader pack directory name
            }
        }
        
        // Load metadata for each discovered shader pack
        for (String packName : shaderPackNames) {
            ShaderPackMetadata metadata = loadMetadata(packName);
            if (metadata != null) {
                availablePacks.add(metadata);
            }
        }
        
        LOGGER.info("Found {} baked-in shader packs", availablePacks.size());
    }
    
    private ShaderPackMetadata loadMetadata(String packName) {
        // Try to load pack.mcmeta for this shader pack
        ResourceLocation packMetaLocation = ResourceLocation.withDefaultNamespace(
            "shaders/" + packName + "/pack.mcmeta"
        );
        
        // If no metadata, create basic metadata from directory name
        return new ShaderPackMetadata(
            packName,
            "Shader Pack: " + packName,
            "Unknown",
            "1.0",
            packName,
            true // isResource = true (from JAR resources)
        );
    }
    
    public List<ShaderPackMetadata> getAvailablePacks() {
        return Collections.unmodifiableList(availablePacks);
    }
    
    public CompletableFuture<ShaderPack> loadShaderPack(ShaderPackMetadata metadata) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                ShaderPackLoader loader = new ShaderPackLoader(resourceManager, metadata.resourcePath());
                return loader.load();
            } catch (Exception e) {
                LOGGER.error("Failed to load shader pack: {}", metadata.name(), e);
                return null;
            }
        }, Util.backgroundExecutor());
    }
    
    public void setActivePack(ShaderPack pack) {
        if (this.activePack != null) {
            this.activePack.close();
        }
        this.activePack = pack;
    }
    
    public Optional<ShaderPack> getActivePack() {
        return Optional.ofNullable(activePack);
    }
}
```

**Key Changes from OptiFine/Iris:**
- Scans `ResourceManager` instead of filesystem directories
- No ZIP file handling needed - shaders are already unzipped in resources
- All shader packs are discovered from the JAR's embedded resources
- No need to create or manage a runtime `shaderpacks/` folder

### 1.3 Shader Pack Metadata

```java
public record ShaderPackMetadata(
    String name,
    String description,
    String author,
    String version,
    String resourcePath,  // Resource path within JAR, e.g., "complementary_reimagined"
    boolean isResource    // Always true for MattMC (baked into JAR)
) {
    public static ShaderPackMetadata fromResource(ResourceManager resourceManager, String packName) {
        // Try to read pack.mcmeta from resources
        ResourceLocation metaLocation = ResourceLocation.withDefaultNamespace(
            "shaders/" + packName + "/pack.mcmeta"
        );
        
        // Parse metadata or use defaults
        return new ShaderPackMetadata(
            packName,
            "Shader Pack: " + packName,
            "Unknown",
            "1.0",
            packName,
            true
        );
    }
}
```

### 1.4 Resource-Based Shader Loading

**Purpose**: Load shader files from JAR resources instead of filesystem.

```java
public class ResourceShaderPackAccessor {
    private final ResourceManager resourceManager;
    private final String packBasePath;
    
    public ResourceShaderPackAccessor(ResourceManager resourceManager, String packName) {
        this.resourceManager = resourceManager;
        this.packBasePath = "shaders/" + packName;
    }
    
    public InputStream openFile(String relativePath) throws IOException {
        ResourceLocation location = ResourceLocation.withDefaultNamespace(
            packBasePath + "/" + relativePath
        );
        
        Optional<Resource> resource = resourceManager.getResource(location);
        if (resource.isEmpty()) {
            throw new FileNotFoundException("Shader resource not found: " + location);
        }
        
        return resource.get().open();
    }
    
    public boolean fileExists(String relativePath) {
        ResourceLocation location = ResourceLocation.withDefaultNamespace(
            packBasePath + "/" + relativePath
        );
        return resourceManager.getResource(location).isPresent();
    }
    
    public List<String> listFiles(String directory) {
        // List all resources under the specified directory
        Map<ResourceLocation, Resource> resources = resourceManager.listResources(
            packBasePath + "/" + directory,
            loc -> true
        );
        return resources.keySet().stream()
            .map(ResourceLocation::getPath)
            .collect(Collectors.toList());
    }
}
```

**Key Changes from OptiFine/Iris:**
- No filesystem or ZIP handling - uses Minecraft's `ResourceManager`
- All shaders accessed via `ResourceLocation` (same as game assets)
- Leverages existing resource loading infrastructure
- No need for separate file system abstraction layer

### 1.5 Options Integration

Add shader pack selection to `Options.java`:

```java
// In Options.java
private String shaderPack = "";  // Empty string = no shader pack

public String getShaderPack() {
    return shaderPack;
}

public void setShaderPack(String packName) {
    if (!Objects.equals(this.shaderPack, packName)) {
        this.shaderPack = packName;
        // Trigger shader pack reload
        Minecraft.getInstance().reloadShaderPack();
    }
}

// In save() method
writer.println("shaderPack:" + this.shaderPack);

// In load() method
case "shaderPack":
    this.shaderPack = value;
    break;
```

---

## Phase 2: Shader Pack Loading System

**Goal**: Implement resource-based shader pack parsing and GLSL shader loading from JAR resources.

**Duration**: 2-3 weeks

### 2.1 Shader Pack Loader

```java
public class ShaderPackLoader {
    private final ResourceShaderPackAccessor resourceAccessor;
    private final Map<String, String> shaderSources = new HashMap<>();
    private ShaderProperties properties;
    
    public ShaderPackLoader(ResourceManager resourceManager, String packName) {
        this.resourceAccessor = new ResourceShaderPackAccessor(resourceManager, packName);
    }
    
    public ShaderPack load() throws IOException {
        // 1. Load shaders.properties from resources
        properties = loadShaderProperties();
        
        // 2. Dynamically discover all shader program files from resources
        Map<ShaderProgramType, ShaderProgramSource> programs = discoverPrograms();
        
        // 3. Load custom textures from resources
        Map<String, ResourceLocation> customTextures = loadCustomTextures();
        
        // 4. Parse block.properties and entity.properties
        BlockMappings blockMappings = loadBlockMappings();
        EntityMappings entityMappings = loadEntityMappings();
        
        return new ShaderPack(properties, programs, customTextures, 
                              blockMappings, entityMappings);
    }
    
    private Map<ShaderProgramType, ShaderProgramSource> discoverPrograms() {
        Map<ShaderProgramType, ShaderProgramSource> programs = new EnumMap<>(ShaderProgramType.class);
        
        // Dynamically scan for gbuffers_* programs (not hardcoded)
        List<String> shaderFiles = resourceAccessor.listFiles("shaders");
        Set<String> programNames = new HashSet<>();
        
        // Extract unique program names from .vsh and .fsh files
        for (String file : shaderFiles) {
            if (file.endsWith(".vsh") || file.endsWith(".fsh")) {
                String programName = file.substring(0, file.lastIndexOf('.'));
                programNames.add(programName);
            }
        }
        
        // Load each discovered program
        for (String programName : programNames) {
            String vshPath = "shaders/" + programName + ".vsh";
            String fshPath = "shaders/" + programName + ".fsh";
            
            String vertexSource = loadShaderSource(vshPath);
            String fragmentSource = loadShaderSource(fshPath);
            
            if (vertexSource != null || fragmentSource != null) {
                ShaderProgramType type = ShaderProgramType.fromName(programName);
                if (type != null) {
                    programs.put(type, new ShaderProgramSource(vertexSource, fragmentSource));
                }
            }
        }
        
        LOGGER.info("Discovered {} shader programs in pack", programs.size());
        return programs;
    }
    
    private String loadShaderSource(String path) {
        if (!resourceAccessor.fileExists(path)) {
            return null;
        }
        
        try (InputStream is = resourceAccessor.openFile(path);
             BufferedReader reader = new BufferedReader(new InputStreamReader(is))) {
            
            StringBuilder source = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                // Process #include directives (supports both relative and absolute)
                if (line.trim().startsWith("#include")) {
                    String includePath = parseIncludePath(line);
                    String includedSource = loadShaderSource(includePath);
                    if (includedSource != null) {
                        source.append(includedSource).append("\n");
                    }
                } else {
                    source.append(line).append("\n");
                }
            }
            return source.toString();
        } catch (IOException e) {
            LOGGER.error("Failed to load shader source: {}", path, e);
            return null;
        }
    }
}
```

**Key Changes from OptiFine/Iris:**
- Loads from `ResourceManager` instead of filesystem/ZIP
- **Dynamic discovery**: Scans resource listings to find all shaders (not hardcoded)
- Custom textures referenced as `ResourceLocation` instead of file paths
- No ZIP extraction or temporary files needed

### 2.2 Shader Properties Parser

```java
public class ShaderPropertiesParser {
    public ShaderProperties parse(InputStream inputStream) throws IOException {
        Properties props = new Properties();
        props.load(inputStream);
        
        return ShaderProperties.builder()
            .options(parseOptions(props))
            .profiles(parseProfiles(props))
            .bufferFormats(parseBufferFormats(props))
            .shadowMapSettings(parseShadowSettings(props))
            .customTextures(parseCustomTextures(props))
            .build();
    }
    
    private List<ShaderOption> parseOptions(Properties props) {
        List<ShaderOption> options = new ArrayList<>();
        
        // Parse screen.* and sliders.* entries
        for (String key : props.stringPropertyNames()) {
            if (key.startsWith("screen.")) {
                // Parse option groups for UI
            } else if (key.startsWith("sliders.")) {
                // Parse slider options
            } else if (key.startsWith("option.")) {
                // Parse individual options
            }
        }
        
        return options;
    }
}
```

### 2.3 GLSL Preprocessing

Handle shader pack-specific preprocessing directives:

```java
public class ShaderPreprocessor {
    private final Map<String, String> defines = new HashMap<>();
    
    public String preprocess(String source, ShaderProperties properties) {
        // 1. Apply #define values from shaders.properties
        for (ShaderOption option : properties.getOptions()) {
            defines.put(option.getName(), option.getValue());
        }
        
        // 2. Process #ifdef, #ifndef, #endif
        source = processConditionals(source);
        
        // 3. Inject built-in defines
        source = injectBuiltInDefines(source);
        
        // 4. Add version directive if missing
        if (!source.startsWith("#version")) {
            source = "#version 330 core\n" + source;
        }
        
        return source;
    }
    
    private String injectBuiltInDefines(String source) {
        StringBuilder defines = new StringBuilder();
        defines.append("#define MC_VERSION 12110\n");  // 1.21.10
        defines.append("#define MC_GL_VERSION 330\n");
        defines.append("#define MC_RENDER_QUALITY 1.0\n");
        // Add more built-in defines...
        
        return defines.toString() + source;
    }
}
```

---

## Phase 3: Rendering Pipeline Transformation

**Goal**: Modify existing rendering code to support shader pack rendering flow.

**Duration**: 3-4 weeks

### 3.1 Shader Render Pipeline

Create new rendering orchestrator:

```java
public class ShaderRenderPipeline {
    private final ShaderPack pack;
    private final GBufferManager gBufferManager;
    private final ShadowRenderer shadowRenderer;
    private final UniformManager uniformManager;
    
    private boolean initialized = false;
    
    public void initialize(int width, int height) {
        gBufferManager.resize(width, height);
        shadowRenderer.initialize(pack.getShadowMapResolution());
        initialized = true;
    }
    
    public void render(LevelRenderState levelState, CameraRenderState cameraState, DeltaTracker deltaTracker) {
        if (!initialized) return;
        
        RenderSystem.assertOnRenderThread();
        
        // Update uniforms for this frame
        uniformManager.updateFrameUniforms(levelState, cameraState, deltaTracker);
        
        // Execute rendering pipeline
        executeShadowPass(levelState, cameraState);
        executePreparePass();
        executeGBuffersPass(levelState, cameraState);
        executeDeferredPass();
        executeCompositePass();
        executeFinalPass();
    }
    
    private void executeShadowPass(LevelRenderState levelState, CameraRenderState cameraState) {
        if (!pack.hasShadowProgram()) return;
        
        shadowRenderer.beginShadowPass();
        
        // Set shadow uniforms
        uniformManager.updateShadowUniforms();
        
        // Bind shadow program
        ShaderProgram shadowProgram = pack.getShadowProgram();
        shadowProgram.bind();
        
        // Render world from light's perspective
        renderWorldGeometry(levelState, cameraState, RenderPass.SHADOW);
        
        shadowRenderer.endShadowPass();
    }
    
    private void executeGBuffersPass(LevelRenderState levelState, CameraRenderState cameraState) {
        gBufferManager.bindForWriting();
        
        // Clear buffers
        RenderSystem.clear(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT);
        
        // Render sky
        renderSky(levelState, cameraState);
        
        // Render terrain (solid)
        renderTerrain(levelState, cameraState, GBuffersProgram.TERRAIN);
        renderTerrain(levelState, cameraState, GBuffersProgram.TERRAIN_SOLID);
        
        // Render terrain (cutout - leaves, flowers, etc.)
        renderTerrain(levelState, cameraState, GBuffersProgram.TERRAIN_CUTOUT);
        
        // Render entities
        renderEntities(levelState, cameraState, GBuffersProgram.ENTITIES);
        
        // Render water
        renderWater(levelState, cameraState);
        
        // Render particles
        renderParticles(levelState, cameraState);
        
        // Render weather
        renderWeather(levelState, cameraState);
        
        // Render hand/held items
        renderHand(levelState, cameraState);
        
        gBufferManager.unbind();
    }
    
    private void executeCompositePass() {
        for (int i = 0; i < pack.getCompositePassCount(); i++) {
            CompositeProgram program = pack.getCompositeProgram(i);
            if (program == null) continue;
            
            // Bind composite program
            program.bind();
            
            // Bind input textures
            gBufferManager.bindTexturesForReading(program.getRequiredTextures());
            
            // Bind output framebuffer
            RenderTarget output = gBufferManager.getCompositeTarget(i);
            output.bindWrite(false);
            
            // Update composite-specific uniforms
            uniformManager.updateCompositeUniforms(i);
            
            // Render full-screen quad
            renderScreenQuad();
            
            output.unbindWrite();
        }
    }
}
```

### 3.2 LevelRenderer Integration

Modify `LevelRenderer.java` to use shader pipeline:

```java
// In LevelRenderer.java

private ShaderRenderPipeline shaderPipeline;

public void setShaderPack(ShaderPack pack) {
    if (this.shaderPipeline != null) {
        this.shaderPipeline.close();
    }
    
    if (pack != null) {
        this.shaderPipeline = new ShaderRenderPipeline(pack);
        this.shaderPipeline.initialize(minecraft.getWindow().getWidth(), 
                                       minecraft.getWindow().getHeight());
    } else {
        this.shaderPipeline = null;
    }
}

public void renderLevel(LevelRenderState state, CameraRenderState camera, DeltaTracker tracker) {
    if (shaderPipeline != null && shaderPipeline.isActive()) {
        // Use shader pack rendering
        shaderPipeline.render(state, camera, tracker);
    } else {
        // Use vanilla rendering
        renderLevelVanilla(state, camera, tracker);
    }
}
```

### 3.3 Render Type Mapping

Map Minecraft's `RenderType` to gbuffers programs:

```java
public enum GBuffersProgram {
    BASIC("gbuffers_basic"),
    TEXTURED("gbuffers_textured"),
    TEXTURED_LIT("gbuffers_textured_lit"),
    TERRAIN("gbuffers_terrain"),
    TERRAIN_SOLID("gbuffers_terrain_solid"),
    TERRAIN_CUTOUT("gbuffers_terrain_cutout"),
    TERRAIN_CUTOUT_MIPPED("gbuffers_terrain_cutout_mipped"),
    DAMAGED_BLOCK("gbuffers_damaged_block"),
    SKYBASIC("gbuffers_skybasic"),
    SKYTEXTURED("gbuffers_skytextured"),
    CLOUDS("gbuffers_clouds"),
    ENTITIES("gbuffers_entities"),
    ENTITIES_GLOWING("gbuffers_entities_glowing"),
    ENTITIES_TRANSLUCENT("gbuffers_entities_translucent"),
    ARMOR_GLINT("gbuffers_armor_glint"),
    SPIDER_EYES("gbuffers_spider_eyes"),
    HAND("gbuffers_hand"),
    HAND_WATER("gbuffers_hand_water"),
    WEATHER("gbuffers_weather"),
    BLOCK("gbuffers_block"),
    BEACON_BEAM("gbuffers_beaconbeam"),
    PARTICLES("gbuffers_particles"),
    WATER("gbuffers_water");
    
    public static GBuffersProgram fromRenderType(RenderType renderType) {
        // Map vanilla RenderType to appropriate gbuffers program
        String name = renderType.name();
        
        if (name.contains("solid")) return TERRAIN_SOLID;
        if (name.contains("cutout")) return TERRAIN_CUTOUT;
        if (name.contains("entity")) return ENTITIES;
        if (name.contains("water")) return WATER;
        // ... more mappings
        
        return TEXTURED; // Default fallback
    }
}
```

---

## Phase 4: G-Buffer and Deferred Rendering

**Goal**: Implement multiple render target (MRT) support for deferred rendering.

**Duration**: 2-3 weeks

### 4.1 G-Buffer Manager

```java
public class GBufferManager {
    private static final int MAX_COLOR_TEXTURES = 16;
    private static final int MAX_DEPTH_TEXTURES = 3;
    
    private final ColorTexture[] colorTextures = new ColorTexture[MAX_COLOR_TEXTURES];
    private final DepthTexture[] depthTextures = new DepthTexture[MAX_DEPTH_TEXTURES];
    private int width, height;
    private int mainFramebuffer;
    
    public void initialize(int width, int height) {
        this.width = width;
        this.height = height;
        
        // Create main framebuffer object
        mainFramebuffer = GlStateManager.glGenFramebuffers();
        
        // Create color textures (colortex0-15)
        for (int i = 0; i < MAX_COLOR_TEXTURES; i++) {
            TextureFormat format = getColorTextureFormat(i);
            colorTextures[i] = new ColorTexture(i, width, height, format);
        }
        
        // Create depth textures (depthtex0-2)
        for (int i = 0; i < MAX_DEPTH_TEXTURES; i++) {
            depthTextures[i] = new DepthTexture(i, width, height);
        }
    }
    
    private TextureFormat getColorTextureFormat(int index) {
        // Read from shaders.properties or use defaults
        // colortex0: RGBA8 (albedo)
        // colortex1: RGBA16F (lighting/bloom)
        // colortex2: RGBA16 (normals)
        // colortex3: RGBA16 (specular/roughness)
        // colortex4-7: RGBA16F (custom data)
        
        return switch (index) {
            case 0 -> TextureFormat.RGBA8;
            case 1, 4, 5, 6, 7 -> TextureFormat.RGBA16F;
            case 2, 3 -> TextureFormat.RGBA16;
            default -> TextureFormat.RGBA8;
        };
    }
    
    public void bindForWriting() {
        GlStateManager._glBindFramebuffer(GL30.GL_FRAMEBUFFER, mainFramebuffer);
        
        // Attach all color textures
        for (int i = 0; i < MAX_COLOR_TEXTURES; i++) {
            GL30.glFramebufferTexture2D(
                GL30.GL_FRAMEBUFFER,
                GL30.GL_COLOR_ATTACHMENT0 + i,
                GL11.GL_TEXTURE_2D,
                colorTextures[i].getTextureId(),
                0
            );
        }
        
        // Attach primary depth texture
        GL30.glFramebufferTexture2D(
            GL30.GL_FRAMEBUFFER,
            GL30.GL_DEPTH_ATTACHMENT,
            GL11.GL_TEXTURE_2D,
            depthTextures[0].getTextureId(),
            0
        );
        
        // Enable all color attachments for MRT
        int[] drawBuffers = new int[MAX_COLOR_TEXTURES];
        for (int i = 0; i < MAX_COLOR_TEXTURES; i++) {
            drawBuffers[i] = GL30.GL_COLOR_ATTACHMENT0 + i;
        }
        GL20.glDrawBuffers(drawBuffers);
        
        // Verify framebuffer is complete
        int status = GL30.glCheckFramebufferStatus(GL30.GL_FRAMEBUFFER);
        if (status != GL30.GL_FRAMEBUFFER_COMPLETE) {
            throw new IllegalStateException("Framebuffer is not complete: " + status);
        }
    }
    
    public void bindTextureForReading(String textureName, int textureUnit) {
        ColorTexture texture = getColorTexture(textureName);
        if (texture != null) {
            RenderSystem.activeTexture(GL13.GL_TEXTURE0 + textureUnit);
            RenderSystem.bindTexture(texture.getTextureId());
        }
    }
    
    public void resize(int newWidth, int newHeight) {
        if (newWidth == width && newHeight == height) return;
        
        // Recreate all textures with new dimensions
        for (ColorTexture tex : colorTextures) {
            tex.resize(newWidth, newHeight);
        }
        for (DepthTexture tex : depthTextures) {
            tex.resize(newWidth, newHeight);
        }
        
        width = newWidth;
        height = newHeight;
    }
}
```

### 4.2 Color Texture Implementation

```java
public class ColorTexture {
    private final int index;
    private int textureId;
    private int width, height;
    private TextureFormat format;
    
    public ColorTexture(int index, int width, int height, TextureFormat format) {
        this.index = index;
        this.width = width;
        this.height = height;
        this.format = format;
        
        createTexture();
    }
    
    private void createTexture() {
        textureId = GlStateManager._genTexture();
        GlStateManager._bindTexture(textureId);
        
        // Set texture parameters
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
        
        // Allocate texture storage
        GL11.glTexImage2D(
            GL11.GL_TEXTURE_2D,
            0,
            format.getInternalFormat(),
            width,
            height,
            0,
            format.getFormat(),
            format.getType(),
            (ByteBuffer) null
        );
    }
    
    public void resize(int newWidth, int newHeight) {
        if (textureId != 0) {
            GlStateManager._deleteTexture(textureId);
        }
        this.width = newWidth;
        this.height = newHeight;
        createTexture();
    }
}

public enum TextureFormat {
    RGBA8(GL11.GL_RGBA8, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE),
    RGBA16(GL30.GL_RGBA16, GL11.GL_RGBA, GL11.GL_UNSIGNED_SHORT),
    RGBA16F(GL30.GL_RGBA16F, GL11.GL_RGBA, GL30.GL_FLOAT),
    RGBA32F(GL30.GL_RGBA32F, GL11.GL_RGBA, GL30.GL_FLOAT),
    RGB8(GL11.GL_RGB8, GL11.GL_RGB, GL11.GL_UNSIGNED_BYTE),
    RGB16F(GL30.GL_RGB16F, GL11.GL_RGB, GL30.GL_FLOAT);
    
    private final int internalFormat;
    private final int format;
    private final int type;
    
    TextureFormat(int internalFormat, int format, int type) {
        this.internalFormat = internalFormat;
        this.format = format;
        this.type = type;
    }
    
    public int getInternalFormat() { return internalFormat; }
    public int getFormat() { return format; }
    public int getType() { return type; }
}
```

---

## Phase 5: Shadow Mapping System

**Goal**: Implement shadow map generation from light source perspective.

**Duration**: 2-3 weeks

### 5.1 Shadow Renderer

```java
public class ShadowRenderer {
    private int shadowMapResolution;
    private ShadowMapManager shadowMapManager;
    private Matrix4f shadowProjectionMatrix;
    private Matrix4f shadowModelViewMatrix;
    
    public void initialize(int resolution) {
        this.shadowMapResolution = resolution;
        this.shadowMapManager = new ShadowMapManager(resolution);
        calculateShadowMatrices();
    }
    
    private void calculateShadowMatrices() {
        // Calculate sun/moon position based on world time
        float sunAngle = calculateSunAngle();
        
        // Create orthographic projection for shadow map
        shadowProjectionMatrix = new Matrix4f().ortho(
            -100.0f, 100.0f,  // left, right
            -100.0f, 100.0f,  // bottom, top
            -100.0f, 200.0f   // near, far
        );
        
        // Create view matrix looking from sun toward player
        Vector3f sunDirection = new Vector3f(
            (float) Math.cos(sunAngle),
            (float) Math.sin(sunAngle),
            0.0f
        );
        
        Vector3f cameraPos = getCameraPosition();
        Vector3f shadowCenter = cameraPos; // Or offset toward chunks
        
        shadowModelViewMatrix = new Matrix4f().lookAt(
            shadowCenter.add(sunDirection.mul(100.0f)),
            shadowCenter,
            new Vector3f(0, 1, 0)
        );
    }
    
    public void beginShadowPass() {
        shadowMapManager.bindForWriting();
        
        // Clear shadow map
        RenderSystem.clear(GL11.GL_DEPTH_BUFFER_BIT);
        
        // Set viewport to shadow map resolution
        RenderSystem.viewport(0, 0, shadowMapResolution, shadowMapResolution);
        
        // Enable depth testing, disable color writes
        RenderSystem.enableDepthTest();
        RenderSystem.colorMask(false, false, false, false);
        
        // Optional: Enable back-face culling to reduce shadow acne
        RenderSystem.enableCull();
        RenderSystem.cullFace(GL11.GL_FRONT);
    }
    
    public void endShadowPass() {
        shadowMapManager.unbind();
        
        // Restore render state
        RenderSystem.colorMask(true, true, true, true);
        RenderSystem.cullFace(GL11.GL_BACK);
        
        // Restore viewport to screen size
        Window window = Minecraft.getInstance().getWindow();
        RenderSystem.viewport(0, 0, window.getWidth(), window.getHeight());
    }
    
    public Matrix4f getShadowProjectionMatrix() {
        return shadowProjectionMatrix;
    }
    
    public Matrix4f getShadowModelViewMatrix() {
        return shadowModelViewMatrix;
    }
    
    public void bindShadowTextures(int shadowtex0Unit, int shadowtex1Unit) {
        shadowMapManager.bindTexturesForReading(shadowtex0Unit, shadowtex1Unit);
    }
}
```

### 5.2 Cascaded Shadow Maps (Advanced)

```java
public class CascadedShadowMapRenderer {
    private static final int CASCADE_COUNT = 4;
    private final ShadowMapManager[] cascades = new ShadowMapManager[CASCADE_COUNT];
    private final float[] cascadeSplits = new float[CASCADE_COUNT + 1];
    
    public void initialize(int baseResolution) {
        // Create shadow maps for each cascade
        for (int i = 0; i < CASCADE_COUNT; i++) {
            cascades[i] = new ShadowMapManager(baseResolution >> i); // Halve resolution per cascade
        }
        
        // Calculate cascade split distances
        calculateCascadeSplits();
    }
    
    private void calculateCascadeSplits() {
        float nearClip = 0.1f;
        float farClip = 256.0f;  // Max shadow distance
        float lambda = 0.75f;     // Split interpolation factor
        
        cascadeSplits[0] = nearClip;
        cascadeSplits[CASCADE_COUNT] = farClip;
        
        for (int i = 1; i < CASCADE_COUNT; i++) {
            float uniform = nearClip + (farClip - nearClip) * (i / (float) CASCADE_COUNT);
            float logarithmic = nearClip * (float) Math.pow(farClip / nearClip, i / (float) CASCADE_COUNT);
            cascadeSplits[i] = lambda * logarithmic + (1.0f - lambda) * uniform;
        }
    }
    
    public void renderCascades(LevelRenderState state, CameraRenderState camera) {
        for (int i = 0; i < CASCADE_COUNT; i++) {
            Matrix4f cascadeMatrix = calculateCascadeMatrix(i, camera);
            renderCascade(i, cascadeMatrix, state);
        }
    }
}
```

---

## Phase 6: Uniforms and Shader Interface

**Goal**: Implement comprehensive uniform system for passing game state to shaders.

**Duration**: 2 weeks

### 6.1 Uniform Manager

Implement ~200+ uniforms organized by category:

```java
public class UniformManager {
    private final Map<String, UniformProvider> uniformProviders = new HashMap<>();
    
    public void initialize() {
        registerProvider("matrix", new MatrixUniformProvider());
        registerProvider("time", new TimeUniformProvider());
        registerProvider("camera", new CameraUniformProvider());
        registerProvider("world", new WorldStateUniformProvider());
        registerProvider("player", new PlayerStateUniformProvider());
    }
    
    public void updateUniforms(ShaderProgram program, RenderContext context) {
        for (String uniformName : program.getRequiredUniforms()) {
            UniformProvider provider = findProviderForUniform(uniformName);
            if (provider != null) {
                Object value = provider.getValue(uniformName, context);
                program.setUniform(uniformName, value);
            }
        }
    }
}

// Example: Matrix Uniforms
public class MatrixUniformProvider implements UniformProvider {
    @Override
    public Object getValue(String name, RenderContext context) {
        return switch (name) {
            case "gbufferModelView" -> context.getModelViewMatrix();
            case "gbufferModelViewInverse" -> context.getModelViewMatrix().invert(new Matrix4f());
            case "gbufferProjection" -> context.getProjectionMatrix();
            case "gbufferProjectionInverse" -> context.getProjectionMatrix().invert(new Matrix4f());
            case "gbufferPreviousModelView" -> context.getPreviousModelViewMatrix();
            case "shadowProjection" -> context.getShadowProjectionMatrix();
            case "shadowModelView" -> context.getShadowModelViewMatrix();
            default -> null;
        };
    }
}
```

### Key Uniform Categories:

1. **Transformation Matrices** (16 uniforms)
2. **Time and World State** (20 uniforms)  
3. **Camera and View** (15 uniforms)
4. **Player State** (25 uniforms)
5. **Lighting** (20 uniforms)
6. **Weather and Atmosphere** (15 uniforms)
7. **Texture Samplers** (30+ uniforms)
8. **Custom/Optional** (100+ uniforms)

---

## Phase 7: Configuration and UI Integration

**Goal**: Add in-game UI for shader selection and configuration.

**Duration**: 1-2 weeks

### 7.1 Shader Selection Screen

```java
public class ShaderPackSelectionScreen extends Screen {
    private final Screen lastScreen;
    private ShaderPackList shaderList;
    private Button selectButton;
    private Button optionsButton;
    
    @Override
    protected void init() {
        this.shaderList = new ShaderPackList(this.minecraft, this.width, this.height, 32, this.height - 64, 36);
        
        // Populate with available shader packs
        ShaderPackRepository repository = minecraft.getShaderPackRepository();
        for (ShaderPackMetadata metadata : repository.getAvailablePacks()) {
            shaderList.addEntry(new ShaderPackEntry(metadata));
        }
        
        // Add buttons
        this.selectButton = this.addRenderableWidget(Button.builder(
            Component.literal("Select"), 
            button -> selectShaderPack()
        ).bounds(this.width / 2 - 154, this.height - 52, 150, 20).build());
        
        this.optionsButton = this.addRenderableWidget(Button.builder(
            Component.literal("Shader Options..."),
            button -> openShaderOptions()
        ).bounds(this.width / 2 + 4, this.height - 52, 150, 20).build());
        
        this.addRenderableWidget(Button.builder(
            CommonComponents.GUI_DONE,
            button -> this.onClose()
        ).bounds(this.width / 2 - 100, this.height - 28, 200, 20).build());
    }
    
    private void selectShaderPack() {
        ShaderPackEntry selected = shaderList.getSelected();
        if (selected != null) {
            minecraft.options.setShaderPack(selected.getMetadata().name());
            minecraft.reloadShaderPack();
        }
    }
}
```

### 7.2 Shader Options Screen

Dynamic UI generation based on shaders.properties:

```java
public class ShaderOptionsScreen extends Screen {
    private final ShaderPack pack;
    private OptionsList optionsList;
    
    @Override
    protected void init() {
        this.optionsList = new OptionsList(this.minecraft, this.width, this.height, 32, this.height - 32, 25);
        
        // Generate UI from shader options
        for (ShaderOption option : pack.getOptions()) {
            AbstractWidget widget = createWidgetForOption(option);
            optionsList.addWidget(widget);
        }
        
        this.addRenderableWidget(optionsList);
    }
    
    private AbstractWidget createWidgetForOption(ShaderOption option) {
        return switch (option.getType()) {
            case BOOLEAN -> createBooleanOption(option);
            case SLIDER -> createSliderOption(option);
            case SELECTION -> createSelectionOption(option);
            case COLOR -> createColorOption(option);
        };
    }
}
```

### 7.3 Video Settings Integration

```java
// In VideoSettingsScreen.java
private Button shaderButton;

@Override
protected void init() {
    // ... existing code ...
    
    // Add "Shaders..." button
    this.shaderButton = this.addRenderableWidget(Button.builder(
        Component.literal("Shaders..."),
        button -> this.minecraft.setScreen(new ShaderPackSelectionScreen(this))
    ).bounds(this.width / 2 - 155, this.height / 6 + 48 - 6, 150, 20).build());
}
```

---

## Phase 8: Advanced Features

**Goal**: Implement advanced shader features for full compatibility.

**Duration**: 2-3 weeks

### 8.1 Block and Entity ID Encoding

Encode block/entity IDs into G-buffers for per-block effects:

```java
public class BlockEntityIdEncoder {
    // Encode block ID into unused alpha channel or separate buffer
    public static int encodeBlockId(BlockState state) {
        return Registry.BLOCK.getId(state.getBlock());
    }
    
    public static int encodeEntityId(Entity entity) {
        return Registry.ENTITY_TYPE.getId(entity.getType());
    }
    
    // In vertex shader:
    // gl_FragData[2].a = float(blockId) / 65535.0;  // Encode in alpha
}
```

### 8.2 Dimension-Specific Shaders

```java
public class DimensionShaderManager {
    private final Map<ResourceKey<Level>, ShaderOverrides> dimensionOverrides = new HashMap<>();
    
    public void loadDimensionShaders(ShaderPack pack) {
        // Load world-1/ (Nether), world1/ (End), etc.
        for (ResourceKey<Level> dimension : KNOWN_DIMENSIONS) {
            String path = "world" + getDimensionId(dimension) + "/";
            if (pack.hasDirectory(path)) {
                ShaderOverrides overrides = loadOverrides(pack, path);
                dimensionOverrides.put(dimension, overrides);
            }
        }
    }
    
    public ShaderProgram getProgram(ResourceKey<Level> dimension, String programName) {
        ShaderOverrides overrides = dimensionOverrides.get(dimension);
        if (overrides != null && overrides.hasProgram(programName)) {
            return overrides.getProgram(programName);
        }
        return pack.getProgram(programName); // Fallback to default
    }
}
```

### 8.3 Custom Textures

```java
public class CustomTextureManager {
    public void loadCustomTextures(ShaderPack pack) {
        Map<String, Path> textures = pack.getCustomTextures();
        
        for (Map.Entry<String, Path> entry : textures.entrySet()) {
            String name = entry.getKey();
            Path path = entry.getValue();
            
            // Load texture and bind to specific unit
            NativeImage image = NativeImage.read(Files.newInputStream(path));
            int textureId = TextureUtil.generateTextureId();
            TextureUtil.prepareImage(textureId, image.getWidth(), image.getHeight());
            image.upload(0, 0, 0, false);
            
            customTextures.put(name, textureId);
        }
    }
    
    public void bindCustomTexture(String name, int unit) {
        Integer textureId = customTextures.get(name);
        if (textureId != null) {
            RenderSystem.activeTexture(GL13.GL_TEXTURE0 + unit);
            RenderSystem.bindTexture(textureId);
        }
    }
}
```

### 8.4 Shader Hot-Reloading

```java
public class ShaderHotReloader {
    private final Path shaderPackPath;
    private WatchService watchService;
    
    public void startWatching() {
        try {
            watchService = FileSystems.getDefault().newWatchService();
            shaderPackPath.register(watchService,
                StandardWatchEventKinds.ENTRY_MODIFY,
                StandardWatchEventKinds.ENTRY_CREATE);
            
            Thread watchThread = new Thread(this::watchForChanges);
            watchThread.setDaemon(true);
            watchThread.start();
        } catch (IOException e) {
            LOGGER.error("Failed to start shader hot-reloader", e);
        }
    }
    
    private void watchForChanges() {
        while (true) {
            WatchKey key = watchService.take();
            for (WatchEvent<?> event : key.pollEvents()) {
                Path changed = (Path) event.context();
                if (changed.toString().endsWith(".fsh") || changed.toString().endsWith(".vsh")) {
                    scheduleReload();
                }
            }
            key.reset();
        }
    }
}
```

---

## Phase 9: Performance Optimization

**Goal**: Optimize rendering for smooth gameplay with shaders.

**Duration**: 2 weeks

### 9.1 Shader Program Caching

```java
public class ShaderProgramCache {
    private final Map<ShaderProgramKey, CompiledProgram> cache = new ConcurrentHashMap<>();
    
    public CompiledProgram getOrCompile(ShaderProgramKey key) {
        return cache.computeIfAbsent(key, k -> {
            return compileProgram(k.vertexSource(), k.fragmentSource());
        });
    }
    
    // Precompile common programs on background thread
    public CompletableFuture<Void> precompileAsync(ShaderPack pack) {
        return CompletableFuture.runAsync(() -> {
            for (ShaderProgramType type : COMMON_PROGRAMS) {
                getOrCompile(pack.getProgramKey(type));
            }
        }, Util.backgroundExecutor());
    }
}
```

### 9.2 Uniform Buffer Objects (UBO)

```java
public class UniformBufferManager {
    private int matrixUBO;
    private int worldStateUBO;
    
    public void initialize() {
        // Create UBOs for frequently updated uniforms
        matrixUBO = GL15.glGenBuffers();
        GL15.glBindBuffer(GL31.GL_UNIFORM_BUFFER, matrixUBO);
        GL15.glBufferData(GL31.GL_UNIFORM_BUFFER, 256, GL15.GL_DYNAMIC_DRAW); // 4 matrices * 64 bytes
        
        worldStateUBO = GL15.glGenBuffers();
        GL15.glBindBuffer(GL31.GL_UNIFORM_BUFFER, worldStateUBO);
        GL15.glBufferData(GL31.GL_UNIFORM_BUFFER, 128, GL15.GL_DYNAMIC_DRAW);
    }
    
    public void updateMatrices(Matrix4f modelView, Matrix4f projection) {
        GL15.glBindBuffer(GL31.GL_UNIFORM_BUFFER, matrixUBO);
        
        ByteBuffer buffer = BufferUtils.createByteBuffer(128);
        modelView.get(buffer);
        buffer.position(64);
        projection.get(buffer);
        buffer.flip();
        
        GL15.glBufferSubData(GL31.GL_UNIFORM_BUFFER, 0, buffer);
    }
    
    public void bindToProgram(ShaderProgram program) {
        int blockIndex = GL31.glGetUniformBlockIndex(program.getId(), "MatrixBlock");
        if (blockIndex != -1) {
            GL31.glUniformBlockBinding(program.getId(), blockIndex, 0);
            GL30.glBindBufferBase(GL31.GL_UNIFORM_BUFFER, 0, matrixUBO);
        }
    }
}
```

### 9.3 Frustum Culling for Shadow Pass

```java
public class ShadowFrustumCuller {
    public boolean isChunkVisible(ChunkPos pos, Matrix4f shadowMatrix) {
        // Transform chunk bounding box to shadow space
        // Test against shadow frustum
        // Skip chunks outside shadow view
        return true; // Simplified
    }
}
```

---

## Phase 10: Testing and Validation

**Goal**: Ensure compatibility and stability.

**Duration**: 2-3 weeks

### 10.1 Test Suite

1. **Unit Tests**: Core functionality (properties parser, uniform manager, etc.)
2. **Integration Tests**: Full pipeline with test shader pack
3. **Compatibility Tests**: Major shader packs (Complimentary, BSL, Sildurs, SEUS)
4. **Performance Tests**: FPS benchmarks, memory usage
5. **Stress Tests**: Rapid shader switching, window resizing, dimension changes

### 10.2 Validation Checklist

- [ ] Can load ZIP-based shader packs
- [ ] Can load directory-based shader packs
- [ ] Shadow mapping works correctly
- [ ] G-buffers populated correctly
- [ ] All uniforms provide correct values
- [ ] Shader options UI functional
- [ ] Hot-reloading works (development feature)
- [ ] No memory leaks on shader pack switching
- [ ] Compatible with vanilla resource packs
- [ ] Works across all dimensions
- [ ] Weather effects render correctly
- [ ] Water rendering works
- [ ] Entity rendering works
- [ ] Particles render correctly
- [ ] Hand/held items render correctly
- [ ] Performance within 80% of OptiFine/Iris

### 10.3 Known Shader Packs to Test

| Shader Pack | Priority | Expected Compatibility |
|-------------|----------|------------------------|
| Complimentary Reimagined | High | Full |
| BSL Shaders | High | Full |
| Sildurs Vibrant | High | Full |
| Vanilla Plus | Medium | Full |
| SEUS PTGI | Medium | Partial (may need ray tracing features) |
| Nostalgia | Medium | Full |
| Continuum | Low | Partial (compute shaders required) |

---

## Implementation Timeline

### Overall Duration: 20-28 weeks (5-7 months)

```
Month 1: Foundation & Loading
├── Week 1-2: Phase 1 (Foundation Infrastructure)
├── Week 3-4: Phase 2 (Shader Pack Loading)
└── Week 4: Initial testing with simple shader pack

Month 2: Rendering Pipeline
├── Week 5-6: Phase 3 (Pipeline Transformation)
├── Week 7-8: Phase 4 (G-Buffer System)
└── Week 8: Integration testing

Month 3: Advanced Rendering
├── Week 9-10: Phase 5 (Shadow Mapping)
├── Week 11-12: Phase 6 (Uniforms)
└── Week 12: Shadow testing

Month 4: UI & Advanced Features
├── Week 13-14: Phase 7 (Configuration & UI)
├── Week 15-17: Phase 8 (Advanced Features)
└── Week 17: Feature complete milestone

Month 5: Optimization & Testing
├── Week 18-19: Phase 9 (Performance Optimization)
├── Week 20-22: Phase 10 (Testing & Validation)
└── Week 22: Beta release

Month 6-7: Polish & Compatibility
├── Week 23-25: Bug fixes from beta testing
├── Week 26-27: Additional shader pack compatibility
└── Week 28: Production release
```

---

## Risk Assessment and Mitigation

### Technical Risks

| Risk | Probability | Impact | Mitigation |
|------|-------------|--------|------------|
| OpenGL compatibility issues | Medium | High | Test on multiple GPU vendors, fallback modes |
| Performance degradation | High | High | Profiling, optimization passes, quality settings |
| Shader compilation errors | High | Medium | Robust error handling, shader validation |
| Memory leaks with large packs | Medium | High | Careful resource management, leak detection tools |
| Breaking vanilla rendering | Medium | Critical | Feature flag, extensive vanilla testing |
| Incompatibility with some packs | Medium | Medium | Document limitations, implement workarounds |

### Schedule Risks

| Risk | Probability | Impact | Mitigation |
|------|-------------|--------|------------|
| Underestimated complexity | High | High | Build in 20% buffer time, prioritize features |
| Scope creep | Medium | High | Strict feature freeze after Phase 8 |
| Dependencies on bug fixes | Low | Medium | Work around or patch locally |

### Mitigation Strategies

1. **Incremental Development**: Each phase can be tested independently
2. **Feature Flags**: Keep vanilla path working, shader system optional
3. **Early Testing**: Test with real shader packs from Phase 3 onwards
4. **Community Involvement**: Beta test with community feedback
5. **Performance Budgets**: Set target FPS minimums, optimize early
6. **Documentation**: Document architecture decisions for maintainability

---

## Appendices

### Appendix A: Glossary

- **G-Buffer**: Geometry buffer - multiple render targets storing scene geometry data
- **MRT**: Multiple Render Targets - rendering to multiple textures simultaneously
- **FBO**: Framebuffer Object - OpenGL off-screen rendering target
- **Deferred Rendering**: Rendering technique where lighting is calculated after geometry
- **Shadow Mapping**: Technique for rendering real-time shadows using depth maps
- **Uniform**: GLSL variable passed from CPU to GPU shader program
- **Sampler**: GLSL variable representing a texture
- **Composite Pass**: Post-processing stage that combines or modifies rendered images

### Appendix B: References

- **Iris ShaderDoc**: https://github.com/IrisShaders/ShaderDoc
- **OptiFine Documentation**: https://github.com/sp614x/optifine/tree/master/OptiFineDoc
- **Complimentary Shaders**: https://www.complementary.dev/
- **OpenGL Documentation**: https://www.khronos.org/opengl/
- **LearnOpenGL**: https://learnopengl.com/
- **ShaderLABS Wiki**: https://shaderlabs.org/wiki/

### Appendix C: Code Size Estimates

| Module | Estimated Lines | Files | Complexity |
|--------|----------------|-------|------------|
| Shader Pack Management | 3,000 | 8 | Medium |
| G-Buffer System | 2,000 | 6 | High |
| Shadow Mapping | 2,500 | 5 | High |
| Shader Programs | 4,000 | 12 | High |
| Uniform System | 3,500 | 15 | Medium |
| Properties Parser | 1,500 | 5 | Medium |
| Pipeline Integration | 4,000 | 3 | Very High |
| UI Components | 1,500 | 4 | Low |
| **Total New Code** | **22,000** | **58** | - |
| Modified Existing | ~3,000 | ~15 | - |
| **Grand Total** | **~25,000** | **~73** | - |

### Appendix D: System Requirements

**Minimum Requirements:**
- OpenGL 3.3 support
- 4GB RAM
- Dedicated GPU with 1GB VRAM
- Quad-core CPU

**Recommended Requirements:**
- OpenGL 4.5+ support
- 8GB RAM
- Modern dedicated GPU with 4GB+ VRAM
- Modern multi-core CPU
- SSD for faster shader compilation

**Optimal Requirements:**
- OpenGL 4.6 support
- 16GB+ RAM
- High-end GPU (RTX 3060 / RX 6700 XT or better)
- Modern CPU (8+ cores)
- NVMe SSD

---

## Conclusion

This comprehensive plan outlines the implementation of an OptiFine/Iris-style shader rendering system for MattMC with a unique **baked-in architecture**. The implementation is substantial but achievable in 5-7 months with dedicated development effort.

### Key Success Factors:

1. **Incremental Approach**: Build and test each phase independently
2. **Resource-Based Design**: Leverage Minecraft's existing ResourceManager infrastructure
3. **Dynamic Discovery**: Implement non-hardcoded shader pack detection
4. **Early Validation**: Test with adapted shader packs early and often
5. **Performance Focus**: Profile and optimize throughout development
6. **Documentation**: Maintain clear architecture documentation
7. **Fallback Support**: Always maintain working vanilla rendering path

### Expected Outcome:

Upon completion, MattMC will feature:
- **Multiple baked-in shader packs** bundled with the game (Complimentary Reimagined, BSL, etc.)
- **In-game shader selection** from video settings menu
- **Shader-specific configuration** through dedicated UI
- **Full deferred rendering pipeline** with G-buffers, shadows, and advanced effects
- **Dynamic shader discovery** from resources directory (not hardcoded)
- **Runtime shader switching** between baked-in options without restarting
- **Full dimension support** (Overworld, Nether, End)
- **Performance comparable to OptiFine/Iris** for the bundled shader packs

### Key Architectural Differences:

**MattMC's Baked-In Approach:**
- ✅ Shaders compiled into JAR from `src/main/resources/assets/minecraft/shaders/`
- ✅ Each shader pack is an unzipped directory in resources
- ✅ Dynamic runtime discovery (scans resources, not hardcoded)
- ✅ In-game selection between bundled options
- ❌ No user-added shader packs (requires recompilation)

**vs. OptiFine/Iris:**
- Loads shader packs from `shaderpacks/` folder as ZIP files
- Users can add/remove packs without recompiling
- Runtime file I/O required

**vs. Vanilla Minecraft:**
- Only basic fixed-pipeline shaders
- No deferred rendering, G-buffers, or advanced effects
- No user-selectable shaders

### Post-Implementation:

After the initial release, ongoing work will include:
- **Adding more bundled shader packs** to the resources directory
- **Performance optimizations** based on profiling and user feedback
- **Bug fixes and stability improvements**
- **Enhanced shader options** and configuration capabilities
- **Potential advanced features** (compute shaders, ray tracing hooks)
- **Documentation for shader pack authors** on adapting packs for MattMC's baked-in format

### Adding New Shader Packs:

For developers wanting to add shader packs to MattMC:

1. **Obtain shader pack** (e.g., Complimentary Reimagined ZIP)
2. **Unzip into resources**: `src/main/resources/assets/minecraft/shaders/pack_name/`
3. **Verify structure**: Must contain `shaders/` subdirectory with .vsh/.fsh files
4. **Recompile game**: `./gradlew build`
5. **Shader automatically discovered**: Game scans resources at startup

No code changes needed - the system dynamically discovers all shader packs in the resources directory.

---

**End of MattMC Shader Implementation Plan**

*Document Version: 2.0*  
*Date: December 2024*  
*Project: MattMC Baked-In Shader System*  
*Target Version: MattMC 1.21.10+ (Minecraft Java 1.21.10 Fork)*

**Note**: This is a living document that may be updated as the implementation progresses and new insights are gained.

