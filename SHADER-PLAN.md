# Comprehensive Shader Compatibility Implementation Plan for MattMC
## Full OptiFine/Iris-Compatible Shader System Integration

---

## Executive Summary

This document outlines a complete, in-depth implementation plan to add **full shader pack compatibility** to the MattMC Minecraft client, enabling users to drop shader packs like **Complimentary Reimagined** into a `shaderpacks` folder and select/use them in-game with complete feature parity to OptiFine and Iris shader loaders.

The implementation will transform MattMC from using vanilla Minecraft's basic shader system (core shaders for basic rendering + post-processing effects) into a comprehensive shader mod architecture that supports the advanced OptiFine/Iris shader pack format, including:
- Deferred rendering pipeline with G-buffers
- Shadow mapping with cascaded shadow maps
- Custom uniforms for world/player state
- Dynamic shader pack loading and hot-swapping
- Per-pack configuration and user options
- Advanced post-processing effects
- Full compatibility with existing shader packs

**Target Compatibility**: Complimentary Reimagined r5.6.1+ and other major shader packs (BSL, Sildurs, SEUS PTGI, Vanilla Plus, etc.)

**Estimated Implementation Scope**: ~15,000-25,000 lines of new code across 80-120 new classes plus modifications to existing rendering pipeline.

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

Based on analysis of the MattMC codebase (Minecraft 1.21.10), the current shader system includes:

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

### Gap Analysis: What's Missing for Full Shader Pack Support

To achieve full compatibility with OptiFine/Iris shader packs like Complimentary Reimagined, MattMC needs:

| Feature | Current State | Required State |
|---------|---------------|----------------|
| **Shader Pack Loading** | None | Dynamic loading from `shaderpacks/` folder |
| **G-Buffers** | Single color + depth buffer | Multiple color attachments (8+) for deferred data |
| **Shadow Mapping** | None | Shadow pass with depth textures, cascaded shadow maps |
| **Gbuffers Programs** | Fixed core shaders | Dynamic gbuffers_* programs per geometry type |
| **Composite Passes** | Basic post-processing | Multiple composite stages with access to all buffers |
| **Uniforms System** | ~20 basic uniforms | 200+ uniforms for world/player/camera state |
| **Shader Properties** | None | Parse `shaders.properties` for configuration |
| **Custom Textures** | None | Custom texture loading per shader pack |
| **Dimension-Specific Shaders** | None | Per-dimension shader programs |
| **Block/Entity ID Buffers** | None | Entity and block ID encoding for advanced effects |
| **Dynamic Shader Switching** | Compile-time only | Runtime shader pack selection |
| **Shader Options Menu** | None | In-game UI for per-pack settings |

---

## Shader Pack Architecture Deep Dive

### Understanding OptiFine/Iris Shader Pack Format

A shader pack (like Complimentary Reimagined) is a ZIP file with the following structure:

```
ComplementaryReimagined_r5.6.1.zip
├── shaders/
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
│   └── world-1/                        # Dimension-specific shaders
│       └── composite.fsh
├── textures/                           # Custom textures
│   ├── noise.png
│   ├── waterNormal.png
│   ├── clouds.png
│   └── ...
└── pack.mcmeta                         # Pack metadata (optional)
```

### Rendering Pipeline Flow

The OptiFine/Iris shader pack rendering pipeline operates in distinct stages:

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
│  │  - Scans shaderpacks/ folder                                     │   │
│  │  - Loads available packs                                         │   │
│  │  - Provides pack selection API                                   │   │
│  └──────────────────────────┬───────────────────────────────────────┘   │
│                             ↓                                             │
│  ┌─────────────────────────────────────────────────────────────────┐   │
│  │                    Shader Pack Loader                            │   │
│  │  - Unzips/reads shader pack files                               │   │
│  │  - Parses shaders.properties                                     │   │
│  │  - Loads GLSL shader files                                       │   │
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

**Purpose**: Discover and manage available shader packs.

**Implementation**:

```java
package net.minecraft.client.renderer.shader.pack;

public class ShaderPackRepository {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String SHADER_PACKS_DIR = "shaderpacks";
    
    private final Path shaderPacksDirectory;
    private final List<ShaderPackMetadata> availablePacks;
    private ShaderPack activePack;
    
    public ShaderPackRepository(Path gameDirectory) {
        this.shaderPacksDirectory = gameDirectory.resolve(SHADER_PACKS_DIR);
        this.availablePacks = new ArrayList<>();
        ensureDirectoryExists();
    }
    
    public void scanForPacks() {
        availablePacks.clear();
        
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(shaderPacksDirectory)) {
            for (Path path : stream) {
                if (isValidShaderPack(path)) {
                    ShaderPackMetadata metadata = loadMetadata(path);
                    if (metadata != null) {
                        availablePacks.add(metadata);
                    }
                }
            }
        } catch (IOException e) {
            LOGGER.error("Failed to scan shader packs directory", e);
        }
        
        LOGGER.info("Found {} shader packs", availablePacks.size());
    }
    
    private boolean isValidShaderPack(Path path) {
        // Check if it's a ZIP file or directory with shaders/ folder
        if (Files.isDirectory(path)) {
            return Files.exists(path.resolve("shaders"));
        } else if (path.toString().endsWith(".zip")) {
            return true; // Will validate contents during load
        }
        return false;
    }
    
    public List<ShaderPackMetadata> getAvailablePacks() {
        return Collections.unmodifiableList(availablePacks);
    }
    
    public CompletableFuture<ShaderPack> loadShaderPack(ShaderPackMetadata metadata) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                ShaderPackLoader loader = new ShaderPackLoader(metadata.path());
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

### 1.3 Shader Pack Metadata

```java
public record ShaderPackMetadata(
    String name,
    String description,
    String author,
    String version,
    Path path,
    boolean isDirectory
) {
    public static ShaderPackMetadata fromPath(Path path) {
        // Read pack.mcmeta or shaders.properties for metadata
        // Fallback to filename if no metadata available
    }
}
```

### 1.4 File System Abstraction

**Purpose**: Handle both ZIP files and directory-based shader packs uniformly.

```java
public interface ShaderPackFileSystem extends AutoCloseable {
    InputStream openFile(String path) throws IOException;
    boolean fileExists(String path);
    List<String> listFiles(String directory);
    
