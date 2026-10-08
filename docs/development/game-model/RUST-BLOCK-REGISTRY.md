# Rust block registry

> **Current behavior** (Phase 1 of the [migration plan](MIGRATION-PLAN.md)).
> Native state graphs and fluid definitions are also implemented migration
> slices; remaining definitions/gameplay in the [game model](index.md) are proposals.

Rust owns one registry of every block, property and block state:
[`content/block/`](https://github.com/HungLo2020/MattMC/blob/master/src/main/rust/content/block/mod.rs).
Rust subsystems derive their per-state lookup tables from it. They no longer
receive a private table from their own Java bridge. Java still defines the
blocks:
[`NativeBlockRegistry`](https://github.com/HungLo2020/MattMC/blob/master/src/main/java/net/minecraft/world/level/block/NativeBlockRegistry.java)
exports the frozen registries once and Rust installs them. The export is lazy:
the first `NativeBlockRegistry.ready()` call initializes `Holder.READY` and
caches success or failure. The current wire format is **4**. Java exports a native fluid-state ID per
block state, plus the remaining block/offset facts. Rust resolves fluid facts
from the [native fluid registry](FLUID-DEFINITIONS.md). Shared
[property declarations](PROPERTY-DEFINITIONS.md) are sent as native IDs, with
no name/domain export; Rust shares their immutable schemas. Synthetic/custom
schemas may still use the explicit encoding after a `-1` marker; every
registered block property uses a native reference.

## What it holds

- **Typed IDs:** `BlockId`, `StateId(u16)`, `PropertyId` and `FaceId`.
  Block and state IDs equal Java's `BuiltInRegistries.BLOCK` and
  `Block.BLOCK_STATE_REGISTRY` IDs.
- **Blocks:** each has a name, a contiguous state range, a default state, and
  its properties in name order.
- **Property arithmetic:** a state's value index for each property, `with_value` and
  `state(block, values)`. These are arithmetic on the state ID; the last
  property varies fastest. The [shared native state layout](STATE-GRAPHS.md)
  also constructs the graph behind Java's temporary `StateDefinition` views.
- **Per-state columns:**
  - the owning block
  - `StateFlags`: air, blocks motion, has fluid, random ticks, light-empty
    shape, leaves, custom `BlockState` subclass, solid render, can occlude,
    block entity, and falling fluid
  - light block and emission
  - six light occlusion faces
  - typed fluid-state association; fluid kind and own height resolve from the
    native fluid owner, while has-fluid/falling flags are derived at freeze
  - model offset type; each block also has its maximum horizontal and
    vertical offsets
- **Faces:** the light occlusion faces (`LightEngine.getOcclusionShape`) interned
  by exact box list, with face 0 empty. The face table holds
  `Shapes.faceShapeOccludes` for every pair; Java computes it at export.

Items, entity types, tags and biomes are not in this shared registry yet.
Existing native consumers may still use separately supplied Java metadata;
the shared model remains future work (see the [migration plan](MIGRATION-PLAN.md)).

## Consumers

World and storage consumers cache immutable derived views beside their code,
using `OnceLock` after the registry is installed. Terrain meshing derives its
view per state registration and keeps its separate reloadable cache. Java
passes state IDs at the consumer boundary:

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
Rust declines (including custom subclasses, unknown states or an unsupported
native-fluid request), and when the registry is unavailable. A failed downcall
throws; view rejection does not promise fallback after every exception. Java prepares only render-owned columns before trying the view. Explicit
block/fluid facts are computed only when the view declines; the normal route
does not reconstruct native-owned metadata in Java.
`NativeMeshingStateViewTest` compares the explicit and derived records for
every state in both fluid modes, field for field. This is a low-level record
fixture, not an end-to-end render or reload test; its execution is not covered
by the earlier aggregate results on the [verification page](BLOCK-REGISTRY-VERIFICATION.md).

## Adding a column or a consumer

1. Add the fact to `StateFacts` and the column in `content/block/mod.rs`.
   For facts still owned by Java, export them in `NativeBlockRegistry.export()`
   and decode them in
   [`export.rs`](https://github.com/HungLo2020/MattMC/blob/master/src/main/rust/content/block/export.rs).
   Bump `FORMAT` on both sides. Facts already owned by native content should
   be resolved from typed IDs, not exported back through Java.
2. Check it for every state in `NativeBlockRegistryTest` against Java's live
   answer.
3. In the consumer, derive the view from `&BlockRegistry` in a plain function
   with a Rust unit test, and cache it with `OnceLock` behind
   `content::block::installed()`.
4. Gate the registry-backed route on `NativeBlockRegistry.ready()`. When it
   is false or Rust declines a view, retain that consumer's existing fallback
   (the explicit native record registration for meshing).

## Constraints

- **Property identities stay distinct.** Native declaration IDs identify schemas;
  block-registry `PropertyId`s still follow first use of each Java object.
  Equal names do not merge different domains. Native schemas are shared by
  `Arc`; malformed declaration IDs reject installation.
- **Fluid facts have one owner.** `StateFacts` accepts a `FluidStateId`, not a
  kind/height copy. Supplying HAS_FLUID or FLUID_FALLING in its input flags is
  rejected; the builder derives them from the fluid definition/state. Unknown
  fluid IDs are rejected. The two-byte association replaces five bytes of
  duplicated kind/height columns per block state. Native consumers also retain
  exact source/flowing state identity, which those two columns alone cannot
  represent.
- **Installation is all or nothing.** The export is refused and registry-backed
  views are unavailable if any of these fails:
  - every block's states are contiguous, in block order
  - every state is a distinct object at its own ID
  - the state count is between 1 and 65,535, leaving `0xffff` reserved
  - Rust's arithmetic reproduces every exported value index
- **Custom `BlockState` subclasses** are flagged `CUSTOM`. Lighting,
  skylight, heightmap and meshing views decline those states; the chunk-section
  vocabulary declines the whole registry if one exists. Noise flags and the
  carvers' block lookup still use the exported facts under their own existing
  eligibility rules. There is no universal custom-state rejection.
- **The registry is process-wide and immutable.** Installing an equal registry
  again succeeds; a different one is rejected. This is not a content-reload or
  generation-publication mechanism; the rendering cache has its own lifecycle.
- **Views stay in their subsystem.** `content` must not depend on consumers.
  The standalone [`src/test/rust/worldgen.rs`](https://github.com/HungLo2020/MattMC/blob/master/src/test/rust/worldgen.rs)
  harness includes `content` for that reason.

## Testing

```sh
# Registry columns and property arithmetic against Java
./gradlew test -x testRustNative --tests 'net.minecraft.world.level.block.NativeBlockRegistryTest'
# Meshing view record parity (not selected by the aggregate driver below)
./gradlew test -x testRustNative --tests 'net.sodium.client.render.chunk.compile.pipeline.NativeMeshingStateViewTest'
# Rust units for the registry and its world/storage views
(
  cd src/main/rust || exit
  for filter in content:: world::level::lighting world::level::levelgen::heightmap \
    world::level::levelgen::noise_fill world::level::levelgen::proto_chunk \
    world::level::levelgen::carver storage::chunk; do
    cargo test --lib -- "$filter" || exit
  done
)
# Selected consumer parity, old-against-new benchmarks and a results report
python3 DevUtils/tests/content/VerifyRustBlockRegistry.py
```

The driver builds the reference commit in `build/block-registry-migration/`.
It then compares, in fresh JVMs:
- startup table construction, using
  [`BlockTableStartup.java`](https://github.com/HungLo2020/MattMC/blob/master/DevUtils/tests/content/BlockTableStartup.java)
- each consumer's native benchmark, whose outputs must match between trees

Results are in [Block registry verification](BLOCK-REGISTRY-VERIFICATION.md).
The driver reports performance booleans in `results.json`; a successful exit
does not require those booleans to pass. Inspect the per-case evidence before
claiming a performance gate or full Phase 1 acceptance.

## Troubleshooting

- **A consumer suddenly takes its Java path everywhere:** check
  `NativeBlockRegistry.ready()`. A registry change that breaks one of the
  installation checks (for example, a block registered with states out of
  order) disables the registry-backed routes. Meshing retains its explicit
  registration fallback; other consumers retain their own compatibility paths.
- **A `NativeBlockRegistryTest` column mismatch** after changing block code
  means the exported fact no longer matches Java's answer. Fix the export,
  not the test.
