# Native structure pieces

`StructureStart.placeInChunk` hands each piece to
[`NativeStructurePieces`](https://github.com/HungLo2020/MattMC/blob/master/src/main/java/net/minecraft/world/level/levelgen/structure/NativeStructurePieces.java)
before its Java `postProcess`. Java keeps the piece objects (their saved form)
and encodes each piece's fields as words; Rust places the piece over the bound
decoration region and hands back the fields it changed (moved boxes, spawn and
chest flags). Entities, loot seeds and spawner entities are Java steps that
draw from the placement random in Rust's order.

## What runs in Rust

| Family | Rust | Java piece encoder |
| --- | --- | --- |
| Swamp hut, buried treasure, desert pyramid (and its `afterPlace` suspicious sand), jungle temple | [`structure/scattered.rs`](https://github.com/HungLo2020/MattMC/blob/master/src/main/rust/world/level/levelgen/structure/scattered.rs) | each piece's `nativeWords` |
| Mineshaft corridor, crossing, room, stairs | [`mineshaft.rs`](https://github.com/HungLo2020/MattMC/blob/master/src/main/rust/world/level/levelgen/structure/mineshaft.rs) | `MineshaftPieces` |
| Every stronghold piece | [`stronghold.rs`](https://github.com/HungLo2020/MattMC/blob/master/src/main/rust/world/level/levelgen/structure/stronghold.rs) | `StrongholdPieces` |
| Every Nether fortress piece | [`fortress.rs`](https://github.com/HungLo2020/MattMC/blob/master/src/main/rust/world/level/levelgen/structure/fortress.rs) | `NetherFortressPieces` |
| Ocean monument building and its child rooms (one call) | [`monument.rs`](https://github.com/HungLo2020/MattMC/blob/master/src/main/rust/world/level/levelgen/structure/monument.rs) | `MonumentBuilding.nativeWords`: the room graph its children reach |
| Woodland mansion `afterPlace` | [`structure/mod.rs`](https://github.com/HungLo2020/MattMC/blob/master/src/main/rust/world/level/levelgen/structure/mod.rs) | the pieces' boxes |
| Template pieces' tail: data markers (igloo, shipwreck, End city, ocean ruin, mansion), jigsaw `final_state` writes, the ruined portal's netherrack, drip columns, vines and leaves | [`template_tail.rs`](https://github.com/HungLo2020/MattMC/blob/master/src/main/rust/world/level/levelgen/structure/template_tail.rs) | `TemplateStructurePiece.nativeTailWords` |
| Feature pool elements (village trees and decor) | the decoration program with the caller's random | `NativeDecoration.placeWithRandom` |

Template blocks themselves place through [native templates](RUST-STRUCTURE-TEMPLATES.md);
the tail reads the markers and jigsaws compiled into the same template object.
A jigsaw pool element's data-marker pass is skipped when its class keeps the
no-op `handleDataMarker` (processors only read, so the pass has no effect).

## Contracts

- `java_step` publishes pending events before Java runs, so a loot step sees
  the chest Rust just wrote. Steps index Java's table: chest, dispenser,
  brushable and randomizable-container loot per table, then the fixed steps in
  [`Misc`](https://github.com/HungLo2020/MattMC/blob/master/src/main/rust/world/level/levelgen/structure/mod.rs).
- The region random (`WorldGenRegion.getRandom()`) comes back to Java only when
  Rust drew from it; Java steps (mob `finalizeSpawn`) draw from Java's copy.
- Shape and support tests use exported per-state bitsets; dynamic shapes ask
  Java through pure upcalls (`OP_STURDY`, `OP_CENTER_SUPPORT`,
  `OP_COLLISION_FACE`).
- `ctx.sea_level` is `getSeaLevel()`; the monument's water boxes compare
  world Y against it.

## Placement checks

`createStructures` asks
[`structure/placement.rs`](https://github.com/HungLo2020/MattMC/blob/master/src/main/rust/world/level/levelgen/structure/placement.rs)
which structure sets place in the chunk: random-spread grids, concentric-ring
chunks (exported once the rings exist), the four frequency reducers and
exclusion zones. Other placement types answer 2 and Java asks them.
`-Dmattmc.worldgen.javaStructurePlacement=true` keeps Java's checks.

## Jigsaw starts

`Structure.generate` hands jigsaw structures to
[`NativeJigsaw`](https://github.com/HungLo2020/MattMC/blob/master/src/main/java/net/minecraft/world/level/levelgen/NativeJigsaw.java),
which exports each structure's reachable pools (expanded weighted lists,
fallbacks), templates' per-palette jigsaw blocks and alias bindings once.
[`structure/jigsaw.rs`](https://github.com/HungLo2020/MattMC/blob/master/src/main/rust/world/level/levelgen/structure/jigsaw.rs)
then samples constant or uniform start heights, resolves aliases, places the
start piece, checks the stub's biome on the climate tree (carrying the
thread's previous leaf) and assembles the pieces. Free space is a box minus
placed boxes: Java's voxel shapes are integer boxes, so `joinIsNotEmpty` with
the deflated piece box is a cell-containment test. Heights come from the
[native column source](../RUST-NOISE-FILL.md#base-heights). Java builds the
`PoolElementStructurePiece`s from the plan. `jigsawPlanned=` and
`jigsawJava={reason=count}` report the route; `-Dmattmc.worldgen.javaJigsaw=true`
keeps Java's planner.

## Other start positions

[`NativeStructureStubs`](https://github.com/HungLo2020/MattMC/blob/master/src/main/java/net/minecraft/world/level/levelgen/NativeStructureStubs.java)
finds the start position and checks its biome in
[`structure/stubs.rs`](https://github.com/HungLo2020/MattMC/blob/master/src/main/rust/world/level/levelgen/structure/stubs.rs)
for structures whose `nativeStub()` names a family, then (for valid stubs)
plans every piece in Rust. Java rebuilds the pieces from the returned words
(`addNativePieces` / `nativePlanPieces`) without drawing randomness; Rust
redraws everything the context random gives, so its state starts fresh.

| Structures | Start position | Pieces |
| --- | --- | --- |
| Ocean monument | surrounding-biome scan (every quart, as `getBiomesWithin` updates the previous leaf) | [`monument_plan.rs`](https://github.com/HungLo2020/MattMC/blob/master/src/main/rust/world/level/levelgen/structure/monument_plan.rs): room graph, fitted rooms, wings |
| Desert pyramid, jungle temple | lowest corner against sea level | [`small_plan.rs`](https://github.com/HungLo2020/MattMC/blob/master/src/main/rust/world/level/levelgen/structure/small_plan.rs) |
| Swamp hut, buried treasure, igloo, shipwreck, ocean ruin | chunk-centre heights | [`small_plan.rs`](https://github.com/HungLo2020/MattMC/blob/master/src/main/rust/world/level/levelgen/structure/small_plan.rs) (igloo basements, ocean ruin clusters) |
| Fortress, stronghold | fixed position | [`fortress_plan.rs`](https://github.com/HungLo2020/MattMC/blob/master/src/main/rust/world/level/levelgen/structure/fortress_plan.rs), [`stronghold_plan.rs`](https://github.com/HungLo2020/MattMC/blob/master/src/main/rust/world/level/levelgen/structure/stronghold_plan.rs) (re-seeded retries until a portal room exists) |
| Mansion, End city | rotation draw, then the 5x5 lowest corner | [`mansion_plan.rs`](https://github.com/HungLo2020/MattMC/blob/master/src/main/rust/world/level/levelgen/structure/mansion_plan.rs) (grid and template pieces), [`end_city_plan.rs`](https://github.com/HungLo2020/MattMC/blob/master/src/main/rust/world/level/levelgen/structure/end_city_plan.rs) (towers, bridges, ship) |
| Mineshaft | chunk position | [`mineshaft_plan.rs`](https://github.com/HungLo2020/MattMC/blob/master/src/main/rust/world/level/levelgen/structure/mineshaft_plan.rs): room, corridors, crossings, stairs, vertical move |
| Ruined portal | every `findGenerationPoint` draw and `findSuitableY`'s four base columns | Java builds the single piece from the drawn setup, template, rotation and mirror |
| Nether fossil | random column and uniform height, base-column floor scan | Java replays the draws and builds the piece |

Template-based planners take each template's size from
`nativePlanParams(StructureTemplateManager)` (boxes come from template sizes),
and a missing or unexpected template keeps Java's planner. Base columns come
from the [native column source](../RUST-NOISE-FILL.md#base-heights). Plain
multi-noise biome sources and the End's source only; `structureStubs=` counts
the starts and `-Dmattmc.worldgen.javaStructureStubs=true` keeps Java's search
and planners. Monuments loaded from disk still regenerate their pieces in
Java (`regeneratePiecesAfterLoad`).

## Verify

```sh
python3 DevUtils/tests/worldgen/VerifyFullGeneration.py --output build/full-generation/<label> --skip-build --rounds 0 --radius 3 \
  --sides reference,current --reference-root build/reference-<commit> --dimensions minecraft:overworld \
  --structures minecraft:monument,minecraft:mansion,minecraft:igloo,minecraft:ruined_portal,...
```

Use `--dimensions minecraft:the_nether` for fortresses and Nether fossils and
`minecraft:the_end` for End cities. `pieces=` counts native placements;
`javaPieces={class=count}` lists structure work Java still wrote inside a bound
region (template tails as `tail <class>`, feature pool elements by reason).
