# Failed Tests Analysis

This document provides a comprehensive analysis of the 11 failing tests in the MattMC project. Each test failure is documented with:
- What the test is testing
- Why it's failing
- Whether the failure indicates a real bug or a false positive due to changed behavior

## Summary

**Total Failing Tests: 11**

1. **ChunkLightSerializationTest**: 3 failures - Light data not persisting after serialization
2. **LightStorageTest**: 1 failure - Array size changed from legacy to RGBI format
3. **CrossChunkLightRemovalTest**: 1 failure - Light not propagating across chunks
4. **CrossChunkLightTest**: 4 failures - Cross-chunk light propagation issues
5. **CrossChunkVertexLightTest**: 2 failures - Vertex light sampling at chunk boundaries

---

## 1. ChunkLightSerializationTest (3 failures)

### Test Purpose
Tests that light data (both skylight and block light) persists correctly through the save/load cycle when chunks are serialized to NBT format and then deserialized.

### Failing Tests

#### 1.1 testLightDataRoundTrip()
**Line 46**: `assertEquals(10, loaded.getSkyLight(0, 64, 0), "Sky light at (0,64,0)")`

**Failure**: `expected: <10> but was: <15>`

**What it tests**: 
- Sets custom skylight values (10, 8, 6) at various positions
- Serializes chunk to NBT
- Deserializes chunk from NBT
- Verifies that custom skylight values are preserved

**Why it's failing**:
The test sets skylight to 10, but after deserialization it returns to 15 (the default). This indicates that custom skylight values are NOT being preserved during the save/load cycle. The issue is in the chunk serialization/deserialization process - specifically in `ChunkNBT.java`.

**Root Cause Analysis**:
Looking at `ChunkNBT.fromNBT()` (line 243), there's a call to `chunk.recalculateBlockLight()` at the end of loading. This method likely resets the light values, overwriting the loaded data. Additionally, the chunk sets `suppressLightUpdates` to true during loading, but after loading completes and it's set back to false, the light system may be reinitializing values.

**Is this a real bug or false positive?**: **REAL BUG** - Light data should persist across save/load cycles. Players expect their lighting to remain the same when they reload their world.

---

#### 1.2 testLightWithModifiedValues()
**Line 86**: `assertEquals(3, loaded.getSkyLight(8, 64, 8))`

**Failure**: `expected: <3> but was: <15>`

**What it tests**:
- Sets a block with very dark skylight (3) and bright blocklight (14)
- Serializes and deserializes
- Verifies the custom values are preserved

**Why it's failing**:
Same root cause as testLightDataRoundTrip - skylight values are being reset to default (15) instead of preserving the saved value of 3.

**Is this a real bug or false positive?**: **REAL BUG** - Same issue as above.

---

#### 1.3 testChunkPosition()
**Line 107**: `assertEquals(11, loaded.getSkyLight(3, 100, 7))`

**Failure**: `expected: <11> but was: <15>`

**What it tests**:
- Tests that chunk position is preserved
- Tests that light data in non-zero chunk positions works correctly
- Sets skylight to 11 and blocklight to 4 at position (3, 100, 7)

**Why it's failing**:
Same serialization issue - the skylight value of 11 is not being preserved and defaults back to 15.

**Is this a real bug or false positive?**: **REAL BUG** - Same serialization issue.

---

## 2. LightStorageTest (1 failure)

### Test Purpose
Tests the `LightStorage` class which stores light data for a 16x16x16 chunk section using packed arrays.

### Failing Test

#### 2.1 testArraySerialization()
**Line 65**: `assertEquals(2048, blockLight.length)`

**Failure**: `expected: <2048> but was: <8192>`

**What it tests**:
- Creates a LightStorage with some light values
- Gets the raw byte arrays for serialization
- Expects skylight array to be 2048 bytes (nibble array, 4 bits per value)
- Expects blocklight array to be 2048 bytes (nibble array, 4 bits per value)

**Why it's failing**:
The `LightStorage` class has been refactored to use **RGBI (Red, Green, Blue, Intensity) lighting** instead of the legacy single-channel lighting. The new format uses:
- **SkyLight**: Still 2048 bytes (4 bits per value) ✓ PASSING
- **BlockLight**: Now 8192 bytes (2 bytes per position for RGBI, storing 4×4 bits = 16 bits)

