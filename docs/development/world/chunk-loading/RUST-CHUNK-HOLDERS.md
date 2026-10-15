# Rust chunk holder registry

[`ChunkMap`](https://github.com/HungLo2020/MattMC/blob/master/src/main/java/net/minecraft/server/level/ChunkMap.java)
decides which chunks have a `ChunkHolder`, when holders are promoted to the
visible map, which holders unload, and which chunks are saved eagerly.
[`chunk_holders/`](https://github.com/HungLo2020/MattMC/tree/master/src/main/rust/world/level/chunk_holders)
owns that structure:

- the updating, visible and pending-unload maps;
- the drop set and the eager-save set;
- the save throttle.

It also makes the scheduling and unload decisions.
[`NativeChunkHolders`](https://github.com/HungLo2020/MattMC/blob/master/src/main/java/net/minecraft/server/level/NativeChunkHolders.java)
is the bridge.

Java keeps:

- the `ChunkHolder` objects (by native id), their futures and status work;
- save IO and the unload queue's runnables;
- `visibleChunkMap`, a projection rebuilt from the native order at each promote.

The holder's futures and `GenerationChunkHolder` are unchanged.

## Preserve these contracts

- **Order is observable.**
  - Visible and updating maps keep `Long2ObjectLinkedOpenHashMap` insertion
    order. Visible order drives ticking, saving and debug iteration.
  - The drop set is an exact fastutil `LongOpenHashSet` (the port shared with
    [ticket storage](RUST-TICKET-STORAGE.md)). `processUnloads` empties it
    through its iterator, so unload order is its hash order.
  - The eager-save set is a linked set walked like its iterator. The next key
    is fixed when the current one is returned, so a key appended after the walk
    passed the old tail waits for the next walk.
- **Scheduling.** `updateChunkScheduling` asks Rust, which updates the drop
  set, pending unloads and updating map, and answers one of:
  - unchanged;
  - set the given holder's level;
  - revive pending holder *n*;
  - construct holder *n*.

  Java constructs a new holder between `schedule` and `commit_new`. If the
  constructor throws, `abort_new` releases the id and nothing is inserted, as
  in the original.
- **Unloading.** The drop pass moves holders to the pending unloads in drop-set
  order. Java then attaches each holder's save-future callback
  (`scheduleUnload`), which only queues a runnable.
  - A queued runnable completes the unload only if that holder is still pending
    at its chunk (`finish_unload`, by identity). Rust then frees the id.
  - A completion left over from an earlier unload of a revived or freed holder
    finds no matching id.
- **Threads.**
  - The light engine, `hasChunk`/`isPositionTicking` and mods read
    `visibleChunkMap` off the main thread. It stays a volatile, immutable
    projection: an old snapshot keeps its holder objects alive.
  - Updating lookups go through Rust's lock. Java resolves the id to a holder
    under the same monitor that frees and reuses ids, so an off-thread reader
    (Distant Horizons' generator) never gets another chunk's holder.
  - Neither lock is held while Java runs.
- **Saving.** `ChunkMap.save`, its failure handling and IOWorker recovery are
  unchanged Java. Only the throttle map (`nextChunkSaveTime`) moved.

## Verify and measure

```bash
python3 DevUtils/tests/world/chunkholders/VerifyChunkHolders.py --java-home "$JAVA_HOME" \
  --control-ref <pre-change commit> --output build/chunk-holders-diff [--benchmark]
python3 DevUtils/tests/world/chunkholders/VerifyChunkHoldersServer.py --java "$JAVA_HOME/bin/java" \
  --classpath-file main-classpath.txt --native-dir build/rust/native --output build/chunk-holders-server \
  [--class-overlay <pre-change classes>] [--inject-readonly-region]
cargo test --release --lib chunk_holders   # from src/main/rust
```

The [differential driver](https://github.com/HungLo2020/MattMC/blob/master/DevUtils/tests/world/chunkholders/VerifyChunkHolders.java)
builds a real `ChunkMap` without its constructor, the pattern of the #818
save-recovery test. It drives:

- the real `TicketStorage`, `ChunkMap.DistanceManager` and loading graph into
  `updateChunkScheduling`, plus promotes;
- holders with injected full chunks and out-of-order save dependencies;
- tickets at the coordinate bound, where holder creation throws;
- `tick` under budgets, and `saveAllChunks` with and without flushing;
- the real `ChunkMap.save`, with writes completed or failed by the driver and
  some snapshots failing synchronously.

It observes only through `ChunkMap` methods, so Frozen, the pre-change sources
and the candidate run the same driver; all three traces must match. An
off-thread reader checks every visible and updating lookup for the right
chunk (updating only on the native registry: the original map is unsafe to
read off-thread).

The live driver uses a disposable flat world. It covers:

- forceload, write, release and unload, then reload;
- release and re-add (revival);
- `save-all`, `save-all flush` and restart persistence.

Already-open region files ignore permissions, so live write-failure injection
is not available, and the live driver has no failed-save gate.
`--inject-readonly-region` is an inconclusive experiment: with a read-only
region directory, the native region storage fails to *open* missing region
files, for loads too, and the watchdog stops the server. The pre-change overlay
does the same. Failed saves are covered by the differential's real
`ChunkMap.save` failures (synchronous and asynchronous) and by the #818 IOWorker
recovery tests.

## Troubleshooting

- *Trace differs in visible order:* compare promote timing and the updating
  map's insertion order first.
- *Unload order differs:* check the drop set's capacity and iterator removal
  against the fastutil port.
