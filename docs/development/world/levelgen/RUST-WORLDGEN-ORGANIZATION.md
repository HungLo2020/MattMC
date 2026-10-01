# Rust world-generation organization

## Ownership

World generation lives under `src/main/rust/world/level/levelgen/`. Noise
synthesis (`synth`), density evaluation (`density`), aquifer evaluation (`aquifer`),
and surface evaluation (`surface`) are separate domains. Biome corner selection and
[climate lookup](../biome/RUST-CLIMATE.md) live under `world/level/biome/`.
Surface evaluation consumes biome corner selection's column results.

The original organization change was a structural refactor. Seed construction, Java graph/cache ownership,
native state layouts, exported symbol names, array bounds, and callback/write
order are preserved. The native library is still one Cargo `cdylib`.

```text
world/level/
├── biome/
│   ├── fiddled_distance.rs
│   └── climate/     # Ordered climate search, SIMD distances and FFI
└── levelgen/
    ├── math.rs
    ├── synth/       # Noise families, their optimized kernels, state and FFI
    ├── density/     # Programs, evaluation, cell kernels, End islands and FFI
    ├── aquifer/     # Center search, material/fluid decisions, cell batches and FFI
    └── surface/     # Rule program, resumable frame, evaluation and FFI
```

### Synthesis

- `improved.rs`: gradient noise and derivatives.
- `perlin.rs`: ordered Improved-noise octave accumulation.
- `normal.rs`: two Perlin samplers combined with the original scaling.
- `blended.rs`: lower, upper, and selector octave groups.
- `simplex.rs`: two- and three-dimensional Simplex evaluation.
- `perlin_simplex.rs`: ordered Simplex octave accumulation.
- Family subdirectories contain optimized kernels. `simplex/optimized.rs`
  deliberately names the bounded scalar specialization accurately.
- `dispatch.rs`: family dispatch, CPU/coordinate eligibility, bounded batches.
  `dispatch_avx2.rs` dispatches shared Improved/Perlin/Normal vector routes.
- `common.rs` and `avx2_helpers.rs`: shared scalar/vector primitives.
- `state.rs`, `validation.rs`, `ffi.rs`: Java-owned state, validation, and the
  unchanged exported native interface.

No dispatch guard moves into a caller. Internal Rust callers use the synthesis
API directly, without depending on exported C entry points.

### Density

`program.rs` owns the packed header/node layout, with named input/cell fields.
`validation.rs` owns structural and work-bound checks. `math.rs` implements the
shared exact-order arithmetic and Java NaN/signed-zero semantics.
`evaluator.rs`, `unary.rs`, and `operations.rs` implement the respective
expression, unary, and scalar/array routes. `cell/` contains general cell
evaluation and specialized terrain, unary, and AVX2 kernels. `end_islands.rs`
owns End terrain policy and consumes Simplex synthesis. `ffi.rs` keeps all
existing symbol names, including `mattmc_noise_end_island` for ABI compatibility.

`spline/` owns validated curve layouts, exact float evaluation, fused coordinate
transforms and its FFI. See [terrain splines](RUST-SPLINE.md) for caller boundaries
and focused verification.

Shared interpolation lives in `levelgen/math.rs`; density no longer depends on
the noise module for generic interpolation.

### Surface

`program.rs` owns rule opcode names and bytecode validation. `frame.rs` names
the existing 24-word resumable frame slots. `evaluator.rs` retains the fused
column scan and rule loop, including its yields before external requests.
`ffi.rs` owns the existing surface exports, including the compatibility adapter
for biome column selection. Java still owns ordered block commits and external
conditions/rules.

### Java integration

`NativeDensityProgram`, `NativeDensityMath`, and `NativeUnaryProgram` live in
`net.minecraft.world.level.levelgen`. Noise sampling/constructors remain in
`levelgen.synth`. Its sealed `NativeNoiseState` interface exposes only the
read-only segment, bounded-call eligibility, and octave count. It reuses the
existing state object and identity, with no new per-call allocation. Changed
amplitudes still publish a new snapshot; the old snapshot remains immutable.

## Verification

Focused Rust tests can run without compiling renderer/audio dependencies:

```sh
mkdir -p build/worldgen-organization
rustc --edition=2021 --test src/test/rust/worldgen.rs \
  -o build/worldgen-organization/world-tests
build/worldgen-organization/world-tests
./gradlew -PmattmcRustProfile=release test \
  --tests 'net.minecraft.world.level.levelgen.*' \
  --tests net.minecraft.util.RustNativePanamaBoundaryTest -x testRustNative
```