Looking at `LightStorage.java`:
- Line 17: `private static final int BLOCKLIGHT_ARRAY_SIZE = TOTAL_BLOCKS * 2; // 8192 bytes`
- Line 22-23: Format is `RRRRGGGG BBBBIIII` (5R, 5G, 5B, 5I bits) stored in 2 bytes

**Is this a real bug or false positive?**: **FALSE POSITIVE** - This is expected behavior change. The lighting system has been upgraded from single-channel to RGBI (colored lighting). The test expectations need to be updated to:
```java
assertEquals(8192, blockLight.length); // Changed from 2048 to 8192
```

**Additional Context**:
The test also verifies round-trip serialization (line 69-70) which should still work correctly with the new format, as the constructor handles both legacy (2048 bytes) and RGBI (8192 bytes) formats (see LightStorage.java lines 58-82).

---

## 3. CrossChunkLightRemovalTest (1 failure)

### Test Purpose
Tests that when a light source (torch) near a chunk boundary is removed, the light is properly removed from BOTH the source chunk AND neighboring chunks.

### Failing Test

#### 3.1 testTorchJustInsideChunkBoundary()
**Line 123**: `assertTrue(chunk1.getBlockLightR(1, y, 8) > 0, "Light should propagate further into chunk 1")`

**Failure**: `expected: <true> but was: <false>`

**What it tests**:
- Places a torch one block inside chunk boundary (x=14 in chunk 0)
- Verifies light reaches into neighboring chunk (chunk 1)
- Tests that light propagates at least 2 blocks into neighbor (x=0 and x=1)
- Then removes torch and verifies all light is gone

**Why it's failing**:
The test fails at the propagation verification step, before even getting to the removal test. The torch at x=14 should propagate:
- x=14 → x=15 (edge of chunk 0)
- x=15 → x=0 (chunk boundary crossing into chunk 1)  
- x=0 → x=1 (further into chunk 1)

The light is NOT propagating far enough into chunk 1. It might reach x=0 but doesn't reach x=1.

**Root Cause Analysis**:
The test uses `propagator.addBlockLightRGB(chunk0, nearEdgeX, y, 8, 14, 11, 0)` with RGB values (14, 11, 0). 
Looking at Blocks.java line 69: `TORCH = register("torch", new Block(false, 14, 14, 11, 0))`

However, the test expects the light to travel:
- Distance from x=14 to x=1 in chunk1 = 3 blocks
- With RGB (14, 11, 0), the red channel (14) should attenuate: 14 → 13 → 12 → 11
- At distance 3, red should be 11, which is > 0

The issue is likely that cross-chunk light propagation is not working correctly. The light may not be propagating across the chunk boundary at all, or it's being attenuated incorrectly.

**Is this a real bug or false positive?**: **REAL BUG** - Cross-chunk light propagation is not working as expected. This is critical functionality for a voxel game as lighting should be seamless across chunk boundaries.

---

## 4. CrossChunkLightTest (4 failures)

### Test Purpose
Tests that light from blocks (especially torches) properly propagates across chunk boundaries in both directions, with correct attenuation.

### Failing Tests

#### 4.1 testBlockLightCrossesChunkBoundary()
**Line 57**: `assertEquals(13, neighborLight, "Light should attenuate by 1 across boundary")`

**Failure**: `expected: <13> but was: <14>`

**What it tests**:
- Places torch at chunk edge (x=15 in chunk 0)
- Torch has light level 14
- Expects light in neighboring chunk (x=0 in chunk 1) to be 13 (attenuated by 1)

**Why it's failing**:
The test expects `neighborLight = 13` but gets `14`. This means NO ATTENUATION is happening across the chunk boundary. The light value of 14 is being copied directly to the neighboring chunk without any distance-based reduction.

**Root Cause**:
The cross-chunk light propagation is not properly applying attenuation. When light crosses from chunk 0 (x=15) to chunk 1 (x=0), it should decrease by 1, but it's staying at 14.

**Is this a real bug or false positive?**: **REAL BUG** - Light should attenuate when it propagates. This indicates the cross-chunk propagation logic is incorrectly handling attenuation.

---

#### 4.2 testDeferredUpdateWhenChunkNotLoaded()
**Line 80**: `assertTrue(deferredCount > 0, "Should have deferred updates for unloaded chunk")`

