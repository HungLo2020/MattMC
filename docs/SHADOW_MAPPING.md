# Shadow Mapping System

## Overview

MattMC implements shadow mapping to cast realistic shadows from directional sunlight. This document describes the implementation, technical details, and configuration options for the shadow system.

## Features

- **Shadow mapping**: Depth-based shadows from the sun
- **PCF filtering**: 3x3 Percentage Closer Filtering for soft shadow edges
- **Day/night cycle integration**: Shadows only render during daytime when sun is visible
- **Configurable**: Can be enabled/disabled via settings
- **Optimized**: Uses orthographic projection for directional light
- **Seamless integration**: Works with existing smooth lighting and ambient occlusion

## Technical Implementation

### Components

#### 1. ShadowMapFramebuffer (`mattmc.client.renderer.ShadowMapFramebuffer`)

Manages a depth-only framebuffer for rendering the scene from the sun's perspective.

**Features**:
- 2048x2048 depth texture (configurable)
- GL_DEPTH_COMPONENT format for depth storage
- GL_CLAMP_TO_EDGE wrapping to handle out-of-bounds samples
- No color buffer (depth-only rendering)

**Key Methods**:
- `bind()` - Bind framebuffer for shadow rendering
- `unbind()` - Return to default framebuffer
- `bindDepthTexture(int textureUnit)` - Bind depth texture for sampling
- `clear()` - Clear depth buffer

#### 2. ShadowRenderer (`mattmc.client.renderer.ShadowRenderer`)

Handles the shadow map rendering pass.

**Configuration**:
- Shadow map size: 2048x2048 pixels
- Shadow frustum size: 128 units (covers area around player)
- Shadow depth range: -200 to 200 units

**Shadow Matrix Calculation**:
```
shadowMatrix = biasMatrix × projectionMatrix × viewMatrix
```

Where:
- `biasMatrix`: Transforms from [-1,1] NDC to [0,1] texture space
- `projectionMatrix`: Orthographic projection for directional light
- `viewMatrix`: Look-at matrix from sun position to player

**Rendering Process**:
1. Bind shadow framebuffer
2. Set up orthographic projection (centered on player)
3. Set up view matrix (looking from sun toward player)
4. Calculate shadow matrix for shader uniforms
5. Enable depth shader
6. Disable color writes, enable front-face culling
7. Render all chunk geometry depth-only
8. Restore state and unbind framebuffer

#### 3. Shadow Depth Shader

**Vertex Shader** (`shadow_depth.vs`):
```glsl
#version 120

void main() {
    gl_Position = gl_ProjectionMatrix * gl_ModelViewMatrix * gl_Vertex;
}
```

**Fragment Shader** (`shadow_depth.fs`):
```glsl
#version 120

void main() {
    // Depth is automatically written
    gl_FragColor = vec4(1.0);
}
```

Simple pass-through shader that only writes depth values.

#### 4. VoxelLitShader Updates

**New Uniforms**:
- `uShadowMap` (sampler2D): Shadow depth texture
- `uShadowMatrix` (mat4): Transform to shadow texture space
- `uShadowsEnabled` (int): Toggle shadows on/off

**Vertex Shader Changes**:
- Added `vShadowCoord` output varying
- Calculates shadow coordinates: `vShadowCoord = uShadowMatrix * worldPos`

**Fragment Shader Changes**:
- Added `calculateShadow()` function
- Implements PCF (Percentage Closer Filtering) with 3x3 samples
- Applies bias to prevent shadow acne
- Returns shadow factor (1.0 = fully lit, 0.0 = fully shadowed)
- Only applies shadow to sun diffuse component, not ambient light

### Shadow Calculation Algorithm

```glsl
float calculateShadow() {
    // Perspective divide to NDC
    vec3 projCoords = vShadowCoord.xyz / vShadowCoord.w;
    
    // Transform to [0,1] range
    projCoords = projCoords * 0.5 + 0.5;
    
    // Check bounds
    if (out of bounds) return 1.0; // No shadow
    
    // Adaptive bias to prevent shadow acne
    float bias = max(0.005 * (1.0 - dot(normal, sunDir)), 0.001);
    
    // PCF filtering (3x3 samples)
    float shadow = 0.0;
    for (int x = -1; x <= 1; x++) {
        for (int y = -1; y <= 1; y++) {
            float depth = texture2D(shadowMap, coords + offset);
            shadow += currentDepth - bias > depth ? 0.0 : 1.0;
        }
    }
    return shadow / 9.0; // Average
}
```

### Integration with Lighting

