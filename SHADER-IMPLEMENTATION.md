# Minecraft Complementary Shader Implementation Plan

This document outlines a comprehensive plan to implement a Minecraft Complementary shader-style rendering system with colored block lights, dynamic shadows, and advanced visual effects.

## Project Baseline

**Current State:**
- OpenGL 2.1 / GLSL 1.20 compatibility
- Voxel-based chunk rendering with VBO/VAO
- Existing systems: cascaded shadow maps, smooth lighting, ambient occlusion, sky/block light
- Day/night cycle with sun/moon positioning
- Fog and gamma correction

**Target:** Complementary shader-style rendering with:
- Physically-based lighting (PBL)
- Colored block lights (torches, lava, etc.)
- Dynamic shadows with soft penumbra
- Volumetric lighting / god rays
- Screen-space reflections (SSR)
- Ambient occlusion (SSAO)
- Water refraction/reflection
- Sky atmosphere scattering

---

## Phase 1: Foundation & Cleanup

### Step 1.1: Clean Slate - Remove Existing Shadow/Light System
**Goal:** Start fresh with a minimal baseline

**Actions:**
- Remove all existing shadow rendering code
- Remove complex lighting propagation system
- Keep only basic texture and color rendering
- Preserve chunk mesh generation and rendering pipeline

**Files to modify:**
- Delete: `CascadedShadowRenderer.java`, `ShadowRenderer.java`, `ShadowDepthShader.java`
- Delete: `LightPropagator.java`, `RelightScheduler.java`, `LightAccessor.java`, `LightStorage.java`
- Delete: `VertexLightSampler.java`, `SkylightInitializer.java`
- Simplify: `voxel_lit.vs`, `voxel_lit.fs` - minimal pass-through
- Simplify: `VoxelLitShader.java`, `ChunkRenderer.java`, `MeshBuilder.java`

**Debug/Test:**
```java
// Add to ChunkRenderer
logger.info("Rendering {} chunks with basic shader", chunkCount);
```
- Visual: World should render with flat shading (no lighting)
- Console: "Rendering X chunks with basic shader"
- Test: Build succeeds, world is visible but flat

**Success Criteria:**
- ✅ Project builds without errors
- ✅ World renders with textures but no lighting
- ✅ No shadow/lighting artifacts
- ✅ Performance baseline established (FPS metric)

---

### Step 1.2: Minimal Lighting Foundation
**Goal:** Implement simple directional sun lighting

**Actions:**
- Add basic Lambert diffuse lighting (N·L)
- Sun direction from existing DayCycle
- No shadows, just basic shading

**Shader changes:**
```glsl
// voxel_lit.fs
uniform vec3 uSunDir;
uniform float uSkyBrightness;

void main() {
    vec4 texColor = texture2D(uTexture, vTexCoord);
    vec3 albedo = vColor.rgb * texColor.rgb;
    
    // Basic Lambert lighting
    float NdotL = max(dot(normalize(vNormal), uSunDir), 0.0);
    vec3 lighting = vec3(0.3) + vec3(0.7) * NdotL * uSkyBrightness;
    
    vec3 color = albedo * lighting;
    gl_FragColor = vec4(color, texColor.a);
}
```

**Debug/Test:**
- Add shader uniform logging
- Visual: Blocks should have directional shading based on sun
- Console: "Sun direction: [x, y, z], brightness: X"
- Test: Day/night cycle affects brightness

**Success Criteria:**
- ✅ Blocks show directional shading
- ✅ Day/night cycle changes lighting
- ✅ No performance regression

---

## Phase 2: Colored Block Lights

### Step 2.1: Block Light Data Structure
**Goal:** Store per-block light emission data with RGB color

**Actions:**
- Create `BlockLightEmission` class with RGB values
- Extend block registry with light emission data
- Define light colors for vanilla blocks:
  - Torch: (255, 200, 120) - warm orange
  - Redstone torch: (255, 50, 50) - red
  - Glowstone: (255, 230, 150) - bright yellow
  - Lava: (255, 100, 50) - orange-red
  - Sea lantern: (150, 200, 255) - cool blue

