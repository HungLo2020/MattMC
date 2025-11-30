# Compute Shader Optimization Opportunities

This document identifies CPU-intensive operations in MattMC that could be offloaded to GPU compute shaders for significant performance improvements.

## Table of Contents

1. [Terrain Noise Generation](#1-terrain-noise-generation) - **High Priority**
2. [Chunk Mesh Generation](#2-chunk-mesh-generation) - **High Priority**
3. [Light Propagation (BFS)](#3-light-propagation-bfs) - **High Priority**
4. [Skylight Initialization](#4-skylight-initialization) - **High Priority**
5. [Frustum Culling](#5-frustum-culling) - **Medium Priority**
6. [Particle System Simulation](#6-particle-system-simulation) - **Medium Priority**
7. [Smooth Lighting / Vertex Light Sampling](#7-smooth-lighting--vertex-light-sampling) - **Medium Priority**
8. [Ambient Occlusion Calculation](#8-ambient-occlusion-calculation) - **Medium Priority**
9. [Collision Detection](#9-collision-detection) - **Medium Priority**
10. [Heightmap Calculation](#10-heightmap-calculation) - **Low Priority**
11. [Face Culling](#11-face-culling) - **Low Priority**
12. [Cross-Chunk Light Propagation](#12-cross-chunk-light-propagation) - **Low Priority**

---

## 1. Terrain Noise Generation

### Priority: **High**

### Current Implementation Location
- **Files:**
  - `src/main/java/mattmc/world/level/levelgen/WorldGenerator.java`
  - `src/main/java/mattmc/world/level/levelgen/PerlinNoise.java`
  - `src/main/java/mattmc/world/level/levelgen/OctaveNoise.java`
  - `src/main/java/mattmc/world/level/levelgen/NoiseParameters.java`

### Description
The world generator uses multi-octave Perlin noise to generate terrain heights. For each block column in a 16×16 chunk, the generator:
1. Samples 4 noise parameters: continentalness, erosion, peaks/valleys, weirdness
2. Each noise parameter uses 3-4 octaves of Perlin noise
3. Combines noise values to calculate terrain height
4. Fills blocks from bedrock to surface

**Code path:** `WorldGenerator.generateChunkTerrain()` → `getTerrainHeight()` → `NoiseParameters.sample*()` → `OctaveNoise.sample()` → `PerlinNoise.noise()`

### Why It's a Good Candidate
- **Highly parallel:** Each column (256 columns per chunk) is completely independent
- **Compute-intensive:** Multiple octaves of noise evaluation per column
- **GPU-friendly math:** Perlin noise is pure floating-point operations (fade, lerp, gradient)
- **Proven pattern:** Minecraft Bedrock Edition uses compute shaders for terrain generation
- **No synchronization needed:** Pure function with no shared state

### Expected Performance Impact
- **Current:** ~5-15ms per chunk on CPU (varies by hardware)
- **Expected with compute:** <1ms per chunk, potentially generating multiple chunks per frame
- **Speedup:** 10-50x for terrain generation
- **Benefit:** Faster world loading, larger view distances, smoother chunk streaming

### Implementation Complexity: **Medium**

### Implementation Notes
```glsl
// Pseudo-code for compute shader approach
layout(local_size_x = 16, local_size_y = 16) in;

uniform int chunkX;
uniform int chunkZ;
uniform int seed;

layout(std430, binding = 0) buffer HeightmapBuffer {
    int heights[256]; // 16x16 grid
};

void main() {
    int localX = int(gl_LocalInvocationID.x);
    int localZ = int(gl_LocalInvocationID.y);
    int worldX = chunkX * 16 + localX;
    int worldZ = chunkZ * 16 + localZ;
    
    // Sample noise parameters
    float continentalness = sampleOctaveNoise(worldX, worldZ, CONTINENTALNESS_PARAMS);
    float erosion = sampleOctaveNoise(worldX, worldZ, EROSION_PARAMS);
    float pv = sampleOctaveNoise(worldX, worldZ, PV_PARAMS);
    float weirdness = sampleOctaveNoise(worldX, worldZ, WEIRDNESS_PARAMS);
    
    // Combine to get height
    heights[localZ * 16 + localX] = calculateHeight(continentalness, erosion, pv, weirdness);
}
```

### Dependencies & Considerations
- Need to upload noise permutation tables to GPU (512 ints per PerlinNoise instance)
- Results need to be read back to CPU for block placement (unless using indirect draws)
- Consider generating heightmap only, then filling blocks on CPU (hybrid approach)
- Integration with `AsyncChunkLoader` for seamless background generation

---

## 2. Chunk Mesh Generation

### Priority: **High**

### Current Implementation Location
- **Files:**
  - `src/main/java/mattmc/client/renderer/chunk/MeshBuilder.java`
  - `src/main/java/mattmc/client/renderer/block/BlockFaceCollector.java`
  - `src/main/java/mattmc/client/renderer/chunk/ChunkMeshBuffer.java`
  - `src/main/java/mattmc/world/level/chunk/AsyncChunkLoader.java` (calls mesh building)

### Description
When a chunk changes, the mesh builder:
1. Iterates through all blocks in the chunk (16×16×384 = 98,304 blocks)
2. For each non-air block, checks 6 neighbors for face culling
3. Generates 4 vertices per visible face with positions, UVs, normals, and light data
4. Samples 3×3×3 grid for smooth lighting per vertex
5. Produces vertex and index arrays for GPU upload

**Code path:** `MeshBuilder.build()` → iterates faces → `addTopFace()` etc. → `sampleVertexLight()` → vertex generation

### Why It's a Good Candidate
- **Highly parallel:** Each block can be processed independently
- **Memory-intensive:** Generates large vertex buffers (suitable for GPU streaming)
- **Minecraft precedent:** Sodium/Iris mods use compute shaders for chunk meshing
- **Currently bottleneck:** Chunk rebuild is often the main cause of lag spikes

### Expected Performance Impact
- **Current:** ~10-30ms per chunk rebuild on CPU
- **Expected with compute:** 1-3ms per chunk
- **Speedup:** 10x or more
- **Benefit:** Smoother gameplay during block updates, faster initial world loading

### Implementation Complexity: **High**

### Implementation Notes
```glsl
// Two-pass approach:
// Pass 1: Count visible faces per block
// Pass 2: Generate vertices with prefix-sum for output offsets

// Pass 1 shader
layout(local_size_x = 4, local_size_y = 4, local_size_z = 4) in;

layout(std430, binding = 0) buffer BlockData {
    uint blocks[]; // Chunk block IDs (packed)
};

layout(std430, binding = 1) buffer FaceCount {
    uint faceCounts[]; // Per-block face count
};

void main() {
    ivec3 pos = ivec3(gl_GlobalInvocationID);
    uint block = getBlock(pos);
    
    if (block == AIR_ID) {
        faceCounts[posToIndex(pos)] = 0;
        return;
    }
    
    uint count = 0;
    if (shouldRenderFace(pos, UP)) count++;
    if (shouldRenderFace(pos, DOWN)) count++;
    // ... etc for all 6 faces
    
    faceCounts[posToIndex(pos)] = count;
}
```

### Dependencies & Considerations
- Need efficient way to handle variable-length output (prefix sum / atomic counters)
- Texture atlas UV lookups need to be available on GPU
- Block model data needs to be uploaded (for non-cube blocks)
- Light data (3×3×3 sampling) adds complexity but is parallelizable
- May need CPU fallback for complex model elements

---

## 3. Light Propagation (BFS)

### Priority: **High**

### Current Implementation Location
- **Files:**
  - `src/main/java/mattmc/world/level/lighting/LightPropagator.java`
  - `src/main/java/mattmc/world/level/lighting/WorldLightManager.java`
  - `src/main/java/mattmc/world/level/lighting/CrossChunkLightPropagator.java`

### Description
Block light uses BFS flood-fill propagation:
1. When a light source is placed, enqueue its position with full intensity (15)
2. For each queued position, propagate to 6 neighbors with intensity - 1
3. Update light values and continue until intensity reaches 0
4. Handle opacity blocking (solid blocks stop propagation)
5. Supports RGB colored light (R, G, B, I channels)

**Code path:** `LightPropagator.addBlockLightRGB()` → BFS loop → `propagateRGBIToNeighbor()` → updates chunk light storage

### Why It's a Good Candidate
- **Wave-parallel pattern:** Light propagation follows distance waves that can be parallelized
- **Regular access pattern:** 6-neighbor stencil pattern is GPU-friendly
- **Bounded iterations:** Maximum 15 steps from any source
- **Hot path:** Light updates happen frequently during gameplay

### Expected Performance Impact
- **Current:** Can cause noticeable hitches when placing/breaking light sources near many blocks
- **Expected with compute:** Near-instant light updates even for large affected areas
- **Speedup:** 5-20x for complex light scenarios
- **Benefit:** No frame drops when updating lighting

### Implementation Complexity: **High**

### Implementation Notes
```glsl
// Jump flooding / wave-front approach
// Each dispatch propagates light by one step

layout(local_size_x = 8, local_size_y = 8, local_size_z = 8) in;

layout(std430, binding = 0) buffer LightCurrent {
    uvec4 lightRGBI[]; // Current light values (packed RGBI per voxel)
};

layout(std430, binding = 1) buffer LightNext {
    uvec4 lightRGBINext[]; // Next iteration values
};

void main() {
    ivec3 pos = ivec3(gl_GlobalInvocationID);
    uvec4 current = lightRGBI[posToIndex(pos)];
    uvec4 maxNeighbor = uvec4(0);
    
    // Sample 6 neighbors, find max - 1
    for (int dir = 0; dir < 6; dir++) {
        ivec3 neighborPos = pos + DIRECTIONS[dir];
        if (isOpaque(neighborPos)) continue;
        uvec4 neighborLight = lightRGBI[posToIndex(neighborPos)];
        maxNeighbor = max(maxNeighbor, neighborLight - uvec4(1, 1, 1, 1));
    }
    
    lightRGBINext[posToIndex(pos)] = max(current, maxNeighbor);
}
```

### Dependencies & Considerations
- Need to handle cross-chunk boundaries (extend buffer or multi-dispatch)
- Opacity data must be accessible on GPU
- May need multiple iterations (up to 15) for full propagation
- Consider hybrid: GPU for bulk updates, CPU for single-block changes
- Light removal is more complex (need to clear and re-propagate)

---

## 4. Skylight Initialization

### Priority: **High**

### Current Implementation Location
- **Files:**
  - `src/main/java/mattmc/world/level/lighting/SkylightEngine.java`
  - `src/main/java/mattmc/world/level/lighting/SkylightInitializer.java` (if exists)
  - `src/main/java/mattmc/world/level/chunk/LevelChunk.java` (stores light)

### Description
Skylight initialization:
1. For each column, find the highest opaque block (heightmap)
2. Set skylight = 15 for all blocks above heightmap
3. Set skylight = 0 for blocks at/below heightmap
4. BFS propagate skylight into caves and overhangs with attenuation

**Code path:** `SkylightEngine.initializeChunkSkylight()` → `initializeColumnSkylight()` → `propagateSkylightBelowHeightmap()`

### Why It's a Good Candidate
- **Column-parallel:** 256 columns can be processed simultaneously
- **Simple logic:** Height scan is trivially parallel
- **Heavy BFS component:** Cavity propagation benefits from same techniques as block light
- **Chunk generation bottleneck:** Skylight init is called for every new chunk

### Expected Performance Impact
- **Current:** Significant portion of chunk generation time
- **Expected with compute:** <1ms for complete skylight initialization
- **Speedup:** 10-30x
- **Benefit:** Faster chunk loading, especially in complex terrain

### Implementation Complexity: **Medium**

### Implementation Notes
```glsl
// Pass 1: Compute heightmap and set initial skylight
layout(local_size_x = 16, local_size_y = 1, local_size_z = 16) in;

void main() {
    int x = int(gl_GlobalInvocationID.x);
    int z = int(gl_GlobalInvocationID.z);
    
    // Scan from top to find heightmap
    int heightmapY = MIN_Y;
    for (int y = MAX_Y; y >= MIN_Y; y--) {
        if (getOpacity(x, y, z) >= 15) {
            heightmapY = y;
            break;
        }
    }
    
    // Set initial skylight values
    for (int y = MIN_Y; y <= MAX_Y; y++) {
        if (y > heightmapY) {
            setSkyLight(x, y, z, 15);
        } else {
            setSkyLight(x, y, z, 0);
        }
    }
    
    heightmap[z * 16 + x] = heightmapY;
}
```

### Dependencies & Considerations
- Block opacity data must be on GPU
- Propagation phase similar to block light (can share implementation)
- Cross-chunk propagation handled separately after initial chunks load

---

## 5. Frustum Culling

### Priority: **Medium**

### Current Implementation Location
- **Files:**
  - `src/main/java/mattmc/client/renderer/Frustum.java`
  - Used in chunk rendering loop (ChunkRenderLogic or similar)

### Description
Frustum culling tests each chunk's bounding box against 6 frustum planes:
1. Extract frustum planes from view-projection matrix
2. For each chunk, test AABB against all 6 planes
3. If completely outside any plane, skip rendering

**Code path:** `Frustum.update()` → `isChunkVisible()` / `isBoxVisible()` per chunk

### Why It's a Good Candidate
- **Embarrassingly parallel:** Each chunk test is independent
- **Simple math:** 6 plane-point tests per chunk
- **Scales with view distance:** More chunks = more benefit from parallelism
- **GPU already has data:** Frustum planes come from matrices already on GPU

### Expected Performance Impact
- **Current:** Negligible for small view distances, but grows with chunk count
- **Expected with compute:** Constant ~0.1ms regardless of chunk count
- **Speedup:** 5-10x for large view distances (32+ chunks)
- **Benefit:** Enables larger view distances without CPU bottleneck

### Implementation Complexity: **Low**

### Implementation Notes
```glsl
layout(local_size_x = 64) in;

uniform mat4 viewProjection;

struct ChunkBounds {
    vec3 minPos;
    vec3 maxPos;
};

layout(std430, binding = 0) buffer Chunks {
    ChunkBounds chunks[];
};

layout(std430, binding = 1) buffer Visibility {
    uint visible[]; // Bit-packed visibility flags
};

shared vec4 frustumPlanes[6];

void main() {
    // Extract frustum planes (first invocation only)
    if (gl_LocalInvocationID.x == 0) {
        extractFrustumPlanes(viewProjection, frustumPlanes);
    }
    barrier();
    
    uint chunkIdx = gl_GlobalInvocationID.x;
    if (chunkIdx >= chunkCount) return;
    
    ChunkBounds bounds = chunks[chunkIdx];
    bool isVisible = testAABBFrustum(bounds.minPos, bounds.maxPos, frustumPlanes);
    
    // Write visibility
    atomicOr(visible[chunkIdx / 32], uint(isVisible) << (chunkIdx % 32));
}
```

### Dependencies & Considerations
- Chunk positions need to be uploaded to GPU
- Results can drive indirect draw commands
- Can be combined with LOD selection or occlusion culling

---

## 6. Particle System Simulation

### Priority: **Medium**

### Current Implementation Location
- **Files:**
  - `src/main/java/mattmc/client/particle/ParticleEngine.java`
  - `src/main/java/mattmc/client/particle/Particle.java`
  - Various particle types: `SmokeParticle.java`, `FlameParticle.java`, `FallingLeavesParticle.java`, etc.

### Description
Particle simulation:
1. Each frame, tick all active particles (up to 16,384 per layer)
2. Update position based on velocity and gravity
3. Update lifetime, check for death
4. Render particles grouped by render type
5. Handle collision with blocks (some particle types)

**Code path:** `ParticleEngine.tick()` → per-particle `Particle.tick()` → physics update

### Why It's a Good Candidate
- **Massive parallelism:** Thousands of independent particles
- **Classic GPU workload:** Particle systems are textbook compute shader use case
- **Simple physics:** Position += velocity, velocity += gravity
- **Potential for GPU-resident particles:** Avoid CPU↔GPU transfer

### Expected Performance Impact
- **Current:** Can cause frame drops with many particles (10K+)
- **Expected with compute:** Handle 100K+ particles easily
- **Speedup:** 20-100x for particle-heavy scenarios
- **Benefit:** Richer visual effects without performance cost

### Implementation Complexity: **Medium**

### Implementation Notes
```glsl
layout(local_size_x = 256) in;

struct Particle {
    vec3 position;
    vec3 velocity;
    float lifetime;
    float maxLifetime;
    uint type;
    // ... other properties
};

layout(std430, binding = 0) buffer Particles {
    Particle particles[];
};

layout(std430, binding = 1) buffer ParticleCount {
    uint aliveCount;
    uint deadIndices[];
};

void main() {
    uint idx = gl_GlobalInvocationID.x;
    if (idx >= aliveCount) return;
    
    Particle p = particles[idx];
    
    // Update physics
    p.velocity.y -= GRAVITY * DELTA_TIME;
    p.position += p.velocity * DELTA_TIME;
    p.lifetime -= DELTA_TIME;
    
    // Check death
    if (p.lifetime <= 0.0) {
        // Mark for removal (atomic append to dead list)
        uint deadIdx = atomicAdd(deadCount, 1);
        deadIndices[deadIdx] = idx;
    } else {
        particles[idx] = p;
    }
}
```

### Dependencies & Considerations
- Need to handle particle spawn/death (compaction or free-list)
- Block collision requires block data on GPU
- Rendering can use instancing or point sprites
- Consider separate update and render dispatches

---

## 7. Smooth Lighting / Vertex Light Sampling

### Priority: **Medium**

### Current Implementation Location
- **Files:**
  - `src/main/java/mattmc/client/renderer/chunk/VertexLightSampler.java`
  - `src/main/java/mattmc/client/renderer/chunk/MeshBuilder.java` (calls sampler)

### Description
For each vertex in the mesh, smooth lighting:
1. Samples a 3×3×3 grid (27 positions) around the block
2. For each position: get opacity, skylight, blocklight RGB, shade brightness
3. Precomputes per-corner light values for 8 corners × 3 axes
4. Uses complex trilinear interpolation (`calcLightmap()`) for final values
5. Computes ambient occlusion brightness

**Code path:** `sampleVertexLight()` → `computeLightingAt()` → 27-position sampling → `combine()` → `calcLightmap()`

### Why It's a Good Candidate
- **Per-vertex parallel:** Each vertex calculation is independent
- **Compute-intensive:** Complex interpolation math per vertex
- **Memory-bound aspects:** 27 memory accesses per block, benefits from GPU caching
- **Part of mesh generation:** Can be integrated with chunk meshing compute

### Expected Performance Impact
- **Current:** Significant portion of mesh build time (27 samples × 4 vertices × faces)
- **Expected with compute:** Integrated into mesh generation, near-zero marginal cost
- **Speedup:** Indirect (enables faster mesh generation overall)
- **Benefit:** Enables more complex lighting without CPU penalty

### Implementation Complexity: **Medium-High**

### Implementation Notes
```glsl
// Can be integrated into chunk mesh generation shader
// or run as separate post-process on vertex buffer

vec4 sampleVertexLight(ivec3 blockPos, vec3 vertexOffset, vec3 normal) {
    // Sample 3x3x3 grid
    float t[3][3][3];  // transparency
    float s[3][3][3];  // skylight
    float b[3][3][3];  // blocklight
    float ao[3][3][3]; // ambient occlusion
    
    for (int dx = 0; dx <= 2; dx++) {
        for (int dy = 0; dy <= 2; dy++) {
            for (int dz = 0; dz <= 2; dz++) {
                ivec3 samplePos = blockPos + ivec3(dx-1, dy-1, dz-1);
                t[dx][dy][dz] = getOpacity(samplePos) < 15 ? 1.0 : 0.0;
                s[dx][dy][dz] = getSkyLight(samplePos);
                b[dx][dy][dz] = getBlockLight(samplePos);
                ao[dx][dy][dz] = getShadeBrightness(samplePos);
            }
        }
    }
    
    // Precompute corners and interpolate...
    return vec4(skyLight, blockLightR, blockLightG, aoValue);
}
```

### Dependencies & Considerations
- Light data arrays must be on GPU
- Block opacity data required
- Complex interpolation algorithm needs careful porting
- Consider texture-based lookup for light data

---

## 8. Ambient Occlusion Calculation

### Priority: **Medium**

### Current Implementation Location
- **Files:**
  - `src/main/java/mattmc/client/renderer/chunk/AmbientOcclusion.java`
  - Integrated with `VertexLightSampler.java`

### Description
AO calculation per vertex:
1. For each vertex corner, identify 2 edge neighbors and 1 diagonal neighbor
2. Sample shade brightness of each neighbor
3. Check corner visibility (can the diagonal be "seen" past the edges?)
4. Average 4 samples for final AO value

**Code path:** `AmbientOcclusion.calculateVertexAO()` → neighbor sampling → `getShadeBrightness()` → averaging

### Why It's a Good Candidate
- **Same parallel structure as smooth lighting:** Per-vertex, independent
- **Simple lookups:** Just solid/non-solid checks for neighbors
- **Already GPU-adjacent:** Could be computed in fragment shader or mesh generation

### Expected Performance Impact
- **Current:** Part of mesh build overhead
- **Expected with compute:** Negligible cost when integrated with mesh generation
- **Benefit:** Cleaner separation of concerns, potential quality improvements

### Implementation Complexity: **Low-Medium**

### Implementation Notes
```glsl
// Can be part of vertex shader or mesh generation
float calculateVertexAO(ivec3 blockPos, int faceIndex, int vertexIndex) {
    ivec3 faceNormal = FACE_NORMALS[faceIndex];
    ivec3 samplePos = blockPos + faceNormal;
    
    ivec3 edge0 = EDGE_DIRS[faceIndex][vertexIndex][0];
    ivec3 edge1 = EDGE_DIRS[faceIndex][vertexIndex][1];
    
    float edge0AO = getShadeBrightness(samplePos + edge0);
    float edge1AO = getShadeBrightness(samplePos + edge1);
    float faceAO = getShadeBrightness(samplePos);
    
    // Corner visibility check
    bool canSeeCorner = !isSolid(samplePos + edge0 + faceNormal) || 
                        !isSolid(samplePos + edge1 + faceNormal);
    
    float cornerAO = canSeeCorner ? 
        getShadeBrightness(samplePos + edge0 + edge1) : edge0AO;
    
    return (edge0AO + edge1AO + cornerAO + faceAO) * 0.25;
}
```

### Dependencies & Considerations
- Block solidity data required on GPU
- Naturally integrates with mesh generation
- Lookup tables for edge directions per face

---

## 9. Collision Detection

### Priority: **Medium**

### Current Implementation Location
- **Files:**
  - `src/main/java/mattmc/world/entity/player/CollisionDetector.java`
  - `src/main/java/mattmc/world/entity/player/PlayerPhysics.java`
  - `src/main/java/mattmc/world/phys/AABB.java`
  - `src/main/java/mattmc/world/phys/shapes/VoxelShape.java`

### Description
Collision detection:
1. Calculate AABB from player position and dimensions
2. Find all blocks that could intersect player AABB
3. For each block, get its collision shape (VoxelShape → list of AABBs)
4. Test player AABB against each block AABB
5. Resolve collisions axis-by-axis

**Code path:** `CollisionDetector.checkCollision()` → block iteration → `VoxelShape.toAabbs()` → `AABB.intersects()`

### Why It's a Good Candidate
- **Batch parallelism:** When many entities need collision (future multiplayer)
- **Spatial queries:** GPU acceleration for broad-phase culling
- **Predictable workload:** Fixed number of blocks to check based on AABB size

### Expected Performance Impact
- **Current:** Fast for single player, but scales poorly with entity count
- **Expected with compute:** Handle hundreds of entities efficiently
- **Benefit:** Multiplayer and mob AI optimization

### Implementation Complexity: **Medium**

### Implementation Notes
```glsl
// Broad-phase: find all blocks potentially colliding with any entity
// Narrow-phase: precise AABB tests

layout(local_size_x = 64) in;

struct Entity {
    vec3 position;
    vec3 size;
    vec3 velocity;
};

layout(std430, binding = 0) buffer Entities {
    Entity entities[];
};

layout(std430, binding = 1) buffer CollisionResults {
    vec3 adjustedPositions[];
};

void main() {
    uint entityIdx = gl_GlobalInvocationID.x;
    Entity e = entities[entityIdx];
    
    vec3 minBlock = floor(e.position - e.size * 0.5);
    vec3 maxBlock = floor(e.position + e.size * 0.5);
    
    vec3 resolved = e.position + e.velocity;
    
    // Check each block in range
    for (int x = int(minBlock.x); x <= int(maxBlock.x); x++) {
        for (int y = int(minBlock.y); y <= int(maxBlock.y); y++) {
            for (int z = int(minBlock.z); z <= int(maxBlock.z); z++) {
                if (blockHasCollision(ivec3(x, y, z))) {
                    // Resolve collision
                    resolved = resolveAABBCollision(resolved, e.size, ivec3(x, y, z));
                }
            }
        }
    }
    
    adjustedPositions[entityIdx] = resolved;
}
```

### Dependencies & Considerations
- Block collision shapes must be accessible on GPU
- Complex shapes (stairs, slabs) need proper representation
- CPU may still handle response logic (jumping, etc.)
- Consider for future mob pathfinding optimization

---

## 10. Heightmap Calculation

### Priority: **Low**

### Current Implementation Location
- **Files:**
  - `src/main/java/mattmc/world/level/chunk/ColumnHeightmap.java`
  - Updated in `LevelChunk` when blocks change
  - Used by `SkylightEngine` for initial skylight

### Description
Heightmap tracks the topmost opaque block per column:
1. Scan from top of chunk downward
2. Find first block with opacity >= 15
3. Store world Y coordinate

**Code path:** `SkylightEngine.findTopmostOpaqueBlock()`, `ColumnHeightmap.setHeight()`

### Why It's a Good Candidate
- **Trivially parallel:** 256 independent column scans
- **Simple operation:** Linear scan with early termination
- **Part of larger operations:** Could be integrated with skylight init

### Expected Performance Impact
- **Current:** Very fast on CPU (simple loop)
- **Expected with compute:** Even faster, but marginal absolute improvement
- **Benefit:** Integration with other chunk operations on GPU

### Implementation Complexity: **Low**

### Implementation Notes
```glsl
// Often combined with skylight initialization
layout(local_size_x = 16, local_size_y = 16) in;

void main() {
    int x = int(gl_LocalInvocationID.x);
    int z = int(gl_LocalInvocationID.y);
    
    int height = MIN_Y;
    for (int y = MAX_Y; y >= MIN_Y; y--) {
        if (getOpacity(x, y, z) >= 15) {
            height = y;
            break;
        }
    }
    
    heightmap[z * 16 + x] = height;
}
```

### Dependencies & Considerations
- Naturally part of skylight initialization
- Minimal standalone benefit

---

## 11. Face Culling

### Priority: **Low**

### Current Implementation Location
- **Files:**
  - `src/main/java/mattmc/client/renderer/block/BlockFaceCollector.java`
  - Called during mesh building

### Description
Face culling determines which block faces are visible:
1. For each block, check 6 neighbors
2. If neighbor is air or non-occluding, the face is visible
3. Only visible faces are added to mesh

**Code path:** `BlockFaceCollector.collectBlockFaces()` → `shouldRenderFace()` → neighbor checks

### Why It's a Good Candidate
- **Part of mesh generation:** Naturally parallelizes with mesh building
- **Simple lookups:** Just air/solid checks
- **Currently optimized:** Uses unchecked access for interior blocks

### Expected Performance Impact
- **Current:** Already fast, uses optimized access paths
- **Expected with compute:** Integrated into mesh generation
- **Benefit:** Part of overall mesh generation speedup

### Implementation Complexity: **Low** (when part of mesh generation)

### Dependencies & Considerations
- Should be integrated with chunk mesh compute shader, not standalone
- Block occlusion properties need to be on GPU

---

## 12. Cross-Chunk Light Propagation

### Priority: **Low**

### Current Implementation Location
- **Files:**
  - `src/main/java/mattmc/world/level/lighting/CrossChunkLightPropagator.java`
  - `src/main/java/mattmc/world/level/chunk/LevelChunk.java` (deferred updates)

### Description
When light reaches chunk boundaries:
1. Calculate neighbor chunk coordinates
2. If neighbor loaded, propagate directly
3. If neighbor not loaded, defer update for later
4. Process deferred updates when chunks load

**Code path:** `CrossChunkLightPropagator.propagateBlockLightCross()` → neighbor lookup → deferred queue

### Why It's a Good Candidate
- **Extension of light propagation:** Same algorithm, larger scope
- **Can use extended buffers:** GPU can process multi-chunk regions

### Expected Performance Impact
- **Current:** Handled incrementally as chunks load
- **Expected with compute:** Faster bulk updates when multiple chunks are ready
- **Benefit:** Smoother light updates during chunk streaming

### Implementation Complexity: **Medium-High**

### Dependencies & Considerations
- Requires coordination with chunk loading system
- GPU buffers need to span multiple chunks
- May be better handled by extending single-chunk light compute
- CPU handling may be adequate given deferred nature

---

## Summary Table

| Opportunity | Priority | Complexity | Expected Speedup | Integration Effort |
|------------|----------|------------|------------------|-------------------|
| Terrain Noise Generation | High | Medium | 10-50x | Medium |
| Chunk Mesh Generation | High | High | 10x+ | High |
| Light Propagation (BFS) | High | High | 5-20x | High |
| Skylight Initialization | High | Medium | 10-30x | Medium |
| Frustum Culling | Medium | Low | 5-10x | Low |
| Particle System | Medium | Medium | 20-100x | Medium |
| Smooth Lighting | Medium | Medium-High | Integrated | High |
| Ambient Occlusion | Medium | Low-Medium | Integrated | Medium |
| Collision Detection | Medium | Medium | Scalability | Medium |
| Heightmap Calculation | Low | Low | Marginal | Low |
| Face Culling | Low | Low | Integrated | Low |
| Cross-Chunk Light | Low | Medium-High | Situational | High |

---

## Recommended Implementation Order

### Phase 1: Foundation (High Impact, Moderate Effort)
1. **Terrain Noise Generation** - Self-contained, proven pattern, immediate benefit
2. **Frustum Culling** - Simple to implement, good learning exercise for compute architecture

### Phase 2: Core Rendering (High Impact, High Effort)  
3. **Light Propagation** - Critical for gameplay feel, complex but well-understood algorithms
4. **Skylight Initialization** - Pairs well with light propagation

### Phase 3: Mesh Generation (Highest Impact, Highest Effort)
5. **Chunk Mesh Generation** - Combines face culling, smooth lighting, and AO
6. **Integrate Smooth Lighting & AO** - Part of mesh generation

### Phase 4: Enhancements (Moderate Impact, Moderate Effort)
7. **Particle System** - Independent system, good for visual improvements
8. **Collision Detection** - Important for multiplayer/mob scaling

---

## Architecture Considerations

### Backend Abstraction
All compute shader implementations should follow MattMC's backend abstraction paradigm:
- Define compute operations in the `RenderBackend` interface
- OpenGL implementation in `backend/opengl/compute/`
- Future Vulkan implementation in `backend/vulkan/compute/`
- Game logic should not know which compute API is used

### Data Transfer
- Minimize CPU↔GPU data transfer
- Use persistent mapped buffers where possible
- Consider GPU-resident data structures for frequently-accessed data
- Profile carefully: compute overhead can exceed transfer savings

### Fallback Strategy
- Maintain CPU implementations for all compute operations
- Auto-detect compute shader support at runtime
- Allow user toggle in settings
- Test on variety of hardware

### Integration with Async Loading
- Compute shaders can integrate with `AsyncChunkLoader` pipeline
- Consider compute queue vs graphics queue scheduling
- Avoid stalling the graphics pipeline waiting for compute

---

## References

- [Minecraft Bedrock Edition Render Dragon](https://minecraft.wiki/w/Bedrock_Edition_render_engine) - Uses compute for terrain generation
- [Sodium Mod](https://github.com/CaffeineMC/sodium-fabric) - Highly optimized chunk rendering
- [OpenGL Compute Shaders](https://www.khronos.org/opengl/wiki/Compute_Shader) - API documentation
- [Real-Time Rendering, 4th Edition](http://www.realtimerendering.com/) - GPU compute fundamentals
- [GPU Pro/GPU Zen series](https://www.routledge.com/GPU-Pro-360-Guide-to-Geometry-Manipulation/Engel/p/book/9780815385547) - Advanced GPU techniques
