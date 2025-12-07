# Rendering System

## Overview

The Minecraft rendering system is a complex multi-layered architecture built on OpenGL 4.4+ through LWJGL 3. The system is divided into two major components: **Blaze3D** (low-level OpenGL abstraction) and the **Minecraft Renderer** (game-specific rendering logic).

## Architecture

```
┌─────────────────────────────────────────┐
│         Game Logic Layer                │
│   (Entities, Blocks, UI, etc.)         │
└─────────────────┬───────────────────────┘
                  │
┌─────────────────▼───────────────────────┐
│      Minecraft Renderer Layer           │
│  - GameRenderer                         │
│  - LevelRenderer (world)                │
│  - EntityRenderer (entities)            │
│  - BlockRenderer (blocks)               │
│  - GuiRenderer (UI)                     │
└─────────────────┬───────────────────────┘
                  │
┌─────────────────▼───────────────────────┐
│         Blaze3D Layer                   │
│  - RenderSystem (state management)      │
│  - GlStateManager (OpenGL state)        │
│  - Vertex buffers and formats           │
│  - Shader management                    │
│  - Texture management                   │
└─────────────────┬───────────────────────┘
                  │
┌─────────────────▼───────────────────────┐
│      LWJGL 3 / OpenGL 4.4+              │
│  (Native OpenGL bindings)               │
└─────────────────────────────────────────┘
```

## Core Components

### 1. Blaze3D (`com.mojang.blaze3d`)

Low-level rendering abstraction layer providing a Java-friendly interface to OpenGL.

#### Key Classes

**RenderSystem** (`com.mojang.blaze3d.systems.RenderSystem`)
- Central rendering state manager
- Thread-safe queue for render commands
- Ensures all OpenGL calls happen on render thread
- Manages global rendering state

**GlStateManager** (`com.mojang.blaze3d.opengl.GlStateManager`)
- Direct OpenGL state management
- Caching of OpenGL state to reduce redundant calls
- Manages:
  - Blend modes
  - Depth testing
  - Culling
  - Scissor testing
  - Textures
  - Framebuffers

**Vertex System**
- **VertexFormat**: Defines vertex data layout (position, color, UV, normal, etc.)
- **VertexBuffer**: GPU-side vertex buffer management
- **BufferBuilder**: CPU-side vertex data construction
- **VertexConsumer**: Interface for writing vertex data

**Shader System** (`com.mojang.blaze3d.shaders`)
- **GlShaderModule**: Individual shader compilation (vertex/fragment)
- **GlProgram**: Linked shader program
- **Uniform**: Shader uniform variable management
- **UniformType**: Supported uniform types (float, vec3, mat4, etc.)

**Texture System**
- **GlTexture**: Texture object management
- **TextureAtlas**: Sprite sheet management
- **NativeImage**: Image data handling

**OpenGL Abstractions** (`com.mojang.blaze3d.opengl`)
- **GlBuffer**: Buffer object wrapper (VBO, IBO, UBO)
- **GlDevice**: Device capability queries
- **GlFence**: Synchronization primitives
- **GlRenderPipeline**: Render pass management
- **DirectStateAccess**: DSA optimization when available

#### Pipeline Features
- **FrameGraph System**: Modern frame graph for managing render passes
- **RenderTarget**: Framebuffer abstraction for render-to-texture
- **Pipeline States**: Configurable rendering pipeline
- **Cross-Frame Resource Pool**: Resource reuse across frames

### 2. Game Renderer (`net.minecraft.client.renderer`)

High-level game-specific rendering logic.

#### GameRenderer

**Location**: `net.minecraft.client.renderer.GameRenderer`

Main renderer orchestrating the entire rendering pipeline.

**Responsibilities**:
- Main render loop coordination
- Camera management
- Projection matrix setup
- Post-processing effects
- Screenshot capture
- Hand/item rendering

**Key Methods**:
- `render()`: Main render entry point
- `renderLevel()`: World rendering
- `renderItemInHand()`: First-person item rendering
- `pick()`: Ray casting for block/entity selection

**Render Pipeline Order**:
1. Setup camera and matrices
2. Render world (LevelRenderer)
3. Render entities
4. Render particles
5. Render translucent blocks
6. Render hand/items
7. Post-processing effects
8. Render GUI

#### LevelRenderer

**Location**: `net.minecraft.client.renderer.LevelRenderer`

Renders the game world including terrain, blocks, and world effects.

