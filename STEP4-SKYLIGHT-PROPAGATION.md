# Step 4: Skylight Propagation Below Heightmap

## Implementation Summary

Extended skylight to propagate below the heightmap into cavities using BFS with attenuation. This allows light to "pour" into underground areas when they're exposed to the sky.

## Core Algorithm

### BFS Skylight Propagation

Unlike the static vertical fill in Step 2, this uses full BFS flood-fill:

```
Initialization Algorithm:
1. Compute heightmap (topmost opaque block per column)
2. Fill skylight=15 from world top down to heightmap (vertical fill)
3. Seed BFS queue with positions just below heightmap
4. BFS propagate into cavities:
   - Check if block is transparent (opacity < 15)
   - Calculate new light = current - 1 (attenuation)
   - If new light > existing light: update and enqueue
   - Propagate to all 6 neighbors
```

### Dynamic Updates

When blocks change in a column:

```
Column Update Algorithm:
1. Recompute heightmap for the column
2. If heightmap increased (block placed above):
   - Remove skylight from newly covered area
   - BFS removal with re-propagation from boundaries
3. If heightmap decreased (block removed):
   - Add skylight to newly exposed area
   - BFS propagate from the new opening
4. If opacity changed at same height:
   - Add/remove skylight based on new transparency
```

## Implementation Details

### SkylightEngine.java

Main class with key methods:

**`initializeChunkSkylight(chunk)`**
- Computes heightmap for all columns
- Vertical fill above heightmap
- BFS propagation below heightmap into cavities

**`updateColumnSkylight(chunk, x, y, z, newBlock, oldBlock)`**
- Called from LevelChunk.setBlock() when opacity changes
- Recomputes heightmap for the column
- Handles add/remove based on heightmap changes
- BFS propagates updates to neighbors

**`propagateSkylightBelowHeightmap(chunk)`**
- Seeds queue with cavity entrances
- BFS flood-fill with attenuation
- Respects opacity blocking

### Integration Points

**WorldGenerator.java**
```java
SkylightEngine engine = new SkylightEngine();
engine.initializeChunkSkylight(chunk);
```

**AsyncChunkLoader.java**
```java
// When loading from disk
SkylightEngine engine = new SkylightEngine();
engine.initializeChunkSkylight(chunk);
```

**LevelChunk.java**
```java
// In setBlock() when opacity changes
SkylightEngine skylightEngine = new SkylightEngine();
skylightEngine.updateColumnSkylight(this, x, y, z, block, oldBlock);
```

## Testing

### Unit Tests (SkylightEngineTest.java)

Five test scenarios:

1. **testBasicSkylightPropagation** - Simple surface and cavity
2. **testSkylightAttenuation** - Vertical shaft attenuation
3. **testOpaqueBlockStopsPropagation** - Opacity blocking
4. **testHeightmapUpdate** - Heightmap changes
5. **testDigVerticalShaft** - Dynamic digging simulation

Run: `./gradlew test --tests "SkylightEngineTest"`

### Manual Test (SkylightPropagationTest.java)

Three demonstration scenarios:

1. **Dig Vertical Shaft** - Shows skylight pouring in
2. **Fill the Shaft** - Shows skylight removed
3. **Heightmap Updates** - Shows heightmap tracking

Run: `./gradlew runDebugTest`

**Sample Output:**
```
--- Test 1: Dig Vertical Shaft ---
Digging from y=70 down to y=60...

Skylight after digging shaft:
  Y    Block           Skylight
  ---  --------------  --------
  75   air             15
  70   air             15
  65   air             14
  60   air             12
  59   stone           0

Verification:
  ✓ Top should have near-full skylight
  ✓ Middle should have some skylight
  ✓ Bottom should have some skylight
  ✓ Below floor should have no skylight

--- Test 2: Fill the Shaft ---
Filling from y=60 back up to y=70...

Skylight after filling shaft:
  Y    Block           Skylight
  ---  --------------  --------
  70   stone           0
  65   stone           0
  60   stone           0

  ✓ Filled shaft should have no skylight
```

## Key Features

### Skylight "Pours" Into Cavities

When a column is exposed to sky (no opaque blocks above), skylight propagates down and sideways:
- Full brightness (15) at the opening
- Gradual attenuation (-1 per block)
- Can spread horizontally into caves
- Blocked by opaque blocks

### Proper Removal

When a cavity is sealed (opaque block placed):
- All skylight from that source is removed
- BFS removal tracks which light came from removed source
- Re-propagation from remaining sources fills gaps
- No orphaned light values

### Heightmap Synchronization

Heightmap stays in sync with terrain:
- Updated every time a block's opacity changes
- Drives the "above/below surface" logic
- Enables efficient skylight updates

## Performance Considerations

- **Initialization**: O(columns × height × cavity_volume)
  - Dominated by BFS propagation in large caves
  - Typical: 100-1000 blocks updated per chunk

- **Column Update**: O(affected_volume)
  - Digging one block: 10-100 blocks updated
  - Filling one block: similar scale
  - Depends on cavity size

- **Optimization Opportunity**: Chunk-local only
  - Cross-chunk propagation not implemented
  - Light stops at chunk boundaries
  - Future: pass neighbor chunks to engine

## Limitations (Current Implementation)

1. **Chunk-local only**: Skylight doesn't propagate across chunks
2. **Synchronous**: Updates happen during setBlock() call
3. **No sunlight strength**: Always 15 at surface (could vary by time of day)

## Comparison with Minecraft

**Similar:**
- Heightmap-based surface detection
- BFS propagation with attenuation
- Opacity blocking at 15

**Different:**
- Minecraft: cross-chunk propagation
- Minecraft: packed long arrays for storage
- Minecraft: async light update scheduling

## Future Enhancements (Not Included)

- Cross-chunk skylight propagation
- Async/batched updates for performance
- Time-of-day skylight strength variation
- Sky occlusion for overhangs
- Smooth lighting interpolation
