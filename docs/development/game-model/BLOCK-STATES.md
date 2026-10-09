# Block states (proposal)

> Partly implemented: native definitions own registered block names, state
> layouts/defaults, physical settings and intrinsic map-color/emission/fluid
> rules. Native properties and state graphs also back Java's compatibility views.
> The typed state-access API below,
> complete per-state table and general shape interning remain proposals. See
> [the current registry](RUST-BLOCK-REGISTRY.md) and [game model index](index.md).

## Block vs state

These stay two separate things, as in Java:
- A **block** (`BlockId`, 1,235 registrations in the source inventory) is a
  kind of block, such as `furnace`.
  It owns the definition: properties, behavior, item, sound and strength.
- A **state** (`StateId`, 31,809 in the [author's parity record](BLOCK-REGISTRY-VERIFICATION.md))
  is one combination of that block's
  property values, such as `furnace[facing=north,lit=true]`. Each block's
  states are a contiguous range, and each state's `block` column names its
  block.

What changes is the representation. Java's `BlockState` is a heap object
with a property map and cached facts. In Rust a state is just its number, and
the facts are columns indexed by it.

## Encoding: arithmetic, not maps

Java still keeps per-state value maps and neighbour reference arrays, but
the [native graph owner](STATE-GRAPHS.md) now constructs the ordered values
and transition targets that those arrays project. Registered blocks borrow
shared graphs from [native definitions](BLOCK-DEFINITIONS.md); synthetic
definitions can still supply domains from Java. Rust's registry reads or
changes a state's property index arithmetically:

```text
state = block.first_state + Σ value_index(p) × stride(p)
```

Each block keeps its property layout (property IDs, value counts, strides),
so reading or changing a property is arithmetic. The current registry exposes
`value`, `with_value` and `state(block, values)` with property IDs and
value indices. The following typed convenience API is still proposed:

```rust
let age = state.get(AGE);                 // (id - first) / stride % count
let next = state.with(AGE, age + 1);      // id ± delta
let facing = state.get(FACING);           // returns Direction, typed
```

- **The proposed access API uses typed constants**: `Property<bool>`,
  `Property<u8>` (ranged integers), `Property<Direction>` and enum properties (`SlabType`,
  `StairsShape`, …) via a small `PropertyValue` trait. A wrong value type is a
  compile error.
- **Preserve value and state order.** Native templates put properties in name
  order; the shared layout varies the last property fastest. Java projects
  those rows into `BlockState` objects. The Frozen observer checks the original
  order independently; Java live-registry tests check the compatibility views.

## Per-state table: columns, not objects

Java's `BlockStateBase` caches about 30 facts per state in its constructor
and `initCache()`. Rust keeps the same facts as **struct-of-arrays columns**
indexed by `StateId`, built once. This table is the proposed full layout;
[current columns](RUST-BLOCK-REGISTRY.md#what-it-holds) include block ownership,
flags, lighting faces, fluid facts and offsets, but not all entries below:

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

The complete table is a destination, not the current producer boundary.
[Physical profiles](BLOCK-PHYSICS.md) now originate in Rust and supply Java's
constructor caches; native registry construction derives air and can-occlude
bits directly from them. [Intrinsic state rules](BLOCK-INTRINSICS.md) evaluate
map-color identities, emission and canonical fluid associations once into
native `StateTraits`. [State sound rules](BLOCK-SOUND-AND-OFFSETS.md) also
produce native columns, and native offset tables support Java views.
Java still evaluates blocked light, shapes and contextual predicates. Its
format-8 export includes blocked light, face IDs and remaining state flags.
Dynamic-shape and world-dependent queries still use Java behavior. A native
lookup column does not imply that every function producing or consuming it
has migrated.

## Shapes are interned

The proposal deduplicates voxel shapes into a `ShapeId` table, along with derived data:
face projections as `FaceId`, a face-occlusion truth table, and box lists.
The current Java exporter interns light-occlusion faces by exact box lists
and supplies their IDs and pairwise truth table to Rust; the registry does
not provide a general `ShapeId` owner. `world/phys/shapes` already has Rust
kernels for joins,
raycasts and closest points. Taking `ShapeId`s instead of raw shape buffers
would be future work.

## Why columns

Hot loops (light, meshing, noise fill, saving) touch one or two facts for
millions of states. Separate columns keep each loop's data dense in cache and
need no virtual calls. Rarely used facts cost nothing to the hot ones.
