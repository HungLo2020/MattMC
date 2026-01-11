# FerriteCore Optimization Analysis

This document provides a comprehensive analysis of the optimizations implemented by the FerriteCore mod for Minecraft. FerriteCore is a memory optimization mod that reduces RAM usage through targeted improvements to Minecraft's internal data structures and algorithms.

---

## Overview

FerriteCore achieves significant memory savings (typically 600-1500 MB or more depending on modpack size) by replacing Minecraft's default data structures with more efficient alternatives. The optimizations are split between client-side and both-sided (client and server) improvements, with minimal to zero performance impact.

---

## Optimization 1: Optional Reduction in KeyValueCondition

**Status**: Obsoleted by Optimization 4  
**Memory Saved**: ~100 MB  
**CPU Impact**: Zero or negative  
**Side**: Client  
**Default**: N/A (obsolete)

### Problem
Vanilla Minecraft's multipart model system creates code patterns like:
```java
Optional<T> opt = newlyCreatedOptional();
if (!opt.isPresent()) {
    // Something
} else {
    return () -> doThing(opt.get());
}
```

The created lambdas are kept around for extended periods, and there are millions of them. The captured `Optional` objects have a shallow size totaling about 100 MB, which cannot be garbage collected because the lambdas maintain references to them.

### Solution
Replace the else-branch with:
```java
T unwrapped = opt.get();
return () -> doThing(unwrapped);
```

This unwraps the value from the Optional before capturing it in the lambda, allowing the Optional itself to be garbage collected immediately after use.

### Technical Details
- **Memory Impact**: The shallow size of millions of Optional instances (~100 MB) can be reclaimed
- **Performance**: Actually slightly faster due to one less pointer indirection when the lambda executes
- **Safety**: Completely safe as the unwrapped value is captured directly

---

## Optimization 2: BlockState Neighbor Lookup (FastMap)

**Memory Saved**: ~600 MB  
**CPU Impact**: Near zero (potentially positive)  
**Side**: Both (client and server)  
**Default**: Enabled  
**Mixin Package**: `fastmap`

### Problem
Minecraft's `StateHolder#setValue` (commonly seen as `BlockState#setValue`) needs to quickly find "neighbor states" - states that differ by exactly one property value. Vanilla implements this using a `Table<Property<?>, Comparable<?>, S>` for each individual blockstate.

**Memory Complexity**: O(number_of_states × sum(values_per_property))

For example, a block with 3 binary properties (8 states) and properties with 2, 2, and 2 values respectively would use 8 × (2+2+2) = 48 table entries. These tables collectively consume about 600 MB.

### Solution
FerriteCore replaces the per-state tables with a single `FastMap` per block definition. A FastMap is essentially an ArrayList used as a multi-dimensional array, with two implementation strategies:

#### CompactFastMapKey (Compact Mode)
Uses modulo and division operations to index into a densely-packed array:
```java
public T getValue(int mapIndex) {
    int index = (mapIndex / mapFactor) % numValues();
    return byInternalIndex(index);
}
```

**Memory Complexity**: O(number_of_states)

#### BinaryFastMapKey (Default, Bitmask Mode)
Uses bitwise operations for faster access at slightly higher memory usage:
```java
public T getValue(int mapIndex) {
    final int clearAbove = mapIndex & lowestNBits(firstBitAfterValue);
    return byInternalIndex(clearAbove >>> firstBitInValue);
}
```

Each property's values are stored in a specific bit range within the map index, allowing extraction via bitmasking and bit shifting.

### Technical Details
- **Data Structure**: Single FastMap per block type instead of Table per blockstate
- **Index Calculation**: Each blockstate gets an integer index into the FastMap's value matrix
- **Property Lookup**: Bitwise operations (bitmask mode) or integer arithmetic (compact mode)
- **Neighbor Finding**: Replace bits or adjust index arithmetically to get the new state
- **Memory**: FastMaps total ~7 MB vs ~600 MB for vanilla tables

**Example**: For a blockstate at index 42 (binary: ...101010), changing a property stored in bits 2-3:
1. Create mask to clear bits 2-3: `keepMask = ~0b1100 | 0b11 = ...110011`
2. Apply mask: `42 & keepMask = ...100010`
3. Insert new value: `...100010 | (newValue << 2)`