**Code:**
```java
public class BlockLightEmission {
    public final int r, g, b;  // 0-255
    public final int intensity; // 0-15 (Minecraft light level)
    
    public BlockLightEmission(int r, int g, int b, int intensity) {
        this.r = r;
        this.g = g;
        this.b = b;
        this.intensity = intensity;
    }
}
```

**Debug/Test:**
```java
logger.info("Torch light: RGB({}, {}, {}) intensity {}",
    torchLight.r, torchLight.g, torchLight.b, torchLight.intensity);
```
- Console: Light color values for each emissive block
- Test: Registry contains all expected light sources

**Success Criteria:**
- ✅ Block light data structure created
- ✅ All vanilla light sources defined
- ✅ Unit tests for light data

---

### Step 2.2: Colored Light Propagation
**Goal:** BFS light propagation with RGB channels

**Actions:**
- Implement 3-channel BFS propagation (R, G, B separately)
- Store light in chunk data as 3 bytes per voxel
- Propagate with attenuation (15 blocks range)
- Handle light removal/updates

**Code:**
```java
public class ColoredLightPropagator {
    private void propagateLight(int x, int y, int z, int r, int g, int b) {
        Queue<LightNode> queue = new ArrayDeque<>();
        queue.add(new LightNode(x, y, z, r, g, b));
        
        while (!queue.isEmpty()) {
            LightNode node = queue.poll();
            if (node.r == 0 && node.g == 0 && node.b == 0) continue;
            
            // Propagate to 6 neighbors with attenuation
            for (Direction dir : Direction.values()) {
                int nx = node.x + dir.dx;
                int ny = node.y + dir.dy;
                int nz = node.z + dir.dz;
                
                int nr = Math.max(0, node.r - 17);
                int ng = Math.max(0, node.g - 17);
                int nb = Math.max(0, node.b - 17);
                
                if (shouldPropagate(nx, ny, nz, nr, ng, nb)) {
                    setBlockLight(nx, ny, nz, nr, ng, nb);
                    queue.add(new LightNode(nx, ny, nz, nr, ng, nb));
                }
            }
        }
    }
}
```

**Debug/Test:**
```java
logger.info("Propagated light from ({},{},{}): {} nodes processed",
    x, y, z, nodesProcessed);
```
- Visual: Place torch, see colored light spread
- Console: "Propagated light... X nodes processed"
- Test: Light reaches 15 blocks, attenuates correctly

**Success Criteria:**
- ✅ Colored light propagates correctly
- ✅ Light attenuates over distance
- ✅ Multiple lights blend additively
- ✅ Performance < 5ms for single light update

---

### Step 2.3: Smooth Colored Lighting in Shaders
**Goal:** Per-vertex colored light interpolation

**Actions:**
- Pass RGB light data to vertices (via texture coord or vertex attribute)
- Interpolate across face in fragment shader
- Blend with directional sun lighting

**Shader changes:**
```glsl
// voxel_lit.vs
varying vec3 vBlockLightColor; // RGB 0-1

void main() {
    // ... existing code ...
    
    // Sample block light at vertex (from chunk data)
    vBlockLightColor = gl_MultiTexCoord1.xyz / 255.0;
}

// voxel_lit.fs
varying vec3 vBlockLightColor;

void main() {
    vec4 texColor = texture2D(uTexture, vTexCoord);
    vec3 albedo = vColor.rgb * texColor.rgb;
    
    // Directional sun
    float NdotL = max(dot(normalize(vNormal), uSunDir), 0.0);
    vec3 sunLight = vec3(0.7) * NdotL * uSkyBrightness;
    
    // Colored block light
    vec3 blockLight = vBlockLightColor;
    
    // Combine: sun + block lights
    vec3 lighting = vec3(0.2) + sunLight + blockLight;
    
    vec3 color = albedo * lighting;
    gl_FragColor = vec4(color, texColor.a);
}
```

**Debug/Test:**
- Render colored spheres at light source positions for debugging
- Visual: Torches emit warm orange glow, lava emits orange-red
- Console: "Rendering block light color: RGB(X, Y, Z)"
- Test: Multiple colored lights blend correctly

