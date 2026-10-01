# Rust palette histograms

The multi-entry branch of `PalettedContainer.count()` uses Rust for standard
4096-entry `SimpleBitStorage` and `ZeroBitStorage`, with source widths 0–16.
This accelerates section block/fluid counter reconstruction and other count
consumers. The existing single-entry shortcut, small containers, custom storage
and wider formats keep their existing path.

The kernel lives in
[`palette/histogram/`](https://github.com/HungLo2020/MattMC/tree/master/src/main/rust/world/level/chunk/palette/histogram).
[`NativePaletteHistogram`](https://github.com/HungLo2020/MattMC/blob/master/src/main/java/net/minecraft/world/level/chunk/NativePaletteHistogram.java)
owns transfers and scratch lifetime. Java still resolves palette objects and
invokes consumers; `LevelChunkSection` counter/update code is unchanged.

## Constraints when changing this code

- Preserve **ordered (palette ID, frequency) records**, not merely totals.
  The original fastutil 8.5.12 map visits zero first, then occupied buckets
  descending. Reproduce its initial capacity, hash mixing, linear probing and
  every resize, including zero in the resize threshold. Review this contract
  before updating fastutil; the verifier pins its JAR digest.
- Count IDs separately even if two IDs refer to the same object. Nulls and
  custom palette/registry callbacks retain their original behavior.
- Decode exactly 4096 fields; ignore complete-word and final-word padding.
  The uniform-word shortcut must mask that padding and reread current input.
  Never cache histogram results.
- Deliver callbacks in Java using the current container palette **for each
  callback**. Consumers may mutate the container or throw; preserve partial
  callback traces and exception behavior. Reentrant scans use the original
  path until the outer scratch lease closes in `finally`.
- Keep the original ownership rules: counting adds no lock. Concurrent unchecked
  mutation is not made safe by the native boundary. Independent readers have
  separate scratch; ordinary downcalls allow GC during native scans.
- Rust allocates nothing and retains no pointers. The caller owns disjoint
  input, workspace and output buffers. The dense workspace starts zero and
  every touched count is reset before returning, before any Java callback.
- Preserve existing section counter semantics. Recounting adds both a nonair
  block contribution and a nonempty fluid contribution to the nonempty count;
  its fluid tick condition differs from incremental `setBlockState()`.
  Do not change those rules while migrating the histogram.

## Verify a change

```sh
python3 DevUtils/tests/chunk/VerifyRustPaletteHistogram.py --parity-only
python3 DevUtils/tests/chunk/VerifyRustPaletteHistogram.py --forks 3
```

The Linux driver uses `taskset`/`lscpu` and builds the release library by default.
Use `--cpu N` to select an available logical CPU. It pins the original count,
recount and mutation bodies to Git `fffe4a073`, allowing only the known packing
and histogram dispatch insertions in the container. It also runs the directly
affected palette-packing parity tests. No server or renderer is required.

Tests compare ordered raw ID/count records against actual fastutil and compare
all callback identities/frequencies against the original Java method. Coverage
includes all registered states, palette/resize boundaries, zero-key insertion
positions, aliases/nulls, every supported width, padding, saved sections,
all three exact section counters, incremental updates, callback exceptions,
reentrancy/mutation, compatibility callbacks, independent threads and GC.

Timings cover the complete count caller: eligibility, fresh input copies,
one ordinary FFM call, result copies, palette lookups and consuming callbacks.
Recount workloads also run the unchanged `BlockCounter`, publish all fields and
consume the section's three public status predicates. Both implementations use
identical sources. Setup and initial scratch allocation occur before timing.

Single-entry shortcuts are excluded from timings and are not credited as Rust
work. Saved workloads use **every multi-entry palette section** in the unchanged
16-chunk corpus (two seeds, four dimensions). Separate cases include stale
palettes that become uniform, collisions and all 4096 distinct IDs.

Three independent warmed JVM pairs alternate order on the same CPU. Every
measured round must report zero JIT compilation time. Each workload must take
at least 5% less time in every pair and at the upper 95% bootstrap ratio bound.
Raw rounds, checksums, source/library hashes and parity evidence are written to
`build/palette-histogram-migration/acceptance/`. Measurements apply to this slice;
loading, disk I/O, full lighting and world generation are outside that timing.

## Verified status

Release acceptance passed on 2026-10-01: Ryzen 5 5600G, Linux x86_64,
OpenJDK 25.0.4.1. Twelve histogram Java tests, two Rust tests and eleven
palette-packing regression tests passed with zero differences. Coverage includes
772 seeded/resize fixtures, 70 zero-key cases, all 31,809 registered states and
324 saved sections. Finite tests cannot enumerate every possible section.

All 27 timing workloads passed in every JVM pair and their confidence bounds.
The smallest individual-pair reduction was **12.3%**, with a conservative
95% bound of **11.5%**. Complete recount timings for saved multi-entry sections:

| Dimension | Sections | Original Java | Rust including boundary | Less time |
|---|---:|---:|---:|---:|
| Overworld | 44 | 9.74 µs | 4.65 µs | 52.3% |
| Nether | 30 | 8.61 µs | 3.82 µs | 55.7% |
| End | 2 | 6.83 µs | 2.80 µs | 58.9% |
| Primordial | 94 | 9.01 µs | 4.54 µs | 49.6% |

These 170 sections exercise the migrated branch. The corpus's remaining 154
single-entry sections are included in parity checks, outside these timings.
The largest synthetic palette workloads improve less than normal terrain;
all 4096 distinct IDs still take 20.3% less time including the boundary.

`build/palette-histogram-migration/acceptance/REPORT.md` contains all workloads
and scope details. `results.json` retains the raw rounds and confirms final
source/library hashes and cross-fork checksums match the measured snapshot.