**Failure**: `expected: <true> but was: <false>`

**What it tests**:
- Places torch at chunk edge BEFORE the neighboring chunk is loaded
- Expects the lighting system to queue "deferred updates" for the unloaded chunk
- When the chunk loads, deferred updates should be processed automatically

**Why it's failing**:
The test expects `deferredCount > 0` but gets 0. This means the lighting system is NOT tracking deferred updates for unloaded chunks.

**Root Cause**:
The `CrossChunkLightPropagator.getDeferredUpdateCount()` method is returning 0. Either:
1. The system is not creating deferred updates when propagating to unloaded chunks, OR
2. The deferred update tracking mechanism is not implemented

This is a critical feature for open-world games - when you place a torch near a chunk boundary, the light should automatically propagate when that chunk loads later.

**Is this a real bug or false positive?**: **REAL BUG** - Deferred updates are a required feature for proper lighting in chunk-based worlds. Without this, players would see lighting "pop in" when chunks load.

---

#### 4.3 testLightCrossesMultipleChunkBoundaries()
**Line 106**: `assertTrue(chunk0.getBlockLight(torchX, y, z) == 11, "Source chunk should have full torch light")`

**Failure**: `expected: <true> but was: <false>`

**What it tests**:
- Places torch in middle of chunk 0 (x=8)
- Expects light level of 11 at torch position
- Tests that light reaches across chunk boundaries to chunk 1 and potentially chunk 2

**Why it's failing**:
The torch at x=8 doesn't have light level 11. Looking at the torch definition (Blocks.java line 69), a torch has RGB values (14, 14, 11, 0). The test uses `getBlockLight()` which returns the intensity channel (deprecated method, see LightStorage.java line 246).

The issue is that `getBlockLight()` returns the **intensity** (I) channel, not the max of RGB. When a torch is placed via `setBlock()`, it should set RGBI properly, but it seems the light value is not being set correctly.

**Root Cause**:
When blocks are placed, their emission values may not be propagated correctly to create the actual light. The test expects the torch to emit light level 11 (from the B channel), but `getBlockLight()` returns intensity which might not be set.

**Is this a real bug or false positive?**: **REAL BUG** - When torches are placed, they should automatically emit light. This is core gameplay functionality.

---

#### 4.4 testNoSeamsAtChunkBoundary()
**Line 141-142**: `assertEquals(lightAtBoundary - 1, lightAcrossBoundary, "Light should attenuate smoothly across boundary")`

**Failure**: `expected: <13> but was: <14>`

**What it tests**:
- Places torch at x=15 (chunk 0 edge)
- Gets light at x=15: expects 14
- Gets light at x=0 (chunk 1): expects 13
- Verifies they differ by exactly 1 (smooth attenuation, no "seam")

**Why it's failing**:
Same issue as testBlockLightCrossesChunkBoundary - no attenuation is happening. The light stays at 14 instead of dropping to 13.

**Is this a real bug or false positive?**: **REAL BUG** - Same attenuation issue.

---

## 5. CrossChunkVertexLightTest (2 failures)

### Test Purpose
Tests that when building chunk meshes for rendering, vertex light values are correctly sampled from neighboring chunks at chunk boundaries. This is critical for smooth lighting across chunk borders.

### Failing Tests

#### 5.1 testBlockLightCrossesChunkBoundaryForVertices()
**Line 58**: `assertEquals(10, boundaryLight, "Light should attenuate by 1 across boundary")`

**Failure**: `expected: <10> but was: <14>`

**What it tests**:
- Places torch at chunk edge (x=15 in chunk 0)
- Torch has light level 14
- Expects light at x=0 in chunk 1 to be 10 (attenuated)

**Why it's failing**:
The test expects attenuation from 14 to 10 (reduction of 4), but gets 14 (no attenuation). 

**Note**: There's an inconsistency in the test itself. The test comment says "attenuate by 1" but expects 10 instead of 13. Looking at the test more carefully:
- Line 53: Torch light level is checked to be 14
- Line 58: Expected boundary light is 10

This suggests the test writer expected more attenuation than just -1. However, based on the actual failure (getting 14), the same attenuation bug exists here too.

**Is this a real bug or false positive?**: **REAL BUG** - Same cross-chunk attenuation issue. The test expectations might also need review, but the core bug is that NO attenuation is happening (getting 14 instead of any lower value).

