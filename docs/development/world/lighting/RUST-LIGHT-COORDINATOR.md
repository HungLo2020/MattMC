# Per-engine lighting coordinator

**S0026 candidate:** stock node checks, section lifecycle, queued reconciliation,
COW, publication/notification and whole sky-source seeding now share native
ownership. Seven complete workloads meet the general allowance; the exact
retained-import workload has a bounded coordinator exception. Publication is pending.
The approved S0026/r4 continuation connects saved/public producers, typed
threaded commands and actual generation/root lifetime to those consumers.
Primary owners, native propagation inputs and saved-light handoff have focused
development verification. Stock typed check commands now connect to
the real level bindings and PRE/POST queue. Shared identity resources and repeated
entry handoff, typed imports/retention, and the connected status frame have
focused verification after correcting fallible binding timing. Connected
enable/source commands now match the portable effects and larger engine/threaded
observers. The guarded r4 Java31/native `fc1e557a` pair passes ordinary/parity/slow
Java tiers; the full Rust suite retains the same 57 rendering-host failures.
All six actual server fresh/reopen checks also pass for that exact image.
Cost acceptance remains open. Java21/native `011e83c3` below
is the preserved r3 reference, not validation of the new changes.

The connected level pending/update and task-pass consumer now has a guarded
Java33/native `7d49c5a0` image: 113 affected Rust tests, the full Java tiers,
24 portable consumer rows, the private metadata-failure retry control, larger
engine/threaded/sky comparisons, and nine semantic omission controls pass. Its
full Rust run retains the same 57 rendering-host failures. All six actual server
fresh/reopen runs pass after moving the audit to the actual level/task consumer;
unchanged Frozen/pre lifecycle and zero-counter observations match the original
audit exactly. The old bypassed-hook failure is retained.

The Java33/native `7d49c5a0` final campaigns (`s0026-r4-final-outer-timing-8`
and `s0026-r4-final-import-timing-10`) complete all original eight workloads,
three process pairs each, with all work, quiet-sample and source/class/native
identity guards passing. Seven workloads meet the general 10% allowance:

| Workload | Current/Frozen, three pairs | Current/pre-change, three pairs | Status |
| --- | --- | --- | --- |
| Empty threaded queue | 0.9441 / 0.9493 / 0.9199 | 0.6028 / 0.6085 / 0.5893 | Within general budget |
| One task | 1.0644 / 1.0689 / 1.0341 | 1.0448 / 1.0154 / 1.0248 | Within general budget |
| 64 tasks | 0.6744 / 0.7185 / 0.7099 | 0.9818 / 1.0115 / 0.9851 | Within general budget |
| 1,000 tasks | 0.7747 / 0.7786 / 0.8356 | 1.1589 / 1.1173 / 1.2012 | Within general budget |
| 2,501 tasks | 0.8293 / 0.7966 / 0.7734 | 1.1787 / 1.1194 / 1.1234 | Within general budget |
| Complete source production | 0.6675 / 0.6461 / 0.7232 | 0.6306 / 0.9127 / 0.9743 | Within general budget |
| Retained queued imports | 2.3695 / 2.3687 / 2.3338 | 0.7962 / 0.4874 / 0.8054 | Temporary exception, decision 258 |
| Direct empty update | 0.5856 / 0.5963 / 0.5733 | 0.4951 / 0.5063 / 0.4708 | Within general budget |

