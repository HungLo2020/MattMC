# Rust-owned ticket storage

[`TicketStorage`](https://github.com/HungLo2020/MattMC/blob/master/src/main/java/net/minecraft/world/level/TicketStorage.java)
holds every chunk ticket: active and deactivated lists, expiry, forced chunks,
the persisted-ticket filter and notifications to the
[loading](RUST-LOADING-DISTANCE.md) and [simulation](RUST-SIMULATION-DISTANCE.md)
trackers. [`chunk_distance/ticket_storage/`](https://github.com/HungLo2020/MattMC/tree/master/src/main/rust/world/level/chunk_distance/ticket_storage)
owns that state. The Java class is a facade with the original public API and
save codec. [`NativeTicketStorage`](https://github.com/HungLo2020/MattMC/blob/master/src/main/java/net/minecraft/world/level/NativeTicketStorage.java)
holds the downcalls, the `Ticket` bindings and the list views. There is no Java fallback.

Java keeps:

- the `Ticket` objects, so callers get the same instances back;
- the published forced-chunk `LongSet`;
- the listener fields;
- the readiness answers (`ChunkHolder.isReadyForSaving`).

## Preserve these contracts

- **Table order is observable.** It decides listener order, expiry order, save
  order and the forced set's iteration order. [`map.rs`](https://github.com/HungLo2020/MattMC/blob/master/src/main/rust/world/level/chunk_distance/ticket_storage/map.rs)
  is an exact fastutil 8.5.12 `Long2ObjectOpenHashMap` port. It covers initial
  sizes 4 and 16, growth, shrinking on removal, key 0, and the iterator's
  wrapped-key list. Keep it exact; do not swap in a Rust hash map.
- **`removeTicketIf` is a resumable walk.** Cursors are plain indices, as Java's
  iterators were:
  - an entry's `getValue()` is re-read from its table slot;
  - the list iterator keeps `pos`/`last`;
  - a callback may mutate the storage between steps, which gives the original
    positional results (including its exceptions).

  The walk returns to Java only for a readiness question, a Java predicate or a
  Java listener. It must never hold a native borrow across one.
- **Java predicates run in batches.** Rust offers the next tickets as
  `(key, cell)` pairs, up to 256 and sized to recent use. With each it records
  the walk state just after that ticket's `next()`. Java tests them in order and
  stops at the first match, or once a predicate changed the lists (the
  facade's modification count). Rust then restores the recorded state and
  applies the answer.
  - This is exact because the original has no side effects between tickets
    whose predicate is false, including across entries where nothing was
    removed. A batch stops at the end of an entry with removals.
  - Filling never raises a fault early; the live walk raises it in place.
  - `Ticket` objects and fields are read at call time, never prefetched.
  - `-Dmattmc.ticketStorage.batch=N` fixes the size for parity runs.
- **Java notifications in a walk are paired.** A loading notification carries
  the simulation level read at the same moment. Java delivers both, unless the
  first listener changed the lists or the simulation listener is no longer a
  Java one.
- **Readiness is asked in walk order, one ticket at a time,** exactly where
  `canTicketExpire` read it (timed types without `CAN_EXPIRE_IF_UNLOADED`). A
  future completed by an earlier entry's listener is seen by later tickets. Do
  not batch or pre-read it.
- **`ticksLeft` is shared by identity.** A stored `Ticket` binds to one native
  cell ([`slab.rs`](https://github.com/HungLo2020/MattMC/blob/master/src/main/rust/world/level/chunk_distance/ticket_storage/slab.rs)).
  - Every entry, every storage and the Java object read and write that cell:
    Java reads it directly at its address, Rust decrements it in the purge.
  - The cell lives as long as the `Ticket` object, and its Java cleaner frees
    it once the ticket is unreachable. A cell is therefore never reused while
    anything can still read it, on any thread, and tickets may move between
    threads as before.
  - While storage entries refer to the cell, `Cells` pins the ticket strongly,
    as the lists held it. When the last entry goes, the cell is queued, and a
    drain under `Cells.LOCK` unpins it to a weak reference if it is still
    unreferenced. Every re-store re-pins under the same lock. Walks drain once,
    at their end.
  - A ticket is used by one thread at a time, as its field was.
- **Production trackers are notified natively.** A tracker passes
  `TicketChunkDistance.listener(...)`, which carries its graph. The storage then
  calls the graph under the graph's lock, reading its own current level.
  - Any other listener is called from Java.
  - Native delivery stops at the first Java listener, and Java delivers the
    rest in the original order. Removal levels are read from the removed-from
    list when each notification is delivered.
- **Locking.** Each graph has one lock (`SharedTicketDistance`), taken by every
  Java call and by storage notifications. Each storage has its own lock. The
  order is storage → graph. Neither lock is held while Java runs, so callbacks
  re-enter freely. The Java `ReentrantLock` was removed: no path ever re-entered it.
- **The forced set is published by Java.**
  - Adding a `FORCED` ticket adds to the same set object.
  - Removing one rebuilds a new set in table order (`getAllChunksWithTicketThat`).
- **`getTickets` returns a live view** of the native list. It reads and
  writes in place (`add`/`set`/`remove`, bypassing listeners and the forced set,
  as writes to the original `ObjectArrayList` did) and keeps its identity while
  the list lives.
  - A list leaves the storage only when emptied, through `removeTicket` or a
    walk. From then on, a retained view is that detached list: it starts empty,
    accepts writes and no longer affects the storage.
  - A later ticket at the chunk gets a new list and view.
  - Absent chunks return `List.of()`, as before.
  - Writing directly to a list breaks the trackers' ticket-level mirror,
    exactly as it bypassed the original's notifications. No production caller
    writes to these lists.
- **`activateAllDeactivatedTickets` iterates the live deactivated map**
  (walk kind `Activate`). A listener that deactivates or reactivates during
  activation sees and causes the original positional results, including
  exceptions.
- **A Java destination map** in `removeTicketIf` gets lists of default
  capacity; capacity is not observable.

## Verify and measure

```bash
python3 DevUtils/tests/world/tickets/VerifyTicketStorage.py --java-home "$JAVA_HOME" \
  --control-ref <pre-change commit> --output build/ticket-storage-diff [--benchmark]
cargo test --release --lib ticket_storage   # from src/main/rust, or ./gradlew testRustNative
./gradlew test -PmattmcRustProfile=release --tests 'net.minecraft.server.level.Native*ChunkTrackerTest'
python3 DevUtils/tests/world/tickets/VerifyTicketStorageServer.py --java "$JAVA_HOME/bin/java" \
  --classpath-file main-classpath.txt --native-dir build/rust/native --output build/ticket-server \
  [--class-overlay <pre-change classes>]
```

[`VerifyTicketStorageServer.py`](https://github.com/HungLo2020/MattMC/blob/master/DevUtils/tests/world/tickets/VerifyTicketStorageServer.py)
runs a disposable dedicated server. It forceloads chunks in two dimensions,
removes one, then saves and restarts twice. Console assertions check which
chunks are loaded. Run it on the candidate and on a pre-change overlay; both
must pass.

The [driver](https://github.com/HungLo2020/MattMC/blob/master/DevUtils/tests/world/tickets/VerifyTicketStorage.java)
runs the real `TicketStorage` on Frozen, on the pre-change sources compiled over
the candidate classpath, and on the candidate, and requires identical trace digests.

- **Operations:** adds (new, shared instance, same type/level re-add, radius),
  removals, forced toggles, purge, close/reopen, level replacement, codec
  save/load and Java `removeTicketIf` predicates. The predicates log each call,
  change later shared tickets, mutate the lists and throw.
- **Holders:** a stub `ChunkMap` serves holders whose save futures the driver
  controls. A listener completes some of them mid-walk.
- **Callbacks:** a listener sometimes adds, removes, deactivates or reactivates
  mid-walk or mid-activation; the exception class and resulting state must match.
- **Views and a second storage:** retained `getTickets` views (also written
  through) persist across list removal and re-creation, and a second storage
  shares Ticket objects.
- **Observed each step:**
  - listener calls with notification-time level reads;
  - lists, ticket identity and retained views;
  - `ticksLeft` and debug strings;
  - forced-set identity and order, and replaced sets left unchanged;
  - keep-active.
- **Tracker seeds:** seeds 9–12 attach the real `SimulationChunkTracker` (natively
  notified) next to a Java loading listener, and trace its levels. They skip
  direct list writes, which the tracker mirror is not specified for.
- **Large seeds:** seeds 13–14 hold 700 tickets, so predicate walks cross batch
  boundaries.
- **Fixed batch sizes:** the runner also runs the candidate at sizes 1 and 256
  (`--batch-capacities`); every trace must match.

`--benchmark` adds two workloads and checks that all three sides do the same
work (checksum):

- **Production-listener path:** server-shaped churn (portal, ender-pearl,
  spawn-search, forced, dragon and unknown tickets, untimed removals and
  `purgeStaleTickets` against holders), driving a real `DistanceManager`'s
  trackers with production listeners.
- **Custom-listener path:** the original S0006 churn baseline. Java listeners
  and a Java `removeTicketIf` predicate make every notification and predicate
  a Java call.

The tracker parity tests drive real `DistanceManager`s, so they cover direct
native notification against the pinned Java trackers.

## Measurements

The implementation author recorded these on 2026-10-15, using
`VerifyTicketStorage.py --benchmark` on a release build with JDK 25. Each side
ran 3 alternating rounds of 10 samples in fresh JVMs, and every side did the
same work (equal checksums). The run measured the published candidate on
master `fb7f3fa0` (source pin `9abf0fcf…`, native `3c779238…`).

| Workload (median per run) | Frozen | Before this change | Native storage |
|---|---:|---:|---:|
| Production listeners: server-shaped churn, real trackers | 329.0 ms | 248.5 ms | 256.4 ms |
| Custom listeners: Java listeners and a Java `removeTicketIf` predicate | 13.21 ms | 13.28 ms | 40.69 ms |

**Production listeners** run 22% faster than Frozen, mostly from the earlier
native graphs, and are about neutral against the code before this change.

**Custom listeners** run about 3.1× slower (2.9× in the run the owner reviewed:
38.34 against 13.05 ms). Every Java listener call or
predicate match is a native step, where the original made an in-JVM call. Each
newly stored ticket also registers a cleaner.
- Phases on the same workload, Frozen against native, measured before the
  final lifetime protocol: adds 5.2 against 6.5 ms; removals 3.7 against
  4.0 ms; the predicate walk 13 against 34 ms.
- No production code takes this path:
  - the game binds only the native trackers as listeners;
  - its walks use native predicates;
  - nothing calls `removeTicketIf` with a Java predicate.

### Accepted migration debt

This cost exceeds the [interim allowances](../../tooling/MIGRATION-ACCEPTANCE.md#performance-comparisons).
The owner accepted it on 2026-10-15 as temporary migration debt for exactly
this path: custom Java listeners and Java `removeTicketIf` predicates. It is
not a waiver for any other workload or regression.

Resolve it by moving the remaining Java callers onto native listeners or
predicates, or by removing the Java-callback API once nothing uses it. Do not
optimize the callback bridge itself.

## Troubleshooting

- *`UnsatisfiedLinkError … mattmc_ticket_storage_*`:* the harness loads
  `build/rust/native`. Rebuild with `./gradlew buildRustNative -PmattmcRustProfile=release`.
- *Trace differs at a purge:* check readiness order and the walk's table order
  before anything else.
