# Rust ordered palette values

`PalettedContainer.getAll()` scans standard nonzero-width biome (64-entry) and
block (4096-entry) storage in Rust. World generation uses this caller to gather
biomes for decoration. Java retains palette objects and consumer calls.

The kernel lives in
[`palette/distinct/`](https://github.com/HungLo2020/MattMC/tree/master/src/main/rust/world/level/chunk/palette/distinct).
[`NativePaletteDistinct`](https://github.com/HungLo2020/MattMC/blob/master/src/main/java/net/minecraft/world/level/chunk/NativePaletteDistinct.java)
owns FFM transfers and about 48 KiB of numeric scratch per calling
thread. Rust allocates nothing and retains no pointers or prior results.

## Compatibility rules

- Emit IDs in **first-occurrence order**, matching the original `IntArraySet`.
  Distinct IDs remain separate even when they resolve to aliases or null.
- Capture the Java palette before scanning. Resolve each ID through that
  captured object during delivery. Consumer mutations may replace the
  container's palette; they must not redirect the remaining outer callbacks.
- Finish reading storage before any palette lookup or consumer call. Preserve
  lookup order, partial delivery and the exact thrown callback exception.
- Hold the scratch lease through delivery and release it in `finally`.
  Reentrant scans retain the original Java loop. No callback runs inside Rust.
- Only exact standard containers and `SimpleBitStorage` with widths 1–16 and
  sizes 64/4096 use Rust. Zero-bit storage avoids bridge initialization;
  custom storage, subclasses and unsupported formats retain original behavior.
- Decode only logical fields; ignore full-word and final-word padding. Always
  read current packed words. Stable input ownership is required by the original
  unsynchronized method too; this migration adds no lock.
- FFI buffers must be aligned, valid, live and disjoint. Metadata failures write
  nothing. The 1024-word seen workspace starts zero and is restored after every
  successful call; output records are freshly computed.
- Biome calls use a separate critical heap-access entry point strictly limited
  to 64 fields. It allocates nothing, never blocks or calls Java, and finishes
  at most 64 workspace resets before releasing heap access. Larger 4096-entry
  scans use ordinary downcalls and copied buffers, permitting GC while scanning.

## Why the output is identical

Both scans visit packed fields in the same order. Before each field, the native
seen bits represent exactly the IDs already inserted into Java's set. An unseen
ID sets its bit and appends one record; a repeated ID appends nothing. Induction
gives identical ordered IDs. After all representable IDs occur, remaining fields
cannot introduce another ID, so stopping early preserves the result. Java then
uses the same captured palette and invokes the same callbacks in that order.

## Verify

```sh
python3 DevUtils/tests/chunk/VerifyRustPaletteDistinct.py --parity-only
python3 DevUtils/tests/chunk/VerifyRustPaletteDistinct.py --forks 3
# Narrow workload in fresh JVMs; keep evidence in a separate directory:
python3 DevUtils/tests/chunk/VerifyRustPaletteDistinct.py --forks 3 \
  --case saved_biomes_overworld --output build/palette-distinct-migration/isolated-overworld
```

The Linux driver builds release Rust, pins the original complete `getAll()` body
to Git `7af3a1594` and permits only the exact native dispatch. It also runs
affected histogram, packing, resizing and global-loading tests, plus focused
Rust palette tests. `--cpu N` selects a CPU; `--skip-build` requires an already
matching release library. No renderer or full-game test is required.

Saved biome fixtures preserve the exact palettes and words from the existing
16-chunk profiling corpus (two seeds, four dimensions). Tests use canonical
biome names as palette objects, with the ordinary biome strategy. Re-extract
only from the recorded, hash-verified regions:

```sh
python3 DevUtils/tests/chunk/ExtractDistinctBiomeCorpus.py \
  --profile build/worldgen-profile-20260930 \
  --output src/test/resources/worldgen/palette_distinct/biomes.json
```

Parity covers insertion order, callback identity, every supported width,
padding, aliases/nulls, missing entries, all registered block states, saved
biomes/blocks, consumer mutations, resizing, exceptions, reentry and concurrent
readers during GC. Finite tests supplement the ordered-scan argument.

Performance measures the complete production caller: eligibility, fresh input
copy or bounded heap access, the downcall, ordered ID delivery, palette lookups and consumption of
every callback. Equal inputs, freshly computed outputs, JVM warmup and initial
scratch/library setup exclusions apply equally to both implementations. The
27 workloads include 64-entry biome cases and all mixed saved sections;
zero-bit compatibility paths are tested without crediting them as native gains.
Acceptance requires three independent JVM pairs, alternating order, zero
reported compiler time during measurements, equal checksums, at least 5% less
time in every pair and an upper 95% bootstrap ratio bound no greater than 0.95.

## Recorded verification

Release acceptance passed 2026-10-01 (Ryzen 5 5600G, Linux x86_64,
OpenJDK 25.0.4.1). Eight new Java tests, 40 affected Java tests and 13 focused
Rust palette tests passed with zero differences. Coverage includes 65,536
exhaustive ordered inputs in each language, every supported width, all 31,809
registered states, 324 saved biome sections and 324 saved block sections.

All 27 main workloads and three fresh single-workload checks passed three
independent JVM pairs and their confidence bounds. Main-suite minimum **65.5%
less time**; including the narrow checks, minimum **40.9%**, conservative 95%
bound **40.9%**. The narrow uniform case has different Java compilation context;
retain its lower reduction when describing the overall result.

| Fresh single-workload caller | Original Java | Rust + boundary |
|---|---:|---:|
| Uniform 64-entry biome, retained one-bit palette | 125.33 ns | 62.08 ns |
| All five mixed saved Overworld biome sections | 232.36 ns | 35.37 ns |
| 4096-entry block section, two values | 26.31 µs | 2.51 µs |

`build/palette-distinct-migration/acceptance/REPORT.md` has the ordered-scan
argument, all timings and verification commands. Main `results.json` and
`isolated-*/results.json` retain matching source/library hashes, output checksums
and zero measured compiler time. Initial interrupted builds and one-pair
diagnostics are separate. These are warmed caller measurements, not total
world-generation time, arbitrary callback costs, compatibility inputs or FPS.
