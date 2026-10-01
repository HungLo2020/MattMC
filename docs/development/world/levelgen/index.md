# World generation

Start with the affected evaluator's guide before changing world generation.
Preserve seed compatibility, evaluation order, and Java/native ownership rules.

- [Feature Generation](feature/index.md): ore geometry and placement verification.
- [Noise Evaluation](RUST-NOISE.md): sampler behavior and parity checks.
- [Density Evaluation](RUST-DENSITY.md): expression evaluation, batching, and verification.
- [Terrain Splines](RUST-SPLINE.md): native curve plans, coordinate bindings, and focused parity/performance checks.
- [Structure Terrain Adjustment](RUST-BEARDIFIER.md): Beardifier cell evaluation, geometry ownership, and parity/performance checks.
- [Aquifer Evaluation](RUST-AQUIFER.md): cell material decisions, fluid sources, and parity/performance checks.
- [Surface Evaluation](RUST-SURFACE.md): column processing, callbacks, and compatibility limits.
- [World-Generation Organization and Refactor Report](RUST-WORLDGEN-ORGANIZATION.md):
  module ownership and the recorded correctness/performance comparisons.

Recorded measurements describe the versions tested; rerun the relevant checks
before claiming that a later change preserves correctness or performance.
