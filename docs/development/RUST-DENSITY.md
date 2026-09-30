# Native density evaluation

## Production ownership

All migrated density operations have one production implementation, in Rust:

- Noise coordinate scaling; Shift, ShiftA and ShiftB scaling.
- Shifted-noise coordinate combination and both rarity mappings.
- Add, multiply, min/max, constant arithmetic, mapped transforms and clamp.
- Range-choice predicates and arithmetic short-circuit predicates.
- End island-height search (in `world/noise.rs`).

`world/density.rs` shares these routines between fused expression programs and
individual/batched operations called through `NativeDensityMath`. There is no
Java arithmetic fallback for these operations. Loading the native library is
mandatory.

Java still owns graph construction, metadata bounds, serialization, seed/state
construction, context traversal, caches and interpolation. Operations that were
never migrated, such as spline evaluation, blending and Y-clamped gradients,
retain their existing Java implementations. Extension-owned functions likewise
retain their own behavior; this cutover does not port arbitrary plugin code.

## Traversal and batching

`DensityBatch` records raw coordinates and input values in their original Java
visitation order. Rust performs coordinate scaling, shifting, rarity mapping,
noise evaluation and result scaling. Native batches are bounded to 256 points
and reuse the existing SIMD noise kernels. Small or unsupported batch contexts
use scalar Rust operations through the same Java traversal contract.

Arithmetic arrays also execute in Rust. Terrain providers and known,
non-reentrant children batch branch decisions and result arithmetic, preserving
the original `forIndex` sequence. Per-thread primitive scratch buffers avoid
repeated allocation; buffers larger than 4096 points are not retained. Unknown
contexts or extension children preserve incremental per-element result writes.
Java obtains each necessary child value; Rust chooses the short-circuit predicate
and computes the result. This preserves cache counters and custom-context behavior.

`NativeDensity` fuses supported pure expressions into one native call. Its
unfused `source.compute` and `source.fillArray` routes remain as traversal
adapters: the migrated source-node operations now delegate to Rust as well.
They no longer represent a second Java implementation of the arithmetic.

## Private evaluation plans

Cache nodes retain their original `wrapped()` graphs. Their private `evaluator`
fields use optimized plans. This distinction matters: rewriting the canonical
graph changed structural deduplication in an early experiment and created extra
interpolators. The current Overworld graph retains the original eight interpolators.

- `NativeUnary` combines up to 16 consecutive transforms. Constant min/max steps
  preserve the original array traversal through the source adapter; the scalar
  transform pipeline remains native.
- `NativeOperands` combines arithmetic after Java visits up to four mandatory
  inputs in their original order. Conditional nonconstant children remain opaque;
  neither skipped children nor reentrant input calls are speculatively evaluated.
- `DensityBatch` groups selected noise leaves in range-choice arrays and batches
  BlendedNoise terrain samples. It records coordinates in their original accessor
  and visitation order. BlendedNoise uses four independent AVX2 lanes when bounds
  permit, with per-lane octave order and branch masks preserved; tails and other
  inputs use scalar Rust.
- `NativeCellDensity` captures up to eight interpolators' corner values once per
  cell epoch. Rust evaluates the cell expression in bounded array windows. This
  path requires the exact owning `NoiseChunk`, an active interpolation loop and
  cell filling. Other contexts use the original traversal adapter.

Cell filling preserves the original **X, Y, Z** interpolation order. Normal block
interpolation retains the existing Java **Y, X, Z** staging; substituting one for
the other changes rounding. The native cell evaluator also reproduces the final
provider coordinates and array index, including skipped tails and direct fills'
postincremented index. It does not flatten other cache types or call back into Java.

Direct Rust kernels handle the common terrain/noodle expression and the
single-interpolator postprocessing used by simpler settings, after exact
structural matches. Constants and bounds remain program parameters. Other
expressions use the general Rust evaluator. The direct kernels reuse identical
X/Y interpolation intermediates along each Z row and precompute coordinate
fractions using the original divisions. The single-interpolator kernel evaluates
four Z samples together on AVX2 hardware, retaining the same per-lane arithmetic
and ordered comparisons for NaNs and signed zero. Other hardware and row tails
use scalar Rust. Neither route performs algebraic reassociation.
The empty structure marker and the immutable `Beardifier.EMPTY` instance are
recognized as zero inputs; their final addition is still performed, including
its signed-zero behavior. Nonempty structure adjustments retain their existing
traversal outside the native cell expression.