---

## Optimization 3: BlockState Property Storage

**Memory Saved**: ~170 MB  
**CPU Impact**: Near zero  
**Side**: Both (client and server)  
**Default**: Enabled (part of fastmap)  
**Mixin Package**: Part of `fastmap`

### Problem
Each blockstate stores its property-value mappings as an `ImmutableMap<Property<?>, Comparable<?>>`. With potentially millions of blockstates loaded, these maps consume approximately 170 MB of memory.

### Solution
Replace the `ImmutableMap` with a custom `FastMapEntryMap` that doesn't actually store the data. Instead, it calculates property values on-demand using the FastMap from Optimization 2.

**Implementation**:
```java
public class FastMapEntryMap implements Reference2ObjectMap<Property<?>, Comparable<?>> {
    private final FastMapStateHolder<?> viewedState;
    
    @Override
    public Comparable<?> get(@Nullable Object key) {
        return viewedState.getStateMap().getValue(viewedState.getStateIndex(), key);
    }
}
```

### Technical Details
- **Storage**: No actual map storage; properties are computed from the blockstate's index in the FastMap
- **Lookup Process**:
  1. Get the blockstate's index (stored as a single int)
  2. Find the FastMapKey for the requested property
  3. Extract the property value from the index using bit operations or arithmetic
- **Compatibility**: Implements the full `Reference2ObjectMap` interface for compatibility
- **Memory**: Single int per blockstate vs full ImmutableMap instance
- **Trade-off**: Tiny computation cost for massive memory savings

---

## Optimization 4: Multipart Model Predicate Caching

**Memory Saved**: 300-400 MB  
**CPU Impact**: Some during model loading, zero during gameplay  
**Side**: Client  
**Default**: Enabled  
**Mixin Package**: `predicates`

### Problem
Multipart block models (used extensively for pipes, fences, walls, etc.) use predicates to determine which model parts to render based on blockstate properties. Vanilla creates new predicate instances for each blockstate, even when they test identical conditions. These predicates consume 300-400 MB.

**Common pattern**: A pipe block with 6 directional boolean properties (north, south, east, west, up, down) creates thousands of predicates, many checking the same property-value pairs.

### Solution
Deduplicate predicates by caching them based on their logic:

#### For KeyValueCondition (single property test)
Cache key: `(Property, Value)` pair
```java
// Both of these would use the same cached predicate instance:
// state -> state.getValue(NORTH) == true
// state -> state.getValue(NORTH) == true
```

#### For And/OrCondition (compound predicates)
Cache key: List of constituent predicates sorted by hash code
```java
// Predicates testing (north=true AND south=true) share the same instance
// even if created separately, as long as they combine the same sub-predicates
```

### Technical Details
- **Deduplication Strategy**: 
  - For simple conditions: hash by (property reference, value)
  - For compound conditions: hash by sorted list of sub-predicates
- **Property Equality**: Minecraft blocks never have two distinct properties that are `.equals()`, so reference equality works
- **Result**: Reduces predicate count from tens of thousands/millions to hundreds
- **Implementation**: Global cache maps storing canonical predicate instances
- **Safety**: Predicates are stateless and immutable, making sharing completely safe

---

## Optimization 5: ModelResourceLocation String Deduplication

**Memory Saved**: ~300 MB  
**CPU Impact**: Zero or negative for first part, slight (<1s) during loading for second part  
**Side**: Client  
**Default**: Enabled  
**Mixin Package**: `mrl`

### Problem
The `ModelResourceLocation` constructor that accepts a `ResourceLocation` and variant string has two inefficiencies:

1. **String Recreation**: It converts the ResourceLocation to a string, then immediately parses it back:
   ```java
   // Vanilla approach (pseudocode):
   String rlString = resourceLocation.toString(); // "minecraft:stone"
   // Then splits rlString back into namespace and path
   ```
   This creates duplicate String objects for namespace and path.

2. **Variant String Duplication**: Many MRLs share the same variant string (e.g., "inventory", "normal"), but each gets its own String instance.

