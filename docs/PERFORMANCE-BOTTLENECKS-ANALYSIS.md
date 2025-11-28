# Performance Bottlenecks Analysis for MattMC

This document identifies 10 performance bottlenecks and unoptimized sections of code found in the MattMC codebase. Each issue includes a description, why it's problematic, and recommended solutions. All fixes maintain identical player-facing behavior.

---

## Issue #1: Repeated Block Index Calculation in LightStorage

**Location:** `src/main/java/mattmc/world/level/chunk/LightStorage.java`

**Problem:**
In the `LightStorage` class, the methods `getBlockLightR()`, `getBlockLightG()`, `getBlockLightB()`, and `getBlockLightI()` each independently call `getBlockIndex(x, y, z)` which performs bounds checking and calculates the block index. When all four RGBI values are needed (which happens during vertex light sampling), this results in 4 redundant calculations of the same index.

```java
// Current code - Each method calls getBlockIndex() separately:
public int getBlockLightR(int x, int y, int z) {
    int blockIndex = getBlockIndex(x, y, z);  // Bounds check + calculation
    int byteIndex = blockIndex * 2;
    int packed = ((blockLight[byteIndex] & 0xFF) << 8) | (blockLight[byteIndex + 1] & 0xFF);
    return (packed >> 12) & 0x0F;
}
// getBlockLightG, getBlockLightB, getBlockLightI all repeat the same pattern
```

**Impact:** 
- **CPU:** 4x redundant bounds checking and index calculation per light sample
- During mesh building, this occurs millions of times per chunk (~16K blocks × 6 faces × 4 vertices × 4 samples = ~1.5M calls)

**Solution:**
Add a `getBlockLightRGBI(int x, int y, int z)` method that unpacks all four values in a single call with one bounds check and one index calculation.

```java
public int[] getBlockLightRGBI(int x, int y, int z) {
    int blockIndex = getBlockIndex(x, y, z);  // Single bounds check
    int byteIndex = blockIndex * 2;
    int packed = ((blockLight[byteIndex] & 0xFF) << 8) | (blockLight[byteIndex + 1] & 0xFF);
    return new int[] {
        (packed >> 12) & 0x0F,  // R
        (packed >> 8) & 0x0F,   // G
        (packed >> 4) & 0x0F,   // B
        packed & 0x0F           // I
    };
}
```

**Expected Improvement:** 10-20% reduction in light sampling overhead during mesh building.

---

## Issue #2: String-based Texture Lookup in Hot Path

**Location:** `src/main/java/mattmc/client/renderer/chunk/ModelElementRenderer.java` (lines 218-227)

**Problem:**
During mesh building, texture resolution involves string manipulation and HashMap lookups in the hot path:

```java
// Strip namespace prefix (string operation)
if (texturePath.contains(":")) {
    texturePath = texturePath.substring(texturePath.indexOf(':') + 1);
}

// Build atlas path (string concatenation)
String atlasPath = "assets/textures/" + texturePath + ".png";

// Resolve texture ID (HashMap lookup with string key)
int textureId = uvMapper.resolveTextureId(atlasPath);
```

**Impact:**
- **CPU:** String allocations and HashMap string lookups are expensive
- **RAM/GC:** Creates temporary String objects for every face in every model element
- For complex models (stairs, fences), this happens thousands of times per chunk

**Solution:**
Pre-bake texture IDs during model loading. Store integer texture IDs in `BlockModel` and `ModelElement` structures at resource load time, eliminating runtime string operations entirely.

```java
// During model loading (one time):
model.resolvedTextureIds = new HashMap<>();
for (String textureVar : model.getTextures().keySet()) {
    String path = resolveTexturePath(textureVar);
    model.resolvedTextureIds.put(textureVar, textureAtlas.getTextureId(path));
}

// During mesh building (hot path):
int textureId = model.getResolvedTextureId(elementFace.getTextureVar());  // Direct int lookup
```

**Expected Improvement:** 20-30% reduction in model element rendering time.

---

## Issue #3: LevelChunk Block Array Uses Excessive Memory Layout

**Location:** `src/main/java/mattmc/world/level/chunk/LevelChunk.java` (line 55)

