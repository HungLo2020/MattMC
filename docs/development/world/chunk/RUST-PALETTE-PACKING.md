# Rust block-section palette packing

Standard 4096-entry block containers use Rust for `PalettedContainer.pack()`:
decoding source words, compacting used IDs in first-use order, and encoding
padded save words with identity-alias remapping when needed. Java owns the
container lock, resolves used object identities, and builds the returned
list/stream. This is save packing; [block palette growth](RUST-PALETTE-RESIZE.md)
is a separate migration. Ordinary storage reads/writes, network serialization,
unpacking and 64-entry biome packing remain their existing implementations.
Chunk saves no longer call this path: the
[section serializer](RUST-CHUNK-SECTIONS.md) packs inside its own Rust call. This
path still serves other `pack()` and codec callers.

The kernels and FFI live in
[`world/level/chunk/palette/`](https://github.com/HungLo2020/MattMC/tree/master/src/main/rust/world/level/chunk/palette).
The Java bridge is
[`NativePalettePacking`](https://github.com/HungLo2020/MattMC/blob/master/src/main/java/net/minecraft/world/level/chunk/NativePalettePacking.java).

## Constraints

- Preserve **object identity**, not `equals()`. Duplicate source identities
  collapse to one output entry, ordered by their first decoded occurrence.
  Distinct objects comparing equal must remain distinct.
- Preserve the requested strategy's output bit count, absent storage for a
  single identity, and every output word. Unused padding must be zero even if
  input padding is nonzero. Packing must not mutate the source.
- Keep the original `acquire()`/`finally release()` contract. Use thread-owned
  native scratch and ordinary FFM downcalls; Rust borrows disjoint buffers,
  allocates nothing and retains no pointers. Never use critical heap access
  for this full-section work.
- Resolve local identities from used palette entries afresh each call; never
  cache their object references or inspect unused entries. Global labels refer only to the
  immutable canonical block-state registry after bootstrap; registry growth
  selects the original path. Do not mutate that registry after bootstrap.
- Custom containers, strategies, palettes/storage, noncanonical global maps,
  malformed IDs, used null entries and unsupported sizes/widths retain the
  original compatibility path before publishing a result. Avoid additional
  custom callbacks: eligibility checks must precede virtual reads.
- Keep the original shared reencoding helper: global unpacking still uses it.

## Verify a change

```sh
python3 DevUtils/tests/chunk/VerifyRustPalettePacking.py --parity-only
python3 DevUtils/tests/chunk/VerifyRustPalettePacking.py --forks 3
```

The driver uses Linux `taskset`/`lscpu`; select another available logical CPU
with `--cpu N` if CPU 2 is unavailable. It builds the release library by default.

The driver pins the original Java pack/reencoding bodies to Git `fffe4a073` and
checks that production code changed only at the native dispatch insertion.
It runs this subsystem's Java and Rust tests without a server or renderer.
Comparisons require identical palette object references/order, bit counts,
optional-storage presence and all raw words, followed by saved-section decode
round trips. Cases cover single/local/global palettes, width boundaries,
every registered global block state, identity aliases, stale/changing palettes,
padding, malformed inputs, compatibility
callback traces, independent threads and GC.

Timings include the **complete public `pack()` caller**: locking, eligibility,
local identity preparation, copies, downcalls, output allocation and consuming
the resulting list/words. Fixture loading and immutable registry-label bootstrap
are excluded; results are recomputed every call. Saved terrain uses every
section from 16 FULL chunks: two seeds and four dimensions. Synthetic cases
exercise palette cardinalities and stale palettes separately.

Three independent process pairs alternate order and warm the JVM until recent
timings stabilize and compilation stops. Measured rounds require zero reported
JIT compilation time. Each workload must take at least 5% less time in every
pair and at its upper 95% bootstrap ratio bound. Evidence is written beneath
`build/palette-packing-migration/acceptance/`: raw rounds, checksums, JVM/hardware,
source/library hashes and focused parity results. These measurements describe
save packing on the measured machine, not complete chunk generation or disk I/O.

The container source audit additionally permits only the separately pinned
[ordered-value dispatch](RUST-PALETTE-DISTINCT.md); the original reference bodies
remain unchanged.

## Verified status

Release acceptance passed on 2026-10-01: Ryzen 5 5600G, Linux x86_64,
OpenJDK 25.0.4.1. Eleven Java tests and three Rust tests passed with zero
differences: 672 seeded fixtures / 2,752,512 entries, all 31,809 registered
global states, and 324 saved sections with complete decode round trips.
Finite tests cannot enumerate every possible section.

All 21 workloads passed in every independent process pair and their confidence
bounds. The smallest individual-pair reduction was **18.3%**, with a conservative
95% bound of **18.1%**. Saved-terrain timings per complete section pack:

| Dimension | Original Java | Rust including boundary | Less time |
|---|---:|---:|---:|
| Overworld | 8.68 µs | 3.40 µs | 60.8% |
| Nether | 7.46 µs | 3.38 µs | 54.7% |
| End | 3.65 µs | 0.40 µs | 89.1% |
| Primordial | 12.71 µs | 6.64 µs | 47.8% |

The initial implementation was rejected for resolving unused palette entries;
the stale-256 case regressed. Resolving only used identities fixed it, and the
complete acceptance was rerun. The final stale-256 case takes 4.39 µs versus
6.70 µs for Java (34.5% less time).

`build/palette-packing-migration/acceptance/REPORT.md` contains all 21 workloads
and scope details. `results.json` preserves raw measurements; `integrity.json`
records matching final source/library hashes and cross-process checksums.

Those hashes describe that acceptance snapshot. The later
[histogram migration](RUST-PALETTE-HISTOGRAM.md) adds a separate count dispatch
to the same container; the packing verifier permits that exact known insertion
and continues to pin the original pack body and shared helpers.
It also permits the exact block-resize/global-unpack dispatches and constructor
visibility change. These slice integrations retain the original shared helpers. Historical
hashes and timings above describe their recorded snapshot.
