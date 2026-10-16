# Rust-owned ticket storage

[`TicketStorage`](https://github.com/HungLo2020/MattMC/blob/master/src/main/java/net/minecraft/world/level/TicketStorage.java)
holds every chunk ticket. Active lists and counters live in
[`chunk_distance/ticket_storage/`](https://github.com/HungLo2020/MattMC/tree/master/src/main/rust/world/level/chunk_distance/ticket_storage).
The working deactivation integration uses the actual Java destination map exposed
through virtual `removeTicketIf`: callers may retain and mutate that map, so it
is the one VM backing resource for deactivated tickets. Native commands drive
its decode, activation and persistence walks. There is no production native
copy of that map. The Java facade retains the public API and save codec.
[`NativeTicketStorage`](https://github.com/HungLo2020/MattMC/blob/master/src/main/java/net/minecraft/world/level/NativeTicketStorage.java)
holds downcalls, Ticket bindings and native list views. The `f570cee4` integration
passes the scoped correctness/runtime gates and has a documented temporary
cost exception in [decision275](https://github.com/HungLo2020/MattMC/discussions/888#discussioncomment-18978296).
It exceeds the ordinary budgets; historical image results remain separate.

Java keeps:

- the `Ticket` objects, so callers get the same instances back;
- the published forced-chunk `LongSet`;
- the listener fields;
- the actual deactivation destination map exposed to overrides;
- the readiness answers (`ChunkHolder.isReadyForSaving`).

## Generation integration

The [retained generation work](RUST-CHUNK-GENERATION.md) also owns stock Ticket
counter resources. Its current native ownership is validated with the bounded
cost exception described below; earlier measurements certify their own images.

A stock Ticket keeps its immutable constructor specification until first mutation
or admission. Its actual native cell then owns the live counter, the historical
unbound value and original bind visibility. `boundCell()` stays `-1` until the
original binding step; `unboundTicksLeft()` freezes there and does not become an
alias for the live stored counter. Counter methods retain the original private
TicketType queries. Subclasses select the original field adapter before native
mutation to preserve overridable dispatch.

New admission, decoded append and live-list insert/set prepare actual JVM weak
roots and cleanup resources before publishing an entry. Native-issued commands
select the existing producers. The admission continuation queries actual stored
and incoming tickets in order, then calls the actual stored reset method. List replacement must capture the actual old Ticket before
release-queue draining. Allocation failure must preserve identity/counter aliases
and permit retry.

The native cell has separate entry and resource ownership. An owning read-only
JVM memory segment retains the latter through its auto arena; it has no Ticket
backreference. Manual constructor rollback and registered GC cleanup share an
idempotent action. Native retirement, entry reference changes, queue draining and
slot reuse share the native lifecycle lock. A queued managed cell still needs its
Java unpin event after its last resource retires.

**Each physical list owns its Ticket references.** The process lookup contains
weak Ticket and resource holders. A native map owns list leases and its genuine
VM anchor retains only the corresponding list binding resources. Each list has
its own native membership, counter-entry references and VM Ticket roots. Public
views retain that list resource and only a weak reference to the facade; the
facade caches views weakly. Keeping one view after facade collection therefore
keeps its entries, while unrelated lists and tickets can collect.

Removing or replacing a map entry releases its map lease without draining a
retained list. Such a view remains mutable, independently of a replacement at
the same key or a reused map slot. Shared Tickets retain the same counter across
lists and stores. Native release records distinguish map-to-list resource keys
from list-to-Ticket cell keys; releasing one scope cannot unpin another's root.
Native counter resource ownership remains separate from entry ownership,
including for subclasses. Once admission publishes a foreign Ticket's counter
lease, its binding callback may remove the entry without retiring that lease.
Rollback cleans only an unpublished reservation.

Hold `Cells.LOCK` while registering sources, preparing bindings, applying scoped
release records and draining native retirement. Resource maps use primitive keys
and retain capacity, so release does not allocate. Prepare actual references and
rollback state before publishing an entry. Keep replaced or removed Ticket
objects captured through callbacks and root release; a shared scalar out-buffer
is not that object's lifetime. A global strong Ticket array is invalid: a Ticket
that captures its storage would anchor an otherwise unreachable cycle.

The source-query integration uses the same native owner graph. Loading and simulation
trackers register Java listeners and run resumable native graph operations.
Rust owns the queue and propagation decisions; the operation yields each actual
source query, holder query and setter at its original evaluation point. No graph
lock or borrowed mutable graph reference spans a Java effect. Each invocation
retains its own operation and actual Java resources, so nested updates resume
against the live graph. Simulation setters update the native published view
immediately. Loading setters use actual manager/holder dispatch and ChunkMap's
native scheduling registry; the old deferred bulk replay is no longer the
tracker's execution path.

Minimum-level and debug queries iterate actual Ticket objects under native
selection commands. Candidate and current-best getters retain their original
order, including a getter changing the best ticket's level. A notification-time
source mirror cannot substitute for these live queries. The working source
cursor now advances native list membership and iterator position together for
admission, minimum/debug queries and removal. It groups only unconditional
ordered getter pairs; native commands still select the branch from their answers.
For minima, candidate and best levels precede the candidate's type query when
the original short-circuit requires them. Keep the actual best Ticket captured
across subsequent callbacks.

Native list retirement does not end a retained list's life. A callback can remove
it from storage and append through its retained VM list view. At the next original
iterator query, the native cursor requests that actual backing's size/get at its
existing position. It must not restart or follow a recycled native list index.
An overridden `getTickets` returning a foreign list retains its actual iterator
effects under the existing native selection controller.

The working cursor allocation now belongs to the native source. Reserve a slot
before preparing its actual bounded VM view; open its source operation only
after that binding succeeds. Each nested operation gets a distinct active slot.
Returned slots have nonwrapping incarnations, checked before resume/release and
header use. The source retains immutable VM view descriptors for its peak slot
count, not Ticket payload or active-cursor truth. Close returns a reservation
once without allocating; source retirement frees all native slots. Fence the
actual Java source through native header reads and Ticket lookup so its genuine
VM roots survive, alongside the native lease. No source lock crosses a VM effect.

Admission and expiry use native command controllers for actual virtual Ticket
effects. The subsequent working consumer slice replaces cached removal,
keep-active, persistence, replacement and forced scans with resumable native
walks. Removed-ticket getters, notification-time minima and dirty calls are
separate Java effects at their original points. Null list slots own no Ticket
counter; failed null admission retains the original published list prefix.
Before the lighting sync, the integrated image passed the six full 37,200-step
ticket traces, focused dispatch/reentry and source-lifetime controls, and the
ordinary/parity/slow Java tiers. A subsequent binding-reentry counter regression
was reproduced and repaired. Verification of that repair on the combined image,
broader acceptance and complete-cost measurements remain pending.

The combined pre-cursor image (`44f4fc9b`, base `3f1d4307`) subsequently passed
full Java tiers, all 3,007 Rust tests (nine ignored), all six ticket traces,
holder composition and the retained all-dimension fresh/reopen comparisons.
Its one-round complete-cost pilot failed: quiet loaded requests were 256–260 ns,
versus 23–34 ns Frozen and about 44 ns pre-change; holder reads also exceeded
the allowance. FULL cohorts were inconclusive. An instrumented warmed request
made 30 native calls, including eight Java-iterator size/get calls. That is a
boundary count, not CPU attribution. These receipts remain separate from the
new source-cursor image, whose focused 165 Java cases (162 passed, three isolated
pause skips) and 103 affected Rust tests pass. That image (`3f23b49a`) also failed
its one-round complete-cost pilot: quiet loaded medians were 187–195 ns,
5.49–5.76 times its matched Frozen values; FULL cohorts remained inconclusive.
Warmed boundary counts fell from 30 to eight. Separate allocation diagnostics
reported 240/304 Java bytes per loaded request for fresh/reopen, versus 64 Frozen
and 72 pre-change; native allocation is excluded. These are preserved failed
receipts, not acceptance of the subsequent source-owned allocation prototype.
That source-pool image (`a2a09a65`) subsequently passed the full Java tiers and
3,013 Rust tests (nine ignored), but its one-round cost pilot also failed:
loaded requests were 252/256 ns fresh/reopened, 7.32/7.59 times matched Frozen.
A later retained-view GC control exposed its whole-store Ticket retention;
those earlier happy-path checks did not cover this contract.

The physical-list owner repair (`638e557e`) passes the expanded five collection
cases, including facade GC, later list mutation and same-key replacement, plus
170 focused Java cases (167 passes, three isolated pause skips), 108 affected
Rust tests and all six ticket traces. The sealed original expanded controls
pass all five on Frozen and fail the two retained-view GC cases on `a2a09a65`.
An additional isolated final-list-owner test passes and detects omission of its
retirement completion, after confirming the native map has released its lease.
Adapted admission, scoped-root, counter-adoption, detached-cursor and cursor
reservation mutation controls pass their positives and detect their intended
negative failures. Full tiers and complete-cost acceptance of this repair and
remaining request/cache control work are pending. These are distinct candidate
images; earlier verification does not certify the newer implementation.

A subsequent caller/cache image (`97e76c7d`) passed 175 focused Java cases
(171 passes, four isolated skips), all eight isolated public cache contracts,
109 affected Rust checks, 3,016 full Rust tests (nine ignored), all ticket traces
and the retained four-dimension fresh/reopen comparisons. Review then exposed
a separate consumer alias regression: Frozen and pre-change pass two cases
where a type getter unmaps and repopulates its captured list; this image loses
later keep-active and persisted tickets. Those failures remain retained in
`s25-r6-retained-consumer-original-controls-v2`. Its full-suite passes did not
cover that contract and do not clear this hold.

The physical-list correction (`912063fb`) retains the actual native list in
each active consumer, and captures its genuine VM Ticket-root binding before
any getter callback. Later reads use that live list independently of its former
routing slot. Map-entry keys retain their original reread points. No storage
or list lock spans a getter; close drops the list lease and completes queued
retirement. The three cases are maintained in `LiveTicketConsumerContractTest`. They pass
on Frozen, pre-change and the repaired image. Its paired focused run passes 178
Java cases (174 passes, four isolated skips) and 110 affected Rust checks;
ticket replay and all-dimension fresh/reopen comparisons pass again. The expanded
root control passes 22 positives and detects missing consumer VM capture,
weak-only retention and cross-scope unpinning. An initial GC witness fired during
nested removal and failed to detect the missing-capture mutation; the corrected
witness arms GC only after detachment and then kills it. Both attempts remain
retained. The final Java tiers pass: 2,373 ordinary passes (six skips), 193 parity and
11 slow tests; all 3,017 Rust tests pass (nine ignored). The first slow runner
was interrupted during timing preparation; its separate complete rerun passes.
The one-round six-process complete-cost pilot still fails: quiet loaded medians
are 325/289 ns fresh/reopened, 9.35/8.45 times matched Frozen. Holder reads are
1.67/1.55 times Frozen; FULL has only two/four quiet Current samples and remains
inconclusive. Work/output/image seals match, JVM swap is zero and host swap is
257 pages in, zero out. Evidence: `s25-r6-caller-complete-cost-pilot`.

The coordinator subsequently recorded an owner-approved interim exception for
the exact `912063fb` loaded-request and holder measurements, including their
regression against pre-change Current. This is limited to the documented costs;
FULL performance remains inconclusive and all correctness/runtime gates still
apply. See [decision268](https://github.com/HungLo2020/MattMC/discussions/888#discussioncomment-18977110).
The retained debt belongs to HungLoDE/S25. Its exit requires future assignments
to migrate the actual Ticket/holder/future/executor resources and dynamic
dispatch with their producers/consumers, preserve callback/alias/error/lifetime
semantics, and rerun these pinned complete workloads. Generated VM control and
skipped virtual effects are excluded.

A concurrent independent review found another hold: a bulk `removeTicketIf`
predicate can remove its entry through a nested walk, repopulate the captured
list and return false. Frozen still visits its next ticket; pre-change and
`912063fb` skip it. Originals remain in `s25-r6-bulk-retained-original-controls`.
The correction (`f570cee4`) retains physical list leases in native walk/start
state and supplies genuine VM-list resource identities with predicate batches.
Java captures only the current list's actual Ticket roots, including across
resume and GC. Native prefetch does not VM-root unvisited lists. The exact
approved `912063fb` capsule stays immutable; the newer image has its own
correctness verification and explicit cost exception275 below.
Its paired run passes 180 focused Java cases (176 passes, four isolated skips),
110 affected Rust checks, 27 lifetime positives and all four intended VM-anchor
and cross-scope mutations. All six ticket traces pass again. Both straight and
interrupted FULL runs compare 648 chunks per complete runtime across all four
dimensions: exact pinned pre-change equality, three standard dimensions exact
with Frozen and every known Primordial difference retained. Source/class/native
seals and actual candidate/pre native mappings are verified.

The two 16,000-step holder replays match retained original/adapted baselines;
23,196,560 reader observations have no inconsistency and all 992 hash-order
oracle passes agree (441,538 holders). Native and Java injected failures both
restore all nine touched chunks; the failed chunks stay at CARVERS in memory
and absent on disk. Cancellation comparisons pass on Frozen, Current and
Current's Java-decoration route: each pair has 169 same-status chunks and no
mismatch below FEATURES without a decorated neighbour. Later-stage differences
remain reported as scheduling-dependent; this is not whole-save-form equality
under concurrent cancellation. The first cancellation wrapper blocked its
child on the same driver lock; only that harness attempt was stopped, the
completed failure evidence was preserved, and cancellation reran independently.
All 3,017 Rust tests pass (nine ignored). A further maintained detached-removal
case checks three actual getter calls and dirty before the original live-entry
fault. Final Java tiers now pass on this image: 2,376 ordinary passes (six skips),
193 parity and 11 slow. The maintained detached fixture has six positives; the
expanded root control has 28, with all intended mutations detected. The subsequent 18-process cost campaign has sufficient quiet FULL samples in
every cohort and fails that budget. It uses three rotated rounds on each
runtime's fresh/reopened server, 512 warmup requests and 45 batches of 16 new
FULL requests; only the supported sample count increased from the earlier
15-batch pilot. Work, saved-output, runtime and mapped-image seals match.

| Round / phase | Frozen ms/FULL | Pre-change ms/FULL | Current ms/FULL | Current/Frozen | Quiet Current / 45 |
| --- | ---: | ---: | ---: | ---: | ---: |
| 1 fresh | 0.562 | 1.037 | 1.343 | 2.388 | 8 |
| 1 reopen | 0.535 | 0.861 | 0.992 | 1.856 | 13 |
| 2 fresh | 0.509 | 0.971 | 1.309 | 2.573 | 11 |
| 2 reopen | 0.557 | 0.834 | 1.093 | 1.964 | 15 |
| 3 fresh | 0.753 | 0.831 | 1.315 | 1.747 | 7 |
| 3 reopen | 0.496 | 0.838 | 0.976 | 1.968 | 19 |

Loaded requests cost 286–328 ns, 7.56–12.25 times their matched Frozen values;
holder reads are 1.54–1.69 times Frozen. These operation ratios are not measured
whole-game impact or CPU-share attribution. All JVM swap is zero; peak RSS is
5,478,113,280 bytes, with 101 host pages swapped in and none out. Raw attempts,
all 45 samples and quiet counts remain in `s25-r6-bulk-final-complete-cost`.
FULL was outside decision268's loaded/holder-only exception; it required the
separate review and approval275 below. Bounded attribution on unchanged code records 10,465 selected-adapter native
transitions per fresh FULL request and 6,246 reopened, versus pre-change's
1,057/1,047. Most added groups are task continuation stepping and holder
exchange/change/dependency operations. A separate JFR-only fresh control leaves
production class bytes untouched apart from the existing audit boot hook:
server-thread CPU is about 0.512 ms/FULL versus 0.298 ms pre-change, while exact
server-thread Java allocation falls from about 242 KB to 205 KB. These are
profile observations, not acceptance timings or a precise apportionment of the
0.131–0.484 ms acceptance delta.

Sampling points to holder exchange/change and native snapshot calls. Source
review confirms additional native snapshot map copying, incarnation-vector
allocation and Arc retention; only one actual view is materialized per FULL in
the counted batch, despite 11–16 promotion-entry calls. Managed waits also
increase in the profile. Native `perf` access is denied on this host, so native
execution, native allocation/retirement and native lock waiting remain
unseparated. Call counts and JFR allocation weights are not CPU shares, native
allocation totals or whole-game impact. Observations and JFR-only controls are
separate in `s25-r6-full-attribution` and its `-jfr-only` sibling; all workload,
output and source/image seals match.

[Owner policy272](https://github.com/HungLo2020/MattMC/discussions/888#discussioncomment-18978065)
removes pre-change improvement as an eligibility floor for case-by-case
coordinator exceptions. [Decision275](https://github.com/HungLo2020/MattMC/discussions/888#discussioncomment-18978296)
explicitly approves the exact `f570cee4` FULL, loaded-request and holder costs
across all six cohorts and 18 processes, with all raw rows, quiet counts and
attribution limits retained. It covers FULL 0.975897–1.342893 ms/request,
1.7471–2.5734 times Frozen and 1.1519–1.5826 times pre-change; loaded requests
286.11682–327.653605 ns and holder reads 5.2008–5.91037 ns. This supersedes the
performance hold for this image without reassigning the earlier `912063fb`
approval or changing the general thresholds. Correctness, memory safety and
save integrity have no exception.

HungLoDE/S25 owns the retained debt. The exit is a future coherent native
future/dependency/snapshot producer-state-consumer slice that reduces genuine
VM resource handoffs and native snapshot copying/retirement. It must preserve
dynamic callbacks, aliases, GC and fallible publication, then demonstrate lower
cost on these same complete workloads and original baselines. The measured
transition counts and snapshot work are verifiable starting points. Do not skip
virtual effects, introduce a generated VM control engine or resume bridge tuning
inside this accepted slice. The broader migration remains open; this exception
is specific to this measured scope, not a whole-game/FPS claim.

The ticket observer requires a complete sealed pre-change runtime and an explicit
Frozen native directory. It compiles the same fixture for each side and uses
that side's classes and native image. It also runs the original observer pinned
at `5f9c0b3b819a72d374882418c0e7c08145096ac4` on Frozen to check fixture equivalence.
The adaptation changes only readiness-map lookup and save-dependency setup;
trace operations, RNG and assertions are unchanged. Never compile old storage
sources over a newer candidate library: native cell layouts have changed.

Counter ownership mutation drivers in `DevUtils/tests/world/tickets/` take
`--output <new artifact directory>` and optionally `--classpath-file` and
`--java-home`. They compile isolated copies and guard production inputs:

- `MutateTicketCounterAdoption.py`: registered arena cleanup plus rollback;
- `MutateTicketPublishedCounter.py`: entry removal during binding must retain
  the live Ticket's published counter lease;
- `MutateTicketCellAdmission.py`: root preparation before entry publication;
- `MutateForeignTicketRegistration.py`: foreign reservation cleanup;
- `MutateTicketScopedRoots.py`: per-list retention, unreachable cycles,
  source-specific release and actual captured-consumer VM roots after detachment;
- `MutateTicketDetachedCursor.py`: minimum, admission and removal continue at
  their original position after a callback retires and mutates the actual list;
- `MutateTicketCursorAdoption.py`: binding failure precedes source mutation,
  returns its reservation for retry, and repeated close releases once;
- `MutateTicketCellRetirement.py`: native lifecycle lock and queued unpin events;
- `MutateTicketStoreRelease.py`: final store, graph and retained-list ownership and JVM unpin completion.

Each requires its positive control and its specific intended negative failure.
The double-release controls trap the second raw release rather than performing
an unsafe native free.

Native store cleanup completes queued source releases and counter retirement
after the native release returns. Generation graph and task resources that can retain the store
do the same after releasing their lease. A complete generation resource is a record containing the native segment and
its actual VM source. Retaining that record keeps the source and Ticket objects
available; retaining a bare native address is not a complete VM binding lease.
After the last owner retires, its native counter data must retire without waiting
for another storage operation. Cleanup actions retain handles and ownership capabilities,
not the Java source/store objects. Empty-store rollback avoids initializing the
root registry. `MutateTicketStoreRelease.py` checks ordinary store collection and
retained generation ownership and retained-list final ownership, then removes
list-only and all-owner completion in isolated adapters to require the intended
retirement failures.

Deactivation must call the actual virtual `removeTicketIf` with the real
`Long2ObjectOpenHashMap<List<Ticket>>` destination. It cannot pass an empty
placeholder or synchronize a second container after callbacks. An override can
retain the map, insert foreign lists and change what activation later sees.
The VM map retains its original identity; native-issued commands choose each
iterator query, getter, add and clear in their original order. Active-list
removal into any destination sizes a new list from the live source entry when
the destination invokes its mapping function.

## Preserve these contracts

- **Table order is observable.** It decides listener order, expiry order, save
  order and the forced set's iteration order. [`map.rs`](https://github.com/HungLo2020/MattMC/blob/master/src/main/rust/world/level/chunk_distance/ticket_storage/map.rs)
  is an exact fastutil 8.5.12 `Long2ObjectOpenHashMap` port. It covers initial
  sizes 4 and 16, growth, shrinking on removal, key 0, and the iterator's
  wrapped-key list. Keep it exact; do not swap in a Rust hash map.
- **`removeTicketIf` is a resumable walk.** Cursors are plain indices, as Java's
  iterators were:
  - an entry's `getValue()` is re-read from its table slot;
  - the list iterator keeps `pos`/`last`;
  - a callback may mutate the storage between steps, which gives the original
    positional results (including its exceptions).

  The walk returns to Java only for a readiness question, a Java predicate or a
  Java listener. It must never hold a native borrow across one.
- **Java predicates run in batches.** Rust offers the next tickets as
  `(key, cell)` pairs, up to 256 and sized to recent use. With each it records
  the walk state just after that ticket's `next()`. Java tests them in order and
  stops at the first match, or once a predicate changed the lists (the
  facade's modification count). Rust then restores the recorded state and
  applies the answer.
  - This is exact because the original has no side effects between tickets
    whose predicate is false, including across entries where nothing was
    removed. A batch stops at the end of an entry with removals.
  - Filling never raises a fault early; the live walk raises it in place.
  - `Ticket` objects and fields are read at call time, never prefetched.
  - `-Dmattmc.ticketStorage.batch=N` fixes the size for parity runs.
- **Java notifications in a walk are paired.** A loading notification carries
  the simulation level read at the same moment. Java delivers both, unless the
  first listener changed the lists or the simulation listener is no longer a
  Java one.
- **Readiness is asked in walk order, one ticket at a time,** exactly where
  `canTicketExpire` read it (timed types without `CAN_EXPIRE_IF_UNLOADED`). A
  future completed by an earlier entry's listener is seen by later tickets. Do
  not batch or pre-read it.
- **`ticksLeft` is shared by identity.** A stored `Ticket` binds to one native
  cell ([`slab.rs`](https://github.com/HungLo2020/MattMC/blob/master/src/main/rust/world/level/chunk_distance/ticket_storage/slab.rs)).
  - Every entry, every storage and the Java object read and write that cell:
    Java reads it directly at its address, Rust decrements it in the purge.
  - Stock counter resources retain their cell independently of entry references;
    foreign Ticket adapters use the original Ticket cleaner. A cell cannot be
    reused while an entry, actual resource or pending unpin still needs it.
    Tickets may move between threads as before.
  - While storage entries refer to the cell, `Cells` pins the ticket strongly,
    as the lists held it. When the last entry goes, the cell is queued, and a
    drain under `Cells.LOCK` unpins it to a weak reference if it is still
    unreferenced. Every re-store re-pins under the same lock. Walks drain once,
    at their end.
  - A ticket is used by one thread at a time, as its field was.
- **Production tracker queries are live.** Loading and simulation listeners enter
  their resumable native graph operations through Java. Each source query reads
  the actual storage at the original graph evaluation point. Compatibility
  native-listener entry points remain, but a cached ticket level cannot replace
  observable getter dispatch.
- **Locking.** Each graph and storage has its own native lock. An operation may
  acquire a lock for one native step; it releases it before publishing an effect
  to Java. Do not retain a borrowed graph or storage reference across an effect.
  Reentry must resume the original operation against the live state.
- **The forced set is published by Java.**
  - Adding a `FORCED` ticket adds to the same set object.
  - Removing one rebuilds a new set in table order (`getAllChunksWithTicketThat`).
- **`getTickets` returns a live view** of the native list. It reads and
  writes in place (`add`/`set`/`remove`, bypassing listeners and the forced set,
  as writes to the original `ObjectArrayList` did) and keeps its identity while
  the list lives.
  - A list leaves the storage only when emptied, through `removeTicket` or a
    walk. From then on, a retained view is that detached list: it starts empty,
    accepts writes and no longer affects the storage.
  - A later ticket at the chunk gets a new list and view.
  - Absent chunks return `List.of()`, as before.
  - Writing directly to a list breaks the trackers' ticket-level mirror,
    exactly as it bypassed the original's notifications. No production caller
    writes to these lists.
- **`activateAllDeactivatedTickets` iterates the live deactivated map**
  (walk kind `Activate`). A listener that deactivates or reactivates during
  activation sees and causes the original positional results, including
  exceptions.
- **A Java destination map** in `removeTicketIf` gets lists of default
  capacity; capacity is not observable.

## Verify and measure

```bash
python3 DevUtils/tests/world/tickets/VerifyTicketStorage.py --java-home "$JAVA_HOME" \
  --pre-runtime <sealed complete runtime> --frozen-native-dir <Frozen native directory> \
  --output build/ticket-storage-diff [--benchmark]
cargo test --release --lib ticket_storage   # from src/main/rust, or ./gradlew testRustNative
./gradlew test -PmattmcRustProfile=release --tests 'net.minecraft.server.level.Native*ChunkTrackerTest'
python3 DevUtils/tests/world/tickets/VerifyTicketStorageServer.py --java "$JAVA_HOME/bin/java" \
  --classpath-file main-classpath.txt --native-dir build/rust/native --output build/ticket-server \
  [--class-overlay <pre-change classes>]
```

[`VerifyTicketStorageServer.py`](https://github.com/HungLo2020/MattMC/blob/master/DevUtils/tests/world/tickets/VerifyTicketStorageServer.py)
runs a disposable dedicated server. It forceloads chunks in two dimensions,
removes one, then saves and restarts twice. Console assertions check which
chunks are loaded. Run it on the candidate and on a pre-change overlay; both
must pass.

The [driver](https://github.com/HungLo2020/MattMC/blob/master/DevUtils/tests/world/tickets/VerifyTicketStorage.java)
runs the real `TicketStorage` on Frozen, on the pre-change sources compiled over
the candidate classpath, and on the candidate, and requires identical trace digests.

- **Operations:** adds (new, shared instance, same type/level re-add, radius),
  removals, forced toggles, purge, close/reopen, level replacement, codec
  save/load and Java `removeTicketIf` predicates. The predicates log each call,
  change later shared tickets, mutate the lists and throw.
- **Holders:** a stub `ChunkMap` serves holders whose save futures the driver
  controls. A listener completes some of them mid-walk.
- **Callbacks:** a listener sometimes adds, removes, deactivates or reactivates
  mid-walk or mid-activation; the exception class and resulting state must match.
- **Views and a second storage:** retained `getTickets` views (also written
  through) persist across list removal and re-creation, and a second storage
  shares Ticket objects.
- **Observed each step:**
  - listener calls with notification-time level reads;
  - lists, ticket identity and retained views;
  - `ticksLeft` and debug strings;
  - forced-set identity and order, and replaced sets left unchanged;
  - keep-active.
- **Tracker seeds:** seeds 9–12 attach the real `SimulationChunkTracker` (natively
  notified) next to a Java loading listener, and trace its levels. They skip
  direct list writes, which the tracker mirror is not specified for.
- **Large seeds:** seeds 13–14 hold 700 tickets, so predicate walks cross batch
  boundaries.
- **Fixed batch sizes:** the runner also runs the candidate at sizes 1 and 256
  (`--batch-capacities`); every trace must match.

`--benchmark` adds two workloads and checks that all three sides do the same
work (checksum):

- **Production-listener path:** server-shaped churn (portal, ender-pearl,
  spawn-search, forced, dragon and unknown tickets, untimed removals and
  `purgeStaleTickets` against holders), driving a real `DistanceManager`'s
  trackers with production listeners.
- **Custom-listener path:** the original S0006 churn baseline. Java listeners
  and a Java `removeTicketIf` predicate make every notification and predicate
  a Java call.

The tracker parity tests drive real `DistanceManager`s, so they cover direct
native notification against the pinned Java trackers.

## Measurements

The implementation author recorded these on 2026-10-15, using
`VerifyTicketStorage.py --benchmark` on a release build with JDK 25. Each side
ran 3 alternating rounds of 10 samples in fresh JVMs, and every side did the
same work (equal checksums). The run measured the published candidate on
master `fb7f3fa0` (source pin `9abf0fcf…`, native `3c779238…`).

| Workload (median per run) | Frozen | Before this change | Native storage |
|---|---:|---:|---:|
| Production listeners: server-shaped churn, real trackers | 329.0 ms | 248.5 ms | 256.4 ms |
| Custom listeners: Java listeners and a Java `removeTicketIf` predicate | 13.21 ms | 13.28 ms | 40.69 ms |

**Production listeners** run 22% faster than Frozen, mostly from the earlier
native graphs, and are about neutral against the code before this change.

**Custom listeners** run about 3.1× slower (2.9× in the run the owner reviewed:
38.34 against 13.05 ms). Every Java listener call or
predicate match is a native step, where the original made an in-JVM call. Each
newly stored ticket also registers a cleaner.
- Phases on the same workload, Frozen against native, measured before the
  final lifetime protocol: adds 5.2 against 6.5 ms; removals 3.7 against
  4.0 ms; the predicate walk 13 against 34 ms.
- No production code takes this path:
  - the game binds only the native trackers as listeners;
  - its walks use native predicates;
  - nothing calls `removeTicketIf` with a Java predicate.

### Accepted migration debt

This cost exceeds the [interim allowances](../../tooling/MIGRATION-ACCEPTANCE.md#performance-comparisons).
The owner accepted it on 2026-10-15 as temporary migration debt for exactly
this path: custom Java listeners and Java `removeTicketIf` predicates. It is
not a waiver for any other workload or regression.

Resolve it by moving the remaining Java callers onto native listeners or
predicates, or by removing the Java-callback API once nothing uses it. Do not
optimize the callback bridge itself.

## Troubleshooting

- *`UnsatisfiedLinkError … mattmc_ticket_storage_*`:* the harness loads
  `build/rust/native`. Rebuild with `./gradlew buildRustNative -PmattmcRustProfile=release`.
- *Trace differs at a purge:* check readiness order and the walk's table order
  before anything else.

The earlier global-array re-pin barrier driver (`MutateTicketRePin.py`) describes
the retained pre-scope protocol. Its historical receipts do not certify current
scoped roots. Use the scoped-root, managed-retirement and publication controls
above for the working implementation.
