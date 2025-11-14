# Step 7: Shader Lighting with Gamma Mapping

Complete implementation of per-vertex lighting in GLSL shaders with configurable gamma correction.

## Implementation

### Vertex Shader (voxel_lit.vs)

The vertex shader now reads light data from `gl_MultiTexCoord1` and passes it to the fragment shader:

```glsl
varying vec3 vLightData; // (skyLight, blockLight, ao)

void main() {
    // ... position/texture/color ...
    
    // Pass through light data from secondary texture coordinate
    vLightData = gl_MultiTexCoord1.xyz;
}
```

### Fragment Shader (voxel_lit.fs)

The fragment shader implements the complete lighting pipeline:

```glsl
uniform float uLightGamma;      // Gamma exponent (default 1.4)
uniform float uEmissiveBoost;   // Brightness boost (default 1.0)

float lightToBrightness(float lightValue, float gamma) {
    float normalized = clamp(lightValue / 15.0, 0.0, 1.0);
    return pow(normalized, gamma);
}

void main() {
    // Extract light components
    float skyLight = vLightData.x;    // 0-15
    float blockLight = vLightData.y;  // 0-15
    
    // Apply gamma curve
    float skyBrightness = lightToBrightness(skyLight, uLightGamma);
    float blockBrightness = lightToBrightness(blockLight, uLightGamma);
    
    // Combine (whichever is brighter)
    float finalBrightness = max(skyBrightness, blockBrightness);
    
    // Minimum brightness (never completely dark)
    finalBrightness = max(finalBrightness, 0.05);
    
    // Apply emissive boost
    finalBrightness *= uEmissiveBoost;
    
    // Apply to final color
    gl_FragColor = vColor * texColor * vec4(vec3(finalBrightness), 1.0);
}
```

## Gamma Curve

The gamma exponent controls the contrast of the lighting:

### Gamma Values

| Gamma | Effect | Use Case |
|-------|--------|----------|
| 0.7 | Flat lighting, less contrast | High visibility, less atmospheric |
| 1.0 | Linear (no curve) | Technically correct, often looks washed out |
| 1.4 | **Default** - Balanced | Good contrast, natural look |
| 1.8 | High contrast | Dramatic shadows, bright highlights |
| 2.2 | sRGB gamma | Monitor-correct (if framebuffer is linear) |

### Formula

```
brightness = (lightValue / 15) ^ gamma
```

**Example with gamma = 1.4:**
- Light 15: `(15/15)^1.4 = 1.00` (full brightness)
- Light 12: `(12/15)^1.4 = 0.77` (77% brightness)
- Light 8:  `(8/15)^1.4  = 0.45` (45% brightness)
- Light 4:  `(4/15)^1.4  = 0.17` (17% brightness)
- Light 0:  `(0/15)^1.4  = 0.00` → 0.05 (minimum floor)

## Configuration

### Java API

```java
// Get shader from renderer
ChunkRenderer renderer = ...;
VoxelLitShader shader = renderer.getShader();

if (shader != null) {
    // Adjust gamma (1.0-2.0 recommended)
    shader.setLightGamma(1.8f);
    
    // Adjust emissive boost (1.0-1.5 recommended)
    shader.setEmissiveBoost(1.2f);
    
    // Check current values
    float gamma = shader.getLightGamma();
    float boost = shader.getEmissiveBoost();
}
```

### Default Values

```java
public static final float DEFAULT_LIGHT_GAMMA = 1.4f;
public static final float DEFAULT_EMISSIVE_BOOST = 1.0f;
```

## Light Combination

Sky and block light are combined using `max()`:

```glsl
finalBrightness = max(skyBrightness, blockBrightness);
```

**Why max instead of add?**
- **Prevents over-brightening**: Sky already provides base illumination
- **Matches Minecraft**: Whichever light source is brighter dominates
- **More realistic**: Light doesn't stack linearly in real life
- **Better gameplay**: Torches are useful even during daytime

## Minimum Brightness

A minimum brightness floor ensures areas are never completely black:

```glsl
finalBrightness = max(finalBrightness, 0.05);
```

This provides:
- **Better visibility**: Players can always see something
- **Less frustrating**: Navigation is possible even without torches
- **Atmospheric**: Still dark enough to feel dangerous
- **Configurable**: Can be adjusted in shader code

## sRGB Correctness

OpenGL handles sRGB conversion automatically when:
1. Textures are loaded with `GL_SRGB8_ALPHA8` format
2. Framebuffer is created with `GL_FRAMEBUFFER_SRGB` enabled

The shader works in **linear space** and OpenGL converts to sRGB for display.

If manual sRGB correction is needed:

```glsl
// At end of fragment shader (if framebuffer is linear)
vec3 srgb = pow(linearColor.rgb, vec3(1.0/2.2));
gl_FragColor = vec4(srgb, linearColor.a);
```

## Visual Testing

### Test Scenarios

1. **Torch Placement**
   - Place torches in a dark cave
   - Light should fall off smoothly (no banding)
   - Verify 8-sample smoothing creates soft edges