**Problem:**
The chunk stores blocks as a 3D array `Block[16][384][16]`. This layout causes poor cache locality because accessing blocks in the Y direction jumps across memory:

```java
private final Block[][][] blocks;  // [x][y][z]
```

The Y dimension is 384 blocks (24x larger than X or Z), meaning sequential Y access causes cache misses.

**Impact:**
- **CPU Cache:** Poor locality when iterating Y columns (common in terrain generation, lighting)
- **RAM:** 3D arrays in Java have pointer overhead for each sub-array (16 × 384 = 6,144 extra pointers per chunk)

**Solution:**
Flatten to a 1D array with inline index calculation, or reorder to `[y][x][z]` if column operations are more common:

```java
// Option 1: Flatten with inline calculation
private final Block[] blocks = new Block[16 * 384 * 16];  // 98,304 blocks

public Block getBlock(int x, int y, int z) {
    return blocks[x + (z << 4) + (y << 8)];  // y * 256 + z * 16 + x
}

// Option 2: Use section-based storage like Minecraft
private final Block[][] sections = new Block[24][4096];  // 24 sections of 16³ each
```

**Expected Improvement:** 5-15% improvement in chunk iteration operations, reduced memory overhead by ~50KB per chunk.

---

## Issue #4: ChunkNBT.createSection() Iterates Entire Section Multiple Times

**Location:** `src/main/java/mattmc/world/level/chunk/ChunkNBT.java` (lines 65-180)

**Problem:**
When serializing a chunk section to NBT, the code iterates through all 4,096 blocks (16³) three separate times:

1. First pass: Check if section is empty (lines 70-79)
2. Second pass: Collect unique blocks for palette (lines 110-121)
3. Third pass: Fill block states array (lines 132-145)

```java
// Pass 1: Check empty
for (int x = 0; x < 16 && isEmpty; x++) {
    for (int y = baseY; y < baseY + 16 && isEmpty; y++) {
        for (int z = 0; z < 16 && isEmpty; z++) {
            if (chunk.getBlock(x, y, z) != Blocks.AIR) isEmpty = false;
        }
    }
}

// Pass 2: Build palette  
for (int x = 0; x < 16; x++) {
    for (int y = 0; y < 16; y++) {
        for (int z = 0; z < 16; z++) {
            Block block = chunk.getBlock(x, baseY + y, z);
            // ... palette building
        }
    }
}

// Pass 3: Fill block states
for (int x = 0; x < 16; x++) {
    for (int y = 0; y < 16; y++) {
        for (int z = 0; z < 16; z++) {
            Block block = chunk.getBlock(x, baseY + y, z);
            // ... block state packing
        }
    }
}
```

**Impact:**
- **CPU:** 12,288 getBlock() calls instead of 4,096 (3x more)
- **IO:** Slower chunk saving = longer world save times

**Solution:**
Merge into a single pass that builds palette and block states simultaneously:

```java
// Single pass: Check empty, build palette, and fill states
BitPackedArray blockStates = null;
List<String> paletteList = new ArrayList<>();
Map<String, Integer> paletteMap = new HashMap<>();
boolean isEmpty = true;

for (int x = 0; x < 16; x++) {
    for (int y = 0; y < 16; y++) {
        for (int z = 0; z < 16; z++) {
            Block block = chunk.getBlock(x, baseY + y, z);
            String identifier = block.getIdentifier();
            if (identifier == null) identifier = "mattmc:air";
            
            if (!isEmpty || block != Blocks.AIR) isEmpty = false;
            
            Integer paletteIndex = paletteMap.get(identifier);
            if (paletteIndex == null) {
                paletteIndex = paletteList.size();
                paletteMap.put(identifier, paletteIndex);
                paletteList.add(identifier);
            }
            
            // Store in temp array, convert to BitPackedArray at end
        }
    }
}
```

**Expected Improvement:** 2-3x faster chunk serialization.

---

## Issue #5: WorldGenerator.getTerrainHeight() Called Twice Per Column

**Location:** `src/main/java/mattmc/world/level/levelgen/WorldGenerator.java` (lines 93-96, 113-167)