Queued imports remain over both Frozen allowances. Their matched quiet medians
are Frozen 7.997 / 8.045 / 8.071 us, pre-change 23.800 / 39.100 / 23.385 us,
and Current 18.949 / 19.056 / 18.835 us per complete cycle. Current improves
19.5–51.3% on pre-change. [Coordinator decision 258](https://github.com/HungLo2020/MattMC/discussions/888#discussioncomment-18972999)
approves only this exact Java33/native `7d49c5a0` retained-import workload. The
exception does not cover other candidates, changed workloads, cold initialization
or native algorithm regressions. This S0026 owner remains responsible for moving
the remaining actual producer/caller/resource ownership into Rust, retiring
Java-only adapters while preserving surviving VM contracts, then rerunning this
unchanged workload against the pinned references. Reaching the applicable 10%
general or 25% attributable allowance ends the exception; material candidate,
scope or cost changes require a new decision. Java allocation
is 25,048 / 18,624 / 17,024 bytes per cycle for Frozen/pre/Current; this excludes
native allocation. The complete producer, PRE/update/POST and ownership costs stay
charged. Required VM monitors, layer/array aliases and published map identities
remain at their original observable points; native maps, locks, scans, copies and
allocation are not automatically attributed to the boundary.

Cold queued-fixture construction, outside the repeated-cycle window, is
737–744 ms Frozen, 557–651 ms pre-change and 648–655 ms Current. Its internal
first engine construction is 5.9–6.5 / 57.3–62.0 / 155.9–157.6 ms respectively:
the enclosing fixture improvement does not erase that startup debt. Preserve all
raw cold/resource/sample observations. No client/FPS or native CPU-share result
is implied. `s0026-r4-acceptance-review-26/review.json` records exact identities,
all workload pairs and receipt checksums. The subsequent `decision-258.json`
retains the bounded decision and all cold comparisons. Complete creation through
first initialized ready state meets the general 10% allowance in all 24 matched
workload pairs. The threaded driver reports adjacent constructor/initialize
intervals separately; the coordinator fixture constructor includes initialization.
Their internal engine-construction components remain disclosed, with no
independent component pass or new sub-gate implied.

The following historical r4 costs refer to preserved Java31/native
`fc1e557a` image. Its first complete cost campaigns remain sealed:


| Workload | Current/Frozen, three pairs | Status |
| --- | --- | --- |
| Empty threaded queue | 1.2772 / 1.1278 / 1.1626 | Open: first pair exceeds 25% |
| One task | 1.0892 / 1.3799 / 1.0728 | Open: second pair exceeds 25%; all slower than pre-change |
| 64 tasks | 0.6465 / 0.6798 / 0.6823 | Within general budget |
| 1,000 tasks | 0.7901 / 0.7832 / 0.7765 | Within general budget |
| 2,501 tasks | 0.7675 / 0.7947 / 0.7804 | Within general budget |
| Complete source production | 0.7553 / 0.7402 / 0.7243 | Within general budget |
| Retained queued imports | 2.2530 / 2.3427 / 1.3208 | Open; pre-change improves in all pairs |
| Direct empty update | 0.7541 / 0.7238 / 0.7492 | Within general budget |

All pairs matched work and quiet-sample criteria. No over-budget exception is
granted. A separate lifecycle diagnostic found fewer registrations and cleanup
activity; it does not replace these charged results or measure native CPU share.
Keep both r4 receipts (`s0026-r4-final-outer-timing-7` and
`s0026-r4-final-import-timing-9`) and the preserved source/class/native image
when changing the remaining consumer path.

## Ownership and entry points

The implementation is split between
[Java effects and collection facades](https://github.com/HungLo2020/MattMC/blob/master/src/main/java/net/minecraft/world/level/lighting/NativeLightCoordinator.java)
and [native policy/continuations](https://github.com/HungLo2020/MattMC/blob/master/src/main/rust/world/level/lighting/coordinator/mod.rs).
It reuses the existing [maps](RUST-LIGHT-MAPS.md),
[layers](RUST-LIVE-LAYERS.md), [propagation](RUST-LIGHT-PROPAGATION.md) and
[threaded dispatcher](RUST-THREADED-LIGHTING.md). Generation, tickets, save IO and
rendering policy are outside this slice.

Rust owns the actual tables and transition decisions. Java supplies contextual
chunk/block reads, custom-object operations, notifications and resource adoption.
Facade collections address the same native state; they are not Java membership
mirrors. Object value arrays retain typed entries directly, including captured
old array generations.

Storage may serve several engines. Each binding keeps its own node/work queues
and callback context. Native frames retain their originating binding and actual
storage allocation; retiring a binding waits for its frames. No native guard or
borrow survives a return to a Java effect.

Stock sky seeding copies bounded immutable source input into its frame and
consumes the complete descending section/COW/increase-queue operation. Java still
obtains the five chunk source objects. Custom readers, custom storage and mixed
or escaped layers use the original conditional Java loop. The old Java
per-section matrix replay is not the production fallback.

## Contracts when changing this code

- Preserve fastutil table order, live cursors, resize/trim behavior, default/null
  semantics and callback failure prefixes. Sorted final state is insufficient.
- Captured key/value arrays observe writes before rehash and retain old arrays
  after it. Preserve each bulk method's capture/advance point, live entry indices
  and reused fast-entry object. Callback commits use the originally selected
  position, even after callback mutation.
- A published map and every contained layer need the original COW lifetime.
  Retaining a map root alone does not isolate mutable layers. Custom copies,
  escaped arrays and virtual operations retain their original effects/aliases.
- Source heights must be read after any preceding custom copy callback. Eager
  source gathering changes conditional calls and can introduce a neighbor error
  that Frozen never reaches. `SkyLightEngine` is final; use supported storage,
  layer and source overrides in controls.
- Source admission rejects incompatible inputs before mutation. Each completed
  native section publishes its queued prefix before later fallible COW work.
- Reentry observes current state. Rejected propagation cannot overwrite callback
  additions with an earlier snapshot. Earlier committed effects survive failure.
- Empty-update eligibility and public pending work are distinct projections.
  Changed/affected-only publication prevents bypass even when `hasLightWork()` is
  false. Preserve the original cache-clear point and node-table trim.
- Eligibility publication and queue producers share the queue guard. Producers
  invalidate eligibility before returning; a stale scan cannot restore it.
- CPU binding slots have one native reservation/active/retirement/acknowledgement
  record. Java clears each heap reference before acknowledgement permits reuse.
  Prepared descriptors carry immutable slot/identity metadata; existing active
  object identity wins over a proposed new identity.
- Native cells own reservation claims/cancellation. Rejection clears the Java
  reference before cancellation; a consumed receipt prevents caller release even
  after an error. Unused cells cancel when the lease dies. Native-created layers
  retain their actual owner through retirement and lazy facade resolution.
- Publish actual owner metadata before Java effects resume. The r4 facade reads
  that owner and acquires a generation lease when a Java reader needs it;
  older exported leases retain independent lifetimes. Unobserved native
  identities can remain lazy.
- A mixed propagation pass preserves its previous Java layer pin through
  capture and intervening block effects. The next native layer read replaces
  that pin at the original layer-read boundary. Explicit exported readers keep
  their own generation leases; clearing the temporary callback pin must not
  invalidate them.
- Adoption rollback and registered cleanup share one handle-only release action.
  Reclaimer registration transfers ownership only on successful commit. Map
  owner/view registration commits or cancels together; cancellation and drainer
  claims are mutually exclusive. Post-commit bookkeeping cannot appear to fail
  the ownership transfer.

Stock level commands retain the actual block and sky bindings and execute block
before sky. Status and enable frames return to Java only for real custom-layer
operations, contextual source lookups, refreshed facade metadata, or required
resource retirement. Typed threaded rows retain their original engine and
position until POST; PRE failure preserves the original pending prefix for retry.
The public initialization and lighting futures keep their original scheduling.

When extending enable/source commands, preserve these specific boundaries:

- Read the highest source before reading the live sky bounds. A source callback
  can change those bounds or throw after the enabled-column commit.
- Keep the selected writable layer through `isEmpty` and `fill`, even if a custom
  predicate replaces that section in the map. Do not look it up again for `fill`.
- `DataLayer.isEmpty()` requires lazy default zero. An allocated zero array is
  not definitely empty and must retain its allocation and contents.
- Obtain block and sky chunk contexts at their original execution points. The
  existing native source kernels consume the real queues and roots; custom
  source readers and layer operations still use their original virtual calls.

The level continuation retains real engine bindings for each invocation.
`VerifyCoordinatorSnapshotFailure.java` injects a real private metadata lookup
failure and compares error identity, unchanged visible layers, pending POST, and
retry aliases against the preserved earlier implementation. This is not actual
heap exhaustion or a Frozen-negative claim.
`VerifyCoordinatorLevelConsumerNegative.py` restores only the previous caller in
an isolated class and requires the unchanged admission observer to hit its
original failure marker; linkage failures do not count. Native
work/idle projections have different meanings: changed/affected-only publication
can require an update while public `hasLightWork()` is false. Queue publication
and coordinator publication must update the same retained level projection;
observer resources must not change the existing sixteen-byte engine header.
The task cursor must wait for its update effect's reply before entering POST.
A produced snapshot stays owned by its native frame until the original Java
publication adapter claims it; an abandoned frame releases an unclaimed root.

## Capture and compare

Use JDK25 and a release native image. Preserve the exact Frozen and pre-change
classes, native image, configuration and fixtures before rebuilding anything that
could overwrite them. For Campaign-based server and cost drivers, pass
`--frozen-native-dir` explicitly to keep Frozen on its preserved library;
`--control-native-dir` independently selects the pre-change library. Both native
images are hashed through the run. The S0026 pre-change image is `5f9c0b3b`; Frozen is
`7a4d1817`. Retaining only the changed classes is insufficient before a full build:
the complete selected pre-change image contains 13,704 class files.

Run captures without concurrent source/build changes. The initial actual-engine
observer uses the standard three-image arguments:

```sh
python3 DevUtils/tests/lighting/coordinator/VerifyLightCoordinator.py \
  --java-home /path/to/jdk25 \
  --frozen-classpath-file frozen-classpath.txt \
  --current-classpath-file current-classpath.txt \
  --control-ref <pre-change-commit> \
  --control-classpath-file preserved-pre/classpath.txt \
  --control-native-dir preserved-pre/native \
  --control-image-manifest preserved-pre/manifest.json \
  --output build/light-coordinator/new-capture
```

The fixture constructs real block/sky engines, verifies nine enabled columns and
known light, then observes edits, node bursts, queued layers, retention/removal,
ordered notifications, callback failures/reentry and held published layers. It
also exercises shared storage with independent engine contexts and retirement.
This controlled CPU fixture is separate from live-server validation.

`VerifyCoordinatorCandidate.py` reuses a sealed capture with `--reference`,
`--kind` (`engine`, `threaded` or `collections`), candidate classpath and native/Java
build manifests. It compiles that capture's exact archived observer against the
changed candidate and compares the new trace. This is a new candidate execution,
not an earlier run relabeled as testing new code.

`VerifyCoordinatorCollections.py` checks protected facades. Its focused audits
cover collections, callback/equality effects, spliterators and exception messages.
Keep failed witnesses: engine parity alone did not catch the original bulk and
range differences. Prepared split failure must preserve the donor cursor;
committed children retain their actual arrays/storage without a parent root.

`VerifyCoordinatorSkyProducer.py` captures source references with
`--baseline-receipt <sealed-cost-result>`, retained `--frozen-native-dir` and
`--control-native-dir`. Candidate runs use `--reference`, the current classpath
and build manifests. Both modes require `--java-home` and `--output`. Its nine
cases cover stock admission, lazy defaults, aliases, escaped arrays, conditional
reader errors and copy callbacks with both canonical and custom storage. Known
inherited pre-change differences stay visible; the repaired candidate must match
Frozen. Stock cases also require actual native whole-producer admission.

`VerifyCoordinatorNegativeControls.py` accepts `--positive-result`, `--java-home`
and `--output`. Use successful normal-engine and sky-producer receipts. It omits
actual operations in isolated class overlays and runs unchanged observers.
Linkage/native-load errors are not semantic kills. Stock writes now bypass Java
`writable`; its omission is checked on the actual custom/escaped consumer.

Focused lifetime/error tools include `VerifyCoordinatorAdoption.java`,
`VerifyNativeReclaimerRegistration.java` and `VerifyCoordinatorQueueAdoption.java`.
`VerifyCoordinatorLevelConsumers.java` establishes original processed totals,
lookup/notification reentry, cache clearing, changed-only publication, and
threaded propagation retry. Its callbacks must be asserted to run.
`VerifyCoordinatorNativeLevelConsumers.java` separately checks actual native
projection and task consumption with the old Java propagation runnable replaced
by a failure marker. This is current-only admission evidence.

`VerifyCoordinatorSourceEffects.java` compares deferred enable/source effects,
custom highest-source and layer callbacks, failure/retry, nested PRE execution,
map replacement, allocated-zero handling and original source lookup errors.
`VerifyCoordinatorNativeSources.java` separately checks actual typed capture rows
and their release at POST; it is a current-only admission check.
`VerifyCoordinatorTaskLifetimes.java` observes original queued engine/position
roots and their release after POST. `VerifyCoordinatorPositionContracts.java`
compares custom routing, deferred packing and PRE failure/retry with Frozen.
The separate Current-only `VerifyCoordinatorNativeTasks.java` and
`VerifyCoordinatorIdentityBindings.java` check actual typed admission and shared
resource/entry reuse; they do not replace the portable semantic observers.
`VerifyCoordinatorLayerPinLifetime.java` exercises the actual private callback's
VM pin replacement while retaining a separate generation reader. Pair that
resource control with the native fallback-to-direct transition control; neither
alone reproduces a public mixed-path weak-reference failure.
`VerifyCoordinatorImportMonitor.java` checks that the real writer blocks on
the original monitor without committing. `VerifyCoordinatorImportLifetimes.java`
checks disabled-sky capture retirement against Frozen. The private-resource
`VerifyCoordinatorImportBindingFailure.java` injects one actual binding
publication failure and checks original PRE timing, pending work, error identity
and retry; this is a controlled failure, not an OOM reproduction.
Compile against the candidate and run their declared package main classes with
native access and the candidate native directory. Injected failures model
ordering, not actual heap exhaustion. Retirement remains bounded per call; larger
alias fixtures must check progress until the native pending count reaches zero.

`VerifyCoordinatorOwnership.java` compiles with the unchanged threaded observer
and takes a trace output path. Its six public-API cases queue the same layer into
block/sky storage, mutate or escape it before PRE execution, mutate it again
inside PRE, and retain aliases through publication and later fill. A throwing
PRE retains the original error identity and pending prefix on retry. The initial
74-row traces match Frozen, pre-change and preserved Java21/native `011e83c3`.
Keep this reference unchanged when replacing the producer/lifetime path.
`VerifyCoordinatorNativeInputs.java` is a separate Current-only nonvacuity
check. It initializes actual engines and requires native propagation input
reads in both channels; it does not alter the portable Frozen trace.

## Server and final tiers

`VerifyCoordinatorServer.py` takes the standard capture arguments plus optional
`--sides` and `--timeout`. Original player-loading/threaded audit source and
assertions remain unchanged. Added scalar observations require all four native
per-engine phases in both light channels and a whole sky-source batch on Current.
Pre-S0026 already owns native threaded tasks; Frozen does not.

Reopen first checks the original saved blocks/light. A separate test hook then
submits boundary-emitter removal, a roof shadow and restoration to the real main
thread, waits on actual threaded-light barriers and verifies the results. The
original read-only reopen fixture alone does not exercise node/source production.
The audit stores scalar counters rather than engine/world roots and claims no
client, network or timing validation.

Run ordinary, parity and slow Java tasks separately as described in
[testing](../../tooling/TESTING.md), plus the full Rust suite. The candidate's final
Java run is 2,211 ordinary tests (6 skipped), 193 parity and 11 slow, with no
failures. The full Rust run is **2,869 passed, 57 failed, 9 ignored**. All 57
failure names and normalized failure bodies match the earlier host-renderer
run; this is an explicit non-green full-suite result, not a claimed hardware pass.

The latest actual pair uses native `011e83c3` and complete-scope Java21. All 13
changed Java sources compile together. Only `SkyLightEngine.class` changed after
the Java20 full-scope check; normal 27,534/threaded 50,144 observations and the
536-row source gate pass. Eight actual omissions are killed. The protected
192/29/29/23 results retain their original inputs: their native library and Java
facade/adapter classes are unchanged by the last sky-only correction.

## Complete costs and open acceptance

`BenchmarkLightCoordinator.py` uses the standard capture arguments and supports
`--modes direct-empty,direct-seed,queued-retained`. Source production includes COW,
queue production and real block/sky updates. The retained-import window excludes
layer creation/decode; it includes public producers, actual threaded PRE/update/
POST and ownership/publication costs. Observations occur outside timed windows.

`BenchmarkThreadedLighting.py` covers public producers, dispatch and actual
propagation at 0/1/64/1000/2501 operations. Use multiple matched process pairs,
retain every sample/outlier, require compiler-quiet samples and keep builds/tests
outside timing windows. Capture source/class/native/observer guards and noise.

The sealed Java21/native `011e83c3` outer campaign uses three matched process
pairs, 12 samples per process and CPU4. All work, compiler-quiet and identity
guards pass. Current/Frozen ratios are:

| Tasks per cycle | Pair 1 | Pair 2 | Pair 3 |
| --- | ---: | ---: | ---: |
| 0 | 1.1601 | 1.1358 | 1.1811 |
| 1 | 1.3905 | 1.1971 | 1.1754 |
| 64 | 0.6784 | 0.7025 | 0.6811 |
| 1,000 | 0.7505 | 0.7541 | 0.7617 |
| 2,501 | 0.8638 | 0.7967 | 0.8112 |

The general 10% gate fails. Zero work reaches native pending projections through
the Java scheduling carrier, with no dispatch, propagation or measured Java
allocation. This identifies the temporary boundary path, not its CPU percentage.
The first one-task pair exceeds 25% and is also slower than pre-change Current;
it remains a failed result, with every sample retained.

`ProfileLightLifecycle.java` separately brackets the unchanged complete threaded
cycle with GC and reclaimer counter snapshots. Run its package main with task
count, steps per sample and sample count; use `-DthreadedLight.frozen=true` only
for Frozen. Observation stays outside timing. The sealed diagnostic used 24
samples at both 1,024 and 8,192 steps. At 8,192, quiet medians are 45.754 us
Frozen, 62.705 us pre-change and 66.844 us Current. Current's measured windows
include nine GCs, 1,955 ms GC time and 1,560,442 native releases. These counters
show lifecycle activity; they do not isolate native CPU cost or establish an
exception. The diagnostic neither replaces the failed short-window result nor
certifies acceptance.

The sealed Java21 three-pair source/import/empty campaign also passes all work,
compiler-quiet and identity guards:

| Complete cycle | Current/Frozen, three pairs | Current/pre-change, three pairs |
| --- | --- | --- |
| Source production and updates | 0.7859 / 0.7461 / 0.7688 | 1.0864 / 1.0088 / 1.0273 |
| Retained queued imports | 3.9972 / 2.6778 / 2.6195 | 1.3558 / 0.8791 / 0.6753 |
| Empty block and sky updates | 0.7355 / 0.7325 / 0.7373 | 0.6289 / 0.6280 / 0.6308 |

Queued imports fail the general and attributable-boundary budgets. Two pairs
improve on pre-change Current; the first is slower. Its Current samples begin
at 75–101 us and end at 22–26 us, all compiler quiet. Pre-change also has early
high samples. Preserve the complete nonstationary evidence: selecting only the
late samples would not establish acceptance. No exception has been approved.
The coordinator accepted zero under the attributable allowance and the successful
workloads individually; one-task and queued-import costs remain unresolved.
The broader producer/command/lifetime/consumer revision is approved and under
development. Preserve the complete r3 candidate and failed costs while replacing
that ownership boundary; no new candidate acceptance is implied.

The historical Java20 single-pair source/update cost was 138.186 us versus
Frozen 185.282 us and pre-change 138.613 us; retained imports were 22.106 us versus
8.093 us and 24.144 us. These remain observations of that earlier Java image.

Acceptance uses Frozen-relative allowances of 10% generally **or** 25% when
attributable to the temporary boundary, never both added together. In-budget
work does not require improvement over pre-change Current. A separate bounded
over-budget exception requires demonstrated matched Current improvement and
explicit coordinator approval before publication. No earlier assignment's
exception covers this candidate. See the
[migration acceptance policy](../../tooling/MIGRATION-ACCEPTANCE.md).