---

#### 5.2 testVertexLightSamplingConsistency()
**Line 163**: `assertEquals(lightFromChunk0 - 1, lightFromChunk1, "Light values should be consistent across chunk boundary")`

**Failure**: `expected: <12> but was: <13>`

**What it tests**:
- Places torch at x=14 in chunk 0
- Gets light at x=15 (chunk 0 edge)
- Gets light at x=0 (chunk 1)
- Expects them to differ by exactly 1

**Why it's failing**:
- Light at x=15 is 13
- Light at x=0 is 13
- Expected x=0 to be 12 (13 - 1)

This shows the same attenuation bug - light is not being reduced when crossing the chunk boundary.

**Is this a real bug or false positive?**: **REAL BUG** - Same cross-chunk attenuation issue.

---

## Root Cause Summary

### Primary Issues Identified

1. **Light Serialization Bug (3 tests)**
   - Skylight values are not persisting through save/load cycles
   - After deserialization, skylight returns to default value (15)
   - Likely caused by `recalculateBlockLight()` or other post-load initialization overwriting loaded data
   - **Impact**: HIGH - Players lose custom lighting when reloading worlds

2. **RGBI Format Change (1 test)**
   - LightStorage refactored from single-channel (2048 bytes) to RGBI (8192 bytes)
   - Test expectations need updating
   - **Impact**: NONE - This is intentional behavior, test needs updating

3. **Cross-Chunk Attenuation Bug (6 tests)**
   - Light does not attenuate when crossing chunk boundaries
   - Light value stays the same instead of decreasing by 1 per block
   - Affects both block light propagation and vertex light sampling
   - **Impact**: HIGH - Visual seams at chunk boundaries, unrealistic lighting

4. **Deferred Updates Not Working (1 test)**
   - System doesn't track light updates for unloaded chunks
   - When chunks load later, they don't receive pending light updates
   - **Impact**: MEDIUM - Light won't appear until chunk is reloaded or light source is updated

---

## Recommended Actions

### Critical Fixes (Real Bugs)
1. **Fix light serialization** - Investigate why skylight values reset to 15 after loading
   - Check `ChunkNBT.fromNBT()` and `recalculateBlockLight()`
   - Ensure loaded light data is not overwritten by initialization

2. **Fix cross-chunk attenuation** - Light should decrease by 1 when crossing chunk boundaries
   - Check `CrossChunkLightPropagator` implementation
   - Ensure attenuation is applied when propagating to neighboring chunks

3. **Implement deferred updates** - Track and apply light updates for unloaded chunks
   - Ensure `getDeferredUpdateCount()` properly tracks pending updates
   - Process deferred updates when chunks load

### Test Updates (False Positives)
1. **Update LightStorageTest.testArraySerialization()** - Change expected blockLight size from 2048 to 8192 bytes to reflect RGBI format

---

## Test Status Summary

| Test Class | Test Method | Status | Type |
|------------|------------|--------|------|
| ChunkLightSerializationTest | testLightDataRoundTrip | ❌ | Real Bug |
| ChunkLightSerializationTest | testLightWithModifiedValues | ❌ | Real Bug |
| ChunkLightSerializationTest | testChunkPosition | ❌ | Real Bug |
| LightStorageTest | testArraySerialization | ⚠️ | False Positive |
| CrossChunkLightRemovalTest | testTorchJustInsideChunkBoundary | ❌ | Real Bug |
| CrossChunkLightTest | testBlockLightCrossesChunkBoundary | ❌ | Real Bug |
| CrossChunkLightTest | testDeferredUpdateWhenChunkNotLoaded | ❌ | Real Bug |
| CrossChunkLightTest | testLightCrossesMultipleChunkBoundaries | ❌ | Real Bug |
| CrossChunkLightTest | testNoSeamsAtChunkBoundary | ❌ | Real Bug |
| CrossChunkVertexLightTest | testBlockLightCrossesChunkBoundaryForVertices | ❌ | Real Bug |
| CrossChunkVertexLightTest | testVertexLightSamplingConsistency | ❌ | Real Bug |

**Real Bugs**: 10 out of 11 tests  
**False Positives**: 1 out of 11 tests (LightStorageTest - expected behavior change)