Shadows only affect the **sun diffuse lighting** component:

```glsl
// Calculate shadow factor
float shadowFactor = calculateShadow();

// Apply to sun diffuse only
vec3 sunDiffuse = uSunColor * NdotL * skyLight * brightness * shadowFactor;

// Sky lighting = ambient (no shadow) + sun diffuse (with shadow)
vec3 skyLighting = skyAmbient + sunDiffuse;
```

This approach ensures:
- Ambient light provides base illumination in shadows
- Torch/block light is unaffected by shadows
- Shadows only darken sun-facing surfaces
- Natural, Minecraft-like appearance

## Configuration

### Settings

Shadow mapping can be toggled via the options system:

**OptionsManager Methods**:
- `OptionsManager.areShadowsEnabled()` - Check if shadows are enabled
- `OptionsManager.setShadowsEnabled(boolean)` - Enable/disable shadows
- `OptionsManager.toggleShadows()` - Toggle shadow state

**Options File** (`Options.txt`):
```
# Shadow mapping (true = enabled, false = disabled)
# NOTE: This is an experimental feature, disabled by default
shadows=false
```

### Performance Considerations

**Shadow Map Resolution**:
- Current: 2048x2048
- Higher resolution = better quality, lower performance
- Lower resolution = faster rendering, more pixelated shadows

**Shadow Frustum Size**:
- Current: 128 units
- Larger frustum = more area covered, lower shadow resolution
- Smaller frustum = higher resolution, less area covered

**Rendering Overhead**:
- Shadow pass renders all visible chunks twice (depth + color)
- PCF filtering adds 9 texture samples per fragment
- Only active during daytime (brightness > 0.3)

### Quality vs Performance

| Setting | Shadow Map Size | Frustum Size | Quality | Performance |
|---------|-----------------|--------------|---------|-------------|
| Low     | 1024x1024       | 96 units     | Basic   | Fast        |
| Medium  | 2048x2048       | 128 units    | Good    | Moderate    |
| High    | 4096x4096       | 160 units    | Excellent | Slow      |

Current implementation uses **Medium** settings.

## Shadow Artifacts and Solutions

### Shadow Acne

**Problem**: Self-shadowing creates a moiré pattern on surfaces.

**Solution**: 
- Adaptive bias based on surface angle
- Front-face culling during shadow pass
- Bias formula: `bias = max(0.005 * (1.0 - N·L), 0.001)`

### Peter Panning

**Problem**: Objects appear to float above ground (from too much bias).

**Solution**: Carefully tuned bias values to minimize artifact while preventing acne.

### Shadow Aliasing

**Problem**: Blocky, pixelated shadow edges.

**Solution**: 
- PCF filtering (3x3 samples) for soft edges
- Higher shadow map resolution
- Consider implementing cascaded shadow maps for better resolution distribution

## Future Enhancements

Possible improvements:

1. **Cascaded Shadow Maps (CSM)**: Multiple shadow maps at different resolutions for better detail distribution
2. **Variable penumbra**: Softer shadows for distant light sources
3. **Dynamic shadow map resolution**: Adjust based on performance/quality setting
4. **Shadow fade distance**: Gradually fade shadows at far distances
5. **Moon shadows**: Shadow mapping for night-time moon light
6. **PCSS (Percentage Closer Soft Shadows)**: More realistic soft shadows
7. **Contact hardening**: Shadows get softer with distance from caster

## Compatibility

- Works with existing smooth lighting system
- Compatible with ambient occlusion
- Integrates with day/night cycle
- No changes required to world data or chunk storage
- Fully optional via settings

## Performance Impact

**Approximate overhead**:
- Shadow pass: ~30-40% of main rendering time
- PCF filtering: ~5-10% fragment shader cost
- Total impact: ~20-30% FPS reduction when enabled
- No impact when disabled or at night

**Optimization opportunities**:
- Frustum culling for shadow pass (not yet implemented)
- Lower shadow map resolution setting
- Simpler filtering (2x2 instead of 3x3)
- Shadow distance limit

## References

- OpenGL Shadow Mapping Tutorial
- Real-Time Rendering, 4th Edition (Chapter 7: Shadows)
- GPU Gems: Efficient Shadow Volume Rendering
- Minecraft Java Edition shadow mapping techniques

## Related Documentation

- [DAY_NIGHT_CYCLE.md](DAY_NIGHT_CYCLE.md) - Sun direction and day cycle
- [SMOOTH_LIGHTING.md](SMOOTH_LIGHTING.md) - Smooth lighting and ambient occlusion
- README.md - Project overview
