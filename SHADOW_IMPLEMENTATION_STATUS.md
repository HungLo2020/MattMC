# Shadow System Implementation - Phase 1

## What's Implemented

### Core Shadow Mapping Infrastructure (Complete)

1. **ShadowMap.java** - Individual shadow map management
   - Creates depth texture + framebuffer for each cascade
   - 2048x2048 resolution depth-only rendering
   - Hardware PCF support via GL_COMPARE_REF_TO_TEXTURE
   - Shadow matrix computation (bias × projection × view)
   - Texture binding for shader access

2. **CascadedShadowRenderer.java** - CSM system coordinator
   - 3-cascade shadow mapping (near/mid/far)
   - Distance splits: 16, 48, 128 blocks
   - Orthographic frustum sizes: 32, 96, 256 blocks
   - Sun direction calculation from DayCycle
   - Depth-only rendering pass for all cascades
   - Only renders shadows during daytime

## Next Steps (Phase 2)

To complete the shadow system, you need to:

### 1. Integrate with LevelRenderer
- Add CascadedShadowRenderer instance to LevelRenderer
- Call `renderShadowMaps()` before main scene rendering
- Provide callback to render chunks in depth-only mode
- Bind shadow textures during main rendering

### 2. Create/Modify Shaders
- **Depth shader** (shadow_depth.vert + shadow_depth.frag)
  - Minimal vertex shader: transform position only
  - Fragment shader: empty (depth written automatically)
  
- **Main lighting shader** modifications
  - Add shadow map samplers (sampler2DShadow × 3)
  - Add shadow matrices (mat4 × 3)
  - Add sun direction uniform (vec3)
  - Implement shadow sampling with PCF
  - Compute NdotL (diffuse term)
  - Multiply by shadow factor

### 3. Settings Integration
- Add "Enable Shadows" option to OptionsManager
- Default to disabled (performance impact)
- Hot-reload support (rebuild shadow maps on toggle)

### 4. Shader Code Template

Main fragment shader additions needed:

```glsl
uniform sampler2DShadow shadowMap0;  // Near cascade
uniform sampler2DShadow shadowMap1;  // Mid cascade
uniform sampler2DShadow shadowMap2;  // Far cascade
uniform mat4 shadowMatrix0;
uniform mat4 shadowMatrix1;
uniform mat4 shadowMatrix2;
uniform vec3 sunDirection;

float sampleShadow(int cascade, vec4 shadowCoord) {
    // Select cascade
    if (cascade == 0) return texture(shadowMap0, shadowCoord.xyz);
    if (cascade == 1) return texture(shadowMap1, shadowCoord.xyz);
    return texture(shadowMap2, shadowCoord.xyz);
}

float getShadowFactor(vec3 worldPos, float dist) {
    // Select cascade based on distance
    int cascade = 0;
    if (dist > 16.0) cascade = 1;
    if (dist > 48.0) cascade = 2;
    
    // Transform to shadow space
    mat4 shadowMat = (cascade == 0) ? shadowMatrix0 :
                     (cascade == 1) ? shadowMatrix1 : shadowMatrix2;
    vec4 shadowCoord = shadowMat * vec4(worldPos, 1.0);
    
    // Sample shadow map with PCF
    return sampleShadow(cascade, shadowCoord);
}

void main() {
    // ... existing light calculations ...
    
    // Get skylight and block light from lightmap
    float skyLight = lightCoord.x;
    float blockLight = lightCoord.y;
    
    // Directional sun lighting
    float NdotL = max(dot(normal, sunDirection), 0.0);
    float shadowFactor = getShadowFactor(worldPosition, distance);
    
    // Final lighting
    vec3 ambient = skyLight * 0.1;  // Base ambient from skylight
    vec3 diffuse = sunColor * NdotL * shadowFactor;
    vec3 emission = blockLight * emissionColor;
    
    fragColor = vec4(texColor.rgb * (ambient + diffuse + emission), 1.0);
}
```

### 5. Files to Modify/Create

**Modify:**
- `LevelRenderer.java` - Add shadow rendering integration
- `VoxelLitShader.java` or create new shader class
- `OptionsManager.java` - Add shadow toggle

**Create:**
- `src/main/resources/shaders/shadow_depth.vert`
- `src/main/resources/shaders/shadow_depth.frag`
- `src/main/resources/shaders/voxel_shadow.vert`
- `src/main/resources/shaders/voxel_shadow.frag`

## Current Status

✅ Phase 1: Core shadow infrastructure (THIS PR)
- ShadowMap class with depth texture management
- CascadedShadowRenderer with 3-cascade setup
- Sun direction calculation
- Matrix math for shadow transforms

⏳ Phase 2: Integration & Shaders (NEXT)
- LevelRenderer integration
- Shader creation and modification
- Settings toggle
- Testing and debugging

## Testing Plan

Once Phase 2 is complete:
1. Enable shadows in settings
2. Verify shadow maps render (use RenderDoc or GL debugger)
3. Check shadows appear under blocks during daytime
4. Verify shadows disappear at night
5. Test performance impact
6. Verify cascade transitions are smooth
