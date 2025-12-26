# Map System Implementation Plan for MattMC

## Executive Summary

This document outlines the architectural design and feature set for implementing a standalone world map system into MattMC (Minecraft 1.21.10). Similar to how JourneyMap and Xaero's World Map operate as independent systems, this implementation will be a self-contained map mod built directly into the base game source code, without relying on vanilla map or waypoint systems.

**Core Features:**
- Full-screen world map accessible via M key
- Progressive map exploration (reveals as player explores)
- Mouse wheel zoom with maximum detail at 1 pixel per block
- Waypoint creation with teleportation support via dedicated menu
- Multi-dimension support (Overworld, Nether, End)
- Persistent storage across game sessions (save-specific for single player, server-specific for multiplayer)
- No minimap or cave layer complexity

**Key Architectural Decision:**
This system will be **completely independent** from vanilla Minecraft's map items and waypoint systems. It will implement its own chunk scanning, tile rendering, waypoint management, and data persistence - similar to how external map mods operate, but with the advantage of direct source code integration.

---

## Table of Contents

1. [Research Summary](#research-summary)
2. [Core Architecture](#core-architecture)
3. [Feature Specifications](#feature-specifications)
4. [System Components](#system-components)
5. [User Interface Design](#user-interface-design)
6. [Data Persistence](#data-persistence)
7. [Performance Requirements](#performance-requirements)
8. [Implementation Phases](#implementation-phases)
9. [Integration Points](#integration-points)

---

## Research Summary

### JourneyMap Zoom System

**Zoom Implementation:**
- Mouse wheel controls zoom in/out
- Discrete zoom levels with smooth transitions
- Center-on-mouse zoom behavior (point under cursor stays fixed)
- Zoom range typically from 1 pixel = multiple blocks to 1 pixel = 1 block
- Configuration for custom zoom steps and ranges

**Detail Levels:**
- Maximum zoom: 1 pixel per block (highest detail)
- Minimum zoom: Up to hundreds of blocks per pixel (overview)
- Dynamic tile rendering adjusts to zoom level
- Smooth interpolation between zoom levels

### Xaero's World Map Zoom System

**Zoom Characteristics:**
- Google Maps-like zooming experience
- Mouse wheel for continuous zoom control
- Maximum detail: Pixel-perfect block representation
- Massive zoom-out: Can display up to ~760,000 blocks with extensions
- Standard display: ~30,000 blocks wide at full zoom-out

**Rendering Approach:**
- Dynamic scaling based on zoom level
- Tile-based rendering with multiple resolution levels
- Resource-intensive at extreme zoom-out levels
- Configurable zoom range in settings

### Key Takeaways for MattMC

**Zoom System Design:**
1. Mouse wheel as primary zoom control
2. Maximum detail level: 1 pixel = 1 block (highest zoom)
3. Smooth zoom transitions with center-on-mouse behavior
4. Configurable zoom range
5. Dynamic tile rendering based on zoom level

**Architecture Patterns:**
1. Independent chunk scanning system
2. Multi-resolution tile generation
3. Asynchronous background processing
4. Separate storage from vanilla game data
5. Custom rendering pipeline

---

## Core Architecture

### System Overview

```
┌─────────────────────────────────────────────────────────────┐
│              MattMC Standalone Map System                    │
├─────────────────────────────────────────────────────────────┤
│                                                              │
│  Input Layer                                                 │
│  ┌─────────────────────────────────────────────┐            │
│  │ - M Key (registered with vanilla keys)      │            │
│  │ - Mouse Wheel (zoom control)                │            │
│  │ - Mouse Drag (pan control)                  │            │
│  │ - Mouse Click (waypoint interaction)        │            │
│  └─────────────────────────────────────────────┘            │
│                         ↓                                    │
│  GUI Layer                                                   │
│  ┌─────────────────────────────────────────────┐            │
│  │ - World Map Screen (full-screen)            │            │
│  │ - Waypoints Menu (create/manage/teleport)   │            │
│  │ - Dimension Selector                        │            │
│  │ - Zoom Controls & Display                   │            │
│  └─────────────────────────────────────────────┘            │
│                         ↓                                    │
│  Rendering Layer                                             │
│  ┌─────────────────────────────────────────────┐            │
│  │ - Tile Renderer (OpenGL/LWJGL)              │            │
│  │ - Waypoint Overlay Renderer                 │            │
│  │ - Player Position Marker                    │            │
│  │ - Viewport Management                       │            │
│  └─────────────────────────────────────────────┘            │
│                         ↓                                    │
│  Data Layer                                                  │
│  ┌─────────────────────────────────────────────┐            │
│  │ - World Chunk Scanner (async)               │            │
│  │ - Tile Generator (multi-resolution)         │            │
│  │ - Tile Cache (LRU, in-memory)               │            │
│  │ - Waypoint Manager                          │            │
│  └─────────────────────────────────────────────┘            │
│                         ↓                                    │
│  Storage Layer                                               │
│  ┌─────────────────────────────────────────────┐            │
│  │ - PNG Tile Files                            │            │
│  │ - Waypoint JSON                             │            │
│  │ - Explored Chunks Metadata                  │            │
│  │ - Configuration Files                       │            │
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

### Directory Structure

**Single Player (Save-Specific):**
```
<minecraft_root>/
  └── mattmc_map/
      └── saves/
          └── <world_name>/           # Save-specific directory
              ├── overworld/
              │   ├── tiles/
              │   │   ├── zoom_0/     # 1:1 pixel per block (highest detail)
              │   │   ├── zoom_1/     # 1:2 pixel per block
              │   │   ├── zoom_2/     # 1:4 pixel per block
              │   │   └── zoom_n/     # Progressive zoom-out levels
              │   └── explored.dat
              ├── the_nether/
              ├── the_end/
              ├── waypoints.json
              └── config.json
```

**Multiplayer/Dedicated Server (Server-Specific):**
```
<minecraft_root>/
  └── mattmc_map/
      └── servers/
          └── <server_address>/       # Server-specific directory (not world-specific)
              ├── overworld/
              │   ├── tiles/
              │   │   ├── zoom_0/
              │   │   ├── zoom_1/
              │   │   └── zoom_n/
              │   └── explored.dat
              ├── the_nether/
              ├── the_end/
              ├── waypoints.json
              └── config.json
```

**Server Address Format:**
- IP-based: `192.168.1.100_25565` (IP_PORT)
- Domain-based: `play.example.com_25565`
- Localhost: `localhost_25565`

**Directory Selection Logic:**
- Single player: Use `saves/<world_name>/`
- Multiplayer: Use `servers/<server_address>/`
- This ensures each server maintains its own separate map data

---

## Feature Specifications

### 1. Map Display

**Core Functionality:**
- Full-screen map interface (replaces entire game view)
- Real-time rendering of explored terrain
- Smooth panning with mouse drag
- Player position indicator (always visible)
- Dimension indicator (shows current dimension)
- Coordinate display (updates on mouse hover)

**Visual Elements:**
- Block-accurate terrain coloring
- Biome-aware grass and water colors
- Height-based shading (brighter = higher, darker = lower)
- Structure highlighting (optional)
- Grid overlay (optional, shows chunk boundaries)

### 2. Zoom System

**Zoom Levels:**
- **Maximum Zoom (Zoom 0):** 1 pixel = 1 block (highest detail)
- **Progressive Zoom Out:** Each level reduces detail (1:2, 1:4, 1:8, 1:16, etc.)
- **Minimum Zoom:** Configurable, default ~1:64 (large area overview)

**Zoom Controls:**
- **Mouse Wheel:** Primary zoom control
  - Scroll up: Zoom in (increase detail)
  - Scroll down: Zoom out (decrease detail)
- **Keyboard:** Secondary zoom control (+ and - keys)
- **UI Buttons:** Zoom in/out buttons with current zoom level display

**Zoom Behavior:**
- Center-on-mouse: Point under cursor remains stationary during zoom
- Smooth transitions between zoom levels
- Configurable zoom speed (sensitivity)
- Zoom limits to prevent over-zoom or excessive zoom-out

### 3. Waypoint System

**Waypoint Menu:**
- Dedicated "Waypoints" button in map interface
- Opens full waypoint management screen
- List view of all waypoints (grouped by dimension)
- Search/filter functionality
- Sort options (name, distance, dimension, creation date)

**Waypoint Creation:**
- Click "Create Waypoint" in waypoints menu
- Or right-click on map to create at location
- Customization options:
  - Name (text input)
  - Color (color picker with presets)
  - Dimension (auto-detected, read-only)
  - Coordinates (auto-filled from click location)

**Waypoint Actions:**
- **Teleport:** Instant teleportation to waypoint
  - Requires creative mode or teleport permission
  - Handles cross-dimension teleport automatically
  - Confirmation dialog for long-distance teleports
- **Edit:** Modify waypoint properties
- **Delete:** Remove waypoint (with confirmation)
- **Share:** Copy coordinates to chat/clipboard
- **Navigate:** Show path/direction to waypoint

**Waypoint Display:**
- Waypoint icon with user-selected color displayed on map
- Single default icon design (not configurable by user)
- Name labels (toggle-able)
- Distance indicator when hovering
- Visual effects (glow, pulse) for selected waypoint
- Dim/hide disabled waypoints

### 4. Dimension Support

**Dimension Switching:**
- Dropdown menu in map header
- Keyboard shortcuts (Tab to cycle)
- Quick-switch to player's current dimension
- Remembers last viewed position per dimension

**Coordinate Conversion:**
- Automatic Nether ↔ Overworld conversion (1:8 ratio)
- Visual indicator when viewing different dimension
- "Show Equivalent Position" feature (overlay other dimension coordinates)

**Dimension-Specific Features:**
- Separate explored area per dimension
- Independent waypoint lists
- Dimension-appropriate coloring (Nether = red tint, End = purple tint)

### 5. Exploration Tracking

**Progressive Revelation:**
- Map initially blank/unexplored
- Reveals as player enters chunks
- Explored areas persist across sessions
- Visual distinction between explored and unexplored

**Update Mechanisms:**
- Real-time update as chunks load
- Background scanning of loaded chunks
- Manual refresh option
- Auto-update while map is open

### 6. Save/Server Management

**Single Player:**
- Maps stored per world save
- Each world has its own independent map data
- Waypoints unique to each world

**Multiplayer/Servers:**
- Maps stored per server address (not per world)
- Server-specific map persists across reconnections
- Waypoints shared across all worlds on same server
- Separate map data for each server you connect to

---

## System Components

### Component 1: World Chunk Scanner

**Purpose:** Asynchronously scan loaded chunks and extract map data

**Responsibilities:**
- Monitor chunk load events
- Extract block surface data (top-most solid block)
- Gather biome information
- Calculate height values for shading
- Mark chunks as explored
- Queue tile regeneration

**Processing Flow:**
1. Chunk loads in game world
2. Scanner detects load event
3. Scan scheduled in background thread
4. Block data extracted (top-down scan)
5. Color and height information stored
6. Affected tiles marked dirty
7. Tile regeneration queued

**Performance Characteristics:**
- Non-blocking (runs in separate thread)
- Configurable scan priority
- Batch processing for multiple chunks
- Throttling to prevent CPU overload

### Component 2: Block Color Mapper

**Purpose:** Independent color mapping system for all Minecraft blocks

**Responsibilities:**
- Maintain color database for all blocks
- Handle biome-specific colors (grass, leaves, water)
- Apply height-based shading
- Support resource pack color extraction

**Color Categories:**
- Stone variants (granite, andesite, diorite, deepslate)
- Wood types (oak, spruce, birch, jungle, etc.)
- Terrain (dirt, sand, gravel, clay)
- Foliage (grass, leaves - biome-aware)
- Water (biome-aware, depth-aware)
- Special blocks (ores, structures)

**Biome Coloring:**
- Grass: Temperature and humidity-based interpolation
- Leaves: Biome foliage color
- Water: Biome water color
- Other biome-specific blocks

### Component 3: Tile Generator

**Purpose:** Generate multi-resolution PNG tiles from chunk data

**Tile Specifications:**
- Base tile size: 512×512 pixels
- Multiple zoom levels (0 to n)
- Zoom 0: 512×512 blocks (1 pixel = 1 block)
- Zoom 1: 1024×1024 blocks (1 pixel = 2 blocks)
- Zoom n: Progressive scaling

**Generation Process:**
1. Receive tile request (dimension, coordinates, zoom level)
2. Load chunk data for tile coverage area
3. Apply color mapping to blocks
4. Apply height-based shading
5. Generate PNG image
6. Cache in memory
7. Save to disk (async)

**Optimization:**
- Generate only requested zoom levels
- Lazy generation (on-demand)
- Incremental updates (only changed areas)
- Multi-threaded generation

### Component 4: Tile Cache

**Purpose:** In-memory LRU cache for fast tile access

**Characteristics:**
- Size: Configurable, default 256 tiles (~512MB at 512×512 px)
- Eviction: Least Recently Used (LRU)
- Write-back: Save dirty tiles before eviction
- Preload: Load tiles around viewport

**Cache Strategies:**
- Viewport-based priority (visible tiles stay in cache)
- Adjacent tile preloading (smooth panning)
- Zoom-level awareness (cache relevant zoom level)
- Dimension isolation (clear cache on dimension switch)

### Component 5: Waypoint Manager

**Purpose:** Manage user-created waypoints

**Data Model:**
- UUID (unique identifier)
- Name (user-defined string)
- Dimension (world key)
- Position (x, y, z coordinates)
- Color (RGB integer)
- Creation timestamp
- Enabled flag

**Operations:**
- Create waypoint
- Read waypoint (by ID, by name, by dimension)
- Update waypoint (rename, recolor)
- Delete waypoint
- List waypoints (filtered, sorted)
- Teleport to waypoint

**Persistence:**
- JSON file format
- Save on modification
- Load on world load
- Backup/restore functionality

### Component 6: Map Renderer

**Purpose:** Render map tiles and overlays to screen

**Rendering Pipeline:**
1. Calculate visible viewport (based on center position and zoom)
2. Determine required tiles (tile coordinates)
3. Load tiles from cache (or queue generation)
4. Render tiles to screen (OpenGL texture quads)
5. Render waypoint markers and labels
6. Render player marker
7. Render UI elements (controls, info)

**Rendering Optimizations:**
- Batch tile rendering (single draw call)
- Texture atlas for UI elements
- Frustum culling (skip offscreen tiles)
- LOD (level of detail) based on zoom

### Component 7: Viewport Manager

**Purpose:** Manage map viewport (position, zoom, transform)

**State:**
- Center position (world X, Z coordinates)
- Zoom level (0 to max)
- Screen dimensions
- Current dimension

**Coordinate Transformations:**
- World to screen (blocks → pixels)
- Screen to world (pixels → blocks)
- Tile to world (tile coordinates → block coordinates)
- World to tile (block coordinates → tile coordinates)

**Viewport Operations:**
- Pan (translate center position)
- Zoom (change zoom level, adjust center)
- Center on position (player, waypoint)
- Fit bounds (show specific area)

### Component 8: Save/Server Directory Manager

**Purpose:** Determine correct storage directory based on connection type

**Responsibilities:**
- Detect if player is in single player or multiplayer
- Generate appropriate directory path
- Handle server address formatting
- Create directories as needed

**Directory Logic:**
- Single player: Use world save name
- Multiplayer: Use server IP:port or domain:port
- Sanitize addresses (replace invalid filesystem characters)

---

## User Interface Design

### Main Map Screen Layout

```
┌──────────────────────────────────────────────────────────────┐
│  [X] World Map          Overworld ▼    Waypoints  Settings   │ Header (40px)
├──────────────────────────────────────────────────────────────┤
│                                                               │
│                                                               │
│                    MAP VIEWPORT                               │
│                                                               │
│               [Rendered tiles, waypoints,                     │ Main Area
│                player position]                               │
│                                                               │
│                                                               │
│                                                               │
├──────────────────────────────────────────────────────────────┤
│ Zoom: [-] 100% (1:1) [+]  │  X: 1234, Z: 5678  │  [Center]  │ Footer (35px)
└──────────────────────────────────────────────────────────────┘
```

**Header Elements:**
- Close button (X) - top left
- Title "World Map" - left
- Dimension selector dropdown - center
- "Waypoints" button - right of center
- "Settings" button - top right

**Footer Elements:**
- Zoom controls (- button, level display, + button) - left
- Coordinate display (updates on hover) - center
- "Center on Player" button - right

### Waypoints Menu

```
┌──────────────────────────────────────────────────────────────┐
│  Waypoints                                        [X] Close   │
├──────────────────────────────────────────────────────────────┤
│  [Search...]                    [+ Create Waypoint]           │
├──────────────────────────────────────────────────────────────┤
│                                                               │
│  Overworld (3)                                                │
│    ● Home Base          X: 100, Y: 64, Z: 200     [Teleport] │
│    ● Mine Entrance      X: -50, Y: 12, Z: 300     [Teleport] │
│    ● Castle             X: 500, Y: 80, Z: -100    [Teleport] │
│                                                               │
│  The Nether (1)                                               │
│    ● Nether Hub         X: 12, Y: 64, Z: 25       [Teleport] │
│                                                               │
│  The End (0)                                                  │
│    (No waypoints)                                             │
│                                                               │
├──────────────────────────────────────────────────────────────┤
│  Selected: Home Base                [Edit]  [Delete]          │
└──────────────────────────────────────────────────────────────┘
```

**Features:**
- Search bar (filter by name)
- Create button (opens creation dialog)
- Grouped by dimension (collapsible sections)
- Each waypoint row shows: waypoint icon (colored), name, coordinates, teleport button
- Selection highlights row
- Edit/Delete buttons for selected waypoint

**Note:** Waypoints use a single default icon design, colored by user choice (not configurable icons)

### Create/Edit Waypoint Dialog

```
┌─────────────────────────────────────────┐
│  Create Waypoint              [X] Close  │
├─────────────────────────────────────────┤
│  Name: [_________________________]       │
│                                          │
│  Color: [●●●●●●●●●●●●] Custom: [    ]  │
│         Red Orange Yellow Green          │
│         Blue Purple Pink White           │
│                                          │
│  Location:                               │
│    Dimension: Overworld                  │
│    X: 123   Y: 64   Z: 456               │
│                                          │
│              [Cancel]  [Create]          │
└─────────────────────────────────────────┘
```

**Fields:**
- Name (text input, required)
- Color (preset palette + custom color picker)
- Location (auto-filled, can be edited)

**Note:** Uses single default icon design - only color is customizable

### Settings Panel

```
┌─────────────────────────────────────────┐
│  Map Settings                 [X] Close  │
├─────────────────────────────────────────┤
│  Display                                 │
│    [ ] Show chunk grid                   │
│    [✓] Show waypoint labels              │
│    [✓] Show player marker                │
│    [ ] Show coordinates                  │
│                                          │
│  Zoom                                    │
│    Min Zoom: [1:64 ▼]                    │
│    Max Zoom: [1:1  ▼]                    │
│    Zoom Speed: [Normal ▼]                │
│                                          │
│  Performance                             │
│    Tile Cache: [256 tiles ▼]             │
│    [ ] High quality rendering            │
│                                          │
│              [Reset]  [Apply]            │
└─────────────────────────────────────────┘
```

**Settings Categories:**
- Display options (toggles for various visual elements)
- Zoom configuration (range and speed)
- Performance tuning (cache size, quality)

### Visual Design Guidelines

**Color Scheme:**
- Background: Semi-transparent dark overlay (#000000CC)
- UI panels: Dark gray (#2B2B2B)
- Text: White (#FFFFFF) and light gray (#CCCCCC)
- Accents: Minecraft green (#55FF55)
- Buttons: Minecraft button style (matching vanilla)

**Typography:**
- UI text: Minecraft font
- Coordinates: Monospace for alignment
- Headers: Bold variant

**Waypoint Markers:**
- Single default icon design (e.g., pin/marker shape)
- Size: 12-16 pixels
- Colors: User-selectable from palette
- Icon design not configurable (always same shape, just different colors)

---

## Data Persistence

### File Formats

**Tile Files:**
- Format: PNG (compressed)
- Location (Single Player): `mattmc_map/saves/<world_name>/<dimension>/tiles/zoom_<level>/<region>/tile_<x>_<z>.png`
- Location (Multiplayer): `mattmc_map/servers/<server_address>/<dimension>/tiles/zoom_<level>/<region>/tile_<x>_<z>.png`
- Naming: Tile coordinates in file name
- Organization: Grouped by region (32×32 tiles per region directory)

**Waypoint File:**
- Format: JSON
- Location (Single Player): `mattmc_map/saves/<world_name>/waypoints.json`
- Location (Multiplayer): `mattmc_map/servers/<server_address>/waypoints.json`
- Structure:
  ```json
  {
    "version": 1,
    "waypoints": [
      {
        "id": "uuid-string",
        "name": "Home Base",
        "dimension": "minecraft:overworld",
        "x": 123, "y": 64, "z": 456,
        "color": 16711680,
        "created": 1234567890000,
        "enabled": true
      }
    ]
  }
  ```

**Explored Chunks:**
- Format: Custom binary format (compact)
- Location (Single Player): `mattmc_map/saves/<world_name>/<dimension>/explored.dat`
- Location (Multiplayer): `mattmc_map/servers/<server_address>/<dimension>/explored.dat`
- Content: Bitset of explored chunk coordinates

**Configuration:**
- Format: JSON
- Location: Per-world/server config and global config
- Settings: Zoom ranges, display options, performance tuning

### Save/Load Strategy

**When to Save:**
- Tile generation: Save immediately after generation
- Waypoint changes: Save on create/edit/delete
- Explored chunks: Periodic save (every 60 seconds) + on world save
- Configuration: Save on settings change

**When to Load:**
- World load: Load waypoints and explored chunks
- Map open: Load configuration
- Tile display: Load tiles on-demand (lazy loading)

**Error Handling:**
- Corrupted files: Fallback to empty/default state
- Missing files: Create new with defaults
- Version mismatch: Attempt migration or reset

### Directory Management

**Single Player Detection:**
- Check if connected to integrated server
- Use world save name for directory

**Multiplayer Detection:**
- Check if connected to dedicated server
- Extract server address (IP:port or domain:port)
- Sanitize address for filesystem compatibility
- Create server-specific directory

---

## Performance Requirements

### Target Metrics

**Rendering:**
- 60 FPS minimum while map is open
- < 100ms time to open map screen
- < 16ms per frame rendering time
- Smooth pan and zoom (no stuttering)

**Tile Generation:**
- < 500ms to generate single tile (zoom 0)
- < 2 seconds for full viewport (typical 3×3 tiles)
- Background generation doesn't impact game FPS

**Memory:**
- < 200MB total map system overhead
- Tile cache: Configurable, default 256 tiles (~512MB)
- Waypoint data: < 1MB (thousands of waypoints)

**Storage:**
- < 10MB per 1000 explored chunks (tiles + metadata)
- PNG compression for efficient disk usage
- Incremental saves (only changed data)

### Optimization Strategies

**Rendering Optimizations:**
- Texture atlas for UI elements
- Batch rendering (minimize draw calls)
- Viewport culling (don't render offscreen content)
- LOD system (lower detail for distant areas)

**Generation Optimizations:**
- Multi-threaded tile generation
- Incremental updates (only regenerate changed areas)
- Lazy generation (generate only when needed)
- Caching intermediate results

**Memory Optimizations:**
- LRU cache with aggressive eviction
- Lazy loading (load tiles on-demand)
- Unload tiles outside viewport margin
- Compress data in memory where possible

---

## Implementation Phases

### Phase 1: Core Infrastructure (Weeks 1-2)

**Focus:** Foundation and data collection

**Deliverables:**
- World chunk scanner (async background processing)
- Block color mapper (complete block palette)
- Chunk data extraction and storage
- Explored chunk tracking
- File system structure setup (with save/server detection)

**Validation:**
- Chunks scanned without blocking game
- Colors accurate for all block types
- Explored chunks persist across sessions
- Correct directory used for single player vs multiplayer

### Phase 2: Tile System (Weeks 3-4)

**Focus:** Multi-resolution tile generation and caching

**Deliverables:**
- Tile generator for multiple zoom levels
- PNG encoding/decoding
- Tile cache with LRU eviction
- Directory organization for tiles
- Height-based shading algorithm

**Validation:**
- Tiles generate at all zoom levels
- 1:1 pixel-per-block at zoom 0
- Cache eviction works correctly
- Tiles save/load from disk

### Phase 3: Map GUI (Weeks 5-6)

**Focus:** User interface and rendering

**Deliverables:**
- World map screen (full-screen interface)
- Viewport management (pan, zoom)
- Tile rendering (OpenGL)
- Mouse controls (drag, wheel, click)
- Coordinate display and UI elements

**Validation:**
- Map opens with M key
- Smooth panning with mouse drag
- Mouse wheel zoom works correctly
- Center-on-mouse zoom behavior
- 60 FPS rendering achieved

### Phase 4: Waypoint System (Weeks 7-8)

**Focus:** Waypoint creation and management

**Deliverables:**
- Waypoint data model (color selection only)
- Waypoint manager (CRUD operations)
- Waypoints menu UI
- Waypoint creation/edit dialog
- Waypoint rendering on map (single icon design, user-colored)
- JSON persistence

**Validation:**
- Create waypoints via menu
- Edit and delete waypoints
- Waypoints render on map with default icon in chosen color
- Waypoints persist across sessions
- Search and filter work

### Phase 5: Teleportation (Week 9)

**Focus:** Waypoint teleportation functionality

**Deliverables:**
- Teleport command integration
- Cross-dimension teleport handling
- Permission system integration
- Teleport confirmation dialogs
- Coordinate conversion (Nether/Overworld)

**Validation:**
- Teleport within dimension
- Cross-dimension teleport
- Nether coordinates convert correctly (1:8)
- Permission checks work

### Phase 6: Polish & Configuration (Week 10)

**Focus:** Settings, optimization, and final polish

**Deliverables:**
- Settings menu
- Configuration persistence
- Performance optimizations
- Visual polish and animations
- Help/tutorial screen
- Documentation

**Validation:**
- Settings save and load
- Performance targets met
- UI is polished and consistent
- No major bugs

---

## Integration Points

### 1. Key Registration

**Location:** Register with vanilla key bindings system

**Implementation Approach:**
- Register M key in the same place vanilla keys are registered
- Use Minecraft's `KeyMapping` system
- Category: Custom "MattMC Map" category or "Gameplay" category
- Binding: Default to M, user-configurable

**Registration Point:**
- Client initialization phase
- Alongside vanilla key bindings (movement, inventory, etc.)
- Before main menu displays

### 2. Chunk Load Events

**Hook:** Client-side chunk load events

**Purpose:** Trigger chunk scanning when chunks load

**Integration:**
- Subscribe to chunk load events (Forge/Fabric event bus)
- Filter for client-side only
- Trigger async scan in background

### 3. Client Tick

**Hook:** Client tick event

**Purpose:** Update systems, process queues

**Tasks:**
- Process tile generation queue
- Update tile cache
- Handle async operation completion
- Update UI animations

### 4. World Load/Unload

**Hook:** World load and unload events

**Purpose:** Load/save map data, determine directory

**Actions on Load:**
- Detect single player vs multiplayer
- Determine appropriate directory path
- Load waypoints from JSON
- Load explored chunks metadata
- Load configuration
- Initialize map systems

**Actions on Unload:**
- Save modified tiles
- Save waypoints
- Save explored chunks
- Save configuration
- Clean up resources

### 5. Render Pipeline

**Integration:** Custom screen rendering

**Approach:**
- Map screen extends vanilla `Screen` class
- Custom rendering using `GuiGraphics` and OpenGL
- Integrates with vanilla GUI rendering system

---

## Conclusion

This map system is designed as a completely **standalone, independent system** that operates similarly to JourneyMap and Xaero's World Map, but with the advantage of being integrated directly into MattMC's source code.

### Key Design Principles

1. **Independence from Vanilla:**
   - No dependencies on vanilla map or waypoint systems
   - Custom chunk scanning and data extraction
   - Independent color mapping and rendering

2. **Feature-Focused:**
   - Essential features only (no minimap, no cave layers)
   - Mouse wheel zoom with 1 pixel per block maximum detail
   - Dedicated waypoints menu for creation and teleportation
   - Single default waypoint icon design (color customizable, shape not)
   - Multi-dimension support with coordinate conversion
   - Save-specific maps for single player, server-specific for multiplayer

3. **Performance-Oriented:**
   - Async processing to avoid blocking game
   - Efficient tile caching and rendering
   - Configurable performance tuning
   - Target: 60 FPS with < 200MB overhead

4. **User-Friendly:**
   - Intuitive controls (M key, mouse wheel, drag)
   - Clear UI with dedicated waypoints menu
   - Smooth zoom and pan
   - Integrated with vanilla key bindings
   - Single default waypoint icon (visible, color customizable)

### Storage Strategy

**Single Player:**
- Maps saved per world in `mattmc_map/saves/<world_name>/`
- Each world has independent map data

**Multiplayer:**
- Maps saved per server in `mattmc_map/servers/<server_address>/`
- Server maps persist across reconnections
- Not world-specific - all worlds on same server share map data

### Timeline

**Total Duration:** 10 weeks

**Major Milestones:**
- Week 2: Core data collection working with save/server detection
- Week 4: Tiles generating at all zoom levels
- Week 6: Map GUI functional with zoom
- Week 8: Waypoint system complete (default icon, color customizable)
- Week 9: Teleportation working
- Week 10: Polish and release

### Success Criteria

- Map opens instantly with M key
- Smooth 60 FPS rendering during pan/zoom
- Maximum zoom shows 1 pixel per block detail
- Waypoints menu allows easy creation and teleportation
- Waypoints display with default icon in user-selected color
- Multi-dimension support with proper coordinate conversion
- Data persists correctly across sessions
- Correct directory used for single player vs multiplayer
- Memory usage stays under budget

This architecture provides a solid foundation for a high-performance, user-friendly map system that rivals external mods while benefiting from direct source code integration.

---

*Document Version: 4.1*  
*Updated: December 2024*  
*For: MattMC (Minecraft 1.21.10)*  
*Focus: Architecture & Design (Single default icon, Save/Server-specific storage)*