    static ShaderPackFileSystem open(Path packPath) throws IOException {
        if (Files.isDirectory(packPath)) {
            return new DirectoryShaderPackFileSystem(packPath);
        } else {
            return new ZipShaderPackFileSystem(packPath);
        }
    }
}
```

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

**Goal**: Implement shader pack file parsing and GLSL shader loading.

**Duration**: 2-3 weeks

### 2.1 Shader Pack Loader

```java
public class ShaderPackLoader {
    private final ShaderPackFileSystem fileSystem;
    private final Map<String, String> shaderSources = new HashMap<>();
    private ShaderProperties properties;
    
    public ShaderPack load() throws IOException {
        // 1. Load shaders.properties
        properties = loadShaderProperties();
        
        // 2. Discover all shader program files
        Map<ShaderProgramType, ShaderProgramSource> programs = discoverPrograms();
        
        // 3. Load custom textures
        Map<String, Path> customTextures = loadCustomTextures();
        
        // 4. Parse block.properties and entity.properties
        BlockMappings blockMappings = loadBlockMappings();
        EntityMappings entityMappings = loadEntityMappings();
        
        return new ShaderPack(properties, programs, customTextures, 
                              blockMappings, entityMappings);
    }
    
    private Map<ShaderProgramType, ShaderProgramSource> discoverPrograms() {
        Map<ShaderProgramType, ShaderProgramSource> programs = new EnumMap<>(ShaderProgramType.class);
        
        // Scan for gbuffers_* programs
        for (GBuffersProgram type : GBuffersProgram.values()) {
            String vshPath = "shaders/" + type.getName() + ".vsh";
            String fshPath = "shaders/" + type.getName() + ".fsh";
            
            if (fileSystem.fileExists(vshPath) || fileSystem.fileExists(fshPath)) {
                String vertexSource = loadShaderSource(vshPath);
                String fragmentSource = loadShaderSource(fshPath);
                programs.put(type, new ShaderProgramSource(vertexSource, fragmentSource));
            }
        }
        
        // Scan for composite programs (composite, composite1-15)
        // Scan for deferred programs (deferred, deferred1-15)
        // Scan for shadow, prepare, final programs
        
        return programs;
    }
    
    private String loadShaderSource(String path) throws IOException {
        if (!fileSystem.fileExists(path)) {
            return null;
        }
        
        try (InputStream is = fileSystem.openFile(path);
             BufferedReader reader = new BufferedReader(new InputStreamReader(is))) {
            
            StringBuilder source = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                // Process #include directives
                if (line.trim().startsWith("#include")) {
                    String includePath = parseIncludePath(line);
                    String includedSource = loadShaderSource(includePath);
                    source.append(includedSource).append("\n");
                } else {
                    source.append(line).append("\n");
                }
            }
            return source.toString();
        }
    }
}
```

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

This comprehensive plan outlines the full implementation of OptiFine/Iris-compatible shader system for MattMC. The implementation is substantial but achievable in 5-7 months with dedicated development effort.

### Key Success Factors:

1. **Incremental Approach**: Build and test each phase independently
2. **Early Validation**: Test with real shader packs early and often
3. **Performance Focus**: Profile and optimize throughout development
4. **Community Testing**: Engage beta testers for broad compatibility testing
5. **Documentation**: Maintain clear architecture documentation
6. **Fallback Support**: Always maintain working vanilla rendering path

### Expected Outcome:

Upon completion, MattMC users will be able to:
- Drop Complimentary Reimagined (and other shader packs) into `shaderpacks/` folder
- Select shader packs from in-game video settings
- Configure shader-specific options through dedicated UI
- Experience fully-featured shader rendering with performance comparable to OptiFine/Iris
- Switch between shader packs without restarting the game
- Use shaders across all dimensions (Overworld, Nether, End)

### Post-Implementation:

After the initial release, ongoing work will include:
- Expanding compatibility with more shader packs
- Performance optimizations based on user feedback
- Bug fixes and stability improvements
- Potential advanced features (compute shaders, ray tracing integration)
- Community-contributed shader pack optimizations

---

**End of Shader Compatibility Implementation Plan**

*Document Version: 1.0*  
*Date: December 2025*  
*Project: MattMC Shader System*  
*Target Version: MattMC 1.21.10+*

