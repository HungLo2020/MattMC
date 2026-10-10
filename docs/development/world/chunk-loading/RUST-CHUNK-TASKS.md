# Rust server chunk-task scheduling

[`world/level/chunk_tasks/`](https://github.com/HungLo2020/MattMC/tree/2003b118dfac30ca501dc135b9de0e2d7e2567fb/src/main/rust/world/level/chunk_tasks)
owns all priority buckets, ordered chunk membership, task-token lists, moves,
release and pop. The ordinary
[`ChunkTaskPriorityQueue`](https://github.com/HungLo2020/MattMC/blob/2003b118dfac30ca501dc135b9de0e2d7e2567fb/src/main/java/net/minecraft/server/level/ChunkTaskPriorityQueue.java)
uses that owner directly. Its Java priority maps and per-chunk pending lists are
removed. Rust also assigns reusable callable slots from the primary task arena.
The temporary Java adapter keeps callable references in those rows;
it does not rebuild the native queue from Java state.

`ChunkTaskDispatcher` still owns its consecutive executor, future completion,
level-change callbacks and execution throttling. General simulation, holders and
task bodies remain Java. This is a queue migration, not a completed server port.

Source ownership and fixture definitions were reviewed at
[`2003b118`](https://github.com/HungLo2020/MattMC/commit/2003b118dfac30ca501dc135b9de0e2d7e2567fb).
The release counts and timing results below are implementation-author reports;
this documentation review did not rerun Java/Rust suites, Frozen histories,
benchmarks or servers. They do not establish current hardware or whole-game
acceptance; [the rendering checkpoint](../../rendering/GOAL-5-STATUS.md) preserves
that separate boundary.

## Preserve the dispatch contract

- Lowest priority level first; chunks retain insertion order within a level.
  Task order within each chunk is FIFO. Moving into an existing destination
  appends source tasks; moving within one level removes and reinserts the chunk.
- `release(false)` retains nonempty task lists. `release(true)` removes the
  chunk at every level. Popped lists are independent mutable Java lists; null
  tasks and repeated callable identity remain valid queue inputs.
- An old priority at or above the level count is a no-op. Negative old levels
  fail before dereferencing the position. An invalid destination is checked only
  when source tasks exist: Frozen removes those tasks before throwing. Native
  removal and callable retirement preserve that completed prefix.
- The original consecutive dispatcher excludes compound mutations. Keep that single-writer
  exclusion through consuming borrowed native output; no callback runs during
  native mutation. The adapter adds no second operation lock. Run popped tasks after returning.
  Atomic native top/pending projections support concurrent diagnostic readers.
  They are independent reads, not a coherent snapshot.
  Each result projects the next callable row and whether the next bounded
  critical call is permitted. The excluded writer consumes those scalar facts;
  it does not reread or reconstruct pending queue state. Larger queues use the
  normal safepoint-capable call. Stale row IDs decline before native mutation.
- Task tokens are CPU identities, never pointers or GPU handles. Private owner/metadata
  addresses stay in the adapter on supported 64-bit targets; the automatic arena
  remains pinned through output consumption. The versioned change symbol rejects
  incompatible older local prototypes before creating an owner. Callable pins
  are retired on pop, release and invalid-destination removal. Slot capacity
  follows peak pending count. Rust retains reusable task and chunk arenas and
  output scratch; moves splice task lists without per-chunk vector allocation.
  Chunk-order links use arena indices, so removal needs one position lookup.
  Native maps retain per-level peak capacities; chunk rows track peak
  simultaneous membership.
  Owners are reclaimed with their automatic CPU arena. One completed numeric
  output batch may remain until the next mutation; it contains no callable or
  world pointers. Sustained memory still requires runtime verification.

## Verify

Set `JAVA_HOME` to a JDK 25 installation and use consistent native release settings:

```sh
CARGO_TARGET_DIR=build/rust/target-tests cargo test --manifest-path src/main/rust/Cargo.toml --locked --profile suite --lib world::level::chunk_tasks::
CARGO_PROFILE_RELEASE_DEBUG=line-tables-only CARGO_PROFILE_RELEASE_STRIP=none ./gradlew -PmattmcRustProfile=release buildRustNative
CARGO_PROFILE_RELEASE_DEBUG=line-tables-only CARGO_PROFILE_RELEASE_STRIP=none ./gradlew -PmattmcRustProfile=release parityTest -x testRustNative --tests net.minecraft.server.level.NativeChunkTasksTest
python3 DevUtils/RunWiki.py check
```

[`GenerateFrozenChunkTasksOracle.java`](https://github.com/HungLo2020/MattMC/blob/2003b118dfac30ca501dc135b9de0e2d7e2567fb/DevUtils/tests/world/chunk/GenerateFrozenChunkTasksOracle.java)
executes the actual untouched Frozen queue and records 37,624 operations in eight
synthetic CPU histories. Five compiled-class pins accompany exact popped task
IDs, work/top projections, pending counts and exception classes. The recorder
does not copy the expected algorithm. The generator checks those Frozen class
hashes; the Java replay reads recorded names/hash shapes and compares queue
answers, rather than launching or rehashing live Frozen classes. Replaying
these histories does not prove
server ticks, chunk generation, streaming performance or whole-game parity.

The 2026-10-10 CPU checkpoint passes 257 Rust world checks, 27 architecture
checks and four focused Java controls. The full Java suites report 1,918 tests,
two skips (1,916 executed) and zero failures/errors across 344 suites; six workers map the exact
release image. The four focused Java controls exercise recorded Frozen
histories, callback reentry, concurrent readers through the consecutive executor,
80 reuse rounds and a 65,536-task output batch. Native rows retire before the
next publication; this is bounded reuse evidence, not sustained-memory acceptance.

The final six alternating pairs use the identical compiled driver, five 15,000-round
warmups per JVM and 15,000 measured rounds. Median Current 249.94 ms versus
Frozen 255.99 ms is 2.4% lower in this supplemental workload. This does not establish
server tick, chunk-generation, streaming, FPS or whole-game performance parity.
The release image is `6f0ea0b6`; receipts are in `build/native-chunk-tasks/`.

Rejected prototypes and every timing run are retained. Per-chunk vectors were
3.48× slower in the initial 1,500-round comparison; a custom lookup table was
1.69× slower in a 15,000-round comparison. The custom table and compact top/count
projections were removed. Native task/chunk arenas, one-call output borrowing,
original writer exclusion, scalar row/call-mode facts, primary-field snapshots
and inlined map helpers remain. ELF inspection confirms the map helpers are
inlined; kernel policy denied `perf`, without configuration changes.

After compiling test classes, supply isolated runtime classpath files to
[`VerifyRustChunkTasks.py`](https://github.com/HungLo2020/MattMC/blob/2003b118dfac30ca501dc135b9de0e2d7e2567fb/DevUtils/tests/server/VerifyRustChunkTasks.py):

```sh
python3 DevUtils/tests/server/VerifyRustChunkTasks.py \
  --java "$JAVA_HOME/bin/java" \
  --frozen-classpath-file /path/to/frozen-runtime-classpath.txt \
  --current-classpath-file /path/to/current-runtime-classpath.txt \
  --native-dir build/rust/native --output build/chunk-task-timing \
  --pairs 6 --rounds 15000 --warmup-rounds 15000
```

Keep builds and other heavy work outside timing runs. Each run uses a new JVM;
its protocol records order, warmup, driver/native pins, commands and checksums
before timing. The output directory must not already exist. A short warmup is
not steady-state evidence. Check real server workloads separately before
accepting overall performance.

The [independent source and fixture review](https://github.com/HungLo2020/MattMC/issues/776#issuecomment-6102692740)
decoded the committed oracle: eight histories of 4,703 operations, 46 levels,
five class-hash pins and 553 recorded `ArrayIndexOutOfBoundsException` outcomes,
ending exactly at EOF. This checks the fixture contents; it does not rerun the
Java/native controls or establish server tick, streaming or FPS performance.
