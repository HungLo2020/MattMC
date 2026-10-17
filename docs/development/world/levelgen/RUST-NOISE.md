# Native noise evaluation

## Production ownership

Rust is the sole production evaluator for Improved, Perlin, Normal, Blended,
Simplex (2D/3D), and Perlin-Simplex noise, including ImprovedNoise derivatives.
The Java sampling methods delegate through `NativeNoise`. They contain no
alternate Java sampling algorithm and cannot switch to one when native loading
fails. The native library is required.

Rust also constructs the Improved and Simplex leaf offsets and permutations.
A native invocation requests the original three `RandomSource.nextDouble`
calls and descending 256 bounded integer draws. Java performs those genuine
VM effects on the actual source, preserving custom dispatch, reentry and
exception prefixes. Rust owns the shuffle and compact immutable leaf state;
Java retains the public offset fields and an arena-managed native owner.

The subsequent [Perlin graph/factory increment](RUST-PERLIN.md) moves its
modern/legacy decisions, frequency factors, bounds and ordered amplitude readers
into Rust. Its verification is separate from the published leaf evidence below.
The [Normal producer and paired readers](RUST-NORMAL.md) are the next connected
implementation under focused verification. Blended and Perlin-Simplex
construction, random-source state/consumption algorithms, codecs and public
integration remain Java-owned. Neither increment completes
noise construction or world generation. The unused Java Simplex gradient
table and dot-product helper have been removed.

`world/level/levelgen/synth/` contains separate Improved, Perlin, Normal,
Blended, Simplex and Perlin-Simplex modules. Family-specific optimized kernels
live below their family module. `dispatch.rs` owns CPU/coordinate guards and
bounded batches; `ffi.rs` preserves the exported symbols. Scalar Rust handles
other hardware and unsupported inputs. See [module organization](RUST-WORLDGEN-ORGANIZATION.md).
No reassociation, fast-math, or fused multiply-add is used to change the original
floating-point evaluation order.

## State and boundary

Each completed native leaf contains three offsets and a 256-byte permutation.
Its builder retains no borrowed Java pointer across RNG callbacks and is
released in `finally`; a handle-only idempotent automatic-arena cleanup owns
the transferred leaf. The original 256-byte Improved and 512-element Simplex
scratch bounds remain observable for invalid custom RNG results.

The evaluation snapshot remains lazy and arena-owned, with its existing
80-byte header and 1320 bytes per octave. Rust packs leaf state directly into
that ABI; no Java permutation or packing algorithm remains. Unusual offsets
are accepted during construction and validated at the original lazy snapshot
point. Public debug formatting copies a diagnostic permutation on demand.
Native sampling has no allocation registry, retained Java-array pointers, or
Java callbacks.

Normal/Perlin amplitude snapshots are checked and rebuilt for supported
sequential parameter changes. State remains live for the duration of each call.
Concurrent mutation of caller-owned configuration is not a new supported contract.

Batch calls process at most 256 points. Critical calls are restricted to bounded
noise configurations; larger custom octave configurations use ordinary foreign
calls and off-heap staging buffers. This is a native execution choice, not a
fallback to a Java algorithm.

End island-height evaluation also runs in Rust, preserving the original integer
overflow, float intermediates, threshold conversion and operation order. Java
retains RNG construction and the final density scaling.

## Build

```sh
./gradlew -PmattmcRustProfile=release classes
python3 DevUtils/RunDev.py
```

`RunDev.py` selects the release Rust profile. Direct Gradle invocations otherwise
default to an unoptimized Rust development profile; prior performance results
apply to release builds.

## Leaf construction checks

With a coherent release build, run the callback and ownership checks:

```sh
./gradlew -PmattmcRustProfile=release test \
  --tests '*NoiseConstructorContractTest' --tests '*NativeNoiseSeedTest'
python3 DevUtils/tests/worldgen/noise_construction/VerifyConstructors.py \
  --pre-runtime build/worldgen/noise-construction/pre-929-runtime \
  --output build/worldgen/noise-construction/portable-review
```

The portable driver requires complete immutable reference runtimes and the
classpath setup from the [chunk-generation guide](../chunk-loading/RUST-CHUNK-GENERATION.md).
It compares actual constructor effects, failures, reentry, array bounds, public
metadata, debug permutation text, samples, derivatives and the next RNG word.
Native-owner tests check real builder failure retirement and leaf collection
while an independently copied evaluator remains usable. Intermediate leaf
verification is in progress; earlier sampling results below are historical
and do not validate this construction change. Add `--screen-only` for the
bounded constructor screen on CPU4; it requires separate portable contract
receipts. All runs retain source/class/native origins and immutable-runtime guards.

## Validation and removed migration scaffolding

The native implementation has passed repeated runs of 3,289,014 comparisons
against the original Java noise algorithms, including the production build after
the boundary optimizations. The mandatory
native density cutover was also checked against the previously recorded density
and terrain fingerprints; see `RUST-DENSITY.md`. Finite values compare bitwise;
NaN payloads are canonicalized. The new four-sample BlendedNoise batch kernel
also passes 295,488 independent comparisons against original Java across both
random-source families, 32 seeds per family, nine parameter configurations and
513 points per configuration. Unsupported parameter/coordinate ranges retain
the scalar Rust route.

