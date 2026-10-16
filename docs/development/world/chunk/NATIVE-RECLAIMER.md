# Native section reclamation

Chunk-section native objects that are created at a high rate are freed by a
batched phantom-reference reclaimer, not by one automatic FFM arena each.
This covers live block and biome sections, their retired view generations,
section counters, DH contributor snapshots (one per column and one batch per
saved sidecar) and the block snapshots the terrain mesher captures.

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
  holding it. A `View` holds that owner segment, so a reachable view keeps the
  owner and its current generation alive; views register nothing. When palette
  growth replaces a generation, the next view call returns the replaced
  generations, registered against the previous view, which readers may still
  hold. Every read through a view fences it with `Reference.reachabilityFence`.
  `NativeDhContributors` registers itself, and each column holds and fences it.
- Light layers register their owner segment, and each light view its metadata
  segment, which terrain meshing passes to Rust and fences. DH heightmaps,
  emitter sources and light fields register their owner (or view) segment;
  native callers fence the holder (`ChunkInput`, the wrapper) across the call.
  `NativeDhColumns` keeps its automatic arena, because column list views alias
  its point slices.
- Chunk block and biome input bindings register their owner segment unless a
  release scope is open. Earlier light-storage maps registered owner and view
  through `registerLightMap`. The S0026/r4 candidate registers the actual map
  root once, with its metadata borrowed from that root; the adapter uses the
  existing kind and registration contract. The paired API remains unchanged.
- A downcall that receives a raw address taken from a reclaimer-owned segment
  must keep the referent reachable until the call returns.
- Adding a kind needs a code in both `NativeReclaimer` and `reclaim.rs`, and a
  release export that drops exactly one reference. Kinds 1–17 are: block
  section, retired block views, biome section, retired biome views, counters,
  contributor snapshot, (7: retired saved contributor batch; saved footprints now decode into their source), block snapshot, light layer,
  light view, DH heightmaps, DH emitter sources, DH light field, chunk inputs,
  chunk biome inputs, light map, light map view. An unknown kind stops the
  batch, and Java logs the leaked count.

## Chunk slabs

`ChunkAccess.replaceMissingSections` creates a chunk's missing fresh sections
through one [`NativeChunkSlab`](https://github.com/HungLo2020/MattMC/blob/master/src/main/java/net/minecraft/world/level/chunk/NativeChunkSlab.java)
call (Rust `world/level/chunk/slab.rs`) when two or more are missing. The slab
holds each section's block owner, biome owner and counters. Java's segments
for all of them carry one automatic arena's scope, so holding any of them keeps
the slab alive, FFM enforces that, and the slab is released once, when none is
reachable. Each item is still an ordinary `Arc` owner, so Rust consumers that
retain one (chunk inputs, residency, stages) outlive the slab safely.

The sections match `new LevelChunkSection(factory)` exactly: single-value
native containers of the factory's defaults, created with the arguments
`adopt` uses, and zeroed counters. A factory or strategy that `adopt` would
decline keeps the one-by-one path. Copies, generation installs and network
reads replace items with ordinary owners. By code inspection, a DH-generated
section used to make about eleven registrations: five when created, two for
the native block install and four for the biome fill. It now makes about two,
plus one slab per chunk. `-Dmattmc.chunk.sectionSlabs=false`
restores the one-by-one path for control runs. `NativeChunkSlab.created()`
counts slabs.

## Release scopes

[`NativeReleaseScope`](https://github.com/HungLo2020/MattMC/blob/master/src/main/java/net/minecraft/world/level/chunk/NativeReleaseScope.java)
frees a short-lived workload's chunk storage explicitly, without waiting for the GC.

- **Binding.** While a scope is open on a thread, these are bound to the scope's
  shared arena instead of the reclaimer:
  - block and biome section owners, their view segments, and counters;
  - chunk slabs;
  - chunk inputs and biome chunk inputs.
- **Closing.** Closing the scope closes the arena and frees them all. A read
  afterwards throws FFM's "already closed" `IllegalStateException`; it never
  reads freed memory.
- **Shared items.** Rust items stay reference counted, so a native consumer that
  retains one keeps it alive.
- **Deferred closes.** If another thread is still inside a call on one of the
  segments, the close is retried by a later scope (`deferredCloses()`).

Each DH batch generation event (`GenerationEvent`, not the internal-server
mode) runs in a scope. Its chunks are event-local copies, and their results are
merged into LOD sources before the event returns. Two kinds of object stay with
the reclaimer: objects created on other threads, such as vanilla's parallel
noise steps, and native DH outputs (columns, heightmaps, lighting fields).
`-Dmattmc.dh.eventReleaseScope=false` turns scopes off. Open a scope only around
work whose chunks provably don't escape it.

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
| Release scopes, direct DH merge, more reclaimer kinds | 10.6, 10.6 GB | 7.1, 8.0 GB | 136 / 34.9 ms, 129 / 36.9 ms | 165 / 20.7 ms, 114 / 33.3 ms |

In the earlier rows Current restarted measurement while it waited for DH
publication (12 and 20 restarts in the run before the last row), so it timed
only quiet stretches. The last row (series `dhsmall6`, 2026-10-12) uses
`dhPublicationDuringMeasurement=false`: neither client restarted, both timed
frames during DH background work, and its timings are the first comparable
pair. Current still peaked about 2.6–3.5 GB above Frozen and swapped
(1.8–2.3 GB); Frozen did not. These
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
