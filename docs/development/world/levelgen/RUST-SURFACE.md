# Rust surface evaluation

## Production path

`SurfaceSystem.buildSurface` compiles its rule tree into a private, forward-only
native program once per chunk. Ordinary `ProtoChunk` columns are scanned and
evaluated in Rust. For those chunks Rust also owns the chunk's sections and
world-generation heightmaps for the whole stage, commits blocks in the original
descending order and installs the results once; see
[surface chunk storage](RUST-SURFACE-STORAGE.md). The native library is
required; there is no Java arithmetic fallback in this production path.

The migrated operations are the solid/fluid/air column scan, stone-depth and
water tracking, rule sequencing and negation, stone/water/Y/hole/preliminary
surface predicates, vertical-gradient bounds, biome membership, and
terracotta-band selection. Biome
Voronoi corner selection runs once per column in Rust and reuses corner jitter
within each group of four block heights. On Rust-owned storage the selected
quarts are read from a per-chunk table of registry IDs Java fills once;
otherwise Java resolves the selected quart biome holders per column.

Java still owns the 16×16 X/Z column loop, its per-column context update and
biome lookup for extensions, registry bindings, noise/random objects,
temperature and custom callbacks. The badlands and frozen-ocean extensions
also remain Java (reading and writing Rust-owned storage through a block
column on eligible chunks). The existing
`SurfaceRules` Java bindings remain available to extensions and provide a test
oracle. Carvers' `topMaterial` uses the native rule evaluator through a scalar
adapter; these contexts retain their supplied Java biome lookup and predicate.

Unknown extension rules/conditions, specialized chunk/system/biome-manager
subclasses, and unusually large/deep rule trees use the incremental Java column
traversal with native rule evaluation. This preserves callbacks that inspect or
mutate the chunk. These compatibility paths are not covered by the performance
claim for shipped rules on ordinary chunks.

## Ordering and arithmetic

The column scanner reads the same original solid runs as Java. Native evaluation
yields before a callback. Java first commits every preceding completed block,
then updates the callback context and answers the request. In particular, a
lazy steepness condition sees earlier heightmap changes, and fluid replacements
still produce the same ordered postprocessing entries. No block write is
silently skipped because its state equals the existing state.

Integer arithmetic that wraps in Java uses wrapping Rust operations. Floating
point expression order, saturating float-to-integer conversion, biome distance
comparison, and first-match rule order are preserved. The native evaluator does
not reorder predicates or eagerly evaluate noise/random conditions. Vertical
gradients first check the lower bound, then the upper bound, exactly as Java
does; only heights strictly between those bounds request the Java random test.

## Boundary and ownership

The FFM interface borrows Java-owned arrays for one call and retains no pointers.
Each compiled evaluator owns its mutable buffers; evaluators are confined to
their chunk/task. Only the FFI entry points construct slices from raw pointers.
Their safety contracts specify lengths, alignment, disjoint buffers, and the
validated immutable program required from the Java caller.

Programs use eight `i32` words per instruction. Word zero is the instruction
area length; instructions begin at word eight. Instructions contain opcode,
four operands, false-branch target, inversion flag, and a reserved word.
Trailing data holds sorted biome IDs or terracotta block IDs. Validation checks
instruction ranges, forward jumps, opcodes, and data ranges before execution.
`world/level/levelgen/surface/frame.rs` names the resumable frame slots;
`program.rs` owns rule opcodes and validation, `evaluator.rs` keeps the fused
scan/rule loop, and `ffi.rs` exposes the existing symbols. Biome corner selection
lives in `world/level/biome/fiddled_distance.rs`, with its existing surface ABI
adapter preserved. See [module organization](RUST-WORLDGEN-ORGANIZATION.md).

Columns are bounded at 4,096 entries, covering the supported dimension-height
limit plus the top sentinel. One evaluator call executes at most 8,192 steps;
Java resumes bounded yields. Large rule trees are partitioned into native
continuations. Large program validation uses a regular downcall and native
memory instead of a long heap-pinning critical call.

## Reproducing verification

Use JDK 25 and a release native library. No copied Java implementation is kept
in production or test source: the driver extracts the original `SurfaceSystem`
and `SurfaceRules` from commit
`0719ec4bd5f4956ceb9f341c9c641ad7294ef632` and compiles them into an isolated
reference classpath under `build/`.

```sh
./gradlew -PmattmcRustProfile=release test \
  --tests net.minecraft.world.level.levelgen.NativeSurfaceTest -x testRustNative
rustc --edition=2021 --test src/test/rust/worldgen.rs \
  -o build/surface-rust-tests
build/surface-rust-tests
python3 DevUtils/tests/worldgen/VerifyRustSurface.py --forks 3
```

