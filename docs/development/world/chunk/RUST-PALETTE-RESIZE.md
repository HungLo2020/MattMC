# Rust block-section palette resizing

Current canonical storage uses the [Rust live section owner](RUST-LIVE-SECTIONS.md).
The helper path and historical acceptance below are narrower verification;
historical pinned-body drivers reject the new container ownership changes.
Use the live-section guide's current Gradle checks; the older driver commands
below describe the historical helper acceptance, not current-owner verification.


Compatibility block palettes grow through `PalettedContainer.onResize()`. Rust collects
used source IDs and repacks 4,096 entries into the new storage. Java assigns
object identities to the new palette and publishes the completed data. This
covers normal 0→4→5→6→7→8→global growth and direct jumps from local to global.
Compatibility biome resizing and custom paths retain their existing implementation.
Canonical live block palettes and admitted [live biome palettes](../biome/RUST-LIVE-BIOMES.md)
grow inside their separate Rust owners.

The kernel is in
[`palette/resize/`](https://github.com/HungLo2020/MattMC/tree/master/src/main/rust/world/level/chunk/palette/resize).
[`NativePaletteResize`](https://github.com/HungLo2020/MattMC/blob/master/src/main/java/net/minecraft/world/level/chunk/NativePaletteResize.java)
owns the two ordinary FFM calls and thread-local scratch. There is no native
allocation, retained pointer or remapping-result cache.

## Constraints

- The destination must be the fresh data created by `onResize()`. Reused data
  keeps the original per-entry copy. Publish `this.data` at its original point;
  do not add locks or alter the caller's existing exclusion rules.
- Preserve first-use **source ID** order, then assign destination IDs with the
  original Java palette. Identity aliases collapse exactly as before. Do not
  reorder entries using registry order or equality.
- Hash palettes add the triggering object **before** calling `onResize()`.
  An old 8-bit palette can therefore have 257 entries; only IDs 0–255 occur in
  its old storage. Copy used values, then perform the original triggering lookup.
- Validate every used source ID/reference before changing the destination.
  Invalid IDs, nulls or uninitialized values select the original copy so its
  exception and partial-copy behavior remain intact. Do not inspect unused values.
- Accept only exact built-in storage/palette types, a standard block strategy
  with the canonical block registry, and an empty local destination or the
  canonical global palette. Custom callbacks must use the original path without
  extra eligibility reads. Unsupported widths and global source palettes also
  retain that path.
- Input padding is ignored; the newly allocated destination's padding stays zero.
  Do not reuse dirty destination words as if they were fresh storage.
- Scratch is borrowed for one call chain, never shared between threads. Clear
  temporary object references in `finally`. Ordinary downcalls permit GC while
  Rust borrows disjoint native buffers.

## Why the remapping is equivalent

The native prepass visits the same 4,096 positions in the same order and records
only each ID's first occurrence. Built-in palette reads have no user callbacks;
repeating a destination lookup for an already inserted object returns the same
ID. Assigning IDs at those first occurrences therefore creates the same palette,
including aliases. Native packing writes those assigned IDs into the same fields
of fresh zeroed storage. The original triggering lookup and publication order
are unchanged. Inputs outside these assumptions use the original copy.

## Verification

```sh
python3 DevUtils/tests/chunk/VerifyRustPaletteResize.py --parity-only
python3 DevUtils/tests/chunk/VerifyRustPaletteResize.py --forks 3
```

The Linux driver builds the release library, pins original resize/copy/write
bodies to Git `b81c01943`, and permits only the exact resize/unpack dispatches
and constructor visibility change. It runs this slice's parity tests and affected packing/histogram
regressions. Use `--cpu N` to select an available logical CPU.

Checks compare palette identity/order, configurations, raw words, every decoded
ID/reference and returned old values. They exercise all growth boundaries,
direct global jumps, every registered block state, saved sections, aliases,
unused null entries, stale palettes, padding, invalid inputs, callback traces,
incremental resize/write/save sequences and independent threads during GC.

Timing covers the **complete resize caller**, including destination allocation,
eligibility/preflight, used-value resolution, Java ID assignment, both FFM calls,
all copies, publication, triggering lookup and output consumption. Triggered-write
cases also include the outer write and equal input copies in both modes; that
extra restoration cost is reported, rather than credited as migrated work.
Both reference and production data fields are volatile. Fixtures and initial
scratch setup occur before timing; results are recomputed every call.

Acceptance requires at least three independently warmed JVM pairs, zero reported
JIT compilation during measured rounds, matching output checksums, and at least
5% less time in every pair and at the upper 95% bootstrap ratio bound for each
workload. Evidence goes in `build/palette-resize-migration/acceptance/`.
This measures palette growth, not complete world generation or all block writes.

The container source audit additionally permits only the separately pinned
[ordered-value dispatch](RUST-PALETTE-DISTINCT.md); the original reference bodies
remain unchanged.

## Verified status

Release acceptance passed on 2026-10-01: Ryzen 5 5600G, Linux x86_64,
OpenJDK 25.0.4.1. Nine resize Java tests, 23 affected packing/histogram
regressions and eight Rust palette tests passed with zero differences.
Coverage includes 438 growth fixtures / 1,794,048 entries, all 31,809 registered
states, all 324 saved sections, and 72,000 incremental writes followed by
original-versus-native save packing. Finite tests cannot enumerate every section.

All 31 workloads passed in all three JVM pairs and their confidence bounds.
The smallest individual-pair reduction was **80.4%**, with a conservative
95% bound of **80.1%**. Complete `onResize()` timings for random inputs:

| Old palette | Growth | Original Java | Rust including boundary | Less time |
|---|---|---:|---:|---:|
| 1 | 0→4 bits | 16.80 µs | 0.49 µs | 97.1% |
| 16 | 4→5 bits | 61.70 µs | 5.84 µs | 90.5% |
| 32 | 5→6 bits | 57.06 µs | 6.82 µs | 88.1% |
| 64 | 6→7 bits | 67.21 µs | 8.09 µs | 88.0% |
| 128 | 7→8 bits | 72.38 µs | 10.27 µs | 85.8% |
| 256 | 8→global | 69.49 µs | 10.69 µs | 84.6% |

An actual write triggering 256→global growth takes 13.18 µs versus 72.95 µs
(81.9% less time), including equal per-call input-copy/reset costs. Those copies
are benchmark restoration overhead, not an extra requirement of production.

Saved-section resize cases use all 96 Overworld, 64 Nether, 64 End and 100
Primordial sections. Every original section qualifies; these cases request a
resize on that input rather than asserting every section resizes during gameplay.

An independent spot check also called the **unchanged production Java
`Data.copyFrom()`** directly, instead of the adapted test oracle. Rust took
97.5%, 90.2% and 83.7% less time for 1-, 16- and 256-entry inputs, including the
native boundary. This supports the main result; it is not a substitute for the
three-pair full-caller acceptance.

`build/palette-resize-migration/acceptance/REPORT.md` contains all 31 workloads,
scope and parity evidence. `results.json` retains raw rounds, checksums and
matching final source/library hashes; `live-baseline.json` and its Java source
record the independent production-loop spot check. No explicit SIMD kernel was
needed: the bulk loops remove repeated per-entry Java operations.
