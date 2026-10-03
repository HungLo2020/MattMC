# Rust bulk long-array conversion

Fixed and length-prefixed long arrays use the Rust kernel in
`network/codec/long_array/` for bulk conversion. Chunk palettes use this wire
format. Java owns arrays, Netty buffers, length prefixes and cursor updates.

## Constraints

- Preserve every bit: wire longs are big-endian. The scalar path converts each
  eight-byte word; AVX2 applies the same byte permutation to four words at once.
  Signed values and arbitrary packed bits follow the same rule.
- Production converts arrays of at least 128 longs in approved heap/direct
  buffer classes and approved views with a known owner. Validate the live owner
  region, including retained views after parent capacity changes.
- Keep buffers exclusively owned and alive through each call. Direct regions
  use their owner's current address; Java uses `reachabilityFence`. No pointer
  or query result is retained by Rust.
- Each critical FFM call handles at most 8,192 longs (64 KiB per region).
  Larger arrays use consecutive bounded calls. CPU dispatch is initialized
  before borrowing; the kernel allocates nothing and makes no callbacks.
- Preserve the literal Java loop for small arrays, custom buffers or parents,
  advanced access tracking, swapped/read-only buffers, growth and truncation.
  Those paths retain partial cursor/output changes and original exceptions.
- Simple leak wrappers may use Rust because they do not record individual
  accesses. Advanced wrappers retain every original access callback in Java.

See
[`NativeLongArray`](https://github.com/HungLo2020/MattMC/blob/master/src/main/java/net/minecraft/network/NativeLongArray.java)
for ownership/dispatch and the
[`Rust codec`](https://github.com/HungLo2020/MattMC/tree/master/src/main/rust/network/codec/long_array)
for conversion and ABI validation.

## Verify

```sh
python3 DevUtils/tests/network/VerifyRustLongArray.py --parity-only
python3 DevUtils/tests/network/VerifyRustLongArray.py --forks 3
```

The benchmark driver uses Linux affinity. Its default CPUs are 2 for the caller
and 3/4 for JVM workers; select available cores with `--cpu` and
`--background-cpus`. The driver builds the release native library by default.

The driver pins the original four fixed/prefixed Java methods to `8b9173b39`
and audits the entire production `FriendlyByteBuf` after removing its two
dispatch hooks. Focused tests compare bytes, decoded longs, cursor positions,
capacity, partial failures, custom/leak callbacks, current inputs and GC.
Rust tests independently compare scalar/SIMD conversion, unaligned byte regions,
tail lengths, ABI limits and rejected inputs without writes.

The benchmark times actual array callers, including buffer checks, segment
construction, all FFM transitions, conversion, chunking and cursor updates.
Normal palette sizes contain actual 4,096-cell `SimpleBitStorage` payloads.
Library/fixture setup is excluded equally. Allocation leak sampling is disabled
equally to give both JVMs the same concrete buffer types; tracking behavior is
tested separately. No input/result cache is used.

Acceptance requires three independent comparisons per workload, at least
15 seconds of stable JIT warmup, eleven measured rounds, zero measured
compilation, and at least 5% less time in every paired median and its upper
95% bootstrap ratio bound. Heap/GC flags are identical; two untimed collector
requests precede warmup in both modes. Natural GC counts are reported separately.
Raw evidence stays under `build/long-array-migration/`. `--pilot` is diagnostic.

## Status

Final-source checks passed: **11 Java tests and 3 Rust tests**; the main parity
matrix covers 585 fixtures and 2,648,070 longs, with additional ownership,
tracking, mutation and failure tests. All 18 workloads passed three paired
comparisons: the worst pair used **14.26% less time**, and the conservative
95% saving bound was **13.78%**, including boundary costs.

Measured on a Ryzen 5 5600G with OpenJDK 25.0.4.1 and Rust 1.93.1. Source/library
hashes and cross-JVM checksums matched. Final raw evidence is in
`build/long-array-migration/acceptance-final/`, with the detailed report in
`build/long-array-migration/REPORT.md`. These results describe the converted
subsystem routes on this system; whole-game and compatibility-route speed was
not measured. Pilot and interrupted runs are excluded from acceptance.
