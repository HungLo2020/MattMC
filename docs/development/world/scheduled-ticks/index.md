# Scheduled block and fluid ticks

**2026-10-14: Rust owns the per-chunk scheduled-tick queues, their dedup keys,
the container registry and the collection heap that orders containers.** Java
still makes the chunk lifecycle decisions and keeps the `nextTickForContainer`
map, the eligibility scan, the tick objects, the callbacks and the pending
saved-tick list. Goal 3 is not complete.

## Where the code is

- [`world/level/scheduled_ticks`](https://github.com/HungLo2020/MattMC/blob/master/src/main/rust/world/level/scheduled_ticks/mod.rs)
  has the per-chunk queue, the drain loop and the registry.
- [`NativeChunkTicks`](https://github.com/HungLo2020/MattMC/blob/master/src/main/java/net/minecraft/world/ticks/NativeChunkTicks.java)
  is the Java bridge.
- [`LevelChunkTicks`](https://github.com/HungLo2020/MattMC/blob/master/src/main/java/net/minecraft/world/ticks/LevelChunkTicks.java)
  and [`LevelTicks`](https://github.com/HungLo2020/MattMC/blob/master/src/main/java/net/minecraft/world/ticks/LevelTicks.java)
  delegate to it.

`ProtoChunkTicks`, world generation and the save codecs are unchanged.

## Contracts to preserve

- **Exact Java queue port.** The chunk queue is an exact port of
  `java.util.PriorityQueue`'s array heap, ordered by
  `ScheduledTick.DRAIN_ORDER`. The ported pieces are offer, poll, `removeAt`,
  sift with the JDK tie rules, and the iterator's revisit-deque behavior.
  Iteration, `getAll` and saved-tick order expose the array order. Do not
  swap in a different heap.
- **`clearArea` removal.** `clearArea` uses a dedicated native box removal
  (`LevelChunkTicks.removeInBox`). It runs the same iterator sequence as
  `removeIf`, with an inclusive box on full `i32` coordinates. Generic
  `removeIf(Predicate)` still evaluates the Java predicate; do not route other
  predicates to the box path.
- **Same Java objects.** Java keeps each `ScheduledTick` object by entry id, so
  `peek()` returns the same object every time. `LevelTicks.clearArea` relies
  on that identity.
- **Dedup key.** The key is full `i32` x, y and z plus a per-container type
  handle. The type handle is object identity, not a registry id.
- **Pending saved ticks.** They stay a Java list that is neither deduplicated
  nor sorted. `unpack` schedules every one unchecked, with sub-tick orders
  -N to -1.
- **Collect phase.** Collection between containers uses
  `INTRA_TICK_DRAIN_ORDER`: priority, then sub-tick order.
  - The competing head is captured once, and the current container keeps
    draining while its head is not worse than that captured head.
  - Map updates carry the chunk key and trigger tick captured at the moment of
    the update.
  - The heap persists until `cleanupAfterTick`. After a throwing callback it
    keeps duplicate slots, exactly as the original does.
- **Original crash kept.** When a retained duplicate has been drained empty,
  the original throws a `NullPointerException` on the next collect. The native
  path applies the completed prefix and then throws the same exception, with
  no rollback.
- **Container lifetime.** Containers are reference-counted on the Rust side.
  A registry binding is per key and per registry. A slot stays alive while it
  is registered or a heap entry refers to it. Freed handles are held back
  until the next collect, so a handle is never reused while an unconsumed
  output still names it.
- **Threads.** Every native call takes an atomic in-use guard. A call that
  overlaps another fails with `ConcurrentModificationException` instead of
  racing. The registry also rejects any call made while it is still consuming
  a collect's outputs.

## Verify

```sh
CARGO_TARGET_DIR=build/rust/target cargo test --locked \
  --manifest-path src/main/rust/Cargo.toml --profile suite --lib world::level::scheduled_ticks
./gradlew -PmattmcRustProfile=release test --tests '*NativeChunkTicksTest'
python3 DevUtils/tests/world/ticks/VerifyChunkTicks.py \
  --java-home /path/to/jdk25 --output /new/dir --control-ref <pre-change-commit> [--benchmark]
python3 DevUtils/tests/world/ticks/VerifyChunkTicksServer.py --java /path/to/java \
  --classpath-file main-classpath.txt --native-dir build/rust/native --output /new/dir [--class-overlay DIR]
```

**`NativeChunkTicksTest`** runs random operations against an unmodified copy of
the original `LevelChunkTicks` and compares identities, orders, predicate
visits, exceptions and saved rows.

**`VerifyChunkTicks.py`** runs the actual `LevelTicks` on Frozen, the
pre-change classes and the candidate, and compares traces. Each run is 10
seeds:

- budgets 0, 1, 7 and 200, with comparator ties across chunks;
- eligibility changes, plus `clearArea` and rescheduling from inside callbacks;
- unloading and reloading chunks, and replacing a chunk's tick container
  without removing the old one first;
- one container registered under two keys, and a second `LevelTicks` sharing a
  container;
- throwing callbacks, which end at the original's own crash.

**`VerifyChunkTicksServer.py`** checks on a real dedicated server that pending
sand and water ticks survive a save and reopen.

## Troubleshooting

- *"Scheduled tick container queued without a head"*: this is the original
  game's behavior after a callback threw during a tick, not a migration bug.
  A server crash report follows.
- *"Concurrent native chunk tick access"*: two threads used one container or
  registry at the same time. Find the second caller.

Performance measurements and their explanation are on
[Scheduled tick performance](PERFORMANCE.md).
