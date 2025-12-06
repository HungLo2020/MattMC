# Server System

## Overview

The Minecraft server is the authoritative game state manager, handling world simulation, player connections, entity ticking, chunk loading, and multiplayer coordination. It runs a fixed tick loop at 20 TPS (ticks per second) and manages all game logic.

## Architecture

```
┌─────────────────────────────────────────┐
│      Main Thread (Tick Loop)            │
│   - World ticking                       │
│   - Entity updates                      │
│   - Block updates                       │
│   - Player logic                        │
└──────────────┬──────────────────────────┘
               │
       ┌───────┴────────┐
       ▼                ▼
┌──────────────┐  ┌──────────────┐
│  Network     │  │  Worker      │
│  Threads     │  │  Threads     │
│  (Netty)     │  │  (Chunks)    │
└──────────────┘  └──────────────┘
```

## Core Components

### 1. MinecraftServer

**Location**: `net.minecraft.server.MinecraftServer`

Central server class managing the game loop and server lifecycle.

**Key Responsibilities**:
- Tick loop execution (20 TPS)
- World management
- Player tracking
- Command execution
- Resource loading
- Server lifecycle

**Server Types**:
- **DedicatedServer**: Standalone server (Main.java)
- **IntegratedServer**: Singleplayer/LAN (embedded in client)

**Key Fields**:
- `nextTickTimeNanos`: Target time for next tick
- `levels`: All loaded worlds (overworld, nether, end)
- `playerList`: Server player list
- `resources`: Server resources (recipes, loot, etc.)
- `executor`: Main thread executor
- `running`: Server running flag

**Key Methods**:
- `runServer()`: Main server loop
- `tickServer()`: Single tick execution
- `tickChildren()`: Tick all worlds
- `halt()`: Graceful shutdown
- `saveAllChunks()`: Save all worlds
- `isRunning()`: Check if running

### 2. Server Lifecycle

#### Initialization

**Process**:
1. Bootstrap static registries
2. Load server.properties
3. Setup EULA check
4. Create data pack repository
5. Load data packs
6. Build resource manager
7. Generate/load worlds
8. Start network listener
9. Prepare spawn area
10. Enter main loop

#### Main Loop

**Location**: `MinecraftServer.runServer()`

```java
while (running) {
    long currentTime = Util.getNanos();
    long deltaTime = currentTime - nextTickTimeNanos;
    
    if (deltaTime > TICK_TIME_NANOS) {
        // Tick the server
        tickServer(() -> true);
        
        // Calculate next tick time
        nextTickTimeNanos += TICK_TIME_NANOS;
        
        // Skip ticks if too far behind
        if (deltaTime > MAX_TICK_CATCH_UP) {
            nextTickTimeNanos = currentTime;
        }
    } else {
        // Sleep until next tick
        Thread.sleep(1);
    }
}
```

**Tick Duration**: 50ms (1000ms / 20 ticks)

#### Tick Execution

**Location**: `MinecraftServer.tickServer()`

**Order**:
1. **Auto-save check** (every 6000 ticks = 5 minutes)
2. **Profiler start**
3. **Command functions** (tick.json tag)
4. **Tick worlds** (entities, blocks, chunks)
5. **Player connections** (process packets)
6. **Server tasks** (scheduled operations)
7. **Network flush** (send queued packets)
8. **Profiler stop**
9. **TPS tracking**

#### Shutdown

**Process**:
1. Stop accepting new connections
2. Kick all players
3. Save all chunks
4. Unload all chunks
5. Close network server
6. Stop worker threads
7. Save server state
8. Exit

### 3. Server Levels (Worlds)

**Location**: `net.minecraft.server.level.ServerLevel`

Server-side world representation.

**Key Features**:
- Entity tracking
- Chunk loading/generation
- Block ticking
- Weather simulation
- Time tracking
- Spawn tracking
- Entity spawning

**Key Methods**:
- `tick()`: Tick the world
- `tickChunk()`: Tick single chunk
- `addFreshEntity()`: Spawn entity
- `removeEntity()`: Despawn entity
- `setBlock()`: Place/break block
- `explode()`: Create explosion
- `sendBlockUpdated()`: Notify clients of change

**World Tick Order**:
1. Time increment
2. Weather update
3. Sleeping player check
4. Random ticks (crops, grass spread)
5. Chunk ticking
6. Entity ticking
7. Block entity ticking
8. Scheduled ticks (redstone, fluids)
9. Raid management
10. Wandering trader spawning

### 4. Chunk Loading System

**Location**: `net.minecraft.server.level.ChunkMap`

Manages chunk loading, generation, and tracking.

**Chunk Ticket System**:
- Tickets keep chunks loaded
- Distance levels (0-33):
  - **Level 31**: Entity ticking
  - **Level 32**: Entity processing (no AI)
  - **Level 33**: Border chunks (generation only)
  - **Level 34+**: Unloaded

**Ticket Types**:
- `PLAYER`: Player nearby
- `START`: Spawn chunks
- `PORTAL`: Nether portal
- `POST_TELEPORT`: After teleport
- `FORCED`: Force-loaded chunks
- `UNKNOWN`: Fallback

