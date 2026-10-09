# Rust global block-palette loading

Current canonical storage uses the [Rust live section owner](RUST-LIVE-SECTIONS.md).
The helper path and historical acceptance below are narrower verification;
historical pinned-body drivers reject the new container ownership changes.
Use the live-section guide's current Gradle checks; the older driver commands
below describe the historical helper acceptance, not current-owner verification.


`PalettedContainer.unpack()` uses Rust to repack a saved block section's
palette IDs into global in-memory IDs. This applies to standard block palettes
with 257–65,536 saved entries (9–16 bits). Small palettes already load by wrapping
the saved words; they keep that direct path. This migration measures the global
repacking operation, not all chunk loading or world generation.

The kernel lives in
[`palette/unpack/`](https://github.com/HungLo2020/MattMC/tree/master/src/main/rust/world/level/chunk/palette/unpack).
[`NativePaletteUnpacking`](https://github.com/HungLo2020/MattMC/blob/master/src/main/java/net/minecraft/world/level/chunk/NativePaletteUnpacking.java)
resolves used objects through the Java global palette, calls the existing native
encoder, and constructs the Java-owned result. Both FFM calls are ordinary calls;
Rust allocates nothing and retains no pointers. Thread-local scratch uses about
360 KiB, is protected against reentry, and holds no palette/block references.

## Constraints

- Keep declared-bit checks, stream consumption, error messages and publication
  in the original caller order. Invalid source IDs select the original path,
  preserving its exception and first missing ID. Never publish partial native output.
- Only the canonical block registry, standard block strategy/global configuration,
  and exact known list implementations qualify. Custom lists/registries/strategies,
  biome data and unsupported widths retain the original compatibility path.
- Every palette entry must be non-null. For such lists, the original temporary
  `HashMapPalette` assigns `byId[i] = entries[i]`, including identity aliases.
  That permits direct lookup without building its unused identity hash table.
  Nulls have different insertion/growth behavior and require the original path.
- Preserve identity-based global lookup, including the original unknown-object
  ID-zero result. Mask target IDs exactly as `SimpleBitStorage` does, ignore
  input padding, and zero all output padding.
- Native decode workspace must be zero on entry and exit, including errors.
  Buffers must be disjoint, aligned and valid for the full call. Java owns input
  and registry stability under the same rules as the original unpacker.
- The package-visible container constructor supports Java-owned native results
  and the literal original test caller. Keep its initialization unchanged.

## Verification

```bash
python3 DevUtils/tests/chunk/VerifyRustPaletteUnpacking.py --parity-only
python3 DevUtils/tests/chunk/VerifyRustPaletteUnpacking.py --forks 3
# Supplemental uniform minimum/boundary palettes; run after full acceptance:
python3 DevUtils/tests/chunk/VerifyRustPaletteUnpackingUniform.py
```

The Linux driver builds release Rust, pins the complete original unpack/reencode
bodies to Git `b81c01943`, and permits only the specific resize/unpack dispatches
and constructor visibility change. It runs focused unpack tests plus affected
packing, histogram and resize regressions. Use `--cpu N` for an available CPU.

Parity compares configurations, raw words, every decoded ID and object identity.
Coverage includes source-width boundaries, every registered state, aliases,
null/custom compatibility, malformed inputs, stream/callback order, actual codec
round trips and concurrent readers. Unchanged saved sections test local-path
compatibility; edited saved sections exercise global loading and original
save/load/count/native-save integration. Finite tests cannot enumerate every section;
the equivalence also depends on the non-null `byId[i]` invariant above.

Timing includes the **complete unpack caller**: fresh stream/packed-data creation,
stream materialization, all allocations, eligibility checks, Java ID lookup,
both native calls, all buffer copies, result construction and word consumption.
Original Java's temporary palette construction is included because production
really performs it. Fixtures and initial scratch setup are outside timing;
results are recomputed every call. The main workloads use `ListN`, confirmed
from the actual codec, with other known list classes covered separately.

Acceptance requires three independently warmed JVM pairs, zero reported JIT
compilation in measured rounds, equal checksums within/across forks, and at
least 5% less time in every pair and at the upper 95% bootstrap ratio bound for
every workload. Raw evidence belongs in
`build/palette-unpacking-migration/acceptance/`.

The container source audit additionally permits only the separately pinned
[ordered-value dispatch](RUST-PALETTE-DISTINCT.md); the original reference bodies
remain unchanged.

## Recorded verification

Release acceptance passed on 2026-10-01 (Ryzen 5 5600G, Linux x86_64,
OpenJDK 25.0.4.1). Eight unpack Java tests, 32 affected Java regressions and
10 Rust palette tests passed with zero output differences. Coverage includes
768 fixtures / 3,145,728 entries, every 31,809 registered state, all 324
unchanged saved sections, 16 edited global sections and the actual codec path.

All 26 main workloads and four supplemental uniform global palettes passed
three JVM pairs and their confidence bounds. The smallest individual gain was
**19.1% less time**, with a conservative 95% bound of **18.8%**. Median complete
caller timings (including the boundary):

| Input | Original Java | Rust + boundary | Less time |
|---|---:|---:|---:|
| Random, 257 entries | 47.02 µs | 10.75 µs | 77.1% |
| Random, 1,024 entries | 79.40 µs | 23.01 µs | 71.0% |
| Random, 4,096 entries | 222.71 µs | 67.56 µs | 69.7% |
| Edited saved Overworld sections | 28.32 µs | 21.12 µs | 25.4% |
| Edited saved End sections | 26.11 µs | 20.84 µs | 20.2% |

The edited-section cases are explicitly derived from saved data; the unchanged
corpus uses local loading. Serialized palettes above 4,096 entries exercise
redundant/unused-entry file boundaries, not ordinary save output. Results include
uniform 257/512/513/1,024-entry palettes where Java's run shortcut helps most.

`build/palette-unpacking-migration/acceptance/REPORT.md` contains the equivalence
argument, scope and all 30 results. Raw rounds, checksums, compiler counters and
matching final source/library hashes are in `results.json`,
`uniform-results.json` and process logs. These are measurements of this migration
slice after warmup, not cold startup or whole-game performance.
