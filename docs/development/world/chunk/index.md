# Chunk storage

- [Rust ordered palette values](RUST-PALETTE-DISTINCT.md): first-occurrence biome
  and block scans, callback compatibility and complete caller verification.
- [Rust global palette loading](RUST-PALETTE-UNPACKING.md): saved-data repacking,
  exact codec compatibility and complete caller performance verification.
- [Rust palette resizing](RUST-PALETTE-RESIZE.md): bulk storage remapping during
  block palette growth, ownership constraints and focused verification.
- [Rust palette histograms](RUST-PALETTE-HISTOGRAM.md): ordered counting and
  block/fluid counter reconstruction with focused parity/performance checks.
- [Rust palette packing](RUST-PALETTE-PACKING.md): block-section save packing,
  exact serialization compatibility, and focused performance verification.
- [Rust chunk section serialization](RUST-CHUNK-SECTIONS.md): saving a chunk's
  sections list as NBT tape in Rust, exact key order and the save path.

- [Rust loaded-section snapshots](RUST-SECTION-SNAPSHOTS.md): immutable native
  rebuild state, Java compatibility views, bulk halo reads and lifetime rules.
