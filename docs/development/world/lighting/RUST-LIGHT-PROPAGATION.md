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

## Effectful block propagation under development

The bulk section path does not preserve observable custom height-provider calls.
A new observer, `DevUtils/tests/lighting/coordinator/PropagationHeightEffects.java`,
separates a real node check from subsequent queued propagation. Its original
Frozen value case makes 13,504 height calls; preserved `172ef018` and published
`66d9690e` make nine. A checked failure calls the original provider once but
those native paths call it twice through replay. Their selected light values
and work counts match; callback behavior does not. These results predate the
block-node input checkpoint and remain failures.

The local correction is a Rust block-pass policy over the actual pending queue
owner, with Java supplying the original state, opacity, emission, shape and
storage calls. Queue guards end before every effect. Successful writes and
consumed inputs remain committed when a VM call fails; the adapter retains and
rethrows that exact throwable without replaying it. Invocation-local VM state
references serve the original callbacks and clear at their policy boundary.
Unknown contextual getters take this path; the bulk route requires a stock
chunk source and silent admission of its actual chunks, height providers and
sections. The sky path retains its existing implementation.

Compile the observer once against original Frozen with
`VerifyThreadedLighting.java` and `VerifyBlockInputEffects.java`, then run the
same classes on complete preserved and matching Current runtimes. Compare every
`PROPAGATION_HEIGHT` row, including call count, hash, prefix, checked identity,
work, pending state and light values. `PropagationGetterEffects.java` adds fifteen
lookup/state failure and reentry cases across direct, level and threaded entry
points, including complete updating-layer hashes. The published `66d9690e` image
differs in all fifteen traces and in three reentrant lookup layer hashes.

The first local `8341a0b6` plus owned policy runtime matches all three height and
fifteen getter cases, using the unchanged original Frozen observer bytes. Three
native tests also exercise the actual queue/layer, committed failure prefix,
live callback additions and release after consumption or abandonment. These
focused results do not certify a later integration. On the coupled `03f1df23`
image, all eighteen original rows, four real VM queue/arena retirement cases and
the genuine retained-base omission control pass. Full affected and save gates
remain pending for this correction. The first resource screen fails its unchanged warmup
bound: repeated coordinator frame adapters allocate about 6.8 MB per 64-edit
cycle and prevent the required quiet window. Its raw failure remains; no heap,
deadline or quiet criterion was relaxed.

The next local version retains the actual native coordinator binding alongside
the queues. Canonical reads use its live layer owners, and writes run the same
existing COW/write/affected policy directly in Rust. Metadata refresh and pending
VM retirement still finish before the next observable effect. Missing, custom,
escaped or pending-binding inputs keep the original VM calls. Three real native
storage tests and the original eighteen cases plus queue lifetime controls pass
on the matching local `03f1df23` image. Its resource screen completes with eight
quiet samples and matching output: Java allocation falls to 127,398 bytes per
cycle, but latency remains 55.12 ms, about 10.62 times original Frozen. Publication
remains held; this measurement is not a performance acceptance.

The next local slice connects each admitted state's actual primary section to
that consumer. It reuses the node-input admission rules and original contextual
lookup, reads the live owner after the VM borrow ends, and retires that input
before the property consumer or later VM effects. The existing native block
registry supplies opacity, emission, empty-shape and face policy. Mixed/custom
shapes reconstruct only canonical registry facades alongside the original custom
state. Registry-owned states are permanent roots; custom states still clear at
their original VM boundary.

Admission can succeed before `PalettedContainer.onResize()` materializes the
Java storage and invalidates the actual primary owner. The consumer must drop
that captured owner and use the original state fallback. It reads the chunk
selected by the preceding contextual lookup, using that lookup's cached key
and the same mutable position; a getter can change the position while returning
a different chunk. Resetting the position or deriving a new chunk key changes
the original result. `PropagationAdmissionEffects.java` reproduces this with a
delegating capture shim and real container materialization. Its once-Frozen-
compiled observer completes on original Frozen and forced Java; the pre-fix
native image throws `IllegalStateException` after committing the source write.
The companion `DevUtils/tests/lighting/coordinator/PropagationAdmissionEffects.c`
delegates capture to the actual library selected by the `RUNTIME` compiler macro.
Build it as a shared library linked to that same native image. For the native
observer run, point `mattmc.rust.natives.dir` at the shim directory and set
process-local `LD_PRELOAD` to the real library so its existing SONAME resolves.
Keep the same once-Frozen-compiled Java observer for all three runs; compare
`PROPAGATION_ADMISSION` rows and require `ADMISSION_HOOKS 1` on native, along with
actual class/library origins and input hashes.
The corrected local `37848c3a` image matches that original observation. The
integrated `a648afd7` image repeats the pass with the same observer bytes,
verified class/native origins and one actual admission hook. Five native
primary-input tests cover live reads, abandonment, initial decline, mixed
properties and late invalidation/unsupported state, including retirement before
fallback and its failure path. Both images also pass the original eighteen
callback rows, four actual primary-input and four queue-input VM cases, and the
two genuine base-retention omission controls. The failed baseline remains
recorded; integrated gate results follow below.

The matching local Root4 image passes four native primary-input tests, all
original eighteen rows, four actual primary-input VM cases and four actual queue
VM cases, with both genuine omission controls. Its unchanged screen completes
with eight quiet samples, matching work/full state and zero swap: 31.23 ms and
127,438 Java bytes per 64-edit cycle, about 6.02 times original Frozen latency.
This is sufficient for intermediate correctness/save validation under the
[migration policy](../../tooling/MIGRATION-ACCEPTANCE.md); it is not final
performance acceptance. S31 retains this
debt and the connected next step: move the contextual state/chunk-cache producer
and live-input consumers together while preserving original lookup, reentry,
custom dispatch and mutable-position behavior.

After the late-invalidation correction and `a648afd7` integration, the same
screen matches work/full state with eight quiet samples and zero swap, but
latency rises to 42.20 ms, about 8.13 times original Frozen; Java allocation is
unchanged. A delegating diagnostic observes 11,952,333 actual admissions and no
late-fallback calls, excluding repeated fallback as the cause. The additional
latency remains unresolved debt, with the same connected ownership exit above.
The integrated image passes 493 focused Java tests. Its full native suite is
**3,182 passed, 57 failed, nine ignored**, with all required controls passing.
The separate [bounded source-coordinate qualification](https://github.com/HungLo2020/MattMC/discussions/890#discussioncomment-19007417)
matches the historical failures; the raw run and strict comparison stay failed.
The full Java tiers pass with fresh XML and intact guards: ordinary has 2,821
passes and ten skips, parity 193 passes, and slow eleven passes. Original legacy
traces and the portable Source corpus (252 rows plus two genuine leak controls)
also pass. Saved-light order, custody and 125 fresh serialized chunk/entity cases
plus their own save/reopen checks pass with intact guards. Current matches the
guarded pre-change image and its own reopen exactly. The same 25 Primordial
differences from Frozen remain byte-for-byte in the comparison report; full
Frozen equality remains false. These are intermediate correctness results; the
whole lighting migration and its performance/hardware acceptance remain open.

`VerifyPropagationStateInput.java` exercises the actual native capture slot and
live primary mutation, real VM collection within five seconds, checked-failure
prefix and release before a later state callback. When calling the private
native entry point directly, initialize `NativeBlockRegistry.ready()` first, as
`NativeLightPropagation.run()` does through its table admission. Its original
fixture omitted this precondition; the preserved failure does not establish a
production failure. Keep the real base-close omission and caller-held controls;
do not replace them with counters or extend their collection deadline.

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
