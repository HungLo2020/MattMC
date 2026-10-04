# World lighting

Server-side light-source data and propagation are separate from rendering.

- [Rust skylight-source reconstruction](RUST-SKYLIGHT-SOURCES.md): packed scans,
  exact occlusion tables, ownership and focused acceptance checks.
- [Rust leveled work queue](RUST-PRIORITY-QUEUE.md): the queue behind the Rust
  chunk/section distance graphs, its contracts and tests.