**Success Criteria:**
- ✅ Torches emit visible colored light
- ✅ Light colors match expected values
- ✅ Smooth interpolation across surfaces
- ✅ No color banding

---

## Phase 3: Dynamic Shadows

### Step 3.1: Single Shadow Map Pass
**Goal:** Basic shadow mapping from sun direction

**Actions:**
- Render depth from sun's perspective to shadow map
- Single 2048x2048 texture, orthographic projection
- Cover ~64 block radius around player
- Basic PCF filtering

**Code:**
```java
public class ShadowPass {
    private void renderShadowMap(float sunX, float sunY, float sunZ,
                                 float playerX, float playerY, float playerZ) {
        // Bind shadow FBO
        shadowFBO.bind();
        glViewport(0, 0, 2048, 2048);
        glClear(GL_DEPTH_BUFFER_BIT);
        
        // Setup orthographic projection from sun
        glMatrixMode(GL_PROJECTION);
        glLoadIdentity();
        glOrtho(-32, 32, -32, 32, -100, 100);
        
        glMatrixMode(GL_MODELVIEW);
        glLoadIdentity();
        gluLookAt(playerX + sunX * 50, playerY + sunY * 50, playerZ + sunZ * 50,
                  playerX, playerY, playerZ,
                  0, 1, 0);
        
        // Render chunks depth-only
        renderChunksDepthOnly();
        
        shadowFBO.unbind();
    }
}
```

**Shader changes:**
```glsl
// voxel_lit.vs
uniform mat4 uShadowMatrix;
varying vec4 vShadowCoord;

void main() {
    // ... existing code ...
    vShadowCoord = uShadowMatrix * vec4(vWorldPos, 1.0);
}

// voxel_lit.fs
uniform sampler2D uShadowMap;
varying vec4 vShadowCoord;

float calculateShadow() {
    vec3 proj = vShadowCoord.xyz / vShadowCoord.w;
    proj = proj * 0.5 + 0.5; // [-1,1] to [0,1]
    
    if (proj.x < 0.0 || proj.x > 1.0 ||
        proj.y < 0.0 || proj.y > 1.0) return 1.0;
    
    float depth = texture2D(uShadowMap, proj.xy).r;
    float bias = 0.005;
    return proj.z - bias > depth ? 0.0 : 1.0;
}
```

**Debug/Test:**
```java
logger.info("Shadow map: {} chunks rendered, frustum size: {}",
    chunksRendered, frustumSize);
```
- Visual: Hard shadows appear from blocks
- Console: "Shadow map: X chunks rendered..."
- Test: Shadow matches sun direction
- Debug texture: Display shadow map in corner of screen

**Success Criteria:**
- ✅ Shadows visible and match sun direction
- ✅ No shadow acne or peter-panning
- ✅ Shadows update with sun movement
- ✅ Shadow map renders at 60+ FPS

---

### Step 3.2: Soft Shadows with PCF
**Goal:** Softer shadow edges

**Actions:**
- Implement Percentage Closer Filtering
- Variable kernel size based on distance
- Dithered sampling pattern to reduce banding

**Shader changes:**
```glsl
const vec2 poissonDisk[16] = vec2[](
    vec2(-0.94201624, -0.39906216), vec2(0.94558609, -0.76890725),
    vec2(-0.094184101, -0.92938870), vec2(0.34495938, 0.29387760),
    // ... 12 more samples
);

float calculateSoftShadow() {
    vec3 proj = vShadowCoord.xyz / vShadowCoord.w;
    proj = proj * 0.5 + 0.5;
    
    if (proj.x < 0.0 || proj.x > 1.0 ||
        proj.y < 0.0 || proj.y > 1.0) return 1.0;
    
    float shadow = 0.0;
    float bias = 0.005;
    vec2 texelSize = vec2(1.0 / 2048.0);
    
    for (int i = 0; i < 16; i++) {
        vec2 offset = poissonDisk[i] * texelSize * 2.0;
        float depth = texture2D(uShadowMap, proj.xy + offset).r;
        shadow += proj.z - bias > depth ? 0.0 : 1.0;
    }
    
    return shadow / 16.0;
}
```

