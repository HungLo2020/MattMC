# Step 3: BlockLight Propagation (BFS Add/Remove)

## Implementation Summary

Implemented full blockLight propagation using BFS queues with automatic updates when blocks are placed or broken.

## Core Algorithm

### BFS Light Propagation

The propagator uses **breadth-first search (BFS)** to efficiently propagate light:

```
Add Light Algorithm:
1. Set source position to emission level
2. Enqueue source with its light level
3. While queue not empty:
   - Dequeue node
   - For each of 6 neighbors (±X, ±Y, ±Z):
     - Check bounds and opacity
     - Calculate new light = current - 1 (attenuation)
     - If new light > neighbor's current light:
       - Update neighbor
       - Enqueue neighbor
```

### Light Removal with Re-propagation

When a light source is removed, we need to:
1. Remove all light that came from that source
2. Re-propagate from remaining sources to fill gaps

```
Remove Light Algorithm:
1. Clear light at source position
2. BFS removal phase:
   - Enqueue source with its old light level
   - For each neighbor:
     - If neighbor light < source light: remove it (was from this source)
     - If neighbor light >= source light: add to boundary queue (from other source)
3. Re-propagation phase:
   - BFS from all boundary nodes
   - Propagate their light to fill removed areas
```

## Implementation Details

### LightPropagator.java

Main class with three key methods:

**`addBlockLight(chunk, x, y, z, emission)`**
- Propagates light from an emissive block
- Uses BFS with attenuation
- Respects opacity blocking

**`removeBlockLight(chunk, x, y, z)`**
- Removes light from a position
- Two-phase: removal then re-propagation
- Handles multiple overlapping light sources

**`updateBlockLight(chunk, x, y, z, newBlock, oldBlock)`**
- Called automatically from `LevelChunk.setBlock()`
- Handles both adding and removing light
- Compares old vs new emission/opacity

### Integration Hook

Modified `LevelChunk.setBlock()`:
```java
Block oldBlock = blocks[x][y][z];
blocks[x][y][z] = block;

// Update light if emission or opacity changed
if (oldBlock.getLightEmission() != block.getLightEmission() || 
    oldBlock.getOpacity() != block.getOpacity()) {
    LightPropagator propagator = new LightPropagator();
    propagator.updateBlockLight(this, x, y, z, block, oldBlock);
}
```

## Testing

### Unit Tests (LightPropagatorTest.java)

Eight comprehensive tests:

1. **testAddBlockLightFromTorch** - Basic propagation with attenuation
2. **testLightBlockedByOpaqueBlock** - Opacity blocks propagation
3. **testRemoveBlockLight** - Light removal clears all affected areas
4. **testMultipleLightSources** - Overlapping light from multiple sources
5. **testRemoveOneOfMultipleSources** - Re-propagation from remaining sources
6. **testUpdateBlockLight** - Automatic updates via setBlock hook
7. **testLightAttenuation** - Verify value-1 attenuation per block
8. **testVerticalPropagation** - Light spreads up and down

Run: `./gradlew test --tests "LightPropagatorTest"`

### Manual Test (BlockLightPropagationTest.java)

Four test scenarios:

1. **Place a Torch** - Shows light spreading from source
2. **Remove the Torch** - Shows light being removed
3. **Multiple Torches** - Shows overlapping light sources
4. **Persistence** - Verifies blockLight saves and loads

Run: `./gradlew runDebugTest`

**Sample Output:**
```
Placing torch at (8, 64, 8)...
Torch light emission: 14

BlockLight values around torch:
  Source (8,64,8): 14
  1 block away +X: 13
  2 blocks away +X: 12
  1 block up: 13
  Diagonal: 12

Verification:
  ✓ Source should have full emission
  ✓ Neighbor should have emission - 1
  ✓ Distance 2 should have emission - 2

Removing torch...
BlockLight values after removal:
  Former source: 0
  Former neighbor: 0
  ✓ Light removed from source
  ✓ Light removed from neighbors
```

## Limitations (Current Implementation)

1. **Chunk-local only**: Propagation stops at chunk boundaries
   - Cross-chunk propagation would require neighbor chunk access
   - Future improvement: pass neighbor chunks to propagator

2. **Synchronous**: Light updates happen immediately during setBlock()
   - Could cause lag if many blocks change at once
   - Future improvement: batch updates or async propagation

3. **No diagonal optimization**: Each diagonal step costs 2 (one horizontal, one vertical)
   - This is actually correct for flood-fill lighting
   - Minecraft uses the same approach

## Performance

- **Time Complexity**: O(R³) where R is light radius (typically 14 blocks for torch)
- **Space Complexity**: O(R³) for the BFS queue
- **Per Block Change**: ~100-1000 blocks updated for a torch (depending on surroundings)

## Future Enhancements (Not Included)

- Cross-chunk light propagation
- Async/batched light updates
- Light update queue for multiple simultaneous changes
- Sky light propagation (similar algorithm)
- Light scheduling for smooth updates over multiple frames
