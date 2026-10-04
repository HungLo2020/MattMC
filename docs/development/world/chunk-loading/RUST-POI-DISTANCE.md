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
Java tests drive the original and the native tracker through PoiManager's
notification protocol and compare published iteration order and levels: all
length-four histories over two sections (changes, unchanged notifications,
silent changes with tag reloads), seeded churn near packed-coordinate wrap and
the `Long.MAX_VALUE` source, and GC release. Removing the reseed fails both
history and churn tests. Rust checks levels against a 3D distance reference.

Benchmarks replay POI notifications (mostly unchanged, as ticket claims are),
centres appearing/disappearing, per-tick runs and `sectionsToVillage` queries.

## Measurements

Full release run, 2026-10-04, with the same setup and run as the
[player distance measurements](RUST-PLAYER-DISTANCE.md#measurements).

| Workload | Java median | Rust median | Paired median reduction | 95% ratio interval |
|---|---:|---:|---:|---:|
| Three villages, occupancy notifications, 1,200 ticks | 27.9 ms | 22.0 ms | 21% | 0.749–0.822 |
| Twelve villages with changing centres, 600 ticks | 101 ms | 85 ms | 16% | 0.789–0.856 |
| Six villages, heavy unchanged notifications, 1,200 ticks | 33.3 ms | 24.5 ms | 27% | 0.717–0.771 |

Every pair improved by more than 5%; times include `sectionsToVillage` queries
and the equal Java village-centre checks. Variation was 1–7% per JVM (12% in
one churn JVM); 3 of 540 samples overlapped JIT activity. Not whole-server
or villager AI measurements.
