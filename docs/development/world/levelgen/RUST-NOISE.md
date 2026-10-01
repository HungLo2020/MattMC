# Native noise evaluation

## Production ownership

Rust is the sole production evaluator for Improved, Perlin, Normal, Blended,
Simplex (2D/3D), and Perlin-Simplex noise, including ImprovedNoise derivatives.
The Java sampling methods delegate through `NativeNoise`. They contain no
alternate Java sampling algorithm and cannot switch to one when native loading
fails. The native library is required.

Java retains the original constructors, random-source consumption, permutations,
octave parameters, metadata bounds, codecs, and public integration classes.
These preserve seed compatibility and the existing Java API. The unused Java
Simplex gradient table and dot-product helper have been removed.

`world/level/levelgen/synth/` contains separate Improved, Perlin, Normal,
Blended, Simplex and Perlin-Simplex modules. Family-specific optimized kernels
live below their family module. `dispatch.rs` owns CPU/coordinate guards and
bounded batches; `ffi.rs` preserves the exported symbols. Scalar Rust handles
other hardware and unsupported inputs. See [module organization](RUST-WORLDGEN-ORGANIZATION.md).
No reassociation, fast-math, or fused multiply-add is used to change the original
floating-point evaluation order.

## State and boundary

Native state is an immutable allocation owned by a Java automatic arena. Its ABI
has an 80-byte header and 1320 bytes per octave. It stores copies of Java-created
permutations and coefficients. Validation precedes evaluation; native sampling
has no allocation registry, retained Java-array pointers, or Java callbacks.

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
