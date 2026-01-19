# World Generation System

## Overview

Minecraft's world generation system creates infinite, procedurally-generated terrain using noise functions, biomes, features, and structures. The system is highly data-driven, with most configuration done through JSON files and codecs.

## Architecture

```
┌─────────────────────────────────────────┐
│     Chunk Generation Pipeline           │
└─────────────────┬───────────────────────┘
                  │
         ┌────────┴────────┐
         ▼                 ▼
   ┌──────────┐      ┌──────────┐
   │  Terrain │      │  Biomes  │
   │  Shape   │      │          │
   └────┬─────┘      └────┬─────┘
        │                 │
        └────────┬────────┘
                 ▼
         ┌───────────────┐
         │   Features    │
         │ (trees, ores) │
         └───────┬───────┘
                 ▼
         ┌───────────────┐
         │  Structures   │
         │ (villages)    │
         └───────┬───────┘
                 ▼
         ┌───────────────┐
         │   Carving     │
         │  (caves)      │
         └───────┬───────┘
                 ▼
            [Complete
             Chunk]
```

## Core Components

### 1. Chunk Generator

**Location**: `net.minecraft.world.level.chunk.ChunkGenerator`

Base class for all world generation strategies.

**Types**:
- **NoiseBasedChunkGenerator**: Standard overworld/nether
- **FlatLevelSource**: Superflat worlds
- **DebugLevelSource**: Block grid for testing

**Generation Stages**:
1. **Structure Starts**: Place structure bounding boxes
2. **Biome Placement**: Assign biomes to columns
3. **Noise Generation**: Generate terrain shape
4. **Surface Building**: Apply surface blocks (grass, sand)
5. **Carving**: Caves and ravines
6. **Feature Placement**: Trees, ores, lakes, etc.
7. **Spawn Calculation**: Initial spawn position

### 2. Noise-Based Generation

**Location**: `net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator`

The primary world generator using noise functions.

**Key Concepts**:

#### Density Functions

**Location**: `net.minecraft.world.level.levelgen.DensityFunction`

Mathematical functions that compute values at any 3D coordinate.

**Purpose**: Determine where blocks should be placed
- Positive values → Air
- Negative values → Solid blocks
- Zero → Surface

**Types**:
- **Noise**: Perlin/Simplex noise
- **YClampedGradient**: Height-based gradients
- **Blend**: Interpolate between functions
- **Math Operations**: Add, multiply, min, max, etc.
- **Spline**: Cubic spline curves
- **Cache**: Memoized functions

**Common Functions** (`DensityFunctions`):
- `shiftedNoise()`: Warped noise
- `continents()`: Large-scale land/ocean
- `erosion()`: Terrain roughness
- `ridges()`: Mountain ridges
- `depth()`: Underground depth
- `caves()`: Cave openings

#### Noise Router

**Location**: `net.minecraft.world.level.levelgen.NoiseRouter`

Combines density functions for complete generation.

**Components**:
- `finalDensity()`: Main terrain shape
- `veinToggle()`: Ore vein placement
- `veinRidged()`: Ore vein shape
- `veinGap()`: Ore vein gaps
- `barrierNoise()`: Bedrock randomization
- `fluidLevelFloodedness()`: Water level
- `fluidLevelSpread()`: Water spread
- `temperature()`: Climate zones
- `vegetation()`: Plant distribution
- `continents()`: Landmass scale
- `erosion()`: Terrain weathering
- `depth()`: Y-level depth
- `ridges()`: Ridge formation

#### Noise Generation Settings

**Location**: `net.minecraft.world.level.levelgen.NoiseGeneratorSettings`

Configures noise-based generation parameters.

**Settings**:
- Sea level (Y=63 typically)
- Disable mob generation
- Aquifer settings (underground water)
- Ore vein settings
- Legacy random source flag
- Noise configuration

**Default Presets**:
- `overworld`: Standard generation
- `nether`: Netherrack terrain
- `end`: End island generation
- `amplified`: Extreme terrain (mountains)
- `large_biomes`: 4× biome scale

### 3. Biome System

**Location**: `net.minecraft.world.level.biome`

Biomes define climate, appearance, and content of regions.

#### Biome Class

**Location**: `net.minecraft.world.level.biome.Biome`

Defines all biome properties.

**Properties**:
- **Climate**: Temperature, humidity, downfall (rain/snow)
- **Effects**: Sky color, fog color, water color, grass color
- **Music**: Ambient music and sounds
- **Particles**: Ambient particles (ash in basalt deltas)
- **Spawning**: Mob spawn rates by category
- **Features**: Placed features (trees, flowers)
- **Carvers**: Cave/ravine generation

**Climate Settings**:
- Temperature: Hot (desert) to cold (tundra)
- Humidity: Dry (desert) to wet (jungle)
- Downfall: None, rain, or snow
- Temperature modifier: Frozen peaks

#### Biome Sources

**Location**: `net.minecraft.world.level.biome.BiomeSource`

