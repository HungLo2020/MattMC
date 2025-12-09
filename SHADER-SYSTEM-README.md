# MattMC Shader System

## Overview

MattMC features a baked-in shader pack system where shader packs are compiled directly into the game JAR rather than loaded from external files. This provides a seamless, integrated shader experience without the complexity of external shader management.

## Architecture

### Key Concepts

1. **Baked-In Design**: Shader packs are stored in `src/main/resources/assets/minecraft/shaders/` and compiled into the JAR
2. **Dynamic Discovery**: Shader packs are discovered at runtime by scanning the resources directory
3. **No External Loading**: No ZIP files, no user shader folders - everything is built into the game
4. **Full Pipeline**: Complete deferred rendering with G-buffers, shadow maps, and post-processing

### Directory Structure

```
src/main/resources/assets/minecraft/shaders/
├── core/           # Vanilla Minecraft shaders (excluded from discovery)
├── post/           # Vanilla post-processing (excluded from discovery)
├── include/        # Vanilla includes (excluded from discovery)
└── <pack_name>/    # Your shader pack
    ├── shaders/    # Shader programs (.vsh, .fsh)
    │   ├── gbuffers_terrain.vsh
    │   ├── gbuffers_terrain.fsh
    │   ├── composite.vsh
    │   ├── composite.fsh
    │   └── final.fsh
    ├── include/    # Shared shader code
    │   └── common.glsl
    ├── shaders.properties  # Pack configuration
    └── pack.mcmeta         # Pack metadata (optional)
```

## Adding a Shader Pack

### Step 1: Create Directory Structure

Create your shader pack directory under `src/main/resources/assets/minecraft/shaders/`:

```bash
mkdir -p src/main/resources/assets/minecraft/shaders/my_shader_pack/shaders
```

### Step 2: Add Shader Programs

Shader programs are named according to their purpose:

**Geometry Pass (gbuffers_*)**:
- `gbuffers_basic` - Simple geometry
- `gbuffers_textured` - Textured geometry
- `gbuffers_terrain` - Terrain blocks
- `gbuffers_water` - Water rendering
- `gbuffers_entities` - Entities
- `gbuffers_skybasic` - Sky rendering
- And many more...

**Shadow Pass**:
- `shadow` - Shadow map rendering
- `shadow_solid` - Solid geometry shadows
- `shadow_cutout` - Cutout geometry shadows

**Post-Processing**:
- `composite` - First composite pass
- `composite1-15` - Additional composite passes
- `deferred` - Deferred lighting
- `final` - Final output

### Step 3: Write Shaders

Example `gbuffers_terrain.vsh`:
```glsl
#version 330 core

in vec3 vaPosition;
in vec2 vaUV0;
in vec4 vaColor;

out vec2 texCoord;
out vec4 vertexColor;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(vaPosition, 1.0);
    texCoord = vaUV0;
    vertexColor = vaColor;
}
```

Example `gbuffers_terrain.fsh`:
```glsl
#version 330 core

in vec2 texCoord;
in vec4 vertexColor;

out vec4 fragColor;

uniform sampler2D Sampler0;

void main() {
    vec4 texColor = texture(Sampler0, texCoord);
    fragColor = texColor * vertexColor;
}
```

### Step 4: Configure Pack (Optional)

Create `shaders.properties`:
```properties
# Shadow map resolution (default: 2048)
shadowMapSize=2048

# Enable/disable features
shadows=true
```

Create `pack.mcmeta`:
```json
{
  "pack": {
    "description": "My Shader Pack",
    "pack_format": 1,
    "name": "My Shaders",
    "author": "Your Name",
    "version": "1.0.0"
  }
}
```

### Step 5: Rebuild and Test

```bash
./gradlew build
```

Your shader pack will be automatically discovered and available in:
**Options → Video Settings → Shaders...**

## Available Uniforms

The shader system provides numerous uniforms for shader programs:

### World State
- `float worldTime` - Current world time (0-24000)
- `int worldDay` - Current day number
- `float frameTimeCounter` - Continuously increasing counter
- `float sunAngle` - Sun position (0-1)
- `float moonAngle` - Moon position (0-1)
- `float rainStrength` - Rain intensity (0-1)
- `float thunderStrength` - Thunder intensity (0-1)
- `float skyBrightness` - Sky brightness (0-1)
- `bool isNether` - True if in the Nether
- `bool isEnd` - True if in the End
- `bool isOverworld` - True if in Overworld

