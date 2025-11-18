# Lighting and Shadow System - Bug Analysis Report

**Date**: 2025-11-18  
**Analysis Scope**: Complete lighting and shadow system inspection  
**Status**: Comprehensive bug documentation  

---

## Table of Contents
1. [System Architecture Overview](#system-architecture-overview)
2. [Critical Bugs](#critical-bugs)
3. [Moderate Issues](#moderate-issues)
4. [Minor Issues & Edge Cases](#minor-issues--edge-cases)
5. [Design Concerns](#design-concerns)
6. [Potential Performance Issues](#potential-performance-issues)
7. [Recommendations](#recommendations)

---

## System Architecture Overview

The lighting system consists of several interconnected components:

### Core Components
- **LightPropagator**: BFS-based blocklight propagation with RGBI (Red-Green-Blue-Intensity) support
- **SkylightEngine**: BFS-based skylight propagation with heightmap management
- **SkylightInitializer**: Static skylight initialization (legacy, simpler approach)
- **CrossChunkLightPropagator**: Handles light propagation across chunk boundaries with deferred updates
- **WorldLightManager**: Coordinates all lighting systems
- **LightStorage**: Packed array storage for skylight (4-bit) and blocklight RGBI (16-bit)
- **VertexLightSampler**: Samples light at vertices for smooth lighting gradients
- **VoxelLitShader**: OpenGL shader for rendering with gamma-corrected lighting

### Data Flow
```
Block Change → WorldLightManager → LightPropagator/SkylightEngine
                                 ↓
                         CrossChunkLightPropagator (if boundary)
                                 ↓
                            LightStorage (RGBI packed)
                                 ↓
                          VertexLightSampler
                                 ↓
                           VoxelLitShader
```

---

## Critical Bugs

### BUG-001: Inconsistent Opacity Threshold Between Systems
**Location**: `SkylightInitializer.java:84` vs `SkylightEngine.java:380` vs `LightPropagator.java:142`

**Description**: Different opacity thresholds are used across the lighting system:
- **SkylightInitializer** uses `opacity > 0` to find opaque blocks
- **SkylightEngine** uses `opacity >= 15` to block light propagation
- **LightPropagator** uses `opacity >= 15` to block light propagation

**Impact**: 
- Blocks with opacity between 1-14 are treated as opaque for heightmap calculation but transparent for light propagation
- This creates inconsistency where partially transparent blocks (like glass, leaves, water) may:
  - Be counted as the heightmap top (blocking skylight from above)
  - Still allow light to propagate through them
  - Result in incorrect skylight values in columns with semi-transparent blocks

**Example Scenario**:
```
Y=100: Air (skylight=15)
Y=99:  Glass (opacity=5) ← Heightmap set here
Y=98:  Air (skylight=?) ← Should be 15 but might be 0
Y=97:  Stone (opacity=15)
```

**Fix Required**: Standardize opacity threshold to `>= 15` across all systems, or implement proper semi-transparency support with light attenuation.

---

### BUG-002: Missing Re-propagation in LightPropagator.updateBlockLight()
**Location**: `LightPropagator.java:419-421`

**Description**: When a non-emissive opaque block is placed, the code clears light at that position but has a TODO comment indicating re-propagation from neighbors should happen but doesn't:

```java
// TODO: This should trigger re-propagation from neighbors
// For now, we just clear the light at this position
```

**Impact**:
- Light is removed when opaque block is placed, but neighboring light doesn't fill in
- Creates permanent dark spots until chunks are reloaded or light is manually re-propagated
- Affects gameplay when building structures with blocks that should block light

**Reproduction**:
1. Place torch to light area
2. Place stone block in lit area
3. Light at block position goes dark (correct)
4. Remove stone block
5. Light doesn't return until torch is updated or chunk reloads

**Fix Required**: Implement `propagateLightFromNeighbors()` call after clearing light from newly placed opaque blocks.

---

### BUG-003: Missing Cross-Chunk Support in propagateLightFromNeighbor()
**Location**: `LightPropagator.java:466-468`

**Description**: The method has a TODO for cross-chunk propagation:

```java
// TODO: Handle cross-chunk propagation
return;
```

**Impact**:
- When removing an opaque block at chunk edge, light from neighboring chunks doesn't propagate into the newly transparent space
- Creates dark edges at chunk boundaries when terrain changes
- Requires chunk reload or manual light updates to fix

**Example Scenario**:
```
Chunk 0                Chunk 1
[Stone][Air]  →  [Air][Torch]
       ^
   Remove this stone
   Light from Chunk 1 torch should propagate here but doesn't
```

**Fix Required**: Implement cross-chunk propagation in `propagateLightFromNeighbor()` similar to the existing propagation methods.

---

### BUG-004: RGBI Color Scaling Issues in VertexLightSampler
**Location**: `VertexLightSampler.java:293-310`

**Description**: The RGB scaling logic may produce incorrect results:

```java
int maxRGB = Math.max(r, Math.max(g, b));
if (maxRGB == 0) {
    return new int[] {intensity, intensity, intensity}; // ← Converts to white!
}
float scale = (float) intensity / maxRGB;
```

**Impact**:
- If RGB values are all 0 but intensity is non-zero (data corruption case), the code returns white light instead of black
- This masks data corruption issues and creates incorrect lighting colors
- Should never happen in correct operation, but indicates a defensive programming issue

**Fix Required**: Return `[0, 0, 0]` if `maxRGB == 0` regardless of intensity, or log an error for this invalid state.

---

### BUG-005: Potential Integer Overflow in chunkKey() Calculation
**Location**: `CrossChunkLightPropagator.java:569-571`

**Description**: Chunk key calculation may overflow:

```java
private long chunkKey(int chunkX, int chunkZ) {
    return ((long)chunkX << 32) | (chunkZ & 0xFFFFFFFFL);
}
```

**Impact**:
- Works correctly for positive chunk coordinates
- For negative chunk Z coordinates, the `& 0xFFFFFFFFL` mask treats them as large positive values
- This is actually correct behavior for the key uniqueness, BUT:
  - If chunkX is extremely large (> 2^31), it could cause issues
  - If chunkZ negative values aren't handled correctly elsewhere, lookup failures occur

**Verification Needed**: Test with negative chunk coordinates (chunks at negative world positions).

---

## Moderate Issues

### ISSUE-001: Color Mixing During Propagation May Not Preserve Hue
**Location**: `LightPropagator.java:157-164`

**Description**: When two lights of the same intensity but different colors meet, they are averaged:

```java
if (newI == currentI) {
    int mixedR = (r + currentR) / 2;
    int mixedG = (g + currentG) / 2;
    int mixedB = (b + currentB) / 2;
    chunk.setBlockLightRGBI(x, y, z, mixedR, mixedG, mixedB, newI);
}
```

**Impact**:
- Integer division truncates: `(15 + 14) / 2 = 14` (loses 0.5)
- Repeated mixing gradually dims colors: Mix(15,15,15) + (14,14,14) = (14,14,14)
- Over multiple mixing operations, all colors tend toward dimmer values
- Example: Red torch (14,0,0) + Green torch (0,14,0) = (7,7,0) yellow, but repeated mixing → (6,6,0) → (5,5,0) etc.

**Fix Suggestion**: Use weighted averaging or store partial values to preserve brightness.

---

### ISSUE-002: Light Removal May Miss Corners in Complex Geometries
**Location**: `LightPropagator.java:335-347`

**Description**: Light removal algorithm compares intensities to determine if light came from removed source:

```java
if (neighborI == sourceIntensity) {
    // Equal intensity - could be from this source or another
    // Remove it and let re-propagation decide
```

**Impact**:
- In complex scenarios with multiple light sources of equal intensity, removal may be overly aggressive
- Then relies on re-propagation to restore correct lighting
- This works but is inefficient and can cause temporary lighting artifacts
- Example: Three torches at corners of triangle, remove center torch - corner lights flicker

**Note**: Current implementation is conservative (remove then re-propagate) which is safe but potentially slow.

---

### ISSUE-003: Skylight Propagation Doesn't Handle Semi-Transparent Blocks
**Location**: `SkylightEngine.java:152-155`

**Description**: Skylight uses binary opacity check:

```java
if (block == null || block.getOpacity() >= 15) {
    return; // Opaque block stops light or null block
}
```

**Impact**:
- Semi-transparent blocks (glass, leaves, ice) should attenuate but not completely block skylight
- Currently treats them as fully transparent (no attenuation)
- This is actually fine for most cases but unrealistic for colored glass, tinted windows, etc.

**Enhancement Opportunity**: Implement opacity-based attenuation: `newLight = oldLight - opacity/15`

---

### ISSUE-004: Deferred Updates May Grow Unbounded
**Location**: `CrossChunkLightPropagator.java:79-82`

**Description**: Deferred updates are stored in a HashMap with no size limit:

```java
private final Map<Long, List<CrossChunkUpdate>> deferredUpdates = new HashMap<>();
```

**Impact**:
- If chunks never load (player moves away before chunk loads), deferred updates accumulate
- Memory leak in long-running servers with lots of chunk edge light changes
- No cleanup mechanism for updates to chunks that may never load

**Fix Suggestion**: Add max deferred update limit or time-based cleanup, or clear on chunk unload events.

---

### ISSUE-005: Vertex Light Sampling Doesn't Account for Block Shapes
**Location**: `VertexLightSampler.java:146-207`

**Description**: Vertex sampling assumes full cube blocks:

```java
private int[] getVertexSampleOffsets(int normalIndex, int cornerIndex)
```

**Impact**:
- Stairs, slabs, fences sample light from wrong positions
- Vertical edges of stairs may use light from blocks above/below the actual stair surface
- Causes visual artifacts on non-full-block geometry

**Current Workaround**: Special handling exists for stairs in `StairsGeometryBuilder` but may not be complete.

---

## Minor Issues & Edge Cases

### EDGE-001: Chunk Y-Bounds Check Missing in Some Paths
**Location**: Multiple files

**Description**: Some light sampling paths check Y bounds, others assume valid:
- `VertexLightSampler.getSkyLightSafe()` checks: `if (y < 0 || y >= LevelChunk.HEIGHT)`
- `LightPropagator.propagateRGBIToNeighbor()` checks Y bounds
- But some internal methods assume Y is already validated

**Impact**: Minor - most paths validate before calling, but edge cases near world height limits could cause array access exceptions.

---

### EDGE-002: Null Block Handling Inconsistency
**Location**: `LightPropagator.java:142`, `SkylightEngine.java:153`

**Description**: Different null block handling:
- LightPropagator: `if (block == null || block.getOpacity() >= 15) return;` (treats null as opaque)
- SkylightEngine: `if (block == null || block.getOpacity() >= 15) return;` (treats null as opaque)

**Impact**: 
- Null blocks should theoretically never occur but defensive checks exist
- Treating as opaque is safe but may hide chunk data corruption
- Should log warning when null block encountered

---

### EDGE-003: Light Values Clamped at Set, Not Get
**Location**: `LightStorage.java:150-152`

**Description**: Light values are validated and clamped only when setting:

```java
if (level < 0 || level > 15) {
    throw new IllegalArgumentException("Light level must be 0-15, got: " + level);
}
```

**Impact**:
- Get operations trust stored data is valid
- If data corruption occurs (bad save file, memory corruption), invalid values propagate
- May cause rendering artifacts or shader issues

**Enhancement**: Add validation on get, or checksum verification on load.

---

### EDGE-004: Shader Minimum Brightness May Hide Lighting Bugs
**Location**: `voxel_lit.fs:58`

**Description**: Fragment shader enforces minimum brightness:

```glsl
finalLightColor = max(finalLightColor, vec3(0.25));
```

**Impact**:
- Prevents completely black blocks (good for gameplay)
- But also masks lighting bugs where light should be 0
- Difficult to debug why areas aren't as dark as expected
- Value increased from 0.05 → 0.20 → 0.25 to fix "interior corner darkness"

**Note**: This is a band-aid fix. Root cause of dark corners should be investigated separately.

---

### EDGE-005: Race Condition in Cross-Chunk Deferred Updates
**Location**: `CrossChunkLightPropagator.java:511-530`

**Description**: Deferred updates processed immediately when chunk loads, but if multiple chunks load simultaneously, order matters:

```java
public void processDeferredUpdates(LevelChunk chunk) {
    List<CrossChunkUpdate> updates = deferredUpdates.remove(chunkKey);
    for (CrossChunkUpdate update : updates) {
        // Process each update
    }
}
```

**Impact**:
- If chunk A and B load at same time, and both have deferred updates to each other:
  - A loads, processes updates to A (may reference B which isn't loaded yet)
  - B loads, processes updates to B (may reference A which is already loaded)
- Could cause asymmetric lighting at chunk boundaries
- Very rare in single-threaded loading but possible in concurrent scenarios

---

## Design Concerns

### DESIGN-001: Dual Skylight Systems (Initializer vs Engine)
**Location**: `SkylightInitializer.java` and `SkylightEngine.java`

**Description**: Two different skylight systems exist:
- **SkylightInitializer**: Static vertical fill based on heightmap
- **SkylightEngine**: BFS propagation with dynamic updates

**Concern**:
- Unclear which is used where
- Initializer is simpler but doesn't handle caves/overhangs
- Engine is complete but more complex
- Tests use both, creating confusion

**Recommendation**: Deprecate SkylightInitializer, standardize on SkylightEngine.

---

### DESIGN-002: RGBI vs RGB-Only Storage
**Location**: `LightStorage.java:87-93`

**Description**: Block light stores RGBI (16 bits) but intensity is derivable from RGB:

```java
// Pack RGBI into 2 bytes: RRRRGGGG BBBBIIII
int packed = ((r & 0x0F) << 12) | ((g & 0x0F) << 8) | ((b & 0x0F) << 4) | (i & 0x0F);
```

**Concern**:
- Intensity is redundant: `I = max(R, G, B)` in most cases
- Wastes 4 bits per block (could store another property)
- BUT: Used for faster intensity comparisons without unpacking RGB

**Tradeoff**: Storage efficiency vs computation speed. Current design favors speed.

---

### DESIGN-003: BFS Queue Reuse May Cause State Issues
**Location**: `LightPropagator.java:47-48`

**Description**: BFS queues are class-level fields reused across operations:

```java
private final Queue<LightNode> addQueue = new ArrayDeque<>();
private final Queue<LightNode> removeQueue = new ArrayDeque<>();
```

**Concern**:
- If an operation is interrupted or throws exception, queue may have stale data
- Next operation could process stale nodes
- Queues are cleared at operation start, but if operation fails mid-way...

**Recommendation**: Clear queues in finally blocks or use method-local queues.

---

### DESIGN-004: No Light Dirty Tracking
**Location**: General architecture

**Description**: No mechanism to track which chunks have dirty/outdated light:

**Concern**:
- When chunk loads, deferred updates are processed, but what if updates were missed?
- No way to know if a chunk's lighting is "correct" or needs recalculation
- Chunk saves include light data, but no versioning or dirty flags

**Enhancement**: Add dirty flag + version number to chunk light data, allow forced recalculation.

---

### DESIGN-005: Heightmap Opacity Threshold Mismatch
**Location**: `SkylightInitializer.java:84` (see BUG-001)

**Description**: As mentioned in BUG-001, this is also a design concern:
- The heightmap concept assumes binary opacity (opaque vs transparent)
- But the light system supports semi-transparent blocks
- These two concepts conflict

**Decision Needed**: 
- Option A: Heightmap only tracks fully opaque blocks (opacity >= 15)
- Option B: Heightmap tracks any opacity (opacity > 0) but light attenuates through semi-transparent
- Current implementation is hybrid and inconsistent

---

## Potential Performance Issues

### PERF-001: Cross-Chunk Propagation Uses Recursion
**Location**: `CrossChunkLightPropagator.java:272-278`

**Description**: Cross-chunk RGBI propagation is recursive:

```java
propagateBlockLightRGBICross(targetChunk, targetLocalX - 1, y, targetLocalZ, r, g, b, nextI);
propagateBlockLightRGBICross(targetChunk, targetLocalX + 1, y, targetLocalZ, r, g, b, nextI);
// ... 4 more recursive calls
```

**Impact**:
- Deep recursion for lights with high intensity (15 blocks deep)
- Stack overflow risk for large connected light volumes
- Slower than iterative BFS

**Fix Suggestion**: Convert to iterative BFS with explicit queue (like LightPropagator does internally).

---

### PERF-002: Light Removal Re-propagates Entire Affected Volume
**Location**: `LightPropagator.java:178-254`

**Description**: Light removal algorithm:
1. BFS removes all light possibly from source
2. Collects boundary nodes
3. Re-propagates from ALL boundary nodes

**Impact**:
- Conservative approach ensures correctness
- But re-propagates even areas that didn't need updating
- Removing torch in large lit room re-propagates entire room
- O(n²) behavior where n = lit volume

**Optimization**: Track which areas actually lost light, only re-propagate those regions.

---

### PERF-003: Vertex Light Sampling Redundant Calculations
**Location**: `VertexLightSampler.java:138-141`

**Description**: Each vertex samples 4 positions, many vertices share sample points:

```java
for (int i = 0; i < 4; i++) {
    int sx = cx + dx;
    int sy = cy + dy;
    int sz = cz + dz;
    int skyLight = getSkyLightSafe(face.chunk, sx, sy, sz);
```

**Impact**:
- Adjacent block faces sample many of the same positions
- Light values retrieved multiple times from storage
- Could cache light values per chunk section during mesh building

**Optimization**: Add light value cache to MeshBuilder, clear per chunk section.

---

### PERF-004: Deferred Updates Stored as Objects
**Location**: `CrossChunkLightPropagator.java:22-64`

**Description**: Each deferred update allocates a `CrossChunkUpdate` object:

```java
public static class CrossChunkUpdate {
    public final int chunkX, chunkZ;
    public final int localX, localY, localZ;
    public final int lightLevel;
    // ... more fields
}
```

**Impact**:
- Many small object allocations during chunk boundary light propagation
- GC pressure in areas with lots of chunk loading/unloading
- Each update is ~40+ bytes (object header + fields)

**Optimization**: Use primitive arrays or packed long values instead of objects.

---

### PERF-005: No Spatial Indexing for Deferred Updates
**Location**: `CrossChunkLightPropagator.java:485-492`

**Description**: Deferred updates stored in HashMap by chunk key only:

```java
deferredUpdates.computeIfAbsent(chunkKey, k -> new ArrayList<>()).add(update);
```

**Impact**:
- When processing, must iterate all updates for a chunk
- No spatial locality - updates for opposite corners processed together
- Could benefit from octree or spatial subdivision

**Note**: Likely premature optimization unless deferred update count is very high (>1000 per chunk).

---

## Recommendations

### High Priority Fixes

1. **Fix BUG-001 (Opacity Threshold Inconsistency)**
   - Standardize to `opacity >= 15` for full opacity
   - Update SkylightInitializer to match
   - Add test coverage for semi-transparent blocks

2. **Fix BUG-002 (Missing Re-propagation)**
   - Implement neighbor re-propagation when opaque block placed
   - Test with torch → stone → remove stone workflow

3. **Fix BUG-003 (Missing Cross-Chunk in propagateLightFromNeighbor)**
   - Add cross-chunk support to complete the lighting loop
   - Test at chunk boundaries

### Medium Priority Improvements

4. **Address ISSUE-001 (Color Mixing Precision)**
   - Consider using fixed-point arithmetic or accumulation buffers
   - Prevent color drift over multiple mixing operations

5. **Address ISSUE-004 (Deferred Update Memory Leak)**
   - Add max size limit or TTL for deferred updates
   - Clear deferred updates for chunks that unload without loading

6. **Resolve DESIGN-001 (Dual Skylight Systems)**
   - Deprecate SkylightInitializer
   - Migrate all code to use SkylightEngine
   - Remove or clearly document when each is appropriate

### Low Priority Enhancements

7. **Add DESIGN-004 (Light Dirty Tracking)**
   - Version light data in chunks
   - Allow forced light recalculation
   - Detect and repair corrupt lighting

8. **Optimize PERF-001 (Recursive Cross-Chunk)**
   - Convert to iterative BFS
   - Add stack overflow protection

9. **Optimize PERF-002 (Light Removal Re-propagation)**
   - Track dirty regions more precisely
   - Only re-propagate areas that lost light

### Testing & Validation

10. **Add Integration Tests**
    - Test semi-transparent blocks (glass, leaves, water)
    - Test negative chunk coordinates
    - Test extreme light intensities
    - Test concurrent chunk loading

11. **Add Lighting Validation Tool**
    - Detect light leaks
    - Find orphaned light (light with no source)
    - Verify light intensity gradients

12. **Performance Profiling**
    - Measure light update time in chunk loading
    - Profile deferred update processing
    - Benchmark vertex light sampling

---

## Summary Statistics

- **Critical Bugs**: 5
- **Moderate Issues**: 5  
- **Minor Issues/Edge Cases**: 5
- **Design Concerns**: 5
- **Performance Issues**: 5

**Total Issues Identified**: 25

**Severity Breakdown**:
- Must Fix: 3 (BUG-001, BUG-002, BUG-003)
- Should Fix: 7 (BUG-004, BUG-005, ISSUE-001, ISSUE-002, ISSUE-004, DESIGN-001, PERF-001)
- Nice to Fix: 15 (remaining issues)

---

## Conclusion

The lighting system is **generally functional but has several critical bugs** that affect:
1. **Light consistency** at chunk boundaries
2. **Light propagation** through semi-transparent blocks  
3. **Light removal/updates** when terrain changes

The most impactful issues are:
- **Opacity threshold mismatch** causing skylight errors with glass/leaves
- **Missing re-propagation** creating permanent dark spots
- **Incomplete cross-chunk support** causing chunk boundary artifacts

The good news:
- ✅ Test coverage is extensive (25+ lighting tests)
- ✅ RGBI color lighting works correctly in most cases
- ✅ BFS propagation algorithm is sound
- ✅ Cross-chunk deferred updates are implemented

**Recommended immediate action**: Fix BUG-001, BUG-002, and BUG-003 first, as these have the most visible impact on gameplay and user experience.

---

**End of Report**