**Debug/Test:**
- Render shadow map to screen quad for inspection
- Visual: Shadow edges are softer, less aliasing
- Console: "PCF samples: 16, kernel size: X"
- Test: Performance within 10% of hard shadows

**Success Criteria:**
- ✅ Soft shadow edges visible
- ✅ No performance degradation > 10%
- ✅ Adjustable softness parameter works

---

### Step 3.3: Cascaded Shadow Maps (CSM)
**Goal:** High-quality shadows near player, lower quality far away

**Actions:**
- 3 cascades: near (16 blocks), mid (48 blocks), far (128 blocks)
- Blend between cascades smoothly
- Automatic cascade selection in fragment shader

**Code:**
```java
public class CascadedShadowRenderer {
    private final int[] cascadeSizes = {16, 48, 128};
    private final ShadowMapFBO[] cascadeFBOs = new ShadowMapFBO[3];
    
    public void renderAllCascades(float sunDir[], float camPos[]) {
        for (int i = 0; i < 3; i++) {
            renderCascade(i, sunDir, camPos, cascadeSizes[i]);
        }
    }
    
    private void renderCascade(int index, float[] sunDir,
                               float[] camPos, int size) {
        cascadeFBOs[index].bind();
        setupOrthoProjection(-size, size, -size, size);
        setupSunView(sunDir, camPos);
        renderChunksDepthOnly();
        cascadeFBOs[index].unbind();
    }
}
```

**Shader changes:**
```glsl
uniform sampler2D uShadowMap0, uShadowMap1, uShadowMap2;
uniform float uCascadeSplit0, uCascadeSplit1;

float calculateShadowCSM() {
    float dist = length(vWorldPos - uCameraPos);
    
    sampler2D shadowMap;
    vec4 shadowCoord;
    
    if (dist < uCascadeSplit0) {
        shadowMap = uShadowMap0;
        shadowCoord = vShadowCoord0;
    } else if (dist < uCascadeSplit1) {
        shadowMap = uShadowMap1;
        shadowCoord = vShadowCoord1;
    } else {
        shadowMap = uShadowMap2;
        shadowCoord = vShadowCoord2;
    }
    
    return sampleShadowPCF(shadowMap, shadowCoord);
}
```

**Debug/Test:**
```java
logger.info("CSM rendered - Near: {}ms, Mid: {}ms, Far: {}ms",
    nearTime, midTime, farTime);
```
- Visual: Sharp shadows near player, softer far away
- Console: "CSM rendered - Near: Xms..."
- Debug: Color-code cascades (red/green/blue) for debugging
- Test: No visible seams between cascades

**Success Criteria:**
- ✅ 3 cascades rendering correctly
- ✅ No visible seams/transitions
- ✅ Near shadows are sharp, far shadows softer
- ✅ Total shadow render time < 8ms

---

## Phase 4: Volumetric Lighting

### Step 4.1: Simple God Rays
**Goal:** Basic volumetric scattering when looking toward sun

**Actions:**
- Raymarch through view frustum
- Sample shadow map along ray
- Accumulate light scattering
- Screen-space post-process effect

**Shader (post-process):**
```glsl
// godrays.fs
uniform sampler2D uSceneColor;
uniform sampler2D uSceneDepth;
uniform vec2 uSunScreenPos; // Sun position in screen space
uniform float uExposure;

vec4 calculateGodRays() {
    vec2 texCoord = vTexCoord;
    vec2 deltaTexCoord = (texCoord - uSunScreenPos);
    deltaTexCoord *= 1.0 / float(NUM_SAMPLES) * uDensity;
    
    vec4 color = texture2D(uSceneColor, texCoord);
    float illuminationDecay = 1.0;
    
    for (int i = 0; i < NUM_SAMPLES; i++) {
        texCoord -= deltaTexCoord;
        vec4 sample = texture2D(uSceneColor, texCoord);
        
        sample *= illuminationDecay * uWeight;
        color += sample;
        illuminationDecay *= uDecay;
    }
    
    return color * uExposure;
}
```