### Solution

#### Part 1: Direct Field Access
Directly access the ResourceLocation's namespace and path fields instead of converting to string and re-parsing. This eliminates unnecessary String allocations and parsing overhead.

#### Part 2: Variant String Interning
Deduplicate variant strings by maintaining a cache:
```java
private static final Map<String, String> VARIANT_CACHE = new HashMap<>();

public static String deduplicateVariant(String variant) {
    return VARIANT_CACHE.computeIfAbsent(variant, Function.identity());
}
```

### Technical Details
- **String Savings**: Each avoided duplicate saves ~40-80 bytes (String object overhead + char array)
- **Common Variants**: "inventory", "normal", and blockstate property combinations
- **Performance**: Eliminates string concatenation and parsing overhead
- **Cache Size**: Relatively small (few hundred entries) for the variant cache
- **Memory Impact**: ~300 MB saved from avoiding duplicate strings across millions of MRLs

---

## Optimization 6: Multipart Model Instance Deduplication

**Memory Saved**: ~200 MB  
**CPU Impact**: Slight during loading, zero at runtime  
**Side**: Client  
**Default**: Enabled  
**Mixin Package**: `dedupmultipart`

### Problem
Every blockstate using multipart models gets its own instance of the multipart model, even when multiple states use identical model configurations. Since multipart models are typically used for blocks with many states (e.g., pipes with 64+ states), this creates massive redundancy.

**Example**: A pipe with 64 states might have 64 separate `MultipartBakedModel` instances, all containing the same predicate-model pairs.

### Solution
Deduplicate `MultipartBakedModel` instances by using the model's data as a cache key.

**Cache Key**: `List<Pair<Predicate<BlockState>, IBakedModel>>`

Since predicates are already deduplicated by Optimization 4, and baked models are typically shared, many multipart models have identical input data. The cache reuses instances when the input lists match.

### Technical Details
- **Deduplication Logic**:
  ```java
  Map<List<Pair<Predicate, BakedModel>>, MultipartBakedModel> cache;
  
  MultipartBakedModel getOrCreate(List<Pair<Predicate, BakedModel>> parts) {
      return cache.computeIfAbsent(parts, this::createNew);
  }
  ```
- **Instance Reduction**: From ~200,000 instances to ~1,500 instances (in Direwolf20 1.16.4 pack)
- **Prerequisite**: Requires Optimization 4 (predicate caching) for maximum effectiveness
- **Safety**: Multipart models are immutable after creation, making instance sharing safe
- **Memory**: Each avoided instance saves roughly 1 KB (object overhead + references)

---

## Optimization 7: BlockState Cache Deduplication

**Memory Saved**: ~200 MB  
**CPU Impact**: Some during loading and world joining (<1 second), none afterward  
**Side**: Both (client and server)  
**Default**: Enabled  
**Mixin Package**: `blockstatecache`

### Problem
Blockstates that aren't marked as "variable opacity" cache their collision and render shapes. This consumes about 200 MB, primarily because:
1. There are millions of blockstates
2. Many blockstates share identical shapes (e.g., all full cube blocks)
3. Many mods maintain their own shape caches in addition to the blockstate cache, multiplying the duplication

### Solution
Deduplicate the cached shapes using two strategies:

#### Strategy 1: Share Identical VoxelShape Instances
```java
Map<ArrayVoxelShape, ArrayVoxelShape> shapeCache;

ArrayVoxelShape deduplicate(ArrayVoxelShape shape) {
    return shapeCache.computeIfAbsent(shape, Function.identity());
}
```

Uses custom hash and equality functions that compare the actual shape data (point coordinates and discrete voxel data) rather than object identity.

#### Strategy 2: Replace VoxelShape Internals
When a mod caches its own shapes, FerriteCore replaces the internal data arrays with the canonical versions:
```java
void replaceInternals(ArrayVoxelShape toKeep, ArrayVoxelShape toReplace) {
    ArrayVSAccess toReplaceAccess = (ArrayVSAccess) toReplace;
    ArrayVSAccess toKeepAccess = (ArrayVSAccess) toKeep;
    toReplaceAccess.setXPoints(toKeepAccess.getXPoints());
    toReplaceAccess.setYPoints(toKeepAccess.getYPoints());
    toReplaceAccess.setZPoints(toKeepAccess.getZPoints());
    // ... etc
}
```