Determines biome placement strategy.

**Types**:

**MultiNoiseBiomeSource**: 
- Modern system (1.18+)
- Uses noise parameters for biome selection
- 6D climate space (temperature, humidity, continents, erosion, depth, weirdness)
- Smooth biome transitions

**FixedBiomeSource**:
- Single biome everywhere
- Debug/testing

**CheckerboardColumnBiomeSource**:
- Checkerboard pattern
- Debug/testing

**TheEndBiomeSource**:
- End dimension biomes
- Center island vs outer islands

#### Multi-Noise Biomes

Climate parameters determine biome:
- **Temperature**: -2.0 (cold) to 2.0 (hot)
- **Humidity**: -2.0 (dry) to 2.0 (wet)
- **Continents**: -1.2 (ocean) to 1.0 (inland)
- **Erosion**: -1.0 (peaks) to 1.0 (valleys)
- **Depth**: 0.0 (surface) to 1.0 (underground)
- **Weirdness**: -1.0 to 1.0 (unusual terrain)

**Biome Selection**:
1. Calculate climate parameters at position
2. Find closest matching biome in climate space
3. Euclidean distance in 6D space

### 4. Features

**Location**: `net.minecraft.world.level.levelgen.feature`

Decorative elements placed after terrain generation.

#### Feature Types

**Vegetation**:
- `TreeFeature`: Oak, birch, spruce, etc.
- `HugeMushroomFeature`: Giant mushrooms
- `RandomPatchFeature`: Grass, flowers
- `SeagrassFeature`: Underwater plants
- `KelpFeature`: Kelp columns

**Terrain**:
- `LakeFeature`: Water/lava lakes
- `SpringFeature`: Water/lava springs
- `IcebergFeature`: Ice mountains
- `DiskFeature`: Clay/sand disks
- `DeltaFeature`: Basalt deltas

**Underground**:
- `OreFeature`: Ore veins
- `GeodeFeature`: Amethyst geodes
- `UnderwaterMagmaFeature`: Magma bubbles
- `DripstoneClusterFeature`: Dripstone caves

**Decorative**:
- `BlockPileFeature`: Random block piles
- `SimpleBlockFeature`: Single blocks
- `RandomBooleanSelectorFeature`: Random choice

#### Placed Features

**Location**: `net.minecraft.world.level.levelgen.placement`

Features with placement rules.

**Placement Modifiers**:
- `CountPlacement`: How many times to place
- `RarityFilter`: Placement probability
- `InSquarePlacement`: Random XZ within chunk
- `HeightmapPlacement`: On terrain surface
- `BiomeFilter`: Only in certain biomes
- `BlockPredicateFilter`: Check block conditions
- `SurfaceWaterDepthFilter`: Check water depth

**Placement Chain**:
```
Feature → PlacementContext → Placement Modifiers → Final Position
```

### 5. Structures

**Location**: `net.minecraft.world.level.levelgen.structure`

Large generated structures (villages, temples, etc.).

#### Structure Types

**Overworld**:
- `VillageStructure`: Villages with buildings
- `PillagerOutpostStructure`: Pillager towers
- `MineshaftStructure`: Abandoned mines
- `StrongholdStructure`: End portal fortress
- `BuriedTreasureStructure`: Treasure maps
- `ShipwreckStructure`: Shipwrecks
- `OceanMonumentStructure`: Ocean monuments
- `WoodlandMansionStructure`: Mansions
- `DesertPyramidStructure`: Desert temples
- `JunglePyramidStructure`: Jungle temples
- `SwampHutStructure`: Witch huts
- `IglooStructure`: Igloos
- `RuinedPortalStructure`: Broken portals
- `AncientCityStructure`: Deep dark cities
- `TrailRuinsStructure`: Archaeological sites

**Nether**:
- `NetherFortressStructure`: Fortresses
- `BastionRemnantStructure`: Piglin bastions

**End**:
- `EndCityStructure`: End cities with ships

#### Structure Generation

**Process**:
1. **Structure Check**: Determine if structure should spawn
   - Use chunk position and seed
   - Check spacing and separation
   - Biome validation
2. **Structure Start**: Create structure pieces
   - Load template pools (jigsaw)
   - Assemble pieces with random selection
3. **Structure Placement**: Write blocks to world
   - Process references (jigsaw blocks)
   - Spawn loot chests
   - Spawn entities (villagers, etc.)

#### Jigsaw System

Modern template-based structure system.

**Components**:
- **Structure Templates**: NBT building pieces
- **Template Pools**: Collections of pieces
- **Jigsaw Blocks**: Connection points
- **Target Pools**: What can connect

**Example - Village**:
1. Start with town center template
2. Jigsaw blocks reference "village/streets"
3. Randomly select street template
4. Continue until max depth or no connections

### 6. Carvers

**Location**: `net.minecraft.world.level.levelgen.carver`

Create caves and ravines by removing blocks.