## Ownership and native limits

Compiled programs are shared by `RandomState` using immutable arithmetic keys;
these keys retain no chunk, cache, context or mutable operand objects. Cell corner
snapshots belong to their individual chunk evaluator. Serialization uses the
original graph representation.

A density program is one immutable, GC-owned allocation with a 16-byte header,
up to 48 nodes of 64 bytes each, and validated copies of any noise states. Child
indexes point backwards and noise references are checked offsets in that same
allocation. Pure-noise graphs are bounded to 512 expanded node visits and 128
octave evaluations. Operand and cell programs cannot contain noise-state nodes.
Cell graphs must be trees so traversal phases have an unambiguous ordering.
Unary programs have a separate bounded layout (8-byte header, 24 bytes per step).

Array calls contain at most 256 points. Heap array pointers are borrowed only for
the foreign call and never retained by Rust; immutable program segments stay live
through the call. Native validation precedes execution. Unknown contexts and
unsupported graphs keep the Java traversal contract while migrated operations
still execute in Rust.

Rust preserves Java NaN and signed-zero rules, branch thresholds, short circuits
and floating-point operation order. No fast-math, FMA or reassociation is enabled.
The End port also preserves Java wrapping integers and float intermediates.

## Cutover validation

The mandatory-native operation cutover matched the saved original-Java results:

- 1,528,325 density/End values, including batch tails and exceptional inputs.
- 3,289,014 core noise comparisons.
- 295,488 additional BlendedNoise batch comparisons against the original Java
  evaluator, across both random-source families and varied seeds/parameters.
- 40,000 concurrent fused-expression comparisons and context/serialization checks.
- 40 terrain fingerprints: eight settings, five seeds and six chunk positions,
  totaling 240 chunks and 17,203,200 base blocks, plus raw router densities,
  cached climate samples and aquifer decisions.

Finite floating-point values compare bit-for-bit; NaN payloads are canonicalized.
Terrain coverage is the NOISE stage, including interpolation and ore decisions.
It does not claim full surface/carver/decoration or rendered-game coverage, nor
universal correctness for every possible input or CPU.

The production release build passes 34 focused Java tests; nine focused Rust
unit tests also pass. These cover the new plans, provider state,
exceptional floating-point values, invalid programs, batch boundaries, reentrancy
and frame lifetime. The cell arithmetic tests include randomly generated trees
and separate Java formulas for both specialized expressions, with 865,950
comparisons per expression across varied dimensions, parameters, frame slots,
batch tails and optional trailing addition.
An additional test exercises SIMD row boundaries through width 64, including
signed zero, infinities, NaNs and subnormal values.

The copied Java implementations and old migration-only Gradle tasks remain
removed.

`DevUtils/VerifyRustWorld.py` is the retained, opt-in end-to-end comparison. It
extracts the original Java implementation from commit
`ee1692c10cf96a806c22c7e195bad01abb588a2e` into ignored build output and runs the
same `NativeWorldVerification` driver against reference and production classes.
No copied Java sampling algorithm is added to production or retained test source.

```sh
# Exact terrain fingerprints only:
python3 DevUtils/VerifyRustWorld.py --parity-only

# Full parity and three independent JVM forks per implementation, all settings:
python3 DevUtils/VerifyRustWorld.py --forks 3 --cpu 2

# Narrow a performance investigation to one setting:
python3 DevUtils/VerifyRustWorld.py --world overworld --forks 3 --cpu 2
```

The script builds release natives by default; `--skip-build` requires an existing
production release library. Generated references, full logs, native binary hash
and the machine-readable report live under `build/rust-world-verification`.
It never checks out source, creates commits or changes production routing.

## Performance scope

