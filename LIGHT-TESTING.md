# Light Data Structures - Testing and Verification

This document describes how to verify the light data implementation.

## Files Changed

1. **LightStorage.java** - New class for storing per-section light data
   - Stores skyLight and blockLight as nibble arrays (0-15)
   - Each 16x16x16 section uses 2048 bytes per light type
   - Default values: skyLight=15, blockLight=0

2. **LevelChunk.java** - Integrated light storage
   - Added `lightSections` array (24 sections for 384-block height)
   - Added getSkyLight/setSkyLight/getBlockLight/setBlockLight methods
   - Added getLightStorage/setLightStorage for serialization

3. **ChunkNBT.java** - Light serialization/deserialization
   - Saves SkyLight and BlockLight byte arrays in NBT format
   - Only saves sections with blocks or non-default light values
   - Properly restores light data on chunk load

4. **Block.java** - Added getOpacity() method
   - Returns 15 for solid blocks (fully opaque)
   - Returns 0 for air/transparent blocks
   - getLightEmission() already existed

## Running Tests

### Unit Tests

Run the light storage tests:
```bash
./gradlew test --tests "LightStorageTest"
./gradlew test --tests "ChunkLightSerializationTest"
```

### Manual Persistence Test

To run the manual test that creates a world, saves it, and verifies persistence:

```bash
# Option 1: Using gradle (if Java versions match)
./gradlew runDebugTest

# Option 2: Run directly with java
./gradlew compileTestJava
java -cp "build/classes/java/test:build/classes/java/main:$(./gradlew -q printTestClasspath)" \
     mattmc.world.level.lighting.LightPersistenceTest
```

The manual test will:
1. Create a temporary world
2. Set blocks and custom light values at specific positions
3. Save the world
4. Load the world in a new Level instance
5. Verify all light values persisted correctly
6. Print results to console

### Expected Output

The manual test should output:
```
=== Light Data Persistence Test ===

--- Phase 1: Creating and saving world ---
Setting light values:
  (5, 64, 5): sky=12, block=3
  (10, 100, 10): sky=7, block=9
  (3, 150, 7): sky=2, block=14 (torch)
  (15, 180, 8): sky=15, block=0 (defaults)
World saved successfully.

--- Phase 2: Loading and verifying world ---
Verifying light values:
  Block at (5, 64, 5): mattmc:stone
  Block at (10, 100, 10): mattmc:dirt
  Block at (3, 150, 7): mattmc:torch
  (5, 64, 5): sky=12, block=3
  (10, 100, 10): sky=7, block=9
  (3, 150, 7): sky=2, block=14
  (15, 180, 8): sky=15, block=0 (defaults)

Verifying Block API:
  STONE emission: 0
  STONE opacity: 15
  TORCH emission: 14
  TORCH opacity: 0
  AIR opacity: 0

=== TEST PASSED ===
All light values persisted correctly!
```

## Manual Testing in Game

To manually test in the actual game:

1. Build and run the game:
   ```bash
   ./gradlew installDist
   ./build/install/MattMC/bin/MattMC
   ```

2. Create or load a world

3. Place some blocks (the world should save automatically when you exit)

4. Exit and reload the world

5. The blocks should still be there with their associated light data

## API Usage

### Setting Light Values

```java
LevelChunk chunk = level.getChunk(x, z);

// Set sky light (0-15) at chunk-local coordinates
chunk.setSkyLight(x, y, z, 12);

// Set block light (0-15) at chunk-local coordinates  
chunk.setBlockLight(x, y, z, 8);
```

### Reading Light Values

```java
int skyLight = chunk.getSkyLight(x, y, z);   // 0-15
int blockLight = chunk.getBlockLight(x, y, z); // 0-15
```

### Block Properties

```java
int emission = block.getLightEmission(); // 0-15 (how much light it emits)
int opacity = block.getOpacity();        // 0-15 (how much light it blocks)
```

## Implementation Notes

- Light values are stored as nibbles (4 bits) to save space
- Each byte stores two light values (upper and lower nibbles)
- Section indices are calculated as: `sectionIndex = chunkY / 16`
- Light storage is automatically created for all 24 sections
- Empty sections with default light values are not saved to disk
- Serialization format is compatible with future lighting algorithms
