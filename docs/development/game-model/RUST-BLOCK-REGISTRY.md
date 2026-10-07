# Rust block registry

> **Current behavior** (Phase 1 of the [migration plan](MIGRATION-PLAN.md)).
> The rest of the [game model](index.md) is still a proposal.

Rust owns one registry of every block, property and block state:
[`content/block/`](https://github.com/HungLo2020/MattMC/blob/master/src/main/rust/content/block/mod.rs).
Rust subsystems derive their per-state lookup tables from it. They no longer
receive a private table from their own Java bridge. Java still defines the
blocks: at startup,
[`NativeBlockRegistry`](https://github.com/HungLo2020/MattMC/blob/master/src/main/java/net/minecraft/world/level/block/NativeBlockRegistry.java)
exports the frozen registries once and Rust installs them.

## What it holds

- **Typed IDs:** `BlockId`, `StateId(u16)`, `PropertyId` and `FaceId`.
  Block and state IDs equal Java's `BuiltInRegistries.BLOCK` and
  `Block.BLOCK_STATE_REGISTRY` IDs.
- **Blocks:** each has a name, a contiguous state range, a default state, and
  its properties in name order.
- **Property arithmetic:** a state's value index for each property, `with_value` and
  `state(block, values)`. These are arithmetic on the state ID; the last
  property varies fastest, as in `StateDefinition`.
- **Per-state columns:**
  - the owning block
  - `StateFlags`: air, blocks motion, has fluid, random ticks, light-empty
    shape, leaves, custom `BlockState` subclass, solid render, can occlude,
    block entity, and falling fluid
  - light block and emission
  - six light occlusion faces
  - fluid kind (none, water, lava, other) and the fluid's own height
  - model offset type; each block also has its maximum horizontal and
    vertical offsets
- **Faces:** the light occlusion faces (`LightEngine.getOcclusionShape`) interned
  by exact box list, with face 0 empty. The face table holds
  `Shapes.faceShapeOccludes` for every pair; Java computes it at export.

Items, entity types, tags and biomes are not in it yet. Each is added when a
Rust consumer needs it (see the [migration plan](MIGRATION-PLAN.md)).

## Consumers

Each consumer builds its view once, on first use, with a `OnceLock` next to
its code, and Java passes state IDs:

| Consumer | View | Java passes |
|---|---|---|
| [Light propagation](../world/lighting/RUST-LIGHT-PROPAGATION.md) | light types (`Tables::from_registry`) | palette, uniform or per-block state IDs |
| [Skylight sources](../world/lighting/RUST-SKYLIGHT-SOURCES.md) | up/down descriptors and edge table | local palette state IDs; null for global |
| [Heightmap priming](../world/levelgen/heightmap/RUST-HEIGHTMAP.md) | six-type masks per state | local palette state IDs; null for global |
| [Noise fill](../world/levelgen/RUST-NOISE-FILL.md), proto chunk, surface | noise flags per state | nothing |
| [Carvers](../world/levelgen/carver/index.md) | the block column | nothing |
| [Chunk sections](../world/chunk/RUST-CHUNK-SECTIONS.md) | each state's `BlockState.CODEC` tape fragment and palette storage bits | state IDs as labels |
| [Palette packing](../world/chunk/RUST-PALETTE-PACKING.md) | none: a global palette's state IDs are their own labels | no label table for global palettes |
| Terrain meshing states (`render/chunk/meshing`) | the block facts of each meshing state | rendering's own columns (below) |

**Terrain meshing states** mix rendering's own columns with block facts.
`NativeStaticBlockModelRegistry` sends only rendering's columns through
`registerStateView`: the model selector, materials and passes, shader-pack
IDs, tint, skip groups, fluid sprites, and the model, cullable and
fluid-overlay flags. Two controls say whether fluids are forced to Java and
whether the native producer supports the state's fluid.
[`state_from_registry`](https://github.com/HungLo2020/MattMC/blob/master/src/main/rust/render/chunk/meshing/cache.rs)
fills in the rest from the registry: air, solid render, occlusion, block
entity, motion, emission, fluid type, height and falling, and offsets. The
explicit `registerState` remains for corpus replays, benchmarks, and states
Rust declines (custom subclasses, or no registry).
`NativeMeshingStateViewTest` checks that both give the same record for
every state.

## Adding a column or a consumer

1. Add the fact to `StateFacts` and the column in `content/block/mod.rs`.
   Export it in `NativeBlockRegistry.export()` and decode it in
   [`export.rs`](https://github.com/HungLo2020/MattMC/blob/master/src/main/rust/content/block/export.rs).
   Bump `FORMAT` on both sides.
2. Check it for every state in `NativeBlockRegistryTest` against Java's live
   answer.
3. In the consumer, derive the view from `&BlockRegistry` in a plain function
   with a Rust unit test, and cache it with `OnceLock` behind
   `content::block::installed()`.
4. Gate the Java route on `NativeBlockRegistry.ready()`. When it is false,
   or Rust declines a view, the Java path runs as before.

## Constraints

- **Installation is all or nothing.** The export is refused, and every
  consumer keeps its Java path, if any of these fails:
  - every block's states are contiguous, in block order
  - every state is a distinct object at its own ID
  - the state count fits in 16 bits
  - Rust's arithmetic reproduces every exported value index
- **Custom `BlockState` subclasses** are flagged `CUSTOM`. Consumers decline
  them as their old bridges did. The chunk-section vocabulary declines the
  whole registry if one exists.
- **The registry is process-wide and immutable.** Installing an equal registry
  again succeeds; a different one is rejected.
- **Views stay in their subsystem.** `content` must not depend on consumers.
  The standalone [`src/test/rust/worldgen.rs`](https://github.com/HungLo2020/MattMC/blob/master/src/test/rust/worldgen.rs)
  harness includes `content` for that reason.

## Testing

```sh
# Every state against Java, plus the consumer parity tests
./gradlew test -x testRustNative --tests 'net.minecraft.world.level.block.NativeBlockRegistryTest'
# Rust units for the registry and each view
(cd src/main/rust && cargo test --lib -- content:: lighting heightmap noise_fill storage::chunk)
# Parity, old-against-new benchmarks and an acceptance report
python3 DevUtils/tests/content/VerifyRustBlockRegistry.py
```

The driver builds the reference commit in `build/block-registry-migration/`.
It then compares, in fresh JVMs:
- startup table construction, using
  [`BlockTableStartup.java`](https://github.com/HungLo2020/MattMC/blob/master/DevUtils/tests/content/BlockTableStartup.java)
- each consumer's native benchmark, whose outputs must match between trees

Results are in [Block registry verification](BLOCK-REGISTRY-VERIFICATION.md).

## Troubleshooting

- **A consumer suddenly takes its Java path everywhere:** check
  `NativeBlockRegistry.ready()`. A registry change that breaks one of the
  installation checks (for example, a block registered with states out of
  order) disables every consumer at once.
- **A `NativeBlockRegistryTest` column mismatch** after changing block code
  means the exported fact no longer matches Java's answer. Fix the export,
  not the test.