`VerifyRustWorld.py` and `VerifyRustSurface.py` retain original-Java parity
comparisons. `VerifyRustWorldgenRefactor.py` compares frozen pre-refactor and
post-refactor release libraries **and Java classes**, including boundary costs:

```sh
# Before editing, after a release testClasses build and classpath preparation:
python3 DevUtils/tests/worldgen/VerifyRustWorldgenRefactor.py --capture-baseline \
  --classpath build/rust-surface-verification/classpath.txt
# After rebuilding the candidate:
python3 DevUtils/tests/worldgen/VerifyRustWorldgenRefactor.py --forks 3 \
  --classpath build/rust-surface-verification/classpath.txt
```

Each implementation runs in independent warmed JVMs pinned to the same CPU;
pair order alternates. All timing samples and JIT counters are retained.
The report gives paired ratios and hierarchical bootstrap intervals. An
interval whose lower endpoint exceeds 1 reports a detected regression; an
interval overlapping 1 does not prove absolute equality. Its upper endpoint
states how much slowdown the measurements can still admit.

Full-generation checks use `VerifyRustSurfaceWorld.py` with a frozen classpath
and `--native-dir`. Fixed request order is used for exact FULL parity because
existing feature scheduling can otherwise change results. Performance runs use
normal parallel generation without hash instrumentation.

## Refactor correctness evidence

The release build passes 42 selected Java tests, including the new read-only
noise-state bridge tests, and 15 focused Rust tests (12 generation tests and
three existing colormap tests). The native symbol audit preserves all 259
`mattmc_*` exports.

Direct pre/post native comparisons pass 1,686,514 checks across six noise
families, scalar/batch routes, derivatives, End heights, and density arithmetic.
Finite values and signed zero match bitwise; NaNs are compared canonically.

The retained original-Java drivers also match:

- 40 terrain fingerprints: eight settings, five seeds, six positions each;
  240 base chunks and 17,203,200 blocks, plus router/climate/aquifer observations.
- 3,280 surface fixture chunks, including custom rules and boundary inputs.
- Separately, frozen pre/post full-world runs match 200 completed chunks,
  648 surface checkpoints, and 648 carving checkpoints across two seeds and
  all four dimensions. Every server exits normally.

Artifacts live under `build/worldgen-organization/`: `native-differential.json`,
`abi-symbols.json`, `original-java-noise/report.json`,
`original-java-surface/results.json`, and `full/parity-*-candidate/comparison.json`.
These are finite tested corpora on Linux x64, not a proof over all possible
seeds, extensions, or hardware.

## Refactor performance results

These measurements compare the frozen native implementation **before this
organization change** with the reorganized implementation. They do not reuse
the earlier original-Java-versus-Rust speedup figures.

Environment: Ryzen 5 5600G, Linux x64, OpenJDK 25.0.4.1, Rust 1.93.1, release
native library. Both versions use the same drivers and JVM options, including
ZGC, a 1–4 GiB heap, and compact object headers. Each comparison has three
independent JVM pairs with alternating execution order. Stage benchmarks use
`-Xbatch` and CPU 2. Java-to-native calls are included in the timers.

**No statistically detectable regression was found in the 20 workloads.**
This is not an equivalence proof: particularly for parallel full generation,
the intervals still admit small slowdowns. Positive changes mean longer elapsed
time; negative changes mean faster execution. Intervals are 95% hierarchical
bootstrap intervals from 10,000 resamples of forks and measured rounds.

### Terrain generation

The existing adaptive warmup requires at least ten seconds, ten rounds without
JIT activity, and a recent timing spread below 8%. Each fork then measures 11
rounds of at least one second each; significant compilation restarts the entire
measurement window. Setup and generation boundary costs remain included.

| Setting | Before (ms/chunk) | After (ms/chunk) | Paired elapsed change | 95% interval |
| --- | ---: | ---: | ---: | --- |
| amplified | 9.301 | 9.325 | +0.23% | -1.01% to +1.48% |
| caves | 1.384 | 1.395 | +0.44% | -0.42% to +5.44% |
| end | 2.004 | 2.005 | +0.06% | -1.26% to +0.99% |
| floating_islands | 1.695 | 1.692 | -0.20% | -9.14% to +0.85% |
| large_biomes | 10.531 | 10.466 | -0.61% | -1.17% to +0.01% |
| nether | 0.918 | 0.913 | -0.50% | -3.02% to +1.96% |
| overworld | 9.532 | 9.627 | +0.73% | -0.20% to +3.33% |
| primordial_caves | 3.054 | 3.048 | +0.68% | -3.46% to +1.25% |