**Debug/Test:**
```java
logger.info("God rays: sun screen pos ({}, {}), {} samples",
    sunScreenX, sunScreenY, numSamples);
```
- Visual: Light shafts visible when looking toward sun
- Console: "God rays: sun screen pos..."
- Test: Toggle on/off with keybind
- Performance: < 3ms overhead

**Success Criteria:**
- ✅ God rays visible looking toward sun
- ✅ Intensity adjustable
- ✅ No artifacts when sun off-screen
- ✅ Performance < 3ms

---

### Step 4.2: Volumetric Fog with Colored Lights
**Goal:** Fog scatters colored block lights

**Actions:**
- Raymarch view ray through fog volume
- Sample colored block light at each step
- Accumulate scattered light
- Depth-aware fog density

**Shader:**
```glsl
vec3 calculateVolumetricFog(vec3 worldPos, vec3 viewDir) {
    vec3 fogColor = vec3(0.0);
    float stepSize = length(worldPos - uCameraPos) / float(NUM_STEPS);
    vec3 rayStep = viewDir * stepSize;
    vec3 currentPos = uCameraPos;
    
    for (int i = 0; i < NUM_STEPS; i++) {
        currentPos += rayStep;
        
        // Sample colored block light at current position
        vec3 blockLight = sampleBlockLight(currentPos);
        
        // Accumulate fog with exponential falloff
        float density = uFogDensity * exp(-currentPos.y * 0.1);
        fogColor += blockLight * density * stepSize;
    }
    
    return fogColor;
}
```

**Debug/Test:**
- Render raymarch steps as debug spheres
- Visual: Torches create colored fog halos
- Console: "Volumetric fog: {} steps, density: {}",
- Test: Fog density adjustable

**Success Criteria:**
- ✅ Colored light visible in fog
- ✅ Torch halos in fog
- ✅ Performance acceptable (5-10ms)
- ✅ Depth-aware fog density

---

## Phase 5: Advanced Effects

### Step 5.1: Screen-Space Ambient Occlusion (SSAO)
**Goal:** Contact shadows in crevices

**Actions:**
- Render scene depth and normals to G-buffer
- Sample hemisphere around each fragment
- Count occluded samples
- Blur result

**Shader:**
```glsl
// ssao.fs
uniform sampler2D uDepthTex;
uniform sampler2D uNormalTex;
uniform sampler2D uNoiseTex;
uniform vec3 uSamples[64];

float calculateSSAO() {
    vec3 fragPos = reconstructPosition(vTexCoord, uDepthTex);
    vec3 normal = texture2D(uNormalTex, vTexCoord).xyz;
    vec3 randomVec = texture2D(uNoiseTex, vTexCoord * uNoiseScale).xyz;
    
    vec3 tangent = normalize(randomVec - normal * dot(randomVec, normal));
    vec3 bitangent = cross(normal, tangent);
    mat3 TBN = mat3(tangent, bitangent, normal);
    
    float occlusion = 0.0;
    for (int i = 0; i < 64; i++) {
        vec3 samplePos = TBN * uSamples[i];
        samplePos = fragPos + samplePos * uRadius;
        
        vec4 offset = uProjection * vec4(samplePos, 1.0);
        offset.xyz /= offset.w;
        offset.xyz = offset.xyz * 0.5 + 0.5;
        
        float sampleDepth = getLinearDepth(texture2D(uDepthTex, offset.xy).r);
        float rangeCheck = smoothstep(0.0, 1.0, uRadius / abs(fragPos.z - sampleDepth));
        occlusion += (sampleDepth >= samplePos.z + uBias ? 1.0 : 0.0) * rangeCheck;
    }
    
    return 1.0 - (occlusion / 64.0);
}
```

**Debug/Test:**
- Display SSAO buffer as grayscale
- Visual: Dark contact shadows in corners
- Console: "SSAO: {} samples, radius: {}"
- Test: Adjustable radius and intensity

**Success Criteria:**
- ✅ Contact shadows visible
- ✅ No obvious noise/artifacts
- ✅ Blur removes noise
- ✅ Performance < 5ms

