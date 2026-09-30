# World generation

Start with the affected evaluator's guide before changing world generation.
Preserve seed compatibility, evaluation order, and Java/native ownership rules.

- [Noise Evaluation](RUST-NOISE.md): sampler behavior and parity checks.
- [Density Evaluation](RUST-DENSITY.md): expression evaluation, batching, and verification.
- [Surface Evaluation](RUST-SURFACE.md): column processing, callbacks, and compatibility limits.
- [World-Generation Organization and Refactor Report](RUST-WORLDGEN-ORGANIZATION.md):
  module ownership and the recorded correctness/performance comparisons.

Recorded measurements describe the versions tested; rerun the relevant checks
before claiming that a later change preserves correctness or performance.
