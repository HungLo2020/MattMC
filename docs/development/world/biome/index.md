# Biomes

- [DH cached tint neighborhoods](RUST-DH-TINT.md): native canonical cache storage,
  borrowed full-data sampling and preserved resolver boundaries.

- [Climate lookup](RUST-CLIMATE.md): native biome selection, compatibility constraints,
  and the independent Java comparison.
- [Biome fill](RUST-BIOME-FILL.md): the Rust-owned BIOMES stage (climate sampling,
  searches and section containers), its gate and parity/performance checks.
- [Biome searches](RUST-BIOME-SEARCH.md): Rust `findBiomeHorizontal` and
  `findClosestBiome3d` for multi-noise sources (ring placement, `/locate biome`).
- [Section color snapshots](RUST-SECTION-COLORS.md): shared native world-color
  inputs, literal-provider compatibility, lifetime and chunk rebuilding checks.
- [Live biome ownership and color sampling](RUST-LIVE-BIOMES.md): retained native
  sections/index, live identity queries, direct sky/fog sampling, generation-validated reuse and scoped
  Frozen verification.

- [Shared biome temperature](RUST-TEMPERATURE.md): native temperature policy, shared per-thread FIFO history, callback ordering and verification.

- [Shared biome weather policy](RUST-WEATHER.md): native precipitation/freezing/snow decisions, actual temperature-child lifetime and ordered world observations.
