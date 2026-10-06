# World generation

Start with the affected evaluator's guide before changing world generation.
Preserve seed compatibility, evaluation order, and Java/native ownership rules.

- [Carving](carver/index.md): canyon geometry, live-mask ordering and focused verification.
- [Old-terrain Blending](blending/index.md): batched height grids and saved-world compatibility.
- [Feature Generation](feature/index.md): ore geometry, geode density fields and placement verification.
- [Heightmaps](heightmap/index.md): packed chunk-column reconstruction and parity checks.
- [Noise Evaluation](RUST-NOISE.md): sampler behavior and parity checks.
- [Density Evaluation](RUST-DENSITY.md): expression evaluation, batching, and verification.
- [Terrain Splines](RUST-SPLINE.md): native curve plans, coordinate bindings, and focused parity/performance checks.
- [Structure Terrain Adjustment](RUST-BEARDIFIER.md): Beardifier cell evaluation, geometry ownership, and parity/performance checks.
- [NOISE Fill](RUST-NOISE-FILL.md): native block loop, ore veins, section/heightmap
  ownership, eligibility gate and parity/performance checks.
- [Noise Router](RUST-NOISE-ROUTER.md): native interpolation slices from compiled
  density graphs, short circuits, cache rules and parity/performance checks.
- [Preliminary Surface Level](RUST-PRELIMINARY-SURFACE.md): per-RandomState native
  surface programs, batched column searches and parity/performance checks.
- [Chunk Noise Instantiation](RUST-CHUNK-NOISE.md): per-seed templates that replace
  per-chunk Java graph wrapping, lazy wrapping and parity/performance checks.
- [Aquifer Evaluation](RUST-AQUIFER.md): cell material decisions, fluid sources, and parity/performance checks.
- [Surface Evaluation](RUST-SURFACE.md): column processing, callbacks, and compatibility limits.
- [Surface Chunk Storage](RUST-SURFACE-STORAGE.md): Rust-owned sections and
  heightmaps shared by the SURFACE and CARVERS stages, the install contract and
  parity/performance checks.
- [World-Generation Organization and Refactor Report](RUST-WORLDGEN-ORGANIZATION.md):
  module ownership and the recorded correctness/performance comparisons.

Recorded measurements describe the versions tested; rerun the relevant checks
before claiming that a later change preserves correctness or performance.