This saves memory even when the mod keeps the shape wrapper, as the bulk of the memory (coordinate arrays) is shared.

### Technical Details
- **Deduplication Points**: Collision shapes, render shapes, and face sturdiness arrays
- **Hash Strategy**: Custom hash based on coordinate arrays and discrete voxel shape data
- **Optimization**: Checks if the previous cache already has equivalent shapes before map lookup
- **Safety**: Assumes VoxelShapes are immutable after creation (vanilla assumption)
- **Benefit**: Works even with mod-side caching by replacing internals
- **Additional**: Also deduplicates boolean[] arrays used for face sturdiness checks

**VoxelShape Components**:
- `xPoints`, `yPoints`, `zPoints`: double[] arrays of boundary coordinates
- `shape`: DiscreteVoxelShape with the actual voxel data
- `faces`: Optional cached face data

---

## Optimization 8: Quad Vertex Data Deduplication

**Memory Saved**: ~150 MB  
**CPU Impact**: Some during model loading, none afterward  
**Side**: Client  
**Default**: Enabled  
**Mixin Package**: `bakedquad`

### Problem
Baked quads (the building blocks of Minecraft's models) store vertex data in `int[]` arrays. These arrays account for about 340 MB total. Many quads have identical vertex data (e.g., standard cube faces, commonly used decorations), but each has its own array copy.

**Quad Structure**:
```java
class BakedQuad {
    private final int[] vertices; // 28-32 ints: position, color, UV, normal, etc.
    private final int tintIndex;
    private final Direction direction;
    private final TextureAtlasSprite sprite;
}
```

### Solution
Deduplicate the `int[]` vertex arrays by using a cache:

```java
Map<int[], int[]> vertexDataCache;

int[] deduplicateVertexData(int[] vertices) {
    // Use Arrays.hashCode() and Arrays.equals()
    return vertexDataCache.computeIfAbsent(vertices, Function.identity());
}
```

When loading models, replace each quad's vertex array with the cached canonical version if an identical array already exists.

### Technical Details
- **Application Scope**: Only applied to quads in `SimpleBakedModel` instances by default
- **Safety Consideration**: The `int[]` arrays are technically mutable, but in practice:
  - Vanilla code treats them as immutable
  - Most mods follow this convention
  - Only applying to SimpleBakedModel reduces risk
- **Hash Strategy**: Uses Arrays.hashCode() for the vertex data
- **Memory Reduction**: From ~340 MB to ~195 MB (reduces by ~43%)
- **Common Duplicates**: 
  - Standard block faces (full cubes)
  - Common decorative elements
  - Repeated texture quads

**Vertex Data Format** (per vertex, 4 vertices per quad):
- Position (x, y, z): 3 floats encoded as ints
- Color: 1 int (RGBA)
- Texture UV: 2 floats encoded as ints
- Optional: Normal, padding

---

## Optimization 9: Threading Detector Optimization

**Memory Saved**: 10-15 MB base, scales with loaded chunks  
**CPU Impact**: None or slightly negative  
**Side**: Both (client and server)  
**Default**: **Disabled** (opt-in due to rare race conditions)  
**Mixin Package**: `threaddetec`

### Problem
Since Minecraft 1.18, each `PalettedContainer` (used for storing blocks in chunk sections) contains a `ThreadingDetector` to crash when multiple threads access it concurrently. This debugging feature:
- Exists for every chunk section (actually two per section due to `ImposterProtoChunk`)
- Uses ~10-15 MB with one player in singleplayer (both client and server)
- Scales with render distance and loaded chunks
- Uses a relatively heavyweight object for a simple thread-safety check

**Vanilla Implementation**:
```java
class ThreadingDetector {
    private final AtomicInteger currentThreadId;
    private final String name;
    // Additional fields for crash information
}
```

### Solution
Replace `ThreadingDetector` with a single `byte` field in `PalettedContainer`, using the container itself as the synchronization monitor.

**New Implementation**:
```java
class PalettedContainer {
    private byte ferritecore$threadingState; // UNLOCKED, LOCKED, or CRASHING
    
    public void acquire() {
        synchronized (this) {
            if (ferritecore$threadingState == UNLOCKED) {
                ferritecore$threadingState = LOCKED;
                return; // Fast path
            }
            // Slow path: prepare crash
        }
    }
}
```

### Technical Details

#### Fast Path (Normal Operation)
**Vanilla**:
1. Acquire lock on ThreadingDetector
2. Atomic CAS operation (tryAcquire)
3. Release lock

**FerriteCore**:
1. Acquire lock on PalettedContainer
2. Non-atomic read and write (safe because synchronized)
3. Release lock

**Performance**: Similar or slightly better (no atomic operation needed)

#### Slow Path (Thread Conflict Detection)
When a conflict is detected, the implementation uses a global `IdentityHashMap<SmallThreadDetectable, CrashingState>` to coordinate the crash between threads. Since this path only runs when about to crash, performance is irrelevant.

**Crash Coordination**:
1. First conflicting thread creates `CrashingState` in global map
2. Both threads wait until both have checked in
3. Primary thread creates the exception
4. Both threads throw the same exception

#### Memory Savings Breakdown
- **Per Container**: 1 byte vs ~48+ bytes (object + atomic + string)
- **Multiplier**: 2 per chunk section (16×16×16 blocks) due to implementation details
- **Scaling**: More chunks = more savings
- **Example**: 10,000 loaded sections × 2 × 47 bytes saved ≈ 940 KB per 10k sections

### Why Disabled by Default
Very rare race conditions have been reported under unknown circumstances. The optimization is sound in theory, but the crashes are difficult to reproduce and debug, so it's opt-in for safety.

---

## Additional Client-Side Optimizations

While the summary.md file mentions optimizations 1, 4, 5, 6, and 8 as client-side only, the actual implementation in version 1.21.9 appears to have removed or consolidated some of these. The remaining codebase focuses on:

- **Model Sides Optimization**: Uses smaller data structures for simple models with few side-specific faces (enabled by default)
- Configuration options exist but corresponding mixin implementations for multipart predicates, MRL caching, and quad deduplication were not found in the analyzed version

---

## Configuration System

FerriteCore provides a configuration file allowing users to enable/disable individual optimizations:

### Default Enabled Optimizations
- `replaceNeighborLookup`: BlockState neighbor table (Opt. 2)
- `replacePropertyMap`: Property storage (Opt. 3)
- `blockstateCacheDeduplication`: Shape cache deduplication (Opt. 7)
- `modelSides`: Simplified model side data structures
- `cacheMultipartPredicates`: Predicate caching (Opt. 4) *if implemented*
- `modelResourceLocations`: MRL string optimization (Opt. 5) *if implemented*
- `multipartDeduplication`: Multipart model deduplication (Opt. 6) *if implemented*
- `bakedQuadDeduplication`: Quad vertex deduplication (Opt. 8) *if implemented*

### Opt-In Optimizations
- `useSmallThreadingDetector`: Threading detector optimization (Opt. 9) - disabled by default
- `compactFastMap`: Use compact mode instead of bitmask mode for FastMap - disabled by default
- `populateNeighborTable`: Fill vanilla neighbor table for mod compatibility - disabled by default

---

## Implementation Architecture

### Mixin-Based Approach
FerriteCore uses Mixin to inject its optimizations without modifying Minecraft's source code:

1. **Accessor Mixins**: Provide access to private fields and methods
2. **Implementation Mixins**: Replace or wrap vanilla code
3. **Duck Interfaces**: Add new fields and methods to existing classes

### Key Design Patterns

#### FastMap Pattern
```java
// Each block definition has one FastMap
FastMap<BlockState> stateMap;

// Each blockstate has an index
int stateIndex;

// Properties computed on demand
Comparable<?> propertyValue = stateMap.getValue(stateIndex, property);

// Neighbor states found via index manipulation
BlockState neighbor = stateMap.with(stateIndex, property, newValue);
```

#### Deduplication Pattern
```java
// Global cache with custom equality
Map<T, T> cache = new CustomHashMap<>(customHashStrategy);

// Deduplicate an instance
T canonical = cache.computeIfAbsent(instance, Function.identity());
```

#### Replace Internals Pattern
```java
// When object sharing isn't possible, share the expensive internals
void replaceInternals(Shape keep, Shape replace) {
    ArrayAccess replaceAccess = (ArrayAccess) replace;
    ArrayAccess keepAccess = (ArrayAccess) keep;
    replaceAccess.setInternalData(keepAccess.getInternalData());
}
```

---

## Performance Characteristics

### Memory Savings Summary
| Optimization | Memory Saved | Pack |
|-------------|--------------|------|
| BlockState neighbors | ~600 MB | ForgeCraft 1 |
| Property storage | ~170 MB | ForgeCraft 1 |
| Predicate caching | 300-400 MB | ForgeCraft 1 |
| MRL strings | ~300 MB | DW20 1.2.0 |
| Multipart instances | ~200 MB | DW20 1.2.0 |
| Shape cache | ~200 MB | DW20 1.2.0 |
| Quad vertices | ~150 MB | DW20 1.2.0 |
| Threading detector | 10-15 MB+ | Scales with chunks |

**Total**: 1.5+ GB typical savings on modded instances

### CPU Impact
- **Negligible Runtime Impact**: Most optimizations trade memory for tiny computation costs
- **Loading Time**: Slight increase during model loading (<1 second) for deduplication
- **Potential Gains**: Some optimizations may actually improve performance:
  - Fewer pointer indirections (Optional removal)
  - Better cache locality (shared data)
  - Reduced GC pressure (fewer objects)

---

## Compatibility and Safety

### Safety Analysis

#### Completely Safe
- **Optimizations 2-3** (FastMap): Different data structure, same semantics
- **Optimization 4** (Predicates): Stateless immutable functions
- **Optimization 5** (MRL): String deduplication is always safe
- **Optimization 6** (Multipart): Immutable models after creation

#### Requires Assumptions
- **Optimization 7** (Shapes): Assumes VoxelShapes are immutable (vanilla assumption)
- **Optimization 8** (Quads): Assumes vertex data not modified (mostly true)
- **Optimization 9** (Threading): Rare race conditions reported (disabled by default)

### Mod Compatibility
- **High Compatibility**: Most mods work without issues
- **Neighbor Table**: Some mods may directly access the vanilla neighbor table
  - Enable `populateNeighborTable` config option if needed
- **Property Map**: Compatible because FastMapEntryMap implements full interface
- **Override System**: Mods can request specific optimizations be disabled

---

## Technical Innovations

### BitMask-Based Multi-Dimensional Array
The BinaryFastMapKey is particularly clever:
- Traditional approach: `array[prop1][prop2][prop3]` requires nested arrays
- FastMap approach: Single flat array with bit-encoded indices
- Benefits: Cache-friendly, minimal overhead, fast bitwise operations

### On-Demand Property Computation
Instead of storing a map of properties:
- Encode all property values in a single integer
- Extract values via bit manipulation when needed
- Trade: ~48 bytes (ImmutableMap) → 4 bytes (int)

### Internal Data Sharing
When object instances can't be shared (mod caching):
- Share the expensive internal data arrays
- Shallow size remains, but deep size is deduplicated
- Exploits Minecraft's immutability assumptions

---

## Conclusion

FerriteCore demonstrates that significant memory optimizations are possible through careful data structure selection and strategic deduplication. The mod achieves 1.5+ GB savings by:

1. **Replacing inefficient data structures** (Tables → FastMap)
2. **Eliminating redundant storage** (computing properties on-demand)
3. **Aggressive deduplication** (shapes, predicates, models, strings, vertices)
4. **Exploiting domain knowledge** (immutability assumptions, common patterns)

The optimizations maintain full compatibility with Minecraft and mods by preserving semantics while changing implementations. The minimal performance impact (often slightly positive) makes these optimizations essentially "free" memory savings.

For modpack developers and server operators, FerriteCore is a highly effective way to reduce memory usage without gameplay changes or compatibility issues. The modular configuration system allows fine-tuning if edge cases arise.
