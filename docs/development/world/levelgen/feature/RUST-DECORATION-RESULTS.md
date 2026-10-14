# Native decoration: recorded results

Results for the first native decoration slice (ore placed features), measured
on 2026-10-13 against Current `94e748593` (pre-change) and Frozen `7a4d18171`.
They describe this version only; rerun the [FULL-chunk driver](../RUST-FULL-GENERATION.md)
before claiming them for a later change. Receipts are under `build/full-generation/`.

These are implementation-author measurements, not suites or captures rerun by
this documentation review. The later [origin-region qualification](../RUST-FULL-GENERATION.md#known-differences-2026-10-14)
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

## Slice 2: `setBlock` features (replace blobs, glowstone, springs)

Uncommitted working-tree results, 2026-10-13. The oracle moved off spawn
(regions at chunk (64,−48) and (128,−96)); there, Frozen and a pre-change
reference checkout (`build/reference-342352375`) each repeat exactly.

- **Parity:** 648 FULL chunks identical to the reference; identical to Frozen
  except Primordial Caves (intentional ores). Nether native placements: 68
  replace-blobs, 484 glowstone, 1,283 springs; replayed events included 34 light
  checks, one section-status change and 1,127 fluid ticks.
- **Shadow replay:** 41,029 native placements under 4-worker parallel
  generation, 0 mismatches in blocks, final heightmaps, sky sources, ticks and
  post-processing.
- **Timing** (same method as above; medians of three launches; "Java route"
  now includes the faster live-owner palette lookup):

| Dimension | Feature step: native / Java route / Frozen | FULL wall time: native / Java route / Frozen |
| --- | --- | --- |
| Overworld | 1,754 / 2,164 / 2,362 ms | 7.25 / 7.75 / 13.28 s |
| Primordial Caves | 4,273 / 4,563 / 3,878 ms | 9.81 / 9.97 / 13.91 s |
| Nether | 757 / 970 / 810 ms | 3.73 / 4.01 / 8.03 s |

The Nether feature step is now 6.5% faster than Frozen; the earlier gap closed.

## Slice 3: the step loop, templates and the feature tail

Uncommitted working-tree results, 2026-10-14. These remain author-reported
measurements after the documentation was committed in `46cb83c5`; the committed
record does not independently identify an exact tested commit/native image.
No runtime suite was rerun for this documentation review. The ordered pre-pass
and off-spawn setup differ from the earlier unordered/origin protocol; preserve
those earlier failures rather than treating the workloads as interchangeable.
See [source review #775](https://github.com/HungLo2020/MattMC/issues/775#issuecomment-6171699188).

Rust runs the admitted step loop, structure templates and supported vanilla
feature programs. End spikes, the End gateway and platform, fossils' own code
(their templates are native) and planted huge fungi retain Java logic. All
parity runs use the ordered pre-pass.

- **Pre-change Current** (`build/reference-342352375`): identical in 648
  standard chunks, 17 overworld and 8 Nether/End biome regions, and the
  interrupted (`--resume`) runs. Overworld and Nether structure regions differ
  only in End-city shulker yaw and in jigsaw piece boxes of a reloaded start
  (see [known differences](../RUST-FULL-GENERATION.md#known-differences-2026-10-14)).
- **Frozen:** identical in all biome regions and in the overworld, Nether and
  End standard regions; Primordial Caves differs as before (intentional ores).
- **Shadow replay:** 0 mismatches over 78,285 overworld and 9,193 Nether/End
  biome-suite placements, and 19,012 placements in four desert regions with
  desert wells forced to every chunk (26 wells; the Java route matched too).
  Vegetation patches found that `HashMap` tree bins change `HashSet` order;
  [`java_set.rs`](https://github.com/HungLo2020/MattMC/blob/master/src/main/rust/world/level/levelgen/decoration/java_set.rs)
  now emulates them, checked against Java.
- **Failure paths:** an injected failure on either route raises the "Feature
  placement" crash in the same chunk; nothing at FEATURES or later is held or
  persisted. This is the author's recorded outcome; the checked-in driver gates
  per-route statuses but does not compare failed chunk identities and permits
  an absent in-memory entry. See [the exact acceptance limits](../RUST-FULL-GENERATION.md#interrupted-and-failed-generation).
- Rust suite 2,810 passed (3 ignored); Java 1,950 passed (2 skipped).

**Timing** (`timing-final`; same method as above, medians of three launches):

| Dimension | Feature step: native / Java route / Frozen | FULL wall time: native / Java route / Frozen |
| --- | --- | --- |
| Overworld | 1,511 / 2,096 / 2,377 ms | 6.94 / 7.63 / 13.19 s |
| Primordial Caves | 1,589 / 4,503 / 3,866 ms | 6.98 / 9.92 / 13.77 s |
| Nether | 566 / 985 / 796 ms | 3.49 / 3.88 / 7.91 s |
| End | 101 / 110 / 98 ms | 0.92 / 1.02 / 1.81 s |

The End feature step is level with Frozen (runs 98–106 ms against 95–107 ms);
the remaining Java time there is End-city placement. Across dimensions most
Java-route time left is structure orchestration (trial chambers, mineshaft
legacy pieces, End cities), measured with `decorationProfile`.
