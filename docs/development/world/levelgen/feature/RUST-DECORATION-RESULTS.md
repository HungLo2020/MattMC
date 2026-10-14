# Native decoration: recorded results

Results for the first native decoration slice (ore placed features), measured
on 2026-10-13 against Current `94e748593` (pre-change) and Frozen `7a4d18171`.
They describe this version only; rerun the [FULL-chunk driver](../RUST-FULL-GENERATION.md)
before claiming them for a later change. Receipts are under `build/full-generation/`.

These are implementation-author measurements, not suites or captures rerun by
this documentation review. The later [origin-region qualification](../RUST-FULL-GENERATION.md#known-differences-2026-10-13)
records one of nine Current runs with nine differing trees; immediate native-on
and Java-route reruns matched. Thus the identical Current runs below describe
those attempts, not universal determinism. Resumed generation is also not a
strict whole-world oracle; per-placement shadow replay has a different scope
and its altered palette order must not be used as normal parity output.

## Correctness

Seed 42, two 81-chunk regions in each of the four dimensions (648 FULL chunks),
serial requests on one worker, on the committed build (`ore-final`):

- **Pre-change Current:** identical in every chunk and field. The native
  route ran in every bound region (968 bound, none declined; 20,116 native
  ore placements).
- **Frozen:** End and Nether identical; overworld differs in the same 3 chunks
  where Frozen differs from itself (`control-frozen`); Primordial Caves differs
  in every chunk because of the intentional ore-tag fix, exactly as pre-change
  Current does.
- **Repeatability:** two pre-change Current runs were identical; two Frozen
  runs differed in 13 overworld chunks.
- **Shadow replay** (`-Dmattmc.worldgen.verifyDecoration=true`), which
  undoes each native placement, replays it on Java's route and compares every
  bound section: 0 mismatches over 37,469 placements under 4-worker parallel
  generation (`verify-parallel-1`) and 20,116 placements on chunks restored
  from disk at CARVERS (`verify-resume-3`), where Java primed its own WG
  heightmaps and they matched Rust's lazily primed ones.
- **Exact arithmetic:** the HotSpot `sin`/`cos` port matches the JVM for all
  2²⁴ ore angles; libm differs for 66,193. Rust's `Mth.SIN` matched Java's
  table at every startup.
- Rust suite 2,797 passed (3 ignored); Java 1,947 + 183 parity tests passed
  (2 skipped). Server logs have no new errors.

The parallel shadow count above follows this dedicated result and the source
commit (37,469). The root `PROGRESS.md` at the same source reports 44,416;
these records are not reconciled here and must not be combined into a single
verified total. Both report 20,116 resumed placements.

## Performance

Normal parallel generation, 4 workers on CPUs 2–7, three alternating launches
per side, each with two warmup regions and three measured 169-chunk regions per
dimension (507 chunks). Medians of per-launch totals (`timing-ore-final`;
an earlier set, `timing-ore-1`, agreed within 4%). "Java route" is the same build
with `-Dmattmc.worldgen.javaDecoration=true`, which runs the pre-change code.
Feature time is the synchronous `generateFeatures` step summed across workers.

| Dimension | Feature step: native / Java route / Frozen | FULL wall time: native / Java route / Frozen |
| --- | --- | --- |
| Overworld | 1,969 / 2,356 / 2,448 ms | 6.92 / 7.41 / 12.89 s |
| Primordial Caves | 4,763 / 5,018 / 4,204 ms | 10.91 / 11.12 / 16.67 s |
| Nether | 746 / 772 / 636 ms | 3.67 / 3.71 / 7.91 s |

The overworld feature step is 16.4% faster than the Java route and faster
than Frozen. Primordial Caves cannot be compared with Frozen's feature time,
because Frozen places far fewer ores there. **The Nether feature step is 17%
slower than Frozen on both routes**: the gap predates this change and stays
open. Nether FULL generation as a whole is 2.2× faster than Frozen.
