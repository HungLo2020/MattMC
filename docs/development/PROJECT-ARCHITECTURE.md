# Project Architecture

This document describes the Rust source layout for MattMC. It intentionally does not mirror the Java package tree. Rust code is organized by subsystem ownership so the tree can grow toward the long-term native engine architecture.

The completed runtime target is **one Rust executable** supporting client and dedicated-server modes, **at most one separately loaded Rust library**, and **no Java**. Current Java bridges, configuration and CPU semantic producers describe the incremental migration, not completion of that target. See the [Goal 5 rendering checkpoint](rendering/GOAL-5-STATUS.md) for the current rendering boundary and evidence limits.

The active [Rust migration milestones](RUST-MIGRATION.md) target one executable
with at most one separately loaded Rust library and specify ownership and
acceptance for the whole project.

## Rust Source Layout

```text
src/main/rust/
├── Cargo.toml
├── lib.rs
├── app/
├── assets/
├── compat/
├── content/
│   └── block/
├── core/
├── gameplay/
│   └── tacz/
├── network/
│   └── codec/
│       └── long_array/
├── platform/
├── render/
│   ├── chunk/
│   │   ├── meshing/
│   │   ├── direct_trigger.rs
│   │   ├── gfni_trigger.rs
│   │   ├── index.rs
│   │   ├── occlusion.rs
│   │   ├── render_data.rs
│   │   ├── render_list.rs
│   │   └── translucent.rs
│   ├── dh_collector/
│   ├── scene/
│   ├── shared/
│   ├── shaderpack/
│   ├── worldrender/
│   ├── guirender/
│   ├── bridge/
│   └── vulkanic/
│       └── backends/
│           ├── opengl/
│           └── vulkan/
├── storage/
│   ├── chunk/
│   ├── nbt/
│   ├── poi/
│   └── region/
├── tools/
└── world/
    ├── phys/
    │   └── shapes/
    │       ├── boolean_join/
    │       ├── box_extract/
    │       ├── rotation/
    │       ├── closest_point/
    │       └── raycast/
    └── level/
        ├── biome/
        │   ├── climate/
        │   └── search/
        ├── chunk/
        │   └── palette/
        ├── lighting/
        │   ├── priority_queue/
        │   ├── propagation/
        │   └── skylight_sources/
        ├── chunk_distance/
        ├── color_map_color_util.rs
        └── levelgen/
            ├── synth/
            ├── density/
            ├── router/
            ├── noise_fill/
            ├── biome_fill/
            ├── aquifer/
            ├── surface/
            ├── proto_chunk/
            ├── carver/
            ├── feature/
            ├── blending/
            ├── heightmap/
            ├── math.rs
            └── random.rs
```

## Directory Responsibilities

### `app/`

Application-level orchestration belongs here. This is the intended home for future startup, lifecycle, configuration, and high-level runtime coordination that does not belong to a lower-level engine subsystem.

### `core/`

Shared engine primitives belong here. Use this for low-level types, algorithms, memory utilities, math, identifiers, and cross-subsystem foundations that are not specifically rendering, world, platform, or gameplay code.

### `world/`