2. **Gamma Comparison**
   - Set gamma = 1.0: Should look washed out
   - Set gamma = 1.4: Balanced, natural
   - Set gamma = 2.0: Dramatic shadows

3. **Cross-Chunk**
   - Place torch near chunk boundary
   - Light should continue smoothly into neighbor chunk
   - No visible seams

4. **Sky + Block Light**
   - Place torch in sunlit area
   - Sky brightness should dominate (gamma curve applied to both)
   - Torch adds slight boost in shadowed areas

### Expected Behavior

- **Smooth falloff**: No hard edges or banding
- **Soft shadows**: 8-sample averaging creates gradient
- **Natural look**: Gamma curve makes lighting feel realistic
- **No seams**: Cross-chunk propagation works
- **Playable darkness**: Minimum brightness allows navigation

## Performance

### Shader Cost

Very efficient - per-fragment operations are:
- 2× light value normalization (divide + clamp)
- 2× pow() for gamma (GPU-optimized)
- 1× max() for combining
- 1× max() for minimum floor
- 1× multiply for final color

Total: ~10 GPU instructions per fragment.

### Optimization Notes

- Light data is **interpolated** by GPU across triangle faces
- Only 3 vertices per triangle need light calculation
- Fragment shader benefits from GPU parallelism
- Gamma curve is smooth (no branching)

## Troubleshooting

### Lighting doesn't appear

**Check:**
1. Is light data being sampled in MeshBuilder? (Step 6)
2. Is gl_MultiTexCoord1 enabled in ChunkVAO?
3. Are shader uniforms being set in ChunkRenderer?
4. Is shader compilation successful?

### Everything is too dark

**Solutions:**
- Increase emissive boost: `shader.setEmissiveBoost(1.5f)`
- Decrease gamma: `shader.setLightGamma(1.0f)`
- Increase minimum brightness in shader (edit .fs file)

### Everything is too bright

**Solutions:**
- Decrease emissive boost: `shader.setEmissiveBoost(0.8f)`
- Increase gamma: `shader.setLightGamma(1.8f)`
- Check light values aren't being over-sampled

### Banding/hard edges

**Check:**
- Is 8-sample smoothing working in MeshBuilder?
- Is vertex interpolation enabled?
- Try bilinear filtering on textures

## Future Enhancements

### Ambient Occlusion

The third component of `vLightData` is reserved for AO:

```glsl
float ao = vLightData.z;  // 0-3 for corner AO
float aoFactor = 1.0 - (ao * 0.15);  // Darken by 15% per AO level
finalBrightness *= aoFactor;
```

### HDR/Bloom

For very bright emissive blocks:

```glsl
if (finalBrightness > 1.0) {
    // Extract bright areas for bloom pass
    bloom = finalBrightness - 1.0;
}
```

### Time of Day

Dynamic sky brightness:

```glsl
uniform float uSkyBrightness; // 0.0-1.0 based on time
float skyBrightness = lightToBrightness(skyLight, uLightGamma) * uSkyBrightness;
```

### Per-Block Emissive

Read from texture alpha or separate emissive map:

```glsl
float emissive = texColor.a; // If alpha stores emissive flag
finalBrightness *= mix(1.0, uEmissiveBoost, emissive);
```

## Files Modified

1. `src/main/resources/assets/shaders/voxel_lit.vs`
   - Added `varying vec3 vLightData`
   - Read from `gl_MultiTexCoord1`

2. `src/main/resources/assets/shaders/voxel_lit.fs`
   - Added gamma mapping function
   - Added light combination logic
   - Added configurable uniforms

3. `src/main/java/mattmc/client/renderer/VoxelLitShader.java`
   - Added `setLightGamma(float)`
   - Added `setEmissiveBoost(float)`
   - Added `applyDefaultLighting()`

4. `src/main/java/mattmc/client/renderer/chunk/ChunkRenderer.java`
   - Call `applyDefaultLighting()` when rendering
   - Added `getShader()` for runtime access

## Complete Data Flow

```
1. Light Propagation (Steps 1-5)
   ↓
2. Chunk Light Storage (nibble arrays)
   ↓
3. Mesh Building (Step 6)
   - Sample 4 positions per vertex
   - Average skyLight and blockLight
   ↓
4. Vertex Buffer Upload
   - Store in gl_MultiTexCoord1 (offset 12-14)
   ↓
5. Vertex Shader
   - Read gl_MultiTexCoord1
   - Pass to fragment shader (vLightData)
   ↓
6. Fragment Shader (Step 7)
   - Apply gamma curve to both lights
   - Combine with max()
   - Apply minimum brightness
   - Multiply texture color
   ↓
7. Display (sRGB framebuffer)
   - GPU converts linear → sRGB
   - Final pixel on screen
```

## Summary

The lighting system is now **fully functional and visual**:

✅ Light propagates through the world (Steps 1-5)  
✅ Light is sampled smoothly per-vertex (Step 6)  
✅ Light is rendered with gamma correction (Step 7)  
✅ Configurable for different visual styles  
✅ Performance optimized for real-time rendering  

The result is smooth, atmospheric lighting that enhances the visual quality and gameplay experience.
