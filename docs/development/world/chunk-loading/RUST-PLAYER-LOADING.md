# Player-loading policy and admission

Player-loading decisions and admission run in
[`player_loading.rs`](https://github.com/HungLo2020/MattMC/blob/master/src/main/rust/world/level/chunk_distance/player_loading.rs).
This does not complete Goal 3 or move the generic executor into Rust.

## Ownership and entry points

`ChunkMap.updatePlayerStatus`, `move` and `setServerViewDistance` feed
`DistanceManager`. The existing native player-distance graph applies its
`PLAYER_TICKETS` changes directly to the player-loading owner. Rust owns:

- the published player-ticket levels and changed-key set;
- view-distance membership and entering/leaving decisions;
- dispatched queue levels, including their synchronized cross-thread reads;
- readiness-release candidates and their live walk;
- the throttling dispatcher's distinct in-flight positions and admission limit.

The ordinary production limit remains four chunk positions. Completing a task
batch does **not** release a position: the entity-ticking readiness future or
the original cancellation path does that.

[`NativePlayerLoading`](https://github.com/HungLo2020/MattMC/blob/master/src/main/java/net/minecraft/server/level/NativePlayerLoading.java)
adapts lifetimes and numeric effects. Java retains the actual players, Ticket
objects, holder references, futures, executor mechanics and effectful storage
calls. The [ticket storage](RUST-TICKET-STORAGE.md) and
[task queue](RUST-CHUNK-TASKS.md) remain their existing native owners. The
unthrottled worldgen and lighting dispatchers keep their existing behavior.

## Constraints when changing the policy

- Preserve fastutil long-key table and iteration order, including zero keys,
  growth, shrink and live mutation. The owner reuses the exact ticket-map port;
  replacing its sets with unordered Rust sets changes dispatch order.
- A view-distance scan commits its new distance **after** the final effect.
  A callback or executor can reenter before then. A throwing scan does not
  commit the new distance or undo its completed effects.
- A resort reads the queued level when its delayed supplier runs and updates
  that level only after the queue move succeeds. The captured submission
  priority and the later admission-time membership check are separate inputs.
- A ticket-admission callback rechecks the current level and view distance.
  Adding the release candidate follows successful ticket insertion; do not
  pre-add it, batch future observations or turn a failed effect into a retry.
- Readiness candidates are processed only after the original holder-update
  branch permits it. Test for the actual `PLAYER_LOADING` type identity at that
  point. Clear the candidate set only after every attachment completes.
- A normal unsuccessful `ChunkResult` still executes `thenAccept`; an
  exceptional or cancelled future does not. A missing holder throws before
  clearing candidates. Preserve these original failure paths.
- Each invocation has its own live cursor, continuation and numeric output.
  Native guards end before Java runs. Nested callbacks use independent walks;
  safe locks protect queued levels without speculative lock-free reads.
- Walks retain their native owner through an `Arc` and close in Java `finally`
  blocks. A walk belongs to its creating thread; closed or foreign-thread access
  fails before touching its native invocation storage. Queue-level scalar calls
  remain safe across executor threads. Scheduled effects retain their actual objects. Never carry recyclable
  holder ids through a future or filter an old key-based release by an invented
  work-generation rule.

## Verification

The comparison drivers currently target Linux x86-64 with JDK 25 and the pinned
Frozen checkout at `../MattMC_JavaPerfTesting/MattMC`. Build the candidate before
comparing classpaths:

```sh
./gradlew -PmattmcRustProfile=release classes
./gradlew -PmattmcRustProfile=release test --tests '*NativePlayerLoadingTest'
CARGO_TARGET_DIR=build/rust/target cargo test --locked \
  --manifest-path src/main/rust/Cargo.toml --profile suite --lib player_loading \
  -- --test-threads=1
python3 DevUtils/tests/world/playerloading/VerifyPlayerLoading.py \
  --java-home /path/to/jdk25 --control-ref <pre-change-commit> \
  --frozen-classpath-file /path/to/frozen-main-and-test-dependencies.txt \
  --current-classpath-file /path/to/current-main-and-test-dependencies.txt \
  --output build/player-loading/<new-run>
```

Classpath files contain one absolute, platform-separated classpath line, with
main classes/resources and test dependency jars. Build Frozen separately and
keep its tracked tree unchanged. The driver compiles one identical observer
against Frozen, the actual pre-change sources and Current. It executes real
`DistanceManager`, ticket storage and throttling dispatch, using stepped
executors, fixture holder callbacks and real controlled futures. It records
ordered enqueues, ticket identities, readiness effects, exceptions and debug
order; equal identity hashes make the holder-pass order comparable.

The deterministic prefix prevents executor-rejection histories from making
ticket-removal coverage vacuous. Explicit cases cover future failures,
cancellation, attachment failures, missing holders, retry, type identity,
executor reentry and inline execution. The queued-stale-admission case captures
the real second main-thread hop, withdraws membership and runs its graph, then
executes that callback before queued cancellation work. These are subsystem checks, **not**
dedicated-server or client evidence. The retained-callback case drops the caller's
fixture references before GC and then completes the actual readiness callback.
The focused bridge test separately requires collection of its Java admission
facade while the native walk remains usable.

Use `VerifyPlayerLoadingServer.py` with the same classpath/control arguments to
run disposable actual dedicated servers. Real `ServerPlayer` instances join
through `PlayerList`; an in-memory packet sink replaces transport only. The
driver checks shared chunks, movement, view distance, spectators, dimensions,
queued disconnects, the admission cap, readiness, unload and saved block/light
effects after reopening. It runs initial and reopen processes on every side.
This is headless server evidence, not network, client or FPS coverage.

Add `--shutdown-pending` to stop the initial server while 81 real FULL requests
have unfinished futures and the chunk map still has work. The observer calls
ordinary `halt` while its main-thread action still owns the queue; the normal
shutdown deactivates tickets, drains work and saves. The reopened process checks
the existing saved block/light effects and completes nine requests in the
pending region. A pending-work assertion, process failure or reopen failure
fails the run; this mode does not compare scheduling-dependent partial saves.

For a later migration, use `--sides frozen,pre,current --pre-runtime <directory>`
with a complete sealed pre-change runtime (classes, resources, native image,
classpath and checksum manifest). Pass `--frozen-native-dir <directory>` for the
image paired with the pinned Frozen classes. This avoids an old-class/new-library
overlay. The runner verifies the complete Current image before/after, checks
the sealed reference and records actual mapped libraries on Linux. Legacy
`control` overlays compile only when that side is explicitly selected.

After sealing a successful oracle result, run the deliberately failing controls
with exclusive ownership of the checkout and build outputs:

```sh
python3 DevUtils/tests/world/playerloading/VerifyPlayerLoadingMutations.py \
  --java-home /path/to/jdk25 \
  --reference-result build/player-loading/<oracle-run>/result.json \
  --output build/player-loading/<new-mutation-run>
```

The driver preserves exact originals, temporarily changes two explicit source
files, and restores and rebuilds them in `finally`. A crash or build failure
does not count as detecting incorrect behavior. A known actual-admission
fixture assertion counts only with a differing sealed Frozen effect prefix;
terminating early alone does not count. Inspect `source_restored` and
`restored_binary_verified` before any other build or publication. This driver
builds the **dev** profile; rebuild release afterward.

`BenchmarkPlayerLoading.py` accepts the same classpath/control arguments and
`--native-profile release`. Keep builds and servers stopped for measurement.
It alternates fresh JVMs for walking, teleports, view changes, delayed readiness
and queued disconnects, with separate warmups and samples. Actual-work checksums
must match. Warmup includes the full observation/reporting loop and requires
12 consecutive compiler-quiet cycles after its configured minimum, with a
default 512-cycle bound. Failure to settle stops the run. Samples with measured
JIT work remain in the raw results but are
excluded from the compiler-quiet timing summary; insufficient quiet samples
in any JVM make a campaign inconclusive. Each JVM receives equal weight in the
summary; paired ratios and raw sample ranges remain available.
Construction, bootstrap and the first complete feedback cycle are reported
separately. A separate instrumented pass
reports queue delay and throughput for the stepped executor fixture, outside
the primary timing windows; it does not measure production server latency.
Apply the project's workload-specific performance limits to measured results;
the driver reports ratios and allowances without granting an exception.

Compare the complete ordered per-index work vectors across backends. A reused
fixture can have deterministic alternating phases; do not require one checksum
for every iteration or sort phases to hide a difference. For historical receipts
from the former single-checksum check, `ReviewPlayerLoadingTiming.py --result
<receipt> --output <new-directory>` reparses the raw samples and checks
source/library/observer identities before applying the ordered comparison. It
preserves the original receipt and changes no measured samples or timings.

Java thread allocation, native policy state/table capacities and whole-process
RSS/swap are separate metrics. Native capacity excludes Arc and allocator
bookkeeping; process memory includes the JVM and all native owners. The focused
churn test checks capacity returns to its initial bound after repeated growth
and removal. A post-measurement GC pass records settled heap, native capacity
and process memory separately and verifies that a collection was observed.
Affected/full suites and all of these gates remain required before
publication; a green wiki build alone does not establish runtime acceptance.

For the older ticket differential, `VerifyTicketStorageCompatibility.py` runs
its unchanged operations with a portable holder-lookup fixture on every side.
It accepts `--java-home`, `--control-ref`, `--output` and
`--original-frozen-log` (the successful original driver's Frozen run). The
adapted Frozen digest must match that original trace. This avoids depending on
the removed private `ChunkMap.updatingChunkMap` field; its generated source diff
and both observer hashes are retained with the result.

## Troubleshooting

- **Native symbol missing:** rebuild `buildRustNative` with the same profile
  used for the Java run; do not mix a new facade with an old native library.
- **A slot remains occupied:** inspect the actual captured readiness future
  and its completion kind before treating task-batch completion as a release.
- **Order differs after reentry:** compare map history and the exact delayed
  effect sequence, including when the scan commits and when queued levels change.
