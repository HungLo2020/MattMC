# Cascaded Shadow Maps (CSM) System

## Overview

MattMC implements Cascaded Shadow Maps (CSM) to cast realistic shadows from directional sunlight, similar to Minecraft's Complementary shaders. This document describes the CSM implementation, technical details, and configuration.

## Features

- **3-Cascade Shadow Mapping**: Near, mid, and far cascades for optimal quality distribution
- **Depth-only rendering**: Efficient shadow map generation from sun's perspective
- **PCF filtering**: 3x3 Percentage Closer Filtering for soft shadow edges
- **Day/night cycle integration**: Shadows only render during daytime
- **Configurable**: Can be enabled/disabled via settings
- **Optimized**: Uses orthographic projection for directional light

## Cascade Configuration

### Cascade Splits

The shadow frustum is divided into 3 cascades based on distance from camera:

| Cascade | Distance Range | Frustum Size | Quality | Use Case |
|---------|---------------|--------------|---------|----------|
| Near    | 0-16 blocks   | 32 blocks    | Highest | Immediate player vicinity |
| Mid     | 16-48 blocks  | 96 blocks    | Medium  | Medium distance |
| Far     | 48-128 blocks | 256 blocks   | Lower   | Distant shadows |

### Shadow Map Resolution

Each cascade uses a **2048×2048** depth texture:
- Near cascade: ~64 texels per block (highest detail)
- Mid cascade: ~21 texels per block (medium detail)
- Far cascade: ~8 texels per block (wide coverage)

## Technical Implementation

### Shadow Rendering Pass

**Before each frame**, render the scene 3 times from the sun's perspective:

```java
for (int cascade = 0; cascade < 3; cascade++) {
    1. Bind cascade framebuffer
    2. Set viewport to 2048×2048
    3. Clear depth buffer
    4. Set orthographic projection (cascade-specific frustum)
    5. Set view matrix (looking from sun toward player)
    6. Render all chunks in depth-only mode
    7. Calculate shadow matrix (bias × projection × view)
}
```

**Shadow Matrix Calculation**:
```
shadowMatrix[cascade] = biasMatrix × projectionMatrix[cascade] × viewMatrix[cascade]
```

Where:
- `biasMatrix`: Transforms from [-1,1] NDC to [0,1] texture space
- `projectionMatrix[cascade]`: Orthographic projection for this cascade's frustum
- `viewMatrix[cascade]`: Look-at matrix from sun position to player

### Main Render Pass

**For each pixel** during world rendering:

1. **Transform to light space**: Calculate shadow coordinates for all 3 cascades
   ```glsl
   vShadowCoord0 = uShadowMatrix0 * worldPos;
   vShadowCoord1 = uShadowMatrix1 * worldPos;
   vShadowCoord2 = uShadowMatrix2 * worldPos;
   ```

2. **Select cascade**: Based on distance from camera
   ```glsl
   float dist = length(vWorldPos - uCameraPos);
   if (dist < 16.0) use cascade 0;
   else if (dist < 48.0) use cascade 1;
   else use cascade 2;
   ```

3. **Sample shadow map**: Perform perspective divide and sample
   ```glsl
   vec3 projCoords = shadowCoord.xyz / shadowCoord.w;
   projCoords = projCoords * 0.5 + 0.5;  // [-1,1] to [0,1]
   ```

4. **Depth comparison**: Compare current depth with stored depth
   ```glsl
   float closestDepth = texture2D(shadowMap, projCoords.xy).r;
   float currentDepth = projCoords.z;
   float bias = max(0.005 * (1.0 - N·L), 0.001);
   shadow = (currentDepth - bias > closestDepth) ? 0.0 : 1.0;
   ```

5. **PCF filtering**: Average 3×3 samples for soft edges
   ```glsl
   for (int x = -1; x <= 1; x++) {
       for (int y = -1; y <= 1; y++) {
           sample and accumulate...
       }
   }
   shadow /= 9.0;
   ```

## Shader Integration

### Vertex Shader Changes

**New uniforms**:
```glsl
uniform mat4 uShadowMatrix0; // Near cascade
uniform mat4 uShadowMatrix1; // Mid cascade
uniform mat4 uShadowMatrix2; // Far cascade
```

**New outputs**:
```glsl
varying vec4 vShadowCoord0;
varying vec4 vShadowCoord1;
varying vec4 vShadowCoord2;
```

### Fragment Shader Changes

**New uniforms**:
```glsl
uniform sampler2D uShadowMap0;      // Near cascade shadow map
uniform sampler2D uShadowMap1;      // Mid cascade shadow map
uniform sampler2D uShadowMap2;      // Far cascade shadow map
uniform vec3 uCameraPos;            // For cascade selection
uniform float uCascadeSplit0;       // 16.0 blocks
uniform float uCascadeSplit1;       // 48.0 blocks
```

