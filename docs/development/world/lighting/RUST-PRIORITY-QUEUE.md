# Rust leveled work queue

[`priority_queue/`](https://github.com/HungLo2020/MattMC/tree/master/src/main/rust/world/level/lighting/priority_queue)
is the leveled FIFO work queue behind the Rust
[chunk and section distance trackers](../chunk-loading/index.md). It keeps the
original `LeveledPriorityQueue` semantics plus the graph's compound scheduling
(`reschedule`, `cancel_computed`, `enqueue_computed`). It is Rust-internal: the
Java adapter, `DynamicGraphMinFixedPoint`, `ChunkTracker` and `SectionTracker`
were removed once every distance tracker moved to Rust. Block-light and
skylight propagation use other queues.

## Preserve these contracts

- Duplicates keep their FIFO position; removal then reinsertion appends. The
  same value can exist independently at different priorities; every bit of the
  value is preserved.
- A scan honours the supplied bound, including empty buckets. An overlong scan
  removes the entry and publishes its bound before failing; do not normalize it.
- A reschedule performs the original removal then insertion, keeping partial
  state if either fails. 255 is the graph's absent-computed sentinel, not a level.
- Ordered tables stay at most one-quarter full (except the one-entry minimum),
  reuse capacity until growth and never allocate on removal. Allocation is
  fallible and reported, never a process abort.

## Verify

```sh
(cd src/main/rust && cargo test --release priority_queue)
python3 DevUtils/tests/server/VerifyRustPlayerChunkDistances.py --parity-only
```

Rust tests cover insertion order and slot reuse, bounded scans and failure
state, the ordered table against a FIFO model, and every compound operation
against the original primitive dequeue/enqueue sequence, failures included.
The distance driver exercises the queue end to end through all four trackers
against the pinned original Java graph and queue oracles
(`JavaDynamicGraphMinFixedPoint`, `JavaLeveledPriorityQueue`).

## Historical measurements

Before the graphs moved, the queue was a native object behind a Java adapter
called once per queue operation. On 2026-10-03 that adapter measured 9–12%
faster than the original Java queue on single-ticket, overlapping-ticket and
churn workloads (54 JVMs, three pairs each), with mixed caller-level results.
Those numbers describe the removed adapter path; current performance is
measured per tracker in the chunk-loading pages.