---

### Step 5.2: Screen-Space Reflections (SSR)
**Goal:** Water and smooth block reflections

**Actions:**
- Raymarch in screen space along reflection vector
- Check depth buffer for intersections
- Fetch color from scene buffer
- Fresnel falloff at grazing angles

**Shader:**
```glsl
// ssr.fs
vec4 calculateSSR(vec3 worldPos, vec3 normal, vec3 viewDir) {
    vec3 reflectDir = reflect(viewDir, normal);
    
    // Screen-space raymarch
    vec3 rayStart = worldPos;
    vec3 rayDir = reflectDir;
    float stepSize = 0.1;
    
    for (int i = 0; i < MAX_STEPS; i++) {
        vec3 rayPos = rayStart + rayDir * (float(i) * stepSize);
        vec4 screenPos = uProjection * uView * vec4(rayPos, 1.0);
        screenPos.xyz /= screenPos.w;
        vec2 screenUV = screenPos.xy * 0.5 + 0.5;
        
        if (screenUV.x < 0.0 || screenUV.x > 1.0 ||
            screenUV.y < 0.0 || screenUV.y > 1.0) break;
        
        float sceneDepth = texture2D(uDepthTex, screenUV).r;
        float rayDepth = screenPos.z;
        
        if (rayDepth > sceneDepth) {
            // Hit! Sample scene color
            vec4 reflectColor = texture2D(uSceneTex, screenUV);
            float fresnel = pow(1.0 - dot(-viewDir, normal), 5.0);
            return vec4(reflectColor.rgb, fresnel);
        }
    }
    
    return vec4(0.0);
}
```

**Debug/Test:**
- Highlight reflection hits with colored overlay
- Visual: Water reflects sky and nearby blocks
- Console: "SSR: {} hits per frame avg"
- Test: Reflections visible in water

**Success Criteria:**
- ✅ Water shows reflections
- ✅ Fresnel effect at grazing angles
- ✅ No obvious artifacts
- ✅ Performance < 8ms

---

### Step 5.3: Physically-Based Rendering (PBR)
**Goal:** Metallic and roughness material properties

**Actions:**
- Add metallic and roughness maps to blocks
- Implement Cook-Torrance BRDF
- Image-based lighting (IBL) with cubemap
- Proper energy conservation

**Shader:**
```glsl
// PBR lighting
vec3 fresnelSchlick(float cosTheta, vec3 F0) {
    return F0 + (1.0 - F0) * pow(1.0 - cosTheta, 5.0);
}

float distributionGGX(vec3 N, vec3 H, float roughness) {
    float a = roughness * roughness;
    float a2 = a * a;
    float NdotH = max(dot(N, H), 0.0);
    float NdotH2 = NdotH * NdotH;
    
    float num = a2;
    float denom = (NdotH2 * (a2 - 1.0) + 1.0);
    denom = PI * denom * denom;
    
    return num / denom;
}

float geometrySchlickGGX(float NdotV, float roughness) {
    float r = (roughness + 1.0);
    float k = (r * r) / 8.0;
    
    float num = NdotV;
    float denom = NdotV * (1.0 - k) + k;
    
    return num / denom;
}

vec3 calculatePBR(vec3 albedo, float metallic, float roughness,
                  vec3 N, vec3 V, vec3 L, vec3 lightColor) {
    vec3 H = normalize(V + L);
    
    // F0 for dielectrics is 0.04, metals use albedo
    vec3 F0 = vec3(0.04);
    F0 = mix(F0, albedo, metallic);
    
    // Cook-Torrance BRDF
    float NDF = distributionGGX(N, H, roughness);
    float G = geometrySchlickGGX(max(dot(N, V), 0.0), roughness) *
              geometrySchlickGGX(max(dot(N, L), 0.0), roughness);
    vec3 F = fresnelSchlick(max(dot(H, V), 0.0), F0);
    
    vec3 kS = F;
    vec3 kD = vec3(1.0) - kS;
    kD *= 1.0 - metallic;
    
    vec3 numerator = NDF * G * F;
    float denominator = 4.0 * max(dot(N, V), 0.0) * max(dot(N, L), 0.0) + 0.001;
    vec3 specular = numerator / denominator;
    
    float NdotL = max(dot(N, L), 0.0);
    return (kD * albedo / PI + specular) * lightColor * NdotL;
}
```