**Key Features**:
- **Chunk rendering**: Frustum culling and occlusion culling
- **Block rendering**: Solid, cutout, translucent layers
- **Sky rendering**: Sun, moon, stars, clouds
- **Weather**: Rain and snow particle effects
- **Lighting**: Dynamic light updates
- **Shadows**: Optional shadow rendering
- **Transparency sorting**: Back-to-front sorting for correct blending

**Rendering Passes**:
1. **Solid blocks**: Opaque geometry (GL_CULL_FACE enabled)
2. **Cutout blocks**: Alpha-tested geometry (grass, leaves)
3. **Cutout-mipped**: Same as cutout with mipmaps
4. **Translucent blocks**: Alpha-blended geometry (water, glass)

**Optimization Techniques**:
- Chunk frustum culling
- Occlusion queries
- Chunk render batching
- Vertex buffer deduplication

#### Entity Rendering

**Location**: `net.minecraft.client.renderer.entity`

Renders all game entities (mobs, items, projectiles, etc.).

**EntityRenderDispatcher**: Routes entities to appropriate renderers

**EntityRenderer Types**:
- **LivingEntityRenderer**: Base for living entities
  - Model rendering
  - Animation support
  - Layer rendering (armor, held items, etc.)
- **ItemEntityRenderer**: Dropped item rendering
- **ProjectileRenderer**: Arrow, fireball rendering
- **BlockDisplayRenderer**: Display entities

**Layer System**:
Renders additional features on top of entities:
- Armor layers
- Elytra
- Held items
- Custom layers (glow, saddle, etc.)

**Animation**:
- **EntityModel**: Hierarchical bone structure
- **AnimationState**: Animation state machine
- Interpolation between keyframes

#### Block Rendering

**Location**: `net.minecraft.client.renderer.block`

Renders blocks in the world and in inventory.

**BlockRenderDispatcher**: Main entry point for block rendering

**BlockModelRenderer**: Renders block models
- Quad-based rendering
- Per-face culling
- Ambient occlusion
- Smooth lighting
- Color tinting

**Model System**:
- **BakedModel**: Compiled, ready-to-render model
- **BlockModel**: JSON-defined block model
- **Multipart models**: Conditional model composition

**RenderTypes**:
- Solid
- Cutout
- Cutout-mipped
- Translucent

#### GUI Rendering

**Location**: `net.minecraft.client.gui`

Renders all user interface elements.

**GuiGraphics**: Main UI rendering API
- Text rendering
- Item rendering
- Texture rendering
- 9-slice (nine-patch) rendering
- Tooltips
- Color overlays

**Screen System**:
- Modal screen stack
- Input handling
- Widget system (buttons, sliders, text fields)

### 3. Shader System

Minecraft uses JSON-defined shader programs.

**Shader Locations**: 
- Core shaders: `assets/minecraft/shaders/core/`
- Post-processing: `assets/minecraft/shaders/post/`

**Core Shaders**:
- `position`: Simple position-only
- `position_color`: Position + vertex color
- `position_tex`: Position + texture coordinates
- `position_tex_color`: Position + texture + color
- `rendertype_solid`: Solid block rendering
- `rendertype_cutout`: Cutout block rendering
- `rendertype_translucent`: Translucent rendering
- `rendertype_entity_*`: Entity rendering variants

**Shader Components**:
- **Vertex Shader (.vsh)**: Vertex transformation
- **Fragment Shader (.fsh)**: Pixel shading
- **JSON definition (.json)**: Shader program configuration
  - Attributes (vertex format)
  - Uniforms (parameters)
  - Samplers (textures)

**Common Uniforms**:
- `ModelViewMat`: Model-view matrix
- `ProjMat`: Projection matrix
- `ChunkOffset`: Chunk position offset
- `FogStart`, `FogEnd`: Fog parameters
- `ColorModulator`: Global color multiplier
- `GameTime`: Animated effects

### 4. Texture System

**Location**: `net.minecraft.client.renderer.texture`

Manages texture loading, atlasing, and binding.

**TextureManager**: Central texture registry
- Texture loading from resources
- Texture caching
- Dynamic textures

**TextureAtlas** (Sprite Sheet):
- Combines many small textures into one large texture
- Reduces texture binding overhead
- Block texture atlas
- Item texture atlas
- Particle texture atlas

**Stitching**: Process of combining textures into atlas
- Automatic layout optimization
- Mipmap generation
- Animated textures support

**Special Textures**:
- **DynamicTexture**: Runtime-generated textures
- **LightTexture**: 2D light level lookup texture
- **OverlayTexture**: Damage/flash overlays

### 5. Lighting System

**Location**: `net.minecraft.client.renderer.LightTexture`

Manages the lighting lookup texture for block and sky light.

