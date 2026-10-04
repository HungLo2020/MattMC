# World lighting

Server-side light-source data and propagation are separate from rendering.

- [Rust skylight-source reconstruction](RUST-SKYLIGHT-SOURCES.md): packed scans,
  exact occlusion tables, ownership and focused acceptance checks.
- [Rust-owned leveled work queue](RUST-PRIORITY-QUEUE.md): chunk/POI distance
  scheduling, native lifetime, exact transition tests and production-call timings.