**Debug/Test:**
```java
logger.info("PBR: metallic={}, roughness={} for block {}",
    metallic, roughness, blockType);
```
- Visual: Metal blocks reflect environment, rough blocks diffuse
- Console: "PBR: metallic=X, roughness=Y..."
- Test: Different material properties look distinct

**Success Criteria:**
- ✅ Metallic blocks reflect environment
- ✅ Roughness affects specular spread
- ✅ Energy conserving (not too bright)
- ✅ Performance < 3ms overhead

---

## Phase 6: Water & Transparency

### Step 6.1: Water Rendering with Refraction
**Goal:** Realistic water surface

**Actions:**
- Render water in separate pass
- Refraction using scene color texture
- Reflection using SSR or reflection map
- Fresnel blend between refraction/reflection
- Animated normal map for waves

**Shader:**
```glsl
// water.fs
uniform sampler2D uSceneColor;
uniform sampler2D uSceneDepth;
uniform sampler2D uWaterNormal;
uniform float uTime;

vec4 renderWater() {
    // Animated water normal
    vec2 uv1 = vTexCoord * 4.0 + vec2(uTime * 0.01);
    vec2 uv2 = vTexCoord * 4.0 - vec2(uTime * 0.01);
    vec3 normal1 = texture2D(uWaterNormal, uv1).xyz * 2.0 - 1.0;
    vec3 normal2 = texture2D(uWaterNormal, uv2).xyz * 2.0 - 1.0;
    vec3 waterNormal = normalize(normal1 + normal2);
    
    // Refraction
    vec2 refractOffset = waterNormal.xy * 0.1;
    vec3 refractColor = texture2D(uSceneColor, vTexCoord + refractOffset).rgb;
    
    // Reflection using SSR
    vec3 reflectColor = calculateSSR(vWorldPos, waterNormal, normalize(vViewDir)).rgb;
    
    // Fresnel
    float fresnel = pow(1.0 - dot(-normalize(vViewDir), waterNormal), 5.0);
    
    // Blend
    vec3 waterColor = mix(refractColor, reflectColor, fresnel);
    waterColor *= vec3(0.1, 0.3, 0.5); // Water tint
    
    return vec4(waterColor, 0.8);
}
```

**Debug/Test:**
- Render water normal map to screen
- Visual: Water refracts and reflects
- Console: "Water: fresnel={}, wave height={}"
- Test: Animated waves visible

**Success Criteria:**
- ✅ Water refracts objects below
- ✅ Water reflects sky/environment
- ✅ Animated waves
- ✅ Fresnel effect at grazing angles

---

## Phase 7: Optimization & Polish

### Step 7.1: Performance Profiling
**Goal:** Identify and fix bottlenecks

**Actions:**
- GPU profiling with timer queries
- CPU profiling with JMH
- Identify slow shader passes
- Optimize hot paths

**Code:**
```java
public class PerformanceProfiler {
    private Map<String, Long> timings = new HashMap<>();
    
    public void startTiming(String name) {
        glBeginQuery(GL_TIME_ELAPSED, queries.get(name));
    }
    
    public void endTiming(String name) {
        glEndQuery(GL_TIME_ELAPSED);
        long time = glGetQueryObjectui64(queries.get(name), GL_QUERY_RESULT);
        timings.put(name, time);
        logger.info("{}: {:.2f}ms", name, time / 1_000_000.0);
    }
}
```

**Debug/Test:**
- Display frame timing graph
- Console: Detailed pass timings
- Test: Identify passes > 5ms

**Success Criteria:**
- ✅ All passes profiled
- ✅ Total frame time < 16ms (60 FPS)
- ✅ Bottlenecks identified
- ✅ Optimization targets clear

---

### Step 7.2: Level of Detail (LOD)
**Goal:** Reduce distant chunk complexity

