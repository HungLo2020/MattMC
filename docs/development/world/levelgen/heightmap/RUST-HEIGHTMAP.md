# Rust heightmap priming

`Heightmap.primeHeightmaps()` reconstructs column heights through
`levelgen/heightmap/`. The Rust scanner consumes existing packed sections and
updates packed heightmaps, stopping once each requested column/type is resolved.
`NativeHeightmap` lives beside chunk storage in Java so its borrowed palette and
storage access can remain package-private. Incremental `Heightmap.update()` is
unchanged.

## Compatibility and ownership

- Preserve the original explicit `Blocks.AIR` rejection and all six opacity
  predicates. Canonical block-state air/solid/fluid properties are immutable;
  their type masks are shared. Local palette mappings are refreshed on each call.
- A column with no matching block **keeps its previous height**. Existing packed
  padding bits and unrequested maps also remain unchanged. Do not clear maps or
  simplify fluid/leaf predicates when working on this code.
- Section traversal proceeds downward, with original X/Z order inside sections.
  Empty sections use the same AIR shortcut as normal chunk reads. Java publishes
  the native scratch results only after successful evaluation.
- One ordinary FFM call handles each needed nonempty section. Input words are
  copied into thread-owned native memory; Rust allocates nothing and retains no
  pointers. Calls follow existing chunk ownership rules: sections must not be
  concurrently mutated while priming.
- Standard `ProtoChunk` and non-debug `LevelChunk` readers use Rust. Custom
  readers, sections, palettes, storages, state subclasses and non-`EnumSet`
  selections retain the compatibility path. Unsupported dimensions do too.
  Malformed palette IDs fall back before publication, preserving the original
  exception and partial-write behavior. Late registry changes disable stale
  global-palette snapshots.

## Verify a change

```sh
python3 DevUtils/tests/worldgen/VerifyRustHeightmap.py --parity-only
python3 DevUtils/tests/worldgen/VerifyRustHeightmap.py --forks 3
```

The retained Java compatibility method serves as the baseline. The driver pins
its complete body and every shared `Heightmap` helper to Git `21c268342`; the
only permitted changes are its name and the public native dispatch entry.
Focused tests compare raw packed maps and visible heights for every type subset,
missing/existing maps, untouched padding, fluids, leaves, air variants, global
palettes, changed blocks, dimension limits, malformed IDs and concurrent readers.
The corpus also replays 16 saved FULL chunks from two seeds and all four
dimensions; loading the corpus needs no server or renderer. Regenerate it from
the retained profiling worlds with:

```sh
python3 DevUtils/tests/worldgen/ExtractHeightmapCorpus.py \
  --profile build/worldgen-profile-20260930 \
  --output src/test/resources/worldgen/heightmap/chunks.json
```

The performance gate times the complete caller: eligibility checks, palette
mapping, input transfer, native calls, output publication and consuming raw maps.
Missing-map cases also include creating the maps. Real saved terrain and uniform,
empty, fluid, leaf and global-palette fixtures are measured with both worldgen
types and all six types. Registry/corpus/fixture loading is excluded equally.

Each implementation runs alone in an independently warmed JVM, pinned to the
same CPU. Three pairs alternate order; measured rounds must report zero JIT
compilation. Every workload must take at least 5% less time in every pair and at
the upper 95% bootstrap ratio bound. Raw rounds, source/library/corpus hashes and
environment details are under `build/heightmap-migration/acceptance/`.
These are complete priming timings, not a full chunk-generation speedup claim.

## Status

Release acceptance passed on 2026-10-01 (Linux x86_64, Ryzen 5 5600G,
OpenJDK 25.0.4.1). Eight focused Java tests and two Rust tests passed with zero
mismatches: 6,291,456 seeded column comparisons, 1,008 saved-terrain type-subset
cases, 756 unresolved-column cases, and the edge cases described above.

All 22 workloads passed the 5% gate in each of three independently warmed JVM
pairs. The smallest observed reduction was **71.3%**, with a conservative 95%
bound of **71.2%**. Saved-terrain worldgen-type timings below are medians of
process medians per complete priming call, including boundary overhead:

| Dimension | Original Java | Rust including boundary | Less time |
|---|---:|---:|---:|
| Overworld | 52.61 µs | 10.10 µs | 80.8% |
| Nether | 11.98 µs | 2.86 µs | 76.1% |
| End | 53.98 µs | 3.77 µs | 93.0% |
| Primordial | 12.25 µs | 2.85 µs | 76.7% |

Saved-terrain all-six-type cases, including map creation, also passed. Uniform
water/leaves stress cases show larger gains because the original Java list
revisits accumulated unresolved entries. Those are not typical terrain results.
The scanner uses integer operations without SIMD; it avoids repeated chunk
reads, predicate dispatch and individual packed-height writes.

`build/heightmap-migration/acceptance/REPORT.md` contains all 22 measurements;
`results.json` records raw rounds, checksums and hashes. Source and native-library
hashes were rechecked after measurement. These results apply to this machine and
tested corpus. Finite parity corpora cannot enumerate every possible input.
