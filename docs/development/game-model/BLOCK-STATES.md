# Block states (proposal)

> Proposal; not implemented. See the [game model index](index.md).

## Block vs state

These stay two separate things, as in Java:
- A **block** (`BlockId`, 1,211 today) is a kind of block, such as `furnace`.
  It owns the definition: properties, behavior, item, sound and strength.
- A **state** (`StateId`, 31,809 today) is one combination of that block's
  property values, such as `furnace[facing=north,lit=true]`. Each block's
  states are a contiguous range, and each state's `block` column names its
  block.

What changes is the representation. Java's `BlockState` is a heap object
with a property map and cached facts. In Rust a state is just its number, and
the facts are columns indexed by it.

## Encoding: arithmetic, not maps

Java stores each state's property values in a map and precomputes a
neighbour table for `setValue`. In Rust a state is just a number:

```text
state = block.first_state + Σ value_index(p) × stride(p)
```

Each block keeps its property layout (property IDs, value counts, strides),
so reading or changing a property is arithmetic:

```rust
let age = state.get(AGE);                 // (id - first) / stride % count
let next = state.with(AGE, age + 1);      // id ± delta
let facing = state.get(FACING);           // returns Direction, typed
```

- **Properties are typed constants**: `Property<bool>`, `Property<u8>` (ranged
  integers), `Property<Direction>` and enum properties (`SlabType`,
  `StairsShape`, …) via a small `PropertyValue` trait. A wrong value type is a
  compile error.
- **Value order and state order must match Java exactly.** `StateDefinition`
  sorts properties and expands the cartesian product in a specific order. The
  parity test checks every state's ID and values.

## Per-state table: columns, not objects

Java's `BlockStateBase` caches about 30 facts per state in its constructor
and `initCache()`. Rust keeps the same facts as **struct-of-arrays columns**
indexed by `StateId`, built once:

| Column | Type | Used by |
|---|---|---|
| `flags` | bitset `u32` | air, liquid, blocks motion, can occlude, solid render, random ticks, replaceable, conductor, suffocating, … |
| `light_block`, `emission` | `u8` | lighting, meshing |
| `shape`, `collision`, `occlusion`, `interaction`, `visual` | `ShapeId` (`u16`) | physics, outline, culling |
| `occlusion_face` | `[FaceId; 6]` | lighting, culling (replaces two Java face dedups) |
| `support` | bits per face × support type | placement, `canSurvive` |
| `fluid` | `FluidStateId` | fluids, aquifers, meshing |
| `destroy_speed`, `push_reaction`, `map_color`, `sound`, `instrument`, `offset` | small scalars | gameplay, maps, audio |
| `block` | `BlockId` | everything that needs the owning block |

Java evaluates per-state functions in `Properties` (for example
`lightLevel(s -> …)` or the redstone-conductor predicates) for every state
when it builds the cache. Rust evaluates them the same way, once, into
columns, so no function is ever called per lookup. Dynamic-shape blocks
(`dynamicShape`) keep a flag that sends shape queries to their behavior.

## Shapes are interned

Voxel shapes are deduplicated into a `ShapeId` table, along with derived data:
face projections as `FaceId`, a face-occlusion truth table, and box lists.
Light propagation already relies on exactly this, and
`world/phys/shapes` already has the Rust kernels for joins, raycasts and
closest points. They would take `ShapeId`s instead of raw shape buffers.

## Why columns

Hot loops (light, meshing, noise fill, saving) touch one or two facts for
millions of states. Separate columns keep each loop's data dense in cache and
need no virtual calls. Rarely used facts cost nothing to the hot ones.
