# Native section reclamation

Chunk-section native objects that are created at a high rate are freed by a
batched phantom-reference reclaimer, not by one automatic FFM arena each.
This covers live block sections and their views, live biome sections and their
views, section counters and DH contributor snapshots.

- Java: [`NativeReclaimer`](https://github.com/HungLo2020/MattMC/blob/master/src/main/java/net/minecraft/world/level/chunk/NativeReclaimer.java)
- Rust: [`reclaim.rs`](https://github.com/HungLo2020/MattMC/blob/master/src/main/rust/world/level/chunk/reclaim.rs)
  (`mattmc_native_release_batch`)

## Why not `Arena.ofAuto()`

An automatic arena per object allocates an implicit session, a shared resource
list, cleanup actions and a registration with the JDK's single
`Common-Cleaner` thread. During Distant Horizons world generation that thread
fell millions of releases behind, which kept gigabytes of native memory and
heap alive and caused ZGC allocation stalls. The reclaimer costs one phantom
reference per object, and its own daemon thread (`MattMC-Native-Reclaimer`)
releases dead objects in batches of up to 1,024 per downcall.

## Rules for owners

- Register the object that every reader of the native memory holds, then derive
  plain global segments (`reinterpret(n).asReadOnly()`) from the pointer. Those
  segments do not keep anything alive. Only the registered referent does.
- Section owners register their owner segment, and callers pin a section by
  holding it. Views register the `View` object itself, and every read through
  a view fences it with `Reference.reachabilityFence`.
  `NativeDhContributors` registers itself, and each column holds and fences it.
- A downcall that receives a raw address taken from a reclaimer-owned segment
  must keep the referent reachable until the call returns.
- Adding a kind needs a code in both `NativeReclaimer` and `reclaim.rs`, and a
  release export that drops exactly one reference. An unknown kind stops the
  batch, and Java logs the leaked count.

## Pressure collections

Reclamation follows GC discovery: ZGC queues a dead referent only after the
generation holding it is collected. Native memory adds no heap pressure, so
without help ZGC can go minutes without the old-generation collection that
finds dead promoted owners. In one DH run about 1M sections were registered
before the first release, and malloc in-use memory reached about 9.5 GB.

So a second thread, `MattMC-Native-Reclaimer-GC`, calls `System.gc()` once
unreleased registrations grow by `mattmc.nativeReclaimer.gcStep` (default
262,144, roughly 400 MB) past the level the previous collection left. It
waits at least 1 second between collections. ZGC runs these collections
concurrently (pauses under 1 ms), and only this thread waits on them. Don't
start the client with `-XX:+DisableExplicitGC`, because that turns these
collections off.

## Keep creation rates down

Pressure collections bound the backlog, but each object still costs a
registration, so the number of objects created matters more than how fast
the drain thread runs. Most remaining sections come from DH's batch generation
(a fresh `ProtoChunk` per generated chunk, borders included) and contributor
snapshots read from DH's database. Two avoidable creation sites were removed:

- `ImposterProtoChunk` no longer allocates 24 placeholder sections. Its section
  reads all go to the wrapped chunk (`getSections`, `getSection`,
  `getNoiseBiome`), so the base `ProtoChunk` is built with null section slots.
- DH's `DhLitWorldGenRegion.getChunk` wrapped a `LevelChunk` in a new imposter
  on every call, including every `getBlockState`. It now keeps one read-only
  imposter per chunk for each region, as vanilla keeps one per chunk holder.

Fresh `ProtoChunk` sections for chunks that DH generates still allocate native
owners up front, because generation writes into them.

## Measured results (2026-10-12)

These are historical results from the laptop, using the small DH fixture
(`PrepareSmallDhSource.py`, radius 32, no DH cache). The runs used the
moving-camera gameplay harness with shaders off and 6,000 measured frames,
and the terrain-drain gate was off for both clients. Peak client memory is
RSS plus swap. The machine swapped during the Current runs, so treat the
timings as indicative.

| Build | Current peak | Frozen peak | Current FPS / p99 | Frozen FPS / p99 |
| --- | --- | --- | --- | --- |
| Before reclamation changes | 23.5 GB | — | — (did not measure) | — |
| Reclaimer and creation-site fixes | 19.3, 16.7 GB | 7.9, 8.4 GB | 216 / 17.9 ms, 159 / 26.5 ms | 136 / 33.3 ms, 138 / 33.3 ms |
| Plus pressure collections | 11.6, 12.1 GB | 7.9, 7.5 GB | 238 / 13.3 ms, 270 / 12.6 ms | 116 / 33.3 ms, 97 / 33.3 ms |

Current still peaked about 4 GB above Frozen. Frozen's runs were shorter
because Current restarts measurement while it waits for DH publication. These
runs don't establish bounded long-session memory, and they don't resolve the
DH startup RSS failure recorded in
[gameplay performance](../../rendering/GAMEPLAY-PERFORMANCE.md).

## Diagnostics

Start the client with `-Dmattmc.dev.nativeReclaimerStats=true` to log
`Native reclaimer stats pending=… released=… registered=…` every 5 seconds.
The registered counts are per kind, in kind-code order. A second line reports
glibc `mallinfo2` totals (`inUseMB` and `freeMB`), which separates live native
memory from allocator fragmentation. If `pending` keeps
growing while `released` stalls, the referents are still reachable or are
waiting for an old-generation collection. Use a class histogram
(`jcmd <pid> GC.class_histogram`) to find what holds them.

## Tests

```bash
cd src/main/rust && cargo test --release --lib reclaim
./gradlew -PmattmcRustProfile=release test parityTest -x testRustNative --tests 'net.minecraft.world.*'
```
