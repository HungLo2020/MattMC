# Rust tick-selection views

Two published level views decide which chunks a server tick visits and in what
order:

- the simulation tracker's view drives block and entity ticking:
  `forEachEntityTickingChunk`, `inEntityTickingRange`, `inBlockTickingRange` and
  `getChunkLevel(pos, true)`;
- the natural-spawn view drives spawn candidates: `getSpawnCandidateChunks`,
  `getNaturalSpawnChunkCount` and `hasPlayersNearby`.

Both are Rust-owned [`level_view`](https://github.com/HungLo2020/MattMC/blob/master/src/main/rust/world/level/chunk_distance/level_view.rs)s.
[`NativeLevelView`](https://github.com/HungLo2020/MattMC/blob/master/src/main/java/net/minecraft/server/level/NativeLevelView.java)
is the bridge. Each graph run applies its ordered `setLevel` changes to its view
natively; no Java map or replay remains:

- the [simulation graph](RUST-SIMULATION-DISTANCE.md) after `run`;
- the [natural-spawn field](RUST-PLAYER-DISTANCE.md) through `runInto`.

Java keeps:

- the holder join, through the visible projection (`visibleChunkMap`) of the
  [holder registry](RUST-CHUNK-HOLDERS.md);
- `getTickingChunk`, `tickChunk`;
- the player-proximity check;
- `Util.shuffle(level.random)`.

The join stays Java on purpose. A native join would hand out holder ids from the
native visible map, and a visible holder's id can be freed and reused before the
next promote. The projection holds the holder objects themselves.

## Preserve these contracts

- **Order is the original's.** Each view is an exact port of fastutil's
  `Long2ByteOpenHashMap`, sharing the map port of
  [ticket storage](RUST-TICKET-STORAGE.md):
  - mixed-hash linear probing and the zero key;
  - growth and shrink;
  - iteration from the zero key, then descending slots.

  Keys are chunk longs, so the order is deterministic. Block-tick order consumes
  `level.random`, and the spawn list is shuffled with it, so order is
  RNG-observable.
- **Walks are live.** A walk is fastutil's `MapIterator` over the current table,
  stepped one entry per downcall:
  - Callbacks between steps, such as a `tickChunk` that forces a chunk load and a
    simulation run, change what later steps see, exactly as with the Java map.
  - When the view shrinks or grows under a walk, the walk throws the original
    `ArrayIndexOutOfBoundsException` or `NullPointerException` (same messages).
  - The entry count is fixed when the walk starts.
- **Locks and lifetime.**
  - Every view call holds the view's lock only while it runs; no lock is held
    while Java runs.
  - A walk holds its view alive (an `Arc`), so it stays usable after its wrapper
    is collected.
  - Point reads take the lock, so off-thread callers of the public range queries
    are safe.
- **Rules as the original trackers:**
  - Simulation stores levels below 33; absent is 33.
  - Natural spawn stores levels up to its maximum distance (8); absent is the
    maximum + 2.

## Verify and measure

```bash
python3 DevUtils/tests/world/tickselection/VerifyTickSelection.py --java-home "$JAVA_HOME" \
  --control-ref <pre-change commit> --output build/tick-selection-diff [--benchmark]
./gradlew test --tests net.minecraft.server.level.NativeLevelViewTest \
  --tests net.minecraft.server.level.NativeSimulationChunkTrackerTest
cargo test --release --lib level_view   # from src/main/rust
```

- [`VerifyTickSelection`](https://github.com/HungLo2020/MattMC/blob/master/DevUtils/tests/world/tickselection/VerifyTickSelection.java)
  drives a real `DistanceManager` and `TicketStorage` on Frozen, the pre-change
  sources and the candidate. The inputs are seeded tickets, headless stub players
  (not a client), simulation distance changes and runs. All three traces must
  match. It records:
  - both walks in order;
  - the seeded shuffle and its final RNG state;
  - the spawn count and point reads;
  - reentrant walks and walks that throw partway.

  It also runs an off-thread point reader against the candidate.
- [`NativeLevelViewTest`](https://github.com/HungLo2020/MattMC/blob/master/src/test/java/net/minecraft/server/level/NativeLevelViewTest.java)
  runs the same histories on a real fastutil map and on the native view: zero key,
  collisions, growth, shrink, and scripted mutation during walks (including the
  iterator exceptions). It also checks that a walk outlives its wrapper.
- The benchmark times, per step, a simulation run, both walks, the spawn count and
  entity-tick-rate point reads. It's a harness workload, not a whole-server
  measurement.

## Troubleshooting

- *Walk order differs:* compare the view's history of puts and removes (a removal
  can shrink the table) before suspecting the cursor.
- *An iterator exception message differs:* the walk must report the position and
  table length it read, as Java's array access would.
