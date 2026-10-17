# Rust light propagation

Ordinary live block sections now use [direct primary-owner capture](RUST-LIGHT-SECTION-INPUTS.md)
instead of an intermediate packed callback payload. Historical measurements
below remain scoped to the source versions they describe.

[`propagation/`](https://github.com/HungLo2020/MattMC/tree/master/src/main/rust/world/level/lighting/propagation)
runs the decrease and increase queues of `LightEngine.runLightUpdates()` for
`BlockLightEngine` and `SkyLightEngine`. Its sky-section kernel also fills skylight above the lowest sources and seeds
the increase queue. The S0026 coordinator candidate consumes that kernel in a
complete stock `SkyLightEngine.propagateLightSources()` operation. The bridge is
`NativeLightPropagation` (lighting package) with `NativeLightBlocks` (chunk
package, for palette data). Ordinary block palettes and packed words now come
directly from their [Rust live owner](../chunk/RUST-LIVE-SECTIONS.md), without
reconstructing a Java palette or word array for the light handoff. Before S0026,
Java owns the surrounding section/node/queued-layer lifecycle, retention,
reconciliation and publication policy.
Canonical section maps and sky metadata now use
[shared Rust snapshots](RUST-LIGHT-MAPS.md). Canonical live light bytes have a
[Rust owner](RUST-LIVE-LAYERS.md); public mutable arrays and subclasses retain
compatibility ownership.

The in-development [per-engine coordinator](RUST-LIGHT-COORDINATOR.md) moves
the remaining stock node/storage/publication policy and whole sky-source section
loop into Rust, and accepts typed propagation results directly. Java still supplies contextual and custom-object
effects. This candidate has focused correctness proof but unresolved complete
costs; the published orchestration description and historical results here
do not establish its acceptance.

Canonical [block-light source production](RUST-BLOCK-LIGHT-SOURCES.md) now
enumerates primary section owners and appends native work directly. Java supplies contextual chunk lookup and custom effects; compatibility chunks
retain callbacks.
Server PRE/propagation/POST batch policy and scheduled admission belong to the
[native threaded coordinator](RUST-THREADED-LIGHTING.md); S0026's per-engine owner
handles the connected stock section and publication policy.

## How a pass works

1. Producers append position/entry pairs to retained Rust pending queues.
   Rust takes those queues directly and runs every decrease, then every
   increase. Original inputs remain in a transaction journal until admission
   succeeds; there is no Java drain buffer or native input copy. See
   [work ownership and verification](RUST-LIGHT-WORK.md).
2. One callback function supplies two separately cached snapshots per section:
   the updating `DataLayer` (a pinned native CPU owner for canonical layers;
   bytes or a lazy default for compatibility layers) and
   `lightOnInSection` on the first layer request, then block states only when
   first needed. The block snapshot contains palette state IDs plus packed
   words, or one state ID per block for chunk kinds the bridge does not model;
   Rust maps state IDs to light types in place. A section needing both
   snapshots makes two callback invocations.
3. Rust writes levels into its own pass copies. S0026's canonical coordinator
   consumes ordered results, selects COW targets and installs native bytes using
   typed owners. Custom or mutable-array targets remain explicit Java effects.
   The legacy bridge without a coordinator receives ordered keys, selects the
   first-write COW target in Java and adds affected-section entries. In both
   paths, earlier installation effects remain committed if a later effect fails.
4. Unsupported input or a failed section callback returns before propagation
   writes are installed. Rust restores the original inputs after any new
   callback additions, matching the former Java refill order. Java runs the
   compatibility loops, which may complete or throw. This is a
   boundary around propagation writes, not a rollback of earlier `checkNode`
   work or callback side effects. Invalid ABI input (`-1`), downcall failures
   and result-transfer failures throw instead of replaying in Java; see the
   [current bridge status handling](https://github.com/HungLo2020/MattMC/blob/4246f4e7bfc1f3ab7862272ebba5f1f38aa16953/src/main/java/net/minecraft/world/level/lighting/NativeLightPropagation.java#L204-L259).
   An installation error does not roll back sections already installed.

The S0026 stock source producer captures bounded source inputs in an owned native
frame. Rust admits the actual typed layers before mutation, then owns the whole
descending section/COW/seed/increase-queue operation. Java obtains the five chunk-source facades and synchronously borrows their
native owners. Rust reads the actual packed payload once per source lock and
derives the seed columns. The old Java array is only a derived compatibility
transport, captured at the same point to preserve reentry. No source lock crosses
a Java effect. Source state, updates and reconstruction are described in
[skylight-source ownership](RUST-SKYLIGHT-SOURCES.md). Every completed
section publishes its queue prefix before later fallible work.

Custom readers, custom storage and mixed or escaped layers use the
[live-source cursor](https://github.com/HungLo2020/MattMC/blob/master/src/main/rust/world/level/lighting/sky_source_policy/mod.rs).
Rust retains the actual work queue and chooses the descending section/cell order,
conditional neighbor reads and writes. Java performs the original virtual
source, storage and layer effects. Each successful write that qualifies as a source appends its
entry directly to the native queue. No native borrow or queue lock crosses a
Java effect, including a reentrant call on the same engine. A failed effect
preserves the committed prefix and releases the invocation's queue lease.

Do not gather and replay a source matrix before a custom `DataLayer.copy()` or
`set()` callback: callbacks can change live source heights. The stock producer
above keeps its separate canonical admission path. The former conditional Java
loop has been removed; the native section kernels remain independently tested.

The portable observers cover nine stock/source-copy/escaped/custom-storage
cases, four additional exception/reentry/queue contracts, and checked exception
identity. Native tests exercise the actual retained queue allocation, abandoned
writes and committed prefixes. Focused correctness evidence does not establish
complete lighting or final performance acceptance; retain each tested image's
receipts separately.

## Constraints when changing this code

- Preserve the original Throwable object from virtual effects, including checked
  exceptions thrown through an unchecked custom signature. Keep the committed
  write/queue prefix when an effect fails.
- Keep FIFO order, the returned work count, entry bits
  (`LightEngine.QueueEntry`) and every `setStoredLevel`, including writes of an
  unchanged value. A lazy layer must allocate exactly when the original would.
- Block properties are light types that Rust derives from the
  [block registry](../../game-model/RUST-BLOCK-REGISTRY.md):
  `max(1, getLightBlock())`, emission, `isEmptyShape` and each direction's
  occlusion face, numbered in state order. Faces are merged only by exact box
  lists. Occlusion is a truth table of `Shapes.faceShapeOccludes`, so never
  approximate geometry.
- Only exact `BlockLightEngine`/`SkyLightEngine` with their vanilla storages
  use Rust queue propagation. `ProtoChunk`, `LevelChunk` and `ImposterProtoChunk` (non-debug)
  sections are read packed. Other `LightChunk`s answer per block through
  `getBlockState`. A null chunk reads as bedrock and blocks outside the build
  height read as air, as in `LightEngine.getState`.
- Rust keeps no pointers into the propagation pass's Java inputs between calls.
  Each engine has its own Rust handle, callback buffer and separate pending
  queue owner. No queue lock spans a section callback. The engine's thread
  owns the pass; the bridge excludes nested native use of that same handle.
  This guard does not establish general concurrent/reentrant engine semantics.
- `-Dmattmc.lighting.javaPropagation=true` keeps the Java loops.
  `NativeLightPropagation.setEnabled` toggles this in tests.

## Verify a change

For contextual source changes, compile `VerifyLightCoordinator.java`,
`VerifyCoordinatorSkySources.java`, `VerifyLiveSkySourceContracts.java`,
`VerifyLiveSkyCheckedFailure.java` and `BenchmarkLiveSkySources.java` from `DevUtils/tests/lighting/coordinator/`
once against the original Frozen classpath. Run those same observer classes
before each tested image on the classpath. `VerifyCoordinatorSkySources` takes
an output directory and `all`; `VerifyLiveSkySourceContracts` takes a trace
file; `VerifyLiveSkyCheckedFailure` takes no arguments. Compare the complete
outputs, and verify the loaded native image.

`BenchmarkLiveSkySources write 4` and `BenchmarkLiveSkySources skip 4` exercise
custom storage, five custom source readers and a custom layer. Each sample
checks source reads, writes, FIFO hash and all 4,096 pixels. Only the complete
producer call is timed; actual queue draining and checks are outside that
window. Retain raw work, JIT/GC, allocation, RSS and swap rows under matched
conditions. This focused screen does not establish whole-lighting performance.

The intermediate custom-source screen on base `3eeb5f02d` plus this cursor
recorded the following medians (microseconds per complete producer call):

| Custom workload | Original Frozen | Published pre-cursor | Cursor candidate |
|---|---:|---:|---:|
| 1,280 source reads, 4,096 writes, 512 FIFO words | 23.336 | 19.809 | 113.833 |
| 256 source reads, no writes or FIFO words | 4.226 | 7.700 | 18.587 |

Work, FIFO hashes and pixels matched; each row had at least 11 JIT/GC-quiet
samples, with no swap. Candidate Java allocation was 240 bytes per call,
versus 72 bytes for the published pre-cursor image. A single write-workload
control restored only the old `SkyLightEngine.class` over the candidate's other
classes and same native library: 20.568 microseconds, 72 Java bytes per call.
This implicates the new cursor path; it does not attribute individual functions
or establish an unavoidable cost. The material latency regression remains open.
The canonical stock producer is unchanged and was not timed by this fixture.
This is intermediate evidence, not complete lighting or final performance
acceptance; retain the separate request-lifecycle latency debt as well.

Start with the [live-layer production tests and Frozen oracle](RUST-LIVE-LAYERS.md#verification)
for current ownership changes. The older driver below also embeds a source-shape
audit from before native live layers; keep its audit verdict separate from the
runtime parity suites it invokes.

```sh
python3 DevUtils/tests/lighting/VerifyRustLightPropagation.py --parity-only
python3 DevUtils/tests/lighting/VerifyRustLightPropagation.py --forks 3
```

The historical driver audits edits to `LightEngine`, `SkyLightEngine` and
`DataLayer` against Git `54611cfc2`; that source-shape audit predates live light
ownership. Do not interpret its rejection of the new storage shape as a
semantic verdict. Use the linked live-layer Frozen oracle and production checks
for current storage changes. The driver also runs `NativeLightPropagationTest` and the Rust
lighting tests. The parity tests light worlds on both routes over the same
chunks and compare all light storage after every pass:

- updating, visible and queued layers, including whether each layer is lazy or
  allocated and which layers are shared between the two maps
- section states, changed and affected sections, sky top sections
- light-update notification membership (sets, not order or duplicate counts)
  and work counts

Worlds:

- block states from 36 saved FULL overworld chunks
  (`src/test/resources/lighting/terrain.json.gz`, extracted by
  `DevUtils/tests/lighting/ExtractLightTerrainCorpus.py`),
  [reconstructed as `ProtoChunk` fixtures](https://github.com/HungLo2020/MattMC/blob/5c02fd8215f4c1dde624dbe3d21a476d38b16708/src/test/java/net/minecraft/world/level/lighting/LightPropagationFixtures.java#L48-L65)
  and relit; the corpus contains no saved light and does not test a region-file
  save/load roundtrip
- noise-filled overworld, amplified, nether and End terrain (sky light over islands above the void)
- sparse empty chunks with floating blocks and pillars, so skylight fills
  unstored sections below stored ones across every chunk border
- chunks served through a generic `LightChunk`
- world limits with missing neighbours

Edits cover torches, glowstone, lava, slabs, stairs, shafts, roofs, emptied
sections, queued section data and chunks with light switched off. Every pass
also checks that layers published before it are unchanged (copy-on-write). Another test checks every registered state
against its table type, and checks every merged face against every other face.

These fixtures exercise the light engines over saved/generated blocks, not a
live loaded-`LevelChunk`/`ImposterProtoChunk` lifecycle or full-server world
generation. They do not establish concurrent/reentrant pass or handle-cleanup
coverage. The benchmark times production calls only. Terrain loading and engine
construction are untimed.

| Case | Workload |
|---|---|
| `terrain` | Initial lighting of the saved corpus in shuffled batches |
| `edits` | 96 self-reverting edits on that lit terrain |
| `nether` | Initial block lighting of 36 noise-filled nether chunks |

Three independent JVM pairs alternate the route order on the same CPUs. Each
case must save at least 5% in every pair and at the upper 95% bootstrap
bound. Results are written to `build/light-propagation-migration/acceptance/`.
Inspect `results.json` → `performance` → each case's `passes` value: the
[driver records a failed performance gate without failing its process](https://github.com/HungLo2020/MattMC/blob/5c02fd8215f4c1dde624dbe3d21a476d38b16708/DevUtils/tests/lighting/VerifyRustLightPropagation.py#L163-L186),
so exit success alone is not performance acceptance.

## Status

The results below are historical: they measured the original slice, when Java
built the light types. The [block registry verification record](../../game-model/BLOCK-REGISTRY-VERIFICATION.md)
separately reports the author's later parity comparison against the slice's
previous code; its full multi-fork hot-path comparison was not completed.

The implementation author recorded release acceptance on 2026-10-06:
Ryzen 5 5600G, Linux x86_64, OpenJDK 25. The author reported eight Java tests
and eleven Rust lighting tests passing with zero differences (1,801 Rust
passes compared). Four of those Rust tests are newly added propagation tests;
eleven is the broader lighting-suite count. The author also reported that
thirteen deliberate semantic mutations were all caught. Each mutation broke one
of: entry bits, comparisons, edge crossings in
each direction, the lowest data section, affected sections, `lightOnInSection`,
occlusion direction, copy-on-write, lazy-layer allocation or seeding.

Median time per workload over three JVM pairs. The last column is the
conservative saving at the upper 95% bound of the time ratio:

| Case | Java | Rust including boundary | Time saved | Conservative saving |
|---|---:|---:|---:|---:|
| `terrain` | 60.1 ms | 38.3 ms | 36.2% | 33.6% |
| `edits` | 32.8 ms | 17.6 ms | 46.3% | 45.1% |
| `nether` | 23.7 ms | 13.5 ms | 43.0% | 32.2% |

Every pair passed: the smallest individual-pair saving was 33.1% (nether).
A few measured rounds overlapped JIT compilation (10, 5 and 2 of the 180
samples per case); medians and bootstrap bounds include them. These are
warmed subsystem timings on this machine and corpus, not whole-game frame or
chunk-generation claims. Finite parity tests cannot enumerate every input.
Raw samples, checksums and source/library hashes are in
`build/light-propagation-migration/acceptance/results.json` (not bundled with
the wiki). The [2026-10-06 maintenance review for #776](https://github.com/HungLo2020/MattMC/issues/776#issuecomment-6025144528)
inspected source and committed fixtures;
it did not rerun the Java/Rust suites, mutations, benchmarks or a live world,
or independently verify those raw acceptance results.