The six copied Java noise implementations, migration parity/benchmark drivers,
`DevUtils/PerfAudit/Noise.py`, and their Gradle tasks have been removed from the
source tree. The benchmark-only Rust export and Java binding were removed too.
Focused Rust unit checks remain alongside the production modules. Java tests
cover the density plans and boundary contracts. The opt-in
`DevUtils/tests/worldgen/VerifyRustWorld.py` extracts reference classes into build output and
compares original-Java and native terrain generation; see `RUST-DENSITY.md`.

Local audit evidence is retained under the ignored `build/noise-validation`,
`build/density-validation`, and `build/cutover-validation` directories. Temporary
reference drivers in build output are not production or committed source.
The original Java source also remains recoverable from Git history at
`ee1692c10cf96a806c22c7e195bad01abb588a2e`.

Earlier performance reports describe the preceding implementation. They are
historical evidence and do not certify the timing of later boundary changes.


## Current production performance

The speedup measurements below predate the structural module reorganization.
Its before/after checks are documented in [module organization](RUST-WORLDGEN-ORGANIZATION.md).

The final boundary-inclusive NOISE-stage comparison passed the 10% gate in all
eight settings and all three JVM pairs. Median paired time reductions are
12.15–17.34%; the worst upper confidence bound still clears 10%. See
[RUST-DENSITY.md](RUST-DENSITY.md#final-production-results--2026-09-30) for the
per-setting table, original-Java reference, hardware, scope and retained evidence.
The measured implementation keeps all migrated sampling and arithmetic in Rust.

## Intermediate leaf construction screen — October 16, 2026

The coherent release candidate on `929fb693…` uses native `31a21620…`,
JDK25 and Rust/Cargo1.94.0 on HungLoDE. It passed75 focused integration
checks, ten portable contracts per original Frozen/sealed published929/Current,
and the same27,840 metadata, debug, sample, derivative and next-RNG-word
observations (SHA `805da08e…`). Five actual Rust owner/construction checks
and three Java owner checks passed; four Rust and two Java omission controls
failed their intended assertions. The final checkpoint verification is below.

A first512-call screen was explicitly inconclusive because of variable timings;
its raw data remains under `portable-second/`. A bounded4096-call repeat used
CPU4, the same observer,24 warm batches and six JIT/GC-quiet accepted samples
per row. Extra warm/retry work remains recorded, rather than assumed equal.
The table gives median ns per public constructor, including its actual RNG
draws and next word. Each row's metadata/RNG result matched every runtime.

| Constructor / RNG | Frozen ns | Pre-change ns | Current ns | Current/pre |
| --- | ---: | ---: | ---: | ---: |
| Improved / Legacy | 2141 | 2094 | 3425 | 1.636× |
| Improved / Xoroshiro | 380 | 538 | 2813 | 5.232× |
| Simplex / Legacy | 2213 | 2204 | 3642 | 1.652× |
| Simplex / Xoroshiro | 434 | 432 | 2973 | 6.879× |

Timing spread remains explicit in `screen-pinned/screen-assessment.json`.
Every Current accepted sample was slower than every Frozen sample in its
corresponding row. Median Current/Frozen ratios are1.600×,7.398×,1.646×
and6.850×; they are intermediate cost evidence, not precise final ratios or
budget passes. The ordered VM RNG effects add temporary boundary work.
The ownership exit is native random-source state/producers and composite
consumers, while preserving actual custom VM dispatch. S33 owns that work.

Java allocation per constructor was512B Current: pre/Frozen Improved320/312B,
Simplex2112/2104B. Native allocation is not attributed by these values. All
actual native builders and leaf owners retired after each row. Sampled process
peak RSS was85.77/85.34/84.98MB for Frozen/pre/Current, all with zero swap;
these whole-process values are not causal allocator measurements. No unbounded
retention was observed in this finite work. Whole noise/world-generation
acceptance remains incomplete.

Receipts: `build/worldgen/noise-construction/{second-focused,portable-second,screen-pinned,native-controls-second,java-controls}/`.
The earlier Java-only pre-constructor mapping requirement was corrected in the
observer; its failed receipt is retained under `portable-first/`. An additional
null-source check reproduced and corrected the helpful NPE parameter name;
all three runtime contracts now preserve it.

## Leaf checkpoint verification — published `c5a9de42…`

The same `929fb693…` plus leaf candidate, release `31a21620…`, passed
2,586 ordinary Java tests with six expected skips,193 parity tests,11 slow
tests, and3,113 Rust tests with nine ignored: zero failures. Both Gradle runs
completed successfully. An additional seven actual composite contracts and
50,368 composite metadata/sample/mutable-amplitude/next-RNG observations
on this leaf candidate matched both immutable references
(SHA `1d6b875c…`); composite production code was unchanged.

Actual servers generated648 FULL chunks per runtime in each of the straight
and CARVERS-save/unload/reload-resume phases across four dimensions. Current
matched sealed pre-change929 throughout. The three standard dimensions matched
original Frozen; the existing162 Primordial differences in each phase remain
recorded. All servers saved and exited successfully. Injected failure rollback,
cancellation, stock stage/binding fresh/reopen, and player shutdown with
pending generation followed by each runtime's own save reopen also passed.
Loaded native mappings and source/class/native/immutable-reference guards
stayed intact. These are the checked fixtures, not a general lighting,
gameplay/FPS or final performance acceptance claim. Whole world generation,
composite construction and native random-source ownership remain in progress.

Receipts: `build/worldgen/noise-construction/{stable-tiers,stable-worlds,full-straight,full-resume,failure-cancel,stock-binding,player-shutdown}/` and
`build/worldgen/noise-composite/current-leaf-integration/`.