**Actions:**
- Multiple LOD levels for chunks
- Switch based on distance
- Simpler shading for distant chunks
- Lower shadow resolution for distant cascades

**Code:**
```java
public enum ChunkLOD {
    FULL(0, 32),      // 0-32 blocks
    MEDIUM(32, 96),   // 32-96 blocks
    LOW(96, 256);     // 96-256 blocks
    
    public boolean shouldRender(float distance) {
        return distance >= minDist && distance < maxDist;
    }
}
```

**Debug/Test:**
- Color-code chunks by LOD level
- Visual: Distant chunks simpler
- Console: "LOD distribution: Full={}, Medium={}, Low={}"
- Test: FPS improves with LOD

**Success Criteria:**
- ✅ 3 LOD levels implemented
- ✅ Smooth transitions between levels
- ✅ 20%+ FPS improvement
- ✅ Minimal visual quality loss

---

### Step 7.3: Quality Settings
**Goal:** Configurable quality levels

**Actions:**
- Settings for each major feature
- Presets: Low, Medium, High, Ultra
- Per-feature toggles
- Performance-adaptive mode

**Code:**
```java
public class GraphicsSettings {
    public enum Quality { LOW, MEDIUM, HIGH, ULTRA }
    
    private Quality shadowQuality = Quality.HIGH;
    private Quality lightingQuality = Quality.HIGH;
    private boolean ssao = true;
    private boolean ssr = true;
    private boolean volumetricLighting = true;
    private boolean godRays = false;
    
    public void applyPreset(Quality preset) {
        switch (preset) {
            case LOW:
                shadowQuality = Quality.LOW;
                lightingQuality = Quality.MEDIUM;
                ssao = false;
                ssr = false;
                volumetricLighting = false;
                godRays = false;
                break;
            // ... other presets
        }
    }
}
```

**Debug/Test:**
- Test each preset
- Visual: Quality differences visible
- Console: "Preset applied: {}"
- Test: Performance scales with quality

**Success Criteria:**
- ✅ All features have quality settings
- ✅ Presets work correctly
- ✅ Performance scales predictably
- ✅ Settings persist across restarts

---

## Testing & Validation

Each phase includes:

1. **Unit Tests:** Test individual components
2. **Visual Tests:** Screenshot comparisons
3. **Performance Tests:** FPS benchmarks
4. **Integration Tests:** Full feature interaction

**Automated Testing:**
```java
@Test
public void testColoredLightPropagation() {
    Level level = new Level();
    level.setBlock(0, 0, 0, Blocks.TORCH);
    
    ColoredLightPropagator propagator = new ColoredLightPropagator(level);
    propagator.propagateLight(0, 0, 0);
    
    // Check light at distance 1
    int[] rgb = level.getBlockLight(1, 0, 0);
    assertTrue(rgb[0] > 200); // Torch is warm, R should be high
    assertTrue(rgb[2] < 150); // Blue should be lower
    
    // Check attenuation
    int[] farRgb = level.getBlockLight(10, 0, 0);
    assertTrue(farRgb[0] < rgb[0]); // Should be dimmer
}
```

---

## Implementation Order

**Recommended sequence:**
1. Phase 1.1 → 1.2 (Foundation)
2. Phase 2.1 → 2.2 → 2.3 (Colored lights)
3. Phase 3.1 → 3.2 → 3.3 (Shadows)
4. Phase 4.1 (God rays - optional)
5. Phase 5.1 (SSAO)
6. Phase 4.2 (Volumetric fog - after colored lights working)
7. Phase 5.2 (SSR - for water)
8. Phase 6.1 (Water)
9. Phase 5.3 (PBR - optional, advanced)
10. Phase 7 (Optimization & polish)

**Total Estimated Time:** 40-60 hours of development

---

## Final Notes

- Each step is self-contained and testable
- Debug logging helps track progress
- Visual feedback crucial for shader work
- Performance monitoring at each step
- Can skip optional phases (PBR, volumetric fog) if needed

**Next Step:** Confirm this plan, then start with Phase 1.1
