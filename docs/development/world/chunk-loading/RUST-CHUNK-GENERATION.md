# Retained chunk generation

This records native request/holder/task integration; whole world generation
remains in progress under [assignment318](https://github.com/HungLo2020/MattMC/discussions/888#discussioncomment-18984776).
The S33 checkpoint connects actual native invocation, pending-consumer and
callback binding lifetimes. Its intermediate regression/resource screen follows
[policy317](https://github.com/HungLo2020/MattMC/discussions/888#discussioncomment-18984771);
completed-system performance acceptance remains required. Published S32, S28
and S25 results remain separately identified below.
Its scope is request → retained holder/task
control → ordered stages → status and FULL publication, including holder readiness,
visible snapshots and save/send dependencies. The request handoff
joins the native ticket owner and its actual Ticket, counter and source resources.
The [ticket storage integration](RUST-TICKET-STORAGE.md) covers live ticket and
distance-source consumers. Stage algorithms remain in their levelgen modules. Lighting internals, unload
policy and save IO remain separate owners.

[`chunk_generation/`](https://github.com/HungLo2020/MattMC/tree/master/src/main/rust/world/level/chunk_generation)
owns holder admission, started status, future/task/save-fence membership,
generation claims, task progression, stage admission/completion control,
cancellation and pending/release traversal.
It also owns pending-task pass order, persisted status, FULL effect ordering and
the holder lifecycle controller in `request.rs`. Standalone, registered and
detached holders use the same native incarnation for levels and readiness.
[`NativeChunkGeneration`](https://github.com/HungLo2020/MattMC/blob/master/src/main/java/net/minecraft/server/level/NativeChunkGeneration.java)
and its VM effect adapters/callback roots retain actual Java futures, status
objects and original holder/step resources; they store no
completion-result cache or second scheduler. The registry attaches the same
native incarnation; a retained holder owns it independently of registry id reuse. Keep this control out
of replaceable `NativeChunkInputs` captures.

## Ownership and publication

- A holder/task/status has an actual native Arc retained by its Java auto arena.
  Private address/length metadata does not own memory. Every native call/read
  fences its enclosing owner before that allocation can become unreachable.
  The bounded private read capability never escapes and allocates no views.
- Create the arena before native allocation. Constructor rollback and arena
  cleanup share an idempotent handle-only release action. JDK 25 can register
  `reinterpret` cleanup before allocating its returned segment; independently
  releasing that handle in both paths would free it twice.
- Each fixed future/task/save-fence native word has a nonwrapping 60-bit
  generation, a binding-location bit, presence and Rust's FULL admission command.
  Rust reserves the opposite **published** location and consumes the reservation
  once. Java never chooses the current location from its own bookkeeping.
  Null clears receive fresh generations; exhaustion changes no membership.
- Two fixed Java locations per field retain actual opaque objects. Separate heap
  publication tags validate visibility/reuse, not membership. Before writing a
  candidate, invalidate its heap tag; initialize its objects, then release-publish
  the native-provided tag. Commit the native word only after those roots exist.
  After commit, invalidate and clear the retired location immediately.
- Readers acquire a matching heap tag **before** the object, and validate it
  **after** capture. A reused location cannot supply a pending object to an old
  native selection. Retry only metadata; retain the captured actual future
  through `getNow`, `isDone` and callbacks without replaying those methods.
- FULL admission, its actual future and the opaque allowed-status binding share
  one native publication. Status writes retain the future; future writes retain
  status. Foreign status identities require the original virtual-query path.
  Equal indexes do not make an object canonical. Started and persisted status
  retain separate 48-bit tags and matching strong pending/previous Java leaves.
- Future reads **and** compare/exchanges validate against the immutable status
  count. Extra task/save-fence storage must not widen the original array bounds.
  Preserve the exception class/message, query count and prior completed effects.

Java payload visibility comes from supported heap acquire/release operations,
not an assumption that a raw native load publishes Java objects. Acquiring the
candidate tag before capture observes its initialization. If capture observes a
later reused payload, that payload's acquire also observes prior invalidation;
the final tag check rejects the old native generation. Tags never wrap or reuse.
A paused producer retains the native-selected previous location. A retired
future becomes collectible while the holder remains live, unless a reader or
caller still holds that actual future.

The native array belongs to the holder Arc; it has no separately freed rows.
Generation validation cannot make a freed native read safe. Atomic words use
Rust `AtomicU64` sequential consistency and aligned FFM volatile loads of the
same width. See [VarHandle access modes](https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/lang/invoke/VarHandle.html),
[Rust foreign atomic access](https://doc.rust-lang.org/std/sync/atomic/struct.Atomic.html#method.as_ptr)
and the [FFM memory-model caveat](https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/lang/foreign/package-summary.html).

## Effects, reentry and failure

The task continuation retains one actual Task Arc and Rust async
state machine per invocation. Java directly polls it to obtain a typed command,
copies all command words, and performs the actual engine effect on that
invocation's strong frame. No async executor or runtime is added. Nested calls
own separate continuations and share the original task state. No task upcall,
thread-local task-frame lookup, native lock or mutable borrowed output crosses
an engine effect. Failures resume Rust so its original cleanup sequence runs;
Java retains and throws the original engine exception. Each invocation has an
exclusive atomic command exchange; no mailbox lock is needed. Stock task-cache
handles are written directly into scoped native scratch memory. Pending-task
passes use a native cursor and release it after the last Java action; empty
passes allocate no Java frame or native cursor.

Holder readiness uses the same native-selected opaque bindings as generation.
Rust controls accessible/ticking/entity preparation, cancellation, promotion,
demotion and dependency combination. It commits the live level after the actual
listener returns. A failure leaves that commit pending for retry. Capture the
promotion argument before cancelling the old confirmation; cancellation can
reenter and replace the live full future. Conversely, dependency combination
must reread its live binding after `isDone` returns. Actual CF methods remain
Java effects, including their exception identity and null-message boundaries.

Promoted visible views retain native map order and opaque holder bindings from
the same instant. Later promotions and registry id reuse cannot rebind an old
view. Escaped streams and iterators retain that snapshot. Neither a native lock
nor a borrowed mutable command buffer may cross a Java consumer callback.
Preparation leaves the published view and pending-change flag intact. Allocate
and adopt all actual Java holder bindings before the nonallocating native commit;
then publish the volatile Java view. Failed binding allocation must leave the
previous view visible and the update available for retry.

Loaded stock requests consume native commands in the original JVM caller's
locals. The native request owner retains the actual ticket-storage and holder
registry owners. UNKNOWN admission constructs the actual Ticket and calls the
actual virtual cache/storage admission methods, including stored-ticket getters
and reset overrides, before native storage publication. A complete retained
request resource includes its actual VM ticket source alongside the native
lease; a bare segment is insufficient to retain those VM bindings. Java
listener/dirty effects and custom virtual dispatch remain ordered effects.

The four-entry request cache also resides in that
native owner: Rust stores keys and insertion order, selects a physical binding
slot, applies null/create and immediate-read rules, and chooses invalidation
after the original update effects. Java's fixed arrays retain only actual status
and chunk objects. Identity, presence and chunk-kind observations contain no
callbacks; they do not carry cache keys or ordering. Hits do not refresh order.
A blocking request publishes only after its actual future join and result query;
a create failure publishes no entry. Native cache locks and frame-pool locks
end before any profiler, executor, future, holder or generation effect.

Public get/get-now/future dispatch and distance-manager update completion use
native continuation decisions. Java retains the original executor submissions,
managed-block/join operations and exception construction. The update controller
must finish both map promotion and generation-task effects before clearing the
cache; a failure retains the original completed prefix. Nested operations keep
their own native invocation and use the live shared cache when they resume.
The historical caller/cache and captured-list owner image (`912063fb`) passes
all Java tiers and 3,017 Rust checks (nine ignored), the ticket traces, public
cache reentry/error controls and retained four-dimension fresh/reopen output.
Its representative complete-cost pilot exceeds the loaded-request and holder
budgets. The subsequent bulk-walk alias correction (`f570cee4`) passes the
remaining correctness/runtime gates, but its 18-process campaign also measures
FULL generation at 1.75–2.57 times Frozen fresh and 1.86–1.97 times reopened,
with sufficient quiet samples in all cohorts. Decision275 explicitly accepts
these measured FULL, loaded-request and holder costs as temporary mixed VM
handoff/native snapshot debt, preserving the attribution limits and ownership
exit. See the [current ticket integration results](RUST-TICKET-STORAGE.md#generation-integration)
for exact image identities, failed timings and the explicit approval. The
ownership move alone does not establish acceptance or publication authority.
Save/send dependency commands consume
the actual `isDone` result and reread the live future before combination; they
retain no completion-truth mirror.

Preserve these contracts when changing either side:

- Commit started status before a stage. Complete an unloaded future before
  clearing its slot. Imposter replacement shares one future across pre-FULL
  slots and preserves a failure's completed prefix.
- Keep foreign comparison, parent, pyramid and dependency calls at their
  original boundaries. Assignment must not add a foreign index query. A
  canonical scheduled layer cannot finish a foreign same-index target.
- Capture a save fence **before** decrementing claims. Complete that exact fence
  at zero. Capture the next live fence only after earlier completion callbacks;
  those callbacks can replace later epochs or reenter the task. Preserve negative
  counts and exact failure prefixes. Retry starts at the first cell again.
- Native stock finalization retains the same holder Arcs as the original cache.
  Custom map/holder subclasses retain their virtual release path. Do not snapshot
  epochs early, coalesce claims across tasks or add a released bit.
- Acquire/schedule/release cells X-then-Z. Pending probes are LIFO: `getNow`,
  commit pop/unpin, then `isSuccess`. Return the strong pre-callback future when
  waiting, even if reentry retires and reuses its row. Cancellation still probes
  already pending work.
- Immediate `applyStep` → `getNow` → conditional `isSuccess` has no intervening
  task-state observation and stays one effect. Keep pending probes split at pop.
- Close the profiler zone before scheduled status commits. Preserve primary,
  suppressed and suppression-failure exception precedence, including null blame.
- FULL retains actual executors, constructors/imposter replacement, post-load,
  entity/block-entity, tick and listener effects. A partial failure cannot expose
  a successful FULL future. Late FULL completion remains blocked after demotion
  until actual readmission; `isDone` callbacks must not be replayed.

## Focused verification

Use the bundled JDK and build the intended native profile:

```sh
./gradlew -PmattmcRustProfile=release test \
  --tests '*GenerationChunkHolderContractTest' \
  --tests '*ChunkGenerationTaskContractTest' \
  --tests '*ChunkStageInvocationContractTest' \
  --tests '*ChunkStageBindingContractTest' \
  --tests '*NativeStageBindingOwnerTest' \
  --tests '*ChunkPublicationContractTest' \
  --tests '*NativePendingGenerationTest' \
  --tests '*NativeGenerationSlotReadTest' \
  --tests '*NativeGenerationLifetimeTest' \
  --tests '*NativeStatusPublicationTest' \
  --tests '*NativeGenerationTaskCacheTest' \
  --tests '*HolderLifecycleBoundaryContractTest' \
  --tests '*ChunkRequestBoundaryContractTest' \
  --tests '*VisibleHolderBoundaryContractTest' \
  --tests '*TicketRequestBoundaryContractTest' \
  --tests '*QueuedPromotionLifetimeContractTest' \
  --tests '*NestedChunkRequestContractTest' \
  --tests '*TicketOwnershipBoundaryContractTest' \
  --tests '*NativeTicketCellsTest' \
  --tests '*NativeTicketResourceLifetimeTest'
cargo test --manifest-path src/main/rust/Cargo.toml --locked --profile suite \
  --lib chunk_generation -- --test-threads=1
```

The portable observable Java contracts avoid private field probes. They cover shared
future identity, demotion and late completion, imposter replacement, generation
claims, save-fence reentry, layer order, cancellation and failure prefixes.
Native-only checks additionally cover JDK live-list equivalence, concurrent
slot publication/null clears, fixed opaque binding storage and actual native-owner
GC. FULL admission checks exercise actual `isDone` reentry/throws and foreign
allowed-status queries; the controlled pause covers both a new future and a
pending allowed-status publication.
`GenerationContractRunner.java` in the observer directory runs the same compiled
contracts against sealed pre-change and Frozen classpaths.

The early real-server probe is
[`VerifyChunkGeneration.py`](https://github.com/HungLo2020/MattMC/blob/master/DevUtils/tests/worldgen/chunk_generation/VerifyChunkGeneration.py):

```sh
python3 DevUtils/tests/worldgen/chunk_generation/VerifyChunkGeneration.py \
  --pre-runtime build/chunk-generation/<sealed-pre-change-runtime> \
  --output build/chunk-generation/<new-probe> --skip-build
```

The pre-change artifact must contain a complete compiled classpath, native
library, dependency hashes and a `manifest.json`; an overlay cannot certify
unrelated changed dependencies. Frozen stays pinned to
`7a4d181717dc0c7da9086f1a17687dfd522ce417`. Each side uses its own dependencies.
The driver seals production source, compiled classes, observer and mapped
native images; do not edit or build while its servers run.

The existing holder regression also has an actual-runtime comparison driver:

```sh
python3 DevUtils/tests/worldgen/chunk_generation/VerifyHolderComposition.py \
  --pre-runtime build/chunk-generation/<sealed-pre-change-runtime> \
  --test-classpath-file build/chunk-generation/<test-classpath-file> \
  --output build/chunk-generation/<new-holder-comparison>
```

It runs the adapted fixture on Current, Frozen and pre-change runtimes, and the
original fixture on Frozen and pre-change. Exact traces and collision-order
oracle traces must agree without normalization. A setup control verifies that
fixture adaptation preserves the public FULL identity and untouched lower slots.
This is a correctness check, not a performance measurement.
`--reuse-baselines <earlier-comparison>` reruns Current only after verifying
retained reference receipts and the exact approved native setup signature change.
Keep the archived `VerifyChunkHolders.executed.java` with its original hash.
Reused Frozen/pre-change runs retain their original image identities.

The probe checks actual stock requests to FULL, future/chunk and shared imposter
identity, saved output, already-loaded request samples, then restart from disk.
Its default is one End chunk, with `--dimensions` for additional dimensions.
For a deterministic all-dimension comparison, use `--ordered-features` to run
the existing ordered FEATURES margin before FULL. That entire prepass is inside
the request timer; record it as a different workload from a direct FULL request.
Primordial differences from Frozen require exact equality with the sealed
pre-change output, and the report retains every differing field fingerprint.
First-request latency and short loaded samples are diagnostic. They do not
replace the final [FULL/resume/failure/cancellation campaign](../levelgen/RUST-FULL-GENERATION.md),
controlled executor/publication failure tests, native-owner GC checks or matched
performance acceptance under the [migration policy](../../tooling/MIGRATION-ACCEPTANCE.md).

For complete control-path cost, use the separate flat-air fixture:

```sh
python3 DevUtils/tests/worldgen/chunk_generation/VerifyGenerationCost.py \
  --pre-runtime build/chunk-generation/<sealed-pre-change-runtime> \
  --output build/chunk-generation/<new-cost-comparison> --warmup-requests 512
```

This runs three rotated Frozen/pre-change/Current rounds, each fresh and
reopened. Every process performs the same fixed warmup followed by 15 batches
of 16 actual stock FULL requests, then loaded-request and live-holder samples.
The fixture changes only its artifact's world properties; stages, executors,
IO, constructors and publication use production code. Consumption is timed;
saved-form serialization and saving are outside the request timer. All saved
forms must match across runtimes and the disk roundtrip. The untimed warmup
settle waits for one second without compiler/GC progress, with a 15-second cap;
it never changes the measured request count. Retain all attempts and raw samples.
Fewer than five compilation/GC-quiet samples yields no quiet median, and the
driver never grants automatic performance acceptance. `--pilot` permits a
smaller fixture check and cannot establish acceptance.

Two isolated failure drivers exercise the actual native image without editing
production inputs:

```sh
python3 DevUtils/tests/worldgen/chunk_generation/MutateVisiblePromotion.py \
  --classpath-file build/chunk-generation/<test-classpath-file> \
  --output build/chunk-generation/<new-promotion-control>
python3 DevUtils/tests/worldgen/chunk_generation/MutateTicketAdoption.py \
  --classpath-file build/chunk-generation/<test-classpath-file> \
  --output build/chunk-generation/<new-ticket-owner-control>
```

The promotion control injects failure while allocating actual holder bindings.
Its negative commits native state early and must fail the retry assertion.
The ticket-owner control throws after actual JDK auto-arena cleanup registration,
then observes manual and GC cleanup with one native release. Its negative traps
the second raw release and requires the intended assertion; it never deliberately
frees an allocation twice. Compilation failure cannot count as a mutation kill.
These controls cover storage-owner adoption, separately from Ticket-cell binding.

## Verification status

### Native readiness and dependencies

S28 moves prerequisite aggregation, wait/wakeup, completion/cancellation and
accessible/ticking/entity readiness, including send/save fences, into Rust.
It starts from `006afac17` and integrates the separately published S24/S27/S29
changes through `2b183557`. Java retains genuine futures, resource bindings,
virtual dispatch and world, light, network and save effects.

Native `readiness.rs` now owns range dependency nodes, their publication barrier,
error selection, ordered join/result passes, holder completion/downgrade/retry,
request rescheduling and task wait/wake decisions. Direct futures remain mutable
until their parent node publishes; a published child retains its frozen error
resource. Java prepares that actual error binding before native commit. The
original obtrusion-before/after-pair distinction is a required contract.
Native parent links and a reserved ready queue advance eligible continuations
directly; completion does not rescan the whole graph or allocate queue capacity.

The private chunk future adapter routes only the engine's fence combiner into
this native graph. All actual virtual `thenCombine` and stage-conversion calls
still occur, including instrumented stock-class replacements. Arbitrary public
CF combinators and executors keep their existing behavior. Actual generation
fences, status futures and range adapters use this producer; public future aliases
remain the same objects throughout callbacks and native publication.

Keep the internal join stage separate from the public result stage. A caller
can complete the public future early: joins still occur, while result callbacks
are skipped. Preserve captured task incarnations and repeated custom wake
callbacks; cancellation does not suppress their original outstanding probes.
Retire input/error roots at their final engine use: fences and failures release
before waking callers, and successful ranges release futures after joining but
before result callbacks. Only the selected error and actual result payloads
survive. A native-selected completion role determines this boundary.

The original `e31df0e5` retirement repair passes 133 focused Java cases (four
controlled cases skipped in that ordinary run). Original range10, wake5 and sync5 contracts pass on Frozen and the sealed
complete published runtime. The initial merged-stage, per-leaf error snapshot
and class-purity shortcuts each have preserved failing Current controls under
`s28-*`; no earlier positive is relabeled as covering a subsequently added case.
Two further original retirement cases pass on Frozen/pre and fail on the
pre-repair Current image; they pass after native-selected root retirement.
The GC fixture needs both Mockito harness roots released before judging cycles.

Run the isolated allocation/publication/root/release controls with:

```sh
python3 DevUtils/tests/worldgen/chunk_generation/MutateChunkDependencies.py \
  --classpath-file build/chunk-generation/<test-classpath-file> \
  --output build/chunk-generation/<new-dependency-control>
```

The unchanged copy passes post-adoption failure/retry and real retirement.
Early native publication, weak VM prerequisite bindings and missing native
release are each detected. Production source/class/native input guards and actual
mapped-image checks surround the controls. `rangeStatistics` counts live,
created and retired owners plus live/peak requested owner/vector payload bytes;
it excludes Arc control blocks, allocator overhead, VM resources and all other
native/process allocations. It is not total native memory or RSS.

Preparation and generation claims now use native continuation decisions.
Accessible/entity projection callbacks capture no map or holder. Delayed send
callbacks retain their actual map, holder and center chunk without retaining
the old range or neighbors. Five preparation and two callback-lifetime cases
match original Frozen/pre behavior. Final integrated native `53e7d890` passes
2,512 ordinary Java cases (six expected skips), 193 parity, 11 slow and 3,085
Rust cases (nine ignored). Isolated positives and intended mutation witnesses
cover dependency publication/VM roots/native release, epoch publication and
failed blocking creation. Two resource tests cover retained pending graphs and
success, failure and external cancellation while the terminal output stays live;
actual owner/payload counters return to their prior bound after last use.

Actual player loading, movement, dimension transfer, lighting and save/reopen
checks pass. Shutdown begins with 81 actual FULL futures pending; reopening
completes nine requests in that region. The holder lifecycle passes 18 checks.
Straight and interrupted/resumed generation each check 648 FULL chunks across
four dimensions: all Current/pre fields match, three standard dimensions match
Frozen, and known Primordial differences remain recorded. Injected native/Java
failures restore all nine touched chunks; the failed target is CARVERS in
memory and absent on disk, not a durability result. Cancellation drains all
660 holders and preserves the below-FEATURES comparison away from decorated
neighbors; later and neighboring scheduling differences remain explicit.

Keep original assigned-pre `006afac17`/`f570cee4` and the separate clean integrated
pre `2b18ec0d`/`967be670` fixtures distinct. The latter includes independently
published item/generation work and isolates this slice's cost. Neither replaces
Frozen or relabels earlier receipts. The original matched `e31df0e5` campaign
uses 18 processes, three rotated rounds and fresh/reopened stock flat-air FULL
requests. Current quiet medians are 1.834–2.026 ms fresh and 0.937–0.941 ms
reopened, 386.6–390.6 ns loaded and 5.81–5.90 ns for holder reads. These exceed
the normal Frozen allowance; they are measured migration debt, not a speedup.
The [scoped temporary disposition](https://github.com/HungLo2020/MattMC/discussions/888#discussioncomment-18982157) records the exact images,
S29 integration impact, responsible owner and ownership-based exit.

The single four-process diagnostic identifies actual VM frame allocation,
boxed native continuation/Arc-exchange work and native transitions alongside
existing snapshot and wait work. Instrumentation can change JIT/escape analysis;
its Java allocation/CPU and sampled weights are diagnostic, not uninstrumented
production certification or causal shares. Native allocator/Arc/synchronization
costs remain unpartitioned. No whole-process native-memory, server or FPS claim
is made. Retain invocation state across native producers and consumers through
genuine VM observation/publication boundaries to remove this temporary churn;
recheck the same complete workloads and lifetime/save contracts for that exit.

### Native stage binding lifetimes

S33 builds on published S32 `4199640e`/native `e2097036`. Native
[`stage_binding.rs`](https://github.com/HungLo2020/MattMC/blob/master/src/main/rust/world/level/chunk_generation/stage_binding.rs)
creates one binding for an actual admitted stage. Registration, each callable
callback lease, actual native task invocations and pending rows retain that same
binding. The invocation keeps its Arc across real immediate `getNow`/`isSuccess`
queries; a pending row takes a separate lease from that invocation. Completion
opens an independent invocation through the callback lease and composes the
existing native completion policy. It does not reconstruct a holder-only
completion operation. Native identities never wrap or rebind after retirement.

The actual Java callback strongly retains its original holder and step. Those
managed roots survive task cancellation/release and repeated, nested or concurrent
callback invocations. They do not retain the admission map/cache or engine/work
future. Rust stores no Java pointers. Per-holder weak VM projections resolve
native-selected identities against the actual returned futures; native task
custody survives collection of a Java facade. A pending VM row retains the
projection and actual future, independently of its native row's binding Arc.
A later consumer claims that same live native identity and adopts its own
facade lease; a retired identity cannot be resurrected. These resource bindings
retain no completion-result cache: virtual `applyStep`, `handle`, `getNow`, `isDone`,
`complete` and obtrusion retain their genuine objects and original boundaries.

Scope temporary projection roots to the genuine virtual call, including
collection before a custom override returns. Release those temporary roots after
actual-future capture and native invocation adoption, before querying the future.
Keep callback and pending-consumer leases independent.
Publish a VM projection before native membership. Serialize only private alias
metadata publication and identity matching, and restore the previous projection
if its native commit fails. Never hold native table locks across VM effects.
Retiring an old binding cannot clear a newer binding at the same status. Preserve
constructor/adoption rollback and independently release callback and task leases;
completion alone does not make a retained callback uncallable.

Run `ChunkStageBindingContractTest` and `NativeStageBindingOwnerTest` with the
focused Java suite above. The latter physically collects the callback and Java
facade while the native pending task remains live, then checks last-use retirement.
[`VerifyStageBindings.py`](https://github.com/HungLo2020/MattMC/blob/master/DevUtils/tests/worldgen/chunk_generation/VerifyStageBindings.py)
accepts the same five classpath/runtime/output options as `VerifyChunkStages.py`.
It runs identical portable contracts on Frozen, complete published S32 and Current;
its optional native requirement rejects old runtimes only after the actual task
has run. Missing symbols and compilation failures are not intended controls.
[`MutateStageBindings.py`](https://github.com/HungLo2020/MattMC/blob/master/DevUtils/tests/worldgen/chunk_generation/MutateStageBindings.py)
takes `--classpath-file` and `--output`. Its disposable adapter copies exercise
allocation/adoption failure, concurrent alias publication, rollback, release and
VM-root retention. Omitting the native pending pin must fail the physical owner
check after facade collection. Production sources and native images remain sealed.

Add `--require-native-binding` alongside `--require-native-stage` to the real
server `VerifyChunkGeneration.py` probe. Outside request timers it checks actual
binding creation, callback dispatch, task consumption and retirement, including
saved-world reopening. Nine binding statistics report live/created/retired
bindings, live/created/retired callback leases, completion attempts, consumer
lease adoptions and the pending-adoption subset. These count registered resource
handoffs, not every Arc clone, byte totals or cached future truth. Existing stage statistics retain their separate four-word contract.

The `c701b5fd` release image (production source `afba0faa`, Main classes
`0f76496f`) passes 2,537 ordinary Java tests (six expected skips), 193 parity,
11 slow and 3,098 Rust tests (nine ignored). Focused coverage passes 126 Java
cases (four controlled skips), 49 Rust cases, six portable-runtime processes
and 13 isolated controls: 25 adoption/root positive methods, two alias-positive
cases and ten intended mutation kills. Real immediate `getNow` collection,
virtual-return collection, later-consumer reuse, callback/task release and
last-use retirement are physical ownership checks, not counter-only witnesses.

Actual four-dimension fresh/reopen servers pass the native binding assertions.
Fresh campaigns generate 648 straight and 648 resumed FULL chunks per runtime:
Current/pre output is exact; standard Frozen dimensions match; original
Primordial differences remain separately recorded. Failure rollback, actual
cancellation, all six player initial/reopen runs, holder lifecycle, dependency
publication, slot epochs and forced-create failure pass on this same image.
These headless checks do not certify rendered-client/FPS behavior.

The short representative screen uses the original Frozen `7a4d1817`, immutable
complete published S32 `4199640e`/`e2097036`, and Current on `11346d05` plus this
checkpoint. A later four-document sync to `1d9d657b` leaves all tested production
source/classes/native unchanged. Each process uses 512 warmup requests, 45
batches of 16 measured requests, 100,000 loaded iterations, 6 GiB heap and CPUs
4–7. Original eligible Frozen/pre receipts and identical compiled observers are
reused; earlier prototype Current pairs remain historical. Work/output and image
seals match, and every cohort has at least five compilation/GC-quiet samples.

| Workload | Current median | Current/Frozen | Current/published S32 |
|---|---|---|---|
| Fresh FULL | 2.935239 ms | 5.82338 | 1.28853 |
| Disk-loaded FULL | 0.957482 ms | 1.61154 | 0.98260 |
| Loaded request after fresh | 384.728 ns | 11.06555 | 0.98476 |
| Loaded request after reopen | 386.011 ns | 11.22311 | 0.99831 |
| Holder read after fresh | 6.06588 ns | 1.72827 | 1.03038 |
| Holder read after reopen | 5.86257 ns | 1.75124 | 1.00162 |

This records temporary debt, not a normal Frozen-budget pass. Under policy317,
the sufficient intermediate screen replaces the queued extra 12 performance
processes; no correctness job was interrupted. Current JVM swap is zero, peak
RSS is 3.887 GB fresh/2.786 GB reopened, host swap-in is two/three pages and
swap-out is zero. RSS is whole-process memory, not native allocation accounting.

A bounded instrumented diagnostic retains the original unchanged pre pair and
reruns only the affected Current pair. Both fresh counted 16-FULL windows make
35,568 actual holder entries and 192 engine calls. FFM crossings are
767,127→869,109 fresh and 186,408→186,651 reopened. Current still allocates one
`StageAdmission` per holder attempt; the actual binding/projection/callback
objects are created per admitted engine. Instrumented main-thread Java bytes/FULL
are 323,368→330,028 fresh and 216,587→221,258 reopened; loaded requests remain
240 bytes. These counts/CPU/allocation observations change code shape and are
not acceptance timings or pure-FFI/native-allocation/synchronization shares.

HungLoDE/Codex retains complete world-generation ownership. Continue connected
producer/state/consumer migrations, including remaining Java compatibility and noise
construction. The subsequent [biome weather migration](../biome/RUST-WEATHER.md)
removes the higher-level Java precipitation/freezing/snow decisions. The subsequent
[shared temperature/cache migration](../biome/RUST-TEMPERATURE.md) removes that
Java implementation; neither increment completes world generation. At that checkpoint, region/effect and lighting reservations remained with S31.
The later published43f FEATURES handoff transfers generation metadata/adoption
and pending effects to S33/r3; lighting and its narrow save-selection interface
remain S31-owned.
Original failed controls, source snapshots, full runtime receipts, screen and
bounded diagnostic data remain under `build/chunk-generation/s33-*` and
`build/full-generation/s33-*`; superseded images are not relabeled.

### Native task stage execution

S32 starts from published S28 `a429c021`/native `53e7d890`. The private
[`stage.rs`](https://github.com/HungLo2020/MattMC/blob/master/src/main/rust/world/level/chunk_generation/stage.rs)
controller selects admission, bump outcomes, engine registration and completion
versus fatal reporting, composing existing native holder request/completion
loops. `GenerationChunkHolder.applyStep` keeps its genuine virtual entry.
Actual engine `handle` dispatch and its returned future remain VM effects;
callbacks retain only the original holder and step, with independent resources
for nested completion.

Read the step target at every original boundary. Started status commits before
engine/registration failure. A supplied engine failure reports fatal without
publishing the status; completion/publication exceptions retain their own prefix.
Work and status-slot results have distinct wrappers. Cancelling work does not
cancel status publication, and late completion can publish a replacement after
downgrade. Release map/cache/temporary status bindings before engine callbacks.
No native lock or borrowed output crosses a VM effect.

The `e2097036` candidate passes 2,522 ordinary Java tests (six expected skips),
193 parity, 11 slow and 3,091 Rust tests (nine ignored). Ten stage contracts pass
on pinned Frozen, complete published S28 and Current. The actual-task witness
advances multiple stages and resumes a pending engine; an agent detects the
original Java orchestration on both references and passes Current. Four isolated
allocation/effect/retirement contracts and all ten stage contracts pass on
injected adapter copies. Omitting stage-driver release fails its intended
native-holder witness. These hooks never modify production sources or outputs.

Actual four-dimension server fresh/reopen stage assertions pass. Fresh
three-runtime campaigns each generate 648 straight and 648 resumed FULL chunks:
Current/pre output is exact, all three standard dimensions match Frozen, and
162 original Primordial differences remain per phase. Failure rollback preserves
nine touched chunks; cancellation retains 169 compared statuses and releases
660 logical holders, with the recorded neighbor/later-stage differences intact.
All six player initial/reopen processes pass: shutdown begins with 81 actual FULL
requests pending and nine sampled chunks reopen at FULL. Actual holder lifecycle
passes 18 checks/16 stepped tick operations. Native GC/root-retirement,
dependency/epoch mutation and forced-create-failure controls also pass.
These are headless server and contract checks, not client/FPS acceptance.

Run stage contracts and the original-policy control with
[`VerifyChunkStages.py`](https://github.com/HungLo2020/MattMC/blob/master/DevUtils/tests/worldgen/chunk_generation/VerifyChunkStages.py).
Supply `--classpath-file` for Current tests, `--pre-runtime` for complete S28,
`--frozen-classpath-file`, `--frozen-native-dir`, and an unused `--output` below
`build/`. The identical fixture compiles against each complete runtime; only test
classes are overlaid.
[`MutateChunkStages.py`](https://github.com/HungLo2020/MattMC/blob/master/DevUtils/tests/worldgen/chunk_generation/MutateChunkStages.py)
takes the Current test classpath and output directory for isolated failure copies.
Admission allocation failure leaves no claim/dispatch; engine-effect failure
retains the started prefix; work-wrapper failure preserves the published slot
without reclassifying it as engine fatal. Add `--require-native-stage` to
`VerifyChunkGeneration.py` to require actual Current fresh FULL requests to
traverse multiple native-controlled stages. Attempt counters are outside request
timers and do not account for allocation or CPU.

The complete-cost campaign retains 18 processes: the unchanged first pilot
rotation plus two missing rotations. Each process uses 512 fixed warmup requests,
45 batches of 16 measured requests and 100,000 loaded-read iterations, 6 GiB heap,
CPUs 4–7. Work and serialized output match. Original rotation-two Frozen fresh
has only four quiet samples and retains a null median/ratio. The other cohorts
meet the five-quiet-sample minimum. Under
[decision298](https://github.com/HungLo2020/MattMC/discussions/888#discussioncomment-18983400),
only original rotations one/three support paired fresh Current/Frozen ratios;
a later standalone Frozen fresh run (nine quiet samples, 0.514749 ms) is
corroboration, never paired with old rotation-two Current.

| Workload | Current median range | Current/Frozen | Current/S28 |
|---|---|---|---|
| Fresh FULL | 2.169018–2.539808 ms | 4.41636–4.66349 (two pairs) | 1.18893–1.49733 |
| Disk-loaded FULL | 0.915389–1.001299 ms | 1.46808–1.81466 | 0.82232–0.96756 |
| Loaded request after fresh | 386.262–399.420 ns | 10.96030–11.42614 | 0.99299–1.03014 |
| Loaded request after reopen | 385.576–398.608 ns | 11.38448–13.56850 | 0.98536–1.02644 |
| Holder read after fresh | 5.89918–5.91701 ns | 1.67909–1.68728 | 1.00204–1.01610 |
| Holder read after reopen | 5.80520–5.90822 ns | 1.73192–1.76464 | 0.99749–1.00335 |

These exceed the ordinary 10%/25% allowances. Only
[S32 decision300](https://github.com/HungLo2020/MattMC/discussions/888#discussioncomment-18983480)
accepts these measured temporary costs for this exact candidate; it does not
waive correctness or approve broader production changes. JVM swap is zero, host swap-out
is zero, and observed host swap-in of 0–179 pages remains recorded. Current peak
RSS is 3.695–3.812 GB fresh and 2.752–2.806 GB reopened: higher than S28 fresh,
lower than Frozen. RSS is whole-process memory, not native allocation accounting.

A separate four-process instrumented diagnostic retains equal 35,568 holder
stage entries and 192 engine dispatches per counted 16-FULL window. FFM calls
increase 472,826→771,540 fresh and 172,722→189,328 reopened. Existing fallback
continuations are composed, not duplicated per request. Worker samples and
allocation weights expose `StageFrame`/control costs. Main-thread instrumented
Java bytes/FULL are 316,618→322,970 fresh and 216,095→216,606 reopened; loaded
requests remain 240 bytes. Instrumentation affects code shape; sampled all-thread
allocation weights are estimates, not exact production totals or CPU shares.
Native allocator/Arc/work/synchronization shares remain unresolved. No pure-FFI,
hard-floor or optimality claim follows.

S32's recorded ownership exit was connected task/VM-future invocation binding
creation, lifetime and consumers; S33 addresses that ownership above. The S32
measurements and bounded approval remain historical and do not certify S33.
Original results/source snapshots remain under `build/chunk-generation/s32-*`
and `build/full-generation/s32-*`. Its immutable pre manifest's omitted eleven
Java goal/target metadata entries have a separate supplement; all runtime classes
were present, and no old FULL output is relabeled as that pre execution.

### Published S25 slice

The `f570cee4` slice passes 2,376 ordinary Java tests (six skips), 193 parity,
11 slow and all 3,017 Rust tests (nine ignored), with the ticket/holder/lifetime,
straight/resumed FULL and failure/cancellation gates described in the
[ticket integration](RUST-TICKET-STORAGE.md#generation-integration). Its 18-process
FULL/loaded/holder costs are accepted only under the bounded exception275.
The later documentation/skill-only sync to `e2242d18` leaves all 3,957 tested
source/class/native inputs unchanged; it requires no repeated acceptance run.
Frozen remains pinned to `7a4d181717dc0c7da9086f1a17687dfd522ce417`; clean
pre-change is `5f9c0b3b819a72d374882418c0e7c08145096ac4`. Later documentation
and skill syncs do not relabel those runtime identities. Exact raw failed/noisy
attempts and executed input capsules remain under `build/chunk-generation/`.

### Historical images and preserved failures

The connected request/ticket-producer image `77b0df8f` passed 103 focused Java
cases (100 passes, three pause cases exercised separately), 78 Rust checks,
78 portable cases on each runtime and all 11 isolated publication positives;
the missing-tag mutation failed at its intended assertion. Four-world fresh
and reopened forms/aliases matched 24 retained reference rows. Two 16,000-step
holder replays observed 5,978,240 reads without inconsistency; all 992 collision
oracle passes agreed. See `s25-r4-caller-*`; the producer-results capsule copies
144 actual source/class/native inputs. These receipts retain their exact tested
image and do not certify later changes.

That image still failed complete cost: fresh/reopened loaded requests were
75.87/93.37 ns and raw holder observations 5.82/5.26 ns. FULL had only three/two
quiet samples, so neither side has a quiet median. JVM swap was zero; host swap
was six pages in, zero out. Earlier `526`, `c960`, `4b83` and the original matched
18-process failures remain retained. No exception is granted by these results.

An additional isolated allocation-failure control exposed lost promotion retry
in both pre-change and `77b0`, while Frozen passed. The prepare/adopt/commit repair
(`5295e9e3`) passes 14 affected Java and 79 native checks. Its isolated positive
retries successfully; moving commit before Java adoption kills the intended
retry assertion. Evidence: `s25-r4-promotion-failure` and
`s25-r5-promotion-retry-{results,controlled}`. Earlier happy-path receipts did
not cover this error path.

[Revision255](https://github.com/HungLo2020/MattMC/discussions/888#discussioncomment-18966979)
extends ownership through Ticket identity, historical unbound and stored
counters, admission/adoption and facade lifetime. Newly included originals are
sealed in `s25-r5-originals`. Before their implementation, six public ticket
controls passed on Frozen, clean pre-change and the repaired candidate:
`s25-r5-ticket-original-contracts`. Counter ownership integration remains pending.
Stable final tiers, FULL/resume/failure/cancellation and matched performance
remain required before publication. Follow the
[migration policy](../../tooling/MIGRATION-ACCEPTANCE.md); retain per-workload
ownership debt and record any required bounded coordinator exception explicitly.

The subsequent counter-resource image (`273fd4ea`) passed 118 focused Java cases
(115 passes and three separate pause cases), 84 native checks, 88 portable cases
on each runtime, all 11 controlled publication positives and actual four-world
fresh/reopen saved forms and aliases. Its registered-adoption and early-entry
publication mutations were detected. These receipts remain in
`s25-r5-counter-*`; no holder replay or charged cost started for that image.

A deterministic native cleanup interleaving then exposed a later reservation
being freed by an old last-owner cleanup. The repair serializes the owner claim
with reuse and retains queued Java unpin events. Its image (`180a99c1`) passes
118 focused Java and 86 native checks. Isolated removal of owner serialization
and queued-unpin preservation fails at the intended assertions. Actual Java
re-pin controls also pass for same-ticket re-store and recycled managed slots;
removing the Java drain monitor loses both required strong roots after GC.
Evidence is in `s25-r5-counter-cleanup-*` and
`s25-r5-java-repin-controlled-v2`. Broader connected verification and performance
for this repair remain pending. See the
[ticket ownership constraints](RUST-TICKET-STORAGE.md#generation-integration)
when changing counter lifetime or binding order.