Acceptance measurements include chunk construction, the Java/Rust boundary,
interpolation, density evaluation, aquifers, ore decisions and base-block selection.
They use seed 42 and five chunk positions, a pinned CPU, identical heap settings,
`-Xbatch`, at least ten seconds of warmup and eleven measurement rounds per setting.
Warmup must also stabilize and observe ten consecutive rounds without JIT activity.
Significant compilation invalidates an entire measurement window for either
implementation; those windows remain in the log.

The report subtracts JIT time (plus counter rounding allowance) from the Java
baseline only, and bootstraps JVM forks and their rounds. The 10% gate requires
**every setting's** upper 95% time-ratio bound and every fork ratio to be at most
0.90. A failing setting makes the command fail; aggregate gains cannot hide it.
This is a measured workload contract, not a guarantee for every seed, custom data
pack, individual scalar call or CPU.

Historical cutover measurement: original Java 10.733 ms/chunk versus mandatory
Rust 12.252 ms/chunk (14.15% more time). That measurement preceded the private
plans and cell kernel and is not a current performance result.

The first full matrix (`build/rust-world-acceptance`) failed six settings. A
subsequent candidate (`build/rust-world-final`) passed six, but missed the gate
for Nether (9.01% less time) and Primordial Caves (4.78% less time). Those logs
remain available. The single-interpolator SIMD kernel was added to address
those misses. The final production run below passed the full gate.

### Final production results — 2026-09-30

**PASS: all eight settings**, all three independent JVM pairs, and every upper
95% bootstrap time-ratio bound meet the 0.90 requirement. Boundary overhead is
included. This run uses the full Cargo release library, not a development probe.

Machine: AMD Ryzen 5 5600G, pinned CPU 2, Ubuntu OpenJDK 25.0.4.1,
Rust 1.93.1. Both modes use `-Xbatch -Xms512m -Xmx3g` and the same driver.

| Setting | Java median ms/chunk | Rust median ms/chunk | Paired time saved | 95% lower bound on time saved |
| --- | ---: | ---: | ---: | ---: |
| Amplified | 10.453 | 8.708 | 16.47% | 16.23% |
| Caves | 1.547 | 1.286 | 17.34% | 16.69% |
| End | 2.353 | 1.950 | 17.30% | 13.01% |
| Floating Islands | 1.903 | 1.594 | 16.16% | 13.64% |
| Large Biomes | 11.592 | 9.890 | 14.09% | 13.43% |
| Nether | 1.013 | 0.848 | 16.46% | 15.79% |
| Overworld | 10.712 | 8.904 | 16.37% | 16.14% |
| Primordial Caves | 3.254 | 2.853 | 12.15% | 11.34% |

The millisecond columns are medians of the three JVM medians. The percentages
are medians of paired Rust / compilation-adjusted Java ratios, so they need not
be exactly the quotient of the displayed millisecond columns. All eleven valid
rounds per setting and JVM remain in the report; no timing-based retries or
individual-round exclusions were used.

The slowest individual pair still used **11.44% less time** than Java. The most
conservative upper confidence bound corresponds to **11.34% less time**. Median
paired reductions range from **12.15% to 17.34%**. This satisfies the requested
10% measured target; the preferred 15% reduction was not reached in every setting.

Evidence is in ignored build output:

- `build/rust-world-simd-final/report.json`: raw rounds, JIT counters, all three
  pair ratios, confidence bounds, original-Java fingerprints and final gate.
- `build/rust-world-simd-final/evidence.json`: tested source/binary hashes,
  focused test results, independent parity counts and process cleanup check.
- `build/rust-world-simd-final/fork-*.log`: complete benchmark output, including
  whole windows invalidated by further JVM compilation.

Production native SHA-256:
`bcd03442b66f5f21aa10e4bfe00a10639e479503bff7fd187976f9afa941f3ec`.

All 32 frozen source/test/driver/binary hashes matched after measurement. The
comparison process exited successfully and no benchmark JVMs remained. No commits
or pushes were made. These measurements cover the NOISE-stage workload described
above, not cold startup, full chunk surface/carver/decoration stages, every data
pack, or every CPU. The parity corpus found no mismatches; it is not a formal
proof over every possible input.
