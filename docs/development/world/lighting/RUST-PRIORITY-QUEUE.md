# Rust-owned leveled work queue

[`LeveledPriorityQueue`](https://github.com/HungLo2020/MattMC/blob/master/src/main/java/net/minecraft/world/level/lighting/LeveledPriorityQueue.java)
is the Java lifetime/exception adapter over
[`priority_queue/`](https://github.com/HungLo2020/MattMC/tree/master/src/main/rust/world/level/lighting/priority_queue).
Rust owns membership, FIFO order, storage, priority selection, rescheduling and
priority scans. Production has no Java queue implementation or fallback.

Current callers are chunk-distance and POI section-distance trackers through
`DynamicGraphMinFixedPoint`; block-light/skylight propagation uses other queues.
Graph traversal, callbacks and the computed-level map remain Java.

## Preserve these contracts

- Duplicates keep their FIFO position. Removal then reinsertion appends. The same
  long can exist independently at different priorities. Preserve every position bit.
- Preserve the supplied scan bound, including empty buckets. An overlong scan
  removes the entry and publishes its bound before throwing; do not normalize it.
- A Java reentrant lock excludes simultaneous native access. Graph entry operations
  hold it across their existing work; nested queue calls reuse ownership.
  Release acquired scopes in `finally`. The graph/map still require an exclusive
  caller, as before.
- Mutations are immediate. A reschedule combines the original remove/add sequence,
  which has no intervening Java callback. Do not defer operations or skip an enqueue
  based only on the computed map: callback failures can leave those states different.
- Private numeric IDs encode native addresses. Auto arenas own cleanup and ordinary
  pop scratch. Fence **both lifetime segments** across every scalar downcall.
  Never expose/retain an ID without its owner. Scratch and diagnostic reads use the
  same lock. See the [JDK lifetime contract](https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/lang/ref/Reference.html#reachabilityFence(java.lang.Object)).

The graph suppresses an identical pending-map write only after the queue operation
succeeds. When its earlier computed value already equals the new value, it rereads
current map state: a callback may have reentered and changed it. Absence is unsigned
255; a clamped destination cannot equal that sentinel. Queue actions and callback
order are preserved.

## Native safety and storage

Ordinary calls can allocate. Separate critical calls never allocate, free, block or
call Java: probes/gap preflights stop after 32 cells and priority scans allow at most
256 levels. Rescheduling preflights both buckets before committing; unsuitable
operations request ordinary retry **before any mutation or output write**.

Scalar bindings use `critical(false)`. Fast pop uses `critical(true)` to borrow one
private heap long under the queue lock; Rust writes it during the call and never
retains its address. Ordinary retry uses arena scratch and copies the result back.
Never bind an allocating/unbounded operation as critical. See the
[JDK critical-call contract](https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/lang/foreign/Linker.Option.html#critical(boolean)).

Graph levels, including the absent-computed sentinel, fit in bytes and travel in
one packed scalar. The adapter routes out-of-domain signed integers through the
ordinary full-width protocol, preserving invalid-input behavior.

Dense ordered hash tables keep keys separate from FIFO links and pop without a
membership lookup. Tables stay at most one-quarter full, except their smallest
one-entry allocation, and reuse capacity until growth. This trades approximately
twice the table storage of a half-full table for shorter probes and avoids shrink
allocation churn. A queue releases its retained capacity on cleanup. Allocation
is fallible; native memory usage and allocation-failure thresholds differ from Java.

## Verify and measure

```sh
python3 DevUtils/tests/lighting/VerifyRustLeveledPriorityQueue.py --parity-only
python3 DevUtils/tests/lighting/VerifyRustLeveledPriorityQueue.py --forks 3 --cpu 5 --background-cpus 0,1 \
  --output build/lighting-priority-queue-migration/acceptance-fixed
```

The Linux driver audits literal original queue/graph sources against Git
`cfa7057b6`. Fourteen Java and ten Rust tests cover the migration. The audit permits only documented scopes, native scheduling calls and the
unchanged-value write check in the production graph. Focused tests cover exhaustive
short histories, seeded valid/invalid transitions, failure messages/state, growth,
wrapped collisions, ownership, GC, callback failure/recovery/reentry, level limits
and ordered graph callbacks. Rust checks allocation-free critical paths and retry
non-mutation. No renderer or arbitrary game tests are required. Finite coverage is
regression evidence, not proof of every history or allocator failure.

Queue timings replay actual scheduling intents and access scopes; Java executes
the original priority/remove/add actions. Caller timings run the real `ChunkTracker`
algorithm with equal controlled worlds, callbacks and ticket changes. Primary
workloads drain with `Integer.MAX_VALUE`, as production distance/POI callers do;
additional caller workloads use incremental budget 127. All boundary, ownership,
data movement, growth retries and output checksums are timed. Construction/capture
is excluded equally; no cached outputs or timing-only shortcuts are used.

Each JVM warms for at least 15 seconds with stable recent medians and no JIT activity
in measured samples. Three independent pairs alternate order. Source/library hashes,
input trace hashes, outputs, raw rounds and machine/JVM metadata are recorded under
`build/lighting-priority-queue-migration/acceptance-fixed/`. These controlled caller
workloads do not measure whole-game performance. Thread CPU time is also recorded
to expose scheduling interference; it includes native execution but excludes waiting
and concurrent worker CPU. Elapsed time remains the primary result.

## Measurements

The historical HashMap/node-slot version took roughly twice the Java queue time;
`build/lighting-priority-queue-migration/acceptance/` records that obsolete version.
The revised full release run (2026-10-03) used 54 JVMs, three pairs per
workload, with CPU 5 for the mutator and CPUs 0/1 for JVM workers.

| Queue workload | Paired median time reduction | 95% reduction interval |
|---|---:|---:|
| Single ticket | 9.9% | 5.1–11.9% |
| Overlapping tickets | 12.3% | 4.8–20.5% |
| Ticket churn | 8.8% | 7.7–28.0% |

Every queue pair improved by more than 5%. The stricter confidence criterion
passed single-ticket and churn; overlap narrowly missed it. Caller medians were
3–4% faster for overlap/churn, but 4–5% slower for single-ticket workloads.
Several caller intervals were wide. **There is no uniform caller speedup claim.**
The queue's original roughly 2× slowdown is resolved; prefer the adjacent graph
scheduling boundary for further work rather than forcing a micro-optimization.

Raw results and the detailed report are in
`build/lighting-priority-queue-migration/acceptance-fixed/{results.json,REPORT.md}`.
An earlier incomplete run with heavy CPU contention is retained separately as
`acceptance-fixed-interrupted-noisy/`; it is not part of these statistics.
