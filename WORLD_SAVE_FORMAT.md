# MattMC World Save Format

MattMC now uses a Minecraft Java Edition-inspired world save format with region files and NBT (Named Binary Tag) data.

## Directory Structure

```
saves/
└── [WorldName]/
    ├── level.dat          # World metadata and player data (NBT, gzip compressed)
    ├── level.dat_old      # Backup of previous level.dat
    └── region/
        ├── r.-1.-1.mca    # Region files (Anvil format)
        ├── r.-1.0.mca
        ├── r.0.-1.mca
        └── r.0.0.mca
```

## File Formats

### level.dat

The `level.dat` file contains world metadata and player information in NBT format, compressed with gzip.

**Structure:**
```
{
  Data: {
    LevelName: "World"           # String: World name
    LastPlayed: 1699112345678    # Long: Last played timestamp (milliseconds)
    SpawnX: 0                    # Int: World spawn X
    SpawnY: 64                   # Int: World spawn Y
    SpawnZ: 0                    # Int: World spawn Z
    GameType: 0                  # Int: Game mode (0=survival, 1=creative)
    hardcore: 0                  # Byte: Is hardcore mode
    Difficulty: 2                # Byte: Difficulty level
    Version: {
      Name: "MattMC 1.0"         # String: Version name
      Id: 1                      # Int: Version ID
    }
    Player: {
      Pos: [8.5, 65.0, 8.5]      # List<Double>: Player position
      Rotation: [45.0, 0.0]      # List<Float>: [yaw, pitch]
    }
  }
}
```

### Region Files (.mca)

Region files follow the Anvil format used in Minecraft Java Edition:

- **File naming:** `r.x.z.mca` where x and z are region coordinates
- **Region size:** 32×32 chunks (512×512 blocks horizontally)
- **Compression:** Zlib (deflate) for chunk data
- **Format:** Binary with 8KB header

**Header Structure:**
- **Locations table (4KB):** 1024 entries × 4 bytes
  - 3 bytes: sector offset
  - 1 byte: sector count
- **Timestamps table (4KB):** 1024 entries × 4 bytes
  - Unix timestamp (seconds) for each chunk

**Chunk Data:**
- **Length:** 4 bytes (int) - length of chunk data + compression type
- **Compression type:** 1 byte (2 = zlib/deflate)
- **Compressed NBT data:** Variable length

### Chunk NBT Structure

Each chunk is stored as NBT with the following structure:

```
{
  xPos: 0                        # Int: Chunk X coordinate
  zPos: 0                        # Int: Chunk Z coordinate
  DataVersion: 1                 # Int: Data format version
  Status: "full"                 # String: Generation status
  sections: [                    # List<Compound>: Chunk sections
    {
      Y: -4                      # Byte: Section Y (world Y / 16)
      Palette: [                 # List<Compound>: Block palette
        {
          Name: "mattmc:grass_block"
        },
        {
          Name: "mattmc:dirt"
        },
        {
          Name: "mattmc:stone"
        }
      ]
      BlockStates: [...]         # LongArray: Palette indices for each block
    }
  ]
}
```

## Coordinate Systems

### Region Coordinates
- Region X = floor(chunkX / 32)
- Region Z = floor(chunkZ / 32)
- Each region file contains 32×32 chunks = 1024 chunks total

### Chunk Coordinates
- Chunk X/Z: World coordinates divided by 16
- Local chunk index in region: localX + localZ × 32
- Range: 0-31 in each direction within a region

### Block Coordinates
- World Y range: -64 to 319 (384 blocks total)
- Chunk-local Y: 0 to 383
- Conversion: worldY = chunkY + Chunk.MIN_Y

## Implementation Details

### NBT Utility (MattMC.nbt.NBTUtil)

A lightweight NBT implementation supporting:
- Tag types: Byte, Int, Long, Float, Double, String, ByteArray, LongArray, List, Compound
- Compression: Gzip (for level.dat) and Deflate (for chunks)
- Java-native Map/List based API for simplicity

### RegionFile Class

Manages individual `.mca` files with:
- Header caching for performance
- Lazy loading of chunks
- Simple append-based allocation (no defragmentation)
- Automatic sector alignment (4KB sectors)

### ChunkNBT Class

Converts between Chunk objects and NBT format:
- Palette-based block storage for efficiency
- Empty section optimization (skips air-only sections)
- Section-based vertical chunking (16-block-tall sections)

### LevelData Class

Manages world metadata:
- Player position and rotation
- Spawn point
- Game settings
- World name and timestamp

## Comparison to Minecraft

| Feature | MattMC | Minecraft Java |
|---------|---------|----------------|
| Region files | ✓ (.mca) | ✓ (.mca) |
| Region size | 32×32 chunks | 32×32 chunks |
| Chunk height | 384 blocks | 384 blocks (1.18+) |
| Y range | -64 to 319 | -64 to 319 (1.18+) |
| level.dat | ✓ NBT | ✓ NBT |
| Compression | Gzip + Deflate | Gzip + Deflate |
| Block palette | ✓ Per-section | ✓ Per-section |
| Entity storage | ✗ | ✓ |
| Biomes | ✗ | ✓ |
| Structures | ✗ | ✓ |
| POI data | ✗ | ✓ |

## Usage Example

```java
// Save a world
World world = new World();
// ... generate/modify world ...
WorldSaveManager.saveWorld(world, "MyWorld", 
    playerX, playerY, playerZ, 
    playerYaw, playerPitch);

// Load a world
WorldSaveManager.WorldLoadResult result = WorldSaveManager.loadWorld("MyWorld");
World loadedWorld = result.world;
WorldSaveManager.WorldMetadata metadata = result.metadata;

// Access player position
float playerX = metadata.playerX;
float playerY = metadata.playerY;
float playerZ = metadata.playerZ;
```

## Benefits

1. **Efficient Storage:** Region files group 1024 chunks, reducing file system overhead
2. **Fast Loading:** Only loads chunks that are actually present
3. **Scalable:** Supports effectively infinite worlds
4. **Compatible Format:** Uses industry-standard NBT and similar structure to Minecraft
5. **Clean Organization:** Clear separation of world data, metadata, and regions

## Future Enhancements

- [ ] Entity storage in chunks
- [ ] Biome data
- [ ] Structure data
- [ ] Region file defragmentation
- [ ] Compressed block palettes (bit-packed)
- [ ] Asynchronous chunk loading/saving
- [ ] World conversion tools