**Chunk States**:
1. `EMPTY`: Not generated
2. `STRUCTURE_STARTS`: Structures placed
3. `STRUCTURE_REFERENCES`: Structure references
4. `BIOMES`: Biomes assigned
5. `NOISE`: Terrain generated
6. `SURFACE`: Surface blocks
7. `CARVERS`: Caves carved
8. `FEATURES`: Features placed
9. `INITIALIZE_LIGHT`: Lighting initialized
10. `LIGHT`: Lighting complete
11. `SPAWN`: Spawn prepared
12. `FULL`: Fully generated

**Chunk Loading**:
- View distance controlled by settings
- Async generation on worker threads
- Progressive loading (border → center)
- Prioritized by distance to players

### 5. Entity Management

**Location**: `net.minecraft.server.level.ServerLevel`

Server tracks and ticks all entities.

**Entity Lists**:
- `entityTickList`: Entities to tick
- `navigatingMobs`: Pathfinding entities
- `entitiesById`: Lookup by ID
- `dragonFight`: End dragon fight

**Entity Tracking**:
- Entities synced to nearby players
- Tracking distance varies by type
- Position delta encoding
- Update frequency varies by distance

**Tracking Distances**:
- Players: 10 chunks
- Items: 6 chunks
- Most mobs: 5 chunks
- Large mobs: 10 chunks
- Projectiles: 4 chunks

**Spawning**:
- Natural mob spawning
- Per-player spawn cap
- Biome-specific spawning
- Category limits (hostile, passive, etc.)
- Despawn rules (distance, persistence)

### 6. Player Management

**Location**: `net.minecraft.server.players.PlayerList`

Manages all connected players.

**Key Responsibilities**:
- Player join/leave
- Respawning
- Teleportation
- Operator management
- Whitelist/bans
- Player data saving/loading

**Key Methods**:
- `placeNewPlayer()`: Handle player join
- `remove()`: Handle disconnect
- `respawn()`: Respawn player
- `broadcastSystemMessage()`: Send to all
- `saveAll()`: Save all player data
- `isOp()`: Check operator status

**Player Data**:
- Stored per-player (UUID-based files)
- Location: `world/playerdata/<uuid>.dat`
- Contains:
  - Inventory
  - Position
  - Health/hunger
  - Experience
  - Ender chest
  - Stats/achievements

### 7. Server Configuration

**Location**: `net.minecraft.server.dedicated.DedicatedServerSettings`

#### server.properties

Main server configuration file.

**Key Settings**:
- `server-port`: Network port (25565)
- `server-ip`: Bind address
- `max-players`: Player limit
- `view-distance`: Chunk render distance
- `simulation-distance`: Tick distance
- `gamemode`: Default game mode
- `difficulty`: World difficulty
- `hardcore`: Hardcore mode
- `pvp`: PvP enabled
- `max-world-size`: World border
- `motd`: Server description
- `level-name`: World folder name
- `level-seed`: World seed
- `level-type`: Generation type
- `spawn-protection`: Spawn radius
- `allow-flight`: Allow flying
- `white-list`: Whitelist mode
- `online-mode`: Mojang authentication
- `resource-pack`: Server resource pack

#### EULA

**Location**: `eula.txt`

Must set `eula=true` to run server.

```properties
eula=true
```

### 8. Game Rules

**Location**: `net.minecraft.world.level.GameRules`

Customizable gameplay rules.

**Common Rules**:
- `doMobSpawning`: Enable mob spawning
- `doFireTick`: Fire spreads
- `keepInventory`: Keep items on death
- `mobGriefing`: Mobs destroy blocks
- `doMobLoot`: Mobs drop items
- `doDaylightCycle`: Time advances
- `doWeatherCycle`: Weather changes
- `randomTickSpeed`: Random tick rate
- `commandBlockOutput`: Show command output
- `naturalRegeneration`: Health regen
- `announceAdvancements`: Announce achievements
- `disableElytraMovementCheck`: Elytra check
- `maxEntityCramming`: Entity cramming limit
- `doEntityDrops`: Entity drops
- `doTileDrops`: Block drops
- `sendCommandFeedback`: Command feedback

**Rule Types**:
- Boolean rules (true/false)
- Integer rules (numeric value)

### 9. Scheduled Tasks

**Location**: `net.minecraft.server.MinecraftServer.execute()`

Queue tasks to run on main thread.

**Task Types**:
- One-time execution
- Delayed execution
- Repeating tasks

**Common Uses**:
- Network packet handling
- Async operation completion
- Delayed game logic

**Example**:
```java
server.execute(() -> {
    // Runs on next tick
});
```

### 10. Server Commands

**Location**: `net.minecraft.server.commands`

Server-specific commands.

**Server Management**:
- `/stop`: Stop server
- `/save-all`: Save all data
- `/save-on`, `/save-off`: Auto-save toggle
- `/reload`: Reload data packs
- `/publish`: Open to LAN

**Player Management**:
- `/kick`: Kick player
- `/ban`, `/ban-ip`: Ban player/IP
- `/pardon`, `/pardon-ip`: Unban
- `/op`, `/deop`: Operator status
- `/whitelist`: Whitelist commands
- `/list`: List players