### Camera
- `vec3 cameraPosition` - Camera world position
- `vec3 previousCameraPosition` - Previous frame camera position
- `vec3 viewWidth` - Screen width in pixels
- `vec3 viewHeight` - Screen height in pixels
- `float aspectRatio` - Screen aspect ratio
- `float cameraYaw` - Camera yaw angle
- `float cameraPitch` - Camera pitch angle

### Matrices (set by rendering system)
- `mat4 gbufferModelView` - Model-view matrix
- `mat4 gbufferProjection` - Projection matrix
- `mat4 gbufferModelViewInverse` - Inverse model-view
- `mat4 gbufferProjectionInverse` - Inverse projection

### Textures
- `sampler2D colortex0-7` - G-buffer color attachments
- `sampler2D depthtex0` - Depth texture
- `sampler2D shadowtex0` - Shadow map
- `sampler2D Sampler0-15` - Minecraft textures

## Development Tips

### 1. Use #include for Shared Code

Create reusable code in the `include/` directory:

```glsl
// shaders/gbuffers_terrain.fsh
#include "common.glsl"

void main() {
    vec4 color = getBaseColor();
    fragColor = color;
}
```

### 2. Check Compilation Logs

Shader compilation errors are logged to the console:
```
[ERROR] Failed to compile shader program: gbuffers_terrain
```

### 3. Start Simple

Begin with basic pass-through shaders and gradually add features:
1. Basic geometry rendering
2. Add lighting
3. Add shadows
4. Add post-processing effects

### 4. Test Incrementally

Test each shader program individually before combining them.

## System Components

### Discovery and Loading
- `ShaderPackRepository` - Discovers shader packs in resources
- `ShaderPackLoader` - Loads GLSL files with #include support
- `ShaderPackMetadata` - Pack information and metadata

### Compilation
- `ShaderCompiler` - Compiles GLSL to OpenGL programs
- `CompiledShaderProgram` - Manages OpenGL program lifecycle
- `ShaderProgramType` - Enum of all shader program types

### Rendering Infrastructure
- `ShaderRenderPipeline` - Orchestrates rendering flow
- `GBufferManager` - Manages G-buffer framebuffers (8 color + depth)
- `ShadowMapManager` - Manages shadow map framebuffers
- `UniformManager` - Manages uniform variables

### Uniform Providers
- `WorldStateUniforms` - Provides world state uniforms
- `CameraUniforms` - Provides camera-related uniforms

### UI
- `ShaderPackSelectionScreen` - In-game shader selection UI
- Accessible via: **Options → Video Settings → Shaders...**

## Technical Details

### G-Buffer Format
- **8 color attachments** (colortex0-7): RGBA16F (HDR)
- **Depth attachment**: 24-bit depth

### Shadow Maps
- **Resolution**: Configurable (default 2048x2048)
- **Format**: 24-bit depth
- **Filtering**: Hardware PCF enabled

### Render Pipeline
1. **Shadow Pass**: Render scene from light POV
2. **G-Buffer Pass**: Render geometry to MRT
3. **Deferred Pass**: Compute lighting
4. **Composite Passes**: Post-processing effects
5. **Final Pass**: Output to screen

## Current Limitations

- **No LevelRenderer Integration**: Shaders compile and initialize but don't render yet
- **No Runtime Shader Loading**: Shaders must be baked into JAR
- **No Shader Hot-Reload**: Requires game restart to update shaders

## Future Work

- Integrate with LevelRenderer for actual rendering
- Add block/entity ID encoding
- Support dimension-specific shader overrides
- Custom texture loading from shader packs
- Performance profiling and optimization

## References

- **SHADER-PLAN.md** - Complete implementation specification
- **SHADER-IMPLEMENTATION-STATUS.md** - Current progress tracking
- Shader pack structure inspired by OptiFine/Iris shaders

## Support

For issues or questions about the shader system:
1. Check the logs for compilation errors
2. Review SHADER-IMPLEMENTATION-STATUS.md for current limitations
3. Refer to SHADER-PLAN.md for architectural details

---

**Note**: The shader system is ~50% complete. All infrastructure is in place, but integration with the actual rendering pipeline is still needed for shaders to display in-game.
