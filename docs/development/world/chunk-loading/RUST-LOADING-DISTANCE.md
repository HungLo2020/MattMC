# Rust-owned loading ticket distance

[`LoadingChunkTracker`](https://github.com/HungLo2020/MattMC/blob/master/src/main/java/net/minecraft/server/level/LoadingChunkTracker.java)
turns loading tickets into chunk ticket levels, which decide which chunks
`ChunkMap` creates, keeps and unloads. [`chunk_distance/`](https://github.com/HungLo2020/MattMC/tree/master/src/main/rust/world/level/chunk_distance)
owns the graph, its level map, pending levels, work queue and a mirror of each
chunk's lowest loading ticket level. [`TicketChunkDistance`](https://github.com/HungLo2020/MattMC/blob/master/src/main/java/net/minecraft/server/level/TicketChunkDistance.java)
is the lifetime/exception adapter, shared with the [simulation tracker](RUST-SIMULATION-DISTANCE.md).
There is no Java graph or fallback.

`ChunkMap` still owns holders, scheduling and unloading; `TicketStorage` owns tickets.

## Preserve these contracts

- The original graph read each chunk's level from its `ChunkHolder` (or
  `MAX_LEVEL` when absent or queued for drop). After every `setLevel(pos, i)`
  that state reads back `i`, and nothing else changes holder ticket levels,
  so Rust's own level map equals it. This requires the tracker to exist before
  any holder, as `DistanceManager` creates it. If another path ever sets
  holder ticket levels, it must go through the tracker.
- Ticket levels are mirrored exactly as for the simulation tracker: seeded from
  existing tickets, then the storage's current loading level on every listener call.
- A run replays the original ordered `setLevel` calls into chunk scheduling
  (`updateChunkScheduling`, `chunksToUpdateFutures`) after Rust finishes;
  that code never reenters the tracker. `runDistanceUpdates` returns the
  native remaining budget, which `DistanceManager` uses as its processed count.
- Holder creation beyond `ChunkPos.MAX_COORDINATE_VALUE` throws in `setLevel`.
  Rust stops its run at exactly that call, with the same graph state the
  original has when it throws, and Java's replay throws the original exception.
  Production tickets stay inside the world border, so this is a safety net.

## Verify and measure

The [player distance driver](RUST-PLAYER-DISTANCE.md#verify-and-measure) covers
this tracker; `--case loading_walk,loading_group,loading_churn` selects its
workloads. Java tests give the original and the native tracker separate real
`TicketStorage`s and [test DistanceManagers](https://github.com/HungLo2020/MattMC/blob/5218ac875eda9f2c4151ff1a97795f20f5f356cf/src/test/java/net/minecraft/server/level/NativeLoadingChunkTrackerTest.java#L37-L86)
that copy ChunkMap's holder scheduling and holder moves during unloads. They
compare ordered `setLevel` transcripts, remaining budgets or exceptions, holder
levels, drop/unload state and the `chunksToUpdateFutures` collection: all length-four
histories over two chunks (one-node and full runs, unloads), seeded churn with
all ticket kinds, small budgets, replacements, close/reactivate cycles and
unloads at edge positions, and pre-existing tickets. The implementation author
recorded 101 mid-run holder failures compared and reported that removing ticket
seeding failed the pre-existing test; the source asserts that at least one such
failure is exercised. The [GC case](https://github.com/HungLo2020/MattMC/blob/5218ac875eda9f2c4151ff1a97795f20f5f356cf/src/test/java/net/minecraft/server/level/NativeLoadingChunkTrackerTest.java#L290-L303)
creates 1,000 unreachable tracker instances, requests collection and checks a
surviving instance; it does not count released handles or prove bounded memory.

Benchmarks run walking players whose loading tickets shift with an 8-chunk view,
portal/pearl churn, per-tick runs, futures-collection clearing and the fixture's
holder moves during unloads. The [benchmark](https://github.com/HungLo2020/MattMC/blob/5218ac875eda9f2c4151ff1a97795f20f5f356cf/src/test/java/net/minecraft/server/level/LoadingChunkTrackerVerification.java#L23-L125)
uses the same test manager and scheduled ticket removals. It does not run the
real ticket purge, complete ChunkMap lifecycle, chunk generation, storage I/O
or future completion. Construction is excluded from timed rounds.

## Measurements

The implementation author recorded a full release run on 2026-10-04, with the
same setup and run as the
[player distance measurements](RUST-PLAYER-DISTANCE.md#measurements).

| Workload | Java median | Rust median | Paired median reduction | 95% ratio interval |
|---|---:|---:|---:|---:|
| One walker, 8-chunk view, 400 ticks | 165 ms | 85 ms | 48% | 0.510–0.542 |
| Four walkers, 200 ticks | 141 ms | 82 ms | 42% | 0.516–0.609 |
| Two walkers plus portal/pearl churn, 300 ticks | 183 ms | 95 ms | 48% | 0.484–0.532 |

Every pair improved by more than 5%; times include the equal Java ticket storage
and holder scheduling work. Variation was 1–7% per JVM; 4 of 540 samples
overlapped JIT activity. Not whole-server or chunk-generation measurements.

The [shared verification note](RUST-PLAYER-DISTANCE.md#verify-and-measure) records
the current suite selection and documentation-review limits. Raw acceptance
results are ignored and are not bundled with this guide.
