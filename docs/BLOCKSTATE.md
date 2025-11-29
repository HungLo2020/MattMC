# BlockState System: MattMC vs Minecraft Java Edition

This document provides a detailed comparison between MattMC's blockstate system and Minecraft Java Edition's blockstate system. It covers architecture, performance, modularity, and recommendations for improvement.

## Table of Contents

1. [Overview](#overview)
2. [MattMC's BlockState System](#mattmcs-blockstate-system)
3. [Minecraft Java's BlockState System](#minecraft-javas-blockstate-system)
4. [Detailed Comparison](#detailed-comparison)
5. [Performance Analysis](#performance-analysis)
6. [Modularity Analysis](#modularity-analysis)
7. [Power & Flexibility Analysis](#power--flexibility-analysis)
8. [Recommendations for Improvement](#recommendations-for-improvement)

---

## Overview

| Aspect | MattMC | Minecraft Java |
|--------|--------|----------------|
| **Property System** | Dynamic `Map<String, Object>` | Static typed `IProperty<?>` |
| **State Caching** | None (states created on-demand) | All states pre-computed and cached |
| **Storage** | Sparse HashMap per chunk | Palette-based section storage |
| **Property Types** | Enums stored as objects | Strongly-typed IProperty system |
| **State Lookup** | String-based variant matching | Direct state reference |
| **Memory Model** | ~24 bytes per stateful block | ~4-8 bytes per block position |

---

## MattMC's BlockState System

### Architecture

MattMC uses a **simple, dynamic blockstate system** with two distinct concepts:

#### 1. Runtime BlockState (`mattmc.world.level.block.state.BlockState`)

Used during gameplay to store block properties in the world:

```java
public class BlockState {
    private final Map<String, Object> properties;  // Dynamic property storage
    
    public BlockState setValue(String property, Object value) {
        properties.put(property, value);
        return this;
    }
    
    public Object getValue(String property) {
        return properties.get(property);
    }
    
    // Typed accessors for common property types
    public Direction getDirection(String property) { ... }
    public Half getHalf(String property) { ... }
    public Axis getAxis(String property) { ... }
}
```

#### 2. Model BlockState (`mattmc.client.resources.model.BlockState`)

Used for resource loading to map property combinations to models:

```java
public class BlockState {
    private Map<String, Object> variants;  // "facing=north" -> model info
    
    public List<BlockStateVariant> getVariantsForState(String state) {
        return parsedVariants.get(state);
    }
}
```

### Property Types

MattMC defines four property enums:

```java
// mattmc.world.level.block.state.properties

public enum Direction { NORTH, SOUTH, WEST, EAST }      // 4 values
public enum Axis { X, Y, Z }                             // 3 values  
public enum Half { TOP, BOTTOM }                         // 2 values
public enum StairsShape { STRAIGHT, INNER_LEFT, ... }   // 5 values
```

### Storage Model

Blockstates are stored **sparsely** in chunks using a HashMap:

```java
public class LevelChunk {
    private final Block[][][] blocks;                    // Block type per position
    private final Map<Long, BlockState> blockStates;     // Only for stateful blocks
    
    private long getPositionKey(int x, int y, int z) {
        return ((long)x << 32) | ((long)y << 16) | (long)z;
    }
}
```

### Placement Example (Stairs)

```java
public class StairsBlock extends Block {
    @Override
    public BlockState getPlacementState(...) {
        BlockState state = new BlockState();
        
        // Determine facing from player direction
        Direction facing = calculateFacingFromPlayer(playerX, playerZ, blockX, blockZ);
        state.setValue("facing", facing);
        
        // Determine half from click position
        Half half = hitY > 0.5f ? Half.TOP : Half.BOTTOM;
        state.setValue("half", half);
        
        state.setValue("shape", StairsShape.STRAIGHT);
        return state;
    }
}
```

### Variant String Generation

For model lookup, blockstates convert to strings:

```java
public String toVariantString() {
    // Sort alphabetically for consistent lookup
    // "facing=north,half=bottom,shape=straight"
    return properties.entrySet().stream()
        .sorted(Map.Entry.comparingByKey())
        .map(e -> e.getKey() + "=" + e.getValue().toString().toLowerCase())
        .collect(Collectors.joining(","));
}
```

---

## Minecraft Java's BlockState System

### Architecture

Minecraft uses a **pre-computed, strongly-typed blockstate system** with several layers:

#### 1. Property Definition (`IProperty<T>`)

Properties are strongly-typed interfaces:

```java
public interface IProperty<T extends Comparable<T>> {
    String getName();
    Collection<T> getAllowedValues();
    Class<T> getValueClass();
    Optional<T> parseValue(String value);
}

// Implementations:
public class PropertyDirection implements IProperty<Direction> { ... }
public class PropertyBool implements IProperty<Boolean> { ... }
public class PropertyEnum<T extends Enum<T>> implements IProperty<T> { ... }
public class PropertyInteger implements IProperty<Integer> { ... }
```

#### 2. Block Class with State Definition

```java
public class StairBlock extends Block {
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final EnumProperty<Half> HALF = BlockStateProperties.HALF;
    public static final EnumProperty<StairsShape> SHAPE = BlockStateProperties.STAIRS_SHAPE;
    
    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, HALF, SHAPE);
    }
    
    public StairBlock(Properties props) {
        super(props);
        // Set default state
        registerDefaultState(stateDefinition.any()
            .setValue(FACING, Direction.NORTH)
            .setValue(HALF, Half.BOTTOM)
            .setValue(SHAPE, StairsShape.STRAIGHT));
    }
}
```

#### 3. StateDefinition (Pre-computed States)

Minecraft pre-generates ALL possible state combinations at startup:

```java
public class StateDefinition<O, S extends StateHolder<O, S>> {
    private final ImmutableSortedMap<String, IProperty<?>> properties;
    private final ImmutableList<S> states;  // ALL possible combinations
    
    // For stairs: 4 facing × 2 half × 5 shape = 40 cached states
    
    public S any() { return states.get(0); }
    
    public Collection<S> getPossibleStates() { return states; }
}
```

#### 4. BlockState (Immutable State Holder)

Each blockstate is an **immutable object** with pre-computed neighbor states:

```java
public class BlockState extends StateHolder<Block, BlockState> {
    // Pre-computed map for fast state transitions
    private Map<Property<?>, Map<Comparable<?>, BlockState>> neighbourMap;
    
    // O(1) state transition
    public <T extends Comparable<T>> BlockState setValue(Property<T> property, T value) {
        return neighbourMap.get(property).get(value);
    }
    
    public <T extends Comparable<T>> T getValue(Property<T> property) {
        return (T) values.get(property);
    }
}
```

### Storage Model (Palette-based)

Minecraft stores blocks using a **palette-compressed section format**:

```java
public class ChunkSection {
    // Palette maps small indices to actual BlockStates
    private Palette<BlockState> palette;
    
    // Compact bit storage (4-8 bits per block depending on palette size)
    private BitStorage storage;  // 16×16×16 = 4096 entries
    
    public BlockState getBlockState(int x, int y, int z) {
        int index = storage.get(x, y, z);
        return palette.valueFor(index);
    }
    
    public void setBlockState(int x, int y, int z, BlockState state) {
        int index = palette.idFor(state);
        storage.set(x, y, z, index);
    }
}
```

#### Palette Example

For a chunk section with only 3 unique states:
- Palette: `[air, stone, dirt]`
- Storage: 2 bits per block (can represent 0-3)
- Memory: 4096 × 2 bits = 1KB per section

### Block Registration

```java
public class Blocks {
    public static final Block OAK_STAIRS = register("oak_stairs", 
        new StairBlock(OAK_PLANKS.defaultBlockState(), 
            BlockBehaviour.Properties.of(Material.WOOD)));
}
```

---

## Detailed Comparison

### 1. State Definition

| Aspect | MattMC | Minecraft Java |
|--------|--------|----------------|
| **Property Declaration** | Implicit (added at runtime) | Explicit (`createBlockStateDefinition()`) |
| **Type Safety** | Weak (Object casting) | Strong (generics + compile-time checks) |
| **Default State** | Created per-placement | Pre-registered singleton |
| **Validation** | None | Property validates allowed values |

**MattMC:**
```java
BlockState state = new BlockState();
state.setValue("facing", Direction.NORTH);  // No validation
state.setValue("typo", "invalid");          // Silently accepted
```

**Minecraft:**
```java
BlockState state = block.defaultBlockState()
    .setValue(FACING, Direction.NORTH);     // Type-checked
    .setValue(TYPO, "invalid");             // Compile error
```

### 2. State Retrieval

| Aspect | MattMC | Minecraft Java |
|--------|--------|----------------|
| **Get Property** | HashMap lookup + cast | Direct field access |
| **Type Handling** | Runtime casting | Compile-time generics |
| **Null Safety** | Manual null checks | Properties always have values |

**MattMC:**
```java
public Direction getDirection(String property) {
    Object value = properties.get(property);
    return value instanceof Direction ? (Direction) value : Direction.NORTH;
}
```

**Minecraft:**
```java
Direction facing = state.getValue(FACING);  // Never null, type-safe
```

### 3. State Transitions

| Aspect | MattMC | Minecraft Java |
|--------|--------|----------------|
| **Complexity** | O(1) map put | O(1) lookup in neighbour map |
| **Object Creation** | New BlockState each time | Returns cached instance |
| **Memory** | Allocates new object | Zero allocation |

**MattMC:**
```java
public BlockState setValue(String property, Object value) {
    properties.put(property, value);  // Mutates existing object
    return this;
}
```

**Minecraft:**
```java
public BlockState setValue(Property<T> property, T value) {
    return neighbourMap.get(property).get(value);  // Returns cached state
}
```

### 4. Model Resolution

| Aspect | MattMC | Minecraft Java |
|--------|--------|----------------|
| **Lookup Method** | String matching | Pre-baked model per state |
| **String Generation** | On-demand | Cached/pre-computed |
| **Complexity** | O(n) string ops | O(1) direct reference |

**MattMC:**
```java
String variantKey = state.toVariantString();  // "facing=north,half=bottom"
List<BlockStateVariant> variants = blockState.getVariantsForState(variantKey);
```

**Minecraft:**
```java
BakedModel model = modelManager.getBlockModel(state);  // Direct lookup
```

---

## Performance Analysis

### Memory Usage

#### MattMC (per chunk with 100 stateful blocks):

```
Block array:      16 × 384 × 16 × 8 bytes = 768 KB
BlockState map:   100 entries × ~80 bytes = 8 KB
Total per chunk:  ~776 KB
```

#### Minecraft (per chunk, palette-compressed):

```
24 sections × 4096 blocks × 4 bits avg = 48 KB palette data
24 sections × palette overhead = ~2 KB
Total per chunk: ~50 KB (15x more efficient)
```

### CPU Performance

| Operation | MattMC | Minecraft |
|-----------|--------|-----------|
| Get state property | ~50ns (HashMap + cast) | ~5ns (field access) |
| Set state property | ~100ns (new object + put) | ~10ns (cached lookup) |
| Generate variant string | ~500ns (string ops) | N/A (pre-baked) |
| State comparison | ~200ns (map comparison) | ~2ns (reference equality) |

### Garbage Collection

- **MattMC**: Creates new BlockState objects frequently, causing GC pressure
- **Minecraft**: All states are cached singletons, zero allocation during gameplay

---

## Modularity Analysis

### MattMC Strengths

1. **Simplicity**: Easy to add new properties without registration
2. **Flexibility**: Any property name/value combination works
3. **Low Boilerplate**: No need for property declarations

### MattMC Weaknesses

1. **No Property Discovery**: Can't enumerate valid properties
2. **No Validation**: Invalid property names/values silently accepted
3. **Tight Coupling**: Properties hardcoded as strings throughout

### Minecraft Strengths

1. **Property Registry**: All properties are discoverable
2. **State Enumeration**: Can iterate all valid states
3. **Tool Support**: IDE autocomplete, compile-time errors
4. **Modular Properties**: Reusable `BlockStateProperties` class

### Minecraft Weaknesses

1. **Boilerplate**: More code required per block
2. **Complexity**: Multiple classes involved
3. **Fixed States**: Cannot add properties at runtime

---

## Power & Flexibility Analysis

### Features MattMC Lacks

| Feature | Minecraft Has | MattMC Has |
|---------|---------------|------------|
| **Waterlogging** | `WATERLOGGED` property | ❌ |
| **Age/Growth** | `PropertyInteger` for crops | ❌ |
| **Power Level** | `PropertyInteger(0-15)` | ❌ |
| **Multi-Part Blockstates** | Complex multipart system | ❌ |
| **Model Weighting** | Random model variants | ✅ (weight property) |
| **UV Lock** | Rotation UV preservation | ✅ |

### Expressive Power

**Minecraft can express:**
```json
// Multipart blockstate (e.g., fence)
{
  "multipart": [
    { "apply": { "model": "block/fence_post" } },
    { "when": { "north": "true" }, "apply": { "model": "block/fence_side" } },
    { "when": { "east": "true" }, "apply": { "model": "block/fence_side", "y": 90 } }
  ]
}
```

**MattMC is limited to:**
```json
// Simple variants only
{
  "variants": {
    "facing=north": { "model": "block/stairs" },
    "facing=east": { "model": "block/stairs", "y": 90 }
  }
}
```

---

## Recommendations for Improvement

### Priority 1: Typed Property System

Replace `Map<String, Object>` with typed properties:

```java
// New: Property interface
public interface Property<T extends Comparable<T>> {
    String getName();
    T getDefaultValue();
    Collection<T> getPossibleValues();
    Class<T> getValueClass();
}

// New: Type-safe BlockState
public class BlockState {
    private final ImmutableMap<Property<?>, Comparable<?>> values;
    
    public <T extends Comparable<T>> T getValue(Property<T> property) {
        return (T) values.get(property);
    }
    
    public <T extends Comparable<T>> BlockState setValue(Property<T> property, T value) {
        // Return cached instance instead of mutating
    }
}
```

### Priority 2: State Caching

Pre-compute all states at block registration:

```java
public abstract class Block {
    private StateDefinition<Block, BlockState> stateDefinition;
    private BlockState defaultState;
    
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        // Override in subclasses to add properties
    }
    
    public Block() {
        StateDefinition.Builder<Block, BlockState> builder = new StateDefinition.Builder<>();
        createBlockStateDefinition(builder);
        this.stateDefinition = builder.build(BlockState::new);
        this.defaultState = stateDefinition.any();
    }
}
```

### Priority 3: Palette-Based Storage

Replace HashMap storage with palette compression:

```java
public class ChunkSection {
    private Palette<BlockState> palette = new LinearPalette<>();
    private PackedIntArray storage = new PackedIntArray(4, 4096);
    
    public BlockState getBlockState(int x, int y, int z) {
        int index = y << 8 | z << 4 | x;
        return palette.get(storage.get(index));
    }
}
```

### Priority 4: Multipart Support

Add multipart blockstate format for complex blocks:

```java
public class BlockStateDefinition {
    private Map<String, List<BlockStateVariant>> variants;
    private List<MultipartCase> multipart;  // NEW
    
    public List<BakedQuad> getQuads(BlockState state) {
        if (multipart != null) {
            return evaluateMultipart(state);
        }
        return variants.get(state.toVariantString());
    }
}
```

### Priority 5: Property Validation

Add runtime validation with helpful error messages:

```java
public <T extends Comparable<T>> BlockState setValue(Property<T> property, T value) {
    if (!stateDefinition.getProperties().contains(property)) {
        throw new IllegalArgumentException("Block " + block + " does not have property " + property);
    }
    if (!property.getPossibleValues().contains(value)) {
        throw new IllegalArgumentException("Property " + property + " does not allow value " + value);
    }
    return getNeighbor(property, value);
}
```

---

## Implementation Roadmap

### Phase 1: Type Safety (Low Risk)
1. Create `Property<T>` interface
2. Create typed property implementations
3. Update existing properties to use new system
4. Keep backward compatibility with `Map<String, Object>`

### Phase 2: State Caching (Medium Risk)
1. Implement `StateDefinition` builder
2. Pre-compute neighbor maps
3. Update block registration to generate states
4. Profile memory savings

### Phase 3: Storage Optimization (High Risk)
1. Implement palette compression
2. Update chunk serialization
3. Migrate existing world data
4. Profile performance gains

### Phase 4: Advanced Features (Medium Risk)
1. Add multipart blockstate support
2. Add integer properties for redstone/crops
3. Add waterlogging support
4. Update model baking system

---

## Summary

| Aspect | MattMC Current | Recommendation |
|--------|----------------|----------------|
| **Type Safety** | Weak | Adopt typed `Property<T>` system |
| **Performance** | Good enough | Cache states for zero-allocation |
| **Memory** | Inefficient | Palette compression (15x savings) |
| **Modularity** | Simple but limited | Property registry for discoverability |
| **Features** | Basic | Multipart support for complex blocks |

MattMC's current system is simple and works for basic blocks, but adopting Minecraft's patterns would unlock significant performance improvements and enable more complex block types like fences, walls, and redstone components.
