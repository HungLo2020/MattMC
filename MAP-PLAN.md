# Map System Implementation Plan for MattMC

## Executive Summary

This document outlines a comprehensive plan for implementing a standalone, integrated world map system into MattMC (Minecraft 1.21.10). Similar to how JourneyMap and Xaero's World Map operate as independent systems, this implementation will be a self-contained map mod built directly into the base game source code, without relying on vanilla map or waypoint systems.

**Core Features:**
- Full-screen world map accessible via M key
- Progressive map exploration (reveals as player explores)
- Waypoint creation with teleportation support
- Multi-dimension support (Overworld, Nether, End)
- Persistent storage across game sessions
- No minimap or cave layer complexity

**Key Architectural Decision:**
This system will be **completely independent** from vanilla Minecraft's map items and waypoint systems. It will implement its own chunk scanning, tile rendering, waypoint management, and data persistence - similar to how external map mods operate, but with the advantage of direct source code integration.

---

## Table of Contents

1. [Research Summary](#research-summary)
2. [Architecture Overview](#architecture-overview)
3. [Component Design](#component-design)
4. [Implementation Phases](#implementation-phases)
5. [Technical Specifications](#technical-specifications)
6. [Performance Considerations](#performance-considerations)
7. [Integration Points](#integration-points)
8. [Data Persistence](#data-persistence)
9. [User Interface Design](#user-interface-design)
10. [Testing Strategy](#testing-strategy)
11. [Future Extensibility](#future-extensibility)

---

## Research Summary

### JourneyMap Architecture

JourneyMap operates as a completely independent mapping system:

**Data Collection:**
- Custom chunk scanner that directly accesses world data
- Independent from vanilla map rendering
- Hooks into client tick events to track player movement
- Asynchronously scans chunks in background threads
- Extracts block data, biome info, and height information directly from chunk sections

**Rendering Pipeline:**
- Custom tile renderer using OpenGL/LWJGL directly
- Generates 512×512 pixel map tiles from chunk data
- Applies custom color mapping for blocks (not using vanilla MapColor)
- Height-based shading algorithm (brighter for higher elevations)
- Biome-aware grass and water coloring

**Storage System:**
- Tiles stored as PNG files: `.minecraft/journeymap/data/mp/<world>/DIM<id>/<region>/`
- Separate directory structure per dimension
- Metadata files track explored regions
- Completely independent from vanilla saved game data

**Waypoint System:**
- Custom waypoint data structure (independent from vanilla)
- Stored in JSON files: `.minecraft/journeymap/data/sp/<world>/waypoints/`
- Dimension-aware waypoint management
- Teleportation via custom command system

### Xaero's World Map Architecture

Xaero's operates similarly as a standalone system:

**Rendering Approach:**
- Direct access to chunk data via client world
- Custom color palette for all Minecraft blocks
- Real-time tile generation as chunks load
- Tile cache in memory (LRU eviction)
- On-disk tile storage in `.minecraft/XaeroWorldMap/`

**Map Display:**
- Custom GUI screen (not extending vanilla screens)
- Direct OpenGL rendering for smooth panning/zooming
- Texture atlas for efficient tile rendering
- No dependency on vanilla rendering systems

**Waypoint Architecture:**
- Independent waypoint data model
- Stored separately from vanilla game data
- Custom serialization format
- Synchronized between minimap and world map

### Key Takeaways for MattMC Implementation

Both successful map mods share these architectural patterns:

1. **Independent Data Collection:**
   - Direct chunk data access (not through vanilla map systems)
   - Custom chunk scanners running asynchronously
   - Block and biome data extraction

2. **Standalone Rendering:**
   - Custom tile generation algorithms
   - Independent color mapping systems
   - Direct OpenGL/texture manipulation

3. **Separate Storage:**
   - Own directory structure outside vanilla saves
   - Custom file formats (PNG tiles + metadata)
   - Independent persistence layer

4. **Self-Contained Waypoints:**
   - Custom waypoint data structures
   - Separate from any vanilla systems
   - Own serialization and storage

This MattMC implementation will follow these same patterns, creating a completely independent map system that happens to be integrated into the source code for better performance and access.

---

## Architecture Overview

### High-Level Design

```
┌─────────────────────────────────────────────────────────────┐
│              MattMC Standalone Map System                    │
├─────────────────────────────────────────────────────────────┤
│                                                              │
│  ┌───────────────┐      ┌──────────────┐                    │
│  │  Map Key      │─────>│  Map GUI     │                    │
│  │  Handler      │      │  Screen      │                    │
│  │  (M Key)      │      │              │                    │
│  └───────────────┘      └──────┬───────┘                    │
│                                 │                            │
│                                 v                            │
│  ┌─────────────────────────────────────────────┐            │
│  │         Map Tile Renderer                   │            │
│  │  - Custom OpenGL tile rendering             │            │
│  │  - Pan/Zoom viewport management             │            │
│  │  - Texture atlas for tile batching          │            │
│  └──────────────┬──────────────────────────────┘            │
│                 │                                            │
│                 v                                            │
│  ┌─────────────────────────────────────────────┐            │
│  │         World Data Collector                │            │
│  │  - Asynchronous chunk scanner               │            │
│  │  - Direct block data extraction             │            │
│  │  - Biome and height information             │            │
│  └──────────────┬──────────────────────────────┘            │
│                 │                                            │
│                 v                                            │
│  ┌─────────────────────────────────────────────┐            │
│  │         Tile Cache & Storage                │            │
│  │  - In-memory LRU cache                      │            │
│  │  - PNG file persistence                     │            │
│  │  - Region-based organization                │            │
│  └─────────────────────────────────────────────┘            │
│                                                              │
│  ┌─────────────────────────────────────────────┐            │
│  │         Waypoint System (Independent)       │            │
│  │  - Custom waypoint data model               │            │
│  │  - JSON persistence                         │            │
│  │  - Teleport command integration             │            │
│  └─────────────────────────────────────────────┘            │
│                                                              │
└─────────────────────────────────────────────────────────────┘

Note: This system does NOT use:
  ❌ vanilla MapRenderer
  ❌ vanilla MapColor  
  ❌ vanilla map items
  ❌ vanilla waypoint systems (ClientWaypointManager, ServerWaypointManager)
  ❌ vanilla waypoint data structures
```

### Data Flow

1. **Chunk Exploration:**
   ```
   Chunk Load Event → Background Scanner → Extract Block Data →
   Custom Color Mapping → Generate Tile Image → Cache & Save
   ```

2. **Map Viewing:**
   ```
   M Key → Open Custom Map Screen → Load Tiles from Cache/Disk →
   Render with OpenGL → Display Waypoints → Handle Input
   ```

3. **Waypoint Management:**
   ```
   User Input → Create Waypoint Object → Store in JSON →
   Render on Map → Enable Teleportation
   ```

### Directory Structure

```
<minecraft_root>/
  └── mattmc_map/               # Completely separate from vanilla saves
      ├── <world_name>/
      │   ├── overworld/
      │   │   ├── regions/
      │   │   │   ├── r.0.0/
      │   │   │   │   ├── tile_0_0.png
      │   │   │   │   ├── tile_0_1.png
      │   │   │   │   └── ...
      │   │   │   └── r.0.1/
      │   │   └── explored.dat      # Explored chunk tracking
      │   ├── the_nether/
      │   ├── the_end/
      │   └── waypoints.json        # Custom waypoint storage
      └── config.json               # Map system configuration
```

---

## Component Design

### 1. World Data Collector

**Package:** `net.minecraft.client.mattmc.map.collector`

**Class:** `WorldChunkScanner`

**Purpose:** Asynchronously scan loaded chunks and extract renderable data

```java
public class WorldChunkScanner {
    private final ExecutorService scannerThread;
    private final ClientLevel world;
    private final Set<ChunkPos> scannedChunks;
    
    /**
     * Scan a chunk and extract all renderable data
     * Runs in background thread to avoid blocking client
     */
    public CompletableFuture<ChunkMapData> scanChunk(ChunkPos pos) {
        return CompletableFuture.supplyAsync(() -> {
            LevelChunk chunk = world.getChunk(pos.x, pos.z);
            return extractChunkData(chunk);
        }, scannerThread);
    }
    
    /**
     * Extract block colors, heights, and biome data from chunk
     * Does NOT use vanilla MapColor - custom color mapping
     */
    private ChunkMapData extractChunkData(LevelChunk chunk) {
        ChunkMapData data = new ChunkMapData(chunk.getPos());
        
        // Scan from top to bottom to find surface blocks
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
                
                // Find highest non-air block
                for (int y = world.getMaxBuildHeight() - 1; y >= world.getMinBuildHeight(); y--) {
                    pos.set(chunk.getPos().getMinBlockX() + x, y, chunk.getPos().getMinBlockZ() + z);
                    BlockState state = chunk.getBlockState(pos);
                    
                    if (!state.isAir()) {
                        // Custom color extraction (independent from vanilla)
                        int color = BlockColorMapper.getBlockColor(state, chunk.getBiome(pos));
                        int height = y;
                        
                        data.setPixel(x, z, color, height);
                        break;
                    }
                }
            }
        }
        
        return data;
    }
    
    /**
     * Register chunk load listener
     */
    public void onChunkLoad(ChunkEvent.Load event) {
        if (event.getLevel().isClientSide()) {
            ChunkPos pos = event.getChunk().getPos();
            if (!scannedChunks.contains(pos)) {
                scanChunk(pos).thenAccept(data -> {
                    TileGenerator.markTileDirty(pos);
                    scannedChunks.add(pos);
                });
            }
        }
    }
}
```

**Class:** `ChunkMapData`

```java
public class ChunkMapData {
    private final ChunkPos position;
    private final int[][] colors;      // 16x16 pixel colors
    private final int[][] heights;     // 16x16 heights for shading
    
    public ChunkMapData(ChunkPos pos) {
        this.position = pos;
        this.colors = new int[16][16];
        this.heights = new int[16][16];
    }
    
    public void setPixel(int x, int z, int color, int height) {
        colors[x][z] = color;
        heights[x][z] = height;
    }
    
    public int getColor(int x, int z) {
        return colors[x][z];
    }
    
    public int getHeight(int x, int z) {
        return heights[x][z];
    }
}
```

### 2. Custom Block Color Mapper

**Package:** `net.minecraft.client.mattmc.map.color`

**Class:** `BlockColorMapper`

**Purpose:** Independent color mapping system (does NOT use vanilla MapColor)

```java
public class BlockColorMapper {
    private static final Map<Block, Integer> BLOCK_COLORS = new HashMap<>();
    private static final Map<Block, BiFunction<BlockState, Holder<Biome>, Integer>> BIOME_COLORS = new HashMap<>();
    
    static {
        // Initialize custom color mappings for all blocks
        initializeBlockColors();
        initializeBiomeColors();
    }
    
    /**
     * Get color for a block, with biome awareness
     */
    public static int getBlockColor(BlockState state, Holder<Biome> biome) {
        Block block = state.getBlock();
        
        // Check biome-sensitive blocks first (grass, leaves, water)
        if (BIOME_COLORS.containsKey(block)) {
            return BIOME_COLORS.get(block).apply(state, biome);
        }
        
        // Standard block color
        return BLOCK_COLORS.getOrDefault(block, 0x808080); // Gray default
    }
    
    private static void initializeBlockColors() {
        // Stone variants
        BLOCK_COLORS.put(Blocks.STONE, 0x7F7F7F);
        BLOCK_COLORS.put(Blocks.DEEPSLATE, 0x4A4A4A);
        BLOCK_COLORS.put(Blocks.GRANITE, 0x926B5B);
        
        // Wood variants
        BLOCK_COLORS.put(Blocks.OAK_LOG, 0x6B5434);
        BLOCK_COLORS.put(Blocks.SPRUCE_LOG, 0x3D2712);
        BLOCK_COLORS.put(Blocks.BIRCH_LOG, 0xD7CB8D);
        
        // Terrain
        BLOCK_COLORS.put(Blocks.DIRT, 0x8B6340);
        BLOCK_COLORS.put(Blocks.SAND, 0xDDD799);
        BLOCK_COLORS.put(Blocks.GRAVEL, 0x7F7B7B);
        BLOCK_COLORS.put(Blocks.SNOW, 0xFFFEFE);
        BLOCK_COLORS.put(Blocks.ICE, 0x9DDBFF);
        
        // ... (continue for all blocks)
    }
    
    private static void initializeBiomeColors() {
        // Grass color varies by biome
        BIOME_COLORS.put(Blocks.GRASS_BLOCK, (state, biome) -> {
            return getBiomeGrassColor(biome);
        });
        
        // Leaves color varies by biome
        BIOME_COLORS.put(Blocks.OAK_LEAVES, (state, biome) -> {
            return getBiomeFoliageColor(biome);
        });
        
        // Water color varies by biome
        BIOME_COLORS.put(Blocks.WATER, (state, biome) -> {
            return getBiomeWaterColor(biome);
        });
    }
    
    private static int getBiomeGrassColor(Holder<Biome> biome) {
        // Custom biome grass coloring logic
        // Similar to vanilla but independent implementation
        int temperature = (int)(biome.value().getBaseTemperature() * 100);
        int humidity = (int)(biome.value().climateSettings.downfall() * 100);
        return interpolateGrassColor(temperature, humidity);
    }
}
```

### 3. Tile Generator and Cache

**Package:** `net.minecraft.client.mattmc.map.tile`

**Class:** `MapTileGenerator`

**Purpose:** Generate PNG tiles from chunk data

```java
public class MapTileGenerator {
    private static final int TILE_SIZE = 512;  // 512x512 pixels
    private static final int CHUNKS_PER_TILE = 32;  // 32x32 chunks
    
    /**
     * Generate a tile from chunk data
     * One tile covers 32x32 chunks = 512x512 blocks = 512x512 pixels at 1:1 scale
     */
    public static MapTile generateTile(ResourceKey<Level> dimension, int tileX, int tileZ, 
                                       Map<ChunkPos, ChunkMapData> chunkData) {
        BufferedImage image = new BufferedImage(TILE_SIZE, TILE_SIZE, BufferedImage.TYPE_INT_RGB);
        
        int baseChunkX = tileX * CHUNKS_PER_TILE;
        int baseChunkZ = tileZ * CHUNKS_PER_TILE;
        
        for (int cx = 0; cx < CHUNKS_PER_TILE; cx++) {
            for (int cz = 0; cz < CHUNKS_PER_TILE; cz++) {
                ChunkPos chunkPos = new ChunkPos(baseChunkX + cx, baseChunkZ + cz);
                ChunkMapData data = chunkData.get(chunkPos);
                
                if (data != null) {
                    renderChunkToTile(image, cx, cz, data);
                }
            }
        }
        
        return new MapTile(dimension, tileX, tileZ, image);
    }
    
    /**
     * Render a single chunk (16x16 blocks) onto the tile
     */
    private static void renderChunkToTile(BufferedImage tile, int chunkX, int chunkZ, 
                                          ChunkMapData data) {
        int pixelBaseX = chunkX * 16;
        int pixelBaseZ = chunkZ * 16;
        
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                int color = data.getColor(x, z);
                int height = data.getHeight(x, z);
                
                // Apply height-based shading
                color = applyHeightShading(color, height, getNeighborHeights(data, x, z));
                
                tile.setRGB(pixelBaseX + x, pixelBaseZ + z, color);
            }
        }
    }
    
    /**
     * Apply height-based shading (brighter for higher, darker for lower)
     */
    private static int applyHeightShading(int baseColor, int height, int[] neighborHeights) {
        // Calculate average height difference with neighbors
        int avgNeighborHeight = 0;
        for (int h : neighborHeights) avgNeighborHeight += h;
        avgNeighborHeight /= neighborHeights.length;
        
        int heightDiff = height - avgNeighborHeight;
        
        // Shade factor: +/- 20% based on height difference
        double shadeFactor = 1.0 + (heightDiff / 8.0) * 0.2;
        shadeFactor = Math.max(0.7, Math.min(1.3, shadeFactor));
        
        int r = (int)(((baseColor >> 16) & 0xFF) * shadeFactor);
        int g = (int)(((baseColor >> 8) & 0xFF) * shadeFactor);
        int b = (int)((baseColor & 0xFF) * shadeFactor);
        
        r = Math.max(0, Math.min(255, r));
        g = Math.max(0, Math.min(255, g));
        b = Math.max(0, Math.min(255, b));
        
        return (r << 16) | (g << 8) | b;
    }
}
```

**Class:** `MapTile`

```java
public class MapTile {
    private final ResourceKey<Level> dimension;
    private final int tileX, tileZ;
    private final BufferedImage image;
    private final long timestamp;
    private boolean dirty;
    
    public MapTile(ResourceKey<Level> dimension, int tileX, int tileZ, BufferedImage image) {
        this.dimension = dimension;
        this.tileX = tileX;
        this.tileZ = tileZ;
        this.image = image;
        this.timestamp = System.currentTimeMillis();
        this.dirty = true;
    }
    
    /**
     * Save tile to disk as PNG
     */
    public void saveToDisk(Path mapDirectory) {
        try {
            Path regionDir = getTileRegionPath(mapDirectory);
            Files.createDirectories(regionDir);
            
            Path tileFile = regionDir.resolve(String.format("tile_%d_%d.png", tileX, tileZ));
            ImageIO.write(image, "PNG", tileFile.toFile());
            
            dirty = false;
        } catch (IOException e) {
            // Log error
        }
    }
    
    /**
     * Load tile from disk
     */
    public static MapTile loadFromDisk(ResourceKey<Level> dimension, int tileX, int tileZ, 
                                       Path mapDirectory) {
        try {
            Path regionDir = getTileRegionPath(mapDirectory, dimension, tileX, tileZ);
            Path tileFile = regionDir.resolve(String.format("tile_%d_%d.png", tileX, tileZ));
            
            if (Files.exists(tileFile)) {
                BufferedImage image = ImageIO.read(tileFile.toFile());
                MapTile tile = new MapTile(dimension, tileX, tileZ, image);
                tile.dirty = false;
                return tile;
            }
        } catch (IOException e) {
            // Log error
        }
        return null;
    }
    
    private Path getTileRegionPath(Path mapDirectory) {
        int regionX = Math.floorDiv(tileX, 32);
        int regionZ = Math.floorDiv(tileZ, 32);
        return getTileRegionPath(mapDirectory, dimension, regionX, regionZ);
    }
    
    private static Path getTileRegionPath(Path mapDirectory, ResourceKey<Level> dimension, 
                                          int regionX, int regionZ) {
        String dimName = dimension.location().getPath().replace(":", "_");
        return mapDirectory.resolve(dimName)
                          .resolve("regions")
                          .resolve(String.format("r.%d.%d", regionX, regionZ));
    }
}
```

**Class:** `TileCache`

```java
public class TileCache {
    private final int maxSize;
    private final LinkedHashMap<TileKey, MapTile> cache;
    
    public TileCache(int maxSize) {
        this.maxSize = maxSize;
        this.cache = new LinkedHashMap<>(maxSize, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<TileKey, MapTile> eldest) {
                if (size() > maxSize) {
                    // Save to disk before evicting
                    if (eldest.getValue().isDirty()) {
                        eldest.getValue().saveToDisk(getMapDirectory());
                    }
                    return true;
                }
                return false;
            }
        };
    }
    
    public MapTile getTile(ResourceKey<Level> dimension, int tileX, int tileZ) {
        TileKey key = new TileKey(dimension, tileX, tileZ);
        return cache.get(key);
    }
    
    public void putTile(MapTile tile) {
        TileKey key = new TileKey(tile.getDimension(), tile.getTileX(), tile.getTileZ());
        cache.put(key, tile);
    }
}
```

### 4. Custom Map GUI Screen

**Package:** `net.minecraft.client.mattmc.map.gui`

**Class:** `WorldMapScreen`

**Purpose:** Full-screen map interface with custom rendering

```java
public class WorldMapScreen extends Screen {
    private final MapTileRenderer tileRenderer;
    private final WaypointRenderer waypointRenderer;
    private final MapDataManager dataManager;
    
    // Viewport state
    private double centerWorldX, centerWorldZ;
    private double zoomLevel = 1.0;  // 0.5, 1.0, 2.0, 4.0
    private ResourceKey<Level> currentDimension;
    
    // UI state
    private boolean dragging = false;
    private double dragStartX, dragStartZ;
    
    public WorldMapScreen() {
        super(Component.literal("World Map"));
        this.tileRenderer = new MapTileRenderer();
        this.waypointRenderer = new WaypointRenderer();
        this.dataManager = MapDataManager.getInstance();
        
        // Initialize to player's current position and dimension
        Player player = Minecraft.getInstance().player;
        if (player != null) {
            this.centerWorldX = player.getX();
            this.centerWorldZ = player.getZ();
            this.currentDimension = player.level().dimension();
        }
    }
    
    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // Render dark background
        graphics.fill(0, 0, this.width, this.height, 0xE0000000);
        
        // Calculate visible area
        MapViewport viewport = calculateViewport();
        
        // Render map tiles
        tileRenderer.render(graphics, viewport, currentDimension, dataManager.getTileCache());
        
        // Render waypoints
        waypointRenderer.render(graphics, viewport, dataManager.getWaypoints(currentDimension));
        
        // Render player position
        renderPlayerMarker(graphics, viewport);
        
        // Render UI elements
        renderHeader(graphics);
        renderFooter(graphics, mouseX, mouseY);
        
        super.render(graphics, mouseX, mouseY, partialTick);
    }
    
    private MapViewport calculateViewport() {
        return new MapViewport(
            centerWorldX, centerWorldZ,
            width, height - 70,  // Account for header/footer
            zoomLevel
        );
    }
    
    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, 
                                double dragX, double dragY) {
        if (button == 0) {  // Left click
            // Pan the map
            double worldDragX = dragX / zoomLevel;
            double worldDragZ = dragY / zoomLevel;
            
            centerWorldX -= worldDragX;
            centerWorldZ -= worldDragZ;
            
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }
    
    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        // Zoom in/out
        if (scrollY > 0) {
            zoomLevel = Math.min(4.0, zoomLevel * 1.2);
        } else {
            zoomLevel = Math.max(0.5, zoomLevel / 1.2);
        }
        return true;
    }
    
    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 1) {  // Right click
            // Check if clicked on waypoint
            MapViewport viewport = calculateViewport();
            BlockPos worldPos = viewport.screenToWorld((int)mouseX, (int)mouseY);
            
            Waypoint waypoint = waypointRenderer.getWaypointAtPosition(worldPos, 10);
            if (waypoint != null) {
                openWaypointMenu(waypoint);
                return true;
            }
        } else if (button == 0) {  // Left click
            // Create new waypoint
            MapViewport viewport = calculateViewport();
            BlockPos worldPos = viewport.screenToWorld((int)mouseX, (int)mouseY);
            openCreateWaypointDialog(worldPos);
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }
    
    private void renderHeader(GuiGraphics graphics) {
        // Title
        graphics.drawString(font, "World Map", 10, 10, 0xFFFFFF);
        
        // Dimension selector
        // ... render dimension dropdown ...
        
        // Close button
        // ... render X button ...
    }
    
    private void renderFooter(GuiGraphics graphics, int mouseX, int mouseY) {
        int footerY = height - 30;
        
        // Zoom controls
        // ... render zoom buttons ...
        
        // Coordinates
        MapViewport viewport = calculateViewport();
        BlockPos hoverPos = viewport.screenToWorld(mouseX, mouseY);
        String coords = String.format("X: %d, Z: %d", hoverPos.getX(), hoverPos.getZ());
        graphics.drawString(font, coords, width / 2 - 50, footerY, 0xFFFFFF);
    }
}
```

### 5. Independent Waypoint System

**Package:** `net.minecraft.client.mattmc.map.waypoint`

**Class:** `MapWaypoint`

**Purpose:** Custom waypoint data structure (independent from vanilla)

```java
public class MapWaypoint {
    private final UUID id;
    private String name;
    private final ResourceKey<Level> dimension;
    private final BlockPos position;
    private int color;  // RGB color
    private WaypointIcon icon;
    private final long createdTime;
    private boolean enabled;
    
    public MapWaypoint(String name, ResourceKey<Level> dimension, BlockPos position, 
                       int color, WaypointIcon icon) {
        this.id = UUID.randomUUID();
        this.name = name;
        this.dimension = dimension;
        this.position = position.immutable();
        this.color = color;
        this.icon = icon;
        this.createdTime = System.currentTimeMillis();
        this.enabled = true;
    }
    
    // Getters and setters
    
    /**
     * Serialize to JSON (not NBT)
     */
    public JsonObject toJson() {
        JsonObject json = new JsonObject();
        json.addProperty("id", id.toString());
        json.addProperty("name", name);
        json.addProperty("dimension", dimension.location().toString());
        json.addProperty("x", position.getX());
        json.addProperty("y", position.getY());
        json.addProperty("z", position.getZ());
        json.addProperty("color", color);
        json.addProperty("icon", icon.name());
        json.addProperty("created", createdTime);
        json.addProperty("enabled", enabled);
        return json;
    }
    
    /**
     * Deserialize from JSON
     */
    public static MapWaypoint fromJson(JsonObject json) {
        UUID id = UUID.fromString(json.get("id").getAsString());
        String name = json.get("name").getAsString();
        ResourceKey<Level> dimension = ResourceKey.create(
            Registries.DIMENSION,
            ResourceLocation.parse(json.get("dimension").getAsString())
        );
        BlockPos position = new BlockPos(
            json.get("x").getAsInt(),
            json.get("y").getAsInt(),
            json.get("z").getAsInt()
        );
        int color = json.get("color").getAsInt();
        WaypointIcon icon = WaypointIcon.valueOf(json.get("icon").getAsString());
        
        MapWaypoint waypoint = new MapWaypoint(name, dimension, position, color, icon);
        waypoint.id = id;
        waypoint.createdTime = json.get("created").getAsLong();
        waypoint.enabled = json.get("enabled").getAsBoolean();
        
        return waypoint;
    }
}

public enum WaypointIcon {
    HOME,      // House icon
    DEATH,     // Skull
    PORTAL,    // Purple swirl
    CAVE,      // Pickaxe
    VILLAGE,   // Bell
    CUSTOM     // Star (default)
}
```

**Class:** `WaypointManager`

```java
public class WaypointManager {
    private final Map<ResourceKey<Level>, List<MapWaypoint>> waypointsByDimension;
    private final Path waypointsFile;
    
    public WaypointManager(Path mapDirectory) {
        this.waypointsByDimension = new HashMap<>();
        this.waypointsFile = mapDirectory.resolve("waypoints.json");
        load();
    }
    
    public void addWaypoint(MapWaypoint waypoint) {
        waypointsByDimension
            .computeIfAbsent(waypoint.getDimension(), k -> new ArrayList<>())
            .add(waypoint);
        save();
    }
    
    public void removeWaypoint(UUID id) {
        for (List<MapWaypoint> waypoints : waypointsByDimension.values()) {
            waypoints.removeIf(w -> w.getId().equals(id));
        }
        save();
    }
    
    public List<MapWaypoint> getWaypoints(ResourceKey<Level> dimension) {
        return waypointsByDimension.getOrDefault(dimension, Collections.emptyList());
    }
    
    public void teleportToWaypoint(MapWaypoint waypoint) {
        Player player = Minecraft.getInstance().player;
        if (player == null) return;
        
        // Build teleport command
        String command = String.format(
            "/execute in %s run tp @s %d %d %d",
            waypoint.getDimension().location(),
            waypoint.getPosition().getX(),
            waypoint.getPosition().getY(),
            waypoint.getPosition().getZ()
        );
        
        // Execute command
        player.connection.sendCommand(command.substring(1));  // Remove leading /
    }
    
    /**
     * Save waypoints to JSON file
     */
    private void save() {
        try {
            JsonObject root = new JsonObject();
            JsonArray waypointsArray = new JsonArray();
            
            for (List<MapWaypoint> waypoints : waypointsByDimension.values()) {
                for (MapWaypoint waypoint : waypoints) {
                    waypointsArray.add(waypoint.toJson());
                }
            }
            
            root.addProperty("version", 1);
            root.add("waypoints", waypointsArray);
            
            Files.writeString(waypointsFile, new GsonBuilder().setPrettyPrinting().create().toJson(root));
        } catch (IOException e) {
            // Log error
        }
    }
    
    /**
     * Load waypoints from JSON file
     */
    private void load() {
        if (!Files.exists(waypointsFile)) return;
        
        try {
            String json = Files.readString(waypointsFile);
            JsonObject root = JsonParser.parseString(json).getAsJsonObject();
            JsonArray waypointsArray = root.getAsJsonArray("waypoints");
            
            waypointsByDimension.clear();
            
            for (JsonElement element : waypointsArray) {
                MapWaypoint waypoint = MapWaypoint.fromJson(element.getAsJsonObject());
                waypointsByDimension
                    .computeIfAbsent(waypoint.getDimension(), k -> new ArrayList<>())
                    .add(waypoint);
            }
        } catch (IOException e) {
            // Log error
        }
    }
}
```

---

## Implementation Phases

### Phase 1: Core Chunk Scanner (Week 1-2)

**Goals:**
- Implement independent chunk data collector
- Create custom block color mapping system
- Set up background scanning threads

**Deliverables:**
- `WorldChunkScanner` with async scanning
- `BlockColorMapper` with full block palette
- `ChunkMapData` structure
- Chunk load event hooks

**Tests:**
- Verify chunks are scanned on load
- Verify color mapping matches expected values
- Verify background thread doesn't block client

### Phase 2: Tile Generation & Storage (Week 3-4)

**Goals:**
- Implement tile generator from chunk data
- Create PNG persistence system
- Build in-memory tile cache

**Deliverables:**
- `MapTileGenerator` with height shading
- `MapTile` with PNG save/load
- `TileCache` with LRU eviction
- Directory structure creation

**Tests:**
- Generate tiles from test chunks
- Verify PNG files are created
- Verify cache eviction works
- Benchmark tile generation speed

### Phase 3: Map GUI & Rendering (Week 5-6)

**Goals:**
- Create custom map screen
- Implement viewport system
- Add pan and zoom

**Deliverables:**
- `WorldMapScreen` with custom rendering
- `MapViewport` coordinate transformation
- `MapTileRenderer` for OpenGL rendering
- M key binding

**Tests:**
- Open map with M key
- Pan around smoothly
- Zoom in/out
- Verify rendering performance (60 FPS)

### Phase 4: Waypoint System (Week 7-8)

**Goals:**
- Implement independent waypoint data
- Create waypoint UI
- Add persistence

**Deliverables:**
- `MapWaypoint` class
- `WaypointManager` with JSON storage
- `WaypointRenderer` for map display
- Waypoint creation dialog

**Tests:**
- Create waypoints
- Save and reload
- Render on map
- Edit/delete waypoints

### Phase 5: Teleportation & Commands (Week 9)

**Goals:**
- Add teleport functionality
- Implement waypoint commands
- Handle dimension switching

**Deliverables:**
- Teleport integration
- `/mapwaypoint` command
- Cross-dimension teleport
- Permission checks

**Tests:**
- Teleport within dimension
- Teleport across dimensions
- Test Nether coordinate conversion

### Phase 6: Polish & Optimization (Week 10)

**Goals:**
- Optimize performance
- Add UI polish
- Documentation

**Deliverables:**
- Performance optimizations
- Help screen
- Configuration options
- User guide

---

## Technical Specifications

### File Structure

```
<minecraft_root>/mattmc_map/
  └── <world_name>/
      ├── overworld/
      │   ├── regions/
      │   │   └── r.<rx>.<rz>/
      │   │       └── tile_<x>_<z>.png
      │   └── explored.dat
      ├── the_nether/
      ├── the_end/
      ├── waypoints.json
      └── config.json
```

### Tile Specifications

- **Size:** 512×512 pixels
- **Coverage:** 32×32 chunks = 512×512 blocks
- **Format:** PNG (compressed)
- **Scale:** 1 pixel = 1 block at 100% zoom

### Waypoint JSON Format

```json
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
      "color": 16711680,
      "icon": "HOME",
      "created": 1234567890000,
      "enabled": true
    }
  ]
}
```

### Coordinate Conversion

```java
// Nether to Overworld (multiply by 8)
public static BlockPos netherToOverworld(BlockPos nether) {
    return new BlockPos(nether.getX() * 8, nether.getY(), nether.getZ() * 8);
}

// Overworld to Nether (divide by 8)
public static BlockPos overworldToNether(BlockPos overworld) {
    return new BlockPos(overworld.getX() / 8, overworld.getY(), overworld.getZ() / 8);
}

// End uses 1:1 mapping with Overworld
```

---

## Performance Considerations

### Target Metrics

- **60 FPS** map rendering
- **< 100ms** map screen open time
- **< 50ms** pan/zoom response
- **< 2 seconds** tile generation per 32×32 chunk area
- **< 200MB** memory footprint

### Optimization Strategies

1. **Async Chunk Scanning:**
   - Dedicated thread pool for chunk scanning
   - Queue-based processing
   - Non-blocking client thread

2. **Tile Cache:**
   - LRU cache (256 tiles = ~512MB)
   - Aggressive eviction
   - Lazy loading

3. **Rendering:**
   - Texture atlas for tiles
   - Batch OpenGL calls
   - Viewport culling

4. **Storage:**
   - PNG compression
   - Region-based organization
   - Incremental saves

---

## Integration Points

### 1. Chunk Load Events

**Hook:** Forge/Fabric chunk load event

```java
@SubscribeEvent
public void onChunkLoad(ChunkEvent.Load event) {
    if (event.getLevel().isClientSide()) {
        WorldChunkScanner.getInstance().onChunkLoad(event);
    }
}
```

### 2. Key Binding

**Registration:**

```java
public static final KeyMapping OPEN_MAP = new KeyMapping(
    "key.mattmc.openmap",
    GLFW.GLFW_KEY_M,
    "key.categories.mattmc"
);
```

**Handler:**

```java
@SubscribeEvent
public void onKeyInput(InputEvent.Key event) {
    if (OPEN_MAP.consumeClick()) {
        Minecraft.getInstance().setScreen(new WorldMapScreen());
    }
}
```

### 3. Client Tick

**Purpose:** Update active scans, cache management

```java
@SubscribeEvent
public void onClientTick(TickEvent.ClientTickEvent event) {
    if (event.phase == TickEvent.Phase.END) {
        MapDataManager.getInstance().tick();
    }
}
```

---

## Conclusion

This implementation plan describes a **completely independent map system** that operates similarly to JourneyMap and Xaero's World Map, but integrated directly into MattMC's source code.

**Key Architectural Decisions:**

1. **No Vanilla Dependencies:**
   - Does NOT use vanilla MapRenderer
   - Does NOT use vanilla MapColor
   - Does NOT use vanilla waypoint systems
   - Custom chunk scanning and data extraction

2. **Standalone Components:**
   - Custom block color mapping
   - Independent tile generation
   - Separate file storage
   - Own waypoint data model

3. **Similar to Map Mods:**
   - Async chunk scanning
   - PNG tile storage
   - LRU caching
   - Custom rendering pipeline

**Advantages:**

- Complete control over all systems
- Optimized for MattMC's needs
- No vanilla limitations
- Better performance through source integration
- Focused feature set

**Timeline:** 10 weeks for full implementation

This system will operate as a true "map mod" built into the game, independent of all vanilla mapping and waypoint systems.

---

*Document Version: 2.0*  
*Updated: December 2024*  
*For: MattMC (Minecraft 1.21.10)*  
*Architecture: Independent Map System (No Vanilla Dependencies)*