**Shadow calculation**:
```glsl
float calculateShadow() {
    // Select cascade based on distance
    float dist = length(vWorldPos - uCameraPos);
    
    if (dist < uCascadeSplit0) {
        return sampleShadowMap(uShadowMap0, vShadowCoord0, texelSize);
    } else if (dist < uCascadeSplit1) {
        return sampleShadowMap(uShadowMap1, vShadowCoord1, texelSize);
    } else {
        return sampleShadowMap(uShadowMap2, vShadowCoord2, texelSize);
    }
}
```

## Components

### 1. CascadedShadowRenderer

Manages the 3 shadow cascades and rendering passes.

**Key Methods**:
- `renderShadowCascades()` - Render all 3 cascades from sun's perspective
- `getCascadeShadowMatrix(int)` - Get shadow matrix for a specific cascade
- `bindCascadeShadowMap(int, int)` - Bind cascade shadow map to texture unit
- `getCascadeSplits()` - Get cascade distance thresholds

**Configuration**:
```java
private static final int NUM_CASCADES = 3;
private static final int SHADOW_MAP_SIZE = 2048;

private static final float[] CASCADE_SPLITS = {
    16.0f,   // Near
    48.0f,   // Mid
    128.0f   // Far
};

private static final float[] CASCADE_FRUSTUM_SIZES = {
    32.0f,   // Near: tight coverage
    96.0f,   // Mid: medium coverage
    256.0f   // Far: wide coverage
};
```

### 2. ShadowMapFramebuffer

Unchanged from previous implementation. Each cascade uses its own instance:
- Depth-only framebuffer
- GL_DEPTH_COMPONENT texture
- GL_CLAMP_TO_EDGE wrapping

### 3. VoxelLitShader

Updated to support CSM uniforms:

**New Methods**:
- `setShadowMapSamplers(int, int, int)` - Set samplers for 3 cascades
- `setShadowMatrices(float[], float[], float[])` - Set all 3 shadow matrices
- `setCascadeSplits(float, float)` - Set cascade split distances

## Performance Impact

**Rendering Overhead**:
- Shadow pass: ~3× single shadow map cost (one pass per cascade)
- Each cascade renders all visible chunks in depth-only mode
- Total impact: ~40-60% of main rendering time during daytime
- No impact when disabled or at night

**Memory Usage**:
- 3 × 2048×2048 depth textures = ~12 MB GPU memory
- Minimal CPU overhead (matrix calculations only)

**Optimization opportunities**:
- Frustum culling per cascade (different frustums)
- Distance-based chunk LOD for far cascade
- Adjustable cascade splits based on render distance
- Lower resolution for far cascade

## Advantages Over Single Shadow Map

1. **Better quality distribution**: High detail close to player, wide coverage far away
2. **Reduced shadow aliasing**: Each cascade optimized for its distance range
3. **Smoother transitions**: Cascades blend naturally at split boundaries
4. **Complementary shader compatibility**: Similar approach to Minecraft shader packs

## Shadow Artifacts and Solutions

### Shadow Acne

**Problem**: Self-shadowing creates moiré patterns.

**Solution**: 
- Adaptive bias: `bias = max(0.005 * (1.0 - N·L), 0.001)`
- Front-face culling during shadow pass

### Peter Panning

**Problem**: Objects float above ground (excessive bias).

**Solution**: Carefully tuned bias values per cascade.

### Cascade Transitions

**Problem**: Visible seams between cascades.

**Solution**: 
- Consistent bias across cascades
- PCF smooths edges
- Future: Add blend zone between cascades

## Configuration

**Enable/Disable Shadows**:
```java
OptionsManager.setShadowsEnabled(true/false);
```

**Options File** (`Options.txt`):
```
shadows=true  # Enable CSM
```

## Comparison to Complementary Shaders

**Similarities**:
- Cascaded shadow maps approach
- Depth-only rendering pass
- Light space transformation in main pass
- PCF filtering for soft edges

**Differences**:
- Fixed 3 cascades (vs dynamic cascade count)
- Simple orthographic projection (vs perspective shadow maps)
- Basic PCF (vs variable penumbra)
- No contact hardening yet

## Future Enhancements

1. **Dynamic cascade count**: Adjust based on render distance
2. **Perspective shadow maps**: For better far cascade quality
3. **Variable penumbra**: Softer shadows for distant lights
4. **Contact hardening**: Shadows soften with distance from caster
5. **Cascade blending**: Smooth transitions between cascades
6. **Temporal AA**: Jitter sample patterns across frames

## Related Documentation

- [DAY_NIGHT_CYCLE.md](DAY_NIGHT_CYCLE.md) - Sun direction and day cycle
- [SMOOTH_LIGHTING.md](SMOOTH_LIGHTING.md) - Voxel lighting and AO
- README.md - Project overview