**Problem:**
In `generateChunkTerrain()`, `getTerrainHeight()` is called for each XZ column. Then `isOcean()` internally calls `getTerrainHeight()` again:

```java
// In generateChunkTerrain():
int terrainHeight = getTerrainHeight(worldX, worldZ);
// ... fill terrain ...

// Later, checking for ocean:
if (terrainHeight < SEA_LEVEL) {  // Already known from above!
    // ...
}

// In isOcean() which might be called elsewhere:
public boolean isOcean(int worldX, int worldZ) {
    int height = getTerrainHeight(worldX, worldZ);  // Recalculates!
    return height < SEA_LEVEL;
}
```

More significantly, `getTerrainHeight()` performs 4 noise samples each time it's called:
- `sampleContinentalness()`
- `sampleErosion()`
- `samplePeaksValleys()`
- `sampleWeirdness()`

Each noise sample involves multiple octave calculations.

**Impact:**
- **CPU:** Noise sampling is computationally expensive; redundant calls waste cycles
- Terrain generation is a major performance factor during chunk loading

**Solution:**
Cache height values in a height map during generation, or add a memoization layer:

```java
// Option 1: Pre-compute height map for the chunk
public void generateChunkTerrain(LevelChunk chunk, WorldLightManager lightManager) {
    int chunkX = chunk.chunkX();
    int chunkZ = chunk.chunkZ();
    
    // Pre-compute all heights (single pass through noise)
    int[][] heights = new int[16][16];
    for (int x = 0; x < 16; x++) {
        for (int z = 0; z < 16; z++) {
            heights[x][z] = getTerrainHeight(chunkX * 16 + x, chunkZ * 16 + z);
        }
    }
    
    // Use cached heights for terrain filling
    // ...
}
```

**Expected Improvement:** 10-20% faster terrain generation.

---

## Issue #6: BlockFaceCollector Creates Excessive FaceData Objects

**Location:** `src/main/java/mattmc/client/renderer/block/BlockFaceCollector.java` (lines 96-112)

**Problem:**
For each visible face, a new `FaceData` object is created with 13 fields:

```java
if (topVisible) {
    topFaces.add(new FaceData(x, y, z, color, 1f, 1f, block, "top", null, chunk, cx, cy, cz));
}
// Similar for all 6 faces...
```

For a typical chunk with ~16K exposed faces, this creates ~16,000 `FaceData` objects per mesh build.

**Impact:**
- **RAM:** Each FaceData object has ~80 bytes of overhead (object header + references + primitives)
- **GC:** Creates significant garbage during chunk loading
- 16K objects × 80 bytes = ~1.3 MB of allocations per chunk mesh

**Solution:**
Use a struct-of-arrays (SoA) approach instead of array-of-structs (AoS):

```java
public class BlockFaceCollector {
    // Instead of List<FaceData>, use parallel arrays:
    private final FloatList faceX = new FloatList();
    private final FloatList faceY = new FloatList();
    private final FloatList faceZ = new FloatList();
    private final IntList faceBlock = new IntList();  // Block ID
    private final IntList faceCx = new IntList();
    private final IntList faceCy = new IntList();
    private final IntList faceCz = new IntList();
    // ... etc
    
    public void addFace(float x, float y, float z, Block block, ...) {
        faceX.add(x);
        faceY.add(y);
        // ...
    }
}
```

Or pool and reuse FaceData objects across mesh builds.

**Expected Improvement:** 50-70% reduction in mesh building allocations.

---

## Issue #7: ColorUtils Methods Allocate Arrays in Hot Path

**Location:** `src/main/java/mattmc/util/ColorUtils.java` (lines 59-80)

**Problem:**
`toNormalizedRGB()` and `toNormalizedRGBA()` create new float arrays on every call:

```java
public static float[] toNormalizedRGB(int rgb) {
    return new float[] {  // Allocation!
        extractRed(rgb) / NORMALIZE_DIVISOR,
        extractGreen(rgb) / NORMALIZE_DIVISOR,
        extractBlue(rgb) / NORMALIZE_DIVISOR
    };
}

public static float[] toNormalizedRGBA(int rgb, float alpha) {
    return new float[] {  // Allocation!
        extractRed(rgb) / NORMALIZE_DIVISOR,
        extractGreen(rgb) / NORMALIZE_DIVISOR,
        extractBlue(rgb) / NORMALIZE_DIVISOR,
        alpha
    };
}
```

