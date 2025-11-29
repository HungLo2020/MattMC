# MattMC vs Minecraft: Light and Rendering Comparison

This document analyzes the differences between MattMC's lighting and rendering pipeline compared to Minecraft's implementation, and provides actionable recommendations to make MattMC look less flat and more visually similar to Minecraft.

## Table of Contents
1. [Executive Summary](#executive-summary)
2. [Lighting System Differences](#lighting-system-differences)
3. [Ambient Occlusion Differences](#ambient-occlusion-differences)
4. [Fog System](#fog-system)
5. [Directional Face Shading](#directional-face-shading)
6. [Light Texture (Lightmap)](#light-texture-lightmap)
7. [Shader Pipeline](#shader-pipeline)
8. [Recommendations](#recommendations)

---

## Executive Summary

MattMC's world looks "flat" primarily due to these missing/incomplete features:

| Feature | Minecraft | MattMC | Impact |
|---------|-----------|--------|--------|
| Dynamic Light Texture (Lightmap) | ✅ Full 16x16 lookup table with color tinting | ❌ Missing | **HIGH** |
| Block Light Warmth | ✅ Orange/warm tint for torches | ❌ Uniform color | **MEDIUM** |
| Fog with Distance Blending | ✅ Cylinder/sphere fog blending into sky | ❌ Basic or no fog | **MEDIUM** |
| Directional Face Shading | ⚠️ Applied in shader | ⚠️ AO has it, not fully used | **MEDIUM** |
| Sky Light Darkening at Night | ✅ Smooth transitions | ❌ May be too static | **MEDIUM** |
| Lightning Flash Effects | ✅ Implemented | ❌ Missing | **LOW** |
| Gamma Correction Curve | ✅ Non-linear `notGamma()` | ⚠️ Simple pow() | **LOW** |

---

## Lighting System Differences

### Minecraft's LightTexture System

Minecraft uses a **16x16 dynamic lightmap texture** (`LightTexture.java`) that is updated every frame. This texture encodes the combined brightness of:
- **X-axis (columns)**: Block light level (0-15)  
- **Y-axis (rows)**: Sky light level (0-15)

Each pixel in the lightmap stores the RGB color that should be applied for that combination of block/sky light levels.

**Key features of Minecraft's lightmap:**

1. **Color Tinting**: Block light has a warm orange/yellow tint:
   ```java
   // From LightTexture.java lines 118-122
   float f9 = f9 * ((f9 * 0.6F + 0.4F) * 0.6F + 0.4F); // Green channel
   float f10 = f9 * (f9 * f9 * 0.6F + 0.4F);           // Blue channel
   vector3f1.set(f9, f10, f11);  // Orange-ish tint for block light
   ```

2. **Torch Flicker**: Subtle random brightness variation:
   ```java
   // Line 53-55
   this.blockLightRedFlicker += (float)((Math.random() - Math.random()) * Math.random() * Math.random() * 0.1D);
   this.blockLightRedFlicker *= 0.9F;
   ```

3. **Sky Darkness Blend**: Sky light blends with a blue tint:
   ```java
   // Line 112
   Vector3f vector3f = (new Vector3f(f, f, 1.0F)).lerp(new Vector3f(1.0F, 1.0F, 1.0F), 0.35F);
   ```

4. **Non-linear Gamma**: The `notGamma()` function creates a smooth brightness curve:
   ```java
   // Lines 181-184
   private float notGamma(float pValue) {
      float f = 1.0F - pValue;
      return 1.0F - f * f * f * f;  // Inverse quartic curve
   }
   ```

5. **Brightness Formula**: Block brightness uses Minecraft's specific curve:
   ```java
   // Lines 186-189
   public static float getBrightness(DimensionType pDimensionType, int pLightLevel) {
      float f = (float)pLightLevel / 15.0F;
      float f1 = f / (4.0F - 3.0F * f);  // Non-linear curve!
      return Mth.lerp(pDimensionType.ambientLight(), f1, 1.0F);
   }
   ```

### MattMC's Lighting System

MattMC uses a **per-vertex lighting approach** (`VertexLightSampler.java`) that samples light directly in the mesh builder:

```java
// VertexLightSampler.java - Returns [skyLight, blockLightR, G, B, ao]
float[] light = lightSampler.sampleVertexLight(face, normalIndex, cornerIndex);
```

The shader then applies a simple gamma curve:
```glsl
// voxel_lit.fs
float lightToBrightness(float lightValue, float gamma) {
    float normalized = clamp(lightValue / 15.0, 0.0, 1.0);
    return pow(normalized, gamma);  // Simple power curve
}
```

**What's missing:**
1. No dynamic lightmap texture with color mixing
2. No warm orange tint for block light (torches look white/blue)
3. No flicker effect for block light
4. Simple gamma instead of Minecraft's `notGamma()` quartic curve
5. No brightness curve: `f / (4.0F - 3.0F * f)`

---

## Ambient Occlusion Differences

### Minecraft's AO System (ModelBlockRenderer.AmbientOcclusionFace)

Minecraft's AO is highly sophisticated:

1. **4-sample weighted blending** for each vertex:
   ```java
   // Line 491-498: Average of 4 neighbor samples
   float f9 = (f3 + f + f5 + f8) * 0.25F;
   ```

2. **Non-cubic weight interpolation** for sub-block faces:
   ```java
   // Lines 456-489: Complex weight calculation based on quad shape
   if (pShapeFlags.get(1) && modelblockrenderer$adjacencyinfo.doNonCubicWeight) {
       // Weight blending based on vertex position within the quad
   }
   ```

3. **Direction-based corner sampling**: Uses `AdjacencyInfo` enum with pre-computed sampling directions for each face direction.

4. **View blocking check**: Considers whether adjacent blocks allow light through:
   ```java
   boolean flag = !blockstate4.isViewBlocking(pLevel, pos) || blockstate4.getLightBlock() == 0;
   ```

### MattMC's AO System

MattMC has a working AO implementation (`AmbientOcclusion.java`) that follows similar principles:

```java
// Lines 182-252: Similar 4-sample approach
float edge0Brightness = getShadeBrightness(chunk, e0x, e0y, e0z);
float edge1Brightness = getShadeBrightness(chunk, e1x, e1y, e1z);
float cornerBrightness = getShadeBrightness(chunk, cx, cy, cz);
float faceBrightness = getShadeBrightness(chunk, sx, sy, sz);
float ao = (edge0Brightness + edge1Brightness + cornerBrightness + faceBrightness) * 0.25f;
```

**What's different:**
1. MattMC's AO returns 0-1 brightness values but they may not be applied properly in the shader
2. The `vAO` uniform exists but the fragment shader doesn't use it (line 44: `// Not used yet, reserved for ambient occlusion`)
3. MattMC's brightness values are: 1.0 (air), 0.2 (solid) - simpler than Minecraft's block-specific shading

---

## Fog System

### Minecraft's FogRenderer

Minecraft has an extensive fog system (`FogRenderer.java`):

1. **Biome-based fog color** with smooth transitions:
   ```java
   // Uses CubicSampler for smooth biome color blending
   Vec3 vec32 = CubicSampler.gaussianSampleVec3(vec31, ...);
   ```

2. **Time-of-day fog color blending**:
   ```java
   float f11 = Mth.clamp(Mth.cos(pLevel.getTimeOfDay(pPartialTicks) * ((float)Math.PI * 2F)) * 2.0F + 0.5F, 0.0F, 1.0F);
   ```

3. **Sunrise/sunset color on horizon**:
   ```java
   if (f16 > 0.0F) {
       float[] afloat = pLevel.effects().getSunriseColor(pLevel.getTimeOfDay(pPartialTicks), pPartialTicks);
       if (afloat != null) {
           fogRed = fogRed * (1.0F - f16) + afloat[0] * f16;
           // ... blend sunrise colors
       }
   }
   ```

4. **Fog shape**: Uses `FogShape.CYLINDER` for horizontal fog that blends to sky:
   ```java
   fogrenderer$fogdata.shape = FogShape.CYLINDER;
   ```

### MattMC's Fog

MattMC appears to have minimal or no fog implementation. The `SkyRenderer.java` handles celestial bodies but doesn't implement fog blending.

**Impact**: Without fog, distant terrain has sharp edges against the sky, and there's no atmospheric depth.

---

## Directional Face Shading

### Minecraft's Approach

Each face direction has a base shade multiplier applied via `BlockAndTintGetter.getShade()`:

```java
// From AdjacencyInfo enum in ModelBlockRenderer.java
DOWN(..., 0.5F, ...)   // Bottom faces are darkest
UP(..., 1.0F, ...)     // Top faces are brightest  
NORTH/SOUTH(..., 0.8F, ...) // Sides are medium
WEST/EAST(..., 0.6F, ...)   // These sides are darker
```

This is applied in `renderModelFaceFlat()`:
```java
float f = pLevel.getShade(bakedquad.getDirection(), bakedquad.isShade());
```

### MattMC's Approach

MattMC has directional shading defined in `AmbientOcclusion.java`:

```java
// Lines 327-335
public static float getDirectionalShade(int faceIndex) {
    return switch (faceIndex) {
        case FACE_UP -> 1.0f;
        case FACE_DOWN -> 0.5f;
        case FACE_NORTH, FACE_SOUTH -> 0.8f;
        case FACE_WEST, FACE_EAST -> 0.6f;
        default -> 1.0f;
    };
}
```

**However**: This method exists but may not be called! The mesh building code passes per-vertex colors and light data, but doesn't seem to multiply by directional shade. Check `MeshBuilder.addTopFace()` etc. - the color comes from `uvMapper.extractColor(face)` which may not include directional shading.

---

## Light Texture (Lightmap)

This is the **biggest difference** between the two systems.

### Minecraft's Packed Light

Minecraft packs light into a single integer for GPU efficiency:
```java
// LightTexture.java lines 192-201
public static int pack(int pBlockLight, int pSkyLight) {
   return pBlockLight << 4 | pSkyLight << 20;
}

public static int block(int pPackedLight) {
   return (pPackedLight & 0xFFFF) >> 4;
}

public static int sky(int pPackedLight) {
   return pPackedLight >> 20 & '\uffff';
}
```

The fragment shader then samples the **lightmap texture** using UV coordinates derived from these packed values:
- U = blockLight / 16
- V = skyLight / 16

This allows complex color mixing without per-fragment computation.

### MattMC's Direct Approach

MattMC passes light values directly to the fragment shader:
```glsl
varying vec4 vLightData; // (skyLight, blockLightR, blockLightG, blockLightB)
```

Then computes brightness per-fragment:
```glsl
float skyBrightness = lightToBrightness(skyLight, uLightGamma);
vec3 blockLightColor = lightToColor(blockLightRGB, uLightGamma);
```

**Pros**: Simpler, supports colored light natively
**Cons**: No pre-computed color mixing, no warm tint for torch light, no subtle effects

---

## Shader Pipeline

### Minecraft Shaders

Minecraft uses many specialized shaders for different render types:
- `rendertype_solid`, `rendertype_cutout`, `rendertype_translucent` for blocks
- Entity shaders with lighting
- Post-processing effects

All block shaders sample the lightmap texture using packed light coordinates.

### MattMC Shaders

MattMC has a single `voxel_lit` shader that:
1. Samples base texture
2. Computes light brightness with gamma
3. Adds sky + block light (additive)
4. Applies minimum brightness floor (0.25)

**Issues**:
1. The minimum brightness of 0.25 is too high - makes caves too bright
2. Block light has no warm tint
3. AO value (`vAO`) is passed but unused

---

## Recommendations

### High Priority (Visual Impact)

#### 1. Implement Lightmap Texture System
Create a 16x16 dynamic texture that pre-computes light colors:

```java
public class LightTexture {
    private final NativeImage lightPixels; // 16x16 texture
    
    public void update(float timeOfDay) {
        for (int blockLight = 0; blockLight < 16; blockLight++) {
            for (int skyLight = 0; skyLight < 16; skyLight++) {
                // Compute Minecraft-style color
                float skyBright = getBrightness(skyLight) * getSkyDarkness(timeOfDay);
                float blockBright = getBrightness(blockLight);
                
                // Block light warm tint
                float r = blockBright;
                float g = blockBright * (blockBright * 0.6f + 0.4f);
                float b = blockBright * (blockBright * blockBright * 0.6f + 0.4f);
                
                // Combine
                r = max(skyBright, r);
                g = max(skyBright * 0.8f, g);
                b = max(skyBright * 0.6f, b);
                
                lightPixels.setPixelRGBA(blockLight, skyLight, packColor(r, g, b));
            }
        }
        uploadTexture();
    }
}
```

#### 2. Add Warm Block Light Tint
If not implementing full lightmap, at least add warmth in the shader:

```glsl
// Modified voxel_lit.fs
vec3 lightToColor(vec3 lightRGB, float gamma) {
    vec3 normalized = clamp(lightRGB / 15.0, 0.0, 1.0);
    
    // Apply warm tint (Minecraft-style)
    normalized.g *= (normalized.g * 0.6 + 0.4);
    normalized.b *= (normalized.b * normalized.b * 0.6 + 0.4);
    
    return pow(normalized, vec3(gamma));
}
```

#### 3. Reduce Minimum Brightness
```glsl
// Change from 0.25 to something more atmospheric
finalLightColor = max(finalLightColor, vec3(0.04)); // Much darker minimum
```

### Medium Priority

#### 4. Apply Ambient Occlusion in Shader
```glsl
// Use the vAO value that's already being passed
void main() {
    // ... existing code ...
    
    // Apply AO to final brightness
    float aoFactor = mix(1.0, vAO, 0.6); // Blend factor for AO strength
    finalLightColor *= aoFactor;
    
    gl_FragColor = vColor * texColor * vec4(finalLightColor, 1.0);
}
```

#### 5. Apply Directional Face Shading
In `MeshBuilder.java`, multiply the vertex color by directional shade:

```java
private void addTopFace(...) {
    float[] color = uvMapper.extractColor(face);
    float directionalShade = AmbientOcclusion.getDirectionalShade(0); // TOP = 1.0
    
    // Apply shade to color
    color[0] *= directionalShade;
    color[1] *= directionalShade;
    color[2] *= directionalShade;
    
    // ... rest of method
}
```

#### 6. Implement Distance Fog
Add fog uniforms to the shader:

```glsl
uniform float uFogStart;
uniform float uFogEnd;
uniform vec3 uFogColor;

void main() {
    // ... existing lighting code ...
    
    // Calculate fog based on depth
    float depth = gl_FragCoord.z / gl_FragCoord.w;
    float fogFactor = smoothstep(uFogStart, uFogEnd, depth);
    
    vec3 finalColor = mix(litColor.rgb, uFogColor, fogFactor);
    gl_FragColor = vec4(finalColor, litColor.a);
}
```

### Low Priority (Polish)

#### 7. Use Minecraft's Brightness Curve
```java
// Replace simple division with Minecraft's curve
public static float getBrightness(int lightLevel) {
    float f = (float)lightLevel / 15.0F;
    float f1 = f / (4.0F - 3.0F * f);  // Minecraft's curve
    return f1;
}
```

#### 8. Add Subtle Torch Flicker
```java
public class BlockLightFlicker {
    private float flicker = 0;
    
    public void tick() {
        flicker += (Math.random() - Math.random()) * Math.random() * Math.random() * 0.1;
        flicker *= 0.9f;
    }
    
    public float getFlicker() {
        return 1.5f + flicker; // Center around 1.5 like Minecraft
    }
}
```

---

## Implementation Priority Order

1. **Reduce minimum brightness** to 0.04 (quick fix, immediate visual improvement)
2. **Apply directional face shading** in MeshBuilder (medium effort)
3. **Use AO value in shader** (small change)
4. **Add warm block light tint** in shader (small change)
5. **Implement distance fog** (medium effort)
6. **Create lightmap texture** for full Minecraft parity (large effort)

---

## Summary

MattMC's "flat" appearance is primarily due to:
1. Missing warm tint on block light (torches look cold/white)
2. Too high minimum brightness (caves aren't dark enough)
3. Ambient occlusion computed but not applied in shader
4. No distance fog blending to sky
5. Simple linear lighting instead of Minecraft's curved response

The good news is that MattMC already has many of the building blocks in place (per-vertex lighting, AO calculation, directional shade values). The fixes are mostly about connecting these existing systems and adding the missing color processing in the shader.