**World Management**:
- `/seed`: Show world seed
- `/setworldspawn`: Set spawn
- `/forceload`: Force load chunks
- `/worldborder`: World border

### 11. Performance Monitoring

**Location**: Server profiling and metrics

**Key Metrics**:
- **TPS** (Ticks Per Second): Should be 20
- **MSPT** (Milliseconds Per Tick): Should be <50ms
- **Chunk Load Time**: Generation performance
- **Entity Count**: Performance impact
- **Memory Usage**: Heap usage

**Profiling**:
- `/debug start/stop`: Built-in profiler
- Spark plugin: Advanced profiling
- Profiler results: timing breakdowns

**Performance Factors**:
- Entity count
- Loaded chunks
- Redstone contraptions
- Hopper chains
- Mob farms
- Plugins/mods

### 12. World Saving

**Location**: `net.minecraft.world.level.storage`

Persistent world storage.

**Save Format**:
- Anvil region files (.mca)
- 32×32 chunk regions
- Compressed with zlib
- Located in `world/region/`

**Saved Data**:
- Chunks (blocks, biomes)
- Entities
- Block entities
- Players
- World data (time, seed, spawn)
- Structure references
- POI (Points of Interest)

**Save Process**:
1. Mark chunks for saving
2. Serialize chunk data
3. Compress data
4. Write to region file
5. Sync to disk

**Auto-Save**:
- Every 5 minutes (6000 ticks)
- Can be disabled with `/save-off`
- Configurable interval

### 13. Network Server

**Location**: `net.minecraft.server.network.ServerConnectionListener`

Manages network connections.

**Components**:
- Netty server bootstrap
- Channel pipeline setup
- Connection handling
- Packet routing

**Connection Flow**:
1. Client connects
2. Handshake phase
3. Status check OR login
4. Configuration phase
5. Game phase (playing)
6. Disconnect

**Connection States**:
- `HANDSHAKING`: Initial connection
- `STATUS`: Server list ping
- `LOGIN`: Authentication
- `CONFIGURATION`: Setup
- `GAME`: Active play

### 14. Integrated Server

**Location**: `net.minecraft.client.server.IntegratedServer`

Singleplayer/LAN server embedded in client.

**Features**:
- Runs in client process
- Pauses when game menu open
- Can open to LAN
- Shares resources with client

**Differences from Dedicated**:
- Pause on menu
- Direct player reference
- Shared resource loading
- No server.properties

### 15. Server Resource Manager

**Location**: `net.minecraft.server.ReloadableServerResources`

Manages server-side resources.

**Loaded Resources**:
- Recipes
- Loot tables
- Advancements
- Functions
- Predicates
- Item modifiers
- Tags
- World generation data

**Reloading**:
- `/reload` command
- Clears caches
- Rebuilds all resources
- Updates all systems

### 16. Scheduled Ticks

**Location**: `net.minecraft.world.ticks.LevelTicks`

Schedule future block/fluid updates.

**Uses**:
- Redstone updates
- Fluid flow
- Crop growth
- Block state changes

**Tick Scheduling**:
```java
level.scheduleTick(pos, block, delay);
```

**Priorities**:
- High: Urgent updates
- Normal: Standard updates
- Low: Deferred updates

## Performance Optimization

**Server Settings**:
- View distance: Lower = better performance
- Simulation distance: Entity tick range
- Network compression: Bandwidth vs CPU
- Chunk threads: Parallel generation

**Optimization Techniques**:
1. Reduce entity count
2. Optimize redstone
3. Limit mob farms
4. Pre-generate chunks
5. Use paper/purpur (optimized servers)
6. Increase RAM allocation
7. Use SSD for world storage

**Common Bottlenecks**:
- Excessive entities
- Complex redstone
- Chunk generation
- Lighting calculations
- Pathfinding
- Hopper networks

## Key Files

- `MinecraftServer.java`: Server core (2,200+ lines)
- `ServerLevel.java`: World implementation (2,500+ lines)
- `ChunkMap.java`: Chunk management (1,600+ lines)
- `PlayerList.java`: Player management (1,300+ lines)
- `Main.java`: Server entry point
- `DedicatedServer.java`: Dedicated server (600+ lines)

## Server Start Command

```bash
java -Xmx2G -Xms2G -jar minecraft_server.jar nogui
```

**JVM Flags**:
- `-Xmx`: Maximum memory
- `-Xms`: Initial memory
- `-XX:+UseG1GC`: G1 garbage collector
- `-XX:+ParallelRefProcEnabled`: Parallel reference processing
- `nogui`: No GUI (console only)

## Related Systems

- [Networking System](NETWORKING-SYSTEM.md) - Server-client communication
- [Entity System](ENTITY-SYSTEM.md) - Entity ticking
- [World Generation](WORLD-GENERATION-SYSTEM.md) - Chunk generation
- [Command System](COMMAND-SYSTEM.md) - Server commands
- [Data System](DATA-SYSTEM.md) - Data pack loading