**Impact:**
- **GC:** If called frequently (e.g., per face or per vertex), creates massive garbage
- Each call allocates 16-20 bytes for the array

**Solution:**
Use output parameter pattern or ThreadLocal reusable arrays:

```java
// Option 1: Output parameter
public static void toNormalizedRGB(int rgb, float[] out) {
    out[0] = extractRed(rgb) / NORMALIZE_DIVISOR;
    out[1] = extractGreen(rgb) / NORMALIZE_DIVISOR;
    out[2] = extractBlue(rgb) / NORMALIZE_DIVISOR;
}

// Option 2: ThreadLocal (already used elsewhere in codebase)
private static final ThreadLocal<float[]> RGB_RESULT = ThreadLocal.withInitial(() -> new float[3]);

public static float[] toNormalizedRGB(int rgb) {
    float[] result = RGB_RESULT.get();
    result[0] = extractRed(rgb) / NORMALIZE_DIVISOR;
    // ...
    return result;  // Caller must copy if storing
}
```

**Expected Improvement:** Eliminates allocations, reduces GC pressure.

---

## Issue #8: LightPropagator Creates New LightNode Objects for Every BFS Step

**Location:** `src/main/java/mattmc/world/level/lighting/LightPropagator.java` (lines 23-44, 151)

**Problem:**
The BFS light propagation algorithm creates a new `LightNode` object for every position visited:

```java
private static class LightNode {
    final LevelChunk chunk;
    final int x, y, z;
    final int r, g, b, i;
    
    LightNode(LevelChunk chunk, int x, int y, int z, int r, int g, int b, int i) { ... }
}

// In propagation:
if (newI > currentI) {
    chunk.setBlockLightRGBI(x, y, z, r, g, b, newI);
    addQueue.offer(new LightNode(chunk, x, y, z, r, g, b, newI));  // Allocation!
}
```

For a torch with light level 14, propagation can visit ~2,000-3,000 blocks. For loading a chunk with multiple light sources, this could be 10,000+ allocations.

**Impact:**
- **GC:** Heavy allocation pressure during chunk loading and light updates
- **CPU:** Object construction overhead

**Solution:**
Use an object pool or packed primitive representation:

```java
// Option 1: Packed longs in queue
// Pack: chunk ID (16 bits) | x (4 bits) | y (9 bits) | z (4 bits) | r (4 bits) | g (4 bits) | b (4 bits) | i (4 bits)
// = 49 bits, fits in a long

// Option 2: Object pool
private final Queue<LightNode> nodePool = new ArrayDeque<>();

private LightNode acquireNode(LevelChunk chunk, int x, int y, int z, int r, int g, int b, int i) {
    LightNode node = nodePool.poll();
    if (node == null) {
        node = new LightNode();
    }
    node.set(chunk, x, y, z, r, g, b, i);
    return node;
}

private void releaseNode(LightNode node) {
    nodePool.offer(node);
}
```

**Expected Improvement:** 80-90% reduction in light propagation allocations.

---

## Issue #9: ChunkUtils.isSectionEmpty() Does Full Scan

**Location:** `src/main/java/mattmc/world/level/chunk/ChunkUtils.java` (lines 197-210)

**Problem:**
To check if a section is empty, the code scans all 4,096 blocks:

```java
public static boolean isSectionEmpty(LevelChunk chunk, int startY, int endY) {
    for (int x = 0; x < CHUNK_WIDTH; x++) {
        for (int y = startY; y < endY; y++) {
            for (int z = 0; z < CHUNK_DEPTH; z++) {
                if (!chunk.getBlock(x, y, z).isAir()) {
                    return false;
                }
            }
        }
    }
    return true;
}
```

This is called from `AsyncChunkLoader.collectChunkFaces()` for every section (24 per chunk).

**Impact:**
- **CPU:** Up to 24 × 4,096 = 98,304 block lookups per chunk for empty sections
- Called during mesh building on background threads

**Solution:**
Maintain a per-section non-air block counter in `LevelChunk`:

