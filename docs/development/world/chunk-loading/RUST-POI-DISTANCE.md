# Rust-owned POI village distance

`PoiManager.DistanceTracker` gives each section its distance (0–6) to the
nearest village centre, a section holding an occupied POI tagged
`minecraft:village`; `sectionsToVillage` answers villager and raid queries
from it. [`chunk_distance/`](https://github.com/HungLo2020/MattMC/tree/master/src/main/rust/world/level/chunk_distance)
owns the 3D section graph (26 neighbours, `SectionPos` packing), pending
levels, work queue and a mirror of which sections are village centres.
[`PoiSectionDistance`](https://github.com/HungLo2020/MattMC/blob/master/src/main/java/net/minecraft/world/entity/ai/village/poi/PoiSectionDistance.java)
is the lifetime/exception adapter; the tracker in
[`PoiManager`](https://github.com/HungLo2020/MattMC/blob/master/src/main/java/net/minecraft/world/entity/ai/village/poi/PoiManager.java)
keeps only its published levels. There is no Java graph or fallback.

## Preserve these contracts

- Village-centre state changes only when a section's records change (add,
  remove, ticket claim/release, consistency refresh: all `setDirty`), when a
  section loads (`onSectionLoad`), or when POI type tags rebind. The first two
  pass the current state with their notification; sections never unload.
- After a data pack reload rebinds tags, `MinecraftServer` calls
  `PoiManager.refreshVillageCentres()`, which reseeds every loaded section
  without scheduling work — exactly what the original's lazy reads would see.
  Any new path that changes records or membership must notify the tracker.
- The original propagates only to level 5 but its increase path stores the top
  level 6, so sections at distance 6 or more may read 6 or 7 depending on
  history. Rust reproduces this exactly; do not "fix" it.
- `SectionStorage.loadedSectionKeys()` exists only for that reseed.

## Verify and measure

The [player distance driver](RUST-PLAYER-DISTANCE.md#verify-and-measure) covers
this tracker; `--case village_occupancy,village_growth,poi_churn` selects its
workloads. Production edits to `PoiManager`, `SectionStorage` and
`MinecraftServer` are pinned as exact patches under `DevUtils/tests/server/audited/`.
Java tests drive the original and the native tracker through a
[predicate-backed fixture](https://github.com/HungLo2020/MattMC/blob/5218ac875eda9f2c4151ff1a97795f20f5f356cf/src/test/java/net/minecraft/world/entity/ai/village/poi/NativePoiDistanceTrackerTest.java#L22-L55)
that models PoiManager's notification protocol using sets of loaded sections
and village centres. They compare published iteration order and levels: all
length-four histories over two sections (changes, unchanged notifications,
silent changes with tag reloads), seeded churn near packed-coordinate wrap and
the `Long.MAX_VALUE` source. The implementation author reported that removing
the reseed failed both history and churn tests. The [GC case](https://github.com/HungLo2020/MattMC/blob/5218ac875eda9f2c4151ff1a97795f20f5f356cf/src/test/java/net/minecraft/world/entity/ai/village/poi/NativePoiDistanceTrackerTest.java#L156-L167)
creates 2,000 unreachable fixtures, requests collection and checks a surviving
tracker; it does not count released handles or prove bounded memory. Rust checks
levels against a 3D distance reference. The tag-reload tests invoke the tracker
reseed directly; the live server data-pack reload path is source-inspected and
patch-audited, not exercised by these cases.

Benchmarks replay POI notifications (mostly unchanged, as ticket claims are),
centres appearing/disappearing, per-tick runs and query behavior corresponding
to `sectionsToVillage`. The [benchmark backends](https://github.com/HungLo2020/MattMC/blob/5218ac875eda9f2c4151ff1a97795f20f5f356cf/src/test/java/net/minecraft/world/entity/ai/village/poi/PoiDistanceTrackerVerification.java#L23-L49)
use centre-set predicates and direct tracker runs/level queries; they do not
exercise real POI records, section storage, tag rebinding, villager AI or raids.
Construction is excluded from timed rounds.

## Measurements

The implementation author recorded a full release run on 2026-10-04, with the
same setup and run as the
[player distance measurements](RUST-PLAYER-DISTANCE.md#measurements).

| Workload | Java median | Rust median | Paired median reduction | 95% ratio interval |
|---|---:|---:|---:|---:|
| Three villages, occupancy notifications, 1,200 ticks | 27.9 ms | 22.0 ms | 21% | 0.749–0.822 |
| Twelve villages with changing centres, 600 ticks | 101 ms | 85 ms | 16% | 0.789–0.856 |
| Six villages, heavy unchanged notifications, 1,200 ticks | 33.3 ms | 24.5 ms | 27% | 0.717–0.771 |

Every pair improved by more than 5%; times include the equivalent tracker
queries and equal Java centre-set predicate checks. Variation was 1–7% per JVM (12% in
one churn JVM); 3 of 540 samples overlapped JIT activity. Not whole-server
or villager AI measurements.

The [shared verification note](RUST-PLAYER-DISTANCE.md#verify-and-measure) records
the current suite selection and documentation-review limits. Raw acceptance
results are ignored and are not bundled with this guide.