**Types**:
- `CaveCarver`: Cave tunnels
- `CanyonCarver`: Ravines
- `NetherCaveCarver`: Nether caves

**Carving Process**:
1. Generate random path through chunk
2. Carve tunnel/cavern along path
3. Variable width and height
4. Check for aquifers (water)

**Noise Caves** (1.18+):
- Use 3D noise instead of paths
- More natural cave shapes
- "Cheese caves" (large caverns)
- "Spaghetti caves" (long tunnels)
- "Noodle caves" (thin tunnels)

### 7. Aquifers

**Location**: `net.minecraft.world.level.levelgen.Aquifer`

Underground water/lava system.

**Types**:
- Water aquifers (below sea level)
- Lava aquifers (deep underground)
- Air (caves)

**Aquifer Logic**:
- Noise-based placement
- Smooth transitions
- Respect biome settings
- Create water-filled caves

### 8. Surface Rules

**Location**: `net.minecraft.world.level.levelgen.SurfaceRules`

Determine surface blocks (grass, sand, stone, etc.).

**Rule Types**:
- `SequenceRule`: Try rules in order
- `ConditionRule`: If condition, apply rule
- `BlockRule`: Place specific block
- `BiomeRule`: Based on biome

**Conditions**:
- Above/below certain Y level
- Steep slopes
- Near water
- Biome check
- Noise threshold

**Example**:
- If grassland biome → grass block
- If desert biome → sand
- If steep slope → stone
- If underwater → gravel

### 9. Chunk Status

**Location**: `net.minecraft.world.level.chunk.status.ChunkStatus`

Tracks chunk generation progress.

**Statuses** (in order):
1. `EMPTY`: Chunk created
2. `STRUCTURE_STARTS`: Structure positions decided
3. `STRUCTURE_REFERENCES`: Adjacent structures noted
4. `BIOMES`: Biomes assigned
5. `NOISE`: Terrain height generated
6. `SURFACE`: Surface blocks placed
7. `CARVERS`: Caves carved
8. `FEATURES`: Features placed
9. `INITIALIZE_LIGHT`: Lighting calculated
10. `LIGHT`: Lighting propagated
11. `SPAWN`: Mob spawning prepared
12. `FULL`: Completely generated

**Chunk Dependencies**:
- Each status requires adjacent chunks at previous status
- Ensures smooth generation across boundaries

### 10. World Presets

**Location**: `net.minecraft.world.level.levelgen.presets.WorldPresets`

Predefined world generation settings.

**Presets**:
- `NORMAL`: Standard overworld + nether + end
- `FLAT`: Superflat customizable
- `LARGE_BIOMES`: 4× larger biomes
- `AMPLIFIED`: Extreme mountains
- `SINGLE_BIOME_SURFACE`: One biome
- `DEBUG_ALL_BLOCK_STATES`: All blocks grid

## Noise Types

**Perlin Noise**:
- Smooth, continuous noise
- Used for terrain features

**Simplex Noise**:
- Improved Perlin with better gradients
- Less directional artifacts

**Worley Noise** (Cell Noise):
- Based on distance to random points
- Used for some features

## Data-Driven Configuration

**World Generation Data** (`data/minecraft/worldgen/`):
- `biome/`: Biome definitions
- `configured_feature/`: Feature configurations
- `placed_feature/`: Feature placement rules
- `structure/`: Structure definitions
- `structure_set/`: Structure placement rules
- `density_function/`: Custom density functions
- `noise/`: Noise parameters
- `noise_settings/`: Complete generation settings

**JSON Example** (simplified biome):
```json
{
  "temperature": 0.8,
  "downfall": 0.4,
  "effects": {
    "sky_color": 7907327,
    "fog_color": 12638463,
    "water_color": 4159204
  },
  "spawners": {
    "monster": [...],
    "creature": [...]
  },
  "spawn_costs": {},
  "features": [...]
}
```

## Performance Considerations

**Optimization Techniques**:
1. **Caching**: Reuse noise calculations
2. **Batching**: Generate multiple chunks together
3. **Lazy Evaluation**: Generate only when needed
4. **Blending**: Smooth old/new chunk boundaries
5. **Threading**: Generate on background threads

**Generation Speed**:
- Noise calculation is expensive
- Structure placement is slow
- Feature placement varies
- Lighting calculation is costly

## Key Files

- `ChunkGenerator.java`: Base generator (800+ lines)
- `NoiseBasedChunkGenerator.java`: Noise generator (700+ lines)
- `NoiseChunk.java`: Chunk noise context (900+ lines)
- `DensityFunctions.java`: Density function implementations (1,800+ lines)
- `NoiseRouter.java`: Noise function router
- `Biome.java`: Biome definition
- `Feature.java`: Feature base class
- `Structure.java`: Structure base class

## Related Systems

- [Data System](DATA-SYSTEM.md) - World generation data files
- [Server System](SERVER-SYSTEM.md) - Chunk generation pipeline
- [Entity System](ENTITY-SYSTEM.md) - Mob spawning
