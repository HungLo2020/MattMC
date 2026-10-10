# Biomes

- [Climate lookup](RUST-CLIMATE.md): native biome selection, compatibility constraints,
  and the independent Java comparison.
- [Biome fill](RUST-BIOME-FILL.md): the Rust-owned BIOMES stage (climate sampling,
  searches and section containers), its gate and parity/performance checks.
- [Biome searches](RUST-BIOME-SEARCH.md): Rust `findBiomeHorizontal` and
  `findClosestBiome3d` for multi-noise sources (ring placement, `/locate biome`).
- [Section color snapshots](RUST-SECTION-COLORS.md): shared native world-color
  inputs, literal-provider compatibility, lifetime and chunk rebuilding checks.
- [Live biome ownership and color sampling](RUST-LIVE-BIOMES.md): retained native
  sections/index, direct sky/fog sampling, generation-validated reuse and scoped
  Frozen verification.