**Light Levels**:
- Block light (0-15): Torches, lava, glowstone
- Sky light (0-15): Sunlight/moonlight
- Combined into 16×16 texture (256 combinations)

**Night Vision & Effects**:
- Modifies light texture for potion effects
- Underwater darkening
- Darkness effect

**Smooth Lighting**:
- Interpolates light values between blocks
- Ambient occlusion darkening in corners

### 6. Particle System

**Location**: `net.minecraft.client.particle`

Renders particle effects (smoke, flames, portal particles, etc.).

**ParticleEngine**: Manages all active particles
- Particle spawning
- Tick updates (movement, lifetime)
- Batch rendering by texture
- Frustum culling

**Particle Types**:
- Texture-based particles
- Custom rendered particles
- Atlas-based particles (single texture atlas)

### 7. Post-Processing Effects

**Location**: `net.minecraft.client.renderer.PostChain`

Screen-space effects applied after main rendering.

**Effects**:
- Creeper damage (green tint)
- Spider vision (multiple eyes)
- Spectator shader (entity outlines)
- Blur effects
- Depth of field

**Pipeline**:
1. Render scene to framebuffer
2. Apply shader passes
3. Composite to screen

## Rendering Pipeline Flow

```
1. Frame Start
   ↓
2. Camera Setup (GameRenderer.render())
   - Update camera position
   - Calculate view and projection matrices
   ↓
3. World Rendering (LevelRenderer.renderLevel())
   - Sky rendering
   - Terrain chunks (solid)
   - Entities (opaque)
   - Terrain chunks (cutout)
   - Terrain chunks (translucent) - sorted
   - Particles
   - Weather effects
   ↓
4. Hand/Item Rendering (GameRenderer.renderItemInHand())
   ↓
5. Post-Processing (if enabled)
   ↓
6. GUI Rendering (Screen.render())
   - HUD elements
   - Chat
   - Debug info
   - Active screens
   ↓
7. Frame End
   - Buffer swap
   - FPS limiting
```

## State Management

**RenderSystem Rules**:
1. All OpenGL calls must happen on render thread
2. Use `RenderSystem.assertOnRenderThread()` for safety
3. Queue operations if not on render thread
4. State changes are cached to avoid redundant calls

**Batch Rendering**:
- Group draw calls by render state
- Minimize state changes
- Use instancing where possible

## Performance Considerations

**Optimization Techniques**:
1. **Frustum Culling**: Don't render chunks outside view
2. **Occlusion Culling**: Don't render chunks behind solid chunks
3. **Batching**: Combine multiple draws with same state
4. **VBO Usage**: Keep vertex data on GPU
5. **Texture Atlasing**: Reduce texture binding
6. **Lazy Updates**: Update only when necessary
7. **LOD**: Future system for distant terrain

**Performance Metrics**:
- Visible chunks
- Rendered entities
- Draw calls per frame
- FPS and frame time
- GPU memory usage

## Render Types

Minecraft uses different render types for different content:

**Block Render Types**:
- `SOLID`: Opaque blocks (stone, dirt)
- `CUTOUT`: Alpha-tested (grass, flowers)
- `CUTOUT_MIPPED`: Cutout with mipmaps (leaves)
- `TRANSLUCENT`: Alpha-blended (water, glass)

**Entity Render Types**:
- `ENTITY_SOLID`: Opaque entities
- `ENTITY_CUTOUT`: Alpha-tested entities
- `ENTITY_TRANSLUCENT`: Transparent entities
- `ENTITY_GLINT`: Enchantment glint effect

## Debug Rendering

**F3 Debug Features**:
- Chunk borders
- Hitbox rendering
- Path finding visualization
- Light level overlay
- Profiler charts

**Debug Renderer Classes**:
- `DebugRenderer`: Main debug rendering coordinator
- Specialized renderers for different debug views

## Key Files

- `GameRenderer.java`: Main rendering orchestrator
- `LevelRenderer.java`: World rendering (56K+ lines)
- `EntityRenderDispatcher.java`: Entity rendering
- `BlockRenderDispatcher.java`: Block rendering
- `RenderSystem.java`: OpenGL abstraction
- `GlStateManager.java`: OpenGL state management
- `VertexBuffer.java`: GPU buffer management
- `TextureAtlas.java`: Texture atlas/spritesheet management

## Related Systems

- [Entity System](ENTITY-SYSTEM.md) - Entity models and rendering
- [World System](WORLD-GENERATION-SYSTEM.md) - Chunk data for rendering
- [Resource System](PROJECT-STRUCTURE.md#resources) - Loading textures and models
