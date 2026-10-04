# Rust-owned player chunk distances

[`DistanceManager`](https://github.com/HungLo2020/MattMC/blob/master/src/main/java/net/minecraft/server/level/DistanceManager.java)
tracks how far each chunk is from the nearest player chunk twice: the natural
spawn counter (radius 8) and the player-ticket tracker (radius 32).
[`player_distance/`](https://github.com/HungLo2020/MattMC/tree/master/src/main/rust/world/level/player_distance)
owns both graphs: player presence, levels, pending computed levels, work queues
and propagation. [`PlayerChunkDistances`](https://github.com/HungLo2020/MattMC/blob/master/src/main/java/net/minecraft/server/level/PlayerChunkDistances.java)
is the Java lifetime/exception adapter. There is no Java graph or fallback for
these two trackers.

Java keeps `playersPerChunk`, each tracker's published `chunks` map and every
downstream reaction: spawn candidate iteration, `hasPlayersNearby`, the ticket
tracker's `toUpdate`/view-distance logic and ticket dispatch. Loading and
simulation ticket trackers and POI section distances still run the Java
`DynamicGraphMinFixedPoint` over the native [work queue](../lighting/RUST-PRIORITY-QUEUE.md).

## Preserve these contracts

- Rust is an exact port of `DynamicGraphMinFixedPoint` + `ChunkTracker` +
  `FixedPlayerDistanceChunkTracker`: clamping, the unsigned-255 absence sentinel,
  signed-byte levels, `i32` wrapping coordinates, neighbour loop order and the
  `INVALID_CHUNK_POS` source sentinel. It reuses the native queue's scheduling.
- Player presence is the graph's only source. `addPlayer` calls
  `playerEntered` (both fields `update(pos, 0, true)`); `removePlayer` calls
  `chunkVacated` only when the chunk's player set empties. Change any of those
  call sites together, or presence and the Java map will diverge.
- A run returns that field's `setLevel(pos, level)` calls in their original
  order; Java replays them through the original `setLevel`/`onLevelChange`. Map
  layout, spawn-candidate iteration order and `toUpdate` contents therefore
  match the original. Nothing in Java reads levels during propagation, so
  replaying after the run is equivalent. Sinks must not reenter the adapter.
- `runAllUpdates` skips the downcall when the last native result reported no
  queued work for that field, as the original returned on an empty queue.
- Handles follow the queue's lifetime rules: an auto arena owns release, every
  downcall fences the owner, and a lock serializes access. Only `drain`, a
  bounded copy into a heap array, is bound `critical(true)`. Allocation is
  fallible and surfaces as `OutOfMemoryError`; native memory use differs from Java.

The architecture boundary test matches `ash::`/`glow::` as plain substrings, so
this module avoids `std::hash::` paths and uses its own
[position map](https://github.com/HungLo2020/MattMC/blob/master/src/main/rust/world/level/player_distance/position_map.rs).

## Verify and measure

```sh
python3 DevUtils/tests/server/VerifyRustPlayerChunkDistances.py --parity-only
python3 DevUtils/tests/server/VerifyRustPlayerChunkDistances.py --forks 3 --cpu 5 --background-cpus 0,1 \
  --output build/player-chunk-distance-migration/acceptance
```

The driver regenerates the original tracker oracle from Git
(`player_distance_oracle.py`), checks it and the pinned original graph/queue
oracles, and permits only the documented production edits to `DistanceManager`.
Java tests compare ordered `onLevelChange` transcripts, published map iteration
order and sampled levels after every run: all length-four histories of two
players over three chunks, seeded walks/teleports around chunk 0,0, coordinate
wrap and the `INVALID_CHUNK_POS` neighbourhood, shared chunks, constructor
validation and GC release. Rust tests check converged levels against a
brute-force Chebyshev reference, budgets and the C ABI. A neighbour-order
mutation that leaves converged levels unchanged fails three Java tests.
Finite coverage is regression evidence, not proof of every history.

Each JVM times one backend through the production adapter: DistanceManager
player bookkeeping, ChunkMap's remove-then-add move order (including vertical
section moves), per-tick `runAllUpdates` for both fields, ticket `toUpdate`
collection and 16 spawn-range level queries per tick. Construction is excluded
equally. Ticket dispatch, chunk loading and whole-game frame time are not measured.

## Measurements

Full release run, 2026-10-04: three alternating JVM pairs per workload on an
i7-10750H laptop (CPU 5 measured, CPUs 0/1 for JVM workers), at least 15 s of
warmup and 30 samples per JVM. Outputs and input traces matched across
backends and JVMs.

| Workload | Java median | Rust median | Paired median reduction | 95% ratio interval |
|---|---:|---:|---:|---:|
| Single walker, 1,200 ticks | 819 ms | 525 ms | 36% | 0.605–0.698 |
| Eight walkers, 300 ticks | 596 ms | 407 ms | 32% | 0.635–0.734 |
| Four teleporting players, 300 ticks | 1,448 ms | 996 ms | 31% | 0.636–0.742 |

Every pair improved by more than 5%, and thread CPU time agreed. Laptop
variation was 5–8% per JVM; 15 of 540 samples overlapped JIT activity (recorded,
not excluded). These are subsystem workloads through the production adapter,
not whole-server tick or chunk-loading measurements. Raw rounds, hashes and
metadata are in `build/player-chunk-distance-migration/acceptance/results.json`.
