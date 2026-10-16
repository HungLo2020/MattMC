# Rust chunk holder registry

[`ChunkMap`](https://github.com/HungLo2020/MattMC/blob/master/src/main/java/net/minecraft/server/level/ChunkMap.java)
decides which chunks have a `ChunkHolder`, when holders are promoted to the
visible map, which holders unload, and which chunks are saved eagerly.
[`chunk_holders/`](https://github.com/HungLo2020/MattMC/tree/master/src/main/rust/world/level/chunk_holders)
owns that structure:

- the updating, visible and pending-unload maps;
- the drop set and the eager-save set;
- the save throttle;
- each holder's level state: its ticket level, the level its futures last
  reflected, and whether it was accessible since the last save.

The unpublished [generation integration](RUST-CHUNK-GENERATION.md) extends this
owner through request admission, stable visible snapshots and holder readiness.
Its guide records the current validation limits.

It also makes the scheduling and unload decisions, applies the loading graph's
level changes to holders directly, and decides each holder's status
transition.
[`NativeChunkHolders`](https://github.com/HungLo2020/MattMC/blob/master/src/main/java/net/minecraft/server/level/NativeChunkHolders.java)
is the bridge.

Java keeps:

- the opaque `ChunkHolder` objects (by native id) and actual future objects;
- the effects of each status transition: preparing and unloading futures,
  promotion and demotion listeners, save dependencies and the queue-level
  listeners;
- `DistanceManager.chunksToUpdateFutures` and its two passes (see below);
- save IO and the unload queue's runnables;
- opaque bindings for each native promoted visible snapshot; actual map membership
  and order remain native.

Generation control is being integrated with the separate
[retained chunk-generation owner](RUST-CHUNK-GENERATION.md). Actual future
objects and engine effects remain Java; that guide distinguishes the current
prototype from the remaining integration and verification work.

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
- **Loading hand-off.** `LoadingChunkTracker.runDistanceUpdates` runs the native
  loading graph, then `ChunkMap.applyLoadingLevels`. Rust takes the graph's
  changes and applies them in replay order: it compares each change with the
  holder's native level, schedules the holder and sets its level. Java receives
  records to construct holders and add each holder to
  `chunksToUpdateFutures`.
  - Records stop after each construction. If the constructor throws, the rest of
    that run's changes are discarded, as the original replay ended there.
  - Other `DistanceManager` subclasses (tests) keep the Java replay through
    `updateChunkScheduling`.
- **Status transitions.** `ChunkHolder.updateFutures` asks Rust for the
  transition from the old level to the current one, then runs the effects in the
  original order.
  - Rust marks the holder accessible before the effects.
  - The old level is committed only after the effects and the level listener.
    If an effect throws, the next pass decides the same transition again.
  - Standalone and detached holders retain their own native incarnation, using
    the same controller and actual future bindings as registered holders.
- **`chunksToUpdateFutures` stays Java.** It is a `ReferenceOpenHashSet`, so its
  traversal follows identity hashes. It is kept as an explicit ordering
  adapter, with both passes: every `updateHighestAllowedStatus`, then every
  `updateFutures`. A throw in either pass keeps the whole set, so the next run
  processes every holder again, including the ones already done.
- **Unloading.** The drop pass moves holders to the pending unloads in drop-set
  order. Java then attaches each holder's save-future callback
  (`scheduleUnload`), which only queues a runnable.
  - A queued runnable completes the unload only if that holder is still pending
    at its chunk (`finish_unload`, by identity). Rust then frees the id.
  - A completion left over from an earlier unload of a revived or freed holder
    finds no matching id.
  - Freeing the id leaves the holder's own native incarnation intact. A
    reference can outlive the unload, for example in a pending set that a
    throwing pass kept, and the id may already belong to another holder.
- **Threads.**
  - The light engine, `hasChunk`/`isPositionTicking` and mods read
    `visibleChunkMap` off the main thread. Its volatile native view retains
    exact holder bindings from promotion; old snapshots keep their original
    holders alive across subsequent promotions and registry id reuse.
    Native preparation does not consume the pending change. Java allocates and
    adopts every binding before committing the native view, so allocation failure
    retains the previous view and permits a retry.
  - Updating lookups go through Rust's lock. Java resolves the id to a holder
    under the same monitor that frees and reuses ids, so an off-thread reader
    (Distant Horizons' generator) never gets another chunk's holder.
  - Neither lock is held while Java runs. Taking the loading changes copies
    them under the graph's lock, then applies them under the registry's; the
    two locks never nest.
  - `getTicketLevel` and queue/accessibility reads use the holder's fenced native
    incarnation. The registry updates that same state after attachment; its
    constructor reservation ceases to be active state. Detaching only removes
    registry membership. Actual ready/save/send CFs have native-selected opaque
    bindings, with no Java level or future-membership mirror.
- **Native lifetime.** Create the auto arena before allocating the registry.
  Constructor rollback and arena cleanup share one handle-only action that
  atomically releases once. JDK 25 can register `reinterpret` cleanup before
  allocating the returned segment, so independent manual release is unsafe.
  The cleanup action must not retain the registry, segment or arena. The focused
  generation lifetime checks cover modeled failure ordering and a real
  registry/holder cycle with the same attached native generation Arc.
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
- the real `runAllUpdates`, with prepare futures that are complete, complete
  later (with a chunk, unloaded, or failed) or throw;
- a full-status listener that reenters, adding a ticket and running the loading
  graph inside a pass;
- after a holder constructor throws, the updating holders left around both
  coordinate-bound keys, then a retry;
- a fixed scenario before the random steps: a pass throws while the update set
  holds a dropping holder, that holder's unload completes, new holders take the
  freed ids, and the retry pass runs the old holder's transition. On the native
  side the driver also prints whether the id was reused (`SCENARIO`, outside the
  digest);
- holders with injected full chunks and out-of-order save dependencies;
- tickets at the coordinate bound, where holder creation throws;
- `tick` under budgets, and `saveAllChunks` with and without flushing;
- the real `ChunkMap.save`, with writes completed or failed by the driver and
  some snapshots failing synchronously.

It observes only through `ChunkMap` and public holder methods, so Frozen, the
pre-change sources and the candidate run the same driver. It records:

- per-holder level, accessibility, queue level, full status and future identity;
- prepare, listener and queue-level effects in order;
- the pending set in traversal order.

Each side runs in two modes, and all three sides must match in each:

- **Exact** (`-XX:hashCode=2`): every identity hash is equal, so the set's
  traversal is the same on every JVM. The ordered traces must match, including
  throws and reentry.
- **Hash** (ordinary identity hashes): the driver records each add with its hash
  and replays them through its own model of fastutil's table. Every pass must
  run in the predicted order. Stubs decide by chunk rather than call order,
  prepares complete at once, and nothing reenters or throws inside a pass. A
  pending future completed by an unloading pass would release save dependencies
  in pass order. So the traces still compare across JVMs, and pending futures
  are covered by exact mode. An
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
- *Hash-mode oracle mismatch:* something other than the passes added to or
  iterated `chunksToUpdateFutures`, or the model no longer matches the fastutil
  version. Compare `TableModel` with `ReferenceOpenHashSet`.
- *A retained holder reads another chunk's state:* check that every consumer uses
  its actual incarnation rather than looking up a reusable registry id. Loading
  and direct level updates must reach that same native owner.