Before/after columns are medians of each implementation's fork medians. The
change is the median of the three paired ratios, which can differ from dividing
the two displayed columns (including the sign in the Primordial terrain row).
All individual fork ratios and samples are retained.

### Surface evaluation

Each fork uses 60 warmup rounds and 24 measured rounds. One timed round processes
24 fixture chunks. Fixture construction is outside the timer; production
preparation, native boundary calls, rule evaluation, and block writes are inside.
The measured windows recorded 31 ms of total JIT activity before and 33 ms after
across all eight settings and three forks. No samples were discarded.

| Setting | Before (ms/24 chunks) | After (ms/24 chunks) | Paired elapsed change | 95% interval |
| --- | ---: | ---: | ---: | --- |
| amplified | 178.477 | 174.630 | -2.32% | -2.92% to -1.82% |
| caves | 114.284 | 112.018 | -1.51% | -2.28% to -1.04% |
| end | 34.245 | 34.441 | +0.72% | -0.62% to +1.09% |
| floating_islands | 113.579 | 110.677 | -2.98% | -3.22% to -1.86% |
| large_biomes | 146.521 | 145.005 | -1.94% | -4.73% to -0.12% |
| nether | 73.381 | 72.247 | -1.95% | -5.65% to -0.21% |
| overworld | 157.051 | 154.576 | -1.83% | -2.51% to -0.89% |
| primordial_caves | 181.389 | 180.230 | -0.64% | -2.41% to +2.75% |

An initial complete comparison used the driver's original 12 warmups and 12
measurements. Late compilation and fork variability prompted the longer warmup
for **all** settings and both implementations. That initial run remains in
`results.json`; the table above uses `surface-stable/results.json` throughout.
Six settings show a measured improvement; End and Primordial remain statistically
unresolved.

### Full parallel world generation

Three fork pairs use seeds 42, -123456789, and 42; CPUs 2–5 and three workers.
Each dimension has three warmup regions of radius four followed by three
measured regions of radius five per fork. Each implementation requests 1,089
measured FULL chunks per dimension, plus the prerequisites needed to finish
them. These runs use normal parallel scheduling without hash instrumentation.
JIT, GC, and boundary overhead are included; save/drain time is excluded.

| Dimension | Before (total s) | After (total s) | Elapsed change | 95% interval |
| --- | ---: | ---: | ---: | --- |
| Overworld | 44.677 | 44.454 | -0.50% | -5.61% to +5.83% |
| Primordial | 45.277 | 45.537 | +0.57% | -2.36% to +3.85% |
| End | 3.967 | 4.021 | +1.35% | -6.30% to +8.04% |
| Nether | 15.276 | 15.476 | +1.31% | -4.70% to +8.77% |

Here the ratio uses summed elapsed time across all nine measured regions per
dimension, with paired resampling of forks and regions. The first pair's slower
candidate timings did not repeat in the third pair, where all dimensions were
faster. All six timing servers and four parity servers exited with code zero.

### Retained artifacts

Under `build/worldgen-organization/`:

- `baseline/` and `candidate/`: frozen release libraries and Java classes.
- `results.json`: terrain and initial surface samples, ratios, and intervals.
- `surface-stable/results.json`: longer-warmup surface samples and intervals.
- `full/timing-*/`: individual region timings and server process outcomes.
- `verified-performance.json`: combined final performance summary.
- `final-artifact-check.json`: final rebuilt library is byte-for-byte identical
  to the measured candidate after two frame-documentation comment corrections.
- `final-rebuild.log` and `rust-tests-final.log`: final successful release build,
  42 selected Java tests, and 15 focused Rust tests.
- `*-command.json` and logs: exact commands, warmup output, and JIT counters.

The comparison tool records library and class hashes and rejects a stale
candidate snapshot. Reproduction of the longer surface comparison uses
`--domain surface --surface-warmups 60 --surface-rounds 24 --forks 3` with
`--benchmark-only` and the frozen baseline/candidate directories.

Final measured/rebuilt library SHA-256:
`31f5882c67464d6c15546573a3e7240b8ad77126014efcfa725381223f501356`.
No build dependencies or system configuration were changed for this refactor.