The focused Gradle invocation excludes the repository-wide Rust test task,
which also builds unrelated renderer/audio tests. The standalone Rust command
above tests world-generation modules and their shared dependencies directly.

`VerifyRustSurface.py` records reference revision, native binary hash, exact
fingerprints, every timing sample, and compilation time. It checks all eight
noise settings, five seeds including both long extremes, all registered biomes,
negative and world-border coordinates, mixed biome columns, and custom rule
fixtures. Fingerprints cover every block, primed heightmaps, and ordered fluid
postprocessing lists. The focused tests additionally cover integer overflow,
NaN/infinity/subnormal secondary noise, lazy observation order, biome selection,
and large/deep programs.

Timing includes `SurfaceSystem.buildSurface`: native program construction,
column preparation, every boundary crossing, Java callbacks, and block writes.
Fixture construction is outside the timer. Independent JVM forks alternate
Java/Rust order, use `-Xbatch`, and perform 12 warmup plus 12 measured rounds of
24 fresh chunks per setting. The gate requires at least 5% less time in every
fork's median and in a hierarchical bootstrap's upper 95% ratio bound. For a
conservative comparison, measured Java compilation time plus one millisecond
is subtracted from each Java sample before calculating the gate.

Full-world checks use isolated servers and fresh worlds under `build/`:

```sh
python3 DevUtils/tests/worldgen/VerifyRustSurfaceWorld.py parity-java --reference --hash \
  --serial --workers 1 --radius 2 --rounds 1 --warmups 0
python3 DevUtils/tests/worldgen/VerifyRustSurfaceWorld.py parity-native --hash \
  --serial --workers 1 --radius 2 --rounds 1 --warmups 0 --compare parity-java
```

Compare the per-region surface/carver JSON maps and `full_fingerprints` in
`timings.json`. Omit `--hash` and `--serial` for normal parallel full-generation
timings. Each server binds only to loopback on an ephemeral port, saves its
isolated world, and shuts down. No user world is opened.

## Scope of the evidence

Exact equality on a finite corpus is not a proof over every possible seed or
third-party callback. Full-generation results also depend on later feature
placement: independent runs of the original Java code produced different FULL
chunks under parallel scheduling despite identical surface chunks. Java control
runs and fixed-order parity runs are retained so those existing differences are
not misreported as either native parity or native failures.

Measured results and artifact paths follow.

## Final parity results — 2026-09-30

- Four focused Java tests passed, including 196,000 scalar rule comparisons,
  600 biome-column cases, maximum-size columns, lazy write observations, and
  large/deep rule trees. Three standalone Rust tests passed.
- **3,280 / 3,280** fixture chunk fingerprints matched, across eight noise
  settings (both legacy and modern random sources) and five seeds. Eight custom rule fixtures per setting/seed include
  a mutated biome-key list, air/fluid replacements, empty sequences, nested
  negation, terracotta bands, equal gradient bounds, and a mutating callback.
- Two isolated fixed-order world runs (seeds `42` and `-123456789`) matched
  **648 surface checkpoints, 648 carver checkpoints, and 200 FULL chunks**
  across Overworld, Primordial Caves, Nether, and End. There were no differences
  in the checked blocks, heightmaps, or postprocessing lists.
- The Java-versus-Java controls are retained under
  `build/surface-migration/full/parity-42-java-control/` and
  `build/surface-migration/full/serial-42-java-control/`. Parallel original runs
  differed in 230 of 392 FULL chunks while every surface checkpoint matched.
  The fixed-order original control differed in two of 100 FULL chunks; one
  inspected difference was a single leaf-litter block at a chunk corner.
  These controls show that full-world reproducibility cannot be inferred from
  seed alone under arbitrary scheduling, even with the original implementation.

Final exact comparisons: `build/surface-migration/parity-verified/results.json`
and `build/surface-migration/world-verified/parity-*/comparison.json`.

Production native SHA-256:
`5ae897da3edc42d1501e982941145cc95d19e9208f56f34c37eff4c3e003cd70`.

## Surface performance — final release build

Ryzen 5 5600G, JDK 25.0.4.1, Rust 1.93.1, Linux x64. Each time is the median
of three independent fork medians, in milliseconds per 24 fresh surface passes.
All preparation, FFM crossings, callbacks, and ordered writes are included.

