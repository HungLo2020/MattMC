# Rust-owned simulation distance

[`SimulationChunkTracker`](https://github.com/HungLo2020/MattMC/blob/master/src/main/java/net/minecraft/server/level/SimulationChunkTracker.java)
decides which chunks are within simulation range of a simulating ticket; its
levels drive `inEntityTickingRange`, `inBlockTickingRange` and
`forEachEntityTickingChunk`. [`chunk_distance/`](https://github.com/HungLo2020/MattMC/tree/master/src/main/rust/world/level/chunk_distance)
owns the graph, pending levels, work queue and a mirror of each chunk's lowest
simulating ticket level. [`TicketChunkDistance`](https://github.com/HungLo2020/MattMC/blob/master/src/main/java/net/minecraft/server/level/TicketChunkDistance.java)
is the lifetime/exception adapter; the tracker keeps only its published
`chunks` view. There is no Java graph or fallback.

`TicketStorage` still owns tickets, timeouts and persistence. The
[loading tracker](RUST-LOADING-DISTANCE.md) shares this adapter and graph.

## Preserve these contracts

- The graph is the shared exact port described in
  [player chunk distances](RUST-PLAYER-DISTANCE.md), parameterised as the
  original: 34 levels, levels of 33 and above removed, absent chunks at 33.
- The original graph read `getTicketLevelAt(pos, true)` lazily. Rust reads
  its mirror instead, so the mirror must equal that value whenever the graph
  could read it:
  - The constructor seeds every chunk with active tickets before attaching the
    listener. In production none exist yet, but a later attach must not lose them.
  - Every listener call passes the storage's current level at that chunk, read
    in Java, plus the notified level the graph propagates. `TicketStorage`
    notifies on every active-ticket change while the listener is attached.
  - Mutating active ticket lists without the listener (`getTickets` results,
    replacing the listener) breaks this; neither happens in production.
- Runs replay the original ordered `setLevel` calls into the published map, so
  hot range queries stay in Java and entity-ticking iteration order is unchanged.
- `TicketStorage.activeTicketChunks()` is the only storage change.

## Verify and measure

The [player distance driver](RUST-PLAYER-DISTANCE.md#verify-and-measure)
covers both trackers; `--case player_walk,ticket_churn,distance_changes`
selects the simulation workloads. It regenerates the original tracker oracle
from Git and permits only the documented `TicketStorage` accessor.

Java tests attach the original and the native tracker to separate real
`TicketStorage` instances driven identically and compare ordered `setLevel`
transcripts, published iteration order and sampled levels: all length-four
histories over two chunks and three ticket kinds; seeded churn with load-only,
simulation-only and dual tickets, duplicates, `replaceTicketLevelOfType`,
forced chunks and the close/reactivate `removeTicketIf` cycle at edge
positions; pre-existing tickets read by a neighbour's recompute; and GC
release. Removing the seeding loop fails that pre-existing ticket test. Rust
checks converged levels against a lowest-ticket-plus-distance reference.

Benchmarks feed a real `TicketStorage` per backend: player simulation tickets
moved as DistanceManager does, short-lived ender-pearl/portal tickets and
periodic simulation distance changes, then per-tick `runAllUpdates`, 64
entity-ticking range queries and one entity-ticking chunk iteration.

## Measurements

Full release run, 2026-10-04, with the same setup and run as the
[player distance measurements](RUST-PLAYER-DISTANCE.md#measurements).

| Workload | Java median | Rust median | Paired median reduction | 95% ratio interval |
|---|---:|---:|---:|---:|
| Four walkers with simulation tickets, 600 ticks | 170 ms | 132 ms | 23% | 0.701–0.796 |
| Walkers plus ender-pearl/portal tickets, 600 ticks | 268 ms | 207 ms | 23% | 0.719–0.818 |
| Simulation distance change every 30 ticks, 300 ticks | 104 ms | 58 ms | 44% | 0.545–0.585 |

Every pair improved by more than 5%; times include the shared Java
`TicketStorage` work and entity-ticking queries, which are equal on both sides.
Variation was 2–6% per JVM; 10 of 540 samples overlapped JIT activity. The
previous two-tracker run measured 23–47%. Not whole-server measurements.
