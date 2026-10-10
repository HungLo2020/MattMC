# World lighting

World light-source data, live light storage and propagation are separate from
rendering. Live layers also support independent client packet imports. The
renderer’s [bulk terrain-light consumer](../../rendering/RUST-TERRAIN-LIGHTING.md)
borrows retained generations; Java still owns light-engine orchestration and
contextual rendering callbacks.

- [Rust skylight-source reconstruction](RUST-SKYLIGHT-SOURCES.md): packed scans,
  exact occlusion tables, ownership and focused acceptance checks.
- [Rust light-map publication](RUST-LIGHT-MAPS.md): shared 64-shard snapshots,
  direct block/sky scalar reads, CPU identity pins, sky metadata and compatibility
  contracts.
- [Rust live light layers](RUST-LIVE-LAYERS.md): retained light ownership, direct
  propagation handoffs, array compatibility and current verification scope.
- [Rust light propagation](RUST-LIGHT-PROPAGATION.md): block and sky light
  queues and sky-source seeding in Rust, section callbacks, parity and benchmarks.
- [Rust leveled work queue](RUST-PRIORITY-QUEUE.md): the queue behind the Rust
  chunk/section distance graphs, its contracts and tests.