```java
// In LevelChunk:
private final int[] sectionBlockCount = new int[24];  // Non-air count per section

public void setBlock(int x, int y, int z, Block block) {
    Block oldBlock = blocks[x][y][z];
    blocks[x][y][z] = block;
    
    int section = y / 16;
    if (oldBlock.isAir() && !block.isAir()) {
        sectionBlockCount[section]++;
    } else if (!oldBlock.isAir() && block.isAir()) {
        sectionBlockCount[section]--;
    }
}

public boolean isSectionEmpty(int sectionIndex) {
    return sectionBlockCount[sectionIndex] == 0;
}
```

**Expected Improvement:** O(1) empty check instead of O(4096), ~20-50x faster per section.

---

## Issue #10: MeshBuilder Uses 17 Floats Per Vertex (High Memory Bandwidth)

**Location:** `src/main/java/mattmc/client/renderer/chunk/MeshBuilder.java` (lines 375-396)

**Problem:**
Each vertex stores 17 floats (68 bytes):
- Position: 3 floats (x, y, z)
- UV: 2 floats
- Color: 4 floats (r, g, b, a)
- Normal: 3 floats (nx, ny, nz)
- Light: 4 floats (skyLight, blockLightR, G, B)
- AO: 1 float

```java
private void addVertex(float x, float y, float z, float u, float v, float[] color,
                      float nx, float ny, float nz, 
                      float skyLight, float blockLightR, float blockLightG, float blockLightB, float ao) {
    vertices.add(x);
    vertices.add(y);
    vertices.add(z);
    // ... 14 more adds
}
```

**Impact:**
- **VRAM:** Larger vertex buffers consume more GPU memory
- **GPU Bandwidth:** More data to transfer from VRAM to shader cores
- **CPU→GPU Transfer:** Larger mesh uploads take longer
- For a chunk with 10K faces × 4 vertices × 68 bytes = ~2.7 MB per chunk

**Solution:**
Pack data more efficiently:
1. Use bytes for colors (0-255 → 4 bytes instead of 16 bytes)
2. Pack normals into 2 bytes (octahedral encoding) or use face normal index (6 values = 3 bits)
3. Pack light values into bytes (0-15 fits in 4 bits each)

```java
// Reduced format: 36 bytes per vertex instead of 68
// Position: 3 floats = 12 bytes
// UV: 2 floats = 8 bytes  
// Color: 4 bytes (RGBA packed)
// Normal: 1 byte (face index 0-5)
// Light: 4 bytes (sky, blockR, blockG, blockB packed)
// AO: 1 byte
// = 12 + 8 + 4 + 1 + 4 + 1 = 30 bytes (can pad to 32)

// Vertex stride reduction: 68 → 32 bytes = 53% less memory/bandwidth
```

**Expected Improvement:** 40-50% reduction in VRAM usage and GPU bandwidth per chunk.

---

## Summary Table

| Issue | Component | Primary Impact | Expected Improvement |
|-------|-----------|----------------|---------------------|
| #1 | LightStorage | CPU | 10-20% faster light sampling |
| #2 | ModelElementRenderer | CPU, GC | 20-30% faster model rendering |
| #3 | LevelChunk | CPU Cache, RAM | 5-15% faster iteration, 50KB/chunk saved |
| #4 | ChunkNBT | CPU, IO | 2-3x faster serialization |
| #5 | WorldGenerator | CPU | 10-20% faster terrain gen |
| #6 | BlockFaceCollector | RAM, GC | 50-70% less mesh allocations |
| #7 | ColorUtils | GC | Eliminates allocation hotspot |
| #8 | LightPropagator | GC, CPU | 80-90% less light propagation allocs |
| #9 | ChunkUtils | CPU | 20-50x faster empty section check |
| #10 | MeshBuilder | VRAM, GPU | 40-50% less GPU memory/bandwidth |

---

## Next Steps

When you're ready to address any of these issues, I will:
1. Run the existing performance tests BEFORE making changes
2. Implement the fix
3. Run the performance tests AFTER to measure improvement
4. Provide a detailed comparison of the results

Let me know which issue(s) you'd like me to fix first!
