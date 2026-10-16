# Retained chunk generation

This records the scoped native request/holder/task integration; the broader
migration remains open. Correctness/runtime gates pass on `f570cee4`, with the
bounded temporary cost exception in [decision275](https://github.com/HungLo2020/MattMC/discussions/888#discussioncomment-18978296).
Its scope is request → retained holder/task
control → ordered stages → status and FULL publication, including holder readiness,
visible snapshots and save/send dependencies. The working request handoff now
joins the native ticket owner and its actual Ticket, counter and source resources.
The [ticket storage integration](RUST-TICKET-STORAGE.md) covers live ticket and
distance-source consumers. Lighting internals, stage algorithms,
unload policy and save IO remain separate owners.

[`chunk_generation/`](https://github.com/HungLo2020/MattMC/tree/master/src/main/rust/world/level/chunk_generation)
owns holder admission, started status, future/task/save-fence membership,
generation claims, task progression, cancellation and pending/release traversal.
It also owns pending-task pass order, persisted status, FULL effect ordering and
the holder lifecycle controller in `request.rs`. Standalone, registered and
detached holders use the same native incarnation for levels and readiness.
[`NativeChunkGeneration`](https://github.com/HungLo2020/MattMC/blob/master/src/main/java/net/minecraft/server/level/NativeChunkGeneration.java)
retains actual Java futures, status objects and engine bindings; it stores no
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

The current caller prototype also puts the four-entry request cache in that
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
The combined caller/cache and captured-list owner image (`912063fb`) passes
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
