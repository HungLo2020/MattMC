# Rust height-blending grids

`NoiseChunk` initializes its alpha/offset quart grid through
[`Blender.fillBlendingOutputs`](https://github.com/HungLo2020/MattMC/blob/master/src/main/java/net/minecraft/world/level/levelgen/blending/Blender.java).
Eligible grids collect the current old-terrain height samples once and evaluate
all positions with one ordinary Rust call. This applies where newly generated
terrain meets old chunks. Worlds with no blending data retain their existing
empty path. Density blending, biome selection and scalar blending stay in Java.

The numeric evaluator lives in
[`world/level/levelgen/blending/`](https://github.com/HungLo2020/MattMC/tree/master/src/main/rust/world/level/levelgen/blending).
[`NativeBlending`](https://github.com/HungLo2020/MattMC/blob/master/src/main/java/net/minecraft/world/level/levelgen/blending/NativeBlending.java)
owns sample collection, buffer copies and the boundary. It caches numeric
scratch only; terrain samples and outputs are refreshed on every grid fill.

## Preserve compatibility

- Keep the original fastutil `forEach` sample order and direct-height lookup
  precedence. Weighted sums are sequential in that order; never reorder them.
- Distance uses wrapped Java int subtraction, float conversion, double
  squares/square root, **float rounding**, then double promotion. Preserve the
  left-associated four-factor product in the inverse fourth-power weight, the
  27-quart cutoff, smoothing expression and double positive modulo.
- Native grids require the exact builtin Blender, map and BlendingData types,
  5–17 positions per axis, at least 16 unresolved positions and 32 height samples.
  Each data array must have the original 16-entry layout. Limit maps to 256
  chunks/4096 samples. Custom, smaller and unsupported inputs use original
  scalar calls with their callback order, exceptions and partial writes.
- Check custom types/layouts before moving any virtual reads. Nonfinite heights
  or arithmetic results select Java compatibility; publish no partial native
  grid. Output arrays retain identity and untouched trailing entries.
- Capture samples afresh: `BlendingData.unpack` can retain externally owned
  arrays, and blending data can be populated after construction. Inputs must be
  stable during a call, as required by the unsynchronized original.
- `NoiseChunk` keeps its original empty-blender branch, alpha/offset array layout
  and later caches. Each result is stored at `x + z * size`.
- Ordinary FFM permits GC. Rust allocates nothing, calls no Java code and retains
  no pointers. Buffers must be valid, aligned and disjoint for the call. Metadata
  validation precedes writes; thread-local scratch is guarded and released in
  `finally`. Maximum scratch is about 148 KiB per participating thread, split
  between heap and automatic native arenas.

## Verify this slice

```sh
python3 DevUtils/tests/worldgen/VerifyRustBlending.py --parity-only
python3 DevUtils/tests/worldgen/VerifyRustBlending.py --forks 3
```

The Linux driver audits literal original Blender and NoiseChunk grid-loop
oracles against Git `7af3a1594956f41ed57c3bf67d11ce006e61530f`. It checks that
scalar blending/data behavior is unchanged and only the constructor grid
boundary changed. It runs focused Java/Rust tests without graphics contexts.
Parity compares raw alpha/offset bits for current seeded maps, cancellation,
missing/direct values, ordering, negative/extreme coordinates, malformed data,
nonfinite fallback, custom callbacks, partial writes and concurrent GC.
Finite tests supplement preservation of the original arithmetic; they do not
enumerate every world seed.

Benchmarks compare the complete grid caller against the literal original loop,
including eligibility, direct lookups, fresh sample collection, all heap/native
copies, the ordinary call, array writes and output consumption. They use equal
current maps and queries; neither path caches results. Fixtures/startup/initial
scratch are excluded equally. Gains describe height-grid initialization at
old-terrain borders, not complete chunk generation or FPS.

Eight workloads include a chunk-aligned seam with only exposed edge heights,
small and sparse borders, mixed direct hits, large old regions, wider grids and samples outside the cutoff. Acceptance requires three
independent warmed JVM pairs per workload, matching checksums, no measured JIT
compiler time, at least 5% less time in each pair and an upper 95% bootstrap
ratio bound no greater than 0.95. Raw evidence belongs under
`build/blending-migration/`; use the results matching your source/native hashes.

## Recorded verification

Release acceptance passed on 2026-10-02 (Ryzen 5 5600G, Linux x86_64,
OpenJDK 25.0.4.1). Ten focused Java tests and four Rust tests passed. The counted
matrices plus a supplemental reused-owner/capacity check compared 3,467 grids,
131,219 positions and 262,438 raw double outputs with zero differences. Additional
checks cover custom exceptions, partial writes, malformed inputs and concurrent GC.
The supplementary check covers 0/1/2/255/256/257 chunks, grids through 17², and
externally mutated height arrays on the same Blender objects.

All eight workloads passed three fresh JVM pairs each (48 JVMs), stable warmup
and zero measured compiler time. The weakest pair saved **9.90%**; the
lowest conservative 95% saving bound was **9.85%**.

| Complete grid caller | Original Java | Rust + boundary |
|---|---:|---:|
| Exposed old-terrain seam, 25 positions | 52.49 µs | 8.02 µs |
| Small border, 25 positions | 7.55 µs | 5.27 µs |
| Dense large region, 25 positions | 159.39 µs | 143.12 µs |

`build/blending-migration/acceptance/REPORT.md` contains all timings, raw evidence
and verification limits. The seam reconstructs the original column-exposure
rules; denser maps are stress cases. These are warmed grid-caller measurements
on this machine; compatibility paths and complete chunk generation are outside
the speed claim.
