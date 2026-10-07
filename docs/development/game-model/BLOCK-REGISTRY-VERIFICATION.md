# Block registry verification

> Evidence for [the Rust block registry](RUST-BLOCK-REGISTRY.md) (Phase 1),
> recorded on 2026-10-07 on a laptop (Intel i7-10750H, Linux x86_64,
> OpenJDK 25, `powersave` governor with turbo). It compares against commit
> `5457b41ae`, the last commit before the registry.

## Parity

[`VerifyRustBlockRegistry.py`](https://github.com/HungLo2020/MattMC/blob/master/DevUtils/tests/content/VerifyRustBlockRegistry.py)
ran 65 Java tests and 48 Rust tests with no failures. Key counters:

- `NativeBlockRegistryTest`: 1,235 blocks and 31,809 states. Each state's
  block, flags, light block, emission and six faces match Java. All 491,067
  `setValue` results match. The 55 face IDs equal exact box lists, and their
  truth table equals `Shapes.faceShapeOccludes`.
- Derived views, checked for every state:
  - light types: 83 types
  - skylight catalog: 28 compact faces, 2,256 original shape pairs
  - noise flags
  - save fragments: 11,038,126 bytes, equal to `BlockState.CODEC`'s tape
- Each consumer's existing parity suite passed unchanged in substance:
  - light propagation: 1,801 Rust passes
  - skylight: 65,536 columns
  - heightmaps: 6,291,456 columns
  - noise fill, surface, carvers
  - chunk-section save and load: 40 saved chunks
  - palette packing: 2,752,512 entries

Mutation testing applied 11 deliberate bugs, one at a time:
- **9 were caught.** These covered stride order, the exported leaves flag,
  the heightmap leaves rule, light opacity, unmapped light palette IDs,
  swapped skylight faces, vocabulary property order, default-state
  properties, and the carvers' block lookup.
- **1 is equivalent:** dropping the heightmap's explicit AIR-block test
  changes no mask, because `isAir()` already clears every bit.
- **1 exposed a gap:** dropping the noise flags' AIR-block bit went
  undetected, because no fixture writes `minecraft:air` into an all-air
  section. `NativeNoiseFillTest.stateFlagsMatchEveryState` now checks every
  state and catches it.

## Startup

One-time table construction, from frozen registries to every table the Rust
consumers use, timed by
[`BlockTableStartup.java`](https://github.com/HungLo2020/MattMC/blob/master/DevUtils/tests/content/BlockTableStartup.java).
Each sample is a fresh JVM. There were two independent runs of 8 samples per
tree, in alternating order:

| Tree | Median | Range |
|---|---:|---:|
| Reference: eight Java builders | 1.41–1.49 s | 1.37–1.60 s |
| Registry: one export, Rust install and views | 0.76 s | 0.72–0.93 s |

That is about **48% less time**. Every registry sample was faster than every
reference sample. Which old builder cost the most was not profiled.

## Hot paths

The per-call paths do the same work as before. Java passes state IDs where it
used to pass per-palette values, and Rust maps them. They were **spot-checked**,
not fully measured:

- **Palette packing**, alternating fresh JVMs, minimum per-call times:
  - `random_64`: 12.2–12.7 µs against 13.1–13.5 µs
  - `random_4096` (global palette): 103–106 µs against 110–113 µs

  An earlier version that passed no label table for local palettes too was
  about 12% slower on small palettes, for reasons not established. Local
  palettes therefore keep their 256-entry identity table.
- **Heightmap** `water` rerun in isolation: 334–348 µs against 345–383 µs, so
  the same within noise.
- **Every consumer benchmark** produced identical checksums in both trees.

A full multi-fork hot-path comparison was started and stopped. On this laptop,
single-fork ratios swung by ±50% with other load running, so they are not
reported. Run the driver on a quiet machine for that evidence:

```sh
python3 DevUtils/tests/content/VerifyRustBlockRegistry.py              # parity, startup and hot paths
python3 DevUtils/tests/content/VerifyRustBlockRegistry.py --case none  # parity and startup only
```

## Not measured

- Noise fill, proto chunk, surface and carvers. Their per-call path only lost
  a table pointer argument.
- Rendering, whose tables are unchanged.
