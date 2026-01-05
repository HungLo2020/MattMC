# WorldEdit Integration Plan for MattMC

## Executive Summary

This document outlines a comprehensive plan to integrate WorldEdit functionality directly into the MattMC Minecraft 1.21.10 fork. The implementation will replicate WorldEdit's complete feature set natively within the base game **without using mixins**, leveraging direct source code modification instead.

**Key Requirements:**
- NO mixins allowed - all functionality integrated through direct code modification
- Complete WorldEdit feature parity with the reference implementation in `frnsrc/`
- Custom "wand" item using stick texture in the OP_BLOCKS creative tab
- Fully integrated into base game, not a separate mod layer

---

## Table of Contents

1. [Architecture Overview](#architecture-overview)
2. [Core Systems](#core-systems)
3. [Integration Strategy](#integration-strategy)
4. [Component Implementation](#component-implementation)
5. [Command System](#command-system)
6. [Item & Tool System](#item--tool-system)
7. [Session Management](#session-management)
8. [Region Selection](#region-selection)
9. [Block Manipulation](#block-manipulation)
10. [Clipboard & Schematics](#clipboard--schematics)
11. [Brushes & Tools](#brushes--tools)
12. [History & Undo/Redo](#history--undoredo)
13. [Permissions & Configuration](#permissions--configuration)
14. [Phase Breakdown](#phase-breakdown)
15. [File Structure](#file-structure)
16. [Testing Strategy](#testing-strategy)
17. [Risk Mitigation](#risk-mitigation)

---

## Architecture Overview

### High-Level Design

WorldEdit will be implemented as a **first-class citizen** of MattMC, integrated directly into the game engine rather than as an external mod. The architecture follows a layered approach:

```
┌─────────────────────────────────────────────────────────┐
│               Command Layer                              │
│  (Brigadier commands integrated into vanilla system)     │
└─────────────────────────────────────────────────────────┘
                          ↓
┌─────────────────────────────────────────────────────────┐
│            WorldEdit API Layer                           │
│  (EditSession, LocalSession, WorldEdit core)             │
└─────────────────────────────────────────────────────────┘
                          ↓
┌─────────────────────────────────────────────────────────┐
│          Platform Abstraction Layer                      │
│  (Adapters between WE and Minecraft internals)           │
└─────────────────────────────────────────────────────────┘
                          ↓
┌─────────────────────────────────────────────────────────┐
│         Minecraft Core Integration Points                │
│  (Direct modifications to server/client code)            │
└─────────────────────────────────────────────────────────┘
```

### Package Structure

All WorldEdit code will reside under:
```
net.minecraft.worldedit/
├── core/           # Core WorldEdit logic (adapted from worldedit-core)
├── platform/       # MattMC-specific platform integration
├── command/        # Command implementations
├── tool/           # Tool system (wand, brushes, etc.)
├── session/        # Session management
├── extent/         # Block manipulation abstractions
├── region/         # Region selection and manipulation
├── history/        # Undo/redo system
├── clipboard/      # Clipboard and schematic operations
├── math/           # Mathematical utilities
├── function/       # Functional programming utilities
└── util/           # General utilities
```

### Key Principles

1. **No Mixins**: All integrations done through direct code modification
2. **Minimal Footprint**: Touch vanilla code only where absolutely necessary
3. **Performance First**: Leverage direct access to internals for optimization
4. **Maintainability**: Clear separation between WE logic and MC integration
5. **Compatibility**: Ensure all vanilla functionality remains intact

---

## Core Systems

### 1. WorldEdit Instance Management

**Location**: `net.minecraft.worldedit.core.WorldEdit`

The central singleton that manages all WorldEdit operations:

- **Platform Manager**: Manages the MattMC platform integration
- **Session Manager**: Tracks player sessions and their selections
- **Configuration**: Loads and manages WorldEdit settings
- **Command Manager**: Registers and dispatches WE commands
- **Event Bus**: Internal event system for WE operations
- **Factory System**: Block, item, pattern, and mask factories

**Integration Point**: Initialize during server startup in `net.minecraft.server.MinecraftServer`

### 2. Platform Abstraction

**Location**: `net.minecraft.worldedit.platform.MattMCPlatform`

Provides the bridge between WorldEdit abstractions and Minecraft internals:

- **World Adapter**: Wraps `ServerLevel` to provide WorldEdit's `World` interface
- **Player Adapter**: Wraps `ServerPlayer` to provide WorldEdit's `Player` interface
- **Block Adapter**: Converts between MC `BlockState` and WE `BlockState`
- **Entity Adapter**: Handles entity operations
- **NBT Converter**: Bidirectional NBT conversion

**Integration Points**:
- Server initialization
- Player join/leave events
- World load/unload events

### 3. Session Management

**Location**: `net.minecraft.worldedit.session.SessionManager`

Manages per-player WorldEdit sessions:

- **LocalSession**: Stores selection, clipboard, tools, history per player
- **Session Persistence**: Save/load sessions to disk
- **Idle Timeout**: Clean up inactive sessions
- **Multi-world Support**: Track selections across dimensions

**Integration Point**: Hook into player connection events in `ServerGamePacketListenerImpl`

---

## Integration Strategy

### Replacing Mixin Functionality

WorldEdit's Fabric implementation uses 3 mixins. Each must be replaced with direct code modifications:

#### 1. MixinMinecraftServer → Direct Server Modification

**Original Purpose**: Implements `Watchdog` interface and provides storage path access

**Replacement Strategy**:
- Modify `net.minecraft.server.MinecraftServer` to directly implement `Watchdog` interface
- Add `worldEdit_tick()` method that updates `nextTickTimeNanos`
- Add `worldEdit_getStoragePath(Level world)` method for dimension paths

**Code Changes**:
```java
// In MinecraftServer.java
public class MinecraftServer implements Watchdog, ... {
    // Add WorldEdit-specific methods
    public void worldEdit_tick() {
        this.nextTickTimeNanos = Util.getNanos();
    }
    
    public Path worldEdit_getStoragePath(Level world) {
        return this.storageSource.getDimensionPath(world.dimension());
    }
}
```

#### 2. MixinServerGamePacketListenerImpl → Direct Packet Handler Modification

**Original Purpose**: Intercepts left-click air events and swing packets for tool activation

**Replacement Strategy**:
- Modify `handleAnimate()` in `ServerGamePacketListenerImpl` to check for WorldEdit tools
- Modify `handlePlayerAction()` to track swing packet suppression
- Add WorldEdit tool activation logic directly in packet handling

**Code Changes**:
```java
// In ServerGamePacketListenerImpl.java
private int worldEdit_ignoreSwingPackets = 0;

public void handleAnimate(ServerboundSwingPacket packet) {
    // WorldEdit integration: Check for left-click air with WE tool
    if (!this.player.gameMode.isDestroyingBlock) {
        if (this.worldEdit_ignoreSwingPackets > 0) {
            this.worldEdit_ignoreSwingPackets--;
        } else {
            // Check if player has WorldEdit tool and activate it
            WorldEditIntegration.onLeftClickAir(this.player, packet.getHand());
        }
    }
    // ... existing code
}
```

#### 3. MixinLevelChunkSetBlockHook → Direct Chunk Modification

**Original Purpose**: Allows WorldEdit to suppress block update notifications during bulk operations

**Replacement Strategy**:
- Modify `LevelChunk.setBlockState()` to check for WorldEdit "silent mode" flag
- Add thread-local or context-based flag system for bulk operation mode
- Conditionally skip `onPlace()` calls when WorldEdit is performing bulk edits

**Code Changes**:
```java
// In LevelChunk.java
private static final ThreadLocal<Boolean> worldEdit_suppressUpdates = 
    ThreadLocal.withInitial(() -> false);

public static void worldEdit_setSuppressUpdates(boolean suppress) {
    worldEdit_suppressUpdates.set(suppress);
}

public BlockState setBlockState(BlockPos pos, BlockState state, boolean move) {
    // ... existing block setting logic
    
    // WorldEdit integration: conditionally suppress updates
    boolean shouldUpdate = !worldEdit_suppressUpdates.get();
    if (shouldUpdate) {
        blockstate1.onPlace(this.level, pos, blockstate, move);
    }
    
    // ... rest of method
}
```

### Event Hooks

Instead of Fabric's event system, we'll use direct method calls at strategic points:

1. **Player Join**: Initialize WorldEdit session
2. **Player Leave**: Save and cleanup session
3. **Player Interact**: Check for wand/tool usage
4. **Block Break**: Check for super pickaxe mode
5. **Server Tick**: Process pending operations, cleanup

---

## Component Implementation

### 1. Custom Wand Item

**Location**: `net.minecraft.world.item.WandItem`

A new item class specifically for WorldEdit's selection wand:

```java
package net.minecraft.world.item;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.InteractionResult;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

public class WandItem extends Item {
    public WandItem(Properties properties) {
        super(properties);
    }
    
    @Override
    public InteractionResult useOn(UseOnContext context) {
        // Handle right-click selection (primary point)
        if (!context.getLevel().isClientSide) {
            Player player = context.getPlayer();
            BlockPos pos = context.getClickedPos();
            // Delegate to WorldEdit session
            WorldEditIntegration.handleWandRightClick(player, pos);
        }
        return InteractionResult.SUCCESS;
    }
    
    // Left-click handled in packet handler modification
}
```

**Registration**:
- Add to `Items.java`: `public static final Item WAND = register("wand", new WandItem(new Item.Properties()));`
- Add to `CreativeModeTabs.OP_BLOCKS`: `output.accept(Items.WAND);` (after command blocks)

**Texture**: Use vanilla stick texture (`minecraft:item/stick`)

### 2. WorldEdit Integration Class

**Location**: `net.minecraft.worldedit.platform.WorldEditIntegration`

Central coordination point for all vanilla-to-WorldEdit interactions:

```java
public class WorldEditIntegration {
    private static WorldEdit instance;
    private static SessionManager sessionManager;
    private static MattMCPlatform platform;
    
    public static void initialize(MinecraftServer server) {
        // Initialize WorldEdit core systems
        instance = WorldEdit.getInstance();
        platform = new MattMCPlatform(server);
        sessionManager = new SessionManager(platform);
        
        // Register platform
        instance.getPlatformManager().register(platform);
        
        // Load configuration
        loadConfiguration();
        
        // Register commands
        registerCommands(server);
    }
    
    public static void handleWandLeftClick(ServerPlayer player, BlockPos pos) {
        LocalSession session = sessionManager.get(player);
        // Set secondary position
        session.getRegionSelector(world).selectSecondary(pos);
    }
    
    public static void handleWandRightClick(ServerPlayer player, BlockPos pos) {
        LocalSession session = sessionManager.get(player);
        // Set primary position
        session.getRegionSelector(world).selectPrimary(pos);
    }
    
    public static void onLeftClickAir(ServerPlayer player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        // Check if player has a WorldEdit tool
        LocalSession session = sessionManager.get(player);
        Tool tool = session.getTool(stack.getItem());
        if (tool != null) {
            tool.actPrimary(platform, player, session);
        }
    }
    
    // ... more integration methods
}
```

### 3. Platform Implementation

**Location**: `net.minecraft.worldedit.platform.MattMCPlatform`

Implements WorldEdit's `Platform` interface:

```java
public class MattMCPlatform extends AbstractPlatform {
    private final MinecraftServer server;
    private final MattMCConfiguration config;
    private final CommandManager commandManager;
    
    @Override
    public String getName() {
        return "MattMC-WorldEdit";
    }
    
    @Override
    public World matchWorld(String name) {
        ServerLevel level = server.getLevel(parseDimension(name));
        return level != null ? new MattMCWorld(level) : null;
    }
    
    @Override
    public void setGameMode(Player player, GameMode gameMode) {
        ServerPlayer mcPlayer = ((MattMCPlayer) player).getPlayer();
        mcPlayer.setGameMode(convertGameMode(gameMode));
    }
    
    // ... implement all Platform methods
}
```

---

## Command System

### Brigadier Integration

WorldEdit uses its own command system (Piston), but we'll integrate it with Minecraft's Brigadier:

**Location**: `net.minecraft.server.commands.WorldEditCommands`

```java
public class WorldEditCommands {
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        // Get all WorldEdit commands
        CommandManager weCommandManager = WorldEdit.getInstance()
            .getPlatformManager()
            .getPlatformCommandManager()
            .getCommandManager();
        
        // Register each command with Brigadier
        for (Command command : weCommandManager.getAllCommands()) {
            registerCommand(dispatcher, command);
        }
    }
    
    private static void registerCommand(
            CommandDispatcher<CommandSourceStack> dispatcher, 
            Command command) {
        // Convert WorldEdit command to Brigadier format
        LiteralArgumentBuilder<CommandSourceStack> builder = 
            Commands.literal(command.getName())
                .requires(source -> hasPermission(source, command))
                .executes(context -> executeCommand(context, command));
        
        dispatcher.register(builder);
    }
}
```

**Integration Point**: Call `WorldEditCommands.register()` in `Commands.fillUsableCommands()`

### Major Command Categories

All commands from WorldEdit will be ported:

1. **Selection Commands** (`//pos1`, `//pos2`, `//chunk`, `//expand`, `//contract`, etc.)
2. **Region Commands** (`//set`, `//replace`, `//overlay`, `//walls`, `//faces`, etc.)
3. **Clipboard Commands** (`//copy`, `//cut`, `//paste`, `//rotate`, `//flip`)
4. **Generation Commands** (`//hcyl`, `//sphere`, `//pyramid`, `//generate`)
5. **Utility Commands** (`//fill`, `//fillr`, `//drain`, `//fixwater`, `//fixlava`)
6. **Tool Commands** (`//wand`, `/tool`, `/brush`, `/mask`)
7. **History Commands** (`//undo`, `//redo`, `//clearhistory`)
8. **Schematic Commands** (`//schematic load`, `//schematic save`, `//schematic list`)
9. **Navigation Commands** (`//unstuck`, `//ascend`, `//descend`, `//thru`, `//jumpto`)
10. **Snapshot Commands** (for world restoration)
11. **Brush Commands** (sphere brush, cylinder brush, smooth brush, etc.)
12. **Scripting Commands** (CraftScript support)

---

## Item & Tool System

### Tool Management

**Location**: `net.minecraft.worldedit.tool.ToolManager`

Maps items to WorldEdit tools per player session:

```java
public class ToolManager {
    private Map<Item, Tool> toolBindings = new HashMap<>();
    
    public void bindTool(Item item, Tool tool) {
        toolBindings.put(item, tool);
    }
    
    public Tool getTool(Item item) {
        return toolBindings.get(item);
    }
    
    public boolean hasTool(Item item) {
        return toolBindings.containsKey(item);
    }
}
```

### Tool Types

1. **Selection Wand** (custom wand item)
   - Left-click: Set secondary position
   - Right-click: Set primary position

2. **Navigation Wand** (bindable to any item)
   - Left-click: Jump through blocks
   - Right-click: Jump to top block

3. **Far Wand** (bindable to any item)
   - Long-range block placement/breaking

4. **Tree Tool** (bindable to any item)
   - Plant or remove trees instantly

5. **Replacer Tool** (bindable to any item)
   - Replace clicked block with held block

6. **Data Cycler** (bindable to any item)
   - Cycle through block states

7. **Flood Fill** (bindable to any item)
   - Fill connected blocks of same type

8. **Brush Tools** (bindable to any item)
   - Sphere brush, cylinder brush, smooth brush, etc.
   - Configurable size, pattern, mask

### Wand Behavior

The wand item specifically:
- Cannot be used in survival mode (OP only)
- Shows selection size in chat
- Visual feedback for selection points (particle effects)
- Persists selection across sessions
- Works across dimensions

---

## Session Management

### LocalSession Architecture

Each player gets a `LocalSession` that persists:

```java
public class LocalSession {
    // Selection state
    private Map<World, RegionSelector> selectors;
    private RegionSelectorType defaultSelector;
    
    // Clipboard state
    private ClipboardHolder clipboard;
    private Transform clipboardTransform;
    
    // Tool bindings
    private ToolManager toolManager;
    
    // History
    private LinkedList<EditSession> history;
    private int historyPointer;
    private int maxHistorySize;
    
    // Preferences
    private Mask defaultMask;
    private int defaultChangeLimit;
    private boolean useInventory;
    private boolean fastMode;
    
    // State tracking
    private boolean wandItem; // has wand
    private boolean superPickaxe;
    private SuperPickaxeMode pickaxeMode;
    
    // Brush state
    private Map<Item, BrushTool> brushes;
}
```

### Session Persistence

**Location**: `net.minecraft.worldedit.session.SessionStore`

Sessions saved to: `<world>/worldedit/sessions/<uuid>.json`

Persistence format:
```json
{
  "version": 1,
  "selector_type": "cuboid",
  "selections": {
    "minecraft:overworld": {
      "pos1": [100, 64, 200],
      "pos2": [150, 80, 250]
    }
  },
  "history_size": 15,
  "tool_bindings": {
    "minecraft:wooden_axe": "selection_wand",
    "minecraft:diamond_pickaxe": "super_pickaxe"
  },
  "preferences": {
    "fast_mode": false,
    "use_inventory": false
  }
}
```

### Session Lifecycle

1. **Player Join**: Load or create session
2. **During Play**: Update session state in memory
3. **Periodic Save**: Auto-save every 5 minutes
4. **Player Leave**: Save session to disk
5. **Server Shutdown**: Save all sessions

---

## Region Selection

### Region Selectors

Implement all WorldEdit selector types:

1. **Cuboid** (default): Two corner points
2. **Extend**: Cuboid that extends with each click
3. **Poly**: 2D polygon selection
4. **Ellipsoid**: Sphere/ellipse defined by center and radius points
5. **Sphere**: Perfect sphere selection
6. **Cylinder**: Cylindrical selection
7. **Convex**: Convex polyhedron

**Location**: `net.minecraft.worldedit.region.selector.*`

### Selection Visualization

Since we can't rely on client-side mods, use server-side visualization:

1. **Particle Effects**: Send particle packets to show selection corners
2. **Chat Messages**: Detailed selection info in chat
3. **Title/Subtitle**: Quick selection size updates
4. **Boss Bar**: Show selection dimensions (optional)

**Example**:
```java
public void visualizeSelection(ServerPlayer player, Region region) {
    // Send particles at corners
    BlockVector3 min = region.getMinimumPoint();
    BlockVector3 max = region.getMaximumPoint();
    
    sendParticle(player, min, ParticleTypes.FLAME);
    sendParticle(player, max, ParticleTypes.FLAME);
    
    // Send dimensions in chat
    int volume = region.getVolume();
    player.sendSystemMessage(
        Component.literal("Selection: " + volume + " blocks")
    );
}
```

---

## Block Manipulation

### EditSession

**Location**: `net.minecraft.worldedit.core.EditSession`

The core class for all block manipulation operations:

```java
public class EditSession implements Extent {
    private final World world;
    private final ChangeSet changeSet;
    private final BlockBag blockBag;
    private int changeLimit;
    private boolean fastMode;
    
    // Core operations
    public boolean setBlock(BlockVector3 pos, BlockState block);
    public BlockState getBlock(BlockVector3 pos);
    
    // Bulk operations
    public int setBlocks(Region region, Pattern pattern);
    public int replaceBlocks(Region region, Mask mask, Pattern pattern);
    
    // Advanced operations
    public int makeCuboidFaces(Region region, Pattern pattern);
    public int makeCuboidWalls(Region region, Pattern pattern);
    public int overlayCuboidBlocks(Region region, Pattern pattern);
    
    // History tracking
    public void undo(EditSession other);
    public void redo(EditSession other);
}
```

### Fast Mode

When enabled, suppresses:
- Block updates
- Light updates
- Physics updates
- Neighbor notifications

Implemented via the `LevelChunk` modification mentioned earlier.

### Change Tracking

Every block change recorded in `ChangeSet`:

```java
public interface ChangeSet {
    void add(Change change);
    Iterator<Change> backwardIterator();
    Iterator<Change> forwardIterator();
    int size();
}
```

### Extent Pipeline

Operations flow through a pipeline of extents:

```
Player Command
     ↓
EditSession (main API)
     ↓
ChangeSetExtent (history tracking)
     ↓
BlockChangeLimiter (enforces limits)
     ↓
ChunkBatchingExtent (performance optimization)
     ↓
SurvivalModeExtent (inventory handling)
     ↓
MattMCWorld (actual world manipulation)
```

---

## Clipboard & Schematics

### Clipboard System

**Location**: `net.minecraft.worldedit.clipboard.*`

```java
public interface Clipboard extends Extent {
    Region getRegion();
    BlockVector3 getOrigin();
    void setOrigin(BlockVector3 origin);
    
    // Entity support
    List<Entity> getEntities();
    void addEntity(Entity entity);
}
```

### Schematic Formats

Support multiple schematic formats:

1. **Sponge Schematic** (`.schem`) - Modern format, recommended
2. **MCEdit Schematic** (`.schematic`) - Legacy format
3. **Structure Blocks** (`.nbt`) - Vanilla format compatibility

**Location**: `net.minecraft.worldedit.clipboard.io.*`

### Clipboard Operations

1. **Copy**: `//copy [-e] [-b]`
   - `-e`: Include entities
   - `-b`: Copy biomes

2. **Cut**: `//cut [-e] [-b]`
   - Same as copy but removes blocks

3. **Paste**: `//paste [-a] [-e] [-b] [-o]`
   - `-a`: Skip air blocks
   - `-e`: Paste entities
   - `-b`: Paste biomes
   - `-o`: Paste at original position

4. **Rotate**: `//rotate <y> [x] [z]`
   - Rotate clipboard around axes

5. **Flip**: `//flip [direction]`
   - Mirror clipboard

### Schematic Storage

Schematics saved to: `<world>/worldedit/schematics/`

**File Organization**:
```
worldedit/
  schematics/
    player_builds/
      castle.schem
      house.schem
    shared/
      templates/
        bridge.schem
```

### Clipboard Sharing

Optional feature: Players can share clipboards

```java
public class ClipboardShare {
    private Map<String, ClipboardHolder> sharedClipboards;
    
    public void share(String name, ClipboardHolder clipboard) {
        sharedClipboards.put(name, clipboard);
    }
    
    public ClipboardHolder getShared(String name) {
        return sharedClipboards.get(name);
    }
}
```

---

## Brushes & Tools

### Brush System

**Location**: `net.minecraft.worldedit.tool.brush.*`

```java
public interface Brush {
    void build(EditSession session, BlockVector3 position, 
               Pattern pattern, double size);
}
```

### Brush Types

1. **Sphere Brush**: `//brush sphere <pattern> [radius]`
   - Creates spheres of blocks
   - Supports hollow mode

2. **Cylinder Brush**: `//brush cylinder <pattern> [radius] [height]`
   - Creates cylinders
   - Vertical or horizontal

3. **Clipboard Brush**: `//brush clipboard`
   - Pastes clipboard on click

4. **Smooth Brush**: `//brush smooth [size] [iterations]`
   - Smooths terrain

5. **Gravity Brush**: `//brush gravity [radius]`
   - Simulates gravity on blocks

6. **Deform Brush**: `//brush deform <expression>`
   - Custom deformations via expressions

7. **Raise/Lower Brush**: `//brush raise/lower <size>`
   - Terrain sculpting

8. **Replace Brush**: `//brush replace <mask> <pattern> [radius]`
   - Selective block replacement

### Brush Configuration

Each brush has:
- **Size**: Radius of effect
- **Pattern**: What blocks to place
- **Mask**: What blocks to affect
- **Range**: How far the brush can reach

```java
public class BrushTool implements Tool {
    private Brush brush;
    private Pattern pattern;
    private Mask mask;
    private double size;
    private int range;
    
    public void configure(Brush brush, Pattern pattern, 
                         Mask mask, double size) {
        this.brush = brush;
        this.pattern = pattern;
        this.mask = mask;
        this.size = size;
    }
}
```

### Brush Usage

1. Bind brush to item: `//brush sphere stone 5`
2. Set mask (optional): `/mask grass_block`
3. Set range: `/range 100`
4. Right-click to use brush

---

## History & Undo/Redo

### History Implementation

**Location**: `net.minecraft.worldedit.history.*`

Each edit session is recorded:

```java
public class EditHistory {
    private LinkedList<EditSession> history;
    private int position;
    private int maxSize;
    
    public void record(EditSession session) {
        // Remove any redo history
        while (history.size() > position) {
            history.removeLast();
        }
        
        // Add new session
        history.add(session);
        position++;
        
        // Enforce size limit
        while (history.size() > maxSize) {
            history.removeFirst();
            position--;
        }
    }
    
    public EditSession undo() {
        if (position > 0) {
            position--;
            return history.get(position);
        }
        return null;
    }
    
    public EditSession redo() {
        if (position < history.size()) {
            EditSession session = history.get(position);
            position++;
            return session;
        }
        return null;
    }
}
```

### Change Tracking

Two main change types:

1. **Block Changes**: Position + old state + new state
2. **Entity Changes**: Entity data + position

```java
public interface Change {
    void undo(UndoContext context);
    void redo(UndoContext context);
}

public class BlockChange implements Change {
    private BlockVector3 position;
    private BlockState previous;
    private BlockState current;
    
    @Override
    public void undo(UndoContext context) {
        context.getExtent().setBlock(position, previous);
    }
    
    @Override
    public void redo(UndoContext context) {
        context.getExtent().setBlock(position, current);
    }
}
```

### History Optimization

Large edits are memory-intensive. Optimizations:

1. **Block Compression**: Group consecutive same-block changes
2. **Disk Spill**: Move old history to disk
3. **Smart Limits**: Adaptive limits based on available memory
4. **Delta Encoding**: Store only differences for similar blocks

```java
public class BlockOptimizedHistory implements ChangeSet {
    // Compressed storage for repeated blocks
    private Map<BlockState, List<BlockVector3>> blockMap;
    
    public void add(Change change) {
        if (change instanceof BlockChange bc) {
            // Group by block type for compression
            blockMap.computeIfAbsent(bc.getPrevious(), 
                k -> new ArrayList<>()).add(bc.getPosition());
        }
    }
}
```

### Commands

- `//undo [times] [player]`: Undo last edit(s)
- `//redo [times]`: Redo undone edit(s)
- `//clearhistory`: Clear all history
- `/history list`: Show edit history

---

## Permissions & Configuration

### Permission System

**Location**: `net.minecraft.worldedit.platform.permissions.*`

Since MattMC doesn't have a permission plugin system by default, implement a simple one:

```java
public class PermissionManager {
    private Map<UUID, Set<String>> playerPermissions;
    private Set<String> opPermissions;
    
    public boolean hasPermission(Player player, String permission) {
        // OPs have all permissions
        if (isOp(player)) return true;
        
        // Check player-specific permissions
        Set<String> perms = playerPermissions.get(player.getUniqueId());
        return perms != null && perms.contains(permission);
    }
    
    public void grantPermission(Player player, String permission) {
        playerPermissions.computeIfAbsent(
            player.getUniqueId(), 
            k -> new HashSet<>()
        ).add(permission);
    }
}
```

### Permission Nodes

```
worldedit.selection.*        - All selection commands
worldedit.region.*           - All region operations
worldedit.clipboard.*        - Clipboard operations
worldedit.history.*          - Undo/redo
worldedit.generation.*       - Generation commands
worldedit.schematic.*        - Schematic operations
worldedit.tool.*             - Tool usage
worldedit.brush.*            - Brush usage
worldedit.navigation.*       - Navigation commands
worldedit.scripting.*        - Script execution
worldedit.admin              - Administrative commands
```

### Configuration

**Location**: `<world>/worldedit/config.yml`

```yaml
# WorldEdit Configuration for MattMC

limits:
  # Maximum number of blocks that can be changed in one edit
  max-blocks-changed:
    default: 1000000
    maximum: -1  # -1 for unlimited
  
  # Maximum number of entities affected
  max-entities: 1000
  
  # Maximum polygon points
  max-polygon-points: 20
  
  # Maximum brush radius
  max-brush-radius: 10
  
  # Maximum super pickaxe size
  max-super-pickaxe-size: 5

history:
  # Number of edit sessions to keep in memory
  size: 15
  
  # Expire history after this many minutes of inactivity
  expiration: 10

sessions:
  # Timeout sessions after this many minutes
  timeout: 10
  
  # Save sessions to disk
  save: true

tools:
  # Allow all items to be bound as tools
  allow-all-items: false
  
  # Max range for tool usage
  max-range: 100
  
  # Wand item (custom wand item)
  wand-item: "minecraft:wand"

navigation:
  # Allow unstuck command
  allow-unstuck: true
  
  # Allow jumpto command
  allow-jumpto: true
  
  # Max distance for navigation
  max-distance: 100

performance:
  # Use fast mode by default (suppress updates)
  fast-mode: false
  
  # Chunk batching for better performance
  chunk-batching: true

paths:
  # Where to store schematics
  schematics: "worldedit/schematics"
  
  # Where to store sessions
  sessions: "worldedit/sessions"
  
  # Where to store snapshots
  snapshots: "worldedit/snapshots"

logging:
  # Log all WorldEdit actions
  log-commands: false
  
  # File to log to
  log-file: "worldedit/worldedit.log"
```

---

## Phase Breakdown

### Phase 1: Foundation (Week 1-2)

**Goal**: Set up core infrastructure

1. Create package structure under `net.minecraft.worldedit`
2. Port core WorldEdit classes (WorldEdit, EditSession, LocalSession)
3. Implement basic platform abstraction (MattMCPlatform, MattMCWorld, MattMCPlayer)
4. Add WorldEdit initialization to MinecraftServer
5. Create WorldEditIntegration coordination class
6. Implement basic session management

**Deliverables**:
- WorldEdit instance initializes on server start
- Basic platform integration working
- Can create and retrieve player sessions

### Phase 2: Selection System (Week 3)

**Goal**: Implement region selection

1. Create custom wand item
2. Register wand in Items and CreativeModeTabs
3. Modify ServerGamePacketListenerImpl for click handling
4. Implement RegionSelector types (cuboid first, others later)
5. Add selection visualization
6. Port selection commands (`//pos1`, `//pos2`, `//chunk`, etc.)

**Deliverables**:
- Wand item exists and is obtainable
- Left/right click sets selection points
- Selection visible via particles and chat
- Basic selection commands work

### Phase 3: Basic Block Manipulation (Week 4)

**Goal**: Core editing functionality

1. Modify LevelChunk for update suppression
2. Implement EditSession extent pipeline
3. Port basic region commands (`//set`, `//replace`, `//overlay`)
4. Implement pattern system
5. Implement mask system
6. Add change tracking for history

**Deliverables**:
- Can fill regions with blocks
- Can replace blocks with patterns
- Basic masks work
- Changes are tracked

### Phase 4: History & Undo (Week 5)

**Goal**: Undo/redo system

1. Implement ChangeSet and Change classes
2. Create EditHistory per session
3. Port undo/redo commands
4. Implement history optimization (compression, limits)
5. Add history persistence

**Deliverables**:
- `//undo` and `//redo` commands work
- History limited properly
- History persists across sessions

### Phase 5: Clipboard System (Week 6-7)

**Goal**: Copy/paste functionality

1. Implement Clipboard interface and implementations
2. Port copy/cut/paste commands
3. Implement rotation and flip
4. Add schematic I/O (Sponge format first)
5. Implement clipboard transformation
6. Add entity support

**Deliverables**:
- Can copy and paste regions
- Can rotate and flip
- Can save/load schematics
- Entities are preserved

### Phase 6: Advanced Commands (Week 8-9)

**Goal**: Full command coverage

1. Port generation commands (`//hcyl`, `//sphere`, `//pyramid`, etc.)
2. Port utility commands (`//fill`, `//drain`, `//fixwater`, etc.)
3. Port navigation commands (`//ascend`, `//descend`, `//thru`, etc.)
4. Implement expression system for `//generate`
5. Port advanced region operations (`//stack`, `//move`, `//smooth`)

**Deliverables**:
- All major command categories implemented
- Generation commands work
- Navigation commands work
- Advanced operations functional

### Phase 7: Tools & Brushes (Week 10-11)

**Goal**: Tool system

1. Implement Tool interface and tool manager
2. Port all tool types (far wand, tree, replacer, etc.)
3. Implement brush system
4. Port all brush types
5. Add tool binding commands
6. Implement brush configuration (size, pattern, mask, range)

**Deliverables**:
- All tools can be bound to items
- All brush types work
- Brush configuration functional
- Super pickaxe mode works

### Phase 8: Configuration & Permissions (Week 12)

**Goal**: Admin features

1. Implement configuration loading (YAML)
2. Create permission system
3. Add configurable limits
4. Implement block logging (optional)
5. Add admin commands
6. Performance profiling and optimization

**Deliverables**:
- Config file controls all settings
- Permissions work for all commands
- Limits are enforced
- Performance is acceptable

### Phase 9: Polish & Testing (Week 13-14)

**Goal**: Production readiness

1. Comprehensive testing of all features
2. Bug fixing
3. Documentation completion
4. Performance optimization
5. Edge case handling
6. Error messages and user feedback

**Deliverables**:
- All features tested and working
- Performance benchmarks met
- Documentation complete
- Ready for production use

### Phase 10: Advanced Features (Week 15+)

**Goal**: Optional enhancements

1. Snapshot/restore system
2. CraftScript support
3. Multi-world clipboard sharing
4. Async operation support
5. Web-based schematic browser
6. Integration with vanilla structure blocks

**Deliverables**:
- Advanced features as desired
- Extended functionality beyond basic WorldEdit

---

## File Structure

Complete file tree for the implementation:

```
src/main/java/net/minecraft/
├── worldedit/
│   ├── core/
│   │   ├── WorldEdit.java                    # Main WorldEdit singleton
│   │   ├── EditSession.java                  # Core editing operations
│   │   ├── EditSessionBuilder.java           # Builder for EditSession
│   │   └── LocalSession.java                 # Player session state
│   │
│   ├── platform/
│   │   ├── MattMCPlatform.java               # Platform implementation
│   │   ├── MattMCWorld.java                  # World adapter
│   │   ├── MattMCPlayer.java                 # Player adapter
│   │   ├── MattMCBlockAdapter.java           # Block conversion
│   │   ├── MattMCEntityAdapter.java          # Entity operations
│   │   ├── MattMCNBTAdapter.java             # NBT conversion
│   │   └── WorldEditIntegration.java         # Central integration point
│   │
│   ├── command/
│   │   ├── SelectionCommands.java            # //pos, //chunk, etc.
│   │   ├── RegionCommands.java               # //set, //replace, etc.
│   │   ├── ClipboardCommands.java            # //copy, //paste, etc.
│   │   ├── GenerationCommands.java           # //hcyl, //sphere, etc.
│   │   ├── UtilityCommands.java              # //fill, //drain, etc.
│   │   ├── HistoryCommands.java              # //undo, //redo, etc.
│   │   ├── ToolCommands.java                 # /tool, /mask, etc.
│   │   ├── BrushCommands.java                # //brush commands
│   │   ├── NavigationCommands.java           # //ascend, //thru, etc.
│   │   ├── SchematicCommands.java            # //schem commands
│   │   ├── SnapshotCommands.java             # //snapshot commands
│   │   └── AdminCommands.java                # WorldEdit admin
│   │
│   ├── tool/
│   │   ├── Tool.java                         # Tool interface
│   │   ├── ToolManager.java                  # Tool binding system
│   │   ├── SelectionWand.java                # Wand tool
│   │   ├── NavigationWand.java               # Nav wand
│   │   ├── FarWand.java                      # Long range tool
│   │   ├── TreeTool.java                     # Tree placer
│   │   ├── ReplacerTool.java                 # Block replacer
│   │   ├── DataCycler.java                   # State cycler
│   │   ├── FloodFillTool.java                # Flood fill
│   │   ├── BrushTool.java                    # Brush container
│   │   └── SuperPickaxe.java                 # Super pickaxe modes
│   │
│   ├── brush/
│   │   ├── Brush.java                        # Brush interface
│   │   ├── SphereBrush.java                  # Sphere brush
│   │   ├── CylinderBrush.java                # Cylinder brush
│   │   ├── ClipboardBrush.java               # Clipboard brush
│   │   ├── SmoothBrush.java                  # Smoothing brush
│   │   ├── GravityBrush.java                 # Gravity simulation
│   │   ├── DeformBrush.java                  # Deformation brush
│   │   └── ButcherBrush.java                 # Entity removal
│   │
│   ├── session/
│   │   ├── SessionManager.java               # Session lifecycle
│   │   ├── SessionStore.java                 # Session persistence
│   │   └── ClipboardShare.java               # Shared clipboards
│   │
│   ├── extent/
│   │   ├── Extent.java                       # Block access interface
│   │   ├── AbstractExtent.java               # Base implementation
│   │   ├── ChangeSetExtent.java              # History tracking
│   │   ├── BlockChangeLimiter.java           # Limit enforcement
│   │   ├── MaskingExtent.java                # Mask application
│   │   ├── ChunkBatchingExtent.java          # Performance optimization
│   │   ├── SurvivalModeExtent.java           # Inventory handling
│   │   └── UpdateSuppressionExtent.java      # Update control
│   │
│   ├── region/
│   │   ├── Region.java                       # Region interface
│   │   ├── CuboidRegion.java                 # Cuboid selection
│   │   ├── EllipsoidRegion.java              # Ellipsoid selection
│   │   ├── CylindricalRegion.java            # Cylinder selection
│   │   ├── Polygonal2DRegion.java            # 2D polygon
│   │   ├── ConvexPolyhedralRegion.java       # 3D polyhedron
│   │   ├── selector/
│   │   │   ├── RegionSelector.java           # Selector interface
│   │   │   ├── CuboidRegionSelector.java     # Cuboid selector
│   │   │   ├── ExtendingCuboidSelector.java  # Extending cuboid
│   │   │   ├── PolygonalRegionSelector.java  # Polygon selector
│   │   │   ├── EllipsoidRegionSelector.java  # Ellipsoid selector
│   │   │   └── SphereRegionSelector.java     # Sphere selector
│   │   └── iterator/
│   │       ├── RegionIterator.java           # Iterate region blocks
│   │       └── FlatRegionIterator.java       # 2D iteration
│   │
│   ├── history/
│   │   ├── ChangeSet.java                    # Change set interface
│   │   ├── Change.java                       # Individual change
│   │   ├── BlockChange.java                  # Block change
│   │   ├── EntityChange.java                 # Entity change
│   │   ├── EditHistory.java                  # History management
│   │   ├── BlockOptimizedHistory.java        # Compressed history
│   │   └── UndoContext.java                  # Undo execution context
│   │
│   ├── clipboard/
│   │   ├── Clipboard.java                    # Clipboard interface
│   │   ├── BlockArrayClipboard.java          # Array-based impl
│   │   ├── ClipboardHolder.java              # Clipboard + transform
│   │   ├── io/
│   │   │   ├── ClipboardFormat.java          # Format interface
│   │   │   ├── ClipboardReader.java          # Reader interface
│   │   │   ├── ClipboardWriter.java          # Writer interface
│   │   │   ├── SpongeSchematicReader.java    # .schem format
│   │   │   ├── SpongeSchematicWriter.java    # .schem format
│   │   │   ├── MCEditSchematicReader.java    # .schematic format
│   │   │   └── MCEditSchematicWriter.java    # .schematic format
│   │   └── transform/
│   │       ├── ClipboardTransform.java       # Transformation
│   │       └── BlockTransform.java           # Block rotation/flip
│   │
│   ├── function/
│   │   ├── pattern/
│   │   │   ├── Pattern.java                  # Pattern interface
│   │   │   ├── BlockPattern.java             # Single block
│   │   │   ├── RandomPattern.java            # Random blocks
│   │   │   ├── ClipboardPattern.java         # Clipboard as pattern
│   │   │   └── ExpressionPattern.java        # Math expression
│   │   ├── mask/
│   │   │   ├── Mask.java                     # Mask interface
│   │   │   ├── BlockMask.java                # Block type mask
│   │   │   ├── ExistingBlockMask.java        # Non-air mask
│   │   │   ├── RegionMask.java               # Region boundary
│   │   │   ├── ExpressionMask.java           # Math expression
│   │   │   └── BiomeMask.java                # Biome filter
│   │   ├── operation/
│   │   │   ├── Operation.java                # Operation interface
│   │   │   ├── ForwardExtentCopy.java        # Copy operation
│   │   │   └── Operations.java               # Operation utilities
│   │   └── visitor/
│   │       ├── RegionVisitor.java            # Visit all blocks
│   │       ├── RecursiveVisitor.java         # Flood fill visitor
│   │       └── DownwardVisitor.java          # Gravity simulator
│   │
│   ├── math/
│   │   ├── BlockVector3.java                 # Integer 3D vector
│   │   ├── Vector3.java                      # Double 3D vector
│   │   ├── Vector2.java                      # 2D vector
│   │   ├── transform/
│   │   │   ├── Transform.java                # Transformation
│   │   │   ├── AffineTransform.java          # Affine transformation
│   │   │   └── CombinedTransform.java        # Combined transforms
│   │   └── interpolation/
│   │       └── Interpolation.java            # Curve interpolation
│   │
│   ├── expression/
│   │   ├── Expression.java                   # Math expression
│   │   ├── ExpressionParser.java             # Expression parsing
│   │   ├── ExpressionCompiler.java           # Compilation
│   │   └── ExpressionEnvironment.java        # Variable context
│   │
│   ├── config/
│   │   ├── WorldEditConfiguration.java       # Main config
│   │   ├── YamlConfiguration.java            # YAML loading
│   │   └── ConfigurationNode.java            # Config tree
│   │
│   ├── permission/
│   │   ├── PermissionManager.java            # Permission handling
│   │   ├── PermissionProvider.java           # Provider interface
│   │   └── Permissions.java                  # Permission constants
│   │
│   └── util/
│       ├── BlockCategories.java              # Block tag helpers
│       ├── Location.java                     # Location wrapper
│       ├── Direction.java                    # Direction enum
│       ├── SideEffect.java                   # Side effect flags
│       └── formatting/
│           └── text/
│               ├── Component.java            # Text component
│               └── TextComponent.java        # Simple text
│
├── world/item/
│   ├── WandItem.java                         # Custom wand item
│   └── Items.java                            # Modified: register WAND
│
├── world/item/
│   └── CreativeModeTabs.java                 # Modified: add WAND to OP_BLOCKS
│
├── server/
│   ├── MinecraftServer.java                  # Modified: init WorldEdit, implement Watchdog
│   └── network/
│       └── ServerGamePacketListenerImpl.java # Modified: tool activation
│
├── world/level/chunk/
│   └── LevelChunk.java                       # Modified: update suppression
│
└── server/commands/
    └── Commands.java                         # Modified: register WE commands
```

---

## Testing Strategy

### Unit Testing

Create test suite under `src/test/java/net/minecraft/worldedit/`:

1. **Math Tests**: Vector operations, transformations
2. **Region Tests**: Selection algorithms, containment checks
3. **Pattern Tests**: Pattern generation, randomization
4. **Mask Tests**: Mask evaluation, combinations
5. **History Tests**: Undo/redo correctness
6. **Clipboard Tests**: Copy/paste accuracy, transformations

### Integration Testing

1. **Command Tests**: Execute commands, verify results
2. **Tool Tests**: Simulate tool usage
3. **Brush Tests**: Brush application accuracy
4. **Schematic Tests**: Save/load round-trip
5. **Session Tests**: Session persistence

### Performance Testing

Benchmark critical operations:

1. **Fill Operations**: Time to fill various region sizes
2. **Replace Operations**: Replacement speed with patterns/masks
3. **Copy/Paste**: Large clipboard performance
4. **History Size**: Memory usage for history
5. **Undo Speed**: Time to undo large operations

**Targets**:
- Fill 1M blocks: < 5 seconds
- Copy 100K blocks: < 2 seconds
- Paste 100K blocks: < 3 seconds
- Undo 1M blocks: < 6 seconds

### Manual Testing Scenarios

1. **Basic Selection**: Use wand to select regions, verify bounds
2. **Fill Region**: Fill with single block, verify all blocks changed
3. **Pattern Fill**: Fill with random pattern, verify distribution
4. **Masked Replace**: Replace with mask, verify only masked blocks changed
5. **Copy/Paste**: Copy structure, paste elsewhere, verify accuracy
6. **Rotation**: Rotate clipboard, verify correct orientation
7. **Undo Chain**: Make multiple edits, undo all, verify restoration
8. **Brush Usage**: Use various brushes, verify results
9. **Cross-Dimension**: Select in overworld, paste in nether
10. **Session Persistence**: Set selection, logout, login, verify selection restored

---

## Risk Mitigation

### Potential Risks

1. **Performance Degradation**
   - **Risk**: Large edits cause server lag
   - **Mitigation**: Implement chunk batching, fast mode, async operations

2. **Memory Issues**
   - **Risk**: History consumes too much memory
   - **Mitigation**: Configurable history size, compression, disk spill

3. **Vanilla Conflicts**
   - **Risk**: Modifications break vanilla features
   - **Mitigation**: Minimal invasive changes, thorough testing, fallback paths

4. **Command Conflicts**
   - **Risk**: WorldEdit commands clash with vanilla
   - **Mitigation**: Use `//` prefix, check for conflicts during registration

5. **Save Corruption**
   - **Risk**: Bugs cause world corruption
   - **Mitigation**: Extensive testing, backup recommendations, safe mode

6. **Permission Bypass**
   - **Risk**: Players access restricted commands
   - **Mitigation**: Strict permission checks, default-deny, audit logging

### Contingency Plans

1. **Performance Issues**: Add rate limiting, operation queuing, chunk-per-tick limits
2. **Bugs in Production**: Quick rollback mechanism, disable specific commands
3. **Integration Problems**: Abstraction layers allow swapping implementations
4. **User Confusion**: Comprehensive help system, example commands, tutorials

---

## Success Criteria

The implementation will be considered complete when:

1. ✅ All major WorldEdit commands are functional
2. ✅ Custom wand item works for selections
3. ✅ Copy/paste operations preserve blocks and entities accurately
4. ✅ Undo/redo works for all operation types
5. ✅ All brush types are implemented and functional
6. ✅ Schematics can be saved and loaded in Sponge format
7. ✅ Performance targets are met for large operations
8. ✅ No vanilla functionality is broken
9. ✅ Configuration system controls all aspects
10. ✅ Permissions properly restrict access
11. ✅ Sessions persist across logouts
12. ✅ Documentation is complete and accurate

---

## Conclusion

This plan provides a comprehensive roadmap for integrating WorldEdit functionality directly into MattMC without using mixins. The approach leverages direct source code access to create a native, high-performance implementation that surpasses what would be possible with a traditional mod.

**Key Advantages of This Approach:**

1. **Performance**: Direct access to internals eliminates abstraction overhead
2. **Reliability**: No mixin conflicts or version compatibility issues
3. **Integration**: Seamless part of the base game, not an add-on
4. **Customization**: Full control over every aspect of implementation
5. **Optimization**: Can optimize specifically for MattMC's codebase

**Estimated Timeline**: 14-15 weeks for full implementation

**Resource Requirements**:
- 1 senior developer familiar with Minecraft internals
- Access to WorldEdit source code for reference
- Testing environment with various world sizes
- Documentation time for user guides

This implementation will make MattMC the most powerful Minecraft fork for creative building and world editing, with WorldEdit capabilities built right into the core game.
