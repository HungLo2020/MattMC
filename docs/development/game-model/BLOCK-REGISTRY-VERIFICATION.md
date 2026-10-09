# Block registry verification

> Author-recorded evidence for [the Rust block registry](RUST-BLOCK-REGISTRY.md)
> (Phase 1), recorded on 2026-10-07 on a laptop (Intel i7-10750H, Linux x86_64,
> OpenJDK 25, `powersave` governor with turbo). It compares against commit
> `5457b41ae`, the last commit before the registry. The results below predate
> the final format-2 meshing view at `7a6009f8`; they are not a fresh acceptance
> run of that head.

## Parity

[`VerifyRustBlockRegistry.py`](https://github.com/HungLo2020/MattMC/blob/master/DevUtils/tests/content/VerifyRustBlockRegistry.py)
was reported by the implementation author to have run 65 Java tests and
48 Rust tests with no failures. Key counters from that run:

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
- Rendering was outside the recorded comparison. Its meshing block facts were
  subsequently switched to the registry in `7a6009f8`; no rendering performance
  or reload result follows from the measurements above.

## Current source and verification scope

The figures above remain the author's historical Phase 1 record. At the
reviewed source
[`87046367`](https://github.com/HungLo2020/MattMC/commit/87046367cdf0a4a427f10066a9010dd6d39fd422),
[registered definitions](BLOCK-DEFINITIONS.md), [physical profiles](BLOCK-PHYSICS.md),
[intrinsic state rules](BLOCK-INTRINSICS.md), [properties](PROPERTY-DEFINITIONS.md),
[fluids](FLUID-DEFINITIONS.md), [state graphs](STATE-GRAPHS.md),
[sound definitions](SOUND-DEFINITIONS.md), [block sounds/offsets](BLOCK-SOUND-AND-OFFSETS.md)
and [block-family configuration](BLOCK-FAMILY-TYPES.md) have native owners.
The [format-8 decoder](https://github.com/HungLo2020/MattMC/blob/87046367cdf0a4a427f10066a9010dd6d39fd422/src/main/rust/content/block/export.rs)
reads native names, layouts/defaults, emission, fluid-state associations and
offset kinds/limits. It derives air/can-occlude and fluid flags natively, while
Java still exports face IDs/truth tables, remaining state flags and blocked
light. Java also retains shapes, contextual predicates, cache initialization
and world callbacks. Synthetic/unregistered compatibility objects do not
provide fallback for missing registered definitions.

The current [v8 observer](STATE-GRAPHS.md#verification) retains v7 sound/offset
coverage and adds family definitions, aliases/codecs and registered bindings.
Later milestone results belong to their linked pages, not the Phase 1 totals
above. The author's [family summary](https://github.com/HungLo2020/MattMC/blob/87046367cdf0a4a427f10066a9010dd6d39fd422/SUMMARY.md)
reports 1,711 Java/2,366 Rust passes (two skips/three ignores), seven lifecycle
cases, two reviewed static image pairs and eight matching content digests.
It also explicitly records **Performance FAIL**: vanilla and vanilla+DH fail
average FPS and p99, including a 28.9% vanilla+DH median FPS gap. Both shader
modes pass that run. These are author-recorded results, not independently
reproduced acceptance or an isolated family performance attribution.

The block registry's 31,809 states are distinct from the observer's 31,846
combined block/fluid states: the latter includes 37 fluid states. Likewise,
491,067 block-property transitions and 491,395 combined graph transitions
describe different scopes. These counters are preserved from author-recorded
results, not independently rerun here.

The following format-2 paragraph is a **historical source checkpoint**.
Static review at
[`7a6009f8`](https://github.com/HungLo2020/MattMC/commit/7a6009f84d966263293f864933f4da06b1823dfa)
confirmed format 2 exported fluid/offset facts and that the meshing view derived
block facts from the installed registry. That revision's
[`NativeMeshingStateViewTest`](https://github.com/HungLo2020/MattMC/blob/7a6009f84d966263293f864933f4da06b1823dfa/src/test/java/net/sodium/client/render/chunk/compile/pipeline/NativeMeshingStateViewTest.java)
compares 17 integer fields and 18 raw float-bit fields per state in each of two
fluid modes. For the author's reported 31,809 states, that would be 63,618
records; this is a calculation of fixture scope, not a newly observed pass.
`NativeBlockRegistryTest` at that checkpoint also checked fluid kinds, heights
and offsets.

The aggregate driver's `PARITY` list does not select the meshing fixture, so
its earlier 65-Java/48-Rust report must not be treated as covering that change.
Run the separate command on the [registry page](RUST-BLOCK-REGISTRY.md#testing)
when validating meshing changes. The fixture compares explicit and derived
records; it does not exercise a live rendered world or resource/world reloads.

The driver records `faster_5pct` and `no_regression` booleans but does not fail
solely because either is false. Its full multi-fork hot-path comparison was
not completed in the reported session. A report file or successful command
exit alone therefore does not establish full Phase 1 performance acceptance.
This documentation review ran no Java/Rust tests, mutation tests, benchmarks
or live worlds, and did not independently reproduce the runtime counters.