| Setting | Original Java | Rust production path | Less time | Conservative 95% ratio interval |
| --- | ---: | ---: | ---: | --- |
| amplified | 484.286 ms | 176.405 ms | 63.6% | 0.3574–0.3836 |
| caves | 273.249 ms | 116.511 ms | 57.4% | 0.4250–0.4602 |
| end | 43.532 ms | 34.698 ms | 20.3% | 0.8126–0.8227 |
| floating_islands | 309.344 ms | 115.845 ms | 62.6% | 0.3689–0.3812 |
| large_biomes | 255.989 ms | 158.217 ms | 38.2% | 0.6154–0.6275 |
| nether | 160.038 ms | 78.322 ms | 51.1% | 0.4853–0.5134 |
| overworld | 305.896 ms | 167.973 ms | 45.1% | 0.5211–0.5697 |
| primordial_caves | 312.992 ms | 186.461 ms | 40.4% | 0.5518–0.6066 |

Every setting passed: every fork median was at least 5% faster and every
upper 95% ratio bound was below 0.95. The weakest conservative upper bound
was 0.8227 (End), corresponding to at least 17.7% less time by this gate.
The interval uses the deliberately reduced Java timings described above;
the displayed medians are raw elapsed times.

Raw samples and gate results: `build/surface-migration/benchmark-verified/results.json`.
Each fork log also records JVM compilation time. Surface timing is distinct
from full chunk generation, which includes unchanged density, carver, feature,
and lighting work.

## Full-generation measurements and limits

Three additional Java/Rust process pairs used normal parallel chunk generation
(three workers, CPUs 2–5), alternating process order, with seeds `42`,
`-123456789`, and `42`. Each dimension had three warmup regions of 81 requested
chunks followed by three measured regions of 121 requested FULL chunks per
process. Across the three forks, that is nine measured regions and 1,089 FULL
chunk requests per dimension per implementation. Neighboring dependency chunks
are included in the work. Explicit saving and the inter-region drain are outside
the generation timer. These runs have no fingerprinting or stage instrumentation.

These full-generation samples include remaining asynchronous JVM compilation
and GC. Their intervals describe the measured workloads on this machine; they
are not a guarantee for every seed or workload. In particular, they do **not**
establish a 5% improvement for total Overworld or End generation. The surface
performance gate above measures the complete migrated surface pass, including
all Java/Rust boundary costs.

A separate stage-timed Overworld check for seed `-123456789` measured 225 surface
calls in each of three regions. Surface elapsed time fell **35.2%, 17.6%, and
14.1%**, respectively; surface CPU time fell **35.4%, 17.6%, and 14.0%**.
Surface allocation fell **78.5%–85.8%**. Thus the faster surface pass also appears
in real terrain. The slower overall samples spent more time in unchanged
noise/block filling; the full-generation data cannot isolate the cause of that
variation or establish a whole-engine Overworld gain.

Raw full-generation logs, commands, process exits, and timings:
`build/surface-migration/world-verified/timing-*/`.
Stage breakdown: `build/surface-migration/full/stage-comparison.json`.
Surface chart: `build/surface-migration/surface-performance.svg` (also PNG).

### Full-generation totals

Elapsed seconds sum the nine measured regions per dimension. “Less time”
is calculated from these totals. The 95% intervals resample paired regions
within JVM forks and describe the native/Java elapsed-time ratio. Unlike the
surface gate, these full-generation intervals do not subtract compilation time.

| Dimension | Java total | Rust total | Less time | 95% ratio interval | Less allocation |
| --- | ---: | ---: | ---: | --- | ---: |
| overworld | 45.130 s | 45.334 s | -0.5% | 0.9406–1.0643 | 22.2% |
| primordial_caves | 55.639 s | 46.035 s | 17.3% | 0.7609–0.9027 | 66.7% |
| the_end | 4.168 s | 4.136 s | 0.8% | 0.9705–1.0165 | 28.5% |
| the_nether | 23.071 s | 15.635 s | 32.2% | 0.6504–0.7025 | 54.0% |

Overworld and End intervals cross 1.0: these measurements do not establish
an overall generation improvement for either dimension. Primordial Caves and
Nether improved in every full-generation fork.

The additional End stage check used six warmup regions and three measured
regions, with 729 surface calls per measured region. Surface elapsed time fell
**7.7%, 19.6%, and 36.5%**; CPU time fell **9.3%, 19.5%, and 36.5%**.
Raw data: `build/surface-migration/full/end-stage-comparison.json`.

All final verification and benchmark servers exited successfully. Twelve frozen
source/test/driver/native-binary hashes still matched after measurement, and
HEAD remained unchanged. No commits or pushes were made.
