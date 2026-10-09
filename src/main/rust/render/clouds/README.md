# Cloud CPU sources

Built-in DH cloud motion, placement, corner culling and color-change history
live here. This module has no GAL/backend or presentation dependency. Java
supplies bounded camera/configuration values and keeps ordered API callbacks,
texture-derived box registration and semantic world-color queries.

- `mod.rs`: mutable group policy and arithmetic matching Frozen OpenGL.
- `culling.rs`: scalar corner tests and DH's float normalizer.
- `ffi.rs`: unique CPU owner, ordinary downcalls and a 16-pose history.
- `tests.rs`: policy boundaries; the Java tests independently exercise the
  former object-based calculation and public API overrides.

Native generic-group decoding reads a requested owner/pose and copies its
origin. Queued submissions decode before returning; the older pipelined route
pins owners until join. No owner pointer survives in GPU work. Java coordinate
projections serve API observers, never canonical render input.
