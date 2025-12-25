# Map System Implementation Plan for MattMC

## Executive Summary

This document outlines a comprehensive plan for implementing an integrated world map system into MattMC (Minecraft 1.21.10). Unlike external mods like JourneyMap or Xaero's World Map, this implementation will be integrated directly into the base game source code, allowing for deep integration with vanilla systems while keeping the feature set focused and performant.

**Core Features:**
- Full-screen world map accessible via M key
- Progressive map exploration (reveals as player explores)
- Waypoint creation with teleportation support
- Multi-dimension support (Overworld, Nether, End)
- Persistent storage across game sessions
- No minimap or cave layer complexity

---

## Table of Contents

1. [Research Summary](#research-summary)
2. [Existing Infrastructure](#existing-infrastructure)
3. [Architecture Overview](#architecture-overview)
4. [Component Design](#component-design)
5. [Implementation Phases](#implementation-phases)
6. [Technical Specifications](#technical-specifications)
7. [Performance Considerations](#performance-considerations)
8. [Integration Points](#integration-points)
9. [Data Persistence](#data-persistence)
10. [User Interface Design](#user-interface-design)
11. [Testing Strategy](#testing-strategy)
12. [Future Extensibility](#future-extensibility)

---

## Research Summary

### JourneyMap Architecture

Based on extensive research, JourneyMap implements the following key systems:

**Data Collection:**
- Hooks into Minecraft client events to intercept player movement and world updates
- Collects chunk and block data including terrain, biomes, entities, and light levels
- Uses configurable rendering delays to minimize performance impact
- Scans chunks around player location, filtering relevant data

**Rendering & Storage:**
- Dynamically renders visible areas onto texture buffers
- Supports multiple layers per dimension (surface/day, moonlight/night)
- Saves rendered map segments as PNG files locally
- Enables persistent world mapping between sessions

**Map Display:**
- In-game UI overlays for map display
- Full map can open in browser via local web server (localhost:8080)
- Interactive navigation with layer control
- Waypoint system with dimension-specific separation

**Performance Optimizations:**
- Delayed rendering cycles
- Selective chunk scanning
- Multiple configuration options for tuning
- Asynchronous rendering to avoid blocking main thread

### Xaero's World Map Architecture

**Client-Side Storage:**
- Map data saved locally in `.minecraft/XaeroWorldMap` or config folders
- Each world/server has corresponding directory with data files
- Stores rendered top-down map tiles and exploration footprint
- Does NOT save raw block data, only visual representation

**Rendering Method:**
- Google Maps-like interface using Minecraft's graphical pipeline
- Pan, zoom, click functionality
- Terrain shading and coloring
- Incremental rendering as player enters new chunks
- Re-renders tiles when resource packs change

**Data Structure:**
- Tile-based caching system
- Separate maps for different dimensions
- PNG export capability for sharing
- Synchronized waypoints between minimap and world map mods

### Common Patterns in Map Mods

**Chunk Rendering:**
- Divide world into sections (chunks, typically 16×16 blocks)
- Render only visible chunks based on camera position
- Batch rendering commands for GPU efficiency
- Region-based memory structure (e.g., 8×4×8 chunk sections per region)

**Tile-Based Storage:**
- World modeled as layers of tiles indexed through 2D arrays
- Tiles reference definitions (images and metadata)
- Smart caching - load chunks on demand
- PNG tiles cached to avoid redundant computation

**Dimension Support:**
- Nether ↔ Overworld coordinate conversion: 1:8 ratio
  - Overworld → Nether: divide X/Z by 8
  - Nether → Overworld: multiply X/Z by 8
- End coordinates map 1:1 with Overworld
- Separate map data per dimension

---

## Existing Infrastructure

MattMC already has substantial waypoint infrastructure that can be leveraged:

### Current Waypoint System

**Client-Side:**
- `net.minecraft.client.waypoints.ClientWaypointManager`
  - Manages tracked waypoints using `ConcurrentHashMap`
  - Tracks, updates, and untracks waypoints
  - Provides distance-sorted waypoint iteration

**Server-Side:**
- `net.minecraft.server.waypoints.ServerWaypointManager`
  - Tracks waypoint transmitters
  - Manages player-waypoint connections
  - Handles waypoint broadcasting to players

**Core Classes:**
- `net.minecraft.world.waypoints.Waypoint` - Interface with icon support
- `net.minecraft.world.waypoints.TrackedWaypoint` - Tracked waypoint implementation
- `net.minecraft.world.waypoints.WaypointTransmitter` - Transmission logic
- `net.minecraft.world.waypoints.WaypointStyleAsset` - Visual styling

### Existing Rendering Infrastructure

**Map Rendering:**
- `net.minecraft.client.renderer.MapRenderer` (100 lines)
- `net.minecraft.client.renderer.state.MapRenderState`
- `net.minecraft.client.resources.MapTextureManager`
- `net.minecraft.client.color.item.MapColor`

**Key Mapping:**
- `net.minecraft.client.KeyMapping` - Keybinding system
- `net.minecraft.client.ToggleKeyMapping` - Toggle key support
- `net.minecraft.client.KeyboardHandler` - Input handling

**GUI System:**
- Extensive screen system in `net.minecraft.client.gui.screens`
- `Screen` base class for custom UIs
- Popup and overlay support


---

## Architecture Overview

### High-Level Design

```
┌─────────────────────────────────────────────────────────────┐
│                    MattMC Map System                         │
├─────────────────────────────────────────────────────────────┤
│                                                              │
│  ┌───────────────┐      ┌──────────────┐                    │
│  │  Key Handler  │─────>│  Map Screen  │                    │
│  │  (M Key)      │      │  (GUI)       │                    │
│  └───────────────┘      └──────┬───────┘                    │
│                                 │                            │
│                                 v                            │
│  ┌─────────────────────────────────────────────┐            │
│  │         Map Renderer & Viewport             │            │
│  │  - Pan/Zoom Controls                        │            │
│  │  - Chunk Tile Rendering                     │            │
│  │  - Waypoint Overlay                         │            │
│  └──────────────┬──────────────────────────────┘            │
│                 │                                            │
│                 v                                            │
│  ┌─────────────────────────────────────────────┐            │
│  │         Map Data Manager                    │            │
│  │  - Chunk Data Collection                    │            │
│  │  - Tile Cache Management                    │            │
│  │  - Dimension Handling                       │            │
│  └──────────────┬──────────────────────────────┘            │
│                 │                                            │
│                 v                                            │
│  ┌─────────────────────────────────────────────┐            │
│  │         Waypoint Manager (Enhanced)         │            │
│  │  - Create/Delete Waypoints                  │            │
│  │  - Teleport Commands                        │            │
│  │  - Persistent Storage                       │            │
│  └─────────────────────────────────────────────┘            │
│                                                              │
└─────────────────────────────────────────────────────────────┘
```

### Data Flow

1. **Exploration Phase:**
   ```
   Player Movement → Chunk Load Event → Extract Chunk Data → 
   Generate Tile → Cache Tile → Store to Disk
   ```

2. **Map Viewing Phase:**
   ```
   M Key Press → Open Map Screen → Load Cached Tiles →
   Render Viewport → Display Waypoints → Handle User Input
   ```

3. **Waypoint Creation:**
   ```
   User Click → Create Waypoint → Store in Manager →
   Persist to Disk → Render on Map
   ```

4. **Teleportation:**
   ```
   User Select Waypoint → Validate Permissions →
   Execute Teleport Command → Update Player Position
   ```

---

## Component Design

### 1. Map Data Manager

**Class:** `net.minecraft.client.map.WorldMapDataManager`

**Responsibilities:**
- Track explored chunks per dimension
- Generate map tiles from chunk data
- Manage tile cache (memory and disk)
- Handle dimension switching
- Coordinate conversion for Nether/End

**Key Methods:**
```java
public class WorldMapDataManager {
    // Chunk tracking
    void markChunkExplored(ResourceKey<Level> dimension, ChunkPos pos);
    boolean isChunkExplored(ResourceKey<Level> dimension, ChunkPos pos);
    
    // Tile generation
    MapTile generateTile(ResourceKey<Level> dimension, int tileX, int tileZ);
    void invalidateTile(ResourceKey<Level> dimension, int tileX, int tileZ);
    
    // Cache management
    MapTile getCachedTile(ResourceKey<Level> dimension, int tileX, int tileZ);
    void cacheTile(MapTile tile);
    void clearCache();
    
    // Dimension handling
    void switchDimension(ResourceKey<Level> dimension);
    BlockPos convertCoordinates(ResourceKey<Level> fromDim, ResourceKey<Level> toDim, BlockPos pos);
    
    // Persistence
    void save();
    void load();
}
```

**Data Structure:**
- `ConcurrentHashMap<ResourceKey<Level>, Set<ChunkPos>>` for explored chunks
- `LRU Cache<TileKey, MapTile>` for in-memory tile cache (max 256-512 tiles)
- File-based storage: `saves/<world>/map_data/<dimension>/<region>/tile_<x>_<z>.png`

### 2. Map Tile System

**Class:** `net.minecraft.client.map.MapTile`

**Tile Size:** 256×256 pixels (represents 16×16 chunks = 256×256 blocks)

**Structure:**
```java
public class MapTile {
    private final ResourceKey<Level> dimension;
    private final int tileX;
    private final int tileZ;
    private final BufferedImage image;
    private final long lastModified;
    private boolean dirty;
    
    // Rendering
    void renderFromChunks(List<LevelChunk> chunks);
    void renderPixel(int x, int z, BlockState topBlock, Biome biome);
    
    // Caching
    void saveToDisk(Path directory);
    static MapTile loadFromDisk(Path file);
    
    // Utilities
    boolean contains(BlockPos pos);
    int getPixelX(BlockPos pos);
    int getPixelZ(BlockPos pos);
}
```

**Rendering Strategy:**
- Iterate through chunk sections from top to bottom
- Find highest solid block (non-air, non-transparent)
- Apply color based on block type and biome
- Apply shading based on height differences (like vanilla maps)
- Cache rendered result as PNG

**Color Mapping:**
- Reuse vanilla `MapColor` system
- Biome-aware grass/foliage coloring
- Height-based shading (darker for lower, lighter for higher)

### 3. Map Screen GUI

**Class:** `net.minecraft.client.gui.screens.MapScreen`

**Layout:**
```
┌────────────────────────────────────────────────────────┐
│  [X] World Map              [Dimension: Overworld ▼]   │
├────────────────────────────────────────────────────────┤
│                                                         │
│                   MAP VIEWPORT                          │
│                   [Waypoints visible as icons]          │
│                   [Player position marked]              │
│                                                         │
├────────────────────────────────────────────────────────┤
│  Zoom: [- 100% +]  |  Waypoints  |  Teleport  |  Help  │
└────────────────────────────────────────────────────────┘
```

**Implementation:**
```java
public class MapScreen extends Screen {
    private WorldMapDataManager dataManager;
    private MapViewport viewport;
    private MapWaypointRenderer waypointRenderer;
    
    // Viewport state
    private double centerX, centerZ;
    private double zoomLevel = 1.0;
    private ResourceKey<Level> currentDimension;
    
    // Input handling
    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, 
                                double dragX, double dragY);
    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, 
                                 double scrollX, double scrollY);
    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button);
    
    // Rendering
    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, 
                       float partialTick);
}
```

### 4. Enhanced Waypoint Manager

**Class:** `net.minecraft.client.map.PlayerWaypointManager`

**Features:**
- User-created waypoints (not just tracked entities)
- Persistent storage per-world
- Teleportation integration
- Multi-dimension support

```java
public class PlayerWaypointManager {
    private Map<ResourceKey<Level>, List<PlayerWaypoint>> waypointsByDimension;
    private final Path saveDirectory;
    
    // CRUD operations
    PlayerWaypoint createWaypoint(String name, ResourceKey<Level> dimension, 
                                  BlockPos pos, int color);
    void deleteWaypoint(UUID waypointId);
    void renameWaypoint(UUID waypointId, String newName);
    
    // Queries
    List<PlayerWaypoint> getWaypointsInDimension(ResourceKey<Level> dimension);
    PlayerWaypoint getNearestWaypoint(ResourceKey<Level> dimension, BlockPos pos);
    
    // Teleportation
    boolean canTeleportTo(PlayerWaypoint waypoint);
    void teleportToWaypoint(PlayerWaypoint waypoint);
    
    // Persistence
    void saveWaypoints();
    void loadWaypoints();
}
```

---

## Implementation Phases

### Phase 1: Core Infrastructure (Week 1-2)

**Goals:**
- Set up basic map data structures
- Implement chunk tracking
- Create simple tile generation

**Deliverables:**
- `WorldMapDataManager` with chunk tracking
- `MapTile` with basic rendering
- `ChunkDataCollector` hooks
- File-based persistence for explored chunks

### Phase 2: Map Screen GUI (Week 3-4)

**Goals:**
- Create map viewing interface
- Implement pan and zoom
- Display map tiles

**Deliverables:**
- `MapScreen` with viewport
- `MapViewport` with coordinate transforms
- `MapKeyHandler` for M key
- Basic tile rendering in GUI

### Phase 3: Waypoint System (Week 5-6)

**Goals:**
- Enhance waypoint management
- Add waypoint UI in map screen
- Implement waypoint persistence

**Deliverables:**
- `PlayerWaypointManager`
- `PlayerWaypoint` data class
- Waypoint creation UI
- Waypoint rendering on map

### Phase 4: Teleportation (Week 7)

**Goals:**
- Implement teleport commands
- Add teleport UI in map
- Handle cross-dimension teleport

**Deliverables:**
- `/waypoint tp` command
- Teleport button in waypoint menu
- Dimension switching logic
- Permission checks

### Phase 5: Multi-Dimension Support (Week 8)

**Goals:**
- Separate map data per dimension
- Implement dimension switcher
- Handle coordinate conversion

**Deliverables:**
- Dimension dropdown in map UI
- Per-dimension tile storage
- Nether coordinate conversion (1:8)
- End dimension support

### Phase 6: Polish & Optimization (Week 9-10)

**Goals:**
- Optimize rendering performance
- Improve tile caching
- Add quality-of-life features

**Deliverables:**
- Async tile generation
- LRU cache optimization
- Search/filter waypoints
- Help screen

---

## Technical Specifications

### Tile System Specifications

**Tile Coverage:**
- 1 tile = 256×256 pixels
- 1 tile = 16×16 chunks = 256×256 blocks
- At 100% zoom: 1 pixel = 1 block
- At 200% zoom: 2 pixels = 1 block
- At 50% zoom: 1 pixel = 2 blocks

**Tile Naming Convention:**
```
saves/<world>/map_data/<dimension>/region_<rx>_<rz>/tile_<tx>_<tz>.png
```

**Example:**
```
saves/MyWorld/map_data/overworld/region_0_0/tile_0_0.png
saves/MyWorld/map_data/the_nether/region_-1_0/tile_15_7.png
```

### Waypoint File Format

**File:** `saves/<world>/map_data/waypoints.dat`

**Format:** NBT CompoundTag
```nbt
{
    "version": 1,
    "waypoints": [
        {
            "id": "uuid-string",
            "name": "Home Base",
            "dimension": "minecraft:overworld",
            "x": 123,
            "y": 64,
            "z": 456,
            "color": 0xFF0000,
            "icon": "home",
            "created": 1234567890L,
            "enabled": true
        }
    ]
}
```

### Coordinate Conversion

**Nether ↔ Overworld:**
```java
public static BlockPos overworldToNether(BlockPos pos) {
    return new BlockPos(pos.getX() / 8, pos.getY(), pos.getZ() / 8);
}

public static BlockPos netherToOverworld(BlockPos pos) {
    return new BlockPos(pos.getX() * 8, pos.getY(), pos.getZ() * 8);
}
```

---

## Performance Considerations

### Rendering Performance

**Strategies:**
1. **Async Tile Generation:**
   - Generate tiles in background thread
   - Don't block main render thread
   - Use `CompletableFuture` for async operations

2. **Viewport Culling:**
   - Only render visible tiles
   - Pre-calculate visible tile range
   - Skip offscreen tiles

3. **Tile Batching:**
   - Batch tile draws into single GPU call
   - Use texture atlas for multiple tiles
   - Minimize state changes

**Target Performance:**
- 60 FPS at 100% zoom
- < 100ms to open map screen
- < 50ms to pan/zoom
- < 500ms to generate new tile

### Memory Optimization

**Strategies:**
1. **Lazy Loading:**
   - Load tiles only when needed
   - Unload tiles outside viewport + margin
   - Keep only current dimension in memory

2. **Compressed Storage:**
   - Use PNG compression for disk storage
   - Decompress only when needed

**Memory Budget:**
- Tile cache: 64MB max
- Chunk tracking: ~1MB per dimension
- Waypoint data: < 1MB
- Total: < 100MB overhead

---

## Integration Points

### 1. Chunk Loading System

**Hook:** `ClientLevel.onChunkLoaded()`

**Action:**
- Mark chunk as explored
- Queue tile regeneration if needed

**Code Location:**
- `net.minecraft.client.multiplayer.ClientLevel`

### 2. Key Binding System

**Hook:** `Minecraft.handleKeybinds()`

**Action:**
- Check if M key pressed
- Open map screen

**Code Location:**
- `net.minecraft.client.Minecraft`

### 3. Dimension Change

**Hook:** `ClientLevel.onDimensionChange()`

**Action:**
- Switch map to new dimension
- Load dimension-specific tiles

### 4. Command Registration

**Hook:** `Commands.register()`

**Action:**
- Register `/waypoint` command

**Code Location:**
- `net.minecraft.commands.Commands`

---

## Data Persistence

### Save Strategy

**When to Save:**
1. World save event
2. Player disconnect
3. Map screen close
4. Periodic auto-save (every 5 minutes)

**What to Save:**
1. Explored chunks per dimension
2. Modified map tiles
3. All waypoints
4. Map settings

```java
public class MapDataPersistence {
    public void save() {
        saveExploredChunks();
        saveTiles();
        saveWaypoints();
    }
    
    public void load() {
        loadExploredChunks();
        loadWaypoints();
    }
}
```

---

## User Interface Design

### Map Screen Layout

**Dimensions:**
- Full screen (minus header/footer)
- Header: 30px (title + controls)
- Footer: 40px (info + buttons)
- Viewport: Remaining space

**Controls:**
- Zoom: [−] 50% | 100% | 200% | 400% [+]
- Center on player button
- Coordinate display: (X, Z)

### Waypoint UI

**Waypoint Menu (Right-click):**
```
┌──────────────────────┐
│ Home Base            │
├──────────────────────┤
│ > Teleport Here      │
│ > Rename             │
│ > Change Color       │
│ > Delete             │
└──────────────────────┘
```

**Create Waypoint Dialog:**
```
┌─────────────────────────────┐
│  Create Waypoint             │
├─────────────────────────────┤
│  Name: [_______________]     │
│  Color: [🔴🟢🔵🟡]         │
│  Icon: [🏠🏰⚔️🎁]          │
│                              │
│  [Cancel]  [Create]          │
└─────────────────────────────┘
```

---

## Testing Strategy

### Unit Tests

- Map Data Manager chunk tracking
- Coordinate conversion
- Tile rendering
- Waypoint CRUD operations

### Integration Tests

- M key opens map
- Pan and zoom functionality
- Waypoint creation
- Dimension switching
- Teleportation

### Performance Tests

- Tile rendering benchmarks
- Memory usage with large maps
- Viewport with 1000+ tiles

---

## Future Extensibility

### Potential Enhancements

1. **Shared Waypoints:**
   - Multiplayer waypoint sharing
   - Team/party waypoints

2. **Biome Overlay:**
   - Toggle biome boundaries
   - Color-code by biome

3. **Structure Markers:**
   - Auto-mark villages, temples
   - Custom structure icons

4. **Path Drawing:**
   - Draw routes on map
   - Distance measurements

5. **Map Export:**
   - Export to PNG image
   - Share map with others

6. **Minimap (Optional):**
   - Small overlay in corner
   - Configurable size/position

---

## Advantages of Integrated Implementation

### Compared to External Mods

1. **Direct Source Access:**
   - No reflection or bytecode manipulation
   - Direct access to chunk data
   - Can modify vanilla systems

2. **Better Performance:**
   - No mod loader overhead
   - Direct GPU access
   - Lower-level optimizations

3. **Tighter Integration:**
   - Seamless UI integration
   - Consistent styling
   - No mod conflicts

4. **Simpler Architecture:**
   - No multi-layer abstractions
   - Direct event handling
   - Cleaner code

5. **Reduced Scope:**
   - Focus on essential features
   - No feature bloat
   - Easier maintenance

---

## Implementation Checklist

### Core Systems
- [ ] Create `WorldMapDataManager` class
- [ ] Implement chunk tracking system
- [ ] Create `MapTile` class with rendering
- [ ] Implement tile cache (LRU)
- [ ] Set up file-based persistence
- [ ] Create `ChunkDataCollector` hooks

### GUI Components
- [ ] Create `MapScreen` class
- [ ] Implement `MapViewport` with pan/zoom
- [ ] Add dimension selector dropdown
- [ ] Create waypoint icon renderer
- [ ] Add coordinate display

### Key Binding
- [ ] Register M key binding
- [ ] Implement key handler
- [ ] Hook into input system

### Waypoint System
- [ ] Create `PlayerWaypointManager`
- [ ] Implement `PlayerWaypoint` class
- [ ] Add waypoint creation UI
- [ ] Add waypoint edit UI
- [ ] Implement waypoint persistence

### Teleportation
- [ ] Create `/waypoint` command
- [ ] Implement teleport logic
- [ ] Add permission checks
- [ ] Handle dimension switching

### Multi-Dimension
- [ ] Implement dimension data separation
- [ ] Add coordinate conversion
- [ ] Test Nether coordinate mapping
- [ ] Test End coordinate mapping

### Polish
- [ ] Optimize rendering performance
- [ ] Add loading indicators
- [ ] Implement error handling
- [ ] Create user documentation

### Testing
- [ ] Unit test all core classes
- [ ] Integration test map screen
- [ ] Performance test with large maps
- [ ] User acceptance testing

---

## Conclusion

This plan provides a comprehensive roadmap for implementing an integrated world map system in MattMC. The design leverages existing infrastructure (waypoint system, rendering pipeline, GUI framework) while adding focused map functionality.

**Key Success Factors:**
1. Modular design with clear separation of concerns
2. Efficient tile-based rendering and caching
3. Seamless integration with vanilla systems
4. Focus on core features without bloat
5. Extensive testing and optimization

**Timeline:** 10 weeks for full implementation

**Estimated Complexity:**
- Core map system: Medium
- GUI implementation: Medium
- Waypoint enhancement: Low (builds on existing)
- Teleportation: Low
- Multi-dimension: Medium
- Polish & optimization: High

The integrated nature of this implementation (directly in source code) provides significant advantages over external mods, while the focused feature set keeps complexity manageable.

### Research Sources

This plan is based on extensive research into:
- JourneyMap mod architecture and implementation
- Xaero's World Map and Minimap systems
- Minecraft chunk rendering and tile-based storage
- Dimension coordinate conversion mechanisms
- Client-side keybinding and GUI systems
- Waypoint management and teleportation patterns

The combination of external mod insights with MattMC's existing infrastructure creates a solid foundation for a performant, integrated map system.

---

*Document Version: 1.0*  
*Created: December 2024*  
*For: MattMC (Minecraft 1.21.10)*