World simulation and world data helpers belong here. Current code includes
`world/level/color_map_color_util.rs` for Java color-map behavior,
`world/level/biome/` for biome corner selection and [climate lookup](world/biome/RUST-CLIMATE.md) (with the
[biome fill](world/biome/RUST-BIOME-FILL.md) in `world/level/levelgen/biome_fill/`),
[eligible biome searches](world/biome/RUST-BIOME-SEARCH.md) in
`world/level/biome/search/` for compiled climate sampling and search traversal
while Java retains acceptance preparation and horizontal random selection,
[skylight-source reconstruction](world/lighting/RUST-SKYLIGHT-SOURCES.md) and
[block/sky light propagation and sky seeding](world/lighting/RUST-LIGHT-PROPAGATION.md)
under `world/level/lighting/`, [block-section save packing](world/chunk/RUST-PALETTE-PACKING.md)
under `world/level/chunk/palette/`, and `world/level/levelgen/` for
noise synthesis, density evaluation, aquifers, surface evaluation with eligible
native conditions and cached rule programs over
[Rust-owned chunk storage](world/levelgen/RUST-SURFACE-STORAGE.md), the
[carvers stage](world/levelgen/carver/RUST-CARVERS.md), worldgen
randomness, the [NOISE fill](world/levelgen/RUST-NOISE-FILL.md) that owns
base-terrain block writes, eligible cell traversal and native-route aquifer
materials, and the
[noise router](world/levelgen/RUST-NOISE-ROUTER.md) that fills interpolation
slices and evaluates shared point programs for
[preliminary surface levels](world/levelgen/RUST-PRELIMINARY-SURFACE.md) and
[aquifer fluid sources](world/levelgen/RUST-AQUIFER.md#native-fluid-sources).
[Per-seed chunk-noise templates](world/levelgen/RUST-CHUNK-NOISE.md) let eligible
fills instantiate their programs without per-chunk Java graph wrapping. See
[Rust World-Generation Organization](world/levelgen/RUST-WORLDGEN-ORGANIZATION.md) for module
ownership, native boundaries, and recorded verification.

Packed chunk storage also uses [palette histograms](world/chunk/RUST-PALETTE-HISTOGRAM.md)
under `world/level/chunk/palette/histogram/` for ordered counting and section
counter reconstruction, and [palette resizing](world/chunk/RUST-PALETTE-RESIZE.md)
under `world/level/chunk/palette/resize/` for bulk remapping during block palette growth.
[Global palette loading](world/chunk/RUST-PALETTE-UNPACKING.md) uses
`world/level/chunk/palette/unpack/` and the existing encoder for saved-data repacking.
[Player chunk distances](world/chunk-loading/RUST-PLAYER-DISTANCE.md) use
`world/level/chunk_distance/` for DistanceManager's natural-spawn, player-ticket,
[simulation](world/chunk-loading/RUST-SIMULATION-DISTANCE.md) and
[loading](world/chunk-loading/RUST-LOADING-DISTANCE.md) distance graphs and
[POI village](world/chunk-loading/RUST-POI-DISTANCE.md) section distances;
Java keeps player sets, tickets, POI records, holders and published level views.
[Ordered palette values](world/chunk/RUST-PALETTE-DISTINCT.md) use
`world/level/chunk/palette/distinct/` for biome/block scans; Java retains palette
resolution and callback delivery.
[Geode density fields](world/levelgen/feature/RUST-GEODE.md) use
`world/level/levelgen/feature/geode/` for one grid batch; Java retains ordered
random draws, placement decisions and world callbacks.
[Canyon ellipsoids](world/levelgen/carver/RUST-CANYON.md) use
`world/level/levelgen/carver/canyon/` for pure canyon candidate geometry on
chunks that retain Java carving. Eligible chunks instead use the
[Rust carvers stage](world/levelgen/carver/RUST-CARVERS.md), including tunnel
randomness, carving masks and block operations over native chunk storage.
[Old-terrain height blending](world/levelgen/blending/RUST-HEIGHT-BLENDING.md) uses
`world/level/levelgen/blending/` for current chunk-grid evaluation; Java retains
direct lookup precedence, sample traversal and scalar/custom compatibility.

World collision geometry also uses [voxel Boolean joins](world/physics/RUST-VOXEL-JOIN.md)
under `world/phys/shapes/boolean_join/`. Java keeps coordinate merging and
shape ownership while Rust evaluates packed occupancy.
Ordered [merged voxel boxes](world/physics/RUST-VOXEL-BOXES.md) use
`world/phys/shapes/box_extract/`; Java retains the original snapshot and callbacks.
[Voxel rotation](world/physics/RUST-VOXEL-ROTATION.md) uses
`world/phys/shapes/rotation/` for packed axis transforms while Java retains
floating coordinates and shape ownership.
[Closest collision points](world/physics/RUST-VOXEL-CLOSEST-POINT.md) use
`world/phys/shapes/closest_point/` to combine the established box traversal
with clamping and ordered distance reduction in one call. Java retains input
ownership, custom compatibility and result construction.
[Ray/shape intersection](world/physics/RUST-VOXEL-RAYCAST.md) uses
`world/phys/shapes/raycast/` for ordered outside-ray hits without exporting a
box list. Java retains public ray prechecks and immediate inside-hit behavior.

### `gameplay/`

Gameplay systems belong here. `gameplay/tacz/function_modifier.rs` already
contains the TaCZ function-modifier kernel. Native player, entity, item,
combat, block-interaction and component systems migrate here by ownership;
the proposed general [game model](game-model/index.md) is not yet implemented
beyond its block registry.

### `content/`

[`content/state`](game-model/STATE-GRAPHS.md) owns ordered state domains,
Cartesian values and transition graphs. The shared registry and temporary
Java block/fluid views use it. Java block declarations and general gameplay
remain outside this completed construction slice.

[`content/fluid`](game-model/FLUID-DEFINITIONS.md) owns built-in fluid names,
registration order, property declarations, defaults and intrinsic state facts.
Java projects compatibility objects; world-dependent fluid simulation remains
Java. The registry is immutable and independent of its consumers.

Content definitions and registries belong here. Use this for native representations of blocks, items, fluids, models, recipes, data-driven definitions, and other game content metadata. Today it holds the [block registry](game-model/RUST-BLOCK-REGISTRY.md) (`content/block/`): every block, property and block state with per-state columns, populated once by a lazy export from Java's frozen registries. World and storage subsystems derive their tables from it, and terrain meshing combines its block facts with render-owned columns. The block registry is immutable for the process lifetime; rendering's cache and resource lifecycle remain separate. Java still owns block definitions, and Rust item/entity registries and general gameplay components remain proposals. `content` must not depend on its consumers.

### `render/`

Rendering systems belong here. This includes backend-independent render code, native render data structures, chunk rendering helpers, and backend-facing rendering subsystems. For how to work on the renderer, start at the [Rendering](rendering/index.md) developer docs.

Important current subdirectories:

- `render/chunk/`: native chunk-rendering infrastructure, render lists, occlusion, translucent sorting, index generation, and rebuild triggers.
- `render/chunk/meshing/`: native chunk mesher implementation, including section scanning, static models, fluids, lighting/AO, tinting, culling, packing, assembly, FFI records, and diagnostics.
- `render/vulkanic/`: the VulkanicGAL graphics abstraction layer (handles, resources, commands, frames, sync, capabilities, metrics) and GAL creation. See its [README](https://github.com/HungLo2020/MattMC/blob/master/src/main/rust/render/vulkanic/README.md).
- `render/vulkanic/backends/`: private backend implementation modules. Code outside `render::vulkanic` must not call into backend modules directly.
- `render/dh_collector/`: the Distant Horizons column ledger: generations, column payloads, publication, retirement, owner leases, the prepared frame's visible segments and route receipts ([rendering architecture](rendering/RENDER-ARCHITECTURE.md)). Its Java exports are `render/bridge/dh_collector.rs`. Java still supplies the DH quadtree candidates, material provenance and frame parameters; native ledger ownership does not move those producers into Rust.
- `render/scene/`: wire and data vocabulary shared by Java and the renderers ([README](https://github.com/HungLo2020/MattMC/blob/master/src/main/rust/render/scene/README.md)).
- `render/shared/`: helpers used by both the world and GUI renderers ([README](https://github.com/HungLo2020/MattMC/blob/master/src/main/rust/render/shared/README.md)).
- `render/shaderpack/`: shader-pack parsing, planning and runtime ([README](https://github.com/HungLo2020/MattMC/blob/master/src/main/rust/render/shaderpack/README.md)).
- `render/worldrender/`: the world renderer ([README](https://github.com/HungLo2020/MattMC/blob/master/src/main/rust/render/worldrender/README.md)). `terrain/staging.rs` retains assembled vertices by mesh key and generation until asset acceptance or discard. `terrain/publication.rs` owns published section-layer identities and changed rows, synchronized directly into the Rust section graph. Java still registers/removes those identities, coordinates asset acceptance and resource-reload swaps, and supplies build/sort/atlas inputs. See [terrain publication](rendering/JAVA-BRIDGE.md#terrain-publication-registry) and [terrain intake](rendering/JAVA-BRIDGE.md#terrain-layer-intake) for the ownership, copy and lifetime limits.
- `render/guirender/`: the GUI renderer: sprites, quads, item meshes and rasters, the panorama and GUI post effects ([README](https://github.com/HungLo2020/MattMC/blob/master/src/main/rust/render/guirender/README.md)).
- `render/bridge/`: the Java FFI bridge: the `mattmc_vulkanic_gal_*` C ABI, wire records, decoding and the context registry. It is the only code that creates GALs and chooses their backend ([README](https://github.com/HungLo2020/MattMC/blob/master/src/main/rust/render/bridge/README.md)).

### `assets/`

Asset loading, decoding, caching, and resource processing belong here. Future Rust-side texture, model, shader, language, and pack-resource work should live here when it is not exclusively part of a rendering backend.

### `network/`

Network protocols and wire codecs belong here. Current
[bulk long-array conversion](network/codec/RUST-LONG-ARRAY.md) uses
`network/codec/long_array/` for exact big-endian conversion. Java retains Netty
buffer ownership, length prefixes, cursor updates and compatibility behavior.

### `storage/`

Persistent encodings and region storage belong here. The
[chunk-section serializer](world/chunk/RUST-CHUNK-SECTIONS.md) uses `storage/chunk/`
to encode eligible section palettes, packed values, light layers and section Y
straight into NBT tape. Rust derives the block-state save vocabulary and
palette storage bits from the shared block registry. Java still snapshots live
chunks, supplies per-chunk biome names, builds the remaining root compound
and owns save scheduling. Pending writes retain tape and create Java tags lazily when read;
unsupported section inputs keep the Java encoding route. Eligible current-version
loads also decode section tape natively; Java reconstructs containers and the
remaining chunk state, with non-current/noncanonical input retaining upgrade
and parse compatibility. This boundary does not migrate the complete chunk
lifecycle or establish concurrent-save parity.
The `nbt/`, `poi/` and `region/` modules retain their separate responsibilities.

### `platform/`

Platform abstraction belongs here. Use this for OS, filesystem, threading, native library, windowing-adjacent, and platform-specific integration that should not leak into game or render logic.

### `compat/`

Compatibility and interop layers belong here. Use this for bridge code that exists because Java, Fabric, shader packs, mods, or legacy systems still need adaptation during the incremental migration.

### `tools/`

Developer and offline tooling belongs here. This is for Rust code that supports build-time utilities, diagnostics, conversion tools, generators, or replay/benchmark helpers rather than runtime engine behavior.

## Boundary Notes

Rust `render/vulkanic/backends/` is intentionally private. The architecture tests enforce that:

- `render::vulkanic` owns backend routing.
- Rust code outside `render::vulkanic` cannot reference backend implementation modules.
- `ash` and `shaderc` usage stays inside the Vulkan backend.
- `glow` usage stays inside the OpenGL backend.
- OpenGL and Vulkan backend modules do not depend on each other.

The same tests (`render/vulkanic/architecture_boundary.rs`) enforce the renderer layering: which render layer may depend on which, and that only the bridge creates GALs. See [Render Architecture](rendering/RENDER-ARCHITECTURE.md) for the rules and where new rendering code belongs.

Java package names may still appear in Java source and Java tests. The Rust tree should use subsystem ownership instead of Java-style package paths.
